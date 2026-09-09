package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.InputFilter
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class CommitAndInsertSpaceTest {
    private class Editor(
        text: String,
        var extractedOffset: Int = 0,
        var extractionAvailable: Boolean = true,
        var acceptsCommit: Boolean = true,
    ) : BaseInputConnection(View(ApplicationProvider.getApplicationContext()), true) {
        private val content = SpannableStringBuilder(text)
        var selectionWrites = 0
        var extractionReads = 0
        var batchDepth = 0
        override fun getEditable() = content
        override fun beginBatchEdit(): Boolean { batchDepth++; return true }
        override fun endBatchEdit(): Boolean { batchDepth--; return true }
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
            acceptsCommit && super.commitText(text, newCursorPosition)
        override fun setSelection(start: Int, end: Int): Boolean {
            selectionWrites++
            assertEquals(1, batchDepth)
            return super.setSelection(start, end)
        }
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
            extractionReads++
            if (!extractionAvailable) return null
            return ExtractedText().apply {
                text = content.substring(extractedOffset)
                startOffset = extractedOffset
                selectionStart = Selection.getSelectionStart(content) - extractedOffset
                selectionEnd = Selection.getSelectionEnd(content) - extractedOffset
            }
        }
        fun select(start: Int, end: Int = start) = Selection.setSelection(content, start, end)
        fun assertResult(text: String, cursor: Int) {
            assertEquals(text, content.toString())
            assertEquals(cursor, Selection.getSelectionStart(content))
            assertEquals(cursor, Selection.getSelectionEnd(content))
            assertEquals(0, batchDepth)
        }
    }

    @Test fun selectedTextIsReplacedWithoutMovingPastSpace() {
        for ((start, end) in listOf(1 to 4, 4 to 1)) {
            val editor = Editor("abcdefghi")
            editor.select(start, end)
            assertTrue(editor.commitRawTextAndInsertSpace("", ""))
            editor.assertResult("a efghi", 2)
            assertEquals(0, editor.selectionWrites)
        }
    }

    @Test fun convertedTextIsReplacedWithRawTextOfDifferentLength() {
        val editor = Editor("愛ZZ")
        editor.setComposingRegion(0, 1)
        editor.select(1)
        assertTrue(editor.commitRawTextAndInsertSpace("あい", ""))
        editor.assertResult("あい ZZ", 3)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
    }

    @Test fun rawCompositionAndRepeatedSpacesKeepTheEditorCursor() {
        val editor = Editor("helloZZ", extractionAvailable = false)
        editor.setComposingRegion(0, 5)
        editor.select(5)
        assertTrue(editor.commitRawTextAndInsertSpace("hello", ""))
        assertTrue(editor.commitRawTextAndInsertSpace("", ""))
        editor.assertResult("hello  ZZ", 7)
        assertEquals(0, editor.extractionReads)
    }

    @Test fun tailIsPreservedAfterConvertedCompositionWithAnOffset() {
        val editor = Editor("prefix愛😀zZZ", extractedOffset = 6)
        editor.setComposingRegion(6, 10)
        editor.select(10)
        assertTrue(editor.commitRawTextAndInsertSpace("あい", "😀z"))
        editor.assertResult("prefixあい 😀zZZ", 9)
        assertEquals(0, editor.selectionWrites)
    }

    @Test fun emptyLeftCompositionKeepsTailAndPlacesCursorAfterSpace() {
        val editor = Editor("abcZZ")
        editor.setComposingRegion(0, 3)
        editor.select(3)
        assertTrue(editor.commitRawTextAndInsertSpace("", "abc"))
        editor.assertResult(" abcZZ", 1)
    }

    @Test fun unavailableExtractionKeepsCursorBeforeTail() {
        val editor = Editor("abcZZ", extractionAvailable = false)
        editor.setComposingRegion(0, 3)
        editor.select(3)
        assertTrue(editor.commitRawTextAndInsertSpace("a", "bc"))
        editor.assertResult("a bcZZ", 2)
        assertEquals(0, editor.selectionWrites)
    }

    @Test fun unavailableExtractionPreservesSupplementaryTailAfterConversion() {
        val editor = Editor("prefix愛😀zZZ", extractionAvailable = false)
        editor.setComposingRegion(6, 10)
        editor.select(10)
        assertTrue(editor.commitRawTextAndInsertSpace("あい", "😀z"))
        editor.assertResult("prefixあい 😀zZZ", 9)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
    }

    @Test fun unavailableExtractionWithEmptyLeftKeepsCursorBeforeTail() {
        val editor = Editor("prefixabcZZ", extractionAvailable = false)
        editor.setComposingRegion(6, 9)
        editor.select(9)
        assertTrue(editor.commitRawTextAndInsertSpace("", "abc"))
        editor.assertResult("prefix abcZZ", 7)
    }

    @Test fun subsequentSpacesAndTypingStayBeforePreservedTail() {
        val editor = Editor("abcZZ", extractionAvailable = false)
        editor.setComposingRegion(0, 3)
        editor.select(3)
        assertTrue(editor.commitRawTextAndInsertSpace("a", "bc"))
        assertTrue(editor.commitRawTextAndInsertSpace("", ""))
        assertTrue(editor.commitText("x", 1))
        editor.assertResult("a  xbcZZ", 4)
    }

    @Test fun editorFiltersCanChangeBothCommittedLengths() {
        val editor = Editor("prefixabcZZ")
        editor.setComposingRegion(6, 9)
        editor.select(9)
        editor.editable.filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
            source.subSequence(start, end).toString().replace("a", "AA").replace("bc", "B")
        })
        assertTrue(editor.commitRawTextAndInsertSpace("a", "bc"))
        editor.assertResult("prefixAA BZZ", 9)
    }

    @Test fun rejectedCommitDoesNotMoveCursorAndClosesBatch() {
        val editor = Editor("abc", acceptsCommit = false)
        editor.select(1)
        assertFalse(editor.commitRawTextAndInsertSpace("a", "bc"))
        editor.assertResult("abc", 1)
        assertEquals(0, editor.extractionReads)
        assertEquals(0, editor.selectionWrites)
    }
}
