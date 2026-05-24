package com.mesutpiskin.keycloak.auth.messaging;

import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.models.KeycloakSession;

public class MessagingAuthenticatorCredentialProviderFactory
        implements CredentialProviderFactory<MessagingAuthenticatorCredentialProvider> {

    public static final String PROVIDER_ID = MessagingAuthenticatorCredentialModel.TYPE_ID;

    @Override public MessagingAuthenticatorCredentialProvider create(KeycloakSession session) {
        return new MessagingAuthenticatorCredentialProvider(session);
    }

    @Override public String getId() { return PROVIDER_ID; }
}
