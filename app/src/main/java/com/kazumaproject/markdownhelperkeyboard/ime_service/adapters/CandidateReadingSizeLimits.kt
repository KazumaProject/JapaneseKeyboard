package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlin.math.ceil

/** The settings editor and renderer use the same size limit, in sp, for the fixed rows. */
internal object CandidateReadingSizeLimits {
    private const val GLYPHS = "漢字感じへんかんあいうえおがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽアイウエオガギグゲゴパピプペポABCDEFGHIJKLMNOPQRSTUVWXYZgjpqy"
    private data class Key(
        val density: Float, val scaledDensity: Float, val fontScale: Float, val bodySize: Float,
        val portraitRows: Int, val portraitHeight: Int,
        val landscapeRows: Int, val landscapeHeight: Int,
        val font: KeyboardFontSnapshot
    )
    private var cached: Pair<Key, Int>? = null

    fun maximumSp(context: Context): Int {
        val prefs = AppPreference
        val metrics = context.resources.displayMetrics
        val portraitRows = prefs.getCandidateColumn(false).toIntOrNull() ?: 1
        val landscapeRows = prefs.getCandidateColumn(true).toIntOrNull() ?: 1
        val key = Key(metrics.density, metrics.scaledDensity, context.resources.configuration.fontScale, prefs.candidate_letter_size ?: 14f,
            portraitRows, prefs.getCandidateVisibleHeightDp(false, portraitRows.toString()),
            landscapeRows, prefs.getCandidateVisibleHeightDp(true, landscapeRows.toString()),
            KeyboardFontApplicator.processSnapshot)
        cached?.takeIf { it.first == key }?.let { return it.second }
        val maximum = minOf(
            measureRow(context, key, key.portraitRows, key.portraitHeight),
            measureRow(context, key, key.landscapeRows, key.landscapeHeight)
        )
        cached = key to maximum
        return maximum
    }

    fun configurePreference(context: Context, preference: androidx.preference.SeekBarPreference) {
        preference.min = AppPreference.MIN_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE
        preference.max = maximumSp(context)
        preference.value = AppPreference.live_conversion_candidate_yomi_size.coerceIn(preference.min, preference.max)
    }

    fun clamp(context: Context, sizeSp: Float): Float =
        sizeSp.coerceIn(AppPreference.MIN_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE.toFloat(), maximumSp(context).toFloat())

    private fun measureRow(context: Context, key: Key, rows: Int, heightDp: Int): Int {
        val item = LayoutInflater.from(context).inflate(R.layout.suggestion_item, null) as CandidateReadingLayout
        item.findViewById<TextView>(R.id.suggestion_item_text_view).apply {
            text = "    漢字    "
            textSize = key.bodySize
            KeyboardFontApplicator.apply(this, key.font)
        }
        val spacing = if (rows > 1) context.resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing) else 0
        val rowHeight = ((heightDp * key.density).toInt() / rows - spacing - spacing / rows).coerceAtLeast(0)
        item.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.AT_MOST))
        item.layout(0, 0, item.measuredWidth, item.measuredHeight)
        val body = item.findViewById<TextView>(R.id.suggestion_item_text_view)
        val bounds = Rect(0, 0, body.width, body.height)
        item.offsetDescendantRectToMyCoords(body, bounds)
        val ink = Rect()
        body.paint.getTextBounds(GLYPHS, 0, GLYPHS.length, ink)
        // The first annotation can use the empty space above the centered strip. The
        // strip's measured items and grid decorations remain exactly as with reading OFF.
        val stripHeight = rows * item.measuredHeight + if (rows > 1) (rows + 1) * spacing else 0
        val frameHeight = (heightDp * key.density).toInt()
        val headroom = ((frameHeight - stripHeight) / 2).coerceAtLeast(0) + spacing
        val available = (headroom + bounds.top + body.baseline + ink.top - ceil(key.density.toDouble()).toInt()).coerceAtLeast(0)
        val reading = item.findViewById<TextView>(R.id.suggestion_item_yomi_text_view)
        KeyboardFontApplicator.apply(reading, key.font)
        val paint = Paint(reading.paint)
        var maximum = AppPreference.MIN_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE
        for (size in maximum..AppPreference.MAX_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), context.resources.displayMetrics)
            paint.getTextBounds(GLYPHS, 0, GLYPHS.length, ink)
            if (ink.height() <= available) maximum = size
        }
        return maximum
    }
}
