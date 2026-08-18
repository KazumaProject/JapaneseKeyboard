package com.kazumaproject.core.data.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

/** Compact preview that renders through the same catalog and drawables as the real IME. */
class KeyboardSkinPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), Choreographer.FrameCallback {
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val keyRect = RectF()
    private val popupRect = RectF()
    private var skinRef: KeyboardSkinRef = KeyboardSkinRef.DEFAULT
    private var motionMode = KeyboardSkinMotionMode.OFF
    private var runtimeGeneration = KeyboardSkinRuntime.generation()
    private var deck = renderer().createSurfaceDrawable(context, KeyboardSurfaceRole.DECK)
    private var candidateStrip = renderer().createSurfaceDrawable(context, KeyboardSurfaceRole.CANDIDATE_STRIP)
    private var characterKey = renderer().createKeyDrawable(context, KeyboardElementRole.CHARACTER, 1)
    private var pressedCharacterKey = renderer().createKeyDrawable(context, KeyboardElementRole.CHARACTER, 2).apply {
        state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
    }
    private var modifierKey = renderer().createKeyDrawable(context, KeyboardElementRole.MODIFIER, 3)
    private var actionKey = renderer().createKeyDrawable(context, KeyboardElementRole.ACTION, 4)
    private var spaceKey = renderer().createKeyDrawable(context, KeyboardElementRole.SPACE, 5)
    private var popupKey = renderer().createKeyDrawable(context, KeyboardElementRole.POPUP, 6)
    private var frameScheduled = false
    private var startNanos = 0L
    private var lastDrawNanos = 0L

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        updateTypography()
    }

    fun setSkin(
        skin: KeyboardSkinId,
        motion: KeyboardSkinMotionMode = motionMode,
    ) = setSkin(KeyboardSkinRef.BuiltIn(skin), motion)

    fun setSkin(
        skin: KeyboardSkinRef,
        motion: KeyboardSkinMotionMode = motionMode,
    ) {
        val nextRuntimeGeneration = runtimeGenerationFor(skin)
        if (
            skin == skinRef &&
            motion == motionMode &&
            nextRuntimeGeneration == runtimeGeneration
        ) return
        stopFrames()
        skinRef = skin
        motionMode = motion
        runtimeGeneration = nextRuntimeGeneration
        val renderer = renderer()
        deck = renderer.createSurfaceDrawable(context, KeyboardSurfaceRole.DECK)
        candidateStrip = renderer.createSurfaceDrawable(context, KeyboardSurfaceRole.CANDIDATE_STRIP)
        characterKey = renderer.createKeyDrawable(context, KeyboardElementRole.CHARACTER, 1)
        pressedCharacterKey = renderer.createKeyDrawable(context, KeyboardElementRole.CHARACTER, 2).apply {
            state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
        }
        modifierKey = renderer.createKeyDrawable(context, KeyboardElementRole.MODIFIER, 3)
        actionKey = renderer.createKeyDrawable(context, KeyboardElementRole.ACTION, 4)
        spaceKey = renderer.createKeyDrawable(context, KeyboardElementRole.SPACE, 5)
        popupKey = renderer.createKeyDrawable(context, KeyboardElementRole.POPUP, 6)
        updateTypography()
        invalidate()
        updateFrames()
    }

    private fun runtimeGenerationFor(skin: KeyboardSkinRef): Long =
        if (skin is KeyboardSkinRef.Imported) KeyboardSkinRuntime.generation() else 0L

    fun setMotionMode(mode: KeyboardSkinMotionMode) {
        setSkin(skinRef, mode)
    }

    fun currentSkin(): KeyboardSkinId = (skinRef as? KeyboardSkinRef.BuiltIn)?.id
        ?: KeyboardSkinId.DEFAULT

    fun currentSkinRef(): KeyboardSkinRef = skinRef

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        deck.setBounds(0, 0, width, height)
        deck.draw(canvas)

        val outer = dp(5f)
        val candidateHeight = height * 0.19f
        candidateStrip.setBounds(
            outer.toInt(),
            outer.toInt(),
            (width - outer).toInt(),
            (outer + candidateHeight).toInt(),
        )
        candidateStrip.draw(canvas)
        drawCandidateText(canvas, candidateHeight, outer)

        val top = outer + candidateHeight + dp(3f)
        val bottom = height - outer
        val availableHeight = bottom - top
        val rowGap = dp(1.5f)
        val rowHeight = (availableHeight - rowGap * 3f) / 4f
        drawFiveKeyRow(canvas, 0, top, rowHeight, ROW_ONE)
        drawFiveKeyRow(canvas, 1, top + rowHeight + rowGap, rowHeight, ROW_TWO)
        drawFunctionRow(canvas, top + (rowHeight + rowGap) * 2f, rowHeight)
        drawBottomRow(canvas, top + (rowHeight + rowGap) * 3f, rowHeight)

        if (showsPressedKeyPopup(skinRef)) {
            drawPressedKeyPopup(canvas, top, rowHeight)
        }
    }

    private fun drawCandidateText(canvas: Canvas, candidateHeight: Float, outer: Float) {
        val spec = KeyboardSkinCatalog.specFor(skinRef)
        textPaint.color = spec.palette.candidateTextColor
        textPaint.textSize = (candidateHeight * 0.34f).coerceAtLeast(dp(7f))
        val baseline = outer + candidateHeight * 0.64f - (textPaint.descent() + textPaint.ascent()) * 0.08f
        canvas.drawText("こんにちは", width * 0.2f, baseline, textPaint)
        canvas.drawText("今日は", width * 0.5f, baseline, textPaint)
        canvas.drawText("ありがとう", width * 0.8f, baseline, textPaint)
    }

    private fun drawFiveKeyRow(
        canvas: Canvas,
        row: Int,
        top: Float,
        height: Float,
        labels: Array<String>,
    ) {
        val gap = dp(1.2f)
        val outer = dp(4f)
        val keyWidth = (width - outer * 2f - gap * 4f) / 5f
        for (column in 0 until 5) {
            val left = outer + column * (keyWidth + gap)
            keyRect.set(left, top, left + keyWidth, top + height)
            val isPressed = row == 0 && column == 3
            drawKey(
                canvas,
                if (isPressed) pressedCharacterKey else characterKey,
                KeyboardElementRole.CHARACTER,
                labels[column],
                keyRect,
                pressed = isPressed,
            )
        }
    }

    private fun drawFunctionRow(canvas: Canvas, top: Float, height: Float) {
        val labels = FUNCTION_LABELS
        val gap = dp(1.2f)
        val outer = dp(4f)
        val keyWidth = (width - outer * 2f - gap * (labels.size - 1)) / labels.size
        for (column in labels.indices) {
            val left = outer + column * (keyWidth + gap)
            keyRect.set(left, top, left + keyWidth, top + height)
            val role = if (column == 3) KeyboardElementRole.SPACE else KeyboardElementRole.MODIFIER
            drawKey(canvas, if (role == KeyboardElementRole.SPACE) spaceKey else modifierKey, role, labels[column], keyRect)
        }
    }

    private fun drawBottomRow(canvas: Canvas, top: Float, height: Float) {
        val outer = dp(4f)
        val gap = dp(1.2f)
        val units = BOTTOM_UNITS
        val totalUnits = units.sum()
        val unitWidth = (width - outer * 2f - gap * 4f) / totalUnits
        var left = outer
        for (index in units.indices) {
            val keyWidth = unitWidth * units[index]
            keyRect.set(left, top, left + keyWidth, top + height)
            val role = when (index) {
                2 -> KeyboardElementRole.SPACE
                4 -> KeyboardElementRole.ACTION
                else -> KeyboardElementRole.MODIFIER
            }
            val drawable = when (role) {
                KeyboardElementRole.SPACE -> spaceKey
                KeyboardElementRole.ACTION -> actionKey
                else -> modifierKey
            }
            drawKey(canvas, drawable, role, BOTTOM_LABELS[index], keyRect)
            left += keyWidth + gap
        }
    }

    private fun drawKey(
        canvas: Canvas,
        drawable: android.graphics.drawable.Drawable,
        role: KeyboardElementRole,
        label: String,
        bounds: RectF,
        pressed: Boolean = false,
    ) {
        drawable.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        drawable.draw(canvas)
        val spec = KeyboardSkinCatalog.specFor(skinRef)
        textPaint.color = if (pressed && skinRef.isBuiltIn(KeyboardSkinId.FLAT)) {
            Color.WHITE
        } else if (pressed && skinRef.isBuiltIn(KeyboardSkinId.TERMINAL)) {
            spec.palette.backgroundColor
        } else if (pressed && skinRef.isBuiltIn(KeyboardSkinId.MONOCHROME_LCD)) {
            spec.palette.normalKeyColor
        } else {
            spec.palette.textColor(role)
        }
        textPaint.textSize = (bounds.height() * 0.33f).coerceAtLeast(dp(6f))
        val baseline = bounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
    }

    private fun drawPressedKeyPopup(canvas: Canvas, keyTop: Float, keyHeight: Float) {
        val gap = dp(1.2f)
        val outer = dp(4f)
        val keyWidth = (width - outer * 2f - gap * 4f) / 5f
        val left = outer + 3f * (keyWidth + gap) - keyWidth * 0.15f
        popupRect.set(
            left,
            keyTop - keyHeight * 0.82f,
            left + keyWidth * 1.3f,
            keyTop + keyHeight * 0.18f,
        )
        popupKey.setBounds(
            popupRect.left.toInt(),
            popupRect.top.toInt(),
            popupRect.right.toInt(),
            popupRect.bottom.toInt(),
        )
        popupKey.draw(canvas)
        textPaint.color = KeyboardSkinCatalog.specFor(skinRef).palette.specialKeyTextColor
        textPaint.textSize = keyHeight * 0.48f
        val baseline = popupRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f - dp(1f)
        canvas.drawText("た", popupRect.centerX(), baseline, textPaint)
    }

    private fun updateTypography() {
        val typography = KeyboardSkinCatalog.specFor(skinRef).typography
        textPaint.typeface = Typeface.create(
            typography.familyName,
            if (typography.bold) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    private fun renderer(): KeyboardSkinRenderer = KeyboardSkinRendererRegistry.rendererFor(skinRef)

    private fun showsPressedKeyPopup(skin: KeyboardSkinRef): Boolean =
        skin is KeyboardSkinRef.Imported || skin.isBuiltIn(KeyboardSkinId.CUPERTINO) ||
            skin.isBuiltIn(KeyboardSkinId.CUPERTINO_DARK) ||
            skin.isBuiltIn(KeyboardSkinId.SUMI_HANSHI) ||
            skin.isBuiltIn(KeyboardSkinId.LETTERPRESS) ||
            skin.isBuiltIn(KeyboardSkinId.PORCELAIN) ||
            skin.isBuiltIn(KeyboardSkinId.URUSHI) ||
            skin.isBuiltIn(KeyboardSkinId.CHALKBOARD) ||
            skin.isBuiltIn(KeyboardSkinId.LINEN) ||
            skin.isBuiltIn(KeyboardSkinId.MONOCHROME_LCD)

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!shouldAnimate()) return
        if (startNanos == 0L) startNanos = frameTimeNanos
        if (frameTimeNanos - lastDrawNanos >= FRAME_INTERVAL_NANOS) {
            val period = KeyboardSkinCatalog.specFor(skinRef).motion.continuousPeriodMs * 1_000_000L
            val phase = ((frameTimeNanos - startNanos) % period).toFloat() / period.toFloat()
            (deck as? PhasedKeyboardSkinDrawable)?.setPhase(phase)
            invalidate()
            lastDrawNanos = frameTimeNanos
        }
        scheduleFrame()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateFrames()
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateFrames()
    }

    private fun updateFrames() {
        if (shouldAnimate()) scheduleFrame() else stopFrames()
    }

    private fun shouldAnimate(): Boolean {
        if (!isAttachedToWindow || !isShown || motionMode != KeyboardSkinMotionMode.FULL) return false
        if (KeyboardSkinCatalog.specFor(skinRef).motion.continuousPeriodMs <= 0L) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopFrames() {
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this)
        frameScheduled = false
        startNanos = 0L
        lastDrawNanos = 0L
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val FRAME_INTERVAL_NANOS = 33_333_333L
        private val ROW_ONE = arrayOf("あ", "か", "さ", "た", "な")
        private val ROW_TWO = arrayOf("は", "ま", "や", "ら", "⌫")
        private val FUNCTION_LABELS = arrayOf("あa1", "^^", "、。?!", "日本語", "◀", "▶")
        private val BOTTOM_LABELS = arrayOf("◎", "♩", "", "変換", "↵")
        private val BOTTOM_UNITS = floatArrayOf(1f, 1f, 2.3f, 1f, 1.15f)
    }
}
