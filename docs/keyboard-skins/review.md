# 実装レビュー / Implementation review

Self-review against `dev` covered saved preferences, every IME theme call site,
style normalization, drawable state isolation and popup lifecycle. No independent
reviewer or agent review is claimed.

## 修正した問題 / Findings fixed

- First launch directly into a skin attempted to dismiss uninitialized tenkey
  popup windows. Guard initialization and test first application to fresh views.
- Normal tenkey/gojuon/QWERTY configuration used unqualified theme calls. Thread
  `skinId` through these as well as floating/custom call sites.
- Popup style normalization discarded the skin identifier. Preserve it through
  every existing copy/normalization path.
- Symbol background inherited a lightened key color. Resolve background and tab
  selection from the skin palette.
- Circular custom guides did not receive popup presentation settings. Add a
  presentation-only path while retaining direction ranges and selection labels.
- Cross popup redraw initially replaced backgrounds during drawing. Move that
  work to state/cell updates to avoid continuous invalidation.
- Keep skin fields initialized before view initializer blocks and dismiss old
  tenkey/gojuon/QWERTY popup presentations when changing skins.
- Restore original `ColorStateList` values after kana long-press label dimming;
  do not dim key surfaces by changing parent alpha.

## 検証した保護条件 / Verified invariants

- Missing/unknown skin values fall back to Default without deleting saved data.
- Effective snapshot changes only an explicit set of appearance fields; all
  candidate/layout/input fields are equal to the saved snapshot.
- Default returns the existing snapshot instance; custom colors survive a full
  skin round trip.
- Fresh tenkey/gojuon/QWERTY views accept Cupertino before any legacy theme call.
- Their key bounds are identical across skins; returning to Default restores
  identical native Canvas pixels for the tested custom-color configuration.
- Separate key drawable instances do not share pressed-state changes.
- Existing custom input/controller and QWERTY tests pass.
- Standalone core device-render fixture passes without replacing the user's IME.

## テスト結果 / Test results

The reviewed run passed 296 unit tests: app 31, core 30, custom keyboard 194,
tenkey 6, QWERTY 34, gojuon 1. Failures/errors/skips: 0. The isolated Android
device renderer test passed 1 test. `assembleLiteStandardDebug` succeeded.
The successful iOS capture runs and the limited surface comparison are recorded
in the reference-status document; they are not counted as Android unit tests.

## Draft を維持する理由 / Why this remains draft

The requested full iOS reproduction is not yet verified. Only the measured flat
key sample and upper-left key corner pass the stated color/geometry thresholds.
Popup shape/baseline/gradient profiles, actual-IME configuration screenshots and
synchronized animation timing remain acceptance work. See
[reference status](reference-status.md) for the exact limits and reproduction commands.
