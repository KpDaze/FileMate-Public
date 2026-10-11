# Automatic organiser repair — 11 October 2026

This report supplements the original V1 specification and definitive October 4 handover. It does not redefine V1 or close phone findings. All F01–F15 remain OPEN in [the audit](V1-RECOVERY-AUDIT-2026-10-11.md).

## Baseline and coordination

- Active baseline: `main`, `743b9a6b1a6ea2f45b29bdad8edf58a13ddc6f4e`.
- Original `Personal_AI_File_Organiser_Handover.txt` and definitive `FileMate-DEFINITIVE-Handover-2026-10-04.md` were read completely from Library and retained unchanged.
- A dedicated Coder implemented the repair; a separate Verifier inspected actual source, wrote independent adversarial tests, checked migration SQL and re-reviewed fixes. The coordinator reconciled the recovery branch, preserved its standalone storage/Gallery functionality and ran full local Android checks.
- Baseline `743b9a6` only assigned project metadata. It had no physical automatic organiser invocation. The October 4 recovery code was on `recover-core-auto-sorting` at `cc9b8ec022299e806eb7db9f42d0ac05b10a5215`, absent from main. Recovery code was selectively reconciled without importing its workflows or replacing the audit.
- App package stays `app.filemate`; update version is `0.4.1-organiser-repair`, versionCode 7. Database advances additively to schema 9 from both main schema 6 and recovery schema 8. Existing private signing identity remains outside Git and GitHub Actions.

## Implementation

| Area | Implemented behavior | Main source |
|---|---|---|
| Source detection | Whole provider names/aliases, selected-app package and label, freshness, folder, supported extension, generation-style filename and reviewed local patterns are combined. Conflicting providers stay uncertain. Timing alone stays Low. Generation-style names and learned patterns can improve suggestions without establishing High certainty. | `Rules.kt`, `LearningStore.kt` |
| Project suggestions | Unique complete project-name matching is separate from AI source confidence. Filename-pattern suggestions use existing user-created projects; corrections can lower a conflicting exact match to Medium. Needs Sorting, details, Gallery items and unanimously matching screenshot groups expose suggestions. | `ProjectRules.kt`, `MainActivity.kt`, `GalleryScreen.kt` |
| Local learning | Reviewed file/Gallery assignments and source corrections teach filename patterns. One example per file prevents repeat taps inflating support; correction replaces/retracts previous project evidence. Source learning needs two distinct reviewed examples and preserves disagreement. Old confirmed assignments are seeded conservatively; old journal paths and camera media are excluded. Automatic moves do not train themselves. | `LearningStore.kt`, `Store.kt`, `GalleryStore.kt` |
| Physical organisation | Stable, fresh, genuinely new live events can move to `Documents/FileMate/<project>/` only with High source and unique High project confidence. Downloaded names are kept. Catch-up and newly discovered folder scans never initiate physical moves. Managed folders, canonical aliases, prior action paths and intervening user reviews are excluded. First-event proof closes the race where reopen catch-up indexes a new live file before its stability checks finish. | `AutoSorter.kt`, `MonitorService.kt` |
| Move safety | Full SHA-256 verification, exclusive destination creation, a journal before transfer, read-back checking, file-identity/parent checks and Activity/Undo protect moves. Conflicting or interrupted copies are retained for review. Undo cannot overwrite a recreated original path. | `FileActions.kt`, `VerifiedFileTransfer.kt`, `Store.kt` |
| Filename suggestions | Saved naming preference now controls reviewed move defaults. Current/proposed filenames appear before the final path review. Repeating a tidy suggestion does not keep adding project/date prefixes. Defaults and all automatic moves preserve original filenames. | `FileActions.kt`, `MainActivity.kt`, `GalleryScreen.kt` |
| Recovery compatibility | Restore existing timeout/naming/provider controls and reviewed Gallery physical moves from recovery, preserving schema-8 tables/settings. Gallery moves now use the common journal/lock and reject stale indexed media. Failed provider copies retain their partial destination for review. | `Store.kt`, `GalleryFileOrganiser.kt`, provider classes |
| Existing-download review | Catch-up can surface previously baseline-indexed candidates without duplicating old observations, and only logs meaningful new detections. Cleanup uses local learned/corrected source evidence. “Not AI” corrections update current Cleanup classification/counts immediately. | `FileMateApp.kt`, `Cleanup.kt`, `LearningStore.kt` |

## Verification evidence

Verification was performed locally without an Android emulator or GitHub Actions run.

- **PASS:** `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` using Gradle 8.13, JDK 17, Android compile SDK 36 and the repository's declared dependencies. Final batch completed successfully in 3m 59s, including Android Kotlin/Java compilation, R8/resource shrinking and APK assembly.
- **PASS:** all **73 JVM tests**, zero failures, errors or skips. The independent Verifier separately inspected every XML report. This includes 17 independently authored regressions, 10 existing transfer tests and 46 other tests.
- **PASS:** Android lint has **zero errors** and 17 warnings. These include dependency/update advice and an existing mutable-collection state warning; this is not a claim of zero warnings or verified phone layout.
- **PASS:** `git diff --check`; original F01–F15 rows match the baseline verbatim.

- Independent adversarial JVM tests exercise source/provider ambiguity, stale/future timing, folder/learned/Not-AI conflicts, camera exclusion, unique project boundaries, correction suppression, live-only policy, symlinks and same-content source/destination replacement during copying.
- `python3 tools/verify-schema-migration.py` executes SQL extracted from the actual Kotlin schema functions against host SQLite. It verifies 6→9 and 8→9 migration, every seeded old row, schema equivalence to a fresh database, foreign keys, integrity, reviewed-only seeding and live eligibility vetoes. Seeded data includes projects, Gallery, favourites, albums, all journal states, provider settings/rules and legacy learning rows. This is not Android SQLiteOpenHelper/device proof.
- The original 15 phone-finding rows are checked verbatim against the baseline. No requirement was deleted, silently deferred or marked complete.

## What remains open

| Audit finding | Status after this batch |
|---|---|
| F01 — useful file content previews | OPEN. Details/correction access improves navigation; general document/image content preview in Likely AI files is not implemented by this batch. |
| F02 — automatic organisation | OPEN. Implemented and checked in code; actual watcher, filesystem move and Activity/Undo need phone acceptance. |
| F03 — AI attribution mostly Low | OPEN. More clues and learned suggestions implemented; actual filenames/app activity need phone acceptance. Generic timing alone deliberately remains Low. |
| F04 — learning/corrections | OPEN. Local examples, migration seeding and corrections implemented; persistence and usefulness need phone acceptance. |
| F05 — Gallery/project suggestions | OPEN. Item/group suggestions implemented; actual screenshot names and phone presentation need acceptance. |
| F06 — comparison Trash rescans | OPEN. Full-library refresh behavior is unchanged. |
| F07 — catch-up log/classification | OPEN. Meaningful-entry logging retained and historical candidate recognition improved; on-phone behavior and coverage remain unverified. |
| F08 — notification access control | OPEN. Reported Android permission behavior is not resolved by this batch. |
| F09 — Choose access control | OPEN. Reported picker/permission behavior needs separate reproduction and repair. |
| F10 — invisible filename suggestions | OPEN. Preference wiring and visible previews implemented; phone acceptance still required. |
| F11 — Gallery selection spacing/destinations | OPEN. The reported selection-layout problem is not closed. |
| F12 — storage controls/readability | OPEN. Restored choices use a vertical layout and explain reviewed copies; provider flow and phone readability still need acceptance. |
| F13 — resume after timeout | OPEN. Existing configurable finite sessions are preserved; monitoring still requires a new Hub launch after expiry. No unapproved background restart policy was introduced. |
| F14 — ZIP grouped as Archives | OPEN as the original ledger requires. ZIP remains an archive; extension alone never authorizes a move. |
| F15 — Drive/provider acceptance | OPEN. Existing reviewed SAF provider functionality is preserved; it is not full account-level Drive organisation and has not passed phone acceptance. |

Other original V1 requirements, including optional on-device OCR and richer metadata/Drive scope, remain governed by the original documents. Legacy aggregate token rows are retained, but new suggestions use provenance-aware reviewed examples so a correction can retract its own support. Learning cannot infer the topic of an arbitrary timestamp-only screenshot.

## Phone acceptance — disposable files only

These are outstanding tests, not claimed results. Install the verified update over the existing app; do not uninstall or clear app data.

1. **Preservation:** open FileMate and check existing projects, Gallery favourites/albums and settings. Confirm Setup reports `0.4.1-organiser-repair`. Report an update/opening failure before proceeding.
2. **One safe automatic move:** create a distinctive temporary two-word project such as `Cobalt Lantern`. Launch an AI through FileMate, then download a disposable file whose filename includes that provider and the whole project name, e.g. `ChatGPT_Cobalt_Lantern_notes.txt` from ChatGPT. It should move to `Documents/FileMate/Cobalt Lantern/`, appear under that project and record “Automatically organised” in Activity. Return to FileMate promptly once as part of the test. Use Activity → Undo; the same file should return to its original location. Reopening must not move it again.
3. **Uncertain cases:** download generic, wrong-provider and ambiguous-project names. They must not auto-move. Existing catch-up files and personal camera media must remain physically untouched. Do not use important files as fixtures.
4. **Learning/correction:** assign a disposable `mountain_assets_v01` file to the temporary project. A later matching filename should suggest that project, without moving on learned evidence alone. Change the reviewed assignment and check that the old example no longer supports its former project. Correct the source on two distinct similarly named disposable files through Details → Correct source; a later matching name can suggest that source at Medium. Mark one Not AI to check conflicting evidence remains review-only.
5. **Filename preview:** choose Setup → Suggest tidy names, then open a disposable file’s Details → Move / filename suggestions, choose a project and inspect the proposed name. Cancel first and confirm no change. Then explicitly preview/confirm one move and test Undo. Repeat the preview from Gallery → Move / rename file.
6. **Gallery:** inspect a downloaded image with a matching project/pattern. Its suggestion should be visible and require review; personal camera items must not be automatically organised.

Report the step and observed result. Remaining permission, comparison, selection-layout, timeout-restart and provider findings retain their own acceptance requirements in the audit; these checks do not replace them.
