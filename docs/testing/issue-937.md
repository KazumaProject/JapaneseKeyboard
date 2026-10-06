# Issue #937: orientation-specific position and size preview

## Historical investigation

- [Issue #937](https://github.com/KazumaProject/JapaneseKeyboard/issues/937)
  reports that editing landscape position/size while the device is in portrait
  uses an unexpected preview position. The reverse orientation needs the same fix.
- Commit [`49cf94d8`](https://github.com/KazumaProject/JapaneseKeyboard/commit/49cf94d84cdfe51dcafe1e3da62a911ec2826fb8),
  merged in [PR #390](https://github.com/KazumaProject/JapaneseKeyboard/pull/390)
  on 2025-10-16 JST, added forced landscape orientation in `onResume()` and
  reset orientation to `UNSPECIFIED` in `onPause()`. The landscape candidate
  height editor also requested landscape orientation.
- Commit [`4d108168`](https://github.com/KazumaProject/JapaneseKeyboard/commit/4d1081685250ce6167794b2c32fa1788763ab401),
  merged in [PR #469](https://github.com/KazumaProject/JapaneseKeyboard/pull/469)
  on 2025-11-04 JST, removed these lifecycle hooks and orientation requests.
- Neither PR contains comments or reviews describing the failing devices or
  exact symptoms. The owner's [comment on #937](https://github.com/KazumaProject/JapaneseKeyboard/issues/937#issuecomment-5471014248)
  confirms device-dependent behavior as the reason for abandoning forced rotation,
  but says the details are no longer remembered. The exact failure is unknown.
- Searches of all local history for `requestedOrientation` / `SCREEN_ORIENTATION`
  and upstream/fork PR searches for #937, orientation, landscape, screenOrientation,
  requestedOrientation and Japanese rotation terms found no directly relevant
  unmerged fix. [PR #943](https://github.com/KazumaProject/JapaneseKeyboard/pull/943)
  changed settings navigation/action bars, rather than preview geometry.

## Approach and acceptance criteria

Render an orientation-specific logical canvas inside the existing settings
screen. Scale the preview uniformly, leaving keyboard dimensions and saved dp
values in logical coordinates. Never request activity rotation. The opposite
orientation is a simulation based on the current window dimensions; it cannot
predict every device's future window shape, system inset or fold state.

- Portrait and landscape editors use their own width/height coordinate systems.
- Width loading, dragging, bounds and saving use the same reference width.
- Touch movement is converted back from rendered pixels to logical pixels.
- Opening the editor, changing pages, rotating/resizing the window, or touching
  a handle without movement does not rewrite preferences.
- TenKey/QWERTY and portrait/landscape settings remain independent.
- Cancelling a gesture restores the saved preview without saving the gesture.
- Resize and move handles remain reachable in the scaled preview.

## Focused automated checks

The `keyboard_size_setting.preview` test package contains:

- `KeyboardPreviewGeometryTest`: target orientation, square windows, scale,
  inverse drag coordinates, width percentage round trips and display-only
  projection of excessive saved margins.
- `KeyboardPreviewViewportTest`: real settings-layout inflation, logical canvas
  measurement, separated resize strips and touch delivery to all five handles
  in normal and narrow windows.
- `KeyboardSizeEditorRegressionTest`: real activity/navigation, unchanged
  preferences across both orientations and keyboard types, taps/cancelled
  gestures, returning from numeric input and saving a scaled height drag before
  another layout pass.

```powershell
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests 'com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview.*' --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
```

Verified on 2026-10-04 JST: application compilation and all 12 focused tests
passed (6 geometry, 2 viewport, 4 activity/editor regression tests; no failures,
errors or skipped tests). Physical-device checks have not been run.

Device-specific orientation requests are deliberately absent. These headless
tests do not replace the device checks below for system insets or fold states.

## Manual device checks

1. With rotation lock enabled, open the landscape editor in portrait and the
   portrait editor in landscape. The activity orientation should remain unchanged.
2. For both TenKey and QWERTY, drag each size handle, move the keyboard, switch
   alignment, visit numeric input and return to the editor. Numeric input should
   show the edited dp/%, and returning should restore the full preview canvas.
3. Open/switch pages/leave without dragging. Confirm all four settings slots keep
   their previous values, including unusually large saved margins.
4. Cancel a drag, and resize/rotate the window. Confirm no uncommitted values save.
5. Check a small phone, a large-screen device and split-screen mode. The opposite
   orientation preview is an estimate; actual IME geometry must be checked in the
   target window when exact placement matters.
