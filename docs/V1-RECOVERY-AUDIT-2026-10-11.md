# FileMate V1 recovery and phone-test control checklist

Created 2026-10-11. This is an evidence ledger, not a declaration that V1 is complete. **Original V1 specification and 2026-10-04 definitive handover govern scope.** Do not replace either with this summary.

## Verified repository baseline
- Repository: KpDaze/FileMate-Public, main; tree at audit start: eec5833ece45766430cc7f993f721b27052f7833.
- Current repository ROADMAP.md is stale: it still calls Stage 3D runtime proof pending and describes Stage 3A as current, while later evidence and the user's phone tests exist. Treat it as historical, not authoritative.
- Source inspected: Rules.kt, MonitorService.kt, docs/ROADMAP.md, docs/BEHAVIOUR.md.
- Rules.kt: named provider + matching recent app gives High **source** confidence; filename alone Medium; recent-app timing alone Low. There is no learned-pattern input to this classifier.
- MonitorService.kt: monitors selected-app activity and watches shared folders while a user-started session is running; stops after 30 minutes of inactivity. No evidence here of automatic session restart after expiry.
- The inspected source does not establish whether the later October 4 auto-organiser is implemented elsewhere. **Do not mark absent without inspecting all other current files and later checkpoint artifacts.**
- No emulator, APK packaging, or paid service was started during this audit.

## User physical-phone findings: OPEN, not fixed
| ID | Finding | Category | Acceptance evidence needed |
|---|---|---|---|
| F01 | Likely AI files have no usable content preview | UI | Preview image/doc details before selecting |
| F02 | High-confidence items in Recent do not automatically organise | Functionality / integration | Safe unique project+source evidence triggers reviewed policy; Activity and Undo work |
| F03 | AI attribution is mostly Low when provider absent from filename | Classifier | Active app, timing, filename, type, folder, learned evidence combined without false certainty |
| F04 | Existing downloads and user corrections do not appear to seed learning | V1 requirement | Local pattern learning and correction test |
| F05 | Gallery/screenshots provide no likely project suggestion | V1 requirement | Suggestions with uncertainty and no unsafe camera-photo automation |
| F06 | Comparison Trash forces full library rescan | UX / performance | Refresh affected group without unnecessary full scan, preserving correctness |
| F07 | Activity repeats 'Catch-up complete' and hundreds of likely AI files are not recognised | Logging / classification | Deduplicated meaningful entries, catch-up verified separately from AI attribution |
| F08 | Allow notifications appears ineffective | Phone bug report | Permission/UI state actually changes or explains Android restriction |
| F09 | Choose access appears ineffective and unclear | Phone bug report | Correct picker/permission state and useful explanation |
| F10 | Suggest tidy names has no visible result | Functionality / discoverability | Name preview in appropriate move flow, unchanged default |
| F11 | Gallery selection creates large gap and hides destination controls | UI | Controls remain discoverable on phone screen |
| F12 | Storage choices off-screen and 'selected storage after verified copy' unclear | UI | All options readable; explain copy then verified removal |
| F13 | 30-minute timeout misses resumed AI use without new Hub launch | Monitoring | Define safe resumed-activity behaviour, test physical device |
| F14 | ZIP files grouped as Archives | Expected classification / UX decision | Keep ZIP as archive; no automatic move based on extension alone |
| F15 | Drive not yet accepted/tested | Pending V1 scope | Verify real provider integration and user-approved Drive requirements separately |

## Original V1 acceptance requirements not to lose
- AI Hub, event-based live watcher, app switching, inactivity policy, catch-up and unrelated-file exclusion.
- Separate source/project confidence; high-confidence safe organisation, medium suggestion, low Needs Sorting; local learning from corrections; projects remain user-created.
- File previews, readable naming, explicit bulk review, protected moves/Undo, recoverable Trash; no silent deletion or overwrites.
- Local Gallery, screenshots, exact duplicates vs similar versions, favourites/albums, reviewed move/rename/trash; conservative camera photos.
- Optional Drive with same project structure, browse/search/create/move/rename/upload; no local deletion until remote copy verified.
- No paid backend, 24/7 polling, or emulator without specific approval.

## Next controlled work (not yet completed)
1. Read complete original V1 handover and definitive 2026-10-04 handover alongside all current production source, tests and Git history. Reconcile apparent later automatic-organiser and storage changes, including which repository/commit actually produced the phone APK.
2. Convert each row above to a source-linked finding and test. Distinguish a missing feature from a broken existing feature. Prioritise F02/F03/F04/F07 and phone UI failures.
3. Fix in batches with static/unit/lint verification first; no unapproved emulator. Phone acceptance uses disposable files.
4. Record tested commit, APK SHA-256, signer scheme/certificate, and user phone outcomes before closing any row.
