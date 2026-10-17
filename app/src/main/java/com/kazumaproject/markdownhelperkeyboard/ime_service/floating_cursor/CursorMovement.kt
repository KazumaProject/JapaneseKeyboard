package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.icu.text.BreakIterator
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

internal enum class CursorDirection(val keyCode: Int) {
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT), UP(KeyEvent.KEYCODE_DPAD_UP),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT), DOWN(KeyEvent.KEYCODE_DPAD_DOWN);
    val towardsStart get() = this == LEFT || this == UP
}

/** Residual distance belongs to one touch gesture, never to the next gesture. */
internal class CursorPadGesture(private val step: Float, private val move: (CursorDirection) -> Unit) {
    init { require(step.isFinite() && step > 0) }
    private var x = 0f
    private var y = 0f
    private var dx = 0f
    private var dy = 0f
    fun begin(x: Float, y: Float) { this.x = x; this.y = y; dx = 0f; dy = 0f }
    fun move(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        dx += x - this.x; dy += y - this.y
        this.x = x; this.y = y
        while (abs(dx) >= step || abs(dy) >= step) {
            if (abs(dx) >= abs(dy)) {
                val direction = if (dx < 0) CursorDirection.LEFT else CursorDirection.RIGHT
                dx += if (dx < 0) step else -step
                move(direction)
            } else {
                val direction = if (dy < 0) CursorDirection.UP else CursorDirection.DOWN
                dy += if (dy < 0) step else -step
                move(direction)
            }
        }
    }
}

/** Read remote editor text off-main; apply only to the same, still-current input operation. */
internal class CursorMovement(
    private val readMutex: Mutex,
    private val setSelection: (InputConnection, Int, Int) -> Boolean,
    private val sendKey: (InputConnection, KeyEvent) -> Boolean,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val selectionRevision: () -> Long = { 0L },
    private val latestSelection: () -> Pair<Int, Int>? = { null },
) {
    private var fixedEnd: Int? = null
    private var movingEnd: Int? = null
    fun reset() { fixedEnd = null; movingEnd = null }

    suspend fun move(editor: InputConnection, direction: CursorDirection, selecting: Boolean, current: () -> Boolean) {
        val (extracted, selectionVersion) = readMutex.withLock {
            val version = selectionRevision()
            val text = withContext(readDispatcher) { runCatching { editor.getExtractedText(ExtractedTextRequest(), 0) }.getOrNull() }
            text to version
        }
        if (!current()) return
        val snapshot = extracted?.validSnapshot()
        val stillCurrent = {
            current() && (selectionRevision() == selectionVersion ||
                (snapshot != null && latestSelection() == (snapshot.start to snapshot.end)))
        }
        // A delayed notification for our preceding move is safe only when its actual
        // endpoints agree with this read. A different external selection invalidates it.
        if (!stillCurrent()) return
        if (snapshot == null) {
            reset()
            nativeMove(editor, direction, selecting, stillCurrent)
            return
        }
        val (text, offset, start, end) = snapshot
        if (!selecting && start != end) {
            reset()
            val collapsed = if (direction.towardsStart) minOf(start, end) else maxOf(start, end)
            if (!setSelection(editor, collapsed, collapsed) && stillCurrent()) nativeMove(editor, direction, false, stillCurrent)
            return
        }
        if (direction == CursorDirection.UP || direction == CursorDirection.DOWN) {
            nativeMove(editor, direction, selecting, stillCurrent)
            // The next read establishes the editor's resulting endpoints after its own line navigation.
            reset()
            return
        }
        val previousFixed = fixedEnd
        val previousMoving = movingEnd
        val matches = previousFixed != null && previousMoving != null &&
            minOf(previousFixed, previousMoving) == minOf(start, end) &&
            maxOf(previousFixed, previousMoving) == maxOf(start, end)
        val anchor = if (selecting && matches) requireNotNull(previousFixed) else start
        val caret = if (selecting && matches) requireNotNull(previousMoving) else end
        val local = caret - offset
        // Extraction can contain only part of a document. Its boundary is not a document boundary.
        if (local !in 0..text.length || (direction == CursorDirection.LEFT && local == 0) ||
            (direction == CursorDirection.RIGHT && local == text.length)
        ) {
            reset()
            nativeMove(editor, direction, selecting, stillCurrent)
            return
        }
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        var boundary = if (direction == CursorDirection.LEFT) iterator.preceding(local) else iterator.following(local)
        // API 24's ICU splits some emoji sequences. Coalesce their internal boundaries
        // while retaining ICU's normal boundaries for letters, scripts and line breaks.
        while (boundary in 1 until text.length && isEmojiInternalBoundary(text, boundary)) {
            boundary = if (direction == CursorDirection.LEFT) iterator.preceding(boundary) else iterator.following(boundary)
        }
        if (boundary == BreakIterator.DONE || !stillCurrent()) return
        val target = offset + boundary
        val fixed = if (selecting) anchor else target
        if (setSelection(editor, fixed, target)) {
            fixedEnd = fixed; movingEnd = target
        } else if (stillCurrent()) {
            reset()
            nativeMove(editor, direction, selecting, stillCurrent)
        }
    }

    private fun nativeMove(editor: InputConnection, direction: CursorDirection, selecting: Boolean, current: () -> Boolean) {
        if (!current()) return
        val meta = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val downTime = SystemClock.uptimeMillis()
        fun key(action: Int, code: Int, state: Int = meta) = KeyEvent(downTime,
            SystemClock.uptimeMillis(), action, code, 0, state, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD)
        // Native EditText's ArrowKeyMovementMethod reads SHIFT from its Editable's
        // meta-key spans, so event metadata alone does not extend a vertical selection.
        if (selecting) sendKey(editor, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT))
        try {
            sendKey(editor, key(KeyEvent.ACTION_DOWN, direction.keyCode))
        } finally {
            // Release both keys even if selection callbacks invalidate this operation.
            sendKey(editor, key(KeyEvent.ACTION_UP, direction.keyCode))
            if (selecting) sendKey(editor, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0))
        }
    }

    private data class Snapshot(val text: String, val offset: Int, val start: Int, val end: Int)
    private fun isEmojiInternalBoundary(text: String, at: Int): Boolean {
        val before = text.codePointBefore(at)
        val after = text.codePointAt(at)
        if (Character.getType(before) in setOf(Character.CONTROL.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt())) return false
        if (after == 0x200D || after in 0x1F3FB..0x1F3FF || after in 0xE0020..0xE007F) return true
        if (before == 0x200D && pictographic(after)) {
            var previous = at - 1
            while (previous > 0) {
                val cp = text.codePointBefore(previous)
                if (cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()) {
                    previous -= Character.charCount(cp)
                } else return pictographic(cp)
            }
        }
        if (before in 0x1F1E6..0x1F1FF && after in 0x1F1E6..0x1F1FF) {
            var count = 0; var previous = at
            while (previous > 0 && text.codePointBefore(previous) in 0x1F1E6..0x1F1FF) {
                previous -= Character.charCount(text.codePointBefore(previous)); count++
            }
            return count % 2 == 1
        }
        return false
    }
    private fun pictographic(cp: Int): Boolean = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF ||
        cp in setOf(0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139, 0x3030, 0x303D, 0x3297, 0x3299)

    private fun ExtractedText.validSnapshot(): Snapshot? {
        val text = text?.toString() ?: return null
        if (startOffset < 0 || partialStartOffset >= 0 || selectionStart !in 0..text.length || selectionEnd !in 0..text.length) return null
        return Snapshot(text, startOffset, startOffset + selectionStart, startOffset + selectionEnd)
    }
}
