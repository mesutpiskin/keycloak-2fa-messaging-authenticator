package com.mesutpiskin.keycloak.auth.messaging;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.Config;
public class MessagingAuthenticatorRequiredActionFactory implements RequiredActionFactory {
    @Override public RequiredActionProvider create(KeycloakSession s) { return null; }
    @Override public String getId() { return MessagingAuthenticatorRequiredAction.PROVIDER_ID; }
    @Override public String getDisplayText() { return ""; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
