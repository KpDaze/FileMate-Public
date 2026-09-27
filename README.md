# FileMate

A personal, local-first Android AI file organiser and gallery for KpDaze. The approved blue design remains at the [phone preview](https://filemate-design-preview.kellypel1980.chatgpt.site).

## Current status

The corrected 0.1.1 Android proof passed its bounded Android 15 fixture checks and the real production 30-minute inactivity interval. [Public run 36285379397](https://github.com/KpDaze/FileMate-Public/actions/runs/36285379397) used a standard `ubuntu-latest` runner with KVM and API 35. It verified the build and JVM tests, an active monitoring session at the at-least-29-minute check, automatic stop, the Activity entry and removal of the monitoring notification. No shortened timeout or device-clock change was used.

Kel installed the corrected APK on a physical phone on 27 September 2026. Installation and the screens tried appeared to work. The later broad-access setup was deliberately left incomplete, so real-provider downloads, ordinary app switching and the 30-minute stop are not yet accepted on that phone.

Stage 2A source is now on `main`. It adds manual project creation, rename, delete, project browsing and a non-destructive database migration. [Build run 36289780572](https://github.com/KpDaze/FileMate-Public/actions/runs/36289780572) passed the JVM tests, Android lint and debug assembly. Stage 2A does not assign, move, rename or delete phone files. The current Stage 2 source has not been packaged as a phone update; Kel plans to use one APK after the complete Stage 2 checkpoint.

## Native behaviour

The existing Android proof provides:

- A Hub populated from launchable apps installed on the phone.
- A foreground file-event watcher acknowledged before opening the selected AI.
- Downloads and Documents observation, including subfolders.
- Local SQLite records, source confidence and Activity history.
- Optional Usage Access for ordinary switching among selected AI apps.
- Automatic stop after about 30 minutes without selected AI activity.
- Catch-up on reopen, plus manual Stop.
- A local Projects area in Stage 2A. Projects are always created by the user.

No shared file is moved, renamed, deleted or uploaded in Stage 2A. No network permission, cloud backend, paid service or analytics SDK is included.

## Build

Requires JDK 17, Android SDK platform 36 and build-tools 35.0.0. The Gradle wrapper pins Gradle 8.13; dependency versions are pinned in the build files.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. FileMate needs Android 11 or newer. A personal signing key can be supplied using `FILEMATE_TEST_KEY` and `FILEMATE_KEY_PASSWORD`; never commit the key or password. A differently signed APK cannot update the installed personal copy.

The lightweight Android build workflow runs for ordinary app changes. The separate 30-minute emulator workflow runs only when its own workflow or verification script changes, or when manually dispatched.

## Evidence and roadmap

See [phone test](docs/PHONE-TEST.md), [behaviour](docs/BEHAVIOUR.md), [verification](docs/VERIFICATION.md), [runtime evidence](docs/RUNTIME-VERIFICATION.md) and [roadmap](docs/ROADMAP.md).

This public repository is the active development source. The earlier private repository is a preserved historical checkpoint. Signing and recovery material stays private and outside public Git. Preserve history; do not force-push or add paid dependencies without explicit approval.
