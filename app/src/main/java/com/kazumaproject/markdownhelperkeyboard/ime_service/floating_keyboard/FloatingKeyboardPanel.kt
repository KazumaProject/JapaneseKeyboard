package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardBody
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
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
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var chromeSettings = FloatingKeyboardChromeSettings.read(preferences)
    private val hideButtonBounds = Rect()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == FloatingKeyboardChromeSettings.HIDE_BUTTON_POSITION ||
            key == FloatingKeyboardChromeSettings.SHOW_EDIT_BUTTON) {
            applyChromeSettings()
        }
    }
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
        hide.isClickable = true
        hide.isFocusable = true
        hide.setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(hide, LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.LEFT).apply {
            leftMargin = dp(8)
        })
        listOf(binding.floatingKeyboardContainer, binding.floatingSymbolKeyboard, binding.candidatesRowView).forEach {
            (it.layoutParams as ConstraintLayout.LayoutParams).apply { bottomMargin = 0; it.layoutParams = this }
        }
        (binding.root.parent as? ViewGroup)?.removeView(binding.root)
        input.addView(binding.root, LayoutParams(-1, -2))
        contentContainer.addView(input, LayoutParams(-1, -1))
        applyChromeSettings()
    }

    private fun applyChromeSettings() {
        chromeSettings = FloatingKeyboardChromeSettings.read(preferences)
        val atTop = chromeSettings.hideButtonPosition == FloatingHideButtonPosition.TOP_LEFT
        setHeaderVisible(chromeSettings.showEditButton || editing)
        // Reserve a full touch target at the bottom; the keyboard keys keep their height.
        setMoveBandHeight(if (atTop) FloatingPanelFrame.MOVE_BAND_DP else 48)
        binding.suggestionViewParent.minimumHeight = if (atTop) dp(48) else 0
        val suggestions = binding.suggestionRecyclerView
        (suggestions.layoutParams as ConstraintLayout.LayoutParams).apply {
            startToStart = ConstraintLayout.LayoutParams.UNSET
            endToEnd = ConstraintLayout.LayoutParams.UNSET
            leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            leftMargin = if (atTop) dp(48) else 0
            suggestions.layoutParams = this
        }
        binding.floatingHideKeyboardBtn.imageTintList = ColorStateList.valueOf(colors.icon)
        requestLayout()
    }

    override fun setEditing(value: Boolean) {
        super.setEditing(value)
        // Even with the pencil hidden, shortcut-initiated editing needs a Done button.
        setHeaderVisible(chromeSettings.showEditButton || value)
    }

    override fun setColors(value: CandidatePanelColors) {
        super.setColors(value)
        binding.floatingHideKeyboardBtn.imageTintList = ColorStateList.valueOf(value.icon)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        applyChromeSettings()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (chromeSettings.hideButtonPosition == FloatingHideButtonPosition.TOP_LEFT) {
            val row = binding.suggestionViewParent
            hideButtonBounds.set(0, 0, row.width, row.height)
            offsetDescendantRectToMyCoords(row, hideButtonBounds)
            val hide = binding.floatingHideKeyboardBtn
            val buttonTop = hideButtonBounds.top + (row.height - hide.measuredHeight) / 2
            hide.layout(hideButtonBounds.left, buttonTop,
                hideButtonBounds.left + hide.measuredWidth, buttonTop + hide.measuredHeight)
        }
    }

    override fun handleAt(x: Float, y: Float): GuideHandle? {
        binding.floatingHideKeyboardBtn.getHitRect(hideButtonBounds)
        // The bottom-left button belongs to the same band as the drag grip. Do not
        // intercept its tap as a move (or resize) gesture.
        if (hideButtonBounds.contains(x.toInt(), y.toInt())) return null
        return super.handleAt(x, y)
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
