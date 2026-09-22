# Release Guide

This repository publishes release artifacts through GitHub Actions.

## Triggers

The release workflow is a two-step, PR-reviewed flow:

1. **`workflow_dispatch`** with a `version` input (e.g. `26.0.2`) — bumps `pom.xml` on a
   `release/v<version>` branch and opens a PR to `main`. Nothing is tagged or published yet.
2. **Merging that PR** — tags `v<version>`, creates a GitHub Release with auto-generated notes,
   then builds and publishes the jars for the last 10 Keycloak releases.

## Required GitHub Secrets

Configure these repository secrets before publishing:

- `CENTRAL_TOKEN_USERNAME`
- `CENTRAL_TOKEN_PASSWORD`
- `GPG_SIGNING_KEY`
- `GPG_SIGNING_KEY_PASSWORD`

Optional:

- `RELEASE_TOKEN` — a PAT with `contents` and `pull-requests` write, used instead of the default
  `GITHUB_TOKEN`. Without it, the release PR is opened by `GITHUB_TOKEN`, which cannot trigger the
  `ci.yml` checks on that PR (a GitHub Actions limitation). Only matters if `main` requires status
  checks to merge.

## Recommended Release Flow

1. Run the `Publish packages` workflow via `workflow_dispatch`, entering the version to release
   (e.g. `26.0.2`, no leading `v`, no `-KC` suffix).
2. Review the opened `Release <version>` PR — it bumps `pom.xml` on `main`.
3. Merge the PR. This tags `v<version>`, publishes the GitHub Release, and kicks off the matrix
   publish job.
4. Wait for the `Publish packages` workflow to complete.
5. Verify publication on Maven Central, GitHub Packages, and the GitHub Release assets page.

## Versioning

`main`'s `pom.xml` carries the base plugin version only (e.g. `26.0.2`), with no `-KC` suffix.
Each matrix publish job sets its own version at deploy time, following the pattern:

```
<plugin-version>-KC<keycloak-version>
```

Example:

```
26.0.2-KC26.6.2
```

## Notes

- Maven Central publishing uses the `release` Maven profile.
- GitHub Packages publishing uses the `github` Maven profile.
- GitHub Actions derives the published project version from the Git tag by stripping the leading `v`.
