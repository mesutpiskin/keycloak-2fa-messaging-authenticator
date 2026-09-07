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

### AWS SNS

The released JAR bundles the AWS SDK, so `aws-sns` works from the single plugin JAR with no AWS
SDK JARs copied alongside it. Libraries Keycloak already ships — Apache HttpClient, slf4j,
commons-logging — are deliberately not bundled, so the plugin does not put a second copy of them
on the server's classpath.

Configure it on the authenticator:

| Setting | Config key | Example |
|---|---|---|
| AWS SNS Region | `aws.region` | `eu-west-1` |
| AWS Access Key ID | `aws.accessKeyId` | `AKIA...` |
| AWS Secret Access Key | `aws.secretAccessKey` | *(masked)* |

The IAM principal needs only `sns:Publish`. Publishing to a phone number is not scoped to a
topic ARN, so the resource is `*`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "sns:Publish", "Resource": "*" }
  ]
}
```

Credentials come from those three settings only; instance profiles, IRSA and the other AWS
credential providers are not used. Retries, endpoint resolution and clock-skew correction are
handled by the SDK.

Two AWS-side settings commonly look like plugin failures:

- new accounts sit in the **SMS sandbox**, where messages only reach phone numbers you have
  verified — request production access before rolling out
- SNS enforces a **monthly SMS spending limit**, USD 1.00 by default, after which publishes fail

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

You can also download the JAR from GitHub Releases. Install it with:

```bash
cp keycloak-2fa-messaging-authenticator-*.jar /opt/keycloak/providers/
/opt/keycloak/bin/kc.sh build
```

Then restart Keycloak. `kc.sh build` is required on the Quarkus distribution; without it the
provider is not registered and the authenticator does not appear in the admin console.

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
