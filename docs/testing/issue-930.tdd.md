# Issue #930 TDD Evidence

## Source and user journey

- Source: <https://github.com/KazumaProject/JapaneseKeyboard/issues/930>
- As a user flicking a key on the bottom row in portrait mode, I want the
  selected character to remain visible while flicking down, even though there
  is no keyboard space below the key.
- As a user holding that key, I want all five long-press guide values, including
  the bottom-flick `0`, to remain visible without overlapping.

## Execution report

- RED: `PopupWindowExtensionTest` was added before the placement helper existed.
  Running the focused test failed during test compilation with
  `Unresolved reference: calculateFlickBottomPopupPlacement`.
- GREEN: the bottom-flick placement now detects portrait bottom-row keys and
  places their popup above the anchor with a downward-pointing arrow. The same
  focused command passed all three placement cases.
- RED: the long-press guide tests initially failed to compile because its
  placement helper did not exist. A second RED showed that fixed-height spacing
  overlapped when the popup size was increased.
- GREEN: portrait bottom-row long-press guides now shift the top, left, center,
  and right values inward while placing the bottom value over the anchor. The
  spacing uses both popup and anchor height, so configured popup scaling remains
  separated.
- Regression: all 13 TenKey unit tests passed, and
  `:app:assembleLiteStandardDebug` completed successfully.
- Emulator: `:app:installLiteStandardDebug` installed the updated APK on
  `Medium_Phone_API_36.1`, and the Lite IME was selected again.

## Test specification

| # | What is guaranteed | Test or command | Type | Result |
|---|---|---|---|---|
| 1 | A portrait bottom-row down-flick is shown above the key instead of being omitted below the keyboard | `PopupWindowExtensionTest.portraitBottomRowFlickBottomIsPlacedAboveTheAnchor` | Unit | PASS |
| 2 | A portrait non-bottom-row down-flick keeps its normal directional placement | `PopupWindowExtensionTest.portraitNonBottomRowFlickBottomKeepsTheDirectionalPlacement` | Unit | PASS |
| 3 | A landscape bottom-row down-flick keeps its existing directional placement | `PopupWindowExtensionTest.landscapeBottomRowFlickBottomKeepsTheDirectionalPlacement` | Unit | PASS |
| 4 | A portrait bottom-row long press displays the complete five-way guide inside the keyboard edge | `PopupWindowExtensionTest.portraitBottomRowLongPressGuideIsShiftedAboveTheKeyboardEdge` | Unit | PASS |
| 5 | Enlarged long-press popups use scaled spacing and do not collapse onto each other | `PopupWindowExtensionTest.portraitBottomRowLongPressGuideUsesTheScaledPopupSpacing` | Unit | PASS |
| 6 | Non-bottom portrait and landscape guides retain their previous offsets | `PopupWindowExtensionTest.portraitNonBottomRowLongPressGuideKeepsItsExistingOffsets`, `landscapeBottomRowLongPressGuideKeepsItsExistingOffsets` | Unit | PASS |
| 7 | Existing TenKey behavior remains valid | `:tenkey:testDebugUnitTest` | Unit, 13 tests | PASS |
| 8 | The Lite Standard debug application packages successfully | `:app:assembleLiteStandardDebug` | Build | PASS |
| 9 | The modified TenKey module satisfies Android Lint | `:tenkey:lintDebug` | Static analysis | PASS |

## Coverage and known gaps

No JaCoCo task is configured for this module, so no numeric coverage percentage
is available. The placement helpers' bottom-row, non-bottom-row, portrait,
landscape, default-size, and scaled-size branches are exercised directly.

The complete Lite application unit suite executed 1,260 tests, with 1,253
passing, 4 skipped, and 3 unrelated existing failures in SQLite migration and
system n-gram tests. Application-wide lint also remains blocked by 15 existing
errors, beginning with a `MissingPermission` error in
`GemmaImeMediaPanelController.kt`; TenKey lint passes.

RED and GREEN were recorded without checkpoint commits because repository
instructions reserve commits and GPG authentication for the user.
