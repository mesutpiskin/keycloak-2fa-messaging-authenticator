package com.mesutpiskin.keycloak.auth.messaging.packaging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inspects the artifact produced by the build, so a packaging regression — a thin jar
 * without the AWS SDK — fails the build instead of reaching a release.
 *
 * <p>Runs against {@code ${project.build.finalName}.jar}, whose path failsafe passes in as the
 * {@code shaded.artifact} system property.
 */
@DisplayName("Packaged artifact")
class PackagingIT {

    private static final String SERVICES_ENTRY =
            "META-INF/services/com.mesutpiskin.keycloak.auth.messaging.service.MessageSender";

    private static Path artifact;

    @BeforeAll
    static void locateArtifact() {
        String path = System.getProperty("shaded.artifact");
        assertNotNull(path, "shaded.artifact system property is not set");
        artifact = Path.of(path);
        assertTrue(Files.isRegularFile(artifact), "artifact was not built: " + artifact);
    }

    private static boolean contains(String entry) throws IOException {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            return jar.getJarEntry(entry) != null;
        }
    }

    private static String read(String entry) throws IOException {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            JarEntry e = jar.getJarEntry(entry);
            assertNotNull(e, entry + " is missing from the artifact");
            try (InputStream in = jar.getInputStream(e)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Test
    @DisplayName("contains this extension's own classes")
    void containsExtensionClasses() throws IOException {
        assertTrue(contains("com/mesutpiskin/keycloak/auth/messaging/service/impl/AwsSnsMessageSender.class"));
    }

    @Test
    @DisplayName("contains the AWS SNS runtime classes AwsSnsMessageSender needs")
    void containsAwsRuntime() throws IOException {
        // Exactly the types AwsSnsMessageSender references; asserting more would make this
        // brittle against incidental changes in the AWS dependency tree.
        assertTrue(contains("software/amazon/awssdk/services/sns/SnsClient.class"),
                "SnsClient is missing: the artifact is a thin jar again");
        assertTrue(contains("software/amazon/awssdk/regions/Region.class"),
                "Region is missing: AWS SDK runtime dependencies are not packaged");
    }

    @Test
    @DisplayName("keeps the merged MessageSender service descriptor")
    void keepsServiceDescriptor() throws IOException {
        String descriptor = read(SERVICES_ENTRY);
        assertTrue(descriptor.contains(
                        "com.mesutpiskin.keycloak.auth.messaging.service.impl.AwsSnsMessageSender"),
                "AwsSnsMessageSender is not registered:\n" + descriptor);
        // The other senders must survive the shade merge too.
        for (String sender : new String[]{"Twilio", "Vonage", "Telegram", "WhatsApp", "Signal"}) {
            assertTrue(descriptor.contains(sender + "MessageSender"),
                    sender + " registration was lost:\n" + descriptor);
        }
    }

    @Test
    @DisplayName("keeps the Keycloak SPI descriptors")
    void keepsKeycloakDescriptors() throws IOException {
        assertTrue(contains("META-INF/services/org.keycloak.authentication.AuthenticatorFactory"));
        assertTrue(contains("META-INF/services/org.keycloak.authentication.RequiredActionFactory"));
        assertTrue(contains("META-INF/services/org.keycloak.credential.CredentialProviderFactory"));
    }

    @Test
    @DisplayName("does not bundle Keycloak, which the server provides")
    void excludesKeycloak() throws IOException {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var bundled = jar.stream()
                    .map(JarEntry::getName)
                    .filter(n -> n.startsWith("org/keycloak/"))
                    .limit(5)
                    .toList();
            assertTrue(bundled.isEmpty(), "Keycloak classes must stay provided, found: " + bundled);
        }
    }

    @Test
    @DisplayName("does not bundle libraries the server already ships")
    void excludesServerSuppliedLibraries() throws IOException {
        // Duplicating these would pin copies that stop moving when Keycloak is upgraded, and
        // Keycloak's Netty is a different version from the AWS SDK's. ServiceLoaderIT proves
        // the SDK still resolves what it needs from the server's classpath.
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var prefixes = java.util.List.of(
                    "io/netty/",        // async client only; SnsClient is synchronous
                    "org/apache/http/", // supplied by Keycloak
                    "org/slf4j/",       // supplied by Keycloak
                    "org/apache/commons/logging/");
            var bundled = jar.stream()
                    .map(JarEntry::getName)
                    .filter(n -> prefixes.stream().anyMatch(n::startsWith))
                    .limit(5)
                    .toList();
            assertTrue(bundled.isEmpty(), "server-supplied libraries were bundled: " + bundled);
        }
    }

    @Test
    @DisplayName("drops signature files that shading invalidates")
    void excludesStaleSignatures() throws IOException {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var signatures = jar.stream()
                    .map(JarEntry::getName)
                    .filter(n -> n.startsWith("META-INF/")
                            && (n.endsWith(".SF") || n.endsWith(".DSA") || n.endsWith(".RSA")))
                    .toList();
            assertTrue(signatures.isEmpty(), "stale signatures were packaged: " + signatures);
        }
    }
}
