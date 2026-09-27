# Verification status — corrected runtime checks

The original Test 01 APK (0.1.0, version code 1) is **on hold**. Android 15 runtime testing on 25 September 2026 reproduced a Hub launch failure after monitoring started. Do not treat the original APK as an accepted device-tested build.

The corrected 0.1.1 build has passed both Hub launches, background live detection, ordinary app switching, receipt exclusion, low-confidence handling, manual stop with notification removal, catch-up and repeated-open record deduplication on Android 15. Its eight JVM tests passed. The full 30-minute inactivity test was interrupted after 728 seconds when the execution/emulator control sessions became unavailable; its result is inconclusive. The timer boundary logic passed its JVM test. See [the exact runtime evidence and limits](RUNTIME-VERIFICATION.md).

The earlier corrected emulator candidate used a disposable fixture signing key. Kel subsequently confirmed no FileMate APK had been installed, so a fresh first-install signing identity was generated, backed up and applied to the same compiled payload. Its certificate and recovery references are recorded in `signing-identity-2026-09-26.json`. The unsigned compiled payload has been saved as `FileMate-0.1.1-Build-Checkpoint.zip` so delivery does not depend on rebuilding. This checkpoint is not installable.

# Test 01 verification

The earlier build on 25 September 2026 compiled and passed all eight JVM logic tests. Its signed APK verified and its packaged entries matched the Gradle output. Android lint found zero errors, with a backup-configuration warning addressed in the current source.

Automatic workspace maintenance subsequently removed the temporary APK and signing key before their attempted durable save succeeded. The source checkpoint at commit 7779097570d232caf13330d26825e3f7375fb19e remained safe in GitHub. The source was restored and the final changes reapplied. No APK from the removed signing identity was delivered or installed; a new identity is used for the first delivered package. The replacement APK and its private signing backup have now both been saved successfully.

## Emulator failure

An Android 15 emulator was attempted without hardware acceleration. Its Android startup never reached a confirmed completed boot. The package installer then failed in Android's own StorageManagerService: a PackageManagerInternal reference was null. This happened before FileMate was installed, so it was not a FileMate application crash. The exact cause of the emulator's failed startup was not established. Lack of hardware acceleration is a limitation of this environment, not a proven cause of that exception.

The emulator result does not count as a passed installation, UI or monitoring test. The emulator was stopped.

## Phone acceptance still required

PHONE-TEST.md covers installation, permissions, choosing two real installed AI apps, watcher readiness before launch, shared download detection, ignoring unrelated downloads, switching through Android Recent Apps, session survival, 30-minute inactivity stop and catch-up after interruption. None is claimed to be proven on a physical phone yet.

Test 01 leaves all shared files untouched. It is the first technical proof, not the finished Version 1. Project organisation, screenshots/OCR/duplicates, gallery, cleanup and optional Drive remain on the full roadmap.

## Delivered Test 01 result

Fresh rebuild: successful. Eight JVM tests passed, none failed. Android lint: zero errors, one advisory that a newer core-ktx is available; the pinned dependency was retained. Final APK signature and 16 KB ZIP alignment verified. Every packaged entry matches the Gradle output byte for byte. The merged manifest contains no INTERNET permission. See test-01-artifact.json for the exact package hash and signing-certificate fingerprint.

This was the first delivered APK, about 8 MB. It is now superseded by the runtime failure noted above; do not install it for acceptance testing. No APK signed by the removed preliminary key was delivered.
