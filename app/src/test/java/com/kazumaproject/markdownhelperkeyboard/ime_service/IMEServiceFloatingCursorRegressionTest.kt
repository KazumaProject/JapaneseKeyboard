package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor.CursorComposingExpectation
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor.CursorMovement
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class IMEServiceFloatingCursorRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val editor = mock<InputConnection>()
    private val service = IMEService().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        it.appPreference = mock<AppPreference>()
        ReflectionHelpers.setField(it, "listAdapter", mock<FloatingCandidateListAdapter>())
        ReflectionHelpers.setField(it, "runtimeInputSharedPreferences", PreferenceManager.getDefaultSharedPreferences(context))
        ReflectionHelpers.setField(it, "qwertyGlideInputCoordinator", mock<QwertyGlideInputCoordinator>())
        ReflectionHelpers.setField(it, "isVibration", false)
        ReflectionHelpers.setField(it, "dictionaryInputConnection", editor)
    }

    @After fun cleanup() {
        listOf("scope", "ioScope").forEach { ReflectionHelpers.getField<CoroutineScope>(service, it).cancel() }
    }

    private fun expectation(): CursorComposingExpectation? = ReflectionHelpers.getField(service, "cursorWrittenComposing")

    @Test fun acceptedComposingWritesUseTheLatestTextBeforeTheNativeRangeNotification() {
        whenever(editor.setComposingText(any(), any())).thenReturn(true)
        ReflectionHelpers.setField(service, "cursorNativeComposingRange", 0..1)
        assertTrue(service.setComposingText("ああ", 1))
        assertEquals(CursorComposingExpectation(0, "ああ"), expectation())
        whenever(editor.finishComposingText()).thenReturn(false, true)
        assertFalse(service.finishComposingText())
        assertEquals(CursorComposingExpectation(0, "ああ"), expectation())
        assertTrue(service.finishComposingText())
        assertNull(expectation())
    }

    @Test fun aFirstWriteDoesNotTreatThePreviousCaretAsAConfirmedComposingStart() {
        whenever(editor.setComposingText(any(), any())).thenReturn(true)
        ReflectionHelpers.setField(service, "cursorNativeSelectionStart", 5)
        ReflectionHelpers.setField(service, "cursorNativeSelectionEnd", 5)
        assertTrue(service.setComposingText("あ", 1))
        assertEquals(CursorComposingExpectation(null, "あ"), expectation())
    }

    @Test fun rejectedWritesKeepTheLastAcceptedExpectationAndCommitsClearIt() {
        whenever(editor.setComposingText(any(), any())).thenReturn(true, false)
        ReflectionHelpers.setField(service, "cursorNativeComposingRange", 0..1)
        assertTrue(service.setComposingText("あ", 1))
        assertFalse(service.setComposingText("ああ", 1))
        assertEquals(CursorComposingExpectation(0, "あ"), expectation())
        whenever(editor.commitText(any(), any())).thenReturn(false, true)
        assertFalse(service.commitText("あ", 1))
        assertEquals(CursorComposingExpectation(0, "あ"), expectation())
        assertTrue(service.commitText("あ", 1))
        assertNull(expectation())
    }

    private fun pendingPreedit(): MutableStateFlow<String> {
        val input = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
        input.value = "あ"
        ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").set("し")
        ReflectionHelpers.setField(service, "cursorInputTracking", true)
        whenever(editor.setComposingText(any(), any())).thenReturn(true)
        assertTrue(service.setComposingText("あし", 1))
        return input
    }

    private fun paste(text: String) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "pasteClipboardItemContent",
            ClassParameter.from(ClipboardItem::class.java, ClipboardItem.Text(0, text)))
    }

    @Test fun acceptedPasteClearsLocalPreeditWithoutWaitingForSelectionNotification() {
        val input = pendingPreedit()
        whenever(editor.commitText("ZZ", 1)).thenReturn(true)
        paste("ZZ")
        verify(editor).commitText("ZZ", 1)
        assertEquals("", input.value)
        assertEquals("", ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").get())
        assertNull(expectation())
        // A key accepted before the editor's selection callback starts a fresh preedit.
        input.value += "あ"
        assertTrue(service.setComposingText(input.value, 1))
        val writes = argumentCaptor<CharSequence>()
        verify(editor, atLeastOnce()).setComposingText(writes.capture(), eq(1))
        assertEquals("あ", writes.lastValue.toString())
        assertEquals("あ", input.value)
    }

    @Test fun rejectedPasteKeepsLocalPreeditAndTail() {
        val input = pendingPreedit()
        val before = expectation()
        whenever(editor.commitText("ZZ", 1)).thenReturn(false)
        paste("ZZ")
        assertEquals("あ", input.value)
        assertEquals("し", ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").get())
        assertEquals(before, expectation())
        verify(service.appPreference, never()).last_pasted_clipboard_text_preference = any()
    }

    @Test fun emptyPasteDoesNotReplaceOrDiscardPreedit() {
        val input = pendingPreedit()
        val before = expectation()
        paste("")
        verify(editor, never()).commitText(any(), any())
        assertEquals("あ", input.value)
        assertEquals(before, expectation())
    }

    private fun checkVertical(key: QWERTYKey, keyCode: Int, gesture: GestureType? = null) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        ReflectionHelpers.setField(service, "scope", CoroutineScope(dispatcher + SupervisorJob()))
        ReflectionHelpers.setField(service, "cursorMovement\$delegate", lazy {
            CursorMovement(Mutex(), { _, a, b -> service.setSelection(a, b) },
                { _, event -> editor.sendKeyEvent(event) }, dispatcher)
        })
        ReflectionHelpers.setField(service, "isInputViewActive", true)
        val view = mock<QWERTYKeyboardView>()
        var listener: QWERTYKeyListener? = null
        doAnswer { listener = it.getArgument(0); null }.whenever(view).setOnQWERTYKeyListener(any())
        ReflectionHelpers.callInstanceMethod<Unit>(service, "configureQwertyView",
            ClassParameter.from(QWERTYKeyboardView::class.java, view),
            ClassParameter.from(MainLayoutBinding::class.java, mock<MainLayoutBinding>()))
        ReflectionHelpers.setField(service, "cursorSelectionMode", true)
        if (gesture == null) {
            requireNotNull(listener).onReleasedQWERTYKey(key, null, null)
        } else {
            requireNotNull(listener).onCursorFlickQWERTYKey(key, gesture)
        }
        advanceUntilIdle()
        assertTrue(ReflectionHelpers.getField<Boolean>(service, "cursorSelectionMode"))
        val events = argumentCaptor<KeyEvent>()
        verify(editor, atLeastOnce()).sendKeyEvent(events.capture())
        val arrows = events.allValues.filter { it.keyCode == keyCode }
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), arrows.map { it.action })
        assertTrue(arrows.all { it.isShiftPressed })
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, events.lastValue.keyCode)
        assertEquals(KeyEvent.ACTION_UP, events.lastValue.action)
        assertFalse(events.lastValue.isShiftPressed)
    }

    @Test fun qwertyCursorUpKeepsSelectionAndUsesBalancedShiftEvents() = checkVertical(QWERTYKey.QWERTYKeyCursorUp, KeyEvent.KEYCODE_DPAD_UP)
    @Test fun qwertyCursorDownKeepsSelectionAndUsesBalancedShiftEvents() = checkVertical(QWERTYKey.QWERTYKeyCursorDown, KeyEvent.KEYCODE_DPAD_DOWN)

    @Test fun leftCursorFlickUpKeepsSelection() = checkVertical(QWERTYKey.QWERTYKeyCursorLeft, KeyEvent.KEYCODE_DPAD_UP, GestureType.FlickTop)
    @Test fun leftCursorFlickDownKeepsSelection() = checkVertical(QWERTYKey.QWERTYKeyCursorLeft, KeyEvent.KEYCODE_DPAD_DOWN, GestureType.FlickBottom)
    @Test fun rightCursorFlickUpKeepsSelection() = checkVertical(QWERTYKey.QWERTYKeyCursorRight, KeyEvent.KEYCODE_DPAD_UP, GestureType.FlickTop)
    @Test fun rightCursorFlickDownKeepsSelection() = checkVertical(QWERTYKey.QWERTYKeyCursorRight, KeyEvent.KEYCODE_DPAD_DOWN, GestureType.FlickBottom)

    @Test fun verticalCursorFlickPreservesTheStartingKeysPreeditBehavior() {
        val view = mock<QWERTYKeyboardView>()
        var listener: QWERTYKeyListener? = null
        doAnswer { listener = it.getArgument(0); null }.whenever(view).setOnQWERTYKeyListener(any())
        ReflectionHelpers.callInstanceMethod<Unit>(service, "configureQwertyView",
            ClassParameter.from(QWERTYKeyboardView::class.java, view),
            ClassParameter.from(MainLayoutBinding::class.java, mock<MainLayoutBinding>()))
        val input = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
        val tail = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail")
        for (gesture in listOf(GestureType.FlickTop, GestureType.FlickBottom)) {
            input.value = "ab"
            tail.set("x")
            requireNotNull(listener).onCursorFlickQWERTYKey(QWERTYKey.QWERTYKeyCursorLeft, gesture)
            assertEquals("a", input.value)
            assertEquals("bx", tail.get())

            input.value = "ab"
            tail.set("x")
            requireNotNull(listener).onCursorFlickQWERTYKey(QWERTYKey.QWERTYKeyCursorRight, gesture)
            assertEquals("abx", input.value)
            assertEquals("", tail.get())
        }
    }
}
