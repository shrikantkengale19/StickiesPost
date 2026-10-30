# StickiesPost Codebase and Update Guide

This document records the current implementation and the project requirements that should guide future code updates. Keep it aligned with the code when architecture or behavior changes.

## Project at a glance

- Single-module Android application: `:app`.
- Kotlin, Jetpack Compose, and Material 3 UI.
- Kotlin package, Android namespace, and installable application ID: `com.example.stickiespost`; launcher app name: `stpTest`.
- SDK configuration: compile/target SDK 37, minimum SDK 24.
- Java source and target compatibility: Java 11.
- Versions and dependency aliases belong in `gradle/libs.versions.toml`; module dependencies use generated `libs` accessors.
- The application is a voice- and text-driven notes/reminders app. Speech recognition is launched through Android's speech-recognition activity; requests are interpreted by Gemini.
- Build property `notesSyncProvider` selects `FIREBASE` (default) or `SUPABASE`. Firebase selection requires the app-specific `app/google-services.json`; Supabase selection reads `supabase.url` and `supabase.publishable_key` from the ignored root `local.properties`. Only one provider is started per build.
- Firebase builds provide email/password sign-up and sign-in using Firebase Authentication. `RoomNotesRepository` is the app-facing note store and applies local changes to Room before writing them through to Firestore. Firestore snapshot changes update Room; reads and writes are scoped to the signed-in UID and Firestore's Android SDK caches/queues data while offline. Use `firestore.rules` and enable Email/Password sign-in in Firebase Console.
- Supabase builds install PostgREST and Realtime only (the Auth dependency is not wired into the client). `SupabaseNoteSync` listens to `public.note_items` and replaces the cloud-mirrored rows in Room; it does not upload local changes and there is no user sign-in or per-user isolation in the Supabase implementation. Configure restrictive RLS and do not expose private notes through an anonymous/public read policy.
- Optional `firebase.test.email` and `firebase.test.password` properties in root `local.properties` enable automatic sign-in in debug builds only. They are embedded in the debug APK; only use a disposable test account and never distribute that APK. Release/unsigned builds contain empty test credential fields.
- Release and Unsigned builds enable R8 code shrinking/optimization and resource shrinking, using the optimized Android default ProGuard rules plus `app/proguard-rules.pro`. Release currently uses the debug signing key; the separate Unsigned build type inherits release optimizations but has no signing configuration. Use `assembleUnsigned` for its unsigned APK.

## Architecture and ownership

| Area | Location | Responsibility |
|---|---|---|
| Android entry point and wiring | `app/src/main/java/com/example/stickiespost/MainActivity.kt`, `StickiesPostApplication.kt` | Enables edge-to-edge UI, chooses one sync provider at build time, wires Room/platform services, owns Firebase client setup, and hosts the screens. |
| Presentation | `presentation/NotesScreen.kt`, `presentation/NoteStatusScreen.kt`, `presentation/TasksScreen.kt`, `presentation/NotesViewModel.kt`, `presentation/TasksViewModel.kt` | Compose screens, UI state, command submission, category selection, API-key settings, and note/task completion state. |
| Domain | `domain/model/`, `domain/assistant/`, `domain/repository/`, `domain/reminder/`, `domain/usecase/` | App models and contracts; `ProcessCommandUseCase` validates interpreted commands and coordinates storage/reminders. Keep Android-specific APIs out of this layer. |
| Local persistence | `data/local/`, `data/repository/` | Room schema/DAO and mapping between database entities and domain models. |
| Gemini integration | `data/remote/GeminiCommandInterpreter.kt` | Builds the structured prompt, calls Gemini over HTTP, validates the response, and retries retryable HTTP failures. |
| Supabase note sync | `data/remote/SupabaseClientFactory.kt`, `data/remote/SupabaseNoteSync.kt` | Initializes PostgREST/Realtime and mirrors the initial accessible note list and subsequent table changes into Room (one-way remote-to-local). |
| Firebase auth and note sync | `StickiesPostApplication.kt`, `presentation/FirebaseAuthScreen.kt`, `data/remote/FirebaseNoteStore.kt`, `data/remote/FirebaseNoteSync.kt`, `data/repository/RoomNotesRepository.kt` | Authenticates users and synchronizes their note changes between Room and their Firestore documents. |
| API-key protection | `data/security/ApiKeyStore.kt` | Encrypts the user-provided key with Android Keystore AES-GCM and stores ciphertext in private preferences. |
| Android reminders | `reminders/` | Implements the domain scheduler with `AlarmManager` and displays notifications from a manifest-registered receiver. |
| Shared visual theme | `ui/theme/` | Material color schemes and typography. |
| Platform configuration and resources | `app/src/main/AndroidManifest.xml`, `app/src/main/res/` | Permissions, activity/receiver registration, backup exclusions, labels, and app resources. |

`MainActivity` currently performs manual dependency wiring. There is no dependency-injection framework in the project; preserve that approach unless a broader architecture change is requested.

## Main behavior and data flow

1. `NotesScreen` accepts typed input or launches Android speech recognition. A floating **+** button opens the note composer bottom sheet. The direct **Save Note** action saves the draft to Room without Gemini, using the selected category; it requires a specific category rather than `ALL`. Successful saves clear the draft and dismiss the sheet.
2. `NotesViewModel.processCommand` owns the loading/error/result state and invokes `ProcessCommandUseCase`.
3. Direct saves use `AddNoteUseCase` to validate the draft/category and persist one note. The Gemini path asks `CommandInterpreter` to turn the utterance into a `SAVE` or `LIST` command.
4. `GeminiCommandInterpreter` obtains the key from `ApiKeyStore`, sends the request to Gemini, and converts the structured JSON response into domain values.
5. For `SAVE`, the use case validates the category, items, and future reminder time, stores one `NoteItem` per parsed item, then schedules any timed reminders.
6. For `LIST`, the repository queries Room by category (`ALL` includes every category).
7. Room's observable note stream updates the UI without an explicit sort order. The Statuses screen groups notes under Open, In progress, and Closed; tapping a note's outlined initial advances only that note through the cycle. Notes can be permanently deleted from this screen after confirmation.
8. A reminder can be set or changed separately on each note with its clock control. The date picker blocks past dates, the time is validated as future, and the reminder is persisted before its `AlarmManager` broadcast is scheduled. Android 13+ notification permission is requested when needed.
9. The Tasks screen is opened from the Notes screen. Tasks are stored separately in `recurring_tasks` and support daily, selected-weekday weekly, or selected-day monthly recurrence. Each task has a reminder time and Open/Fulfilled state. Completing a task affects only its current occurrence; when its next reminder fires, the app sends a default-sound notification, advances the scheduled occurrence, and reopens the task. Task alarms are restored after device restart, and notification permission is requested on Android 13+.
10. In a Supabase build, `public.note_items` supplies a live, one-way note feed. Realtime emits the initial accessible rows and subsequent inserts, updates, and deletes; each full snapshot atomically replaces the cloud-mirrored rows in Room. Cloud IDs must be positive `bigint` values and are stored as negative Room IDs so they cannot collide with locally auto-generated positive IDs. Local note edits are not uploaded to Supabase. The feed runs while the app process is active and reconnects on the next app launch; it is not a background sync service.
11. In a Firebase build, email/password sign-in is required before accessing notes. Local note additions, status changes, reminder changes, and deletions are written to Room and then written through to Firestore. Each note stores a stable UUID `remoteId` and owner UID in Room (added by migrations 3→4 and 4→5). Firestore reads are filtered by `ownerUserId`; `firestore.rules` restrict each user to their own documents. Firestore's Android SDK caches snapshots and queues writes while offline. Documents in the `notes` collection contain `text`, `category`, `createdAtMillis`, optional `reminderAtMillis`, `status`, and `ownerUserId`. Notes are isolated by account; sign in with the same account on each device to share them. No explicit version-based conflict resolution is implemented.
12. Recurring tasks are stored and consumed only through Room on each device; neither sync provider currently synchronizes the `recurring_tasks` table. Their Android alarms and notification state are also device-local.

The Supabase table contract is: positive `bigint` identity `id`, `text` text, `category` text, `created_at_millis` bigint, nullable `reminder_at_millis` bigint, and `status` text. Category and status values must match the app's stored enum names (category cannot be `ALL`; status is `OPEN`, `IN_PROGRESS`, or `CLOSED`). Configure the table for Supabase Realtime and apply RLS policies appropriate to the app's data-access model. This app currently has no Supabase sign-in, so a publishable key alone does not provide per-user access control.

The category values are `GROCERIES`, `VEGETABLES`, `HOUSEHOLD`, `BEAUTY`, `WORK`, `ELECTRONICS`, `PERSONAL`, `OTHER`, and `ALL`. `ALL` is a query/filter category, not a valid category for saving a note. Grocery shopping is for general food items, Vegetables is for fresh vegetables, Household is for home and cleaning supplies, Electronics is for electronics, and Beauty is for beauty and grooming items. Category values are stored by enum name; adding categories does not require a Room migration, but existing values must remain unchanged. **Known limitation:** `ELECTRONICS` is present in `NoteCategory` and shown in the UI, but is currently omitted from the category list in the Gemini prompt; update the prompt when fixing category inference. Note statuses are `OPEN`, `IN_PROGRESS`, and `CLOSED`; the Statuses screen groups notes by status, but note queries do not impose an ordering. Migration 1→2 maps the former completed flag to `CLOSED` and incomplete notes to `OPEN`. Reminder timestamps are epoch milliseconds in the domain/database and originate from an ISO-8601 value with UTC offset in the model response.

The note list uses the shared palette in `ui/theme/Color.kt`: rows and status circles use status-specific green, cream/sage, and blush/rust colors; category chips are soft green, selected category filters are pale pink, and unselected filters have a neutral outline.

The Room database is version 5. Migration 1→2 replaces the original completion flag with the three-state note status; 2→3 adds `recurring_tasks`; 3→4 adds the note `remoteId`; and 4→5 adds `ownerUserId`. Register the full migration chain by the app and every standalone database consumer such as reminder receivers. Firebase sync metadata exists only on notes; task rows have no remote identity or owner.

## Requirements for future updates

- Keep Kotlin package names aligned with the source directory under `com.example.stickiespost`.
- Keep Compose screens in the presentation layer and platform setup in `MainActivity` or the appropriate Android adapter. Put business rules in domain code and use contracts for persistence, interpretation, and reminder scheduling.
- When changing note fields or persistence behavior, update the domain model, Room entity/schema, mappings, DAO/repository as needed, and tests together. Increment the Room database version and provide a migration for any schema change that must preserve existing user data.
- When changing command behavior or parsed fields, update the domain command models, Gemini prompt/response validation, use case validation, UI messages, and relevant unit tests as one coherent change.
- When changing reminders or permissions, keep the scheduler, receiver, manifest declarations, runtime permission flow, and user-facing denied-permission behavior consistent.
- Never embed API keys or other credentials in source, resources, or build configuration. Keep key handling in `ApiKeyStore`; do not log the key. Maintain the backup exclusions for its preference file (`secure_gemini_settings.xml`).
- The default notes sync provider is Firebase. To select Supabase, build with `-PnotesSyncProvider=SUPABASE` and provide `supabase.url` and `supabase.publishable_key` in ignored root `local.properties`. Supabase sync is a one-way feed into Room only; keep Realtime enabled and RLS policies restrictive. There is no Supabase authentication or local-to-cloud write path.
- Firebase requires this app's `app/google-services.json`, Email/Password enabled in Firebase Authentication, and deployed `firestore.rules`. Build with `-PnotesSyncProvider=FIREBASE` (the default). Firebase uses the `notes` collection for per-user note sync with a stable Room `remoteId` and owner UID. Provider selection is build-time, not runtime.
- Recurring tasks are not synchronized. If adding remote task sync, define stable remote IDs, ownership, deletion propagation, offline behavior, conflict handling, and schema migrations before changing repositories.
- For local Firebase testing, the ignored root `local.properties` may include `firebase.test.email` and `firebase.test.password`; these enable debug-only launch sign-in and must contain only disposable test credentials because debug APK contents are inspectable.
- Preserve explicit failures and user-visible error reporting. Do not silently ignore invalid model responses, storage failures, or platform-service errors.
- Keep dependency versions in the version catalog and preserve the existing Kotlin DSL and Java 11 bytecode settings.
- Update this guide and the relevant README or Copilot instructions when the architecture, setup, or behavior materially changes.

## Tests and verification

- Local JVM tests are under `app/src/test`; `ProcessCommandUseCaseTest` uses fake interpreter/repository/scheduler implementations and is the best location for domain command-flow tests.
- Instrumented Android tests are under `app/src/androidTest` and require a connected device or emulator.
- Add tests for changed behavior rather than relying only on a successful build. Remote Gemini calls and Android platform scheduling should generally be covered through existing domain contracts/fakes in local unit tests.
- Run commands from the project root on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

`connectedDebugAndroidTest` requires a connected device or running emulator. For a narrow JVM test run, use Gradle's `--tests` filter with the fully qualified class or method name.
