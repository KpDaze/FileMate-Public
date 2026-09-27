#!/usr/bin/env bash
set -euo pipefail

adb uninstall app.filemate >/dev/null 2>&1 || true
./gradlew installDebug --no-daemon
adb shell appops set app.filemate MANAGE_EXTERNAL_STORAGE allow
adb shell am start --user 0 -n app.filemate/.Stage2ProbeActivity >/dev/null

for _ in $(seq 1 120); do
  if adb shell run-as app.filemate test -f files/stage2-probe.json; then
    break
  fi
  sleep 1
done

result="$(adb shell run-as app.filemate cat files/stage2-probe.json | tr -d '\r')"
echo "$result"
python3 -c 'import json,sys; result=json.loads(sys.argv[1]); assert result.get("passed") is True, result' "$result"
