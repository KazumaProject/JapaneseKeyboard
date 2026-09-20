#!/usr/bin/env bash

set -euo pipefail

skin_ime_artifact_name="${IME_LAYOUT_ARTIFACT_NAME:-local}"
skin_ime_artifact_dir="${GITHUB_WORKSPACE:-.}/ime-layout-artifacts/$skin_ime_artifact_name"
skin_ime_log_dir="$skin_ime_artifact_dir/logs"
skin_ime_device_dir="$skin_ime_artifact_dir/device"
skin_ime_application_id="${SKIN_IME_APPLICATION_ID:-com.kazumaproject.skinfidelity.imeheight.lite}"
skin_ime_target="${skin_ime_application_id}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
skin_ime_device_output="/sdcard/Android/data/$skin_ime_application_id/files/ime-layout"
skin_ime_navigation_mode="${IME_NAVIGATION_MODE:-unchanged}"
skin_ime_original_navigation_overlay=""
skin_ime_navigation_changed=false
skin_ime_original_default_ime=""
skin_ime_default_ime_changed=false
skin_ime_pull_interval_seconds="${IME_LAYOUT_PULL_INTERVAL_SECONDS:-0.25}"

case "$skin_ime_navigation_mode" in
  unchanged|threebutton|gestural) ;;
  *)
    echo "IME_NAVIGATION_MODE must be unchanged, threebutton, or gestural."
    exit 2
    ;;
esac

mkdir -p "$skin_ime_log_dir" "$skin_ime_device_dir"
export IME_EMULATOR_LOG_DIR="$skin_ime_log_dir"
export IME_EMULATOR_READINESS_LOG="$skin_ime_log_dir/android-readiness.log"
source "${BASH_SOURCE[0]%/*}/ime-emulator-common.sh"

skin_ime_overlay_candidates=(
  com.android.internal.systemui.navbar.gestural
  com.android.internal.systemui.navbar.threebutton
  com.android.internal.systemui.navbar.twobutton
)

skin_ime_overlay_list() {
  adb -s "$IME_EMULATOR_SERIAL" shell cmd overlay list 2>/dev/null | tr -d '\r' || true
}

skin_ime_capture_navigation_overlay() {
  local overlays="$1"
  skin_ime_original_navigation_overlay=""
  for overlay in "${skin_ime_overlay_candidates[@]}"; do
    if [[ "$overlays" == *"[x] $overlay"* ]]; then
      skin_ime_original_navigation_overlay="$overlay"
      break
    fi
  done
}

skin_ime_configure_navigation() {
  if [[ "$skin_ime_navigation_mode" == "unchanged" ]]; then
    return 0
  fi

  local overlays requested_overlay
  overlays="$(skin_ime_overlay_list)"
  skin_ime_capture_navigation_overlay "$overlays"
  requested_overlay="com.android.internal.systemui.navbar.$skin_ime_navigation_mode"
  if [[ "$overlays" != *"$requested_overlay"* ]]; then
    if [[ "$skin_ime_navigation_mode" == "threebutton" ]]; then
      echo "Three-button navigation overlay is unavailable; keeping the platform default."
      return 0
    fi
    echo "Required navigation overlay is unavailable: $requested_overlay"
    return 1
  fi
  if [[ -z "$skin_ime_original_navigation_overlay" ]]; then
    echo "Unable to identify the original navigation overlay; refusing to change navigation mode."
    return 1
  fi

  # Mark the state as changed before the first mutating command so EXIT cleanup
  # restores the original overlay even if a later adb command fails.
  skin_ime_navigation_changed=true
  for overlay in "${skin_ime_overlay_candidates[@]}"; do
    if [[ "$overlays" == *"$overlay"* ]]; then
      adb -s "$IME_EMULATOR_SERIAL" shell cmd overlay disable --user 0 "$overlay" >/dev/null 2>&1 || true
    fi
  done
  if ! adb -s "$IME_EMULATOR_SERIAL" shell cmd overlay enable --user 0 "$requested_overlay"; then
    return 1
  fi
  sleep 2
  ime_emulator_wait_for_android_services 2>&1 | tee -a "$IME_EMULATOR_READINESS_LOG"
}

skin_ime_restore_navigation() {
  if [[ "$skin_ime_navigation_changed" != true ]]; then
    return 0
  fi

  for overlay in "${skin_ime_overlay_candidates[@]}"; do
    adb -s "$IME_EMULATOR_SERIAL" shell cmd overlay disable --user 0 "$overlay" >/dev/null 2>&1 || true
  done
  if [[ -n "$skin_ime_original_navigation_overlay" ]]; then
    adb -s "$IME_EMULATOR_SERIAL" shell cmd overlay enable --user 0 \
      "$skin_ime_original_navigation_overlay" >/dev/null 2>&1 || true
  fi
}

skin_ime_restore_default_ime() {
  if [[ "$skin_ime_default_ime_changed" != true ||
    -z "$skin_ime_original_default_ime" ||
    "$skin_ime_original_default_ime" == "null" ]]; then
    return 0
  fi
  adb -s "$IME_EMULATOR_SERIAL" shell ime set "$skin_ime_original_default_ime" >/dev/null 2>&1 || true
}

skin_ime_pull_rotation_output() {
  local rotation="$1"
  local remote_output="$skin_ime_device_output/$rotation"
  local local_output="$skin_ime_device_dir/$rotation"
  if adb -s "$IME_EMULATOR_SERIAL" shell test -d "$remote_output"; then
    mkdir -p "$local_output"
    adb -s "$IME_EMULATOR_SERIAL" pull "$remote_output/." "$local_output/" >/dev/null 2>&1 || true
  fi
}

skin_ime_cleanup() {
  skin_ime_restore_navigation
  skin_ime_restore_default_ime
}
trap skin_ime_cleanup EXIT

skin_ime_original_default_ime="$(
  adb -s "$IME_EMULATOR_SERIAL" shell settings get secure default_input_method 2>/dev/null |
    tr -d '\r' | tr -d '\n' || true
)"

if ! ime_emulator_prepare; then
  ime_emulator_capture_diagnostics "emulator-readiness-failure"
  exit 3
fi

# The instrumentation test deliberately refuses to start when the target IME is
# already default. Select another enabled IME for that case and restore it on exit.
current_ime="$(
  adb -s "$IME_EMULATOR_SERIAL" shell settings get secure default_input_method 2>/dev/null |
    tr -d '\r' | tr -d '\n' || true
)"
if [[ "$current_ime" == "$skin_ime_target" ]]; then
  fallback_ime="$(
    adb -s "$IME_EMULATOR_SERIAL" shell ime list -s 2>/dev/null |
      tr -d '\r' |
      awk -v target="$skin_ime_target" '$0 ~ /\// && $0 != target { print; exit }'
  )"
  if [[ -z "$fallback_ime" ]]; then
    echo "No alternate enabled IME is available while $skin_ime_target is default."
    exit 4
  fi
  adb -s "$IME_EMULATOR_SERIAL" shell ime set "$fallback_ime"
  skin_ime_default_ime_changed=true
fi

if ! skin_ime_configure_navigation; then
  ime_emulator_capture_diagnostics "navigation-configuration-failure"
  exit 5
fi

skin_ime_test_method=\
"com.kazumaproject.markdownhelperkeyboard.SkinImeLayoutInstrumentedTest"
skin_ime_overall_status=0

for rotation in portrait landscape; do
  skin_ime_rotation_log="$skin_ime_log_dir/gradle-connected-android-test-$rotation.log"
  set +e
  ./gradlew \
    :app:connectedLiteStandardDebugAndroidTest \
    -I investigation/skin-fidelity.init.gradle \
    --stacktrace \
    --no-daemon \
    --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx2g \
    "-Pandroid.testInstrumentationRunnerArguments.class=$skin_ime_test_method" \
    "-Pandroid.testInstrumentationRunnerArguments.rotation=$rotation" \
    > "$skin_ime_rotation_log" 2>&1 &
  skin_ime_gradle_pid=$!
  while kill -0 "$skin_ime_gradle_pid" 2>/dev/null; do
    skin_ime_pull_rotation_output "$rotation"
    sleep "$skin_ime_pull_interval_seconds"
  done
  wait "$skin_ime_gradle_pid"
  skin_ime_rotation_status=$?
  set -e
  cat "$skin_ime_rotation_log"
  skin_ime_pull_rotation_output "$rotation"

  if ((skin_ime_rotation_status != 0)); then
    skin_ime_overall_status=1
    ime_emulator_capture_diagnostics "gradle-failure-$rotation"
  fi
done

ime_emulator_capture_diagnostics "final-device-state"
adb -s "$IME_EMULATOR_SERIAL" logcat -d -v threadtime \
  > "$skin_ime_log_dir/device-logcat.txt" || true

skin_ime_pull_rotation_output portrait
skin_ime_pull_rotation_output landscape

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## IME Layout Regression"
    echo
    echo "- Navigation mode: $skin_ime_navigation_mode"
    echo "- Rotations: portrait, landscape"
    echo
    echo '```text'
    if ((skin_ime_overall_status == 0)); then
      echo "Both rotations completed successfully."
    else
      echo "At least one rotation failed. See per-rotation Gradle logs and diagnostics."
    fi
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

exit "$skin_ime_overall_status"
