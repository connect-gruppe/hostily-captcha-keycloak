package de.hostily.captcha.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

/** Self-registration: puts the widget into the registration form and checks the token before the user is created. */
public class HostilyCaptchaFormAction implements FormAction {
    private static final Logger LOG = Logger.getLogger(HostilyCaptchaFormAction.class);

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        HostilyCaptchaConfig cfg = config(context.getAuthenticatorConfig());
        if (cfg.isConfigured()) {
            form.addScript(HostilyCaptchaWidget.registrationScript(cfg, context.getSession()));
        }
    }

    @Override
    public void validate(ValidationContext context) {
        HostilyCaptchaConfig cfg = config(context.getAuthenticatorConfig());
        if (!cfg.isConfigured()) {
            context.success();
            return;
        }
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String token = formData.getFirst("hostily-captcha-token");
        if (token == null || token.isBlank()) {
            token = formData.getFirst("g-recaptcha-response");
        }
        Map<String, Object> content = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : formData.entrySet()) {
            content.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
        HostilyCaptchaCheck.Outcome o = HostilyCaptchaCheck.check(cfg, token, context.getConnection().getRemoteAddr(),
            de.hostily.captcha.FormContent.formContent(content));
        if (o.passes()) {
            context.success();
            return;
        }
        LOG.warnf("hostily Captcha: registration rejected (%s)", o.getNote());
        List<FormMessage> errors = new ArrayList<>();
        errors.add(new FormMessage(null, o.getMessageKey()));
        formData.remove("hostily-captcha-token");
        formData.remove("g-recaptcha-response");
        context.getEvent().detail("hostily_captcha", o.getNote());
        context.error(Errors.INVALID_REGISTRATION);
        context.validationError(formData, errors);
    }

    @Override
    public void success(FormContext context) {
    }

    private static HostilyCaptchaConfig config(AuthenticatorConfigModel m) {
        return new HostilyCaptchaConfig(m == null ? Map.of() : m.getConfig());
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
