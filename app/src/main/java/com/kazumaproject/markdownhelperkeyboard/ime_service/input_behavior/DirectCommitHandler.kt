package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class DirectCommitHandler {
    fun commitText(inputConnection: InputConnection?, text: String) {
        if (text.isEmpty()) return
        inputConnection?.commitText(text, 1)
    }

    fun sendEnter(inputConnection: InputConnection?) {
        sendDownUp(inputConnection, KeyEvent.KEYCODE_ENTER)
    }

    fun sendBackspace(inputConnection: InputConnection?) {
        sendDownUp(inputConnection, KeyEvent.KEYCODE_DEL)
    }

    fun sendTab(inputConnection: InputConnection?) {
        sendDownUp(inputConnection, KeyEvent.KEYCODE_TAB)
    }

    private fun sendDownUp(inputConnection: InputConnection?, keyCode: Int) {
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
