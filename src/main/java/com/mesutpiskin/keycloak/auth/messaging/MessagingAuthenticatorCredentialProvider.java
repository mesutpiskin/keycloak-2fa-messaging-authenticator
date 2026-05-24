package com.mesutpiskin.keycloak.auth.messaging;

import org.jboss.logging.Logger;
import org.keycloak.credential.*;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class MessagingAuthenticatorCredentialProvider
        implements CredentialProvider<MessagingAuthenticatorCredentialModel>, CredentialInputValidator {

    private static final Logger logger = Logger.getLogger(MessagingAuthenticatorCredentialProvider.class);

    private final KeycloakSession session;

    public MessagingAuthenticatorCredentialProvider(KeycloakSession session) { this.session = session; }

    @Override public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) { return false; }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return getType().equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        if (!supportsCredentialType(credentialType)) return false;
        if (user.credentialManager()
                .getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID)
                .findAny().isPresent()) {
            return true;
        }
        return isSkipSetupEnabled(realm) && hasContactAddress(user, realm);
    }

    private boolean isSkipSetupEnabled(RealmModel realm) {
        return realm.getAuthenticationFlowsStream()
                .flatMap(flow -> realm.getAuthenticationExecutionsStream(flow.getId()))
                .filter(exec -> MessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator())
                        || ConditionalMessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator()))
                .map(exec -> {
                    String configId = exec.getAuthenticatorConfig();
                    if (configId == null) return MessagingConstants.DEFAULT_SKIP_SETUP;
                    AuthenticatorConfigModel cfg = realm.getAuthenticatorConfigById(configId);
                    if (cfg == null || cfg.getConfig() == null) return MessagingConstants.DEFAULT_SKIP_SETUP;
                    return Boolean.parseBoolean(cfg.getConfig().getOrDefault(
                            MessagingConstants.SKIP_SETUP, String.valueOf(MessagingConstants.DEFAULT_SKIP_SETUP)));
                })
                .reduce(false, (a, b) -> a || b);
    }

    private boolean hasContactAddress(UserModel user, RealmModel realm) {
        // We can't know the configured channel here without the execution config — fall back to "any
        // non-blank phoneNumber/telegramChatId attribute is enough to consider the user configured".
        String phone = user.getFirstAttribute(MessagingConstants.DEFAULT_PHONE_CONTACT_ATTRIBUTE);
        String tg = user.getFirstAttribute(MessagingConstants.DEFAULT_TELEGRAM_CONTACT_ATTRIBUTE);
        return (phone != null && !phone.isBlank()) || (tg != null && !tg.isBlank());
    }

    @Override
    public CredentialModel createCredential(RealmModel realm, UserModel user,
                                            MessagingAuthenticatorCredentialModel credentialModel) {
        if (MessagingAuthenticatorCredentialModel.ensureMetadata(credentialModel)) {
            logger.debugf("Initialized messaging authenticator credential metadata for user %s", user.getId());
        }
        if (credentialModel.getUserLabel() == null || credentialModel.getUserLabel().isBlank()) {
            credentialModel.setUserLabel("Messaging OTP");
        }
        return user.credentialManager().createStoredCredential(credentialModel);
    }

    @Override
    public boolean deleteCredential(RealmModel realm, UserModel user, String credentialId) {
        return user.credentialManager().removeStoredCredentialById(credentialId);
    }

    @Override
    public MessagingAuthenticatorCredentialModel getCredentialFromModel(CredentialModel model) {
        return MessagingAuthenticatorCredentialModel.createFromCredentialModel(model);
    }

    @Override
    public CredentialTypeMetadata getCredentialTypeMetadata(CredentialTypeMetadataContext context) {
        return CredentialTypeMetadata.builder()
                .type(getType())
                .category(CredentialTypeMetadata.Category.TWO_FACTOR)
                .displayName("messaging-authenticator-display-name")
                .helpText("messaging-authenticator-help-text")
                .iconCssClass("kcAuthenticatorMessagingClass")
                .createAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID)
                .removeable(true)
                .build(session);
    }

    @Override public String getType() { return MessagingAuthenticatorCredentialModel.TYPE_ID; }
}
