package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.*
import kotlinx.coroutines.*

/** The real current layout, with only the offered skin overridden. */
class KeyboardThemePreviewView(context: Context, private val skin: KeyboardSkinId) : FrameLayout(context) {
    private var scope: CoroutineScope? = null
    private var loadJob: Job? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        load()
    }
    private fun load() {
        loadJob?.cancel()
        loadJob = scope?.launch {
            try {
                val target = currentKeyboardPreviewTarget(context)
                removeAllViews()
                addView(KeyboardPreviewView(context, target, skin), LayoutParams(-1, -2))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                removeAllViews()
                addView(TextView(context).apply {
                    setText(R.string.keyboard_preview_unavailable); gravity = Gravity.CENTER
                    setOnClickListener { load() }
                }, LayoutParams(-1, -2))
            }
        }
    }
    override fun onDetachedFromWindow() { scope?.cancel(); scope = null; removeAllViews(); super.onDetachedFromWindow() }
}
