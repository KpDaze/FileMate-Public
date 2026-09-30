# Behaviour and implementation boundaries

The user's complete FileMate handover in this conversation is authoritative. This document records the approved boundaries and the implemented milestones; it does not narrow the final Version 1 target.

## Local-first

Native Android, Kotlin, Jetpack Compose, local database. Optional Google Drive later. Core operation has no Google dependency, internet permission, paid inference, backend or recurring service. Shared phone storage only; other apps' private storage is excluded. The user works primarily from an Android phone and does not code. Keep a stable phone design preview and downloadable APK checkpoints.

## Monitoring proof

User tap → foreground service → baseline/catch-up → directory observers attached → readiness acknowledgement → Android app launch. The foreground service is explicitly started while FileMate is visible. It is not restarted on boot or kept running permanently. Each FileObserver is strongly retained until the session ends.

Storage monitoring is event-driven. Download directory contents are not polled. Per-file stability checks happen after a file event. Stage 2 also validates a file's length and modification time, and a full content fingerprint, between preview and apply before changing it. A changed or missing source is refused.

Usage Access is optional. During a session, usage events are sampled every 15 seconds and on a file event. This is not storage polling. Selected AI activity refreshes a monotonic 30-minute timeout. Ordinary Android Recent Apps switching works without returning to FileMate. Screen-off and lock events prevent treating an idle AI screen as continued use. Without permission, the app plainly states that timeout/source context can only use the most recent Hub launch. Android can delay timers while suspending an app; device tests must verify the behaviour and battery restrictions on the actual phone.

A quiet foreground notification has an explicit stop action and is removed when the service ends. Android 13+ notification permission is optional; foreground service execution still follows platform rules if that permission is declined. The `specialUse` foreground type describes this user-started temporary observation; this personal app is sideloaded.

## Catch-up

Only accessible Downloads and Documents are in Test 01. First access indexes existing files without presenting them as new AI downloads. Later opens compare path, size and modified time against SQLite records; a new path with an old modification date is still new. A directory check is marked successful only if traversal completed. Live detection and reconciliation update the index transactionally, suppressing repeated identical observations. Scans never follow symlinks outside the root.

The monitoring catch-up path still does not identify a same-path rewrite that preserves both size and modification time. Stage 2 cleanup separately hashes same-size duplicate candidates, and every proposed move is fingerprinted. OCR is absent. First-run monitoring baseline remains separate from the deliberate historical cleanup scan.

## Classification

Filename clues plus nearby selected AI use can raise source confidence. Filename alone is medium confidence. Timing alone is low confidence, never proof of source. Unrecognised files, installers and obvious receipt/bank/statement filenames are excluded from AI Recent. Catch-up cannot reconstruct past app activity and does not pretend to do so. Unknown generic filenames may therefore remain excluded until the deliberate cleanup scan.

The source classifier is deliberately preliminary: time correlation can produce false suggestions. Projects are never invented. Stage 2 keeps source confidence separate from project confidence. Manual assignment confirms the project only in local metadata; it does not change the file. Cleanup moves and optional tidy names require a separate preview and Apply action.

## Read-only Gallery (Stage 3A)

Phone → Gallery reads MediaStore images and videos on mounted shared-media volumes. It requests ordinary photo/video permissions on demand, including selected-media access on Android 14+, rather than requiring broad All files access. It rechecks access and refreshes on entry/resume or a deliberate Refresh; there is no polling or Gallery background service. Query failure or revoked access hides unavailable entries while retaining user-owned assignments. An empty accessible snapshot never deletes index records.

Schema 5 preserves older tables and records. Media identity includes volume, MediaStore version, kind, row ID and generation-added so recycled Android IDs cannot silently reuse another entry's Gallery identity. Rescans update observed metadata while retaining original indexed name/path and manual project assignments. Gallery and existing organiser records share an explicit assignment for the same path. Deleting a project keeps files and media records. Thumbnails use Android's local provider; failures show Unavailable and Refresh Gallery.

The chronological grid filters All, Camera, Screenshots, Downloads and Projects. Screenshot folder/collection or filename clues have honest confidence; dimensions alone never classify a screenshot. Camera-folder items are browsable and never automatically selected. Details show location, type, size, dimensions, date, video duration and screenshot clues. Project assignment only writes FileMate's database. There are no media moves, renames, trash, deletion, OCR, exact-image grouping or visual-similarity actions in this slice.

## Remaining agreed product behaviour

Project is the main organisation unit across AI sources, screenshots, documents and Drive. Projects are created manually. Preserve useful names; store richer metadata separately. Low-confidence items stay available in Needs Sorting. Batch assignment changes local project metadata only. Cleanup scanning changes no files, and every move/rename is previewed with per-file exclusions. Stage 2 never deletes a file; moved files receive an action journal and Undo where the source and destination still validate.

The completed Gallery stages will add favourites/albums, reviewed recoverable media actions and duplicate/version review to Stage 3A browsing. Camera photos are not automatically changed without a deliberate rule or cleanup action. On-device OCR and exact hashes help screenshots; similar images are suggested groups, not proof that any image is disposable. Distinguish duplicates from changed versions.

Approved Drive direction: global Phone only default; simple per-project Phone + Drive override. Drive after upload is deliberate and removes local data only after upload success is verified. Rule changes apply to future files; existing files require a separate review. Google Drive remains optional. Core functionality must continue without it. No hosted/paid dependencies may be introduced without explicit approval.

### Gallery sorting view — 29 September 2026

Phone → Gallery starts on Unassigned. Assignment saves a project record, closes details on success and removes that item from Unassigned. Errors keep details open. All Gallery and Projects retain assigned media, and clearing assignment returns media to Unassigned. No media is moved or deleted. Project Gallery still opens its selected project.

## Confirmed Gallery assignment — 29 September 2026
Gallery supports manual multi-select. Select photos, choose Assign selected, review the item names and destination, then Confirm assignment. Single-item details use the same review. Choosing a project alone changes nothing; Cancel or Back saves nothing. Clearing assignments also requires confirmation. A successful batch is one SQLite transaction, including organiser metadata and history; an invalid item rolls the batch back. No media moves, renames or deletions occur. Unassigned items leave that view only after confirmation and remain in All Gallery and Projects. Filter changes, Refresh and leaving Gallery clear pending selection.

## Screenshot groups and Needs Sorting — Stage 3B
Gallery’s Screenshots filter groups accessible likely still screenshots by local calendar date, shared-media volume and folder. Camera-folder entries and videos are excluded even when a filename has a screenshot clue. Missing dates/folders remain separate items rather than implying a relationship. These are metadata browsing groups, not visual similarity, OCR, source attribution or project predictions.

Needs Sorting now derives unassigned screenshot groups from the existing media index, alongside other files. An item already in a screenshot group is not repeated in the other-files list. This is a derived view, with no new intake records, schema migration, file operation or overwritten assignment. Opening a group or all unassigned screenshots enters Gallery’s Needs Sorting filter. Review and manually select items, choose a project, then Confirm assignment. Cancel saves nothing. Assignment removes from intake while All Gallery/Projects retain it; clearing/deleting the project restores eligibility. Access loss or scan failure hides media intake while retaining stored assignments. Projects/Needs Sorting refresh accessible Gallery metadata on entry/resume; no background polling.

## Image comparison — Stage 3C
Gallery → Compare images performs a deliberate cancellable scan of accessible still images. Size plus full-stream SHA-256 establishes exact groups. Visual suggestions compare 9×8 decoded colour/edge patterns with aspect checks and exclude flat or animated images; filename-version suggestions require related names in the same folder. These approximate suggestions are distinct from exact copies and never prove an image disposable. There is no OCR, external service or network permission.

Exact hashing covers every readable accessible image up to 256 MiB per file. Visual/name suggestions cover the first 500 successfully read images, newest first, with up to 100 pairs per approximate category. Approximate pairs are not transitively merged; one representative per exact group avoids repeated suggestions. Failed reads/decode and coverage are reported; no result does not prove there are no copies. Metadata is rechecked before showing results, and leaving/resuming requires a fresh scan.

Side-by-side review shows names, locations, size, dimensions, dates and project assignments, with larger views and cycling for groups of more than two. Keep all records a dismissed group in FileMate's existing local state; it never deletes/moves media or changes assignments. Show kept groups and Review this group again restore review. Keys incorporate group members and content/observed metadata; changed evidence can reappear. The most recent 2,000 decisions are retained. Schema remains 5. Camera images can appear in a deliberate comparison, but nothing is auto-selected or reorganised.

## Comparison actions — 30 September 2026 afternoon
Kel explicitly requested resolving comparison findings in place. This supersedes Stage 3C's earlier review-only action scope: each displayed image has a manual checkbox; selected members can be assigned to an existing FileMate project or sent to Android's recoverable Trash. No phone-folder picker or physical project-folder move is introduced. Assignment is still metadata-only and requires destination selection followed by Confirm assignment; Cancel preserves the selection and reopening resets the destination.

Trash review lists exactly the selected names/locations, explains recovery/expiry, and requires Confirm Trash followed by Android's own media confirmation. No automatic selection or permanent-delete API exists. Before assignment/Trash, fresh MediaStore identities, observed metadata and full content hashes must still match the reviewed scan. Changed/unavailable content is refused; requests are bounded to 2,000 selected images. Android's result is checked against actual provider state rather than assuming the button succeeded.

Comparison Trash is reachable before scanning, including after reopening the app. A local list of media identities is persisted before opening Android's prompt, so recovery does not depend on an in-memory Undo button. It lists only accessible, still-trashed items with matching volume/version/generation-added identity, saved projects and Android's expiry date when provided. Restore selected requires FileMate review and Android confirmation using createTrashRequest(false). Android controls expiry and may permanently remove expired items; the app does not promise indefinite recovery. Gallery refresh keeps assignments while hiding trashed/unavailable images. Known media is no longer duplicated as an ordinary project file when unavailable; counts reflect visible media. Restore repopulates Gallery with saved metadata. The browser preview simulates these steps with sample-only session state; it does not exercise Android permissions or real Trash, and reload resets those sample actions.

Platform contract: https://developer.android.com/reference/android/provider/MediaStore#createTrashRequest(android.content.ContentResolver,%20java.util.Collection%3Candroid.net.Uri%3E,%20boolean)
