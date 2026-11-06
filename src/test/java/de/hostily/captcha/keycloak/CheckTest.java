package de.hostily.captcha.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The decision logic against a small stand-in for the service (behaviour of the secret/token as in the other integrations' stub). */
class CheckTest {
    private HttpServer server;
    private final List<String> bodies = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/pruefen", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            int status = 200;
            String out;
            if (body.contains("\"secret\":\"http500\"")) {
                status = 500;
                out = "{\"error\":\"boom\"}";
            } else if (body.contains("\"secret\":\"garbage\"")) {
                out = "<html>nope</html>";
            } else if (body.contains("\"secret\":\"bad\"")) {
                out = "{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}";
            } else if (body.contains("\"response\":\"good\"")) {
                out = "{\"success\":true}";
            } else if (body.contains("\"response\":\"used\"")) {
                out = "{\"success\":false,\"gruende\":[\"token_bereits_verwendet\"]}";
            } else {
                out = "{\"success\":false,\"gruende\":[\"inhalt_spam\"]}";
            }
            byte[] b = out.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HostilyCaptchaConfig cfg(String secret, String onError) {
        return new HostilyCaptchaConfig(Map.of("siteKey", "sk", "secret", secret, "serviceUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/",
            "onError", onError), null);
    }

    @Test
    void notConfiguredChecksNothing() {
        assertTrue(HostilyCaptchaCheck.check(new HostilyCaptchaConfig(Map.of(), null), null, "1.1.1.1", null).passes());
        assertTrue(HostilyCaptchaCheck.check(new HostilyCaptchaConfig(Map.of("siteKey", "sk"), null), "x", "1.1.1.1", null).passes());
        assertTrue(HostilyCaptchaCheck.check(new HostilyCaptchaConfig(Map.of("secret", "s"), null), "x", "1.1.1.1", null).passes());
        assertEquals(0, bodies.size());
    }

    @Test
    void secretFromEnvironment() {
        HostilyCaptchaConfig c = new HostilyCaptchaConfig(Map.of("siteKey", "sk"), "from-env");
        assertTrue(c.isConfigured());
        assertEquals("from-env", c.getSecret());
        assertEquals("https://captcha.hostily.de", c.getServiceUrl());
    }

    @Test
    void goodAndRejected() {
        assertTrue(HostilyCaptchaCheck.check(cfg("s", "pass"), "good", "1.1.1.1", Map.of("name", "Ann")).passes());
        assertNull(HostilyCaptchaCheck.check(cfg("s", "pass"), "good", null, null).getMessageKey());
        assertEquals(HostilyCaptchaCheck.MSG_MISSING, HostilyCaptchaCheck.check(cfg("s", "pass"), null, null, null).getMessageKey());
        assertEquals(HostilyCaptchaCheck.MSG_MISSING, HostilyCaptchaCheck.check(cfg("s", "pass"), "  ", null, null).getMessageKey());
        HostilyCaptchaCheck.Outcome used = HostilyCaptchaCheck.check(cfg("s", "block"), "used", null, null);
        assertEquals(HostilyCaptchaCheck.MSG_FAILED, used.getMessageKey());
        assertEquals(HostilyCaptchaCheck.MSG_FAILED, HostilyCaptchaCheck.check(cfg("s", "pass"), "spam", null, null).getMessageKey());
    }

    @Test
    void outageAndWrongSecretFollowTheSetting() {
        for (String secret : new String[] {"http500", "garbage", "bad"}) {
            assertTrue(HostilyCaptchaCheck.check(cfg(secret, "pass"), "good", null, null).passes(), secret + " pass");
            assertEquals(HostilyCaptchaCheck.MSG_UNAVAILABLE, HostilyCaptchaCheck.check(cfg(secret, "block"), "good", null, null).getMessageKey(), secret + " block");
        }
        // not reachable
        HostilyCaptchaConfig down = new HostilyCaptchaConfig(Map.of("siteKey", "sk", "secret", "s", "serviceUrl", "http://127.0.0.1:1", "onError", "block"), null);
        assertEquals(HostilyCaptchaCheck.MSG_UNAVAILABLE, HostilyCaptchaCheck.check(down, "good", null, null).getMessageKey());
        HostilyCaptchaConfig down2 = new HostilyCaptchaConfig(Map.of("siteKey", "sk", "secret", "s", "serviceUrl", "http://127.0.0.1:1"), null);
        assertTrue(HostilyCaptchaCheck.check(down2, "good", null, null).passes());
    }

    @Test
    void registrationScriptContainsSiteKeyButNotTheSecret() {
        HostilyCaptchaConfig c = cfg("topsecret", "pass");
        assertFalse(c.getSiteKey().isEmpty());
        String uri = HostilyCaptchaWidget.registrationScript(c, null);
        assertTrue(uri.startsWith("data:text/javascript;base64,"));
        String js = new String(java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)), StandardCharsets.UTF_8);
        assertTrue(js.contains("\"sk\""));
        assertFalse(js.contains("topsecret"));
    }

    @Test
    void modeSettingHasFiveValuesAndKeepsTheOldOnes() {
        String[][] expected = {{"portal", ""}, {"visible", "sichtbar"}, {"invisible", "unsichtbar"}, {"hold", "halten"}, {"slide", "schieben"},
            {"", "sichtbar"}, {"unknown", "sichtbar"}};
        for (String[] e : expected) {
            HostilyCaptchaConfig c = new HostilyCaptchaConfig(Map.of("siteKey", "sk", "secret", "s", "mode", e[0]), null);
            assertEquals(e[1], c.getWidgetMode(), e[0]);
            assertEquals(e[1], HostilyCaptchaWidget.attributes(c, null).get("mode"), e[0]);
        }
        assertEquals("sichtbar", new HostilyCaptchaConfig(Map.of(), null).getWidgetMode()); // no value saved: as before
        assertTrue(new HostilyCaptchaConfig(Map.of("mode", "invisible"), null).isInvisible());
    }

    @Test
    void holdTimeOnlyForHoldAndClamped() {
        assertEquals("2200", HostilyCaptchaWidget.attributes(new HostilyCaptchaConfig(Map.of("mode", "hold", "holdTime", "2200"), null), null).get("holdTime"));
        assertEquals("800", HostilyCaptchaWidget.attributes(new HostilyCaptchaConfig(Map.of("mode", "hold", "holdTime", "50"), null), null).get("holdTime"));
        assertEquals("3000", HostilyCaptchaWidget.attributes(new HostilyCaptchaConfig(Map.of("mode", "hold", "holdTime", "99999"), null), null).get("holdTime"));
        assertEquals("1500", HostilyCaptchaWidget.attributes(new HostilyCaptchaConfig(Map.of("mode", "hold", "holdTime", "abc"), null), null).get("holdTime"));
        assertEquals("1500", HostilyCaptchaWidget.attributes(new HostilyCaptchaConfig(Map.of("mode", "hold"), null), null).get("holdTime"));
        assertEquals("", HostilyCaptchaWidget.attributes(new HostilyCaptchaConfig(Map.of("mode", "slide", "holdTime", "2200"), null), null).get("holdTime"));
    }

    @Test
    void registrationScriptSetsModeAttributes() {
        String js = decode(HostilyCaptchaWidget.registrationScript(new HostilyCaptchaConfig(Map.of("siteKey", "sk", "mode", "hold", "holdTime", "2000"), null), null));
        assertTrue(js.contains("m:\"halten\"") && js.contains("h:\"2000\"") && js.contains("data-haltezeit"));
        String portal = decode(HostilyCaptchaWidget.registrationScript(new HostilyCaptchaConfig(Map.of("siteKey", "sk", "mode", "portal"), null), null));
        assertTrue(portal.contains("m:\"\"") && portal.contains("if(c.m)w.setAttribute"));
    }

    private static String decode(String uri) {
        return new String(java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)), StandardCharsets.UTF_8);
    }
}
