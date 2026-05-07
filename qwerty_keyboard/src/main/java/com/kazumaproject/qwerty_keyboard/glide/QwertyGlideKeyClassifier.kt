package com.kazumaproject.qwerty_keyboard.glide

/**
 * Pure helper for classifying a touch coordinate against the QWERTY layout from the
 * point of view of the Glide gesture pipeline.
 *
 * The Glide pipeline must distinguish three cases for a given (x, y):
 *  1. The coordinate falls *exactly* on a Glide-eligible alphabet key. The Glide
 *     gesture uses such points for trail / decoder input.
 *  2. The coordinate falls *exactly* on a visible QWERTY key that is **not** a
 *     Glide-eligible alphabet key (numbers, space, return, cursor, shift,
 *     delete, mode switch keys, emoji, etc.). When this happens for a
 *     candidate / active Glide pointer we want to ignore the point entirely:
 *     neither contributing it to the trail nor letting normal key dispatch fire.
 *  3. The coordinate is in the keyboard background (key gaps, padding, etc.).
 *     Candidate / active Glide MOVE events still consume these points so they
 *     cannot rewrite normal key pointer state.
 *
 * Keeping this logic in a small, view-free helper makes it cheap to unit test
 * without spinning up a real `QWERTYKeyboardView` instance.
 */
object QwertyGlideKeyClassifier {
    enum class KeyHit {
        LETTER,
        NON_GLIDE_KEY,
        NONE
    }

    enum class MoveHandling {
        ROUTE_TO_NORMAL_KEY_HANDLER,
        APPEND_TO_GLIDE_PATH,
        IGNORE_AND_CONSUME
    }

    /**
     * Lightweight rectangle representation used by the classifier. Mirrors the
     * `View.getHitRect()` semantics: the rect contains a point when
     * `left <= x < right && top <= y < bottom`.
     */
    data class KeyRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun contains(x: Int, y: Int): Boolean =
            x in left until right && y in top until bottom
    }

    /**
     * Returns true if (x, y) falls exactly inside one of the supplied
     * Glide-eligible alphabet key hit rects.
     *
     * Note: This is *not* a nearest-neighbor lookup. We deliberately avoid the
     * fallback that `findButtonUnder()` performs, because letting a coordinate
     * inside the number row "snap" to the closest letter key would defeat the
     * purpose of ignoring number rows during Glide.
     */
    fun isOnLetterKey(letterRects: List<KeyRect>, x: Int, y: Int): Boolean {
        return letterRects.any { it.contains(x, y) }
    }

    /**
     * Returns true if (x, y) is exactly inside a visible non-letter QWERTY key.
     *
     * Used by the Glide pipeline to decide whether a moving pointer is currently
     * sitting on a non-Glide key (number / space / return / cursor / shift /
     * delete / mode switch / emoji / etc.) so the event can be silently
     * consumed.
     */
    fun isOnNonGlideKey(
        letterRects: List<KeyRect>,
        nonLetterRects: List<KeyRect>,
        x: Int,
        y: Int
    ): Boolean {
        if (isOnLetterKey(letterRects, x, y)) return false
        return nonLetterRects.any { it.contains(x, y) }
    }

    fun classify(
        letterRects: List<KeyRect>,
        nonLetterRects: List<KeyRect>,
        x: Int,
        y: Int
    ): KeyHit {
        return when {
            isOnLetterKey(letterRects, x, y) -> KeyHit.LETTER
            isOnNonGlideKey(letterRects, nonLetterRects, x, y) -> KeyHit.NON_GLIDE_KEY
            else -> KeyHit.NONE
        }
    }

    /**
     * Decides whether an ACTION_MOVE for [pointerId] is owned by the Glide
     * pipeline. A candidate / active Glide pointer must never fall through to
     * normal key MOVE handling, because that path rewrites pointerButtonMap to
     * whatever non-letter key the finger crosses.
     */
    fun decideMoveHandling(
        glidePointerId: Int?,
        pointerId: Int,
        keyHit: KeyHit
    ): MoveHandling {
        if (glidePointerId != pointerId) return MoveHandling.ROUTE_TO_NORMAL_KEY_HANDLER
        return when (keyHit) {
            KeyHit.LETTER -> MoveHandling.APPEND_TO_GLIDE_PATH
            KeyHit.NON_GLIDE_KEY,
            KeyHit.NONE -> MoveHandling.IGNORE_AND_CONSUME
        }
    }
}
