package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TenkeyPunctuationSizingTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MarkdownKeyboard)

    @Test fun punctuationGuidesUseTheSelectedSizeWithEitherGuideVisibility() = withKeyboard { keyboard, key ->
        keyboard.setModeLetterSizes(mapOf(InputMode.ModeJapanese to 24f), mapOf(InputMode.ModeJapanese to 16f))
        for (enabled in listOf(true, false)) {
            keyboard.setFlickGuideEnabled(enabled, false, false)
            assertEquals(24f, glyphSp(key, ','), .01f)
            for (glyph in listOf('？', '。', '!', '…')) assertEquals(16f, glyphSp(key, glyph), .6f)
        }
        keyboard.setCurrentMode(InputMode.ModeEnglish)
        keyboard.setCurrentMode(InputMode.ModeJapanese)
        assertEquals(24f, glyphSp(key, ','), .01f)
        assertEquals(16f, glyphSp(key, '？'), .6f)
    }

    @Test fun guideOnlyOverridePreservesTheInheritedCenterAndResetRestoresLegacyLabels() = withKeyboard { keyboard, key ->
        keyboard.setFlickGuideEnabled(true, false, false)
        val center = glyphSp(key, ',')
        val originalGuides = listOf('？', '。', '!', '…').associateWith { glyphSp(key, it) }
        keyboard.setModeLetterSizes(emptyMap(), mapOf(InputMode.ModeJapanese to 16f))
        assertEquals(center, glyphSp(key, ','), .01f)
        originalGuides.keys.forEach { assertEquals(16f, glyphSp(key, it), .6f) }
        keyboard.setModeLetterSizes(emptyMap(), emptyMap())
        assertEquals(center, glyphSp(key, ','), .01f)
        originalGuides.forEach { (glyph, size) -> assertEquals(size, glyphSp(key, glyph), .01f) }
    }

    @Test fun centerOnlyOverrideUsesEveryInheritedGuideSize() = withKeyboard { keyboard, key ->
        for (enabled in listOf(false, true)) {
            keyboard.setModeLetterSizes(emptyMap(), emptyMap())
            keyboard.setFlickGuideEnabled(enabled, false, false)
            val guides = listOf('？', '。', '!', '…').associateWith { glyphSp(key, it) }
            keyboard.setModeLetterSizes(mapOf(InputMode.ModeJapanese to 24f), emptyMap())
            assertEquals(24f, glyphSp(key, ','), .01f)
            guides.forEach { (glyph, size) -> assertEquals("Inherited $glyph changed", size, glyphSp(key, glyph), .01f) }
        }
    }

    private fun withKeyboard(block: (TenKey, TextView) -> Unit) {
        val keyboard = LayoutInflater.from(context).inflate(R.layout.fragment_tenkey_candidate_letter_size, null)
            .findViewById<TenKey>(R.id.tenkeyLetterSizePreview)
        try {
            keyboard.setCurrentMode(InputMode.ModeJapanese)
            block(keyboard, keyboard.findViewById(com.kazumaproject.tenkey.R.id.key_12))
        } finally { keyboard.cancelTenKeyScope() }
    }

    private fun glyphSp(key: TextView, glyph: Char): Float {
        val label = key.text as Spanned
        val start = label.indexOf(glyph)
        val paint = TextPaint().apply { textSize = key.textSize }
        label.getSpans(start, start + 1, MetricAffectingSpan::class.java).forEach { it.updateMeasureState(paint) }
        return paint.textSize / key.resources.displayMetrics.scaledDensity
    }
}
