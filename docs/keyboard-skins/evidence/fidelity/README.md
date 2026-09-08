# ポップアップの比較証拠 / Popup fidelity evidence

2026-09-07. iOS 26.4.1 (23E254a), iPhone 17 Pro Max simulator, 1320×2868 at scale 3.
Android production-drawable fixtures: Pixel 6, Android 17, explicitly density 3 / font scale 1.
Actual-view motion uses the device's existing density 420 / font scale 1.3.

## 静止画 / Static states

[Measurements](measurements.json) cover 22 cases: four kana flick directions, the
selected center and complete guide, plus QWERTY q/p/e/a/l previews, each in Light/Dark.
All 22 meet the **chosen geometric tolerance** (maximum whole-outline distance ≤1 pt)
and opaque interior RGB tolerance (maximum difference ≤2). The observed maximum outline
difference is 0.943 pt. This is not pixel equality.

Each case has `-ios.png`, `-android.png` and `-difference.png`. Difference images include
missing surface pixels and placed glyphs; they are not registered or scaled to improve
agreement. The native alpha mask is compared with lossless reference silhouettes. Three
pixels around edges and glyph antialiasing are excluded only from the interior color test.
Paired dark before/held captures resolve corners that overlap indistinguishable white
backgrounds in the light captures. See the verifier for the exact masks and anchor positions.

**Glyph masks do not match exactly.** Bounding-box errors range up to 1.333 pt and
shape differences remain even where the bounds agree. Full typography is a failed criterion,
not an unperformed test. Platform-font substitutions are not called identical iOS fonts.

The fitted dark fields approximate the reference material and lighting. Fitting and
spatial held-out residuals do not certify an unseen device, backdrop or geometry. The
lower-row field interpolates between measured left/right positions; iOS compositor
behavior is not available in the Android renderer.

## 入力互換性 / Input compatibility

QWERTY long-press variations retain the existing **three columns, order, dimensions,
offsets and position-to-character mapping**, including empty-cell handling. The iOS
single-row variation arrangement is deliberately not copied. Its container uses a dedicated
10 pt corner profile and dark lighting field; the fitted field has a spatial held-out
maximum residual of 3.37 RGB units ([fit diagnostics](variation-fit.json)). This adapted
three-column container is not part of the 22 density-matched silhouette cases. Candidate columns and
input thresholds are unchanged. Default delegates to the legacy renderer.

Actual-device tests verify that QWERTY commits happen while the short visual hold is
still showing, while kana commits dismiss the guide immediately. Detach closes retained
QWERTY windows and cancels kana label restoration without another commit.
Unit tests cover Default restoration of popup presentation and cancellation of label fades.

The final device captures retain the actual existing e-key alternatives in three columns:
[Light](android-light-three-column-variations.png) / [Dark](android-dark-three-column-variations.png).
They are adaptations with the existing character content, not the iOS alternative-character row.

## 動作 / Motion

The motion verifier retains PTS, visible-clock calibration residuals, repeated trials,
event-relative transition brackets and marker-relative intervals. It excludes the next
activity from the dismissed-state baseline and candidate changes from the iOS QWERTY ROI.
A missing or wide interval is not a successful frame-match result.

Committed timing reports:

- [Android: 30 kana gestures](verified-android-kana-v12.json), including six holds.
- [iOS: kana holds/flicks](verified-ios-kana-motion.json), with the guide's selected cell
  measured independently of surrounding labels.
- [Android: 18 QWERTY gestures](verified-android-motion-v8.json) and
  [iOS: 18 QWERTY gestures](verified-ios-contrast-motion.json), three trials per key/mode.
- [iOS label fade-in](ios-label-fade-down.json), [fade-out](ios-label-fade-up.json),
  [Android fade-in](android-label-fade-down.json), [fade-out](android-label-fade-up.json).
  These normalized video intensities measure motion, not absolute colors. Android label
  curves use v11 (same label animator as final v12); v12 removes the incorrect guide hold.

The large initial kana ROI conflated fading labels with a retained guide. The corrected
selected-cell metric and a separate guide-edge strip show that the iOS guide itself
vanishes immediately (the inspected Light trial brackets 16.3–19.7 ms after UP), while
labels keep returning to their original colors. The final Android guide also dismisses
immediately; its six hold trials are measured in the report above. QWERTY retains its
separately measured 75 ms visual hold.

The observed QWERTY marker-relative disappearance intervals commonly overlap or differ
by around one display frame, but not every interval establishes the ≤1-frame criterion:
there is a 76.7 ms capture gap in one iOS trial and clock uncertainty of roughly 10–18 ms.
An independent [HEVC recapture](verified-ios-hevc-motion.json) contains another 18 trials;
it still has roughly 20 ms clock uncertainty and does not eliminate the measurement limit.
**Exact animation/frame parity is not certified.** Every transition bracket is retained;
no favorable-trial filtering or averaged pass claim is made.

The existing input thresholds remain active. DOWN-to-long-press timing therefore cannot
be described as identical for arbitrary user settings. The skin controls visible holds
and label transitions independently of character commit and pointer ownership.

## 再測定 / Reproduce

Generate lossless reference captures using the [iOS harness](../../../../tools/keyboard-skins/ios-reference/README.md).
Build/install the isolated core instrumentation APK and run
`com.kazumaproject.core.KeyboardPopupFidelityTest`. Pull its `files/popup-fidelity`
directory into a new directory (pulling into an existing directory can nest it).

```sh
python tools/keyboard-skins/verify-popups.py --reference REFERENCE_DIR \
  --android ANDROID_FIXTURE_DIR --output OUTPUT_DIR
python tools/keyboard-skins/verify-motion.py --platform android \
  --video RECORDING.mp4 --gestures FILES_DIR/motion/gestures.json --output motion.json
```

Use an isolated application ID for the actual-view instrumentation host, never overwrite
the user's installed keyboard. `SkinPopupMotionInstrumentedTest` drives production views
without changing the default IME. Pass `captureStills=false` during short-preview timing:
a synchronous screenshot can extend a press across the existing long-press threshold.

[Source hashes](source-manifest.json) identify the local lossless originals and recordings.
Raw full-screen videos remain in ignored build output; cropped comparison evidence is
committed here. Python tools require Pillow, NumPy and SciPy; motion additionally requires PyAV.
