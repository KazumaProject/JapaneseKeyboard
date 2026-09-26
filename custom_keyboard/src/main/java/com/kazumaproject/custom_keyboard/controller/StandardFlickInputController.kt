package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.flick.FixedGestureSessionConfigSource
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.PopupDirection
import com.kazumaproject.core.ui.skin.SkinGuidePopup
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.StandardFlickPopupView
import com.kazumaproject.custom_keyboard.view.skinDirection
import java.lang.ref.WeakReference
import java.util.WeakHashMap

class StandardFlickInputController(
    private val context: Context,
    private val gestureConfigSource: GestureSessionConfigSource
) {

    companion object {
        /** One active standard flick guide per keyboard window, even though each key owns a controller. */
        private val activeSkinGuides = WeakHashMap<ViewGroup, WeakReference<StandardFlickInputController>>()
    }

    constructor(context: Context) : this(
        context = context,
        gestureConfigSource = FixedGestureSessionConfigSource(
            GestureSessionConfig(
                settingsRevision = 0L,
                flickSensitivity = 100,
                flickThresholdPx = 65f,
                longPressTimeoutMillis =
                    ViewConfiguration.getLongPressTimeout().toLong().coerceIn(100L, 2_000L)
            )
        )
    )

    interface StandardFlickListener {
        fun onPress(character: String)
        fun onFlick(character: String)
        fun onSelectionChanged(character: String?, isFlick: Boolean) {}
        fun onCanceled() {}
    }

    var listener: StandardFlickListener? = null
    private var popupWindowAnchorProvider: (() -> View?)? = null
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private var anchorView: View? = null
    private var segmentedDrawable: SegmentedBackgroundDrawable? = null

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var activeGestureConfig: GestureSessionConfig? = null
    private var inputTextTransform: (String) -> String = { it }
    private var skinGuidePopup: SkinGuidePopup? = null
    private var registeredGuideRoot: ViewGroup? = null

    private val popupWindow: PopupWindow
    private val popupView = StandardFlickPopupView(context)

    private var popupBackgroundColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupStrokeColor: Int = Color.LTGRAY
    private var popupStyle = PopupViewStyle(100, 19f)

    init {
        popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
            elevation = 8f
            animationStyle = 0
            enterTransition = null
            exitTransition = null
        }
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        this.popupBackgroundColor = theme.segmentHighlightGradientStartColor
        this.popupTextColor = theme.textColor
        this.popupStrokeColor = theme.separatorColor
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupStyle = PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor,
            skinId = style.skinId
        )
        popupView.applyPopupViewStyle(popupStyle)
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
    }

    fun setInputTextTransform(transform: (String) -> String) {
        inputTextTransform = transform
        popupView.setInputTextTransform(transform)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        button: View,
        map: Map<FlickDirection, String>,
        drawable: SegmentedBackgroundDrawable
    ) {
        val completeMap = mutableMapOf<FlickDirection, String>()
        completeMap[FlickDirection.TAP] = map[FlickDirection.TAP] ?: ""
        completeMap[FlickDirection.UP] = map[FlickDirection.UP] ?: ""
        completeMap[FlickDirection.DOWN] = map[FlickDirection.DOWN] ?: ""
        completeMap[FlickDirection.UP_LEFT_FAR] = map[FlickDirection.UP_LEFT_FAR]
            ?: map.entries.find { it.key.name.contains("LEFT") }?.value ?: ""

        completeMap[FlickDirection.UP_RIGHT_FAR] = map[FlickDirection.UP_RIGHT_FAR]
            ?: map.entries.find { it.key.name.contains("RIGHT") }?.value ?: ""

        this.characterMap = completeMap
        this.segmentedDrawable = drawable
        button.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dismissOtherSkinGuide(view)
                activeGestureConfig = gestureConfigSource.snapshot()
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                listener?.onPress(characterMap[FlickDirection.TAP] ?: "")
                segmentedDrawable?.highlightDirection = FlickDirection.TAP
                showPopup(FlickDirection.TAP, refreshSkinGuide = true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val direction = calculateDirection(dx, dy)
                segmentedDrawable?.highlightDirection = direction
                showPopup(direction)
                listener?.onSelectionChanged(
                    characterMap[direction]?.takeIf(String::isNotEmpty),
                    direction != FlickDirection.TAP
                )
                return true
            }

            MotionEvent.ACTION_UP -> {
                segmentedDrawable?.highlightDirection = null
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val finalDirection = calculateDirection(dx, dy)
                characterMap[finalDirection]?.let {
                    if (it.isNotEmpty()) {
                        listener?.onFlick(it)
                    }
                }
                dismissPopup(animateSkinRelease = true)
                activeGestureConfig = null
                anchorView = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                segmentedDrawable?.highlightDirection = null
                dismissPopup()
                anchorView = null
                activeGestureConfig = null
                listener?.onCanceled()
                return true
            }
        }
        return false
    }

    private fun showPopup(direction: FlickDirection, refreshSkinGuide: Boolean = false) {
        val keyAnchor = anchorView ?: return
        val windowAnchor = popupWindowAnchorProvider?.invoke() ?: keyAnchor
        if (!isAnchorReady(keyAnchor, windowAnchor)) {
            dismissPopup()
            return
        }

        val skin = KeyboardSkinRegistry.find(popupStyle.skinId)
        if (skin != null) {
            popupWindow.dismiss()
            val labels = skinGuideLabels()
            val hasAlternatives = labels.keys.any { it != PopupDirection.CENTER }
            if (!hasAlternatives) {
                dismissSkinGuideImmediately()
                return
            }

            val guide = skinGuidePopup ?: SkinGuidePopup(context).also { skinGuidePopup = it }
            if (refreshSkinGuide || !guide.isShowing) {
                guide.show(keyAnchor, skin, labels, popupStyle.textSizeSp)
            }
            registerSkinGuide(keyAnchor.rootView as? ViewGroup)
            guide.select(direction.skinDirection())
            return
        }

        dismissSkinGuideImmediately()

        popupView.setFlickDirection(direction)
        popupView.setColors(popupBackgroundColor, popupTextColor, popupStrokeColor)
        popupView.applyPopupViewStyle(popupStyle)

        if (direction == FlickDirection.TAP) {
            popupView.updateMultiCharText(characterMap)
        } else {
            val text = characterMap[direction]
            popupView.updateText(text)
        }

        popupView.setPadding(0, 0, 0, 0)
        popupWindow.elevation = 8f
        popupWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT

        val baseOffsetY = 10
        val flickUpAdditionalOffset = 80

        val location = getLocationRelativeToWindowAnchor(keyAnchor, windowAnchor)
        val x = location[0] + (keyAnchor.width / 2) - (popupView.viewSize / 2)
        var y = location[1] - popupView.viewSize - baseOffsetY

        if (direction == FlickDirection.UP) {
            y -= flickUpAdditionalOffset
        }

        if (popupWindow.isShowing) {
            runCatching {
                popupWindow.update(x, y, -1, -1)
            }
        } else {
            runCatching {
                popupWindow.showAtLocation(windowAnchor, Gravity.NO_GRAVITY, x, y)
            }
        }
    }

    private fun skinGuideLabels(): Map<PopupDirection, CharSequence> = characterMap.mapNotNull { (direction, value) ->
        if (value.isEmpty()) return@mapNotNull null
        val popupDirection = direction.skinDirection()
        popupDirection to inputTextTransform(value)
    }.toMap()

    private fun dismissPopup(animateSkinRelease: Boolean = false) {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
        val guide = skinGuidePopup
        if (guide?.isShowing != true) {
            unregisterSkinGuide()
            return
        }

        val releaseAnimationDuration = if (animateSkinRelease) {
            KeyboardSkinRegistry.find(popupStyle.skinId)?.popupReleaseAnimationMillis ?: 0L
        } else 0L
        val dismissalAnchor = anchorView
        if (releaseAnimationDuration <= 0L || dismissalAnchor == null || !dismissalAnchor.isAttachedToWindow) {
            dismissSkinGuideImmediately()
            return
        }

        guide.dismiss(
            animated = true,
            animationDurationMillis = releaseAnimationDuration,
            onDismissComplete = ::unregisterSkinGuide,
        )
    }

    private fun dismissOtherSkinGuide(anchor: View) {
        val root = anchor.rootView as? ViewGroup ?: return
        val active = activeSkinGuides[root]?.get()
        if (active == null) {
            activeSkinGuides.remove(root)
        } else if (active !== this) {
            active.dismissSkinGuideImmediately()
        }
    }

    private fun registerSkinGuide(root: ViewGroup?) {
        if (root == null) return
        if (registeredGuideRoot !== root) unregisterSkinGuide()
        registeredGuideRoot = root
        activeSkinGuides[root] = WeakReference(this)
    }

    private fun unregisterSkinGuide() {
        registeredGuideRoot?.let { root ->
            if (activeSkinGuides[root]?.get() === this) activeSkinGuides.remove(root)
        }
        registeredGuideRoot = null
    }

    private fun dismissSkinGuideImmediately() {
        skinGuidePopup?.dismiss()
        unregisterSkinGuide()
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val config = activeGestureConfig ?: gestureConfigSource.snapshot()
        if (
            !FlickGestureMath.isThresholdCrossed(
                deltaX = dx,
                deltaY = dy,
                thresholdPx = config.flickThresholdPx,
                thresholdShape = config.flickThresholdShape
            )
        ) {
            return FlickDirection.TAP
        }

        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))

        return when {
            angle > -45 && angle <= 45 -> FlickDirection.UP_RIGHT_FAR
            angle > 45 && angle <= 135 -> FlickDirection.DOWN
            angle < -45 && angle >= -135 -> FlickDirection.UP
            else -> FlickDirection.UP_LEFT_FAR
        }
    }

    fun cancel() {
        listener?.onCanceled()
        activeGestureConfig = null
        dismissPopup()
        anchorView = null
    }

    private fun isAnchorReady(keyAnchor: View, windowAnchor: View?): Boolean {
        if (!keyAnchor.isAttachedToWindow) return false
        if (windowAnchor == null) return false
        if (!windowAnchor.isAttachedToWindow) return false
        return windowAnchor.windowToken != null
    }
}
