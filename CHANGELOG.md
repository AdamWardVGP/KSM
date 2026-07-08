# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.5-beta] — 2026-07-08

### Fixed
- **Breaking:** Downgraded Kotlin from 2.3.0 to 2.2.20 for consumer compatibility. `ksm-ir-plugin`'s `CompilerPluginRegistrar` no longer overrides `pluginId` (not present on the 2.2.x API), and the plugin's supported-Kotlin-version guard now checks for `2.2.x` instead of `2.3.x`.

## [0.0.4-beta] — 2026-07-08

### Added
- `ksm-effects` module: `withEffects` + `onEnter<State>() effect { ... }` DSL for state-entry side effects, decoupled from `:ksm` core. Effect result is dispatched back into the machine as an `Event`; an in-flight effect is cancelled automatically when its state is left.
- `ksm { outputDir = ... }` Gradle extension to override where `ksm-ir-plugin` writes generated `.mmd` files (defaults to `build/ksmGraphs/`).

### Changed
- Side effect DSL simplified to one effect per state; registering a second `effect` for the same state now throws instead of silently replacing it. The previous `effect ::a and ::b` chaining is no longer supported.
- Effect/action names in generated Mermaid notes now suffixed with `﹙﹚` (fullwidth parens) to mark them as functions without colliding with Mermaid's `()` node-shape syntax
- Nested sealed state names in generated Mermaid diagrams now use `.` instead of `·` (U+00B7) as the hierarchy separator (e.g. `Combat.Fighting`).

### Removed
- **Breaking:** `SideEffect(onEnter, onExit)` and the `state { }` builder's inline `onEnter`/`onExit` hooks removed from `:ksm` core. Migrate to the `ksm-effects` module's `withEffects` DSL — note `onExit` has no direct replacement; effects are cancelled on state exit rather than given an exit hook.

## [0.0.3-alpha] — 2026-06-15

### Changed
- Android sample `minSdk` raised to 23

## [0.0.1-alpha] — 2026-03-07

### Added
- IR compiler plugin (`ksm-ir-plugin`) that generates Mermaid state diagrams at compile time
- Gradle plugin DSL support for `ksm-ir-plugin` via `id("coffee.adammakes.ksm.ir")`
- Configurable output directory for generated `.mmd` files (defaults to `build/ksmGraphs/`)
- `SavedStateHandle` integration in the sample app to survive process death
- Compose sample app with an adventure-themed state machine demo
- KDoc on all public API
- Detekt static analysis
- Spotless formatting with ktfmt
- `RELEASING.md` with release process documentation

### Changed
- Package renamed from `org.example` / `dev.adamwardvgp.ksm` → `coffee.adammakes.ksm`
- Sample dialog replaced with a flicker-free full-screen composable
- `VERSION_NAME` introduced in `gradle.properties` for release version management

### Fixed
- License URL typo in README
- IR plugin generating duplicate Mermaid graph files for property-backed state machines
- `mavenPublishing` coordinates version conflict with project version
