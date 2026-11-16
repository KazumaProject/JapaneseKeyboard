package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.*
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomFreeFlickPersistenceTest {
    private fun config() = PopupKeyConfig("custom", "base", listOf(
        PopupMap("base", "Base", "😀", listOf(
            PopupRegion("text", PopupBounds(-1.5f, -.5f, -.5f, .5f), PopupAction(text = "hello")),
            PopupRegion("switch", PopupBounds(.5f, -.5f, 1.5f, .5f), PopupAction(PopupActionType.SWITCH, targetMapId = "next")))),
        PopupMap("next", "Next", "かな", listOf(
            PopupRegion("back", PopupBounds(-.5f, -1.5f, .5f, -.5f), PopupAction(PopupActionType.BACK))))))

    @Test fun mapsRemainEditableAndSurviveSaveReloadDuplicateAndBackup() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = KeyboardRepository(db.keyboardLayoutDao())
            val editor = KeyboardEditorViewModel(repository)
            val key = KeyData("Custom", 0, 0, true, keyType = KeyType.FREE_FLICK, keyId = "custom", popupMapConfig = config())
            editor.applyTemplate(KeyboardLayout(listOf(key), emptyMap(), 1, 1))
            val editableKey = editor.uiState.value.layout.keys.single()
            val changed = config().copy(keyId = editableKey.keyId!!).let {
                it.withMap(it.maps.first().copy(tapText = "edited😀"))
            }
            assertTrue(editor.updateKeyAndMappings(editableKey.copy(popupMapConfig = changed), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            val id = repository.saveLayout(editor.uiState.value.layout, "Free Flick", null)
            fun assertConfig(layout: KeyboardLayout) {
                val restored = layout.keys.single()
                assertEquals(KeyType.FREE_FLICK, restored.keyType)
                assertEquals(changed.copy(keyId = restored.keyId!!), restored.popupMapConfig)
                val session = PopupMapSession(restored.popupMapConfig!!, options = PopupMapInputOptions(nearestEnabled = false))
                session.move(1f, 0f)
                assertEquals("next", session.currentMap.id)
                session.move(0f, -1f)
                assertEquals("base", session.currentMap.id)
                session.move(-1f, 0f)
                assertEquals("hello", session.release(-1f, 0f))
            }
            assertConfig(repository.getFullLayout(id).first())
            repository.duplicateLayout(id)
            repository.getAllCustomKeyboardLayouts().first().forEach(::assertConfig)
            val json = KeyboardLayoutJsonExporter.toJson(repository.getAllFullLayoutsForExport())
            assertFalse(json.contains("hierarchicalFlick"))
            val parsed = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
            assertTrue(repository.importLayouts(parsed.layouts) is KeyboardLayoutImportResult.Success)
            repository.getAllCustomKeyboardLayouts().first().forEach(::assertConfig)
        } finally { db.close() }
    }

    @Test fun invalidMapsAreReportedOnImport() {
        val json = """{"schemaVersion":7,"layouts":[{"layout":{"name":"Broken","columnCount":1,"rowCount":1},"keysWithFlicks":[{"key":{"keyType":"FREE_FLICK","row":0,"column":0,"freeFlick":{"keyId":"x","startMapId":"missing","maps":[]}}}]}]}"""
        val result = KeyboardLayoutJsonImporter.parse(json)
        assertTrue(result is KeyboardLayoutImportResult.Failure || result is KeyboardLayoutImportResult.PartialSuccess)
    }

    @Test fun removedTypeInOldBackupLoadsAsAnOrdinaryKey() {
        val json = """{"schemaVersion":5,"layouts":[{"layout":{"name":"Old","columnCount":1,"rowCount":1},"keysWithFlicks":[{"key":{"keyType":"HIERARCHICAL_FLICK","label":"old","row":0,"column":0,"hierarchicalFlick":{"obsolete":true}},"flicks":[]}]}]}"""
        val result = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
        val key = result.layouts.single().keysWithFlicks.single().key
        assertEquals(KeyType.NORMAL, key.keyType)
        assertNull(key.popupMapConfigJson)
    }
}
