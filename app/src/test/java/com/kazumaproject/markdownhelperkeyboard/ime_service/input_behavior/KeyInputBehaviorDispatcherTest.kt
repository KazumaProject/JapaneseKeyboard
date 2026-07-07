package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyInputBehaviorDispatcherTest {

    private val dispatcher = KeyInputBehaviorDispatcher()

    @Test
    fun directCommitTextCommitsWithoutComposingPipeline() {
        val inputConnection = mock<InputConnection>()
        var inputString = ""
        var candidateRequested = false

        val handled = dispatcher.dispatchText(
            behavior = ResolvedInputBehavior.DIRECT_COMMIT,
            inputConnection = inputConnection,
            text = "c",
            composingPipeline = {
                inputString += "c"
                candidateRequested = true
                inputConnection.setComposingText(inputString, 1)
            }
        )

        assertTrue(handled)
        assertEquals("", inputString)
        assertFalse(candidateRequested)
        verify(inputConnection).commitText("c", 1)
        verify(inputConnection, never()).setComposingText("c", 1)
    }

    @Test
    fun directCommitEnterSendsEnterDownUp() {
        val inputConnection = mock<InputConnection>()

        val handled = dispatcher.dispatchEnter(
            behavior = ResolvedInputBehavior.DIRECT_COMMIT,
            inputConnection = inputConnection,
            composingPipeline = {}
        )

        assertTrue(handled)
        assertDownUp(inputConnection, KeyEvent.KEYCODE_ENTER)
    }

    @Test
    fun directCommitBackspaceSendsDeleteDownUp() {
        val inputConnection = mock<InputConnection>()

        val handled = dispatcher.dispatchBackspace(
            behavior = ResolvedInputBehavior.DIRECT_COMMIT,
            inputConnection = inputConnection,
            composingPipeline = {}
        )

        assertTrue(handled)
        assertDownUp(inputConnection, KeyEvent.KEYCODE_DEL)
    }

    @Test
    fun directCommitTabSendsTabDownUp() {
        val inputConnection = mock<InputConnection>()

        val handled = dispatcher.dispatchTab(
            behavior = ResolvedInputBehavior.DIRECT_COMMIT,
            inputConnection = inputConnection,
            composingPipeline = {}
        )

        assertTrue(handled)
        assertDownUp(inputConnection, KeyEvent.KEYCODE_TAB)
    }

    @Test
    fun composingTextBehaviorUsesExistingPipeline() {
        val inputConnection = mock<InputConnection>()
        var inputString = ""
        var candidateRequested = false

        val handled = dispatcher.dispatchText(
            behavior = ResolvedInputBehavior.COMPOSING_TEXT,
            inputConnection = inputConnection,
            text = "c",
            composingPipeline = {
                inputString += "c"
                candidateRequested = true
                inputConnection.setComposingText(inputString, 1)
            }
        )

        assertFalse(handled)
        assertEquals("c", inputString)
        assertTrue(candidateRequested)
        verify(inputConnection, never()).commitText("c", 1)
        verify(inputConnection).setComposingText("c", 1)
    }

    private fun assertDownUp(inputConnection: InputConnection, keyCode: Int) {
        val captor = argumentCaptor<KeyEvent>()
        verify(inputConnection, org.mockito.kotlin.times(2)).sendKeyEvent(captor.capture())
        val events = captor.allValues
        assertEquals(KeyEvent.ACTION_DOWN, events[0].action)
        assertEquals(keyCode, events[0].keyCode)
        assertEquals(KeyEvent.ACTION_UP, events[1].action)
        assertEquals(keyCode, events[1].keyCode)
    }
}
