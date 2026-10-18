# Contributing to keycloak-2fa-messaging-authenticator

First of all, thank you for your interest in contributing to **keycloak-2fa-messaging-authenticator**! 🎉

Contributions of all kinds are welcome: bug fixes, new messaging providers, translations, documentation improvements, tests, and more. Every contribution helps make the project better.

## Ways to Contribute

You can contribute by:

- Reporting bugs
- Suggesting new features or messaging providers
- Adding or improving translations of the login theme messages
- Improving the documentation website
- Fixing bugs
- Adding or improving tests
- Refactoring existing code
- Reviewing pull requests

## Before You Start

Before working on a contribution, please:

1. Search existing issues and pull requests to avoid duplicate work.
2. For significant changes, such as a new provider, a new configuration option, or a change to the authentication flow, open a **Feature Request** using the GitHub issue template.
3. Wait for feedback before starting a large implementation.

Small bug fixes, documentation improvements, translations, and typo fixes can generally be submitted directly as a pull request.

## Development Setup

### Prerequisites

Make sure you have the following installed:

- JDK 21 (for example [Eclipse Temurin](https://adoptium.net/))
- Maven 3.9 or newer
- Git
- Docker and Docker Compose (optional, for testing against a local Keycloak)
- Node.js 22 and pnpm (optional, only to work on the documentation website)

### 1. Fork the Repository

Fork this repository to your GitHub account.

### 2. Clone Your Fork

```bash
git clone https://github.com/<your-github-username>/keycloak-2fa-messaging-authenticator.git
cd keycloak-2fa-messaging-authenticator
```

### 3. Configure the Upstream Remote

Add the original repository as the upstream remote.

```bash
git remote add upstream https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator.git
```

Verify your remotes:

```bash
git remote -v
```

### 4. Build the Project

```bash
mvn -B verify
```

This compiles the plugin, runs the unit tests, builds the shaded provider JAR under `target/`, and runs the packaging integration tests against it.

## Project Layout

| Path | Contents |
| ---- | -------- |
| `src/main/java/.../messaging` | Authenticator, required action, credential provider and their factories |
| `src/main/java/.../messaging/service/impl` | Built-in message senders (Twilio, AWS SNS, Vonage, Telegram, WhatsApp, Signal) |
| `src/main/resources/theme-resources` | Login templates and message bundles (`messages_<locale>.properties`) |
| `src/main/resources/META-INF/services` | `ServiceLoader` registrations for Keycloak SPIs and message senders |
| `src/test/java` | Unit tests (`*Test`) and packaging integration tests (`*IT`) |
| `website/` | Docusaurus documentation website |
| `docs/` | Maintainer notes (release process, local testing) |

## Development Workflow

### Keep Your Fork Up to Date

Before starting new work, synchronize your fork with the upstream repository.

```bash
git checkout main
git fetch upstream
git merge upstream/main
git push origin main
```

### Create a Branch

Create a new branch from the latest `main` branch.

```bash
git checkout -b feat/your-feature-name
```

Use a descriptive branch name, for example:

- `feat/messagebird-sender`
- `fix/otp-resend-cooldown`
- `docs/aws-sns-setup`
- `i18n/german-messages`
- `test/telegram-sender`

### Make Your Changes

Please:

- Keep changes focused on a single concern.
- Follow the existing project architecture and coding style.
- Write clean, readable, and maintainable code.
- Add or update tests when appropriate.
- Update the documentation (`README.md` and `website/docs`) when configuration or behavior changes.
- Never commit real credentials, phone numbers, chat IDs, or API tokens, including in tests and examples.

### Validate Your Changes

Before opening a pull request, make sure the full build passes:

```bash
mvn -B verify
```

### Compatibility Checks

The release publishes a JAR for each of the last 10 Keycloak releases, not only the version in `pom.xml`. A change that compiles against the default Keycloak version can still break against an older one. CI builds against both the default and the oldest published version on every pull request; to reproduce the second check locally, run:

```bash
mvn -B verify -Dkeycloak.version=<keycloak-version>
```

See [`COMPATIBILITY.md`](COMPATIBILITY.md) for the supported Keycloak versions.

### Test Against a Local Keycloak

To try your change in a running Keycloak, build and start the bundled Docker setup:

```bash
docker compose up --build
```

Keycloak is then available at <http://localhost:8080> (admin / admin). Enable `simulation.mode` on the authenticator to validate the flow without sending real messages. See [`docs/LOCAL_TESTING.md`](docs/LOCAL_TESTING.md) and the [local testing guide](https://mesutpiskin.github.io/keycloak-2fa-messaging-authenticator/) for more details.

### Adding a Messaging Provider

New providers implement `com.mesutpiskin.keycloak.auth.messaging.service.MessageSender`. To add one to this project:

1. Add the implementation under `service/impl`.
2. Register it in `src/main/resources/META-INF/services/com.mesutpiskin.keycloak.auth.messaging.service.MessageSender`.
3. Validate the required configuration keys in `configure(...)` and throw an `IllegalArgumentException` with a descriptive message when one is missing.
4. Add unit tests next to the existing sender tests.
5. Document the provider ID, configuration keys, and setup steps in the README and website.

If you add a dependency, check that it ends up in the shaded JAR (or is already provided by Keycloak) so the provider loads from the single plugin JAR.

Providers that only make sense for your own deployment can live in a separate JAR instead, using the same `ServiceLoader` registration.

### Adding a Translation

Copy `src/main/resources/theme-resources/messages/messages_en.properties` to `messages_<locale>.properties`, translate the values, and keep the keys unchanged.

### Working on the Documentation Website

```bash
cd website
pnpm install
pnpm start
```

Run `pnpm build` before opening a pull request to catch broken links.

## Commit Messages

This project follows the **Conventional Commits** specification.

Using Conventional Commits helps us:

- Generate release notes automatically.
- Maintain a clear and searchable Git history.
- Support the release process.

Use the appropriate commit type for your changes.

| Type       | Description                                 |
| ---------- | ------------------------------------------- |
| `feat`     | Introduces a new feature                    |
| `fix`      | Fixes a bug                                 |
| `docs`     | Documentation-only changes                  |
| `refactor` | Code improvements without changing behavior |
| `test`     | Adds or updates tests                       |
| `build`    | Changes to the build system or dependencies |
| `ci`       | Changes to CI/CD workflows                  |
| `chore`    | Project maintenance tasks                   |

Examples:

```text
feat: add MessageBird SMS sender
fix: enforce resend cooldown after a failed delivery
docs: document the AWS SNS sandbox limits
refactor: simplify sender registry lookup
test: cover Telegram chat ID validation
build: bump Keycloak to 26.6.2
ci: build against the oldest published Keycloak
```

## Pull Requests

Before submitting a pull request, please make sure that:

- `mvn -B verify` passes.
- The change builds against the oldest supported Keycloak version when it touches Keycloak APIs.
- Documentation has been updated when necessary.
- Your pull request focuses on a single logical change.

When opening a pull request, fill in the pull request template:

- Provide a clear summary of your changes.
- Explain the motivation behind the change.
- Link the related issue when applicable (for example, `Closes #123`).
- Mention any breaking changes, such as renamed configuration keys or user attributes.

Please respond promptly to review feedback to help keep the review process efficient.

## Reporting Bugs

If you've found a bug, please use the **Bug Report** issue template.

Before creating a new issue, search existing issues and check the [troubleshooting guide](https://mesutpiskin.github.io/keycloak-2fa-messaging-authenticator/). The template will guide you through providing the information needed to investigate the problem, such as your Keycloak and plugin versions, provider, and server logs.

Please remove credentials, phone numbers, and other personal data from logs before posting them.

## Feature Requests

Have an idea for a new feature, provider, or enhancement?

Please use the **Feature Request** issue template and clearly describe:

- The problem you're trying to solve.
- Your proposed solution.
- Alternative approaches you've considered.
- Any additional context that may help.

## Accessibility

If something in the login pages, the code messages, or the documentation is hard or impossible to use, please use the **Accessibility Issue** template. When you change a login template or message bundle, follow the contributor expectations in [**ACCESSIBILITY.md**](./ACCESSIBILITY.md).

## Security

If you discover a security vulnerability, **do not open a public GitHub issue**.

Please follow the instructions in [**SECURITY.md**](./SECURITY.md) to report it privately.

## Code of Conduct

By participating in this project, you agree to follow our [**Code of Conduct**](./CODE_OF_CONDUCT.md).

Please help us maintain a welcoming, respectful, and inclusive community.

## Questions

If you have a question that is not covered by the [documentation](https://mesutpiskin.github.io/keycloak-2fa-messaging-authenticator/), open an issue using the **Question** template.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

Thank you for contributing to **keycloak-2fa-messaging-authenticator**. Your contributions help improve the project for everyone.
