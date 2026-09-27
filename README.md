# FileMate

A personal, local-first Android AI file organiser and gallery for KpDaze. The approved blue design is at [the phone preview](https://filemate-design-preview.kellypel1980.chatgpt.site).

## Current milestone: Test 01

**Delivery status:** the original 0.1.0 APK is on hold after a Hub launch bug was found in Android runtime testing. The fix is built as 0.1.1 and has passed the corrected launch, live monitoring, app switching, manual stop and catch-up checks on an Android 15 emulator. The full inactivity test was interrupted when the emulator control session became unavailable after 12 minutes; it is not verified. Kel confirmed no APK has been installed; a fresh signing identity is now backed up and the corrected candidate signed. Delivery is waiting on the full inactivity runtime check. See [runtime verification](docs/RUNTIME-VERIFICATION.md).

This native Kotlin / Jetpack Compose build is the first technical proof, not the full Version 1. It provides:

- A folder-style Hub populated from actual installed, launchable apps. Select any app; removing one does not uninstall it.
- A foreground file-event watcher started and acknowledged before opening the selected AI.
- Downloads and Documents observation, including subfolders and newly created directories.
- Local SQLite records, source confidence and the reasons behind it.
- Optional Usage Access to follow selected apps across ordinary Android switching.
- Automatic stop after 30 minutes without selected AI activity; without Usage Access, 30 minutes from the latest Hub launch.
- An initial baseline and catch-up reconciliation on subsequent foreground opens.
- Activity history and an explicit manual Stop action.

No shared files are moved, renamed, deleted or uploaded in this milestone. No network permission, cloud backend, paid service or analytics SDK is included. The GitHub Actions workflow uses a standard public hosted runner for native verification.

## Build

Requires JDK 17, Android SDK platform 36 and build-tools 35.0.0. The Gradle wrapper pins Gradle 8.13; dependency versions are pinned in the build files.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The app needs Android 11 or newer. A personal test signing key can be supplied using `FILEMATE_TEST_KEY` and `FILEMATE_KEY_PASSWORD`; never commit that key or its password. A different signing key cannot update an existing installation.

## Evidence and next steps

See [test instructions](docs/PHONE-TEST.md), [behaviour](docs/BEHAVIOUR.md), [build verification](docs/VERIFICATION.md) and [the full roadmap](docs/ROADMAP.md).

This is a screened public snapshot of the native app for free Android verification. The private repository and its history remain separate. The hosted design preview remains available. Do not alter unrelated repositories, force-push, erase history, or add paid/metered dependencies without explicit user approval.

The delivered test APK is compacted using `tools/package-proof.py`, which removes unused archive gaps, preserves every ZIP entry byte for byte, aligns it, and signs it with the same private test identity. The final artifact hash and actual verification results are recorded in `docs/test-01-artifact.json` when the package is ready.
