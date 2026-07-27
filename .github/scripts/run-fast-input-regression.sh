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
fast_input_readiness_log="$fast_input_log_dir/android-readiness.log"
fast_input_emulator_serial="${ANDROID_SERIAL:-emulator-${EMULATOR_PORT:-5554}}"

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

wait_for_android_services() {
  local attempt
  local boot_completed
  local device_state
  local input_method_service
  local package_service
  local stable_samples=0

  echo "Waiting for Android services on $fast_input_emulator_serial."
  adb start-server >/dev/null 2>&1 || true

  for ((attempt = 1; attempt <= 90; attempt += 1)); do
    device_state="$(
      adb -s "$fast_input_emulator_serial" get-state 2>/dev/null ||
        true
    )"
    if [[ "$device_state" != "device" ]]; then
      stable_samples=0
      echo "Readiness attempt $attempt/90: adb state=${device_state:-unavailable}."
      if [[ "$device_state" == "offline" && $((attempt % 5)) -eq 0 ]]; then
        adb reconnect offline >/dev/null 2>&1 || true
      fi
      sleep 2
      continue
    fi

    boot_completed="$(
      adb -s "$fast_input_emulator_serial" \
        shell getprop sys.boot_completed 2>/dev/null |
        tr -d '\r' ||
        true
    )"
    package_service="$(
      adb -s "$fast_input_emulator_serial" \
        shell service check package 2>/dev/null |
        tr -d '\r' ||
        true
    )"
    input_method_service="$(
      adb -s "$fast_input_emulator_serial" \
        shell service check input_method 2>/dev/null |
        tr -d '\r' ||
        true
    )"

    if [[ "$boot_completed" == "1" &&
      "$package_service" == *"found"* &&
      "$input_method_service" == *"found"* ]]; then
      stable_samples=$((stable_samples + 1))
      echo "Readiness attempt $attempt/90: stable sample $stable_samples/3."
      if ((stable_samples >= 3)); then
        echo "Android package and input-method services are stable."
        return 0
      fi
    else
      stable_samples=0
      echo "Readiness attempt $attempt/90: boot=$boot_completed " \
        "package=[$package_service] input_method=[$input_method_service]."
    fi
    sleep 2
  done

  echo "Android services did not become stable."
  adb devices -l || true
  adb -s "$fast_input_emulator_serial" shell getprop sys.boot_completed || true
  adb -s "$fast_input_emulator_serial" shell service check package || true
  adb -s "$fast_input_emulator_serial" shell service check input_method || true
  return 1
}

export ANDROID_SERIAL="$fast_input_emulator_serial"
if ! wait_for_android_services 2>&1 | tee "$fast_input_readiness_log"; then
  exit 3
fi

adb shell settings put secure show_ime_with_hard_keyboard 1
fast_input_show_ime_with_hard_keyboard="$(
  adb shell settings get secure show_ime_with_hard_keyboard 2>/dev/null |
    tr -d '\r'
)"
if [[ "$fast_input_show_ime_with_hard_keyboard" != "1" ]]; then
  echo "Unable to enable the software IME while hardware keys are present." |
    tee -a "$fast_input_readiness_log"
  exit 4
fi

fast_input_android_config="$(
  adb shell am get-config 2>/dev/null |
    tr -d '\r' |
    head -n 1
)"
echo "Android runtime configuration: $fast_input_android_config" |
  tee -a "$fast_input_readiness_log"
if [[ "$fast_input_android_config" != *"-nokeys-"* ]]; then
  echo "The emulator exposes a hardware keyboard; expected the nokeys configuration." |
    tee -a "$fast_input_readiness_log"
  adb shell dumpsys input \
    > "$fast_input_log_dir/input-service-readiness-failure.txt" || true
  exit 5
fi

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell dumpsys input > "$fast_input_log_dir/input-service-before-test.txt"
adb shell dumpsys input_method > "$fast_input_log_dir/input-method-before-test.txt"
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
