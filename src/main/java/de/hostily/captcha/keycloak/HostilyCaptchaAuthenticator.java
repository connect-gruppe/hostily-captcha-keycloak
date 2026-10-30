package de.hostily.captcha.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Step of the browser flow: shows a page with the hostily Captcha widget and lets the flow continue only when
 * the token is valid. Placed before "Username Password Form" the check happens before the user name and password are asked for.
 */
public class HostilyCaptchaAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(HostilyCaptchaAuthenticator.class);
    static final String TEMPLATE = "hostily-captcha.ftl";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        HostilyCaptchaConfig cfg = config(context);
        if (!cfg.isConfigured()) {
            LOG.debug("hostily Captcha: not configured, step skipped");
            context.success();
            return;
        }
        context.challenge(page(context, cfg, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        HostilyCaptchaConfig cfg = config(context);
        if (!cfg.isConfigured()) {
            context.success();
            return;
        }
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        String token = form.getFirst("hostily-captcha-token");
        if (token == null || token.isBlank()) {
            token = form.getFirst("g-recaptcha-response");
        }
        HostilyCaptchaCheck.Outcome o = HostilyCaptchaCheck.check(cfg, token, context.getConnection().getRemoteAddr(), null);
        if (o.passes()) {
            context.success();
            return;
        }
        LOG.warnf("hostily Captcha: login step rejected (%s)", o.getNote());
        context.getEvent().detail("hostily_captcha", o.getNote()).error("hostily_captcha_failed");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, page(context, cfg, o.getMessageKey()));
    }

    private static Response page(AuthenticationFlowContext context, HostilyCaptchaConfig cfg, String errorKey) {
        var form = context.form();
        form.setAttribute("hostilyCaptcha", HostilyCaptchaWidget.attributes(cfg, context.getSession()));
        if (errorKey != null) {
            form.setError(errorKey);
        }
        return form.createForm(TEMPLATE);
    }

    private static HostilyCaptchaConfig config(AuthenticationFlowContext context) {
        AuthenticatorConfigModel m = context.getAuthenticatorConfig();
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
