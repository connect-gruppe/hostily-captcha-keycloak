package de.hostily.captcha.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class HostilyCaptchaFormActionFactory implements FormActionFactory {
    public static final String ID = "registration-hostily-captcha-action";
    private static final FormAction SINGLETON = new HostilyCaptchaFormAction();
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
        return "Adds the hostily Captcha widget to the registration form and checks the token before the user is created.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return HostilyCaptchaConfig.properties();
    }

    @Override
    public FormAction create(KeycloakSession session) {
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
