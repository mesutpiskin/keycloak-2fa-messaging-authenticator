# Release Guide

This repository publishes release artifacts through GitHub Actions.

## Triggers

The release workflow supports:

- publishing a GitHub Release
- pushing a Git tag that matches `v*`
- manual workflow dispatch for maintenance

## Required GitHub Secrets

Configure these repository secrets before publishing:

- `CENTRAL_TOKEN_USERNAME`
- `CENTRAL_TOKEN_PASSWORD`
- `GPG_SIGNING_KEY`
- `GPG_SIGNING_KEY_PASSWORD`

## Recommended Release Flow

1. Update the changelog or release notes.
2. Create and push a version tag such as `v26.0.1-KC26.6.1`.
3. Optionally publish a GitHub Release using the same tag.
4. Wait for the `Publish packages` workflow to complete.
5. Verify publication on Maven Central, GitHub Packages, and the GitHub Release assets page.

## Versioning

This project uses a version pattern similar to:

```
<plugin-version>-KC<keycloak-version>
```

Example:

```
26.0.0-KC26.6.1
```

## Notes

- Maven Central publishing uses the `release` Maven profile.
- GitHub Packages publishing uses the `github` Maven profile.
- GitHub Actions derives the published project version from the Git tag by stripping the leading `v`.
