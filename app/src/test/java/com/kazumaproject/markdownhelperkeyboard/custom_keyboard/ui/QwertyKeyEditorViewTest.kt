package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.os.Parcelable
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.RadioButton
import android.widget.TextView
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
    private val context get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)

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

    @Test fun previewOpensEditorAndTracksIndependentDisplayAndOutputWithFallbacks() {
        val editor = QwertyKeyEditorView(context)
        editor.setConfig(QwertyKeyConfig(QwertyKeyLayer("a")))
        val input = edit(editor, R.string.qwerty_tap_output)
        assertEquals(View.GONE, (input.parent.parent.parent as View).visibility)
        descendants(editor).filterIsInstance<MaterialButton>()
            .first { it.contentDescription == editor.context.getString(R.string.qwerty_edit_appearance, "a") }
            .performClick()
        assertEquals(View.VISIBLE, (input.parent.parent.parent as View).visibility)
        edit(editor, R.string.qwerty_key_label).setText("Key")
        edit(editor, R.string.qwerty_preview_label).setText("Peek")
        input.setText("actual input")
        assertTrue(descendants(editor).filterIsInstance<MaterialButton>().any { it.text == "Key" })
        assertTrue(descendants(editor).filterIsInstance<MaterialButton>().any { it.text == "Peek" })
        assertText(editor, R.string.qwerty_tap_result, "actual input")
        edit(editor, R.string.qwerty_key_label).setText("")
        edit(editor, R.string.qwerty_preview_label).setText("")
        assertEquals(2, descendants(editor).filterIsInstance<MaterialButton>().count { it.text == "actual input" })
        input.setText("")
        assertFalse(editor.isInputValid)
        assertNotNull((input.parent.parent as TextInputLayout).error)
        input.setText("👩‍💻")
        assertTrue(editor.isInputValid)
        assertNull((input.parent.parent as TextInputLayout).error)
        assertEquals("👩‍💻", editor.config.normal.output)
    }

    @Test fun addEditReorderClearAndRemoveCandidatesMatchesVisibleInputResult() {
        val editor = QwertyKeyEditorView(context)
        editor.setConfig(QwertyKeyConfig(QwertyKeyLayer("a", columns = 2)))
        assertEquals(View.VISIBLE, descendants(editor).filterIsInstance<TextView>().first {
            it.text == editor.context.getString(R.string.qwerty_no_candidates) }.visibility)
        click(editor, R.string.qwerty_add_slot)
        edit(editor, R.string.qwerty_candidate_output).setText("first output")
        edit(editor, R.string.qwerty_candidate_label).setText("First")
        assertText(editor, R.string.qwerty_candidate_result, "first output")
        click(editor, R.string.qwerty_add_slot)
        edit(editor, R.string.qwerty_candidate_output).setText("second")
        click(editor, R.string.qwerty_move_before)
        assertEquals(listOf("second", "first output"), editor.config.normal.slots.map { it.output })
        assertText(editor, R.string.qwerty_selected_slot, 1, 1)
        click(editor, R.string.qwerty_more_columns)
        assertEquals(3, editor.config.normal.columns)
        click(editor, R.string.qwerty_clear_slot)
        assertEquals(listOf("", "first output"), editor.config.normal.slots.map { it.output })
        assertText(editor, R.string.qwerty_blank_result)
        click(editor, R.string.qwerty_remove_slot)
        assertEquals(listOf(QwertyPopupSlot("first output", "First")), editor.config.normal.slots)
        assertText(editor, R.string.qwerty_candidate_result, "first output")
        click(editor, R.string.qwerty_remove_slot)
        assertTrue(editor.config.normal.slots.isEmpty())
        assertEquals(View.VISIBLE, descendants(editor).filterIsInstance<TextView>().first {
            it.text == editor.context.getString(R.string.qwerty_no_candidates) }.visibility)
    }

    @Test fun automaticShiftPreviewIsReadOnlyAndOverrideCanBeEditedIndependently() {
        val editor = QwertyKeyEditorView(context)
        val normal = QwertyKeyLayer("a", slots = listOf(QwertyPopupSlot("abc", "label")))
        editor.setConfig(QwertyKeyConfig(normal))
        descendants(editor).filterIsInstance<RadioButton>().first {
            it.text == editor.context.getString(R.string.qwerty_layer_shift) }.performClick()
        assertEquals("A", edit(editor, R.string.qwerty_tap_output).text.toString())
        assertFalse(edit(editor, R.string.qwerty_tap_output).isEnabled)
        assertFalse(button(editor, R.string.qwerty_add_slot).isEnabled)
        descendants(editor).filterIsInstance<MaterialButton>().first { it.text == "LABEL" }.performClick()
        assertText(editor, R.string.qwerty_candidate_result, "ABC")
        assertFalse(edit(editor, R.string.qwerty_candidate_output).isEnabled)
        descendants(editor).filterIsInstance<CheckBox>().single().performClick()
        assertTrue(edit(editor, R.string.qwerty_tap_output).isEnabled)
        edit(editor, R.string.qwerty_tap_output).setText("keepCase")
        assertEquals("keepCase", editor.config.shifted!!.output)
        assertEquals(normal, editor.config.normal)
        descendants(editor).filterIsInstance<CheckBox>().single().performClick()
        assertNull(editor.config.shifted)
        assertEquals("A", edit(editor, R.string.qwerty_tap_output).text.toString())
    }

    @Test fun wideGridKeepsTouchTargetsAndChangingColumnCountUpdatesSelectedCoordinates() {
        val editor = QwertyKeyEditorView(context)
        editor.setConfig(QwertyKeyConfig(QwertyKeyLayer("a", columns = 12,
            slots = List(13) { QwertyPopupSlot("item$it") })))
        editor.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        editor.layout(0, 0, editor.measuredWidth, editor.measuredHeight)
        val scroll = descendants(editor).filterIsInstance<HorizontalScrollView>().single()
        assertTrue(scroll.getChildAt(0).measuredWidth > scroll.measuredWidth)
        val last = descendants(editor).filterIsInstance<MaterialButton>().first { it.text == "item12" }
        assertTrue(last.measuredWidth >= (48 * editor.resources.displayMetrics.density).toInt())
        last.performClick()
        assertText(editor, R.string.qwerty_selected_slot, 2, 1)
        edit(editor, R.string.qwerty_columns).setText("3")
        assertText(editor, R.string.qwerty_selected_slot, 5, 1)
        assertEquals(13, editor.config.normal.slots.size)
        edit(editor, R.string.qwerty_columns).setText("0")
        assertFalse(editor.isInputValid)
        assertNotNull((edit(editor, R.string.qwerty_columns).parent.parent as TextInputLayout).error)
        descendants(editor).filterIsInstance<MaterialButton>().first { it.text == "item0" }.performClick()
        assertFalse(editor.isInputValid)
        assertEquals("0", edit(editor, R.string.qwerty_columns).text.toString())
        click(editor, R.string.qwerty_more_columns)
        assertTrue(editor.isInputValid)
    }

    @Test fun recreationRestoresShiftSelectionOpenEditorAndInvalidColumnDraft() {
        val editor = QwertyKeyEditorView(context).apply { id = View.generateViewId() }
        val normal = QwertyKeyLayer("a", slots = listOf(QwertyPopupSlot("abc", "caption")))
        editor.setConfig(QwertyKeyConfig(normal, normal.copy(output = "keepCase")))
        descendants(editor).filterIsInstance<RadioButton>().first {
            it.text == editor.context.getString(R.string.qwerty_layer_shift) }.performClick()
        descendants(editor).filterIsInstance<MaterialButton>().first { it.text == "caption" }.performClick()
        click(editor, R.string.qwerty_edit_key)
        edit(editor, R.string.qwerty_columns).setText("0")
        val state = SparseArray<Parcelable>()
        editor.saveHierarchyState(state)
        val restored = QwertyKeyEditorView(context).apply { id = editor.id; setConfig(editor.config) }
        restored.restoreHierarchyState(state)
        assertTrue(descendants(restored).filterIsInstance<RadioButton>().first {
            it.text == restored.context.getString(R.string.qwerty_layer_shift) }.isChecked)
        assertEquals("keepCase", edit(restored, R.string.qwerty_tap_output).text.toString())
        assertEquals("abc", edit(restored, R.string.qwerty_candidate_output).text.toString())
        assertEquals(View.VISIBLE, (edit(restored, R.string.qwerty_tap_output).parent.parent.parent as View).visibility)
        assertFalse(restored.isInputValid)
        assertEquals("0", edit(restored, R.string.qwerty_columns).text.toString())
        assertEquals(editor.config, restored.config)
    }

    private fun edit(editor: QwertyKeyEditorView, hint: Int): EditText = descendants(editor)
        .filterIsInstance<TextInputLayout>().first { it.hint == editor.context.getString(hint) }.editText!!
    private fun button(editor: QwertyKeyEditorView, text: Int): MaterialButton = descendants(editor)
        .filterIsInstance<MaterialButton>().first { it.text == editor.context.getString(text) }
    private fun click(editor: QwertyKeyEditorView, text: Int) { button(editor, text).performClick() }
    private fun assertText(editor: QwertyKeyEditorView, text: Int, vararg args: Any) {
        assertTrue(editor.context.getString(text, *args), descendants(editor).filterIsInstance<TextView>()
            .any { it.text == editor.context.getString(text, *args) })
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
