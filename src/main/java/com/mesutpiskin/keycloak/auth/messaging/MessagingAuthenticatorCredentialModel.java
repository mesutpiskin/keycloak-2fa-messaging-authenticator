package com.mesutpiskin.keycloak.auth.messaging;

import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;

public class MessagingAuthenticatorCredentialModel extends CredentialModel {

    public static final String TYPE_ID = "messaging-authenticator";
    private static final String DEFAULT_CREDENTIAL_DATA = "{\"type\":\"" + TYPE_ID + "\",\"version\":1}";

    public static MessagingAuthenticatorCredentialModel create() {
        MessagingAuthenticatorCredentialModel m = new MessagingAuthenticatorCredentialModel();
        ensureMetadata(m);
        return m;
    }

    public static MessagingAuthenticatorCredentialModel createFromCredentialModel(CredentialModel model) {
        MessagingAuthenticatorCredentialModel out = new MessagingAuthenticatorCredentialModel();
        out.setId(model.getId());
        out.setType(model.getType());
        out.setCreatedDate(model.getCreatedDate());
        out.setUserLabel(model.getUserLabel());
        out.setCredentialData(model.getCredentialData());
        out.setSecretData(model.getSecretData());
        ensureMetadata(out);
        return out;
    }

    public static boolean ensureMetadata(CredentialModel model) {
        if (model == null) return false;
        boolean updated = false;
        if (!TYPE_ID.equals(model.getType())) { model.setType(TYPE_ID); updated = true; }
        if (model.getCreatedDate() == null) { model.setCreatedDate(Time.currentTimeMillis()); updated = true; }
        if (isBlank(model.getCredentialData())) { model.setCredentialData(DEFAULT_CREDENTIAL_DATA); updated = true; }
        return updated;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
