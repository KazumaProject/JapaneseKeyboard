package com.kazumaproject.qwerty_keyboard.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideKeyClassifier.KeyRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挙動の核となる「Glide 中の非アルファベットキー通過を識別する」判定ロジックの
 * ユニットテスト。
 *
 * `QWERTYKeyboardView` 自体は `View` ライフサイクル / レイアウト計測 / view binding
 * を要するため Robolectric 無しでは生成できないが、このクラスの挙動の中心は
 * 純粋な矩形判定であり、`QwertyGlideKeyClassifier` に切り出してテストできる。
 */
class QwertyGlideKeyClassifierTest {

    /**
     * 標準的な英語 QWERTY 配列を模した矩形セットを用意するヘルパー。
     *  - top row    : Q W E R T Y U I O P  (y =   0 ..  100)
     *  - mid row    : A S D F G H J K L     (y = 100 ..  200)
     *  - bottom row : Z X C V B N M         (y = 200 ..  300)
     *  - number row : 1 2 3 4 5 6 7 8 9 0   (y = -100 ..   0)  ← Glide 対象外
     *  - bottom func: shift / space / return (y = 300 .. 400)  ← Glide 対象外
     */
    private data class Layout(
        val letterRects: List<KeyRect>,
        val nonLetterRects: List<KeyRect>
    )

    private fun stdLayout(): Layout {
        val keyW = 100
        val keyH = 100
        // Letter rows
        val letters = mutableListOf<KeyRect>()
        for (col in 0 until 10) {
            // top row
            letters += KeyRect(col * keyW, 0, (col + 1) * keyW, keyH)
        }
        for (col in 0 until 9) {
            // mid row
            letters += KeyRect(col * keyW + 50, keyH, (col + 1) * keyW + 50, 2 * keyH)
        }
        for (col in 0 until 7) {
            // bottom row
            letters += KeyRect(col * keyW + 100, 2 * keyH, (col + 1) * keyW + 100, 3 * keyH)
        }
        // Non-letter QWERTY keys
        val nonLetters = mutableListOf<KeyRect>()
        // Number row above the letters (key_1 .. key_0)
        for (col in 0 until 10) {
            nonLetters += KeyRect(col * keyW, -keyH, (col + 1) * keyW, 0)
        }
        // Side keys
        nonLetters += KeyRect(0, 2 * keyH, 100, 3 * keyH)            // Shift
        nonLetters += KeyRect(800, 2 * keyH, 1000, 3 * keyH)         // Delete
        // Bottom utility row: 123, switchDefault, space, return, cursors, emoji
        nonLetters += KeyRect(0, 3 * keyH, 150, 4 * keyH)            // 123
        nonLetters += KeyRect(150, 3 * keyH, 300, 4 * keyH)          // switchDefault / emoji
        nonLetters += KeyRect(300, 3 * keyH, 700, 4 * keyH)          // space
        nonLetters += KeyRect(700, 3 * keyH, 850, 4 * keyH)          // cursorLeft / cursorRight
        nonLetters += KeyRect(850, 3 * keyH, 1000, 4 * keyH)         // return
        return Layout(letters, nonLetters)
    }

    @Test
    fun coordinateOnLetterKeyIsClassifiedAsLetter() {
        val layout = stdLayout()
        // Center of "Q" at (50, 50) is a letter coordinate.
        assertTrue(QwertyGlideKeyClassifier.isOnLetterKey(layout.letterRects, 50, 50))
        assertFalse(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 50,
                y = 50
            )
        )
    }

    @Test
    fun coordinateOnNumberKeyIsClassifiedAsNonGlide() {
        val layout = stdLayout()
        // Center of "1" at (50, -50)
        assertFalse(QwertyGlideKeyClassifier.isOnLetterKey(layout.letterRects, 50, -50))
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 50,
                y = -50
            )
        )
    }

    @Test
    fun coordinateOnSpaceIsClassifiedAsNonGlide() {
        val layout = stdLayout()
        // Center of "space" at (500, 350)
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 500,
                y = 350
            )
        )
    }

    @Test
    fun coordinateOnReturnIsClassifiedAsNonGlide() {
        val layout = stdLayout()
        // Inside "return" at (900, 350)
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 900,
                y = 350
            )
        )
    }

    @Test
    fun coordinateOnCursorKeyIsClassifiedAsNonGlide() {
        val layout = stdLayout()
        // Inside "cursorLeft / cursorRight" at (750, 350)
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 750,
                y = 350
            )
        )
    }

    @Test
    fun coordinateOnShiftIsClassifiedAsNonGlide() {
        val layout = stdLayout()
        // Inside "Shift" at (50, 250)
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 50,
                y = 250
            )
        )
    }

    @Test
    fun coordinateOnDeleteIsClassifiedAsNonGlide() {
        val layout = stdLayout()
        // Inside "Delete" at (900, 250)
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 900,
                y = 250
            )
        )
    }

    @Test
    fun coordinateInBackgroundIsNeitherLetterNorNonGlideKey() {
        // Use a layout with deliberate gaps between rects.
        val layout = Layout(
            letterRects = listOf(KeyRect(0, 0, 100, 100)),
            nonLetterRects = listOf(KeyRect(200, 0, 300, 100))
        )
        // (150, 50) is in the gap between the two rects.
        assertFalse(QwertyGlideKeyClassifier.isOnLetterKey(layout.letterRects, 150, 50))
        assertFalse(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = layout.letterRects,
                nonLetterRects = layout.nonLetterRects,
                x = 150,
                y = 50
            )
        )
    }

    @Test
    fun lettersTakePrecedenceOverOverlappingNonLetterRect() {
        // Some keyboards may have a non-letter rect that visually overlaps the
        // letter row by a few pixels (e.g. extended hit-target). The classifier
        // should still report letter-first so an in-progress glide on a letter
        // key keeps contributing to the trail.
        val letter = KeyRect(0, 0, 100, 100)
        val overlap = KeyRect(0, 0, 100, 100)

        // Same rect for both — letter must win.
        assertTrue(QwertyGlideKeyClassifier.isOnLetterKey(listOf(letter), 50, 50))
        assertFalse(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = listOf(letter),
                nonLetterRects = listOf(overlap),
                x = 50,
                y = 50
            )
        )
    }

    @Test
    fun noNearestNeighborFallback() {
        // Important: a number-row coordinate that is *closer* to a letter key
        // than to its own non-letter rect must NOT be reclassified as a letter.
        // This guards against a regression where someone replaces the helper
        // with a nearest-neighbor lookup.
        val letter = KeyRect(0, 0, 100, 100)
        val numberKey = KeyRect(0, -100, 100, 0)
        // (50, -1) is one pixel above the letter rect (still inside the number
        // rect under our half-open semantics: -100 .. 0).
        assertFalse(QwertyGlideKeyClassifier.isOnLetterKey(listOf(letter), 50, -1))
        assertTrue(
            QwertyGlideKeyClassifier.isOnNonGlideKey(
                letterRects = listOf(letter),
                nonLetterRects = listOf(numberKey),
                x = 50,
                y = -1
            )
        )
    }
}
