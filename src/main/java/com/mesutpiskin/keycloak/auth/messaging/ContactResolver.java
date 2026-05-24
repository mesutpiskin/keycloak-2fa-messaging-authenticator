package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.keycloak.models.UserModel;

import java.util.Map;

public final class ContactResolver {

    private ContactResolver() {
        throw new UnsupportedOperationException("ContactResolver is a utility class and cannot be instantiated");
    }

    public static String attributeNameFor(MessageChannel channel, Map<String, String> config) {
        if (config == null) config = Map.of();
        String override = config.get(channel.getContactAttributeConfigKey());
        if (override != null && !override.isBlank()) return override.trim();
        return channel.getDefaultAttributeName();
    }

    public static String resolve(UserModel user, MessageChannel channel, Map<String, String> config) {
        if (user == null || channel == null) return null;
        String attr = attributeNameFor(channel, config);
        String value = user.getFirstAttribute(attr);
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
