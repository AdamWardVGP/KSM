# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- IR compiler plugin (`ksm-ir-plugin`) that generates Mermaid state diagrams at compile time
- Gradle plugin DSL support for `ksm-ir-plugin` via `id("coffee.adammakes.ksm.ir")`
- Configurable output directory for generated `.mmd` files (defaults to `build/ksmGraphs/`)
- `SavedStateHandle` integration in the sample app to survive process death
- Compose sample app with an adventure-themed state machine demo
- KDoc on all public API
- Detekt static analysis
- Spotless formatting with ktfmt

### Changed
- Package renamed from `org.example` / `dev.adamwardvgp.ksm` → `coffee.adammakes.ksm`
- Sample dialog replaced with a flicker-free full-screen composable

### Fixed
- License URL typo in README

## [0.0.1-SNAPSHOT] — Initial development snapshot
