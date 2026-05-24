package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAuthenticatorCredentialProvider Tests")
class MessagingAuthenticatorCredentialProviderTest {

    private KeycloakSession session;
    private RealmModel realm;
    private UserModel user;
    private SubjectCredentialManager credentialManager;
    private MessagingAuthenticatorCredentialProvider provider;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
        credentialManager = mock(SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(credentialManager);
        when(realm.getAuthenticationFlowsStream()).thenReturn(Stream.empty());
        provider = new MessagingAuthenticatorCredentialProvider(session);
    }

    @Test @DisplayName("supportsCredentialType matches TYPE_ID only")
    void supportsType() {
        assertTrue(provider.supportsCredentialType(MessagingAuthenticatorCredentialModel.TYPE_ID));
        assertFalse(provider.supportsCredentialType("password"));
    }

    @Test @DisplayName("isConfiguredFor returns true when credential exists")
    void hasCredential() {
        when(credentialManager.getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.of(new CredentialModel()));
        assertTrue(provider.isConfiguredFor(realm, user,
                MessagingAuthenticatorCredentialModel.TYPE_ID));
    }

    @Test @DisplayName("isConfiguredFor returns false when wrong type")
    void wrongType() {
        assertFalse(provider.isConfiguredFor(realm, user, "password"));
    }

    @Test @DisplayName("getType returns TYPE_ID")
    void typeId() {
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, provider.getType());
    }
}
