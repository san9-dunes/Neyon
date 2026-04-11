# Project Guidelines

These are the canonical workspace instructions for AI coding agents in this repository.

## Build And Test

Use Gradle from repo root:

- Build debug APK: `./gradlew assembleDebug`
- Build release APK: `./gradlew assembleRelease` (requires signing setup)
- Build nightly APK: `./gradlew assembleNightly`
- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`
- Full verification: `./gradlew check`

For CI triggers, signing secrets, and release workflow details, see [CI.md](CI.md).

## Architecture

- App type: Android manga reader (Kotlin, Gradle, minSdk 23, targetSdk 36).
- Structure: feature-first packages under `app/src/main/kotlin/io/github/landwarderer/futon/` plus shared `core/`.
- DI: Hilt; central bindings/providers in `app/src/main/kotlin/io/github/landwarderer/futon/core/AppModule.kt`.
- App bootstrap: `app/src/main/kotlin/io/github/landwarderer/futon/core/BaseApp.kt`.
- Data: Room (`MangaDatabase`) with invalidation observers.
- Background: WorkManager with Hilt workers.
- Networking/images: OkHttp + Coil pipeline.

## Conventions

- Performance is prioritized over cosmetic refactors; avoid new dependencies unless required.
- Do not manually edit translation string resources; translations are managed via Weblate.
- Follow existing feature package structure and base classes instead of introducing new architectural patterns ad hoc.
- Keep changes scoped; avoid unrelated refactors in the same patch.

## Kotlin And Android Rules

- Always preserve coroutine cancellation:
  - Prefer `runCatchingCancellable` over `runCatching`.
  - Re-throw `CancellationException`; never swallow it.
- Use `printStackTraceDebug()` for debug-only exception logging.
- Import order:
  1. `android` / `androidx`
  2. third-party libraries
  3. `io.github.landwarderer.futon.*`
  4. `javax.inject.*` (last)

## Project-Specific Pitfalls

- Parser dependency is `com.github.clquwu:kotatsu-parsers-redo` (JitPack), with optional override:
  - `./gradlew assembleDebug -DparsersVersionOverride=<short-sha>`
- Parser/source issues should generally be routed to the parser repo, not this app repo:
  - <https://github.com/Kotatsu-Redo/kotatsu-parsers-redo>
- Release signature validation is enforced in `app/src/main/kotlin/io/github/landwarderer/futon/core/os/AppValidator.kt`; avoid changing signing behavior unintentionally.
- Manifest has extensive deep links and app links; validate intent/deep-link behavior when touching activities or URI handling.

## Link-First References

- Setup and common commands: [README.md](README.md)
- Contribution policy: [CONTRIBUTING.md](CONTRIBUTING.md)
- CI/CD and signing: [CI.md](CI.md)

## Suggested Next Customizations

If this repo needs more targeted behavior, add applyTo-scoped instructions instead of expanding this file:

- `.github/instructions/android-tests.instructions.md` with `applyTo: "app/src/{test,androidTest}/**"`
- `.github/instructions/manifest-links.instructions.md` with `applyTo: "app/src/main/AndroidManifest.xml"`
- `.github/instructions/room-db.instructions.md` with `applyTo: "app/src/main/kotlin/**/core/db/**"`
