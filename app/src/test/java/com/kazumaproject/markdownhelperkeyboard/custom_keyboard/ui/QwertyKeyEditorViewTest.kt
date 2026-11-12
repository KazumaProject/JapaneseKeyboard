package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyKeyEditorViewTest {
    @Test fun editingColumnsAndSlotLabelsKeepsBlanksAndIndependentShiftLayer() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val editor = QwertyKeyEditorView(context)
        val normal = QwertyKeyLayer("a", columns = 2,
            slots = listOf(QwertyPopupSlot("abc", "label"), QwertyPopupSlot(), QwertyPopupSlot("xyz")))
        editor.setConfig(QwertyKeyConfig(normal, normal.copy(output = "keepLowercase")))
        fun edit(hint: Int): EditText = descendants(editor).filterIsInstance<TextInputLayout>()
            .first { it.hint == context.getString(hint) }.editText!!
        assertTrue(editor.isInputValid)
        edit(R.string.qwerty_columns).setText("1")
        assertEquals(normal.slots, editor.config.normal.slots)
        assertEquals(1, editor.config.normal.columns)
        descendants(editor).filterIsInstance<MaterialButton>().first { it.text == "label" }.performClick()
        edit(R.string.qwerty_candidate_label).setText("caption")
        assertEquals("caption", editor.config.normal.slots[0].label)
        assertEquals("abc", editor.config.normal.slots[0].output)
        descendants(editor).filterIsInstance<RadioButton>().first { it.text == context.getString(R.string.qwerty_layer_shift) }.performClick()
        assertEquals("keepLowercase", edit(R.string.qwerty_tap_output).text.toString())
        edit(R.string.qwerty_tap_output).setText("independent")
        assertEquals("a", editor.config.normal.output)
        assertEquals("independent", editor.config.shifted!!.output)
        edit(R.string.qwerty_columns).setText("0")
        assertFalse(editor.isInputValid)
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
