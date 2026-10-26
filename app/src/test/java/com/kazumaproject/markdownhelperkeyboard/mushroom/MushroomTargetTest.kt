package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.text.SpannableStringBuilder
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MushroomTargetTest {
    private fun extracted(text: CharSequence, start: Int, end: Int = start, offset: Int = 0) =
        ExtractedText().apply { this.text = text; selectionStart = start; selectionEnd = end; startOffset = offset }

    private fun connection(snapshot: ExtractedText) = mock<InputConnection> {
        on { getExtractedText(any(), any()) } doReturn snapshot
        on { setSelection(any(), any()) } doReturn true
        on { commitText(any(), any()) } doReturn true
    }

    @Test fun selectedTextTakesPriorityAndResultIsConsumedOnce() {
        val ic = connection(extracted("abc", 2, 1, 10))
        val target = MushroomTarget.capture(ic, "preedit")!!
        assertEquals("b", target.source)
        assertTrue(target.apply(ic, "返却"))
        verify(ic).setSelection(11, 12)
        verify(ic).commitText("返却", 1)
        assertFalse(target.apply(ic, "duplicate"))
    }

    @Test fun composingSpanCoversTailAndLiveConversionDisplay() {
        val styled = SpannableStringBuilder("前漢字後")
        BaseInputConnection.setComposingSpans(styled)
        // Mark only the two displayed composing characters.
        val marker = styled.getSpans(0, styled.length, Any::class.java).first {
            styled.getSpanFlags(it) and android.text.Spanned.SPAN_COMPOSING != 0
        }
        styled.setSpan(marker, 1, 3, android.text.Spanned.SPAN_COMPOSING or android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val ic = connection(extracted(styled, 2))
        val target = MushroomTarget.capture(ic, "かんじ")!!
        assertEquals("かんじ", target.source)
        assertTrue(target.apply(ic, "変換"))
        verify(ic).setSelection(1, 3)
    }

    @Test fun plainEditorPreeditAndEmptySource() {
        val ic = connection(extracted("前かな", 3))
        assertEquals("かな", MushroomTarget.capture(ic, "かな")!!.source)
        val empty = connection(extracted("", 0))
        assertEquals("", MushroomTarget.capture(empty, "")!!.source)
        assertTrue(MushroomTarget.capture(empty, "")!!.apply(empty, ""))
        assertNull(MushroomTarget.capture(ic, "一致しない"))
    }

    @Test fun plainSnapshotUsesOnlyVerifiedComposingRange() {
        val ic = connection(extracted("前漢字後", 2))
        val target = MushroomTarget.capture(ic, "かんじ", 1..3, "漢字")!!
        assertEquals("かんじ", target.source)
        assertTrue(target.apply(ic, "result"))
        verify(ic).setSelection(1, 3)
        assertNull(MushroomTarget.capture(ic, "かんじ", 0..2, "漢字"))
    }

    @Test fun emptyResultDeletesSelectionAndFailedCommitRestoresSelection() {
        val ic = connection(extracted("abc", 1, 2))
        assertTrue(MushroomTarget.capture(ic, "")!!.apply(ic, ""))
        verify(ic).commitText("", 1)
        whenever(ic.commitText(any(), any())).thenReturn(false)
        assertFalse(MushroomTarget.capture(ic, "")!!.apply(ic, "result"))
        verify(ic, times(3)).setSelection(1, 2)
        verify(ic, times(2)).endBatchEdit()
    }

    @Test fun changedTextSelectionOrConnectionIsRejected() {
        for (change in listOf(extracted("changed", 1), extracted("abc", 2), extracted("abc", 1, offset = 4))) {
            val ic = connection(extracted("abc", 1))
            val target = MushroomTarget.capture(ic, "")!!
            whenever(ic.getExtractedText(any(), any())).thenReturn(change)
            assertFalse(target.apply(ic, "result"))
            verify(ic, never()).commitText(any(), any())
        }
        val ic = connection(extracted("abc", 1))
        assertFalse(MushroomTarget.capture(ic, "")!!.apply(connection(extracted("abc", 1)), "result"))
        assertTrue(MushroomTarget.capture(ic, "")!!.apply(connection(extracted("abc", 1)), "result", true))
    }
}
