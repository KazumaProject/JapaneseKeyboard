#!/usr/bin/env bash

# Shared setup for IME instrumentation workflows. Callers must set
# IME_EMULATOR_LOG_DIR before invoking ime_emulator_prepare.

IME_EMULATOR_SERIAL="${IME_EMULATOR_SERIAL:-${ANDROID_SERIAL:-emulator-${EMULATOR_PORT:-5554}}}"
IME_EMULATOR_READINESS_LOG="${IME_EMULATOR_READINESS_LOG:-${IME_EMULATOR_LOG_DIR:?IME_EMULATOR_LOG_DIR is required}/android-readiness.log}"

ime_emulator_wait_for_android_services() {
  local attempt boot_completed device_state input_method_service package_service
  local stable_samples=0

  echo "Waiting for Android services on $IME_EMULATOR_SERIAL."
  adb start-server >/dev/null 2>&1 || true
  for ((attempt = 1; attempt <= 90; attempt += 1)); do
    device_state="$(adb -s "$IME_EMULATOR_SERIAL" get-state 2>/dev/null || true)"
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
      adb -s "$IME_EMULATOR_SERIAL" shell getprop sys.boot_completed 2>/dev/null |
        tr -d '\r' || true
    )"
    package_service="$(
      adb -s "$IME_EMULATOR_SERIAL" shell service check package 2>/dev/null |
        tr -d '\r' || true
    )"
    input_method_service="$(
      adb -s "$IME_EMULATOR_SERIAL" shell service check input_method 2>/dev/null |
        tr -d '\r' || true
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
      echo "Readiness attempt $attempt/90: boot=$boot_completed package=[$package_service] input_method=[$input_method_service]."
    fi
    sleep 2
  done

  echo "Android services did not become stable."
  adb devices -l || true
  adb -s "$IME_EMULATOR_SERIAL" shell getprop sys.boot_completed || true
  adb -s "$IME_EMULATOR_SERIAL" shell service check package || true
  adb -s "$IME_EMULATOR_SERIAL" shell service check input_method || true
  return 1
}

ime_emulator_android_runtime_config() {
  adb -s "$IME_EMULATOR_SERIAL" shell am get-config 2>/dev/null |
    tr -d '\r' |
    head -n 1
}

ime_emulator_detach_at_keyboard() {
  local adb_root_output atkbd_devices device_name runtime_config selinux_mode attempt

  runtime_config="$(ime_emulator_android_runtime_config)"
  echo "Android runtime configuration before keyboard setup: $runtime_config" |
    tee -a "$IME_EMULATOR_READINESS_LOG"
  if [[ "$runtime_config" == *"-nokeys-"* ]]; then
    return 0
  fi

  # ranchu can expose a translated PS/2 keyboard despite hw.keyboard=no. The
  # production IME correctly treats it as physical hardware, so CI detaches it.
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input \
    > "$IME_EMULATOR_LOG_DIR/input-service-before-keyboard-detach.txt" || true
  adb_root_output="$(adb -s "$IME_EMULATOR_SERIAL" root 2>&1 || true)"
  echo "adb root: $adb_root_output" | tee -a "$IME_EMULATOR_READINESS_LOG"
  if ! adb -s "$IME_EMULATOR_SERIAL" wait-for-device; then
    echo "The emulator did not reconnect after restarting adbd as root." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi
  if ! ime_emulator_wait_for_android_services 2>&1 |
    tee -a "$IME_EMULATOR_READINESS_LOG"; then
    return 1
  fi
  if [[ "$(adb -s "$IME_EMULATOR_SERIAL" shell id -u 2>/dev/null | tr -d '\r')" != "0" ]]; then
    echo "The google_apis emulator did not permit root ADB." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi

  atkbd_devices="$(
    adb -s "$IME_EMULATOR_SERIAL" shell \
      'for link in /sys/bus/serio/drivers/atkbd/serio*; do
        if [ -e "$link" ]; then basename "$link"; fi
      done' 2>/dev/null | tr -d '\r'
  )"
  if [[ -z "$atkbd_devices" ]]; then
    echo "No bound atkbd device was found for runtime config [$runtime_config]." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi

  selinux_mode="$(adb -s "$IME_EMULATOR_SERIAL" shell getenforce 2>/dev/null | tr -d '\r')"
  while IFS= read -r device_name; do
    if [[ ! "$device_name" =~ ^serio[0-9]+$ ]]; then
      echo "Refusing unexpected atkbd device name [$device_name]." |
        tee -a "$IME_EMULATOR_READINESS_LOG"
      return 1
    fi
    echo "Detaching emulator AT keyboard $device_name." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    if ! adb -s "$IME_EMULATOR_SERIAL" shell \
      "echo $device_name > /sys/bus/serio/drivers/atkbd/unbind"; then
      if [[ "$selinux_mode" != "Enforcing" ]]; then
        return 1
      fi
      echo "Retrying keyboard detach with SELinux temporarily permissive." |
        tee -a "$IME_EMULATOR_READINESS_LOG"
      adb -s "$IME_EMULATOR_SERIAL" shell setenforce 0
      if ! adb -s "$IME_EMULATOR_SERIAL" shell \
        "echo $device_name > /sys/bus/serio/drivers/atkbd/unbind"; then
        adb -s "$IME_EMULATOR_SERIAL" shell setenforce 1 || true
        return 1
      fi
      adb -s "$IME_EMULATOR_SERIAL" shell setenforce 1
    fi
  done <<< "$atkbd_devices"

  for ((attempt = 1; attempt <= 30; attempt += 1)); do
    runtime_config="$(ime_emulator_android_runtime_config)"
    if [[ "$runtime_config" == *"-nokeys-"* ]]; then
      echo "Android runtime configuration after keyboard setup: $runtime_config" |
        tee -a "$IME_EMULATOR_READINESS_LOG"
      adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input \
        > "$IME_EMULATOR_LOG_DIR/input-service-after-keyboard-detach.txt" || true
      return 0
    fi
    sleep 1
  done

  echo "AT keyboard detach did not produce a nokeys runtime configuration: [$runtime_config]." |
    tee -a "$IME_EMULATOR_READINESS_LOG"
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input \
    > "$IME_EMULATOR_LOG_DIR/input-service-after-keyboard-detach.txt" || true
  return 1
}

ime_emulator_has_system_error_dialog() {
  grep -Eq 'Application (Not Responding|Error):' "$1"
}

ime_emulator_suppress_system_error_dialogs() {
  local attempt hide_error_dialogs
  local window_dump="$IME_EMULATOR_LOG_DIR/window-after-dialog-suppression.txt"

  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys window windows \
    > "$IME_EMULATOR_LOG_DIR/window-before-dialog-suppression.txt" || true
  if ! adb -s "$IME_EMULATOR_SERIAL" shell settings put global hide_error_dialogs 1; then
    echo "Unable to suppress Android system error dialogs." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi
  hide_error_dialogs="$(
    adb -s "$IME_EMULATOR_SERIAL" shell settings get global hide_error_dialogs 2>/dev/null |
      tr -d '\r' || true
  )"
  if [[ "$hide_error_dialogs" != "1" ]]; then
    echo "hide_error_dialogs was [$hide_error_dialogs], expected [1]." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi

  for ((attempt = 1; attempt <= 10; attempt += 1)); do
    adb -s "$IME_EMULATOR_SERIAL" shell am broadcast \
      -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
    sleep 1
    if ! adb -s "$IME_EMULATOR_SERIAL" shell dumpsys window windows > "$window_dump"; then
      echo "Unable to inspect Android windows after closing system dialogs." |
        tee -a "$IME_EMULATOR_READINESS_LOG"
      return 1
    fi
    if ! ime_emulator_has_system_error_dialog "$window_dump"; then
      echo "Android system error dialogs are suppressed." |
        tee -a "$IME_EMULATOR_READINESS_LOG"
      return 0
    fi
    echo "System error dialog is still present (attempt $attempt/10)." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    if ((attempt == 3)) && grep -q \
      'Application Not Responding: com.google.android.apps.nexuslauncher' "$window_dump"; then
      echo "Stopping the unresponsive CI launcher process." |
        tee -a "$IME_EMULATOR_READINESS_LOG"
      adb -s "$IME_EMULATOR_SERIAL" shell am force-stop \
        com.google.android.apps.nexuslauncher || true
    fi
  done

  echo "An Android crash/ANR dialog could not be dismissed." |
    tee -a "$IME_EMULATOR_READINESS_LOG"
  return 1
}

ime_emulator_capture_diagnostics() {
  local label="$1"
  local diagnostic_dir="$IME_EMULATOR_LOG_DIR/$label"
  mkdir -p "$diagnostic_dir"

  if [[ "${IME_EMULATOR_CAPTURE_SCREENSHOTS:-true}" == "true" ]]; then
    adb -s "$IME_EMULATOR_SERIAL" exec-out screencap -p \
      > "$diagnostic_dir/screen.png" || true
  fi
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys window windows \
    > "$diagnostic_dir/window.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys activity top \
    > "$diagnostic_dir/activity-top.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys activity processes \
    > "$diagnostic_dir/activity-processes.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input_method \
    > "$diagnostic_dir/input-method.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input \
    > "$diagnostic_dir/input.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys display \
    > "$diagnostic_dir/display.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell wm size \
    > "$diagnostic_dir/wm-size.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell wm density \
    > "$diagnostic_dir/wm-density.txt" || true
  adb -s "$IME_EMULATOR_SERIAL" shell settings list secure \
    > "$diagnostic_dir/settings-secure.txt" || true
  if adb -s "$IME_EMULATOR_SERIAL" shell test -d /data/anr; then
    adb -s "$IME_EMULATOR_SERIAL" pull /data/anr \
      "$diagnostic_dir/anr" >/dev/null 2>&1 || true
  fi
}

ime_emulator_prepare() {
  mkdir -p "$IME_EMULATOR_LOG_DIR"
  export ANDROID_SERIAL="$IME_EMULATOR_SERIAL"
  if ! ime_emulator_wait_for_android_services 2>&1 | tee "$IME_EMULATOR_READINESS_LOG"; then
    return 1
  fi
  if ! ime_emulator_detach_at_keyboard; then
    return 1
  fi
  if ! ime_emulator_suppress_system_error_dialogs; then
    ime_emulator_capture_diagnostics "system-dialog-readiness-failure"
    return 1
  fi

  adb -s "$IME_EMULATOR_SERIAL" shell \
    settings put secure show_ime_with_hard_keyboard 1
  local show_ime_with_hard_keyboard runtime_config
  show_ime_with_hard_keyboard="$(
    adb -s "$IME_EMULATOR_SERIAL" shell \
      settings get secure show_ime_with_hard_keyboard 2>/dev/null | tr -d '\r'
  )"
  if [[ "$show_ime_with_hard_keyboard" != "1" ]]; then
    echo "Unable to enable the software IME while hardware keys are present." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi

  runtime_config="$(ime_emulator_android_runtime_config)"
  echo "Android runtime configuration: $runtime_config" |
    tee -a "$IME_EMULATOR_READINESS_LOG"
  if [[ "$runtime_config" != *"-nokeys-"* ]]; then
    echo "The emulator exposes a hardware keyboard; expected the nokeys configuration." |
      tee -a "$IME_EMULATOR_READINESS_LOG"
    return 1
  fi

  adb -s "$IME_EMULATOR_SERIAL" shell input keyevent KEYCODE_WAKEUP
  adb -s "$IME_EMULATOR_SERIAL" shell wm dismiss-keyguard
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input \
    > "$IME_EMULATOR_LOG_DIR/input-service-before-test.txt"
  adb -s "$IME_EMULATOR_SERIAL" shell dumpsys input_method \
    > "$IME_EMULATOR_LOG_DIR/input-method-before-test.txt"
  adb -s "$IME_EMULATOR_SERIAL" logcat -c
}
