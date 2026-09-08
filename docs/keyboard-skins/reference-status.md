# 着せ替え / Keyboard skins

## 状態 / Status

**再現精度は未達です。字形の差が測定されているため、Draft PR としてレビューしてください。**
Default、Cupertino Light、Cupertino Dark の切替、描画の分離、保存値の復元を実装しました。
iOS 全体とのピクセル一致やアニメーションのフレーム一致を達成したという意味ではありません。

**Full fidelity is unmet: glyph differences are measured. Keep the PR in draft.** Skin selection and the
presentation integration are implemented; full iOS pixel/motion parity is not certified.

## 参照条件 / Reference conditions

- Base: `dev`, `bbdc7414746adeffc815d5158631e415a1167007`.
- iPhone 17 Pro Max simulator, iOS 26.4.1 (23E254a), 1320 × 2868 pixels,
  440 × 956 logical points, scale 3.
- Dedicated simulator: `KeyboardSkinReference`,
  `F12E79DA-7D73-42BB-BE94-69FE135B72A9`.
- Xcode 26.6 (17F113); build SDK iOS 26.5. The **runtime**, not the SDK, defines the reference.
- Standard UIKit `UITextField`, default keyboard traits; Japanese Kana and English (US).
  Appearance is explicitly Light or Dark; no custom iOS keyboard or private API.
- Capture date: 2026-09-07. Successful XCUITest runs: `configure-us`,
  `kana-gestures`, `reference-matrix` (kana evidence only), `english-reference`.
- English images from the first `reference-matrix` run are excluded: an onboarding
  sheet covered the keyboard. `english-reference` dismisses it and checks hittability.
- PNG stills are lossless. H.264 recordings are used for observing sequence only,
  **never** for sampling colors. One-second delayed captures occur inside long holds;
  they do not provide synchronized touch-to-frame latency measurements.

## 実測値 / Measurements

| Surface | Light | Dark |
| --- | --- | --- |
| Keyboard background | `#E2E4E8` | `#171717` |
| Normal / special key | `#FFFFFF` | `#3D3D3D` |
| Kana guide selected cell | `#0088FF` | `#0091FF` |
| Pressed origin (representative interior sample) | `#E7E8EC` | `#262626` |
| Dimmed kana guide labels | `#737373` | `#545454` |

Kana あ hit rectangle is x=90.7, y=662, w=86.3, h=56 pt. Its visible surface
is sampled at x=281, y=1995, w=241, h=147 **pixels**, without resizing.

The standalone Android fixture uses the same surface dimensions and density 3,
separately from the actual IME layout. On the connected Pixel 6 / Android 17,
opaque sample RGB error is **0** for both appearances. Upper-left corner maximum
error is **1.0 pt** for each appearance after calibration (mean 0.324 pt light,
0.269 pt dark). See [machine-readable measurements](evidence/surface-comparison.json)
and [comparison script](../../tools/keyboard-skins/compare-surfaces.py).

This checks a 12 pt corner contour, not the whole key, text, keyboard or popup.
The mask uses RGB tolerance 2 for the reference and alpha >=253 for Android;
antialiasing and the dark background affect the boundary. It is not a whole-image
MAE score and must not be presented as whole-keyboard acceptance.

![Kana light reference](evidence/ios-light-kana-hold.png)
![Kana dark reference](evidence/ios-dark-kana-hold.png)
![English US dark preview](evidence/ios-dark-qwerty-preview.png)
![English US light variation](evidence/ios-light-qwerty-hold.png)

## 実装 / Implementation

- `KeyboardSkinId`: stable persisted identifiers; absent/unknown identifiers resolve
  to Default without deleting saved values.
- `KeyboardSkinRegistry`: returns the renderer for each skin. Default returns null,
  delegating to the existing visual implementation. Add future renderers here.
- `KeyboardSkin`: owns keyboard/key/popup drawables and presentation-motion hooks.
  Key drawables have independent constant-state instances; changing one key's pressed
  state cannot change another key.
- `ImePreferencesSnapshot.withKeyboardSkinAppearance()`: derives effective colors,
  border, alpha and touch-effect settings from the saved snapshot. It never persists
  overrides. Candidate column counts, dimensions, thresholds and input settings are
  unchanged. Images/videos are suppressed through the existing cleanup/request-id path.
- Tenkey, gojuon, QWERTY, custom layouts and symbol keyboard receive the skin identifier.
  Normal/floating IME configuration paths share these entry points.
- Popups carry `skinId` through style normalization. Kana uses separate cross cells
  and directional pointers; QWERTY uses a cap/neck/stem preview and flat selected cells.
  Custom circular/TFBi guides retain their selections and content, using skin surfaces.
  Those layouts have no native iOS equivalent and are adaptations.
- Default motion remains untouched. Cupertino removes Material ripple/elevation animation,
  updates visible flick windows in place, and holds released QWERTY windows for 75 ms
  while committing immediately. Timing is measured separately in the follow-up evidence.
- Skin selection disables competing controls in the theme screen without clearing
  them. Input-composition color controls remain independent.

## 全ポップアップと動作の追試 / Full-popup and motion follow-up

The follow-up compares lossless iOS captures with native Android production drawables,
including the whole cap, neck, stem, directional arrow, guide cross, and placed glyphs.
Reference and Android images are aligned by their key anchors only: there is no image
rescaling or best-fit registration. Results are in [fidelity evidence](evidence/fidelity/README.md).
The isolated fixture pins density to 3 and font scale to 1; the actual-view motion host
uses the Pixel 6's existing density/font-scale settings. Those are different tests.

- QWERTY long-press variations retain **three columns, character order, window dimensions,
  offsets and hit mapping**. The iOS single row is intentionally not adopted. Candidate
  settings and long-press input thresholds are unchanged.
- Cupertino QWERTY release holds affect the window only. Input ownership and text commits
  end on UP. Kana guides dismiss immediately, independently of surrounding-label fades.
  New gestures, CANCEL, skin switches, hiding and detach clear retained presentations.
- Motion uses a visible display clock and event serial, video presentation timestamps,
  repeated gestures and explicit transition brackets. Candidate-text changes are excluded
  from the iOS dismissal ROI. Capture gaps and clock uncertainty remain in the report;
  missing frames are not classified as a pass.
- Full glyph masks are measured separately from glyph bounding boxes. Similar bounding
  boxes do **not** establish identical typography. Android platform fonts differ from
  the reference. No Apple fonts or extracted glyph assets are shipped.
- Apple's [published font license](https://developer.apple.com/fonts/) does not grant
  embedding the distributed Apple font in an Android app. Shipping identical font data
  would require separately authorized assets; a geometry fix does not resolve this.

### 再現の限界 / Acceptance limits

The finite portrait reference matrix is not proof of identical rendering at every
keyboard width, system font setting, orientation, underlying app background or OS release.
The dark material fields approximate the captured lighting; they do not implement iOS's
compositor. Existing Android layout/selection behavior is deliberately preserved.
Typography and any motion intervals wider than the acceptance window must remain explicit
failures or inconclusive results. Do not mark full iOS parity as complete from build/test
success, fitted RGB samples or a small outline error.

## 検証 / Validation

Commands use JDK from Android Studio and the local Android SDK:

```sh
./gradlew :core:testDebugUnitTest :custom_keyboard:testDebugUnitTest \
  :tenkey:testDebugUnitTest :qwerty_keyboard:testDebugUnitTest :gojuon_keyboard:testDebugUnitTest
./gradlew :app:testLiteStandardDebugUnitTest --tests '*KeyboardSkin*' \
  --tests '*Theme*' --tests '*KeyboardGuide*' --tests '*KeyboardDisplayResolver*'
./gradlew :app:assembleLiteStandardDebug
./gradlew :core:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.core.KeyboardSkinDeviceRenderTest
python3 tools/keyboard-skins/compare-surfaces.py
```

Use `-Pkotlin.compiler.execution.strategy=in-process` if the local Kotlin daemon
cannot connect. The device fixture installs only the core instrumentation package;
it does not install/select the user's IME or change keyboard settings.
