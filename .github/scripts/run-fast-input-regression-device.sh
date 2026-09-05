#!/usr/bin/env bash

set -euo pipefail

fast_input_device_serial="${ANDROID_SERIAL:-${FAST_INPUT_DEVICE_SERIAL:-23241FDF6003NG}}"
fast_input_rounds="${FAST_INPUT_ROUNDS:-1}"
fast_input_generated_surfaces="${FAST_INPUT_GENERATED_SURFACES:-ALL}"
fast_input_generated_columns="${FAST_INPUT_GENERATED_COLUMNS:-1,2,3}"
fast_input_sumire_methods="${FAST_INPUT_SUMIRE_METHODS:-ALL}"
fast_input_timeout_minutes="${FAST_INPUT_TIMEOUT_MINUTES:-90}"
fast_input_artifact_dir="${FAST_INPUT_ARTIFACT_DIR:-${GITHUB_WORKSPACE:-.}/fast-input-artifacts-device}"
fast_input_log_dir="$fast_input_artifact_dir/logs"
fast_input_summary_file="$fast_input_artifact_dir/summary-${FAST_INPUT_SUMMARY_SURFACE:-all}.txt"
fast_input_test_class="com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest"
fast_input_test_method="$fast_input_test_class#generatedTwoFingerInputAcrossAllKeyboardsOnPhysicalDevice"
fast_input_started_at="$(date +%s)"
fast_input_elapsed_seconds=0

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

if ! is_positive_integer "$fast_input_rounds" || ((fast_input_rounds > 10)); then
  echo "FAST_INPUT_ROUNDS must be an integer from 1 to 10."
  exit 2
fi
if ! is_positive_integer "$fast_input_timeout_minutes" ||
  ((fast_input_timeout_minutes < 1 || fast_input_timeout_minutes > 120)); then
  echo "FAST_INPUT_TIMEOUT_MINUTES must be an integer from 1 to 120."
  exit 2
fi
surface_pattern='(TENKEY|GOJUON|SUMIRE|QWERTY|ROMAJI|CUSTOM)'
if [[ "$fast_input_generated_surfaces" != "ALL" &&
  ! "$fast_input_generated_surfaces" =~ ^${surface_pattern}(,${surface_pattern})*$ ]]; then
  echo "FAST_INPUT_GENERATED_SURFACES must be ALL or a comma-separated surface list."
  exit 2
fi
if [[ ! "$fast_input_generated_columns" =~ ^[123](,[123])*$ ]]; then
  echo "FAST_INPUT_GENERATED_COLUMNS must be a comma-separated list containing 1, 2, or 3."
  exit 2
fi
sumire_method_pattern='(toggle|flick|switch-mode-effective)'
if [[ "$fast_input_sumire_methods" != "ALL" &&
  ! "$fast_input_sumire_methods" =~ ^${sumire_method_pattern}(,${sumire_method_pattern})*$ ]]; then
  echo "FAST_INPUT_SUMIRE_METHODS must be ALL or a comma-separated method list."
  exit 2
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  fast_input_adb_path="$(command -v adb || true)"
  if [[ -n "$fast_input_adb_path" ]]; then
    fast_input_sdk_root="$(cd "$(dirname "$fast_input_adb_path")/.." && pwd)"
    if [[ -d "$fast_input_sdk_root/platform-tools" ]]; then
      export ANDROID_HOME="$fast_input_sdk_root"
      export ANDROID_SDK_ROOT="$fast_input_sdk_root"
    fi
  fi
fi

run_fast_input_with_timeout() {
  local timeout_seconds="$1"
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout --foreground --signal=TERM --kill-after=30s "${timeout_seconds}s" "$@"
    return $?
  fi

  "$@" &
  local command_pid=$!
  local started_at=$SECONDS
  while kill -0 "$command_pid" >/dev/null 2>&1; do
    if ((SECONDS - started_at >= timeout_seconds)); then
      kill -TERM "$command_pid" >/dev/null 2>&1 || true
      local kill_deadline=$((SECONDS + 30))
      while kill -0 "$command_pid" >/dev/null 2>&1 && ((SECONDS < kill_deadline)); do
        sleep 1
      done
      kill -KILL "$command_pid" >/dev/null 2>&1 || true
      wait "$command_pid" >/dev/null 2>&1 || true
      return 124
    fi
    sleep 1
  done
  wait "$command_pid"
}

mkdir -p "$fast_input_log_dir"
export ANDROID_SERIAL="$fast_input_device_serial"

capture_device_diagnostics() {
  local label="$1"
  local diagnostic_dir="$fast_input_log_dir/$label"
  mkdir -p "$diagnostic_dir"
  adb -s "$fast_input_device_serial" shell dumpsys window windows \
    > "$diagnostic_dir/window.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys activity top \
    > "$diagnostic_dir/activity-top.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys activity processes \
    > "$diagnostic_dir/activity-processes.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys meminfo \
    > "$diagnostic_dir/meminfo.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys procstats \
    > "$diagnostic_dir/procstats.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys input_method \
    > "$diagnostic_dir/input-method.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys input \
    > "$diagnostic_dir/input.txt" || true
  adb -s "$fast_input_device_serial" shell dumpsys display \
    > "$diagnostic_dir/display.txt" || true
  adb -s "$fast_input_device_serial" shell wm size \
    > "$diagnostic_dir/wm-size.txt" || true
  adb -s "$fast_input_device_serial" shell wm density \
    > "$diagnostic_dir/wm-density.txt" || true
  adb -s "$fast_input_device_serial" shell settings list secure \
    > "$diagnostic_dir/settings-secure.txt" || true
}

write_summary() {
  local status="$1"
  local summary_line="$2"
  local failure_excerpt="$3"
  {
    echo "deviceSerial=$fast_input_device_serial"
    echo "status=$status"
    echo "elapsedSeconds=$fast_input_elapsed_seconds"
    echo "rounds=$fast_input_rounds"
    echo "surfaces=$fast_input_generated_surfaces"
    echo "columns=$fast_input_generated_columns"
    echo "sumireMethods=$fast_input_sumire_methods"
    if [[ -n "$summary_line" ]]; then
      echo "$summary_line"
    else
      echo "FAST_INPUT_MULTITOUCH_SUMMARY was not emitted."
    fi
    if [[ -n "$failure_excerpt" ]]; then
      echo "failure_excerpt=$failure_excerpt"
    fi
  } > "$fast_input_summary_file"
}

adb start-server >/dev/null 2>&1 || true
if ! adb -s "$fast_input_device_serial" wait-for-device; then
  write_summary "SETUP_ERROR" "" "ADB could not reach $fast_input_device_serial."
  exit 3
fi

device_model="$(adb -s "$fast_input_device_serial" shell getprop ro.product.model | tr -d '\r' || true)"
device_sdk="$(adb -s "$fast_input_device_serial" shell getprop ro.build.version.sdk | tr -d '\r' || true)"
echo "Physical device: serial=$fast_input_device_serial model=$device_model sdk=$device_sdk"
if [[ "$device_model" != *"Pixel 6"* ]]; then
  capture_device_diagnostics "device-model-failure"
  write_summary "SETUP_ERROR" "" "Expected Pixel 6, got [$device_model]."
  exit 3
fi

device_ready=false
for attempt in {1..60}; do
  boot_completed="$(adb -s "$fast_input_device_serial" shell getprop sys.boot_completed | tr -d '\r' || true)"
  input_method_service="$(adb -s "$fast_input_device_serial" shell service check input_method | tr -d '\r' || true)"
  if [[ "$boot_completed" == "1" && "$input_method_service" == *"found"* ]]; then
    device_ready=true
    break
  fi
  echo "Device readiness attempt $attempt/60: boot=$boot_completed input_method=[$input_method_service]"
  sleep 2
done
if [[ "$device_ready" != true ]]; then
  capture_device_diagnostics "device-readiness-failure"
  write_summary "SETUP_ERROR" "" "Android services did not become ready."
  exit 3
fi

# The physical path intentionally has no KVM, AVD, AT-keyboard, or emulator-graphics setup.
adb -s "$fast_input_device_serial" shell input keyevent KEYCODE_WAKEUP || true
adb -s "$fast_input_device_serial" shell wm dismiss-keyguard || true
adb -s "$fast_input_device_serial" logcat -c || true

fast_input_gradle_args=(
  :app:connectedFullStandardDebugAndroidTest
  --stacktrace
  --no-daemon
  --max-workers=2
  "-Pandroid.testInstrumentationRunnerArguments.class=$fast_input_test_method"
  "-Pandroid.testInstrumentationRunnerArguments.matrixRounds=$fast_input_rounds"
  "-Pandroid.testInstrumentationRunnerArguments.matrixSurfaces=$fast_input_generated_surfaces"
  "-Pandroid.testInstrumentationRunnerArguments.matrixColumns=$fast_input_generated_columns"
  "-Pandroid.testInstrumentationRunnerArguments.matrixSumireMethods=$fast_input_sumire_methods"
)

set +e
run_fast_input_with_timeout "$((fast_input_timeout_minutes * 60))" \
  ./gradlew "${fast_input_gradle_args[@]}" \
  2>&1 | tee "$fast_input_log_dir/gradle-connected-android-test.log"
fast_input_gradle_status=${PIPESTATUS[0]}
set -e

if ((fast_input_gradle_status != 0)); then
  capture_device_diagnostics "gradle-failure"
fi
capture_device_diagnostics "final-device-state"
adb -s "$fast_input_device_serial" logcat -d -v threadtime \
  > "$fast_input_log_dir/device-logcat.txt" || true

fast_input_summary_line="$(grep -h -E 'FAST_INPUT_MULTITOUCH_SUMMARY' \
  "$fast_input_log_dir/gradle-connected-android-test.log" \
  "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
  tail -n 1 |
  sed -E 's/.*(FAST_INPUT_MULTITOUCH_SUMMARY)/\1/' || true)"
fast_input_failure_excerpt="$(grep -h -E \
  'FAST_INPUT_(RESET_ERROR|SETUP_ERROR|INJECTION_ERROR|RESULT_TIMEOUT|INPUT_MISMATCH)|category=(RESET_ERROR|SETUP_ERROR|INJECTION_ERROR|RESULT_TIMEOUT|INPUT_MISMATCH)|SetupException|AssertionError|FAILURE: Build failed|There were failing tests|DeadObjectException|Error while injecting input event|timeout: sending signal|Terminated' \
  "$fast_input_log_dir/gradle-connected-android-test.log" \
  "$fast_input_log_dir/device-logcat.txt" 2>/dev/null | head -n 12 | cut -c 1-1200 || true)"

fast_input_finished_at="$(date +%s)"
fast_input_elapsed_seconds=$((fast_input_finished_at - fast_input_started_at))

if ((fast_input_gradle_status == 124 || fast_input_gradle_status == 137)); then
  fast_input_status="RESULT_TIMEOUT"
elif ((fast_input_gradle_status == 0)) && [[ -n "$fast_input_summary_line" ]]; then
  fast_input_status="COMPLETED"
else
  fast_input_status="FAILED"
fi
fast_input_exit_status="$fast_input_gradle_status"
if ((fast_input_gradle_status == 0)) && [[ "$fast_input_status" != "COMPLETED" ]]; then
  fast_input_exit_status=1
fi
write_summary "$fast_input_status" "$fast_input_summary_line" "$fast_input_failure_excerpt"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## Fast Input Regression — physical Pixel 6"
    echo
    echo "- Mode: generated two-finger input only"
    echo "- Elapsed seconds: $fast_input_elapsed_seconds"
    echo "- Device: $fast_input_device_serial ($device_model)"
    echo "- Rounds: $fast_input_rounds"
    echo "- Surfaces: $fast_input_generated_surfaces"
    echo "- Columns: $fast_input_generated_columns"
    echo "- Sumire methods: $fast_input_sumire_methods"
    echo "- Script status: $fast_input_status"
    echo
    echo '```text'
    if [[ -n "$fast_input_summary_line" ]]; then
      echo "$fast_input_summary_line"
    else
      echo "FAST_INPUT_MULTITOUCH_SUMMARY was not emitted."
      if [[ -n "$fast_input_failure_excerpt" ]]; then
        echo
        echo "Failure excerpt:"
        echo "$fast_input_failure_excerpt"
      fi
    fi
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

exit "$fast_input_exit_status"
