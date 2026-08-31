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

android_runtime_config() {
  adb -s "$fast_input_emulator_serial" shell am get-config 2>/dev/null |
    tr -d '\r' |
    head -n 1
}

detach_emulated_at_keyboard() {
  local adb_root_output
  local atkbd_devices
  local device_name
  local runtime_config
  local selinux_mode
  local attempt

  runtime_config="$(android_runtime_config)"
  echo "Android runtime configuration before keyboard setup: $runtime_config" |
    tee -a "$fast_input_readiness_log"
  if [[ "$runtime_config" == *"-nokeys-"* ]]; then
    return 0
  fi

  # The x86_64 ranchu machine can expose its translated PS/2 keyboard even when
  # the AVD has hw.keyboard=no. IMEService correctly treats that device as a
  # physical alphabetic keyboard, so remove the emulator-only device rather
  # than adding a production exception for CI.
  adb -s "$fast_input_emulator_serial" shell dumpsys input \
    > "$fast_input_log_dir/input-service-before-keyboard-detach.txt" || true

  adb_root_output="$(
    adb -s "$fast_input_emulator_serial" root 2>&1 ||
      true
  )"
  echo "adb root: $adb_root_output" | tee -a "$fast_input_readiness_log"
  if ! adb -s "$fast_input_emulator_serial" wait-for-device; then
    echo "The emulator did not reconnect after restarting adbd as root." |
      tee -a "$fast_input_readiness_log"
    return 1
  fi
  if ! wait_for_android_services 2>&1 | tee -a "$fast_input_readiness_log"; then
    return 1
  fi
  if [[ "$(
    adb -s "$fast_input_emulator_serial" shell id -u 2>/dev/null |
      tr -d '\r'
  )" != "0" ]]; then
    echo "The google_apis emulator did not permit root ADB." |
      tee -a "$fast_input_readiness_log"
    return 1
  fi

  atkbd_devices="$(
    adb -s "$fast_input_emulator_serial" shell \
      'for link in /sys/bus/serio/drivers/atkbd/serio*; do
        if [ -e "$link" ]; then basename "$link"; fi
      done' 2>/dev/null |
      tr -d '\r'
  )"
  if [[ -z "$atkbd_devices" ]]; then
    echo "No bound atkbd device was found for runtime config [$runtime_config]." |
      tee -a "$fast_input_readiness_log"
    return 1
  fi

  selinux_mode="$(
    adb -s "$fast_input_emulator_serial" shell getenforce 2>/dev/null |
      tr -d '\r'
  )"
  while IFS= read -r device_name; do
    if [[ ! "$device_name" =~ ^serio[0-9]+$ ]]; then
      echo "Refusing unexpected atkbd device name [$device_name]." |
        tee -a "$fast_input_readiness_log"
      return 1
    fi
    echo "Detaching emulator AT keyboard $device_name." |
      tee -a "$fast_input_readiness_log"
    if ! adb -s "$fast_input_emulator_serial" shell \
      "echo $device_name > /sys/bus/serio/drivers/atkbd/unbind"; then
      if [[ "$selinux_mode" != "Enforcing" ]]; then
        return 1
      fi
      echo "Retrying keyboard detach with SELinux temporarily permissive." |
        tee -a "$fast_input_readiness_log"
      adb -s "$fast_input_emulator_serial" shell setenforce 0
      if ! adb -s "$fast_input_emulator_serial" shell \
        "echo $device_name > /sys/bus/serio/drivers/atkbd/unbind"; then
        adb -s "$fast_input_emulator_serial" shell setenforce 1 || true
        return 1
      fi
      adb -s "$fast_input_emulator_serial" shell setenforce 1
    fi
  done <<< "$atkbd_devices"

  for ((attempt = 1; attempt <= 30; attempt += 1)); do
    runtime_config="$(android_runtime_config)"
    if [[ "$runtime_config" == *"-nokeys-"* ]]; then
      echo "Android runtime configuration after keyboard setup: $runtime_config" |
        tee -a "$fast_input_readiness_log"
      adb -s "$fast_input_emulator_serial" shell dumpsys input \
        > "$fast_input_log_dir/input-service-after-keyboard-detach.txt" || true
      return 0
    fi
    sleep 1
  done

  echo "AT keyboard detach did not produce a nokeys runtime configuration: " \
    "[$runtime_config]." | tee -a "$fast_input_readiness_log"
  adb -s "$fast_input_emulator_serial" shell dumpsys input \
    > "$fast_input_log_dir/input-service-after-keyboard-detach.txt" || true
  return 1
}

has_system_error_dialog() {
  local window_dump="$1"

  grep -Eq 'Application (Not Responding|Error):' "$window_dump"
}

suppress_system_error_dialogs() {
  local attempt
  local hide_error_dialogs
  local window_dump="$fast_input_log_dir/window-after-dialog-suppression.txt"

  adb -s "$fast_input_emulator_serial" shell dumpsys window windows \
    > "$fast_input_log_dir/window-before-dialog-suppression.txt" || true

  # A boot-time ANR in an unrelated system app (notably Nexus Launcher) can
  # leave a focusable system dialog above the instrumentation host. The host is
  # then resumed and its editor is focused, but it can never gain window focus.
  # Suppress future crash/ANR UI for this disposable CI emulator and close any
  # dialog that was already shown before this setting was applied.
  if ! adb -s "$fast_input_emulator_serial" shell \
    settings put global hide_error_dialogs 1; then
    echo "Unable to suppress Android system error dialogs." |
      tee -a "$fast_input_readiness_log"
    return 1
  fi
  hide_error_dialogs="$(
    adb -s "$fast_input_emulator_serial" shell \
      settings get global hide_error_dialogs 2>/dev/null |
      tr -d '\r' ||
      true
  )"
  if [[ "$hide_error_dialogs" != "1" ]]; then
    echo "hide_error_dialogs was [$hide_error_dialogs], expected [1]." |
      tee -a "$fast_input_readiness_log"
    return 1
  fi

  for ((attempt = 1; attempt <= 10; attempt += 1)); do
    adb -s "$fast_input_emulator_serial" shell am broadcast \
      -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
    sleep 1
    if ! adb -s "$fast_input_emulator_serial" shell dumpsys window windows \
      > "$window_dump"; then
      echo "Unable to inspect Android windows after closing system dialogs." |
        tee -a "$fast_input_readiness_log"
      return 1
    fi
    if ! has_system_error_dialog "$window_dump"; then
      echo "Android system error dialogs are suppressed." |
        tee -a "$fast_input_readiness_log"
      return 0
    fi
    echo "System error dialog is still present (attempt $attempt/10)." |
      tee -a "$fast_input_readiness_log"
    if ((attempt == 3)) &&
      grep -q \
        'Application Not Responding: com.google.android.apps.nexuslauncher' \
        "$window_dump"; then
      echo "Stopping the unresponsive CI launcher process." |
        tee -a "$fast_input_readiness_log"
      adb -s "$fast_input_emulator_serial" shell am force-stop \
        com.google.android.apps.nexuslauncher || true
    fi
  done

  echo "An Android crash/ANR dialog could not be dismissed." |
    tee -a "$fast_input_readiness_log"
  return 1
}

capture_device_diagnostics() {
  local label="$1"
  local diagnostic_dir="$fast_input_log_dir/$label"

  mkdir -p "$diagnostic_dir"
  adb -s "$fast_input_emulator_serial" exec-out screencap -p \
    > "$diagnostic_dir/screen.png" || true
  adb -s "$fast_input_emulator_serial" shell dumpsys window windows \
    > "$diagnostic_dir/window.txt" || true
  adb -s "$fast_input_emulator_serial" shell dumpsys activity top \
    > "$diagnostic_dir/activity-top.txt" || true
  adb -s "$fast_input_emulator_serial" shell dumpsys activity processes \
    > "$diagnostic_dir/activity-processes.txt" || true
  adb -s "$fast_input_emulator_serial" shell dumpsys input_method \
    > "$diagnostic_dir/input-method.txt" || true
  if adb -s "$fast_input_emulator_serial" shell test -d /data/anr; then
    adb -s "$fast_input_emulator_serial" pull /data/anr \
      "$diagnostic_dir/anr" >/dev/null 2>&1 ||
      echo "Unable to pull /data/anr from the emulator."
  fi
}

export ANDROID_SERIAL="$fast_input_emulator_serial"
if ! wait_for_android_services 2>&1 | tee "$fast_input_readiness_log"; then
  exit 3
fi

if ! detach_emulated_at_keyboard; then
  exit 4
fi

if ! suppress_system_error_dialogs; then
  capture_device_diagnostics "system-dialog-readiness-failure"
  exit 7
fi

adb -s "$fast_input_emulator_serial" shell \
  settings put secure show_ime_with_hard_keyboard 1
fast_input_show_ime_with_hard_keyboard="$(
  adb -s "$fast_input_emulator_serial" shell \
    settings get secure show_ime_with_hard_keyboard 2>/dev/null |
    tr -d '\r'
)"
if [[ "$fast_input_show_ime_with_hard_keyboard" != "1" ]]; then
  echo "Unable to enable the software IME while hardware keys are present." |
    tee -a "$fast_input_readiness_log"
  exit 5
fi

fast_input_android_config="$(android_runtime_config)"
echo "Android runtime configuration: $fast_input_android_config" |
  tee -a "$fast_input_readiness_log"
if [[ "$fast_input_android_config" != *"-nokeys-"* ]]; then
  echo "The emulator exposes a hardware keyboard; expected the nokeys configuration." |
    tee -a "$fast_input_readiness_log"
  adb -s "$fast_input_emulator_serial" shell dumpsys input \
    > "$fast_input_log_dir/input-service-readiness-failure.txt" || true
  exit 6
fi

adb -s "$fast_input_emulator_serial" shell input keyevent KEYCODE_WAKEUP
adb -s "$fast_input_emulator_serial" shell wm dismiss-keyguard
adb -s "$fast_input_emulator_serial" shell dumpsys input \
  > "$fast_input_log_dir/input-service-before-test.txt"
adb -s "$fast_input_emulator_serial" shell dumpsys input_method \
  > "$fast_input_log_dir/input-method-before-test.txt"
adb -s "$fast_input_emulator_serial" logcat -c

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

if ((fast_input_gradle_status != 0)); then
  capture_device_diagnostics "gradle-failure"
fi

adb -s "$fast_input_emulator_serial" logcat -d -v threadtime \
  > "$fast_input_log_dir/device-logcat.txt" || true

if adb -s "$fast_input_emulator_serial" shell \
  test -d "$fast_input_device_output"; then
  adb -s "$fast_input_emulator_serial" \
    pull "$fast_input_device_output" "$fast_input_device_dir/" ||
    echo "Unable to pull fast-input screenshots from the emulator."
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  fast_input_summary_line="$(
    grep -h "FAST_INPUT_SUMMARY" \
      "$fast_input_log_dir/gradle-connected-android-test.log" \
      "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
      tail -n 1 ||
      true
  )"
  fast_input_failure_excerpt="$(
    grep -h -E \
      'FAST_INPUT_SETUP_ERROR|SetupException|AssertionError|FAILURE: Build failed|There were failing tests' \
      "$fast_input_log_dir/gradle-connected-android-test.log" \
      "$fast_input_log_dir/device-logcat.txt" 2>/dev/null |
      head -n 8 |
      cut -c 1-1200 ||
      true
  )"
  {
    echo "## Fast Input Regression"
    echo
    echo "- Cases: $fast_input_start_case-$fast_input_end_case"
    echo "- Rounds: $fast_input_rounds"
    echo "- Capture visuals: $fast_input_capture_visuals"
    echo
    echo '```text'
    if [[ -n "$fast_input_summary_line" ]]; then
      echo "$fast_input_summary_line"
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
