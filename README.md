# FileMate

A personal, local-first Android AI file organiser and gallery for KpDaze. The approved blue design remains at the [phone preview](https://filemate-design-preview.kellypel1980.chatgpt.site).

## Current status

Native development has progressed beyond the historical Stage 3D checkpoint. The recovery build is version `0.4.0-recovery` (version code 6) on the recovery branch while `main` remains untouched.

The recovered core organiser now connects live AI download detection to conservative automatic project organisation. Automatic moves require High source confidence plus one unambiguous existing user-created project match, and reuse the existing fingerprint-verified move journal and Undo. Uncertain files remain physically untouched in Needs Sorting. Manual file and Gallery assignments also teach local filename clues for future review-only project suggestions.

Stage 3 Gallery functionality already present in native source includes screenshot intake, project assignment, exact/similar/version comparison, recoverable Android Trash/restore, Favourites and Albums.

Google Drive remains the substantial external Version 1 gap. The approved Phone-only default and per-project Phone + Drive preference are now represented locally, but neither FileMate repository contains Google OAuth client configuration. FileMate therefore does not claim a working Drive connection yet and will not upload or remove local files until a real Google client is configured and upload success can be verified.

No emulator or GitHub Actions workflow is automatically launched by the recovery work.

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

The lightweight Android build workflow runs for ordinary app changes. A bounded Stage 2 Android proof covers cleanup, move and Undo. A separate bounded Gallery proof covers disposable image/video indexing, permission changes, migration, unchanged bytes and native screen navigation. All three emulator workflows are now manual-only. Each specific run requires Kel’s explicit approval first. Ordinary app pushes run build/JVM/lint checks without an emulator.

## Evidence and roadmap

See [phone test](docs/PHONE-TEST.md), [behaviour](docs/BEHAVIOUR.md), [verification](docs/VERIFICATION.md), [runtime evidence](docs/RUNTIME-VERIFICATION.md) and [roadmap](docs/ROADMAP.md).

This public repository is the active development source. The earlier private repository is a preserved historical checkpoint. Signing and recovery material stays private and outside public Git. Preserve history; do not force-push or add paid dependencies without explicit approval.

Gallery sorting refinement: opens on Unassigned. Successful confirmed assignment closes review and removes the item from that view; All Gallery and its project retain it. Clearing assignment restores it to Unassigned. This is metadata-only, with no schema change or phone APK. Gallery multi-select and explicit confirmation are now implemented; Stage 3B now groups accessible likely screenshots by local date and folder, excluding camera-folder items and videos. Needs Sorting offers unassigned screenshot groups and reviews them through the same confirmed Gallery flow. Groups do not infer a topic, provider or project. No OCR or schema change. Build/JVM/lint and browser evidence passed. Stage 3B native fixture verification passed in separately approved run 36640809269 on Android 15/API 35: media/access probes, screenshot groups, duplicate-row suppression, cancellation and destination reset, confirmed assignment, intake removal, Refresh persistence, All Gallery retention, confirmed clear and unchanged six original paths/hashes. Three Stage 3B runs were separately approved; the first two exposed test-script issues, and the third passed. Physical-phone acceptance remains pending. Kel’s phone has not tested these screens.

Stage 3C adds Gallery → Compare images: full-content SHA-256 duplicate groups, separate approximate visual/filename-version suggestions, side-by-side/larger review and persistent Keep all/restoration. It is read-only for media and assignments. Build, lint and all 45 JVM tests passed at `b19eb17049e03a5b8a58eaf0083a20fc484fb8ed` in run 36662064034. Stage 3C Android runtime verification remains pending: the focused manual Gallery workflow input `stage3c_only=true` is prepared but has not been dispatched. Every emulator run still requires Kel's explicit approval. See verification and behaviour docs for coverage limits.

Comparison action update: select individual images, Assign selected to project with explicit confirmation, or Move selected to Trash with FileMate review and Android confirmation. Comparison Trash offers confirmed restoration with saved assignments and Android expiry information. No physical phone-folder picker or permanent-delete action. These native changes and the sample preview are implemented; Android runtime verification remains pending separate approval.
