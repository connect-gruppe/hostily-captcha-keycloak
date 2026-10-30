package de.hostily.captcha.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class HostilyCaptchaAuthenticatorFactory implements AuthenticatorFactory {
    public static final String ID = "hostily-captcha-authenticator";
    private static final Authenticator SINGLETON = new HostilyCaptchaAuthenticator();
    private static final Requirement[] REQUIREMENTS = {Requirement.REQUIRED, Requirement.DISABLED};

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "hostily Captcha";
    }

    @Override
    public String getReferenceCategory() {
        return "captcha";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENTS;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Shows the hostily Captcha widget and lets the flow continue only with a valid token. Place it before the username/password form.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return HostilyCaptchaConfig.properties();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
