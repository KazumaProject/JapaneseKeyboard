# 実装レビュー / Implementation review

2026-09-08。`dev` を起点とする差分と今回の修正を自己レビューしました。
合意済みの範囲内で、未解決の修正指摘はありません。独立レビューを受けたとの主張はしていません。

Self-review covers the implementation against `dev`, the follow-up fixes, input
compatibility, lifecycle cleanup and the evidence. No unresolved actionable findings
remain within the agreed scope. No independent reviewer or agent review is claimed.

## 修正・確認事項 / Findings fixed and checked

- Keep the skin preference separate from saved custom colors. Default returns the
  original preference snapshot; unknown identifiers fall back without deleting data.
  Effective overrides change appearance only. Candidate columns, key dimensions,
  input thresholds, character order and selection mapping remain unchanged.
- Pass the skin identifier through normal/floating and tenkey/gojuon/QWERTY/custom/
  symbol call sites, including popup style normalization. Preserve Default rendering
  and independently mutable key drawables; test fresh-view initialization and round trips.
- Keep QWERTY long-press variations in their existing three-column grid, including
  empty cells, dimensions and offsets. Its short visual release timer does not delay
  commits. New input, CANCEL, hiding, skin changes and detach close retained visuals.
- Restore original popup gravity, font padding, size, translation, elevation and
  stateful label colors on return to Default. Weak-cache values do not retain views.
- Draw fitting kana guides in the keyboard window overlay. Keep an overflow window
  for smaller floating/embedded roots. The overlay is visual only; the keyboard owns
  hit testing and commits. Cancel both pending pre-draw listeners and active animation
  frames on replacement/detach. Respect disabled system animations.
- Reserve navigation insets outside configured content height in both layout paths.
  Refresh reused candidate appearance at input start as well as view creation, restore
  each role's original text/tint values, and retain the saved theme context under skins.
  [Actual-IME measurements](ime-layout.md) verify the cause and the fix.
- Use direct display timestamps for motion. Reject missing, duplicate, empty or corrupt
  data. Keep all measured trials and predeclare warm-ups. Start the diagnostic frame
  clock after ActivityScenario launch and stop it before close to avoid its idle barrier.
  Incomplete or software-renderer recordings are not used as successful evidence.

## 検証 / Validation

- 615 related unit tests: app 350, core 30, custom keyboard 194, tenkey 6,
  QWERTY 34, gojuon 1. No failures, errors or skips.
- Six Python verifier tests reject invalid input and out-of-budget comparisons.
- Actual-IME checks cover Default → Light → Dark → Default, with empty, composing
  and committed states, in portrait gesture, portrait three-button and landscape
  three-button navigation. Assert 60/110/60 dp, two candidate rows, appearance and
  input results, plus bottom/side navigation separation.
- Final production-view popup tests pass three cases covering commits, five held
  directions, Default-compatible selection and detach/cancellation cleanup.
- 22 static cases pass whole-outline ≤1 pt and opaque RGB ≤2; maximum outline error
  is 0.943 pt. Final direct motion passes 48 trials / 84 milestones, maximum difference
  below 15.001 ms against a 16.667 ms criterion. See [all evidence](evidence/fidelity/README.md).
- Isolated LiteStandardDebug application and Android-test APKs build successfully.
  The normal installed app is not overwritten.

## 合意済みの範囲 / Agreed scope

フォントデータは追加せず既存の字形を維持します。文字のサイズ・配置と動作は検証対象です。
候補列数と QWERTY の3列選択を含む既存の入力仕様を保ちます。

Existing platform glyphs are retained without font assets. The static and motion
results describe the named reference conditions and measured tolerances, not universal
pixel identity at every OS release, user size or backdrop. Final motion is verified on
the host-GPU API 35 emulator; earlier physical Pixel 6 captures are not mislabeled as
verification of the final curve after that device disconnected.
