package com.mesutpiskin.keycloak.auth.messaging.packaging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises provider discovery against the packaged artifact rather than the Maven test
 * classpath, which has the AWS SDK on it either way and so cannot detect a thin jar. Without
 * the SDK packaged this fails as {@code ServiceConfigurationError: ... AwsSnsMessageSender
 * Unable to get public no-arg constructor}.
 *
 * <p>The classloader is deliberately isolated: its parent is the platform classloader, so the
 * only application classes visible are those inside the artifact plus the handful of jars
 * Keycloak itself supplies at runtime. That combination is what a real deployment looks like,
 * and it is what makes excluding those jars from the shade safe to assert rather than assume.
 * Clients are only built, never used to publish, so no AWS credentials or network access are
 * involved.
 */
@DisplayName("ServiceLoader against the packaged artifact")
class ServiceLoaderIT {

    private static final String SENDER_INTERFACE =
            "com.mesutpiskin.keycloak.auth.messaging.service.MessageSender";

    private static Path artifact;

    @BeforeAll
    static void locateArtifact() {
        String path = System.getProperty("shaded.artifact");
        assertNotNull(path, "shaded.artifact system property is not set");
        artifact = Path.of(path);
        assertTrue(Files.isRegularFile(artifact), "artifact was not built: " + artifact);
    }

    /**
     * The jars Keycloak puts on a provider's classpath that this build deliberately does not
     * bundle. Apache HttpClient matters most: it is what the AWS SDK's default sync HTTP client
     * needs, and excluding it from the shade is only correct if the server really does supply it.
     */
    private static final List<String> PROVIDED_BY_KEYCLOAK = List.of(
            "jboss-logging", "httpclient", "httpcore", "commons-codec", "slf4j-api",
            // Apache HttpClient 4.x needs the commons-logging API; Keycloak ships it as the
            // commons-logging-jboss-logging bridge rather than as commons-logging itself.
            "commons-logging");

    /**
     * Artifact plus the jars Keycloak supplies, and nothing else — in particular no AWS SDK
     * unless the artifact itself carries it.
     */
    private static URLClassLoader isolatedLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(artifact.toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            String name = Path.of(entry).getFileName().toString();
            if (PROVIDED_BY_KEYCLOAK.stream().anyMatch(name::startsWith)) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    @SuppressWarnings("unchecked")
    private static List<String> loadProviderIds(ClassLoader loader) throws Exception {
        Class<?> senderInterface = Class.forName(SENDER_INTERFACE, false, loader);
        var providers = ServiceLoader.load((Class<Object>) senderInterface, loader);
        var method = senderInterface.getMethod("getProviderId");
        List<String> ids = new ArrayList<>();
        for (Object provider : providers) {
            ids.add((String) method.invoke(provider));
        }
        return ids;
    }

    @Test
    @DisplayName("discovers and instantiates every sender, aws-sns included")
    void discoversAllSenders() throws Exception {
        try (URLClassLoader loader = isolatedLoader()) {
            List<String> ids = assertDoesNotThrow(() -> loadProviderIds(loader),
                    "ServiceLoader failed against the packaged artifact");

            assertTrue(ids.contains("aws-sns"),
                    "aws-sns was not discovered, found: " + ids);
            assertTrue(ids.containsAll(List.of("twilio", "vonage", "telegram", "whatsapp", "signal")),
                    "a sender registration was lost, found: " + ids);
        }
    }

    @Test
    @DisplayName("AwsSnsMessageSender resolves its AWS types from inside the artifact")
    void awsTypesResolve() throws Exception {
        try (URLClassLoader loader = isolatedLoader()) {
            // Loading the class is not enough: the constructor lookup ServiceLoader performs
            // resolves the parameter types of every constructor, and that is what fails when
            // the AWS types are absent.
            Class<?> sender = Class.forName(
                    "com.mesutpiskin.keycloak.auth.messaging.service.impl.AwsSnsMessageSender",
                    false, loader);
            var ctor = assertDoesNotThrow(() -> sender.getConstructor(),
                    "no public no-arg constructor reachable from the artifact");
            assertNotNull(ctor.newInstance(), "AwsSnsMessageSender could not be instantiated");

            assertDoesNotThrow(
                    () -> Class.forName("software.amazon.awssdk.services.sns.SnsClient", false, loader),
                    "SnsClient is not reachable from the artifact");
        }
    }

    @Test
    @DisplayName("SnsClient builds against Keycloak's classpath, so the excluded jars really are supplied")
    void snsClientBuilds() throws Exception {
        try (URLClassLoader loader = isolatedLoader()) {
            // Building the client resolves SdkHttpService through ServiceLoader and links the
            // Apache HTTP client, which this build excludes from the shade on the assumption
            // that Keycloak provides it. No request is made, so no credentials are exercised.
            Class<?> snsClient = Class.forName(
                    "software.amazon.awssdk.services.sns.SnsClient", false, loader);
            Object builder = snsClient.getMethod("builder").invoke(null);

            Class<?> region = Class.forName("software.amazon.awssdk.regions.Region", false, loader);
            Object euWest1 = region.getMethod("of", String.class).invoke(null, "eu-west-1");
            builder = invokeSingleArg(builder, "region", region, euWest1);

            Class<?> basic = Class.forName(
                    "software.amazon.awssdk.auth.credentials.AwsBasicCredentials", false, loader);
            Object credentials = basic.getMethod("create", String.class, String.class)
                    .invoke(null, "AKIDEXAMPLE", "not-a-real-secret");
            Class<?> staticProvider = Class.forName(
                    "software.amazon.awssdk.auth.credentials.StaticCredentialsProvider", false, loader);
            Class<?> awsCredentials = Class.forName(
                    "software.amazon.awssdk.auth.credentials.AwsCredentials", false, loader);
            Object provider = staticProvider.getMethod("create", awsCredentials).invoke(null, credentials);
            Object configured = invokeSingleArg(builder, "credentialsProvider", null, provider);

            Object client = assertDoesNotThrow(() -> builder0(configured),
                    "SnsClient could not be built: an excluded dependency is not actually provided");
            assertNotNull(client);
        }
    }

    private static Object builder0(Object builder) throws Exception {
        return builder.getClass().getMethod("build").invoke(builder);
    }

    /** Builder methods are declared on interfaces, so match by name and arity. */
    private static Object invokeSingleArg(Object target, String name, Class<?> paramType, Object arg)
            throws Exception {
        for (var m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
            if (paramType != null && !m.getParameterTypes()[0].equals(paramType)) continue;
            if (paramType == null && !m.getParameterTypes()[0].isInstance(arg)) continue;
            return m.invoke(target, arg);
        }
        throw new AssertionError("no single-argument builder method named " + name);
    }
}
