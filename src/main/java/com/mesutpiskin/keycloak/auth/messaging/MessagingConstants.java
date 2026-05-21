package com.mesutpiskin.keycloak.auth.messaging;

public final class MessagingConstants {

    // Session-note keys
    public static final String CODE = "messagingCode";
    public static final String CODE_TTL = "messagingCodeTtl";
    public static final String CODE_RESEND_AVAILABLE_AFTER = "messagingCodeResendAvailableAfter";
    public static final String CODE_ATTEMPTS = "messagingCodeAttempts";
    public static final String SETUP_CONTACT_ADDRESS = "messagingSetupContactAddress";

    // General config keys
    public static final String MESSAGE_CHANNEL = "message.channel";
    public static final String SMS_PROVIDER = "sms.provider";
    public static final String CODE_LENGTH = "otp.length";
    public static final String CODE_TTL_CONFIG = "otp.ttl";
    public static final String RESEND_COOLDOWN = "otp.resend.cooldown";
    public static final String MAX_ATTEMPTS = "otp.max.attempts";
    public static final String SIMULATION_MODE = "simulation.mode";
    public static final String SKIP_SETUP = "skip.setup";

    // Twilio
    public static final String TWILIO_ACCOUNT_SID = "twilio.accountSid";
    public static final String TWILIO_AUTH_TOKEN = "twilio.authToken";
    public static final String TWILIO_FROM_NUMBER = "twilio.fromNumber";

    // AWS SNS
    public static final String AWS_SNS_REGION = "aws.region";
    public static final String AWS_ACCESS_KEY_ID = "aws.accessKeyId";
    public static final String AWS_SECRET_ACCESS_KEY = "aws.secretAccessKey";

    // Vonage
    public static final String VONAGE_API_KEY = "vonage.apiKey";
    public static final String VONAGE_API_SECRET = "vonage.apiSecret";
    public static final String VONAGE_FROM_NUMBER = "vonage.fromNumber";

    // Telegram
    public static final String TELEGRAM_BOT_TOKEN = "telegram.bot.token";
    public static final String TELEGRAM_CONTACT_ATTRIBUTE = "telegram.contact.attribute";
    public static final String DEFAULT_TELEGRAM_CONTACT_ATTRIBUTE = "telegramChatId";

    // WhatsApp
    public static final String WHATSAPP_API_URL = "whatsapp.api.url";
    public static final String WHATSAPP_API_TOKEN = "whatsapp.api.token";
    public static final String WHATSAPP_FROM_NUMBER = "whatsapp.from.number";
    public static final String WHATSAPP_CONTACT_ATTRIBUTE = "whatsapp.contact.attribute";

    // Signal
    public static final String SIGNAL_CLI_REST_URL = "signal.cli.rest.url";
    public static final String SIGNAL_FROM_NUMBER = "signal.from.number";
    public static final String SIGNAL_CONTACT_ATTRIBUTE = "signal.contact.attribute";

    // SMS-shared contact attribute
    public static final String SMS_CONTACT_ATTRIBUTE = "sms.contact.attribute";
    public static final String DEFAULT_PHONE_CONTACT_ATTRIBUTE = "phoneNumber";

    // Defaults
    public static final int DEFAULT_LENGTH = 6;
    public static final int DEFAULT_TTL = 300;
    public static final int DEFAULT_RESEND_COOLDOWN = 60;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final boolean DEFAULT_SIMULATION_MODE = false;
    public static final boolean DEFAULT_SKIP_SETUP = true;
    public static final long MILLIS_ROUNDING_OFFSET = 999L;

    private MessagingConstants() {
        throw new UnsupportedOperationException("MessagingConstants is a utility class and cannot be instantiated");
    }
}
