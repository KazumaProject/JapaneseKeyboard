package com.kazumaproject.custom_keyboard.controller

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.data.popup.TfbiFlickStartPositionMode
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiGestureArrowView
import com.kazumaproject.custom_keyboard.view.TfbiGuidePopupView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hosts the optional TFBi guide and the gesture arrow in the keyboard's own window.
 *
 * The guide is intentionally anchored to the pressed key.  A normal PopupWindow is not used
 * here because the IME can be resized while the gesture is in progress; [KeyboardPopupOverlay]
 * keeps both layers in the same draw traversal as the key.
 */
internal class TfbiGuidePopupHost(
    private val context: Context,
    private val preferredHostProvider: () -> View?
) {
    private val overlay = KeyboardPopupOverlay(::positionVisiblePopups)
    private val keyLocation = IntArray(2)
    private val hostLocation = IntArray(2)
    private val relativeLocation = IntArray(2)

    private var anchorView: View? = null
    private var guideView: TfbiGuidePopupView? = null
    private var arrowView: TfbiGestureArrowView? = null
    private var guideWidth = 1
    private var guideHeight = 1
    private var currentGapPx = 1
    private var currentState: TfbiGuidePopupState? = null
    private var configuredBackgroundColor: Int? = null
    private var configuredHighlightedColor: Int? = null
    private var configuredTextColor: Int? = null

    fun show(
        anchor: View,
        state: TfbiGuidePopupState,
        direction: TfbiFlickDirection?,
        style: PopupViewStyle,
        inputTextTransform: (String) -> String
    ) {
        dismiss()

        val panel = guideView ?: TfbiGuidePopupView(context).also { guideView = it }
        val arrow = arrowView ?: TfbiGestureArrowView(context).also { arrowView = it }
        panel.setInputTextTransform(inputTextTransform)
        panel.applyPopupViewStyle(style)
        applyConfiguredColors(panel, arrow)
        currentState = state
        panel.setState(state)
        arrow.setDirection(direction)

        anchorView = anchor
        val scale = style.sizeScalePercent.coerceIn(50, 200) / 100f
        val baseSize = min(anchor.width, anchor.height).coerceAtLeast(1)
        guideWidth = (baseSize * 1.85f * scale).roundToInt().coerceAtLeast(dp(72f))
        guideHeight = (baseSize * 1.65f * scale).roundToInt().coerceAtLeast(dp(68f))
        currentGapPx = dp(4f)

        val host = preferredHostProvider()
        if (!overlay.show(anchor, host, panel, guideWidth, guideHeight)) {
            anchorView = null
            currentState = null
            return
        }
        // Add the arrow after the panel so it is drawn above the key and the panel edge.
        overlay.show(anchor, host, arrow, anchor.width, anchor.height)
        positionVisiblePopups()
    }

    fun update(
        state: TfbiGuidePopupState,
        direction: TfbiFlickDirection?
    ) {
        val resolvedState = state.copy(
            fingerPosition = state.fingerPosition ?: currentState?.fingerPosition
        )
        currentState = resolvedState
        guideView?.setState(resolvedState)
        arrowView?.setDirection(direction)
        positionVisiblePopups()
    }

    /** Updates the cursor without rebuilding the labels or the current selection. */
    fun updateFingerPosition(position: TfbiGuideFingerPosition?) {
        val state = currentState ?: return
        val updatedState = state.copy(fingerPosition = position)
        currentState = updatedState
        guideView?.setState(updatedState)
        positionVisiblePopups()
    }

    fun setInputTextTransform(transform: (String) -> String) {
        guideView?.setInputTextTransform(transform)
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        guideView?.let { panel ->
            panel.applyPopupViewStyle(style)
            applyConfiguredColors(panel, arrowView)
        }
    }

    fun setColors(
        backgroundColor: Int,
        highlightedColor: Int,
        textColor: Int
    ) {
        configuredBackgroundColor = backgroundColor
        configuredHighlightedColor = highlightedColor
        configuredTextColor = textColor
        applyConfiguredColors(guideView, arrowView)
    }

    fun dismiss() {
        overlay.dismissAll()
        anchorView = null
        currentState = null
    }

    private fun positionVisiblePopups() {
        val anchor = anchorView ?: return
        val host = overlay.currentHost ?: return
        if (host.width <= 0 || host.height <= 0 || anchor.width <= 0 || anchor.height <= 0) return

        getLocationRelativeToOverlayHost(
            keyAnchor = anchor,
            overlayHost = host,
            outLocation = relativeLocation,
            keyLocationScratch = keyLocation,
            hostLocationScratch = hostLocation
        )

        val placement = resolveTfbiGuidePopupPlacement(
            keyLeft = relativeLocation[0],
            keyTop = relativeLocation[1],
            keyWidth = anchor.width,
            panelWidth = guideWidth,
            panelHeight = guideHeight,
            hostWidth = host.width,
            hostHeight = host.height,
            gap = currentGapPx
        )
        guideView?.let { overlay.place(it, placement.panelLeft, placement.panelTop, guideWidth, guideHeight) }
        arrowView?.let {
            overlay.place(it, placement.arrowLeft, placement.arrowTop, anchor.width, anchor.height)
        }
    }

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    private fun applyConfiguredColors(
        panel: TfbiGuidePopupView?,
        arrow: TfbiGestureArrowView?
    ) {
        val backgroundColor = configuredBackgroundColor
        val highlightedColor = configuredHighlightedColor
        val textColor = configuredTextColor
        if (panel != null && backgroundColor != null && highlightedColor != null && textColor != null) {
            panel.setColors(backgroundColor, highlightedColor, textColor)
        }
        arrow?.setColor(highlightedColor ?: DEFAULT_ARROW_COLOR)
    }

    private companion object {
        const val DEFAULT_ARROW_COLOR: Int = 0xff1976d2.toInt()
    }
}

/** Converts a key-local touch coordinate into the guide panel's normalized coordinate space. */
internal fun resolveTfbiGuideFingerPosition(
    anchor: View,
    event: MotionEvent
): TfbiGuideFingerPosition? {
    return resolveTfbiGuideFingerPosition(
        keyWidth = anchor.width,
        keyHeight = anchor.height,
        eventX = event.x,
        eventY = event.y,
        startPositionMode = TfbiFlickStartPositionMode.TOUCH_POINT,
        downX = event.x,
        downY = event.y
    )
}

/**
 * Converts a key-local touch coordinate into the guide panel's normalized coordinate space.
 *
 * In KEY_CENTER mode the marker uses a virtual coordinate system. It starts at the key
 * center and follows the displacement from the original ACTION_DOWN position. The raw touch
 * displacement is still used by the controllers for the flick threshold, so touching an edge
 * does not immediately produce a flick.
 */
internal fun resolveTfbiGuideFingerPosition(
    anchor: View,
    event: MotionEvent,
    startPositionMode: TfbiFlickStartPositionMode,
    downX: Float,
    downY: Float
): TfbiGuideFingerPosition? {
    return resolveTfbiGuideFingerPosition(
        keyWidth = anchor.width,
        keyHeight = anchor.height,
        eventX = event.x,
        eventY = event.y,
        startPositionMode = startPositionMode,
        downX = downX,
        downY = downY
    )
}

internal fun resolveTfbiGuideFingerPosition(
    keyWidth: Int,
    keyHeight: Int,
    eventX: Float,
    eventY: Float,
    startPositionMode: TfbiFlickStartPositionMode,
    downX: Float,
    downY: Float
): TfbiGuideFingerPosition? {
    if (keyWidth <= 0 || keyHeight <= 0) return null

    val (x, y) = when (startPositionMode) {
        TfbiFlickStartPositionMode.TOUCH_POINT -> eventX to eventY
        TfbiFlickStartPositionMode.KEY_CENTER -> {
            keyWidth / 2f + (eventX - downX) to
                    keyHeight / 2f + (eventY - downY)
        }
    }
    return TfbiGuideFingerPosition(
        x = (x / keyWidth.toFloat()).coerceIn(0f, 1f),
        y = (y / keyHeight.toFloat()).coerceIn(0f, 1f)
    )
}
