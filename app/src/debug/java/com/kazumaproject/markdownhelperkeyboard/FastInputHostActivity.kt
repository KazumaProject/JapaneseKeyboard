package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
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
        editText = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
                val connection = super.onCreateInputConnection(outAttrs) ?: return null
                if (!intent.getBooleanExtra("refuseExtraction", false) && !intent.getBooleanExtra("stripStyles", false)) return connection
                return object : android.view.inputmethod.InputConnectionWrapper(connection, false) {
                    override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? {
                        if (intent.getBooleanExtra("refuseExtraction", false)) return null
                        val extracted = super.getExtractedText(request, flags) ?: return null
                        return android.view.inputmethod.ExtractedText().apply {
                            text = extracted.text?.toString()
                            startOffset = extracted.startOffset
                            partialStartOffset = extracted.partialStartOffset
                            partialEndOffset = extracted.partialEndOffset
                            selectionStart = extracted.selectionStart
                            selectionEnd = extracted.selectionEnd
                            this.flags = extracted.flags
                        }
                    }
                }
            }
        }.apply {
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
            getSystemService(InputMethodManager::class.java).restartInput(editText)
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
    fun restartEditorInput(clearText: Boolean) {
        if (clearText) {
            editText.setText("")
        }
        editText.requestFocus()
        editText.setSelection(editText.text.length)
        getSystemService(InputMethodManager::class.java).restartInput(editText)
        requestImeForEditor()
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
