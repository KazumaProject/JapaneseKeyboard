package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.PopupMapSession
import com.kazumaproject.custom_keyboard.view.PopupMapView
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin

class PopupMapInputController(
    private val config: PopupKeyConfig,
    private val windowAnchor: () -> View?,
    private val transform: (String) -> String,
    private val onPress: (String) -> Unit,
    private val onSelection: (String?, Boolean) -> Unit,
    private val onCommit: (String, Boolean) -> Unit,
    private val onCancel: () -> Unit
) {
    private var anchor: View? = null
    private var session: PopupMapSession? = null
    private var popup: PopupWindow? = null
    private var content: PopupMapView? = null
    private var originalLabel: CharSequence? = null
    private var width = 1
    private var height = 1
    private var rotation = 0f
    private val locationScratch = IntArray(2)
    private var initialLeft = 0
    private var initialTop = 0
    private fun sameGeometry(view: View): Boolean {
        view.getLocationOnScreen(locationScratch)
        return view.width == width && view.height == height && view.rotation == rotation &&
            locationScratch[0] == initialLeft && locationScratch[1] == initialTop
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View) {
        anchor = view
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.x !in 0f..view.width.toFloat() || event.y !in 0f..view.height.toFloat()) return@setOnTouchListener false
                    width = view.width.coerceAtLeast(1); height = view.height.coerceAtLeast(1)
                    rotation = view.rotation
                    view.getLocationOnScreen(locationScratch)
                    initialLeft = locationScratch[0]; initialTop = locationScratch[1]
                    session = PopupMapSession(config, width.toFloat(), height.toFloat())
                    originalLabel = (view as? TextView)?.text
                    view.isPressed = true
                    onPress(session!!.currentMap.tapText)
                    render()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val active = session ?: return@setOnTouchListener true
                    if (!sameGeometry(view)) { end(true); return@setOnTouchListener true }
                    for (i in 0 until event.historySize) active.move(event.getHistoricalX(i) / width - .5f, event.getHistoricalY(i) / height - .5f)
                    active.move(event.x / width - .5f, event.y / height - .5f)
                    onSelection(active.selectedText, !active.isTap)
                    render()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!sameGeometry(view)) { end(true); return@setOnTouchListener true }
                    val active = session
                    val output = active?.release(event.x / width - .5f, event.y / height - .5f)
                    val flick = active?.isTap == false
                    end(output == null)
                    if (output != null) onCommit(output, flick)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { end(true); true }
                else -> true
            }
        }
    }

    private fun render() {
        val key = anchor ?: return
        val active = session ?: return
        val host = windowAnchor() ?: key
        if (!key.isAttachedToWindow || !host.isAttachedToWindow || host.windowToken == null) return
        (key as? TextView)?.text = transform(active.currentMap.tapText)
        val regions = active.currentMap.regions
        if (regions.isEmpty()) { popup?.dismiss(); return }
        val angle = Math.toRadians(rotation.toDouble())
        val cosine = cos(angle).toFloat()
        val sine = sin(angle).toFloat()
        val corners = regions.flatMap { region ->
            val b = region.bounds
            listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom).map { (x,y) ->
                (x * width * cosine - y * height * sine) to (x * width * sine + y * height * cosine)
            }
        }
        val left = floor(corners.minOf { it.first }).toInt() - 2
        val top = floor(corners.minOf { it.second }).toInt() - 2
        val right = ceil(corners.maxOf { it.first }).toInt() + 2
        val bottom = ceil(corners.maxOf { it.second }).toInt() + 2
        val drawing = content ?: PopupMapView(key.context).also { content = it }
        drawing.map = active.currentMap; drawing.selectedId = active.selectedRegionId
        drawing.keyWidth = width.toFloat(); drawing.keyHeight = height.toFloat(); drawing.keyRotation = rotation
        drawing.originX = -left.toFloat(); drawing.originY = -top.toFloat(); drawing.textTransform = transform
        val window = popup ?: PopupWindow(drawing, right-left, bottom-top, false).apply {
            isTouchable = false; isOutsideTouchable = false; isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }.also { popup = it }
        val location = getLocationRelativeToWindowAnchor(key, host)
        val x = location[0] + width / 2 + left
        val y = location[1] + height / 2 + top
        if (window.isShowing) window.update(x,y,right-left,bottom-top) else {
            window.width = right-left; window.height = bottom-top
            window.showAtLocation(host, Gravity.TOP or Gravity.LEFT, x, y)
        }
        drawing.invalidate()
    }
    private fun end(canceled: Boolean) {
        popup?.dismiss()
        anchor?.isPressed = false
        if (session != null) (anchor as? TextView)?.text = originalLabel
        if (canceled && session != null) onCancel()
        session = null
    }
    fun cancel() { end(true) }
}
