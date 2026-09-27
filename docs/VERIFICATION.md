# Verification status

## Corrected Test 01 proof

The original 0.1.0 APK is rejected. Android 15 runtime testing on 25 September 2026 reproduced a Hub launch failure after monitoring started.

The corrected 0.1.1 build passed two fixture launches, live background detection, ordinary app switching, receipt exclusion, low-confidence handling, manual stop, notification removal, catch-up and repeated-open deduplication on Android 15. The fixture run used synthetic local Qwen and ChatGPT exporters, not real provider accounts.

[Public GitHub run 36285379397](https://github.com/KpDaze/FileMate-Public/actions/runs/36285379397) completed successfully on 27 September 2026. It used a standard public `ubuntu-latest` runner, KVM and Android 15/API 35. The run built the app, ran the JVM logic tests, installed FileMate, started its private monitoring service under the app identity, confirmed the session remained active at the at-least-29-minute check, then confirmed automatic stop, its Activity record and notification removal. The reported 1785 seconds starts after a 25-second setup delay; actual service life crossed the intended approximately 30-minute boundary. The production timeout and device clock were unchanged.

The public run did not select two real AI apps, use provider accounts, test real provider downloads, or prove physical-phone permission and battery behaviour. The earlier fixture run covers normal switching and live exports separately.

## Physical phone

Kel installed the corrected 0.1.1 APK on 27 September 2026. Installation and the screens tried appeared to work. Kel left the later broad-access setup incomplete. The physical-phone result is therefore partial: installation/basic opening passed; shared file watching, real AI apps, switching, catch-up and the inactivity stop remain pending.

## Stage 2A

Stage 2A adds local manual projects with create, rename, delete and browse screens. Database version 2 uses a non-destructive migration and retains the Hub, indexed files, observations, history and state from version 1. Project deletion leaves phone files untouched and clears their project assignment for future Needs Sorting.

The Stage 2A source uses version `0.2.0-stage2a`, code 3. [Lightweight run 36289780572](https://github.com/KpDaze/FileMate-Public/actions/runs/36289780572) passed the JVM tests, Android lint and debug assembly on 27 September 2026. This source checkpoint is not yet a phone acceptance result or a signed personal update.

## Artifact and signing boundary

The corrected personal 0.1.1 APK has SHA-256 `e64e30bf6130ad338a421f3c387e0cdb771f41ce43f7ba1b5ccd0d72023fd9c1`. Its signature, certificate, 16 KB ZIP alignment and packaged entries were verified. Private signing and recovery records remain outside this public repository. Future phone APKs must use the same backed-up personal identity to update the installed copy.

`docs/test-01-artifact.json` describes the rejected 0.1.0 artifact and must not be treated as the corrected release record.

## Historical failures retained as evidence

- Two early public workflow attempts failed in their harness before inactivity timing began: run `36284780316` tried to start a non-exported service as the shell; run `36285111279` omitted an explicit Android user while using FileMate's debug UID.
- An earlier private run, `36227791658`, never received a runner because the private Actions allowance/billing blocked job startup.
- Older software-only workspace emulators repeatedly failed during Android startup before FileMate installation. Those are environment failures, not FileMate timeout failures.

See [runtime verification](RUNTIME-VERIFICATION.md) for the detailed chronology and its evidence limits.
