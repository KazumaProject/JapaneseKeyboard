package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.os.ResultReceiver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class FastInputHostActivity : Activity() {
    lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        editText = EditText(this).apply {
            id = android.R.id.input
            minLines = 4
            textSize = 22f
            hint = "Fast input probe"
        }
        val status = TextView(this).apply {
            text = "Sumire fast-input device test host"
            textSize = 16f
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    status,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    editText,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )
            }
        )
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        editText.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            editText.requestFocus()
            requestImeForEditor()
        }
    }

    /**
     * Reconnects the editor without changing the selected IME.
     *
     * Switching the default IME while this window owns focus tears down the served editor. The
     * matrix test only needs InputMethodService.onStartInput() to reload its preferences, so a
     * restart on the existing connection is both sufficient and deterministic.
     */
    fun restartEditorInput(clearText: Boolean, readinessToken: String? = null) {
        if (clearText) {
            editText.editableText.clear()
        }
        editText.privateImeOptions = readinessToken?.let(FastInputTestProtocol::privateImeOptions)
        editText.requestFocus()
        editText.setSelection(editText.text.length)
        getSystemService(InputMethodManager::class.java).restartInput(editText)
        requestImeForEditor()
    }

    fun resetEditorForFastInputTest(token: String, receiver: ResultReceiver) {
        editText.editableText.clear()
        editText.setSelection(0)
        getSystemService(InputMethodManager::class.java).sendAppPrivateCommand(
            editText,
            FastInputTestProtocol.ACTION_RESET_FOR_TEST,
            FastInputTestProtocol.resetCommand(token, receiver),
        )
    }

    fun requestImeForEditor() {
        if (!editText.hasWindowFocus()) return
        editText.post {
            if (isFinishing || isDestroyed) return@post
            editText.requestFocus()
            WindowCompat.getInsetsController(window, editText)
                .show(WindowInsetsCompat.Type.ime())
        }
    }
}
