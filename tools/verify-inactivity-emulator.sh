#!/usr/bin/env bash
set -euo pipefail
# Uses a fresh emulator and the repository build. Does not touch a personal phone or files.
apk="app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$apk"
adb shell appops set app.filemate MANAGE_EXTERNAL_STORAGE allow
adb shell appops set app.filemate GET_USAGE_STATS allow
adb shell am start -n app.filemate/.MainActivity
sleep 8
# The debug app owns this non-exported service. Run the command with its UID, as FileMate does.
adb shell run-as app.filemate am start-foreground-service --user 0 -n app.filemate/.MonitorService --es request ci-inactivity --es package app.filemate.test.qwen --es label Qwen
sleep 25

read_state() {
  local db_dir
  db_dir="$(mktemp -d)"
  adb exec-out run-as app.filemate cat databases/filemate.db > "$db_dir/filemate.db"
  adb exec-out run-as app.filemate cat databases/filemate.db-wal > "$db_dir/filemate.db-wal" 2>/dev/null || true
  python3 - "$db_dir/filemate.db" <<'PY'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
try:
    row = db.execute("SELECT value FROM state WHERE key='session_active'").fetchone()
    print(row[0] if row else "missing")
finally:
    db.close()
PY
  rm -rf "$db_dir"
}
initial="$(read_state)"
echo "Initial session_active=$initial"
if [[ "$initial" != "true" ]]; then
  echo "FAIL: monitoring did not start"
  adb logcat -d -s AndroidRuntime:E ActivityManager:E | tail -150
  exit 1
fi
started="$(date +%s)"
# A real elapsed interval: no altered clock, shortened production timer or repeated file scans.
while (( $(date +%s) - started < 29 * 60 )); do sleep 30; done
before="$(read_state)"
echo "After at least 29 minutes, session_active=$before"
if [[ "$before" != "true" ]]; then
  echo "FAIL: monitoring ended before the production 30-minute inactivity boundary"
  exit 1
fi
while (( $(date +%s) - started < 32 * 60 )); do
  after="$(read_state)"
  if [[ "$after" == "false" ]]; then
    echo "PASS: monitoring ended automatically after $(( $(date +%s) - started )) seconds."
    break
  fi
  sleep 15
done
after="$(read_state)"
if [[ "$after" != "false" ]]; then
  echo "FAIL: monitoring was still active after 32 minutes"
  exit 1
fi
adb exec-out run-as app.filemate cat databases/filemate.db > /tmp/filemate-proof.db
adb exec-out run-as app.filemate cat databases/filemate.db-wal > /tmp/filemate-proof.db-wal 2>/dev/null || true
python3 - <<'PY'
import sqlite3
db = sqlite3.connect('/tmp/filemate-proof.db')
rows = db.execute("SELECT title, detail FROM history ORDER BY id DESC LIMIT 5").fetchall()
for title, detail in rows:
    print(title + ": " + detail)
assert any(title == 'Monitoring ended automatically' for title, _ in rows), rows
PY
if adb shell dumpsys notification --noredact | grep -F 'FileMate is watching for AI downloads'; then
  echo "FAIL: monitoring notification is still present"
  exit 1
fi
echo "PASS: automatic stop, history and notification removal verified"
