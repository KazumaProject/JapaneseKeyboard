package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomToggleDirectInputTest {
    private class Editor : BaseInputConnection(View(ApplicationProvider.getApplicationContext()), true) {
        private val content = SpannableStringBuilder()
        init { Selection.setSelection(content, 0) }
        override fun getEditable() = content
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
            text = content.toString()
            startOffset = 0
            selectionStart = Selection.getSelectionStart(content)
            selectionEnd = Selection.getSelectionEnd(content)
        }
    }

    private val editor = Editor()
    private val service = spy(IMEService()).also {
        doReturn(editor).`when`(it).getCurrentInputConnection()
        ReflectionHelpers.setField(it, "isCustomLayoutDirectMode", true)
        ReflectionHelpers.setField(it, "delayTime", 200)
    }
    private val binding = mock(MainLayoutBinding::class.java)

    private fun tap() {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "handleCustomToggleText",
            ClassParameter.from(String::class.java, "key"),
            ClassParameter.from(List::class.java, listOf("a", "b")),
            ClassParameter.from(List::class.java, listOf("a", "b")),
            ClassParameter.from(MainLayoutBinding::class.java, binding))
    }

    @Test fun configuredTimeout_preservesPreviouslyCommittedCharacter() {
        tap()
        ShadowSystemClock.advanceBy(Duration.ofMillis(199))
        tap()
        assertEquals("b", editor.editable.toString())
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        tap()
        assertEquals("ba", editor.editable.toString())
        ShadowSystemClock.advanceBy(Duration.ofMillis(201))
        tap()
        assertEquals("baa", editor.editable.toString())
    }

    @Test fun movedCursor_startsNewSequenceEvenWithinTimeout() {
        tap()
        Selection.setSelection(editor.editable, 0)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "invalidateCustomToggleStateForSelection",
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0))
        tap()
        assertEquals("aa", editor.editable.toString())
        assertEquals(1, Selection.getSelectionStart(editor.editable))
    }
}
