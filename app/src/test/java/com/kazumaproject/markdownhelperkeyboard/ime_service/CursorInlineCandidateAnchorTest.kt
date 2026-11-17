package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.graphics.Color
import android.graphics.Matrix
import android.view.inputmethod.CursorAnchorInfo
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CursorInlineCandidateAnchorTest {
    @Test fun preeditFollowsTheInsertionMarkerAndEditorScroll() {
        val first = info(caretX = 20f, scrollY = 0f).inlineCandidateAnchor(TEXT, null)
        val moved = info(caretX = 75f, scrollY = -24f).inlineCandidateAnchor(TEXT, null)
        assertEquals(PhysicalKeyboardCursorAnchor(60f, 140f, 170f, false), first)
        assertEquals(PhysicalKeyboardCursorAnchor(115f, 116f, 146f, false), moved)
    }

    @Test fun focusedPhraseUsesItsOwnVisibleCharacterBounds() {
        val anchor = info(caretX = 75f, scrollY = 0f, withCharacters = true)
        assertEquals(PhysicalKeyboardCursorAnchor(52f, 110f, 140f, false),
            anchor.inlineCandidateAnchor(TEXT, 0..2))
        assertEquals(PhysicalKeyboardCursorAnchor(62f, 140f, 170f, false),
            anchor.inlineCandidateAnchor(TEXT, 3..5))
    }

    @Test fun staleCompositionAndUnavailablePhraseGeometryKeepTheKeyboardStrip() {
        val anchor = info(caretX = 75f, scrollY = 0f)
        assertNull(anchor.inlineCandidateAnchor("別の文章", null))
        assertNull(anchor.inlineCandidateAnchor(TEXT, 0..2))
    }

    @Test fun selectionKeepsTheThemeAccentAndUsesReadableText() {
        for ((background, foreground) in listOf(Color.WHITE to Color.BLACK, Color.BLACK to Color.WHITE)) {
            val theme = CandidatePanelColors(
                background = background,
                candidate = background,
                text = foreground,
                pressed = foreground,
                selection = ColorUtils.blendARGB(background, foreground, 0.08f),
                selectionText = background,
            )
            val colors = theme.withReadableSelectionText()
            assertEquals(theme.selection, colors.selection)
            assertTrue(ColorUtils.calculateContrast(colors.selectionText, colors.selection) >= 4.5)

            val readableTheme = theme.copy(selectionText = foreground)
            assertEquals(readableTheme, readableTheme.withReadableSelectionText())
        }
    }

    private fun info(caretX: Float, scrollY: Float, withCharacters: Boolean = false): CursorAnchorInfo =
        CursorAnchorInfo.Builder().apply {
            setSelectionRange(16, 16)
            setComposingText(10, TEXT)
            setInsertionMarkerLocation(caretX, 80f, 103f, 110f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
            setMatrix(Matrix().apply { setTranslate(40f, 60f + scrollY) })
            if (withCharacters) {
                addCharacterBounds(10, 0f, 20f, 12f, 50f, CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION)
                addCharacterBounds(11, 12f, 50f, 24f, 80f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
                addCharacterBounds(13, 22f, 80f, 44f, 110f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
            }
        }.build()

    private companion object {
        const val TEXT = "明日は東京に"
    }
}
