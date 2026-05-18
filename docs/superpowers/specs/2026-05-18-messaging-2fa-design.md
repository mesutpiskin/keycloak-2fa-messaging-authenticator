# Keycloak 2FA Messaging Authenticator — Design Spec

**Date:** 2026-05-18  
**Author:** Mesut Pişkin  
**Status:** Approved  
**Reference:** `keycloak-2fa-email-authenticator` (same repository owner, architectural sibling)

---

## 1. Overview

An open-source Keycloak authenticator SPI that delivers one-time passwords (OTP) via messaging channels: **SMS, Telegram, WhatsApp, and Signal**. Admins deploy a single JAR, configure their own provider credentials in the Keycloak admin UI, and the plugin handles delivery.

**Goals:**
- Corporate-grade security (SonarQube clean, no CVEs)
- Community-friendly (easy single-JAR install, Java SPI extension point)
- Keycloak 25.x and 26.x first-class support
- Consistent architecture with `keycloak-2fa-email-authenticator`

**Non-goals (v1):**
- Multi-channel user choice (admin picks one channel per realm)
- Native Keycloak SMTP-equivalent built-in provider (no free SMS default)
- Support for Keycloak < 25.x out of the box (configurable via Maven property)

---

## 2. Architecture

### 2.1 Single JAR + Java SPI Extension Point

One deployable JAR contains all built-in providers. Community extensions implement `MessageSender` and register via `META-INF/services/` — no fork required.

```
providers/
  keycloak-2fa-messaging-authenticator-<version>.jar   ← deploy this
  my-custom-provider.jar                               ← community extension (optional)
```

### 2.2 Project Structure

```
src/main/java/com/mesutpiskin/keycloak/auth/messaging/
├── MessagingConstants.java
├── OtpHashUtils.java
├── MessagingAuthenticatorForm.java
├── MessagingAuthenticatorFormFactory.java
├── ConditionalMessagingAuthenticatorForm.java
├── ConditionalMessagingAuthenticatorFormFactory.java
├── MessagingAuthenticatorRequiredAction.java
├── MessagingAuthenticatorRequiredActionFactory.java
├── MessagingAuthenticatorCredentialModel.java
├── MessagingAuthenticatorCredentialProvider.java
├── MessagingAuthenticatorCredentialProviderFactory.java
├── model/
│   ├── OtpMessage.java           (immutable, Builder)
│   └── MessageChannel.java       (enum: SMS, TELEGRAM, WHATSAPP, SIGNAL)
└── service/
    ├── MessageSender.java         (SPI extension point interface)
    ├── MessageSenderRegistry.java (SPI loader + factory)
    └── impl/
        ├── TwilioMessageSender.java
        ├── AwsSnsMessageSender.java
        ├── VonageMessageSender.java
        ├── TelegramMessageSender.java
        ├── WhatsAppMessageSender.java
        └── SignalMessageSender.java

src/main/resources/
├── META-INF/services/
│   ├── org.keycloak.authentication.AuthenticatorFactory        (2 entries)
│   ├── org.keycloak.authentication.RequiredActionFactory       (1 entry)
│   ├── org.keycloak.credential.CredentialProviderFactory       (1 entry)
│   └── com...messaging.service.MessageSender                   (6 built-in entries)
├── theme-resources/
│   ├── templates/
│   │   ├── messaging-code-form.ftl
│   │   ├── messaging-authenticator-setup-form.ftl
│   │   └── messaging-authenticator-setup-verify-form.ftl
│   └── messages/
│       └── messages_*.properties  (en, tr + community contributions)
```

---

## 3. Keycloak SPI Implementations

| SPI Interface | Implementation | Factory | Role |
|---|---|---|---|
| `AuthenticatorFactory` | `MessagingAuthenticatorForm` | `MessagingAuthenticatorFormFactory` | Main 2FA challenge & validation |
| `AuthenticatorFactory` | `ConditionalMessagingAuthenticatorForm` | `ConditionalMessagingAuthenticatorFormFactory` | Conditional 2FA (role/header/attribute) |
| `RequiredActionProvider` | `MessagingAuthenticatorRequiredAction` | `MessagingAuthenticatorRequiredActionFactory` | Self-enrollment flow |
| `CredentialProvider` | `MessagingAuthenticatorCredentialProvider` | `MessagingAuthenticatorCredentialProviderFactory` | Credential lifecycle |

Auto-discovered via `META-INF/services/` — no manual Keycloak configuration required.

---

## 4. MessageSender Extension Point

### 4.1 Interface

```java
public interface MessageSender {
    void send(OtpMessage message) throws MessageDeliveryException;
    String getProviderId();       // e.g. "twilio", "telegram", "my-custom"
    MessageChannel getChannel();  // SMS, TELEGRAM, WHATSAPP, SIGNAL
    boolean isAvailable();
}
```

### 4.2 OtpMessage (immutable)

```java
OtpMessage.builder()
    .to("+905001234567")   // E.164 for SMS/WhatsApp/Signal; numeric chatId for Telegram
    .code("847291")
    .ttlSeconds(300)
    .locale("tr")
    .build()
```

### 4.3 MessageSenderRegistry

- Loads all `MessageSender` implementations via `ServiceLoader<MessageSender>`
- Matches admin-configured `channel` + `providerId` to the correct sender
- Provider config (API keys etc.) is read from `AuthenticatorConfigModel` and passed via `getSender(channel, providerId, Map<String, String> config)` — senders are instantiated on demand with injected config, not as singletons
- Throws `IllegalStateException` with descriptive message if no matching sender found

### 4.4 Community Extension

Third-party providers ship a separate JAR with:
```
META-INF/services/com.mesutpiskin.keycloak.auth.messaging.service.MessageSender
  → com.example.MyCustomMessageSender
```
Drop both JARs in `providers/` — Keycloak discovers them automatically.

---

## 5. Built-in Providers

| Provider ID | Channel | Required Config |
|---|---|---|
| `twilio` | SMS | `twilio.accountSid`, `twilio.authToken`, `twilio.fromNumber` |
| `aws-sns` | SMS | `aws.region`, `aws.accessKeyId`, `aws.secretAccessKey` |
| `vonage` | SMS | `vonage.apiKey`, `vonage.apiSecret`, `vonage.fromNumber` |
| `telegram` | TELEGRAM | `telegram.bot.token`, `telegram.contact.attribute` (default: `telegramChatId`) |
| `whatsapp` | WHATSAPP | `whatsapp.api.url`, `whatsapp.api.token`, `whatsapp.from.number`, `whatsapp.contact.attribute` (default: `phoneNumber`) |
| `signal` | SIGNAL | `signal.cli.rest.url`, `signal.from.number`, `signal.contact.attribute` (default: `phoneNumber`) |

All secret fields use `ProviderConfigProperty.SECRET_TYPE` — masked in admin UI, never logged.

---

## 6. OTP Flow & Security

### 6.1 Authentication Flow

```
authenticate(context):
  1. resolveContactAddress(user, channel, config)
     → attribute exists? use it
     → attribute missing? assign RequiredAction, return
  2. generate OTP: SecretGenerator.getInstance().randomString(length, DIGITS)
  3. store in session: authNote(CODE) = OtpHashUtils.hash(code)
                       authNote(CODE_TTL) = now + ttl * 1000
                       authNote(CODE_RESEND_AVAILABLE_AFTER) = now + cooldown * 1000
  4. send via MessageSenderRegistry.getSender(channel, providerId).send(otpMessage)
  5. render messaging-code-form.ftl (masked contact address shown)

action(context):
  1. max attempt check → resetFlow() if exceeded
  2. TTL check → challenge(expired) if expired
  3. OtpHashUtils.matches(submitted, storedHash) — constant-time
  4. invalid → increment attempt counter, challenge(invalid)
  5. valid → clear session notes, success()
```

### 6.2 Security Controls

| Threat | Control |
|---|---|
| Timing attack | `MessageDigest.isEqual()` constant-time comparison |
| Brute force | Max attempts (default 5) + Keycloak `BruteForceProtector` integration |
| Code exposure | Raw OTP never logged; only hash stored in session |
| Replay attack | Single-use: session notes cleared after success; TTL enforced |
| Phone number leak | Masked display in UI: `+90 5** *** **67` |
| Injection | `OtpMessage` immutable; E.164 validated before API call |
| Secret leak | All API credentials use `SECRET_TYPE`; excluded from debug logs |

### 6.3 OTP Parameters (admin configurable)

| Parameter | Default | Range |
|---|---|---|
| Code length | 6 | 4–8 |
| TTL | 300s | 60–900s |
| Resend cooldown | 60s | 30–300s (longer than email — SMS cost) |
| Max attempts | 5 | 3–10 |
| Simulation mode | false | — |

---

## 7. Contact Address Resolution

### 7.1 Strategy

```
resolveContactAddress(user, channel, config):
  attributeName = config.get(channel.contactAttributeConfigKey, channel.defaultAttributeName)
  value = user.getFirstAttribute(attributeName)
  if (value != null && !value.isBlank()) return value
  else: assign MessagingAuthenticatorRequiredAction to user, return null
```

### 7.2 Default Attribute Names (admin-overridable)

| Channel | Default Attribute | Config Key |
|---|---|---|
| SMS | `phoneNumber` | `sms.contact.attribute` |
| TELEGRAM | `telegramChatId` | `telegram.contact.attribute` |
| WHATSAPP | `phoneNumber` | `whatsapp.contact.attribute` |
| SIGNAL | `phoneNumber` | `signal.contact.attribute` |

### 7.3 Required Action Enrollment

```
1. Show channel-appropriate form:
   - SMS/WhatsApp/Signal: "Enter your phone number (e.g. +90 500 123 4567)"
   - Telegram: "Enter your Telegram Chat ID" (with @userinfobot help text)
2. Validate format:
   - Phone: E.164 regex ^\+[1-9]\d{6,14}$
   - Telegram chatId: numeric string
3. Send enrollment OTP to entered address
4. User confirms code
5. user.setSingleAttribute(attributeName, value)
6. Remove RequiredAction from user session
```

**Skip-setup:** When enabled, users with a pre-populated attribute (e.g. from LDAP sync) skip enrollment entirely.

---

## 8. Admin Configuration

All config surfaced via `getConfigProperties()` in the factory:

**General:**
- `message.channel` (LIST: SMS, TELEGRAM, WHATSAPP, SIGNAL)
- `otp.length`, `otp.ttl`, `otp.resend.cooldown`, `otp.max.attempts`
- `simulation.mode` (BOOLEAN)
- `skip.setup` (BOOLEAN)

**SMS provider:**
- `sms.provider` (LIST: twilio, aws-sns, vonage)
- Provider-specific secrets (SECRET_TYPE)
- `sms.contact.attribute` (STRING, default: `phoneNumber`)

**Telegram:**
- `telegram.bot.token` (SECRET_TYPE)
- `telegram.contact.attribute` (STRING, default: `telegramChatId`)

**WhatsApp:**
- `whatsapp.api.url`, `whatsapp.api.token` (SECRET_TYPE), `whatsapp.from.number`
- `whatsapp.contact.attribute` (STRING, default: `phoneNumber`)

**Signal:**
- `signal.cli.rest.url`, `signal.from.number`
- `signal.contact.attribute` (STRING, default: `phoneNumber`)

**Conditional authenticator (additional):**
- OTP Control User Attribute, Skip/Force OTP Role, Skip/Force OTP HTTP Header, Fallback behavior

---

## 9. Keycloak Version Compatibility

| Version | Support | Notes |
|---|---|---|
| **26.x** | Primary | Default `pom.xml` target |
| **25.x** | Full | Same SPI interfaces; tested in CI matrix |
| **24.x and below** | Configurable | `mvn package -Dkeycloak.version=24.0.0`; documented in README |
| **23.x** | Roadmap (v2) | Older `CredentialModel` API differences |

**CI matrix** (GitHub Actions):
```yaml
strategy:
  matrix:
    java: [21]
    keycloak: [25.0.0, 26.0.0, 26.6.1]
```

A `COMPATIBILITY.md` file documents known limitations per version.

---

## 10. Testing Strategy

**Framework:** JUnit 5 + Mockito. **Target coverage:** ≥ 80% (Jacoco enforced in CI).

| Test Class | Scope |
|---|---|
| `MessagingAuthenticatorFormTest` | OTP generate/send/validate, TTL expiry, max attempts, resend cooldown |
| `MessagingAuthenticatorCredentialProviderTest` | `isConfiguredFor()`, skip-setup, credential lifecycle |
| `MessagingAuthenticatorCredentialModelTest` | Model metadata, creation, conversion |
| `ConditionalMessagingAuthenticatorFormTest` | Role/attribute/header conditional logic |
| `MessagingConstantsTest` | Config keys and default value validation |
| `OtpHashUtilsTest` | Hash generation, constant-time matching, no collision |
| `MessageSenderRegistryTest` | Provider selection, unknown provider error, SPI loading |
| `TwilioMessageSenderTest` | Config validation, API call mock, error handling |
| `AwsSnsMessageSenderTest` | Config validation, SDK mock, region validation |
| `VonageMessageSenderTest` | Config validation, API mock |
| `TelegramMessageSenderTest` | Bot token validation, chat ID format, API mock |
| `WhatsAppMessageSenderTest` | API URL validation, token mock |
| `SignalMessageSenderTest` | CLI REST URL validation, E.164 format |
| `ContactResolverTest` | Attribute present → use it; attribute absent → required action |
| `E164ValidatorTest` | Valid/invalid format scenarios |
| `MessagingAuthenticatorRequiredActionTest` | Enrollment flow, verification, attribute persistence |

**Security-specific tests:**
- Timing consistency for hash comparison
- No secret values appear in log output
- Special characters in phone/chatId are rejected
- Null/empty contact produces graceful failure (no NPE)

---

## 11. Roadmap

| Milestone | Scope |
|---|---|
| **v1.0** | SMS (Twilio/SNS/Vonage) + Telegram + WhatsApp + Signal; Conditional authenticator; Required action enrollment; Java SPI extension point; Keycloak 25.x + 26.x; EN + TR i18n |
| **v1.1** | Turkish SMS providers (NetGSM, İletimerkezi); additional language contributions |
| **v2.0** | Multi-channel user choice; webhook-based provider; Keycloak 23.x compatibility |
| **Community** | Any `MessageSender` implementation shipped as a separate JAR |

---

## 12. Build & Deployment

```xml
<!-- pom.xml key properties -->
<java.version>21</java.version>
<keycloak.version>26.6.1</keycloak.version>
<maven.compiler.source>${java.version}</maven.compiler.source>
```

**Deploy:**
```bash
cp target/keycloak-2fa-messaging-authenticator-*.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

**Maven artifact:** `io.github.mesutpiskin:keycloak-2fa-messaging-authenticator`

---

## 13. Reference

- Architectural sibling: `keycloak-2fa-email-authenticator`
- Keycloak SPI docs: Server Development Guide — Authentication SPI
- Java SPI: `java.util.ServiceLoader`
- Signal CLI REST mode: `bbernhard/signal-cli-rest-api` Docker image
