package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.os.Looper
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomToggleComposingTest {
    private class Editor : BaseInputConnection(View(ApplicationProvider.getApplicationContext()), true) {
        private val content = SpannableStringBuilder()
        init { Selection.setSelection(content, 0) }
        override fun getEditable() = content
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
        ReflectionHelpers.setField(it, "composingTextArbiter", ComposingTextArbiter(
            writeComposingText = { text, cursor -> editor.setComposingText(text, cursor) },
            finishComposingText = { editor.finishComposingText() },
            copyText = { text -> android.text.SpannableString(text) },
        ))
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext",
            ClassParameter.from(Context::class.java, ApplicationProvider.getApplicationContext<Context>()))
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(it, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Custom
        ReflectionHelpers.setField(it, "delayTime", 200)
        ReflectionHelpers.setField(it, "customComposingTextPreference", true)
        ReflectionHelpers.setField(it, "inputCompositionBackgroundColor", 0x44112233)
        ReflectionHelpers.setField(it, "inputCompositionAfterBackgroundColor", 0x77556677)
    }
    private val binding = mock(MainLayoutBinding::class.java)
    private val input get() = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
    private fun reset() = ReflectionHelpers.callInstanceMethod<Unit>(service, "resetCustomToggleState")
    @After fun cleanup() { reset() }
    private fun waitMillis(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun toggle(key: String = "a", values: List<String> = listOf("あ", "い")) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "handleCustomToggleText",
            ClassParameter.from(String::class.java, key),
            ClassParameter.from(List::class.java, values),
            ClassParameter.from(List::class.java, values),
            ClassParameter.from(MainLayoutBinding::class.java, binding))
    }
    private fun color() = editor.editable.getSpans(0, editor.editable.length, BackgroundColorSpan::class.java).last().backgroundColor

    @Test fun toggleDeadlineAndColors_ignoreSharedModeAndRestartOnEachTap() {
        for (flickOnly in listOf(false, true)) {
            reset()
            input.value = ""
            editor.editable.clear()
            Selection.setSelection(editor.editable, 0)
            ReflectionHelpers.setField(service, "isFlickOnlyMode", flickOnly)
            toggle()
            assertEquals("あ", input.value)
            assertEquals(0x44112233, color())
            waitMillis(199)
            toggle()
            assertEquals("い", input.value)
            waitMillis(199)
            assertEquals(0x44112233, color())
            waitMillis(1)
            assertEquals(0x77556677, color())
            toggle()
            assertEquals("いあ", input.value)
        }
    }

    @Test fun ordinaryTaps_alwaysAppendInBothSharedModes() {
        for (flickOnly in listOf(false, true)) {
            input.value = ""
            ReflectionHelpers.setField(service, "isFlickOnlyMode", flickOnly)
            repeat(3) {
                ReflectionHelpers.callInstanceMethod<Unit>(service, "handleCustomKeyboardText",
                    ClassParameter.from(String::class.java, "あ"),
                    ClassParameter.from(MainLayoutBinding::class.java, binding),
                    ClassParameter.from(Boolean::class.javaPrimitiveType, false))
            }
            assertEquals("あああ", input.value)
            ReflectionHelpers.callInstanceMethod<Unit>(service, "renderCustomKeyboardComposingText",
                ClassParameter.from(String::class.java, input.value))
            assertEquals(0x77556677, color())
        }
    }

    @Test fun equalOutputs_refreshDeadlineWithoutStateFlowEmission() {
        toggle(values = listOf("あ", "あ"))
        waitMillis(199)
        toggle(values = listOf("あ", "あ"))
        waitMillis(199)
        assertEquals(0x44112233, color())
        waitMillis(1)
        assertEquals(0x77556677, color())
    }

    @Test fun cursorMoveAndCommit_cancelPendingUpdate() {
        toggle()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "invalidateCustomToggleStateForSelection",
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0))
        assertNull(ReflectionHelpers.getField<Any?>(service, "customToggleFinalizeJob"))
        toggle()
        assertEquals("ああ", input.value)
        service.finishComposingText()
        assertNull(ReflectionHelpers.getField<Any?>(service, "customToggleFinalizeJob"))
        val previous = editor.editable.toString()
        waitMillis(250)
        assertEquals(previous, editor.editable.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
    }

    @Test fun anotherAction_endsWaitingAndImmediatelyRestoresColor() {
        toggle()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "finishCustomToggleForAction")
        assertEquals(0x77556677, color())
        assertNull(ReflectionHelpers.getField<Any?>(service, "customToggleFinalizeJob"))
        toggle()
        assertEquals("ああ", input.value)
    }

    @Test fun liveConversion_usesCustomDeadlineInBothSharedModes() {
        for (flickOnly in listOf(false, true)) {
            reset()
            ReflectionHelpers.setField(service, "isFlickOnlyMode", flickOnly)
            assertEquals(0L, ReflectionHelpers.callInstanceMethod<Long>(service, "liveConversionApplyDelayMillis"))
            toggle()
            waitMillis(150)
            assertEquals(50L, ReflectionHelpers.callInstanceMethod<Long>(service, "liveConversionApplyDelayMillis"))
            waitMillis(50)
            assertEquals(0L, ReflectionHelpers.callInstanceMethod<Long>(service, "liveConversionApplyDelayMillis"))
        }
    }

    @Test fun liveConversion_rechecksDeadlineWhenEqualOutputDoesNotRestartRequest() {
        toggle(values = listOf("あ", "あ"))
        var applied = false
        val job = CoroutineScope(Dispatchers.Main).launch {
            suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                val method = IMEService::class.java.getDeclaredMethod(
                    "delayBeforeApplyingLiveConversion", Continuation::class.java)
                method.isAccessible = true
                method.invoke(service, continuation)
            }
            applied = true
        }
        try {
            waitMillis(199)
            toggle(values = listOf("あ", "あ"))
            waitMillis(1)
            assertFalse("The original deadline must not end the extended toggle wait", applied)
            waitMillis(198)
            assertFalse(applied)
            waitMillis(1)
            assertTrue(applied)
        } finally {
            job.cancel()
        }
    }

    @Test fun appliedCandidate_cannotBeOverwrittenByPendingToggleTimer() {
        toggle()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "applyFirstSuggestion",
            ClassParameter.from(Candidate::class.java, Candidate("亜", 1, 1u, 0)))
        assertEquals("亜", editor.editable.toString())
        assertNull(ReflectionHelpers.getField<Any?>(service, "customToggleFinalizeJob"))
        waitMillis(250)
        assertEquals("亜", editor.editable.toString())
    }

    @Test fun replacingCustomLayout_cancelsWaitingEvenWhenKeyIdentitiesMatch() {
        toggle()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "setCustomLayoutOnAvailableSurfaces",
            ClassParameter.from(com.kazumaproject.custom_keyboard.data.KeyboardLayout::class.java,
                com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts.createToggleKanaTemplateLayout()))
        assertNull(ReflectionHelpers.getField<Any?>(service, "customToggleFinalizeJob"))
        toggle()
        assertEquals("ああ", input.value)
    }
}
