package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.lang.ref.WeakReference

/** Debug-only editor that consumes IME insets even when edge-to-edge is enforced. */
class CandidateHeightTestHostActivity : Activity() {
    lateinit var editor: EditText
    var receivedTouches = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (intent.getBooleanExtra("landscape", false)) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        current = WeakReference(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        editor = EditText(this).apply {
            id = android.R.id.input
            hint = "Candidate height test editor"
            minLines = 2
        }
        val root = FrameLayout(this).apply {
            addView(editor, FrameLayout.LayoutParams(-1, -1))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(system.left, system.top, system.right, maxOf(system.bottom, ime.bottom))
            insets
        }
        setContentView(root)
        editor.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) restartEditor()
    }

    fun restartEditor() {
        editor.setText("")
        editor.requestFocus()
        getSystemService(InputMethodManager::class.java).restartInput(editor)
        editor.post {
            getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            WindowCompat.getInsetsController(window, editor).show(WindowInsetsCompat.Type.ime())
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) receivedTouches++
        return super.dispatchTouchEvent(event)
    }

    companion object {
        var current = WeakReference<CandidateHeightTestHostActivity>(null)
            private set
    }
}
