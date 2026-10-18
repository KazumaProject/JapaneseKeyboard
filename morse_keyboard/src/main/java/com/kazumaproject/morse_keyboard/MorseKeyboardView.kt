package com.kazumaproject.morse_keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.SkinKeyRole

class MorseKeyboardView @JvmOverloads constructor(baseContext: Context, attrs: AttributeSet? = null) : LinearLayout(localizedMorseContext(baseContext), attrs) {
    var protectNavigationBar: Boolean = false
    var inputAllowed: () -> Boolean = { true }
    var hasConfiguration: Boolean = false
        private set
    var onOutput: (MorseOutput) -> Boolean = { false }
    var onCommand: (MorseCommand) -> Unit = {}
    var onInteraction: () -> Unit = {}
    var highlightControl: MorseControl? = null
        set(value) {
            field = value
            controls.forEachIndexed { index, key -> key.isSelected = settings.controlLayout.rows.flatten().getOrNull(index) == value }
            applyAppearance()
        }
    var onControlSelected: ((MorseControl) -> Unit)? = null
    var onMapSelected: (String) -> Unit = {}
    private var maps = MorsePresets.maps(resources.configuration.locales[0].language == "ja")
    private var settings = MorseSettings()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tone = MorseTone(context)
    private val windowLocation = IntArray(2)
    private val pointerPads = mutableMapOf<Int, Boolean>()
    private val mapBar = LinearLayout(context)
    private val preview = MorseSignalView(context)
    private val pads = LinearLayout(context)
    private val padContainer = LinearLayout(context)
    private val dotPad = pad(MorseGlyphView.Glyph.DOT, R.string.morse_dot)
    private val dashPad = pad(MorseGlyphView.Glyph.DASH, R.string.morse_dash)
    private val controlContainer = LinearLayout(context).apply { orientation = VERTICAL }
    private val controls = mutableListOf<MorseGlyphView>()
    private val defaultDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private var backgroundColorValue = if (defaultDark) 0xff111722.toInt() else 0xfff1f4fa.toInt()
    private var keyColor = if (defaultDark) 0xff202a3a.toInt() else Color.WHITE
    private var textColor = if (defaultDark) 0xffe2e9fa.toInt() else 0xff243049.toInt()
    private var specialColor = if (defaultDark) 0xff192231.toInt() else 0xffe8edf6.toInt()
    private var customColors = false
    private var skinId = KeyboardSkinId.DEFAULT
    private var ready = false
    private val engine = MorseEngine(object : MorseScheduler {
        override fun now() = SystemClock.uptimeMillis()
        override fun after(delayMs: Long, action: () -> Unit): MorseCancellation {
            val runnable = Runnable { action() }
            mainHandler.postDelayed(runnable, delayMs)
            return MorseCancellation { mainHandler.removeCallbacks(runnable) }
        }
    }, maps.first(), onOutput = { output -> onOutput(output) }, onPattern = { updatePreview() }, onTone = { on ->
        tone.set(on, settings)
        if (on && settings.vibration) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    })

    val signal: String get() = engine.pattern
    val selectedMapId: String get() = engine.map.id

    init {
        orientation = VERTICAL
        setPadding(dp(10), dp(3), dp(10), dp(3))
        isMotionEventSplittingEnabled = false
        mapBar.gravity = Gravity.CENTER
        addView(android.widget.HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; isFillViewport = true; addView(mapBar) }, LayoutParams(-1, dp(34)))
        preview.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(preview, LayoutParams(-1, dp(26)))
        pads.orientation = HORIZONTAL
        pads.gravity = Gravity.CENTER
        padContainer.gravity = Gravity.CENTER
        padContainer.addView(pads, LayoutParams(-1, -1))
        addView(padContainer, LayoutParams(-1, 0, 1f))
        rebuildControls()
        dotPad.setOnClickListener { if (inputAllowed()) { onInteraction(); engine.addSignal('.') } }
        dashPad.setOnClickListener { if (inputAllowed()) { onInteraction(); engine.addSignal('-') } }
        dotPad.setOnLongClickListener {
            if (inputAllowed() && settings.style == MorseInputStyle.STRAIGHT) { onInteraction(); engine.addSignal('-'); true } else false
        }
        rebuildPads()
        rebuildMaps()
        updatePreview()
        applyAppearance()
        ready = true
    }

    private fun pad(glyph: MorseGlyphView.Glyph, description: Int) = MorseGlyphView(context, glyph).apply {
        contentDescription = localString(description)
    }

    fun configure(configuration: MorseSettings, availableMaps: List<MorseMap>, selectedId: String) {
        dashPad.contentDescription = localString(R.string.morse_dash)
        dotPad.contentDescription = localString(if (configuration.style == MorseInputStyle.STRAIGHT) R.string.morse_straight else R.string.morse_dot)
        hasConfiguration = true
        val nextMaps = availableMaps.ifEmpty { MorsePresets.maps(resources.configuration.locales[0].language == "ja") }
        if (settings == configuration && maps == nextMaps && engine.map.id == selectedId) { updatePreview(); return }
        cancelInput()
        settings = configuration; maps = nextMaps; engine.settings = configuration
        engine.map = maps.firstOrNull { it.id == selectedId } ?: maps.first()
        rebuildPads(); rebuildControls(); rebuildMaps(); updatePreview(); applyAppearance()
    }

    fun selectMap(id: String) {
        val map = maps.firstOrNull { it.id == id } ?: return
        cancelInput()
        engine.map = map
        onMapSelected(id)
        rebuildMaps(); updatePreview(); applyAppearance()
    }
    fun nextMap() = selectMap(maps[(maps.indexOfFirst { it.id == engine.map.id } + 1).mod(maps.size)].id)

    private fun rebuildControls() {
        controls.clear()
        controlContainer.removeAllViews()
        (controlContainer.parent as? android.view.ViewGroup)?.removeView(controlContainer)
        addView(controlContainer, if (settings.controlLayout.abovePads) 2 else childCount, LayoutParams(-1, -2))
        settings.controlLayout.rows.forEach { keys ->
            val row = LinearLayout(context)
            controlContainer.addView(row, LayoutParams(-1, dp(48)))
            keys.forEach { control ->
                val glyph = MorseGlyphView.Glyph.valueOf(control.name)
                val button = MorseGlyphView(context, glyph).apply {
                    contentDescription = localString(control.labelResource)
                    setOnClickListener {
                        onControlSelected?.let { it(control); return@setOnClickListener }
                        if (!inputAllowed()) return@setOnClickListener
                        onInteraction()
                        when (control) {
                            MorseControl.BOUNDARY -> if (engine.pattern.isNotEmpty() && !engine.boundary()) announceForAccessibility(localString(R.string.morse_unknown))
                            MorseControl.DELETE -> if (!engine.deleteSignal()) onCommand(MorseCommand.DELETE)
                            else -> control.command?.let(onCommand)
                        }
                    }
                    if (control == MorseControl.CONVERT) setOnLongClickListener {
                        if (onControlSelected == null && inputAllowed()) { onInteraction(); onCommand(MorseCommand.SPACE) }
                        true
                    }
                }
                controls += button
                row.addView(button, LayoutParams(0, -1, 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
            }
        }
    }

    private fun rebuildMaps() {
        mapBar.removeAllViews()
        maps.forEach { map ->
            val glyph = when(map.id) {
                MorsePresets.WABUN -> MorseGlyphView.Glyph.WABUN
                MorsePresets.ROMAJI -> MorseGlyphView.Glyph.ROMAJI
                MorsePresets.ENGLISH -> MorseGlyphView.Glyph.GLOBE
                else -> MorseGlyphView.Glyph.CUSTOM
            }
            mapBar.addView(MorseGlyphView(context, glyph).apply {
                contentDescription = map.name
                isSelected = map.id == engine.map.id
                setOnClickListener { if (inputAllowed()) { onInteraction(); selectMap(map.id) } }
            }, LayoutParams(dp(52), dp(32)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun rebuildPads() {
        pads.removeAllViews()
        dotPad.glyph = if (settings.style == MorseInputStyle.STRAIGHT) MorseGlyphView.Glyph.STRAIGHT else MorseGlyphView.Glyph.DOT
        dashPad.contentDescription = localString(R.string.morse_dash)
        dotPad.contentDescription = localString(if (settings.style == MorseInputStyle.STRAIGHT) R.string.morse_straight else R.string.morse_dot)
        val widthPercent = if (settings.oneHand) settings.padWidthPercent * 0.8 else settings.padWidthPercent.toDouble()
        // Weighted side margins keep the pads within the measured normal/floating/split body.
        padContainer.removeAllViews()
        val fraction = widthPercent.coerceIn(40.0, 100.0).toFloat() / 100f
        padContainer.addView(View(context), LayoutParams(0, 1, (1f - fraction) / 2f))
        padContainer.addView(pads, LayoutParams(0, -1, fraction))
        padContainer.addView(View(context), LayoutParams(0, 1, (1f - fraction) / 2f))
        val ordered = if (settings.style == MorseInputStyle.STRAIGHT) listOf(dotPad)
            else if (settings.reverse) listOf(dashPad, dotPad) else listOf(dotPad, dashPad)
        ordered.forEach { pad -> pads.addView(pad, LayoutParams(0, -1, 1f).apply {
            setMargins(dp(settings.gapDp.coerceIn(0, 48) / 2), dp(4), dp(settings.gapDp.coerceIn(0, 48) / 2), dp(4))
        }) }
        pads.weightSum = ordered.size.toFloat()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val height = ((pads.measuredHeight - dp(8)).coerceAtLeast(1) * settings.padHeightPercent.coerceIn(40, 100) / 100).coerceAtLeast(1)
        var changed = false
        for (i in 0 until pads.childCount) {
            val pad = pads.getChildAt(i)
            val params = pad.layoutParams as LayoutParams
            if (params.height != height) {
                params.height = height
                params.gravity = Gravity.CENTER_VERTICAL
                pad.layoutParams = params
                changed = true
            }
        }
        if (changed) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (protectNavigationBar) {
            val inset = androidx.core.view.ViewCompat.getRootWindowInsets(this)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            getLocationInWindow(windowLocation)
            val overlap = (windowLocation[1] + height - (rootView.height - inset)).coerceIn(0, inset)
            val safeBottom = dp(3) + overlap
            if (paddingBottom != safeBottom) setPadding(paddingLeft, paddingTop, paddingRight, safeBottom)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inputAllowed()) return true
                val dot = touchedPad(event.x, event.y) ?: return super.dispatchTouchEvent(event)
                onInteraction()
                pointerPads[event.getPointerId(0)] = dot
                parent?.requestDisallowInterceptTouchEvent(true)
                updateHeld()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (pointerPads.isNotEmpty()) {
                val index = event.actionIndex
                touchedPad(event.getX(index), event.getY(index))?.let { pointerPads[event.getPointerId(index)] = it }
                updateHeld(); return true
            }
            MotionEvent.ACTION_MOVE -> if (pointerPads.isNotEmpty()) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (pointerPads.isNotEmpty()) {
                pointerPads.remove(event.getPointerId(event.actionIndex))
                updateHeld(); return true
            }
            MotionEvent.ACTION_CANCEL -> if (pointerPads.isNotEmpty()) { cancelInput(); return true }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun touchedPad(x: Float, y: Float): Boolean? {
        for (i in 0 until pads.childCount) {
            val pad = pads.getChildAt(i)
            val bounds = android.graphics.Rect()
            pad.getDrawingRect(bounds); offsetDescendantRectToMyCoords(pad, bounds)
            if (bounds.contains(x.toInt(), y.toInt())) return pad === dotPad
        }
        return null
    }
    private fun updateHeld() {
        dotPad.isPressed = pointerPads.values.any { it }
        dashPad.isPressed = pointerPads.values.any { !it }
        engine.pressed(dotPad.isPressed, dashPad.isPressed)
    }
    fun cancelInput(clearPattern: Boolean = false) {
        pointerPads.clear(); dotPad.isPressed = false; dashPad.isPressed = false
        engine.cancel(clearPattern); tone.release()
    }
    fun restoreSignal(signal: String) = engine.restorePattern(signal)
    fun appendSignal(signal: Char) = engine.addSignal(signal)
    fun finishSignal(): Boolean = engine.boundary()
    fun deleteSignal(): Boolean = engine.deleteSignal()

    private fun label(output: MorseOutput?): String = when (output) {
        is MorseOutput.Reading -> output.text
        is MorseOutput.Romaji -> output.text
        is MorseOutput.LiteralText -> output.text
        is MorseOutput.Command -> localString(when (output.action) {
            MorseCommand.DAKUTEN -> R.string.morse_dakuten
            MorseCommand.HANDAKUTEN -> R.string.morse_handakuten
            else -> R.string.morse_action
        })
        null -> localString(R.string.morse_unknown)
    }
    private fun updatePreview() {
        val signal = engine.pattern
        preview.signal = signal
        preview.ink = if (signal.isNotEmpty() && engine.map.decode(signal) == null && engine.map.continuations(signal).isEmpty())
            0xffe07682.toInt() else textColor
        preview.contentDescription = if (signal.isEmpty()) localString(R.string.morse_ready)
            else "$signal → ${label(engine.map.decode(signal))}. " + engine.map.continuations(signal).take(4).joinToString { "${it.signal} ${label(it.output)}" }
        preview.invalidate()
    }

    fun setAppearance(skin: KeyboardSkinId, background: Int, key: Int, text: Int, special: Int, custom: Boolean = false) {
        customColors = custom
        skinId = skin; backgroundColorValue = background; keyColor = key; textColor = text; specialColor = special
        applyAppearance()
    }
    private fun applyAppearance() {
        val skin = KeyboardSkinRegistry.find(skinId)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val foreground = skin?.palette?.text ?: if (dark && textColor == Color.BLACK) Color.WHITE else textColor
        background = skin?.keyboardDrawable(resources) ?: GradientDrawable().apply { setColor(backgroundColorValue) }
        val accent = if (androidx.core.graphics.ColorUtils.calculateLuminance(backgroundColorValue) < .3) 0xffa7b7ff.toInt() else 0xff5267da.toInt()
        val buttons = controls + (0 until mapBar.childCount).map { mapBar.getChildAt(it) as MorseGlyphView }
        (listOf(dotPad, dashPad) + buttons).forEach { key ->
            val isPad = key === dotPad || key === dashPad
            val selected = key.isSelected
            val keyAccent = if (key === dashPad) { if (dark) 0xff65d9ce.toInt() else 0xff168a83.toInt() } else accent
            key.background = skin?.keyDrawable(resources, role = if (isPad) SkinKeyRole.CHARACTER else SkinKeyRole.MODIFIER)
                ?: android.graphics.drawable.StateListDrawable().apply {
                    fun shape(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
                        cornerRadius = dp(radius).toFloat(); setColor(color); setStroke(dp(1).coerceAtLeast(1), stroke)
                    }
                    val fill = if(isPad) keyColor else if(selected) androidx.core.graphics.ColorUtils.blendARGB(specialColor,accent,.15f) else specialColor
                    val stroke = androidx.core.graphics.ColorUtils.blendARGB(fill, foreground, if(isPad) .12f else .04f)
                    addState(intArrayOf(android.R.attr.state_pressed), shape(androidx.core.graphics.ColorUtils.blendARGB(fill,keyAccent,.22f),keyAccent,if(isPad) 24 else 14))
                    addState(intArrayOf(), shape(fill,if(selected) androidx.core.graphics.ColorUtils.blendARGB(fill,accent,.4f) else stroke,if(isPad) 24 else 14))
                }
            key.ink = if(isPad && skin == null && !customColors) keyAccent else if(selected && !customColors) accent
                else if(isPad) foreground else skin?.palette?.specialText ?: foreground
            key.elevation = if(isPad) dp(2).toFloat() else 0f
        }
        updatePreview()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE && ready) cancelInput()
    }
    override fun onDetachedFromWindow() { cancelInput(); super.onDetachedFromWindow() }
    private fun localString(id: Int) = localizedMorseContext(context).getString(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
