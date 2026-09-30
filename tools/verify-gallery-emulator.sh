#!/usr/bin/env bash
set -euo pipefail
adb uninstall app.filemate >/dev/null 2>&1 || true
./gradlew installDebug --no-daemon
fixtures="$(mktemp -d)"
trap 'rm -rf "$fixtures"' EXIT
python3 tools/generate-gallery-fixtures.py "$fixtures"
adb shell mkdir -p /sdcard/Pictures/Screenshots /sdcard/DCIM/Camera /sdcard/Download /sdcard/Movies
publish() {
  adb push "$fixtures/$1" "$2" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$2" >/dev/null
}
probe() {
  adb shell am start --user 0 -n app.filemate/.GalleryProbeActivity --es phase "$1" >/dev/null
  for _ in $(seq 1 60); do
    if adb shell run-as app.filemate test -f "files/gallery-$1.json"; then break; fi
    sleep 1
  done
  result="$(adb shell run-as app.filemate cat "files/gallery-$1.json" | tr -d '\r')"
  echo "$result"
  python3 -c 'import json,sys; r=json.loads(sys.argv[1]); assert r.get("passed") is True,r' "$result"
}
if [[ "${FILEMATE_STAGE3C_ONLY:-0}" == "1" ]]; then
  for name in FileMateCompare_v1.png FileMateCompare_v2.png FileMateCompare_copy.png; do
    publish "$name" "/sdcard/Download/$name"
  done
  adb shell pm grant app.filemate android.permission.READ_MEDIA_IMAGES
  probe stage3c
  python3 tools/gallery-stage3c-ui-smoke.py
  adb shell pm revoke app.filemate android.permission.READ_MEDIA_IMAGES
  adb shell pm revoke app.filemate android.permission.READ_MEDIA_VISUAL_USER_SELECTED || true
  probe stage3c-denied
  adb shell pm grant app.filemate android.permission.READ_MEDIA_VISUAL_USER_SELECTED
  probe stage3c-selected
  exit 0
fi
publish Screenshot_FileMateFixture.png /sdcard/Pictures/Screenshots/Screenshot_FileMateFixture.png
publish FileMateFixture_camera.png /sdcard/DCIM/Camera/FileMateFixture_camera.png
publish FileMateFixture_download.png /sdcard/Download/FileMateFixture_download.png
publish FileMateFixture_video.mp4 /sdcard/Movies/FileMateFixture_video.mp4
adb shell pm grant app.filemate android.permission.READ_MEDIA_IMAGES
adb shell pm grant app.filemate android.permission.READ_MEDIA_VIDEO

probe full
adb shell pm revoke app.filemate android.permission.READ_MEDIA_VIDEO
adb shell pm revoke app.filemate android.permission.READ_MEDIA_VISUAL_USER_SELECTED || true
probe images
adb shell pm revoke app.filemate android.permission.READ_MEDIA_IMAGES
probe denied
adb shell pm grant app.filemate android.permission.READ_MEDIA_VISUAL_USER_SELECTED
probe selected
adb shell pm grant app.filemate android.permission.READ_MEDIA_IMAGES
adb shell pm grant app.filemate android.permission.READ_MEDIA_VIDEO
probe restored
publish FileMateFixture_changed.png /sdcard/Download/FileMateFixture_download.png
probe changed
if [[ "${FILEMATE_STAGE3B_ONLY:-0}" != "1" ]]; then
  python3 tools/gallery-ui-smoke.py
fi

# The same approved emulator session now checks the new Stage 3B slice.
publish Screenshot_FileMateFixture.png /sdcard/Pictures/Screenshots/Screenshot_FileMateFixture_second.png
publish Screenshot_FileMateFixture.png /sdcard/Download/Screenshot_FileMateFixture_download.png
probe stage3b
python3 tools/gallery-stage3b-ui-smoke.py
