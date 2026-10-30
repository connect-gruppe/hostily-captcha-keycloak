package de.hostily.captcha.keycloak;

import de.hostily.captcha.Client;
import de.hostily.captcha.UnavailableException;
import de.hostily.captcha.VerifyResult;
import java.time.Duration;
import java.util.Map;
import org.jboss.logging.Logger;

/** The decision shared by the authenticator and the form action: let through, or reject with a message key. */
public final class HostilyCaptchaCheck {
    private static final Logger LOG = Logger.getLogger(HostilyCaptchaCheck.class);

    /** Message keys of the theme resources (messages_*.properties). */
    public static final String MSG_MISSING = "hostilyCaptchaMissing";
    public static final String MSG_FAILED = "hostilyCaptchaFailed";
    public static final String MSG_UNAVAILABLE = "hostilyCaptchaUnavailable";

    private HostilyCaptchaCheck() {
    }

    /** Result: {@code messageKey == null} means the request may pass. */
    public static final class Outcome {
        private final String messageKey;
        private final String note;

        private Outcome(String messageKey, String note) {
            this.messageKey = messageKey;
            this.note = note;
        }

        public boolean passes() {
            return messageKey == null;
        }

        public String getMessageKey() {
            return messageKey;
        }

        /** Short reason for the server log / event, never contains secret or token. */
        public String getNote() {
            return note;
        }
    }

    static final Outcome PASS = new Outcome(null, "ok");

    public static Outcome check(HostilyCaptchaConfig cfg, String token, String remoteIp, Map<String, ?> content) {
        if (!cfg.isConfigured()) {
            return PASS;
        }
        if (token == null || token.trim().isEmpty()) {
            return new Outcome(MSG_MISSING, "token_fehlt");
        }
        try {
            Client client = new Client(cfg.getSecret(), cfg.getServiceUrl(), Duration.ofSeconds(5));
            VerifyResult r = client.verify(token, remoteIp, content);
            if (r.isSuccess()) {
                return PASS;
            }
            if (r.isConfigurationError() || r.getErrorCodes().contains("bad-request")) {
                // wrong secret or unusable request: a configuration problem, treated like an outage
                return outage(cfg, "service refused the configuration: " + r.getErrorCodes());
            }
            return new Outcome(MSG_FAILED, r.getGruende().isEmpty() ? String.valueOf(r.getErrorCodes()) : String.valueOf(r.getGruende()));
        } catch (UnavailableException e) {
            return outage(cfg, e.getMessage());
        }
    }

    private static Outcome outage(HostilyCaptchaConfig cfg, String why) {
        LOG.errorf("hostily Captcha: %s (%s)", why, cfg.isBlockOnError() ? "request blocked" : "request let through");
        return cfg.isBlockOnError() ? new Outcome(MSG_UNAVAILABLE, "dienst_nicht_erreichbar") : PASS;
    }
}
