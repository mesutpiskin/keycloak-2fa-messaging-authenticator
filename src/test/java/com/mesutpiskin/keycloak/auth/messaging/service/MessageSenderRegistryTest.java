package com.mesutpiskin.keycloak.auth.messaging.service;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSenderRegistry Tests")
class MessageSenderRegistryTest {

    static class FakeSender implements MessageSender {
        private final String id;
        private final MessageChannel ch;
        Map<String,String> received;
        FakeSender(String id, MessageChannel ch) { this.id = id; this.ch = ch; }
        @Override public void send(OtpMessage m) {}
        @Override public String getProviderId() { return id; }
        @Override public MessageChannel getChannel() { return ch; }
        @Override public boolean isAvailable() { return true; }
        @Override public void configure(Map<String, String> config) { this.received = config; }
    }

    @Test @DisplayName("getSender returns matching provider for channel+providerId")
    void getMatch() {
        var registry = new MessageSenderRegistry(List.of(
                new FakeSender("twilio", MessageChannel.SMS),
                new FakeSender("telegram", MessageChannel.TELEGRAM)));
        MessageSender s = registry.getSender(MessageChannel.SMS, "twilio", Map.of("k","v"));
        assertEquals("twilio", s.getProviderId());
        assertEquals(Map.of("k","v"), ((FakeSender)s).received);
    }

    @Test @DisplayName("Unknown provider throws IllegalStateException")
    void unknownProvider() {
        var registry = new MessageSenderRegistry(List.of(new FakeSender("twilio", MessageChannel.SMS)));
        var ex = assertThrows(IllegalStateException.class,
                () -> registry.getSender(MessageChannel.SMS, "nope", Map.of()));
        assertTrue(ex.getMessage().contains("nope"));
        assertTrue(ex.getMessage().contains("SMS"));
    }

    @Test @DisplayName("Channel mismatch throws IllegalStateException")
    void channelMismatch() {
        var registry = new MessageSenderRegistry(List.of(new FakeSender("twilio", MessageChannel.SMS)));
        assertThrows(IllegalStateException.class,
                () -> registry.getSender(MessageChannel.TELEGRAM, "twilio", Map.of()));
    }

    @Test @DisplayName("loadFromServiceLoader returns the singleton registry")
    void serviceLoader() {
        // Acceptable to be empty in unit context — just assert the call works
        assertNotNull(MessageSenderRegistry.loadFromServiceLoader());
    }
}
