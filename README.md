# FileMate

A personal, local-first Android AI file organiser and gallery for KpDaze. The approved blue design remains at the [phone preview](https://filemate-design-preview.kellypel1980.chatgpt.site).

## Current status

The corrected 0.1.1 Android proof passed its bounded Android 15 fixture checks and the real production 30-minute inactivity interval. [Public run 36285379397](https://github.com/KpDaze/FileMate-Public/actions/runs/36285379397) used a standard `ubuntu-latest` runner with KVM and API 35. It verified the build and JVM tests, an active monitoring session at the at-least-29-minute check, automatic stop, the Activity entry and removal of the monitoring notification. No shortened timeout or device-clock change was used.

Kel installed the corrected APK on a physical phone on 27 September 2026. Installation and the screens tried appeared to work. The later broad-access setup was deliberately left incomplete, so real-provider downloads, ordinary app switching and the 30-minute stop are not yet accepted on that phone.

The Stage 2 organiser milestone shipped as `0.2.0-stage2d`. It adds manual projects, Needs Sorting, single and batch assignment, a deliberate phone cleanup scan, previewed move/rename actions, conflict-safe filenames, an action journal and Undo. [Build run 36291518444](https://github.com/KpDaze/FileMate-Public/actions/runs/36291518444) passed the JVM tests, Android lint and debug assembly.

[Android proof run 36291518440](https://github.com/KpDaze/FileMate-Public/actions/runs/36291518440) passed on Android 15/API 35. Using disposable shared-storage fixtures, it verified exact duplicate detection, project assignment, a conflict-safe destination name, a real move, preservation of the pre-existing destination file and Undo back to the original path. Physical-phone acceptance of Stage 2 remains pending.

The personal phone update is `FileMate-0.2.0-Stage2.apk`, version code 3, SHA-256 `79ca2ce8b2d80318191516279d55642dc54355c540e36db571345460ae3d4cee`. It is signed with the same backed-up personal certificate as the installed 0.1.1 copy, so Android can apply it as an update. [Final main build 36292283138](https://github.com/KpDaze/FileMate-Public/actions/runs/36292283138) passed after packaging was returned to manual-only mode.

A later Stage 2 safeguard repair through `1c59694e88c1beb21a001358acbee8af63fdafe0` enforces exclusive destination creation and fingerprints every previewed move so Undo can refuse same-size edits. Interrupted copies remain for review; older hashless actions are not eligible for automatic Undo. See the [repair verification](docs/VERIFICATION.md). This source repair is not in the installed signed Stage 2 APK; no replacement signed phone APK has been delivered.

Stage 3A is now implemented in source as `0.3.0-stage3a`, version code 4. Phone → Gallery reads local image/video metadata through MediaStore, displays chronological thumbnails and Unassigned/All Gallery/Camera/Screenshots/Downloads/Projects filters, and supports confirmed individual and batch project assignment without changing media. Schema 5 adds a forward-only media migration. The [Gallery evidence](docs/VERIFICATION.md#stage-3a-read-only-gallery--28-september-2026-brisbane) distinguishes Android fixture checks from unperformed real-phone tests. No Stage 3 phone APK has been packaged or delivered.

## Native behaviour

The existing Android proof provides:

- A Hub populated from launchable apps installed on the phone.
- A foreground file-event watcher acknowledged before opening the selected AI.
- Downloads and Documents observation, including subfolders.
- Local SQLite records, source confidence and Activity history.
- Optional Usage Access for ordinary switching among selected AI apps.
- Automatic stop after about 30 minutes without selected AI activity.
- Catch-up on reopen, plus manual Stop.
- Projects that are always created by the user, plus Needs Sorting and batch assignment.
- A deliberate cleanup scan across standard shared folders and optional user-selected folders.
- Previewed project moves and optional tidy names, with exclusions, conflict-safe names and Undo.
- Read-only local Gallery under Phone, with honest screenshot clues, useful details and metadata-only project assignment.
- Gallery photo/video permissions independent of broad All files access; denied or limited access retains saved metadata.

The first cleanup scan changes no files. A move or rename occurs only after the user reviews the preview and applies it. FileMate has no file/media deletion action. Gallery has no move, rename, trash, OCR or similarity action. No network permission, cloud backend, paid service or analytics SDK is included.

## Build

Requires JDK 17, Android SDK platform 36 and build-tools 35.0.0. The Gradle wrapper pins Gradle 8.13; dependency versions are pinned in the build files.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. FileMate needs Android 11 or newer. A personal signing key can be supplied using `FILEMATE_TEST_KEY` and `FILEMATE_KEY_PASSWORD`; never commit the key or password. A differently signed APK cannot update the installed personal copy.

The lightweight Android build workflow runs for ordinary app changes. A bounded Stage 2 Android proof covers cleanup, move and Undo. A separate bounded Gallery proof covers disposable image/video indexing, permission changes, migration, unchanged bytes and native screen navigation. The separate 30-minute emulator workflow runs only when its own workflow or verification script changes, or when manually dispatched.

## Evidence and roadmap

See [phone test](docs/PHONE-TEST.md), [behaviour](docs/BEHAVIOUR.md), [verification](docs/VERIFICATION.md), [runtime evidence](docs/RUNTIME-VERIFICATION.md) and [roadmap](docs/ROADMAP.md).

This public repository is the active development source. The earlier private repository is a preserved historical checkpoint. Signing and recovery material stays private and outside public Git. Preserve history; do not force-push or add paid dependencies without explicit approval.

Gallery sorting refinement: opens on Unassigned. Successful confirmed assignment closes review and removes the item from that view; All Gallery and its project retain it. Clearing assignment restores it to Unassigned. This is metadata-only, with no schema change or phone APK. Gallery multi-select and explicit confirmation are now implemented; Stage 3B screenshot grouping and Needs Sorting intake remain future work.
