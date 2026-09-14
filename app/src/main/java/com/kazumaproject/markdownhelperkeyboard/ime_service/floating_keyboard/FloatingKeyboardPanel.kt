package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardBody
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditConstraints
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditOverlayView
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditValues
import kotlin.math.roundToInt

/** The single keyboard uses the same chrome and screen-coordinate gestures as split panes. */
internal class FloatingKeyboardPanel(
    private val binding: FloatingKeyboardLayoutBinding,
    onEdit: () -> Unit,
    private val onPosition: (Int, Int, Boolean) -> Unit,
) : FloatingPanelFrame(binding.root.context, onEdit) {
    var editCallbacks = KeyboardLayoutEditOverlayView.Callbacks()
    private var gesture: ComposingGuideGesture? = null
    private var startHeight = 0
    private var startWidth = 0
    private var startBounds: GuideBounds? = null
    private var draft: KeyboardLayoutEditValues.Floating? = null
    private val keyboardBody = SplitKeyboardBody(context, dp(240), dp(180))
    private val input = object : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean =
            if (editing) true else super.dispatchTouchEvent(event)
    }

    init {
        binding.root.fitsSystemWindows = false
        binding.dragHandle.visibility = View.GONE
        val keys = FrameLayout(context)
        val container = binding.floatingKeyboardContainer
        while (container.childCount > 0) {
            val child = container.getChildAt(0)
            container.removeView(child)
            keys.addView(child, LayoutParams(-1, -1))
        }
        keyboardBody.addView(keys)
        container.addView(keyboardBody, LayoutParams(-1, -1))
        val hide = binding.floatingHideKeyboardBtn
        (hide.parent as ViewGroup).removeView(hide)
        hide.contentDescription = context.getString(com.kazumaproject.markdownhelperkeyboard.R.string.floating_keyboard_hide)
        hide.setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(hide, LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(8); topMargin = dp(4)
        })
        listOf(binding.floatingKeyboardContainer, binding.floatingSymbolKeyboard, binding.candidatesRowView).forEach {
            (it.layoutParams as ConstraintLayout.LayoutParams).apply { bottomMargin = 0; it.layoutParams = this }
        }
        (binding.root.parent as? ViewGroup)?.removeView(binding.root)
        input.addView(binding.root, LayoutParams(-1, -2))
        contentContainer.addView(input, LayoutParams(-1, -1))
    }

    fun matches(other: FloatingKeyboardLayoutBinding) = binding === other

    fun setKeyboardMode(mode: TenKeyQWERTYMode) {
        keyboardBody.setMinimumLayoutSize(dp(when (mode) {
            TenKeyQWERTYMode.Gojuon -> 360
            TenKeyQWERTYMode.Sumire -> 200
            else -> 240
        }), dp(180))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val insets = contentInsets
        val width = MeasureSpec.getSize(widthMeasureSpec)
        binding.root.measure(MeasureSpec.makeMeasureSpec((width - insets.left - insets.right).coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val desired = binding.root.measuredHeight + insets.top + insets.bottom
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(resolveSize(desired, heightMeasureSpec), MeasureSpec.EXACTLY))
    }

    override fun dispatchHandleEvent(event: MotionEvent) {
        val index = event.actionIndex
        val point = GuidePoint(event.getX(index) + event.rawX - event.x, event.getY(index) + event.rawY - event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2).also(::getLocationOnScreen)
                val visible = FloatingWindowCoordinates(context.getSystemService(android.view.WindowManager::class.java)).safeArea(this)
                // A tall keyboard may occupy the whole available height. Never pass inverted limits to the reducer.
                val bounds = GuideBounds(location[0], location[1], width.coerceAtMost(visible.width()), height.coerceAtMost(visible.height()))
                val area = GuideBounds(visible.left, visible.top, visible.width(), visible.height())
                startBounds = bounds
                startHeight = binding.floatingKeyboardContainer.layoutParams.height
                startWidth = width
                draft = null
                gesture = ComposingGuideGesture(bounds, area, dp(120).coerceAtMost(bounds.width), dp(80).coerceAtMost(bounds.height)).also {
                    handleAt(event.x, event.y)?.let { handle -> it.add(event.getPointerId(index), handle, point) }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> handleAt(event.getX(index), event.getY(index))?.let {
                gesture?.add(event.getPointerId(index), it, point)
            }
            MotionEvent.ACTION_MOVE -> {
                val active = gesture ?: return
                val bounds = active.move((0 until event.pointerCount).associate { i ->
                    event.getPointerId(i) to GuidePoint(event.getX(i) + event.rawX - event.x, event.getY(i) + event.rawY - event.y)
                })
                if (editing) {
                    val original = startBounds ?: return
                    draft = KeyboardLayoutEditConstraints.normalizeFloatingForDraft(KeyboardLayoutEditValues.Floating(
                        ((startHeight + bounds.height - original.height) / resources.displayMetrics.density).roundToInt(),
                        ((startWidth + bounds.width - original.width) * 100f / resources.displayMetrics.widthPixels).roundToInt(),
                    )).also(editCallbacks.onFloatingDraftChanged)
                }
                onPosition(bounds.x, bounds.y, false)
            }
            MotionEvent.ACTION_POINTER_UP -> gesture?.remove(event.getPointerId(index))
            MotionEvent.ACTION_UP -> {
                draft?.let(editCallbacks.onFloatingEditCommitted)
                gesture?.bounds?.let { onPosition(it.x, it.y, true) }
                gesture = null
                draft = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (editing) editCallbacks.onFloatingDraftChanged(KeyboardLayoutEditValues.Floating(
                    (startHeight / resources.displayMetrics.density).roundToInt(),
                    (startWidth * 100f / resources.displayMetrics.widthPixels).roundToInt(),
                ))
                gesture?.cancel()?.let { onPosition(it.x, it.y, false) }
                gesture = null
                draft = null
            }
        }
    }
}
