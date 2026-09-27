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
publish Screenshot_FileMateFixture.png /sdcard/Pictures/Screenshots/Screenshot_FileMateFixture.png
publish FileMateFixture_camera.png /sdcard/DCIM/Camera/FileMateFixture_camera.png
publish FileMateFixture_download.png /sdcard/Download/FileMateFixture_download.png
publish FileMateFixture_video.mp4 /sdcard/Movies/FileMateFixture_video.mp4
adb shell pm grant app.filemate android.permission.READ_MEDIA_IMAGES
adb shell pm grant app.filemate android.permission.READ_MEDIA_VIDEO
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
