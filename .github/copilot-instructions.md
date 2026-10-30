# Copilot instructions

## Build, test, and lint

Run these commands from the project root on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testDebugUnitTest --tests "com.example.stickiespost.ExampleUnitTest.addition_isCorrect"
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

`connectedDebugAndroidTest` requires a connected device or running emulator. Use Gradle's `--tests` filter with a fully qualified test class or method to run a narrower test selection.

## Architecture

- This is a single-module Android application: `settings.gradle.kts` includes only `:app`.
- `MainActivity.kt` selects the build-time notes sync provider, wires Room, Gemini, API-key storage, reminder schedulers, and Firebase auth where selected, then hosts the app screens.
- Presentation lives in `presentation/`; domain models, contracts, and command processing live in `domain/`; Room persistence, Gemini access, and secure API-key storage live in `data/`; Android alarm/notification adapters live in `reminders/`.
- Shared Compose styling is in `ui/theme/`. Android configuration and assets live under `app/src/main/AndroidManifest.xml` and `app/src/main/res/`; JVM tests are in `app/src/test` and device/emulator tests are in `app/src/androidTest`.
- Read `CODEBASE_GUIDE.md` before making architectural or behavior changes. It documents the current data flow, update requirements, and test guidance.

## Project conventions

- Keep Kotlin packages aligned with the source path under `com.example.stickiespost`; UI theme code belongs in the existing `com.example.stickiespost.ui.theme` package.
- Compose UI is enabled in the app module. Follow the existing Compose entry-point and theming pattern when adding screens, and keep Android activity/platform setup in `MainActivity`.
- Notes use the `OPEN`, `IN_PROGRESS`, and `CLOSED` status model. The Statuses screen groups notes by status, while Room note queries do not impose a sort order. Persisted note-schema changes require a Room version increment and a data-preserving migration.
- Room is the app-facing source for notes. Firebase sync is scoped by authenticated Firebase UID and supports note writes/snapshots; Supabase sync is currently a one-way inbound feed without authentication. Recurring tasks remain Room-only and do not sync between devices.
- Declare dependency versions and aliases in `gradle/libs.versions.toml`, then use the generated `libs` accessors in module `build.gradle.kts` files.
- The project uses Kotlin DSL Gradle build files and targets Java 11 bytecode; preserve those settings when changing build configuration.
