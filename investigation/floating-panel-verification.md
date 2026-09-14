# Floating / split keyboard verification

Worktree: `MarkdownHelperKeyboard-floating-design`
Branch: `codex/floating-keyboard-design`
Base: local `dev` at `ba79b92ef95dfdcb5802711746b8909324cb2227`
Date: 2026-09-14

## Changes

- Floating keyboards now reuse split keyboards' `FloatingPanelFrame`: rounded border, theme colors, edit/check button, bottom movement grip and four resize handles.
- Existing keyboard content, candidates, symbol input, custom backgrounds and position/size preferences remain connected to the service. Keyboard layouts scale inside the shared body when narrowed.
- The floating window uses the same IME-attached `WindowManager` approach as split panes. This fixes incorrect screen/accessibility bounds on old Android popup decor and keeps saved positions stable after reopening.
- Old Android IME windows sometimes return empty stable insets. The visible display frame now also constrains panel placement, preventing split panes from overlapping the navigation bar. Instrumentation asserts this separation.
- Fixed pre-API-29 touch coordinates for scaled TenKey and Gojuon layouts. The old calculation added unscaled local coordinates to the screen origin, selecting the wrong row on small landscape keyboards. TenKey flick/cursor movement now uses consistent screen coordinates as well.

## Automated checks

`LiteStandardDebug` and its instrumentation APK build successfully. Related Robolectric tests: **58 passed, 0 failures, 0 skipped**. They cover panel edit chrome, cancellation, window lifecycle, coordinates, split layout and edit state.

Build and run on an attached emulator/device:

```sh
./gradlew -I investigation/floating-panel.init.gradle \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
python3 investigation/run_floating_panel_matrix.py DEVICE_SERIAL
./gradlew -I investigation/floating-panel.init.gradle \
  :app:testLiteStandardDebugUnitTest --tests '*Floating*Test' \
  --tests '*SplitKeyboard*Test' --tests '*KeyboardLayoutEditStateTest'
```

The init script installs a separate `com.kazumaproject.floatingqa.lite` application. Tests only reset that app's preferences; the original IME and device rotation are restored. Testing uses the common keyboard implementation in the Lite flavor; it does not exercise full-flavor AI features.

## Device matrix

Each standard scenario uses actual injected touch events: display, text input, candidate commit, movement, edit/resize, dismissal/reopening, and width/position persistence. Floating TENKEY also checks the exact first character (`あ`) and symbol insertion/return. Split scenarios exercise both panes' input and a pane's movement/resize. Portrait and landscape are separate runs.

| Android API | Device | Split portrait / landscape | Floating portrait / landscape |
|---|---|---|---|
| 24 | [arm64 emulator](../build/reports/floating-panel/emulator-5574-api24/) | PASS / PASS | PASS / PASS |
| 25 | [arm64 emulator](../build/reports/floating-panel/emulator-5582-api25/) | PASS / PASS | PASS / PASS |
| 26 | [arm64 emulator](../build/reports/floating-panel/emulator-5568-api26/) | PASS / PASS | PASS / PASS |
| 27 | [arm64 emulator](../build/reports/floating-panel/emulator-5578-api27/) | PASS / PASS | PASS / PASS |
| 28 | [arm64 emulator](../build/reports/floating-panel/emulator-5580-api28/) | PASS / PASS | PASS / PASS |
| 29 | [arm64 emulator](../build/reports/floating-panel/emulator-5572-api29/) | PASS / PASS | PASS / PASS |
| 30 | [arm64 emulator](../build/reports/floating-panel/emulator-5570-api30/) | PASS / PASS | PASS / PASS |

[Pixel 6](../build/reports/floating-panel/23241FDF6003NG-api37/) (physical device, API 37): floating TENKEY portrait with image/video backgrounds, QWERTY landscape with Cupertino dark skin, GOJUON and custom layout portrait passed. ROMAJI/Cupertino light and SUMIRE portrait also passed.

[Additional API 28 runs](../build/reports/floating-panel/emulator-5560-api28/): landscape GOJUON and QWERTY/Cupertino light also passed.

## Evidence

Per-device instrumentation output, screenshots, accessibility bounds and window dumps are under [`build/reports/floating-panel`](../build/reports/floating-panel/). Unit test report: [`app/build/reports/tests/testLiteStandardDebugUnitTest/index.html`](../app/build/reports/tests/testLiteStandardDebugUnitTest/index.html).

These are focused keyboard interaction regressions, not exhaustive testing of every device, application, gesture or preference combination.
