# Release Guide

This repository publishes release artifacts through GitHub Actions.

## Release Flow

A release is a two-step, PR-reviewed flow:

1. Run **Actions → Prepare release → Run workflow** (`prepare-release.yml`), entering the version
   to release (e.g. `26.0.5`, no leading `v`, no `-KC` suffix). It bumps `pom.xml` on a
   `release/v<version>` branch (always cut from `main`) and opens a `Release <version>` PR.
   Nothing is tagged or published yet.
2. Review and merge that PR. Merging runs `release.yml` (`Release`), which tags `v<version>` and
   creates a GitHub Release with auto-generated notes, then runs `publish.yml`
   (`Publish packages`) to build and publish the jars for the last 10 Keycloak releases to Maven
   Central and GitHub Packages, and attach them to the release.
3. Verify publication on Maven Central, GitHub Packages and the GitHub Release page.

Safety check: the tag/publish step refuses to run unless `pom.xml` at the merge commit matches
the version in the `release/v<version>` branch name, so an unrelated branch with that prefix
cannot trigger a release.

## New Keycloak Versions

A new Keycloak release does not need a new release of this project. Each jar is published as
`<version>-KC<keycloak-version>`, so an existing tag can be built and published again for a new
Keycloak version.

- **Weekly, automatically:** `check-keycloak-releases.yml` runs every Monday. It publishes the
  latest release for every Keycloak version released after it (by date, so a patch on an older
  Keycloak line counts too). If the latest release has no jars at all (its own publish failed or
  is still running), it publishes nothing and logs a warning. It can also be run by hand from
  **Actions → Check for new Keycloak releases**. GitHub disables scheduled workflows after 60
  days without repository activity; if that happens, re-enable it from the Actions tab.
- **By hand:** run **Actions → Publish packages → Run workflow**. Leave `tag` empty for the
  latest release, and set `keycloak_versions` (e.g. `26.8.2`), or leave it empty for the last 10
  Keycloak releases. Use this to publish a new Keycloak version between weekly runs, or to fill
  an older gap the weekly check leaves alone. Only plain tags like `v26.0.4` are supported, not
  the older per-Keycloak tags such as `v26.0.2-KC26.6.1`.

Published jars are never rebuilt or replaced. A Keycloak version whose jar is already attached
to the release is skipped. A version already on Maven Central but missing from the release (for
example after a failed run) is not rebuilt: Central's signed files are attached instead.

Each build runs the tests, including the shaded-jar integration tests, against that Keycloak
version. If they fail, nothing is published for it, and the code needs a fix and a normal
release. The weekly check retries a failing new version every week until then.

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
`publish.yml` sets each jar's version at deploy time, following the pattern:

```
<plugin-version>-KC<keycloak-version>
```

Example:

```
26.0.2-KC26.6.2
```

## Notes

- Maven Central publishing uses the `release` Maven profile.
- GitHub Packages receives the same signed files as Maven Central (`deploy:deploy-file`), not a
  separate build.
- `publish.yml` derives the published project version from the Git tag by stripping the leading `v`.
