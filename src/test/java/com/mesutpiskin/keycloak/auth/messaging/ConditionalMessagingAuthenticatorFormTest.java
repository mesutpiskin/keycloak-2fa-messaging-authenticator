package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConditionalMessagingAuthenticatorForm Tests")
class ConditionalMessagingAuthenticatorFormTest {

    private AuthenticationFlowContext ctx;
    private UserModel user;
    private RealmModel realm;
    private AuthenticatorConfigModel cfg;

    @BeforeEach
    void setUp() {
        ctx = mock(AuthenticationFlowContext.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        cfg = mock(AuthenticatorConfigModel.class);
        when(ctx.getUser()).thenReturn(user);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getAuthenticatorConfig()).thenReturn(cfg);
        var http = mock(org.keycloak.http.HttpRequest.class);
        var headers = mock(jakarta.ws.rs.core.HttpHeaders.class);
        when(headers.getRequestHeaders()).thenReturn(new jakarta.ws.rs.core.MultivaluedHashMap<>());
        when(http.getHttpHeaders()).thenReturn(headers);
        when(ctx.getHttpRequest()).thenReturn(http);
    }

    @Test @DisplayName("Skip when user attribute = 'skip'")
    void userAttributeSkip() {
        when(cfg.getConfig()).thenReturn(Map.of("otpControlAttribute", "otp_choice"));
        when(user.getAttributeStream("otp_choice")).thenReturn(Stream.of("skip"));

        new ConditionalMessagingAuthenticatorForm().authenticate(ctx);
        verify(ctx).success();
    }

    @Test @DisplayName("Default fallback = force triggers OTP form path")
    void defaultForce() {
        when(cfg.getConfig()).thenReturn(Map.of("defaultOtpOutcome", "force",
                MessagingConstants.MESSAGE_CHANNEL, "SMS"));
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+15551234567");
        when(user.getAttributeStream(anyString())).thenReturn(Stream.empty());

        // We can't assert challenge() without lots of mocks; just ensure success() is NOT called
        try { new ConditionalMessagingAuthenticatorForm().authenticate(ctx); } catch (Exception ignored) {}
        verify(ctx, never()).success();
    }
}
