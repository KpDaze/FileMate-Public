# FileMate Test 01 — phone check

**Wait for the corrected APK.** The original 0.1.0 package is on hold because runtime testing found a Hub launch bug. These steps apply to the replacement only after it is signed with the current backed-up FileMate identity and explicitly handed over for phone testing. The emulator-only candidate is not a phone release.

This is an early Android test, not the completed organiser. It leaves all your shared files untouched. Android 11 or newer is required.

1. Open the APK and install FileMate. Android may ask you to allow installations from the app you opened it with.
2. Open FileMate → Setup. Allow file access. Allow app activity to test switching and the inactivity timer. Allow notifications to see the quiet monitoring status. If Android disables Usage Access for a sideloaded app, check its App info menu for “Allow restricted settings”, then return to Usage access.
3. Open AI Hub → Add app. Search for and select at least two AI apps already installed on your phone, including any provider not in the original preview. Tap Done.
4. Tap an AI in the Hub. It should open after monitoring starts. Download a clearly named AI file into shared Downloads or Documents.
5. Switch to your other selected AI using Android's normal Recent Apps. Download another file. You do not need to return to FileMate first.
6. Return to FileMate → Recent. Tap a detected file to see the estimated source, confidence, detection route and unchanged path. Activity records that watchers were ready before the launch.
7. While a session is active, download an unrelated file such as `receipt.pdf` or an APK. It should stay out of Recent. Activity's ignored count can increase. This initial classifier is heuristic: generic images near AI activity may still appear as low-confidence suggestions.
8. Leave selected AI apps for 30 minutes. Monitoring should end automatically and its notification disappear. Reopen FileMate and check Activity for the automatic-stop entry. Normal switching back into a selected AI before timeout should extend the session when app activity access is enabled.
9. Stop monitoring, download a file with an obvious AI name (for example `Qwen-catchup.txt`) into shared Downloads, then reopen FileMate. It should appear with “Catch-up” in Recent. A generic name alone is not enough to identify an old file as AI.
10. Close/reopen FileMate again. Previously indexed, unchanged files should not create duplicate entries. Existing files at first setup form a baseline; they are not automatically treated as new AI downloads.

The first useful feedback is: did both apps open, and did their downloads appear? If something fails, tell us which step and your phone model/Android version. No terminal, IDE or code is needed.

Downloads that remain inside another app's private storage cannot be detected. Android or phone-vendor battery restrictions can stop a watcher; catch-up is the safety net. Screenshot detection, project assignment, gallery and Drive are later milestones and are not claimed to work in this APK.
