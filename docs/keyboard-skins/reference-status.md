# 着せ替え / Keyboard skins

## 状態 / Status

**再現精度のゲートは未完了です。Draft PR としてレビューしてください。**
Default、Cupertino Light、Cupertino Dark の切替、描画の分離、保存値の復元を実装しました。
iOS 全体とのピクセル一致やアニメーションのフレーム一致を達成したという意味ではありません。

**The fidelity gate is incomplete. Keep the PR in draft.** Skin selection and the
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
- Default motion remains untouched. Cupertino cancels view-property animation and uses
  immediate state presentation with no Material ripple or popup elevation animation.
  This is a provisional motion implementation, **not a measured iOS timing match**.
- Skin selection disables competing controls in the theme screen without clearing
  them. Input-composition color controls remain independent.

## 未完了の精度検証 / Remaining fidelity gate

Before changing the PR to ready:

1. Compare full popup geometry, baseline, outline and gradient profiles for every
   direction and edge key. Current profiles beyond the measured key corner are
   approximations; no <=1 pt claim is made for them.
2. Record Android and iOS transitions with a shared visual timing marker. Measure
   appearance, movement, selection and dismissal against the <=1-frame criterion.
   Existing capture timestamps do not satisfy this synchronization requirement.
3. Compare typography explicitly. Android uses platform fonts; SF/PingFang assets
   are not bundled. Font metrics/rasterization differences remain.
4. Complete actual-IME screenshot/manual interaction coverage for tenkey, QWERTY,
   gojuon, custom, symbol, floating, landscape, increased font scale and narrow widths.
   Unit and standalone device-render tests do not substitute for this matrix.
5. Exercise restoration of image/video backgrounds and touch effects in the actual
   IME, including switching while a load is pending. Saved-value preservation is
   tested; the complete visual lifecycle is not yet certified.

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
