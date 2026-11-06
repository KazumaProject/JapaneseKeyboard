package com.kazumaproject.markdownhelperkeyboard.ime_service.english

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.SurroundingText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnglishCorrectionEditorTest {
    @Test fun replacesOnlyTheVerbAndKeepsTheCaretBeforeFollowingText() {
        for (useSurrounding in listOf(true, false)) {
            val committed = "A previous sentence. ".repeat(10) + "We walk home yesterday. "
            val following = " Next sentence."
            val editor = RecordingEditor(committed + following, committed.length, useSurrounding)
            val snapshot = checkNotNull(EnglishCorrectionEditor.capture(editor))
            assertTrue(snapshot.offset > 0)
            assertEquals(committed.length, snapshot.caret)
            val start = snapshot.before.lastIndexOf("walk")
            assertTrue(snapshot.replace(editor, EnglishGrammarCorrection(start, start + 4, "walk", "walked")))
            assertEquals(committed.replace("walk", "walked") + following, editor.content)
            assertEquals(committed.length + 2, editor.selectionStart)
            assertEquals(editor.selectionStart, editor.selectionEnd)
            assertEquals(1, editor.commits)
            assertEquals(0, editor.batchDepth)
        }
    }

    @Test fun refusesToReplaceWhenContextOrSelectionChanged() {
        val text = "I see you yesterday "
        for (change in listOf<(RecordingEditor) -> Unit>(
            { it.content = "I see her yesterday " },
            { it.setSelection(0, 0) },
            { it.setSelection(2, 5) },
        )) {
            val editor = RecordingEditor(text, text.length)
            val snapshot = checkNotNull(EnglishCorrectionEditor.capture(editor))
            change(editor)
            val expected = editor.content
            assertFalse(snapshot.replace(editor, EnglishGrammarCorrection(2, 5, "see", "saw")))
            assertEquals(expected, editor.content)
            assertEquals(0, editor.commits)
        }
    }

    @Test fun leavesTextIntactWhenTheEditorRejectsTheSelectionOrCommit() {
        val text = "They was "
        for (rejectSelection in listOf(true, false)) {
            val editor = RecordingEditor(text, text.length)
            val snapshot = checkNotNull(EnglishCorrectionEditor.capture(editor))
            editor.allowRangeSelection = !rejectSelection
            editor.allowCommit = rejectSelection
            assertFalse(snapshot.replace(editor, EnglishGrammarCorrection(5, 8, "was", "were")))
            assertEquals(text, editor.content)
            assertEquals(text.length, editor.selectionStart)
            assertEquals(editor.selectionStart, editor.selectionEnd)
            assertEquals(0, editor.commits)
            assertEquals(0, editor.batchDepth)
        }
    }

    @Test fun refusesContextAtAnActiveSelectionOrInsideAWord() {
        val editor = RecordingEditor("I see you yesterday", 4)
        assertNull(EnglishCorrectionEditor.capture(editor))
        editor.setSelection(2, 5)
        assertNull(EnglishCorrectionEditor.capture(editor))
    }

    private class RecordingEditor(
        var content: String,
        caret: Int,
        private val useSurrounding: Boolean = true,
    ) : BaseInputConnection(View(ApplicationProvider.getApplicationContext<Context>()), true) {
        var selectionStart = caret
            private set
        var selectionEnd = caret
            private set
        var allowRangeSelection = true
        var allowCommit = true
        var commits = 0
            private set
        var batchDepth = 0
            private set

        override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? {
            if (!useSurrounding) return null
            val start = (selectionStart - beforeLength).coerceAtLeast(0)
            val end = (selectionEnd + afterLength).coerceAtMost(content.length)
            return SurroundingText(content.substring(start, end), selectionStart - start, selectionEnd - start, start)
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
            text = content
            selectionStart = this@RecordingEditor.selectionStart
            selectionEnd = this@RecordingEditor.selectionEnd
            partialStartOffset = -1
        }

        override fun beginBatchEdit(): Boolean {
            batchDepth++
            return true
        }

        override fun endBatchEdit(): Boolean {
            batchDepth--
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            if (start != end && !allowRangeSelection) return false
            selectionStart = start
            selectionEnd = end
            return true
        }

        override fun getSelectedText(flags: Int): CharSequence = content.substring(selectionStart, selectionEnd)

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!allowCommit) return false
            check(newCursorPosition == 1)
            val replacement = checkNotNull(text).toString()
            content = content.replaceRange(selectionStart, selectionEnd, replacement)
            selectionStart += replacement.length
            selectionEnd = selectionStart
            commits++
            return true
        }
    }
}
