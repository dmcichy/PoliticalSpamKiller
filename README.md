# PoliticalTextKiller

A personal-use Android app that silently kills political and donation-solicitation
spam texts before you ever see or hear them, while keeping Google Messages installed
and untouched as your default SMS app.

## How it works

PoliticalTextKiller runs as a parallel SMS listener alongside Google Messages.
When a text arrives, it races through a classification pipeline:

1. **Contact check** -- messages from your contacts always pass through
2. **User allowlist** -- explicitly permitted numbers always pass
3. **User blocklist** -- explicitly blocked numbers are always killed
4. **Keyword matching** -- word-boundary matching against a configurable keyword list
5. **Heuristic rules** -- shortcode + donation indicators, campaign names + fundraising
   verbs, known fundraising platform domains
6. **ML classifier** -- optional on-device TensorFlow Lite model (disabled by default)
7. **Default** -- if nothing matches, the message passes through

When a message is classified as spam, the app:
- Deletes it from the SMS inbox (`content://sms/inbox`)
- Cancels the Google Messages notification before you hear it
- Logs it to a local "Spam Vault" for review

## Requirements

- Samsung Galaxy S25+ (or any Android 12+ device)
- Google Messages as the default SMS app
- Android Studio (for building)
- ADB access (for first-time sideload)

## Build & Install

### 1. Open in Android Studio

Open the project root in Android Studio. It will prompt to download the Gradle
wrapper and sync dependencies. Let it complete.

### 2. Build the APK

```bash
./gradlew :app:assembleRelease
```

Or use the debug variant for development:

```bash
./gradlew :app:assembleDebug
```

### 3. Install via ADB

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Grant permissions

Open the app -- the setup wizard will walk you through:

1. **SMS permissions** (Receive SMS, Read SMS)
2. **Contacts access** (so contacts are never blocked)
3. **Notification Access** (to cancel Google Messages notifications)
4. **Post Notifications** (for boot warnings and weekly summaries)

For Notification Access, you can also run:

```bash
adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
```

Toggle **PTK Notification Monitor** ON.

### 5. Verify

```bash
adb shell dumpsys package com.personal.ptk | findstr SMS_RECEIVED
```

You should see the `SmsReceiver` registered with priority 999.

## Training the ML model (optional)

The ML classifier is disabled by default. To train and enable it:

```bash
cd ml/
pip install -r requirements.txt
python train_model.py
```

This generates a TFLite model and copies it to `app/src/main/assets/`.
Rebuild and reinstall the APK, then enable "ML Classifier" in Settings.

For better results, export your vault CSV from the app and add it to
`ml/data/training_data.csv` as labeled training data.

## Usage

Once installed, the app runs silently in the background. You'll never need to
open it unless you want to:

- **Review killed messages** -- open the Vault
- **Add/remove keywords** -- open Rules
- **Allow a false-positive sender** -- tap "Allow Sender" on any vault entry
- **Restore a false positive** -- tap "Restore" on any vault entry
- **Toggle protection on/off** -- use the kill switch on the main screen
- **Export data** -- tap the share icon in the Vault screen

## Project structure

```
app/src/main/kotlin/com/personal/ptk/
├── App.kt                  -- Application class, dependency wiring
├── sms/
│   ├── SmsReceiver.kt      -- SMS broadcast listener
│   ├── SmsScrubber.kt       -- Deletes killed SMS from inbox
│   └── ContactGuard.kt     -- Contacts lookup
├── classify/
│   ├── Classifier.kt       -- Classification pipeline orchestrator
│   ├── Verdict.kt           -- Allow / Kill sealed class
│   ├── KeywordRules.kt     -- Word-boundary keyword matching
│   ├── HeuristicRules.kt   -- Shortcode/donation/campaign heuristics
│   ├── MlClassifier.kt     -- TFLite model wrapper
│   └── DefaultKeywords.kt  -- Shipped keyword list
├── notif/
│   ├── NotificationKiller.kt -- Cancels Google Messages notifications
│   └── KillRingBuffer.kt    -- In-memory bridge for notification matching
├── data/
│   ├── PtkDatabase.kt       -- Room database
│   ├── VaultDao.kt / RuleDao.kt
│   └── entities/
├── boot/
│   └── BootReceiver.kt     -- Post-reboot permission check
├── worker/
│   └── WeeklySummaryWorker.kt -- Weekly kill count notification
├── util/
│   ├── CsvExporter.kt      -- Vault CSV export
│   └── KeywordSuggester.kt -- v1.1 keyword suggestions
└── ui/
    ├── MainActivity.kt
    ├── Navigation.kt
    ├── SetupScreen.kt / MainScreen.kt / RulesScreen.kt
    ├── VaultScreen.kt / SettingsScreen.kt
    └── theme/Theme.kt
```

## License

Personal use only. Not distributed.
