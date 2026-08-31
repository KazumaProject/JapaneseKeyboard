#!/usr/bin/env bash

set -euo pipefail

keyboard_size_rounds="${KEYBOARD_SIZE_ROUNDS:-1}"
keyboard_size_capture_visuals="${KEYBOARD_SIZE_CAPTURE_VISUALS:-true}"
keyboard_size_device_class="${KEYBOARD_SIZE_DEVICE_CLASS:-}"
keyboard_size_artifact_dir="${GITHUB_WORKSPACE:-.}/keyboard-size-artifacts/$keyboard_size_device_class"
keyboard_size_log_dir="$keyboard_size_artifact_dir/logs"
keyboard_size_device_dir="$keyboard_size_artifact_dir/device"
keyboard_size_device_output="/sdcard/Android/data/com.kazumaproject.markdownhelperkeyboard/files/fast-input"

if [[ "$keyboard_size_rounds" != "1" &&
  "$keyboard_size_rounds" != "3" &&
  "$keyboard_size_rounds" != "10" ]]; then
  echo "KEYBOARD_SIZE_ROUNDS must be 1, 3, or 10."
  exit 2
fi
if [[ "$keyboard_size_capture_visuals" != "true" &&
  "$keyboard_size_capture_visuals" != "false" ]]; then
  echo "KEYBOARD_SIZE_CAPTURE_VISUALS must be true or false."
  exit 2
fi
if [[ "$keyboard_size_device_class" != "phone" &&
  "$keyboard_size_device_class" != "tablet" ]]; then
  echo "KEYBOARD_SIZE_DEVICE_CLASS must be phone or tablet."
  exit 2
fi

mkdir -p "$keyboard_size_log_dir" "$keyboard_size_device_dir"
export IME_EMULATOR_LOG_DIR="$keyboard_size_log_dir"
export IME_EMULATOR_READINESS_LOG="$keyboard_size_log_dir/android-readiness.log"
source "${BASH_SOURCE[0]%/*}/ime-emulator-common.sh"

if ! ime_emulator_prepare; then
  ime_emulator_capture_diagnostics "emulator-readiness-failure"
  exit 3
fi

keyboard_size_test_method=\
"com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest#keyboardSizeFullMatrixOnEmulators"

set +e
./gradlew \
  :app:connectedFullStandardDebugAndroidTest \
  --stacktrace \
  --no-daemon \
  --max-workers=2 \
  "-Pandroid.testInstrumentationRunnerArguments.class=$keyboard_size_test_method" \
  "-Pandroid.testInstrumentationRunnerArguments.matrixRounds=$keyboard_size_rounds" \
  "-Pandroid.testInstrumentationRunnerArguments.captureVisuals=$keyboard_size_capture_visuals" \
  "-Pandroid.testInstrumentationRunnerArguments.expectedDeviceClass=$keyboard_size_device_class" \
  2>&1 | tee "$keyboard_size_log_dir/gradle-connected-android-test.log"
keyboard_size_gradle_status=${PIPESTATUS[0]}
set -e

if ((keyboard_size_gradle_status != 0)); then
  ime_emulator_capture_diagnostics "gradle-failure"
fi
ime_emulator_capture_diagnostics "final-device-state"
adb -s "$IME_EMULATOR_SERIAL" logcat -d -v threadtime \
  > "$keyboard_size_log_dir/device-logcat.txt" || true

if adb -s "$IME_EMULATOR_SERIAL" shell test -d "$keyboard_size_device_output"; then
  adb -s "$IME_EMULATOR_SERIAL" pull \
    "$keyboard_size_device_output" "$keyboard_size_device_dir/" ||
    echo "Unable to pull keyboard-size screenshots from the emulator."
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  keyboard_size_summary_line="$(
    grep -h "KEYBOARD_SIZE_SUMMARY" \
      "$keyboard_size_log_dir/gradle-connected-android-test.log" \
      "$keyboard_size_log_dir/device-logcat.txt" 2>/dev/null |
      tail -n 1 || true
  )"
  keyboard_size_failure_excerpt="$(
    grep -h -E \
      'KEYBOARD_SIZE_FAILURE|SetupException|AssertionError|FAILURE: Build failed|There were failing tests' \
      "$keyboard_size_log_dir/gradle-connected-android-test.log" \
      "$keyboard_size_log_dir/device-logcat.txt" 2>/dev/null |
      head -n 20 |
      cut -c 1-1200 || true
  )"
  {
    echo "## Keyboard Size Regression — $keyboard_size_device_class"
    echo
    echo "- Rounds: $keyboard_size_rounds"
    echo "- Capture visuals: $keyboard_size_capture_visuals"
    echo
    echo '```text'
    if [[ -n "$keyboard_size_summary_line" ]]; then
      echo "$keyboard_size_summary_line"
    else
      echo "KEYBOARD_SIZE_SUMMARY was not emitted."
    fi
    if [[ -n "$keyboard_size_failure_excerpt" ]]; then
      echo
      echo "Failure excerpt:"
      echo "$keyboard_size_failure_excerpt"
    fi
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

exit "$keyboard_size_gradle_status"
