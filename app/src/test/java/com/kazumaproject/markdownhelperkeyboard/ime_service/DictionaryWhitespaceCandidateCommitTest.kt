package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryWhitespaceCandidateCommitTest {
    private class Editor : BaseInputConnection(View(ApplicationProvider.getApplicationContext()), true) {
        private val content = SpannableStringBuilder()
        init { Selection.setSelection(content, 0) }
        override fun getEditable() = content
    }

    @Test fun userDictionaryAndTemplateCandidatesCommitAllStoredWhitespace() {
        for (type in listOf(CANDIDATE_TYPE_USER_DICTIONARY, CANDIDATE_TYPE_USER_TEMPLATE)) {
            for (word in listOf("き ", "　き  ", "き　")) {
                val editor = Editor()
                val service = spy(IMEService())
                doReturn(editor).`when`(service).getCurrentInputConnection()
                val candidate = Candidate(word, type, 1u, 4000)
                ReflectionHelpers.callInstanceMethod<Unit>(service, "processCandidate",
                    ClassParameter.from(Candidate::class.java, candidate),
                    ClassParameter.from(String::class.java, "き"),
                    ClassParameter.from(InputMode::class.java, InputMode.ModeJapanese),
                    ClassParameter.from(Int::class.javaPrimitiveType, 0),
                )
                assertEquals(word, editor.editable.toString())
                assertEquals(word.length, Selection.getSelectionStart(editor.editable))
            }
        }
    }
}
