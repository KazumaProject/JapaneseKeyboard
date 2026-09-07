#!/bin/bash
set -euo pipefail
reference_dir="$(cd "$(dirname "$0")" && pwd)"
reference_root="$(cd "$reference_dir/../../.." && pwd)"
reference_output="$reference_root/build/keyboard-skins/KeyboardReference.app"
reference_sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
reference_arch="$(uname -m)"
mkdir -p "$reference_output"
xcrun --sdk iphonesimulator swiftc \
  -sdk "$reference_sdk" -target "${reference_arch}-apple-ios26.0-simulator" \
  -framework UIKit "$reference_dir/main.swift" -o "$reference_output/KeyboardReference"
cp "$reference_dir/Info.plist" "$reference_output/Info.plist"
codesign --force --sign - "$reference_output"
printf '%s\n' "$reference_output"
