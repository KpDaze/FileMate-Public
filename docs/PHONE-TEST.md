# FileMate corrected proof — phone check

The corrected 0.1.1 APK is the valid Test 01 phone build. The original 0.1.0 APK remains rejected because of its Hub launch bug.

## Result recorded on 27 September 2026

Kel installed the corrected APK and passed the initial Play Protect warning. The screens tried appeared to work. Kel deliberately stopped during the later multi-step access setup until FileMate is further built and its access is understood.

This is a **partial phone check**. Installation and basic opening passed. Shared download detection, two real installed AI apps, ordinary app switching, catch-up and automatic timeout remain unverified on the physical phone because the required setup was not completed.

## What each setup item is for

- **File access:** lets FileMate observe shared Downloads and Documents. It does not grant access to other apps' private folders.
- **App activity:** optional; lets FileMate recognise recent use of selected AI apps and extend a session during ordinary switching. It does not read screen contents.
- **Notifications:** optional on supported Android versions; shows the quiet monitoring status and Stop action.

## Full acceptance steps when testing resumes

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

Downloads kept inside another app's private storage cannot be detected. Stage 2A project records do not move files. Needs Sorting, batch assignment, cleanup actions, screenshots, gallery and Drive belong to later checkpoints.
