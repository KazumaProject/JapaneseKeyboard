#!/usr/bin/env bash

set -euo pipefail

fast_input_rounds="${FAST_INPUT_ROUNDS:-1}"
fast_input_capture_visuals="${FAST_INPUT_CAPTURE_VISUALS:-true}"
fast_input_start_case="${FAST_INPUT_START_CASE:-1}"
fast_input_end_case="${FAST_INPUT_END_CASE:-144}"
fast_input_artifact_dir="${GITHUB_WORKSPACE:-.}/fast-input-artifacts"
fast_input_log_dir="$fast_input_artifact_dir/logs"
fast_input_device_dir="$fast_input_artifact_dir/device"
fast_input_device_output="/sdcard/Android/data/com.kazumaproject.markdownhelperkeyboard/files/fast-input"

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

if ! is_positive_integer "$fast_input_rounds" || (( fast_input_rounds > 10 )); then
  echo "FAST_INPUT_ROUNDS must be an integer from 1 to 10."
  exit 2
fi

if ! is_positive_integer "$fast_input_start_case" ||
  ! is_positive_integer "$fast_input_end_case" ||
  (( fast_input_start_case > fast_input_end_case )) ||
  (( fast_input_end_case > 144 )); then
  echo "FAST_INPUT_START_CASE and FAST_INPUT_END_CASE must define a range within 1-144."
  exit 2
fi

if [[ "$fast_input_capture_visuals" != "true" &&
  "$fast_input_capture_visuals" != "false" ]]; then
  echo "FAST_INPUT_CAPTURE_VISUALS must be true or false."
  exit 2
fi

mkdir -p "$fast_input_log_dir" "$fast_input_device_dir"

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put secure show_ime_with_hard_keyboard 1
adb logcat -c

fast_input_test_method=\
"com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest#rapidInputFullMatrixOnPhysicalDevice"

set +e
./gradlew \
  :app:connectedFullStandardDebugAndroidTest \
  --stacktrace \
  --no-daemon \
  --max-workers=2 \
  "-Pandroid.testInstrumentationRunnerArguments.class=$fast_input_test_method" \
  "-Pandroid.testInstrumentationRunnerArguments.startCase=$fast_input_start_case" \
  "-Pandroid.testInstrumentationRunnerArguments.endCase=$fast_input_end_case" \
  "-Pandroid.testInstrumentationRunnerArguments.matrixRounds=$fast_input_rounds" \
  "-Pandroid.testInstrumentationRunnerArguments.captureVisuals=$fast_input_capture_visuals" \
  2>&1 | tee "$fast_input_log_dir/gradle-connected-android-test.log"
fast_input_gradle_status=${PIPESTATUS[0]}
set -e

adb logcat -d -v threadtime > "$fast_input_log_dir/device-logcat.txt" || true

if adb shell test -d "$fast_input_device_output"; then
  adb pull "$fast_input_device_output" "$fast_input_device_dir/" ||
    echo "Unable to pull fast-input screenshots from the emulator."
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## Fast Input Regression"
    echo
    echo "- Cases: $fast_input_start_case-$fast_input_end_case"
    echo "- Rounds: $fast_input_rounds"
    echo "- Capture visuals: $fast_input_capture_visuals"
    echo
    echo '```text'
    grep "FAST_INPUT_SUMMARY" \
      "$fast_input_log_dir/gradle-connected-android-test.log" \
      "$fast_input_log_dir/device-logcat.txt" |
      tail -n 1 || echo "FAST_INPUT_SUMMARY was not emitted."
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

exit "$fast_input_gradle_status"
