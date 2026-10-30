// Pruefung der Keycloak-Erweiterung (Playwright, headless Chromium) gegen einen laufenden Keycloak.
// MODUS=attrappe : Anmelde-Schritt und Registrierung gegen die Attrappe des Dienstes (Token werden ins Formular geschrieben)
// MODUS=echt     : gegen das echte Pruefprojekt 19 (Widget loest, Secret aus /secret/captcha19.secret)
// Der Realm "test", die beiden Ablaeufe (Browser + Registrierung) und die Einstellungen werden ueber die Admin-REST-Schnittstelle angelegt.
import { chromium } from "playwright";
import { mkdirSync, readFileSync } from "node:fs";

const KC = process.env.KC_URL || "http://localhost:8080";
const STUB = process.env.STUB_URL || "http://172.17.0.1:18176";
const MODUS = process.env.MODUS || "attrappe";
const AUFN = "/out/aufnahmen";
mkdirSync(AUFN, { recursive: true });
let fehler = 0;
const ok = (b, t) => { console.log((b ? "OK     " : "FEHLER ") + t); if (!b) fehler++; };

// ---------- Admin-REST ----------
let tok = "";
async function login() {
  const r = await fetch(`${KC}/realms/master/protocol/openid-connect/token`, { method: "POST",
    body: new URLSearchParams({ grant_type: "password", client_id: "admin-cli", username: "admin", password: "admin" }) });
  tok = (await r.json()).access_token;
}
async function api(method, path, body, nochmal = true) {
  let r = await fetch(`${KC}/admin/realms${path}`, { method, headers: { Authorization: `Bearer ${tok}`, "Content-Type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body) });
  if (r.status === 401 && nochmal) { await login(); return api(method, path, body, false); }
  const text = await r.text();
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${text.slice(0, 300)}`);
  return { status: r.status, loc: r.headers.get("location"), json: text ? JSON.parse(text) : null };
}
async function executions(flow) { return (await api("GET", `/test/authentication/flows/${encodeURIComponent(flow)}/executions`)).json; }

const configIds = {};
async function aufbauen() {
  await login();
  try { await api("DELETE", "/test"); } catch {}
  await api("POST", "", { realm: "test", enabled: true, registrationAllowed: true, loginWithEmailAllowed: true, duplicateEmailsAllowed: false,
    verifyEmail: false, internationalizationEnabled: true, supportedLocales: ["en", "de"], defaultLocale: "en", sslRequired: "none" });
  await api("POST", "/test/clients", { clientId: "hc", publicClient: true, standardFlowEnabled: true, redirectUris: ["http://localhost:8080/*"], webOrigins: ["+"] });
  await api("POST", "/test/users", { username: "max", enabled: true, email: "max@example.org", emailVerified: true, firstName: "Max", lastName: "Muster",
    credentials: [{ type: "password", value: "pw1234!", temporary: false }] });
  // Browser-Ablauf: Kopie, die Pruefung als erster Schritt des Anmelde-Teilablaufs (vor "Username Password Form")
  await api("POST", "/test/authentication/flows/browser/copy", { newName: "hc-browser" });
  await api("POST", "/test/authentication/flows/hc-browser%20forms/executions/execution", { provider: "hostily-captcha-authenticator" });
  let ex = (await executions("hc-browser")).find((e) => e.providerId === "hostily-captcha-authenticator");
  await api("PUT", "/test/authentication/flows/hc-browser/executions", { ...ex, requirement: "REQUIRED" });
  for (let i = 0; i < 5; i++) await api("POST", `/test/authentication/executions/${ex.id}/raise-priority`).catch(() => {});
  configIds.browser = ex.id;
  // Registrierung: Kopie, Aktion im Teilablauf "registration form"
  await api("POST", "/test/authentication/flows/registration/copy", { newName: "hc-registration" });
  await api("POST", "/test/authentication/flows/hc-registration%20registration%20form/executions/execution", { provider: "registration-hostily-captcha-action" });
  ex = (await executions("hc-registration")).find((e) => e.providerId === "registration-hostily-captcha-action");
  await api("PUT", "/test/authentication/flows/hc-registration/executions", { ...ex, requirement: "REQUIRED" });
  configIds.reg = ex.id;
  const r = (await api("GET", "/test")).json;
  r.browserFlow = "hc-browser";
  r.registrationFlow = "hc-registration";
  await api("PUT", "/test", r);
}
const configModel = {};
async function einstellen(c) {
  const config = { siteKey: "", secret: "", serviceUrl: STUB, mode: "visible", language: "auto", onError: "pass", ...c };
  for (const [k, id] of Object.entries(configIds)) {
    if (!configModel[k]) {
      const res = await api("POST", `/test/authentication/executions/${id}/config`, { alias: "hc-" + k, config });
      const ex = (await executions(k === "browser" ? "hc-browser" : "hc-registration")).find((e) => e.id === id);
      configModel[k] = ex.authenticationConfig;
    } else {
      await api("PUT", `/test/authentication/config/${configModel[k]}`, { id: configModel[k], alias: "hc-" + k, config });
    }
  }
}
const zaehler = async () => Number(await (await fetch(`${STUB}/_count`)).text());
const letzte = async () => JSON.parse(await (await fetch(`${STUB}/_last`)).text() || "{}");
const zuruecksetzen = () => fetch(`${STUB}/_reset`);
async function benutzer(name) { return (await api("GET", `/test/users?username=${encodeURIComponent(name)}&exact=true`)).json; }

// ---------- Browser ----------
const browser = await chromium.launch();
const neu = async (locale = "en-US") => { const c = await browser.newContext({ viewport: { width: 1100, height: 900 }, locale }); const p = await c.newPage();
  p.on("pageerror", (e) => console.log("Seitenfehler:", e.message.slice(0, 200))); return [c, p]; };
const AUTH = `client_id=hc&response_type=code&scope=openid&redirect_uri=${encodeURIComponent(KC + "/hc-fertig")}`;   // Zielseite gibt es nicht: die Adresse mit code= zeigt die gelungene Anmeldung
const ANMELDEN = `${KC}/realms/test/protocol/openid-connect/auth?${AUTH}`;
const text = (p) => p.locator("body").innerText();
const schritt = (p) => p.locator("#kc-hostily-captcha-form");
const loginForm = (p) => p.locator("#kc-form-login, input[name=username]");
async function tokenSetzen(p, form, wert) {
  await p.locator(form).evaluate((f, v) => { f.querySelector("hostily-captcha")?.remove(); const i = document.createElement("input"); i.type = "hidden"; i.name = "hostily-captcha-token"; i.value = v; f.appendChild(i); }, wert);
}
let nr = 0;
const eindeutig = () => `u${Date.now().toString(36)}${++nr}`;
async function regSeite(p, name) {
  await p.goto(`${KC}/realms/test/protocol/openid-connect/registrations?${AUTH}`);
  await p.fill("input[name=username]", name);
  await p.fill("input[name=email]", `${name}@example.org`);
  await p.fill("input[name=firstName]", "Erika");
  await p.fill("input[name=lastName]", "Muster");
  await p.fill("input[name=password]", "Geheim-12345!");
  await p.fill("input[name=password-confirm]", "Geheim-12345!");
}

await aufbauen();

if (MODUS === "attrappe") {
  // ===== Anmelde-Schritt gegen die Attrappe =====
  console.log("=== Anmelde-Schritt, Attrappe ===");
  const SK = { siteKey: "attrappen-schluessel" };
  await einstellen({ ...SK, secret: "ok-secret" });
  let [c, p] = await neu();
  await zuruecksetzen();
  await p.goto(ANMELDEN);
  ok((await schritt(p).count()) === 1 && (await p.locator("hostily-captcha").count()) === 1, "Schritt: Seite mit Widget-Element vor Benutzername/Kennwort");
  ok((await p.locator("input[name=password]").count()) === 0, "Schritt: noch keine Kennwortabfrage");
  const widget = await p.locator("hostily-captcha").evaluate((e) => [e.getAttribute("data-site-key"), e.getAttribute("data-api"), e.getAttribute("data-modus"), e.getAttribute("data-sprache")]);
  ok(widget[0] === "attrappen-schluessel" && widget[1] === `${STUB}/api/` && widget[2] === "sichtbar" && widget[3] === "en", "Schritt: Widget-Attribute (" + widget.join(", ") + ")");
  ok(!(await p.content()).includes("ok-secret"), "Schritt: Secret nicht im HTML");
  await p.screenshot({ path: `${AUFN}/schritt-widget-bereit.png` });
  // ohne Token
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok(/confirm that you are not a robot/i.test(await text(p)) && (await schritt(p).count()) === 1, "Schritt: ohne Token abgelehnt, Meldung, Schritt bleibt");
  ok((await zaehler()) === 0, "Schritt: ohne Token wird der Dienst nicht gefragt");
  await p.screenshot({ path: `${AUFN}/schritt-ohne-token-abgelehnt.png` });
  for (const [tokenWert, erwartet] of [["used", "failed"], ["spam", "failed"], ["expired", "failed"]]) {
    await tokenSetzen(p, "#kc-hostily-captcha-form", tokenWert);
    await p.locator("#kc-continue").click();
    await p.waitForLoadState("load");
    ok(/security check failed/i.test(await text(p)) && (await schritt(p).count()) === 1, `Schritt: Token "${tokenWert}" abgelehnt`);
  }
  const l = await letzte();
  ok(l.body && l.body.secret === "ok-secret" && l.body.response === "expired" && !("formular" in l.body) && l.ctype.startsWith("application/json") && /hostily-captcha-java\//.test(l.ua), "Schritt: Anfrage an den Dienst (secret, response, JSON, User-Agent, kein Inhalt)");
  // good -> weiter zur Kennwortabfrage, Anmeldung gelingt
  await tokenSetzen(p, "#kc-hostily-captcha-form", "good");
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok((await p.locator("input[name=username]").count()) === 1 && (await schritt(p).count()) === 0, "Schritt: Token \"good\" angenommen, weiter zu Benutzername/Kennwort");
  await p.screenshot({ path: `${AUFN}/schritt-weiter-zur-anmeldung.png` });
  await p.fill("input[name=username]", "max");
  await p.fill("input[name=password]", "pw1234!");
  await p.locator("#kc-login, input[type=submit]").first().click();
  await p.waitForURL(/hc-fertig/, { timeout: 30000 });
  ok(true, "Schritt: Anmeldung vollstaendig durchgelaufen (Weiterleitung mit code)");
  await c.close();
  // CSP
  const r2 = await fetch(ANMELDEN);
  console.log("INFO   Content-Security-Policy der Anmeldeseite: " + r2.headers.get("content-security-policy"));

  // Ausfall des Dienstes
  [c, p] = await neu();
  for (const geheim of ["http500", "garbage", "bad"]) {
    await einstellen({ ...SK, secret: geheim, onError: "pass" });
    await p.goto(ANMELDEN);
    await tokenSetzen(p, "#kc-hostily-captcha-form", "good");
    await p.locator("#kc-continue").click();
    await p.waitForLoadState("load");
    ok((await p.locator("input[name=username]").count()) === 1, `Schritt: Dienst-Antwort "${geheim}" + durchlassen -> weiter`);
    await einstellen({ ...SK, secret: geheim, onError: "block" });
    await c.clearCookies();
    await p.goto(ANMELDEN);
    await tokenSetzen(p, "#kc-hostily-captcha-form", "good");
    await p.locator("#kc-continue").click();
    await p.waitForLoadState("load");
    ok(/currently not available/i.test(await text(p)) && (await schritt(p).count()) === 1, `Schritt: Dienst-Antwort "${geheim}" + blockieren -> abgelehnt`);
    await c.clearCookies();
  }
  await einstellen({ ...SK, secret: "ok-secret", serviceUrl: "http://127.0.0.1:1", onError: "pass" });
  await p.goto(ANMELDEN);
  await tokenSetzen(p, "#kc-hostily-captcha-form", "good");
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok((await p.locator("input[name=username]").count()) === 1, "Schritt: Dienst nicht erreichbar + durchlassen -> weiter");
  await c.clearCookies();
  await einstellen({ ...SK, secret: "ok-secret", serviceUrl: "http://127.0.0.1:1", onError: "block" });
  await p.goto(ANMELDEN);
  await tokenSetzen(p, "#kc-hostily-captcha-form", "good");
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok(/currently not available/i.test(await text(p)), "Schritt: Dienst nicht erreichbar + blockieren -> abgelehnt");
  await p.screenshot({ path: `${AUFN}/schritt-dienst-ausgefallen-blockiert.png` });
  await c.close();

  // Deutsch + unsichtbar + feste Sprache
  await einstellen({ ...SK, secret: "ok-secret", mode: "invisible" });
  [c, p] = await neu("de-DE");
  await p.goto(ANMELDEN + "&kc_locale=de");
  const w2 = await p.locator("hostily-captcha").evaluate((e) => [e.getAttribute("data-modus"), e.getAttribute("data-sprache")]);
  ok(w2[0] === "unsichtbar" && w2[1] === "de", "Schritt: deutsche Seite, Modus unsichtbar (" + w2.join(", ") + ")");
  ok(/Sicherheitsprüfung/.test(await text(p)), "Schritt: Texte deutsch (\"Sicherheitsprüfung\")");
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok(/bestätigen Sie, dass Sie kein Roboter sind/.test(await text(p)), "Schritt: Fehlermeldung deutsch");
  await p.screenshot({ path: `${AUFN}/schritt-deutsch.png` });
  await c.close();

  // ohne Konfiguration
  await einstellen({ siteKey: "", secret: "" });
  [c, p] = await neu();
  await zuruecksetzen();
  await p.goto(ANMELDEN);
  ok((await schritt(p).count()) === 0 && (await p.locator("input[name=username]").count()) === 1, "Schritt: ohne Konfiguration keine Pruefung, Anmeldeseite sofort");
  ok((await zaehler()) === 0, "Schritt: ohne Konfiguration keine Anfrage an den Dienst");
  await c.close();

  // ===== Registrierung gegen die Attrappe =====
  console.log("=== Registrierung, Attrappe ===");
  await einstellen({ ...SK, secret: "ok-secret" });
  [c, p] = await neu();
  let name = eindeutig();
  await regSeite(p, name);
  ok((await p.locator("#kc-register-form hostily-captcha").count()) === 1, "Registrierung: Widget-Element im Formular (per Skript eingefuegt)");
  const wr = await p.locator("hostily-captcha").evaluate((e) => [e.getAttribute("data-site-key"), e.getAttribute("data-api"), e.getAttribute("data-modus")]);
  ok(wr[0] === "attrappen-schluessel" && wr[1] === `${STUB}/api/` && wr[2] === "sichtbar", "Registrierung: Widget-Attribute (" + wr.join(", ") + ")");
  const vorKnopf = await p.evaluate(() => { const w = document.querySelector("hostily-captcha"); const b = document.getElementById("kc-form-buttons"); return !!(w && b && (w.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING)); });
  ok(vorKnopf, "Registrierung: Widget steht vor dem Registrieren-Knopf");
  await p.screenshot({ path: `${AUFN}/registrierung-widget-bereit.png`, fullPage: true });
  // ohne Token
  await zuruecksetzen();
  await p.locator("input[type=submit]").click();
  await p.waitForLoadState("load");
  ok(/confirm that you are not a robot/i.test(await text(p)) && (await benutzer(name)).length === 0, "Registrierung: ohne Token abgelehnt, kein Benutzer angelegt");
  ok((await zaehler()) === 0, "Registrierung: ohne Token wird der Dienst nicht gefragt");
  ok((await p.locator("input[name=username]").inputValue()) === name && (await p.locator("#kc-register-form hostily-captcha").count()) === 1, "Registrierung: Eingaben bleiben stehen, frisches Widget");
  await p.screenshot({ path: `${AUFN}/registrierung-ohne-token-abgelehnt.png`, fullPage: true });
  for (const t of ["used", "spam"]) {
    await p.fill("input[name=password]", "Geheim-12345!"); await p.fill("input[name=password-confirm]", "Geheim-12345!");
    await tokenSetzen(p, "#kc-register-form", t);
    await p.locator("input[type=submit]").click();
    await p.waitForLoadState("load");
    ok(/security check failed/i.test(await text(p)) && (await benutzer(name)).length === 0, `Registrierung: Token "${t}" abgelehnt, kein Benutzer`);
  }
  const l2 = await letzte();
  ok(l2.body && l2.body.secret === "ok-secret" && l2.body.response === "spam" && l2.body.formular && l2.body.formular.username === name && l2.body.formular.email === `${name}@example.org`
     && !JSON.stringify(l2.body).includes("Geheim-12345") && !("password" in l2.body.formular) && !("password-confirm" in l2.body.formular), "Registrierung: Inhalt geht an den Dienst, Kennwoerter nicht");
  await p.fill("input[name=password]", "Geheim-12345!"); await p.fill("input[name=password-confirm]", "Geheim-12345!");
  await tokenSetzen(p, "#kc-register-form", "good");
  await p.locator("input[type=submit]").click();
  await p.waitForLoadState("load");
  await p.waitForURL(/hc-fertig/, { timeout: 30000 }).catch(() => {});
  ok((await benutzer(name)).length === 1, "Registrierung: Token \"good\" angenommen, Benutzer angelegt");
  await p.screenshot({ path: `${AUFN}/registrierung-angelegt.png` });
  await c.close();
  // Ausfall
  for (const [geheim, onError, soll] of [["http500", "pass", true], ["http500", "block", false], ["garbage", "pass", true], ["bad", "block", false], ["bad", "pass", true]]) {
    await einstellen({ ...SK, secret: geheim, onError });
    [c, p] = await neu();
    name = eindeutig();
    await regSeite(p, name);
    await tokenSetzen(p, "#kc-register-form", "good");
    await p.locator("input[type=submit]").click();
    await p.waitForLoadState("load");
    await p.waitForTimeout(1500);
    const da = (await benutzer(name)).length === 1;
    ok(da === soll, `Registrierung: Dienst-Antwort "${geheim}" + ${onError === "pass" ? "durchlassen" : "blockieren"} -> ${soll ? "Benutzer angelegt" : "abgelehnt"}`);
    await c.close();
  }
  // ohne Konfiguration
  await einstellen({ siteKey: "", secret: "" });
  [c, p] = await neu();
  name = eindeutig();
  await zuruecksetzen();
  await regSeite(p, name);
  ok((await p.locator("hostily-captcha").count()) === 0, "Registrierung: ohne Konfiguration kein Widget");
  await p.locator("input[type=submit]").click();
  await p.waitForLoadState("load");
  await p.waitForTimeout(1500);
  ok((await benutzer(name)).length === 1 && (await zaehler()) === 0, "Registrierung: ohne Konfiguration keine Pruefung, Benutzer angelegt");
  await c.close();
} else {
  // ===== echter Dienst, Pruefprojekt 19 =====
  const secret = readFileSync("/secret/captcha19.secret", "utf8").trim();
  const ECHT = { siteKey: "8cdc48a989b7c79151d6", secret, serviceUrl: "https://captcha.hostily.de", onError: "block" };
  async function loesen(p, form) {
    const box = p.locator(`${form} hostily-captcha >> role=checkbox`).first();
    await box.waitFor({ timeout: 30000 });
    await box.scrollIntoViewIfNeeded();
    await p.waitForTimeout(500);
    await box.click();
    await p.locator(`${form} hostily-captcha >> role=checkbox[checked]`).first().waitFor({ timeout: 90000 });
    await p.waitForTimeout(8000);   // der Dienst lehnt Einsendungen < 3 s nach dem Loesen ab ("mindestzeit")
    return p.locator(`${form} input[name='hostily-captcha-token']`).inputValue();
  }
  console.log("=== Anmelde-Schritt, echter Dienst ===");
  await einstellen(ECHT);
  let [c, p] = await neu();
  await p.goto(ANMELDEN);
  await p.screenshot({ path: `${AUFN}/echt-schritt-bereit.png` });
  const t = await loesen(p, "#kc-hostily-captcha-form");
  ok(t.length > 10, "Echt/Schritt: Widget geloest, Token im Formular (" + t.slice(0, 8) + "...)");
  await p.screenshot({ path: `${AUFN}/echt-schritt-geloest.png` });
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok((await p.locator("input[name=username]").count()) === 1 && (await schritt(p).count()) === 0, "Echt/Schritt: Formular kommt durch, weiter zu Benutzername/Kennwort");
  await p.fill("input[name=username]", "max");
  await p.fill("input[name=password]", "pw1234!");
  await p.locator("#kc-login, input[type=submit]").first().click();
  await p.waitForURL(/hc-fertig/, { timeout: 30000 });
  ok(true, "Echt/Schritt: Anmeldung vollstaendig durchgelaufen");
  await c.close();
  [c, p] = await neu();
  await p.goto(ANMELDEN);
  await p.locator("#kc-hostily-captcha-form hostily-captcha").evaluate((e) => e.remove());
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok(/confirm that you are not a robot/i.test(await text(p)) && (await p.locator("input[name=username]").count()) === 0, "Echt/Schritt: ohne Token (Widget entfernt) abgelehnt");
  await p.screenshot({ path: `${AUFN}/echt-schritt-ohne-token-abgelehnt.png` });
  await c.close();
  // ein Token gilt nur einmal: dasselbe Token zweimal -> zweites Mal abgelehnt
  [c, p] = await neu();
  await p.goto(ANMELDEN);
  const t1 = await loesen(p, "#kc-hostily-captcha-form");
  await tokenSetzen(p, "#kc-hostily-captcha-form", "falsch-" + t1.slice(0, 4));
  await p.locator("#kc-continue").click();
  await p.waitForLoadState("load");
  ok(/security check failed/i.test(await text(p)) && (await schritt(p).count()) === 1, "Echt/Schritt: erfundenes Token abgelehnt");
  await c.close();

  console.log("=== Registrierung, echter Dienst ===");
  [c, p] = await neu();
  let name = eindeutig();
  await regSeite(p, name);
  ok((await p.locator("#kc-register-form hostily-captcha").count()) === 1, "Echt/Registrierung: Widget im Formular");
  const t3 = await loesen(p, "#kc-register-form");
  ok(t3.length > 10, "Echt/Registrierung: Widget geloest");
  await p.screenshot({ path: `${AUFN}/echt-registrierung-geloest.png`, fullPage: true });
  await p.locator("input[type=submit]").click();
  await p.waitForLoadState("load");
  await p.waitForURL(/hc-fertig/, { timeout: 30000 }).catch(() => {});
  ok((await benutzer(name)).length === 1, "Echt/Registrierung: Formular kommt durch, Benutzer angelegt");
  await p.screenshot({ path: `${AUFN}/echt-registrierung-angelegt.png` });
  await c.close();
  [c, p] = await neu();
  name = eindeutig();
  await regSeite(p, name);
  await p.locator("#kc-register-form hostily-captcha").evaluate((e) => e.remove());
  await p.waitForTimeout(4000);
  await p.locator("input[type=submit]").click();
  await p.waitForLoadState("load");
  await p.waitForTimeout(1000);
  ok(/confirm that you are not a robot/i.test(await text(p)) && (await benutzer(name)).length === 0, "Echt/Registrierung: ohne Token (Widget entfernt) abgelehnt, kein Benutzer");
  await p.screenshot({ path: `${AUFN}/echt-registrierung-ohne-token-abgelehnt.png`, fullPage: true });
  await c.close();
  // unsichtbar
  await einstellen({ ...ECHT, mode: "invisible" });
  [c, p] = await neu();
  name = eindeutig();
  await regSeite(p, name);
  await p.waitForTimeout(8000);
  await p.locator("input[type=submit]").click();
  await p.waitForURL(/hc-fertig/, { timeout: 60000 }).catch(() => {});
  await p.waitForTimeout(1000);
  ok((await benutzer(name)).length === 1, "Echt/Registrierung: Modus unsichtbar, Widget loest beim Absenden, Benutzer angelegt");
  await c.close();
  // verschaerfte Content-Security-Policy: ohne Erlaubnis geht das Widget nicht, mit der Zeile aus dem README schon
  console.log("=== verschaerfte Content-Security-Policy ===");
  const setzeCsp = async (wert) => { const r = (await api("GET", "/test")).json; r.browserSecurityHeaders = { ...r.browserSecurityHeaders, contentSecurityPolicy: wert }; await api("PUT", "/test", r); };
  await einstellen({ ...ECHT, mode: "visible" });
  await setzeCsp("default-src 'self'; script-src 'self'; connect-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-src 'self'; frame-ancestors 'self'; object-src 'none'");
  [c, p] = await neu();
  await p.goto(ANMELDEN);
  await p.waitForTimeout(4000);
  ok((await p.locator("hostily-captcha >> role=checkbox").count()) === 0, "CSP: ohne Erlaubnis fuer den Dienst erscheint das Widget nicht (Kontrolle, dass die Regel wirkt)");
  await c.close();
  await setzeCsp("script-src 'self' https://captcha.hostily.de data:; connect-src 'self' https://captcha.hostily.de; worker-src 'self' blob:; frame-src 'self'; frame-ancestors 'self'; object-src 'none'");
  [c, p] = await neu();
  await p.goto(ANMELDEN);
  const t5 = await loesen(p, "#kc-hostily-captcha-form");
  ok(t5.length > 10, "CSP: mit der Zeile aus dem README loest das Widget im Anmelde-Schritt");
  await c.close();
  [c, p] = await neu();
  name = eindeutig();
  await regSeite(p, name);
  const t6 = await loesen(p, "#kc-register-form");
  ok(t6.length > 10, "CSP: mit der Zeile aus dem README loest das Widget in der Registrierung (data:-Skript erlaubt)");
  await c.close();
  await setzeCsp("frame-src 'self'; frame-ancestors 'self'; object-src 'none';");
}
await browser.close();
console.log(fehler ? `${fehler} FEHLER` : "ALLES GRUEN");
process.exit(fehler ? 1 : 0);
