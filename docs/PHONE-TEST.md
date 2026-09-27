# FileMate corrected proof — phone check

The corrected 0.1.1 APK is the valid Test 01 phone build. The original 0.1.0 APK remains rejected because of its Hub launch bug.

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

Stage 2 never automatically selects files for a move and has no file deletion action. Screenshots, the gallery and Drive belong to later checkpoints.
