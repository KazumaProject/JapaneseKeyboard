package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.QwertyPopupSlot
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.markdownhelperkeyboard.R
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
class KeyboardEditorQwertyTemplateTest {
    @Test
    fun applyEditSaveReloadAndBackup_preserveAllThreeTemplatesAndExistingLayouts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = KeyboardRepository(db.keyboardLayoutDao())
            val editor = KeyboardEditorViewModel(repository)
            val names = listOf(R.string.template_qwerty, R.string.template_qwerty_number, R.string.template_qwerty_symbol)
            val start = editor.availableTemplates.indexOfFirst { it.nameResId == names.first() }
            assertEquals(names, editor.availableTemplates.drop(start).take(3).map { it.nameResId })

            // A layout saved before the template update must keep its original key type.
            val original = editor.availableTemplates[start].layout.let { template ->
                template.copyWithKeys(template.keys.map { it.copy(keyType = KeyType.NORMAL, qwertyConfig = null) })
            }
            val oldId = repository.saveLayout(original, "Existing QWERTY", null)
            val saved = names.map { name ->
                editor.applyTemplate(editor.availableTemplates.single { it.nameResId == name }.layout)
                val layout = editor.uiState.value.layout
                assertTrue(keyboardEditorCapabilities(layout).showHalfCellControls)
                assertTrue(layout.keys.filterNot { it.isSpecialKey }.all { it.keyType == KeyType.QWERTY })
                val key = layout.keys.first { !it.isSpecialKey }
                val config = requireNotNull(key.qwertyConfig)
                val normal = config.normal.copy(output = "custom text",
                    slots = listOf(QwertyPopupSlot("é"), QwertyPopupSlot(), QwertyPopupSlot("👩‍💻")))
                val edited = key.copy(label = normal.displayLabel, action = KeyAction.Text(normal.output),
                    qwertyConfig = config.copy(normal = normal))
                val expected = layout.copyWithKeys(layout.keys.map { if (it.keyId == key.keyId) edited else it })
                val id = repository.saveLayout(expected, context.getString(name), null)
                assertLayoutContent(expected, repository.getFullLayout(id).first())
                expected
            }
            assertLayoutContent(original, repository.getFullLayout(oldId).first())
            val backups = repository.getAllFullLayoutsForExport().filter { it.layout.layoutId != oldId }
            val imported = KeyboardLayoutJsonImporter.parse(KeyboardLayoutJsonExporter.toJson(backups))
            assertTrue(imported is KeyboardLayoutImportResult.Success)
            repository.deleteLayoutConfirmed(oldId)
            backups.forEach { repository.deleteLayoutConfirmed(it.layout.layoutId) }
            assertTrue(repository.importLayouts((imported as KeyboardLayoutImportResult.Success).layouts)
                is KeyboardLayoutImportResult.Success)
            val restored = repository.getLayoutsNotFlow()
            names.zip(saved).forEach { (name, expected) ->
                val id = restored.single { it.name == context.getString(name) }.layoutId
                assertLayoutContent(expected, repository.getFullLayout(id).first())
            }
        } finally {
            db.close()
        }
    }

    private fun assertLayoutContent(expected: KeyboardLayout, actual: KeyboardLayout) {
        assertEquals(expected.columnUnitCount, actual.columnUnitCount)
        assertEquals(expected.rowUnitCount, actual.rowUnitCount)
        expected.keys.forEach { before ->
            val after = actual.keys.single { it.keyId == before.keyId }
            assertEquals(before.label, after.label)
            assertEquals(before.action, after.action)
            assertEquals(before.keyType, after.keyType)
            assertEquals(before.qwertyConfig, after.qwertyConfig)
            assertEquals(before.doubleTapBinding, after.doubleTapBinding)
        }
        fun placements(layout: KeyboardLayout) = layout.items.filterIsInstance<KeyItem>()
            .associate { it.keyData.keyId to it.placement }
        assertEquals(placements(expected), placements(actual))
    }
}
