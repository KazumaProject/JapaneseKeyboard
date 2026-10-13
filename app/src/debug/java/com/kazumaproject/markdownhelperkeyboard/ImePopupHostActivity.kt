package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout

/** Regression host for editors that hide the IME when their application window loses focus. */
class ImePopupHostActivity : Activity() {
    lateinit var editor: EditText
    var webView: WebView? = null
    var focusLosses = 0
        private set

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        current = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (intent.getBooleanExtra("web", false)) {
            webView = WebView(this).apply {
                settings.javaScriptEnabled = true
                loadDataWithBaseURL("https://ime-popup.invalid/", """
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <style>body{padding:24px}input{display:block;height:48px;width:90%;margin:24px 0;font-size:20px}</style>
                    <label for="email">Email</label><input id="email" type="email" aria-label="Email">
                    <label for="password">Password</label><input id="password" type="password" aria-label="Password">
                """.trimIndent(), "text/html", "utf-8", null)
            }
            setContentView(webView)
        } else {
            editor = EditText(this).apply {
                id = android.R.id.input
                inputType = InputType.TYPE_CLASS_TEXT or intent.getIntExtra("variation", InputType.TYPE_TEXT_VARIATION_NORMAL)
                hint = "Popup QA editor"
                textSize = 20f
            }
            setContentView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(editor, LinearLayout.LayoutParams(-1, 160))
            })
            editor.requestFocus()
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile var current: ImePopupHostActivity? = null
            private set
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val imm = getSystemService(InputMethodManager::class.java)
        if (!hasFocus) {
            focusLosses++
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        } else if (::editor.isInitialized) {
            editor.post { imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
        }
    }
}
