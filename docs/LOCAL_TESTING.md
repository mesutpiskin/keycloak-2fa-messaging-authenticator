# Local Testing

## Build the plugin

```bash
mvn -B verify
```

The generated JAR will be available under `target/`.

## Test with a local Keycloak container

A typical local workflow is:

1. Build the plugin JAR
2. Copy it into a Keycloak `providers/` directory or mount it into a container
3. Start Keycloak
4. Create or update an authentication flow using the messaging authenticator
5. Configure one of the supported providers

## Recommended provider testing order

- Start with `simulation.mode=true` for initial flow validation
- Test SMS providers with a real E.164 number
- Test Telegram using a bot token and numeric `telegramChatId`
- Test WhatsApp and Signal only after verifying their external service endpoints separately

## Useful configuration keys

- `message.channel`
- `sms.provider`
- `otp.length`
- `otp.ttl`
- `otp.resend.cooldown`
- `otp.max.attempts`
- `simulation.mode`
- `skip.setup`
