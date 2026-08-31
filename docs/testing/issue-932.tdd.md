# Issue #932 TDD Evidence

## Source and user journeys

- Source: <https://github.com/KazumaProject/JapaneseKeyboard/issues/932>
- As a settings user, I can find keyboard selection with familiar phrases such as
  `Choose Layout`, `Select Keyboard Layout`, `キーボードを適用`, and
  `レイアウトを選ぶ`.
- As a custom-keyboard user, I can open keyboard selection directly from the
  custom-keyboard screen's header. The action remains available in the overflow
  menu when there is not enough toolbar space.
- As a custom-keyboard user, layouts display an in-use badge and cannot be
  deleted while Custom Keyboard is selected as an input method. Removing Custom
  Keyboard from keyboard selection immediately unlocks layout deletion.
- As a custom-keyboard user, returning from keyboard or key editing preserves
  the destination screen's title, navigation state, and header actions.

## RED / GREEN report

### RED

Command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.KeyboardSelectionEntryPointTest"
```

Result: both tests failed as expected. The common search aliases did not return
`keyboard_selection_preference`, and `keyboard_list_menu` had no keyboard-selection
header action.

Additional-requirement RED command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardListInUseProtectionTest" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.KeyboardLayoutAdapterInUseTest"
```

Result: expected compile-time RED. The list-item usage model, blocked-in-use
event, ViewModel preference dependency, and in-use badge did not exist.

### GREEN

The same focused command passed both tests after adding the aliases, menu action,
and navigation route.

The two additional-requirement classes passed all five tests after the UI badge,
disabled delete action, and ViewModel deletion guard were implemented. The
combined Issue #932 focused run passed all seven tests.

Header-regression RED command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.CustomKeyboardEditorActionBarLifecycleContractTest"
```

Result: the first two tests failed because `KeyboardEditorFragment.onDestroyView()` and
`KeyEditorFragment.onDestroyView()` cleared the activity's shared ActionBar after
Navigation Component had already restored the destination header. A follow-up
list-resume test then failed because `KeyboardListFragment` only initialized its
header in `onCreate()`, which is not called again when its back-stack view is
recreated. All three tests passed after removing the stale teardown writes and
restoring the list title, home state, and options menu in `onResume()`.

The complete Lite Standard unit suite executed 1,368 tests: 1,362 passed, 4 were
skipped, and 2 unrelated existing SQLite migration tests failed with
`SQLiteCantOpenDatabaseException`:

- `CandidateOrderOverrideMigrationTest.migration43To44PreservesExistingRulesAsExactAndAllowsLexicalRule`
- `DoubleTapMigrationTest.migration42To43_removesNormalKeyBindingsAndNormalizesSpecialKeyPolicies`

Build and lint commands both passed:

```text
.\gradlew.bat :app:assembleLiteStandardDebug
.\gradlew.bat :app:lintLiteStandardDebug
```

## Test specification

| # | Guarantee | Test | Type | Result |
|---|---|---|---|---|
| 1 | Common English and Japanese aliases find keyboard selection in both new and legacy settings search | `KeyboardSelectionEntryPointTest.keyboardSelectionCanBeFoundByCommonAliasesInNewAndLegacySearch` | Robolectric | PASS |
| 2 | The custom-keyboard header exposes an `ifRoom` keyboard-selection action with an accessible title and keyboard icon | `KeyboardSelectionEntryPointTest.customKeyboardHeaderLinksToKeyboardSelectionWithAnIfRoomAction` | Robolectric resource | PASS |
| 3 | The header action routes to `keyboardSelectionFragment` | `KeyboardSelectionEntryPointTest.customKeyboardHeaderLinksToKeyboardSelectionWithAnIfRoomAction` | Robolectric navigation resource | PASS |
| 4 | Every custom layout is marked in use while Custom Keyboard is selected as an input method | `KeyboardListInUseProtectionTest.customLayoutsAreBadgedAndDeletionIsBlockedWhileCustomInputMethodIsSelected` | ViewModel | PASS |
| 5 | An in-use layout displays the badge and its delete menu item is disabled | `KeyboardLayoutAdapterInUseTest.inUseLayoutShowsBadgeAndDisablesDeleteMenuItem` | Robolectric UI | PASS |
| 6 | ViewModel deletion requests cannot bypass the selected-input-method lock | `KeyboardListInUseProtectionTest.customLayoutsAreBadgedAndDeletionIsBlockedWhileCustomInputMethodIsSelected` | ViewModel | PASS |
| 7 | Removing Custom Keyboard clears the badge and permits deletion | `KeyboardListInUseProtectionTest.removingCustomInputMethodClearsBadgeAndAllowsDeletion` | ViewModel | PASS |
| 8 | Re-enabling Custom Keyboard before confirming a referenced deletion blocks the final delete | `KeyboardListInUseProtectionTest.referenceConfirmationCannotBypassAReenabledCustomInputMethodLock` | ViewModel | PASS |
| 9 | An unused layout hides the badge and keeps deletion enabled | `KeyboardLayoutAdapterInUseTest.unusedLayoutHidesBadgeAndKeepsDeleteMenuItemEnabled` | Robolectric UI | PASS |
| 10 | The Lite Standard debug APK packages successfully | `:app:assembleLiteStandardDebug` | Build | PASS |
| 11 | The Lite Standard application satisfies Android Lint | `:app:lintLiteStandardDebug` | Static analysis | PASS |
| 12 | Leaving keyboard editing cannot clear the destination screen's ActionBar | `CustomKeyboardEditorActionBarLifecycleContractTest.keyboardEditorDoesNotClearDestinationActionBarWhenItsViewIsDestroyed` | Lifecycle contract | PASS |
| 13 | Leaving key editing cannot clear the parent editor's ActionBar | `CustomKeyboardEditorActionBarLifecycleContractTest.keyEditorDoesNotClearDestinationActionBarWhenItsViewIsDestroyed` | Lifecycle contract | PASS |
| 14 | Resuming the custom-keyboard list restores its title, home state, and header actions | `CustomKeyboardEditorActionBarLifecycleContractTest.keyboardListRestoresItsHeaderWheneverItResumes` | Lifecycle contract | PASS |

## Coverage and known gaps

No JaCoCo or Kover coverage task is configured for this application module, so
no numeric coverage percentage is available. The added tests exercise both search
scopes, the complete menu-resource-to-navigation-resource contract, both badge
visibility states, both deletion states, and the confirmation-time race guard.

RED and GREEN were recorded without checkpoint commits because repository
instructions reserve commits and GPG authentication for the user.
