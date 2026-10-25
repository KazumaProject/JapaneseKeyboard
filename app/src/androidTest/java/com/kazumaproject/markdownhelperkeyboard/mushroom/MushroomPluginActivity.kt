package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Deterministic external plugin, included only in the instrumentation APK. */
class MushroomPluginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
