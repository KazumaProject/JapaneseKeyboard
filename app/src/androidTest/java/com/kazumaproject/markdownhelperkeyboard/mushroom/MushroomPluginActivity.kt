package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Deterministic external plugin, included only in the instrumentation APK. */
class MushroomPluginActivity : Activity() {
    private var editor: android.widget.EditText? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) editor?.let {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(it, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("editor", false)) {
            editor = android.widget.EditText(this).apply {
                id = android.R.id.input
                setText("abc")
                setSelection(1, 2)
            }
            setContentView(editor)
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            editor?.requestFocus()
            return
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MushroomPluginActivity).apply {
                text = "Source: " + intent.getStringExtra(MushroomActivity.REPLACE_KEY).orEmpty()
            })
            for (label in listOf("Return result", "Cancel", "Missing result")) {
                addView(Button(this@MushroomPluginActivity).apply {
                    text = label
                    setOnClickListener {
                        val result = Intent()
                        if (label == "Return result") result.putExtra(MushroomActivity.REPLACE_KEY,
                            "[" + intent.getStringExtra(MushroomActivity.REPLACE_KEY).orEmpty() + "]")
                        setResult(if (label == "Cancel") RESULT_CANCELED else RESULT_OK, result)
                        finish()
                    }
                })
            }
        })
    }
}
