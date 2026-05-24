package com.mesutpiskin.keycloak.auth.messaging.service;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class MessageSenderRegistry {

    private final List<MessageSender> senders;

    public MessageSenderRegistry(List<MessageSender> senders) {
        this.senders = List.copyOf(senders);
    }

    public static MessageSenderRegistry loadFromServiceLoader() {
        List<MessageSender> loaded = new ArrayList<>();
        for (MessageSender s : ServiceLoader.load(MessageSender.class)) loaded.add(s);
        return new MessageSenderRegistry(loaded);
    }

    public MessageSender getSender(MessageChannel channel, String providerId, Map<String, String> config) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalStateException("No providerId configured for channel " + channel);
        }
        for (MessageSender s : senders) {
            if (s.getChannel() == channel && providerId.equalsIgnoreCase(s.getProviderId())) {
                s.configure(config == null ? Map.of() : config);
                return s;
            }
        }
        throw new IllegalStateException(
                "No MessageSender registered for channel=" + channel + " providerId=" + providerId
                        + ". Registered: " + describeRegistered());
    }

    private String describeRegistered() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < senders.size(); i++) {
            MessageSender s = senders.get(i);
            if (i > 0) sb.append(", ");
            sb.append(s.getChannel()).append(":").append(s.getProviderId());
        }
        return sb.append("]").toString();
    }
}
