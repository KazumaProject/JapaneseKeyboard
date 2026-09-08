# ポップアップの比較証拠 / Popup fidelity evidence

2026-09-07. iOS 26.4.1 (23E254a), iPhone 17 Pro Max simulator, 1320×2868 at scale 3.
Android production-drawable fixtures: Pixel 6, Android 17, explicitly density 3 / font scale 1.
Final actual-view motion uses the hardware-rendered API 35 emulator at density 420 / font scale 1.3; see the motion section.

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

**既存フォントを維持し、フォントデータを追加しません（2026-09-08 の依頼者指定）。**
字形の一致は対象外です。文字サイズ・ベースライン・位置は引き続き調整・検証対象です。

**Existing platform fonts are retained without additional font assets**, per the requester
on 2026-09-08. Glyph-outline identity is out of scope. Glyph masks remain diagnostic only;
the recorded measurements are unchanged. Bounding-box differences range up to 1.333 pt.
Text size, baseline and placement remain calibration and verification targets.

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

Production-view tests verify that QWERTY commits happen while the short visual hold is
still showing, while kana commits dismiss the guide immediately. Detach closes retained
QWERTY windows and cancels kana label restoration without another commit.
Unit tests cover Default restoration of popup presentation and cancellation of label fades.

The retained device captures show the actual existing e-key alternatives in three columns:
[Light](android-light-three-column-variations.png) / [Dark](android-dark-three-column-variations.png).
They are adaptations with the existing character content, not the iOS alternative-character row.

## 動作 / Motion

2026-09-08 の最終比較は **48/48 試行・84/84 変化点が合格**しました。
ガイド・フリック・QWERTY の消去、および長押し文字の10%・50%・90%変化点を
全試行で比較し、最大差は **15.001 ms 未満**（基準: 60 Hz の1フレーム、16.667 ms）でした。
平均値による合格判定や、測定後の試行除外は行っていません。

The final direct-timestamp matrix passes all **48 trials / 84 milestones**, with a
maximum first-observed-frame difference of **15.001 ms** rounded upward. The criterion
is one 60 Hz frame (16.667 ms). Every trial is retained; warm-ups were declared before
capture. This is a finite measured tolerance, not pixel or universal frame identity.

- [All-trial comparison](direct-motion-comparison.json)
- [iOS frames, events and transition brackets](verified-direct-ios.json)
- [Android frames, events and transition brackets](verified-direct-android.json)
- [Capture/source hashes and environment](direct-motion-source-manifest.json)

The iOS reference is the native UIKit keyboard in the iOS 26.4.1 simulator. A macOS-only
CoreSimulator diagnostic samples its display callbacks on the same monotonic clock as
logged touch dispatch: 8,666 callbacks, no missing records, p99 sample cost 2.160 ms.
Private CoreSimulator APIs are confined to that host diagnostic; neither app links them.
Android uses the original Winscope v2 display timestamps embedded by screenrecord:
4,526 source frames and 4,526 decoded frames, with per-event clock-domain conversion.
There is no fitted MP4 PTS/marker-clock offset in this final comparison.

The final Android motion environment is the API 35 Pixel_6_Pro emulator using the host
Apple M2 GPU, 1080×2400, density 420, font scale 1.3 and 60 Hz. Earlier Pixel 6 recordings
are retained locally, but are **not** presented as verification of the final animation:
the physical device disconnected during follow-up. The software-GPU emulator recording
had insufficient frame cadence and is also excluded from final acceptance.

The matrix covers a kana hold, four true short flicks and QWERTY q/e/p, each in Light
and Dark with three trials. Dimming is compared relative to guide appearance because
existing long-press recognition settings remain active. Dismissal and label restoration
are compared to actual UP dispatch. Full source intervals and label curves remain in
the reports; missing/duplicate/empty data fail rather than becoming a pass.

Fitting guides use the keyboard window's overlay, avoiding separate guide-surface
creation/retirement; floating overflow retains a non-touchable PopupWindow. Label colors
use a monotonic presentation clock, independent of input ownership. The measured
restoration curve separates its small initial change from its slower return.
QWERTY retains a 34 ms visual timer in addition to platform presentation latency;
character commits still occur immediately on UP. Default presentation is unchanged.

Older PTS-based reports in this directory remain historical diagnostics. Their clock
uncertainty and the subsequently corrected animation parameters do not describe this
final result. The incomplete 120-second v18 recording is not an accepted capture: the
matrix outlasted it while ActivityScenario waited on the diagnostic clock. Starting
the clock after launch and stopping it before close fixed that harness issue; the
final complete matrix took 75.803 seconds.

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
