#!/usr/bin/env bash

set -euo pipefail

fast_input_rounds="${FAST_INPUT_ROUNDS:-1}"
fast_input_generated_surfaces="${FAST_INPUT_GENERATED_SURFACES:-ALL}"
fast_input_generated_columns="${FAST_INPUT_GENERATED_COLUMNS:-1,2,3}"
fast_input_sumire_methods="${FAST_INPUT_SUMIRE_METHODS:-ALL}"
fast_input_timeout_minutes="${FAST_INPUT_TIMEOUT_MINUTES:-40}"
fast_input_artifact_dir="${FAST_INPUT_ARTIFACT_DIR:-${GITHUB_WORKSPACE:-.}/fast-input-artifacts}"
fast_input_log_dir="$fast_input_artifact_dir/logs"
fast_input_summary_file="$fast_input_artifact_dir/summary-${FAST_INPUT_SUMMARY_SURFACE:-all}.txt"
fast_input_test_class="com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest"
fast_input_test_method="$fast_input_test_class#generatedTwoFingerInputAcrossAllKeyboardsOnPhysicalDevice"
fast_input_started_at="$(date +%s)"
fast_input_elapsed_seconds=0

source "${BASH_SOURCE[0]%/*}/fast-input-contract.sh"
fast_input_contract_normalize \
  "$fast_input_rounds" \
  "$fast_input_generated_surfaces" \
  "$fast_input_generated_columns" \
  "$fast_input_sumire_methods"
fast_input_rounds="$FAST_INPUT_CONTRACT_ROUNDS"
fast_input_generated_surfaces="$FAST_INPUT_CONTRACT_SURFACES"
fast_input_generated_columns="$FAST_INPUT_CONTRACT_COLUMNS"
fast_input_sumire_methods="$FAST_INPUT_CONTRACT_SUMIRE_METHODS"

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

if ! is_positive_integer "$fast_input_timeout_minutes" ||
  ((fast_input_timeout_minutes < 1 || fast_input_timeout_minutes > 40)); then
  echo "FAST_INPUT_TIMEOUT_MINUTES must be an integer from 1 to 40."
  exit 2
fi

mkdir -p "$fast_input_log_dir"
export IME_EMULATOR_LOG_DIR="$fast_input_log_dir"
export IME_EMULATOR_READINESS_LOG="$fast_input_log_dir/android-readiness.log"
# The generated test is text-and-accessibility based. Keep the emulator diagnostic bundle
# textual as well; screenshot capture is the failure path that caused the deleted CI run to hang.
export IME_EMULATOR_CAPTURE_SCREENSHOTS=false
source "${BASH_SOURCE[0]%/*}/ime-emulator-common.sh"

write_summary() {
  local status="$1"
  local summary_line="$2"
  local failure_excerpt="$3"
  local summary_json_line="$4"
  {
    echo "surface=${FAST_INPUT_SUMMARY_SURFACE:-$fast_input_generated_surfaces}"
    echo "status=$status"
    echo "elapsedSeconds=$fast_input_elapsed_seconds"
    echo "rounds=$fast_input_rounds"
    echo "columns=$fast_input_generated_columns"
    echo "sumireMethods=$fast_input_sumire_methods"
    if [[ -n "$summary_line" ]]; then
      echo "$summary_line"
    else
      echo "FAST_INPUT_MULTITOUCH_SUMMARY was not emitted."
    fi
    if [[ -n "$summary_json_line" ]]; then
      echo "$summary_json_line"
    fi
    if [[ -n "$failure_excerpt" ]]; then
      echo "failure_excerpt=$failure_excerpt"
    fi
  } > "$fast_input_summary_file"
}

if ! ime_emulator_prepare; then
  ime_emulator_capture_diagnostics "emulator-readiness-failure"
  write_summary "SETUP_ERROR" "" "Emulator preparation failed." ""
  exit 3
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
  ime_emulator_capture_diagnostics "gradle-failure"
fi
ime_emulator_capture_diagnostics "final-device-state"
adb -s "$IME_EMULATOR_SERIAL" logcat -d -v threadtime \
  > "$fast_input_log_dir/device-logcat.txt" || true

fast_input_summary_line="$(grep -h -E 'FAST_INPUT_MULTITOUCH_SUMMARY ' \
  "$fast_input_log_dir/gradle-connected-android-test.log" \
  "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
  tail -n 1 |
  sed -E 's/.*(FAST_INPUT_MULTITOUCH_SUMMARY)/\1/' || true)"
fast_input_summary_json_line="$(grep -h -E 'FAST_INPUT_MULTITOUCH_SUMMARY_JSON ' \
  "$fast_input_log_dir/gradle-connected-android-test.log" \
  "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
  tail -n 1 |
  sed -E 's/.*(FAST_INPUT_MULTITOUCH_SUMMARY_JSON)/\1/' || true)"
fast_input_failure_excerpt="$(grep -h -E \
  'FAST_INPUT_(RESET_ERROR|SETUP_ERROR|INJECTION_ERROR|RESULT_TIMEOUT|INPUT_MISMATCH)|category=(RESET_ERROR|SETUP_ERROR|INJECTION_ERROR|RESULT_TIMEOUT|INPUT_MISMATCH)|SetupException|AssertionError|FAILURE: Build failed|There were failing tests|Error while injecting input event|timeout: sending signal|Terminated' \
  "$fast_input_log_dir/gradle-connected-android-test.log" \
  "$fast_input_log_dir/device-logcat.txt" 2>/dev/null | head -n 12 | cut -c 1-1200 || true)"
fast_input_trace_excerpt="$(grep -h -E 'FastInputTrace|FAST_INPUT_TRACE' \
  "$fast_input_log_dir/gradle-connected-android-test.log" \
  "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
  tail -n 40 | cut -c 1-1200 || true)"
if [[ -n "$fast_input_trace_excerpt" ]]; then
  if [[ -n "$fast_input_failure_excerpt" ]]; then
    fast_input_failure_excerpt+=$'\n'
  fi
  fast_input_failure_excerpt+="trace_excerpt=$fast_input_trace_excerpt"
fi

fast_input_finished_at="$(date +%s)"
fast_input_elapsed_seconds=$((fast_input_finished_at - fast_input_started_at))

if ((fast_input_gradle_status == 124 || fast_input_gradle_status == 137)); then
  fast_input_status="RESULT_TIMEOUT"
elif ((fast_input_gradle_status == 0)) &&
  [[ -n "$fast_input_summary_line" && -n "$fast_input_summary_json_line" ]]; then
  fast_input_status="COMPLETED"
else
  fast_input_status="FAILED"
fi
if [[ "$fast_input_status" == "COMPLETED" ]]; then
  fast_input_failure_excerpt=""
fi
fast_input_exit_status="$fast_input_gradle_status"
if ((fast_input_gradle_status == 0)) && [[ "$fast_input_status" != "COMPLETED" ]]; then
  fast_input_exit_status=1
fi
write_summary \
  "$fast_input_status" \
  "$fast_input_summary_line" \
  "$fast_input_failure_excerpt" \
  "$fast_input_summary_json_line"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## Fast Input Regression — ${FAST_INPUT_SUMMARY_SURFACE:-$fast_input_generated_surfaces}"
    echo
    echo "- Mode: generated two-finger input only"
    echo "- Elapsed seconds: $fast_input_elapsed_seconds"
    echo "- Rounds: $fast_input_rounds"
    echo "- Surfaces: $fast_input_generated_surfaces"
    echo "- Columns: $fast_input_generated_columns"
    echo "- Sumire methods: $fast_input_sumire_methods"
    echo "- Script status: $fast_input_status"
    echo
    echo '```text'
    if [[ -n "$fast_input_summary_line" ]]; then
      echo "$fast_input_summary_line"
      if [[ -n "$fast_input_summary_json_line" ]]; then
        echo "$fast_input_summary_json_line"
      fi
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
