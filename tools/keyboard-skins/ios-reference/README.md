# iOS system-keyboard reference harness

This app contains a standard UIKit text field and Light/Dark controls. Reference
images come from the system keyboard. Use a **dedicated** simulator with Japanese
Kana and English (US) enabled and the hardware keyboard disconnected.

On Apple Silicon with Xcode 26.6, build and capture:

```sh
# Only needed to add English (US) on a fresh simulator; changes that simulator's settings.
tools/keyboard-skins/ios-reference/run-tests.sh DEVICE configure-us testConfigureEnglishUS
python3 tools/keyboard-skins/ios-reference/capture-held.py DEVICE canonical-run testControlledReference
python3 tools/keyboard-skins/ios-reference/capture-held.py DEVICE center-run testCenterPreviews
tools/keyboard-skins/ios-reference/run-tests.sh DEVICE motion-run testQwertyMotionContrast
```

Replace DEVICE with the dedicated simulator's UDID and use unique run names.
The scripts build/install `com.kazumaproject.keyboard-skins.reference`, run
XCUITest, record video, export XCTest attachments and save PNG stills during holds.
They leave the reference app and selected keyboard language on that simulator.
A failed screenshot causes a nonzero exit status. Inspect every still before using
it: a test may succeed while an onboarding sheet or a transition affects evidence.

`run-tests.sh` builds the test target against the installed SDK, then uses an
explicit `.xctestrun` to select the 26.4.1 runtime. This avoids requiring the SDK's
matching runtime merely to build. The testing support libraries are copied from
local Xcode into the ignored runner artifact; they are not committed or distributed.
`build.sh` optionally builds only the reference app without XCUITest.

Outputs are ignored under `build/keyboard-skins/`. Preserve lossless PNGs for color
measurements; video compression changes colors. Delayed hold captures are suitable
for stable states, not synchronized latency measurements.

`testControlledReference` launches a fresh app for each appearance, seeds one lowercase
character and retains default UIKit keyboard traits. It exports paired before/held
images. `testCenterPreviews` uses short holds for e/a/l so the variation chooser cannot
replace the preview. The capture wrapper rejects missing expected image counts.

The motion tests draw a binary CADisplayLink clock and input-event serial, and write
touch timestamps through public UIKit APIs. Copy `Documents/touch-events-Light.json`
and `touch-events-Dark.json` from the current reference app container after the contrast
run. Retrieve its current location with `simctl get_app_container`; installing a fresh
build can change the container path. Use cap-only ROIs on the fixed gray backdrop,
above the candidate strip, when measuring QWERTY dismissal. The earlier white-background
run can confound disappearance with changing candidate text and must not replace this run.

The harness deliberately leaves font specimens in ignored local output. They diagnose
platform metrics; neither extracted fonts nor specimen glyph images are production assets.

See [reference conditions and acceptance gate](../../../docs/keyboard-skins/reference-status.md).
