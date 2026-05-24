package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessagingAuthenticatorFormFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "messaging-authenticator";
    public static final MessagingAuthenticatorForm SINGLETON = new MessagingAuthenticatorForm();

    @Override public String getId() { return PROVIDER_ID; }
    @Override public String getDisplayType() { return "Messaging OTP"; }
    @Override public String getReferenceCategory() { return MessagingAuthenticatorCredentialModel.TYPE_ID; }
    @Override public boolean isConfigurable() { return true; }
    @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return REQUIREMENT_CHOICES; }
    @Override public boolean isUserSetupAllowed() { return true; }
    @Override public String getHelpText() { return "Messaging OTP authenticator (SMS, Telegram, WhatsApp, Signal)."; }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> props = new ArrayList<>();

        props.add(listProp(MessagingConstants.MESSAGE_CHANNEL, "Message Channel",
                "Delivery channel: SMS, TELEGRAM, WHATSAPP, or SIGNAL.",
                MessageChannel.SMS.name(),
                Arrays.stream(MessageChannel.values()).map(Enum::name).toArray(String[]::new)));

        // SMS provider + secrets
        props.add(listProp(MessagingConstants.SMS_PROVIDER, "SMS Provider",
                "SMS gateway: twilio, aws-sns, or vonage. Only used when channel=SMS.",
                "twilio", new String[]{"twilio", "aws-sns", "vonage"}));

        // Twilio
        props.add(secret(MessagingConstants.TWILIO_ACCOUNT_SID, "Twilio Account SID", ""));
        props.add(secret(MessagingConstants.TWILIO_AUTH_TOKEN, "Twilio Auth Token", ""));
        props.add(str(MessagingConstants.TWILIO_FROM_NUMBER, "Twilio From Number", "E.164 phone number used as the sender."));

        // AWS SNS
        props.add(str(MessagingConstants.AWS_SNS_REGION, "AWS SNS Region", "AWS region code (e.g. us-east-1)."));
        props.add(str(MessagingConstants.AWS_ACCESS_KEY_ID, "AWS Access Key ID", "IAM access key ID."));
        props.add(secret(MessagingConstants.AWS_SECRET_ACCESS_KEY, "AWS Secret Access Key", ""));

        // Vonage
        props.add(str(MessagingConstants.VONAGE_API_KEY, "Vonage API Key", ""));
        props.add(secret(MessagingConstants.VONAGE_API_SECRET, "Vonage API Secret", ""));
        props.add(str(MessagingConstants.VONAGE_FROM_NUMBER, "Vonage From", "Sender ID or E.164 number."));

        // SMS contact attribute
        props.add(str(MessagingConstants.SMS_CONTACT_ATTRIBUTE, "SMS Contact User Attribute",
                "User attribute holding the E.164 phone number. Default: phoneNumber"));

        // Telegram
        props.add(secret(MessagingConstants.TELEGRAM_BOT_TOKEN, "Telegram Bot Token", ""));
        props.add(str(MessagingConstants.TELEGRAM_CONTACT_ATTRIBUTE, "Telegram Contact Attribute",
                "User attribute holding the Telegram chat ID. Default: telegramChatId"));

        // WhatsApp
        props.add(str(MessagingConstants.WHATSAPP_API_URL, "WhatsApp API URL",
                "Cloud API messages endpoint, e.g. https://graph.facebook.com/v18.0/<phone-id>/messages"));
        props.add(secret(MessagingConstants.WHATSAPP_API_TOKEN, "WhatsApp API Token", ""));
        props.add(str(MessagingConstants.WHATSAPP_FROM_NUMBER, "WhatsApp From Number", "Sender E.164 number."));
        props.add(str(MessagingConstants.WHATSAPP_CONTACT_ATTRIBUTE, "WhatsApp Contact Attribute",
                "User attribute holding the recipient phone number. Default: phoneNumber"));

        // Signal
        props.add(str(MessagingConstants.SIGNAL_CLI_REST_URL, "Signal CLI REST URL",
                "Base URL of signal-cli-rest-api (e.g. http://signal:8080)."));
        props.add(str(MessagingConstants.SIGNAL_FROM_NUMBER, "Signal From Number", "Sender E.164 number."));
        props.add(str(MessagingConstants.SIGNAL_CONTACT_ATTRIBUTE, "Signal Contact Attribute",
                "User attribute holding the recipient phone number. Default: phoneNumber"));

        // General OTP
        props.add(intProp(MessagingConstants.CODE_LENGTH, "Code Length",
                "Number of digits in the OTP.", MessagingConstants.DEFAULT_LENGTH));
        props.add(intProp(MessagingConstants.CODE_TTL_CONFIG, "Time-to-Live (seconds)",
                "How long the code is valid.", MessagingConstants.DEFAULT_TTL));
        props.add(intProp(MessagingConstants.RESEND_COOLDOWN, "Resend Cooldown (seconds)",
                "Minimum seconds between resend requests.", MessagingConstants.DEFAULT_RESEND_COOLDOWN));
        props.add(intProp(MessagingConstants.MAX_ATTEMPTS, "Max Code Attempts",
                "Maximum incorrect attempts before the code is invalidated.", MessagingConstants.DEFAULT_MAX_ATTEMPTS));
        props.add(bool(MessagingConstants.SIMULATION_MODE, "Simulation Mode (dev only)",
                "If enabled, the OTP is logged instead of sent.", MessagingConstants.DEFAULT_SIMULATION_MODE));
        props.add(bool(MessagingConstants.SKIP_SETUP, "Skip Setup",
                "When enabled, users with a populated contact attribute skip enrollment.",
                MessagingConstants.DEFAULT_SKIP_SETUP));

        return props;
    }

    private static ProviderConfigProperty listProp(String name, String label, String help, String defVal, String[] options) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.LIST_TYPE, defVal, options);
    }

    private static ProviderConfigProperty str(String name, String label, String help) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.STRING_TYPE, null);
    }

    private static ProviderConfigProperty secret(String name, String label, String help) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.PASSWORD, null);
    }

    private static ProviderConfigProperty intProp(String name, String label, String help, int defVal) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.STRING_TYPE, String.valueOf(defVal));
    }

    private static ProviderConfigProperty bool(String name, String label, String help, boolean defVal) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.BOOLEAN_TYPE, String.valueOf(defVal));
    }

    @Override public void close() {}
    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
}
