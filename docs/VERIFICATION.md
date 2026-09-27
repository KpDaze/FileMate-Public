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

## Stage 2 safeguard repair — 27 September 2026, verification pending

Kel authorised corrections enforcing the handover's existing no-overwrite and changed-file/Undo requirements before Gallery work. Source now uses exclusive destination creation (`CREATE_NEW`) instead of an atomic rename that can replace a late-arriving file. Moves copy, flush and verify content before removing the source. This requires temporary free space for the new copy. Copy/check failures retain the source and any partial copy for review; recovery does not automatically remove either copy.

Every supported move preview now computes SHA-256, including unique files that the duplicate scan did not hash. Apply, Undo and interrupted-operation recovery check the recorded content. Older actions lacking a hash are refused for Undo instead of guessing a baseline. Ambiguous actions remain in review. The existing schema version 4 and historical records are preserved.

Ten new JVM filesystem tests and expanded disposable Android fixtures cover destination collisions, same-size edits, legacy records, interrupted copies and recovery. Build/lint/JVM/emulator verification is pending for this repair; previous Stage 2 results do not prove these new cases. No new phone APK has been delivered, and Stage 3A remains unstarted.

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
