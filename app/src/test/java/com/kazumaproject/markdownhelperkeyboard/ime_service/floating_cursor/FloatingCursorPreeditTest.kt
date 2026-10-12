package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingCursorPreeditTest {
    private val service = IMEService().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java,
            ApplicationProvider.getApplicationContext<Context>()))
    }
    private val input get() = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
    private val tail get() = ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail")
    private var selected: Boolean
        get() = ReflectionHelpers.getField(service, "cursorSelectionMode")
        set(value) = ReflectionHelpers.setField(service, "cursorSelectionMode", value)
    private fun hasPreedit() = ReflectionHelpers.callInstanceMethod<Boolean>(service, "hasCursorPreedit")
    @After fun close() {
        for (name in listOf("scope", "ioScope", "localFontScope")) ReflectionHelpers.getField<CoroutineScope>(service, name).cancel()
    }

    @Test fun cursorSelectionNeverEnablesLegacyKeyboardSelectionLayout() {
        selected = true
        ReflectionHelpers.callInstanceMethod<Unit>(service, "updateCursorSelectionAvailability")
        assertTrue(selected)
        assertFalse(ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "_selectMode").value)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "endCursorSelectionForInput")
        assertFalse(selected)
        assertFalse(ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "_selectMode").value)
    }

    @Test fun physicalTypingEndsCursorSelectionWithoutEnablingLegacyMode() {
        selected = true
        service.onKeyDown(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_A))
        assertFalse(selected)
        assertFalse(ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "_selectMode").value)
    }

    @Test fun bothSidesAndConversionStateDisableSelectionWithoutCommittingText() {
        assertFalse(hasPreedit())
        input.value = "あ"; assertTrue(hasPreedit())
        input.value = ""; tail.set("あ"); assertTrue(hasPreedit())
        tail.set("")
        val converting = ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan")
        converting.set(true); assertTrue(hasPreedit())
        selected = true
        ReflectionHelpers.callInstanceMethod<Unit>(service, "updateCursorSelectionAvailability")
        assertFalse(selected)
        converting.set(false); assertFalse(hasPreedit())
    }

    @Test fun canonicalTextAndTransientPreviewAlsoBlockSelectionUntilFinished() {
        var finished = 0
        val arbiter = ComposingTextArbiter({ _, _ -> true }, { finished++; true })
        ReflectionHelpers.setField(service, "composingTextArbiter", arbiter)
        arbiter.setCanonical("未確定", 1)
        assertTrue(hasPreedit())
        selected = true
        ReflectionHelpers.callInstanceMethod<Unit>(service, "updateCursorSelectionAvailability")
        assertFalse(selected); assertEquals(0, finished)
        arbiter.markCanonicalFinished(); assertFalse(hasPreedit())
        arbiter.showPreview("あ", 1); assertTrue(hasPreedit())
        arbiter.releasePreview(false); assertFalse(hasPreedit())
    }

    private fun withHeldArrow(action: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moves = mutableListOf<CursorDirection>()
        val panel = FloatingCursorView(context, {}, {}, {}, moves::add, {})
        val controller = FloatingCursorController(context, { true },
            { CandidatePanelColors.resolve(context) }, {}, {}, {})
        ReflectionHelpers.setField(controller, "view", panel)
        ReflectionHelpers.setField(service, "floatingCursor", controller)
        val arrows = ReflectionHelpers.getField<Map<CursorDirection, View>>(panel, "arrows")
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        arrows.getValue(CursorDirection.LEFT).dispatchTouchEvent(down)
        down.recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertTrue(moves.isNotEmpty())
        val before = moves.size
        try {
            assertFalse(selected)
            action()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals("Input with selection OFF must stop the old repeat", before, moves.size)
        } finally { controller.destroy() }
    }

    @Test fun inputStopsPanelAndQueuedMovesWithSelectionOff() = withHeldArrow {
        val generation = ReflectionHelpers.getField<Long>(service, "cursorActionGeneration")
        ReflectionHelpers.callInstanceMethod<Unit>(service, "endCursorSelectionForInput")
        assertTrue(ReflectionHelpers.getField<Long>(service, "cursorActionGeneration") > generation)
    }

    @Test fun voiceResultStopsHeldArrowBeforeReplacingPreedit() = withHeldArrow {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "applyVoiceInputText",
            ClassParameter.from(String::class.java, "音声"))
        assertEquals("音声", input.value)
    }

    @Test fun interruptingCursorRepeatPreservesLongPressUntilRelease() {
        val left = ReflectionHelpers.getField<AtomicBoolean>(service, "leftCursorKeyLongKeyPressed")
        val right = ReflectionHelpers.getField<AtomicBoolean>(service, "rightCursorKeyLongKeyPressed")
        left.set(true); right.set(true)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "endCursorSelectionForInput")
        assertTrue(left.get()); assertTrue(right.get())
        assertTrue(ReflectionHelpers.getField<AtomicBoolean>(service, "onLeftKeyLongPressUp").get())
        assertTrue(ReflectionHelpers.getField<AtomicBoolean>(service, "onRightKeyLongPressUp").get())
        ReflectionHelpers.callInstanceMethod<Unit>(service, "cancelLeftLongPress")
        ReflectionHelpers.callInstanceMethod<Unit>(service, "cancelRightLongPress")
        assertFalse(left.get()); assertFalse(right.get())
    }
}
