package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.morse_keyboard.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorseInputTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val context = ContextThemeWrapper(activity, R.style.Theme_MarkdownKeyboard)
    private val connection = mock(InputConnection::class.java)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context); it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1 }).`when`(it).getCurrentInputEditorInfo()
        doReturn(connection).`when`(it).getCurrentInputConnection()
        `when`(connection.commitText(any(), anyInt())).thenReturn(true)
        `when`(connection.setComposingText(any(), anyInt())).thenReturn(true)
    }
    private val binding = MainLayoutBinding.inflate(LayoutInflater.from(context)).also {
        activity.setContentView(it.root)
        Reflect.setField(service, "mainLayoutBinding", it)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Morse
        Reflect.setField(service, "isDefaultRomajiHenkanMap", true)
        Reflect.setField(service, "romajiConverter", RomajiKanaConverter(mapOf("ka" to ("か" to 2), "a" to ("あ" to 1))))
        Reflect.callInstanceMethod<Unit>(service, "configureMorseView", ClassParameter.from(MorseKeyboardView::class.java, it.morseView))
    }
    private val input get() = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
    private fun send(signal: String): Boolean {
        signal.forEach(binding.morseView::appendSignal)
        return binding.morseView.finishSignal()
    }
    @Test fun wabunBoundariesAccumulateComposingReadingAndModifiersDoNotCommit() {
        assertTrue(send(".-..")); assertEquals("か", input.value)
        assertTrue(send("..")); assertEquals("が", input.value)
        assertTrue(send(".--")); assertEquals("がや", input.value)
        binding.morseView.onCommand(MorseCommand.SMALL_KANA)
        assertEquals("がゃ", input.value)
        verify(connection, never()).commitText(any(), anyInt())
    }
    @Test fun romajiUsesExistingConverterAndPreservesIntermediateConsonant() {
        binding.morseView.selectMap(MorsePresets.ROMAJI)
        assertTrue(send("-.-")); assertEquals("k", input.value)
        assertTrue(send(".-")); assertEquals("か", input.value)
        verify(connection, never()).commitText(any(), anyInt())
    }
    @Test fun literalDeleteIsTextWhereasCommandDeleteEditsReading() {
        val map = MorseMap("test", "Test", listOf(
            MorseEntry(".", MorseOutput.LiteralText("削除")),
            MorseEntry("-", MorseOutput.Command(MorseCommand.DELETE))))
        binding.morseView.configure(MorseSettings(sound = false), listOf(map), map.id)
        assertTrue(send(".")); verify(connection).commitText("削除", 1)
        input.value = "かき"
        assertTrue(send("-")); assertEquals("か", input.value)
    }
    @Test fun deletePrefersSignalThenReadingAndUnknownSignalRemains() {
        input.value = "かき"
        binding.morseView.appendSignal('.')
        assertTrue(binding.morseView.deleteSignal()); assertEquals("かき", input.value)
        binding.morseView.onCommand(MorseCommand.DELETE); assertEquals("か", input.value)
        assertFalse(send("........")); assertEquals("........", binding.morseView.signal)
        assertEquals("か", input.value)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun control(label: Int) = descendants(binding.morseView).single { it.contentDescription == context.getString(label) }

    @Test fun invalidSignalEnterClearsOnlySignalAndRestoresEnterPresentation() {
        input.value = "か"
        "........".forEach(binding.morseView::appendSignal)
        assertTrue(binding.morseView.hasInvalidSignal)
        control(com.kazumaproject.morse_keyboard.R.string.morse_clear_signal).performClick()
        assertEquals("", binding.morseView.signal)
        assertEquals("か", input.value)
        assertFalse(binding.morseView.hasInvalidSignal)
        assertNotNull(control(com.kazumaproject.morse_keyboard.R.string.morse_enter))
        verify(connection, never()).commitText(any(), anyInt())
        verify(connection, never()).sendKeyEvent(any())
        verify(connection, never()).performEditorAction(anyInt())
    }

    @Test fun drawingSharedFontGlyphDoesNotScheduleAnotherFrame() {
        binding.morseView.hasComposingText = true
        val convert = control(com.kazumaproject.morse_keyboard.R.string.morse_convert)
        convert.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        convert.layout(0, 0, 80, 80)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        convert.draw(canvas)
        shadowOf(convert).clearWasInvalidated()
        convert.draw(canvas)
        assertFalse(shadowOf(convert).wasInvalidated())
        bitmap.recycle()
    }

    @Test fun unfinishedValidPrefixIsNotClearedByEnter() {
        val map = MorseMap("prefix", "Prefix", listOf(MorseEntry("..", MorseOutput.Reading("か"))))
        binding.morseView.configure(MorseSettings(sound = false, autoBoundary = false), listOf(map), map.id)
        binding.morseView.appendSignal('.')
        assertFalse(binding.morseView.hasInvalidSignal)
        control(com.kazumaproject.morse_keyboard.R.string.morse_enter).performClick()
        assertEquals(".", binding.morseView.signal)
        verify(connection, never()).commitText(any(), anyInt())
    }

    @Test fun allPlacementsKeepControlsBesideOrAboveOrBelowPads() {
        val view = binding.morseView
        fun bounds(node: View) = Rect().also { node.getDrawingRect(it); view.offsetDescendantRectToMyCoords(node, it) }
        MorseControlPlacement.entries.forEach { placement ->
            view.configure(MorseSettings(sound = false, controlLayout = MorseControlLayout(placement = placement)),
                MorsePresets.maps(false), MorsePresets.WABUN)
            view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 800, 600)
            val dot = bounds(control(com.kazumaproject.morse_keyboard.R.string.morse_dot))
            val dash = bounds(control(com.kazumaproject.morse_keyboard.R.string.morse_dash))
            val first = bounds(control(com.kazumaproject.morse_keyboard.R.string.morse_boundary))
            val last = bounds(control(com.kazumaproject.morse_keyboard.R.string.morse_keyboard))
            when (placement) {
                MorseControlPlacement.BOTTOM -> assertTrue(first.top >= dot.bottom)
                MorseControlPlacement.TOP -> assertTrue(first.bottom <= dot.top)
                MorseControlPlacement.LEFT -> assertTrue(last.right <= dot.left)
                MorseControlPlacement.RIGHT -> assertTrue(first.left >= dash.right)
                MorseControlPlacement.BOTH_SIDES -> { assertTrue(first.right <= dot.left); assertTrue(last.left >= dash.right) }
            }
            assertTrue(first.width() > 0 && first.height() > 0 && last.width() > 0 && last.height() > 0)
        }
    }

    @Test fun heldDeleteRemovesSignalFirstThenUsesExistingRepeatAndStopsOnCancel() {
        val view = binding.morseView
        val transitions = mutableListOf<Boolean>()
        view.onDeleteRepeat = { transitions += it }
        view.longPressTimeoutMs = 300
        "...".forEach(view::appendSignal)
        val delete = control(com.kazumaproject.morse_keyboard.R.string.morse_delete)
        delete.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals("..", view.signal)
        assertTrue(transitions.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(192))
        assertEquals("", view.signal)
        assertEquals(listOf(true), transitions)
        view.cancelInput()
        assertEquals(listOf(true, false), transitions)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(true, false), transitions)
    }

    @Test fun changingMapReinterpretsPendingSignalWithoutDeletingIt() {
        ".-".forEach(binding.morseView::appendSignal)
        binding.morseView.selectMap(MorsePresets.ENGLISH)
        assertEquals(".-", binding.morseView.signal)
        assertTrue(binding.morseView.finishSignal())
        verify(connection).commitText("a", 1)
    }
}
