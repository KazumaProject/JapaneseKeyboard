package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout

/** Hosts real key/window geometry without starting settings or an editor's IME. */
class FlickEvidenceTestHostActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(FrameLayout(this))
    }
}
