package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingCursorPreeditTest {
    private val service = IMEService().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java,
            ApplicationProvider.getApplicationContext<Context>()))
    }
    private val input get() = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
    private val tail get() = ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail")
    private val selected get() = ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "_selectMode")
    private fun hasPreedit() = ReflectionHelpers.callInstanceMethod<Boolean>(service, "hasCursorPreedit")
    @After fun close() {
        for (name in listOf("scope", "ioScope", "localFontScope")) ReflectionHelpers.getField<CoroutineScope>(service, name).cancel()
    }

    @Test fun bothSidesAndConversionStateDisableSelectionWithoutCommittingText() {
        assertFalse(hasPreedit())
        input.value = "あ"; assertTrue(hasPreedit())
        input.value = ""; tail.set("あ"); assertTrue(hasPreedit())
        tail.set("")
        val converting = ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan")
        converting.set(true); assertTrue(hasPreedit())
        selected.value = true
        ReflectionHelpers.callInstanceMethod<Unit>(service, "refreshFloatingCursorSelection")
        assertFalse(selected.value)
        converting.set(false); assertFalse(hasPreedit())
    }

    @Test fun canonicalTextAndTransientPreviewAlsoBlockSelectionUntilFinished() {
        var finished = 0
        val arbiter = ComposingTextArbiter({ _, _ -> true }, { finished++; true })
        ReflectionHelpers.setField(service, "composingTextArbiter", arbiter)
        arbiter.setCanonical("未確定", 1)
        assertTrue(hasPreedit())
        selected.value = true
        ReflectionHelpers.callInstanceMethod<Unit>(service, "refreshFloatingCursorSelection")
        assertFalse(selected.value); assertEquals(0, finished)
        arbiter.markCanonicalFinished(); assertFalse(hasPreedit())
        arbiter.showPreview("あ", 1); assertTrue(hasPreedit())
        arbiter.releasePreview(false); assertFalse(hasPreedit())
    }
}
