package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessagingAuthenticatorCredentialModel Tests")
class MessagingAuthenticatorCredentialModelTest {

    @Test @DisplayName("create() initializes type, createdDate, credentialData")
    void create() {
        var m = MessagingAuthenticatorCredentialModel.create();
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, m.getType());
        assertNotNull(m.getCreatedDate());
        assertNotNull(m.getCredentialData());
        assertTrue(m.getCredentialData().contains(MessagingAuthenticatorCredentialModel.TYPE_ID));
    }

    @Test @DisplayName("createFromCredentialModel copies fields and applies metadata")
    void fromCredential() {
        CredentialModel base = new CredentialModel();
        base.setId("cred-1");
        base.setUserLabel("Phone");
        var converted = MessagingAuthenticatorCredentialModel.createFromCredentialModel(base);
        assertEquals("cred-1", converted.getId());
        assertEquals("Phone", converted.getUserLabel());
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, converted.getType());
    }

    @Test @DisplayName("ensureMetadata returns true when it had to fill in defaults")
    void ensureMeta() {
        CredentialModel m = new CredentialModel();
        assertTrue(MessagingAuthenticatorCredentialModel.ensureMetadata(m));
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, m.getType());
    }

    @Test @DisplayName("ensureMetadata returns false on null model")
    void ensureMetaNull() {
        assertFalse(MessagingAuthenticatorCredentialModel.ensureMetadata(null));
    }
}
