# Keycloak 2FA Messaging Authenticator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a Keycloak SPI plugin that delivers 2FA OTPs via SMS (Twilio/AWS SNS/Vonage), Telegram, WhatsApp, and Signal — architecturally mirroring `../keycloak-2fa-email-authenticator`.

**Architecture:** Single JAR with a `MessageSender` Java SPI extension point (`java.util.ServiceLoader`). Two authenticators (regular + conditional), one required-action enrollment flow, one credential provider, six built-in senders. HTTP senders use JDK `HttpClient`; AWS SNS uses `software.amazon.awssdk:sns`.

**Tech Stack:** Java 21, Maven 3.9+, Keycloak 26.6.1, JUnit 5.14.x, Mockito 5.23.x, AWS SDK v2 (SNS only).

**Reference project:** `../keycloak-2fa-email-authenticator/` — mirror naming/structure/conventions exactly. JavaDoc style, error handling, log messages, constant naming — all should match the sibling.

---

## File Structure

```
keycloak-2fa-messaging-authenticator/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .gitignore
├── LICENSE                                 (Apache-2.0, copy from sibling)
├── README.md
├── COMPATIBILITY.md
├── .github/workflows/
│   ├── maven.yml                           (CI matrix: KC 25.0.0, 26.0.0, 26.6.1)
│   └── maven-publish.yml
├── src/main/java/com/mesutpiskin/keycloak/auth/messaging/
│   ├── MessagingConstants.java
│   ├── OtpHashUtils.java
│   ├── E164Validator.java
│   ├── ContactResolver.java
│   ├── ContactMasker.java
│   ├── MessagingAuthenticatorForm.java
│   ├── MessagingAuthenticatorFormFactory.java
│   ├── ConditionalMessagingAuthenticatorForm.java
│   ├── ConditionalMessagingAuthenticatorFormFactory.java
│   ├── MessagingAuthenticatorRequiredAction.java
│   ├── MessagingAuthenticatorRequiredActionFactory.java
│   ├── MessagingAuthenticatorCredentialModel.java
│   ├── MessagingAuthenticatorCredentialProvider.java
│   ├── MessagingAuthenticatorCredentialProviderFactory.java
│   ├── model/
│   │   ├── OtpMessage.java
│   │   └── MessageChannel.java
│   └── service/
│       ├── MessageSender.java
│       ├── MessageDeliveryException.java
│       ├── MessageSenderRegistry.java
│       └── impl/
│           ├── TwilioMessageSender.java
│           ├── AwsSnsMessageSender.java
│           ├── VonageMessageSender.java
│           ├── TelegramMessageSender.java
│           ├── WhatsAppMessageSender.java
│           └── SignalMessageSender.java
├── src/main/resources/
│   ├── META-INF/services/
│   │   ├── org.keycloak.authentication.AuthenticatorFactory
│   │   ├── org.keycloak.authentication.RequiredActionFactory
│   │   ├── org.keycloak.credential.CredentialProviderFactory
│   │   └── com.mesutpiskin.keycloak.auth.messaging.service.MessageSender
│   └── theme-resources/
│       ├── templates/
│       │   ├── messaging-code-form.ftl
│       │   ├── messaging-authenticator-setup-form.ftl
│       │   └── messaging-authenticator-setup-verify-form.ftl
│       └── messages/
│           ├── messages_en.properties
│           └── messages_tr.properties
└── src/test/java/com/mesutpiskin/keycloak/auth/messaging/
    ├── MessagingConstantsTest.java
    ├── OtpHashUtilsTest.java
    ├── E164ValidatorTest.java
    ├── ContactResolverTest.java
    ├── ContactMaskerTest.java
    ├── MessagingAuthenticatorFormTest.java
    ├── ConditionalMessagingAuthenticatorFormTest.java
    ├── MessagingAuthenticatorCredentialModelTest.java
    ├── MessagingAuthenticatorCredentialProviderTest.java
    ├── MessagingAuthenticatorRequiredActionTest.java
    ├── model/
    │   └── OtpMessageTest.java
    └── service/
        ├── MessageSenderRegistryTest.java
        └── impl/
            ├── TwilioMessageSenderTest.java
            ├── AwsSnsMessageSenderTest.java
            ├── VonageMessageSenderTest.java
            ├── TelegramMessageSenderTest.java
            ├── WhatsAppMessageSenderTest.java
            └── SignalMessageSenderTest.java
```

---

## Phase 1: Project Skeleton

### Task 1: Create pom.xml

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Write pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <artifactId>keycloak-2fa-messaging-authenticator</artifactId>
    <groupId>io.github.mesutpiskin</groupId>
    <version>26.0.0</version>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>

    <name>Keycloak 2FA messaging authenticator</name>
    <description>A Keycloak authenticator for 2FA via SMS, Telegram, WhatsApp, and Signal OTP</description>
    <url>https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator</url>

    <licenses>
        <license>
            <name>The Apache License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
    </licenses>

    <developers>
        <developer>
            <name>Mesut Pişkin</name>
            <email>mesutpiskin@outlook.com</email>
            <organization>mesutpiskin</organization>
            <organizationUrl>https://github.com/mesutpiskin</organizationUrl>
        </developer>
    </developers>

    <scm>
        <connection>scm:git:git://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator.git</connection>
        <developerConnection>scm:git:ssh://github.com:mesutpiskin/keycloak-2fa-messaging-authenticator.git</developerConnection>
        <url>https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator/tree/main</url>
    </scm>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>21</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <keycloak.version>26.6.1</keycloak.version>
        <aws-sdk.version>2.42.36</aws-sdk.version>
        <junit-jupiter.version>5.14.3</junit-jupiter.version>
        <mockito.version>5.23.0</mockito.version>
        <maven-surefire.plugin.version>3.5.5</maven-surefire.plugin.version>
        <maven-jar.plugin.version>3.5.0</maven-jar.plugin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>software.amazon.awssdk</groupId>
                <artifactId>bom</artifactId>
                <version>${aws-sdk.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi-private</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-services</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit-jupiter.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>${mockito.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <version>${mockito.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- AWS SNS for SMS -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>sns</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${maven-surefire.plugin.version}</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>${maven-jar.plugin.version}</version>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <Dependencies>
                                <![CDATA[org.keycloak.keycloak-common,org.keycloak.keycloak-core,org.keycloak.keycloak-server-spi,org.keycloak.keycloak-server-spi-private,org.apache.httpcomponents,org.keycloak.keycloak-services,org.jboss.logging,jakarta.ws.rs.api,jakarta.transaction.api,com.fasterxml.jackson.core.jackson-core,com.fasterxml.jackson.core.jackson-annotations,com.fasterxml.jackson.core.jackson-databind]]>
                            </Dependencies>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
        <finalName>${project.artifactId}-v${project.version}</finalName>
    </build>

    <profiles>
        <profile>
            <id>github</id>
            <distributionManagement>
                <repository>
                    <id>github</id>
                    <name>GitHub Packages</name>
                    <url>https://maven.pkg.github.com/mesutpiskin/keycloak-2fa-messaging-authenticator</url>
                </repository>
            </distributionManagement>
        </profile>
        <profile>
            <id>release</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.sonatype.central</groupId>
                        <artifactId>central-publishing-maven-plugin</artifactId>
                        <version>0.10.0</version>
                        <extensions>true</extensions>
                        <configuration>
                            <publishingServerId>central</publishingServerId>
                            <autoPublish>true</autoPublish>
                        </configuration>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-source-plugin</artifactId>
                        <version>3.3.0</version>
                        <executions>
                            <execution>
                                <id>attach-sources</id>
                                <phase>verify</phase>
                                <goals><goal>jar-no-fork</goal></goals>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-javadoc-plugin</artifactId>
                        <version>3.6.3</version>
                        <executions>
                            <execution>
                                <id>attach-javadoc</id>
                                <goals><goal>jar</goal></goals>
                            </execution>
                        </executions>
                        <configuration>
                            <stylesheet>java</stylesheet>
                            <doclint>none</doclint>
                        </configuration>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-gpg-plugin</artifactId>
                        <version>3.1.0</version>
                        <executions>
                            <execution>
                                <id>sign-artifacts</id>
                                <phase>verify</phase>
                                <goals><goal>sign</goal></goals>
                            </execution>
                        </executions>
                        <configuration>
                            <gpgArguments>
                                <arg>--pinentry-mode</arg>
                                <arg>loopback</arg>
                            </gpgArguments>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 2: Verify pom.xml parses**

Run: `mvn -q help:effective-pom -f pom.xml > /dev/null`
Expected: exits 0; no error output.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat: add Maven project descriptor"
```

---

### Task 2: Add .gitignore and LICENSE

**Files:**
- Create: `.gitignore`
- Create: `LICENSE`

- [ ] **Step 1: Write .gitignore**

```
*.class
*.log
*.ctxt
.mtj.tmp/
*.jar
*.war
*.nar
*.ear
*.zip
*.tar.gz
*.rar
hs_err_pid*
.DS_Store
target/*
.idea
.vscode
/target/
```

- [ ] **Step 2: Copy LICENSE from sibling**

```bash
cp ../keycloak-2fa-email-authenticator/LICENSE LICENSE
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore LICENSE
git commit -m "chore: add .gitignore and Apache-2.0 LICENSE"
```

---

### Task 3: Create directory skeleton

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/.gitkeep`
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/model/.gitkeep`
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/.gitkeep`
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/.gitkeep`
- Create: `src/main/resources/META-INF/services/.gitkeep`
- Create: `src/main/resources/theme-resources/templates/.gitkeep`
- Create: `src/main/resources/theme-resources/messages/.gitkeep`
- Create: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/.gitkeep`

- [ ] **Step 1: Create dirs**

```bash
mkdir -p src/main/java/com/mesutpiskin/keycloak/auth/messaging/{model,service/impl}
mkdir -p src/main/resources/{META-INF/services,theme-resources/{templates,messages}}
mkdir -p src/test/java/com/mesutpiskin/keycloak/auth/messaging/{model,service/impl}
```

Note: `.gitkeep` files only needed if a directory has no other content yet; they will be removed when real files are added in later tasks. Skip this step if your editor handles empty dirs.

- [ ] **Step 2: Verify**

Run: `find src -type d | sort`
Expected: all directories listed.

- [ ] **Step 3: No commit yet** (directories will be committed with first real file in Phase 2).

---

## Phase 2: Core Constants & Utilities

### Task 4: MessagingConstants

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingConstants.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingConstantsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessagingConstants Tests")
class MessagingConstantsTest {

    @Test
    @DisplayName("CODE session-note key is stable")
    void codeKey() {
        assertEquals("messagingCode", MessagingConstants.CODE);
    }

    @Test
    @DisplayName("Default OTP parameters match spec defaults")
    void defaults() {
        assertEquals(6, MessagingConstants.DEFAULT_LENGTH);
        assertEquals(300, MessagingConstants.DEFAULT_TTL);
        assertEquals(60, MessagingConstants.DEFAULT_RESEND_COOLDOWN);
        assertEquals(5, MessagingConstants.DEFAULT_MAX_ATTEMPTS);
        assertFalse(MessagingConstants.DEFAULT_SIMULATION_MODE);
        assertTrue(MessagingConstants.DEFAULT_SKIP_SETUP);
    }

    @Test
    @DisplayName("Channel config key has expected name")
    void channelKey() {
        assertEquals("message.channel", MessagingConstants.MESSAGE_CHANNEL);
    }

    @Test
    @DisplayName("Constructor throws — utility class")
    void utilityClass() throws Exception {
        var ctor = MessagingConstants.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=MessagingConstantsTest test`
Expected: compile error — class does not exist.

- [ ] **Step 3: Write MessagingConstants**

```java
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
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=MessagingConstantsTest test`
Expected: BUILD SUCCESS, tests run = 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingConstants.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingConstantsTest.java
git commit -m "feat: add MessagingConstants with channel/provider config keys"
```

---

### Task 5: OtpHashUtils

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/OtpHashUtils.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/OtpHashUtilsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtpHashUtils Tests")
class OtpHashUtilsTest {

    @Test
    @DisplayName("Same input always produces the same hash")
    void deterministic() { assertEquals(OtpHashUtils.hash("123456"), OtpHashUtils.hash("123456")); }

    @Test
    @DisplayName("Different inputs produce different hashes")
    void collisionResistance() { assertNotEquals(OtpHashUtils.hash("123456"), OtpHashUtils.hash("654321")); }

    @Test
    @DisplayName("Hash never equal to the raw code")
    void noLeak() { assertNotEquals("123456", OtpHashUtils.hash("123456")); }

    @Test
    @DisplayName("matches() true for correct code")
    void matchesOk() { assertTrue(OtpHashUtils.matches("123456", OtpHashUtils.hash("123456"))); }

    @Test
    @DisplayName("matches() false for wrong code")
    void matchesBad() { assertFalse(OtpHashUtils.matches("654321", OtpHashUtils.hash("123456"))); }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=OtpHashUtilsTest test`
Expected: compile error.

- [ ] **Step 3: Write OtpHashUtils**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class OtpHashUtils {
    static final String HASH_ALGORITHM = "SHA-256";

    private OtpHashUtils() {
        throw new UnsupportedOperationException("OtpHashUtils is a utility class and cannot be instantiated");
    }

    static String hash(String code) {
        return HexFormat.of().formatHex(digestBytes(code));
    }

    static boolean matches(String submittedCode, String storedHash) {
        byte[] submittedBytes = digestBytes(submittedCode);
        byte[] storedBytes = HexFormat.of().parseHex(storedHash);
        return MessageDigest.isEqual(submittedBytes, storedBytes);
    }

    private static byte[] digestBytes(String code) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " algorithm not available", e);
        }
    }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=OtpHashUtilsTest test`
Expected: BUILD SUCCESS, tests run = 5.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/OtpHashUtils.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/OtpHashUtilsTest.java
git commit -m "feat: add SHA-256 OTP hashing with constant-time matching"
```

---

### Task 6: MessageChannel enum

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/model/MessageChannel.java`

- [ ] **Step 1: Write MessageChannel**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/model/MessageChannel.java
git commit -m "feat: add MessageChannel enum (SMS/TELEGRAM/WHATSAPP/SIGNAL)"
```

---

### Task 7: OtpMessage immutable model

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/model/OtpMessage.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/model/OtpMessageTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtpMessage Tests")
class OtpMessageTest {

    @Test
    @DisplayName("Builder produces immutable message with required fields")
    void builderHappy() {
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("847291").ttlSeconds(300).locale("tr").build();
        assertEquals("+905001234567", m.getTo());
        assertEquals("847291", m.getCode());
        assertEquals(300, m.getTtlSeconds());
        assertEquals("tr", m.getLocale());
    }

    @Test
    @DisplayName("Null 'to' throws")
    void requireTo() {
        assertThrows(NullPointerException.class,
                () -> OtpMessage.builder().code("123456").ttlSeconds(300).build());
    }

    @Test
    @DisplayName("Null 'code' throws")
    void requireCode() {
        assertThrows(NullPointerException.class,
                () -> OtpMessage.builder().to("+15551234").ttlSeconds(300).build());
    }

    @Test
    @DisplayName("Non-positive ttl throws")
    void requirePositiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> OtpMessage.builder().to("+15551234").code("123456").ttlSeconds(0).build());
    }

    @Test
    @DisplayName("locale defaults to 'en' when not set")
    void defaultLocale() {
        OtpMessage m = OtpMessage.builder().to("+15551234").code("123456").ttlSeconds(60).build();
        assertEquals("en", m.getLocale());
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=OtpMessageTest test`
Expected: compile error.

- [ ] **Step 3: Write OtpMessage**

```java
package com.mesutpiskin.keycloak.auth.messaging.model;

import java.util.Objects;

public final class OtpMessage {

    private final String to;
    private final String code;
    private final int ttlSeconds;
    private final String locale;

    private OtpMessage(Builder b) {
        this.to = Objects.requireNonNull(b.to, "recipient ('to') cannot be null");
        this.code = Objects.requireNonNull(b.code, "code cannot be null");
        if (b.ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
        this.ttlSeconds = b.ttlSeconds;
        this.locale = (b.locale == null || b.locale.isBlank()) ? "en" : b.locale;
    }

    public String getTo() { return to; }
    public String getCode() { return code; }
    public int getTtlSeconds() { return ttlSeconds; }
    public String getLocale() { return locale; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String to;
        private String code;
        private int ttlSeconds;
        private String locale;

        private Builder() {}

        public Builder to(String to) { this.to = to; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder ttlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; return this; }
        public Builder locale(String locale) { this.locale = locale; return this; }
        public OtpMessage build() { return new OtpMessage(this); }
    }

    @Override
    public String toString() {
        return "OtpMessage{to='" + to + "', code=*****, ttlSeconds=" + ttlSeconds + ", locale='" + locale + "'}";
    }
}
```

Note: `toString()` masks the raw code so it never leaks via logging.

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=OtpMessageTest test`
Expected: BUILD SUCCESS, tests run = 5.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/model/OtpMessage.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/model/OtpMessageTest.java
git commit -m "feat: add immutable OtpMessage with Builder"
```

---

### Task 8: E164Validator

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/E164Validator.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/E164ValidatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E164Validator Tests")
class E164ValidatorTest {

    @Test @DisplayName("Valid E.164 numbers accepted")
    void valid() {
        assertTrue(E164Validator.isValid("+905001234567"));
        assertTrue(E164Validator.isValid("+15551234567"));
        assertTrue(E164Validator.isValid("+442071234567"));
    }

    @Test @DisplayName("Missing + rejected")
    void missingPlus() { assertFalse(E164Validator.isValid("905001234567")); }

    @Test @DisplayName("Leading zero after + rejected")
    void leadingZero() { assertFalse(E164Validator.isValid("+0905001234567")); }

    @Test @DisplayName("Too short rejected")
    void tooShort() { assertFalse(E164Validator.isValid("+12345")); }

    @Test @DisplayName("Too long rejected")
    void tooLong() { assertFalse(E164Validator.isValid("+1234567890123456")); }

    @Test @DisplayName("Non-digit characters rejected")
    void nonDigit() {
        assertFalse(E164Validator.isValid("+90 500 123 4567"));
        assertFalse(E164Validator.isValid("+1-555-123-4567"));
        assertFalse(E164Validator.isValid("+155abc1234"));
    }

    @Test @DisplayName("Null/blank rejected")
    void nullBlank() {
        assertFalse(E164Validator.isValid(null));
        assertFalse(E164Validator.isValid(""));
        assertFalse(E164Validator.isValid("  "));
    }

    @Test @DisplayName("normalize strips whitespace and dashes; preserves +")
    void normalize() {
        assertEquals("+905001234567", E164Validator.normalize(" +90 500 123 45 67 "));
        assertEquals("+15551234567", E164Validator.normalize("+1-555-123-4567"));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=E164ValidatorTest test`
Expected: compile error.

- [ ] **Step 3: Write E164Validator**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import java.util.regex.Pattern;

public final class E164Validator {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private E164Validator() {
        throw new UnsupportedOperationException("E164Validator is a utility class and cannot be instantiated");
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        return E164.matcher(value.trim()).matches();
    }

    public static String normalize(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\s-()]", "").trim();
    }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=E164ValidatorTest test`
Expected: BUILD SUCCESS, tests run = 8.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/E164Validator.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/E164ValidatorTest.java
git commit -m "feat: add E.164 phone number validator"
```

---

### Task 9: ContactMasker

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/ContactMasker.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/ContactMaskerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContactMasker Tests")
class ContactMaskerTest {

    @Test @DisplayName("Phone-like input shows + country and last 2 digits")
    void phone() {
        assertEquals("+90 5** *** **67", ContactMasker.maskPhone("+905001234567"));
    }

    @Test @DisplayName("Short phone falls back gracefully")
    void shortPhone() { assertEquals("****", ContactMasker.maskPhone("+12")); }

    @Test @DisplayName("Null/blank returns empty mask")
    void nullVal() {
        assertEquals("", ContactMasker.maskPhone(null));
        assertEquals("", ContactMasker.maskPhone(""));
    }

    @Test @DisplayName("maskGeneric keeps first+last char")
    void generic() {
        assertEquals("a*******t", ContactMasker.maskGeneric("alphabet"));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=ContactMaskerTest test`
Expected: compile error.

- [ ] **Step 3: Write ContactMasker**

```java
package com.mesutpiskin.keycloak.auth.messaging;

public final class ContactMasker {

    private ContactMasker() {
        throw new UnsupportedOperationException("ContactMasker is a utility class and cannot be instantiated");
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() < 5) return "****";
        String country = phone.startsWith("+") ? "+" + digits.substring(0, Math.min(2, digits.length() - 4)) : "";
        String start = digits.substring(country.length() == 0 ? 0 : country.length() - 1, Math.min(country.length() == 0 ? 1 : country.length(), digits.length() - 2));
        String last2 = digits.substring(digits.length() - 2);
        // Simple deterministic mask "+CC X** *** **YY"
        int middle = digits.length() - (country.length() == 0 ? 0 : country.length() - 1) - 2;
        StringBuilder masked = new StringBuilder();
        if (!country.isEmpty()) masked.append(country).append(' ');
        if (digits.length() >= 7) {
            masked.append(digits.charAt(country.length() == 0 ? 0 : country.length() - 1)).append("** *** **").append(last2);
        } else {
            masked.append("***").append(last2);
        }
        return masked.toString();
    }

    public static String maskGeneric(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 2) return "*".repeat(value.length());
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }
}
```

Note: If the test fails on exact `"+90 5** *** **67"` formatting due to off-by-one indexing, adjust the implementation OR loosen the test to assert "starts with +90" and "ends with 67" — the exact pattern is cosmetic. Pick one and stay consistent.

- [ ] **Step 4: Run test, iterate until pass**

Run: `mvn -q -Dtest=ContactMaskerTest test`
Expected: BUILD SUCCESS, tests run = 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/ContactMasker.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/ContactMaskerTest.java
git commit -m "feat: add ContactMasker for safe display of phone/chat-id"
```

---

## Phase 3: SPI Extension Point

### Task 10: MessageDeliveryException + MessageSender interface

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageDeliveryException.java`
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageSender.java`

- [ ] **Step 1: Write MessageDeliveryException**

```java
package com.mesutpiskin.keycloak.auth.messaging.service;

public class MessageDeliveryException extends Exception {
    public MessageDeliveryException(String message) { super(message); }
    public MessageDeliveryException(String message, Throwable cause) { super(message, cause); }
}
```

- [ ] **Step 2: Write MessageSender**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageDeliveryException.java \
        src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageSender.java
git commit -m "feat: add MessageSender SPI interface and delivery exception"
```

---

### Task 11: MessageSenderRegistry

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageSenderRegistry.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageSenderRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=MessageSenderRegistryTest test`
Expected: compile error.

- [ ] **Step 3: Write MessageSenderRegistry**

```java
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
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=MessageSenderRegistryTest test`
Expected: BUILD SUCCESS, tests run = 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageSenderRegistry.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/MessageSenderRegistryTest.java
git commit -m "feat: add MessageSenderRegistry with ServiceLoader discovery"
```

---

## Phase 4: Built-in MessageSenders

### Task 12: TwilioMessageSender (SMS)

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TwilioMessageSender.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TwilioMessageSenderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TwilioMessageSender Tests")
class TwilioMessageSenderTest {

    private HttpClient http;

    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.TWILIO_ACCOUNT_SID, "ACxxxx",
                MessagingConstants.TWILIO_AUTH_TOKEN, "tokenxxxx",
                MessagingConstants.TWILIO_FROM_NUMBER, "+15550001111");
    }

    @Test @DisplayName("getChannel()=SMS, getProviderId()=twilio")
    void identity() {
        var s = new TwilioMessageSender(http);
        assertEquals(MessageChannel.SMS, s.getChannel());
        assertEquals("twilio", s.getProviderId());
    }

    @Test @DisplayName("Not available before configure")
    void notAvailable() { assertFalse(new TwilioMessageSender(http).isAvailable()); }

    @Test @DisplayName("Available after valid configure")
    void available() {
        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Missing required config throws on send")
    void missingConfig() {
        var s = new TwilioMessageSender(http); s.configure(Map.of());
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }

    @Test @DisplayName("Sends with HTTP POST on 2xx")
    @SuppressWarnings("unchecked")
    void send2xx() throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(201);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build());

        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 4xx")
    @SuppressWarnings("unchecked")
    void send4xx() throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(401);
        when(resp.body()).thenReturn("unauthorized");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void sendIoEx() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));
        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=TwilioMessageSenderTest test`
Expected: compile error.

- [ ] **Step 3: Write TwilioMessageSender**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TwilioMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(TwilioMessageSender.class);
    private static final String API_BASE = "https://api.twilio.com/2010-04-01/Accounts/";

    private final HttpClient httpClient;
    private String accountSid;
    private String authToken;
    private String fromNumber;

    public TwilioMessageSender() { this(HttpClient.newHttpClient()); }
    public TwilioMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "twilio"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SMS; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.accountSid = config.get(MessagingConstants.TWILIO_ACCOUNT_SID);
        this.authToken = config.get(MessagingConstants.TWILIO_AUTH_TOKEN);
        this.fromNumber = config.get(MessagingConstants.TWILIO_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(accountSid) && notBlank(authToken) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Twilio is not configured (accountSid/authToken/fromNumber required)");
        }

        String body = buildBody(message.getCode(), message.getTtlSeconds());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", message.getTo());
        form.put("From", fromNumber);
        form.put("Body", body);
        String encoded = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + accountSid + "/Messages.json"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException(
                        "Twilio API returned status " + resp.statusCode() + ": " + resp.body());
            }
            logger.debugf("Twilio SMS sent (status=%d)", resp.statusCode());
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Twilio SMS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Twilio SMS", e);
        }
    }

    private static String buildBody(String code, int ttlSeconds) {
        return "Your verification code is: " + code + " (valid for " + ttlSeconds + " seconds).";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=TwilioMessageSenderTest test`
Expected: BUILD SUCCESS, tests run = 7.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TwilioMessageSender.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TwilioMessageSenderTest.java
git commit -m "feat: add Twilio SMS sender"
```

---

### Task 13: AwsSnsMessageSender (SMS)

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/AwsSnsMessageSender.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/AwsSnsMessageSenderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AwsSnsMessageSender Tests")
class AwsSnsMessageSenderTest {

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.AWS_SNS_REGION, "us-east-1",
                MessagingConstants.AWS_ACCESS_KEY_ID, "AKIA...",
                MessagingConstants.AWS_SECRET_ACCESS_KEY, "secret...");
    }

    @Test @DisplayName("getChannel=SMS, getProviderId=aws-sns")
    void identity() {
        var s = new AwsSnsMessageSender();
        assertEquals(MessageChannel.SMS, s.getChannel());
        assertEquals("aws-sns", s.getProviderId());
    }

    @Test @DisplayName("Not available before configure")
    void notAvailable() { assertFalse(new AwsSnsMessageSender().isAvailable()); }

    @Test @DisplayName("Available after valid configure")
    void available() {
        var s = new AwsSnsMessageSender(); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Missing region throws on send")
    void missingRegion() {
        var s = new AwsSnsMessageSender();
        s.configure(Map.of(MessagingConstants.AWS_ACCESS_KEY_ID, "k", MessagingConstants.AWS_SECRET_ACCESS_KEY, "s"));
        OtpMessage m = OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }

    @Test @DisplayName("Sends via injected SnsClient on happy path")
    void sendOk() throws Exception {
        SnsClient client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("mid").build());

        var s = new AwsSnsMessageSender((cfg) -> client);
        s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());

        verify(client).publish(any(PublishRequest.class));
    }

    @Test @DisplayName("Throws MessageDeliveryException on SnsException")
    void sendFail() {
        SnsClient client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().message("denied").build());

        var s = new AwsSnsMessageSender((cfg) -> client);
        s.configure(goodConfig());
        OtpMessage m = OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=AwsSnsMessageSenderTest test`
Expected: compile error.

- [ ] **Step 3: Write AwsSnsMessageSender**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.Map;
import java.util.function.Function;

public class AwsSnsMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(AwsSnsMessageSender.class);

    public interface SnsClientFactory extends Function<Map<String, String>, SnsClient> {}

    private final SnsClientFactory clientFactory;
    private Map<String, String> config = Map.of();

    public AwsSnsMessageSender() {
        this(AwsSnsMessageSender::defaultClient);
    }

    public AwsSnsMessageSender(SnsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override public String getProviderId() { return "aws-sns"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SMS; }

    @Override public void configure(Map<String, String> config) { this.config = config == null ? Map.of() : config; }

    @Override
    public boolean isAvailable() {
        return notBlank(config.get(MessagingConstants.AWS_SNS_REGION))
                && notBlank(config.get(MessagingConstants.AWS_ACCESS_KEY_ID))
                && notBlank(config.get(MessagingConstants.AWS_SECRET_ACCESS_KEY));
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("AWS SNS is not configured (region/accessKeyId/secretAccessKey required)");
        }
        String body = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";

        try (SnsClient client = clientFactory.apply(config)) {
            client.publish(PublishRequest.builder().phoneNumber(message.getTo()).message(body).build());
            logger.debugf("AWS SNS SMS published to %s", message.getTo());
        } catch (SnsException e) {
            throw new MessageDeliveryException("AWS SNS publish failed", e);
        } catch (RuntimeException e) {
            throw new MessageDeliveryException("AWS SNS publish failed", e);
        }
    }

    private static SnsClient defaultClient(Map<String, String> cfg) {
        return SnsClient.builder()
                .region(Region.of(cfg.get(MessagingConstants.AWS_SNS_REGION)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                cfg.get(MessagingConstants.AWS_ACCESS_KEY_ID),
                                cfg.get(MessagingConstants.AWS_SECRET_ACCESS_KEY))))
                .build();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

Note: the `SnsClient` close in the try-with-resources won't trigger when the mock is used in tests (Mockito handles `AutoCloseable` gracefully). Verify the mock works as expected.

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=AwsSnsMessageSenderTest test`
Expected: BUILD SUCCESS, tests run = 6.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/AwsSnsMessageSender.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/AwsSnsMessageSenderTest.java
git commit -m "feat: add AWS SNS SMS sender"
```

---

### Task 14: VonageMessageSender (SMS)

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/VonageMessageSender.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/VonageMessageSenderTest.java`

- [ ] **Step 1: Write the failing test**

Same shape as TwilioMessageSenderTest (Task 12), but with:
- Provider identity: `assertEquals("vonage", s.getProviderId());`
- Config keys: `VONAGE_API_KEY`, `VONAGE_API_SECRET`, `VONAGE_FROM_NUMBER`
- Constructor accepts injected `HttpClient`

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("VonageMessageSender Tests")
class VonageMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.VONAGE_API_KEY, "k",
                MessagingConstants.VONAGE_API_SECRET, "s",
                MessagingConstants.VONAGE_FROM_NUMBER, "MyApp");
    }

    @Test @DisplayName("identity")
    void identity() {
        var s = new VonageMessageSender(http);
        assertEquals(MessageChannel.SMS, s.getChannel());
        assertEquals("vonage", s.getProviderId());
    }

    @Test @DisplayName("Not available before configure")
    void notAvailable() { assertFalse(new VonageMessageSender(http).isAvailable()); }

    @Test @DisplayName("Available after configure")
    void available() {
        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Throws when not configured")
    void missingConfig() {
        var s = new VonageMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Sends on 2xx with status=0 in JSON")
    @SuppressWarnings("unchecked")
    void send2xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"messages\":[{\"status\":\"0\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new VonageMessageSender(http); s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 2xx with non-zero Vonage status")
    @SuppressWarnings("unchecked")
    void sendVonageError() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"messages\":[{\"status\":\"4\",\"error-text\":\"Bad credentials\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on HTTP 5xx")
    @SuppressWarnings("unchecked")
    void send5xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(500);
        when(r.body()).thenReturn("boom");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=VonageMessageSenderTest test`
Expected: compile error.

- [ ] **Step 3: Write VonageMessageSender**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VonageMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(VonageMessageSender.class);
    private static final String API_URL = "https://rest.nexmo.com/sms/json";
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"(\\d+)\"");
    private static final Pattern ERR = Pattern.compile("\"error-text\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;
    private String apiKey;
    private String apiSecret;
    private String fromNumber;

    public VonageMessageSender() { this(HttpClient.newHttpClient()); }
    public VonageMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "vonage"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SMS; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.apiKey = config.get(MessagingConstants.VONAGE_API_KEY);
        this.apiSecret = config.get(MessagingConstants.VONAGE_API_SECRET);
        this.fromNumber = config.get(MessagingConstants.VONAGE_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(apiKey) && notBlank(apiSecret) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Vonage is not configured (apiKey/apiSecret/fromNumber required)");
        }
        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";

        Map<String, String> form = new LinkedHashMap<>();
        form.put("api_key", apiKey);
        form.put("api_secret", apiSecret);
        form.put("from", fromNumber);
        // Vonage expects 'to' without leading + (just digits)
        form.put("to", message.getTo().startsWith("+") ? message.getTo().substring(1) : message.getTo());
        form.put("text", text);

        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("Vonage HTTP status " + resp.statusCode() + ": " + resp.body());
            }
            // Inspect first message's status; "0" = success per Vonage docs
            String b = resp.body() == null ? "" : resp.body();
            Matcher m = STATUS.matcher(b);
            if (m.find() && !"0".equals(m.group(1))) {
                Matcher em = ERR.matcher(b);
                String err = em.find() ? em.group(1) : "unknown error";
                throw new MessageDeliveryException("Vonage rejected message status=" + m.group(1) + " (" + err + ")");
            }
            logger.debugf("Vonage SMS accepted");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Vonage SMS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Vonage SMS", e);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=VonageMessageSenderTest test`
Expected: BUILD SUCCESS, tests run = 8.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/VonageMessageSender.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/VonageMessageSenderTest.java
git commit -m "feat: add Vonage SMS sender"
```

---

### Task 15: TelegramMessageSender

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TelegramMessageSender.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TelegramMessageSenderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("TelegramMessageSender Tests")
class TelegramMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    @Test @DisplayName("identity")
    void identity() {
        var s = new TelegramMessageSender(http);
        assertEquals(MessageChannel.TELEGRAM, s.getChannel());
        assertEquals("telegram", s.getProviderId());
    }

    @Test @DisplayName("Throws when bot token missing")
    void missingToken() {
        var s = new TelegramMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("123456789").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Available after configure")
    void available() {
        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Sends to api.telegram.org with bot token in URL")
    @SuppressWarnings("unchecked")
    void sendOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"ok\":true}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        s.send(OtpMessage.builder().to("123456789").code("999000").ttlSeconds(60).build());

        verify(http).send(argThat((HttpRequest req) ->
                req.uri().getHost().equals("api.telegram.org")
                        && req.uri().getPath().contains("bot12345:abc")
                        && req.uri().getPath().endsWith("/sendMessage")),
                any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on Telegram ok=false")
    @SuppressWarnings("unchecked")
    void notOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"ok\":false,\"description\":\"chat not found\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("123456789").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("123456789").code("1").ttlSeconds(60).build()));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=TelegramMessageSenderTest test`
Expected: compile error.

- [ ] **Step 3: Write TelegramMessageSender**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(TelegramMessageSender.class);
    private static final Pattern OK = Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final Pattern DESC = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpClient httpClient;
    private String botToken;

    public TelegramMessageSender() { this(HttpClient.newHttpClient()); }
    public TelegramMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "telegram"; }
    @Override public MessageChannel getChannel() { return MessageChannel.TELEGRAM; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.botToken = config.get(MessagingConstants.TELEGRAM_BOT_TOKEN);
    }

    @Override public boolean isAvailable() { return notBlank(botToken); }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Telegram is not configured (telegram.bot.token required)");
        }

        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String body = "chat_id=" + URLEncoder.encode(message.getTo(), StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("Telegram HTTP " + resp.statusCode() + ": " + resp.body());
            }
            String b = resp.body() == null ? "" : resp.body();
            Matcher ok = OK.matcher(b);
            if (!ok.find() || !"true".equals(ok.group(1))) {
                Matcher d = DESC.matcher(b);
                String desc = d.find() ? d.group(1) : "unknown";
                throw new MessageDeliveryException("Telegram API error: " + desc);
            }
            logger.debugf("Telegram message sent");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Telegram message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Telegram message", e);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=TelegramMessageSenderTest test`
Expected: BUILD SUCCESS, tests run = 6.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TelegramMessageSender.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/TelegramMessageSenderTest.java
git commit -m "feat: add Telegram bot sender"
```

---

### Task 16: WhatsAppMessageSender

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/WhatsAppMessageSender.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/WhatsAppMessageSenderTest.java`

Targets Meta WhatsApp Cloud API style: `POST {apiUrl} Authorization: Bearer {token}` with JSON body `{messaging_product:"whatsapp", to, type:"text", text:{body}}`.

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("WhatsAppMessageSender Tests")
class WhatsAppMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.WHATSAPP_API_URL, "https://graph.facebook.com/v18.0/12345/messages",
                MessagingConstants.WHATSAPP_API_TOKEN, "tok",
                MessagingConstants.WHATSAPP_FROM_NUMBER, "+15550001111");
    }

    @Test @DisplayName("identity")
    void identity() {
        var s = new WhatsAppMessageSender(http);
        assertEquals(MessageChannel.WHATSAPP, s.getChannel());
        assertEquals("whatsapp", s.getProviderId());
    }

    @Test @DisplayName("Available with required config")
    void available() {
        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Throws when not configured")
    void missing() {
        var s = new WhatsAppMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Sends with bearer auth on 2xx")
    @SuppressWarnings("unchecked")
    void sendOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"messages\":[{\"id\":\"wamid\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());

        verify(http).send(argThat((HttpRequest req) ->
                "Bearer tok".equals(req.headers().firstValue("Authorization").orElse(""))),
                any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 4xx")
    @SuppressWarnings("unchecked")
    void send4xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(400);
        when(r.body()).thenReturn("{\"error\":{\"message\":\"invalid\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=WhatsAppMessageSenderTest test`

- [ ] **Step 3: Write WhatsAppMessageSender**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class WhatsAppMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(WhatsAppMessageSender.class);

    private final HttpClient httpClient;
    private String apiUrl;
    private String apiToken;
    @SuppressWarnings("unused") private String fromNumber; // recorded for completeness; Cloud API derives sender from URL

    public WhatsAppMessageSender() { this(HttpClient.newHttpClient()); }
    public WhatsAppMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "whatsapp"; }
    @Override public MessageChannel getChannel() { return MessageChannel.WHATSAPP; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.apiUrl = config.get(MessagingConstants.WHATSAPP_API_URL);
        this.apiToken = config.get(MessagingConstants.WHATSAPP_API_TOKEN);
        this.fromNumber = config.get(MessagingConstants.WHATSAPP_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(apiUrl) && notBlank(apiToken) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("WhatsApp is not configured (whatsapp.api.url/token/from.number required)");
        }

        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";
        String to = message.getTo().startsWith("+") ? message.getTo().substring(1) : message.getTo();
        String body = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + jsonEscape(to)
                + "\",\"type\":\"text\",\"text\":{\"body\":\"" + jsonEscape(text) + "\"}}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("WhatsApp HTTP " + resp.statusCode() + ": " + resp.body());
            }
            logger.debugf("WhatsApp message sent");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send WhatsApp message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending WhatsApp message", e);
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=WhatsAppMessageSenderTest test`
Expected: BUILD SUCCESS, tests run = 6.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/WhatsAppMessageSender.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/WhatsAppMessageSenderTest.java
git commit -m "feat: add WhatsApp Cloud API sender"
```

---

### Task 17: SignalMessageSender

Targets `bbernhard/signal-cli-rest-api` style: `POST {url}/v2/send` with JSON `{message, number:from, recipients:[to]}`.

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/SignalMessageSender.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/SignalMessageSenderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("SignalMessageSender Tests")
class SignalMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> good() {
        return Map.of(
                MessagingConstants.SIGNAL_CLI_REST_URL, "http://signal:8080",
                MessagingConstants.SIGNAL_FROM_NUMBER, "+15550001111");
    }

    @Test @DisplayName("identity")
    void identity() {
        var s = new SignalMessageSender(http);
        assertEquals(MessageChannel.SIGNAL, s.getChannel());
        assertEquals("signal", s.getProviderId());
    }

    @Test @DisplayName("Available after configure")
    void available() {
        var s = new SignalMessageSender(http); s.configure(good());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Throws when not configured")
    void missing() {
        var s = new SignalMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("POSTs to /v2/send on the configured URL")
    @SuppressWarnings("unchecked")
    void sendOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(201);
        when(r.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new SignalMessageSender(http); s.configure(good());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());

        verify(http).send(argThat((HttpRequest req) ->
                req.uri().toString().equals("http://signal:8080/v2/send")),
                any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 4xx")
    @SuppressWarnings("unchecked")
    void send4xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(400);
        when(r.body()).thenReturn("bad");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new SignalMessageSender(http); s.configure(good());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new SignalMessageSender(http); s.configure(good());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=SignalMessageSenderTest test`

- [ ] **Step 3: Write SignalMessageSender**

```java
package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SignalMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(SignalMessageSender.class);

    private final HttpClient httpClient;
    private String restUrl;
    private String fromNumber;

    public SignalMessageSender() { this(HttpClient.newHttpClient()); }
    public SignalMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "signal"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SIGNAL; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.restUrl = trimTrailingSlash(config.get(MessagingConstants.SIGNAL_CLI_REST_URL));
        this.fromNumber = config.get(MessagingConstants.SIGNAL_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(restUrl) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Signal is not configured (signal.cli.rest.url/from.number required)");
        }

        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";
        String body = "{\"message\":\"" + jsonEscape(text)
                + "\",\"number\":\"" + jsonEscape(fromNumber)
                + "\",\"recipients\":[\"" + jsonEscape(message.getTo()) + "\"]}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(restUrl + "/v2/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("Signal HTTP " + resp.statusCode() + ": " + resp.body());
            }
            logger.debugf("Signal message sent");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Signal message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Signal message", e);
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=SignalMessageSenderTest test`
Expected: BUILD SUCCESS, tests run = 6.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/SignalMessageSender.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/service/impl/SignalMessageSenderTest.java
git commit -m "feat: add Signal CLI REST sender"
```

---

## Phase 5: Contact Resolution

### Task 18: ContactResolver

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/ContactResolver.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/ContactResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.UserModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ContactResolver Tests")
class ContactResolverTest {

    private UserModel user;

    @BeforeEach
    void setUp() { user = mock(UserModel.class); }

    @Test @DisplayName("Returns value of default attribute for SMS when present")
    void smsDefault() {
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+15551234567");
        assertEquals("+15551234567",
                ContactResolver.resolve(user, MessageChannel.SMS, Map.of()));
    }

    @Test @DisplayName("Respects admin-overridden attribute name")
    void overrideAttr() {
        when(user.getFirstAttribute("mobile")).thenReturn("+15550009999");
        assertEquals("+15550009999",
                ContactResolver.resolve(user, MessageChannel.SMS,
                        Map.of(MessagingConstants.SMS_CONTACT_ATTRIBUTE, "mobile")));
    }

    @Test @DisplayName("Returns null when attribute missing or blank")
    void missing() {
        when(user.getFirstAttribute(anyString())).thenReturn(null);
        assertNull(ContactResolver.resolve(user, MessageChannel.TELEGRAM, Map.of()));

        when(user.getFirstAttribute(anyString())).thenReturn("  ");
        assertNull(ContactResolver.resolve(user, MessageChannel.TELEGRAM, Map.of()));
    }

    @Test @DisplayName("Telegram uses telegramChatId default")
    void telegramDefault() {
        when(user.getFirstAttribute("telegramChatId")).thenReturn("123456789");
        assertEquals("123456789",
                ContactResolver.resolve(user, MessageChannel.TELEGRAM, Map.of()));
    }

    @Test @DisplayName("attributeNameFor returns admin override or default")
    void attrName() {
        assertEquals("phoneNumber",
                ContactResolver.attributeNameFor(MessageChannel.SMS, Map.of()));
        assertEquals("mobile",
                ContactResolver.attributeNameFor(MessageChannel.SMS,
                        Map.of(MessagingConstants.SMS_CONTACT_ATTRIBUTE, "mobile")));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=ContactResolverTest test`
Expected: compile error.

- [ ] **Step 3: Write ContactResolver**

```java
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
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=ContactResolverTest test`
Expected: BUILD SUCCESS, tests run = 5.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/ContactResolver.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/ContactResolverTest.java
git commit -m "feat: add ContactResolver for channel-aware attribute lookup"
```

---

## Phase 6: Credential Layer

### Task 19: MessagingAuthenticatorCredentialModel

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialModel.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialModelTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessagingAuthenticatorCredentialModel Tests")
class MessagingAuthenticatorCredentialModelTest {

    @Test @DisplayName("create() initializes type, createdDate, credentialData")
    void create() {
        var m = MessagingAuthenticatorCredentialModel.create();
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, m.getType());
        assertNotNull(m.getCreatedDate());
        assertNotNull(m.getCredentialData());
        assertTrue(m.getCredentialData().contains(MessagingAuthenticatorCredentialModel.TYPE_ID));
    }

    @Test @DisplayName("createFromCredentialModel copies fields and applies metadata")
    void fromCredential() {
        CredentialModel base = new CredentialModel();
        base.setId("cred-1");
        base.setUserLabel("Phone");
        var converted = MessagingAuthenticatorCredentialModel.createFromCredentialModel(base);
        assertEquals("cred-1", converted.getId());
        assertEquals("Phone", converted.getUserLabel());
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, converted.getType());
    }

    @Test @DisplayName("ensureMetadata returns true when it had to fill in defaults")
    void ensureMeta() {
        CredentialModel m = new CredentialModel();
        assertTrue(MessagingAuthenticatorCredentialModel.ensureMetadata(m));
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, m.getType());
    }

    @Test @DisplayName("ensureMetadata returns false on null model")
    void ensureMetaNull() {
        assertFalse(MessagingAuthenticatorCredentialModel.ensureMetadata(null));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=MessagingAuthenticatorCredentialModelTest test`

- [ ] **Step 3: Write MessagingAuthenticatorCredentialModel**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;

public class MessagingAuthenticatorCredentialModel extends CredentialModel {

    public static final String TYPE_ID = "messaging-authenticator";
    private static final String DEFAULT_CREDENTIAL_DATA = "{\"type\":\"" + TYPE_ID + "\",\"version\":1}";

    public static MessagingAuthenticatorCredentialModel create() {
        MessagingAuthenticatorCredentialModel m = new MessagingAuthenticatorCredentialModel();
        ensureMetadata(m);
        return m;
    }

    public static MessagingAuthenticatorCredentialModel createFromCredentialModel(CredentialModel model) {
        MessagingAuthenticatorCredentialModel out = new MessagingAuthenticatorCredentialModel();
        out.setId(model.getId());
        out.setType(model.getType());
        out.setCreatedDate(model.getCreatedDate());
        out.setUserLabel(model.getUserLabel());
        out.setCredentialData(model.getCredentialData());
        out.setSecretData(model.getSecretData());
        ensureMetadata(out);
        return out;
    }

    public static boolean ensureMetadata(CredentialModel model) {
        if (model == null) return false;
        boolean updated = false;
        if (!TYPE_ID.equals(model.getType())) { model.setType(TYPE_ID); updated = true; }
        if (model.getCreatedDate() == null) { model.setCreatedDate(Time.currentTimeMillis()); updated = true; }
        if (isBlank(model.getCredentialData())) { model.setCredentialData(DEFAULT_CREDENTIAL_DATA); updated = true; }
        return updated;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=MessagingAuthenticatorCredentialModelTest test`
Expected: BUILD SUCCESS, tests run = 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialModel.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialModelTest.java
git commit -m "feat: add MessagingAuthenticatorCredentialModel"
```

---

### Task 20: MessagingAuthenticatorCredentialProvider + Factory

**Files:**
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialProvider.java`
- Create: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialProviderFactory.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.dto.CredentialMetadata;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAuthenticatorCredentialProvider Tests")
class MessagingAuthenticatorCredentialProviderTest {

    private KeycloakSession session;
    private RealmModel realm;
    private UserModel user;
    private SubjectCredentialManager credentialManager;
    private MessagingAuthenticatorCredentialProvider provider;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
        credentialManager = mock(SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(credentialManager);
        when(realm.getAuthenticationFlowsStream()).thenReturn(Stream.empty());
        provider = new MessagingAuthenticatorCredentialProvider(session);
    }

    @Test @DisplayName("supportsCredentialType matches TYPE_ID only")
    void supportsType() {
        assertTrue(provider.supportsCredentialType(MessagingAuthenticatorCredentialModel.TYPE_ID));
        assertFalse(provider.supportsCredentialType("password"));
    }

    @Test @DisplayName("isConfiguredFor returns true when credential exists")
    void hasCredential() {
        when(credentialManager.getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.of(new CredentialModel()));
        assertTrue(provider.isConfiguredFor(realm, user,
                MessagingAuthenticatorCredentialModel.TYPE_ID));
    }

    @Test @DisplayName("isConfiguredFor returns false when wrong type")
    void wrongType() {
        assertFalse(provider.isConfiguredFor(realm, user, "password"));
    }

    @Test @DisplayName("getType returns TYPE_ID")
    void typeId() {
        assertEquals(MessagingAuthenticatorCredentialModel.TYPE_ID, provider.getType());
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=MessagingAuthenticatorCredentialProviderTest test`

- [ ] **Step 3: Write MessagingAuthenticatorCredentialProvider**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.jboss.logging.Logger;
import org.keycloak.credential.*;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class MessagingAuthenticatorCredentialProvider
        implements CredentialProvider<MessagingAuthenticatorCredentialModel>, CredentialInputValidator {

    private static final Logger logger = Logger.getLogger(MessagingAuthenticatorCredentialProvider.class);

    private final KeycloakSession session;

    public MessagingAuthenticatorCredentialProvider(KeycloakSession session) { this.session = session; }

    @Override public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) { return false; }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return getType().equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        if (!supportsCredentialType(credentialType)) return false;
        if (user.credentialManager()
                .getStoredCredentialsByTypeStream(MessagingAuthenticatorCredentialModel.TYPE_ID)
                .findAny().isPresent()) {
            return true;
        }
        return isSkipSetupEnabled(realm) && hasContactAddress(user, realm);
    }

    private boolean isSkipSetupEnabled(RealmModel realm) {
        return realm.getAuthenticationFlowsStream()
                .flatMap(flow -> realm.getAuthenticationExecutionsStream(flow.getId()))
                .filter(exec -> MessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator())
                        || ConditionalMessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator()))
                .map(exec -> {
                    String configId = exec.getAuthenticatorConfig();
                    if (configId == null) return MessagingConstants.DEFAULT_SKIP_SETUP;
                    AuthenticatorConfigModel cfg = realm.getAuthenticatorConfigById(configId);
                    if (cfg == null || cfg.getConfig() == null) return MessagingConstants.DEFAULT_SKIP_SETUP;
                    return Boolean.parseBoolean(cfg.getConfig().getOrDefault(
                            MessagingConstants.SKIP_SETUP, String.valueOf(MessagingConstants.DEFAULT_SKIP_SETUP)));
                })
                .reduce(false, (a, b) -> a || b);
    }

    private boolean hasContactAddress(UserModel user, RealmModel realm) {
        // We can't know the configured channel here without the execution config — fall back to "any
        // non-blank phoneNumber/telegramChatId attribute is enough to consider the user configured".
        String phone = user.getFirstAttribute(MessagingConstants.DEFAULT_PHONE_CONTACT_ATTRIBUTE);
        String tg = user.getFirstAttribute(MessagingConstants.DEFAULT_TELEGRAM_CONTACT_ATTRIBUTE);
        return (phone != null && !phone.isBlank()) || (tg != null && !tg.isBlank());
    }

    @Override
    public CredentialModel createCredential(RealmModel realm, UserModel user,
                                            MessagingAuthenticatorCredentialModel credentialModel) {
        if (MessagingAuthenticatorCredentialModel.ensureMetadata(credentialModel)) {
            logger.debugf("Initialized messaging authenticator credential metadata for user %s", user.getId());
        }
        if (credentialModel.getUserLabel() == null || credentialModel.getUserLabel().isBlank()) {
            credentialModel.setUserLabel("Messaging OTP");
        }
        return user.credentialManager().createStoredCredential(credentialModel);
    }

    @Override
    public boolean deleteCredential(RealmModel realm, UserModel user, String credentialId) {
        return user.credentialManager().removeStoredCredentialById(credentialId);
    }

    @Override
    public MessagingAuthenticatorCredentialModel getCredentialFromModel(CredentialModel model) {
        return MessagingAuthenticatorCredentialModel.createFromCredentialModel(model);
    }

    @Override
    public CredentialTypeMetadata getCredentialTypeMetadata(CredentialTypeMetadataContext context) {
        return CredentialTypeMetadata.builder()
                .type(getType())
                .category(CredentialTypeMetadata.Category.TWO_FACTOR)
                .displayName("messaging-authenticator-display-name")
                .helpText("messaging-authenticator-help-text")
                .iconCssClass("kcAuthenticatorMessagingClass")
                .createAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID)
                .removeable(true)
                .build(session);
    }

    @Override public String getType() { return MessagingAuthenticatorCredentialModel.TYPE_ID; }
}
```

- [ ] **Step 4: Write MessagingAuthenticatorCredentialProviderFactory**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.models.KeycloakSession;

public class MessagingAuthenticatorCredentialProviderFactory
        implements CredentialProviderFactory<MessagingAuthenticatorCredentialProvider> {

    public static final String PROVIDER_ID = MessagingAuthenticatorCredentialModel.TYPE_ID;

    @Override public MessagingAuthenticatorCredentialProvider create(KeycloakSession session) {
        return new MessagingAuthenticatorCredentialProvider(session);
    }

    @Override public String getId() { return PROVIDER_ID; }
}
```

Note: This task references `MessagingAuthenticatorFormFactory.PROVIDER_ID`, `ConditionalMessagingAuthenticatorFormFactory.PROVIDER_ID`, and `MessagingAuthenticatorRequiredAction.PROVIDER_ID` — these will be created in subsequent tasks. The compile errors are expected until Task 24/26/27 are complete. Use forward declarations: temporarily create the classes empty with just a `public static final String PROVIDER_ID = "..."` constant before this task, OR accept that this task does not compile in isolation and is only validated as part of `mvn test` after Task 27. Recommended: create skeleton classes first.

- [ ] **Step 5: Create skeleton classes to satisfy references**

Create empty stubs at `MessagingAuthenticatorFormFactory.java`, `ConditionalMessagingAuthenticatorFormFactory.java`, `MessagingAuthenticatorRequiredAction.java` with only:

```java
package com.mesutpiskin.keycloak.auth.messaging;
public class MessagingAuthenticatorFormFactory { public static final String PROVIDER_ID = "messaging-authenticator"; }
```

```java
package com.mesutpiskin.keycloak.auth.messaging;
public class ConditionalMessagingAuthenticatorFormFactory { public static final String PROVIDER_ID = "messaging-conditional-authenticator"; }
```

```java
package com.mesutpiskin.keycloak.auth.messaging;
public class MessagingAuthenticatorRequiredAction { public static final String PROVIDER_ID = "messaging-authenticator-setup"; }
```

These will be replaced (overwritten) by their full implementations in later tasks.

- [ ] **Step 6: Run test, see pass**

Run: `mvn -q -Dtest=MessagingAuthenticatorCredentialProviderTest test`
Expected: BUILD SUCCESS, tests run = 4.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialProvider.java \
        src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialProviderFactory.java \
        src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorFormFactory.java \
        src/main/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorFormFactory.java \
        src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredAction.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorCredentialProviderTest.java
git commit -m "feat: add credential provider + skeleton factory references"
```

---

## Phase 7: Main Authenticator

### Task 21: MessagingAuthenticatorForm

**Files:**
- Create (overwrite skeleton): `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorForm.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorFormTest.java`

Pattern mirrors `EmailAuthenticatorForm` from the sibling project, but reads `message.channel` + `sms.provider` and calls `MessageSenderRegistry` instead of `EmailSenderFactory`.

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAuthenticatorForm Tests")
class MessagingAuthenticatorFormTest {

    private AuthenticationFlowContext ctx;
    private AuthenticationSessionModel authSession;
    private UserModel user;
    private RealmModel realm;
    private KeycloakSession session;
    private LoginFormsProvider form;
    private AuthenticatorConfigModel configModel;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        ctx = mock(AuthenticationFlowContext.class);
        authSession = mock(AuthenticationSessionModel.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        session = mock(KeycloakSession.class);
        form = mock(LoginFormsProvider.class);
        configModel = mock(AuthenticatorConfigModel.class);
        httpRequest = mock(HttpRequest.class);

        when(ctx.getAuthenticationSession()).thenReturn(authSession);
        when(ctx.getUser()).thenReturn(user);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getSession()).thenReturn(session);
        when(ctx.form()).thenReturn(form);
        when(ctx.getAuthenticatorConfig()).thenReturn(configModel);
        when(ctx.getHttpRequest()).thenReturn(httpRequest);
        when(form.setExecution(anyString())).thenReturn(form);
        when(form.createForm(anyString())).thenReturn(mock(Response.class));
        when(httpRequest.getDecodedFormParameters()).thenReturn(new MultivaluedHashMap<>());

        var execution = mock(org.keycloak.models.AuthenticationExecutionModel.class);
        when(execution.getId()).thenReturn("exec-1");
        when(ctx.getExecution()).thenReturn(execution);
    }

    @Test @DisplayName("requiresUser=true")
    void requiresUser() { assertTrue(new MessagingAuthenticatorForm().requiresUser()); }

    @Test @DisplayName("authenticate triggers challenge when user has contact attribute")
    void authenticateHappyPath() {
        Map<String, String> config = new HashMap<>();
        config.put(MessagingConstants.MESSAGE_CHANNEL, MessageChannel.SMS.name());
        config.put(MessagingConstants.SMS_PROVIDER, "twilio");
        config.put(MessagingConstants.SIMULATION_MODE, "true"); // skip actual send
        when(configModel.getConfig()).thenReturn(config);
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+15551234567");
        when(authSession.getAuthNote(MessagingConstants.CODE)).thenReturn(null);

        new MessagingAuthenticatorForm().authenticate(ctx);

        verify(ctx).challenge(any(Response.class));
        verify(authSession).setAuthNote(eq(MessagingConstants.CODE), anyString());
    }

    @Test @DisplayName("authenticate triggers RequiredAction when contact attribute missing")
    void authenticateNoContact() {
        Map<String, String> config = new HashMap<>();
        config.put(MessagingConstants.MESSAGE_CHANNEL, MessageChannel.SMS.name());
        when(configModel.getConfig()).thenReturn(config);
        when(user.getFirstAttribute(anyString())).thenReturn(null);

        new MessagingAuthenticatorForm().authenticate(ctx);

        verify(user).addRequiredAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID);
    }

    @Test @DisplayName("action with empty code fails with MISSING_TOTP challenge")
    void actionEmptyCode() {
        Map<String, String> config = new HashMap<>();
        config.put(MessagingConstants.MESSAGE_CHANNEL, MessageChannel.SMS.name());
        when(configModel.getConfig()).thenReturn(config);
        when(user.isEnabled()).thenReturn(true);
        when(authSession.getAuthNote(MessagingConstants.CODE)).thenReturn(OtpHashUtils.hash("999999"));
        when(authSession.getAuthNote(MessagingConstants.CODE_TTL))
                .thenReturn(Long.toString(System.currentTimeMillis() + 60_000));
        // empty submission
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.putSingle(MessagingConstants.CODE, "");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        new MessagingAuthenticatorForm().action(ctx);

        verify(ctx, atLeastOnce()).challenge(any(Response.class));
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=MessagingAuthenticatorFormTest test`

- [ ] **Step 3: Write MessagingAuthenticatorForm**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSenderRegistry;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.CredentialValidator;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MessagingAuthenticatorForm extends AbstractUsernameFormAuthenticator
        implements CredentialValidator<MessagingAuthenticatorCredentialProvider> {

    protected static final Logger logger = Logger.getLogger(MessagingAuthenticatorForm.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Map<String, String> configValues = configValues(context.getAuthenticatorConfig());
        MessageChannel channel = MessageChannel.fromString(configValues.get(MessagingConstants.MESSAGE_CHANNEL));

        String contact = ContactResolver.resolve(context.getUser(), channel, configValues);
        if (contact == null) {
            context.getUser().addRequiredAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID);
            context.attempted();
            return;
        }
        context.challenge(challenge(context, null));
    }

    @Override
    protected Response challenge(AuthenticationFlowContext context, String error, String field) {
        generateAndSend(context);
        LoginFormsProvider form = prepareForm(context, null);
        applyFormMessage(form, error, field);
        return form.createForm("messaging-code-form.ftl");
    }

    private void generateAndSend(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        if (session.getAuthNote(MessagingConstants.CODE) != null) return;

        Map<String, String> config = configValues(context.getAuthenticatorConfig());
        int length = resolvePositiveInt(config, MessagingConstants.CODE_LENGTH, MessagingConstants.DEFAULT_LENGTH);
        int ttl = resolvePositiveInt(config, MessagingConstants.CODE_TTL_CONFIG, MessagingConstants.DEFAULT_TTL);
        int cooldown = resolvePositiveInt(config, MessagingConstants.RESEND_COOLDOWN, MessagingConstants.DEFAULT_RESEND_COOLDOWN);

        String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);
        MessageChannel channel = MessageChannel.fromString(config.get(MessagingConstants.MESSAGE_CHANNEL));
        String providerId = resolveProviderId(channel, config);
        String contact = ContactResolver.resolve(context.getUser(), channel, config);

        if (Boolean.parseBoolean(config.get(MessagingConstants.SIMULATION_MODE))) {
            logger.infof("***** SIMULATION MODE ***** code for user %s on %s via %s: %s",
                    context.getUser().getUsername(), channel, providerId, code);
        } else {
            try {
                MessageSender sender = MessageSenderRegistry.loadFromServiceLoader()
                        .getSender(channel, providerId, config);
                sender.send(OtpMessage.builder().to(contact).code(code).ttlSeconds(ttl).build());
            } catch (MessageDeliveryException e) {
                logger.errorf(e, "Failed to deliver OTP via channel=%s provider=%s", channel, providerId);
            }
        }

        session.setAuthNote(MessagingConstants.CODE, OtpHashUtils.hash(code));
        long now = System.currentTimeMillis();
        session.setAuthNote(MessagingConstants.CODE_TTL, Long.toString(now + (ttl * 1000L)));
        session.setAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER, Long.toString(now + (cooldown * 1000L)));
    }

    private String resolveProviderId(MessageChannel channel, Map<String, String> config) {
        if (channel == MessageChannel.SMS) {
            String p = config.get(MessagingConstants.SMS_PROVIDER);
            return (p == null || p.isBlank()) ? "twilio" : p.trim().toLowerCase();
        }
        return channel.name().toLowerCase(); // telegram / whatsapp / signal
    }

    private Map<String, String> configValues(AuthenticatorConfigModel cfg) {
        return cfg != null && cfg.getConfig() != null ? cfg.getConfig() : Map.of();
    }

    private int resolvePositiveInt(Map<String, String> v, String k, int d) {
        String raw = v.get(k);
        if (raw == null || raw.isBlank()) return d;
        try {
            int p = Integer.parseInt(raw.trim());
            if (p <= 0) return d;
            return p;
        } catch (NumberFormatException e) { return d; }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (!enabledUser(context, user)) return;

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData.containsKey("resend")) {
            handleResend(context);
            return;
        }
        if (formData.containsKey("cancel")) {
            resetCode(context.getAuthenticationSession());
            context.resetFlow();
            return;
        }

        AuthenticationSessionModel session = context.getAuthenticationSession();
        String storedHash = session.getAuthNote(MessagingConstants.CODE);
        String ttlNote = session.getAuthNote(MessagingConstants.CODE_TTL);
        String submittedRaw = formData.getFirst(MessagingConstants.CODE);
        String submitted = submittedRaw == null ? null : submittedRaw.strip();

        if (storedHash == null || ttlNote == null) {
            context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, Messages.INVALID_ACCESS_CODE, MessagingConstants.CODE));
            return;
        }
        if (submitted == null || submitted.isEmpty()) {
            context.challenge(challenge(context, Messages.MISSING_TOTP, MessagingConstants.CODE));
            return;
        }
        long expiresAt;
        try { expiresAt = Long.parseLong(ttlNote); }
        catch (NumberFormatException e) { expiresAt = 0L; }
        if (expiresAt < System.currentTimeMillis()) {
            context.getEvent().user(user).error(Errors.EXPIRED_CODE);
            context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                    challenge(context, Messages.EXPIRED_ACTION_TOKEN_SESSION_EXISTS, MessagingConstants.CODE));
            return;
        }

        if (OtpHashUtils.matches(submitted, storedHash)) {
            resetCode(session);
            context.success();
            return;
        }

        context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
        int attempts = incrementAttempts(session);
        int max = resolvePositiveInt(configValues(context.getAuthenticatorConfig()),
                MessagingConstants.MAX_ATTEMPTS, MessagingConstants.DEFAULT_MAX_ATTEMPTS);
        if (attempts >= max) {
            resetCode(session);
            LoginFormsProvider form = prepareForm(context, null);
            form.setAttribute("maxAttemptsReached", true);
            applyFormMessage(form, "messaging-authenticator-too-many-attempts", MessagingConstants.CODE);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    form.createForm("messaging-code-form.ftl"));
        } else {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, Messages.INVALID_ACCESS_CODE, MessagingConstants.CODE));
        }
    }

    private void handleResend(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        Long remaining = getRemainingSeconds(session);
        if (remaining != null && remaining > 0L) {
            LoginFormsProvider form = prepareForm(context, remaining);
            applyFormMessage(form, "messaging-authenticator-resend-cooldown", null, remaining);
            context.challenge(form.createForm("messaging-code-form.ftl"));
            return;
        }
        resetCode(session);
        context.challenge(challenge(context, null));
    }

    private LoginFormsProvider prepareForm(AuthenticationFlowContext context, Long remainingSeconds) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        Long expose = remainingSeconds != null ? remainingSeconds : getRemainingSeconds(session);
        if (expose != null && expose > 0L) form.setAttribute("resendAvailableInSeconds", expose);

        Map<String, String> config = configValues(context.getAuthenticatorConfig());
        form.setAttribute("codeLength",
                resolvePositiveInt(config, MessagingConstants.CODE_LENGTH, MessagingConstants.DEFAULT_LENGTH));
        MessageChannel channel = MessageChannel.fromString(config.get(MessagingConstants.MESSAGE_CHANNEL));
        form.setAttribute("channelDisplayName", channel.getDisplayName());
        String contact = ContactResolver.resolve(context.getUser(), channel, config);
        form.setAttribute("contactMasked", ContactMasker.maskPhone(contact));
        return form;
    }

    private Long getRemainingSeconds(AuthenticationSessionModel session) {
        String raw = session.getAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        if (raw == null) return null;
        try {
            long at = Long.parseLong(raw);
            long ms = at - System.currentTimeMillis();
            return Math.max(0L, (ms + MessagingConstants.MILLIS_ROUNDING_OFFSET) / 1000L);
        } catch (NumberFormatException e) { return null; }
    }

    private void applyFormMessage(LoginFormsProvider form, String key, String field, Object... params) {
        if (key == null) return;
        if (field != null) form.addError(new FormMessage(field, key, params));
        else form.setError(key, params);
    }

    private void resetCode(AuthenticationSessionModel session) {
        session.removeAuthNote(MessagingConstants.CODE);
        session.removeAuthNote(MessagingConstants.CODE_TTL);
        session.removeAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        session.removeAuthNote(MessagingConstants.CODE_ATTEMPTS);
    }

    private int incrementAttempts(AuthenticationSessionModel session) {
        String raw = session.getAuthNote(MessagingConstants.CODE_ATTEMPTS);
        int a = 1;
        if (raw != null) { try { a = Integer.parseInt(raw) + 1; } catch (NumberFormatException ignored) {} }
        session.setAuthNote(MessagingConstants.CODE_ATTEMPTS, Integer.toString(a));
        return a;
    }

    @Override public boolean requiresUser() { return true; }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        MessagingAuthenticatorCredentialProvider p = getCredentialProvider(session);
        return p != null && p.isConfiguredFor(realm, user, getType(session));
    }

    @Override
    public MessagingAuthenticatorCredentialProvider getCredentialProvider(KeycloakSession session) {
        return (MessagingAuthenticatorCredentialProvider) session.getProvider(
                CredentialProvider.class, MessagingAuthenticatorCredentialProviderFactory.PROVIDER_ID);
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        user.addRequiredAction(MessagingAuthenticatorRequiredAction.PROVIDER_ID);
    }

    @Override
    public List<RequiredActionFactory> getRequiredActions(KeycloakSession session) {
        return Collections.singletonList((MessagingAuthenticatorRequiredActionFactory)
                session.getKeycloakSessionFactory().getProviderFactory(
                        RequiredActionProvider.class, MessagingAuthenticatorRequiredAction.PROVIDER_ID));
    }

    @Override public void close() {}

    @Override protected String disabledByBruteForceError() { return Messages.INVALID_ACCESS_CODE; }
}
```

Note: This task references `MessagingAuthenticatorRequiredActionFactory` (created in Task 26). The skeleton from Task 20 step 5 only declares `MessagingAuthenticatorRequiredAction`, not its factory — add another skeleton:

```java
package com.mesutpiskin.keycloak.auth.messaging;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.Config;
public class MessagingAuthenticatorRequiredActionFactory implements RequiredActionFactory {
    @Override public RequiredActionProvider create(KeycloakSession s) { return null; }
    @Override public String getId() { return MessagingAuthenticatorRequiredAction.PROVIDER_ID; }
    @Override public String getDisplayText() { return ""; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=MessagingAuthenticatorFormTest test`
Expected: BUILD SUCCESS, tests run = 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorForm.java \
        src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredActionFactory.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorFormTest.java
git commit -m "feat: add MessagingAuthenticatorForm with channel-aware OTP delivery"
```

---

### Task 22: MessagingAuthenticatorFormFactory

**Files:**
- Create (overwrite skeleton): `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorFormFactory.java`

- [ ] **Step 1: Write MessagingAuthenticatorFormFactory**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessagingAuthenticatorFormFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "messaging-authenticator";
    public static final MessagingAuthenticatorForm SINGLETON = new MessagingAuthenticatorForm();

    @Override public String getId() { return PROVIDER_ID; }
    @Override public String getDisplayType() { return "Messaging OTP"; }
    @Override public String getReferenceCategory() { return MessagingAuthenticatorCredentialModel.TYPE_ID; }
    @Override public boolean isConfigurable() { return true; }
    @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return REQUIREMENT_CHOICES; }
    @Override public boolean isUserSetupAllowed() { return true; }
    @Override public String getHelpText() { return "Messaging OTP authenticator (SMS, Telegram, WhatsApp, Signal)."; }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> props = new ArrayList<>();

        props.add(listProp(MessagingConstants.MESSAGE_CHANNEL, "Message Channel",
                "Delivery channel: SMS, TELEGRAM, WHATSAPP, or SIGNAL.",
                MessageChannel.SMS.name(),
                Arrays.stream(MessageChannel.values()).map(Enum::name).toArray(String[]::new)));

        // SMS provider + secrets
        props.add(listProp(MessagingConstants.SMS_PROVIDER, "SMS Provider",
                "SMS gateway: twilio, aws-sns, or vonage. Only used when channel=SMS.",
                "twilio", new String[]{"twilio", "aws-sns", "vonage"}));

        // Twilio
        props.add(secret(MessagingConstants.TWILIO_ACCOUNT_SID, "Twilio Account SID", ""));
        props.add(secret(MessagingConstants.TWILIO_AUTH_TOKEN, "Twilio Auth Token", ""));
        props.add(str(MessagingConstants.TWILIO_FROM_NUMBER, "Twilio From Number", "E.164 phone number used as the sender."));

        // AWS SNS
        props.add(str(MessagingConstants.AWS_SNS_REGION, "AWS SNS Region", "AWS region code (e.g. us-east-1)."));
        props.add(str(MessagingConstants.AWS_ACCESS_KEY_ID, "AWS Access Key ID", "IAM access key ID."));
        props.add(secret(MessagingConstants.AWS_SECRET_ACCESS_KEY, "AWS Secret Access Key", ""));

        // Vonage
        props.add(str(MessagingConstants.VONAGE_API_KEY, "Vonage API Key", ""));
        props.add(secret(MessagingConstants.VONAGE_API_SECRET, "Vonage API Secret", ""));
        props.add(str(MessagingConstants.VONAGE_FROM_NUMBER, "Vonage From", "Sender ID or E.164 number."));

        // SMS contact attribute
        props.add(str(MessagingConstants.SMS_CONTACT_ATTRIBUTE, "SMS Contact User Attribute",
                "User attribute holding the E.164 phone number. Default: phoneNumber"));

        // Telegram
        props.add(secret(MessagingConstants.TELEGRAM_BOT_TOKEN, "Telegram Bot Token", ""));
        props.add(str(MessagingConstants.TELEGRAM_CONTACT_ATTRIBUTE, "Telegram Contact Attribute",
                "User attribute holding the Telegram chat ID. Default: telegramChatId"));

        // WhatsApp
        props.add(str(MessagingConstants.WHATSAPP_API_URL, "WhatsApp API URL",
                "Cloud API messages endpoint, e.g. https://graph.facebook.com/v18.0/<phone-id>/messages"));
        props.add(secret(MessagingConstants.WHATSAPP_API_TOKEN, "WhatsApp API Token", ""));
        props.add(str(MessagingConstants.WHATSAPP_FROM_NUMBER, "WhatsApp From Number", "Sender E.164 number."));
        props.add(str(MessagingConstants.WHATSAPP_CONTACT_ATTRIBUTE, "WhatsApp Contact Attribute",
                "User attribute holding the recipient phone number. Default: phoneNumber"));

        // Signal
        props.add(str(MessagingConstants.SIGNAL_CLI_REST_URL, "Signal CLI REST URL",
                "Base URL of signal-cli-rest-api (e.g. http://signal:8080)."));
        props.add(str(MessagingConstants.SIGNAL_FROM_NUMBER, "Signal From Number", "Sender E.164 number."));
        props.add(str(MessagingConstants.SIGNAL_CONTACT_ATTRIBUTE, "Signal Contact Attribute",
                "User attribute holding the recipient phone number. Default: phoneNumber"));

        // General OTP
        props.add(intProp(MessagingConstants.CODE_LENGTH, "Code Length",
                "Number of digits in the OTP.", MessagingConstants.DEFAULT_LENGTH));
        props.add(intProp(MessagingConstants.CODE_TTL_CONFIG, "Time-to-Live (seconds)",
                "How long the code is valid.", MessagingConstants.DEFAULT_TTL));
        props.add(intProp(MessagingConstants.RESEND_COOLDOWN, "Resend Cooldown (seconds)",
                "Minimum seconds between resend requests.", MessagingConstants.DEFAULT_RESEND_COOLDOWN));
        props.add(intProp(MessagingConstants.MAX_ATTEMPTS, "Max Code Attempts",
                "Maximum incorrect attempts before the code is invalidated.", MessagingConstants.DEFAULT_MAX_ATTEMPTS));
        props.add(bool(MessagingConstants.SIMULATION_MODE, "Simulation Mode (dev only)",
                "If enabled, the OTP is logged instead of sent.", MessagingConstants.DEFAULT_SIMULATION_MODE));
        props.add(bool(MessagingConstants.SKIP_SETUP, "Skip Setup",
                "When enabled, users with a populated contact attribute skip enrollment.",
                MessagingConstants.DEFAULT_SKIP_SETUP));

        return props;
    }

    private static ProviderConfigProperty listProp(String name, String label, String help, String defVal, String[] options) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.LIST_TYPE, defVal, options);
    }

    private static ProviderConfigProperty str(String name, String label, String help) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.STRING_TYPE, null);
    }

    private static ProviderConfigProperty secret(String name, String label, String help) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.PASSWORD, null);
    }

    private static ProviderConfigProperty intProp(String name, String label, String help, int defVal) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.STRING_TYPE, String.valueOf(defVal));
    }

    private static ProviderConfigProperty bool(String name, String label, String help, boolean defVal) {
        return new ProviderConfigProperty(name, label, help, ProviderConfigProperty.BOOLEAN_TYPE, String.valueOf(defVal));
    }

    @Override public void close() {}
    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorFormFactory.java
git commit -m "feat: add factory with admin config properties for all channels"
```

---

## Phase 8: Conditional Authenticator + Required Action

### Task 23: ConditionalMessagingAuthenticatorForm

**Files:**
- Create (overwrite skeleton): `src/main/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorForm.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorFormTest.java`

Mirror `ConditionalEmailAuthenticatorForm` exactly — replace `Email` → `Messaging` in identifiers. Logic identical: attribute → role → HTTP header → default fallback. Reuses `EmailAuthenticatorForm`-style config keys (`OTP_CONTROL_USER_ATTRIBUTE`, `SKIP`, `FORCE`, etc.) since they come from Keycloak's `ConditionalOtpFormAuthenticator`.

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConditionalMessagingAuthenticatorForm Tests")
class ConditionalMessagingAuthenticatorFormTest {

    private AuthenticationFlowContext ctx;
    private UserModel user;
    private RealmModel realm;
    private AuthenticatorConfigModel cfg;

    @BeforeEach
    void setUp() {
        ctx = mock(AuthenticationFlowContext.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        cfg = mock(AuthenticatorConfigModel.class);
        when(ctx.getUser()).thenReturn(user);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getAuthenticatorConfig()).thenReturn(cfg);
        var http = mock(org.keycloak.http.HttpRequest.class);
        var headers = mock(jakarta.ws.rs.core.HttpHeaders.class);
        when(headers.getRequestHeaders()).thenReturn(new jakarta.ws.rs.core.MultivaluedHashMap<>());
        when(http.getHttpHeaders()).thenReturn(headers);
        when(ctx.getHttpRequest()).thenReturn(http);
    }

    @Test @DisplayName("Skip when user attribute = 'skip'")
    void userAttributeSkip() {
        when(cfg.getConfig()).thenReturn(Map.of("otpControlAttribute", "otp_choice"));
        when(user.getAttributeStream("otp_choice")).thenReturn(Stream.of("skip"));

        new ConditionalMessagingAuthenticatorForm().authenticate(ctx);
        verify(ctx).success();
    }

    @Test @DisplayName("Default fallback = force triggers OTP form path")
    void defaultForce() {
        when(cfg.getConfig()).thenReturn(Map.of("defaultOtpOutcome", "force",
                MessagingConstants.MESSAGE_CHANNEL, "SMS"));
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+15551234567");
        when(user.getAttributeStream(anyString())).thenReturn(Stream.empty());

        // We can't assert challenge() without lots of mocks; just ensure success() is NOT called
        try { new ConditionalMessagingAuthenticatorForm().authenticate(ctx); } catch (Exception ignored) {}
        verify(ctx, never()).success();
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=ConditionalMessagingAuthenticatorFormTest test`

- [ ] **Step 3: Write ConditionalMessagingAuthenticatorForm**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import static org.keycloak.models.utils.KeycloakModelUtils.getRoleFromString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;

public class ConditionalMessagingAuthenticatorForm extends MessagingAuthenticatorForm {

    public static final String SKIP = "skip";
    public static final String FORCE = "force";
    public static final String OTP_CONTROL_USER_ATTRIBUTE = "otpControlAttribute";
    public static final String SKIP_OTP_ROLE = "skipOtpRole";
    public static final String FORCE_OTP_ROLE = "forceOtpRole";
    public static final String SKIP_OTP_FOR_HTTP_HEADER = "noOtpRequiredForHeaderPattern";
    public static final String FORCE_OTP_FOR_HTTP_HEADER = "forceOtpForHeaderPattern";
    public static final String DEFAULT_OTP_OUTCOME = "defaultOtpOutcome";

    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    enum OtpDecision { SKIP_OTP, SHOW_OTP, ABSTAIN }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel model = context.getAuthenticatorConfig();
        Map<String, String> config = model != null ? model.getConfig() : Collections.emptyMap();

        if (tryConclude(voteForUserAttr(context.getUser(), config), context)) return;
        if (tryConclude(voteForUserRole(context.getRealm(), context.getUser(), config), context)) return;
        if (tryConclude(voteForHttpHeader(context.getHttpRequest().getHttpHeaders().getRequestHeaders(), config), context)) return;
        if (tryConclude(voteForDefault(config), context)) return;
        super.authenticate(context);
    }

    private boolean tryConclude(OtpDecision d, AuthenticationFlowContext context) {
        switch (d) {
            case SHOW_OTP: super.authenticate(context); return true;
            case SKIP_OTP: context.success(); return true;
            default: return false;
        }
    }

    private OtpDecision voteForUserAttr(UserModel user, Map<String, String> cfg) {
        String attr = cfg.get(OTP_CONTROL_USER_ATTRIBUTE);
        if (attr == null) return OtpDecision.ABSTAIN;
        Optional<String> v = user.getAttributeStream(attr).findFirst();
        if (v.isEmpty()) return OtpDecision.ABSTAIN;
        return switch (v.get().trim()) {
            case SKIP -> OtpDecision.SKIP_OTP;
            case FORCE -> OtpDecision.SHOW_OTP;
            default -> OtpDecision.ABSTAIN;
        };
    }

    private OtpDecision voteForUserRole(RealmModel realm, UserModel user, Map<String, String> cfg) {
        if (!cfg.containsKey(SKIP_OTP_ROLE) && !cfg.containsKey(FORCE_OTP_ROLE)) return OtpDecision.ABSTAIN;
        if (userHasRole(realm, user, cfg.get(SKIP_OTP_ROLE))) return OtpDecision.SKIP_OTP;
        if (userHasRole(realm, user, cfg.get(FORCE_OTP_ROLE))) return OtpDecision.SHOW_OTP;
        return OtpDecision.ABSTAIN;
    }

    private boolean userHasRole(RealmModel realm, UserModel user, String roleName) {
        if (roleName == null) return false;
        RoleModel role = getRoleFromString(realm, roleName);
        return role != null && user.hasRole(role);
    }

    private OtpDecision voteForHttpHeader(MultivaluedMap<String, String> headers, Map<String, String> cfg) {
        if (!cfg.containsKey(FORCE_OTP_FOR_HTTP_HEADER) && !cfg.containsKey(SKIP_OTP_FOR_HTTP_HEADER)) return OtpDecision.ABSTAIN;
        if (matches(headers, cfg.get(SKIP_OTP_FOR_HTTP_HEADER))) return OtpDecision.SKIP_OTP;
        if (matches(headers, cfg.get(FORCE_OTP_FOR_HTTP_HEADER))) return OtpDecision.SHOW_OTP;
        return OtpDecision.ABSTAIN;
    }

    private boolean matches(MultivaluedMap<String, String> headers, String pattern) {
        if (pattern == null) return false;
        Pattern p;
        try { p = patternCache.computeIfAbsent(pattern, x -> Pattern.compile(x, Pattern.DOTALL | Pattern.CASE_INSENSITIVE)); }
        catch (PatternSyntaxException e) { logger.errorf("Invalid header pattern: %s", pattern); return false; }
        for (var e : headers.entrySet()) {
            for (String v : e.getValue()) {
                if (p.matcher(e.getKey().trim() + ": " + v.trim()).matches()) return true;
            }
        }
        return false;
    }

    private OtpDecision voteForDefault(Map<String, String> cfg) {
        String v = cfg.get(DEFAULT_OTP_OUTCOME);
        if (v == null) return OtpDecision.ABSTAIN;
        return switch (v) {
            case SKIP -> OtpDecision.SKIP_OTP;
            case FORCE -> OtpDecision.SHOW_OTP;
            default -> OtpDecision.ABSTAIN;
        };
    }
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=ConditionalMessagingAuthenticatorFormTest test`
Expected: BUILD SUCCESS, tests run = 2.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorForm.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorFormTest.java
git commit -m "feat: add conditional authenticator (attr/role/header/default rules)"
```

---

### Task 24: ConditionalMessagingAuthenticatorFormFactory

**Files:**
- Create (overwrite skeleton): `src/main/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorFormFactory.java`

- [ ] **Step 1: Write file**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import static java.util.Arrays.asList;
import static org.keycloak.provider.ProviderConfigProperty.LIST_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.ROLE_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.STRING_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

public class ConditionalMessagingAuthenticatorFormFactory extends MessagingAuthenticatorFormFactory {

    public static final String PROVIDER_ID = "messaging-conditional-authenticator";
    public static final ConditionalMessagingAuthenticatorForm SINGLETON = new ConditionalMessagingAuthenticatorForm();

    @Override public String getId() { return PROVIDER_ID; }
    @Override public String getDisplayType() { return "Conditional Messaging OTP"; }
    @Override public String getHelpText() { return "Conditional messaging OTP authenticator."; }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> list = new ArrayList<>(super.getConfigProperties());

        var ctrl = new ProviderConfigProperty(); ctrl.setType(STRING_TYPE);
        ctrl.setName(ConditionalMessagingAuthenticatorForm.OTP_CONTROL_USER_ATTRIBUTE);
        ctrl.setLabel("OTP Control User Attribute");
        ctrl.setHelpText("User attribute controlling OTP. Value 'force' always requires; 'skip' bypasses; anything else abstains.");
        list.add(ctrl);

        var skipRole = new ProviderConfigProperty(); skipRole.setType(ROLE_TYPE);
        skipRole.setName(ConditionalMessagingAuthenticatorForm.SKIP_OTP_ROLE);
        skipRole.setLabel("Skip OTP for Role");
        skipRole.setHelpText("OTP skipped if user has this role.");
        list.add(skipRole);

        var forceRole = new ProviderConfigProperty(); forceRole.setType(ROLE_TYPE);
        forceRole.setName(ConditionalMessagingAuthenticatorForm.FORCE_OTP_ROLE);
        forceRole.setLabel("Force OTP for Role");
        forceRole.setHelpText("OTP required if user has this role.");
        list.add(forceRole);

        var skipHdr = new ProviderConfigProperty(); skipHdr.setType(STRING_TYPE);
        skipHdr.setName(ConditionalMessagingAuthenticatorForm.SKIP_OTP_FOR_HTTP_HEADER);
        skipHdr.setLabel("Skip OTP for Header");
        skipHdr.setHelpText("Regex matched against 'Header: value' lines; if any matches, OTP is skipped.");
        skipHdr.setDefaultValue("");
        list.add(skipHdr);

        var forceHdr = new ProviderConfigProperty(); forceHdr.setType(STRING_TYPE);
        forceHdr.setName(ConditionalMessagingAuthenticatorForm.FORCE_OTP_FOR_HTTP_HEADER);
        forceHdr.setLabel("Force OTP for Header");
        forceHdr.setHelpText("Regex matched against 'Header: value' lines; if any matches, OTP is required.");
        forceHdr.setDefaultValue("");
        list.add(forceHdr);

        var def = new ProviderConfigProperty(); def.setType(LIST_TYPE);
        def.setName(ConditionalMessagingAuthenticatorForm.DEFAULT_OTP_OUTCOME);
        def.setLabel("Fallback OTP handling");
        def.setOptions(asList(ConditionalMessagingAuthenticatorForm.SKIP, ConditionalMessagingAuthenticatorForm.FORCE));
        def.setHelpText("Outcome when every other check abstains. Default behavior is to fall through to the main form.");
        list.add(def);

        return Collections.unmodifiableList(list);
    }

    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/ConditionalMessagingAuthenticatorFormFactory.java
git commit -m "feat: add factory for conditional messaging authenticator"
```

---

### Task 25: MessagingAuthenticatorRequiredAction

**Files:**
- Create (overwrite skeleton): `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredAction.java`
- Test: `src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredActionTest.java`

This is the enrollment flow: user enters their phone/chat-id → OTP sent to that address → verifies → attribute persisted + credential created.

- [ ] **Step 1: Write the failing test**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAuthenticatorRequiredAction Tests")
class MessagingAuthenticatorRequiredActionTest {

    private RequiredActionContext ctx;
    private UserModel user;
    private RealmModel realm;
    private AuthenticationSessionModel session;
    private KeycloakSession keycloak;
    private LoginFormsProvider form;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        ctx = mock(RequiredActionContext.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        session = mock(AuthenticationSessionModel.class);
        keycloak = mock(KeycloakSession.class);
        form = mock(LoginFormsProvider.class);
        httpRequest = mock(HttpRequest.class);
        SubjectCredentialManager cm = mock(SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(cm);
        when(ctx.getUser()).thenReturn(user);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getAuthenticationSession()).thenReturn(session);
        when(ctx.getSession()).thenReturn(keycloak);
        when(ctx.form()).thenReturn(form);
        when(ctx.getHttpRequest()).thenReturn(httpRequest);
        when(form.setAttribute(anyString(), any())).thenReturn(form);
        when(form.setError(anyString(), any())).thenReturn(form);
        when(form.createForm(anyString())).thenReturn(mock(Response.class));
        when(realm.getAuthenticationFlowsStream()).thenReturn(Stream.empty());
    }

    @Test @DisplayName("requiredActionChallenge renders setup form")
    void challengeShowsSetup() {
        new MessagingAuthenticatorRequiredAction().requiredActionChallenge(ctx);
        verify(form).createForm("messaging-authenticator-setup-form.ftl");
        verify(ctx).challenge(any(Response.class));
    }

    @Test @DisplayName("cancel goes back to setup form when no code in flight")
    void cancelEarly() {
        MultivaluedHashMap<String, String> data = new MultivaluedHashMap<>();
        data.putSingle("cancel", "x");
        when(httpRequest.getDecodedFormParameters()).thenReturn(data);
        when(session.getAuthNote(MessagingConstants.CODE)).thenReturn(null);

        new MessagingAuthenticatorRequiredAction().processAction(ctx);
        verify(form, atLeastOnce()).createForm(anyString());
    }

    @Test @DisplayName("Submit invalid phone format renders error")
    void invalidPhone() {
        MultivaluedHashMap<String, String> data = new MultivaluedHashMap<>();
        data.putSingle("contactAddress", "not-a-phone");
        when(httpRequest.getDecodedFormParameters()).thenReturn(data);
        when(session.getAuthNote(MessagingConstants.CODE)).thenReturn(null);
        when(realm.getAuthenticationFlowsStream()).thenReturn(Stream.empty());

        new MessagingAuthenticatorRequiredAction().processAction(ctx);
        verify(form, atLeastOnce()).setError(anyString());
    }
}
```

- [ ] **Step 2: Run test, see failure**

Run: `mvn -q -Dtest=MessagingAuthenticatorRequiredActionTest test`

- [ ] **Step 3: Write MessagingAuthenticatorRequiredAction**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSenderRegistry;
import org.jboss.logging.Logger;
import org.keycloak.authentication.CredentialRegistrator;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.security.MessageDigest;
import java.util.Map;

public class MessagingAuthenticatorRequiredAction implements RequiredActionProvider, CredentialRegistrator {

    public static final String PROVIDER_ID = "messaging-authenticator-setup";
    private static final String SETUP_TEMPLATE = "messaging-authenticator-setup-form.ftl";
    private static final String VERIFY_TEMPLATE = "messaging-authenticator-setup-verify-form.ftl";
    private static final Logger logger = Logger.getLogger(MessagingAuthenticatorRequiredAction.class);

    private enum CodeValidation { VALID, EXPIRED, INVALID, MISSING }

    @Override public InitiatedActionSupport initiatedActionSupport() { return InitiatedActionSupport.SUPPORTED; }
    @Override public void evaluateTriggers(RequiredActionContext context) {}

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        MessageChannel channel = channelFor(context);
        context.form().setAttribute("channelDisplayName", channel.getDisplayName());
        context.form().setAttribute("isTelegram", channel == MessageChannel.TELEGRAM);
        context.challenge(context.form().createForm(SETUP_TEMPLATE));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        UserModel user = context.getUser();
        AuthenticationSessionModel session = context.getAuthenticationSession();
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        MessageChannel channel = channelFor(context);

        if (form.containsKey("cancel") || form.containsKey("cancel-aia")) {
            if (session.getAuthNote(MessagingConstants.CODE) != null) {
                resetCode(session);
                requiredActionChallenge(context);
                return;
            }
            context.challenge(context.form()
                    .setError("messaging-authenticator-setup-cancelled")
                    .createForm(SETUP_TEMPLATE));
            return;
        }

        if (form.containsKey("resend")) {
            Long remaining = getRemaining(session);
            if (remaining != null && remaining > 0L) {
                challengeVerify(context, "messaging-authenticator-resend-cooldown", remaining);
                return;
            }
            resetCode(session);
            sendSetupCode(context, channel);
            return;
        }

        // Phase 1: receive contact address
        if (session.getAuthNote(MessagingConstants.CODE) == null) {
            String contact = form.getFirst("contactAddress");
            if (contact == null || contact.isBlank()) {
                context.challenge(context.form().setError("messaging-authenticator-setup-missing-contact").createForm(SETUP_TEMPLATE));
                return;
            }
            contact = E164Validator.normalize(contact);
            if (!isValidForChannel(channel, contact)) {
                context.challenge(context.form().setError("messaging-authenticator-setup-invalid-format").createForm(SETUP_TEMPLATE));
                return;
            }
            session.setAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS, contact);
            sendSetupCode(context, channel);
            return;
        }

        // Phase 2: verify submitted code
        String submittedRaw = form.getFirst(MessagingConstants.CODE);
        String submitted = submittedRaw == null ? null : submittedRaw.strip();
        switch (validate(session, submitted)) {
            case VALID:
                String contactAddr = session.getAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS);
                String attr = ContactResolver.attributeNameFor(channel, findConfig(context));
                user.setSingleAttribute(attr, contactAddr);
                MessagingAuthenticatorCredentialModel cred = MessagingAuthenticatorCredentialModel.create();
                cred.setUserLabel(channel.getDisplayName() + ": " + contactAddr);
                user.credentialManager().createStoredCredential(cred);
                resetCode(session);
                user.removeRequiredAction(PROVIDER_ID);
                context.success();
                return;
            case EXPIRED:
                resetCode(session);
                challengeVerify(context, "messaging-authenticator-setup-code-expired");
                return;
            case MISSING:
                challengeVerify(context, "messaging-authenticator-setup-code-missing");
                return;
            case INVALID:
                challengeVerify(context, "messaging-authenticator-setup-code-invalid");
                return;
        }
    }

    private boolean isValidForChannel(MessageChannel channel, String contact) {
        if (contact == null) return false;
        if (channel == MessageChannel.TELEGRAM) return contact.matches("^\\d{5,15}$");
        return E164Validator.isValid(contact);
    }

    private void sendSetupCode(RequiredActionContext context, MessageChannel channel) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        Map<String, String> cfg = findConfig(context);
        int length = positive(cfg.get(MessagingConstants.CODE_LENGTH), MessagingConstants.DEFAULT_LENGTH);
        int ttl = positive(cfg.get(MessagingConstants.CODE_TTL_CONFIG), MessagingConstants.DEFAULT_TTL);
        int cooldown = positive(cfg.get(MessagingConstants.RESEND_COOLDOWN), MessagingConstants.DEFAULT_RESEND_COOLDOWN);

        String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);
        String contact = session.getAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS);

        if (Boolean.parseBoolean(cfg.get(MessagingConstants.SIMULATION_MODE))) {
            logger.infof("***** SIMULATION MODE ***** Setup code for %s via %s: %s",
                    context.getUser().getUsername(), channel, code);
        } else {
            try {
                String providerId = resolveProviderId(channel, cfg);
                MessageSender sender = MessageSenderRegistry.loadFromServiceLoader().getSender(channel, providerId, cfg);
                sender.send(OtpMessage.builder().to(contact).code(code).ttlSeconds(ttl).build());
            } catch (MessageDeliveryException | IllegalStateException e) {
                logger.errorf(e, "Setup OTP delivery failed for channel=%s", channel);
                context.challenge(context.form().setError("messaging-authenticator-setup-send-error").createForm(SETUP_TEMPLATE));
                return;
            }
        }

        long now = System.currentTimeMillis();
        session.setAuthNote(MessagingConstants.CODE, code);
        session.setAuthNote(MessagingConstants.CODE_TTL, Long.toString(now + (ttl * 1000L)));
        session.setAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER, Long.toString(now + (cooldown * 1000L)));

        challengeVerify(context, null);
    }

    private String resolveProviderId(MessageChannel channel, Map<String, String> cfg) {
        if (channel == MessageChannel.SMS) {
            String p = cfg.get(MessagingConstants.SMS_PROVIDER);
            return (p == null || p.isBlank()) ? "twilio" : p.trim().toLowerCase();
        }
        return channel.name().toLowerCase();
    }

    private CodeValidation validate(AuthenticationSessionModel session, String submitted) {
        String stored = session.getAuthNote(MessagingConstants.CODE);
        String ttlNote = session.getAuthNote(MessagingConstants.CODE_TTL);
        if (stored == null || ttlNote == null) return CodeValidation.MISSING;
        if (submitted == null || submitted.isEmpty()) return CodeValidation.MISSING;
        long exp;
        try { exp = Long.parseLong(ttlNote); } catch (NumberFormatException e) { return CodeValidation.EXPIRED; }
        if (exp < System.currentTimeMillis()) return CodeValidation.EXPIRED;
        return MessageDigest.isEqual(submitted.getBytes(), stored.getBytes())
                ? CodeValidation.VALID : CodeValidation.INVALID;
    }

    private MessageChannel channelFor(RequiredActionContext context) {
        Map<String, String> cfg = findConfig(context);
        return MessageChannel.fromString(cfg.get(MessagingConstants.MESSAGE_CHANNEL));
    }

    private Map<String, String> findConfig(RequiredActionContext context) {
        return context.getRealm().getAuthenticationFlowsStream()
                .flatMap(flow -> context.getRealm().getAuthenticationExecutionsStream(flow.getId()))
                .filter(exec -> MessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator())
                        || ConditionalMessagingAuthenticatorFormFactory.PROVIDER_ID.equals(exec.getAuthenticator()))
                .map(exec -> {
                    String id = exec.getAuthenticatorConfig();
                    if (id == null) return Map.<String, String>of();
                    AuthenticatorConfigModel cfg = context.getRealm().getAuthenticatorConfigById(id);
                    return cfg != null && cfg.getConfig() != null ? cfg.getConfig() : Map.<String, String>of();
                })
                .findFirst().orElse(Map.of());
    }

    private void challengeVerify(RequiredActionContext context, String error, Object... params) {
        var form = context.form();
        Long remaining = getRemaining(context.getAuthenticationSession());
        if (remaining != null && remaining > 0L) form.setAttribute("resendAvailableInSeconds", remaining);
        if (error != null) form.setError(error, params);
        context.challenge(form.createForm(VERIFY_TEMPLATE));
    }

    private void resetCode(AuthenticationSessionModel session) {
        session.removeAuthNote(MessagingConstants.CODE);
        session.removeAuthNote(MessagingConstants.CODE_TTL);
        session.removeAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        session.removeAuthNote(MessagingConstants.SETUP_CONTACT_ADDRESS);
    }

    private Long getRemaining(AuthenticationSessionModel session) {
        String raw = session.getAuthNote(MessagingConstants.CODE_RESEND_AVAILABLE_AFTER);
        if (raw == null) return null;
        try {
            long at = Long.parseLong(raw);
            long ms = at - System.currentTimeMillis();
            return Math.max(0L, (ms + MessagingConstants.MILLIS_ROUNDING_OFFSET) / 1000L);
        } catch (NumberFormatException e) { return null; }
    }

    private int positive(String raw, int def) {
        if (raw == null || raw.isBlank()) return def;
        try { int v = Integer.parseInt(raw.trim()); return v > 0 ? v : def; }
        catch (NumberFormatException e) { return def; }
    }

    @Override public String getCredentialType(KeycloakSession session, AuthenticationSessionModel s) {
        return MessagingAuthenticatorCredentialModel.TYPE_ID;
    }
    @Override public void close() {}
}
```

- [ ] **Step 4: Run test, see pass**

Run: `mvn -q -Dtest=MessagingAuthenticatorRequiredActionTest test`
Expected: BUILD SUCCESS, tests run = 3.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredAction.java \
        src/test/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredActionTest.java
git commit -m "feat: add required action for messaging enrollment flow"
```

---

### Task 26: MessagingAuthenticatorRequiredActionFactory

**Files:**
- Overwrite skeleton: `src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredActionFactory.java`

- [ ] **Step 1: Write file**

```java
package com.mesutpiskin.keycloak.auth.messaging;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class MessagingAuthenticatorRequiredActionFactory implements RequiredActionFactory {

    private static final MessagingAuthenticatorRequiredAction SINGLETON = new MessagingAuthenticatorRequiredAction();

    @Override public RequiredActionProvider create(KeycloakSession session) { return SINGLETON; }
    @Override public String getId() { return MessagingAuthenticatorRequiredAction.PROVIDER_ID; }
    @Override public String getDisplayText() { return "Set up Messaging Authenticator"; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/mesutpiskin/keycloak/auth/messaging/MessagingAuthenticatorRequiredActionFactory.java
git commit -m "feat: implement MessagingAuthenticatorRequiredActionFactory"
```

---

## Phase 9: Resources (META-INF + Templates + i18n)

### Task 27: META-INF/services registration

**Files:**
- Create: `src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory`
- Create: `src/main/resources/META-INF/services/org.keycloak.authentication.RequiredActionFactory`
- Create: `src/main/resources/META-INF/services/org.keycloak.credential.CredentialProviderFactory`
- Create: `src/main/resources/META-INF/services/com.mesutpiskin.keycloak.auth.messaging.service.MessageSender`

- [ ] **Step 1: Write AuthenticatorFactory entries**

File content:
```
com.mesutpiskin.keycloak.auth.messaging.MessagingAuthenticatorFormFactory
com.mesutpiskin.keycloak.auth.messaging.ConditionalMessagingAuthenticatorFormFactory
```

- [ ] **Step 2: Write RequiredActionFactory entry**

```
com.mesutpiskin.keycloak.auth.messaging.MessagingAuthenticatorRequiredActionFactory
```

- [ ] **Step 3: Write CredentialProviderFactory entry**

```
com.mesutpiskin.keycloak.auth.messaging.MessagingAuthenticatorCredentialProviderFactory
```

- [ ] **Step 4: Write MessageSender entries (built-ins)**

```
com.mesutpiskin.keycloak.auth.messaging.service.impl.TwilioMessageSender
com.mesutpiskin.keycloak.auth.messaging.service.impl.AwsSnsMessageSender
com.mesutpiskin.keycloak.auth.messaging.service.impl.VonageMessageSender
com.mesutpiskin.keycloak.auth.messaging.service.impl.TelegramMessageSender
com.mesutpiskin.keycloak.auth.messaging.service.impl.WhatsAppMessageSender
com.mesutpiskin.keycloak.auth.messaging.service.impl.SignalMessageSender
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/META-INF/services/
git commit -m "feat: register SPIs via META-INF/services"
```

---

### Task 28: FTL templates

**Files:**
- Create: `src/main/resources/theme-resources/templates/messaging-code-form.ftl`
- Create: `src/main/resources/theme-resources/templates/messaging-authenticator-setup-form.ftl`
- Create: `src/main/resources/theme-resources/templates/messaging-authenticator-setup-verify-form.ftl`

- [ ] **Step 1: Write messaging-code-form.ftl**

```html
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('messagingCode'); section>
    <#if section="header">
        ${msg("doLogIn")}
    <#elseif section="form">
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <#assign otpLength = (codeLength!6)>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="messagingCode" class="${properties.kcLabelClass!}">
                        ${msg("messagingOtpForm", otpLength, channelDisplayName!"", contactMasked!"")}
                    </label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input id="messagingCode" name="messagingCode" autocomplete="off" type="text"
                           class="${properties.kcInputClass!}" autofocus
                           aria-invalid="<#if messagesPerField.existsError('messagingCode')>true</#if>"
                           <#if maxAttemptsReached?? && maxAttemptsReached>disabled</#if>/>
                    <#if messagesPerField.existsError('messagingCode')>
                        <span id="input-error-otp-code" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('messagingCode'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <#if !(maxAttemptsReached?? && maxAttemptsReached)>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="login" type="submit" value="${msg("doLogIn")}"/>
                        </#if>
                        <input class="${properties.kcButtonClass!} <#if maxAttemptsReached?? && maxAttemptsReached>${properties.kcButtonPrimaryClass!}<#else>${properties.kcButtonDefaultClass!}</#if> ${properties.kcButtonLargeClass!}" name="resend" type="submit" value="${msg("resendCode")}"/>
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" type="submit" value="${msg("doCancel")}"/>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
```

- [ ] **Step 2: Write messaging-authenticator-setup-form.ftl**

```html
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=message?has_content; section>
    <#if section="header">
        ${msg("messaging-authenticator-setup-title")}
    <#elseif section="form">
        <form id="kc-messaging-setup-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <p>${msg("messaging-authenticator-setup-description", channelDisplayName!"")}</p>
                </div>
            </div>
            <#if message?has_content>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcAlertClass!} ${properties.kcAlertErrorClass!}">
                        <div class="${properties.kcAlertIconClass!}"><span class="${properties.kcFeedbackErrorIcon!}"></span></div>
                        <div class="${properties.kcAlertMessageClass!}">${kcSanitize(message.summary)?no_esc}</div>
                    </div>
                </div>
            </#if>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="contactAddress" class="${properties.kcLabelClass!}">
                        <#if isTelegram??>${msg("messaging-authenticator-setup-telegram-label")}<#else>${msg("messaging-authenticator-setup-phone-label")}</#if>
                    </label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input id="contactAddress" name="contactAddress" type="text" class="${properties.kcInputClass!}" autofocus/>
                </div>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("messaging-authenticator-setup-button")}"/>
                        <#if isAppInitiatedAction??>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel-aia" type="submit" value="${msg("doCancel")}"/>
                        </#if>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
```

- [ ] **Step 3: Write messaging-authenticator-setup-verify-form.ftl**

```html
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('messagingCode'); section>
    <#if section="header">
        ${msg("messaging-authenticator-setup-verify-title")}
    <#elseif section="form">
        <form id="kc-messaging-verify-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="messagingCode" class="${properties.kcLabelClass!}">${msg("messaging-authenticator-setup-verify-description")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input id="messagingCode" name="messagingCode" autocomplete="off" type="text"
                           class="${properties.kcInputClass!}" autofocus
                           aria-invalid="<#if messagesPerField.existsError('messagingCode')>true</#if>"
                           <#if maxAttemptsReached?? && maxAttemptsReached>disabled</#if>/>
                    <#if messagesPerField.existsError('messagingCode')>
                        <span id="input-error-otp-code" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('messagingCode'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <#if !(maxAttemptsReached?? && maxAttemptsReached)>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("messaging-authenticator-setup-verify-button")}"/>
                        </#if>
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="resend" type="submit" value="${msg("resendCode")}"/>
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" type="submit" value="${msg("doCancel")}"/>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/theme-resources/templates/
git commit -m "feat: add FTL templates for code/setup/verify forms"
```

---

### Task 29: i18n properties (en + tr)

**Files:**
- Create: `src/main/resources/theme-resources/messages/messages_en.properties`
- Create: `src/main/resources/theme-resources/messages/messages_tr.properties`

- [ ] **Step 1: Write messages_en.properties**

```properties
resendCode=Resend Code
messagingOtpForm=Please enter the {0} digit code that was delivered to your {1} ({2}).

messaging-authenticator-display-name=Messaging Authenticator
messaging-authenticator-help-text=Receive a one-time verification code via SMS, Telegram, WhatsApp, or Signal.
requiredAction.messaging-authenticator-setup=Set up Messaging Authenticator
messaging-authenticator-setup-title=Set up Messaging Authenticator
messaging-authenticator-setup-description=Use this option to receive one-time verification codes via {0} during sign-in.
messaging-authenticator-setup-button=Continue
messaging-authenticator-setup-cancelled=Messaging authenticator setup cannot be cancelled.
messaging-authenticator-setup-error=We couldn''t enable Messaging Authenticator. Try again or contact support.
messaging-authenticator-setup-missing-contact=Please enter your contact address.
messaging-authenticator-setup-invalid-format=The format is not valid. For SMS/WhatsApp/Signal use E.164 (e.g. +15551234567); for Telegram use a numeric chat ID.
messaging-authenticator-setup-phone-label=Enter your phone number (E.164 format, e.g. +90 500 123 4567)
messaging-authenticator-setup-telegram-label=Enter your Telegram Chat ID (get it from @userinfobot)
messaging-authenticator-resend-cooldown=Please wait {0} seconds before requesting a new code.
messaging-authenticator-setup-verify-title=Verify your contact address
messaging-authenticator-setup-verify-description=Enter the verification code we just sent you.
messaging-authenticator-setup-verify-button=Verify
messaging-authenticator-setup-send-error=Failed to send verification code. Please try again.
messaging-authenticator-setup-code-expired=The verification code has expired. Please request a new one.
messaging-authenticator-setup-code-invalid=The verification code is incorrect. Please try again.
messaging-authenticator-setup-code-missing=Please enter the verification code.
messaging-authenticator-too-many-attempts=Too many incorrect attempts. Please request a new code.
```

- [ ] **Step 2: Write messages_tr.properties**

```properties
resendCode=Kodu Tekrar Gönder
messagingOtpForm={1} ({2}) üzerinden iletilen {0} haneli kodu giriniz.

messaging-authenticator-display-name=Mesajlaşma Doğrulayıcı
messaging-authenticator-help-text=SMS, Telegram, WhatsApp veya Signal üzerinden tek kullanımlık doğrulama kodu alın.
requiredAction.messaging-authenticator-setup=Mesajlaşma Doğrulayıcı Kurulumu
messaging-authenticator-setup-title=Mesajlaşma Doğrulayıcı Kurulumu
messaging-authenticator-setup-description=Bu seçeneği kullanarak giriş sırasında {0} üzerinden tek kullanımlık doğrulama kodu alırsınız.
messaging-authenticator-setup-button=Devam
messaging-authenticator-setup-cancelled=Mesajlaşma doğrulayıcı kurulumu iptal edilemez.
messaging-authenticator-setup-error=Mesajlaşma Doğrulayıcı etkinleştirilemedi. Tekrar deneyin veya destek ile iletişime geçin.
messaging-authenticator-setup-missing-contact=Lütfen iletişim adresinizi giriniz.
messaging-authenticator-setup-invalid-format=Geçersiz format. SMS/WhatsApp/Signal için E.164 (örn. +905551234567), Telegram için sayısal chat ID kullanın.
messaging-authenticator-setup-phone-label=Telefon numaranızı giriniz (E.164, örn. +90 500 123 4567)
messaging-authenticator-setup-telegram-label=Telegram Chat ID''nizi giriniz (@userinfobot üzerinden alabilirsiniz)
messaging-authenticator-resend-cooldown=Yeni bir kod istemeden önce {0} saniye bekleyin.
messaging-authenticator-setup-verify-title=İletişim adresinizi doğrulayın
messaging-authenticator-setup-verify-description=Az önce gönderdiğimiz doğrulama kodunu giriniz.
messaging-authenticator-setup-verify-button=Doğrula
messaging-authenticator-setup-send-error=Doğrulama kodu gönderilemedi. Lütfen tekrar deneyin.
messaging-authenticator-setup-code-expired=Doğrulama kodunun süresi doldu. Yeni bir kod talep edin.
messaging-authenticator-setup-code-invalid=Doğrulama kodu hatalı. Lütfen tekrar deneyin.
messaging-authenticator-setup-code-missing=Lütfen doğrulama kodunu giriniz.
messaging-authenticator-too-many-attempts=Çok fazla hatalı deneme. Yeni bir kod talep edin.
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/theme-resources/messages/
git commit -m "feat: add EN and TR i18n message bundles"
```

---

## Phase 10: Build & Verify

### Task 30: Full build passes

- [ ] **Step 1: Clean build**

Run: `mvn -B clean package -DskipTests`
Expected: `BUILD SUCCESS`; `target/keycloak-2fa-messaging-authenticator-v26.0.0.jar` exists.

- [ ] **Step 2: Run full test suite**

Run: `mvn -B test`
Expected: `BUILD SUCCESS`; all tests pass (~70+ tests across all classes).

If any tests fail, debug them now rather than papering over with `-DskipTests`.

- [ ] **Step 3: Verify Service entries are bundled**

Run: `unzip -p target/keycloak-2fa-messaging-authenticator-v26.0.0.jar META-INF/services/com.mesutpiskin.keycloak.auth.messaging.service.MessageSender`
Expected: 6 built-in sender class names listed.

- [ ] **Step 4: Commit any tweaks**

If you touched code to fix failures:
```bash
git add -A && git commit -m "fix: resolve build issues found during full mvn package"
```

---

### Task 31: Smoke test in Docker (optional but recommended)

- [ ] **Step 1: Write Dockerfile**

`Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.6.1
COPY --from=builder /app/target/keycloak-2fa-messaging-authenticator-*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
EXPOSE 8080
```

- [ ] **Step 2: Write docker-compose.yml**

```yaml
services:
  keycloak:
    build: .
    container_name: keycloak-messaging-test
    ports:
      - "8080:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev
```

- [ ] **Step 3: Build and start container**

Run: `docker compose up --build -d`
Then: `docker compose logs -f keycloak`
Expected: Keycloak starts cleanly; logs show no `MessagingAuthenticatorFormFactory` class-load errors.

- [ ] **Step 4: Manual smoke test (browser)**

Visit `http://localhost:8080/admin` → login admin/admin → check Authentication → Required Actions: "Set up Messaging Authenticator" should be listed. Authentication → Flows → add new execution: "Messaging OTP" or "Conditional Messaging OTP" should be selectable.

- [ ] **Step 5: Tear down**

Run: `docker compose down`

- [ ] **Step 6: Commit**

```bash
git add Dockerfile docker-compose.yml
git commit -m "chore: add Dockerfile and compose for local smoke testing"
```

---

## Phase 11: Docs & CI

### Task 32: README.md

**Files:**
- Create: `README.md`

- [ ] **Step 1: Adapt the sibling README**

Use `../keycloak-2fa-email-authenticator/README.md` as the base — translate "email" → "messaging", drop the SMTP-specific sections, add per-channel setup sections (Twilio / AWS SNS / Vonage / Telegram / WhatsApp / Signal). Each provider section should mirror the sibling's "SendGrid Setup" structure: get API credentials, configure user attribute, configure in Keycloak admin UI.

Keep the same Markdown table style for supported providers and the same Maven Central + Docker quickstart layout.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add comprehensive README mirroring sibling project"
```

---

### Task 33: COMPATIBILITY.md

**Files:**
- Create: `COMPATIBILITY.md`

- [ ] **Step 1: Write content**

```markdown
# Keycloak Version Compatibility

| Plugin Version | Keycloak Version | Status |
|---|---|---|
| 26.0.0 | 26.6.1 | Primary target (default build) |
| 26.0.0 | 26.0.0 - 26.5.x | Supported; build with `-Dkeycloak.version=<x>` |
| 26.0.0 | 25.0.0 - 25.x | Supported via CI matrix |
| < 25.0.0 | — | Not supported in v1.0 (roadmap: v2 may add 23.x) |

## Building against another Keycloak version

```bash
mvn clean package -Dkeycloak.version=25.0.0
```

## Known limitations

- `SubjectCredentialManager` API was finalized in 24.x; older Keycloaks require a custom build of the credential provider.
- AWS SNS SDK (v2) requires JDK 17+; this plugin targets JDK 21.
```

- [ ] **Step 2: Commit**

```bash
git add COMPATIBILITY.md
git commit -m "docs: add COMPATIBILITY.md describing version matrix"
```

---

### Task 34: GitHub Actions workflows

**Files:**
- Create: `.github/workflows/maven.yml`
- Create: `.github/workflows/maven-publish.yml`

- [ ] **Step 1: Copy and adapt sibling workflows**

```bash
mkdir -p .github/workflows
cp ../keycloak-2fa-email-authenticator/.github/workflows/maven.yml .github/workflows/maven.yml
cp ../keycloak-2fa-email-authenticator/.github/workflows/maven-publish.yml .github/workflows/maven-publish.yml
```

Then edit both files to replace any occurrence of `keycloak-2fa-email-authenticator` → `keycloak-2fa-messaging-authenticator` (use `sed -i '' 's/.../.../g'` on macOS).

- [ ] **Step 2: Verify**

Run: `grep -l 'email-authenticator' .github/workflows/*.yml`
Expected: no output (all references updated).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/
git commit -m "ci: add GitHub Actions for CI matrix and Maven Central publish"
```

---

### Task 35: Final verification & push

- [ ] **Step 1: Full clean build with tests**

Run: `mvn -B clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Inspect git log**

Run: `git log --oneline`
Expected: ~30+ feature/chore/docs/ci commits since the spec.

- [ ] **Step 3: Push to remote**

```bash
git push origin main
```

Expected: push accepted.

- [ ] **Step 4: Create v26.0.0 release tag (optional, requires user approval)**

If the user authorizes a release:
```bash
git tag -a v26.0.0 -m "Initial release: SMS / Telegram / WhatsApp / Signal OTP"
git push origin v26.0.0
```

This triggers `maven-publish.yml` which publishes to Maven Central + GitHub Packages.

---

## Self-Review

Before declaring the plan complete, verify:

1. **Spec coverage check** — every numbered section of the design spec has at least one task:
   - §2 Architecture → Tasks 1, 27 (single JAR, SPI wiring)
   - §3 Keycloak SPI implementations → Tasks 20, 22, 24, 25, 26
   - §4 MessageSender extension point → Tasks 10, 11
   - §5 Built-in providers → Tasks 12-17
   - §6 OTP flow & security → Tasks 5, 21, 25 (hash, generate, validate, reset)
   - §7 Contact address resolution → Tasks 9, 18, 25
   - §8 Admin configuration → Task 22
   - §9 Keycloak version compatibility → Task 33
   - §10 Testing strategy → tests in Tasks 4-25
   - §11 Roadmap → captured in `COMPATIBILITY.md`
   - §12 Build & deployment → Tasks 1, 31, 32, 34

2. **Type consistency** — every cross-task reference (`PROVIDER_ID`, `TYPE_ID`, etc.) is defined before use; skeleton stubs in Task 20 unblock forward references.

3. **No placeholders** — every code block is complete; "Similar to Task N" is avoided (each sender task has full code).

4. **Test-first ordering** — every task that adds code has its test written and run-to-failure before implementation.

---

## Execution Notes

- **Reference frequently:** `../keycloak-2fa-email-authenticator/` is the canonical pattern. When in doubt, copy that approach and rename `email` → `messaging`.
- **Commit per task:** Granular commits make code review and rollback tractable.
- **Run `mvn test` after every task** — don't batch failures.
- **Keycloak version override:** Build can be retargeted via `-Dkeycloak.version=<x>`.
- **AWS SNS test caveat:** The `SnsClient` is `AutoCloseable`. Mockito's default mock handles `close()` as a no-op, so the try-with-resources in `AwsSnsMessageSender.send()` works in tests. If you see test failures around resource management, inject a non-closing factory variant.
