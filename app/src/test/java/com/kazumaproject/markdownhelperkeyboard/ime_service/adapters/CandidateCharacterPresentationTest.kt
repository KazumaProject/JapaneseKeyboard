package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateCharacterPresentationTest {
    private val context: Context get() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return android.view.ContextThemeWrapper(
            base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.JAPANESE) }),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard
        )
    }

    @Test fun mixedTextIsClassifiedByCharactersRegardlessOfCandidateType() {
        val text = "東京でカフェ１２３ABC"
        assertEquals(listOf("で", "カフェ", "１２３", "ABC"), candidateCharacterRanges(text).map { text.substring(it.start, it.end) })
        assertEquals("カナ・全・半", characterKindLabels(context, text))
        for (type in listOf(1, 4, 28, 31, 34)) {
            val candidate = Candidate(text, type.toByte(), 10u, 0)
            assertFalse(candidateLabelPresentation(context, candidate, false, true).toString().contains("カナ・全・半"))
        }
    }

    @Test fun kanaMarksAndKeycapEmojiDoNotSplitOrMisclassify() {
        val halfKana = "ｶﾞｯﾂﾎﾟｰｽﾞ"
        assertEquals(listOf(CandidateCharacterRange(0, halfKana.length, CandidateCharacterKind.HALF_KATAKANA)), candidateCharacterRanges(halfKana))
        assertEquals(1, candidateCharacterRanges("カ\u3099フェー").size)
        assertTrue(candidateCharacterRanges("😀1️⃣").isEmpty())
        assertEquals("", characterKindLabels(context, "ひらがなー漢字😀"))
        assertEquals("半カナ", characterKindLabels(context, "ｶﾞｯﾂﾎﾟｰｽﾞ"))
    }

    @Test fun widthLabelAndOriginAreNotDuplicatedAndTaxonomyStillAppears() {
        val candidate = Candidate("ＡＢＣ", CANDIDATE_TYPE_HALF_WIDTH, 3u, 0,
            dictionaryOrigins = setOf(CandidateDictionaryOrigin.LEARNED_DICTIONARY),
            dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PLACE))
        assertEquals("[学習] [地名]", candidateLabelPresentation(context, candidate, true, true).toString())
        assertEquals("[半] [学習] [地名]", candidateLabelPresentation(context, candidate, true, false).toString())
        assertEquals("[全]", candidateLabelPresentation(context, Candidate("１２３４", 13, 4u, 0), false, false).toString())
        assertEquals("[日付]", candidateLabelPresentation(context, Candidate("12月31日", CANDIDATE_TYPE_DIGIT_DATE, 4u, 0), false, false).toString())
        assertEquals("[AI]", candidateLabelPresentation(context, Candidate("AI候補", CANDIDATE_TYPE_ZENZ_REGENERATED, 4u, 0), false, false).toString())
        assertEquals("[AI]", candidateLabelPresentation(context, Candidate("生成結果", CANDIDATE_TYPE_ZENZ_WHOLE_RESULT, 4u, 0), false, false).toString())
        for (type in listOf(CANDIDATE_TYPE_ZENZ_PASS, CANDIDATE_TYPE_ZENZ_LOCAL_FALLBACK, CANDIDATE_TYPE_ZENZ_ERROR_FALLBACK)) {
            assertEquals("", candidateLabelPresentation(context, Candidate("辞書候補", type, 4u, 0), false, false).toString())
        }
    }

    @Test fun indicatorsActuallyDrawWithoutMovingTextAndDisappearWhenDisabled() {
        for (size in listOf(8f, 14f, 28f)) {
            val view = CandidateCharacterTextView(context).apply {
                text = "カナ１２ABC"
                textSize = size
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(android.graphics.Color.BLACK)
            }
            fun measure() {
                val spec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                view.measure(spec, spec)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
            fun pixels(): Set<Int> {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
                return pixels.toSet()
            }
            measure()
            val metrics = listOf(view.width, view.height, view.baseline)
            view.setCharacterIndicators(view.text.toString(), true)
            measure()
            assertEquals(metrics, listOf(view.width, view.height, view.baseline))
            val enabled = pixels()
            val colors = listOf(
                com.kazumaproject.markdownhelperkeyboard.R.color.candidate_character_katakana,
                com.kazumaproject.markdownhelperkeyboard.R.color.candidate_character_full_width,
                com.kazumaproject.markdownhelperkeyboard.R.color.candidate_character_half_width,
            ).map { context.getColor(it) }
            colors.forEach { assertTrue("Indicator missing at $size sp", it in enabled) }
            view.setCharacterIndicators(view.text.toString(), false)
            val disabled = pixels()
            colors.forEach { assertFalse(it in disabled) }
        }
    }

    @Test fun floatingRowsCanToggleLabelsWithoutRequeryingCandidates() {
        val adapter = FloatingCandidateListAdapter(5)
        adapter.submitList(listOf(com.kazumaproject.core.data.floating_candidate.CandidateItem(
            "カナ１２ABC", 3u, label = "source", characterLabel = "source"
        )))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(context), adapter.getItemViewType(0))
        val label = holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.floating_candidate_label)
        for (enabled in listOf(false, true, false)) {
            adapter.setShowCharacterIndicators(enabled)
            adapter.onBindViewHolder(holder, 0)
            assertEquals("source", label.text.toString())
            val body = holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.text_view_item)
            assertEquals(enabled, body.contentDescription != null)
        }
    }
}
