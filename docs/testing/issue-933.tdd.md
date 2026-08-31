# Issue #933 TDD evidence

## Source and user journeys

The journeys were derived from [Issue #933](https://github.com/KazumaProject/JapaneseKeyboard/issues/933) and its follow-up comments.

- As a new user, I can read the default Japanese TenKey and Sumire/custom keyboard labels without first increasing their text size.
- As a user adjusting TenKey text size, I see the keyboard preview at the height configured in keyboard-size settings, so I can judge the key-size/text-size ratio accurately.

## Task report

### Readable default key text

- RED: `./gradlew.bat :core:testDebugUnitTest --tests "com.kazumaproject.core.domain.key.KeyTextSizeDefaultsTest"` failed to compile because `KeyTextSizeDefaults` did not exist.
- RED: `./gradlew.bat :custom_keyboard:testDebugUnitTest --tests "com.kazumaproject.custom_keyboard.view.FlickKeyboardViewDefaultTextSizeTest"` ran the new test and failed its 20sp assertion against the previous 14sp view default.
- GREEN: the core test passed after centralizing mode-specific defaults, and the custom keyboard test passed after applying the 20sp Sumire default to `FlickKeyboardView`.
- GREEN: `./gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreferenceKeyTextSizeDefaultsTest"` passed with an empty preference store.

### Keyboard height in text-size previews

- RED: `./gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.KeyTextSizePreviewSizingTest"` failed to compile because the shared preview sizing policy did not exist.
- GREEN: the same test passed after using the normal keyboard range (100-420dp), excluding the separately rendered candidate strip, and applying the result in both TenKey text-size preview screens.

## Test specification

| # | What is guaranteed | Test | Type | Result |
|---|---|---|---|---|
| 1 | Japanese TenKey and Sumire normal-key defaults are 20sp | `KeyTextSizeDefaultsTest` | Unit | PASS |
| 2 | English and number TenKey defaults remain 12sp and 16sp | `KeyTextSizeDefaultsTest` | Unit | PASS |
| 3 | Empty preferences resolve the Sumire normal-key size to 20sp | `AppPreferenceKeyTextSizeDefaultsTest` | Robolectric | PASS |
| 4 | A default Sumire layout renders an ordinary key at 20sp | `FlickKeyboardViewDefaultTextSizeTest` | Robolectric | PASS |
| 5 | A 220dp keyboard setting produces a 220dp preview, without candidate-strip height | `KeyTextSizePreviewSizingTest` | Unit | PASS |
| 6 | Text-size previews use the production 100-420dp height bounds | `KeyTextSizePreviewSizingTest` | Unit | PASS |

## Verification, coverage, and known gaps

- `:core:testDebugUnitTest`: 30 passed.
- `:custom_keyboard:testDebugUnitTest`: 195 passed.
- Issue-specific app tests: 3 passed.
- `:app:assembleLiteStandardDebug`: passed and produced `app-lite-standard-debug.apk`.
- `:app:lintLiteStandardDebug`: passed with 0 errors and 407 existing warnings.
- The full app unit-test run executed 1,376 tests: 1,370 passed, 2 failed, and 4 were skipped. The two failures (`CandidateOrderOverrideMigrationTest` and `DoubleTapMigrationTest`) were reproduced in isolation and both fail because Robolectric cannot create their SQLite files under its temporary data directory; they do not exercise the files changed for Issue #933.
- This project does not expose a coverage task or threshold for these Android modules, so no percentage was recorded. The six behavior guarantees above directly cover both requested fixes and their boundary values.

No checkpoint commits were created because repository instructions reserve commits for the user. RED/GREEN command evidence is retained in this report for merge or squash review.
