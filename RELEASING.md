# Releasing

This project follows [Semantic Versioning](https://semver.org/).

## Steps

1. Update `VERSION_NAME` in `gradle.properties` to the new version (e.g. `2.0.0`).
2. Confirm the tag does not already exist:
   ```
   git ls-remote --tags origin refs/tags/v2.0.0
   ```
   Stop if the command returns a matching tag.
3. No separate IR plugin version bump is needed — `ksm-ir-plugin` reads `VERSION_NAME` and embeds it into `version.properties` during the build.
4. Update `CHANGELOG.md` — move items from `[Unreleased]` under a new heading:
   ```
   ## [2.0.0] - YYYY-MM-DD
   ```
5. Commit the release changes and land them on `develop`.
6. Tag the release commit and push only that tag:
   ```
   git tag v2.0.0 <release-commit>
   git push origin refs/tags/v2.0.0
   ```

CI will pick up the `v*` tag, sign the artifacts, and publish `ksm`, `ksm-effects`, and
`ksm-ir-plugin` to Maven Central. The release runs on macOS so the iOS device and simulator
artifacts are published alongside the JVM artifacts.

7. Confirm the Release workflow succeeds, then create a GitHub release for the existing tag
   using the corresponding changelog entry as its release notes.

## Notes

- Pre-release versions use suffixes like `0.0.1-alpha`, `0.1.0-beta.1`.
- Signing is skipped locally when `ORG_GRADLE_PROJECT_signingInMemoryKey` is unset.
- The IR plugin must be at the same version as the core library — both are published together by CI.
