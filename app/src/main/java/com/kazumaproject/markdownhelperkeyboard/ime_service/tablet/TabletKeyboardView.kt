package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.TabletLayoutBinding
import timber.log.Timber

/**
 * A custom view that wraps the tablet keyboard layout and provides easy access
 * to all of its key views via binding.
 */
@SuppressLint("ClickableViewAccessibility")
class TabletKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnTouchListener {

    private val binding: TabletLayoutBinding =
        TabletLayoutBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        binding.apply {
            key1.setOnTouchListener(this@TabletKeyboardView)
        }
    }

    companion object {
        const val KEY_CODE_KIGOU = -100
        const val KEY_CODE_ENGLISH = -101
        const val KEY_CODE_DELETE = -5
        const val KEY_CODE_ENTER = 10
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v != null && event != null) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {

                }

                MotionEvent.ACTION_UP -> {
                    when (v.id) {
                        binding.key1.id -> {
                            Timber.d("id: ${binding.key1.id}")
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {

                }

                MotionEvent.ACTION_POINTER_DOWN -> {

                }

                MotionEvent.ACTION_POINTER_UP -> {

                }
            }
        }
        return true
    }
}