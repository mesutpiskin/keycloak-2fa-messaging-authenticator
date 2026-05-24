package com.mesutpiskin.keycloak.auth.messaging.model;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;

public enum MessageChannel {

    SMS("SMS", MessagingConstants.SMS_CONTACT_ATTRIBUTE, MessagingConstants.DEFAULT_PHONE_CONTACT_ATTRIBUTE),
    TELEGRAM("Telegram", MessagingConstants.TELEGRAM_CONTACT_ATTRIBUTE, MessagingConstants.DEFAULT_TELEGRAM_CONTACT_ATTRIBUTE),
    WHATSAPP("WhatsApp", MessagingConstants.WHATSAPP_CONTACT_ATTRIBUTE, MessagingConstants.DEFAULT_PHONE_CONTACT_ATTRIBUTE),
    SIGNAL("Signal", MessagingConstants.SIGNAL_CONTACT_ATTRIBUTE, MessagingConstants.DEFAULT_PHONE_CONTACT_ATTRIBUTE);

    private final String displayName;
    private final String contactAttributeConfigKey;
    private final String defaultAttributeName;

    MessageChannel(String displayName, String contactAttributeConfigKey, String defaultAttributeName) {
        this.displayName = displayName;
        this.contactAttributeConfigKey = contactAttributeConfigKey;
        this.defaultAttributeName = defaultAttributeName;
    }

    public String getDisplayName() { return displayName; }
    public String getContactAttributeConfigKey() { return contactAttributeConfigKey; }
    public String getDefaultAttributeName() { return defaultAttributeName; }

    public static MessageChannel fromString(String value) {
        if (value == null || value.trim().isEmpty()) return SMS;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return SMS; }
    }
}
