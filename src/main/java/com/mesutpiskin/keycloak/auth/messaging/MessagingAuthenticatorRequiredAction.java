package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSenderRegistry;
import org.jboss.logging.Logger;
import org.keycloak.authentication.CredentialRegistrator;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.security.MessageDigest;
import java.util.Map;

public class MessagingAuthenticatorRequiredAction implements RequiredActionProvider, CredentialRegistrator {

    public static final String PROVIDER_ID = "messaging-authenticator-setup";
    private static final String SETUP_TEMPLATE = "messaging-authenticator-setup-form.ftl";
    private static final String VERIFY_TEMPLATE = "messaging-authenticator-setup-verify-form.ftl";
    private static final Logger logger = Logger.getLogger(MessagingAuthenticatorRequiredAction.class);

    private enum CodeValidation { VALID, EXPIRED, INVALID, MISSING }

    @Override public InitiatedActionSupport initiatedActionSupport() { return InitiatedActionSupport.SUPPORTED; }
    @Override public void evaluateTriggers(RequiredActionContext context) {}

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        MessageChannel channel = channelFor(context);
        context.form().setAttribute("channelDisplayName", channel.getDisplayName());
        context.form().setAttribute("isTelegram", channel == MessageChannel.TELEGRAM);
        context.challenge(context.form().createForm(SETUP_TEMPLATE));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        UserModel user = context.getUser();
        AuthenticationSessionModel session = context.getAuthenticationSession();
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        MessageChannel channel = channelFor(context);

        if (form.containsKey("cancel") || form.containsKey("cancel-aia")) {
            if (session.getAuthNote(MessagingConstants.CODE) != null) {
                resetCode(session);
                requiredActionChallenge(context);
                return;
            }
            context.challenge(context.form()
                    .setError("messaging-authenticator-setup-cancelled")
                    .createForm(SETUP_TEMPLATE));
            return;
        }

        if (form.containsKey("resend")) {
            Long remaining = getRemaining(session);
            if (remaining != null && remaining > 0L) {
                challengeVerify(context, "messaging-authenticator-resend-cooldown", remaining);
                return;
            }
            resetCode(session);
            sendSetupCode(context, channel);
            return;
        }

        // Phase 1: receive contact address
        if (session.getAuthNote(MessagingConstants.CODE) == null) {
            String contact = form.getFirst("contactAddress");
            if (contact == null || contact.isBlank()) {
                context.challenge(context.form().setError("messaging-authenticator-setup-missing-contact").createForm(SETUP_TEMPLATE));
                return;
            }
            contact = E164Validator.normalize(contact);
            if (!isValidForChannel(channel, contact)) {
                context.challenge(context.form().setError("messaging-authenticator-setup-invalid-format").createForm(SETUP_TEMPLATE));
                return;
            }
            session.setAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS, contact);
            sendSetupCode(context, channel);
            return;
        }

        // Phase 2: verify submitted code
        String submittedRaw = form.getFirst(MessagingConstants.CODE);
        String submitted = submittedRaw == null ? null : submittedRaw.strip();
        switch (validate(session, submitted)) {
            case VALID:
                String contactAddr = session.getAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS);
                String attr = ContactResolver.attributeNameFor(channel, findConfig(context));
                user.setSingleAttribute(attr, contactAddr);
                MessagingAuthenticatorCredentialModel cred = MessagingAuthenticatorCredentialModel.create();
                cred.setUserLabel(channel.getDisplayName() + ": " + contactAddr);
                user.credentialManager().createStoredCredential(cred);
                resetCode(session);
                user.removeRequiredAction(PROVIDER_ID);
                context.success();
                return;
            case EXPIRED:
                resetCode(session);
                challengeVerify(context, "messaging-authenticator-setup-code-expired");
                return;
            case MISSING:
                challengeVerify(context, "messaging-authenticator-setup-code-missing");
                return;
            case INVALID:
                challengeVerify(context, "messaging-authenticator-setup-code-invalid");
                return;
        }
    }

    private boolean isValidForChannel(MessageChannel channel, String contact) {
        if (contact == null) return false;
        if (channel == MessageChannel.TELEGRAM) return contact.matches("^\\d{5,15}$");
        return E164Validator.isValid(contact);
    }

    private void sendSetupCode(RequiredActionContext context, MessageChannel channel) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        Map<String, String> cfg = findConfig(context);
        int length = positive(cfg.get(MessagingConstants.CODE_LENGTH), MessagingConstants.DEFAULT_LENGTH);
        int ttl = positive(cfg.get(MessagingConstants.CODE_TTL_CONFIG), MessagingConstants.DEFAULT_TTL);
        int cooldown = positive(cfg.get(MessagingConstants.RESEND_COOLDOWN), MessagingConstants.DEFAULT_RESEND_COOLDOWN);

        String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);
        String contact = session.getAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS);

        if (Boolean.parseBoolean(cfg.get(MessagingConstants.SIMULATION_MODE))) {
            logger.infof("***** SIMULATION MODE ***** Setup code for %s via %s: %s",
                    context.getUser().getUsername(), channel, code);
        } else {
            try {
                String providerId = resolveProviderId(channel, cfg);
                MessageSender sender = MessageSenderRegistry.loadFromServiceLoader().getSender(channel, providerId, cfg);
                sender.send(OtpMessage.builder().to(contact).code(code).ttlSeconds(ttl).build());
            } catch (MessageDeliveryException | RuntimeException | LinkageError e) {
                logger.errorf(e, "Setup OTP delivery failed for channel=%s", channel);
                context.challenge(context.form().setError("messaging-authenticator-setup-send-error").createForm(SETUP_TEMPLATE));
                return;
            }
        }

        long now = System.currentTimeMillis();
        session.setAuthNote(MessagingConstants.CODE, code);
        session.setAuthNote(MessagingConstants.CODE_TTL, Long.toString(now + (ttl * 1000L)));
        session.setAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER, Long.toString(now + (cooldown * 1000L)));

        challengeVerify(context, null);
    }

    private String resolveProviderId(MessageChannel channel, Map<String, String> cfg) {
        if (channel == MessageChannel.SMS) {
            String p = cfg.get(MessagingConstants.SMS_PROVIDER);
            return (p == null || p.isBlank()) ? "twilio" : p.trim().toLowerCase();
        }
        return channel.name().toLowerCase();
    }

    private CodeValidation validate(AuthenticationSessionModel session, String submitted) {
        String stored = session.getAuthNote(MessagingConstants.CODE);
        String ttlNote = session.getAuthNote(MessagingConstants.CODE_TTL);
        if (stored == null || ttlNote == null) return CodeValidation.MISSING;
        if (submitted == null || submitted.isEmpty()) return CodeValidation.MISSING;
        long exp;
        try { exp = Long.parseLong(ttlNote); } catch (NumberFormatException e) { return CodeValidation.EXPIRED; }
        if (exp < System.currentTimeMillis()) return CodeValidation.EXPIRED;
        return MessageDigest.isEqual(submitted.getBytes(), stored.getBytes())
                ? CodeValidation.VALID : CodeValidation.INVALID;
    }

    private MessageChannel channelFor(RequiredActionContext context) {
        Map<String, String> cfg = findConfig(context);
        return MessageChannel.fromString(cfg.get(MessagingConstants.MESSAGE_CHANNEL));
    }

    private Map<String, String> findConfig(RequiredActionContext context) {
        return context.getRealm().getAuthenticationFlowsStream()
                .flatMap(flow -> context.getRealm().getAuthenticationExecutionsStream(flow.getId()))
                .filter(exec -> MessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator())
                        || ConditionalMessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator()))
                .map(exec -> {
                    String id = exec.getAuthenticatorConfig();
                    if (id == null) return Map.<String, String>of();
                    AuthenticatorConfigModel cfg = context.getRealm().getAuthenticatorConfigById(id);
                    return cfg != null && cfg.getConfig() != null ? cfg.getConfig() : Map.<String, String>of();
                })
                .findFirst().orElse(Map.of());
    }

    private void challengeVerify(RequiredActionContext context, String error, Object... params) {
        var form = context.form();
        Long remaining = getRemaining(context.getAuthenticationSession());
        if (remaining != null && remaining > 0L) form.setAttribute("resendAvailableInSeconds", remaining);
        if (error != null) form.setError(error, params);
        context.challenge(form.createForm(VERIFY_TEMPLATE));
    }

    private void resetCode(AuthenticationSessionModel session) {
        session.removeAuthNote(MessagingConstants.CODE);
        session.removeAuthNote(MessagingConstants.CODE_TTL);
        session.removeAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        session.removeAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS);
    }

    private Long getRemaining(AuthenticationSessionModel session) {
        String raw = session.getAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        if (raw == null) return null;
        try {
            long at = Long.parseLong(raw);
            long ms = at - System.currentTimeMillis();
            return Math.max(0L, (ms + MessagingConstants.MILLIS_ROUNDING_OFFSET) / 1000L);
        } catch (NumberFormatException e) { return null; }
    }

    private int positive(String raw, int def) {
        if (raw == null || raw.isBlank()) return def;
        try { int v = Integer.parseInt(raw.trim()); return v > 0 ? v : def; }
        catch (NumberFormatException e) { return def; }
    }

    @Override public String getCredentialType(KeycloakSession session, AuthenticationSessionModel s) {
        return MessagingAuthenticatorCredentialModel.TYPE_ID;
    }
    @Override public void close() {}
}
