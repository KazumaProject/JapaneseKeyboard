package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
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
        assertEquals("", candidateLabelPresentation(context, Candidate(text, 1, 10u, 0), false, true).toString())
        assertEquals("[カナ]", candidateLabelPresentation(context, Candidate(text, 4, 10u, 0), false, true).toString())
        assertEquals("[半]", candidateLabelPresentation(context, Candidate(text, 31, 10u, 0), false, true).toString())
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
        assertEquals("[半] [学習] [地名]", candidateLabelPresentation(context, candidate, true, true).toString())
        assertEquals("[学習] [地名]", candidateLabelPresentation(context, candidate, true, false).toString())
        assertEquals("[全]", candidateLabelPresentation(context, Candidate("１２３４", 13, 4u, 0).withSymbolWidthType(), false, true).toString())
        assertEquals("[日付]", candidateLabelPresentation(context, Candidate("12月31日", CANDIDATE_TYPE_DIGIT_DATE, 4u, 0), false, false).toString())
        assertEquals("[AI]", candidateLabelPresentation(context, Candidate("AI候補", CANDIDATE_TYPE_ZENZ_REGENERATED, 4u, 0), false, false).toString())
        assertEquals("[AI]", candidateLabelPresentation(context, Candidate("生成結果", CANDIDATE_TYPE_ZENZ_WHOLE_RESULT, 4u, 0), false, false).toString())
        for (type in listOf(CANDIDATE_TYPE_ZENZ_PASS, CANDIDATE_TYPE_ZENZ_LOCAL_FALLBACK, CANDIDATE_TYPE_ZENZ_ERROR_FALLBACK)) {
            assertEquals("", candidateLabelPresentation(context, Candidate("辞書候補", type, 4u, 0), false, false).toString())
        }
    }

    @Test fun labelsFollowCandidateTypeRatherThanText() {
        val cases = listOf(
            Triple("ぺ", CANDIDATE_TYPE_KANA_LITERAL, ""),
            Triple("ペ", CANDIDATE_TYPE_KATAKANA, "[カナ]"),
            Triple("ﾍﾟ", CANDIDATE_TYPE_HALF_WIDTH_KATAKANA, "[半カナ]"),
            Triple("ＡＢＣ", CANDIDATE_TYPE_FULL_WIDTH, "[全]"),
            Triple("ABC", CANDIDATE_TYPE_HALF_WIDTH, "[半]"),
            Triple("東京でペン", CANDIDATE_TYPE_CONVERSION, ""),
            Triple("東京ﾍﾟ", CANDIDATE_TYPE_HALF_WIDTH_KATAKANA, "[半カナ]"),
            Triple("ペ", CANDIDATE_TYPE_CONVERSION, ""),
            Triple("！", CANDIDATE_TYPE_FULL_WIDTH_SYMBOL, "[全]"),
            Triple("!", CANDIDATE_TYPE_HALF_WIDTH_SYMBOL, "[半]"),
            Triple("１２", CANDIDATE_TYPE_FULL_WIDTH_NUMBER_SYMBOL, "[全]"),
            Triple("12", CANDIDATE_TYPE_HALF_WIDTH_NUMBER_SYMBOL, "[半]"),
            Triple("①", CANDIDATE_TYPE_NUMBER_SYMBOL, ""),
        )
        for ((text, type, expected) in cases) {
            val candidate = Candidate(text, type, 1u, 0)
            assertEquals("$text/$type", expected, candidateLabelPresentation(context, candidate, false, true).toString())
            assertEquals("$text/$type disabled", "", candidateLabelPresentation(context, candidate, false, false).toString())
        }
    }

    @Test fun characterTypeLabelsUseTheSameJapaneseTextInJapaneseAndEnglish() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cases = listOf(
            CANDIDATE_TYPE_KATAKANA to "[カナ]",
            CANDIDATE_TYPE_HALF_WIDTH_KATAKANA to "[半カナ]",
            CANDIDATE_TYPE_FULL_WIDTH to "[全]",
            CANDIDATE_TYPE_HALF_WIDTH to "[半]",
        )
        for (locale in listOf(Locale.JAPANESE, Locale.ENGLISH)) {
            val localized = base.createConfigurationContext(
                Configuration(base.resources.configuration).apply { setLocale(locale) }
            )
            for ((type, expected) in cases) {
                val candidate = Candidate("候補", type, 1u, 0)
                assertEquals("$locale/$type", expected,
                    candidateLabelPresentation(localized, candidate, false, true).toString())
            }
        }
    }

    @Test fun otherCandidateLabelsAndAllDictionaryCategoriesUseJapaneseInEnglishImeContext() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val english = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        val japanese = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(Locale.JAPANESE) }
        )
        val cases = listOf(
            Candidate("候補", CANDIDATE_TYPE_PARTIAL, 1u, 0) to "[部]",
            Candidate("候補", CANDIDATE_TYPE_DATE, 1u, 0) to "[日付]",
            Candidate("候補", CANDIDATE_TYPE_DIGIT_DATE, 1u, 0) to "[日付]",
            Candidate("漢字\tかんじ", CANDIDATE_TYPE_READING_CORRECTION, 1u, 0) to "[読] かんじ",
            Candidate("候補", CANDIDATE_TYPE_USER_DICTIONARY, 1u, 0) to "[ユーザー]",
            Candidate("候補", CANDIDATE_TYPE_LEARNED_DICTIONARY, 1u, 0) to "[学習]",
            Candidate("候補", CANDIDATE_TYPE_USER_TEMPLATE, 1u, 0) to "[定型]",
            Candidate("候補", CANDIDATE_TYPE_TEXT_MACRO, 1u, 0) to "[マクロ]",
            Candidate("候補", CANDIDATE_TYPE_ENGLISH_CORRECTION, 1u, 0) to "[修正]",
            Candidate("候補", 41, 1u, 0) to "[訳]",
            Candidate("候補", CANDIDATE_TYPE_ZENZ, 1u, 0) to "[AI]",
            Candidate("候補", CANDIDATE_TYPE_CALCULATION, 1u, 0) to "[計算]",
            Candidate("候補", CANDIDATE_TYPE_UNIT_CONVERSION, 1u, 0) to "[換算]",
            Candidate("候補", CANDIDATE_TYPE_FORMULA_UNICODE, 1u, 0) to "[文字]",
            Candidate("候補", CANDIDATE_TYPE_FORMULA_TEX, 1u, 0) to "[TeX]",
        )
        for ((candidate, expected) in cases) {
            assertEquals("type=${candidate.type}", expected,
                candidateLabelPresentation(english, candidate, true, true).toString())
        }
        for (spec in ClassifiedDictionaries.all) {
            val candidate = Candidate("候補", CANDIDATE_TYPE_CONVERSION, 1u, 0,
                dictionaryCategories = setOf(spec.category))
            assertEquals("category=${spec.category}", "[${japanese.getString(spec.labelRes)}]",
                candidateLabelPresentation(english, candidate, false, true).toString())
        }
        assertEquals("[Text]", english.getString(com.kazumaproject.markdownhelperkeyboard.R.string.candidate_badge_formula_unicode))
    }

    @Test fun floatingFormulaBadgesUseJapaneseInEnglishImeContext() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val english = android.view.ContextThemeWrapper(
            base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard
        )
        val adapter = FloatingCandidateListAdapter(5)
        val items = listOf(
            com.kazumaproject.core.data.floating_candidate.CandidateItem("x+1", 1u,
                candidateType = CANDIDATE_TYPE_FORMULA_UNICODE, formulaSource = "x+1"),
            com.kazumaproject.core.data.floating_candidate.CandidateItem("x+1", 1u,
                candidateType = CANDIDATE_TYPE_FORMULA_TEX, formulaSource = "x+1"),
        )
        adapter.submitList(items)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        for ((index, expected) in listOf("[文字]", "[TeX]").withIndex()) {
            val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(english), adapter.getItemViewType(index))
            adapter.onBindViewHolder(holder, index)
            assertEquals(expected, holder.itemView.findViewById<android.widget.TextView>(
                com.kazumaproject.markdownhelperkeyboard.R.id.floating_formula_badge).text.toString())
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
            "ｶﾅ", 3u, label = "source", characterLabel = "[半カナ] source"
        )))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(context), adapter.getItemViewType(0))
        val label = holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.floating_candidate_label)
        for (underlines in listOf(false, true)) for (labels in listOf(false, true)) {
            adapter.setShowCharacterIndicators(underlines)
            adapter.setShowCharacterTypeLabels(labels)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(if (labels) "[半カナ] source" else "source", label.text.toString())
            val body = holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.text_view_item)
            assertEquals(underlines, body.contentDescription != null)
        }
    }

    @Test fun suggestionRowsToggleLabelsIndependentlyOfUnderlines() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(Candidate("ペ", CANDIDATE_TYPE_KATAKANA, 1u, 0))
        for (attempt in 0 until 100) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (adapter.itemCount > 0) break
            Thread.sleep(10)
        }
        assertEquals(1, adapter.itemCount)
        val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(context), adapter.getItemViewType(0))
        val label = holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.suggestion_item_type_text_view)
        for (underlines in listOf(false, true)) for (labels in listOf(false, true)) {
            adapter.setShowCharacterIndicators(underlines)
            adapter.setShowCharacterTypeLabels(labels)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(if (labels) "[カナ]" else "", label.text.toString())
            val body = holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.suggestion_item_text_view)
            assertEquals(underlines, body.contentDescription != null)
        }
        adapter.release()
    }
}
