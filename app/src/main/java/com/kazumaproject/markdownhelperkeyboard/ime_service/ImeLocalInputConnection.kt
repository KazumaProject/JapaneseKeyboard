package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/** Delivers software keys to an IME-local field whose window cannot receive platform key dispatch. */
internal class ImeLocalInputConnection(
    target: InputConnection,
    private val editor: EditText,
    private val isActive: () -> Boolean,
) : InputConnectionWrapper(target, false) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (closed) return false
        // InputConnection can be called from a worker; EditText must run on the UI thread.
        if (Looper.myLooper() == mainHandler.looper) return dispatchKeyEvent(event)
        val copy = KeyEvent(event)
        return mainHandler.post { dispatchKeyEvent(copy) }
    }

    private fun dispatchKeyEvent(event: KeyEvent): Boolean =
        !closed && isActive() && editor.dispatchKeyEvent(event)

    override fun closeConnection() {
        closed = true
        super.closeConnection()
    }
}
