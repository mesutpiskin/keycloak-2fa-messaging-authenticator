package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSenderRegistry;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.CredentialValidator;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MessagingAuthenticatorForm extends AbstractUsernameFormAuthenticator
        implements CredentialValidator<MessagingAuthenticatorCredentialProvider> {

    protected static final Logger logger = Logger.getLogger(MessagingAuthenticatorForm.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Map<String, String> configValues = configValues(context.getAuthenticatorConfig());
        MessageChannel channel = MessageChannel.fromString(configValues.get(MessagingConstants.MESSAGE_CHANNEL));

        String contact = ContactResolver.resolve(context.getUser(), channel, configValues);
        if (contact == null) {
            context.getUser().addRequiredAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID);
            context.attempted();
            return;
        }
        context.challenge(challenge(context, null));
    }

    @Override
    protected Response challenge(AuthenticationFlowContext context, String error, String field) {
        generateAndSend(context);
        LoginFormsProvider form = prepareForm(context, null);
        applyFormMessage(form, error, field);
        return form.createForm("messaging-code-form.ftl");
    }

    private void generateAndSend(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        if (session.getAuthNote(MessagingConstants.CODE) != null) return;

        Map<String, String> config = configValues(context.getAuthenticatorConfig());
        int length = resolvePositiveInt(config, MessagingConstants.CODE_LENGTH, MessagingConstants.DEFAULT_LENGTH);
        int ttl = resolvePositiveInt(config, MessagingConstants.CODE_TTL_CONFIG, MessagingConstants.DEFAULT_TTL);
        int cooldown = resolvePositiveInt(config, MessagingConstants.RESEND_COOLDOWN, MessagingConstants.DEFAULT_RESEND_COOLDOWN);

        String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);
        MessageChannel channel = MessageChannel.fromString(config.get(MessagingConstants.MESSAGE_CHANNEL));
        String providerId = resolveProviderId(channel, config);
        String contact = ContactResolver.resolve(context.getUser(), channel, config);

        if (Boolean.parseBoolean(config.get(MessagingConstants.SIMULATION_MODE))) {
            logger.infof("***** SIMULATION MODE ***** code for user %s on %s via %s: %s",
                    context.getUser().getUsername(), channel, providerId, code);
        } else {
            try {
                MessageSender sender = MessageSenderRegistry.loadFromServiceLoader()
                        .getSender(channel, providerId, config);
                sender.send(OtpMessage.builder().to(contact).code(code).ttlSeconds(ttl).build());
            } catch (MessageDeliveryException | RuntimeException | LinkageError e) {
                // A misconfigured or unloadable provider must not surface as an uncaught
                // server error; the user still gets the code form and can retry or resend.
                logger.errorf(e, "Failed to deliver OTP via channel=%s provider=%s", channel, providerId);
            }
        }

        session.setAuthNote(MessagingConstants.CODE, OtpHashUtils.hash(code));
        long now = System.currentTimeMillis();
        session.setAuthNote(MessagingConstants.CODE_TTL, Long.toString(now + (ttl * 1000L)));
        session.setAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER, Long.toString(now + (cooldown * 1000L)));
    }

    private String resolveProviderId(MessageChannel channel, Map<String, String> config) {
        if (channel == MessageChannel.SMS) {
            String p = config.get(MessagingConstants.SMS_PROVIDER);
            return (p == null || p.isBlank()) ? "twilio" : p.trim().toLowerCase();
        }
        return channel.name().toLowerCase(); // telegram / whatsapp / signal
    }

    private Map<String, String> configValues(AuthenticatorConfigModel cfg) {
        return cfg != null && cfg.getConfig() != null ? cfg.getConfig() : Map.of();
    }

    private int resolvePositiveInt(Map<String, String> v, String k, int d) {
        String raw = v.get(k);
        if (raw == null || raw.isBlank()) return d;
        try {
            int p = Integer.parseInt(raw.trim());
            if (p <= 0) return d;
            return p;
        } catch (NumberFormatException e) { return d; }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (!enabledUser(context, user)) return;

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData.containsKey("resend")) {
            handleResend(context);
            return;
        }
        if (formData.containsKey("cancel")) {
            resetCode(context.getAuthenticationSession());
            context.resetFlow();
            return;
        }

        AuthenticationSessionModel session = context.getAuthenticationSession();
        String storedHash = session.getAuthNote(MessagingConstants.CODE);
        String ttlNote = session.getAuthNote(MessagingConstants.CODE_TTL);
        String submittedRaw = formData.getFirst(MessagingConstants.CODE);
        String submitted = submittedRaw == null ? null : submittedRaw.strip();

        if (storedHash == null || ttlNote == null) {
            context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, Messages.INVALID_ACCESS_CODE, MessagingConstants.CODE));
            return;
        }
        if (submitted == null || submitted.isEmpty()) {
            context.challenge(challenge(context, Messages.MISSING_TOTP, MessagingConstants.CODE));
            return;
        }
        long expiresAt;
        try { expiresAt = Long.parseLong(ttlNote); }
        catch (NumberFormatException e) { expiresAt = 0L; }
        if (expiresAt < System.currentTimeMillis()) {
            context.getEvent().user(user).error(Errors.EXPIRED_CODE);
            context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                    challenge(context, Messages.EXPIRED_ACTION_TOKEN_SESSION_EXISTS, MessagingConstants.CODE));
            return;
        }

        if (OtpHashUtils.matches(submitted, storedHash)) {
            resetCode(session);
            context.success();
            return;
        }

        context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
        int attempts = incrementAttempts(session);
        int max = resolvePositiveInt(configValues(context.getAuthenticatorConfig()),
                MessagingConstants.MAX_ATTEMPTS, MessagingConstants.DEFAULT_MAX_ATTEMPTS);
        if (attempts >= max) {
            resetCode(session);
            LoginFormsProvider form = prepareForm(context, null);
            form.setAttribute("maxAttemptsReached", true);
            applyFormMessage(form, "messaging-authenticator-too-many-attempts", MessagingConstants.CODE);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    form.createForm("messaging-code-form.ftl"));
        } else {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, Messages.INVALID_ACCESS_CODE, MessagingConstants.CODE));
        }
    }

    private void handleResend(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        Long remaining = getRemainingSeconds(session);
        if (remaining != null && remaining > 0L) {
            LoginFormsProvider form = prepareForm(context, remaining);
            applyFormMessage(form, "messaging-authenticator-resend-cooldown", null, remaining);
            context.challenge(form.createForm("messaging-code-form.ftl"));
            return;
        }
        resetCode(session);
        context.challenge(challenge(context, null));
    }

    private LoginFormsProvider prepareForm(AuthenticationFlowContext context, Long remainingSeconds) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        Long expose = remainingSeconds != null ? remainingSeconds : getRemainingSeconds(session);
        if (expose != null && expose > 0L) form.setAttribute("resendAvailableInSeconds", expose);

        Map<String, String> config = configValues(context.getAuthenticatorConfig());
        form.setAttribute("codeLength",
                resolvePositiveInt(config, MessagingConstants.CODE_LENGTH, MessagingConstants.DEFAULT_LENGTH));
        MessageChannel channel = MessageChannel.fromString(config.get(MessagingConstants.MESSAGE_CHANNEL));
        form.setAttribute("channelDisplayName", channel.getDisplayName());
        String contact = ContactResolver.resolve(context.getUser(), channel, config);
        form.setAttribute("contactMasked", ContactMasker.maskPhone(contact));
        return form;
    }

    private Long getRemainingSeconds(AuthenticationSessionModel session) {
        String raw = session.getAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        if (raw == null) return null;
        try {
            long at = Long.parseLong(raw);
            long ms = at - System.currentTimeMillis();
            return Math.max(0L, (ms + MessagingConstants.MILLIS_ROUNDING_OFFSET) / 1000L);
        } catch (NumberFormatException e) { return null; }
    }

    private void applyFormMessage(LoginFormsProvider form, String key, String field, Object... params) {
        if (key == null) return;
        if (field != null) form.addError(new FormMessage(field, key, params));
        else form.setError(key, params);
    }

    private void resetCode(AuthenticationSessionModel session) {
        session.removeAuthNote(MessagingConstants.CODE);
        session.removeAuthNote(MessagingConstants.CODE_TTL);
        session.removeAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        session.removeAuthNote(MessagingConstants.CODE_ATTEMPTS);
    }

    private int incrementAttempts(AuthenticationSessionModel session) {
        String raw = session.getAuthNote(MessagingConstants.CODE_ATTEMPTS);
        int a = 1;
        if (raw != null) { try { a = Integer.parseInt(raw) + 1; } catch (NumberFormatException ignored) {} }
        session.setAuthNote(MessagingConstants.CODE_ATTEMPTS, Integer.toString(a));
        return a;
    }

    @Override public boolean requiresUser() { return true; }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        MessagingAuthenticatorCredentialProvider p = getCredentialProvider(session);
        return p != null && p.isConfiguredFor(realm, user, getType(session));
    }

    @Override
    public MessagingAuthenticatorCredentialProvider getCredentialProvider(KeycloakSession session) {
        return (MessagingAuthenticatorCredentialProvider) session.getProvider(
                CredentialProvider.class, MessagingAuthenticatorCredentialProviderFactory.PROVIDER_ID);
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        user.addRequiredAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID);
    }

    @Override
    public List<RequiredActionFactory> getRequiredActions(KeycloakSession session) {
        return Collections.singletonList((MessagingAuthenticatorRequiredActionFactory)
                session.getKeycloakSessionFactory().getProviderFactory(
                        RequiredActionProvider.class, MessagingAuthenticatorRequiredAction.PROVIDER_ID));
    }

    @Override public void close() {}

    @Override protected String disabledByBruteForceError(String username) { return Messages.INVALID_ACCESS_CODE; }
}
