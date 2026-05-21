package com.mesutpiskin.keycloak.auth.messaging.service;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;

import java.util.Map;

public interface MessageSender {
    void send(OtpMessage message) throws MessageDeliveryException;
    String getProviderId();
    MessageChannel getChannel();
    boolean isAvailable();

    /**
     * Configure this sender instance with admin-supplied credentials. Called by the registry
     * immediately after instantiation, before any send() call. Implementations should validate
     * required keys and throw IllegalArgumentException with a descriptive message if missing.
     */
    void configure(Map<String, String> config);
}
