# Knightfall - Play Console Data Safety answers

Source of truth for the Play Console "Data safety" form for
com.chartmann.knightfall. Derived from the code on 2026-11-23
(app/build.gradle.kts dependencies, AndroidManifest.xml,
SettingsRepository.kt, AdManager.kt). If the SDK set or the privacy
toggles change, update this file and the Console form together.

## Form-level answers

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
  (Firebase, AdMob, Hugging Face and the feedback relay are all HTTPS.)
- Account creation methods: **Username and password** (email),
  **OAuth** (Google Sign-In), **Other** (anonymous guest account).
- Account deletion URL:
  https://chartmann1590.github.io/knightfall/delete-account.html
  (In-app path: Settings -> "Delete account & data". The web page offers
  sign-in-and-delete plus a manual deletion-request form.)
- Can users request data deletion without deleting their account?
  Deletion is all-or-nothing: the in-app button and the web page delete
  the account and its associated data together.

## Behaviour the answers rest on (verified in code)

- The manifest disables Firebase Analytics, Crashlytics, Performance
  Monitoring and Messaging auto-init at cold start
  (firebase_*_collection_enabled = false).
- Nothing is collected until the user finishes onboarding.
  SettingsRepository.applyPrivacyChoices() then enables Analytics,
  Crashlytics, Performance and Messaging per their toggles, which
  DEFAULT TO ON. Each can be turned off individually in
  Settings -> DATA SHARING. This is an opt-out model after onboarding,
  not per-feature opt-in; the listing copy says exactly that.
- AdMob is NOT gated by those toggles: AdManager.initialize() runs in
  KnightfallApp.onCreate and the banner/interstitial units come from CI
  secrets, so the Play build serves real ads (see listing: Ads = Yes).
- Public profiles are opt-in (default off). Only an opted-in user's
  player name, Elo and record appear on the leaderboard/website.
- The AI coach (Gemma 4) runs fully on-device; prompts and commentary
  never leave the phone.

## Data types

### Personal info

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Email address | Yes | No | Optional (only for email/Google accounts; guests have none) | App functionality, Account management |
| Name (player username) | Yes | No | Required for an account profile; guests get one too | App functionality, Account management |
| User IDs (Firebase UID) | Yes | No | Required | App functionality, Account management, Fraud prevention/security |

Note: an opted-in public profile shows the player name, Elo and
win/loss record to other users and on the website. That is the app's
core functionality at the user's own direction, disclosed in the
privacy policy; it is not third-party "sharing" in the Data Safety
sense.

### App activity

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| App interactions (Firebase Analytics usage/session data) | Yes | No | Optional (Settings -> DATA SHARING -> Analytics; on by default after onboarding) | Analytics |
| Other user-generated content (online game moves/results, puzzle progress) | Yes | No | Required for online play | App functionality |

No custom Analytics events are logged in code (no logEvent calls);
Analytics collection is the SDK's automatic usage data.

### App info and performance

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Crash logs (Crashlytics) | Yes | No | Optional (Settings toggle; on by default after onboarding) | Analytics |
| Diagnostics (Performance Monitoring) | Yes | No | Optional (Settings toggle; on by default after onboarding) | Analytics |

### Device or other IDs

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Device or other IDs (advertising ID via AdMob; Firebase installation ID) | Yes | Yes (advertising ID to Google AdMob) | Required | Advertising or marketing, Analytics, App functionality |

### Photos and videos

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Photos (user-selected screenshot attached to a bug report) | Yes | No | Optional (only if the user attaches one) | App functionality |

Bug reports are submitted only at the user's direction through
Settings -> "Report a Problem". The form warns, before submission,
that the report (text, optional name/email, diagnostics and any
attached screenshot) is posted to the app's public GitHub issue
tracker and may be publicly visible. This is user-initiated public
posting, disclosed in the privacy policy (Bug Reports bullet).

### Not collected

- Location (no location permissions in the manifest)
- Financial info (no purchases, no billing SDK)
- Health and fitness, Contacts, Calendar, Messages, Web browsing
  history, Audio files, Files and docs (the bug-report picker is
  user-selected images only)

## SDK inventory behind these answers

Firebase Auth, Firestore, Analytics, Crashlytics, Performance
Monitoring, Messaging (push notifications; auto-init gated by the
Messaging toggle), Remote Config (dependency present, not used in
code), Google Sign-In (Credential Manager / Google ID), AdMob
(play-services-ads: banner in MainActivity + interstitial before
games), LiteRT-LM (on-device only), WorkManager (coach download).

Privacy policy: https://chartmann1590.github.io/knightfall/privacy.html