# Keycloak Version Compatibility

| Plugin Version | Keycloak Version | Status |
|---|---|---|
| 26.0.0 | 26.6.1 | Primary target (default build) |
| 26.0.0 | 26.0.0 - 26.5.x | Supported; build with `-Dkeycloak.version=<x>` |
| 26.0.0 | 25.0.0 - 25.x | Supported via CI matrix |
| < 25.0.0 | — | Not supported in v1.0 (roadmap: v2 may add 23.x) |

## Building against another Keycloak version

```bash
mvn clean package -Dkeycloak.version=25.0.0
```

## Known limitations

- `SubjectCredentialManager` API was finalized in 24.x; older Keycloaks require a custom build of the credential provider.
- AWS SNS SDK (v2) requires JDK 17+; this plugin targets JDK 21.
