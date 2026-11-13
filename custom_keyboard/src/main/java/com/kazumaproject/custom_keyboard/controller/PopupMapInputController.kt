package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.data.PopupMapVisibility
import com.kazumaproject.core.data.popup.PopupViewStyle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.PopupBounds
import com.kazumaproject.custom_keyboard.data.PopupMapSession
import com.kazumaproject.custom_keyboard.data.PopupMapInputOptions
import com.kazumaproject.custom_keyboard.data.PopupMapPresentation
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
    private val onCancel: () -> Unit,
    options: PopupMapInputOptions = PopupMapInputOptions(),
    private val gestureConfigSource: GestureSessionConfigSource? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var visibility: PopupMapVisibility? = null
    private val longPress = Runnable {
        val key=anchor
        if(key!=null && session!=null && !sameGeometry(key)) {end(true);return@Runnable}
        val active=session
        if(active!=null && visibility?.longPress(active.isTap)==true) render()
    }
    private var lastDrawing: List<Any?>? = null
    private var lastPlacement: List<Int>? = null
    private var popupStyle = PopupViewStyle(100, 20f)
    private var highlightColor: Int? = null
    private var borderColor: Int? = null
    fun applyPopupViewStyle(style: PopupViewStyle, highlightColor: Int? = null, borderColor: Int? = null) {
        popupStyle = style; this.highlightColor = highlightColor; this.borderColor = borderColor
        content?.applyPopupViewStyle(style, highlightColor, borderColor)
        guideHost?.view?.applyPopupViewStyle(style, highlightColor, borderColor)
        guideHost?.updatePointer()
    }
    private var inputOptions = options
    private var guideHost: PopupMapGuideHost? = null
    private var fingerPosition: Pair<Float,Float>? = null
    fun setOptions(value: PopupMapInputOptions) {
        if (inputOptions != value) { cancel(); inputOptions = value }
    }
    private fun sample(x: Float, y: Float) {
        fingerPosition = x/width-.5f to y/height-.5f
    }
    private var observingAttachment = false
    private val attachmentListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { cancel() }
    }
    private fun observeAttachment(view: View) {
        if(!observingAttachment) {view.addOnAttachStateChangeListener(attachmentListener);observingAttachment=true}
    }
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
        observeAttachment(view)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.x !in 0f..view.width.toFloat() || event.y !in 0f..view.height.toFloat()) return@setOnTouchListener false
                    end(true)
                    observeAttachment(view)
                    width = view.width.coerceAtLeast(1); height = view.height.coerceAtLeast(1)
                    rotation = view.rotation
                    view.getLocationOnScreen(locationScratch)
                    initialLeft = locationScratch[0]; initialTop = locationScratch[1]
                    session = PopupMapSession(config, width.toFloat(), height.toFloat(), inputOptions)
                    sample(event.x,event.y)
                    val settings=gestureConfigSource?.snapshot() ?: GestureSessionConfig(0L,100,
                        ViewConfiguration.get(view.context).scaledTouchSlop*3.5f,300L)
                    visibility=PopupMapVisibility(settings,event.x,event.y)
                    handler.postDelayed(longPress,settings.longPressTimeoutMillis)
                    originalLabel = (view as? TextView)?.text
                    view.isPressed = true
                    onPress(session!!.currentMap.tapText)
                    render()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val active = session ?: return@setOnTouchListener true
                    if (!sameGeometry(view)) { end(true); return@setOnTouchListener true }
                    for (i in 0 until event.historySize) {
                        visibility?.move(event.getHistoricalX(i),event.getHistoricalY(i))
                        active.move(event.getHistoricalX(i) / width - .5f, event.getHistoricalY(i) / height - .5f)
                    }
                    visibility?.move(event.x,event.y)
                    if(visibility?.longPressEligible==false) handler.removeCallbacks(longPress)
                    sample(event.x,event.y)
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
        val policy = visibility ?: return
        if(!policy.visible) {popup?.dismiss();guideHost?.dismiss();lastDrawing=null;lastPlacement=null;return}
        val drawingState=listOf(active.currentMap.id,active.selectedRegionId,active.isTap,policy.expanded,inputOptions.presentation)
        if(lastDrawing==drawingState && inputOptions.presentation==PopupMapPresentation.AROUND_KEY) return
        lastDrawing=drawingState
        val host = windowAnchor() ?: key
        if (!key.isAttachedToWindow || !host.isAttachedToWindow || host.windowToken == null) return
        (key as? TextView)?.let { label ->
            val text=transform(active.currentMap.tapText)
            if(label.text.toString()!=text) label.text=text
        }
        if (inputOptions.presentation == PopupMapPresentation.GUIDE_ABOVE_KEY) {
            popup?.dismiss()
            val guide = guideHost ?: PopupMapGuideHost(key.context,windowAnchor) { end(true) }.also {
                it.view.applyPopupViewStyle(popupStyle, highlightColor, borderColor); guideHost=it
            }
            guide.show(key)
            guide.view.apply {
                config=this@PopupMapInputController.config;map=active.currentMap;selectedId=active.selectedRegionId
                sourceKeyWidth=this@PopupMapInputController.width.toFloat();sourceKeyHeight=this@PopupMapInputController.height.toFloat()
                fingerPosition=this@PopupMapInputController.fingerPosition;tapActive=active.isTap;textTransform=transform
                visibleRegionIds=policy.regions(active);tapPopupVisible=policy.showsTap(active)
                title=active.mapPath.joinToString(" → ") { id -> config?.map(id)?.name.orEmpty() } + "  " + transform(active.selectedText.orEmpty())
            }
            guide.updatePointer()
            return
        }
        guideHost?.dismiss()
        val angle = Math.toRadians(rotation.toDouble())
        val cosine = cos(angle).toFloat()
        val sine = sin(angle).toFloat()
        val corners = (listOf(PopupBounds.KEY) + config.maps.flatMap { it.regions.map { region -> region.bounds } }).flatMap { b ->
            listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom).map { (x,y) ->
                (x * width * cosine - y * height * sine) to (x * width * sine + y * height * cosine)
            }
        }
        val left = floor(corners.minOf { it.first }).toInt() - 2
        val top = floor(corners.minOf { it.second }).toInt() - 2
        val right = ceil(corners.maxOf { it.first }).toInt() + 2
        val bottom = ceil(corners.maxOf { it.second }).toInt() + 2
        val drawing = content ?: PopupMapView(key.context).also { it.applyPopupViewStyle(popupStyle, highlightColor, borderColor); content = it }
        drawing.map = active.currentMap; drawing.selectedId = active.selectedRegionId; drawing.tapActive = active.isTap
        drawing.visibleRegionIds=policy.regions(active);drawing.tapPopupVisible=policy.showsTap(active)
        drawing.keyWidth = width.toFloat(); drawing.keyHeight = height.toFloat(); drawing.keyRotation = rotation
        drawing.originX = -left.toFloat(); drawing.originY = -top.toFloat(); drawing.textTransform = transform
        val window = popup ?: PopupWindow(drawing, right-left, bottom-top, false).apply {
            isTouchable = false; isOutsideTouchable = false; isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }.also { popup = it }
        val location = getLocationRelativeToWindowAnchor(key, host)
        val x = location[0] + width / 2 + left
        val y = location[1] + height / 2 + top
        val placement=listOf(x,y,right-left,bottom-top)
        if (window.isShowing) {
            if(lastPlacement!=placement) window.update(x,y,right-left,bottom-top)
        } else {
            window.width = right-left; window.height = bottom-top
            window.showAtLocation(host, Gravity.TOP or Gravity.LEFT, x, y)
        }
        lastPlacement=placement
        drawing.invalidate()
    }
    private fun end(canceled: Boolean) {
        handler.removeCallbacks(longPress)
        visibility=null;lastDrawing=null;lastPlacement=null
        popup?.dismiss()
        guideHost?.dismiss();fingerPosition=null
        anchor?.isPressed = false
        if (session != null) (anchor as? TextView)?.text = originalLabel
        if (canceled && session != null) onCancel()
        session = null
    }
    fun cancel() {
        end(true)
        anchor?.removeOnAttachStateChangeListener(attachmentListener)
        observingAttachment=false
    }
}
