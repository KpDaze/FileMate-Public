# Behaviour and implementation boundaries

The user's complete FileMate handover in this conversation is authoritative. This document records the approved boundaries and the Test 01 implementation; it does not narrow the final Version 1 target.

## Local-first

Native Android, Kotlin, Jetpack Compose, local database. Optional Google Drive later. Core operation has no Google dependency, internet permission, paid inference, backend or recurring service. Shared phone storage only; other apps' private storage is excluded. The user works primarily from an Android phone and does not code. Keep a stable phone design preview and downloadable APK checkpoints.

## Monitoring proof

User tap → foreground service → baseline/catch-up → directory observers attached → readiness acknowledgement → Android app launch. The foreground service is explicitly started while FileMate is visible. It is not restarted on boot or kept running permanently. Each FileObserver is strongly retained until the session ends.

Storage monitoring is event-driven. Download directory contents are not polled. Per-file stability checks happen after a file event. Stage 2 also validates a file's length and modification time, and an available hash, between preview and apply before changing it. A changed or missing source is refused.

Usage Access is optional. During a session, usage events are sampled every 15 seconds and on a file event. This is not storage polling. Selected AI activity refreshes a monotonic 30-minute timeout. Ordinary Android Recent Apps switching works without returning to FileMate. Screen-off and lock events prevent treating an idle AI screen as continued use. Without permission, the app plainly states that timeout/source context can only use the most recent Hub launch. Android can delay timers while suspending an app; device tests must verify the behaviour and battery restrictions on the actual phone.

A quiet foreground notification has an explicit stop action and is removed when the service ends. Android 13+ notification permission is optional; foreground service execution still follows platform rules if that permission is declined. The `specialUse` foreground type describes this user-started temporary observation; this personal app is sideloaded.

## Catch-up

Only accessible Downloads and Documents are in Test 01. First access indexes existing files without presenting them as new AI downloads. Later opens compare path, size and modified time against SQLite records; a new path with an old modification date is still new. A directory check is marked successful only if traversal completed. Live detection and reconciliation update the index transactionally, suppressing repeated identical observations. Scans never follow symlinks outside the root.

A same-path rewrite that preserves both size and modification time is not identified in this proof; hashes are a later milestone. No content reading, OCR or hashing is included yet. First-run baseline is not a historical cleanup scan. Historical cleanup remains a separate deliberate review-first feature.

## Classification

Filename clues plus nearby selected AI use can raise source confidence. Filename alone is medium confidence. Timing alone is low confidence, never proof of source. Unrecognised files, installers and obvious receipt/bank/statement filenames are excluded from AI Recent. Catch-up cannot reconstruct past app activity and does not pretend to do so. Unknown generic filenames may therefore remain excluded until manual cleanup in a later build.

The source classifier is deliberately preliminary: time correlation can produce false suggestions. Projects are never invented. Stage 2 keeps source confidence separate from project confidence. Manual assignment confirms the project only in local metadata; it does not change the file. Cleanup moves and optional tidy names require a separate preview and Apply action.

## Remaining agreed product behaviour

Project is the main organisation unit across AI sources, screenshots, documents and Drive. Projects are created manually. Preserve useful names; store richer metadata separately. Low-confidence items stay available in Needs Sorting. Batch assignment changes local project metadata only. Cleanup scanning changes no files, and every move/rename is previewed with per-file exclusions. Stage 2 never deletes a file; moved files receive an action journal and Undo where the source and destination still validate.

The gallery provides chronological local browsing, camera/screenshots/downloads/projects, useful favourites/albums, move/rename/trash and duplicate review. Camera photos are not automatically changed without a deliberate rule or cleanup action. On-device OCR and exact hashes help screenshots; similar images are suggested groups, not proof that any image is disposable. Distinguish duplicates from changed versions.

Approved Drive direction: global Phone only default; simple per-project Phone + Drive override. Drive after upload is deliberate and removes local data only after upload success is verified. Rule changes apply to future files; existing files require a separate review. Google Drive remains optional. Core functionality must continue without it. No hosted/paid dependencies may be introduced without explicit approval.
