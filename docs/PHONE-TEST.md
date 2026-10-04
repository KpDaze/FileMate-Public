# FileMate phone checks

The corrected 0.1.1 APK is the valid Test 01 phone build. The original 0.1.0 APK remains rejected because of its Hub launch bug.

The Stage 2 update is `FileMate-0.2.0-Stage2.apk`, version code 3. It is already installed over the existing personal copy. Its backed-up personal signing certificate matches the installed 0.1.1 build. Android may repeat its sideload or Play Protect warning because this remains a private APK.

## Result recorded on 27 September 2026

Kel installed the corrected APK and passed the initial Play Protect warning. The screens tried appeared to work. Kel deliberately stopped during the later multi-step access setup until FileMate is further built and its access is understood.

This is a **partial phone check**. Installation and basic opening passed. Shared download detection, two real installed AI apps, ordinary app switching, catch-up and automatic timeout remain unverified on the physical phone because the required setup was not completed.

## What each setup item is for

- **File access:** lets FileMate observe shared Downloads and Documents. It does not grant access to other apps' private folders.
- **App activity:** optional; lets FileMate recognise recent use of selected AI apps and extend a session during ordinary switching. It does not read screen contents.
- **Notifications:** optional on supported Android versions; shows the quiet monitoring status and Stop action.

## Stage 1 acceptance steps when testing resumes

1. Open FileMate → Setup and review the three access items above.
2. Open AI Hub → Add app. Select at least two AI apps already installed on the phone, including any provider absent from the original preview.
3. Tap one selected AI in the Hub. It should open after monitoring starts. Download a clearly named file to shared Downloads or Documents.
4. Switch to the other selected AI through Android Recent Apps and download a second file without returning to FileMate first.
5. Return to FileMate → Recent. Open each detection and check its source clue, confidence, detection route and unchanged path.
6. During an active session, download an unrelated receipt or APK. It should stay out of Recent.
7. Use manual Stop and confirm the monitoring notification disappears.
8. With monitoring stopped, add a clearly AI-named file to shared Downloads, reopen FileMate and confirm it appears through Catch-up.
9. Reopen FileMate again and confirm the same unchanged file is not duplicated.
10. In a separate session, stay away from selected AI apps for about 30 minutes. Confirm monitoring stops and Activity records the automatic stop.

Tell the builder the failing step and phone model/Android version only if something fails. No terminal or development tools are needed.

Downloads kept inside another app's private storage cannot be detected.

## Stage 2 organiser check

The installed version 3 APK predates the later destination-collision and fingerprint/Undo repairs. These steps are retained for a future update containing the repaired source. Do not use personal or Killerfect Security files; Kel has intentionally deferred broad-access phone testing.


Use ordinary disposable files for this check. Avoid selecting camera photos until the basic flow is familiar.

1. Open Projects, create a temporary project, rename it and confirm it opens.
2. Open Needs Sorting, select one or more detected files and assign them to the project. Assignment should change only FileMate's record; the files should stay in their original folders.
3. Open Clean Up My Phone and start a scan. Review the results. The scan itself must not move, rename or delete anything.
4. Select one disposable result and choose the temporary project folder under `Documents/FileMate`. Keep its current name for the first attempt.
5. Review the exact old and new paths. Exclude anything unexpected, then apply the remaining move.
6. Check the file at its new path. Open Activity and use Undo. Confirm it returns to its original path.
7. Repeat only if wanted with the optional tidy filename. If a destination name already exists, FileMate should show a numbered alternative and preserve the existing file.
8. Delete the temporary project record if no longer needed. Deleting a project must leave its files alone.

If Android asks for broad file access, read the system explanation before granting it. FileMate uses that access for the shared folders listed in Setup; it cannot read another app's private storage. An extra folder is included only when it is deliberately selected through Android's folder picker.

Stage 2 never automatically selects files for a move and has no file deletion action. Stage 3A Gallery is implemented in source but is not in this installed APK. Optional Drive remains future work.

## Stage 3A future phone check (not performed)

No Stage 3 signed phone package has been delivered. When Kel chooses the later signed update, use a few disposable photos, screenshots and a short video. Open Phone → Gallery, choose only those items where Android offers that option, check the filters/details, assign one to a temporary project and refresh. Revoke/reselect photo access and confirm the saved assignment returns. No broad All files access is needed solely to browse Gallery; do not pressure Kel to grant it. Check actual Android selection/reselection and thumbnail behaviour on the recorded phone model/Android version. Gallery itself must not move, rename or delete the media.

## Stage 3B acceptance, when a later phone update is requested
Stage 3B has passing build/JVM/lint/browser and Android fixture evidence. Stage 3B native fixture verification passed in separately approved run 36640809269 on Android 15/API 35: media/access probes, screenshot groups, duplicate-row suppression, cancellation and destination reset, confirmed assignment, intake removal, Refresh persistence, All Gallery retention, confirmed clear and unchanged six original paths/hashes. Three Stage 3B runs were separately approved; the first two exposed test-script issues, and the third passed. Physical-phone acceptance remains pending. With disposable screenshots on two dates/in two folders plus a camera photo, enter Projects → Needs Sorting. Check date/folder groups and camera exclusion; open a group; manually select screenshots; choose a project and Cancel (unchanged), then Confirm (removed from intake, retained in All Gallery/project). Refresh, clear an assignment and delete the temporary project to check intake restoration. Revoke/reselect photo access and check unavailable entries are hidden while assignments survive. Current installed Stage 2 cannot perform this test. Do not package an APK or start an emulator automatically; each emulator run requires Kel’s explicit approval.


## Recovery build acceptance

The recovery source is version 0.4.0-recovery, version code 6. It restores the missing live automatic organiser while preserving the existing move journal and Undo.

Use disposable files only for first acceptance:
1. Create a temporary FileMate project with a distinctive two-word name.
2. During a Hub-started live AI session, download a disposable file whose filename clearly contains both the active AI provider name and the full project name. Expected: FileMate may organise it automatically into Documents/FileMate/<project> and records the move in Activity with Undo.
3. Download a generic disposable filename during the same session. Expected: timing alone does not move it; uncertain evidence stays for review.
4. Download a file naming a different AI provider than the active one. Expected: no High-confidence automatic move.
5. Use Activity → Undo on the automatic move. Expected: the exact fingerprinted file returns to its original location without overwriting another file.
6. Manually assign several disposable similarly named files to a project. A later uncertain matching filename may show a learned project suggestion, but the learned suggestion alone must not move it.
7. Change a project's Future file storage between Phone only and Phone + Drive. Existing files must not move. Until Google OAuth is genuinely configured, no upload or local removal should occur.

Do not use personal camera photos or important work files for first acceptance. Automatic camera organisation remains out of scope.
