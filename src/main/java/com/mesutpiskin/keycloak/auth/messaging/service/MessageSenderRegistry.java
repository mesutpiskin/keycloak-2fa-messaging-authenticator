package com.mesutpiskin.keycloak.auth.messaging.service;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class MessageSenderRegistry {

    private static final Logger logger = Logger.getLogger(MessageSenderRegistry.class);

    private final List<MessageSender> senders;

    public MessageSenderRegistry(List<MessageSender> senders) {
        this.senders = List.copyOf(senders);
    }

    /**
     * Loads every {@link MessageSender} on the classpath, skipping the ones that fail to load.
     * A sender whose optional dependencies are missing must not prevent the other senders from
     * being registered, so each entry is advanced and read defensively. Depending on the
     * classloader, a missing dependency surfaces either as a {@link LinkageError} raised while
     * the constructor is looked up, or as a {@link ServiceConfigurationError} wrapping it.
     */
    public static MessageSenderRegistry loadFromServiceLoader() {
        List<MessageSender> loaded = new ArrayList<>();
        Iterator<MessageSender> it = ServiceLoader.load(MessageSender.class).iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
                loaded.add(it.next());
            } catch (ServiceConfigurationError | LinkageError e) {
                logger.warnf(e, "Skipping MessageSender that could not be loaded; "
                        + "its dependencies are probably missing from the classpath");
            }
        }
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
