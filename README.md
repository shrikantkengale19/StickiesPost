# StickiesPost

StickiesPost is an Android voice-first notes and reminder app. Speak or type a request such as “remember milk, eggs, and bread” to save separate, categorized list items. Ask “what groceries do I need?” to retrieve the matching items from the local SQL database. Notes move through Open, In progress, and Closed statuses, and requests with explicit future dates or times schedule Android notifications.

## Setup

1. Create a Gemini API key in Google AI Studio and add it in the app under **Settings**. The key is entered by the user, encrypted with Android Keystore-backed AES-GCM storage, excluded from Android backup, and sent directly to Google’s Gemini API. It is not included in the app source or build.
2. The default notes sync provider is Firebase. To use Supabase instead, configure the root `local.properties` file (ignored by Git) with:

   ```properties
   supabase.url=https://<project-ref>.supabase.co
   supabase.publishable_key=sb_publishable_...
   ```

   Create a `public.note_items` table with a positive `bigint` identity `id`, `text`, `category`, `created_at_millis`, nullable `reminder_at_millis`, and `status` columns. Enable Realtime for the table and configure restrictive RLS. Build with `.\gradlew.bat assembleDebug -PnotesSyncProvider=SUPABASE`. This provider listens to Supabase and mirrors notes into Room; it does not upload local edits or authenticate users, so do not expose private notes through a public read policy.
3. For the default Firebase notes sync, enable **Email/Password** in Firebase Console → Authentication → Sign-in method. Put this Firebase project's app-specific `google-services.json` at `app/google-services.json`. Deploy the included per-user Firestore rules using `firebase deploy --only firestore:rules --project <your-project-id>`, then build with `.\gradlew.bat assembleDebug` or explicitly `.\gradlew.bat assembleDebug -PnotesSyncProvider=FIREBASE`. The app provides email sign-up/sign-in; each user can read and write only their own notes. Firestore collection `notes` uses string document IDs and the fields `text`, `category`, `createdAtMillis`, optional `reminderAtMillis`, `status`, and `ownerUserId`. Room remains the app-facing note store and Firestore SDK handles offline caching/queued writes. Each device must sign in to the same account to share notes. Recurring tasks are currently local to a device and are not synced.
   For local testing only, optional `firebase.test.email` and `firebase.test.password` values in root `local.properties` auto-sign in the **debug** build on launch. These values are packaged into the debug APK and can be extracted from it: use only a disposable test account, never production credentials, and do not distribute that APK. Release/unsigned builds do not embed these values.
4. Use the app's **Speak** control for Android speech recognition, or type a command. Allow notifications when Android asks if you create a timed reminder.
5. Build with `.\gradlew.bat assembleDebug`.

Gemini command analysis uses `gemini-3.8-flash`; temporary rate limits and server errors are retried up to twice with backoff. Availability, free-tier quotas, and pricing are governed by Google and may change. Voice transcription is provided by the device's Android speech-recognition service.

## Structure

- `domain/` contains note and assistant models, repository/scheduler contracts, and the command use case.
- `data/local/` and `data/repository/` implement SQL persistence with Room.
- `data/remote/` calls Gemini and validates its structured command response; it also syncs notes from the selected Supabase or Firebase backend with Room (Supabase is inbound-only; Firebase syncs notes per signed-in account).
- `data/security/` encrypts the user's API key using Android Keystore.
- `presentation/` contains the Compose screen and state-owning ViewModel.
- `reminders/` schedules and displays Android reminder notifications.

For architecture details and requirements for future code updates, see [CODEBASE_GUIDE.md](CODEBASE_GUIDE.md).
