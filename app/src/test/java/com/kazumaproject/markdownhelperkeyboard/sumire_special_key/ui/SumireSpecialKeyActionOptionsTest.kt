package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.DisplayAction
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.SpecialKeyActionCategory
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.SpecialKeyActionDropdownAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SumireSpecialKeyActionOptionsTest {
    @Test
    fun defaultIsFirstAndEveryPersistableActionAppearsOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = KeyActionMapper.getDisplayActions(context) + listOf(
            DisplayAction(KeyAction.Text("text"), "Text"),
            DisplayAction(KeyAction.InputText("input"), "Input"))
        val options = sumireSpecialKeyActionOptions(source, "Default") { it.name }
        assertEquals("Default", options.first().label)
        assertNull(options.first().action)
        assertFalse(options.first().isHeader)
        val expected = source.filter { it.action !is KeyAction.Text &&
            it.action !is KeyAction.InputText && KeyActionMapper.fromKeyAction(it.action) != null }
            .map { it.action }
        val actual = options.mapNotNull { it.action?.action }
        assertEquals(expected.toSet(), actual.toSet())
        assertEquals(expected.size, actual.size)
        assertEquals(SpecialKeyActionCategory.entries.map { it.name },
            options.filter { it.isHeader }.map { it.label })
        assertEquals(1, actual.count { it == KeyAction.SwitchToKanaLayout })
        assertEquals(KeyAction.DoNothing, actual.last())
    }

    @Test
    fun headersAreDisabledAndAdapterPositionsMatchTheirActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = sumireSpecialKeyActionOptions(KeyActionMapper.getDisplayActions(context),
            "Default") { it.name }
        val adapter = SpecialKeyActionDropdownAdapter(context, options)
        assertTrue(adapter.isEnabled(0))
        val parent = FrameLayout(context)
        options.forEachIndexed { position, option ->
            assertEquals(!option.isHeader, adapter.isEnabled(position))
            assertEquals(option.label, adapter.getItem(position))
            assertEquals(option.label, (adapter.getView(position, null, parent) as TextView).text.toString())
        }
        val kana = options.indexOfFirst { it.action?.action == KeyAction.SwitchToKanaLayout }
        assertTrue(adapter.isEnabled(kana))
        assertEquals("SwitchToKana", KeyActionMapper.fromKeyAction(options[kana].action!!.action))
    }

    @Test
    fun hiraganaAndCategoryLabelsAreLocalizedInJapaneseAndEnglish() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        for ((language, expected) in listOf("ja" to "ひらがなモードに切り替え",
            "en" to "Switch to Hiragana Mode")) {
            val configuration = Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val context = base.createConfigurationContext(configuration)
            val options = sumireSpecialKeyActionOptions(KeyActionMapper.getDisplayActions(context),
                context.getString(R.string.sumire_special_key_use_default)) { context.getString(it.titleResId) }
            val kana = options.single { it.action?.action == KeyAction.SwitchToKanaLayout }
            assertEquals(expected, kana.label)
            assertEquals(com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
                kana.action!!.iconResId)
            assertEquals(context.getString(R.string.special_key_category_keyboard),
                options.last { it.isHeader && options.indexOf(it) < options.indexOf(kana) }.label)
        }
    }
}
