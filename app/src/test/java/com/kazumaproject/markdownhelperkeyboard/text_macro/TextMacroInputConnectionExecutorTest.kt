package com.kazumaproject.markdownhelperkeyboard.text_macro

import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextMacroInputConnectionExecutorTest {
    @Test
    fun replacesSelectionForCommonWrappingMacros() {
        listOf(
            "**{selection}**" to "**text**",
            "「{selection}」" to "「text」",
            "[{selection}]({cursor})" to "[text]()",
        ).forEach { (body, expected) ->
            val connection = RecordingInputConnection()
            connection.textBuffer.append("text")
            Selection.setSelection(connection.textBuffer, 0, 4)
            val expanded = TextMacroCompiler.compile(body).expand(TextMacroContext(selection = "text"))

            assertTrue(TextMacroInputConnectionExecutor.commit(connection, expanded))
            assertEquals(expected, connection.textBuffer.toString())
            assertEquals(expanded.cursorOffset, Selection.getSelectionStart(connection.textBuffer))
        }
    }

    @Test
    fun placesCursorAtStartMiddleAndEnd() {
        listOf(
            "{cursor}ab" to 0,
            "a{cursor}b" to 1,
            "ab{cursor}" to 2,
            "ab" to 2,
        ).forEach { (body, expectedCursor) ->
            val connection = RecordingInputConnection()
            val expanded = TextMacroCompiler.compile(body).expand(TextMacroContext())
            assertTrue(TextMacroInputConnectionExecutor.commit(connection, expanded))
            assertEquals("ab", connection.textBuffer.toString())
            assertEquals(expectedCursor, Selection.getSelectionStart(connection.textBuffer))
        }
    }

    private class RecordingInputConnection : BaseInputConnection(
        View(ApplicationProvider.getApplicationContext()),
        true,
    ) {
        private val buffer = SpannableStringBuilder()

        override fun getEditable(): Editable = buffer

        val textBuffer: Editable
            get() = buffer
    }
}
