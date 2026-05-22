# PoliticalTextKiller — Project Context

> **Last updated:** 2026-05-21
> **Owner:** Dave
> **Repository:** `D:\Dropbox\_CODEROOT\_Personal\PolicitcalSpamKiller`

---

## What This Project Is

An Android app (sideloaded, personal use) that silently kills political and donation-solicitation spam texts on a Samsung Galaxy S25+ running Google Messages. It listens for incoming SMS in parallel with Google Messages, classifies each message through a contact-check → allowlist → blocklist → keyword → heuristic → ML pipeline, and for spam: deletes the message from the SMS inbox, cancels the Google Messages notification, and logs the kill to a local vault. Google Messages remains the default SMS app with full RCS support. No cloud, no telemetry, fully offline.

---

## Directory Structure

```
PolicitcalSpamKiller/
├── DESIGN.md                          ← Comprehensive design document
├── .cursor/
│   ├── commands/                      ← Project-specific slash commands
│   ├── plans/
│   │   └── project-context.md         ← This file
│   └── rules/
│       └── project-context.mdc        ← Rule enforcing context updates
├── ml/                                ← (planned) Python training scripts + dataset
│   ├── train_model.py
│   └── data/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                    ← TFLite model + vocab
│       ├── kotlin/com/personal/ptk/
│       │   ├── App.kt
│       │   ├── sms/                   ← SmsReceiver, SmsScrubber, ContactGuard
│       │   ├── classify/              ← Classifier, Verdict, KeywordRules, HeuristicRules, MlClassifier
│       │   ├── notif/                 ← NotificationKiller, KillRingBuffer
│       │   ├── data/                  ← Room database, DAOs, entities
│       │   ├── boot/                  ← BootReceiver
│       │   └── ui/                    ← Jetpack Compose screens + theme
│       └── res/
├── gradle/
├── settings.gradle.kts
└── build.gradle.kts
```

---

## Key Components

| Component | Type | Purpose |
|---|---|---|
| `SmsReceiver` | BroadcastReceiver | Listens for `SMS_RECEIVED`, runs classifier, triggers kill pipeline |
| `Classifier` | Pure Kotlin | Short-circuit pipeline: contact → allowlist → blocklist → keywords → heuristics → ML → default ALLOW |
| `ContactGuard` | Repository | Checks if sender is in device contacts (contacts are always allowed) |
| `NotificationKiller` | NotificationListenerService | Cancels Google Messages notifications for killed messages |
| `SmsScrubber` | Helper | Deletes killed messages from `content://sms/inbox` |
| `KillRingBuffer` | In-memory buffer | Bridges SmsReceiver and NotificationKiller with recently-killed metadata |
| `Vault` | Room table | Stores all killed messages for user review and false-positive recovery |
| `RuleStore` | Room table | Persists user-editable keywords, allowlisted numbers, blocklisted numbers |
| `MlClassifier` | TFLite wrapper | On-device ML text classification (political spam probability) |
| `BootReceiver` | BroadcastReceiver | Verifies permissions after reboot |
| `MainActivity` | Compose Activity | Single-activity UI: setup, dashboard, rules, vault, settings |

---

## External Integrations

None. The app is fully offline with no network calls, no analytics, no cloud services.

---

## Database

- **Room** local SQLite database on the Android device
- Two tables:
  - `VaultEntry` — killed messages (sender, body, timestamp, reason, matchedRule, scrubbed)
  - `RuleEntry` — user rules (type: ALLOWLIST_NUMBER / BLOCKLIST_NUMBER / KEYWORD, value, enabled)
- Default keyword list seeded on first database creation

---

## Orchestration / Automation

- `SmsReceiver` triggers automatically on every incoming SMS (manifest-registered broadcast)
- `NotificationKiller` runs as a bound service (notification listener)
- `BootReceiver` fires on device boot to verify permissions
- `WorkManager` periodic task (7-day interval) for weekly summary notification

---

## Credentials & Secrets

None. No API keys, no server credentials, no Doppler integration. Self-signed APK for sideloading.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| Min SDK | API 31 (Android 12) |
| Target SDK | API 35 (Android 15) |
| UI | Jetpack Compose + Material 3 |
| Persistence | Room |
| Async | Kotlin Coroutines |
| ML | TensorFlow Lite |
| Scheduling | WorkManager |
| Testing | JUnit 5 |
| Build | Gradle (Kotlin DSL) |
| Distribution | Self-signed APK, sideload via ADB |

---

## Recent Changes

- **2026-05-21** — Full v1.0 + v1.1 implementation complete:
  - Project scaffolded with Gradle Kotlin DSL, version catalog, Room, Compose, TFLite
  - Classification pipeline: ContactGuard -> allowlist -> blocklist -> keywords -> heuristics -> ML
  - SMS interception (SmsReceiver), scrubbing (SmsScrubber), notification killing (NotificationKiller)
  - KillRingBuffer bridges SMS receiver and notification listener
  - Room database with VaultEntry and RuleEntry tables, default keyword seeding
  - Jetpack Compose UI: SetupScreen, MainScreen, RulesScreen, VaultScreen, SettingsScreen
  - v1.1 features: KeywordSuggester, WeeklySummaryWorker, CsvExporter
  - Python ML training pipeline (ml/train_model.py) with synthetic starter dataset
  - Unit tests for Classifier, KeywordRules, HeuristicRules, KillRingBuffer
  - README with build/install/sideload instructions
- **2026-05-21** — Initial design document (`DESIGN.md`) created.
