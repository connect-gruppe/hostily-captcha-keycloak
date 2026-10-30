# hostily Captcha für Keycloak

Erweiterung (ein Jar, Version 0.1.0, MIT) für [Keycloak](https://www.keycloak.org). Bindet den Spamschutz [hostily Captcha](https://captcha.hostily.de) ein:

* **Authenticator „hostily Captcha“** für den Browser-Anmeldeablauf: eigene Seite mit dem Widget, die vor „Benutzername/Kennwort“ steht. Erst mit gültigem Token geht es weiter.
* **Form Action „hostily Captcha“** für die Selbstregistrierung: das Widget steht im Registrierungsformular, das Token wird geprüft, bevor der Benutzer angelegt wird.
* Einstellungen **je Realm und je Ablauf** über die Konfiguration des Schritts (Zahnrad in der Ablaufansicht der Verwaltungsoberfläche).
* Die Vorlage (`hostily-captcha.ftl`) und die Meldungen (Deutsch, Englisch) kommen im Jar mit und stehen jedem Anmelde-Theme zur Verfügung; **es ist kein eigenes Theme nötig**.
* Die Java-Bibliothek `de.hostily:hostily-captcha` (`integrationen/bibliotheken/java`) ist ins Jar eingebaut, es werden keine weiteren Abhängigkeiten gebraucht.

## Getestete Versionen

Keycloak unterstützt laut Projekt nur die jeweils aktuelle 26.x-Reihe; 25 und 26.0 bis 26.7 bekommen keine Korrekturen mehr (Stand 28.10.2026: aktuell 26.8.1). Das Jar wird gegen die API von 25.0.6 gebaut
und läuft unverändert auf den neueren Ausgaben.

| Version | Ergebnis |
|---|---|
| 26.8.1 (aktuell) | Anmelde-Schritt und Registrierung, Durchgang „attrappe“ und „echt“ (Prüfprojekt 19) grün, dazu verschärfte CSP |
| 25.0.6 | Anmelde-Schritt und Registrierung, Durchgang „attrappe“ und „echt“ grün (nur noch zur Kompatibilität; die Version wird vom Projekt nicht mehr gepflegt) |

Die Läufe benutzten dieselben Dateien wie das Abbild `quay.io/keycloak/keycloak:<Version>` (Verteilung `keycloak-<Version>.tar.gz` mit Temurin-JDK 21, `start-dev`), weil im Docker-Speicher des Testrechners zu wenig Platz war, um das Abbild zu ziehen. `test/lauf.sh` nimmt das Abbild, wenn Platz ist.

Was nicht im Container lief, ist nicht geprüft (insbesondere 26.0 bis 26.7 außer den genannten, Keycloak-Betrieb mit externer Datenbank, Cluster, Keycloak 27+ und Mandantenfähigkeit/Organisationen). Die Erweiterung benutzt nur stabile Erweiterungsschnittstellen
(`Authenticator`, `FormAction`, `ThemeResourceProvider`).

## Einbau

1. Im Dienst (captcha.hostily.de/verwaltung) ein Projekt anlegen; die **Domain, unter der Keycloak erreichbar ist**, in „erlaubte Domains“ eintragen.
2. `hostily-captcha-keycloak-0.1.0.jar` in den Ordner `providers/` von Keycloak legen. Bei `start` (nicht `start-dev`) danach `kc.sh build` ausführen (Docker: `RUN /opt/keycloak/bin/kc.sh build` im eigenen Abbild, oder die Datei nach `/opt/keycloak/providers/` einbinden und `start-dev` benutzen). Keycloak neu starten.
3. Verwaltungsoberfläche → *Authentication* → Ablauf „browser“ **duplizieren** (die eingebauten Abläufe sind nicht änderbar), z. B. „browser hostily“.
4. Im Teilablauf „browser hostily **forms**“ (er enthält „Username Password Form“) → *Add step* → **hostily Captcha**, Requirement **Required**, mit dem Pfeil **ganz nach oben** schieben (vor „Username Password Form“), dann am Zahnrad die Einstellungen eintragen (siehe unten).
5. *Action* → *Bind flow* → **Browser flow**. Fertig: Die Anmeldung zeigt zuerst die Sicherheitsprüfung.
6. Registrierung: Ablauf „registration“ duplizieren, im Teilablauf „… registration form“ → *Add step* → **hostily Captcha** (Form Action, Required, am besten **hinter** „Profile Validation“, damit ein Tippfehler im Formular kein Token verbraucht), Einstellungen eintragen, *Bind flow* → **Registration flow**. Außerdem muss unter *Realm settings → Login* „User registration“ eingeschaltet sein.
7. „Passwort vergessen“: der Authenticator lässt sich auf dieselbe Weise in einen Duplikat des Ablaufs „reset credentials“ als erster Schritt setzen (nicht getestet).

Wer bereits angemeldet ist (Cookie-Schritt des Ablaufs) sieht die Prüfung nicht; sie gilt nur dem Anmeldevorgang. Mehrere Ablaufkopien (je Realm, je Client) mit eigenen Schlüsseln sind möglich.

## Einstellungen (je Schritt)

| Feld | Bedeutung |
|---|---|
| Site key / Secret | aus dem Projekt im Dienst. Das Secret bleibt auf dem Server (Passwortfeld, wird nie in HTML oder Protokoll geschrieben). Ist das Feld leer, gilt die Umgebungsvariable `HOSTILY_CAPTCHA_SECRET` (hält das Secret aus dem Realm-Export). |
| Service URL | Vorgabe `https://captcha.hostily.de` (eigene Installation möglich) |
| Mode | `visible` (Kästchen) oder `invisible` (löst beim Absenden) |
| Widget language | `auto` (Sprache der Anmeldeseite: de oder en, sonst entscheidet das Widget), `de`, `en` |
| If the service fails | `pass` (durchlassen, Standard) oder `block` (ablehnen). Gilt auch bei falschem Secret; steht immer im Protokoll des Servers (`ERROR … hostily Captcha: …`) |

Ohne Site key und Secret ist nichts geschützt und nichts gesperrt (der Schritt wird übersprungen, der Dienst nicht gefragt). Der Dienst lehnt Einsendungen ab, die nur Sekundenbruchteile nach dem Lösen kommen (Grund `mindestzeit`).

## Was geschützt ist

| Ablauf | Schritt | Prüfung |
|---|---|---|
| Browser-Anmeldung | Authenticator, eigene Seite vor Benutzername/Kennwort | Token; es werden keine Formularinhalte gesendet |
| Selbstregistrierung | Form Action im Registrierungsformular | Token plus die Textfelder des Formulars (ohne Kennwörter) für die Inhaltsprüfung |

Nicht geschützt: Direktzugriff (Resource-Owner-Password-Grant, Token-Endpunkt), Admin-Oberfläche und -REST, Konto-Konsole, Anmeldung über externe Identitätsanbieter, Abläufe, in die der Schritt nicht gesetzt wurde.

## Content-Security-Policy

Keycloak setzt auf Anmeldeseiten den Kopf `Content-Security-Policy`. Der Standard lautet
`frame-src 'self'; frame-ancestors 'self'; object-src 'none'`; er enthält **keine** Einschränkung für Skripte und Verbindungen, das Widget funktioniert damit ohne Änderung (so geprüft, siehe Tests).

Haben Sie den Kopf verschärft (Verwaltungsoberfläche → *Realm settings → Security defenses → Content-Security-Policy*), müssen Sie die Adresse des Dienstes ergänzen. Für `https://captcha.hostily.de`:

```
script-src 'self' https://captcha.hostily.de data:; connect-src 'self' https://captcha.hostily.de; worker-src 'self' blob:; frame-src 'self'; frame-ancestors 'self'; object-src 'none'
```

* `script-src`: das Widget-Skript des Dienstes; **`data:`** wird nur für die **Registrierung** gebraucht – dort fügt Keycloak das Widget über ein kleines, vom Server erzeugtes Skript ein (`data:`-Adresse), weil die Registrierungsvorlage von Keycloak keine Stelle für fremde Bausteine hat.
* `connect-src`: die Aufrufe des Widgets an den Dienst (Aufgabe holen, Token einlösen, WASM laden).
* `worker-src blob:`: das Widget rechnet in einem Web Worker.
* Bei eigener Dienst-URL die Adresse entsprechend ersetzen. Die Erweiterung ändert den Kopf nicht selbst (eine automatische Ergänzung ist in Keycloak nicht vorgesehen und würde die Einstellung des Realms überschreiben).

## Wie es arbeitet

* Authenticator: `challenge` mit der Vorlage `hostily-captcha.ftl` (`<hostily-captcha>` plus Widget-Skript vom Dienst); in `action` wird `hostily-captcha-token` (sonst `g-recaptcha-response`) mit der Java-Bibliothek gegen `POST <Dienst>/api/v1/pruefen`
  geprüft. Ablehnung: Meldung auf der Seite, Ereignis `LOGIN_ERROR` mit Fehler `hostily_captcha_failed` (Detail `hostily_captcha` = Grund), Seite mit frischem Widget.
* Form Action: `buildPage` hängt über `addScript` ein kleines Skript an, das das Widget vor den Registrieren-Knopf setzt; `validate` prüft das Token vor dem Anlegen des Benutzers (Ereignis `REGISTER_ERROR`, `invalid_registration`).
* Das Secret verlässt den Server nicht; im Browser steht nur der Site key.

Hinweis Datenschutz: Das Widget-Skript wird vom Dienst geladen (`captcha.hostily.de`, ohne Cookies); in der Datenschutzerklärung nennen.

## Notausgang

Den Schritt im Ablauf auf **Disabled** stellen oder das Site-key-Feld leeren: dann ist nichts mehr geschützt und nichts gesperrt. Wenn die Anmeldung gar nicht mehr geht, in der Verwaltung den Ablauf wieder auf „browser“ binden
(`kcadm.sh update realms/<Realm> -s browserFlow=browser`) oder das Jar aus `providers/` entfernen und neu bauen/starten.

## Bauen und Tests

`bauen.sh` (unter `flock /tmp/claude-0/ap-docker.lock`) baut im `maven`-Container (kein Java auf dem Host nötig): zuerst die Bibliothek (`mvn install`), dann die Erweiterung mit den Einheitstests (Entscheidungslogik gegen einen Test-Server),
und legt `dist/hostily-captcha-keycloak-0.1.0.jar` sowie `dist/hostily-captcha-keycloak.zip` an; Letzteres wird nach `website/downloads/hostily-captcha-keycloak.zip` kopiert. `bauen.sh <Keycloak-Version>` kompiliert gegen eine andere API-Version.

`test/lauf.sh [Version] [alle|attrappe|echt]` zieht `quay.io/keycloak/keycloak:<Version>` (Entwicklungsmodus, keine Datenbank) und lässt `test/browser.spec.mjs` (Playwright) laufen. Der Test legt per Admin-REST einen Realm mit den beiden Abläufen an und prüft:

* **Durchgang „attrappe“** (`test/stub-dienst.php`, Port 18176): Anmelde-Schritt und Registrierung – ohne Token abgelehnt (der Dienst wird nicht gefragt), `good` angenommen, `used`/`spam`/`expired` abgelehnt mit Meldung, `http500`/`garbage`/`bad`/Dienst nicht erreichbar → durchlassen bzw. bei „block“ abgelehnt,
  Inhalt geht ohne Kennwörter an den Dienst, Secret nicht im HTML, deutsche Texte, Modus unsichtbar, ohne Konfiguration keine Prüfung.
* **Durchgang „echt“**: gegen das echte Prüfprojekt 19: Widget lösen, Anmeldung bzw. Registrierung kommt durch; ohne Token (Widget entfernt) abgelehnt; Modus unsichtbar. Aufnahmen: `test/aufnahmen/`.

---

## English

hostily Captcha for Keycloak: one jar (0.1.0, MIT) with an **authenticator** for the browser login flow (a page with the widget before "Username Password Form") and a **form action** for self-registration (the widget sits in the registration form; the token is checked before the user is created).
Settings per realm and flow through the step configuration. Template and messages (English, German) ship in the jar and work with every login theme; no custom theme needed. The Java library is shaded in.

Install: put the jar into `providers/` (run `kc.sh build` for `start`; `start-dev` builds by itself), restart. Admin console → Authentication: **duplicate** the "browser" flow, add the step **hostily Captcha** (Required) to its "forms" sub-flow, move it above "Username Password Form",
enter site key and secret (the secret stays on the server; empty = environment variable `HOSTILY_CAPTCHA_SECRET`), bind the flow as Browser flow. For registration duplicate "registration", add **hostily Captcha** (form action, Required) to its "registration form" sub-flow, bind as Registration flow.
Add the domain of your Keycloak to the allowed domains of the project at captcha.hostily.de. On service failure visitors are let through by default (setting: block); everything is logged. Without site key and secret nothing is protected and nothing is blocked.

CSP: the default Keycloak policy (`frame-src 'self'; frame-ancestors 'self'; object-src 'none'`) does not restrict scripts or connections, so the widget works unchanged. If you have tightened it, allow the service in `script-src` (plus `data:` for the registration page),
`connect-src` and `worker-src blob:` (see the German section for the exact string). The extension does not modify the header itself.
Not covered: direct grant / token endpoint, admin console, account console, logins through external identity providers. Tested versions: see the table above.
