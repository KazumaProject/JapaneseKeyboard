package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpecialKeyActionOptionsTest {
    @Test
    fun categoriesAndKeyboardActionsHavePredictableOrder() {
        val actions = KeyActionMapper.getDisplayActions(ApplicationProvider.getApplicationContext())
            .map { DisplayActionUi(it.displayName, it.action, it.iconResId) }
        val options = groupedSpecialKeyActionOptions(actions) { it.name }
        assertEquals(SpecialKeyActionCategory.entries.map { it.name },
            options.filter { it.isHeader }.map { it.label })
        val keyboardHeader = options.indexOfFirst { it.label == SpecialKeyActionCategory.KEYBOARD.name }
        val keyboardActions = options.drop(keyboardHeader + 1).takeWhile { !it.isHeader }
        assertEquals(listOf(
            KeyAction.SwitchToKanaLayout, KeyAction.SwitchToEnglishLayout,
            KeyAction.SwitchToNumberLayout, KeyAction.ShowEmojiKeyboard,
            KeyAction.MoveCustomKeyboardTab, KeyAction.MoveToCustomKeyboard(""),
            KeyAction.SwitchToNextIme
        ), keyboardActions.map { it.action?.action })
        val editHeader = options.indexOfFirst { it.label == SpecialKeyActionCategory.EDIT_AND_OTHER.name }
        assertEquals(listOf(KeyAction.Cut, KeyAction.Copy, KeyAction.Paste, KeyAction.SelectAll),
            options.drop(editHeader + 1).take(4).map { it.action?.action })
        assertEquals(KeyAction.DoNothing, options.last().action?.action)
        for (category in listOf(SpecialKeyActionCategory.INPUT,
            SpecialKeyActionCategory.DELETE_AND_CURSOR, SpecialKeyActionCategory.TEXT_AND_MODE)) {
            val header = options.indexOfFirst { it.label == category.name }
            val actual = options.drop(header + 1).takeWhile { !it.isHeader }.mapNotNull { it.action }
            assertEquals(actions.filter { it in actual }, actual)
        }
    }

    @Test
    fun everyAvailableActionAppearsOnceUnderASelectableCategory() {
        val actions = KeyActionMapper.getDisplayActions(ApplicationProvider.getApplicationContext())
            .map { DisplayActionUi(it.displayName, it.action, it.iconResId) }

        val options = groupedSpecialKeyActionOptions(actions) { it.name }

        assertEquals(actions.toSet(), options.mapNotNull { it.action }.toSet())
        assertEquals(actions.size, options.count { it.action != null })
        assertEquals(SpecialKeyActionCategory.entries.size, options.count { it.isHeader })
        assertTrue(options.first().isHeader)
        assertTrue(options.filter { it.isHeader }.all { it.action == null })
        assertFalse(options.first { it.action?.action == KeyAction.Convert }.isHeader)
    }

    @Test
    fun categoryHeaderCannotBeSelectedAsAnAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val action = DisplayActionUi("Convert", KeyAction.Convert, null)
        val options = groupedSpecialKeyActionOptions(listOf(action)) { it.name }

        val adapter = SpecialKeyActionDropdownAdapter(context, options)

        assertFalse(adapter.isEnabled(0))
        assertTrue(adapter.isEnabled(1))
        assertTrue(adapter.getItemViewType(0) != adapter.getItemViewType(1))
        assertEquals(action, options[1].action)

        val parent = FrameLayout(context)
        val header = adapter.getView(0, null, parent)
        val item = adapter.getView(1, null, parent) as TextView
        assertTrue(header is MaterialTextView)
        assertTrue((header as MaterialTextView).textSize < item.textSize)
        val indent = (16 * context.resources.displayMetrics.density).roundToInt()
        assertEquals(indent, item.paddingStart - header.paddingStart)
    }

    @Test
    fun clipboardAndSelectionActionsShareTheEditCategory() {
        val actions = KeyActionMapper.getDisplayActions(ApplicationProvider.getApplicationContext())
            .map { DisplayActionUi(it.displayName, it.action, it.iconResId) }

        val options = groupedSpecialKeyActionOptions(actions) { it.name }
        val editActions = options
            .filter { !it.isHeader && it.action?.action in setOf(
                KeyAction.Cut,
                KeyAction.Copy,
                KeyAction.Paste,
                KeyAction.SelectAll
            ) }

        assertEquals(
            setOf(KeyAction.Cut, KeyAction.Copy, KeyAction.Paste, KeyAction.SelectAll),
            editActions.mapNotNull { it.action?.action }.toSet()
        )
        assertEquals(
            setOf(SpecialKeyActionCategory.EDIT_AND_OTHER.name),
            editActions.map { selected ->
                options.takeWhile { it != selected }.last { it.isHeader }.label
            }.toSet()
        )
    }
}
