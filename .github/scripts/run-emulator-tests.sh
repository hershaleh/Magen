#!/usr/bin/env bash
# Run the complete UI suite first, then record only the dedicated visual walkthrough.
set -u
APP_APK="${1:?app apk}"
TEST_APK="${2:?androidTest apk}"
OUT="${3:-emulator-output}"
PKG=app.magen.lock
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
mkdir -p "$OUT"
adb logcat -c || true
adb logcat -v threadtime > "$OUT/logcat-full.txt" 2>&1 &
LOGCAT_PID=$!
status=1

# Install before recording, so launcher/install noise cannot pollute the video.
if ! adb install -r -g "$APP_APK" || ! adb install -r -g "$TEST_APK"; then
  echo "APK installation failed" > "$OUT/instrumentation.txt"
  status=1
else
  # First run every test without recording.
  adb shell am instrument -w -e clearPackageData true "$RUNNER" > "$OUT/instrumentation.txt" 2>&1 || true
  if grep -qE '^OK \([1-9][0-9]* tests?\)' "$OUT/instrumentation.txt" \
     && ! grep -qE 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' "$OUT/instrumentation.txt"; then
    status=0
  fi

  # Then run just the visual walkthrough while recording, on a clean app state.
  VIDEO_REMOTE=/sdcard/magen-ui-walkthrough.mp4
  VIDEO_LOG=/data/local/tmp/magen-screenrecord.log
  adb shell rm -f "$VIDEO_REMOTE" "$VIDEO_LOG"
  VIDEO_PID="$(adb shell "screenrecord --time-limit 90 --bit-rate 4000000 $VIDEO_REMOTE >$VIDEO_LOG 2>&1 & echo \$!")"
  VIDEO_PID="$(echo "$VIDEO_PID" | tr -d '\\r')"
  sleep 2
  adb shell am instrument -w -e class app.magen.lock.MagenUiTest#recordedWalkthroughShowsEveryMainArea "$RUNNER" > "$OUT/walkthrough-instrumentation.txt" 2>&1 || true
  if [ -n "$VIDEO_PID" ]; then adb shell "kill -2 $VIDEO_PID" 2>/dev/null || true; fi
  for attempt in 1 2 3 4 5; do
    sleep 2
    if adb shell "test -s $VIDEO_REMOTE"; then break; fi
  done
  adb pull "$VIDEO_LOG" "$OUT/screenrecord.log" >/dev/null 2>&1 || true
  if ! adb pull "$VIDEO_REMOTE" "$OUT/ui-test-video.mp4" >/dev/null 2>&1 \
     || ! test -s "$OUT/ui-test-video.mp4" \
     || ! dd if="$OUT/ui-test-video.mp4" bs=1 skip=4 count=4 2>/dev/null | grep -q 'ftyp'; then
    echo "Walkthrough video missing or invalid" >&2
    status=1
  fi
  if ! grep -q 'OK (1 test)' "$OUT/walkthrough-instrumentation.txt"; then
    echo "Visual walkthrough test failed" >&2
    status=1
  fi
fi

kill "$LOGCAT_PID" 2>/dev/null || true
adb logcat -d -b crash > "$OUT/logcat-crash.txt" 2>&1 || true
adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null || true
adb pull "/sdcard/Android/data/$PKG/files/failure-screenshots" "$OUT/failure-screenshots" > /dev/null 2>&1 || true
adb pull "/sdcard/Android/data/$PKG/files/walkthrough-screenshots" "$OUT/walkthrough-screenshots" > /dev/null 2>&1 || true
echo "---- full instrumentation ----"
grep -E '^(OK|FAILURES|Tests run)|INSTRUMENTATION_(CODE|FAILED)|test=' "$OUT/instrumentation.txt" | tail -40 || true
echo "---- walkthrough instrumentation ----"
cat "$OUT/walkthrough-instrumentation.txt" 2>/dev/null || true
echo "exit status: $status"
exit "$status"
