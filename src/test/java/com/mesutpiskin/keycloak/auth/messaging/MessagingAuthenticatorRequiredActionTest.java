package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.credential.CredentialModel;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.stream.Stream;

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
    private SubjectCredentialManager cm;

    @BeforeEach
    void setUp() {
        ctx = mock(RequiredActionContext.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        session = mock(AuthenticationSessionModel.class);
        keycloak = mock(KeycloakSession.class);
        form = mock(LoginFormsProvider.class);
        httpRequest = mock(HttpRequest.class);
        cm = mock(SubjectCredentialManager.class);
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
        when(realm.getAuthenticationFlowsStream()).thenAnswer(i -> Stream.empty());
    }

    @Test @DisplayName("requiredActionChallenge renders setup form")
    void challengeShowsSetup() {
        new MessagingAuthenticatorRequiredAction().requiredActionChallenge(ctx);
        verify(form).createForm("messaging-authenticator-setup-form.ftl");
        verify(ctx).challenge(any(Response.class));
    }

    private void useChannel(String channel) {
        AuthenticationFlowModel flow = new AuthenticationFlowModel();
        flow.setId("flow");
        AuthenticationExecutionModel exec = new AuthenticationExecutionModel();
        exec.setAuthenticator(MessagingAuthenticatorFormFactory.PROVIDER_ID);
        exec.setAuthenticatorConfig("cfg");
        AuthenticatorConfigModel cfg = new AuthenticatorConfigModel();
        cfg.setConfig(Map.of(MessagingConstants.MESSAGE_CHANNEL, channel));
        when(realm.getAuthenticationFlowsStream()).thenAnswer(i -> Stream.of(flow));
        when(realm.getAuthenticationExecutionsStream("flow")).thenAnswer(i -> Stream.of(exec));
        when(realm.getAuthenticatorConfigById("cfg")).thenReturn(cfg);
    }

    @Test @DisplayName("Setup form marks Telegram so the template shows the chat ID label")
    void challengeTelegramSetsIsTelegramTrue() {
        useChannel("TELEGRAM");
        new MessagingAuthenticatorRequiredAction().requiredActionChallenge(ctx);
        verify(form).setAttribute("isTelegram", true);
        verify(form).setAttribute("channelDisplayName", "Telegram");
    }

    @Test @DisplayName("Setup form for SMS sets isTelegram=false so the template shows the phone label")
    void challengeSmsSetsIsTelegramFalse() {
        useChannel("SMS");
        new MessagingAuthenticatorRequiredAction().requiredActionChallenge(ctx);
        verify(form).setAttribute("isTelegram", false);
    }

    @Test @DisplayName("Setup form re-rendered with an error keeps the channel attributes")
    void errorRerenderKeepsChannelAttributes() {
        useChannel("TELEGRAM");
        MultivaluedHashMap<String, String> data = new MultivaluedHashMap<>();
        data.putSingle("contactAddress", "not-a-chat-id");
        when(httpRequest.getDecodedFormParameters()).thenReturn(data);

        new MessagingAuthenticatorRequiredAction().processAction(ctx);

        verify(form).setError("messaging-authenticator-setup-invalid-format");
        verify(form).setAttribute("isTelegram", true);
        verify(form).setAttribute("channelDisplayName", "Telegram");
        verify(form).createForm("messaging-authenticator-setup-form.ftl");
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
        when(realm.getAuthenticationFlowsStream()).thenAnswer(i -> Stream.empty());

        new MessagingAuthenticatorRequiredAction().processAction(ctx);
        verify(form, atLeastOnce()).setError(anyString());
    }

    private static final String CONTACT = "+48500100200";
    private static final String LABEL = "SMS: " + CONTACT;

    private void submitValidCode() {
        MultivaluedHashMap<String, String> data = new MultivaluedHashMap<>();
        data.putSingle(MessagingConstants.CODE, "123456");
        when(httpRequest.getDecodedFormParameters()).thenReturn(data);
        when(session.getAuthNote(MessagingConstants.CODE)).thenReturn("123456");
        when(session.getAuthNote(MessagingConstants.CODE_TTL))
                .thenReturn(Long.toString(System.currentTimeMillis() + 60_000));
        when(session.getAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS)).thenReturn(CONTACT);
    }

    private static CredentialModel existingCredential(String id, String label) {
        CredentialModel c = MessagingAuthenticatorCredentialModel.create();
        c.setId(id);
        c.setUserLabel(label);
        return c;
    }

    @Test @DisplayName("Valid code with no existing credential creates one")
    void validCodeCreatesCredential() {
        submitValidCode();
        when(cm.getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.empty());

        new MessagingAuthenticatorRequiredAction().processAction(ctx);

        verify(cm).createStoredCredential(argThat(c -> LABEL.equals(c.getUserLabel())));
        verify(cm, never()).updateStoredCredential(any());
        verify(user).setSingleAttribute(anyString(), eq(CONTACT));
        verify(ctx).success();
    }

    @Test @DisplayName("Valid code with existing credential updates it instead of creating a duplicate")
    void validCodeUpdatesExistingCredential() {
        submitValidCode();
        CredentialModel existing = existingCredential("c1", "SMS: +48999999999");
        when(cm.getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.of(existing));

        new MessagingAuthenticatorRequiredAction().processAction(ctx);

        verify(cm, never()).createStoredCredential(any());
        verify(cm).updateStoredCredential(argThat(c -> "c1".equals(c.getId()) && LABEL.equals(c.getUserLabel())));
        verify(user).setSingleAttribute(anyString(), eq(CONTACT));
        verify(ctx).success();
    }

    @Test @DisplayName("Valid code removes extra messaging credentials before updating the first")
    void validCodeRemovesExtraCredentials() {
        submitValidCode();
        CredentialModel first = existingCredential("c1", "SMS: +48111111111");
        CredentialModel second = existingCredential("c2", LABEL);
        when(cm.getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.of(first, second));

        new MessagingAuthenticatorRequiredAction().processAction(ctx);

        var order = inOrder(cm);
        order.verify(cm).removeStoredCredentialById("c2");
        order.verify(cm).updateStoredCredential(argThat(c -> "c1".equals(c.getId()) && LABEL.equals(c.getUserLabel())));
        verify(cm, never()).removeStoredCredentialById("c1");
        verify(cm, never()).createStoredCredential(any());
        verify(ctx).success();
    }

    @Test @DisplayName("Duplicate credential error shows a form error instead of failing")
    void duplicateCredentialShowsFormError() {
        submitValidCode();
        when(cm.getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.empty());
        when(cm.createStoredCredential(any())).thenThrow(new ModelDuplicateException("Device already exists with the same name"));

        new MessagingAuthenticatorRequiredAction().processAction(ctx);

        verify(form).setError("messaging-authenticator-setup-already-configured");
        verify(form).createForm("messaging-authenticator-setup-form.ftl");
        verify(user, never()).setSingleAttribute(anyString(), anyString());
        verify(ctx, never()).success();
        verify(session).removeAuthNote(MessagingConstants.CODE);
    }
}
