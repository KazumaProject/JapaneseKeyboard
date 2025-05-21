package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.TabletLayoutBinding

/**
 * A custom view that wraps the tablet keyboard layout and provides easy access
 * to all of its key views via binding.
 */
class TabletKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: TabletLayoutBinding =
        TabletLayoutBinding.inflate(LayoutInflater.from(context), this, true)

    init {
    }

    companion object {
        const val KEY_CODE_KIGOU = -100
        const val KEY_CODE_ENGLISH = -101
        const val KEY_CODE_DELETE = -5
        const val KEY_CODE_ENTER = 10
    }
}