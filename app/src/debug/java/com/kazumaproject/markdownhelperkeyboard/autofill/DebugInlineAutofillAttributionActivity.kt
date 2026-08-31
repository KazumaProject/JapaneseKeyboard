package com.kazumaproject.markdownhelperkeyboard.autofill

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Attribution target required by the Slice used in the debug inline presentation. */
class DebugInlineAutofillAttributionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "Sumire Inline Autofill QA provider"
                setPadding(48, 48, 48, 48)
            }
        )
    }
}
