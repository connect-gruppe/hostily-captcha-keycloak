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
    public static final String LANGUAGE = "language";
    public static final String ON_ERROR = "onError";

    public static final String DEFAULT_SERVICE_URL = "https://captcha.hostily.de";
    /** If the secret field is empty, this environment variable is used (keeps the secret out of the realm export). */
    public static final String SECRET_ENV = "HOSTILY_CAPTCHA_SECRET";

    private final String siteKey;
    private final String secret;
    private final String serviceUrl;
    private final boolean invisible;
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
        this.invisible = "invisible".equals(trim(c.get(MODE)));
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
        return invisible;
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
            .property().name(MODE).label("Mode").helpText("Visible: checkbox. Invisible: solves when the form is submitted.")
                .type(ProviderConfigProperty.LIST_TYPE).options("visible", "invisible").defaultValue("visible").add()
            .property().name(LANGUAGE).label("Widget language").helpText("auto = language of the login page (de or en)")
                .type(ProviderConfigProperty.LIST_TYPE).options("auto", "de", "en").defaultValue("auto").add()
            .property().name(ON_ERROR).label("If the service fails").helpText("pass: let the request through (default). block: reject it. Applies to a wrong secret, too; always logged.")
                .type(ProviderConfigProperty.LIST_TYPE).options("pass", "block").defaultValue("pass").add()
            .build();
    }
}
