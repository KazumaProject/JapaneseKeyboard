#!/usr/bin/env bash

set -euo pipefail

settings_freeze_rounds="${SETTINGS_FREEZE_ROUNDS:-10}"
settings_freeze_datasets="${SETTINGS_FREEZE_DATASETS:-empty,user,learn,template,macro,combined}"
settings_freeze_entry_counts="${SETTINGS_FREEZE_ENTRY_COUNTS:-1000,10000,50000}"
settings_freeze_home_modes="${SETTINGS_FREEZE_HOME_MODES:-new,legacy}"
settings_freeze_capture_perfetto="${SETTINGS_FREEZE_CAPTURE_PERFETTO:-true}"
settings_freeze_allow_physical_reset="${SETTINGS_FREEZE_ALLOW_PHYSICAL_DEVICE_DATA_RESET:-false}"
settings_freeze_artifact_dir="${GITHUB_WORKSPACE:-.}/settings-freeze-artifacts"
settings_freeze_log_dir="$settings_freeze_artifact_dir/logs"
settings_freeze_case_dir="$settings_freeze_artifact_dir/cases"
settings_freeze_target_package="com.kazumaproject.markdownhelperkeyboard"
settings_freeze_test_package="com.kazumaproject.markdownhelperkeyboard.test"
settings_freeze_test_runner="$settings_freeze_test_package/androidx.test.runner.AndroidJUnitRunner"
settings_freeze_test_class="com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsLaunchFreezeInstrumentedTest"
settings_freeze_main_component="$settings_freeze_target_package/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity"
settings_freeze_host_component="$settings_freeze_target_package/.FastInputHostActivity"
settings_freeze_ime_component="$settings_freeze_target_package/.ime_service.IMEService"
settings_freeze_device_output="/sdcard/Android/data/$settings_freeze_target_package/files/settings-freeze"

is_integer_in_range() {
  local value="$1"
  local minimum="$2"
  local maximum="$3"
  [[ "$value" =~ ^[0-9]+$ ]] && ((value >= minimum && value <= maximum))
}

contains_csv_value() {
  local allowed="$1"
  local candidate="$2"
  [[ ",$allowed," == *",$candidate,"* ]]
}

if ! is_integer_in_range "$settings_freeze_rounds" 1 10; then
  echo "SETTINGS_FREEZE_ROUNDS must be an integer from 1 to 10."
  exit 2
fi
if [[ "$settings_freeze_capture_perfetto" != "true" &&
  "$settings_freeze_capture_perfetto" != "false" ]]; then
  echo "SETTINGS_FREEZE_CAPTURE_PERFETTO must be true or false."
  exit 2
fi
if [[ "$settings_freeze_allow_physical_reset" != "true" &&
  "$settings_freeze_allow_physical_reset" != "false" ]]; then
  echo "SETTINGS_FREEZE_ALLOW_PHYSICAL_DEVICE_DATA_RESET must be true or false."
  exit 2
fi

IFS=',' read -r -a settings_freeze_dataset_values <<< "$settings_freeze_datasets"
IFS=',' read -r -a settings_freeze_count_values <<< "$settings_freeze_entry_counts"
IFS=',' read -r -a settings_freeze_home_values <<< "$settings_freeze_home_modes"
for dataset_value in "${settings_freeze_dataset_values[@]}"; do
  if ! contains_csv_value "empty,user,learn,template,macro,combined" "$dataset_value"; then
    echo "Unsupported dataset: $dataset_value"
    exit 2
  fi
done
for count_value in "${settings_freeze_count_values[@]}"; do
  if ! contains_csv_value "1000,10000,50000" "$count_value"; then
    echo "Unsupported entry count: $count_value"
    exit 2
  fi
done
for home_value in "${settings_freeze_home_values[@]}"; do
  if ! contains_csv_value "new,legacy" "$home_value"; then
    echo "Unsupported home mode: $home_value"
    exit 2
  fi
done

mkdir -p "$settings_freeze_log_dir" "$settings_freeze_case_dir"
export IME_EMULATOR_LOG_DIR="$settings_freeze_log_dir"
export IME_EMULATOR_READINESS_LOG="$settings_freeze_log_dir/android-readiness.log"
source "${BASH_SOURCE[0]%/*}/ime-emulator-common.sh"

if ! ime_emulator_prepare; then
  ime_emulator_capture_diagnostics "emulator-readiness-failure"
  exit 3
fi

settings_freeze_serial="$IME_EMULATOR_SERIAL"
settings_freeze_is_emulator="$({
  adb -s "$settings_freeze_serial" shell getprop ro.kernel.qemu 2>/dev/null || true
} | tr -d '\r')"
if [[ "$settings_freeze_is_emulator" != "1" &&
  "$settings_freeze_allow_physical_reset" != "true" ]]; then
  echo "Refusing destructive dictionary seeding on a physical device."
  echo "Set SETTINGS_FREEZE_ALLOW_PHYSICAL_DEVICE_DATA_RESET=true only for a disposable device."
  exit 4
fi

settings_freeze_original_ime="$({
  adb -s "$settings_freeze_serial" shell settings get secure default_input_method 2>/dev/null || true
} | tr -d '\r')"
settings_freeze_original_enabled_imes="$({
  adb -s "$settings_freeze_serial" shell ime list -s 2>/dev/null || true
} | tr -d '\r')"

restore_device_state() {
  set +e
  if [[ -n "$settings_freeze_original_ime" && "$settings_freeze_original_ime" != "null" ]]; then
    adb -s "$settings_freeze_serial" shell ime set "$settings_freeze_original_ime" >/dev/null 2>&1
  fi
  if ! grep -Fxq "$settings_freeze_ime_component" <<< "$settings_freeze_original_enabled_imes"; then
    adb -s "$settings_freeze_serial" shell ime disable "$settings_freeze_ime_component" \
      >/dev/null 2>&1
  fi
}
trap restore_device_state EXIT

./gradlew \
  :app:assembleFullStandardDebug \
  :app:assembleFullStandardDebugAndroidTest \
  --stacktrace \
  --no-daemon \
  --max-workers=2 \
  2>&1 | tee "$settings_freeze_log_dir/gradle-build.log"

settings_freeze_app_apk="app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk"
settings_freeze_test_apk="app/build/outputs/apk/androidTest/fullStandard/debug/app-full-standard-debug-androidTest.apk"
test -f "$settings_freeze_app_apk"
test -f "$settings_freeze_test_apk"
adb -s "$settings_freeze_serial" install -r -t "$settings_freeze_app_apk" \
  > "$settings_freeze_log_dir/install-app.txt"
adb -s "$settings_freeze_serial" install -r -t "$settings_freeze_test_apk" \
  > "$settings_freeze_log_dir/install-test.txt"
adb -s "$settings_freeze_serial" shell ime enable "$settings_freeze_ime_component" \
  > "$settings_freeze_log_dir/enable-ime.txt"

run_probe_test() {
  local method="$1"
  local dataset="$2"
  local entry_count="$3"
  local home_mode="$4"
  local output_file="$5"
  local allow_physical="false"
  local instrumentation_status=0
  if [[ "$settings_freeze_is_emulator" != "1" ]]; then
    allow_physical="$settings_freeze_allow_physical_reset"
  fi

  set +e
  adb -s "$settings_freeze_serial" shell timeout 900s am instrument -w -r \
    -e class "$settings_freeze_test_class#$method" \
    -e settingsFreezeProbe true \
    -e allowDestructiveSeed true \
    -e allowPhysicalDeviceDataReset "$allow_physical" \
    -e dataset "$dataset" \
    -e entryCount "$entry_count" \
    -e rounds "$settings_freeze_rounds" \
    -e homeMode "$home_mode" \
    "$settings_freeze_test_runner" > "$output_file" 2>&1
  instrumentation_status=$?
  set -e
  if ((instrumentation_status != 0)); then
    return "$instrumentation_status"
  fi
  if ! grep -Eq '^OK \(1 test\)' "$output_file"; then
    echo "Instrumentation did not report one successful test." >> "$output_file"
    return 1
  fi
}

capture_snapshot() {
  local destination="$1"
  mkdir -p "$destination"
  adb -s "$settings_freeze_serial" exec-out screencap -p \
    > "$destination/screen.png" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell dumpsys activity top \
    > "$destination/activity-top.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell dumpsys window windows \
    > "$destination/window.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell dumpsys input_method \
    > "$destination/input-method.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell dumpsys meminfo "$settings_freeze_target_package" \
    > "$destination/meminfo.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell dumpsys gfxinfo "$settings_freeze_target_package" framestats \
    > "$destination/gfxinfo-framestats.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell run-as "$settings_freeze_target_package" \
    ls -l databases > "$destination/database-files.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" shell dumpsys dbinfo "$settings_freeze_target_package" \
    > "$destination/sqlite-runtime.txt" 2>/dev/null || true
  adb -s "$settings_freeze_serial" exec-out run-as "$settings_freeze_target_package" \
    sqlite3 databases/learn_database \
    "PRAGMA page_count; PRAGMA page_size; PRAGMA freelist_count; PRAGMA journal_mode;" \
    > "$destination/sqlite-pragma.txt" 2>&1 || true
  adb -s "$settings_freeze_serial" logcat -d -t 2000 -v threadtime \
    > "$destination/logcat.txt" 2>/dev/null || true
}

capture_hang() {
  local destination="$1"
  local process_id
  mkdir -p "$destination"
  process_id="$({
    adb -s "$settings_freeze_serial" shell pidof "$settings_freeze_target_package" || true
  } | tr -d '\r')"
  if [[ "$process_id" =~ ^[0-9]+$ ]]; then
    adb -s "$settings_freeze_serial" shell run-as "$settings_freeze_target_package" \
      kill -3 "$process_id" || true
    adb -s "$settings_freeze_serial" shell debuggerd -b "$process_id" \
      > "$destination/native-backtrace.txt" 2>&1 || true
  fi
  capture_snapshot "$destination"
  adb -s "$settings_freeze_serial" pull /data/anr "$destination/anr" \
    > "$destination/anr-pull.txt" 2>&1 || true
}

start_perfetto() {
  local device_trace="$1"
  local perfetto_log="$2"
  adb -s "$settings_freeze_serial" shell rm -f "$device_trace"
  adb -s "$settings_freeze_serial" shell perfetto \
    -o "$device_trace" -t 15s \
    sched freq idle am wm gfx view binder_driver dalvik \
    > "$perfetto_log" 2>&1 &
  settings_freeze_perfetto_pid=$!
}

finish_perfetto() {
  local device_trace="$1"
  local host_trace="$2"
  if [[ -n "${settings_freeze_perfetto_pid:-}" ]]; then
    wait "$settings_freeze_perfetto_pid" || true
    settings_freeze_perfetto_pid=""
  fi
  adb -s "$settings_freeze_serial" pull "$device_trace" "$host_trace" >/dev/null 2>&1 || true
  adb -s "$settings_freeze_serial" shell rm -f "$device_trace" || true
}

run_timed_launch() {
  local label="$1"
  local round="$2"
  local case_output="$3"
  local component="$4"
  local launch_output="$case_output/${label}-round-${round}.txt"
  local status=0

  adb -s "$settings_freeze_serial" shell dumpsys gfxinfo "$settings_freeze_target_package" reset \
    >/dev/null 2>&1 || true
  set +e
  adb -s "$settings_freeze_serial" shell timeout 15s am start -W -n "$component" \
    > "$launch_output" 2>&1
  status=$?
  set -e
  if ((status != 0)); then
    echo "launch_status=$status" >> "$launch_output"
    capture_hang "$case_output/${label}-round-${round}-hang"
    return 1
  fi
  local total_time
  total_time="$(sed -n 's/^TotalTime:[[:space:]]*//p' "$launch_output" | tail -n 1)"
  if [[ "$total_time" =~ ^[0-9]+$ ]] && ((total_time >= 10000)); then
    echo "launch_status=not_interactive_within_10s" >> "$launch_output"
    capture_hang "$case_output/${label}-round-${round}-hang"
    return 1
  fi
  capture_snapshot "$case_output/${label}-round-${round}-snapshot"
}

run_case() {
  local dataset="$1"
  local entry_count="$2"
  local home_mode="$3"
  local case_name="${dataset}-${entry_count}-${home_mode}"
  local case_output="$settings_freeze_case_dir/$case_name"
  local device_trace="/data/local/tmp/settings-freeze-$case_name.perfetto-trace"
  mkdir -p "$case_output"
  echo "Running settings freeze probe: dataset=$dataset count=$entry_count home=$home_mode"

  adb -s "$settings_freeze_serial" shell pm clear "$settings_freeze_target_package" \
    > "$case_output/pm-clear.txt"
  adb -s "$settings_freeze_serial" shell ime enable "$settings_freeze_ime_component" \
    > "$case_output/ime-enable.txt"
  run_probe_test \
    seedSyntheticDatabase "$dataset" "$entry_count" "$home_mode" \
    "$case_output/instrumentation-seed.txt"
  adb -s "$settings_freeze_serial" pull "$settings_freeze_device_output" \
    "$case_output/seed-output" >/dev/null 2>&1 || true

  run_probe_test \
    measureSettingsHomeAndManagementScreens "$dataset" "$entry_count" "$home_mode" \
    "$case_output/instrumentation-screens.txt" || {
      adb -s "$settings_freeze_serial" pull "$settings_freeze_device_output" \
        "$case_output/instrumentation-output" >/dev/null 2>&1 || true
      capture_hang "$case_output/instrumentation-screens-hang"
      return 1
    }
  adb -s "$settings_freeze_serial" pull "$settings_freeze_device_output" \
    "$case_output/instrumentation-output" >/dev/null 2>&1 || true
  if grep -Rqs $'\tANR_RISK$' "$case_output/instrumentation-output"; then
    capture_hang "$case_output/instrumentation-anr-risk"
  fi

  for ((round = 1; round <= settings_freeze_rounds; round += 1)); do
    adb -s "$settings_freeze_serial" shell am force-stop "$settings_freeze_target_package"
    if [[ "$settings_freeze_capture_perfetto" == "true" && "$round" -eq 1 ]]; then
      start_perfetto "$device_trace" "$case_output/cold-perfetto.txt"
    fi
    run_timed_launch "cold-home" "$round" "$case_output" "$settings_freeze_main_component" || true
    if [[ "$settings_freeze_capture_perfetto" == "true" && "$round" -eq 1 ]]; then
      finish_perfetto "$device_trace" "$case_output/cold-home.perfetto-trace"
    fi
  done

  adb -s "$settings_freeze_serial" shell ime set "$settings_freeze_ime_component" \
    > "$case_output/ime-set.txt"
  for ((round = 1; round <= settings_freeze_rounds; round += 1)); do
    adb -s "$settings_freeze_serial" shell am force-stop "$settings_freeze_target_package"
    adb -s "$settings_freeze_serial" shell am start -W -n "$settings_freeze_host_component" \
      > "$case_output/ime-host-round-${round}.txt" 2>&1 || true
    adb -s "$settings_freeze_serial" shell input text aprobe || true
    sleep 1
    if [[ "$settings_freeze_capture_perfetto" == "true" && "$round" -eq 1 ]]; then
      start_perfetto "$device_trace" "$case_output/ime-transition-perfetto.txt"
    fi
    run_timed_launch "ime-to-settings" "$round" "$case_output" \
      "$settings_freeze_main_component" || true
    if [[ "$settings_freeze_capture_perfetto" == "true" && "$round" -eq 1 ]]; then
      finish_perfetto "$device_trace" "$case_output/ime-to-settings.perfetto-trace"
    fi

    adb -s "$settings_freeze_serial" shell am start -W -n "$settings_freeze_host_component" \
      > "$case_output/cache-host-round-${round}.txt" 2>&1 || true
    adb -s "$settings_freeze_serial" shell input text cacheprobe || true
    sleep 2
    adb -s "$settings_freeze_serial" shell input keyevent KEYCODE_BACK || true
    if [[ "$settings_freeze_capture_perfetto" == "true" && "$round" -eq 1 ]]; then
      start_perfetto "$device_trace" "$case_output/cache-warm-perfetto.txt"
    fi
    run_timed_launch "cache-warm-settings" "$round" "$case_output" \
      "$settings_freeze_main_component" || true
    if [[ "$settings_freeze_capture_perfetto" == "true" && "$round" -eq 1 ]]; then
      finish_perfetto "$device_trace" "$case_output/cache-warm-settings.perfetto-trace"
    fi
  done
}

settings_freeze_failures=0
for dataset_value in "${settings_freeze_dataset_values[@]}"; do
  if [[ "$dataset_value" == "empty" ]]; then
    effective_counts=(0)
  else
    effective_counts=("${settings_freeze_count_values[@]}")
  fi
  for count_value in "${effective_counts[@]}"; do
    for home_value in "${settings_freeze_home_values[@]}"; do
      if ! run_case "$dataset_value" "$count_value" "$home_value"; then
        settings_freeze_failures=$((settings_freeze_failures + 1))
      fi
    done
  done
done

adb -s "$settings_freeze_serial" logcat -d -v threadtime \
  > "$settings_freeze_log_dir/final-device-logcat.txt" || true
ime_emulator_capture_diagnostics "final-device-state"

{
  echo "SETTINGS_FREEZE_SUMMARY"
  echo "datasets=$settings_freeze_datasets"
  echo "entry_counts=$settings_freeze_entry_counts"
  echo "home_modes=$settings_freeze_home_modes"
  echo "rounds=$settings_freeze_rounds"
  echo "case_failures=$settings_freeze_failures"
} | tee "$settings_freeze_artifact_dir/summary.txt"

if ! python3 "${BASH_SOURCE[0]%/*}/analyze-settings-freeze-results.py" \
  "$settings_freeze_artifact_dir" \
  > "$settings_freeze_artifact_dir/analysis.log" 2>&1; then
  echo "Result analysis failed; raw diagnostics are still available." \
    | tee -a "$settings_freeze_artifact_dir/analysis.log"
fi

if ((settings_freeze_failures > 0)); then
  exit 1
fi
