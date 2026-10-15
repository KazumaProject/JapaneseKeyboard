package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import com.kazumaproject.core.ui.skin.FeverSpectrum
import kotlin.math.*

/** Procedural effects: bounded particles, no video assets, no input or persistent editor data. */
internal class FeverEffectView(context: Context, private val clock: () -> Long = SystemClock::uptimeMillis) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val stickers = FeverStickerCache(96 * resources.displayMetrics.density)
    private val arena = RectF()
    private val protectedKeys = RectF()
    private val stage = RectF()
    private var keyInteriors = emptyList<RectF>()
    private var controls = emptyList<RectF>()
    private val candidateArea = RectF()
    private val keyArea = RectF()
    private var rawTouchX = 0f
    private var rawTouchY = 0f
    private val particles = ArrayList<Particle>(180)
    private val ornaments = ArrayList<Ornament>(48)
    private val touchMarks = ArrayList<TouchMark>(24)
    private var lastMove = 0L
    private var family = 0
    private var motion = FeverMotion.BURST
    private var gestureDirection = PointF(1f, 0f)
    private var awardColor = Color.CYAN
    private var combo = 0
    private var award: FeverAward? = null
    private var awardStart = 0L
    private var awardCombo = 0
    private var stageStart = 0L
    private var calloutStart = 0L
    private var sparkStart = 0L
    private var candidateStart = 0L
    private var candidateAccent = Color.CYAN
    private var candidateEmphasis = false
    private var callout = ""
    private var cue: FeverCue? = null
    private var calloutColor = Color.WHITE
    private var originX = 0f
    private var originY = 0f
    var intensity = 2
    private val dp get() = resources.displayMetrics.density
    private val titleTypeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
    private val awardMatrix = Matrix()
    private val awardFrame = RectF()
    private var frameShader: SweepGradient? = null
    private var frameAward: FeverAward? = null
    private var titleShader: LinearGradient? = null
    private var titleX = Float.NaN
    private var titleY = Float.NaN
    private var titleSize = Float.NaN
    private var titleTint = 0
    private var titleRainbow = false
    private val gold = 0xffffc83d.toInt()
    private val red = 0xffff1749.toInt()
    private val colors = FeverSpectrum.colors
    private val stages = arrayOf("START!", "GOOD!", "NICE!", "MORE!", "RUSH!", "CHANCE!", "LUCKY 7", "BOOST!", "REACH!")
    private data class Particle(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int, val size: Float, val end: Long, val born: Long, val keyboard: Boolean = false, val motion: FeverMotion = FeverMotion.BURST)

    private data class Ornament(val x: Float, val y: Float, val angle: Float, val speed: Float, val size: Float, val family: Int, val color: Int, val born: Long, val life: Long, val keyboard: Boolean = false, val motion: FeverMotion = FeverMotion.BURST)

    private data class TouchMark(val x: Float, val y: Float, val action: Int, val born: Long, val color: Int)

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setArena(rect: RectF) { if (arena != rect) {
        arena.set(rect); awardFrame.set(rect); awardFrame.inset(2 * dp, 2 * dp)
        frameShader = null; updateStage(); invalidate()
    } }
    fun protectKeys(rect: RectF?) {
        if (rect == null) protectedKeys.setEmpty() else protectedKeys.set(rect)
        updateStage(); invalidate()
    }
    fun setKeyEdges(rects: List<RectF>, candidates: RectF? = null) {
        keyInteriors = rects.map { RectF(it).apply { inset(3 * dp, 3 * dp) } }
        keyArea.setEmpty(); rects.forEach(keyArea::union)
        if (candidates == null) candidateArea.setEmpty() else candidateArea.set(candidates)
        invalidate()
    }
    fun protectControls(rects: List<RectF>) { controls = rects; updateStage(); invalidate() }
    private fun updateStage() {
        stage.set(arena)
        if (!protectedKeys.isEmpty && RectF.intersects(arena, protectedKeys)) {
            val spaces = listOf(
                RectF(arena.left, arena.top, arena.right, protectedKeys.top.coerceIn(arena.top, arena.bottom)),
                RectF(arena.left, protectedKeys.bottom.coerceIn(arena.top, arena.bottom), arena.right, arena.bottom),
                RectF(arena.left, arena.top, protectedKeys.left.coerceIn(arena.left, arena.right), arena.bottom),
                RectF(protectedKeys.right.coerceIn(arena.left, arena.right), arena.top, arena.right, arena.bottom)
            )
            stage.set(spaces.maxBy { it.width() * it.height() })
        }
        val center = stage.centerX()
        controls.filter { RectF.intersects(it, stage) }.forEach { control ->
            if (control.centerX() < center) stage.left = maxOf(stage.left, control.right + 3 * dp)
            else stage.right = minOf(stage.right, control.left - 3 * dp)
        }
    }
    fun setCombo(value: Int) { combo = value; invalidate() }
    override fun onDetachedFromWindow() {
        stickers.clear()
        super.onDetachedFromWindow()
    }
    fun clear() { stickers.clear(); combo = 0; award = null; callout = ""; calloutStart = 0; sparkStart = 0; stageStart = 0; candidateStart = 0; particles.clear(); ornaments.clear(); touchMarks.clear(); invalidate() }

    fun touchPhase(action: Int, x: Float, y: Float, accent: Int) {
        val now = clock()
        if (action == MotionEvent.ACTION_CANCEL) { touchMarks.clear(); invalidate(); return }
        if (!keyArea.contains(x, y) && !candidateArea.contains(x, y)) return
        if (action == MotionEvent.ACTION_MOVE && now - lastMove < 16) return
        if (action == MotionEvent.ACTION_DOWN) touchMarks.clear()
        if (action == MotionEvent.ACTION_MOVE) lastMove = now
        if (touchMarks.size >= 24) touchMarks.removeAt(0)
        touchMarks.add(TouchMark(x, y, action, now, accent))
        rawTouchX = x; rawTouchY = y; sparkStart = now; calloutColor = accent
        // The impact opens in 40 ms, then sheds tiny shards into the finger's wake.
        if (keyArea.contains(x, y)) emit(x, y, now, true,
            if (action == MotionEvent.ACTION_MOVE) 2 else 7 + intensity * 3, false, 0)
        if (candidateArea.contains(x, y)) { candidateStart = now; candidateAccent = accent; candidateEmphasis = true }
        invalidate()
    }

    private fun emit(x: Float, y: Float, time: Long, keyboard: Boolean, count: Int, big: Boolean, delay: Long) {
        val random = kotlin.random.Random(time + particles.size)
        val area = if (keyboard) keyArea else stage
        repeat(count) { index ->
            if (particles.size >= 180) particles.removeAt(0)
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val speed = (if (big) 180 + random.nextFloat() * 420 else 90 + random.nextFloat() * 230) * dp
            var px = x; var py = y
            var vx = cos(angle) * speed; var vy = sin(angle) * speed
            when (motion) {
                FeverMotion.HEARTS, FeverMotion.LOVE -> { vx *= .3f; vy = -speed * .65f }
                FeverMotion.ROCKETS, FeverMotion.FEVER -> { vx *= .22f; vy = -speed; py = area.bottom }
                FeverMotion.CASCADE, FeverMotion.JACKPOT -> {
                    px = area.left + random.nextFloat() * area.width(); py = area.top
                    vx *= .15f; vy = speed * .3f
                }
                FeverMotion.CURSOR -> { vx = gestureDirection.x * speed; vy = gestureDirection.y * speed }
                FeverMotion.SCAN, FeverMotion.REVEAL -> { px = area.left; py += (random.nextFloat() - .5f) * 20 * dp; vx = speed; vy *= .1f }
                else -> Unit
            }
            val born = time + delay + if (big) index % 4 * 85 else if (motion == FeverMotion.CASCADE) index % 4 * 45 else 0
            val shade = when (motion) {
                FeverMotion.HEARTS, FeverMotion.LOVE -> if (index % 2 == 0) 0xffff4d97.toInt() else 0xffffadd9.toInt()
                FeverMotion.ROCKETS, FeverMotion.FEVER -> if (index % 2 == 0) 0xff699bff.toInt() else 0xff74f5ff.toInt()
                FeverMotion.CASCADE -> if (index % 2 == 0) 0xffbe80ff.toInt() else 0xfff1b4ff.toInt()
                FeverMotion.IMPLODE -> 0xffff4965.toInt()
                FeverMotion.FOCUS -> 0xffffa237.toInt()
                FeverMotion.JACKPOT -> if (index % 3 == 0) Color.WHITE else gold
                else -> colors[(index + combo) % 7]
            }
            particles.add(Particle(px, py, vx, vy, shade,
                (if (big) 1.5f + random.nextFloat() * 3 else .8f + random.nextFloat() * 1.7f) * dp,
                born + if (big) 1200 else 600, born, keyboard, motion))
        }
    }

    fun show(event: FeverEvent, touchX: Float? = null, touchY: Float? = null) {
        val time = clock()
        combo = event.combo; cue = event.cue; family = FeverVisualStyle.family(event)
        motion = FeverVisualStyle.motion(event)
        gestureDirection = when (cue) {
            FeverCue.CURSOR_LEFT -> PointF(-1f, 0f)
            FeverCue.CURSOR_UP -> PointF(0f, -1f)
            FeverCue.CURSOR_DOWN -> PointF(0f, 1f)
            else -> PointF(1f, 0f)
        }
        calloutColor = FeverVisualStyle.accent(event); sparkStart = time; stageStart = time
        if (event.award == null) {
            candidateStart = time; candidateAccent = calloutColor
            candidateEmphasis = event.cue in listOf(FeverCue.CANDIDATE, FeverCue.CANDIDATES, FeverCue.CONVERSION)
        }
        if (arena.isEmpty) setArena(RectF(0f, 0f, width.toFloat(), height.toFloat()))
        rawTouchX = (touchX ?: keyArea.centerX()).coerceIn(arena.left, arena.right)
        rawTouchY = (touchY ?: keyArea.centerY()).coerceIn(arena.top, arena.bottom)
        originX = rawTouchX; originY = rawTouchY
        if (protectedKeys.contains(originX, originY)) {
            originX = originX.coerceIn(stage.left, maxOf(stage.left, stage.right)); originY = stage.centerY()
        }
        if (event.cue != null || (event.award == null && event.combo > 0)) {
            calloutStart = time; callout = event.cue?.label ?: "${event.combo}  ${stages[(event.combo - 1) % stages.size]}"
        }
        if (event.award != null) {
            award = event.award; awardStart = time; awardCombo = combo; calloutStart = 0
            awardColor = calloutColor
        }
        val big = event.award != null
        val random = kotlin.random.Random(time)
        if (!stage.isEmpty) emit(if (big) stage.centerX() else originX, if (big) titleY() else originY,
            time, false, if (big) intArrayOf(25, 60, 100)[intensity] else intArrayOf(8, 16, 26)[intensity], big, if (big) 85 else 0)
        if (!keyArea.isEmpty) emit(if (big) keyArea.centerX() else rawTouchX, if (big) keyArea.centerY() else rawTouchY,
            time, true, if (big) 30 else 12, big, if (big) 85 else 0)
        for (keyboard in listOf(false, true)) {
            if (if (keyboard) keyArea.isEmpty else stage.isEmpty) continue
            val space = if (keyboard) keyArea else stage
            // Editing has its own choreography. It must not inherit the character fireworks.
            if (motion in listOf(FeverMotion.IMPLODE, FeverMotion.CURSOR, FeverMotion.FOCUS, FeverMotion.SCAN, FeverMotion.REVEAL)) continue
            val count = if (big) intArrayOf(5, 12, 20)[intensity] else intArrayOf(2, 4, 7)[intensity]
            repeat(count) { index ->
                if (ornaments.size >= 48) ornaments.removeAt(0)
                // A secondary pass enters later with a different trajectory, rather than
                // sending every symbol out of the same radial burst.
                val secondary = !big && index % 3 == 2
                val style = if (secondary) arrayOf(FeverMotion.ROCKETS, FeverMotion.CASCADE,
                    FeverMotion.RIBBON, FeverMotion.HEARTS, FeverMotion.BURST)[family] else motion
                val kind = when (style) {
                    FeverMotion.HEARTS -> 1
                    FeverMotion.LOVE -> if (index % 3 == 0) 5 else 1
                    FeverMotion.ROCKETS, FeverMotion.FEVER -> 3
                    FeverMotion.CASCADE, FeverMotion.JACKPOT -> 4
                    FeverMotion.RIBBON -> 2
                    else -> 0
                }
                val angle = index * 2.39996f + combo * .72f
                val px = if (style in listOf(FeverMotion.CASCADE, FeverMotion.JACKPOT) || (big && style == FeverMotion.FEVER))
                    space.left + space.width() * (index + .5f) / count else if (big) space.centerX() else if (keyboard) rawTouchX else originX
                val py = when (style) {
                    FeverMotion.ROCKETS, FeverMotion.FEVER -> space.bottom
                    FeverMotion.CASCADE, FeverMotion.JACKPOT -> space.top
                    else -> if (big) space.centerY() else if (keyboard) rawTouchY else originY
                }
                val shade = when (style) {
                    FeverMotion.HEARTS, FeverMotion.LOVE -> if (index % 2 == 0) 0xffff4d97.toInt() else 0xffffa8d9.toInt()
                    FeverMotion.ROCKETS, FeverMotion.FEVER -> 0xff74c9ff.toInt()
                    FeverMotion.CASCADE -> 0xffc790ff.toInt()
                    FeverMotion.JACKPOT -> gold
                    else -> colors[(family + index) % 7]
                }
                ornaments.add(Ornament(px, py, angle,
                    (if (big) 220 + random.nextFloat() * 400 else 150 + random.nextFloat() * 260) * dp,
                    minOf((if (big) 9 + random.nextFloat() * 12 else 4 + random.nextFloat() * 6) * dp,
                        (space.height() * .2f).coerceAtLeast(3 * dp)), kind, shade,
                    time + if (big) 80L + index % 4 * 90 else if (secondary) 150 else index % 2 * 40L,
                    if (big) 1500 else 850, keyboard, style))
            }
        }
        invalidate()
    }

    private fun titleY() = stage.top + stage.height() * .43f
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (arena.isEmpty) return
        val now = clock()
        val elapsed = (now - awardStart).coerceAtLeast(0)
        val active = award?.takeIf { elapsed < 2000 }
        canvas.save(); canvas.clipRect(arena)
        if (!protectedKeys.isEmpty) {
            if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(protectedKeys)
            else @Suppress("DEPRECATION") canvas.clipRect(protectedKeys, Region.Op.DIFFERENCE)
        }
        controls.forEach { control ->
            if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(control)
            else @Suppress("DEPRECATION") canvas.clipRect(control, Region.Op.DIFFERENCE)
        }
        if (active != null && !stage.isEmpty) drawAward(canvas, active, elapsed) else if (active == null) award = null
        if (!stage.isEmpty) {
            drawInputLayers(canvas, now)
            drawParticles(canvas, now, false)
            drawOrnaments(canvas, now)
            if (active != null) drawAwardTitle(canvas, active, elapsed)
        } else { particles.removeAll { !it.keyboard }; ornaments.removeAll { !it.keyboard } }
        val smallActive = calloutStart > 0 && now - calloutStart < 700
        if (smallActive && active == null && !stage.isEmpty) drawCallout(canvas, now - calloutStart)
        if (combo > 0 && !stage.isEmpty && !(stage.height() < 110 * dp && (active != null || smallActive))) drawMeter(canvas)
        canvas.restore()
        drawKeyboardOrnaments(canvas, now)
        drawTouchFeedback(canvas, now)
        drawKeyEnergy(canvas, now)
        drawCandidateEnergy(canvas, now)
        paint.reset(); paint.isAntiAlias = true
        if (active != null || particles.isNotEmpty() || ornaments.isNotEmpty() || touchMarks.isNotEmpty() || smallActive || (sparkStart > 0 && now - sparkStart < 650) || (candidateStart > 0 && now - candidateStart < 720)) postInvalidateOnAnimation()
    }

    /** Bright streaks run through the gaps; cap borders are drawn exactly once by the skin. */
    private fun drawKeyEnergy(canvas: Canvas, now: Long) {
        val age = now - sparkStart
        if (keyArea.isEmpty || sparkStart == 0L || age !in 0..520) return
        val t = age / 520f
        // Other motion families paint no gap streaks, so need neither clipping nor a display list.
        if (motion != FeverMotion.CURSOR && motion != FeverMotion.IMPLODE && motion != FeverMotion.RIBBON) return
        canvas.save(); canvas.clipRect(arena); canvas.clipRect(keyArea)
        keyInteriors.forEach { inside ->
            if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(inside)
            else @Suppress("DEPRECATION") canvas.clipRect(inside, Region.Op.DIFFERENCE)
        }
        when (motion) {
            FeverMotion.CURSOR -> repeat(4) { index ->
                val dx = gestureDirection.x; val dy = gestureDirection.y
                drawComet(canvas, rawTouchX - dy * index * 5 * dp, rawTouchY + dx * index * 5 * dp,
                    rawTouchX + dx * 180 * dp, rawTouchY + dy * 180 * dp, t, 0f, 0xff6cbdff.toInt(), dp, 1 - t)
            }
            FeverMotion.IMPLODE -> repeat(6) { index ->
                val angle = index * PI.toFloat() / 3
                drawComet(canvas, rawTouchX + cos(angle) * 75 * dp, rawTouchY + sin(angle) * 75 * dp,
                    rawTouchX, rawTouchY, t, 12 * dp, 0xffff4965.toInt(), dp, 1 - t)
            }
            FeverMotion.RIBBON -> repeat(3) { index ->
                drawComet(canvas, keyArea.left, rawTouchY + (index - 1) * 18 * dp,
                    keyArea.right, rawTouchY, t, (index - 1) * 32 * dp, 0xff60ffbd.toInt(), dp, 1 - t)
            }
            else -> Unit
        }
        canvas.restore()
    }

    private fun drawComet(canvas: Canvas, sx: Float, sy: Float, tx: Float, ty: Float,
                          progress: Float, bend: Float, color: Int, width: Float, opacity: Float) {
        val t = progress.coerceIn(0f, 1f)
        val head = 1 - (1 - t).pow(3)
        val tail = (head - (.08f + .2f * (1 - t))).coerceAtLeast(0f)
        val cx = (sx + tx) / 2 - (ty - sy).let { if (abs(it) > .1f) sign(it) * bend else 0f }
        val cy = (sy + ty) / 2 + bend
        fun x(u: Float) = (1-u).pow(2) * sx + 2*(1-u)*u*cx + u*u*tx
        fun y(u: Float) = (1-u).pow(2) * sy + 2*(1-u)*u*cy + u*u*ty
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        repeat(8) { part ->
            val a = tail + (head - tail) * part / 8
            val b = tail + (head - tail) * (part + 1) / 8
            paint.color = color; paint.alpha = (opacity.coerceIn(0f, 1f) * (part + 1) / 8 * 225).toInt()
            paint.strokeWidth = width * (.25f + part / 8f)
            canvas.drawLine(x(a), y(a), x(b), y(b), paint)
        }
        paint.color = Color.WHITE; paint.alpha = (opacity.coerceIn(0f, 1f) * 245).toInt()
        paint.strokeWidth = width * .8f
        canvas.drawLine(x((head - .012f).coerceAtLeast(0f)), y((head - .012f).coerceAtLeast(0f)), x(head), y(head), paint)
    }

    private fun drawCandidateEnergy(canvas: Canvas, now: Long) {
        val age = now - candidateStart
        if (candidateArea.isEmpty || candidateStart == 0L || age !in 0..720) return
        val t = age / 720f
        val fade = (1 - t).pow(1.5f)
        canvas.save(); canvas.clipRect(arena); canvas.clipRect(candidateArea)
        val layer = canvas.saveLayerAlpha(candidateArea, if (candidateEmphasis) 112 else 80)
        val cx = candidateArea.centerX(); val cy = candidateArea.centerY()
        when (motion) {
            FeverMotion.FOCUS -> repeat(4) { index ->
                drawComet(canvas, if (index % 2 == 0) candidateArea.left else candidateArea.right,
                    candidateArea.top + candidateArea.height() * (index + .5f) / 4, cx, cy,
                    t, (index - 1) * 8 * dp, 0xffffa237.toInt(), 1.5f * dp, fade)
            }
            FeverMotion.SCAN, FeverMotion.CURSOR -> repeat(3) { index ->
                val reverse = motion == FeverMotion.CURSOR && gestureDirection.x < 0
                drawComet(canvas, if (reverse) candidateArea.right else candidateArea.left, cy + (index - 1) * 9 * dp,
                    if (reverse) candidateArea.left else candidateArea.right, cy + (index - 1) * 9 * dp,
                    t, 0f, if (motion == FeverMotion.SCAN) gold else 0xff6cbdff.toInt(), 1.5f * dp, fade)
            }
            FeverMotion.IMPLODE -> repeat(4) { index ->
                val x = candidateArea.left + candidateArea.width() * (index + .5f) / 4
                drawComet(canvas, x, candidateArea.top, cx, cy, t, 0f, 0xffff4965.toInt(), dp, fade)
            }
            else -> repeat(if (candidateEmphasis) 7 else 4) { index ->
                val local = ((age - index * 35) / 500f).coerceIn(0f, 1f)
                val lane = candidateArea.left + candidateArea.width() * (index + .5f) / (if (candidateEmphasis) 7 else 4)
                val kind = when (motion) {
                    FeverMotion.HEARTS, FeverMotion.LOVE -> 1
                    FeverMotion.ROCKETS, FeverMotion.FEVER -> 3
                    FeverMotion.CASCADE, FeverMotion.JACKPOT -> 4
                    else -> 2
                }
                val x = if (motion == FeverMotion.RIBBON) candidateArea.left + candidateArea.width() * local else lane
                val y = when (motion) {
                    FeverMotion.CASCADE, FeverMotion.JACKPOT -> candidateArea.top + candidateArea.height() * local.pow(1.6f)
                    FeverMotion.RIBBON -> cy + sin(local * 6 + index) * candidateArea.height() * .2f
                    else -> candidateArea.bottom - candidateArea.height() * local
                }
                drawMotif(canvas, x, y, (if (candidateEmphasis) 7 else 5) * dp * sin(local * PI.toFloat()),
                    kind, candidateAccent, if (kind == 3) -80f else local * 120, (fade * 255).toInt())
            }
        }
        paint.style = Paint.Style.STROKE; paint.shader = null; paint.color = candidateAccent
        paint.alpha = (fade * 220).toInt(); paint.strokeWidth = .8f * dp
        val sweep = candidateArea.left + (1 - (1 - t).pow(3)) * candidateArea.width()
        canvas.drawLine(sweep - 20 * dp, candidateArea.bottom - dp, sweep, candidateArea.bottom - dp, paint)
        canvas.restoreToCount(layer); canvas.restore()
    }

    /** Impulse, crossing comets, launch and falling confetti; no expanding-ring backdrop. */
    private fun drawAward(canvas: Canvas, value: FeverAward, elapsed: Long) {
        val t = elapsed / 2000f
        val fade = minOf(1f, elapsed / 45f, (2000 - elapsed) / 280f).coerceIn(0f, 1f)
        val compact = stage.height() < 110 * dp
        val cx = stage.centerX(); val cy = titleY()
        when (value) {
            FeverAward.COMBO -> {
                // Crossed launch followed by the separately delayed confetti and elastic caption.
                repeat(6) { index ->
                    val local = ((elapsed - index % 3 * 65) / 550f).coerceIn(0f, 1f)
                    val angle = index * 2.39996f
                    val distance = minOf(stage.width(), if (compact) stage.width() else stage.height()) * .75f
                    drawComet(canvas, cx, cy, cx + cos(angle) * distance,
                        cy + sin(angle) * distance * if (compact) .2f else 1f, local, sin(index * 2f) * 45 * dp,
                        colors[index], 1.4f * dp, fade * (1 - local))
                }
            }
            FeverAward.FEVER -> repeat(8) { index ->
                val local = ((elapsed - index * 95) / 800f).coerceIn(0f, 1f)
                val x = stage.left + stage.width() * (index + .5f) / 8
                drawComet(canvas, x, stage.bottom, x + sin(index.toFloat()) * 50 * dp, stage.top,
                    local, 12 * dp, 0xff6acbff.toInt(), 2 * dp, fade * (1 - local))
            }
            FeverAward.HOT -> repeat(4) { index ->
                val local = ((elapsed - index * 160) / 1000f).coerceIn(0f, 1f)
                drawComet(canvas, stage.left, cy + (index - 2) * 12 * dp, stage.right, cy,
                    local, (index % 2 * 2 - 1) * (if (compact) 10 else 70) * dp,
                    0xffff62a8.toInt(), 1.5f * dp, fade * sin(local * PI.toFloat()))
            }
            FeverAward.JACKPOT -> repeat(9) { index ->
                val local = ((elapsed - index % 3 * 220) / 650f).coerceIn(0f, 1f)
                val reverse = index % 2 == 0
                val y = stage.top + stage.height() * (index + .5f) / 9
                drawComet(canvas, if (reverse) stage.right else stage.left, y,
                    if (reverse) stage.left else stage.right, y, local, (index % 3 - 1) * 20 * dp,
                    if (index % 3 == 0) Color.WHITE else gold, (1.5f + index % 2) * dp, fade * (1 - local))
            }
        }
        paint.style = Paint.Style.STROKE
        if (frameShader == null || frameAward != value) {
            val frameColors = when (value) {
                FeverAward.FEVER -> intArrayOf(Color.WHITE, 0xff4985ff.toInt(), 0xff69eaff.toInt(), Color.WHITE)
                FeverAward.HOT -> intArrayOf(Color.WHITE, 0xffff4d97.toInt(), 0xffffb9e0.toInt(), Color.WHITE)
                FeverAward.JACKPOT -> intArrayOf(Color.WHITE, gold, 0xffff9e3d.toInt(), Color.WHITE)
                else -> colors
            }
            frameShader = SweepGradient(arena.centerX(), arena.centerY(), frameColors, null)
            frameAward = value
        }
        awardMatrix.setRotate(t * 300, arena.centerX(), arena.centerY())
        frameShader!!.setLocalMatrix(awardMatrix); paint.shader = frameShader
        paint.alpha = (fade * 165).toInt(); paint.strokeWidth = 1.1f * dp
        canvas.drawRoundRect(awardFrame, 12 * dp, 12 * dp, paint)
        paint.shader = null
    }

    private fun drawAwardTitle(canvas: Canvas, value: FeverAward, elapsed: Long) {
        if (elapsed < 65) return
        val enter = ((elapsed - 65) / 310f).coerceIn(0f, 1f)
        val exit = ((elapsed - 1530) / 470f).coerceIn(0f, 1f)
        val fade = minOf(enter * 5, (1 - exit).pow(2)).coerceIn(0f, 1f)
        val cx = stage.centerX(); val cy = titleY()
        val compact = stage.height() < 110 * dp
        val label = when (value) {
            FeverAward.COMBO -> "COMBO ×$awardCombo"
            FeverAward.FEVER -> "ON FIRE!"
            FeverAward.HOT -> "LOVE IT!"
            FeverAward.JACKPOT -> "MEGA HIT!"
        }
        val size = minOf(stage.width() / maxOf(3f, label.length * .64f), stage.height() * .56f, 78 * dp)
        val bounce = 1 - exp(-enter * 7) * cos(enter * 12)
        val dx = when (value) {
            FeverAward.FEVER -> -(1 - enter).pow(3) * stage.width() * .55f
            FeverAward.JACKPOT -> sin(enter * 38) * exp(-enter * 9) * 20 * dp
            else -> (1 - enter).pow(3) * stage.width() * .12f
        } + exit.pow(2) * stage.width() * .12f
        val dy = (1 - enter).pow(2) * (if (value == FeverAward.HOT) 65 else 18) * dp - exit.pow(2) * 24 * dp
        canvas.save(); canvas.translate(dx, dy)
        canvas.scale(.55f + .45f * bounce, .5f + .5f * bounce, cx, cy)
        canvas.rotate(-4 + (1 - enter) * -8 + exit * 5, cx, cy)
        metallicText(canvas, label, cx, cy, size, fade, value == FeverAward.COMBO, awardColor)
        canvas.restore()
        if (!compact) {
            paint.typeface = titleTypeface; paint.textSize = 15 * dp
            paint.style = Paint.Style.FILL; paint.color = awardColor; paint.alpha = (fade * 240).toInt(); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("$combo COMBO  /  ${if (value == FeverAward.JACKPOT) "BONUS!" else "KEEP GOING!"}", cx, cy + size * .82f - exit * 12 * dp, paint)
        }
    }

    private fun metallicText(canvas: Canvas, label: String, x: Float, y: Float, size: Float, fade: Float, rainbow: Boolean = false, tint: Int = calloutColor) {
        paint.shader = null; paint.typeface = titleTypeface
        paint.isFakeBoldText = true; paint.textAlign = Paint.Align.CENTER; paint.textSize = size
        val baseline = y - (paint.ascent() + paint.descent()) / 2
        val bevel = minOf(1f, size / (32 * dp))
        paint.alpha = (fade * 255).toInt(); paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0xff030c20.toInt(); paint.alpha = (fade * 255).toInt(); paint.strokeWidth = 5 * dp * bevel
        canvas.drawText(label, x + 1.5f * dp * bevel, baseline + 3 * dp * bevel, paint)
        canvas.drawText(label, x, baseline, paint)
        paint.style = Paint.Style.FILL
        if (titleShader == null || titleX != x || titleY != y || titleSize != size || titleTint != tint || titleRainbow != rainbow) {
            titleShader = LinearGradient(x, y - size * .5f, x, y + size * .5f,
                if (rainbow) intArrayOf(0xffffff92.toInt(), 0xffffcb63.toInt(), 0xff78ffcf.toInt(), 0xff60dfff.toInt(), 0xffc591ff.toInt())
                else intArrayOf(Color.WHITE, tint, Color.rgb(Color.red(tint) / 2, Color.green(tint) / 2, Color.blue(tint) / 2)),
                null, Shader.TileMode.CLAMP)
            titleX = x; titleY = y; titleSize = size; titleTint = tint; titleRainbow = rainbow
        }
        paint.shader = titleShader
        canvas.drawText(label, x, baseline, paint); paint.shader = null; paint.isFakeBoldText = false
    }

    private fun drawParticles(canvas: Canvas, now: Long, keyboard: Boolean) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (p.end <= now) { iterator.remove(); continue }
            if (p.born > now || p.keyboard != keyboard) continue
            val age = (now - p.born) / 1000f
            val t = (now - p.born).toFloat() / (p.end - p.born)
            val travel = (1 - exp(-age * 4.5f)) / 4.5f
            val inward = (p.motion == FeverMotion.IMPLODE || p.motion == FeverMotion.FOCUS)
            val distance = if (inward) (1 - t).pow(3) * .23f else travel
            val sway = if (p.motion == FeverMotion.RIBBON) sin(t * PI.toFloat() * 2) * 22 * dp else 0f
            val x = p.x + p.vx * distance + if ((p.motion == FeverMotion.HEARTS || p.motion == FeverMotion.LOVE)) sin(t * 7 + p.vx) * 10 * dp else sway
            val gravity = when (p.motion) {
                FeverMotion.ROCKETS, FeverMotion.FEVER, FeverMotion.CURSOR, FeverMotion.SCAN, FeverMotion.REVEAL -> 0f
                FeverMotion.HEARTS, FeverMotion.LOVE, FeverMotion.RIBBON -> 25f
                FeverMotion.IMPLODE, FeverMotion.FOCUS -> 0f
                else -> 120f
            }
            val y = p.y + p.vy * distance + gravity * dp * age.pow(2) + if (p.motion == FeverMotion.RIBBON) cos(t * 6 + p.vx) * 12 * dp else 0f
            val stretch = (1 - t).pow(2) * .045f
            paint.shader = null; paint.color = p.color; paint.alpha = ((1 - t).pow(.65f) * 245).toInt()
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = p.size * (1 - t * .7f)
            canvas.drawLine(x, y, x - p.vx * stretch, y - p.vy * stretch, paint)
            if (t > .15f && p.size > 2 * dp) {
                canvas.save(); canvas.rotate(p.vx + t * 420, x, y)
                paint.style = Paint.Style.FILL
                canvas.drawRect(x - p.size, y - p.size * .32f, x + p.size, y + p.size * .32f, paint); canvas.restore()
            }
        }
    }

    private fun drawCallout(canvas: Canvas, elapsed: Long) {
        val enter = (elapsed / 190f).coerceIn(0f, 1f)
        val exit = ((elapsed - 370) / 330f).coerceIn(0f, 1f)
        val response = 1 - exp(-enter * 6) * cos(enter * 10)
        val compact = stage.height() < 110 * dp
        val cx = stage.centerX(); val cy = if (compact) stage.centerY() else stage.top + minOf(stage.height() * .5f, 110 * dp)
        val size = minOf(stage.height() * .6f, if (compact) 21 * dp else 36 * dp, stage.width() / maxOf(3f, callout.length * .65f))
        canvas.save()
        val horizontal = (motion == FeverMotion.RIBBON || motion == FeverMotion.SCAN || motion == FeverMotion.CURSOR)
        val downward = motion == FeverMotion.CASCADE
        canvas.translate(if (horizontal) (1 - enter).pow(3) * -100 * dp + exit.pow(2) * 70 * dp else 0f,
            if (horizontal) 0f else (1 - enter).pow(2) * (if (downward) -35 else 22) * dp - exit.pow(2) * 22 * dp)
        canvas.scale(.65f + response * .35f, .6f + response * .4f, cx, cy)
        canvas.rotate((if (compact) -1f else -5f) + (1 - enter) * -8, cx, cy)
        metallicText(canvas, callout, cx, cy, size, (1 - exit).pow(2))
        canvas.restore()
    }

    private fun drawInputLayers(canvas: Canvas, now: Long) {
        val age = now - stageStart
        if (stageStart == 0L || age !in 0..620) return
        val t = age / 620f
        val compact = stage.height() < 110 * dp
        val cx = if (compact) stage.centerX() else originX
        val cy = if (compact) stage.centerY() else originY
        when (motion) {
            FeverMotion.RIBBON -> repeat(3) { index ->
                drawComet(canvas, stage.left, cy, stage.right, cy, t,
                    (index - 1) * (if (compact) 8 else 40) * dp, 0xff60ffbd.toInt(), 1.2f * dp, 1 - t)
            }
            FeverMotion.FOCUS -> repeat(4) { index ->
                val left = index % 2 == 0
                drawComet(canvas, if (left) stage.left else stage.right, stage.top + index / 4f * stage.height(),
                    cx, cy, t, (index - 1) * 10 * dp, 0xffffa237.toInt(), 1.4f * dp, 1 - t)
            }
            FeverMotion.CURSOR -> drawComet(canvas, cx - gestureDirection.x * 80 * dp,
                cy - gestureDirection.y * 20 * dp, cx + gestureDirection.x * 100 * dp,
                cy + gestureDirection.y * 20 * dp, t, 0f, 0xff6cbdff.toInt(), 2 * dp, 1 - t)
            FeverMotion.IMPLODE -> repeat(3) { index ->
                drawComet(canvas, cx + (index - 1) * 90 * dp, cy - if (compact) 0f else 50 * dp,
                    cx, cy, t, 8 * dp, 0xffff4965.toInt(), dp, 1 - t)
            }
            FeverMotion.BURST, FeverMotion.COMBO -> repeat(3 + intensity) { index ->
                val direction = if (index % 2 == 0) 1 else -1
                drawComet(canvas, cx, cy, (cx + direction * (75 + index * 26) * dp).coerceIn(stage.left, stage.right),
                    if (compact) cy else (cy - (25 + index * 19) * dp).coerceIn(stage.top, stage.bottom), t,
                    if (compact) 4 * dp else direction * 35 * dp, colors[(family + index) % 7], 1.3f * dp, 1 - t)
            }
            else -> Unit
        }
    }

    private fun drawOrnaments(canvas: Canvas, now: Long, keyboard: Boolean = false) {
        val iterator = ornaments.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next(); val age = now - item.born
            if (age >= item.life) { iterator.remove(); continue }
            if (age < 0 || item.keyboard != keyboard) continue
            val t = age.toFloat() / item.life
            val seconds = age / 1000f
            val travel = (1 - exp(-seconds * 3.2f)) / 3.2f
            val compact = !keyboard && stage.height() < 110 * dp
            val bend = sin(t * PI.toFloat()) * 28 * dp
            var x = item.x + cos(item.angle) * item.speed * travel - sin(item.angle) * bend
            var y = item.y + sin(item.angle) * item.speed * travel + cos(item.angle) * bend + 65 * dp * seconds * seconds
            var rotation = item.angle * 25 + t * 300
            when (item.motion) {
                FeverMotion.HEARTS, FeverMotion.LOVE -> {
                    x = item.x + sin(t * 5 + item.angle) * (20 + t * 30) * dp
                    y = item.y - item.speed * travel * .7f
                    rotation = sin(t * 5 + item.angle) * 22
                }
                FeverMotion.ROCKETS, FeverMotion.FEVER -> {
                    x = item.x + cos(item.angle) * 150 * dp * t
                    y = item.y - item.speed * seconds.pow(1.35f)
                    rotation = -75 + cos(item.angle) * 25
                }
                FeverMotion.CASCADE, FeverMotion.JACKPOT -> {
                    x = item.x + sin(t * 5 + item.angle) * 22 * dp
                    val fall = if (keyboard) keyArea.height() else stage.height()
                    y = item.y + fall * t.pow(1.6f)
                    rotation = item.angle * 25 + t * 520
                }
                FeverMotion.RIBBON -> {
                    x = item.x + (t * 2 - .3f) * item.speed * .35f
                    y = item.y + sin(t * 6 + item.angle) * (if (compact) stage.height() * .24f else 30 * dp)
                    rotation = cos(t * 6 + item.angle) * 35
                }
                else -> if (compact) y = stage.centerY() + sin(item.angle + t * 4) * stage.height() * .22f
            }
            val scale = minOf(1.2f, t / .075f) * (1 - t).pow(.4f)
            canvas.save(); canvas.scale(scale * (1 + .7f * exp(-t * 15)), scale, x, y)
            drawMotif(canvas, x, y, item.size, item.family, item.color, rotation, ((1 - t).pow(.7f) * 250).toInt())
            canvas.restore()
        }
    }

    private fun drawTouchFeedback(canvas: Canvas, now: Long) {
        touchMarks.removeAll { now - it.born >= 420 }
        if (touchMarks.isEmpty()) return
        canvas.save(); canvas.clipRect(arena)
        path.reset(); if (!keyArea.isEmpty) path.addRect(keyArea, Path.Direction.CW)
        if (!candidateArea.isEmpty) path.addRect(candidateArea, Path.Direction.CW)
        canvas.clipPath(path)
        val layer = canvas.saveLayerAlpha(arena, 96)
        var previous: TouchMark? = null
        touchMarks.forEach { mark ->
            val t = (now - mark.born) / 420f
            val fade = (1 - t).pow(1.4f)
            if (mark.action == MotionEvent.ACTION_MOVE) {
                previous?.let { before ->
                    paint.shader = LinearGradient(before.x, before.y, mark.x + .1f, mark.y + .1f, colors, null, Shader.TileMode.CLAMP)
                    paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.alpha = (fade * 245).toInt(); paint.strokeWidth = 2 * dp
                    path.reset(); path.moveTo(before.x, before.y); path.quadTo(before.x, mark.y, mark.x, mark.y)
                    canvas.drawPath(path, paint); paint.shader = null
                }
                drawMotif(canvas, mark.x + (1 - (1 - t).pow(3)) * 14 * dp, mark.y - t * 16 * dp,
                    (3 + intensity) * dp * (1 - t), 2, mark.color, -35f, (fade * 240).toInt())
            } else {
                val release = mark.action == MotionEvent.ACTION_UP || mark.action == MotionEvent.ACTION_POINTER_UP
                val size = (if (release) 10 else 7) * dp * (1 - t).pow(.6f)
                canvas.save(); canvas.translate(mark.x, mark.y)
                canvas.scale(1 + .45f * exp(-t * 14) * sin(t * 22), 1 - .25f * exp(-t * 12) * cos(t * 18))
                drawMotif(canvas, 0f, 0f, size, 0, mark.color, t * 90, (fade * 245).toInt())
                canvas.restore()
                repeat(if (release) 6 else 4) { index ->
                    val angle = index * 2.39996f
                    val distance = (if (release) 90 else 48) * dp
                    drawComet(canvas, mark.x, mark.y, mark.x + cos(angle) * distance,
                        mark.y + sin(angle) * distance, t, (index % 2 * 2 - 1) * 9 * dp,
                        colors[index], dp, fade)
                }
            }
            previous = mark
        }
        canvas.restoreToCount(layer); canvas.restore()
    }

    /** A single alpha-composited layer allows stickers on caps without opaque piles hiding labels. */
    private fun drawKeyboardOrnaments(canvas: Canvas, now: Long) {
        if (keyArea.isEmpty) return
        canvas.save(); canvas.clipRect(arena); canvas.clipRect(keyArea)
        val layer = canvas.saveLayerAlpha(keyArea, 96)
        drawParticles(canvas, now, true)
        drawOrnaments(canvas, now, true)
        canvas.restoreToCount(layer); canvas.restore()
    }

    /** Color emoji stickers have their own float/launch/fall trajectories, separate from sparks. */
    private fun drawMotif(canvas: Canvas, x: Float, y: Float, size: Float, kind: Int, color: Int, rotation: Float, alpha: Int) {
        if (size <= .1f || alpha <= 0) return
        canvas.save(); canvas.translate(x, y)
        canvas.rotate(rotation + if (kind == 3) 45 else 0)
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = alpha
        if (kind == 2 || kind == 3) {
            // The launch trail contracts while the sticker keeps travelling.
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 1.2f * dp
            canvas.drawLine(-size, 0f, -size * 3.4f, 0f, paint)
            paint.alpha = alpha / 3; paint.strokeWidth = .6f * dp
            canvas.drawLine(-size * 1.3f, size * .45f, -size * 2.6f, size * .45f, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.alpha = alpha
        paint.typeface = Typeface.DEFAULT; paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER; paint.textSize = size * 2.2f
        stickers.draw(canvas, kind, paint.textSize, alpha)
        canvas.restore()
    }

    private fun drawMeter(canvas: Canvas) {
        val left = maxOf(stage.left + 8 * dp, stage.right - 124 * dp); val right = stage.right - 10 * dp
        val compact = stage.height() < 60 * dp
        val top = stage.top + (if (compact) 3 else 8) * dp
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = 0xff160b19.toInt(); paint.alpha = 245
        canvas.drawRoundRect(left, top, right, top + (if (compact) 28 else 38) * dp, 6 * dp, 6 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp; paint.color = calloutColor
        canvas.drawRoundRect(left, top, right, top + (if (compact) 28 else 38) * dp, 6 * dp, 6 * dp, paint)
        paint.style = Paint.Style.FILL; paint.typeface = titleTypeface
        paint.textAlign = Paint.Align.CENTER; paint.textSize = (if (compact) 15 else 17) * dp; paint.color = calloutColor; paint.alpha = 255
        canvas.drawText("$combo COMBO", (left + right) / 2, top + (if (compact) 19 else 23) * dp, paint)
        paint.color = 0xff4a2435.toInt()
        canvas.drawRoundRect(left + 10 * dp, top + (if (compact) 23 else 29) * dp, right - 10 * dp, top + (if (compact) 25 else 32) * dp, dp, dp, paint)
        paint.color = if (combo % 10 >= 7) red else gold
        val level = if (combo % 10 == 0) 1f else (combo % 10) / 10f
        canvas.drawRoundRect(left + 10 * dp, top + (if (compact) 23 else 29) * dp, left + 10 * dp + (right - left - 20 * dp) * level, top + (if (compact) 25 else 32) * dp, dp, dp, paint)
    }
}
