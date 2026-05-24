package com.mesutpiskin.keycloak.auth.messaging;

import static java.util.Arrays.asList;
import static org.keycloak.provider.ProviderConfigProperty.LIST_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.ROLE_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.STRING_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

public class ConditionalMessagingAuthenticatorFormFactory extends MessagingAuthenticatorFormFactory {

    public static final String PROVIDER_ID = "messaging-conditional-authenticator";
    public static final ConditionalMessagingAuthenticatorForm SINGLETON = new ConditionalMessagingAuthenticatorForm();

    @Override public String getId() { return PROVIDER_ID; }
    @Override public String getDisplayType() { return "Conditional Messaging OTP"; }
    @Override public String getHelpText() { return "Conditional messaging OTP authenticator."; }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> list = new ArrayList<>(super.getConfigProperties());

        var ctrl = new ProviderConfigProperty(); ctrl.setType(STRING_TYPE);
        ctrl.setName(ConditionalMessagingAuthenticatorForm.OTP_CONTROL_USER_ATTRIBUTE);
        ctrl.setLabel("OTP Control User Attribute");
        ctrl.setHelpText("User attribute controlling OTP. Value 'force' always requires; 'skip' bypasses; anything else abstains.");
        list.add(ctrl);

        var skipRole = new ProviderConfigProperty(); skipRole.setType(ROLE_TYPE);
        skipRole.setName(ConditionalMessagingAuthenticatorForm.SKIP_OTP_ROLE);
        skipRole.setLabel("Skip OTP for Role");
        skipRole.setHelpText("OTP skipped if user has this role.");
        list.add(skipRole);

        var forceRole = new ProviderConfigProperty(); forceRole.setType(ROLE_TYPE);
        forceRole.setName(ConditionalMessagingAuthenticatorForm.FORCE_OTP_ROLE);
        forceRole.setLabel("Force OTP for Role");
        forceRole.setHelpText("OTP required if user has this role.");
        list.add(forceRole);

        var skipHdr = new ProviderConfigProperty(); skipHdr.setType(STRING_TYPE);
        skipHdr.setName(ConditionalMessagingAuthenticatorForm.SKIP_OTP_FOR_HTTP_HEADER);
        skipHdr.setLabel("Skip OTP for Header");
        skipHdr.setHelpText("Regex matched against 'Header: value' lines; if any matches, OTP is skipped.");
        skipHdr.setDefaultValue("");
        list.add(skipHdr);

        var forceHdr = new ProviderConfigProperty(); forceHdr.setType(STRING_TYPE);
        forceHdr.setName(ConditionalMessagingAuthenticatorForm.FORCE_OTP_FOR_HTTP_HEADER);
        forceHdr.setLabel("Force OTP for Header");
        forceHdr.setHelpText("Regex matched against 'Header: value' lines; if any matches, OTP is required.");
        forceHdr.setDefaultValue("");
        list.add(forceHdr);

        var def = new ProviderConfigProperty(); def.setType(LIST_TYPE);
        def.setName(ConditionalMessagingAuthenticatorForm.DEFAULT_OTP_OUTCOME);
        def.setLabel("Fallback OTP handling");
        def.setOptions(asList(ConditionalMessagingAuthenticatorForm.SKIP, ConditionalMessagingAuthenticatorForm.FORCE));
        def.setHelpText("Outcome when every other check abstains. Default behavior is to fall through to the main form.");
        list.add(def);

        return Collections.unmodifiableList(list);
    }

    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
}
