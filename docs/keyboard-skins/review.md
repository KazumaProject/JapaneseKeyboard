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

The final related run passed 299 unit tests: app 34, core 30, custom keyboard 194,
tenkey 6, QWERTY 34, gojuon 1. Failures/errors/skips: 0. The isolated Android
device renderer test passed 1 test; full-popup exports passed 2 tests. `assembleLiteStandardDebug` succeeded.
The successful iOS capture runs and the limited surface comparison are recorded
in the reference-status document; they are not counted as Android unit tests.

## 追試で修正した問題 / Follow-up review findings

- Removed the proposed single-row QWERTY variation change. The legacy three-column
  selection path, empty-cell handling, dimensions and offsets are preserved and tested.
- The ACTION_UP cleanup cancelled the visual release hold. Preserve released windows
  on normal UP only, while clearing logical gesture state immediately. Actual-device
  tests assert commit-before-dismissal and immediate cleanup on detach in both skins.
- Switching to Default left popup label gravity, font padding, size, translation and
  elevation modified. Save and restore those presentation values without retaining views
  through WeakHashMap values; verify the round trip against the original values.
- Recreating drawables and illumination fields on each display added work to the touch
  path. Cache per-view drawables and immutable material bitmaps; each window receives
  its own shader matrix. No mutable pressed state is shared between key instances.
- Route QWERTY preview construction through the skin interface instead of hardcoding
  the Cupertino renderer in the keyboard. Input anchors remain in the keyboard view.
- Measure central/second-row previews as well as edge keys. Correct their text baseline
  and dark material instead of extrapolating the top-row field to every row.

Full reproduction is not inferred from the tests. See the explicit image, glyph and
motion results in [fidelity evidence](evidence/fidelity/README.md). This remains a
self-review; no independent reviewer is claimed.

- Separate kana guide disappearance from the label-color restoration: a large ROI
  initially counted the fading surrounding labels as a retained popup. A blue-center
  mask and an independent guide-edge strip show immediate guide disappearance. Remove
  the proposed kana 75 ms hold; retain the QWERTY hold verified on its cap-only ROI.
- Animate only saved label colors, and cancel both fade directions before new input,
  Default restoration or detach. Restore the original ColorStateList after completion.

- Give QWERTY variation containers their own skin factory and cache the drawable across
  selection redraws. Use the measured rounded container/material while leaving the
  original three-column dimensions, offsets and hit mapping untouched. This grid is an
  explicit adaptation and is not included in the 22 reference-aspect-ratio comparisons.
- Repeat the iOS QWERTY capture with HEVC after stopping builds. This reproduces the
  visible release behavior but does not remove the frame-gap/clock uncertainty; keep
  both captures rather than selecting only the more favorable one.
