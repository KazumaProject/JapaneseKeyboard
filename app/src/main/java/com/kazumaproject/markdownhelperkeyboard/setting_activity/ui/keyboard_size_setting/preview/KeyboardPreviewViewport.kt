package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Measures a fixed logical keyboard canvas while exposing only its uniformly scaled size. */
class KeyboardPreviewViewport @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val safePaddingPx = (24f * resources.displayMetrics.density).roundToInt()
    private val maxPreviewHeightPx = (480f * resources.displayMetrics.density).roundToInt()
    private val minimumTouchTargetPx = (48f * resources.displayMetrics.density).roundToInt()
    private val minimumResizeThicknessPx = (24f * resources.displayMetrics.density).roundToInt()
    private val resizeHandleIds = intArrayOf(
        R.id.handle_top,
        R.id.handle_bottom,
        R.id.handle_left,
        R.id.handle_right
    )

    private var canvas: ConstraintLayout? = null
    private var logicalWidthPx = 1
    private var logicalHeightPx = 1
    private var previewScale = 1f
    private var measuredCanvasWidthPx = 1
    private var measuredCanvasHeightPx = 1
    private val originalHandleSizes = mutableMapOf<Int, Pair<Int, Int>>()

    val scale: Float
        get() = previewScale

    val logicalCanvasWidth: Int
        get() = logicalWidthPx

    val logicalCanvasHeight: Int
        get() = logicalHeightPx

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(safePaddingPx, safePaddingPx, safePaddingPx, safePaddingPx)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        canvas = findViewById<ConstraintLayout>(R.id.keyboard_preview_canvas)?.also { view ->
            (resizeHandleIds.asIterable() + R.id.handle_move).forEach { id ->
                view.findViewById<View>(id)?.layoutParams?.let { params ->
                    originalHandleSizes[id] = params.width to params.height
                }
            }
            resizeHandleIds.forEach { id ->
                view.findViewById<View>(id)?.addOnLayoutChangeListener { handle, _, _, _, _, _, _, _, _ ->
                    positionHandleInsideCanvas(handle)
                }
            }
        }
    }

    fun setLogicalCanvasSize(widthPx: Int, heightPx: Int) {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        if (logicalWidthPx == width && logicalHeightPx == height) return
        logicalWidthPx = width
        logicalHeightPx = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> widthSize
            else -> logicalWidthPx + paddingLeft + paddingRight
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val availableHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> min(heightSize, maxPreviewHeightPx)
            MeasureSpec.AT_MOST -> min(heightSize, maxPreviewHeightPx)
            else -> maxPreviewHeightPx
        }
        val scale = KeyboardPreviewGeometry.fitScale(
            size = KeyboardPreviewGeometry.Size(logicalWidthPx, logicalHeightPx),
            availableWidthPx = measuredWidth,
            maxHeightPx = availableHeight,
            paddingPx = safePaddingPx
        )
        previewScale = scale
        measuredCanvasWidthPx = (logicalWidthPx * scale).roundToInt().coerceAtLeast(1)
        measuredCanvasHeightPx = (logicalHeightPx * scale).roundToInt().coerceAtLeast(1)

        canvas?.let { child ->
            expandHandleHitTargets(child, scale)
            child.measure(
                MeasureSpec.makeMeasureSpec(logicalWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(logicalHeightPx, MeasureSpec.EXACTLY)
            )
            child.pivotX = 0f
            child.pivotY = 0f
            child.scaleX = scale
            child.scaleY = scale
        }

        val desiredHeight = measuredCanvasHeightPx + paddingTop + paddingBottom
        val finalHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> min(desiredHeight, maxPreviewHeightPx)
        }
        setMeasuredDimension(measuredWidth, finalHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = canvas ?: return
        val x = ((right - left - measuredCanvasWidthPx) / 2).coerceAtLeast(paddingLeft)
        child.layout(x, paddingTop, x + logicalWidthPx, paddingTop + logicalHeightPx)
        resizeHandleIds.forEach { id ->
            child.findViewById<View>(id)?.let(::positionHandleInsideCanvas)
        }
    }

    private fun expandHandleHitTargets(previewCanvas: ConstraintLayout, scale: Float) {
        val keyboard = previewCanvas.findViewById<View>(R.id.keyboard_container)
        val keyboardParams = keyboard?.layoutParams
        val keyboardWidth = logicalDimension(keyboardParams?.width, logicalWidthPx)
        val keyboardHeight = logicalDimension(keyboardParams?.height, logicalHeightPx)
        val edgeThicknessWidth = (keyboardWidth / 3).coerceAtLeast(1)
        val edgeThicknessHeight = (keyboardHeight / 3).coerceAtLeast(1)

        originalHandleSizes.forEach { (id, originalSize) ->
            val handle = previewCanvas.findViewById<View>(id) ?: return@forEach
            val params = handle.layoutParams
            val minimumWidth: Int
            val minimumHeight: Int
            val maxWidth: Int
            val maxHeight: Int
            when (id) {
                R.id.handle_top, R.id.handle_bottom -> {
                    minimumWidth = minimumTouchTargetPx
                    minimumHeight = minimumResizeThicknessPx
                    maxWidth = keyboardWidth
                    maxHeight = edgeThicknessHeight
                }
                R.id.handle_left, R.id.handle_right -> {
                    minimumWidth = minimumResizeThicknessPx
                    minimumHeight = minimumTouchTargetPx
                    maxWidth = edgeThicknessWidth
                    maxHeight = keyboardHeight
                }
                else -> {
                    minimumWidth = minimumTouchTargetPx
                    minimumHeight = minimumTouchTargetPx
                    maxWidth = keyboardWidth
                    maxHeight = keyboardHeight
                }
            }
            val width = max(originalSize.first, ceil(minimumWidth / scale).toInt())
                .coerceIn(1, maxWidth)
            val height = max(originalSize.second, ceil(minimumHeight / scale).toInt())
                .coerceIn(1, maxHeight)
            if (params.width != width || params.height != height) {
                params.width = width
                params.height = height
                handle.layoutParams = params
            }
        }
    }

    private fun logicalDimension(layoutDimension: Int?, canvasDimension: Int): Int = when {
        layoutDimension == null -> canvasDimension
        layoutDimension > 0 -> layoutDimension.coerceAtMost(canvasDimension)
        layoutDimension == ViewGroup.LayoutParams.MATCH_PARENT -> canvasDimension
        else -> canvasDimension
    }.coerceAtLeast(1)

    private fun positionHandleInsideCanvas(handle: View) {
        val parent = canvas ?: return
        if (parent.width == 0 || parent.height == 0 || handle.width == 0 || handle.height == 0) return
        val desiredX: Float
        val desiredY: Float
        when (handle.id) {
            R.id.handle_top -> { desiredX = 0f; desiredY = -handle.height / 2f }
            R.id.handle_bottom -> { desiredX = 0f; desiredY = handle.height / 2f }
            R.id.handle_left -> { desiredX = -handle.width / 2f; desiredY = 0f }
            R.id.handle_right -> { desiredX = handle.width / 2f; desiredY = 0f }
            else -> return
        }
        // Both axes matter: a side strip can extend below a short keyboard at the bottom edge.
        handle.translationX = desiredX.coerceIn(-handle.left.toFloat(), (parent.width - handle.right).toFloat())
        handle.translationY = desiredY.coerceIn(-handle.top.toFloat(), (parent.height - handle.bottom).toFloat())
    }
}
