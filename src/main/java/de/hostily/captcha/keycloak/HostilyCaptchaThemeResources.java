package de.hostily.captcha.keycloak;

import org.keycloak.theme.ClasspathThemeResourceProviderFactory;

/**
 * Makes the template {@code hostily-captcha.ftl} and the messages available to every login theme
 * (files below {@code theme-resources/} in this jar). The realm does not need a special theme.
 */
public class HostilyCaptchaThemeResources extends ClasspathThemeResourceProviderFactory {
    public static final String ID = "hostily-captcha";

    public HostilyCaptchaThemeResources() {
        super(ID, HostilyCaptchaThemeResources.class.getClassLoader());
    }
}
