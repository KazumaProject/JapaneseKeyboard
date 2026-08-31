# Issue #934 TDD Evidence

## Source

- GitHub issue: https://github.com/KazumaProject/JapaneseKeyboard/issues/934
- The journeys and acceptance criteria below were derived from the issue body and its follow-up comment.

## User journeys

1. As a custom-keyboard editor user, deleting a key leaves a visible `+` at that exact position so I can restore a key later.
2. As a custom-keyboard editor user, the empty key slot remains available after saving and reopening the layout.
3. As a custom-keyboard editor user, deleting a key does not insert or reveal an Undo control that shifts the editor buttons.
4. As a custom-keyboard editor user, leaving with unsaved persistent changes requires explicit confirmation.

## RED / GREEN report

### RED

Command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorIssue934Test" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragmentUiRobolectricTest"
```

Result: expected compile-time RED. The new tests could not resolve `deletedKeySlot`, `isDeletedKeySlot`, `restoreDeletedKeySlot`, or `editor_add_key_to_empty_slot` before the persistent empty-slot implementation was added.

Undo-control reversion RED command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragmentUiRobolectricTest.editorLayout_hasNoUndoDeleteControlThatCanShiftButtons"
```

Result: expected runtime RED because `button_undo_delete` was still present in the editor layout.

### GREEN

The focused Issue/UI command passed after implementation:

```text
BUILD SUCCESSFUL
```

The two targeted classes contain 11 passing tests: five Issue #934 behavior tests and six editor UI tests.

Expanded editor/persistence regression command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorIssue934Test" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragmentUiRobolectricTest" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModelFlexiblePlacementTest" --tests "com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepositorySaveLayoutTest"
```

Result: `BUILD SUCCESSFUL`; 107 tests passed with zero failures.

Build command:

```text
.\gradlew.bat :app:assembleLiteStandardDebug
```

Result: `BUILD SUCCESSFUL`.

## Test specification

| # | Guarantee | Test | Type | Result |
|---|---|---|---|---|
| 1 | Deleting a key leaves a restorable empty slot at the exact original placement | `KeyboardEditorIssue934Test.deleteSelectedKey_keepsRestorableEmptySlotAtSamePlacement` | Unit | PASS |
| 2 | Restoring an empty slot creates a fresh editable key at that exact placement | `KeyboardEditorIssue934Test.restoreDeletedKeySlot_recreatesEmptyKeyAtSamePlacement` | Unit | PASS |
| 3 | The editor renders a visible, accessible `+` and routes its click to empty-slot restoration | `KeyboardEditorFragmentUiRobolectricTest.deletedKeySlotUi_showsAddButtonAtEmptyPosition` | Robolectric UI | PASS |
| 4 | Saving writes the empty slot identifier and full grid placement to spacer storage | `KeyboardRepositorySaveLayoutTest.saveLayout_deletedKeySlotPersistsAsSpacerDefinition` | Unit | PASS |
| 5 | Reloading restores the persisted record as a deleted-key slot | `KeyboardRepositorySaveLayoutTest.getFullLayout_deletedKeySlotRestoresAfterReload` | Unit | PASS |
| 6 | Deleting the last implicit half-cell key preserves flexible placement mode | `KeyboardEditorIssue934Test.deleteSelectedKey_preservesImplicitFlexiblePlacementMode` | Unit | PASS |
| 7 | The editor layout contains no Undo-delete control that can shift adjacent buttons | `KeyboardEditorFragmentUiRobolectricTest.editorLayout_hasNoUndoDeleteControlThatCanShiftButtons` | Robolectric UI | PASS |
| 8 | A deletion is tracked as an unsaved persistent edit | `KeyboardEditorIssue934Test.unsavedChanges_tracksPersistedEditorContentAndDeletion` | Unit | PASS |
| 9 | Persistent input-mode settings are dirty, while selection and editor-only chrome are not | `KeyboardEditorIssue934Test.unsavedChanges_tracksInputModeSettingsButIgnoresEditorOnlyState` | Unit | PASS |

## Coverage and known gaps

- This Gradle project does not expose a JaCoCo/Kover coverage task, so no numeric coverage percentage was available.
- A broad custom-keyboard package run executed 258 tests; 257 passed and the only failure was the pre-existing `DoubleTapMigrationTest` `SQLiteCantOpenDatabaseException`.
- The complete `:app:testLiteStandardDebugUnitTest` run executed 1,265 tests: 1,258 passed, 3 unrelated existing tests failed, and 4 were skipped. The failures were `CandidateOrderOverrideMigrationTest` and `DoubleTapMigrationTest` (`SQLiteCantOpenDatabaseException`) plus `SystemNgramRuntimeTest` (asset assertion). None of their source files were changed for Issue #934.
- `:app:lintLiteStandardDebug` reported 15 existing errors and 371 warnings. No finding referenced an Issue #934 changed file; the first existing error is a `MissingPermission` finding in `GemmaImeMediaPanelController.kt`.
- Git checkpoint commits were not created because repository instructions reserve commits for the user. RED/GREEN evidence is preserved in this report.
