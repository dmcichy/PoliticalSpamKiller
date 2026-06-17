# PoliticalTextKiller — Project Context

> **Last updated:** 2026-06-17
> **Owner:** Dave
> **Repository:** `D:\Dropbox\_CODEROOT\_Personal\PolicitcalSpamKiller`
> **GitHub:** Public repo at `github.com/[owner]/PoliticalTextKiller` (sensitive files gitignored)
> **Target device:** Samsung Galaxy S25+ (SM-S936U), Android 16, One UI

---

## What This Project Is

An Android app that silently kills political and donation-solicitation spam texts on a Samsung Galaxy S25+ running Google Messages. It listens for incoming SMS in parallel with Google Messages, classifies each message through a contact-check → allowlist → blocklist → keyword → heuristic → ML pipeline, and for spam: deletes the message from the SMS inbox, cancels the Google Messages notification, and logs the kill to a local vault. Google Messages remains the default SMS app with full RCS support. Fully offline — no cloud, no telemetry.

**Distribution model:** Google Play Store at $0.99/month with a 30-day free trial. Play Billing Library handles subscriptions. Privacy policy hosted at `dmc-inc.com/privacy`.

**Shizuku integration:** Uses Shizuku for "Power Mode" — elevated ADB-level permissions for notification cancellation and SMS deletion without being the default SMS app. Shizuku's server process is fragile on non-rooted Samsung devices (killed by ADB daemon restarts, lost on reboot). The `tools/` folder has recovery scripts.

---

## Directory Structure

```
PolicitcalSpamKiller/
├── .cursor/
│   ├── plans/
│   │   ├── project-context.md         ← This file
│   │   ├── DESIGN.md                  ← Comprehensive design document
│   │   └── shizuku_power_mode_*.md    ← Shizuku implementation plan
│   └── rules/
│       └── project-context.mdc        ← Rule enforcing context updates
├── app/
│   ├── build.gradle.kts               ← Release signing, R8, Play Billing
│   ├── proguard-rules.pro             ← Keep rules for TFLite, Room, Shizuku, Billing
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── political_spam_model.tflite
│       │   │   └── vocab.json
│       │   ├── kotlin/com/personal/ptk/
│       │   │   ├── App.kt                       ← Application class, classifier init, billing
│       │   │   ├── billing/
│       │   │   │   ├── BillingManager.kt         ← Play Billing client wrapper
│       │   │   │   └── SubscriptionState.kt      ← Sealed class (Loading/Trial/Active/Expired/Grace)
│       │   │   ├── boot/
│       │   │   │   └── BootReceiver.kt
│       │   │   ├── classify/
│       │   │   │   ├── Classifier.kt             ← Pipeline: contact→allow→block→keyword→heuristic→ML
│       │   │   │   ├── ContactChecker.kt         ← Interface for contact lookup
│       │   │   │   ├── DefaultKeywords.kt        ← Bundled keyword list + stopwords
│       │   │   │   ├── HeuristicRules.kt         ← Campaign-name + fundraising-verb heuristic
│       │   │   │   ├── KeywordRules.kt           ← Keyword/phrase matching (Locale.ROOT, prefix-boundary)
│       │   │   │   ├── MlClassifier.kt           ← TFLite model wrapper
│       │   │   │   ├── RuleProvider.kt           ← Interface for rule storage
│       │   │   │   └── Verdict.kt                ← Allow / Kill sealed class
│       │   │   ├── data/
│       │   │   │   ├── Converters.kt
│       │   │   │   ├── PtkDatabase.kt            ← Room DB (ptk.db)
│       │   │   │   ├── RoomRuleProvider.kt
│       │   │   │   ├── RuleDao.kt
│       │   │   │   ├── VaultDao.kt
│       │   │   │   └── entities/
│       │   │   │       ├── RuleEntry.kt          ← KEYWORD / ALLOWLIST_NUMBER / BLOCKLIST_NUMBER
│       │   │   │       ├── RuleType.kt
│       │   │   │       └── VaultEntry.kt
│       │   │   ├── notif/
│       │   │   │   ├── KillRingBuffer.kt
│       │   │   │   └── NotificationKiller.kt
│       │   │   ├── sms/
│       │   │   │   ├── ComposeSmsActivity.kt
│       │   │   │   ├── ContactGuard.kt
│       │   │   │   ├── HeadlessSmsSendService.kt
│       │   │   │   ├── MmsReceiver.kt
│       │   │   │   ├── PendingDeleteReceiver.kt
│       │   │   │   ├── SmsDeliverReceiver.kt
│       │   │   │   ├── SmsLookup.kt
│       │   │   │   ├── SmsReceiver.kt
│       │   │   │   └── SmsScrubber.kt
│       │   │   ├── ui/
│       │   │   │   ├── AnalyticsScreen.kt
│       │   │   │   ├── InboxCleanupCard.kt       ← Inbox scan/purge UI card
│       │   │   │   ├── KillLogScreen.kt          ← Kill log viewer
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── MainScreen.kt             ← Dashboard
│       │   │   │   ├── Navigation.kt             ← NavHost, Screen enum, paywall gate
│       │   │   │   ├── PaywallScreen.kt          ← Subscription prompt (shown when trial expires)
│       │   │   │   ├── RulesScreen.kt            ← Keywords/Allowlist/Blocklist tabs
│       │   │   │   ├── SettingsScreen.kt         ← Kill switch, ML toggle, Shizuku, subscription
│       │   │   │   ├── SetupScreen.kt            ← Tiered setup guide (also doubles as Help)
│       │   │   │   ├── VaultScreen.kt            ← Killed messages vault with restore
│       │   │   │   └── theme/Theme.kt
│       │   │   ├── util/
│       │   │   │   ├── CsvExporter.kt
│       │   │   │   ├── InboxAnalyzer.kt          ← Top senders, year breakdown, sample messages
│       │   │   │   ├── InboxPurger.kt
│       │   │   │   ├── InboxScanner.kt           ← Bulk inbox scan using classifier
│       │   │   │   ├── ShizukuHelper.kt
│       │   │   │   └── SmsRoleHelper.kt
│       │   │   └── worker/
│       │   │       └── WeeklySummaryWorker.kt
│       │   └── res/                              ← Icons, strings, launcher XML
│       └── test/kotlin/com/personal/ptk/
│           ├── classify/
│           │   ├── ClassifierTest.kt
│           │   ├── HeuristicRulesTest.kt
│           │   └── KeywordRulesTest.kt
│           └── notif/
│               └── KillRingBufferTest.kt
├── docs/
│   ├── play-console-setup.md                     ← Manual Play Console steps
│   ├── privacy-policy.html                       ← Privacy policy (also hosted at dmc-inc.com)
│   ├── store-listing.md                          ← Play Store descriptions
│   └── store-assets/                             ← App icon, feature graphic, screenshots (PNG/JPEG)
├── ml/                                           ← Original placeholder ML scripts (superseded)
│   ├── train_model.py
│   └── data/README.md
├── ml-spam-training/                             ← Active ML pipeline (Python)
│   ├── config.py                                 ← API key loading from Doppler
│   ├── step1_extract.py                          ← Extract SMS from XML backup
│   ├── step2_label.py                            ← LLM labeling via OpenAI gpt-4o-mini
│   ├── step3_analyze.py                          ← Label distribution analysis
│   ├── step4_review.py                           ← Interactive HTML review tool
│   ├── step5_apply_corrections.py                ← Merge user corrections into labels
│   ├── step6_train.py                            ← Train TFLite model from labeled data
│   ├── requirements.txt
│   ├── README.md
│   └── .gitignore                                ← Excludes data/, *.csv, review.html
├── tools/
│   ├── restart-shizuku.bat                       ← One-click post-reboot Shizuku + wireless ADB recovery
│   └── shizuku-start.sh                          ← Corrected Shizuku start script (deployed to phone)
├── gradle/
│   ├── libs.versions.toml                        ← Version catalog (includes playBilling, shizuku, tflite)
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── keystore.properties                           ← Release keystore credentials (GITIGNORED)
├── .gitignore
└── README.md
```

---

## Key Components

| Component | Type | Purpose |
|---|---|---|
| `SmsReceiver` | BroadcastReceiver | Listens for `SMS_RECEIVED`, runs classifier, triggers kill pipeline |
| `SmsDeliverReceiver` | BroadcastReceiver | Handles `SMS_DELIVER` when PTK is default SMS app |
| `MmsReceiver` | BroadcastReceiver | Handles incoming MMS |
| `Classifier` | Pure Kotlin | Short-circuit pipeline: contact → allowlist → blocklist → keywords → heuristics → ML → default ALLOW |
| `KeywordRules` | Object | Keyword/phrase matching with `Locale.ROOT` normalization, leading `\b` word-boundary (no trailing `\b` — allows plural/suffix matches), `IGNORE_CASE`, and fallback match against raw body |
| `HeuristicRules` | Object | Candidate-name + fundraising-verb combo detection |
| `ContactGuard` | Repository | Checks if sender is in device contacts (contacts always allowed) |
| `NotificationKiller` | NotificationListenerService | Cancels Google Messages notifications for killed messages |
| `SmsScrubber` | Helper | Deletes killed messages from `content://sms/inbox` |
| `KillRingBuffer` | In-memory buffer | Bridges SmsReceiver and NotificationKiller with recently-killed metadata |
| `InboxScanner` | Utility | Bulk scans SMS+MMS inbox through classifier, deletes spam, vaults results |
| `InboxAnalyzer` | Utility | Top senders, year breakdown, sample messages for analytics UI |
| `InboxPurger` | Utility | Targeted deletion by sender or year |
| `ShizukuHelper` | Utility | Manages Shizuku connection, permission requests, and server status checks |
| `Vault` | Room table | Stores all killed messages for user review and false-positive recovery |
| `RuleStore` | Room table | Persists user-editable keywords, allowlisted numbers, blocklisted numbers |
| `MlClassifier` | TFLite wrapper | On-device ML text classification (political spam probability, threshold 0.90) |
| `BillingManager` | BillingClient wrapper | Play Billing subscription flow, 30-day trial logic, state management |
| `SubscriptionState` | Sealed class | Loading / Trial / Active / Expired / Grace — gates navigation via `hasAccess` |
| `PaywallScreen` | Composable | Shown when trial ends and no active subscription |
| `BootReceiver` | BroadcastReceiver | Verifies permissions after reboot |
| `WeeklySummaryWorker` | WorkManager | Periodic 7-day summary notification |
| `MainActivity` | Compose Activity | Single-activity UI with NavHost |

---

## Classification Pipeline

```
Incoming SMS
  ↓
1. Contact check (contacts always ALLOW)
  ↓
2. User allowlist (numbers → ALLOW)
  ↓
3. User blocklist (numbers → KILL)
  ↓
4. Keyword rules (word-boundary + phrase matching → KILL)
  ↓
5. Heuristic rules (candidate name + fundraising verb → KILL)
  ↓
6. ML classifier (TFLite, score ≥ 0.90 → KILL, opt-in toggle)
  ↓
7. Default → ALLOW
```

---

## External Integrations

| Integration | Purpose |
|---|---|
| Google Play Billing | $0.99/month subscription with 30-day trial |
| Shizuku | ADB-level permissions for notification kill + SMS delete without being default app |
| dmc-inc.com | Privacy policy hosting (static site on Oracle Cloud VM) |

---

## Database

- **Room** local SQLite database (`ptk.db`) on the Android device
- Tables:
  - `VaultEntry` — killed messages (sender, body, timestamp, reason, matchedRule, scrubbed, smsId)
  - `RuleEntry` — user rules (type: KEYWORD / ALLOWLIST_NUMBER / BLOCKLIST_NUMBER, value, enabled, createdAt)
- Default keyword list seeded on first run; retired keywords auto-removed on startup

---

## Orchestration / Automation

- `SmsReceiver` triggers automatically on every incoming SMS (manifest-registered broadcast)
- `NotificationKiller` runs as a bound service (notification listener)
- `BootReceiver` fires on device boot to verify permissions
- `WorkManager` periodic task (7-day interval) for weekly summary notification
- Shizuku persistence via Automate flow (Shizuku-Keeper) + `tools/restart-shizuku.bat` for post-reboot recovery

---

## Credentials & Secrets

| Item | Location | Notes |
|---|---|---|
| Release keystore | `keystore/` (gitignored) | Upload keystore for Play Store signing |
| `keystore.properties` | Root (gitignored) | storeFile, storePassword, keyAlias, keyPassword |
| OpenAI API key | Doppler (for ML training only) | Used by `ml-spam-training/config.py`, not in the app |

---

## ML Training Pipeline (`ml-spam-training/`)

LLM-distillation approach: OpenAI gpt-4o-mini labels SMS messages, human reviews/corrects via interactive HTML tool, then TFLite model is trained from corrected labels for on-device inference.

| Step | Script | Purpose |
|---|---|---|
| 1 | `step1_extract.py` | Extract SMS from XML backup (SMS Backup & Restore format) |
| 2 | `step2_label.py` | Batch-label messages via OpenAI API |
| 3 | `step3_analyze.py` | Analyze label distribution |
| 4 | `step4_review.py` | Generate interactive HTML review page with bulk-action controls |
| 5 | `step5_apply_corrections.py` | Merge `corrections.csv` from review into labeled data |
| 6 | `step6_train.py` | Train TFLite model, export to `app/src/main/assets/` |

Training data is gitignored (contains personal SMS content).

---

## Build & Distribution

| Concern | Details |
|---|---|
| Debug build | `.\gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| Release build | `.\gradlew bundleRelease` → signed AAB with R8 minification |
| Deploy to device | `adb install -r app-debug.apk` (ADB path: `D:\AndroidSdk\platform-tools\adb.exe`) |
| Play Store | Google Play Console, closed testing track |
| Privacy policy | `https://dmc-inc.com/privacy` (static HTML on Oracle Cloud VM) |

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
| Billing | Google Play Billing Library 7.1.1 |
| Privileged ops | Shizuku 13.1.5 |
| Testing | JUnit 5 |
| Build | Gradle (Kotlin DSL) with version catalog |
| Distribution | Google Play Store ($0.99/month, 30-day trial) |

---

## Shizuku Notes (Non-Rooted Samsung)

- Shizuku's privileged server runs as `shell` (uid 2000) via `app_process`, PPID 1 (daemonized), OOM score −1000 (kernel-protected from lmkd).
- The server is killed when the **ADB daemon restarts** (Wi-Fi sleep, wireless debugging cycle, network change) — not by Samsung's battery optimizer.
- The `starter` binary (at `/storage/emulated/0/Android/data/moe.shizuku.privileged.api/starter`) must match the installed Shizuku APK version. A stale starter causes an instant `UnsatisfiedLinkError` crash on `librish.so`.
- v13.6.0 starter = `libshizuku.so` (16680 bytes) extracted from the APK's `lib/arm64/` directory.
- `adb tcpip 5555` enables wireless ADB for the Automate auto-revive flow but does not survive reboot.
- Post-reboot recovery: plug in USB → double-click `tools/restart-shizuku.bat`.

---

## Known Issues / Bugs Fixed

- **Keyword matching was not truly case-insensitive** (2026-06-16): `text.lowercase()` used device locale instead of `Locale.ROOT`. Fixed in `KeywordRules.kt`.
- **Trailing `\b` in keyword regex blocked plural/suffix matches** (2026-06-16): "lawmaker" would not match "Lawmakers". Fixed by removing trailing `\b`; leading `\b` still prevents mid-word false positives.
- **Shizuku starter crash** (2026-05-31): Stale 2025-04-17 starter binary caused `UnsatisfiedLinkError` on `librish.so` with v13.6.0 APK. Fixed by deploying fresh starter from `lib/arm64/libshizuku.so`.
- **Nuclear Shortcode Mode removed** (2026-05): Killed any message from 5-6 digit shortcode containing `$`. Caused excessive false positives with bank ACH notifications.

---

## Recent Changes

- **2026-06-17** — Updated project-context.md to current state
- **2026-06-16** — Fixed keyword matching: `Locale.ROOT` normalization, removed trailing `\b` for plural/suffix support, added `IGNORE_CASE` fallback and raw-body second-pass matching
- **2026-05-31** — Fixed Shizuku starter crash (stale binary), added `tools/restart-shizuku.bat` and `tools/shizuku-start.sh`, diagnosed server death cause (ADB daemon restart, not Samsung lmkd)
- **2026-05-29** — Google Play Store preparation: BillingManager, SubscriptionState, PaywallScreen, release signing with R8, privacy policy at dmc-inc.com, store listing assets, Play Console setup
- **2026-05-27** — ML training pipeline (`ml-spam-training/`): 6-step LLM distillation with interactive review tool, trained TFLite model deployed to app assets
- **2026-05-25** — Removed Nuclear Shortcode Mode (false positive risk), removed KeywordSuggester, added InboxCleanupCard, KillLogScreen, AnalyticsScreen
- **2026-05-21** — Full v1.0 + v1.1 implementation: classification pipeline, SMS interception, notification killing, Room database, Compose UI, unit tests
