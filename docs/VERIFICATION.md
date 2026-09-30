# Verification status

## Corrected Test 01 proof

The original 0.1.0 APK is rejected. Android 15 runtime testing on 25 September 2026 reproduced a Hub launch failure after monitoring started.

The corrected 0.1.1 build passed two fixture launches, live background detection, ordinary app switching, receipt exclusion, low-confidence handling, manual stop, notification removal, catch-up and repeated-open deduplication on Android 15. The fixture run used synthetic local Qwen and ChatGPT exporters, not real provider accounts.

[Public GitHub run 36285379397](https://github.com/KpDaze/FileMate-Public/actions/runs/36285379397) completed successfully on 27 September 2026. It used a standard public `ubuntu-latest` runner, KVM and Android 15/API 35. The run built the app, ran the JVM logic tests, installed FileMate, started its private monitoring service under the app identity, confirmed the session remained active at the at-least-29-minute check, then confirmed automatic stop, its Activity record and notification removal. The reported 1785 seconds starts after a 25-second setup delay; actual service life crossed the intended approximately 30-minute boundary. The production timeout and device clock were unchanged.

The public run did not select two real AI apps, use provider accounts, test real provider downloads, or prove physical-phone permission and battery behaviour. The earlier fixture run covers normal switching and live exports separately.

## Physical phone

Kel installed the corrected 0.1.1 APK on 27 September 2026. Installation and the screens tried appeared to work. Kel left the later broad-access setup incomplete. The physical-phone result is therefore partial: installation/basic opening passed; shared file watching, real AI apps, switching, catch-up and the inactivity stop remain pending.

## Stage 2 organiser

The full Stage 2 source uses version `0.2.0-stage2d`, code 3, with non-destructive database migrations through version 4. It provides:

- user-created projects with create, rename, delete and browse;
- Needs Sorting with source confidence separate from project confidence;
- single and batch assignment without changing the underlying file;
- a deliberate cleanup scan covering Downloads, Documents, DCIM, Pictures, Movies, Music and optional folders selected through Android's folder picker;
- review categories for likely AI files, unsorted downloads, large files, old non-camera files, archives and exact duplicates;
- previewed project moves and optional tidy names, per-file exclusions, conflict-safe destination names, action journalling and Undo.

Cleanup scanning writes only local metadata. It does not move, rename or delete a file. A physical move/rename requires a second reviewed action. Camera and Pictures items receive an explicit warning and are never selected automatically. Unsupported document providers remain assignment-only. Stage 2 contains no delete action.

[Lightweight run 36291518444](https://github.com/KpDaze/FileMate-Public/actions/runs/36291518444) passed 12 JVM tests, Android lint and debug assembly on 27 September 2026. [Bounded Android run 36291518440](https://github.com/KpDaze/FileMate-Public/actions/runs/36291518440) then passed on Android 15/API 35. Its disposable fixtures proved exact duplicate detection, project assignment, conflict-safe naming (`same (2).txt` when `same.txt` already existed), an actual shared-storage move, preservation of the existing file and Undo to the original path and project state.

The Android proof used disposable fixture files rather than Kel's personal folders. It is source-level and emulator evidence, not physical-phone permission or usability acceptance.

## Stage 2 safeguard repair — 27 September 2026

Kel authorised corrections enforcing the handover's existing no-overwrite and changed-file/Undo requirements before Gallery work. Source now uses exclusive destination creation (`CREATE_NEW`) instead of an atomic rename that can replace a late-arriving file. Moves copy, flush and verify content before removing the source. This requires temporary free space for the new copy. Copy/check failures retain the source and any partial copy for review; recovery does not automatically remove either copy.

Every supported move preview now computes SHA-256, including unique files that the duplicate scan did not hash. Apply, Undo and interrupted-operation recovery check the recorded content. Older actions lacking a hash are refused for Undo instead of guessing a baseline. Ambiguous actions remain in review. The existing schema version 4 and historical records are preserved.

Ten new JVM filesystem tests and expanded disposable Android fixtures cover destination collisions, same-size edits, legacy records, interrupted copies and recovery. [Build run 36307146181](https://github.com/KpDaze/FileMate-Public/actions/runs/36307146181) passed unit tests, Android lint and debug assembly for repair commit `075245d134c198fa1914047c9e15f8e35ddea196`. The first [Android run 36307146159](https://github.com/KpDaze/FileMate-Public/actions/runs/36307146159) and [diagnostic run 36307529086](https://github.com/KpDaze/FileMate-Public/actions/runs/36307529086) refused the first disposable preview before a move. The diagnostic proved that Android NIO attributes rounded a timestamp to seconds while the existing scanner retained milliseconds; the file content hashes matched. Commit `1c59694e88c1beb21a001358acbee8af63fdafe0` uses the scanner’s timestamp API consistently without relaxing content checks. [Final build run 36307827229](https://github.com/KpDaze/FileMate-Public/actions/runs/36307827229) passed unit tests, Android lint and debug assembly. [Android run 36307827218](https://github.com/KpDaze/FileMate-Public/actions/runs/36307827218) passed on Android 15/API 35. Its recorded JSON confirms normal move/Undo, preserved pre-existing files, late move and Undo collisions, unique-file fingerprints, same-size edit refusal, legacy Undo refusal, interrupted-copy preservation, validated recovery and millisecond timestamp preservation. Both final runs tested `1c59694e88c1beb21a001358acbee8af63fdafe0` on standard public runners. Packaging and artifact upload were skipped; only disposable emulator test APKs were built. No new phone APK was delivered during the safeguard repair; Stage 3A was still unstarted at that checkpoint.

## Artifact and signing boundary

The corrected personal 0.1.1 APK has SHA-256 `e64e30bf6130ad338a421f3c387e0cdb771f41ce43f7ba1b5ccd0d72023fd9c1`. Its signature, certificate, 16 KB ZIP alignment and packaged entries were verified. Private signing and recovery records remain outside this public repository. Future phone APKs must use the same backed-up personal identity to update the installed copy.

The personal Stage 2 update is `FileMate-0.2.0-Stage2.apk`, version code 3, SHA-256 `79ca2ce8b2d80318191516279d55642dc54355c540e36db571345460ae3d4cee`. APK Signature Scheme v3 verification passed. Its certificate SHA-256 is `538093df7ff5c1e9658785da5530307e3db4d01daa921b3fcbc1e3e7a1bb798b`, matching the preserved personal signing record used for the installed 0.1.1 build. All 19 non-signature APK entries match the tested Gradle output byte-for-byte.

[Packaging run 36292038157](https://github.com/KpDaze/FileMate-Public/actions/runs/36292038157) built, tested and linted the payload before its one-day packaging artifact was retrieved. The private key never entered the repository or GitHub Actions. [Final main run 36292283138](https://github.com/KpDaze/FileMate-Public/actions/runs/36292283138) passed after automatic artifact upload was disabled again.

`docs/test-01-artifact.json` describes the rejected 0.1.0 artifact and must not be treated as the corrected release record.

## Historical failures retained as evidence

- Two early public workflow attempts failed in their harness before inactivity timing began: run `36284780316` tried to start a non-exported service as the shell; run `36285111279` omitted an explicit Android user while using FileMate's debug UID.
- An earlier private run, `36227791658`, never received a runner because the private Actions allowance/billing blocked job startup.
- Older software-only workspace emulators repeatedly failed during Android startup before FileMate installation. Those are environment failures, not FileMate timeout failures.

See [runtime verification](RUNTIME-VERIFICATION.md) for the detailed chronology and its evidence limits.

## Stage 3A read-only Gallery — 28 September 2026 (Brisbane)

Implementation commit `509c23675a0522776ebea279923422349a8ca38f` adds Gallery under Phone, schema 5, MediaStore indexing, photo/video permission handling, screenshot clues, chronology/filters/details and individual project assignment. No network permission or new runtime dependency was added; no signed phone package was created.

- [Build run 36325638328](https://github.com/KpDaze/FileMate-Public/actions/runs/36325638328) passed 29 JVM tests (8 Gallery rules tests plus the existing 21), Android lint and debug assembly.
- [Stage 2 regression run 36325638325](https://github.com/KpDaze/FileMate-Public/actions/runs/36325638325) passed the move/Undo and safeguard fixture proof on Android 15/API 35 with the schema 5 source.
- [Gallery proof run 36325638376](https://github.com/KpDaze/FileMate-Public/actions/runs/36325638376) passed on Android 15/API 35 without All files access. Its six phases are `full`, `images`, `denied`, `selected`, `restored` and `changed`. Three generated PNGs and a synthetic short MP4 were published by the shell, outside FileMate's ownership. Assertions cover metadata/dimensions/video duration, screenshot/camera clues, idempotent scans, explicit assignment/counts, absence and restoration, observed metadata changes with original-name preservation, project deletion, version 4→5 migration preserving records, and byte hashes unchanged across Gallery operations.
- Selected-only permission was tested with **zero selected items**; the browser preview also exercises a simulated non-empty selection. Actual Android picker selection/reselection with non-empty grants, large libraries, removable storage and manufacturer-specific behaviour still need phone/device acceptance. Do not claim those were proven.
- Native UI smoke coverage was added by `0a918006d756e5d3b40f222385c1eeaadda4709c`. [Initial UI run 36326078572](https://github.com/KpDaze/FileMate-Public/actions/runs/36326078572) passed all six media phases and reached Gallery, then failed because the test tapped an off-screen filter without scrolling. Commit `20c196877ab696e9b99ebf40e3b8a9d962105b68` adds that bounded gesture; app code is unchanged. [Final Gallery/UI run 36326540986](https://github.com/KpDaze/FileMate-Public/actions/runs/36326540986) passed all six media phases plus actual Android Phone → Gallery navigation, screenshot and camera filtering, opening media details, and returning to All.

The 30-minute monitoring proof was not repeated because monitoring and timeout behaviour were not changed. Only temporary emulator APKs were built; signing material and personal files were not accessed. Source version `0.3.0-stage3a` / code 4 is reserved for a future requested signed checkpoint and is not installed on Kel's phone.

The same browser preview was published as Site version 4 from source `9342d6d780571cff4bf9a4e4786b40f769124a1a`. Phone → Gallery uses samples and preserves the existing five tabs; browser checks cover filters, details, assignment, limited visibility and empty states. It does not establish real-phone acceptance.


## Unassigned Gallery refinement — 29 September 2026 Brisbane

Native implementation `0d365a16d09353cba0a2abec4c1a74af1f0219c2` adds the user-approved Unassigned starting view, All Gallery, success-only detail dismissal, assignment confirmation and project labels. Assignment remains metadata-only; no schema or file-action change.

- [Build 36487421503](https://github.com/KpDaze/FileMate-Public/actions/runs/36487421503): JVM checks, lint and debug assembly passed. Phone packaging/upload steps were skipped.
- [Gallery proof 36487421513](https://github.com/KpDaze/FileMate-Public/actions/runs/36487421513): one Android 15/API 35 run passed all existing six media phases and the updated native UI proof. Unassigned dropped from 3 to 2 after assigning the screenshot, details closed, Refresh retained the assignment, All Gallery retained all 4 media, and clearing restored 3 unassigned. Exact file hashes at the original four fixture paths were unchanged after the UI flow.
- Browser proof: 5 unassigned samples dropped to 4 after assignment; All Gallery retained 6 and showed the saved project; clear restored 5. Runtime integrity, production build and four Worker checks passed. Same Site published version 6, source `47d6ee23e5d632f469574e84f36fa33b471fcc0f`.

No Stage 2 regression or long inactivity rerun was started; those code paths are unchanged. No signed phone APK, personal file operation, network permission, cloud API/key or paid service was added. The installed phone remains Stage 2, so this refinement is not phone-tested. Stage 3B grouping, Needs Sorting intake and batch Gallery assignment remain future work. The browser preview is separately maintained; it does not automatically update from native code.

## Confirmed single/batch Gallery assignment — 29 September 2026 Brisbane

App implementation `a2f3bffbc9a0e09e7737366cfd672b2f6df088dd` adds manual multi-select and a shared explicit review for single/batch assignment and clearing. Choosing a project alone saves nothing. Confirm commits all items in one SQLite transaction; Cancel leaves assignments unchanged. Successful confirmation returns to Gallery and removes assigned items from Unassigned while retaining All Gallery/project visibility. No media bytes/paths, schema or network permissions change.

- [Build 36502169211](https://github.com/KpDaze/FileMate-Public/actions/runs/36502169211) passed unit tests, lint and debug assembly; phone packaging/upload skipped.
- [Stage 2 regression 36502169212](https://github.com/KpDaze/FileMate-Public/actions/runs/36502169212) passed. The existing broad `app/src/debug/**` trigger automatically included this run when GalleryProbeActivity changed; it was not manually dispatched. No long inactivity run.
- [Initial Gallery run 36502169219](https://github.com/KpDaze/FileMate-Public/actions/runs/36502169219) passed six media phases, atomic batch rollback/success, single and batch cancellation, confirmation of two items, refresh persistence, All Gallery retention and clearing. Its final tap missed Unassigned because the grid was scrolled. Test-only commit `1c96a175d24a472c3d5ebbdd2872c7df33848d75` scrolls back to the header; app code is unchanged.
- [Final Gallery run 36502777235](https://github.com/KpDaze/FileMate-Public/actions/runs/36502777235) passed all six phases and the full native UI proof: cancelled assignments unchanged, two confirmed items removed from Unassigned, Refresh retained them, All Gallery retained four, confirmed clear restored Unassigned to two, and exact hashes at all four original fixture paths remained unchanged.
- Browser checks passed: Cancel leaves five unassigned; confirmed two-item assignment returns to Gallery with three; Refresh keeps assignments; All Gallery keeps six with labels. Single clear requires confirmation, and cancelling it keeps the existing project. Build, protected-runtime integrity (28 files) and four Sites checks passed.
- Same owner-private preview published successfully as version 7, source `56ab266b310f3cdaab1d91cc08f45887ba9f4182`, at the unchanged https://filemate-design-preview.kellypel1980.chatgpt.site URL.

No signed phone APK or real-phone test. Gallery batch assignment is delivered; Stage 3B screenshot grouping and Needs Sorting intake remain. The preview is manually maintained and published separately from native source. Kel's phone still runs the earlier Stage 2 package.

## Initial Stage 3B metadata grouping and screenshot intake — 29 September 2026 Brisbane

Native implementation: `e26614ae7e996bf97a5bef0b8556defd58f3cc6e`. Version name `0.3.0-stage3b`, version code 4, database schema 5 unchanged. Screenshot groups use local date, folder and volume, excluding camera-folder items and videos. Unknown folder/date entries stay separate. Needs Sorting derives unassigned accessible groups from the current media index, suppresses duplicate other-file rows and opens the existing manual selection/confirmed assignment flow. No OCR, new dependency, network permission, file operation, database migration or phone package.

- [Build run 36535657172](https://github.com/KpDaze/FileMate-Public/actions/runs/36535657172) passed `testDebugUnitTest lintDebug assembleDebug`. The suite now contains 37 tests, including 8 new grouping/intake rule tests. Gradle reported BUILD SUCCESSFUL in 3m 3s. Manual phone packaging/upload steps were skipped.
- New tests cover same-day/folder grouping; assigned/unavailable/camera/video/weak-clue exclusions; separate folders, storage volumes and dates; Brisbane midnight boundaries; missing metadata isolation; repeated-snapshot identity deduplication; clear-assignment eligibility; and stable group IDs.
- All three emulator workflows are now `workflow_dispatch` only, with the per-run approval rule in AGENTS.md. The source commit produced exactly one Actions run: the non-emulator build above. Zero emulator runs had been requested or started at this initial checkpoint; the three later approved runs are recorded below.
- Browser QA used the existing Pixel 10 simulation. Needs Sorting initially showed two screenshot groups and two other files. Opening one group showed one screenshot; Show all showed two. Manual selection and Cancel left both unassigned; reopening reset the project selection. Confirming both to ShiftMate removed them from intake, while All Gallery kept six and displayed both project labels. Confirmed clearing restored one screenshot group. Access off hid screenshot intake without altering the other-file list. No application error was observed; sampled console errors came from browser-extension metadata reporting.
- The preview passed TypeScript/Vite build, the protected 28-file mobile-runtime integrity check and four Sites checks. Same owner-private Site version 9 published successfully, source `a670d4944d80d37920af795af688688844e98671`, unchanged URL https://filemate-design-preview.kellypel1980.chatgpt.site.

Boundary at this initial checkpoint: Stage 3B had unit/build/lint and browser evidence, before the later Android runs below. Physical-phone acceptance remains pending. Previous Stage 3A Android evidence remains historical evidence for reused indexing/assignment code, not proof that these new screens were exercised on Android. No emulator run is needed merely to obtain screenshots. Physical acceptance can be batched into a later requested signed update with disposable screenshots, camera media and limited/revoked access; no signed APK was created. Next native development slice is Stage 3C, after Kel asks to continue. OCR remains optional and unadded; Stage 3D, optional Drive and full Version 1 acceptance remain in scope.

## First approved Stage 3B Android run — 29 September 2026, 17:47 Brisbane

Kel explicitly approved one combined emulator run at 17:42. Test-only source `b7531d3ada23d0dfe90113e3fd6710eddbb58533` extended the debug probe and UI script; production app code remains the previously built `e26614a` implementation. [Build 36538547232](https://github.com/KpDaze/FileMate-Public/actions/runs/36538547232) passed. [Approved Android run 36538690605](https://github.com/KpDaze/FileMate-Public/actions/runs/36538690605) ran once on standard `ubuntu-latest`, Android 15/API 35, from 17:47 to 17:54 Brisbane. It finished with a test assertion failure. The later separately approved focused run is recorded below.

Passed: all six media-access/index phases; the complete previous single/batch Gallery UI proof with original-path/hash preservation; the new native Stage 3B database/grouping probe (six disposable media, two screenshot groups, camera/video exclusions, rescan stability, assignment/clear/project-delete behavior, visibility loss/restoration, unchanged hashes); and new UI entry through Projects → Needs Sorting, group counts, duplicate-row suppression, opening the two-item group, manual selection and Cancel preserving two visible/selected screenshots.

The run stopped when the new UI script asserted that the UIAutomator node containing the text `Confirm assignment` had `enabled=false` after reopening review. It reported `Destination survived cancellation`. No UI XML was printed, so the log does not conclusively establish that the destination actually survived. Source `openReview` explicitly resets `target=null` and `targetChosen=false`. The test incorrectly treated a label node’s enabled property as sufficient evidence about the button. The prepared test correction checks absence of selected-destination text and, crucially, taps Confirm without choosing a destination and requires review to remain open. Failures now print the last UI dump. Production app/preview code has not been changed.

At the first-run checkpoint Stage 3B remained open: its later confirmation/removal/retention/clear UI assertions were not reached. A manual `stage3b_only` input is now available to skip the already-passed older UI sequence while retaining media probes and the Stage 3B probe/UI checks. Python syntax, shell syntax and diff checks passed for the test correction. A focused rerun requires Kel’s separate approval under the per-run rule. Preview version 9, installed phone APK and personal media are unchanged.


## Second approved Stage 3B run — 30 September 2026, 02:51 Brisbane

Kel separately approved this focused rerun with “Yes, go.” [Run 36600717161](https://github.com/KpDaze/FileMate-Public/actions/runs/36600717161) used current main `153c0e6483f67ab4db63d05fc91db261aa5859ab`, `stage3b_only=true`, and standard public `ubuntu-latest` / Android 15 API 35. It ran once from 02:51 to 02:57 Brisbane, without restart. Build succeeded; all six media phases and the Stage 3B probe passed.

The corrected behavioral confirmation check passed: Cancel preserved two selected screenshots, reopening had no destination, Confirm without a destination kept review open, and choosing a project plus Confirm assigned two items and removed that group from intake. Returning to Needs Sorting left the one Download screenshot; Review all opened exactly that one item.

The UI script then failed at `tap('Refresh', 'up')`. Its captured XML shows the page at the top, with the access card and a clipped `1 visible` row at y=534–536. Refresh is below that card; searching upward cannot expose it. The test now searches down for Refresh and All Gallery, and up for the count above the filter row. Remaining detail/clear/back navigation was reviewed against the source. Python syntax and diff checks passed. No production app or preview change is justified by this failure.

At the second-run checkpoint Stage 3B stayed open: refresh persistence, All Gallery retention, confirmed clear restoring intake, and final six-path/hash UI checks were not reached. There have been exactly two separately approved Stage 3B emulator runs; neither passed the entire new UI script. No third run had been approved or dispatched at that checkpoint; the subsequently approved final run is below. Any further focused run needs Kel’s explicit approval under AGENTS.md. The preview remains version 9; installed phone APK and personal media are unchanged.


## Stage 3B native fixture verification passed — 30 September 2026 Brisbane

Kel separately approved one further focused run with “Yes.” [Run 36640809269](https://github.com/KpDaze/FileMate-Public/actions/runs/36640809269) used tested commit `120828e863c35a70da156e745b71b9faddade48c`, `stage3b_only=true`, a standard public `ubuntu-latest` runner and Android 15/API 35. It was dispatched once, with no cancellation or restart, and passed. The older Gallery UI flow was skipped as intended; it already passed in the first Stage 3B run.

- Build succeeded; all six media/index/access phases and the Stage 3B data probe passed without All files access.
- Native UI passed date/folder groups, camera/video exclusion, duplicate-row suppression, manual selection, Cancel preserving selection and assignments, destination reset, and Confirm without a destination remaining in review.
- Confirming two screenshots removed their group from intake. Review all then showed one remaining screenshot, and Refresh preserved that result.
- All Gallery retained all six media items and the saved project assignment. Confirmed clear restored the screenshot to Needs Sorting alongside the remaining Download screenshot.
- Exact SHA-256 output at all six original fixture paths was identical before and after the complete UI flow.

This closes Stage 3B as a source/browser/Android-fixture checkpoint. There were three separately approved Stage 3B emulator runs: two test-script failures, followed by this full pass. No further emulator run is needed for this checkpoint. Production app source is unchanged from `e26614ae7e996bf97a5bef0b8556defd58f3cc6e`; the corrections were to tests only. Prior build/lint/37 JVM tests remain applicable. Preview version 9 already matches; no republish or phone APK was needed. No personal media or signing material was accessed.

Physical-phone acceptance remains pending, including non-empty Android selected-photo grants/reselection, larger libraries, removable storage and manufacturer-specific behavior. The selected-only emulator phase covers zero granted items, not a real non-empty picker selection. Installed phone remains Stage 2/version code 3. Stage 3C is the next development slice only when Kel requests it; optional OCR, Stage 3D, optional Drive and full Version 1 acceptance retain their recorded scope.

## Stage 3C source and browser checkpoint — 30 September 2026
Implementation `8bfcea6398414cd7e0ae66340c984982e7a191b9`, then review-scroll/decision-cap fix and focused proof preparation `b19eb17049e03a5b8a58eaf0083a20fc484fb8ed`. Non-emulator runs 36661317222 and 36662064034 passed. Final Gradle build/test/lint finished successfully in 2m 22s; 45 JVM tests comprise Rules 11, verified transfers 10, Gallery 8, screenshot rules 8 and image comparison 8. Phone-package/upload steps were skipped. Comparison tests cover exact evidence, duplicate IDs, conservative visual exclusions, aspect/colour/decode rejection, filename/folder versions, stable/content-sensitive keys, non-transitive pairs, the 500-image approximation limit, exact groups beyond it and cancellation.

Browser checks cover all three categories, side-by-side/larger review, Back without deciding, Keep all, rescan persistence, restoration, all-six retention and unchanged assignment, selected-sample no-match results, and access-off disabling. A retained-scroll issue was fixed in preview and native before the final build. TypeScript/Vite, 28 protected-runtime files and four Sites tests passed. The separate preview uses explicit illustrative sample recipes, never actual phone analysis.

**Zero Stage 3C emulator runs. Native fixture verification remains pending, not complete.** Manual `android-gallery-proof.yml` input `stage3c_only=true` is prepared for one approved Android 15/API 35 run on standard public ubuntu-latest. It installs debug only and uses three generated patterned disposable PNGs: exact copy plus changed version. It covers hashing/decoding, all three groups, stable rescans, project preservation, persisted decisions, real UI larger/back/Keep all/rescan/restore, original paths/hashes, denied access and selected-only zero grants. Python/shell syntax, valid fixture decoding and exact/different bytes were checked locally; the Android scripts themselves have not run. No older long monitoring or Stage 3B flow is included in this focused input. Non-empty Android picker selection, large-library behaviour and physical-phone acceptance remain later tests. No signed APK, key access or personal-media changes.

The same owner-private browser preview was published successfully on 30 September at source `19ee6be3d0126f0194daaa948cd2a304d140f843`; deployment `appgdep_6abc7ba2c9708191b14210bf0d6aa892`, version `appgprj_6ab54d19676881919e31be8a6a0db64e~appgver_24573d4f1b24819188dacdc472380762`, unchanged URL https://filemate-design-preview.kellypel1980.chatgpt.site. One incomplete archive upload was rejected before version creation; repackaging and validation succeeded. No emulator or native rebuild was used for publication.

## Comparison actions — 30 September 2026 afternoon
Kel requested missing selection, project assignment and recoverable Trash directly in comparison and rejected further scope reconfirmation. Implementation `fac352f2cc6adca9d4908fe18a6a550fbfc82ee2` passed non-emulator build 36670822345. Final code/proof preparation `40567817e65ef74f95c39abad3689b71c48025ea` passed non-emulator build 36671175439: build, lint and the 45 existing JVM tests; Gradle 2m 23s. These unit tests do not exercise Android Trash or the newly prepared probe; do not claim they do. No signed packaging or emulator dispatch occurred.

Native adds manual per-image selection, confirmed project assignment, reviewed Android createTrashRequest(true), persistent comparison-recovery identities, provider-state verification and confirmed restoration through createTrashRequest(false). Identity/metadata/full-hash checks refuse changed or missing media before assignment/Trash. Project lists/counts now hide known unavailable media rather than leaking it back as an ordinary file; stored assignments remain. No new schema, dependency, permission or physical-folder picker.

Browser checks passed: nothing preselected; selected-only assignment; no save on destination choice; Cancel preserving selection/assignment; reopened destination reset; explicit confirmation and selection clearing; selected-only Trash review and cancellation; separate labelled sample Android prompt; five remaining All Gallery samples after trashing one; Trash showing its saved ShiftMate project; system-step restore cancellation retaining the entry; confirmed restore emptying Trash; and restored comparison availability. TypeScript/Vite, 28-file runtime integrity and four Sites tests passed. Preview state for Trash/project sample actions resets on reload; native persistence is implemented separately and untested at runtime.

The prepared `stage3c_only=true` manual proof now includes changed/missing fingerprint and metadata refusal, recycled-row identity refusal, untrashed restore refusal, unavailable-media project suppression, comparison assignment/cancel, FileMate and Android Trash cancellation, real Trash, process restart while trashed, confirmed recovery, restored original hashes/paths and project metadata, plus older comparison checks and denied/zero-grant selected access. Shell/Python syntax passed. **None of these new Android runtime checks has run.** One explicit approval is required before dispatch, and each retry needs new approval. No current approval exists for Stage 3C/actions.

The same owner-private preview is published from `51fb61b2ec21d36d753b40c09632e2e488bd137c`, saved version `appgprj_6ab54d19676881919e31be8a6a0db64e~appgver_bd3a67cabb24819194c85de193ee2e8b`, deployment `appgdep_6abc97d6bc34819181add8df03d91069`. Existing URL is unchanged. Browser also verified selecting both members and restored comparison/project details after rescanning. No emulator was used for this publication.
