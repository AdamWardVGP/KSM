# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Effect/action names in generated Mermaid notes now suffixed with `﹙﹚` (fullwidth parens) to mark them as functions without colliding with Mermaid's `()` node-shape syntax

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
