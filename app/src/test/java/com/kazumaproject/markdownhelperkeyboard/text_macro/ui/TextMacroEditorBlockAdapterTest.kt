package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextMacroEditorBlockAdapterTest {
    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_MarkdownKeyboard,
    )

    @Test
    fun focusedTextBlockIsNotReplacedByAStaleFullBind() {
        val adapter = adapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val initial = draft(TextMacroEditorBlock.Text(""))

        holder.bind(initial, position = 0, count = 1)
        val input = holder.itemView.findViewById<android.widget.EditText>(
            R.id.text_macro_text_block_input
        )
        input.isFocusableInTouchMode = true
        assertTrue(input.requestFocus())
        input.setText("ユーザー入力")
        input.setSelection(input.length())

        holder.bind(
            initial.copy(block = TextMacroEditorBlock.Text("stale model value")),
            position = 0,
            count = 1,
        )

        assertEquals("ユーザー入力", input.text.toString())
        assertEquals(input.length(), input.selectionStart)
    }

    @Test
    fun focusedTextBlockIgnoresContentOnlySynchronisation() {
        val adapter = adapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val initial = draft(TextMacroEditorBlock.Text(""))

        holder.bind(initial, position = 0, count = 1)
        val input = holder.itemView.findViewById<android.widget.EditText>(
            R.id.text_macro_text_block_input
        )
        input.isFocusableInTouchMode = true
        assertTrue(input.requestFocus())
        input.setText("ユーザー入力")
        input.setSelection(input.length())

        holder.bindContentOnly(
            initial.copy(block = TextMacroEditorBlock.Text("stale model value"))
        )

        assertEquals("ユーザー入力", input.text.toString())
        assertEquals(input.length(), input.selectionStart)
    }

    private fun adapter() = TextMacroEditorBlockAdapter(
        onTextChanged = { _, _ -> },
        onPatternChanged = { _, _ -> },
        onMove = { _, _ -> },
        onDelete = {},
        onSelectInsertion = {},
        onStartDrag = {},
    )

    private fun draft(block: TextMacroEditorBlock) = TextMacroDraftBlock(
        editorId = 1L,
        block = block,
    )
}
