package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.HierarchicalFlickCodec
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
            val definition = TfbiFlickNode.StatefulKey(
                mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("preserved😀")), label = "saved")
            editor.applyTemplate(KeyboardLayout(listOf(saved, ordinary), emptyMap(), 2, 1,
                hierarchicalFlickMaps = mapOf("saved" to definition)))
            val before = editor.uiState.value.layout
            val oldKey = before.keys.single { it.keyType == KeyType.HIERARCHICAL_FLICK }
            val other = before.keys.single { it.keyType != KeyType.HIERARCHICAL_FLICK }
            assertFalse(editor.updateKeyAndMappings(oldKey.copy(label = "changed"), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            assertFalse(editor.updateKeyAndMappings(other.copy(keyType = KeyType.HIERARCHICAL_FLICK), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            assertTrue(editor.updateKeyAndMappings(other.copy(label = "edited"), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            val edited = editor.uiState.value.layout
            assertEquals(before.hierarchicalFlickMaps, edited.hierarchicalFlickMaps)
            val id = repository.saveLayout(edited, "Suspended", null)
            val reloaded = repository.getFullLayout(id).first()
            assertEquals(edited.hierarchicalFlickMaps, reloaded.hierarchicalFlickMaps)
            repository.duplicateLayout(id)
            val duplicateId = repository.getLayoutsNotFlow().single { it.layoutId != id }.layoutId
            assertEquals(reloaded.hierarchicalFlickMaps, repository.getFullLayout(duplicateId).first().hierarchicalFlickMaps)
            val json = KeyboardLayoutJsonExporter.toJson(repository.getAllFullLayoutsForExport())
            val parsed = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
            parsed.layouts.forEach { imported ->
                assertEquals(reloaded.hierarchicalFlickMaps, imported.keysWithFlicks.mapNotNull {
                    it.key.hierarchicalFlickJson?.let { data -> it.key.keyIdentifier to HierarchicalFlickCodec.decode(data) }
                }.toMap())
            }
            assertTrue(repository.importLayouts(parsed.layouts) is KeyboardLayoutImportResult.Success)
            repository.getAllCustomKeyboardLayouts().first().forEach {
                assertEquals(reloaded.hierarchicalFlickMaps, it.hierarchicalFlickMaps)
            }
        } finally { db.close() }
    }

    @Test fun schemaFourBackupsWithoutAHierarchyStillImport() {
        val json = """{"schemaVersion":4,"layouts":[{"layout":{"name":"old","columnCount":1,"rowCount":1},
            "keysWithFlicks":[{"key":{"keyType":"NORMAL","label":"old","row":0,"column":0},"flicks":[]}]}]}"""
        val parsed = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
        assertNull(parsed.layouts.single().keysWithFlicks.single().key.hierarchicalFlickJson)
    }

    @Test fun malformedHierarchyIsReportedInsteadOfImportedAsAnEmptyKey() {
        val json = """{"schemaVersion":5,"layouts":[{"layout":{"name":"broken","columnCount":1,"rowCount":1},
            "keysWithFlicks":[{"key":{"keyType":"HIERARCHICAL_FLICK","row":0,"column":0,
            "hierarchicalFlick":{"normal":{"RIGHT":{"type":"invalid"}}}}}]}]}"""
        val result = KeyboardLayoutJsonImporter.parse(json)
        val errors = when (result) {
            is KeyboardLayoutImportResult.PartialSuccess -> result.errors
            is KeyboardLayoutImportResult.Failure -> result.errors
            is KeyboardLayoutImportResult.Success -> error("Malformed hierarchy was accepted")
        }
        assertTrue(errors.toString(), errors.any { it is KeyboardLayoutImportError.ValidationFailed })
    }
}
