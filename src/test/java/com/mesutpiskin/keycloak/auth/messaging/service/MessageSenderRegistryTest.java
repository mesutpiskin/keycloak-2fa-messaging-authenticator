package com.mesutpiskin.keycloak.auth.messaging.service;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSenderRegistry Tests")
class MessageSenderRegistryTest {

    public static class FakeSender implements MessageSender {
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

    /** Fails on instantiation, standing in for a sender whose optional dependencies are absent. */
    public static class BrokenSender extends FakeSender {
        static { if (true) throw new RuntimeException("missing dependency"); }
        public BrokenSender() { super("broken", MessageChannel.SMS); }
    }

    public static class LoadableSender extends FakeSender {
        public LoadableSender() { super("loadable", MessageChannel.SMS); }
    }

    @Test @DisplayName("A sender that cannot be loaded is skipped, the rest stay registered")
    void unloadableSenderIsSkipped(@TempDir Path tmp) throws Exception {
        Path services = tmp.resolve("META-INF/services");
        Files.createDirectories(services);
        Files.writeString(services.resolve(MessageSender.class.getName()), String.join("\n",
                LoadableSender.class.getName(),
                // Absent class -> ServiceConfigurationError while the iterator advances
                "com.mesutpiskin.keycloak.auth.messaging.service.impl.DoesNotExistSender",
                // Present but unloadable -> LinkageError when it is instantiated
                BrokenSender.class.getName()) + "\n");

        URL only = tmp.toUri().toURL();
        ClassLoader isolated = new URLClassLoader(new URL[]{only}, getClass().getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                // Serve only this test's service file, not the one on the build classpath
                return name.equals("META-INF/services/" + MessageSender.class.getName())
                        ? findResources(name)
                        : super.getResources(name);
            }
        };

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(isolated);
        try {
            MessageSenderRegistry registry = MessageSenderRegistry.loadFromServiceLoader();
            assertEquals("loadable",
                    registry.getSender(MessageChannel.SMS, "loadable", Map.of()).getProviderId());
            assertThrows(IllegalStateException.class,
                    () -> registry.getSender(MessageChannel.SMS, "broken", Map.of()));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test @DisplayName("Every declared MessageSender has the public no-arg constructor ServiceLoader needs")
    void declaredSendersAreInstantiable() throws Exception {
        String resource = "META-INF/services/" + MessageSender.class.getName();
        try (var in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            List<String> names = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();

            assertFalse(names.isEmpty(), "no senders declared");
            for (String name : names) {
                Class<?> type = Class.forName(name);
                // This is the exact lookup ServiceLoader performs; it fails with
                // "Unable to get public no-arg constructor" when the constructor is missing.
                java.lang.reflect.Constructor<?> ctor = assertDoesNotThrow(
                        () -> type.getConstructor(), name + " needs a public no-arg constructor");
                assertTrue(java.lang.reflect.Modifier.isPublic(ctor.getModifiers()), name);
                assertTrue(MessageSender.class.isAssignableFrom(type), name);
            }
        }
    }
}
