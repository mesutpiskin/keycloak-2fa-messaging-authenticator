package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAuthenticatorRequiredAction Tests")
class MessagingAuthenticatorRequiredActionTest {

    private RequiredActionContext ctx;
    private UserModel user;
    private RealmModel realm;
    private AuthenticationSessionModel session;
    private KeycloakSession keycloak;
    private LoginFormsProvider form;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        ctx = mock(RequiredActionContext.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        session = mock(AuthenticationSessionModel.class);
        keycloak = mock(KeycloakSession.class);
        form = mock(LoginFormsProvider.class);
        httpRequest = mock(HttpRequest.class);
        SubjectCredentialManager cm = mock(SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(cm);
        when(ctx.getUser()).thenReturn(user);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getAuthenticationSession()).thenReturn(session);
        when(ctx.getSession()).thenReturn(keycloak);
        when(ctx.form()).thenReturn(form);
        when(ctx.getHttpRequest()).thenReturn(httpRequest);
        when(form.setAttribute(anyString(), any())).thenReturn(form);
        when(form.setError(anyString())).thenReturn(form);
        when(form.setError(anyString(), any())).thenReturn(form);
        when(form.createForm(anyString())).thenReturn(mock(Response.class));
        when(realm.getAuthenticationFlowsStream()).thenReturn(Stream.empty());
    }

    @Test @DisplayName("requiredActionChallenge renders setup form")
    void challengeShowsSetup() {
        new MessagingAuthenticatorRequiredAction().requiredActionChallenge(ctx);
        verify(form).createForm("messaging-authenticator-setup-form.ftl");
        verify(ctx).challenge(any(Response.class));
    }

    @Test @DisplayName("cancel goes back to setup form when no code in flight")
    void cancelEarly() {
        MultivaluedHashMap<String, String> data = new MultivaluedHashMap<>();
        data.putSingle("cancel", "x");
        when(httpRequest.getDecodedFormParameters()).thenReturn(data);
        when(session.getAuthNote(MessagingConstants.CODE)).thenReturn(null);

        new MessagingAuthenticatorRequiredAction().processAction(ctx);
        verify(form, atLeastOnce()).createForm(anyString());
    }

    @Test @DisplayName("Submit invalid phone format renders error")
    void invalidPhone() {
        MultivaluedHashMap<String, String> data = new MultivaluedHashMap<>();
        data.putSingle("contactAddress", "not-a-phone");
        when(httpRequest.getDecodedFormParameters()).thenReturn(data);
        when(session.getAuthNote(MessagingConstants.CODE)).thenReturn(null);
        when(realm.getAuthenticationFlowsStream()).thenReturn(Stream.empty());

        new MessagingAuthenticatorRequiredAction().processAction(ctx);
        verify(form, atLeastOnce()).setError(anyString());
    }
}
