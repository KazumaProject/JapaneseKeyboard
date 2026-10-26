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


    @Test fun flexibleHalfCellResizeAndMoveKeepTheHierarchy() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = KeyboardRepository(db.keyboardLayoutDao())
            val editor = KeyboardEditorViewModel(repository)
            val key = KeyData("custom", 0, 0, true, keyId = "hierarchy", keyType = KeyType.HIERARCHICAL_FLICK)
            val other = KeyData("other", 0, 2, false, keyId = "other")
            val node = TfbiFlickNode.StatefulKey(mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("x")), label = key.label)
            editor.applyTemplate(KeyboardLayout(
                keys = listOf(key, other), flickKeyMaps = emptyMap(), columnCount = 5, rowCount = 4,
                items = listOf(KeyItem("hierarchy", key, GridPlacement(0, 0, 1, 1)),
                    KeyItem("other", other, GridPlacement(0, 4, 1, 2))),
                isFlexiblePlacementLayout = true, hierarchicalFlickMaps = mapOf("hierarchy" to node)
            ))
            assertTrue(editor.updateKeyAndMappings(key.copy(label = "renamed"), emptyMap(), emptyMap(), emptyMap(), emptyMap(),
                flexibleRowSpanUnits = 1, flexibleColumnSpanUnits = 2, hierarchicalFlick = node))
            editor.swapKeys("hierarchy", "other")
            val updated = editor.uiState.value.layout
            assertEquals(GridPlacement(0, 4, 1, 2), updated.items.single { it.id == "hierarchy" }.placement)
            val id = repository.saveLayout(updated, "Flexible hierarchy", null)
            val reloaded = repository.getFullLayout(id).first()
            assertEquals(updated.hierarchicalFlickMaps, reloaded.hierarchicalFlickMaps)
            assertEquals(updated.items.single { it.id == "hierarchy" }.placement,
                reloaded.items.single { it.id == "hierarchy" }.placement)
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
