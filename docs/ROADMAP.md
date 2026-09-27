# Completion roadmap

1. **Test 01: native Android proof — source proof complete, phone acceptance partial.** Hub, launch ordering, event-based shared download detection, local record, unrelated-file exclusion, ordinary app switching, session continuity, inactivity stop and reopen catch-up have emulator evidence. Installation/basic opening passed on the phone; real provider downloads and broad-access behaviour remain pending.
2. **Organiser — source complete, phone acceptance pending.** Local metadata, manual projects, source/project confidence separately, Recent / Projects / Needs Sorting, single and batch assignment, review-first cleanup, useful naming, safe moves, Activity and Undo are implemented and covered by a bounded Android 15 proof.
3. **Gallery and screenshots.** Detect/index screenshots, on-device text clues, local chronological gallery, project grouping, exact hashes, visual similarity suggestions, duplicate/version review and recoverable bulk actions. Personal camera photos stay conservative.
4. **Optional Drive and Version 1 completion.** Account connection, browse/search/folders/move/rename, selected uploads, same project structure, per-project copy rules, verified-upload-before-local-removal, review of existing Drive files and practical duplicates. Full end-to-end acceptance against the original handover.

Each milestone needs a recoverable Git commit, reproducible build, the same preserved signing identity for updates, and a phone-installable APK. Tests are evidence, not a substitute for verifying real Android file access and app switching.

The full handover's Version 1 remains the target. Deferred work above is not removed from scope. Not required: Google Play, web/iPhone app, multiuser, paid backend/APIs, private app data, 24/7 polling, home-screen replacement, icon drag/drop, face/people/pet recognition, automatic camera organisation, memories or a Google Photos cloud replacement.
