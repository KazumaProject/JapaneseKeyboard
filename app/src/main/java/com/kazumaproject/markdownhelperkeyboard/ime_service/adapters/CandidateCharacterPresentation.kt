package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.util.AttributeSet
import android.content.res.Configuration
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

internal enum class CandidateCharacterKind { HIRAGANA, KATAKANA, FULL_WIDTH, HALF_WIDTH, HALF_KATAKANA }

/** UTF-16 offsets in the visible, unpadded candidate body. Never offsets in its reading. */
internal data class CandidateCharacterRange(val start: Int, val end: Int, val kind: CandidateCharacterKind)

internal fun candidateCharacterRanges(text: String): List<CandidateCharacterRange> {
    fun rawKind(cp: Int): CandidateCharacterKind? = when {
        cp in 0xff66..0xff9f -> CandidateCharacterKind.HALF_KATAKANA
        cp in 0xff61..0xff65 || cp in 0x20..0x7e -> CandidateCharacterKind.HALF_WIDTH
        cp in 0xff01..0xff60 || cp in 0xffe0..0xffe6 || cp == 0x3000 -> CandidateCharacterKind.FULL_WIDTH
        Character.UnicodeScript.of(cp) == Character.UnicodeScript.HIRAGANA -> CandidateCharacterKind.HIRAGANA
        Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA -> CandidateCharacterKind.KATAKANA
        else -> null
    }
    val ranges = mutableListOf<CandidateCharacterRange>()
    var index = 0
    var previous: CandidateCharacterKind? = null
    while (index < text.length) {
        val cp = text.codePointAt(index)
        var end = index + Character.charCount(cp)
        // A keycap emoji is not an ASCII digit/symbol candidate.
        var keycapEnd = end
        if (keycapEnd < text.length && text.codePointAt(keycapEnd) == 0xfe0f) keycapEnd++
        if ((cp in 0x30..0x39 || cp == 0x23 || cp == 0x2a) &&
            keycapEnd < text.length && text.codePointAt(keycapEnd) == 0x20e3) {
            index = keycapEnd + 1
            previous = null
            continue
        }
        val kind = when (cp) {
            0x3099, 0x309a -> previous
            0x30fc, 0x30fb -> previous?.takeIf {
                it == CandidateCharacterKind.KATAKANA || it == CandidateCharacterKind.HIRAGANA
            } ?: if (end < text.length) rawKind(text.codePointAt(end))?.takeIf {
                it == CandidateCharacterKind.KATAKANA || it == CandidateCharacterKind.HIRAGANA
            } else null
            else -> rawKind(cp)
        }
        // Include a following combining dakuten in the same kana range.
        if (kind == CandidateCharacterKind.KATAKANA || kind == CandidateCharacterKind.HIRAGANA) {
            while (end < text.length && text.codePointAt(end) in 0x3099..0x309a) end++
        }
        if (kind != null) {
            val last = ranges.lastOrNull()
            if (last?.kind == kind && last.end == index) {
                ranges[ranges.lastIndex] = last.copy(end = end)
            } else ranges += CandidateCharacterRange(index, end, kind)
        }
        previous = kind
        index = end
    }
    return ranges
}

internal fun CandidateCharacterKind.label(context: Context): String = context.getString(when (this) {
    CandidateCharacterKind.HIRAGANA -> R.string.candidate_character_hiragana
    CandidateCharacterKind.KATAKANA -> R.string.candidate_character_katakana
    CandidateCharacterKind.FULL_WIDTH -> R.string.candidate_character_full_width
    CandidateCharacterKind.HALF_WIDTH -> R.string.candidate_character_half_width
    CandidateCharacterKind.HALF_KATAKANA -> R.string.candidate_character_half_katakana
})

internal fun characterKindLabels(context: Context, text: String): String {
    val kinds = candidateCharacterRanges(text).map { it.kind }.toSet()
    return CandidateCharacterKind.entries.filter { it != CandidateCharacterKind.HIRAGANA && it in kinds }
        .joinToString("・") { it.label(context) }
}

/** Draw decorations without replacing glyphs or changing the body's width/baseline/ruby layout. */
class CandidateCharacterTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialTextView(context, attrs) {
    private var characterRanges: List<CandidateCharacterRange> = emptyList()
    private var bodyOffset = 0
    private var indicatorSelected = false
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class ResolvedStyle(val line: CandidateIndicatorLineStyle, val color: Int)
    private var resolvedStyles = emptyMap<CandidateCharacterKind, ResolvedStyle>()
    private var resolvedFor: Pair<CandidateIndicatorStyles, Boolean>? = null
    private var indicatorBody = ""
    private var indicatorsEnabled = false
    private var indicatorStyles = CandidateIndicatorStyles()
    private var darkOverride: Boolean? = null

    internal fun setCharacterIndicators(
        body: String, enabled: Boolean, offset: Int = 0, selected: Boolean = false,
        styles: CandidateIndicatorStyles = CandidateIndicatorStyles(), dark: Boolean? = null,
    ) {
        indicatorBody = body
        indicatorsEnabled = enabled
        indicatorStyles = styles
        darkOverride = dark
        val night = dark ?: (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        if (resolvedFor != (styles to night)) {
            resolvedStyles = CandidateCharacterKind.entries.mapNotNull { kind ->
                val group = kind.indicatorGroup() ?: return@mapNotNull null
                val style = styles[group]
                kind to ResolvedStyle(style.line, style.color(context, group, night))
            }.toMap()
            resolvedFor = styles to night
        }
        characterRanges = if (enabled) candidateCharacterRanges(body).filter {
            resolvedStyles[it.kind]?.line?.let { line -> line != CandidateIndicatorLineStyle.NONE } == true
        } else emptyList()
        bodyOffset = offset
        indicatorSelected = selected
        val descriptions = characterRanges.mapNotNull { it.kind.indicatorGroup() }.distinct()
            .joinToString("、") { context.getString(it.titleRes) }
        contentDescription = if (descriptions.isEmpty()) null else "$body、$descriptions"
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resolvedFor = null
        setCharacterIndicators(indicatorBody, indicatorsEnabled, bodyOffset, indicatorSelected, indicatorStyles, darkOverride)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val textLayout = layout ?: return
        val density = resources.displayMetrics.density
        indicatorPaint.style = Paint.Style.STROKE
        indicatorPaint.strokeWidth = density.coerceAtLeast(1f)
        canvas.save()
        canvas.clipRect(compoundPaddingLeft, 0, width - compoundPaddingRight, height)
        canvas.translate((compoundPaddingLeft - scrollX).toFloat(), (extendedPaddingTop - scrollY).toFloat())
        for (range in characterRanges) {
            val style = resolvedStyles[range.kind] ?: continue
            val start = range.start + bodyOffset
            val end = range.end + bodyOffset
            if (start < 0 || end > text.length || start >= end) continue
            for (line in textLayout.getLineForOffset(start)..textLayout.getLineForOffset(end - 1)) {
                val lineStart = maxOf(start, textLayout.getLineStart(line))
                val lineEnd = minOf(end, textLayout.getLineEnd(line))
                val x1 = textLayout.getPrimaryHorizontal(lineStart)
                val x2 = if (lineEnd == textLayout.getLineEnd(line)) textLayout.getLineRight(line) else textLayout.getPrimaryHorizontal(lineEnd)
                val baseline = textLayout.getLineBaseline(line).toFloat()
                val bottom = textLayout.getLineBottom(line) - indicatorPaint.strokeWidth / 2f
                val doubleSpacing = minOf(2f * density, ((bottom - baseline) / 2f).coerceAtLeast(indicatorPaint.strokeWidth))
                val y = minOf(baseline + density * 1.5f,
                    bottom - if (style.line == CandidateIndicatorLineStyle.DOUBLE) doubleSpacing else 0f)
                indicatorPaint.color = if (indicatorSelected) currentTextColor else style.color
                indicatorPaint.pathEffect = when (style.line) {
                    CandidateIndicatorLineStyle.DOTTED -> DashPathEffect(floatArrayOf(density * 1.5f, density * 1.5f), 0f)
                    CandidateIndicatorLineStyle.DASHED -> DashPathEffect(floatArrayOf(density * 5f, density * 3f), 0f)
                    else -> null
                }
                val path = Path().apply { moveTo(minOf(x1, x2), y); lineTo(maxOf(x1, x2), y) }
                canvas.drawPath(path, indicatorPaint)
                if (style.line == CandidateIndicatorLineStyle.DOUBLE) {
                    canvas.drawLine(minOf(x1, x2), y + doubleSpacing, maxOf(x1, x2), y + doubleSpacing, indicatorPaint)
                }
            }
        }
        canvas.restore()
    }
}

private fun CandidateCharacterKind.indicatorGroup(): CandidateIndicatorGroup? = when (this) {
    CandidateCharacterKind.HIRAGANA -> null
    CandidateCharacterKind.KATAKANA -> CandidateIndicatorGroup.KATAKANA
    CandidateCharacterKind.FULL_WIDTH -> CandidateIndicatorGroup.FULL_WIDTH
    CandidateCharacterKind.HALF_WIDTH -> CandidateIndicatorGroup.HALF_WIDTH
    CandidateCharacterKind.HALF_KATAKANA -> CandidateIndicatorGroup.HALF_KATAKANA
}
