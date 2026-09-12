package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout

/** Offline editor used only by the isolated composing-guide device test. */
class ComposingGuideWebViewHostActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val button = Button(this).apply {
            text = "Background button"
            setOnClickListener { text = "Background tapped" }
        }
        webView = WebView(this).apply {
            loadData("""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body><textarea aria-label="Guide web input" style="width:90%;height:120px;font-size:24px"></textarea></body></html>""", "text/html", "UTF-8")
        }
        setContentView(LinearLayout(this).apply {
            fitsSystemWindows = true
            orientation = LinearLayout.VERTICAL
            addView(button, LinearLayout.LayoutParams(-1, -2))
            addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
