package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.icu.text.BreakIterator
import android.os.SystemClock
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Absolute anchor and moving endpoint shared by arrows, trackpad and the existing selection keys. */
internal class EditorCursorSelection(
    private val connection: () -> InputConnection?,
    private val revision: () -> Long,
    private val selectionRevision: () -> Long,
    private val readMutex: Mutex,
    private val setSelection: (Int, Int) -> Boolean,
) {
    private var anchor: Int? = null
    private var moving: Int? = null

    fun reset() { anchor = null; moving = null }
    fun onSelectionChanged(start: Int, end: Int) {
        val fixed = anchor
        if (fixed != null && (start == fixed || end == fixed)) moving = if (start == fixed) end else start
        else reset()
    }

    suspend fun move(direction: CursorDirection, selecting: Boolean, isCurrent: () -> Boolean = { true }) {
        val editor = connection() ?: return
        var extracted: ExtractedText? = null
        var stable = false
        // The previous queued action's selection callback can arrive during an editor read.
        // Retry that read rather than dropping a completed trackpad step.
        for (attempt in 0 until 3) {
            val beforeRevision = revision()
            val beforeSelection = selectionRevision()
            val value = readMutex.withLockRead {
                runCatching { editor.getExtractedText(ExtractedTextRequest(), 0) }.getOrNull()
            }
            if (connection() !== editor || !isCurrent()) return
            if (revision() == beforeRevision && selectionRevision() == beforeSelection) {
                extracted = value; stable = true; break
            }
        }
        if (!stable) return
        val snapshot = extracted?.let(::snapshot)
        if (snapshot == null) {
            // Let editors that refuse extracted text implement their own selection semantics.
            sendDpad(editor, direction, selecting)
            return
        }
        val (offset, text, start, end) = snapshot
        if (!selecting) {
            if (start != end) {
                val target = if (direction.horizontal) {
                    if (direction == CursorDirection.LEFT) minOf(start, end) else maxOf(start, end)
                } else moving?.takeIf { it == start || it == end } ?: end
                reset()
                if (!setSelection(target, target)) { sendDpad(editor, direction, false); return }
                if (direction.horizontal) return
            }
            reset()
            sendDpad(editor, direction, false)
            return
        }
        val fixed = anchor?.takeIf { it == start || it == end } ?: start
        val active = moving?.takeIf { it == start || it == end } ?: end
        anchor = fixed; moving = active
        if (direction.horizontal) {
            val local = active - offset
            val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
            var next = if (direction == CursorDirection.LEFT) iterator.preceding(local) else iterator.following(local)
            // API 24's ICU predates modern emoji clusters. Keep joiners, modifiers and flags
            // together even when that ICU version exposes an interior boundary.
            while (next != BreakIterator.DONE && mergesEmojiBoundary(text, next)) {
                next = if (direction == CursorDirection.LEFT) iterator.preceding(next) else iterator.following(next)
            }
            if (next == BreakIterator.DONE) {
                // The extracted window can end before the document does. Let the editor
                // cross its edge (or remain at a real document boundary) without guessing offsets.
                if ((start == fixed && end == active) || setSelection(fixed, active)) sendDpad(editor, direction, true)
                return
            }
            moving = offset + next
            if (!setSelection(fixed, offset + next)) { reset(); sendDpad(editor, direction, true) }
        } else {
            // Preserve endpoint order before native Shift+DPAD so the editor moves the active end.
            if (start != fixed || end != active) {
                if (!setSelection(fixed, active)) return
            }
            sendDpad(editor, direction, true)
        }
    }

    private fun mergesEmojiBoundary(text: String, boundary: Int): Boolean {
        if (boundary <= 0 || boundary >= text.length) return false
        val before = text.codePointBefore(boundary)
        val after = text.codePointAt(boundary)
        if (after == 0x200D || after in 0x1F3FB..0x1F3FF || after in 0xFE00..0xFE0F ||
            after in 0xE0100..0xE01EF || after in 0xE0020..0xE007F) return true
        if (before == 0x200D && (after in 0x1F000..0x1FAFF || after in 0x2600..0x27BF ||
                Character.getType(after) == Character.OTHER_SYMBOL.toInt())) return true
        if (before in 0x1F1E6..0x1F1FF && after in 0x1F1E6..0x1F1FF) {
            var count = 0
            var position = boundary
            while (position > 0 && text.codePointBefore(position) in 0x1F1E6..0x1F1FF) {
                position -= Character.charCount(text.codePointBefore(position)); count++
            }
            return count % 2 == 1
        }
        return false
    }

    private data class Snapshot(val offset: Int, val text: String, val start: Int, val end: Int)
    private fun snapshot(extracted: ExtractedText): Snapshot? {
        val text = extracted.text?.toString() ?: return null
        if (extracted.partialStartOffset >= 0 || extracted.startOffset < 0 || extracted.selectionStart !in 0..text.length ||
            extracted.selectionEnd !in 0..text.length) return null
        return Snapshot(extracted.startOffset, text, extracted.startOffset + extracted.selectionStart,
            extracted.startOffset + extracted.selectionEnd)
    }

    private suspend fun <T> Mutex.withLockRead(read: () -> T): T {
        lock()
        try { return withContext(Dispatchers.IO) { read() } } finally { unlock() }
    }

    private fun sendDpad(editor: InputConnection, direction: CursorDirection, shift: Boolean) {
        val time = SystemClock.uptimeMillis()
        fun send(action: Int, code: Int, meta: Int = 0) = editor.sendKeyEvent(KeyEvent(time, time, action, code,
            0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD))
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        if (shift) send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, meta)
        try { send(KeyEvent.ACTION_DOWN, direction.keyCode, meta); send(KeyEvent.ACTION_UP, direction.keyCode, meta) }
        finally { if (shift) send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT) }
    }
}
