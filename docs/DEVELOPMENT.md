# Knightfall — Developer Notes

Customer-facing info lives in the [README](../README.md) and on the
[website](https://chartmann1590.github.io/knightfall/). This file is the
technical companion.

## Stack

- Kotlin + Jetpack Compose (Material 3), single `:app` module
- AGP 9.2.1 / Gradle 9.5.1 / built-in Kotlin 2.4 / minSdk 31, target 36
- Firebase **Spark plan only**: Auth (anonymous + email), Firestore,
  Analytics, Crashlytics, Performance, FCM, Remote Config
- Chess rules: [chesslib](https://github.com/bhlangonijr/chesslib)
- AI opponent: Stockfish 18 (official android-armv8 build) shipped as
  `libstockfish.so`, exec'd from `nativeLibraryDir`, spoken to over UCI
- AI coach: Gemma 4 E2B (`.litertlm`) via `litertlm-android`, downloaded
  in-app from Hugging Face (Apache 2.0, ungated)

## Building

```bash
./gradlew assembleDebug
```

The Stockfish binary is **not** in git (114 MB > GitHub's 100 MB limit). The
`downloadStockfish` Gradle task fetches it automatically on first build.

Release builds are signed with the keystore configured via the
`KNIGHTFALL_KEYSTORE_*` environment variables or `keystore/keystore.properties`
(git-ignored).

## CI

`.github/workflows/release.yml` runs on every push to `main`:
versionCode = `100 + run_number`, versionName = `1.0.<run_number>`, builds a
signed APK + AAB, and publishes a GitHub Release. Secrets: `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Firestore data model

| Collection | Doc | Notes |
|---|---|---|
| `users/{uid}` | private profile | owner-only read/write; elo clamped 0–4000 by rules |
| `publicProfiles/{uid}` | public mirror | world-readable; exists only while the user opts in |
| `games/{gameId}` | online game | readable by signed-in users; writable by participants; open seat claimable while `waiting` |
| `inviteCodes/{code}` | code → gameId | deleted when consumed |

### Spark-plan design constraints (no Cloud Functions)

- **Matchmaking** is serverless: quick match claims the oldest open `waiting`
  game in a transaction, else creates one. Races are resolved by Firestore
  transactions + a security rule that only allows claiming a `waiting` game.
- **Elo** is computed client-side (`EloCalculator`, K=32) from the rating
  snapshots stored on the game doc at creation, so both clients derive the
  same deltas. Each client applies *its own* user-doc update, guarded by
  per-color `eloApplied` flags set in a transaction. A player who never
  reopens the app just has a stale rating — acceptable for a friendly app.
- **No server authority**: a determined cheater could inflate their own
  rating via the REST API. Rules clamp values to plausible ranges, but real
  anti-cheat needs Cloud Functions (Blaze). Documented trade-off.
- **No device-to-device push**: FCM can't be sent client-to-client without a
  server. In-game realtime updates use Firestore snapshot listeners; FCM is
  wired for console campaigns/topics.

## Website

`/docs` is served by GitHub Pages. `leaderboard.html` / `profile.html` read
`publicProfiles` through the Firestore REST API with the web API key —
security rules (not the site) enforce that only opted-in profiles exist
there.

## Privacy plumbing

All Firebase collection toggles default OFF in the manifest and are enabled
only after onboarding, gated by per-feature switches in Settings
(`SettingsRepository.applyPrivacyChoices`).
