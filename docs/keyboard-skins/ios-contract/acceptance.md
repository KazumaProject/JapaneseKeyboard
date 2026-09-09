# iOS comparison gate — defined before product changes

> 2026-09-09 追記：候補上部の余白を修正し、高さ設定の追加検証と iOS 中断検証を実施。[最新記録](spacing-followup.md)。以下は前回時点の記録を含みます。
Reference: dedicated KeyboardSkinReference simulator, iOS 26.4.1 (23E254a),
standard Japanese Kana keyboard, Light and Dark. Android: physical Pixel 6 IME.
No product popup change is permitted until the observed-state contract is populated.
Earlier motion summaries do not establish continuous-direction behavior.

## Measurements

- Record DOWN, every MOVE, UP/CANCEL, timestamps and the complete display sequence.
- Key-relative coordinates: origin at the pressed key's top-left; lengths divided
  by its width/height. Do not align each popup separately or fit its movement away.
- Each observed state specifies visible surfaces, glyph, selection, original-key
  label, outline, attachment, normalized bounds, baseline, fill, shadow and alpha.
- Each transition specifies which surfaces change, which stay stationary, and its
  first/last visible frame. Input recognition thresholds remain the Android values.
- Edge positions and landscape are separate cases, not inferred from center keys.

## Acceptance (frozen before product edits)

- Same state sequence, selected text and surface lifetime as the reference.
- No unintended doubled surface, missing state, stale surface or revival after UP.
- Corresponding geometry differs by at most 2% of the anchor-key dimension;
  silhouette intersection-over-union at least 95%, after key-relative normalization.
- Solid colors differ by at most 3 per RGB channel in lossless images. Video is not
  used for absolute color. Preserve existing fonts; evaluate baseline/size/clipping
  separately from platform glyph differences.
- Corresponding presentation transitions differ by at most one frame of the slower
  measured capture. Keep source frame intervals. Missing/discontinuous timestamps
  mean inconclusive, not success. Recognition delay is reported separately.
- Watch all continuous paths at normal and slow speed. An unreferenced movement or
  overlap fails even when aggregate metrics pass. Never average away a failed trial.
- Declare one warm-up and three measured trials per case before capture. Retain
  failures and missing cases. A changed threshold needs a documented measurement
  reason before implementation; it cannot be loosened to accommodate the result.

## Case families

Tap/release; hold/release; four immediate directions; return to center; opposite
and adjacent directions; up-right-left-down-center and reverse; boundary oscillation;
movement immediately before/after guide appearance; cancellation and repeated input.
Apply to Light/Dark, portrait/landscape, center/left/right/top/bottom keys. Where a
key has fewer alternatives, record its actual behavior instead of inventing one.

## Tooling limitation

The discovery harness adds continuous pointer paths to XCTest. Its private input
interfaces are confined to the diagnostic runner; the reference app still uses an
unmodified UIKit system keyboard. API provenance:
https://github.com/appium/appium-xcuitest-driver/blob/master/docs/guides/input-events.md

Status: implementation and the bounded device checks are recorded in results.md.
The Mac UI restriction has been resolved. A public UIKit keyboard-dismissal interruption
has now been recorded; see spacing-followup.md. The full gate is still not certified:
that bounded interruption check does not cover every cancellation family or complete
the normal/slow playback requirement. No acceptance criterion has been loosened.
