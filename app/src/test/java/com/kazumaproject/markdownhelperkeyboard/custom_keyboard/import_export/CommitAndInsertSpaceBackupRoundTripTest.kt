package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toFlickAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toDbStrings
import org.junit.Assert.assertEquals
import org.junit.Test

class CommitAndInsertSpaceBackupRoundTripTest {

    @Test
    fun exportImportPreservesNormalAndFlickCommitAndInsertSpaceActions() {
        val action = KeyAction.CommitAndInsertSpace
        val savedAction = KeyActionMapper.fromKeyAction(action)
        val flickDbStrings = FlickAction.Action(action).toDbStrings()
        val fullLayout = FullKeyboardLayout(
            layout = CustomKeyboardLayout(
                layoutId = 863,
                name = "Commit and space",
                columnCount = 1,
                rowCount = 1,
                stableId = "backup-commit-space"
            ),
            keysWithFlicks = listOf(
                KeyWithFlicks(
                    key = KeyDefinition(
                        keyId = 863,
                        ownerLayoutId = 863,
                        label = "Space",
                        row = 0,
                        column = 0,
                        keyType = KeyType.NORMAL,
                        keyIdentifier = "commit-space",
                        action = savedAction
                    ),
                    flicks = listOf(
                        FlickMapping(
                            ownerKeyId = 863,
                            flickDirection = FlickDirection.TAP,
                            actionType = flickDbStrings.first,
                            actionValue = flickDbStrings.second
                        )
                    ),
                    circularFlicks = emptyList(),
                    twoStepFlicks = emptyList(),
                    longPressFlicks = emptyList(),
                    twoStepLongPressFlicks = emptyList()
                )
            ),
            spacers = emptyList()
        )

        val exported = KeyboardLayoutJsonExporter.toJson(listOf(fullLayout))
        val imported = (KeyboardLayoutJsonImporter.parse(exported) as KeyboardLayoutImportResult.Success)
            .layouts.single()

        assertEquals(savedAction, imported.keysWithFlicks.single().key.action)
        assertEquals(
            FlickAction.Action(action),
            imported.keysWithFlicks.single().flicks.single().toFlickAction()
        )
    }
}
