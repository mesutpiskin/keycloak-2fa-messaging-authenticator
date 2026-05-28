# Keycloak 2FA Messaging Authenticator — CI/CD and Documentation Website Design

**Date:** 2026-05-28  
**Author:** Mesut Pişkin  
**Status:** Approved  
**Reference:** `keycloak-2fa-email-authenticator`

## 1. Overview

This design adds a modernized delivery pipeline and a documentation website to the existing `keycloak-2fa-messaging-authenticator` repository.

The repository already contains a working Maven-based Keycloak authenticator implementation, tests, a basic CI workflow, and a release workflow. The goal is to evolve that baseline into a more maintainable and discoverable open-source project by improving CI, refining release automation, and publishing end-user documentation via GitHub Pages.

## 2. Goals

- Keep the existing Maven/Java/Keycloak project structure intact
- Modernize CI for pull requests and main branch pushes
- Support release publishing from both GitHub Releases and version tags
- Publish artifacts to Maven Central and GitHub Packages
- Attach generated JARs to GitHub Releases
- Add a Docusaurus-based documentation website
- Deploy docs automatically to GitHub Pages
- Align project presentation with the sibling email authenticator repository while keeping messaging-specific content

## 3. Non-goals

- Rewriting authenticator runtime behavior
- Refactoring provider implementation internals unrelated to delivery/docs
- Introducing a multi-stage monorepo toolchain beyond what is needed for docs
- Adding enterprise-only quality gates such as Sonar, SAST, or reusable workflow indirection in this iteration

## 4. Current State

The repository currently includes:

- `pom.xml` with Java 21 and artifact metadata
- `README.md`
- `.github/workflows/maven.yml`
- `.github/workflows/maven-publish.yml`
- production source under `src/main`
- tests under `src/test`

The current CI workflow is intentionally minimal and runs `mvn package`. The release workflow already targets Maven Central and GitHub Packages, but it is optimized around GitHub Releases only and is harder to reason about than necessary for ongoing maintenance.

## 5. Proposed Architecture

### 5.1 CI Workflow

A single primary CI workflow will:

- trigger on `push` to `main`
- trigger on `pull_request` to `main`
- use Java 21 and Maven cache
- run `mvn -B verify`
- upload build artifacts for inspection

This workflow remains intentionally simple and fast. It verifies the repository in the way contributors expect without mixing release concerns into day-to-day validation.

### 5.2 Release Workflow

A dedicated release workflow will:

- trigger on GitHub `release.published`
- also trigger on `push` for tags matching `v*`
- checkout with tags/history available
- set up Java + Maven publishing credentials
- derive plugin version from the Git tag or release tag
- publish signed artifacts to Maven Central using the release profile
- publish to GitHub Packages using the GitHub profile
- attach generated artifacts to the GitHub Release when a release context exists

The workflow should remain idempotent where practical. If a Central version already exists, the workflow should fail as little as possible and provide clear logs.

### 5.3 Documentation Website

A Docusaurus site will be added under `website/`.

The initial site structure will include:

- `intro`
- `get-started`
- `installation/local`
- `installation/docker`
- `configuration/authentication-flow`
- `configuration/provider-setup`
- `configuration/template-customization` (only if the repository currently supports it clearly enough to document)
- `providers/*` or a consolidated provider page for Twilio, AWS SNS, Vonage, Telegram, WhatsApp, and Signal
- `local-testing`
- `troubleshooting`
- `contributing`
- `for-ai-agents` only if useful for project maintenance and consistent with the sibling repo style

A dedicated docs deployment workflow will build the static site and deploy it to GitHub Pages when `main` changes affecting the website occur, and also support manual dispatch.

## 6. Documentation Content Strategy

### 6.1 README

The root README will be streamlined to:

- explain what the project is
- link to the documentation site prominently
- keep a short quick-start dependency snippet
- summarize supported messaging channels
- point contributors to the website for deeper setup instructions

### 6.2 Long-form Docs

Longer operational content will move into:

- `docs/RELEASE.md`
- `docs/LOCAL_TESTING.md`
- Docusaurus docs pages under `website/docs/`

This keeps the repository root concise while still preserving practical maintenance instructions in version control.

## 7. `pom.xml` Changes

`pom.xml` will be updated only as needed to support the delivery model cleanly.

Likely additions or refinements:

- release profile for Maven Central publishing
- GitHub Packages profile
- source JAR generation
- Javadoc JAR generation
- GPG signing for release profile
- distribution management alignment with Central/GitHub Packages requirements

Existing project coordinates, SCM metadata, and Java baseline will be preserved unless a concrete compatibility issue is found.

## 8. GitHub Secrets and Operational Requirements

The release flow will document these expected secrets:

- `CENTRAL_TOKEN_USERNAME`
- `CENTRAL_TOKEN_PASSWORD`
- `GPG_SIGNING_KEY`
- `GPG_SIGNING_KEY_PASSWORD`

Docs deployment will rely on GitHub Pages permissions rather than additional secrets.

## 9. File/Directory Plan

Expected additions or updates:

- `.github/workflows/ci.yml` (or modernization of `maven.yml`)
- `.github/workflows/release.yml` (or modernization of `maven-publish.yml`)
- `.github/workflows/deploy-docs.yml`
- `website/` Docusaurus app
- `docs/RELEASE.md`
- `docs/LOCAL_TESTING.md`
- `README.md`
- `pom.xml`

If retaining current workflow filenames reduces churn, implementation may update the existing filenames instead of renaming them. Function matters more than exact filename.

## 10. Alternatives Considered

### Option A — Clone sibling repo structure almost exactly

**Pros**
- very familiar
- low design risk
- easy cross-project consistency

**Cons**
- may copy assumptions that are too specific to the email project
- less opportunity to simplify naming and flow

### Option B — Modernized sibling-inspired structure (**recommended**)

**Pros**
- preserves familiarity
- improves maintainability
- avoids unnecessary complexity
- fits current repository maturity better

**Cons**
- not byte-for-byte aligned with the sibling repo

### Option C — Minimal CI/release only, docs later

**Pros**
- fastest to ship
- smallest change set

**Cons**
- delays discoverability
- leaves project presentation incomplete

## 11. Risks and Mitigations

### Risk: Release duplication or version mismatch
Mitigation: derive version/tag consistently, document release process, keep workflow logs explicit.

### Risk: Docs drift from real behavior
Mitigation: write docs from current source and config keys, avoid aspirational content, keep README short.

### Risk: Overcomplicated release matrix
Mitigation: preserve working pieces from the existing release flow where they add value, but avoid unnecessary expansion in the new baseline.

## 12. Success Criteria

This work is successful when:

- pull requests automatically run Maven verification
- tagged or released versions can publish artifacts through GitHub Actions
- project documentation is available through GitHub Pages
- new users can install and configure the authenticator by following the website docs
- maintainers have written release and local-testing instructions in the repository

## 13. Implementation Notes

Implementation should prefer adapting existing repository assets rather than replacing them wholesale.

The sibling email authenticator repository should be used as a structural reference for workflows, release docs, and website organization, while all user-facing content must be rewritten for the messaging authenticator domain.
