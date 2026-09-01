# Issue #934 TDD Evidence

## Source

- GitHub issue: https://github.com/KazumaProject/JapaneseKeyboard/issues/934
- The journeys and acceptance criteria below were derived from the issue body and its follow-up comment.

## User journeys

1. As a custom-keyboard editor user, deleting a key leaves a visible `+` at that exact position so I can restore a key later.
2. As a custom-keyboard editor user, the empty key slot remains available after saving and reopening the layout.
3. As a custom-keyboard editor user, deleting a key does not insert or reveal an Undo control that shifts the editor buttons.
4. As a custom-keyboard editor user, leaving with unsaved persistent changes requires explicit confirmation.
5. As a custom-keyboard editor user, deleting a row, column, or button requires confirmation before the editor changes.
6. As a custom-keyboard editor user, I can hide only button-deletion warnings for the remainder of the current editing session.

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

Deletion-warning RED command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorIssue934Test" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragmentUiRobolectricTest"
```

Result: expected compile-time RED. The new tests could not resolve the row/column/button deletion targets, dialog specifications, warning policy APIs, or confirmation strings before the deletion-warning implementation was added.

Concrete dialog UI RED command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragmentUiRobolectricTest"
```

Result: expected compile-time RED because the testable dialog factory and button-warning opt-out view tag did not exist before the confirmation UI was extracted from the Fragment.

### GREEN

The focused Issue/UI command passed after implementation:

```text
BUILD SUCCESSFUL
```

The two targeted classes contain 16 passing tests: seven Issue #934 behavior tests and nine editor UI tests.

Expanded editor/persistence regression command:

```text
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorIssue934Test" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragmentUiRobolectricTest" --tests "com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModelFlexiblePlacementTest" --tests "com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepositorySaveLayoutTest"
```

Result: `BUILD SUCCESSFUL`; 112 tests passed with zero failures.

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
| 10 | Row, column, and button deletions each have a dedicated confirmation specification, and only button deletion offers the session opt-out | `KeyboardEditorFragmentUiRobolectricTest.deletionDialogSpecs_warnForEveryTargetAndOnlyButtonHasSessionOptOut` | Robolectric UI | PASS |
| 11 | Suppressing the button-deletion warning never suppresses row or column warnings | `KeyboardEditorIssue934Test.deletionWarnings_buttonCanBeSuppressedWithoutSuppressingRowOrColumn` | Unit | PASS |
| 12 | Button-warning suppression survives recreation within the same edit and resets for the next editing session | `KeyboardEditorIssue934Test.deletionWarnings_buttonSuppressionResetsForNextEditingSession` | Unit | PASS |
| 13 | The button-deletion confirmation visibly contains the session opt-out, and confirming a checked option invokes both suppression and deletion | `KeyboardEditorFragmentUiRobolectricTest.buttonDeletionDialog_showsSessionOptOutAndAppliesItOnlyWhenConfirmed` | Robolectric UI | PASS |
| 14 | Row and column confirmation dialogs do not contain the button-warning opt-out | `KeyboardEditorFragmentUiRobolectricTest.rowAndColumnDeletionDialogs_doNotShowButtonWarningOptOut` | Robolectric UI | PASS |

## Coverage and known gaps

- This Gradle project does not expose a JaCoCo/Kover coverage task, so no numeric coverage percentage was available.
- A broad custom-keyboard package run executed 258 tests; 257 passed and the only failure was the pre-existing `DoubleTapMigrationTest` `SQLiteCantOpenDatabaseException`.
- The complete `:app:testLiteStandardDebugUnitTest` run executed 1,265 tests: 1,258 passed, 3 unrelated existing tests failed, and 4 were skipped. The failures were `CandidateOrderOverrideMigrationTest` and `DoubleTapMigrationTest` (`SQLiteCantOpenDatabaseException`) plus `SystemNgramRuntimeTest` (asset assertion). None of their source files were changed for Issue #934.
- `:app:lintLiteStandardDebug` reported 15 existing errors and 371 warnings. No finding referenced an Issue #934 changed file; the first existing error is a `MissingPermission` finding in `GemmaImeMediaPanelController.kt`.
- Git checkpoint commits were not created because repository instructions reserve commits for the user. RED/GREEN evidence is preserved in this report.
