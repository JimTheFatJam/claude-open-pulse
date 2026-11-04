# Open Pulse

A private, offline-first Android health journal. Record easily obtainable measurements at
home, explore them on honest interactive charts, and understand changes over time — with
no account, no cloud and no tracking.

> Open Pulse is a personal tracking and informational tool. It is **not** a medical device,
> does not diagnose conditions and does not recommend treatment.

## Features

- **Interactive charts** (the centrepiece): every dot is a recorded value on a continuous
  time axis. Pinch to zoom, drag to pan, tap a dot for details. Days without data stay
  visible (day gridlines + a coverage strip) and are never filled in. Optional thin dashed
  connector lines join *real* consecutive readings only when they are at most N days apart
  (configurable); larger gaps stay gaps. Ranges: 1D, 1W, 1M, 3M, 6M, 1Y, All.
- **14 measurement types**: blood pressure (systolic/diastolic + optional pulse), resting
  heart rate, weight, body temperature, SpO₂, height, waist, body fat, blood glucose,
  respiratory rate, heart rate, sleep, water intake (daily totals) and steps. Enable only
  what you track.
- **Fast recording**: current local date/time pre-filled and editable, multiple entries per
  day never overwrite each other, validation of plausible values, tags and notes.
- **Statistics** per period: latest, previous, average, min, max, count, difference vs the
  previous period, % change where meaningful, and conservative trend descriptions
  (increasing / decreasing / stable / no consistent direction / insufficient data). Every
  figure says how many recorded values it is based on.
- **Health profile**: date of birth (age), sex, height, initial weight, time zone, goals and
  medical notes → BMI, weight change, age-appropriate reference info.
- **History** with search, category and date filters.
- **Export / import / backup / restore** (CSV and JSON) through the system file picker.
- Local **reminders**, optional **daily checklist**, **dark/light** themes, customisable
  dashboard cards, unit conversion (kg/lb, °C/°F, cm/ft-in, cm/in, mg/dL/mmol/L, mL/L).
- **App lock** with biometrics or device credential, option to hide content in recents,
  Android cloud backup disabled.
- **Demo mode** with clearly labelled sample data kept strictly separate from real data.
- **Secure self-updater** from GitHub Releases (Wi-Fi only, SHA-256 + signing-certificate
  verification, user-confirmed installation).

## Architecture

```
core/   Pure Kotlin/JVM domain logic (no Android): measurement types & units, validation,
        statistics & trends, timeline/axis maths, reference ranges, CSV/JSON codecs,
        semantic versions & release evaluation, reminder scheduling, demo data.
        → fully unit-tested on the JVM.
app/    Android application
  data/       Room database (measurements, reminders), DataStore preferences & profile,
              repositories, backup manager
  ui/         Jetpack Compose + Material 3 screens and the custom Canvas chart
  update/     GitHub release client, downloader, APK verifier, PackageInstaller flow
  reminders/  WorkManager-based local notifications
  security/   BiometricPrompt app lock
```

Stack: Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager, Navigation
Compose, AndroidX Biometric, kotlinx.serialization. The chart is a purpose-built Compose
`Canvas` component so that gap handling, "dots are data" semantics and gestures are fully
under control (general charting libraries tend to interpolate or connect across gaps).

### Data accuracy rules

- Values are stored in canonical units (mmHg, bpm, kg, °C, %, cm, mg/dL, h, mL, steps);
  changing the display unit never changes stored data.
- Each entry has its own ID; inserts can never replace an existing row.
- Missing measurements are never treated as zero, normal or unchanged. Averages use only
  recorded values. Water and steps are summed per day, and days without entries produce
  no value at all.
- Period comparisons need ≥ 2 values in each period; trends need ≥ 4 values over ≥ 3 days
  and a consistent direction. Otherwise the app says the data is insufficient.
- Reference information (AHA/ACC, WHO, ADA, …) is shown as general context with sources,
  applies to adults only, and a BP category is only shown for an *average* of ≥ 2 readings.

## Building

Requirements: JDK 17, Android SDK (compile SDK 35).

```bash
./gradlew :core:test :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug                  # debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease                # signed if signing is configured
```

Debug builds use the application id `app.openpulse.debug`, so they can be installed next to
a release build. A debug build cannot update to a release build (different signing key).

GitHub Actions:

- `.github/workflows/ci.yml` — tests + debug APK artifact on every push / pull request.
- `.github/workflows/release.yml` — signed release APK + `.sha256` on tags `v*` or manual
  dispatch.

Signing and secrets setup: see [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).

## In-app updater

- Checks `https://api.github.com/repos/<owner>/<repo>/releases/latest` when the app opens
  (via WorkManager, unmetered network constraint) — only on validated Wi-Fi, at most hourly.
  Never on mobile data or offline. Can be disabled; manual "Check for updates" in Settings.
- Owner/repo are build configuration (`openpulse.updateRepoOwner` / `openpulse.updateRepoName`
  in `gradle.properties`).
- A release is accepted only if it is a newer stable semantic version with an `.apk` asset
  and a matching `.apk.sha256` asset, both over HTTPS.
- The APK is downloaded over HTTPS (redirects to plain HTTP are refused), its SHA-256 is
  compared with the published checksum, and then `PackageManager` checks that the package
  name matches, the version code is higher and the signing certificates are **identical**
  to the installed app's.
- Installation uses `PackageInstaller` sessions with `USER_ACTION_REQUIRED`; Android shows
  its own confirmation. Downloaded files are deleted after success, failure, cancellation
  and on the next app start.

## Privacy

All data is stored locally in the app's private storage. No analytics, ads, accounts or
cloud sync. The only network traffic is the optional update check described above.
`android:allowBackup="false"` and data-extraction rules exclude everything from Android
cloud backup and device transfer — use the in-app backup to keep your own copies.
