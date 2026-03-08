# Releasing

This project follows [Semantic Versioning](https://semver.org/).

## Steps

1. Update `VERSION_NAME` in `gradle.properties` to the new version (e.g. `0.1.0`).
2. Update the `version` string in `ksm-ir-plugin/src/main/kotlin/coffee/adammakes/ksm/ir/KsmGradlePlugin.kt` to match.
3. Update `CHANGELOG.md` — move items from `[Unreleased]` under a new heading:
   ```
   ## [0.1.0] — YYYY-MM-DD
   ```
4. Commit: `git commit -m "Release 0.1.0"`
5. Tag and push:
   ```
   git tag v0.1.0
   git push origin main --tags
   ```

CI will pick up the `v*` tag, sign the artifacts, and publish both `ksm` and `ksm-ir-plugin` to Maven Central.

## Notes

- Pre-release versions use suffixes like `0.0.1-alpha`, `0.1.0-beta.1`.
- Signing is skipped locally (no `SIGNING_KEY` env var required for local builds).
- The IR plugin must be at the same version as the core library — both are published together by CI.
