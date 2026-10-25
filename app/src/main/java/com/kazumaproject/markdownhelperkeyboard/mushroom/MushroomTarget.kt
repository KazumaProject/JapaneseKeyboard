package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * One unchanged editor snapshot; the caller must verify editor identity before allowing a reconnect.
 * Source text stays in memory only.
 */
internal class MushroomTarget private constructor(
    private val connection: InputConnection,
    private val snapshot: String,
    private val offset: Int,
    private val selectionStart: Int,
    private val selectionEnd: Int,
    private val start: Int,
    private val end: Int,
    val source: String,
) {
    private var consumed = false

    fun apply(
        current: InputConnection?,
        result: String,
        sameEditorAfterRestart: Boolean = false,
        commitResult: (String) -> Boolean = { current?.commitText(it, 1) == true },
    ): Boolean {
        if (consumed) return false
        consumed = true
        if (current == null || (current !== connection && !sameEditorAfterRestart)) return false
        val text = current.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        if (text.text?.toString() != snapshot || text.startOffset != offset ||
            text.selectionStart != selectionStart || text.selectionEnd != selectionEnd) return false
        current.beginBatchEdit()
        return try {
            if (!current.setSelection(offset + start, offset + end)) return false
            val committed = commitResult(result)
            if (!committed) current.setSelection(offset + selectionStart, offset + selectionEnd)
            committed
        } finally {
            current.endBatchEdit()
        }
    }

    companion object {
        fun capture(
            connection: InputConnection,
            preedit: String,
            composingRange: IntRange? = null,
            displayedPreedit: String = preedit,
        ): MushroomTarget? {
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
                flags = InputConnection.GET_TEXT_WITH_STYLES
            }, 0) ?: return null
            val styled = extracted.text ?: return null
            val text = styled.toString()
            val selStart = extracted.selectionStart
            val selEnd = extracted.selectionEnd
            if (selStart !in 0..text.length || selEnd !in 0..text.length) return null
            var start = minOf(selStart, selEnd)
            var end = maxOf(selStart, selEnd)
            val selected = start != end
            if (!selected && preedit.isNotEmpty()) {
                val composingStart = (styled as? android.text.Spannable)?.let(BaseInputConnection::getComposingSpanStart) ?: -1
                val composingEnd = (styled as? android.text.Spannable)?.let(BaseInputConnection::getComposingSpanEnd) ?: -1
                if (composingStart >= 0 && composingEnd in composingStart..text.length) {
                    start = composingStart
                    end = composingEnd
                } else if (composingRange != null &&
                    composingRange.first - extracted.startOffset in 0..selStart &&
                    composingRange.last - extracted.startOffset in selStart..text.length &&
                    composingRange.last > composingRange.first &&
                    text.substring(composingRange.first - extracted.startOffset,
                        composingRange.last - extracted.startOffset) == displayedPreedit) {
                    start = composingRange.first - extracted.startOffset
                    end = composingRange.last - extracted.startOffset
                } else {
                    // Some editors omit composing spans from extracted text.
                    start = selStart - preedit.length
                    if (start < 0 || text.substring(start, end) != preedit) return null
                }
            }
            return MushroomTarget(connection, text, extracted.startOffset, selStart, selEnd,
                start, end, if (!selected && preedit.isNotEmpty()) preedit else text.substring(start, end))
        }
    }
}
