package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAuthenticatorForm Tests")
class MessagingAuthenticatorFormTest {

    private AuthenticationFlowContext ctx;
    private AuthenticationSessionModel authSession;
    private UserModel user;
    private RealmModel realm;
    private KeycloakSession session;
    private LoginFormsProvider form;
    private AuthenticatorConfigModel configModel;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        ctx = mock(AuthenticationFlowContext.class);
        authSession = mock(AuthenticationSessionModel.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        session = mock(KeycloakSession.class);
        form = mock(LoginFormsProvider.class);
        configModel = mock(AuthenticatorConfigModel.class);
        httpRequest = mock(HttpRequest.class);

        when(ctx.getAuthenticationSession()).thenReturn(authSession);
        when(ctx.getUser()).thenReturn(user);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getSession()).thenReturn(session);
        when(ctx.form()).thenReturn(form);
        when(ctx.getAuthenticatorConfig()).thenReturn(configModel);
        when(ctx.getHttpRequest()).thenReturn(httpRequest);
        when(form.setExecution(anyString())).thenReturn(form);
        when(form.createForm(anyString())).thenReturn(mock(Response.class));
        when(httpRequest.getDecodedFormParameters()).thenReturn(new MultivaluedHashMap<>());

        var execution = mock(org.keycloak.models.AuthenticationExecutionModel.class);
        when(execution.getId()).thenReturn("exec-1");
        when(ctx.getExecution()).thenReturn(execution);
    }

    @Test @DisplayName("requiresUser=true")
    void requiresUser() { assertTrue(new MessagingAuthenticatorForm().requiresUser()); }

    @Test @DisplayName("authenticate triggers challenge when user has contact attribute")
    void authenticateHappyPath() {
        Map<String, String> config = new HashMap<>();
        config.put(MessagingConstants.MESSAGE_CHANNEL, MessageChannel.SMS.name());
        config.put(MessagingConstants.SMS_PROVIDER, "twilio");
        config.put(MessagingConstants.SIMULATION_MODE, "true"); // skip actual send
        when(configModel.getConfig()).thenReturn(config);
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+15551234567");
        when(authSession.getAuthNote(MessagingConstants.CODE)).thenReturn(null);

        new MessagingAuthenticatorForm().authenticate(ctx);

        verify(ctx).challenge(any(Response.class));
        verify(authSession).setAuthNote(eq(MessagingConstants.CODE), anyString());
    }

    @Test @DisplayName("authenticate triggers RequiredAction when contact attribute missing")
    void authenticateNoContact() {
        Map<String, String> config = new HashMap<>();
        config.put(MessagingConstants.MESSAGE_CHANNEL, MessageChannel.SMS.name());
        when(configModel.getConfig()).thenReturn(config);
        when(user.getFirstAttribute(anyString())).thenReturn(null);

        new MessagingAuthenticatorForm().authenticate(ctx);

        verify(user).addRequiredAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID);
    }

    @Test @DisplayName("action with empty code fails with MISSING_TOTP challenge")
    void actionEmptyCode() {
        Map<String, String> config = new HashMap<>();
        config.put(MessagingConstants.MESSAGE_CHANNEL, MessageChannel.SMS.name());
        when(configModel.getConfig()).thenReturn(config);
        when(user.isEnabled()).thenReturn(true);
        when(authSession.getAuthNote(MessagingConstants.CODE)).thenReturn(OtpHashUtils.hash("999999"));
        when(authSession.getAuthNote(MessagingConstants.CODE_TTL))
                .thenReturn(Long.toString(System.currentTimeMillis() + 60_000));
        // empty submission
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.putSingle(MessagingConstants.CODE, "");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        new MessagingAuthenticatorForm().action(ctx);

        verify(ctx, atLeastOnce()).challenge(any(Response.class));
    }
}
