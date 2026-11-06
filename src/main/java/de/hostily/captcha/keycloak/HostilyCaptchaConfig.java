package de.hostily.captcha.keycloak;

import java.util.List;
import java.util.Map;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/** Settings of one authenticator / form action execution (the "authenticator configuration" of the realm). */
public final class HostilyCaptchaConfig {
    public static final String SITE_KEY = "siteKey";
    public static final String SECRET = "secret";
    public static final String SERVICE_URL = "serviceUrl";
    public static final String MODE = "mode";
    public static final String HOLD_TIME = "holdTime";
    public static final String LANGUAGE = "language";
    public static final String ON_ERROR = "onError";

    /** Values of the mode setting. "visible" and "invisible" are the old values and stay valid; "portal" sends no attribute. */
    public static final String MODE_PORTAL = "portal";
    public static final String MODE_VISIBLE = "visible";
    public static final String MODE_INVISIBLE = "invisible";
    public static final String MODE_HOLD = "hold";
    public static final String MODE_SLIDE = "slide";
    public static final int HOLD_MIN = 800;
    public static final int HOLD_MAX = 3000;
    public static final int HOLD_DEFAULT = 1500;

    public static final String DEFAULT_SERVICE_URL = "https://captcha.hostily.de";
    /** If the secret field is empty, this environment variable is used (keeps the secret out of the realm export). */
    public static final String SECRET_ENV = "HOSTILY_CAPTCHA_SECRET";

    private final String siteKey;
    private final String secret;
    private final String serviceUrl;
    private final String mode;
    private final int holdTime;
    private final String language;
    private final boolean blockOnError;

    public HostilyCaptchaConfig(Map<String, String> config) {
        this(config, System.getenv(SECRET_ENV));
    }

    HostilyCaptchaConfig(Map<String, String> config, String envSecret) {
        Map<String, String> c = config == null ? Map.of() : config;
        this.siteKey = trim(c.get(SITE_KEY));
        String s = trim(c.get(SECRET));
        this.secret = s.isEmpty() ? trim(envSecret) : s;
        String u = trim(c.get(SERVICE_URL));
        this.serviceUrl = (u.isEmpty() ? DEFAULT_SERVICE_URL : u).replaceAll("/+$", "");
        String m = trim(c.get(MODE));
        // unknown or empty value: "visible" as before (the default of existing installations)
        this.mode = MODE_PORTAL.equals(m) || MODE_INVISIBLE.equals(m) || MODE_HOLD.equals(m) || MODE_SLIDE.equals(m) ? m : MODE_VISIBLE;
        int h = HOLD_DEFAULT;
        try {
            String t = trim(c.get(HOLD_TIME));
            if (!t.isEmpty()) {
                h = Integer.parseInt(t);
            }
        } catch (NumberFormatException e) {
            h = HOLD_DEFAULT;
        }
        this.holdTime = Math.max(HOLD_MIN, Math.min(HOLD_MAX, h));
        String l = trim(c.get(LANGUAGE));
        this.language = "de".equals(l) || "en".equals(l) ? l : "auto";
        this.blockOnError = "block".equals(trim(c.get(ON_ERROR)));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Without site key and secret nothing is protected and nothing is blocked. */
    public boolean isConfigured() {
        return !siteKey.isEmpty() && !secret.isEmpty() && (serviceUrl.startsWith("https://") || serviceUrl.startsWith("http://"));
    }

    public String getSiteKey() {
        return siteKey;
    }

    public String getSecret() {
        return secret;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public boolean isInvisible() {
        return MODE_INVISIBLE.equals(mode);
    }

    /** One of portal, visible, invisible, hold, slide. */
    public String getMode() {
        return mode;
    }

    /** Value for {@code data-modus}; empty = leave the attribute out (the mode of the project in the customer portal applies). */
    public String getWidgetMode() {
        switch (mode) {
            case MODE_PORTAL:
                return "";
            case MODE_INVISIBLE:
                return "unsichtbar";
            case MODE_HOLD:
                return "halten";
            case MODE_SLIDE:
                return "schieben";
            default:
                return "sichtbar";
        }
    }

    /** Hold time in ms (800 to 3000), only meaningful for the mode "hold". */
    public int getHoldTime() {
        return holdTime;
    }

    /** "de", "en" or "auto" (language of the page). */
    public String getLanguage() {
        return language;
    }

    public boolean isBlockOnError() {
        return blockOnError;
    }

    /** The settings form in the admin console. */
    static List<ProviderConfigProperty> properties() {
        return ProviderConfigurationBuilder.create()
            .property().name(SITE_KEY).label("Site key").helpText("Site key of the project at captcha.hostily.de. Without site key and secret nothing is protected and nothing is blocked.")
                .type(ProviderConfigProperty.STRING_TYPE).add()
            .property().name(SECRET).label("Secret").helpText("Secret of the project. Stays on the server. If empty, the environment variable " + SECRET_ENV + " is used.")
                .type(ProviderConfigProperty.PASSWORD).secret(true).add()
            .property().name(SERVICE_URL).label("Service URL").helpText("Address of the service; the default is for captcha.hostily.de.")
                .type(ProviderConfigProperty.STRING_TYPE).defaultValue(DEFAULT_SERVICE_URL).add()
            .property().name(MODE).label("Mode").helpText("portal: as set for the project in the customer portal. visible: checkbox. invisible: solves when the form is submitted. hold: press and hold. slide: slider. Hold and slide offer a click-to-confirm alternative.")
                .type(ProviderConfigProperty.LIST_TYPE).options(MODE_PORTAL, MODE_VISIBLE, MODE_INVISIBLE, MODE_HOLD, MODE_SLIDE).defaultValue(MODE_VISIBLE).add()
            .property().name(HOLD_TIME).label("Hold time (ms)").helpText("Only for the mode hold: 800 to 3000, default 1500.")
                .type(ProviderConfigProperty.STRING_TYPE).defaultValue(String.valueOf(HOLD_DEFAULT)).add()
            .property().name(LANGUAGE).label("Widget language").helpText("auto = language of the login page (de or en)")
                .type(ProviderConfigProperty.LIST_TYPE).options("auto", "de", "en").defaultValue("auto").add()
            .property().name(ON_ERROR).label("If the service fails").helpText("pass: let the request through (default). block: reject it. Applies to a wrong secret, too; always logged.")
                .type(ProviderConfigProperty.LIST_TYPE).options("pass", "block").defaultValue("pass").add()
            .build();
    }
}
