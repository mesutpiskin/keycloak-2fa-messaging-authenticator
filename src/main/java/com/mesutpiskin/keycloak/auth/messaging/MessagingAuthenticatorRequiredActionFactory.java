package com.mesutpiskin.keycloak.auth.messaging;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class MessagingAuthenticatorRequiredActionFactory implements RequiredActionFactory {

    private static final MessagingAuthenticatorRequiredAction SINGLETON = new MessagingAuthenticatorRequiredAction();

    @Override public RequiredActionProvider create(KeycloakSession session) { return SINGLETON; }
    @Override public String getId() { return MessagingAuthenticatorRequiredAction.PROVIDER_ID; }
    @Override public String getDisplayText() { return "Set up Messaging Authenticator"; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
