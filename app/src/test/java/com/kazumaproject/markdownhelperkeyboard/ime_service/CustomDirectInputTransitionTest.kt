package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class CustomDirectInputTransitionTest {
    private class Editor : BaseInputConnection(
        View(ApplicationProvider.getApplicationContext()), true
    ) {
        private val content = SpannableStringBuilder()

        init {
            Selection.setSelection(content, 0)
        }

        override fun getEditable() = content
    }

    private val editor = Editor()
    private val service = spy(IMEService().apply { appPreference = AppPreference }).also {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        it.appPreference = AppPreference
        doReturn(editor).`when`(it).getCurrentInputConnection()
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext",
            ClassParameter.from(Context::class.java, context))
        ReflectionHelpers.setField(it, "listAdapter", mock(FloatingCandidateListAdapter::class.java))
        ReflectionHelpers.setField(it, "currentInputType", InputTypeForIME.Text)
        ReflectionHelpers.setField(it, "currentInputBehavior", ResolvedInputBehavior.COMPOSING_TEXT)
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(it, "_tenKeyQWERTYMode").value =
            TenKeyQWERTYMode.Custom
        ReflectionHelpers.setField(it, "composingTextArbiter", ComposingTextArbiter(
            writeComposingText = { text, cursor -> editor.setComposingText(text, cursor) },
            finishComposingText = { editor.finishComposingText() },
        ))
    }
    private val input get() = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
    private val tail get() = ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail")

    private fun compose(reading: String, remaining: String = "", displayed: String = reading + remaining) {
        input.value = reading
        tail.set(remaining)
        service.setComposingText(displayed, 1)
    }

    private fun switchLayout(direct: Boolean) {
        ReflectionHelpers.setField(service, "isCustomLayoutDirectMode", direct)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "refreshBaselineInputBehaviorForCurrentKeyboard",
            ClassParameter.from(String::class.java, "custom layout input mode loaded"))
    }

    private fun typeDirect(text: String) {
        assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(service, "dispatchDirectTextIfNeeded",
            ClassParameter.from(String::class.java, text)))
    }

    private fun assertCompositionFinished() {
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
        assertEquals("", input.value)
        assertEquals("", tail.get())
    }

    @After
    fun clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(
            ApplicationProvider.getApplicationContext<Context>()
        ).edit().clear().commit()
    }

    private fun setReplacePreference(enabled: Boolean) {
        AppPreference.custom_direct_input_replace_composing_preference = enabled
        ReflectionHelpers.callInstanceMethod<Unit>(service, "syncRuntimeInputPreferences")
    }

    @Test
    fun replacementKeepsCompositionUntilNextInputAndPreservesCommittedPrefix() {
        editor.commitText("前", 1)
        compose("かな")
        setReplacePreference(true)
        assertEquals("前かな", editor.editable.toString())
        assertEquals("かな", input.value)
        switchLayout(direct = true)
        assertEquals("前かな", editor.editable.toString())
        assertEquals(1, BaseInputConnection.getComposingSpanStart(editor.editable))
        assertEquals("かな", input.value)
        typeDirect("A")
        assertEquals("前A", editor.editable.toString())
        assertCompositionFinished()

        switchLayout(direct = false)
        compose("にほん")
        switchLayout(direct = true)
        typeDirect("B")
        assertEquals("前AB", editor.editable.toString())
        assertCompositionFinished()
    }

    @Test
    fun replacementReplacesTheWholeComposingSpanIncludingTailAtInnerCursor() {
        setReplacePreference(true)
        compose("か", remaining = "な")
        editor.setSelection(1, 1)
        switchLayout(direct = true)
        assertEquals("かな", editor.editable.toString())
        assertEquals("な", tail.get())
        assertEquals(1, Selection.getSelectionStart(editor.editable))
        typeDirect("A")
        assertEquals("A", editor.editable.toString())
        assertCompositionFinished()
    }

    @Test
    fun replacementReplacesDisplayedConversionCandidate() {
        setReplacePreference(true)
        compose("かな", displayed = "仮名")
        ReflectionHelpers.setField(service, "isHenkan", java.util.concurrent.atomic.AtomicBoolean(true))
        switchLayout(direct = true)
        assertEquals("仮名", editor.editable.toString())
        typeDirect("A")
        assertEquals("A", editor.editable.toString())
        assertCompositionFinished()
    }

    @Test
    fun disablingReplacementThroughRuntimeSyncPreservesNextComposition() {
        setReplacePreference(true)
        setReplacePreference(false)
        compose("かな")
        switchLayout(direct = true)
        assertCompositionFinished()
        typeDirect("A")
        assertEquals("かなA", editor.editable.toString())
    }

    @Test
    fun replacementDoesNotAffectOtherDirectInputModes() {
        setReplacePreference(true)
        compose("かな")
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(
            service, "_tenKeyQWERTYMode"
        ).value = TenKeyQWERTYMode.TenKeyQWERTY
        ReflectionHelpers.setField(service, "currentInputType", InputTypeForIME.TypeNull)
        switchLayout(direct = true)
        assertCompositionFinished()
        typeDirect("A")
        assertEquals("かなA", editor.editable.toString())
    }

    @Test
    fun alternatingLayoutsPreserveEachPreviousComposition() {
        compose("かな")
        switchLayout(direct = true)
        assertEquals("かな", editor.editable.toString())
        assertCompositionFinished()
        typeDirect("A")

        switchLayout(direct = false)
        compose("にほん")
        switchLayout(direct = true)
        assertEquals("かなAにほん", editor.editable.toString())
        assertCompositionFinished()
        typeDirect("B")

        assertEquals("かなAにほんB", editor.editable.toString())
        assertCompositionFinished()
    }

    @Test
    fun directInputPreservesTailAndInsertsAtTheComposingCursor() {
        compose("か", remaining = "な")
        editor.setSelection(1, 1)
        switchLayout(direct = true)
        assertEquals("かな", editor.editable.toString())
        assertEquals(1, Selection.getSelectionStart(editor.editable))
        assertCompositionFinished()

        typeDirect("A")

        assertEquals("かAな", editor.editable.toString())
        assertEquals(2, Selection.getSelectionStart(editor.editable))
        assertCompositionFinished()
    }

    @Test
    fun directInputPreservesTheDisplayedConversionCandidate() {
        compose("かな", displayed = "仮名")
        ReflectionHelpers.setField(service, "isHenkan", java.util.concurrent.atomic.AtomicBoolean(true))
        switchLayout(direct = true)
        typeDirect("A")

        assertEquals("仮名A", editor.editable.toString())
        assertCompositionFinished()
    }
}
