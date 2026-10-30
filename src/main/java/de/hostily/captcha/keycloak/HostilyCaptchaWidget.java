package de.hostily.captcha.keycloak;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.keycloak.models.KeycloakSession;

/** Values for the widget on the page. */
final class HostilyCaptchaWidget {
    private HostilyCaptchaWidget() {
    }

    /** Widget language: fixed by the setting, or the language of the page when it is de or en, otherwise empty (the widget decides). */
    static String language(HostilyCaptchaConfig cfg, KeycloakSession session) {
        if (!"auto".equals(cfg.getLanguage())) {
            return cfg.getLanguage();
        }
        try {
            Locale l = session.getContext().resolveLocale(null);
            String lang = l == null ? "" : l.getLanguage();
            return "de".equals(lang) || "en".equals(lang) ? lang : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Attributes for the template of the authenticator page. */
    static Map<String, Object> attributes(HostilyCaptchaConfig cfg, KeycloakSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("siteKey", cfg.getSiteKey());
        m.put("service", cfg.getServiceUrl());
        m.put("api", cfg.getServiceUrl() + "/api/");
        m.put("mode", cfg.isInvisible() ? "unsichtbar" : "sichtbar");
        m.put("language", language(cfg, session));
        return m;
    }

    /**
     * Script for the registration page. The standard registration template has no place for our widget, so a small script
     * (passed as data: URI through {@code LoginFormsProvider.addScript}) inserts the element before the submit buttons
     * and loads the widget script of the service. The site key is public; the secret is never part of it.
     */
    static String registrationScript(HostilyCaptchaConfig cfg, KeycloakSession session) {
        Map<String, Object> a = attributes(cfg, session);
        String js = "(function(){var c={k:" + quote(a.get("siteKey")) + ",s:" + quote(a.get("service")) + ",a:" + quote(a.get("api"))
            + ",m:" + quote(a.get("mode")) + ",l:" + quote(a.get("language")) + "};"
            + "function go(){var f=document.getElementById('kc-register-form');if(!f||f.querySelector('hostily-captcha'))return;"
            + "var w=document.createElement('hostily-captcha');w.setAttribute('data-site-key',c.k);w.setAttribute('data-api',c.a);"
            + "w.setAttribute('data-modus',c.m);if(c.l)w.setAttribute('data-sprache',c.l);"
            + "w.style.display='block';w.style.margin='0 0 1rem';"
            + "var b=f.querySelector('#kc-form-buttons');if(b&&b.parentNode===f)f.insertBefore(w,b);else if(b)b.parentNode.insertBefore(w,b);else f.appendChild(w);"
            + "var s=document.createElement('script');s.src=c.s+'/widget/hostily-captcha.js';s.defer=true;document.head.appendChild(s);}"
            + "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',go);else go();})();";
        return "data:text/javascript;base64," + Base64.getEncoder().encodeToString(js.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(Object o) {
        String s = String.valueOf(o);
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') {
                b.append('\\').append(c);
            } else if (c < 0x20 || c == '<' || c == '>' || c == '&') {
                b.append(String.format("\\u%04x", (int) c));
            } else {
                b.append(c);
            }
        }
        return b.append('"').toString();
    }
}
