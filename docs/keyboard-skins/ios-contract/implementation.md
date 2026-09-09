# Continuous popup correction

> 2026-09-09 追記：候補上部の余白を修正し、高さ設定の追加検証と iOS 中断検証を実施。[最新記録](spacing-followup.md)。以下は前回時点の記録を含みます。
Worktree: `MarkdownHelperKeyboard-keyboard-skins`, branch `codex/keyboard-skins`.
The pre-PR Default reference is `bbdc7414746adeffc815d5158631e415a1167007`.

## Reproduced defects and changes

1. Updating the native PopupWindow position and dimensions on every direction
   change caused an approximately 230 ms diagonal movement from the top balloon
   to the right balloon in the actual Pixel 6 recording. A constant transparent
   window now hosts a direction-specific child. Only that child changes bounds;
   the native window stays stationary. This does not add an animation delay.
2. Normal Cupertino flicks erased the original key label. The label remains visible
   and pressed, as observed on the reference. Default's original behavior is retained.
3. Starting a guide now dismisses all separate directional windows. New contacts
   retire previous popup surfaces before starting their own presentation. UP/CANCEL
   and detach restore the key labels and retire the guide.
4. Reusing the candidate TabLayout retained colors from its first inflation.
   Every input session reapplies selected/unselected text and indicator colors;
   switching back restores the inflated Default colors or the saved custom theme.
   Candidate row count, height, font, adapters and input mappings remain shared.
5. Edge testing found two separate placement problems. Drop-down auto-fit moved
   the whole transparent frame at bottom keys. Absolute screen placement removes
   that implicit shift. Actual visible surfaces are then fitted inside system-bar
   and cutout bounds: a directional balloon is fitted individually; a long-press
   cross is fitted as one unit and does not move between selections. This is a
   presentation-only edge rule, including Android's existing bottom alternative
   on わ, which has no corresponding iOS alternative. It does not change IME height.

## Evidence scope

- The 12 candidate configurations are portrait/landscape × 1/2/3 rows × tabs
  OFF/ON; each exercises Default → Light → Dark → Default. Heights were
  110/120/160 dp in portrait and 60/90/120 dp in landscape. Requested rows,
  visibility, immediate tab colors and Default round-trip pixels passed.
- The pre-PR APK and final viewport APK were compared in all 12 Default
  configurations. Empty/composing phases (24 comparisons) have identical measured
  geometry and exact input-area pixels. The host editor must be launched in its
  actual orientation before selecting the test IME (`startHostBeforeIme=true`).
  Otherwise a portrait-locked launcher can cause either APK to inflate portrait
  resources before the editor rotates. Earlier mismatches are retained as test
  setup failures; no Default height or orientation production code was changed.
- The final viewport APK passed 160 forward continuous gestures across both
  appearances, orientations and five keys; 96 additional reverse/boundary/fixed-time
  gestures; and 32 gestures around each platform's actual guide onset. These totals
  include one warm-up and three measured trials per case: 72 warm-ups and 216
  measured Android gestures. All recordings include the final release. Before/after
  frame inspection confirms that the reproduced diagonal native-window travel is
  gone and the bottom-key surfaces fit above system bars.
- Actual IME interactions passed Default → Light → Dark → Default in portrait and
  landscape, including candidate tab selection, expansion/collapse, commit/delete,
  symbols/category/return. Large candidate text (22 sp), three rows and enabled tabs
  also passed. The 258 selected unit tests, three production-view device tests and
  two production drawable export tests passed.
- Static color/contour measurements use lossless production drawable fixtures.
  Compressed movies are used for sequence and position inspection, not RGB claims.
- Input recognition thresholds and character availability remain those of the
  application. Compare presentation for corresponding recognized states; do not
  label different native recognition boundaries as a rendering timing measurement.

APK: `16013b4d0d747edd15ef19fd0b78bbbde757335c69d7d7d4ca425f07323a68da`.
This is the isolated `com.kazumaproject.skinfidelity.lite` build.

The full frozen iOS gate remains open. Simulator UI access has resumed, and public
UIKit keyboard dismissal during a held contact is recorded in [the follow-up](spacing-followup.md).
This does not certify every interruption case or all playback/timing clauses.
These bounded checks do not certify full iOS timing parity. The user subsequently
confirmed long-press behavior and requested a PR update. See [the final review](../review.md)
for the current decision based on the production diff and device regression evidence.
