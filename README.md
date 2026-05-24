# Keycloak 2FA Messaging Authenticator

A Keycloak Authentication Provider for two-factor authentication (2FA) via messaging OTP. Supports SMS (Twilio, AWS SNS, Vonage), Telegram, WhatsApp, and Signal.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator.svg)](https://central.sonatype.com/artifact/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

## Table of Contents

- [Overview](#overview)
- [Supported Channels](#supported-channels)
- [Quick Start](#quick-start)
- [Docker Quickstart](#docker-quickstart)
- [Configuration](#configuration)
  - [Twilio Setup](#twilio-setup)
  - [AWS SNS Setup](#aws-sns-setup)
  - [Vonage Setup](#vonage-setup)
  - [Telegram Setup](#telegram-setup)
  - [WhatsApp Setup](#whatsapp-setup)
  - [Signal Setup](#signal-setup)
- [User Contact Attribute](#user-contact-attribute)
- [Enrollment Flow](#enrollment-flow)
- [Extending with Custom Senders](#extending-with-custom-senders)
- [Contributing](#contributing)
- [Sponsor](#sponsor)
- [License](#license)

---

## Overview

This plugin adds messaging-based OTP as a second factor in Keycloak authentication flows. On login, the user receives a one-time code via their configured messaging channel; they enter the code in Keycloak's browser flow to proceed.

Two authenticator variants are provided:
- **Messaging OTP** (`messaging-authenticator-form`) — always requires a messaging OTP.
- **Conditional Messaging OTP** (`conditional-messaging-authenticator-form`) — skips the OTP step when a condition authenticator grants access, useful for trusted-device flows.

A **Required Action** (`messaging-authenticator-enrollment`) lets users self-enroll by entering their phone number or chat identifier during their first login.

---

## Supported Channels

| Channel | Provider ID | Credential attribute |
|---|---|---|
| SMS via Twilio | `twilio` | `phoneNumber` (E.164) |
| SMS via AWS SNS | `aws-sns` | `phoneNumber` (E.164) |
| SMS via Vonage | `vonage` | `phoneNumber` (E.164) |
| Telegram | `telegram` | `telegramChatId` |
| WhatsApp (360dialog) | `whatsapp` | `phoneNumber` (E.164) |
| Signal (signal-cli REST) | `signal` | `phoneNumber` (E.164) |

Custom senders can be registered via the Java `ServiceLoader` SPI — see [Extending with Custom Senders](#extending-with-custom-senders).

---

## Quick Start

The easiest way to get the JAR is via Maven Central. Use the version matching your Keycloak installation:

**Maven:**
```xml
<dependency>
  <groupId>io.github.mesutpiskin</groupId>
  <artifactId>keycloak-2fa-messaging-authenticator</artifactId>
  <version>26.0.0-KC26.6.1</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.github.mesutpiskin:keycloak-2fa-messaging-authenticator:26.0.0-KC26.6.1'
```

> Version format: `<plugin-version>-KC<keycloak-version>` — all versions on [Maven Central](https://central.sonatype.com/artifact/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator).

Or download the JAR from [GitHub Releases](../../releases) and drop it into Keycloak's `providers/` directory, then restart Keycloak.

---

## Docker Quickstart

A `docker-compose.yml` is included for local testing with a pre-loaded Keycloak instance:

```bash
# Build the plugin JAR
mvn -B clean package -DskipTests

# Start Keycloak with the plugin mounted
docker-compose up
```

Keycloak will be available at `http://localhost:8080`. The admin console is at `http://localhost:8080/admin` (credentials: `admin` / `admin`).

---

## Configuration

After installing the JAR, go to **Realm Settings → Authentication → Flows** in the Keycloak Admin Console and add either authenticator to your desired flow. Then configure the authenticator step's settings:

| Setting | Description | Example |
|---|---|---|
| `messagingChannel` | Which channel to use | `twilio` |
| `otpLength` | Number of OTP digits | `6` |
| `otpTtl` | OTP validity in seconds | `300` |
| `simulationMode` | Log OTP instead of sending (dev only) | `false` |

Channel-specific credentials are configured as sub-properties. Each provider reads its own keys from the authenticator config map.

---

### Twilio Setup

1. Sign up at [twilio.com](https://www.twilio.com/) and obtain your **Account SID**, **Auth Token**, and a **Twilio phone number**.
2. Set the following authenticator config keys:

| Config key | Value |
|---|---|
| `twilio.accountSid` | Your Twilio Account SID |
| `twilio.authToken` | Your Twilio Auth Token |
| `twilio.fromNumber` | Sender phone number in E.164 format |

3. In the Keycloak Admin Console, open **Users → [user] → Attributes** and set `phoneNumber` to the user's E.164 phone number (e.g. `+14155552671`).

---

### AWS SNS Setup

1. Create an IAM user with the `AmazonSNSFullAccess` policy (or a scoped `sns:Publish` policy).
2. Generate an **Access Key ID** and **Secret Access Key**.
3. Set the following authenticator config keys:

| Config key | Value |
|---|---|
| `aws.accessKeyId` | IAM access key ID |
| `aws.secretAccessKey` | IAM secret access key |
| `aws.region` | AWS region (e.g. `us-east-1`) |

4. Set the user attribute `phoneNumber` to the E.164 phone number.

> AWS SNS SMS is subject to regional availability and monthly spend limits on sandbox accounts. Verify the destination number in the SNS sandbox before testing.

---

### Vonage Setup

1. Sign up at [vonage.com](https://www.vonage.com/) (formerly Nexmo) and note your **API Key** and **API Secret**.
2. Set the following authenticator config keys:

| Config key | Value |
|---|---|
| `vonage.apiKey` | Vonage API key |
| `vonage.apiSecret` | Vonage API secret |
| `vonage.fromNumber` | Sender ID or phone number |

3. Set the user attribute `phoneNumber` to the E.164 phone number.

---

### Telegram Setup

1. Create a Telegram bot via [@BotFather](https://t.me/BotFather) and save the **Bot Token**.
2. Have the user send any message to the bot to obtain their **Chat ID** (use the Telegram getUpdates API or a helper bot).
3. Set the following authenticator config key:

| Config key | Value |
|---|---|
| `telegram.botToken` | Telegram bot token |

4. Set the user attribute `telegramChatId` to the user's numeric Telegram Chat ID.

---

### WhatsApp Setup

This provider targets the [360dialog](https://www.360dialog.com/) WhatsApp Business API. Other BSP providers can be integrated via the custom `MessageSender` SPI.

1. Create a 360dialog account and obtain a **Partner API Key** and your **namespace / template name** for the OTP message.
2. Set the following authenticator config keys:

| Config key | Value |
|---|---|
| `whatsapp.apiKey` | 360dialog partner API key |
| `whatsapp.phoneNumberId` | Your registered WhatsApp phone number ID |

3. Set the user attribute `phoneNumber` to the recipient's E.164 phone number.

---

### Signal Setup

This provider uses the [signal-cli REST API](https://github.com/bbernhard/signal-cli-rest-api). You must run the signal-cli REST API service yourself.

1. Register a Signal number with signal-cli and start the REST API service.
2. Set the following authenticator config keys:

| Config key | Value |
|---|---|
| `signal.apiUrl` | Base URL of your signal-cli REST API (e.g. `http://localhost:8080`) |
| `signal.sender` | Registered Signal sender number in E.164 format |

3. Set the user attribute `phoneNumber` to the recipient's E.164 phone number.

---

## User Contact Attribute

Each user must have the appropriate Keycloak user attribute set for their channel:

| Channel | User attribute | Format |
|---|---|---|
| SMS (Twilio/SNS/Vonage) | `phoneNumber` | E.164, e.g. `+14155552671` |
| WhatsApp | `phoneNumber` | E.164 |
| Signal | `phoneNumber` | E.164 |
| Telegram | `telegramChatId` | Numeric chat ID, e.g. `123456789` |

Attributes can be set by admins (**Users → [user] → Attributes**) or by users themselves through the **Enrollment Required Action** (see below).

Phone numbers are validated against the E.164 format (`+` followed by 7–15 digits) before sending. Invalid numbers cause the OTP step to fail with an error rather than attempting delivery.

---

## Enrollment Flow

When the `messaging-authenticator-enrollment` Required Action is assigned to a user (or set as a default action for new users), the user is prompted to enter their contact detail on their first login:

1. After successful primary authentication, Keycloak redirects the user to the enrollment page.
2. The user enters their phone number or Telegram Chat ID.
3. The plugin validates the input (E.164 for phone, numeric for Telegram).
4. On success, the value is saved as a user attribute and a `MessagingAuthenticatorCredential` is created.
5. Subsequent logins trigger the OTP step using the saved contact.

To assign the Required Action as default for new users, go to **Realm Settings → Authentication → Required Actions** and enable **Default Action** for "Messaging OTP Enrollment".

---

## Extending with Custom Senders

The plugin uses Java's `ServiceLoader` mechanism to discover `MessageSender` implementations at runtime. To add a custom sender:

1. Implement `com.mesutpiskin.keycloak.auth.messaging.service.MessageSender` in your own JAR.
2. Register it in `META-INF/services/com.mesutpiskin.keycloak.auth.messaging.service.MessageSender`.
3. Drop your JAR into Keycloak's `providers/` directory alongside the plugin JAR.

Your sender will be auto-discovered and available as a channel option. The `providerId()` method returns the string used in the `messagingChannel` authenticator config key.

---

## Contributing

Contributions are welcome — bug reports, feature requests, translations, new channel providers, and pull requests. Please open an issue first for significant changes.

---

## Sponsor

This project is developed and maintained voluntarily.
If you'd like to support it, donations go to [KACUV](https://kacuv.org/en/) —
a non-profit supporting children in need.

[![Donate](https://img.shields.io/badge/Donate-KACUV-blue)](https://kacuv.org/en/)

---

## License

Licensed under the [Apache License 2.0](LICENSE).
