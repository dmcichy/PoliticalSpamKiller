# PoliticalTextKiller — Design Document

> A personal-use Android app that silently kills political/donation spam texts before you ever see or hear them, while leaving Google Messages installed and untouched as your default SMS app.

---

## 1. Problem statement

Political and donation-solicitation text messages are the single most aggressive class of unwanted SMS traffic in the U.S. They are:

- **Largely unregulated** — political speech is exempt from the National Do Not Call Registry and from most CAN-SPAM / TCPA protections that apply to commercial messages.
- **List-traded constantly** — replying `STOP` only removes you from a single campaign's list. Your number is resold among PACs, parties, candidates, vendors, and grifters.
- **Adversarial** — many senders rotate phone numbers, shortcodes, and even keyword phrasing to evade carrier-level filters.
- **Carrier-filter-resistant** — Google Messages' built-in "Spam protection," Samsung's filter, and even T-Mobile Scam Shield miss the bulk of political content because political messages are *not* phishing-shaped and don't match commercial-spam signatures.

The user is on a **Samsung Galaxy S25+ (Android 15+)** running **Google Messages** as the default SMS app. They have already enabled every built-in filter, report messages to 7726, and block individual numbers. None of it is sufficient. This document specifies an app that solves the problem at the device level.

---

## 2. Goals & non-goals

### Goals

1. **Zero notification surface** for political/donation spam — no sound, no vibration, no banner, no badge, no entry in the Google Messages inbox.
2. **Keep Google Messages as the default SMS app** — full RCS support, normal sending, no UX disruption for legitimate messages.
3. **Contact-safe** — any text from a number saved in the user's contacts is *always* allowed through, no matter what it says.
4. **User-tunable** — the keyword blocklist and allowlist are first-class settings the user can edit at any time.
5. **Auditable** — every killed message is stored locally in a "Spam Vault" the user can review, in case of false positives.
6. **Personal use, sideload-only** — no Play Store distribution constraints. Permissions can be as aggressive as Android allows for a self-signed APK.
7. **Offline by default** — no network calls, no analytics, no telemetry, no cloud. All classification is on-device.

### Non-goals (v1)

- ❌ Filtering RCS messages (those bypass the SMS path entirely; political spam is overwhelmingly legacy SMS, so this is acceptable).
- ❌ Filtering MMS attachments (rare for political spam).
- ❌ Blocking phone calls (separate problem, use Scam Shield / Call Filter).
- ❌ Multi-device sync, cloud backup of vault, or sharing blocklists across users.
- ❌ Pretty UI. The app exists to disappear, not to be opened daily.
- ❌ Play Store publication (Google has heavy restrictions on `SMS` and `Notification Access` permissions for published apps).

---

## 3. Android architectural constraints (the why behind every choice)

### 3.1 Only the default SMS app can truly intercept SMS

Since Android 4.4 (KitKat, 2013), the `SMS_RECEIVED` broadcast is **no longer abortable** by third-party apps. Only the app currently registered as the user's default SMS handler (via `RoleManager.ROLE_SMS`) can prevent a message from landing in the system SMS provider.

**Implication:** A true "pre-intercept" architecture would require us to become the default SMS app, which (a) breaks RCS, (b) makes outgoing-message UX awkward, and (c) replaces the role Google Messages currently plays. **The user explicitly does not want this.**

### 3.2 Non-default apps can still see SMS and act on them — just not first

A non-default app with `RECEIVE_SMS` permission still receives `SMS_RECEIVED` and `SMS_DELIVER` broadcasts. It just cannot prevent the default app (Google Messages) from also receiving them. This means we can:

- **Read** every incoming SMS (sender + body).
- **Classify** it locally.
- **Delete it** from the system SMS provider (`content://sms/inbox`) via `WRITE_SMS`, which removes it from Google Messages' inbox view.
- **Dismiss the Google Messages notification** via `NotificationListenerService`, which kills sound/vibration/banner.

The race condition is real but small: Google Messages typically posts a notification within ~50–300 ms of receiving the broadcast. Our app receives the same broadcast at the same time and races to dismiss the notification and delete the message. In practice the notification may briefly appear on the lock screen for a few hundred milliseconds in the worst case, and is gone before the user can react. **For the user's stated goal — "stop blowing up my phone" — this is more than sufficient.**

### 3.3 The "Silent Killer" architecture (Option A from our design discussion)

This is the architecture we choose. Diagrammatically:

```
[Carrier SMS] → [Android Telephony layer]
                       │
                       ├──→ [Google Messages]  (default SMS app)
                       │         │
                       │         ├──→ writes to content://sms/inbox
                       │         └──→ posts notification
                       │
                       └──→ [PoliticalTextKiller SmsReceiver]  (parallel listener)
                                 │
                                 ├──→ Classifier.classify(sender, body)
                                 │       │
                                 │       ├──→ ALLOW  → do nothing, let GMS show it
                                 │       └──→ KILL   →  ↓
                                 │
                                 ├──→ delete row from content://sms/inbox
                                 ├──→ NotificationKiller.cancelGmsNotification()
                                 └──→ Vault.insert(killed_message)
```

---

## 4. Detailed design

### 4.1 Components

| Component | Type | Responsibility |
|---|---|---|
| `SmsReceiver` | `BroadcastReceiver` | Receives `SMS_RECEIVED` broadcasts in parallel to Google Messages. Pulls sender + body, hands to `Classifier`. |
| `Classifier` | Pure Kotlin class | Decides ALLOW vs KILL based on: contact check → user allowlist → user blocklist → keyword rules → (optional) on-device ML model. |
| `ContactGuard` | Repository | Wraps `ContactsContract` lookup to determine whether a sender is in the user's contacts. **Single source of truth for the "contacts are sacred" rule.** |
| `RuleStore` | Room database | Persists user-editable keyword lists, allowlist, blocklist. |
| `Vault` | Room database | Stores killed messages with full sender + body + timestamp + matched-rule for user review. |
| `NotificationKiller` | `NotificationListenerService` | Watches for incoming Google Messages notifications and cancels any whose key matches a recently-killed message. |
| `SmsScrubber` | Helper | Deletes killed rows from `content://sms/inbox` (and `content://mms-sms/conversations` if needed) so they disappear from Google Messages' inbox view. |
| `MainActivity` | Activity (Compose) | Single-screen UI for: permission setup, rule editing, vault review, enable/disable kill switch, export logs. |
| `BootReceiver` | `BroadcastReceiver` | Re-arms permissions check after device reboot. (`SmsReceiver` is registered in the manifest and survives reboots automatically; this only handles the foreground-state restore.) |

### 4.2 Classification pipeline

`Classifier.classify(sender: String, body: String): Verdict`

Where `Verdict = ALLOW | KILL(rule: MatchedRule)`.

The pipeline is **short-circuit ordered**. The first matching rule wins; subsequent rules are not evaluated. Order is deliberate:

1. **Contact check** — if `ContactGuard.isKnown(sender)` returns true → `ALLOW`. Always. No exceptions. (Prevents accidentally killing a politically-active friend or family member.)
2. **User allowlist (numbers)** — if sender is on the explicit allowlist → `ALLOW`. (User can pin specific shortcodes or numbers.)
3. **User blocklist (numbers)** — if sender is on the explicit blocklist → `KILL(reason=BLOCKED_NUMBER)`.
4. **Keyword rules** — body is lowercased, stripped of punctuation, and checked against the user's keyword list. Any match → `KILL(reason=KEYWORD, matched=...)`.
5. **Heuristic rules** (built-in, toggle-able):
   - Sender is a 5–6 digit shortcode AND body contains any donation-indicator (`$`, `match`, `donate`, `give`, `chip in`, `gift`, `contribution`, `pitch in`) → `KILL(reason=DONATION_HEURISTIC)`.
   - Body contains both a candidate name (from a small built-in list) AND a fundraising verb → `KILL(reason=CAMPAIGN_HEURISTIC)`.
   - Body contains a known fundraising platform domain (`actblue.com`, `winred.com`, `secure.anedot.com`, `donorbox.org` followed by campaign slugs) → `KILL(reason=DONATION_PLATFORM)`.
6. **(Optional, v2) On-device ML classifier** — TensorFlow Lite model (~5 MB) trained on a labeled corpus of political vs. legitimate texts. Probability ≥ 0.85 → `KILL(reason=ML, score=...)`. Disabled by default in v1.
7. **Default** → `ALLOW`.

### 4.3 Default keyword list (shipped, user-editable)

These ship as the initial blocklist. The user can add/remove via the UI at any time. **None of these will fire if the sender is in the user's contacts** — so a literal text from Grandma saying "did you vote" still gets through.

```
vote, voter, voted, voting, ballot, polls, poll,
donate, donation, donor, chip in, chipping in, pitch in,
match my donation, match 5x, matched 500%, triple match, quadruple match,
final notice, urgent, deadline, midnight deadline, fec deadline,
gop, dnc, rnc, maga, democrat, republican, liberal, conservative,
trump, biden, harris, vance, walz, desantis, newsom,
actblue, winred, anedot, donorbox,
filibuster, impeach, election, campaign, candidate,
your contribution, you in?, are you with us, stand with,
2x match, 5x match, 10x match,
```

The candidate name list is **user-editable** because it goes stale every election cycle. v1 ships with the names that were active in the 2024/2026 election cycles; v2 could pull a refreshed list from a static URL on demand (opt-in).

### 4.4 Notification cancellation

Google Messages posts notifications with a known package name (`com.google.android.apps.messaging`). `NotificationKiller.onNotificationPosted(sbn)` does:

1. Filter to only notifications from `com.google.android.apps.messaging`.
2. Extract the `Notification.EXTRA_TITLE` (typically the sender) and `Notification.EXTRA_TEXT` (typically the message body, possibly truncated).
3. Cross-reference against a small in-memory ring buffer of "recently killed" messages (keyed by sender + first 40 chars of body, written by `SmsReceiver` immediately before this notification could fire).
4. If a match is found → `cancelNotification(sbn.key)`.

The ring buffer is necessary because the SMS broadcast and the notification post are racing. We can't rely on either happening first. We hold killed-message metadata in memory for ~5 seconds, which covers the race window with massive headroom.

### 4.5 SMS scrubbing

Immediately after classification yields `KILL`, `SmsScrubber.scrub(sender, body, timestamp)` does:

1. Query `content://sms/inbox` for rows where `address = sender` AND `date BETWEEN (timestamp - 2s) AND (timestamp + 5s)` AND `body LIKE first-40-chars-of-body`.
2. Delete matching rows.
3. Also probe `content://mms-sms/` if MMS-class political spam shows up (rare; deferred to v2 if observed in real-world testing).

If for any reason the row hasn't been written by Google Messages yet, retry once after 500 ms. After two attempts, give up and rely on the notification kill alone (the message will live in the inbox but the user won't be alerted).

### 4.6 Vault

Every killed message gets a Room row:

```kotlin
@Entity
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val reason: String,     // BLOCKED_NUMBER | KEYWORD | DONATION_HEURISTIC | ...
    val matchedRule: String,// the actual keyword or rule fingerprint that fired
    val scrubbed: Boolean,  // whether we successfully deleted from sms/inbox
)
```

The vault is browsable in the UI with: timestamp, sender, body, reason, "Restore" (writes back to SMS inbox), "Always allow this sender" (adds to allowlist), "Add 'X' to allowlist keywords" (un-trains a false positive).

**Vault auto-prunes after 90 days** to keep storage bounded. User can change retention in settings.

### 4.7 Kill switch

Single top-level toggle in the UI: **"PoliticalTextKiller is ON / OFF"**. When OFF, `SmsReceiver` and `NotificationKiller` short-circuit immediately on entry and do nothing. Useful for debugging or temporarily disabling during, e.g., a legitimate political conversation with a friend who is somehow not in contacts.

---

## 5. Permissions & manifest

```xml
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.WRITE_SMS" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- for our own status notification -->
<!-- Notification Listener Service permission is granted via Settings, not declared as <uses-permission> -->

<application ...>

  <receiver
      android:name=".sms.SmsReceiver"
      android:exported="true"
      android:permission="android.permission.BROADCAST_SMS">
    <intent-filter android:priority="999">
      <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
  </receiver>

  <service
      android:name=".notif.NotificationKiller"
      android:label="@string/notif_killer_label"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
      android:exported="true">
    <intent-filter>
      <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
  </service>

  <receiver
      android:name=".boot.BootReceiver"
      android:exported="true">
    <intent-filter>
      <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
  </receiver>

</application>
```

### Setup permissions (first-run flow)

1. Request `RECEIVE_SMS`, `READ_SMS`, `READ_CONTACTS`, `POST_NOTIFICATIONS` via the standard runtime permission dialog.
2. Open `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` so the user can grant Notification Access to the app. (Required for cancelling Google Messages notifications.)
3. `WRITE_SMS` is granted automatically via the SMS permission group on modern Android.
4. Display a final "You're protected" screen with a test-button that simulates an incoming political text against the classifier so the user can confirm the pipeline works.

---

## 6. Project layout

```
PolicitcalSpamKiller/
├── DESIGN.md                  ← this file
├── README.md                  ← short usage + sideload instructions
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/personal/ptk/
│   │   │   ├── App.kt
│   │   │   ├── sms/
│   │   │   │   ├── SmsReceiver.kt
│   │   │   │   ├── SmsScrubber.kt
│   │   │   │   └── ContactGuard.kt
│   │   │   ├── classify/
│   │   │   │   ├── Classifier.kt
│   │   │   │   ├── Verdict.kt
│   │   │   │   ├── KeywordRules.kt
│   │   │   │   ├── HeuristicRules.kt
│   │   │   │   └── DefaultKeywords.kt
│   │   │   ├── notif/
│   │   │   │   ├── NotificationKiller.kt
│   │   │   │   └── KillRingBuffer.kt
│   │   │   ├── data/
│   │   │   │   ├── Database.kt
│   │   │   │   ├── RuleDao.kt
│   │   │   │   ├── VaultDao.kt
│   │   │   │   └── entities/
│   │   │   ├── boot/
│   │   │   │   └── BootReceiver.kt
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt
│   │   │       ├── SetupScreen.kt
│   │   │       ├── VaultScreen.kt
│   │   │       ├── RulesScreen.kt
│   │   │       └── theme/
│   │   └── res/
│   └── src/test/                ← JVM unit tests for Classifier
├── gradle/
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 7. Tech stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | **Kotlin** | Standard for modern Android, concise, null-safe. |
| Min SDK | **API 31 (Android 12)** | User is on Android 15+. No need to support older. |
| Target SDK | **API 35 (Android 15)** | Required for modern permission model. |
| UI | **Jetpack Compose + Material 3** | Single-screen app, no fragments, minimal code. |
| Persistence | **Room** | Built-in, type-safe, sufficient for two tiny tables. |
| Async | **Kotlin Coroutines** | Receiver work on `Dispatchers.IO` to avoid ANR. |
| DI | **None** (manual) | Project is too small to justify Hilt. |
| Testing | **JUnit 5** for `Classifier`; **Espresso** skipped (no UI complexity worth testing). |
| Build | **Gradle (Kotlin DSL)** | Standard. |
| Distribution | **Self-signed APK, sideload via ADB** | No Play Store. |

---

## 8. Testing strategy

### Unit tests (must pass before any install)

`ClassifierTest`:

- `text from contact with "donate now" → ALLOW`
- `text from shortcode 88022 saying "Trump needs $5 by midnight" → KILL(KEYWORD)`
- `text from random 10-digit saying "your package is delayed" → ALLOW`
- `text from random 10-digit saying "stand with us, chip in $25" → KILL(DONATION_HEURISTIC)`
- `text from random 10-digit containing "actblue.com/foo" → KILL(DONATION_PLATFORM)`
- `text from contact containing every keyword in the list → ALLOW`
- `text with mixed case "VOTE NOW" → KILL (case-insensitive)`
- `text containing "envoté" (false positive trigger) → ALLOW` (we tokenize on word boundaries)
- Boundary cases for empty body, empty sender, non-ASCII.

### Live test plan (after install)

1. Have a friend (not in contacts) text you `"Hey, are you registered to vote in the upcoming election? — your neighbor"` → should be KILLED, vault entry visible.
2. Have same friend, now added to contacts, send the same text → should be ALLOWED.
3. Wait for a real political spam text to arrive → should be killed silently. Verify in vault.
4. Receive a legitimate 2FA code (e.g. Google verification) → should be ALLOWED, no false positive.
5. Receive an Amazon delivery text → should be ALLOWED.
6. Toggle kill switch OFF → next political spam should land normally in Google Messages. Toggle back ON.

---

## 9. Known limitations & risks

| Limitation | Severity | Mitigation |
|---|---|---|
| Brief notification flash possible before cancellation | Low | Race window ~100–300 ms; in practice notification rarely fires. Future: register at higher priority. |
| RCS messages bypass us entirely | Low | Political spam is overwhelmingly SMS, not RCS. Verified empirically in 2024–2026 spam corpora. |
| False positives (legit message containing "vote") | Medium | Contact-bypass + Vault review + "always allow sender" one-tap remediation. |
| Google Messages may re-sync deleted messages from RCS cloud backup | Medium | SMS deletion is local; RCS cloud sync is separate. Spot-check after install. If observed, disable RCS backup for spam senders. |
| Permission revocation by user breaks the app silently | Low | Boot receiver checks all permissions on startup; if any are missing, posts a persistent notification asking user to re-grant. |
| Android version changes may break the SMS provider interface | Medium | Target API stays current; integration-test on each major Android version before upgrading targetSdk. |
| User adds a keyword that matches everything (`"the"`) | Self-inflicted | Settings screen shows a "danger" indicator on suspiciously broad keywords (<3 chars, common stopwords). |

---

## 10. Roadmap

### v1.0 — "Silent Killer" (target: this weekend)

- All Section 4 components.
- Default keyword list shipped.
- Contact-bypass.
- Vault with restore.
- Kill switch.
- Single Compose screen for everything.

### v1.1 — Quality of life

- "Suggest keyword" button — shows the N most common words across the last 30 days of vault entries that aren't yet on the blocklist, so the user can one-tap promote them.
- Daily/weekly summary notification: "PoliticalTextKiller killed 47 spam messages this week."
- Export vault to CSV for personal records.

### v2.0 — Smarter classification

- On-device TFLite model trained on a labeled corpus.
- Sender-reputation tracking (a sender that has had ≥3 messages killed is auto-blocklisted).
- Optional shared community blocklist (opt-in, anonymous, rate-limited).

### v3.0 — Expansion

- Call blocker (uses `CallScreeningService`).
- MMS support.
- Multi-language keyword packs.

---

## 11. Build & install (sideload to Samsung Galaxy S25+)

```bash
# from project root, after Android Studio sync:
./gradlew :app:assembleRelease

# sign with debug key (personal use is fine with debug key, but a stable release key
# avoids "must uninstall first" on upgrade):
./gradlew :app:bundleRelease

# install over ADB:
adb install -r app/build/outputs/apk/release/app-release.apk

# grant Notification Access (one-time, manual):
adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
# → toggle PoliticalTextKiller ON in the list

# verify SMS receiver is registered:
adb shell dumpsys package com.personal.ptk | grep -A 2 SMS_RECEIVED
```

For day-to-day use after first install, no ADB is needed — the app sits in the launcher and runs automatically.

---

## 12. Open questions for the user

1. **ML model later?** Are you OK with keyword + heuristic classification in v1, deferring the TFLite model to v2? (Yes saves ~2 days of effort and ~5 MB APK size.)
2. **Sender allowlist defaults.** Do you want any sender numbers pre-allowlisted? (e.g. your bank, Amazon, your alarm system shortcodes.)
3. **Aggressiveness.** Do you want to default to **"kill anything from a non-contact shortcode containing a dollar sign"**? That's nuclear but extremely effective and catches non-keyword-matching political grifts. Recommended OFF by default, but trivial to flip ON.
4. **Vault retention.** Default 90 days fine, or shorter (less storage) / longer (more recovery window)?
5. **Build environment.** Do you have Android Studio installed and a USB cable to the S25+? If not, we'll need a sideload step via cloud + browser download.

---

*Last updated: 2026-05-21 — initial design draft.*
