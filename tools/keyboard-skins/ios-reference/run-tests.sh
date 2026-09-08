#!/bin/bash
set -euo pipefail
reference_dir="$(cd "$(dirname "$0")" && pwd)"
reference_root="$(cd "$reference_dir/../../.." && pwd)"
reference_device="${1:?Usage: run-tests.sh SIMULATOR_UDID RUN_NAME}"
reference_run="${2:?A unique run name is required}"
reference_products="$reference_root/build/keyboard-skins/xcode-products"
reference_results="$reference_root/build/keyboard-skins/$reference_run.xcresult"
python3 "$reference_dir/make-project.py"
xcodebuild build -project "$reference_dir/KeyboardReference.xcodeproj" \
  -target ReferenceUITests -sdk iphonesimulator -configuration Debug \
  CONFIGURATION_BUILD_DIR="$reference_products" CODE_SIGNING_ALLOWED=NO ARCHS=arm64
reference_platform="$(xcode-select -p)/Platforms/iPhoneSimulator.platform/Developer"
cp "$reference_platform/usr/lib/lib_TestingInterop.dylib" "$reference_products/ReferenceUITests-Runner.app/Frameworks/"
cp "$reference_platform/usr/lib/libXCTestSwiftSupport.dylib" "$reference_products/ReferenceUITests-Runner.app/Frameworks/"
cp -R "$reference_platform/Library/Frameworks/"_Testing_*.framework "$reference_products/ReferenceUITests-Runner.app/Frameworks/"
python3 - "$reference_products" <<'PY'
import plistlib, sys
from pathlib import Path
root = Path(sys.argv[1])
target = dict(BlueprintName='ReferenceUITests',
    TestBundlePath=str(root/'ReferenceUITests-Runner.app/PlugIns/ReferenceUITests.xctest'),
    TestHostPath=str(root/'ReferenceUITests-Runner.app'),
    UITargetAppPath=str(root/'KeyboardReference.app'), IsUITestBundle=True,
    TestHostBundleIdentifier='com.kazumaproject.keyboard-skins.reference.uitests.xctrunner',
    UITargetAppBundleIdentifier='com.kazumaproject.keyboard-skins.reference')
(root/'Reference.xctestrun').write_bytes(plistlib.dumps(dict(
    __xctestrun_metadata__=dict(FormatVersion=2),
    TestConfigurations=[dict(Name='Reference',IsEnabled=True,TestTargets=[target])]
)))
PY
xcrun simctl install "$reference_device" "$reference_products/KeyboardReference.app"
xcrun simctl terminate "$reference_device" com.kazumaproject.keyboard-skins.reference.uitests.xctrunner 2>/dev/null || true
xcrun simctl install "$reference_device" "$reference_products/ReferenceUITests-Runner.app"
if [[ "${IOS_REFERENCE_SKIP_VIDEO:-0}" != "1" ]]; then
  xcrun simctl io "$reference_device" recordVideo --codec="${IOS_REFERENCE_CODEC:-h264}" "$reference_root/build/keyboard-skins/$reference_run.mov" &
  reference_video_pid=$!
  trap 'kill -INT "$reference_video_pid" 2>/dev/null || true; wait "$reference_video_pid" 2>/dev/null || true' EXIT
fi
xcodebuild test-without-building -xctestrun "$reference_products/Reference.xctestrun" \
  -destination "platform=iOS Simulator,id=$reference_device" -parallel-testing-enabled NO \
  -resultBundlePath "$reference_results" ${3:+-only-testing:ReferenceUITests/ReferenceUITests/$3}
xcrun xcresulttool export attachments --path "$reference_results" \
  --output-path "$reference_root/build/keyboard-skins/$reference_run-attachments"
