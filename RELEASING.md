# Releasing

This project follows [Semantic Versioning](https://semver.org/).

## Steps

1. Update `VERSION_NAME` in `gradle.properties` to the new version (e.g. `0.1.0`).
2. Confirm the tag does not already exist:
   ```
   git rev-parse "v0.1.0" >/dev/null 2>&1 && echo "tag exists"
   ```
3. No separate IR plugin version bump is needed — `ksm-ir-plugin` reads `VERSION_NAME` and embeds it into `version.properties` during the build.
4. Update `CHANGELOG.md` — move items from `[Unreleased]` under a new heading:
   ```
   ## [0.1.0] — YYYY-MM-DD
   ```
5. Commit: `git commit -m "Release 0.1.0"`
6. Tag and push:
   ```
   git tag v0.1.0
   git push origin main --tags
   ```

CI will pick up the `v*` tag, sign the artifacts, and publish both `ksm` and `ksm-ir-plugin` to Maven Central.

## Notes

- Pre-release versions use suffixes like `0.0.1-alpha`, `0.1.0-beta.1`.
- Signing is skipped locally (no `SIGNING_KEY` env var required for local builds).
- The IR plugin must be at the same version as the core library — both are published together by CI.
