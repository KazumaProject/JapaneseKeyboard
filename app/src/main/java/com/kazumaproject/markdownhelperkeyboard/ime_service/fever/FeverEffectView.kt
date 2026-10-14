package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/** Procedural effects: bounded particles, no video assets, no input or persistent editor data. */
internal class FeverEffectView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val arena = RectF()
    private val protectedKeys = RectF()
    private val stage = RectF()
    private var keyEdges = emptyList<RectF>()
    private var controls = emptyList<RectF>()
    private val candidateArea = RectF()
    private var rawTouchX = 0f
    private var rawTouchY = 0f
    private val particles = ArrayList<Particle>(180)
    private val ornaments = ArrayList<Ornament>(32)
    private var family = 0
    private var awardFamily = 0
    private var awardColor = Color.CYAN
    private var combo = 0
    private var award: FeverAward? = null
    private var awardStart = 0L
    private var calloutStart = 0L
    private var sparkStart = 0L
    private var callout = ""
    private var cue: FeverCue? = null
    private var calloutColor = Color.WHITE
    private var originX = 0f
    private var originY = 0f
    var intensity = 2
    private val dp get() = resources.displayMetrics.density
    private val motion get() = android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    private val gold = 0xffffc83d.toInt()
    private val red = 0xffff1749.toInt()
    private val colors = intArrayOf(0xff5ae7ff.toInt(), 0xff57fff1.toInt(), gold, red, 0xffc882ff.toInt())
    private val stages = arrayOf("START!", "GOOD!", "NICE!", "MORE!", "RUSH!", "CHANCE!", "LUCKY 7", "BOOST!", "REACH!")
    private data class Particle(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int, val size: Float, val end: Long, val born: Long)

    private data class Ornament(val x: Float, val y: Float, val angle: Float, val speed: Float, val size: Float, val family: Int, val color: Int, val born: Long, val life: Long)

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setArena(rect: RectF) { if (arena != rect) { arena.set(rect); updateStage(); invalidate() } }
    fun protectKeys(rect: RectF?) {
        if (rect == null) protectedKeys.setEmpty() else protectedKeys.set(rect)
        updateStage(); invalidate()
    }
    fun setKeyEdges(rects: List<RectF>, candidates: RectF? = null) {
        keyEdges = rects
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
    fun clear() { combo = 0; award = null; callout = ""; calloutStart = 0; sparkStart = 0; particles.clear(); ornaments.clear(); invalidate() }

    fun show(event: FeverEvent, touchX: Float? = null, touchY: Float? = null) {
        val time = SystemClock.uptimeMillis()
        combo = event.combo; cue = event.cue
        family = FeverVisualStyle.family(event)
        calloutColor = FeverVisualStyle.accent(event)
        sparkStart = time
        if (arena.isEmpty) { arena.set(0f, 0f, width.toFloat(), height.toFloat()); updateStage() }
        originX = (touchX ?: arena.centerX()).coerceIn(arena.left, arena.right)
        originY = (touchY ?: arena.centerY()).coerceIn(arena.top, arena.bottom)
        rawTouchX = originX; rawTouchY = originY
        if (protectedKeys.contains(originX, originY)) {
            originX = originX.coerceIn(stage.left, maxOf(stage.left, stage.right))
            originY = stage.centerY()
        }
        if (event.cue != null || (event.award == null && event.combo > 0)) {
            calloutStart = time
            callout = event.cue?.label ?: "${event.combo}  ${stages[(event.combo - 1) % stages.size]}"
        }
        if (event.award != null) { award = event.award; awardStart = time; calloutStart = 0; awardFamily = family; awardColor = calloutColor }
        if (motion && !stage.isEmpty) {
            val random = kotlin.random.Random(time)
            val big = event.award != null
            val count = if (big) intArrayOf(18, 60, 150)[intensity] else intArrayOf(6, 16, 28)[intensity]
            repeat(count) { index ->
                if (particles.size >= 180) particles.removeAt(0)
                val angle = random.nextFloat() * PI.toFloat() * 2
                val speed = (if (big) 100 + random.nextFloat() * 420 else 30 + random.nextFloat() * 110) * dp
                val spread = if (big && intensity == 2) .7f else 0f
                val x = if (big) stage.centerX() + (random.nextFloat() - .5f) * stage.width() * spread else originX
                val y = if (big) titleY() + (random.nextFloat() - .5f) * stage.height() * spread * .5f else originY
                val color = if (big) FeverVisualStyle.palette[index % FeverVisualStyle.palette.size]
                    else event.cue?.color ?: calloutColor
                val born = time + if (big) (index % 3) * 160L else 0
                particles.add(Particle(x, y, cos(angle) * speed, sin(angle) * speed - 50 * dp, color,
                    (1 + random.nextFloat() * if (big) 4 else 2) * dp, born + if (big) 1400 else 560, born))
            }
            repeat(if (big) intArrayOf(4, 10, 20)[intensity] else intArrayOf(1, 3, 5)[intensity]) { index ->
                if (ornaments.size >= 32) ornaments.removeAt(0)
                val angle = (index * 2.39996f + event.combo * .7f)
                val size = minOf((if (big) 10 + random.nextFloat() * 13 else 5 + random.nextFloat() * 8) * dp,
                    (stage.height() * if (stage.height() < 110 * dp) .16f else .27f).coerceAtLeast(3 * dp))
                ornaments.add(Ornament(if (big) stage.centerX() else originX, if (big) titleY() else originY,
                    angle, (if (big) 95 + random.nextFloat() * 150 else 65 + random.nextFloat() * 90) * dp,
                    size, if (big) (family + index) % 5 else family,
                    FeverVisualStyle.palette[(family + index) % 5], time + if (big) 160L + index % 3 * 120 else 0,
                    if (big) 1400 else 650))
            }
        }
        invalidate()
    }

    private fun titleY() = stage.top + stage.height() * .43f
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (arena.isEmpty) return
        val now = SystemClock.uptimeMillis()
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
            drawParticles(canvas, now)
            drawOrnaments(canvas, now)
            if (active != null) drawAwardTitle(canvas, active, elapsed)
        } else { particles.clear(); ornaments.clear() }
        val smallActive = calloutStart > 0 && now - calloutStart < 700
        if (smallActive && active == null && !stage.isEmpty) drawCallout(canvas, now - calloutStart)
        if (motion && sparkStart > 0 && now - sparkStart < 450) {
            val progress = (now - sparkStart) / 450f
            paint.shader = null; paint.color = calloutColor; paint.alpha = ((1 - progress) * 200).toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (1 - progress) * 3 * dp
            canvas.drawCircle(originX, originY, (8 + progress * 35) * dp, paint)
        }
        if (combo > 0 && !stage.isEmpty && !(stage.height() < 110 * dp && (active != null || smallActive))) drawMeter(canvas)
        canvas.restore()
        drawKeyEnergy(canvas, now, active)
        drawCandidateEnergy(canvas, now, active)
        paint.reset(); paint.isAntiAlias = true
        if (active != null || particles.isNotEmpty() || ornaments.isNotEmpty() || smallActive || (sparkStart > 0 && now - sparkStart < 650)) postInvalidateOnAnimation()
    }

    /** Draw only key borders and gaps. Foreground labels and candidate cells remain untouched. */
    private fun drawKeyEnergy(canvas: Canvas, now: Long, active: FeverAward?) {
        if (keyEdges.isEmpty()) return
        val age = now - sparkStart
        if (active == null && (sparkStart == 0L || age !in 0..650)) return
        val duration = if (active != null) 2000f else 650f
        val progress = if (active != null) (now - awardStart) / duration else age / duration
        val fade = if (active != null) minOf(1f, (1 - progress) * 4) else (1 - progress).coerceIn(0f, 1f)
        canvas.save(); canvas.clipRect(arena)
        val keyboardArea = RectF(keyEdges.first())
        keyEdges.drop(1).forEach(keyboardArea::union)
        canvas.clipRect(keyboardArea)
        // Clipping the inside of every cap also protects edge-positioned flick labels.
        keyEdges.forEach { rect ->
            val inside = RectF(rect).apply { inset(4 * dp, 4 * dp) }
            if (!inside.isEmpty) {
                if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(inside)
                else @Suppress("DEPRECATION") canvas.clipRect(inside, Region.Op.DIFFERENCE)
            }
        }
        paint.shader = null; paint.style = Paint.Style.STROKE
        val accent = when { cue == FeverCue.DELETE || active == FeverAward.HOT -> red
            active != null -> awardColor; else -> calloutColor }
        keyEdges.forEachIndexed { index, rect ->
            val touched = rect.contains(rawTouchX, rawTouchY)
            val wave = if (motion) (.6f + .4f * cos(index * .6f - progress * 9)).coerceIn(0f, 1f) else 1f
            val amount = fade * if (touched) 1f else wave * if (active != null) 1f else .85f
            val outline = RectF(rect).apply { inset(1.5f * dp, 1.5f * dp) }
            paint.color = accent; paint.alpha = (amount * 155).toInt(); paint.strokeWidth = (4 + intensity * 2) * dp
            canvas.drawRoundRect(outline, 9 * dp, 9 * dp, paint)
            paint.alpha = (amount * 245).toInt(); paint.strokeWidth = (1 + intensity * .4f) * dp
            canvas.drawRoundRect(outline, 9 * dp, 9 * dp, paint)
            if (motion) {
                val phase = (progress * 2 + index * .17f) % 1f
                val x = rect.left + rect.width() * phase
                paint.color = Color.WHITE; paint.alpha = (amount * 220).toInt(); paint.strokeWidth = dp
                canvas.drawLine(x - 3 * dp, rect.top + 2 * dp, x + 3 * dp, rect.top + 2 * dp, paint)
            }
        }
        if (motion) {
            paint.color = accent; paint.alpha = (fade * 210).toInt(); paint.strokeWidth = 2 * dp
            val tapProgress = ((now - sparkStart) / 560f).coerceIn(0f, 1f)
            val radius = (8 + (1 - (1 - tapProgress).pow(3)) * if (active != null) 180 else 120) * dp
            canvas.drawCircle(rawTouchX, rawTouchY, radius, paint)
            canvas.drawCircle(rawTouchX, rawTouchY, radius * .72f, paint)
            repeat(if (intensity == 2) 16 else 8) { index ->
                val angle = index * PI.toFloat() / 8
                val x = rawTouchX + cos(angle) * radius; val y = rawTouchY + sin(angle) * radius
                canvas.drawLine(x, y, x - cos(angle) * 18 * dp, y - sin(angle) * 18 * dp, paint)
                if (index % 3 == 0) {
                    paint.style = Paint.Style.FILL
                    drawMotif(canvas, x, y, (4 + intensity) * dp, family, accent, tapProgress * 160, (fade * 220).toInt())
                    paint.style = Paint.Style.STROKE
                }
            }
        }
        canvas.restore()
    }

    private fun drawCandidateEnergy(canvas: Canvas, now: Long, active: FeverAward?) {
        if (candidateArea.isEmpty) return
        val age = now - sparkStart
        if (active == null && (sparkStart == 0L || age !in 0..650)) return
        val progress = if (active == null) age / 650f else (now - awardStart) / 2000f
        val fade = (1 - progress).coerceIn(0f, 1f)
        val accent = if (active != null) awardColor else calloutColor
        canvas.save(); canvas.clipRect(arena); canvas.clipRect(candidateArea)
        paint.style = Paint.Style.FILL
        // The wash reaches only the bottom edge and stays below 8% opacity over text.
        paint.shader = LinearGradient(0f, candidateArea.top, 0f, candidateArea.bottom,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, accent), floatArrayOf(0f, .75f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = (fade * 20).toInt(); canvas.drawRect(candidateArea, paint); paint.shader = null
        val center = if (motion) candidateArea.left + candidateArea.width() * progress else candidateArea.centerX()
        paint.shader = LinearGradient(center - 45 * dp, 0f, center + 45 * dp, 0f,
            intArrayOf(Color.TRANSPARENT, accent, Color.WHITE, accent, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        paint.alpha = (fade * 220).toInt(); paint.strokeWidth = 1.3f * dp
        canvas.drawLine(candidateArea.left, candidateArea.bottom - dp, candidateArea.right, candidateArea.bottom - dp, paint)
        paint.shader = null; canvas.restore()
    }

    /** Gather -> impact -> orbit + confetti. All layers share one bounded two-second clock. */
    private fun drawAward(canvas: Canvas, value: FeverAward, elapsed: Long) {
        val t = if (motion) elapsed.toFloat() else 650f
        val fade = minOf(1f, elapsed / 60f, (2000 - elapsed) / 320f).coerceIn(0f, 1f)
        val cx = stage.centerX(); val cy = titleY()
        val compact = stage.height() < 110 * dp
        val radius = hypot(stage.width(), stage.height())
        val accent = awardColor
        val gather = (t / 160f).coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE; paint.shader = null
        // A contracting halo establishes anticipation before the release at 160ms.
        paint.color = accent; paint.alpha = (fade * 210).toInt(); paint.strokeWidth = (1 + intensity) * dp
        val ring = if (t < 160) radius * (.45f - .32f * gather) else radius * (.13f + ((t - 160) / 1100f).coerceIn(0f, 1f))
        canvas.drawCircle(cx, cy, ring, paint)
        canvas.drawCircle(cx, cy, ring * .78f, paint)
        if (t >= 160) {
            val release = ((t - 160) / 600f).coerceIn(0f, 1f)
            val rays = intArrayOf(8, 16, 28)[intensity]
            repeat(rays) { index ->
                val angle = index * 2 * PI.toFloat() / rays + awardFamily * .3f
                val inner = (12 * dp + release * radius * .25f)
                val outer = inner + (1 - release) * radius * .55f
                paint.color = FeverVisualStyle.palette[(index + awardFamily) % 5]
                paint.alpha = (fade * (1 - release) * 230).toInt(); paint.strokeWidth = (if (index % 3 == 0) 3f else 1f) * dp
                canvas.drawLine(cx + cos(angle) * inner, cy + sin(angle) * inner, cx + cos(angle) * outer, cy + sin(angle) * outer, paint)
            }
            if (intensity == 2 && !compact && t < 600) drawLightning(canvas, cx, cy, radius * .65f, fade * (1 - release), release, accent)
        }
        // The frame travels in color rather than obscuring the editor with an opaque field.
        val border = RectF(arena).apply { inset(2.5f * dp, 2.5f * dp) }
        paint.style = Paint.Style.STROKE
        paint.shader = SweepGradient(arena.centerX(), arena.centerY(), FeverVisualStyle.palette + FeverVisualStyle.palette[0], null)
        paint.alpha = (fade * 180).toInt(); paint.strokeWidth = (3 + intensity) * dp
        canvas.drawRoundRect(border, 12 * dp, 12 * dp, paint); paint.shader = null
    }

    private fun drawAwardTitle(canvas: Canvas, value: FeverAward, elapsed: Long) {
        val t = if (motion) elapsed.toFloat() else 650f
        val fade = minOf(1f, elapsed / 60f, (2000 - elapsed) / 320f).coerceIn(0f, 1f)
        val cx = stage.centerX(); val cy = titleY()
        val compact = stage.height() < 110 * dp
        val accent = awardColor
        if (t >= 120) {
            val impactAge = (t - 160).coerceAtLeast(0f)
            val bounce = if (motion) exp(-impactAge / 150f) * sin(impactAge / 53f) else 0f
            val label = when (value) {
                FeverAward.COMBO -> "$combo COMBO!"
                FeverAward.FEVER -> "ON FIRE!"
                FeverAward.HOT -> "LOVE IT!"
                FeverAward.JACKPOT -> "MEGA HIT!"
            }
            val size = minOf(stage.width() / maxOf(3f, label.length * .64f), stage.height() * .52f, 76 * dp)
            canvas.save(); canvas.scale(1 + bounce * .28f, 1 + bounce * .28f, cx, cy)
            if (!compact) canvas.rotate(-4 + bounce * 3, cx, cy)
            metallicText(canvas, label, cx, cy, size, fade, false, true)
            canvas.restore()
            if (!compact) {
                paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC); paint.textSize = 16 * dp
                paint.style = Paint.Style.FILL; paint.color = accent; paint.alpha = (fade * 255).toInt(); paint.textAlign = Paint.Align.CENTER
                canvas.drawText("$combo COMBO  /  ${if (value == FeverAward.JACKPOT) "BONUS!" else "KEEP GOING!"}", cx, cy + size * .8f, paint)
            }
        }
    }

    private fun drawLightning(canvas: Canvas, cx: Float, cy: Float, radius: Float, fade: Float, progress: Float, accent: Int) {
        repeat(if (intensity == 2) 10 else 5) { bolt ->
            val angle = bolt * 2f * PI.toFloat() / 10 + .13f
            path.reset(); path.moveTo(cx, cy)
            repeat(7) { step ->
                val distance = radius * (step + 1) / 7f
                val bend = sin(bolt * 7f + step * 13f + if (motion) progress * 3 else 0f) * 24 * dp
                path.lineTo(cx + cos(angle) * distance - sin(angle) * bend, cy + sin(angle) * distance + cos(angle) * bend)
            }
            paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND
            paint.color = accent; paint.alpha = (fade * 100).toInt(); paint.strokeWidth = 9 * dp; canvas.drawPath(path, paint)
            paint.color = 0xfffff4cc.toInt(); paint.alpha = (fade * 240).toInt(); paint.strokeWidth = 1.6f * dp; canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun metallicText(canvas: Canvas, label: String, x: Float, y: Float, size: Float, fade: Float, fiery: Boolean, rainbow: Boolean = false) {
        paint.shader = null; paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
        paint.isFakeBoldText = true; paint.textAlign = Paint.Align.CENTER; paint.textSize = size
        val baseline = y - (paint.ascent() + paint.descent()) / 2
        val bevel = minOf(1f, size / (32 * dp))
        paint.alpha = (fade * 255).toInt(); paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0xff030c20.toInt(); paint.alpha = (fade * 255).toInt(); paint.strokeWidth = 5 * dp * bevel
        canvas.drawText(label, x + 1.5f * dp * bevel, baseline + 3 * dp * bevel, paint)
        canvas.drawText(label, x, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(x, y - size * .5f, x, y + size * .5f,
            if (rainbow) intArrayOf(0xffffff92.toInt(), 0xffffcb63.toInt(), 0xff78ffcf.toInt(), 0xff60dfff.toInt(), 0xffc591ff.toInt())
            else intArrayOf(Color.WHITE, if (fiery) 0xffff7cb7.toInt() else calloutColor, 0xffa9b3ff.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawText(label, x, baseline, paint); paint.shader = null; paint.isFakeBoldText = false
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        val iterator = particles.iterator()
        paint.shader = null; paint.style = Paint.Style.FILL
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (p.end <= now) { iterator.remove(); continue }
            if (p.born > now) continue
            val age = (now - p.born).coerceAtLeast(0) / 1000f
            val x = p.x + p.vx * age; val y = p.y + p.vy * age + 100 * dp * age * age
            paint.color = p.color; paint.alpha = (255 * minOf(1f, (p.end - now) / 300f)).toInt()
            paint.strokeWidth = p.size * .6f
            canvas.drawLine(x, y, x - p.vx * .025f, y - p.vy * .025f, paint)
            if (p.size > 2 * dp) {
                canvas.save(); canvas.rotate(age * 180 + p.vx, x, y)
                canvas.drawRect(x - p.size, y - p.size * .35f, x + p.size, y + p.size * .35f, paint); canvas.restore()
            } else canvas.drawCircle(x, y, p.size * .6f, paint)
            if (p.size > 3 * dp) {
                canvas.drawLine(x - p.size * 2, y, x + p.size * 2, y, paint)
                canvas.drawLine(x, y - p.size * 2, x, y + p.size * 2, paint)
            }
        }
    }

    private fun drawCallout(canvas: Canvas, elapsed: Long) {
        val fraction = elapsed / 700f
        val fade = minOf(1f, (1 - fraction) * 3)
        val compact = stage.height() < 110 * dp
        val cx = stage.centerX()
        val cy = stage.top + minOf(stage.height() * .5f, 110 * dp)
        val halfWidth = minOf(98 * dp, (stage.width() / 2 - 3 * dp).coerceAtLeast(dp))
        paint.shader = LinearGradient(cx - halfWidth, cy, cx + halfWidth, cy,
            intArrayOf(Color.TRANSPARENT, 0xee071528.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; paint.alpha = (fade * 220).toInt()
        canvas.drawRect(cx - halfWidth, cy - minOf(18 * dp, stage.height() * .45f), cx + halfWidth, cy + minOf(18 * dp, stage.height() * .45f), paint)
        paint.shader = null
        canvas.save()
        if (motion) { val scale = 1 + .18f * exp(-elapsed / 100f); canvas.scale(scale, scale, cx, cy) }
        metallicText(canvas, callout, cx, cy, minOf(stage.height() * .6f, 26 * dp, halfWidth * 2 / maxOf(3f, callout.length * .65f)), fade, cue == FeverCue.DELETE || combo >= 8)
        canvas.restore()
        if (motion && cue != null) {
            val horizontal = cue == FeverCue.CURSOR_LEFT || cue == FeverCue.CURSOR_RIGHT
            val vertical = cue == FeverCue.CURSOR_UP || cue == FeverCue.CURSOR_DOWN
            val direction = if (cue == FeverCue.CURSOR_LEFT || cue == FeverCue.CURSOR_UP) -1 else 1
            paint.color = calloutColor; paint.alpha = (fade * 240).toInt(); paint.strokeWidth = 2 * dp
            repeat(3) { trail ->
                val offset = (fraction * 75 - trail * 12) * dp * direction
                if (horizontal) canvas.drawLine(originX + offset, originY, originX + offset - direction * 22 * dp, originY, paint)
                else if (vertical) canvas.drawLine(originX, originY + offset, originX, originY + offset - direction * 22 * dp, paint)
            }
        }
    }

    private fun drawInputLayers(canvas: Canvas, now: Long) {
        val age = now - sparkStart
        if (sparkStart == 0L || age !in 0..650) return
        val progress = if (motion) age / 650f else .25f
        val fade = (1 - progress).coerceIn(0f, 1f)
        val compact = stage.height() < 110 * dp
        val cx = if (compact) stage.centerX() else originX
        val cy = if (compact) stage.centerY() else originY
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.color = calloutColor
        paint.alpha = (fade * 210).toInt(); paint.strokeWidth = (1 + intensity * .5f) * dp
        val radius = (8 + (1 - (1 - progress).pow(3)) * if (compact) 70 else 160) * dp
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, radius * .67f, paint)
        // Two satellites curve away from the impact, plus a directional comet for cursor movement.
        repeat(if (intensity == 0) 1 else 3) { index ->
            val angle = progress * 4 + index * 2.094f + combo * .7f
            val rx = radius * cos(angle); val ry = radius * sin(angle) * if (compact) .16f else .7f
            drawMotif(canvas, cx + rx, cy + ry, (if (compact) 5f else 9f) * dp,
                (family + index) % 5, FeverVisualStyle.palette[(family + index) % 5], progress * 130, (fade * 240).toInt())
        }
    }

    private fun drawOrnaments(canvas: Canvas, now: Long) {
        val iterator = ornaments.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val age = now - item.born
            if (age >= item.life) { iterator.remove(); continue }
            if (age < 0) continue
            val seconds = age / 1000f
            val compact = stage.height() < 110 * dp
            val x = item.x + cos(item.angle) * item.speed * seconds
            val y = if (compact) stage.centerY() + sin(item.angle + seconds * 5) * stage.height() * .18f
                else item.y + sin(item.angle) * item.speed * seconds - sin(seconds * 2) * 35 * dp
            drawMotif(canvas, x, y, item.size, item.family, item.color, item.angle * 20 + seconds * 90,
                (minOf(1f, (item.life - age) / 280f) * 245).toInt())
        }
    }

    /** Small vector stickers: stars, hearts, shooting stars, rockets, and holographic diamonds. */
    private fun drawMotif(canvas: Canvas, x: Float, y: Float, size: Float, kind: Int, color: Int, rotation: Float, alpha: Int) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(rotation)
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = alpha
        path.reset()
        when (kind % 5) {
            1 -> {
                path.moveTo(0f, size * .9f)
                path.cubicTo(-size * 1.6f, -size * .1f, -size * .8f, -size * 1.35f, 0f, -size * .5f)
                path.cubicTo(size * .8f, -size * 1.35f, size * 1.6f, -size * .1f, 0f, size * .9f)
            }
            3 -> {
                path.moveTo(size * 1.3f, 0f); path.quadTo(size * .5f, -size, -size * .5f, -size * .45f)
                path.lineTo(-size, -size * .85f); path.lineTo(-size * .8f, 0f); path.lineTo(-size, size * .85f)
                path.lineTo(-size * .5f, size * .45f); path.quadTo(size * .5f, size, size * 1.3f, 0f)
            }
            4 -> {
                path.moveTo(0f, -size); path.lineTo(size * .8f, 0f); path.lineTo(0f, size); path.lineTo(-size * .8f, 0f); path.close()
            }
            else -> repeat(10) { index ->
                val angle = -PI.toFloat() / 2 + index * PI.toFloat() / 5
                val radius = if (index % 2 == 0) size else size * .42f
                if (index == 0) path.moveTo(cos(angle) * radius, sin(angle) * radius)
                else path.lineTo(cos(angle) * radius, sin(angle) * radius)
            }
        }
        path.close(); canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = .8f * dp
        canvas.drawPath(path, paint)
        if (kind == 2 || kind == 3) {
            paint.color = color; paint.strokeWidth = 2 * dp
            canvas.drawLine(-size, 0f, -size * 3.4f, 0f, paint)
            paint.alpha = alpha / 2; paint.strokeWidth = dp
            canvas.drawLine(-size * 1.3f, size * .45f, -size * 2.6f, size * .45f, paint)
        }
        paint.style = Paint.Style.FILL; canvas.restore()
    }

    private fun drawMeter(canvas: Canvas) {
        val left = maxOf(stage.left + 8 * dp, stage.right - 124 * dp); val right = stage.right - 10 * dp
        val compact = stage.height() < 60 * dp
        val top = stage.top + (if (compact) 3 else 8) * dp
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = 0xff160b19.toInt(); paint.alpha = 245
        canvas.drawRoundRect(left, top, right, top + (if (compact) 28 else 38) * dp, 6 * dp, 6 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp; paint.color = gold
        canvas.drawRoundRect(left, top, right, top + (if (compact) 28 else 38) * dp, 6 * dp, 6 * dp, paint)
        paint.style = Paint.Style.FILL; paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
        paint.textAlign = Paint.Align.CENTER; paint.textSize = (if (compact) 15 else 17) * dp; paint.color = 0xffffe38b.toInt(); paint.alpha = 255
        canvas.drawText("$combo COMBO", (left + right) / 2, top + (if (compact) 19 else 23) * dp, paint)
        paint.color = 0xff4a2435.toInt()
        canvas.drawRoundRect(left + 10 * dp, top + (if (compact) 23 else 29) * dp, right - 10 * dp, top + (if (compact) 25 else 32) * dp, dp, dp, paint)
        paint.color = if (combo % 10 >= 7) red else gold
        val level = if (combo % 10 == 0) 1f else (combo % 10) / 10f
        canvas.drawRoundRect(left + 10 * dp, top + (if (compact) 23 else 29) * dp, left + 10 * dp + (right - left - 20 * dp) * level, top + (if (compact) 25 else 32) * dp, dp, dp, paint)
    }
}
