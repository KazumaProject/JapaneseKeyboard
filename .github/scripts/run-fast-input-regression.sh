#!/usr/bin/env bash

set -euo pipefail

fast_input_rounds="${FAST_INPUT_ROUNDS:-1}"
fast_input_capture_visuals="${FAST_INPUT_CAPTURE_VISUALS:-true}"
fast_input_start_case="${FAST_INPUT_START_CASE:-1}"
fast_input_end_case="${FAST_INPUT_END_CASE:-144}"
fast_input_test_scope="${FAST_INPUT_TEST_SCOPE:-all}"
fast_input_generated_surfaces="${FAST_INPUT_GENERATED_SURFACES:-ALL}"
fast_input_generated_columns="${FAST_INPUT_GENERATED_COLUMNS:-1,2,3}"
fast_input_artifact_dir="${GITHUB_WORKSPACE:-.}/fast-input-artifacts"
fast_input_log_dir="$fast_input_artifact_dir/logs"
fast_input_device_dir="$fast_input_artifact_dir/device"
fast_input_device_output="/sdcard/Android/data/com.kazumaproject.markdownhelperkeyboard/files/fast-input"

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

if ! is_positive_integer "$fast_input_rounds" || ((fast_input_rounds > 10)); then
  echo "FAST_INPUT_ROUNDS must be an integer from 1 to 10."
  exit 2
fi
if ! is_positive_integer "$fast_input_start_case" ||
  ! is_positive_integer "$fast_input_end_case" ||
  ((fast_input_start_case > fast_input_end_case)) ||
  ((fast_input_end_case > 144)); then
  echo "FAST_INPUT_START_CASE and FAST_INPUT_END_CASE must define a range within 1-144."
  exit 2
fi
if [[ "$fast_input_capture_visuals" != "true" &&
  "$fast_input_capture_visuals" != "false" ]]; then
  echo "FAST_INPUT_CAPTURE_VISUALS must be true or false."
  exit 2
fi
if [[ "$fast_input_test_scope" != "all" &&
  "$fast_input_test_scope" != "generated" ]]; then
  echo "FAST_INPUT_TEST_SCOPE must be all or generated."
  exit 2
fi
surface_pattern='(TENKEY|GOJUON|SUMIRE|QWERTY|ROMAJI|CUSTOM)'
if [[ ! "$fast_input_generated_surfaces" =~ ^(ALL|${surface_pattern}(,${surface_pattern})*)$ ]]; then
  echo "FAST_INPUT_GENERATED_SURFACES must be ALL or a comma-separated surface list."
  exit 2
fi
if [[ ! "$fast_input_generated_columns" =~ ^[123](,[123])*$ ]]; then
  echo "FAST_INPUT_GENERATED_COLUMNS must be a comma-separated list containing 1, 2, or 3."
  exit 2
fi

mkdir -p "$fast_input_log_dir" "$fast_input_device_dir"
export IME_EMULATOR_LOG_DIR="$fast_input_log_dir"
export IME_EMULATOR_READINESS_LOG="$fast_input_log_dir/android-readiness.log"
source "${BASH_SOURCE[0]%/*}/ime-emulator-common.sh"

if ! ime_emulator_prepare; then
  ime_emulator_capture_diagnostics "emulator-readiness-failure"
  exit 3
fi

fast_input_test_class=\
"com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest"
if [[ "$fast_input_test_scope" == "generated" ]]; then
  fast_input_test_methods=\
"$fast_input_test_class#generatedTwoFingerInputAcrossAllKeyboardsOnPhysicalDevice"
else
  fast_input_test_methods=\
"$fast_input_test_class#generatedTwoFingerInputAcrossAllKeyboardsOnPhysicalDevice,"\
"$fast_input_test_class#qwertyOverlappingTwoFingerInputOnPhysicalDevice,"\
"$fast_input_test_class#sumireThreeColumnRateSweepOnPhysicalDevice,"\
"$fast_input_test_class#rapidInputFullMatrixOnPhysicalDevice"
fi

fast_input_gradle_args=(
  :app:connectedFullStandardDebugAndroidTest
  --stacktrace
  --no-daemon
  --max-workers=2
  "-Pandroid.testInstrumentationRunnerArguments.class=$fast_input_test_methods"
  "-Pandroid.testInstrumentationRunnerArguments.startCase=$fast_input_start_case"
  "-Pandroid.testInstrumentationRunnerArguments.endCase=$fast_input_end_case"
  "-Pandroid.testInstrumentationRunnerArguments.matrixRounds=$fast_input_rounds"
  "-Pandroid.testInstrumentationRunnerArguments.captureVisuals=$fast_input_capture_visuals"
  "-Pandroid.testInstrumentationRunnerArguments.matrixColumns=$fast_input_generated_columns"
)
if [[ "$fast_input_generated_surfaces" != "ALL" ]]; then
  fast_input_gradle_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.matrixSurfaces=$fast_input_generated_surfaces"
  )
fi

set +e
./gradlew "${fast_input_gradle_args[@]}" \
  2>&1 | tee "$fast_input_log_dir/gradle-connected-android-test.log"
fast_input_gradle_status=${PIPESTATUS[0]}
set -e

if ((fast_input_gradle_status != 0)); then
  ime_emulator_capture_diagnostics "gradle-failure"
fi
ime_emulator_capture_diagnostics "final-device-state"
adb -s "$IME_EMULATOR_SERIAL" logcat -d -v threadtime \
  > "$fast_input_log_dir/device-logcat.txt" || true

if adb -s "$IME_EMULATOR_SERIAL" shell test -d "$fast_input_device_output"; then
  adb -s "$IME_EMULATOR_SERIAL" pull \
    "$fast_input_device_output" "$fast_input_device_dir/" ||
    echo "Unable to pull fast-input screenshots from the emulator."
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  fast_input_summary_lines="$(
    grep -h -E \
      'FAST_INPUT_(MULTITOUCH_|RATE_)?SUMMARY' \
      "$fast_input_log_dir/gradle-connected-android-test.log" \
      "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
      tail -n 12 || true
  )"
  fast_input_failure_excerpt="$(
    grep -h -E \
      'FAST_INPUT_SETUP_ERROR|SetupException|AssertionError|FAILURE: Build failed|There were failing tests' \
      "$fast_input_log_dir/gradle-connected-android-test.log" \
      "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
      head -n 8 |
      cut -c 1-1200 || true
  )"
  {
    echo "## Fast Input Regression"
    echo
    echo "- Cases: $fast_input_start_case-$fast_input_end_case"
    echo "- Rounds: $fast_input_rounds"
    echo "- Capture visuals: $fast_input_capture_visuals"
    echo "- Test scope: $fast_input_test_scope"
    echo "- Generated surfaces: $fast_input_generated_surfaces"
    echo "- Generated columns: $fast_input_generated_columns"
    echo
    echo '```text'
    if [[ -n "$fast_input_summary_lines" ]]; then
      echo "$fast_input_summary_lines"
    else
      echo "FAST_INPUT_SUMMARY was not emitted."
      if [[ -n "$fast_input_failure_excerpt" ]]; then
        echo
        echo "Failure excerpt:"
        echo "$fast_input_failure_excerpt"
      fi
    fi
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

exit "$fast_input_gradle_status"
