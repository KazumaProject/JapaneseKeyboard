package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class FastInputHostActivity : Activity() {
    lateinit var editText: EditText
    /** Only enabled by isolated editor compatibility probes. */
    var expandSingleLatinA = false
    var plainSnapshotReads = 0
        private set
    var cursorAnchorRequests = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        editText = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val connection = super.onCreateInputConnection(outAttrs) ?: return null
                if (!intent.getBooleanExtra("plain_editor_snapshots", false)) return connection
                val plain = object : InputConnectionWrapper(connection, false) {
                    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? =
                        super.getExtractedText(request, flags)?.also {
                            it.text = it.text?.toString()
                            plainSnapshotReads++
                        }
                    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? =
                        super.getTextBeforeCursor(n, flags)?.toString()
                    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? =
                        super.getTextAfterCursor(n, flags)?.toString()
                    override fun getSelectedText(flags: Int): CharSequence? =
                        super.getSelectedText(flags)?.toString()
                    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
                        cursorAnchorRequests++
                        return super.requestCursorUpdates(cursorUpdateMode)
                    }
                }
                val snapshots = if (Build.VERSION.SDK_INT >= 31) PlainSurroundingConnection(plain) else plain
                return if (Build.VERSION.SDK_INT >= 33) object : InputConnectionWrapper(snapshots, false) {
                    override fun requestCursorUpdates(cursorUpdateMode: Int, cursorUpdateFilter: Int): Boolean {
                        cursorAnchorRequests++
                        return super.requestCursorUpdates(cursorUpdateMode, cursorUpdateFilter)
                    }
                } else snapshots
            }
        }.apply {
            id = android.R.id.input
            minLines = 4
            textSize = 22f
            hint = "Fast input probe"
            filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
                val requested = source.subSequence(start, end).toString()
                if (expandSingleLatinA && 'a' in requested) {
                    fun offset(position: Int) = requested.take((position - start).coerceIn(0, end - start))
                        .sumOf { if (it == 'a') 2 else 1 }
                    SpannableString(requested.replace("a", "AA")).apply {
                        if (source is Spanned) source.getSpans(start, end, Any::class.java).forEach { span ->
                            val from = offset(source.getSpanStart(span))
                            val to = offset(source.getSpanEnd(span))
                            setSpan(span, from, to, source.getSpanFlags(span))
                        }
                    }
                } else null
            })
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

    private class PlainSurroundingConnection(connection: InputConnection) : InputConnectionWrapper(connection, false) {
        override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): android.view.inputmethod.SurroundingText? =
            super.getSurroundingText(beforeLength, afterLength, flags)?.let {
                android.view.inputmethod.SurroundingText(it.text.toString(), it.selectionStart, it.selectionEnd, it.offset)
            }
    }
}
