package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import android.graphics.Rect
import android.graphics.RectF
import android.view.inputmethod.CursorAnchorInfo
import kotlin.math.roundToInt
import kotlin.math.abs

internal fun isBunsetsuDiffBubbleAdjacent(bubble: Rect, anchor: RectF, gap: Int): Boolean =
    anchor.centerX() >= bubble.left && anchor.centerX() <= bubble.right &&
        minOf(abs(bubble.bottom - anchor.top), abs(bubble.top - anchor.bottom)) <= gap * 2

internal fun bunsetsuDiffAnchors(info: CursorAnchorInfo, snapshot: BunsetsuDiffSnapshot): List<RectF?>? {
    if (info.composingTextStart < 0 || info.composingText?.toString() != snapshot.text) return null
    var offset = 0
    return snapshot.segments.map { segment ->
        val start = offset
        offset += segment.displayText.length
        var bounds: RectF? = null
        var complete = true
        for (index in start until offset) {
            // Android indexes UTF-16; an editor may provide a single box for a surrogate pair.
            if (snapshot.text[index].isLowSurrogate() && index > 0 && snapshot.text[index - 1].isHighSurrogate()) continue
            val absolute = info.composingTextStart + index
            val local = info.getCharacterBounds(absolute)
            val flags = info.getCharacterBoundsFlags(absolute)
            if (local == null || flags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION == 0) {
                complete = false
                continue
            }
            val screen = RectF(local)
            info.matrix.mapRect(screen)
            if (!listOf(screen.left, screen.top, screen.right, screen.bottom).all(Float::isFinite) || screen.isEmpty) {
                complete = false
            } else if (bounds == null) bounds = screen
            else if (screen.top < bounds!!.bottom && screen.bottom > bounds!!.top) bounds?.union(screen)
        }
        bounds.takeIf { complete }
    }
}

/** Stay near the associated text, outside the text and previously placed panels. */
internal fun placeBunsetsuDiffBubble(
    anchor: RectF, width: Int, height: Int, area: Rect, obstacles: List<Rect>, gap: Int,
): Rect? {
    if (area.isEmpty || width <= 0 || height <= 0 || width > area.width() || height > area.height()) return null
    if (!RectF.intersects(anchor, RectF(area))) return null
    val left = (anchor.centerX() - width / 2f).roundToInt().coerceIn(area.left, area.right - width)
    // Use only the immediately adjacent positions. Do not shift to a different segment's row.
    val tops = listOf(anchor.top.toInt() - gap - height, anchor.bottom.toInt() + gap)
    return tops.asSequence().map { Rect(left, it, left + width, it + height) }.firstOrNull { candidate ->
        area.contains(candidate) && isBunsetsuDiffBubbleAdjacent(candidate, anchor, gap) &&
            obstacles.none { Rect.intersects(it, candidate) }
    }
}
