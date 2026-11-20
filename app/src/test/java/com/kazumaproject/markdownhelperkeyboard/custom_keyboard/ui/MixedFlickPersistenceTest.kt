package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toFlickAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonExporter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonImporter
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MixedFlickPersistenceTest {
    @Test
    fun mixedAssignmentsSurviveTheEditorRoomAndJsonBackup() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = KeyboardRepository(database.keyboardLayoutDao())
            val key = KeyData(label = "", row = 0, column = 0, isFlickable = false, keyId = "key")
            val layout = KeyboardLayout(listOf(key), emptyMap(), columnCount = 1, rowCount = 1)
            val targetId = repository.saveLayout(layout, "Target", null)
            val target = repository.getLayoutsNotFlowEnsuringStableIds().single { it.layoutId == targetId }
            val editor = KeyboardEditorViewModel(repository)
            editor.applyTemplate(layout)
            val editorKey = editor.uiState.value.layout.keys.single()
            val assignments = mapOf(
                FlickDirection.TAP to FlickAction.Input("かな"),
                FlickDirection.UP to FlickAction.Action(KeyAction.Delete),
                FlickDirection.DOWN to FlickAction.Action(KeyAction.SwitchToKanaLayout),
                FlickDirection.UP_RIGHT to FlickAction.Action(KeyAction.MoveToCustomKeyboard(target.stableId))
            )
            assertTrue(
                editor.updateKeyAndMappings(
                    editorKey.copy(keyType = KeyType.MIXED_FLICK, action = KeyAction.Text("かな")),
                    assignments, emptyMap(), emptyMap(), emptyMap()
                )
            )
            val layoutId = repository.saveLayout(editor.uiState.value.layout, "Mixed", null)
            val reloaded = repository.getFullLayout(layoutId).first()
            val reloadedKey = reloaded.keys.single()
            assertEquals(KeyType.MIXED_FLICK, reloadedKey.keyType)
            assertEquals(KeyAction.Text("かな"), reloadedKey.action)
            val map = reloaded.flickKeyMaps.getValue(reloadedKey.keyId!!).single()
            val expectedSlots = assignments.mapValues { MixedFlickSlot.fromFlickAction(it.value) }
            assertEquals(expectedSlots, map.mapValues { MixedFlickSlot.fromFlickAction(it.value) })

            val json = KeyboardLayoutJsonExporter.toJson(repository.getAllFullLayoutsForExport())
            val imported = (KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success)
                .layouts.single { it.layout.name == "Mixed" }.keysWithFlicks.single()
            assertEquals(KeyType.MIXED_FLICK, imported.key.keyType)
            val importedSlots = imported.flicks.associate {
                it.flickDirection to MixedFlickSlot.fromFlickAction(it.toFlickAction())
            }
            assertEquals(expectedSlots, importedSlots)
        } finally {
            database.close()
        }
    }
}
