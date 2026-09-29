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

## Stage 3B metadata grouping and screenshot intake — 29 September 2026 Brisbane

Native implementation: `e26614ae7e996bf97a5bef0b8556defd58f3cc6e`. Version name `0.3.0-stage3b`, version code 4, database schema 5 unchanged. Screenshot groups use local date, folder and volume, excluding camera-folder items and videos. Unknown folder/date entries stay separate. Needs Sorting derives unassigned accessible groups from the current media index, suppresses duplicate other-file rows and opens the existing manual selection/confirmed assignment flow. No OCR, new dependency, network permission, file operation, database migration or phone package.

- [Build run 36535657172](https://github.com/KpDaze/FileMate-Public/actions/runs/36535657172) passed `testDebugUnitTest lintDebug assembleDebug`. The suite now contains 37 tests, including 8 new grouping/intake rule tests. Gradle reported BUILD SUCCESSFUL in 3m 3s. Manual phone packaging/upload steps were skipped.
- New tests cover same-day/folder grouping; assigned/unavailable/camera/video/weak-clue exclusions; separate folders, storage volumes and dates; Brisbane midnight boundaries; missing metadata isolation; repeated-snapshot identity deduplication; clear-assignment eligibility; and stable group IDs.
- All three emulator workflows are now `workflow_dispatch` only, with the per-run approval rule in AGENTS.md. The source commit produced exactly one Actions run: the non-emulator build above. Zero emulator runs were requested or started for this stage.
- Browser QA used the existing Pixel 10 simulation. Needs Sorting initially showed two screenshot groups and two other files. Opening one group showed one screenshot; Show all showed two. Manual selection and Cancel left both unassigned; reopening reset the project selection. Confirming both to ShiftMate removed them from intake, while All Gallery kept six and displayed both project labels. Confirmed clearing restored one screenshot group. Access off hid screenshot intake without altering the other-file list. No application error was observed; sampled console errors came from browser-extension metadata reporting.
- The preview passed TypeScript/Vite build, the protected 28-file mobile-runtime integrity check and four Sites checks. Same owner-private Site version 9 published successfully, source `a670d4944d80d37920af795af688688844e98671`, unchanged URL https://filemate-design-preview.kellypel1980.chatgpt.site.

Boundary: Stage 3B has unit/build/lint and browser evidence, not new native Android runtime or physical-phone acceptance. Previous Stage 3A Android evidence remains historical evidence for reused indexing/assignment code, not proof that these new screens were exercised on Android. No emulator run is needed merely to obtain screenshots. Physical acceptance can be batched into a later requested signed update with disposable screenshots, camera media and limited/revoked access; no signed APK was created. Next native development slice is Stage 3C, after Kel asks to continue. OCR remains optional and unadded; Stage 3D, optional Drive and full Version 1 acceptance remain in scope.
