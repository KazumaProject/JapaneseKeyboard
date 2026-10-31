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
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_DIGIT_DATE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_SYMBOL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_NUMBER_SYMBOL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_HALF_WIDTH
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_FULL_WIDTH_SYMBOL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_HALF_WIDTH_SYMBOL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_FULL_WIDTH_NUMBER_SYMBOL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_HALF_WIDTH_NUMBER_SYMBOL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_HALF_WIDTH_KATAKANA
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_CONVERSION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_KATAKANA
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

    @Test fun digitDateKeepsTheLegacyLengthBasedCommitPath() {
        for (input in listOf("1231", "12311")) {
            for (type in listOf(40.toByte(), CANDIDATE_TYPE_DIGIT_DATE)) {
                val editor = Editor()
                val service = spy(IMEService())
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference.init(context)
                service.appPreference = com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
                ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
                    ClassParameter.from(android.content.Context::class.java, context))
                ReflectionHelpers.setField(service, "runtimeInputSharedPreferences",
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
                doReturn(editor).`when`(service).getCurrentInputConnection()
                val candidate = Candidate("12月31日", type, 4u, 8000)
                ReflectionHelpers.callInstanceMethod<Unit>(service, "processCandidate",
                    ClassParameter.from(Candidate::class.java, candidate),
                    ClassParameter.from(String::class.java, input),
                    ClassParameter.from(InputMode::class.java, InputMode.ModeJapanese),
                    ClassParameter.from(Int::class.javaPrimitiveType, 0),
                )
                assertEquals("12月31日", editor.editable.toString())
                val tail = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail")
                assertEquals(if (input.length == 4) "" else "1", tail.get())
            }
        }
    }

    @Test fun newTypesKeepTheirSourceCommitAndPartialInputBehavior() {
        val cases = listOf(
            Triple(CANDIDATE_TYPE_SYMBOL, "!", ""),
            Triple(CANDIDATE_TYPE_FULL_WIDTH_SYMBOL, "！", ""),
            Triple(CANDIDATE_TYPE_HALF_WIDTH_SYMBOL, "!", ""),
            Triple(CANDIDATE_TYPE_NUMBER_SYMBOL, "①", "b"),
            Triple(CANDIDATE_TYPE_FULL_WIDTH_NUMBER_SYMBOL, "１", "b"),
            Triple(CANDIDATE_TYPE_HALF_WIDTH_NUMBER_SYMBOL, "1", "b"),
            Triple(CANDIDATE_TYPE_HALF_WIDTH, "a", "b"),
            Triple(CANDIDATE_TYPE_HALF_WIDTH_KATAKANA, "ﾍﾟ", "b"),
            Triple(CANDIDATE_TYPE_CONVERSION, "ペ", "b"),
            Triple(CANDIDATE_TYPE_KATAKANA, "ペ", "b"),
        )
        for ((type, text, expectedTail) in cases) {
            val editor = Editor()
            val service = spy(IMEService())
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference.init(context)
            service.appPreference = com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
            ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
                ClassParameter.from(android.content.Context::class.java, context))
            ReflectionHelpers.setField(service, "runtimeInputSharedPreferences",
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
            doReturn(editor).`when`(service).getCurrentInputConnection()
            val candidate = Candidate(text, type, 1u, 100)
            ReflectionHelpers.callInstanceMethod<Unit>(service, "processCandidate",
                ClassParameter.from(Candidate::class.java, candidate),
                ClassParameter.from(String::class.java, "ab"),
                ClassParameter.from(InputMode::class.java, InputMode.ModeJapanese),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
            )
            assertEquals("type=$type", text, editor.editable.toString())
            val tail = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail")
            assertEquals("type=$type", expectedTail, tail.get())
        }
    }
}
