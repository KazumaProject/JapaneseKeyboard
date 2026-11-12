package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.CustomFlickSuspension
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportError
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonExporter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonImporter
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
class ThreeStageFlickPersistenceTest {
    @Test fun oldDefinitionSurvivesOtherKeyEditsSaveReloadDuplicateAndBackup() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = KeyboardRepository(db.keyboardLayoutDao())
            val editor = KeyboardEditorViewModel(repository)
            assertFalse(editor.availableTemplates.any { it.nameResId == R.string.template_three_stage_flick })
            val saved = KeyData("saved", 0, 0, true, keyType = KeyType.HIERARCHICAL_FLICK, keyId = "saved")
            val ordinary = KeyData("other", 0, 1, false, keyId = "other")
            val two = KeyData("two", 0, 2, true, keyType = KeyType.TWO_STEP_FLICK, keyId = "two")
            val sticky = KeyData("sticky", 0, 3, true, keyType = KeyType.STICKY_TWO_STEP_FLICK, keyId = "sticky")
            val maps = listOf("two", "sticky").associateWith { mapOf(TfbiFlickDirection.LEFT to
                mapOf(TfbiFlickDirection.UP_LEFT to "きゅ", TfbiFlickDirection.DOWN_LEFT to "きょ")) }
            val definition = """{"normal":{"TAP":{"type":"input","output":"preserved😀"}},"future":{"unknown":true}}"""
            editor.applyTemplate(KeyboardLayout(listOf(saved, ordinary, two, sticky), emptyMap(), 4, 1,
                twoStepFlickKeyMaps = maps, twoStepLongPressKeyMaps = maps,
                preservedFlickDefinitions = mapOf("saved" to definition)))
            val before = editor.uiState.value.layout
            val oldKey = before.keys.single { it.keyType == KeyType.HIERARCHICAL_FLICK }
            val other = before.keys.single { it.keyType == KeyType.NORMAL }
            before.keys.filter { CustomFlickSuspension.isSuspended(it.keyType) }.forEach { key ->
                assertFalse(editor.updateKeyAndMappings(key.copy(label = "changed"), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            }
            assertFalse(editor.updateKeyAndMappings(oldKey.copy(label = "changed"), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            assertFalse(editor.updateKeyAndMappings(other.copy(keyType = KeyType.HIERARCHICAL_FLICK), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            before.keys.filter { it.keyType in listOf(KeyType.TWO_STEP_FLICK, KeyType.STICKY_TWO_STEP_FLICK) }
                .forEach { key ->
                    val keyId = requireNotNull(key.keyId)
                    assertTrue(editor.updateKeyAndMappings(key.copy(label = "edited-${key.label}"),
                        emptyMap(), before.twoStepFlickKeyMaps.getValue(keyId), emptyMap(),
                        before.twoStepLongPressKeyMaps.getValue(keyId)))
                }
            assertTrue(editor.updateKeyAndMappings(other.copy(label = "edited"), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            val edited = editor.uiState.value.layout
            assertEquals(before.preservedFlickDefinitions, edited.preservedFlickDefinitions)
            val id = repository.saveLayout(edited, "Suspended", null)
            val reloaded = repository.getFullLayout(id).first()
            assertEquals(edited.preservedFlickDefinitions, reloaded.preservedFlickDefinitions)
            assertEquals(edited.twoStepFlickKeyMaps, reloaded.twoStepFlickKeyMaps)
            assertEquals(edited.twoStepLongPressKeyMaps, reloaded.twoStepLongPressKeyMaps)
            repository.duplicateLayout(id)
            val duplicateId = repository.getLayoutsNotFlow().single { it.layoutId != id }.layoutId
            assertEquals(reloaded.preservedFlickDefinitions, repository.getFullLayout(duplicateId).first().preservedFlickDefinitions)
            val json = KeyboardLayoutJsonExporter.toJson(repository.getAllFullLayoutsForExport())
            val parsed = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
            parsed.layouts.forEach { imported ->
                assertEquals(reloaded.preservedFlickDefinitions, imported.keysWithFlicks.mapNotNull {
                    it.key.hierarchicalFlickJson?.let { data -> it.key.keyIdentifier to data }
                }.toMap())
            }
            assertTrue(repository.importLayouts(parsed.layouts) is KeyboardLayoutImportResult.Success)
            repository.getAllCustomKeyboardLayouts().first().forEach {
                assertEquals(reloaded.preservedFlickDefinitions, it.preservedFlickDefinitions)
                assertEquals(reloaded.twoStepFlickKeyMaps, it.twoStepFlickKeyMaps)
                assertEquals(reloaded.twoStepLongPressKeyMaps, it.twoStepLongPressKeyMaps)
            }
        } finally { db.close() }
    }

    @Test fun schemaFourBackupsWithoutAHierarchyStillImport() {
        val json = """{"schemaVersion":4,"layouts":[{"layout":{"name":"old","columnCount":1,"rowCount":1},
            "keysWithFlicks":[{"key":{"keyType":"NORMAL","label":"old","row":0,"column":0},"flicks":[]}]}]}"""
        val parsed = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
        assertNull(parsed.layouts.single().keysWithFlicks.single().key.hierarchicalFlickJson)
    }

    @Test fun suspendedDefinitionsAreOpaqueIncludingUnknownFutureFields() {
        val json = """{"schemaVersion":5,"layouts":[{"layout":{"name":"saved","columnCount":1,"rowCount":1},
            "keysWithFlicks":[{"key":{"keyType":"HIERARCHICAL_FLICK","row":0,"column":0,
            "hierarchicalFlick":{"normal":{"RIGHT":{"type":"future","payload":[1,2,3]}}}}}]}]}"""
        val parsed = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
        val payload = JsonParser.parseString(parsed.layouts.single().keysWithFlicks.single().key.hierarchicalFlickJson)
        assertEquals("future", payload.asJsonObject.getAsJsonObject("normal")
            .getAsJsonObject("RIGHT").get("type").asString)
    }
}
