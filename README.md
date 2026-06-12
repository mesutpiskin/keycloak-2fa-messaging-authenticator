# Keycloak 2FA Messaging Authenticator

A Keycloak Authentication Provider for two-factor authentication (2FA) via messaging OTP. It supports SMS providers such as Twilio, AWS SNS, and Vonage, plus Telegram, WhatsApp, and Signal delivery flows.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator.svg)](https://central.sonatype.com/artifact/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

## Documentation

Full documentation — installation, configuration, local testing, provider setup, troubleshooting, and contribution notes — is available at:

**https://mesutpiskin.github.io/keycloak-2fa-messaging-authenticator/**

## Highlights

- Messaging-based OTP login for Keycloak browser authentication flows
- Built-in support for Twilio, AWS SNS, Vonage, Telegram, WhatsApp, and Signal
- Configurable OTP length, TTL, resend cooldown, and maximum attempts
- Enrollment required action for collecting the user contact address
- Custom sender extension support through Java `ServiceLoader` SPI
- Java 21 / Keycloak 26.x friendly build and release pipeline

## Related projects

Need OTP via email (Keycloak SMTP, SendGrid, AWS SES, or Mailgun)? See the sibling project: **[keycloak-2fa-email-authenticator](https://github.com/mesutpiskin/keycloak-2fa-email-authenticator)** — same approach, different channel.

## Supported Channels

| Channel | Provider ID | Default user attribute |
|---|---|---|
| SMS via Twilio | `twilio` | `phoneNumber` |
| SMS via AWS SNS | `aws-sns` | `phoneNumber` |
| SMS via Vonage | `vonage` | `phoneNumber` |
| Telegram | `telegram` | `telegramChatId` |
| WhatsApp | `whatsapp` | `phoneNumber` |
| Signal | `signal` | `phoneNumber` |

## Quick Start

Use the version matching your Keycloak installation.

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
implementation "io.github.mesutpiskin:keycloak-2fa-messaging-authenticator:26.0.0-KC26.6.1"
```

You can also download the JAR from GitHub Releases and copy it into Keycloak's `providers/` directory.

## Local Build

```bash
mvn -B verify
```

The produced JAR is written under `target/`.

## Release and Maintenance

- Release instructions: `docs/RELEASE.md`
- Local testing notes: `docs/LOCAL_TESTING.md`
- Full website docs: `website/`

## Contributing

Contributions are welcome — bug reports, feature requests, translations, provider improvements, and pull requests. Please open an issue first for significant changes.

## Sponsor

This project is developed and maintained voluntarily.
If you'd like to support it, donations go to [KACUV](https://kacuv.org/en/) — a non-profit supporting children in need.

[![Donate](https://img.shields.io/badge/Donate-KACUV-blue)](https://kacuv.org/en/)

## License

Licensed under the [Apache License 2.0](LICENSE).
