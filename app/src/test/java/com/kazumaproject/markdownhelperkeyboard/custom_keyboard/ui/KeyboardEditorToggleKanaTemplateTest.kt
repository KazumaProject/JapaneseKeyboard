package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyTextInputBehavior
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.PETAL_TOGGLE_DIRECTIONS
import com.kazumaproject.markdownhelperkeyboard.R
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
class KeyboardEditorToggleKanaTemplateTest {
    @Test fun selectSaveAndReload_preservesToggleKeysAndFlickMappings() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = KeyboardRepository(db.keyboardLayoutDao())
            val editor = KeyboardEditorViewModel(repository)
            val templates = editor.availableTemplates
            val originalIndex = templates.indexOfFirst { it.nameResId == R.string.template_flick_kana_cursor }
            val template = templates[originalIndex + 1]
            assertEquals(R.string.template_toggle_kana, template.nameResId)
            editor.applyTemplate(template.layout)
            val layout = editor.uiState.value.layout
            val id = repository.saveLayout(layout, "かな入力（トグル）", null)
            val reloaded = repository.getFullLayout(id).first()
            val keys = reloaded.keys.filter { it.textInputBehavior == KeyTextInputBehavior.TOGGLE }
            assertEquals(11, keys.size)
            keys.forEach { key ->
                assertNotNull(key.keyId)
                val before = layout.keys.single { it.label == key.label }
                assertEquals(layout.flickKeyMaps[before.keyId], reloaded.flickKeyMaps[key.keyId])
            }
            val ya = keys.single { it.label == "や" }
            val actions = reloaded.flickKeyMaps.getValue(ya.keyId!!).first()
            assertEquals("や(ゆ)よ", PETAL_TOGGLE_DIRECTIONS.mapNotNull { (actions[it] as? FlickAction.Input)?.char }.joinToString(""))
        } finally {
            db.close()
        }
    }
}
