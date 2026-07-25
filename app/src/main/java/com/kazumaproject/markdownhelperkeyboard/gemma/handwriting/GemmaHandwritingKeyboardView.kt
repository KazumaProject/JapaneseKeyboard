package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.kazumaproject.markdownhelperkeyboard.R

class GemmaHandwritingKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val candidateRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val statusText = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(resolveKeyboardIconColor())
        textSize = 14f
        setPadding(dp(8), 0, dp(8), 0)
    }
    private val progress = ProgressBar(context).apply {
        isIndeterminate = true
        isVisible = false
    }
    private val canvas = HandwritingCanvasView(context).apply {
        contentDescription = context.getString(R.string.gemma_handwriting_canvas_description)
    }

    private val undoButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.undo_24px,
        descriptionRes = R.string.gemma_handwriting_undo,
    )
    private val redoButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.undo_24px,
        descriptionRes = R.string.gemma_handwriting_redo,
    ).apply {
        scaleX = -1f
    }
    private val leftButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
        descriptionRes = R.string.gemma_handwriting_cursor_left,
    )
    private val rightButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
        descriptionRes = R.string.gemma_handwriting_cursor_right,
    )
    private val deleteButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.backspace_24px,
        descriptionRes = R.string.gemma_handwriting_delete_text,
    )
    private val clearButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.baseline_delete_24,
        descriptionRes = R.string.gemma_handwriting_clear_canvas,
    )
    private val recognizeButton = textButton(
        textRes = R.string.gemma_handwriting_recognize_short,
        descriptionRes = R.string.gemma_handwriting_recognize,
    )
    private val returnButton = iconButton(
        iconRes = com.kazumaproject.core.R.drawable.keyboard_24px,
        descriptionRes = R.string.gemma_handwriting_return_keyboard,
    )

    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var onClear: (() -> Unit)? = null
    var onRecognize: (() -> Unit)? = null
    var onReturnToKeyboard: (() -> Unit)? = null
    var onDeleteText: (() -> Unit)? = null
    var onCursorKey: ((Int) -> Unit)? = null
    var onCandidateSelected: ((String) -> Unit)? = null
    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeCommitted: (() -> Unit)? = null
    var onStrokeCancelled: (() -> Unit)? = null
    private var handwritingSettings = GemmaHandwritingSettings()
    private val cursorFlickThresholdPx by lazy {
        maxOf(
            dp(CURSOR_FLICK_MIN_DISTANCE_DP).toFloat(),
            ViewConfiguration.get(context).scaledTouchSlop * 2f,
        )
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(4), dp(6), dp(6))
        applyCanvasTheme()

        val candidateScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                candidateRow,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
            )
        }
        addView(
            candidateScroll,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(42)),
        )

        addView(
            canvas,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = dp(4)
            },
        )

        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            undoButton,
            redoButton,
            leftButton,
            rightButton,
            deleteButton,
            clearButton,
            recognizeButton,
            returnButton,
        ).forEach { button ->
            controls.addView(button, controlButtonLayoutParams())
        }
        addView(
            controls,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(CONTROL_ROW_HEIGHT_DP)),
        )

        undoButton.setOnClickListener { onUndo?.invoke() }
        redoButton.setOnClickListener { onRedo?.invoke() }
        clearButton.setOnClickListener { onClear?.invoke() }
        recognizeButton.setOnClickListener { onRecognize?.invoke() }
        returnButton.setOnClickListener { onReturnToKeyboard?.invoke() }
        deleteButton.setOnClickListener { onDeleteText?.invoke() }
        leftButton.setOnClickListener { onCursorKey?.invoke(KeyEvent.KEYCODE_DPAD_LEFT) }
        rightButton.setOnClickListener { onCursorKey?.invoke(KeyEvent.KEYCODE_DPAD_RIGHT) }
        configureVerticalCursorFlick(leftButton)
        configureVerticalCursorFlick(rightButton)
        canvas.onStrokeStarted = { onStrokeStarted?.invoke() }
        canvas.onStrokeCommitted = { onStrokeCommitted?.invoke() }
        canvas.onStrokeCancelled = { onStrokeCancelled?.invoke() }

        showReady(hasStrokes = false)
        updateHistoryButtons(canUndo = false, canRedo = false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyCanvasTheme()
    }

    fun bindStore(store: HandwritingStrokeStore) {
        canvas.bindStore(store)
    }

    fun refreshCanvas() {
        canvas.refresh()
    }

    fun applySettings(settings: GemmaHandwritingSettings) {
        handwritingSettings = settings
        canvas.setStrokeWidthDp(settings.penSizeDp)
        applyCanvasTheme()
    }

    fun updateHistoryButtons(canUndo: Boolean, canRedo: Boolean) {
        undoButton.isEnabled = canUndo
        redoButton.isEnabled = canRedo
        undoButton.alpha = if (canUndo) 1f else 0.38f
        redoButton.alpha = if (canRedo) 1f else 0.38f
    }

    fun showReady(hasStrokes: Boolean) {
        replaceCandidateContent {
            addView(statusText.apply {
                text = context.getString(R.string.gemma_handwriting_ready)
            })
        }
        recognizeButton.isEnabled = hasStrokes
        canvas.isEnabled = true
    }

    fun showRecognizing() {
        replaceCandidateContent {
            addView(
                progress,
                LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(8)
                },
            )
            progress.isVisible = true
            addView(statusText.apply {
                text = context.getString(R.string.gemma_handwriting_recognizing)
            })
        }
        recognizeButton.isEnabled = false
        canvas.isEnabled = true
    }

    fun showCandidates(candidates: List<String>) {
        replaceCandidateContent {
            candidates.forEach { candidate ->
                addView(
                    MaterialButton(
                        context,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    ).apply {
                        text = candidate
                        isAllCaps = false
                        minWidth = dp(56)
                        minimumWidth = 0
                        insetTop = 0
                        insetBottom = 0
                        contentDescription = context.getString(
                            R.string.gemma_handwriting_candidate_description,
                            candidate,
                        )
                        setOnClickListener { onCandidateSelected?.invoke(candidate) }
                    },
                    LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply {
                        marginEnd = dp(4)
                    },
                )
            }
        }
        recognizeButton.isEnabled = true
        canvas.isEnabled = true
    }

    fun showError(message: String, hasStrokes: Boolean) {
        replaceCandidateContent {
            addView(statusText.apply { text = message })
        }
        recognizeButton.isEnabled = hasStrokes
        canvas.isEnabled = true
    }

    private inline fun replaceCandidateContent(block: LinearLayout.() -> Unit) {
        progress.isVisible = false
        candidateRow.removeAllViews()
        candidateRow.block()
    }

    private fun controlButtonLayoutParams(): LayoutParams {
        return LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureVerticalCursorFlick(button: View) {
        var downRawX = 0f
        var downRawY = 0f
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    false
                }

                MotionEvent.ACTION_UP -> {
                    when (
                        CursorKeyFlickResolver.resolve(
                            deltaX = event.rawX - downRawX,
                            deltaY = event.rawY - downRawY,
                            threshold = cursorFlickThresholdPx,
                        )
                    ) {
                        CursorKeyGesture.Tap -> false
                        CursorKeyGesture.FlickUp -> {
                            cancelButtonTouch(view, event)
                            onCursorKey?.invoke(KeyEvent.KEYCODE_DPAD_UP)
                            true
                        }

                        CursorKeyGesture.FlickDown -> {
                            cancelButtonTouch(view, event)
                            onCursorKey?.invoke(KeyEvent.KEYCODE_DPAD_DOWN)
                            true
                        }

                        CursorKeyGesture.Cancelled -> {
                            cancelButtonTouch(view, event)
                            true
                        }
                    }
                }

                else -> false
            }
        }
    }

    private fun cancelButtonTouch(view: View, source: MotionEvent) {
        MotionEvent.obtain(source).also { cancelEvent ->
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            view.onTouchEvent(cancelEvent)
            cancelEvent.recycle()
        }
    }

    private fun iconButton(iconRes: Int, descriptionRes: Int): MaterialButton {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            setIconResource(iconRes)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            iconSize = dp(24)
            text = ""
            contentDescription = context.getString(descriptionRes)
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun textButton(textRes: Int, descriptionRes: Int): MaterialButton {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = context.getString(textRes)
            textSize = 11f
            isAllCaps = false
            contentDescription = context.getString(descriptionRes)
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(2), 0, dp(2), 0)
        }
    }

    private fun resolveKeyboardIconColor(): Int {
        return runCatching {
            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
        }.getOrDefault(Color.DKGRAY)
    }

    private fun applyCanvasTheme() {
        val darkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (darkMode) 0xFF202124.toInt() else Color.WHITE
        val strokeColor = handwritingSettings.resolvedPenColor(darkMode)
        val borderColor = if (darkMode) 0x66FFFFFF else 0x33000000

        canvas.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), borderColor)
        }
        canvas.setStrokeColor(strokeColor)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val CONTROL_ROW_HEIGHT_DP = 48
        const val CURSOR_FLICK_MIN_DISTANCE_DP = 16
    }
}
