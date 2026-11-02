#!/usr/bin/env bash
# Runs on a booted emulator (started by android-emulator-runner). Installs the APKs built in the same
# workflow run, runs the instrumented tests, and always collects Logcat and failure screenshots into $OUT.
# Exit code: 0 only if the instrumentation output reports "OK (N tests)" with N > 0.
set -u
APP_APK="${1:?app apk}"
TEST_APK="${2:?androidTest apk}"
OUT="${3:-emulator-output}"
PKG=app.magen.lock
mkdir -p "$OUT"

adb logcat -c || true

# Capture the actual emulator display while the UI tests run (maximum 180 seconds).
# The recording is stopped and pulled below, even when instrumentation fails.
adb shell 'screenrecord --time-limit 180 /sdcard/magen-ui-test.mp4 >/dev/null 2>&1 &' || true
adb logcat -v threadtime > "$OUT/logcat-full.txt" 2>&1 &
LOGCAT_PID=$!

status=1
{
  adb install -r -g "$APP_APK" && adb install -r -g "$TEST_APK" \
    && adb shell am instrument -w -e clearPackageData true \
         "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrumentation.txt" 2>&1
} || echo "install or instrument command failed" >> "$OUT/instrumentation.txt"

# `am instrument` exits 0 even when tests fail, so read its result.
if grep -qE '^OK \([1-9][0-9]* tests?\)' "$OUT/instrumentation.txt" \
   && ! grep -qE 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' "$OUT/instrumentation.txt"; then
  status=0
fi

# Stop the emulator-side recording and retrieve it as an MP4 artifact.
adb shell pkill -INT screenrecord 2>/dev/null || true
sleep 2
adb pull /sdcard/magen-ui-test.mp4 "$OUT/ui-test-video.mp4" >/dev/null 2>&1 || true

kill "$LOGCAT_PID" 2>/dev/null || true
adb logcat -d -b crash > "$OUT/logcat-crash.txt" 2>&1 || true
adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null || true
adb pull "/sdcard/Android/data/$PKG/files/failure-screenshots" "$OUT/failure-screenshots" > /dev/null 2>&1 || true

echo "---- instrumentation result ----"
grep -E '^(OK|FAILURES|Tests run)|INSTRUMENTATION_(CODE|FAILED)|test=' "$OUT/instrumentation.txt" | tail -40
echo "exit status: $status"
exit $status
