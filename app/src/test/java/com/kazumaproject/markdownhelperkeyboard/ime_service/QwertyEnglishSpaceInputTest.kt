package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyEnglishSpaceInputTest {
    private class Editor : BaseInputConnection(View(ApplicationProvider.getApplicationContext()), true) {
        private val content = SpannableStringBuilder()
        init { Selection.setSelection(content, 0) }
        override fun getEditable() = content
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
            text = content.toString()
            selectionStart = Selection.getSelectionStart(content)
            selectionEnd = Selection.getSelectionEnd(content)
        }
    }

    private fun checkSpace(
        left: String = "",
        tail: String = "",
        flick: Boolean,
        direct: Boolean = false,
        hasCandidates: Boolean = false,
        romajiFullWidth: Boolean = false,
    ) {
        val editor = Editor()
        editor.setComposingText(left + tail, 1)
        AppPreference.init(ApplicationProvider.getApplicationContext())
        val service = spy(IMEService())
        service.appPreference = AppPreference
        ReflectionHelpers.setField(service, "listAdapter", mock(FloatingCandidateListAdapter::class.java))
        doReturn(editor).`when`(service).getCurrentInputConnection()
        ReflectionHelpers.setField(service, "currentInputModeForSession", InputMode.ModeEnglish)
        ReflectionHelpers.setField(service, "currentQwertyRomajiModeForSession", false)
        ReflectionHelpers.setField(service, "qwertyEnableZenkakuSpacePreference", romajiFullWidth)
        if (direct) ReflectionHelpers.setField(service, "currentInputBehavior", ResolvedInputBehavior.DIRECT_COMMIT)
        val input = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
        val remaining = ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail")
        input.value = left
        remaining.set(tail)
        val candidates = if (hasCandidates) listOf(Candidate("different candidate", 1, 5, 0)) else emptyList()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "handleSpaceKeyClickInQWERTY",
            ClassParameter.from(String::class.java, left),
            ClassParameter.from(MainLayoutBinding::class.java, mock(MainLayoutBinding::class.java)),
            ClassParameter.from(List::class.java, candidates),
            ClassParameter.from(Boolean::class.javaPrimitiveType, flick))
        val space = if (flick) "\u3000" else " "
        assertEquals(left + space + tail, editor.editable.toString())
        assertEquals(left.length + 1, Selection.getSelectionStart(editor.editable))
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
        assertEquals("", input.value)
        assertEquals("", remaining.get())
    }

    @Test fun emptyCompositionUsesRequestedWidthRegardlessOfRomajiPreferenceOrCandidates() {
        for (flick in listOf(false, true)) for (width in listOf(false, true)) {
            for (candidates in listOf(false, true)) {
                checkSpace(flick = flick, romajiFullWidth = width, hasCandidates = candidates)
            }
        }
    }

    @Test fun composingTextCommitsWithRequestedWidthRegardlessOfCandidates() {
        for (flick in listOf(false, true)) for (candidates in listOf(false, true)) {
            checkSpace(left = "hello", flick = flick, hasCandidates = candidates)
        }
    }

    @Test fun composingTailAndCursorArePreservedForBothWidths() {
        for (flick in listOf(false, true)) {
            checkSpace(left = "hel", tail = "lo", flick = flick)
        }
    }

    @Test fun directInputUsesRequestedWidth() {
        for (flick in listOf(false, true)) checkSpace(flick = flick, direct = true)
    }
}
