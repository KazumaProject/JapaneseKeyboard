package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Settings and an illustrative renderer; no IME view or input controller is created here. */
class KeyboardGeometrySettingsFragment : Fragment() {
    private lateinit var store: KeyboardGeometryPreferences
    private lateinit var keyboard: GeometryKeyboard
    private var landscape = false
    private var updating = false
    private var value = KeyboardGeometryPreference()
    private lateinit var preview: GeometryPreview
    private lateinit var splitLabel: TextView
    private lateinit var curveLabel: TextView
    private lateinit var warning: TextView
    private lateinit var split: SeekBar
    private lateinit var curve: SeekBar
    private lateinit var orientation: RadioGroup
    private lateinit var hand: RadioGroup
    private lateinit var height: RadioGroup
    private lateinit var candidates: RadioGroup

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        store = KeyboardGeometryPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        keyboard = runCatching { GeometryKeyboard.valueOf(requireArguments().getString("geometryKeyboard", "QWERTY")) }.getOrDefault(GeometryKeyboard.QWERTY)
        landscape = savedInstanceState?.getBoolean("landscape")
            ?: (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val context = requireContext()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        fun heading(text: Int) = TextView(context).apply { setText(text); textSize = 16f; setPadding(0, dp(14), 0, dp(4)) }.also(content::addView)
        fun group(options: List<Pair<Int, Int>>, vertical: Boolean = false): RadioGroup = RadioGroup(context).apply {
            orientation = if (vertical) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
            options.forEach { (id, label) -> addView(RadioButton(context).apply { this.id = id; setText(label) },
                if (vertical) RadioGroup.LayoutParams(-1, -2) else RadioGroup.LayoutParams(0, -2, 1f)) }
        }.also(content::addView)
        orientation = group(listOf(1 to R.string.keyboard_geometry_portrait, 2 to R.string.keyboard_geometry_landscape))
        heading(R.string.keyboard_geometry_preview)
        preview = GeometryPreview(context, keyboard).also { content.addView(it, LinearLayout.LayoutParams(-1, dp(220))) }
        content.addView(TextView(context).apply { setText(R.string.keyboard_geometry_preview_description) })
        splitLabel = TextView(context).also(content::addView)
        split = SeekBar(context).apply { max = 40 }.also(content::addView)
        splitLabel.visibility = if (keyboard == GeometryKeyboard.QWERTY) View.VISIBLE else View.GONE
        split.visibility = splitLabel.visibility
        curveLabel = TextView(context).also(content::addView)
        curve = SeekBar(context).apply { max = 25 }.also(content::addView)
        val handLabel = heading(R.string.keyboard_geometry_hand)
        hand = group(listOf(3 to R.string.keyboard_geometry_right_hand, 4 to R.string.keyboard_geometry_left_hand))
        handLabel.visibility = if (keyboard == GeometryKeyboard.QWERTY) View.GONE else View.VISIBLE
        hand.visibility = handLabel.visibility
        heading(R.string.keyboard_geometry_height)
        height = group(listOf(5 to R.string.keyboard_geometry_height_auto, 6 to R.string.keyboard_geometry_height_fixed), vertical = true)
        heading(R.string.keyboard_geometry_candidates)
        candidates = group(if (keyboard == GeometryKeyboard.QWERTY) listOf(7 to R.string.keyboard_geometry_top, 8 to R.string.keyboard_geometry_center)
            else listOf(7 to R.string.keyboard_geometry_top, 9 to R.string.keyboard_geometry_left, 10 to R.string.keyboard_geometry_right), vertical = true)
        warning = TextView(context).apply { setPadding(0, dp(12), 0, dp(8)) }.also(content::addView)
        content.addView(Button(context).apply { setText(R.string.keyboard_geometry_reset); setOnClickListener { store.reset(keyboard, landscape); load() } })

        orientation.setOnCheckedChangeListener { _, id -> if (!updating) { landscape = id == 2; load() } }
        hand.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(geometry = value.geometry.copy(hand = if (id == 4) KeyboardCurveHand.LEFT else KeyboardCurveHand.RIGHT))) }
        height.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(geometry = value.geometry.copy(autoHeight = id == 5))) }
        candidates.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(candidates = when (id) {
            8 -> GeometryCandidatePlacement.CENTER; 9 -> GeometryCandidatePlacement.LEFT; 10 -> GeometryCandidatePlacement.RIGHT; else -> GeometryCandidatePlacement.TOP
        })) }
        fun listener(change: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser && !updating) change(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        split.setOnSeekBarChangeListener(listener { save(value.copy(geometry = value.geometry.copy(splitPercent = it))) })
        curve.setOnSeekBarChangeListener(listener { save(value.copy(geometry = value.geometry.copy(curveDegrees = it))) })
        load()
        return ScrollView(context).apply { addView(content) }
    }

    private fun save(next: KeyboardGeometryPreference) { value = next; store.save(keyboard, landscape, next); render() }
    private fun load() { value = store.read(keyboard, landscape); render() }
    private fun render() {
        updating = true
        orientation.check(if (landscape) 2 else 1)
        split.progress = value.geometry.splitPercent
        curve.progress = value.geometry.curveDegrees
        splitLabel.text = getString(R.string.keyboard_geometry_split, value.geometry.splitPercent)
        curveLabel.text = getString(R.string.keyboard_geometry_curve, value.geometry.curveDegrees)
        split.contentDescription = splitLabel.text
        curve.contentDescription = curveLabel.text
        hand.check(if (value.geometry.hand == KeyboardCurveHand.LEFT) 4 else 3)
        height.check(if (value.geometry.autoHeight) 5 else 6)
        candidates.check(when (value.candidates) {
            GeometryCandidatePlacement.TOP -> 7; GeometryCandidatePlacement.CENTER -> 8
            GeometryCandidatePlacement.LEFT -> 9; GeometryCandidatePlacement.RIGHT -> 10
        })
        preview.value = value
        preview.landscape = landscape
        preview.invalidate()
        val metrics = resources.displayMetrics
        val screenWidth = if (landscape) maxOf(metrics.widthPixels, metrics.heightPixels) else minOf(metrics.widthPixels, metrics.heightPixels)
        val widthPercent = when {
            keyboard == GeometryKeyboard.QWERTY && landscape -> AppPreference.qwerty_keyboard_width_landscape
            keyboard == GeometryKeyboard.QWERTY -> AppPreference.qwerty_keyboard_width
            landscape -> AppPreference.keyboard_width_landscape
            else -> AppPreference.keyboard_width
        } ?: 100
        val availableWidth = if (keyboard == GeometryKeyboard.QWERTY) screenWidth * widthPercent.coerceIn(1, 100) / 100 else screenWidth
        preview.keyboardWidthPercent = widthPercent.coerceIn(1, 100)
        preview.alignEnd = when {
            keyboard == GeometryKeyboard.QWERTY && landscape -> AppPreference.qwerty_keyboard_position_landscape
            keyboard == GeometryKeyboard.QWERTY -> AppPreference.qwerty_keyboard_position
            landscape -> AppPreference.keyboard_position_landscape
            else -> AppPreference.keyboard_position
        } ?: true
        val requestedGap = availableWidth * value.geometry.splitPercent / 100f
        val effectiveGap = min(requestedGap, (availableWidth - 240f * metrics.density).coerceAtLeast(0f))
        warning.text = buildList {
            if (keyboard == GeometryKeyboard.QWERTY && effectiveGap > 0f) add(getString(R.string.keyboard_geometry_glide_disabled))
            if (keyboard == GeometryKeyboard.QWERTY && effectiveGap < requestedGap) add(getString(R.string.keyboard_geometry_gap_limited))
            if (keyboard == GeometryKeyboard.QWERTY && value.candidates == GeometryCandidatePlacement.CENTER && effectiveGap < 96f * metrics.density) add(getString(R.string.keyboard_geometry_candidate_fallback))
            if (keyboard != GeometryKeyboard.QWERTY && value.candidates != GeometryCandidatePlacement.TOP && availableWidth < 256f * metrics.density) add(getString(R.string.keyboard_geometry_candidate_fallback))
        }.joinToString("\n")
        updating = false
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("landscape", landscape); super.onSaveInstanceState(outState) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

/** Deliberately separate from runtime geometry and input views. */
private class GeometryPreview(context: Context, private val keyboard: GeometryKeyboard) : View(context) {
    var value = KeyboardGeometryPreference()
    var landscape = false
    var keyboardWidthPercent = 100
    var alignEnd = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun text(canvas: Canvas, label: String, x: Float, y: Float, size: Float) {
        paint.color = Color.WHITE; paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(35, 35, 39))
        val density = resources.displayMetrics.density
        val width = width.toFloat()
        val h = height.toFloat()
        val isQwerty = keyboard == GeometryKeyboard.QWERTY
        val metrics = resources.displayMetrics
        val screenWidth = if (landscape) maxOf(metrics.widthPixels, metrics.heightPixels) else minOf(metrics.widthPixels, metrics.heightPixels)
        val displayScale = width / screenWidth.coerceAtLeast(1)
        val requestedBodyWidth = width * keyboardWidthPercent / 100f
        val side = if (!isQwerty && value.candidates != GeometryCandidatePlacement.TOP && screenWidth >= 256f * density)
            min(160f * density * displayScale, maxOf(96f * density * displayScale, width * .25f)) else 0f
        val bodyWidth = min(requestedBodyWidth, width - side).coerceAtLeast(1f)
        val bodyLeft = when {
            side > 0f && value.candidates == GeometryCandidatePlacement.LEFT -> width - bodyWidth
            side > 0f -> 0f
            alignEnd -> width - bodyWidth
            else -> 0f
        }
        val bodyRight = bodyLeft + bodyWidth
        val gap = if (isQwerty) min(bodyWidth * value.geometry.splitPercent / 100f, (bodyWidth - 240f * density * displayScale).coerceAtLeast(0f)) else 0f
        val rows = if (isQwerty) listOf("qwertyuiop", "asdfghjkl", "zxcvbnm") else listOf("↶あかさ⌫", "←たなは→", "記まやら空", "切小わ、↵")
        val top = h * .24f
        val cellH = h * .15f
        val angle = Math.toRadians(value.geometry.curveDegrees.toDouble())
        val arcSpan = if (isQwerty) (bodyWidth - gap) / 2f else bodyWidth
        val extra = if (angle > 0) (arcSpan * kotlin.math.tan(angle / 2).toFloat() +
            (bodyWidth / (if (isQwerty) 10f else 5f)) * sin(angle).toFloat() / 2f) else 0f
        // The preview has its own renderer. Preserve its bottom edge while illustrating the
        // requested additional height, or fit that same shape into the baseline in fixed mode.
        canvas.save()
        val baselineBottom = top + 4 * cellH
        if (!value.geometry.autoHeight && extra > 0f) {
            val scale = (4 * cellH / (4 * cellH + extra)).coerceIn(.1f, 1f)
            canvas.scale(1f, scale, 0f, baselineBottom)
        }
        canvas.translate(0f, -extra)
        rows.forEachIndexed { row, labels ->
            val chars = labels.toList()
            val count = if (isQwerty) 10 else 5
            val cellW = bodyWidth / count
            chars.forEachIndexed character@ { column, ch ->
                if (ch == ' ') return@character
                val leftHalf = if (isQwerty) when (row) { 0 -> column < 5; 1 -> column < 5; 2 -> column < 4; else -> column < 4 } else value.geometry.hand == KeyboardCurveHand.LEFT
                val factor = if (isQwerty) (bodyWidth - gap) / bodyWidth else 1f
                val rawX = bodyLeft + (column + .5f + if (row == 1 && isQwerty) .3f else 0f) * cellW
                val cx = if (isQwerty) bodyLeft + (rawX - bodyLeft) * factor + if (leftHalf) 0f else gap else rawX
                val span = if (isQwerty) (bodyWidth - gap) / 2f else bodyWidth
                val distance = if (leftHalf) cx - bodyLeft else bodyRight - cx
                val radius = if (angle > 0) span / sin(angle).toFloat() else Float.POSITIVE_INFINITY
                val d = distance.coerceIn(0f, span)
                val offset = if (angle > 0) radius - sqrt((radius * radius - d * d).coerceAtLeast(0f)) else 0f
                val rotation = if (angle > 0) Math.toDegrees(asin((d / radius).coerceIn(-1f, 1f)).toDouble()).toFloat() * if (leftHalf) 1f else -1f else 0f
                val cy = top + row * cellH + cellH / 2f + offset
                canvas.save(); canvas.rotate(rotation, cx, cy)
                paint.color = Color.rgb(73, 73, 80)
                val rect = RectF(cx - cellW * factor * .38f, cy - cellH * .31f, cx + cellW * factor * .38f, cy + cellH * .31f)
                canvas.drawRoundRect(rect, 4f * density, 4f * density, paint)
                text(canvas, ch.toString(), cx, cy, min(cellH * .44f, 16f * density))
                canvas.restore()
            }
        }
        if (isQwerty) {
            val keyWidth = bodyWidth / 10f
            fun key(label: String, left: Float, right: Float, leftHalf: Boolean) {
                val cx = (left + right) / 2f
                val distance = (if (leftHalf) cx - bodyLeft else bodyRight - cx).coerceIn(0f, arcSpan)
                val radius = if (angle > 0) arcSpan / sin(angle).toFloat() else Float.POSITIVE_INFINITY
                val offset = if (angle > 0) radius - sqrt((radius * radius - distance * distance).coerceAtLeast(0f)) else 0f
                val rotation = if (angle > 0 && (gap > 0f || label != "space")) Math.toDegrees(asin((distance / radius).coerceIn(-1f, 1f)).toDouble()).toFloat() * if (leftHalf) 1f else -1f else 0f
                val cy = top + 3.5f * cellH + offset
                canvas.save()
                canvas.rotate(rotation, cx, cy)
                paint.color = Color.rgb(73, 73, 80)
                canvas.drawRoundRect(RectF(left + 4f * density, cy - cellH * .31f, right - 4f * density, cy + cellH * .31f), 4f * density, 4f * density, paint)
                text(canvas, label, cx, cy, min(cellH * .38f, 13f * density))
                canvas.restore()
            }
            key("123", bodyLeft, bodyLeft + keyWidth, true)
            key("↵", bodyRight - keyWidth, bodyRight, false)
            if (gap > 0f) {
                key("space", bodyLeft + keyWidth, bodyLeft + (bodyWidth - gap) / 2f, true)
                key("space", bodyRight - (bodyWidth - gap) / 2f, bodyRight - keyWidth, false)
            } else key("space", bodyLeft + keyWidth, bodyRight - keyWidth, true)
        }
        canvas.restore()
        val example = context.getString(R.string.keyboard_geometry_example_candidate)
        if (isQwerty && gap >= 96f * density * displayScale && value.candidates == GeometryCandidatePlacement.CENTER) {
            text(canvas, example, (bodyLeft + bodyRight) / 2f, top, 12f * density)
        } else if (side > 0f) {
            val cx = if (value.candidates == GeometryCandidatePlacement.LEFT) bodyLeft - side / 2f else bodyRight + side / 2f
            listOf(context.getString(R.string.keyboard_geometry_example_today), context.getString(R.string.keyboard_geometry_example_hello), example)
                .forEachIndexed { i, label -> text(canvas, label, cx, top + i * cellH, 12f * density) }
        } else text(canvas, "$example     $example     $example", width / 2f, h * .1f, 12f * density)
    }
}
