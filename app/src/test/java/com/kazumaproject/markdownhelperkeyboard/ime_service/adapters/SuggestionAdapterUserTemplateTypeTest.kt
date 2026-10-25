package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_ERA
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_CALCULATION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UNIT_CONVERSION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestionAdapterUserTemplateTypeTest {

    @Test
    fun reusedCandidateHolderRestoresOriginalColorsAfterSkinRoundTrip() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(candidate("あ", 1.toByte()))
        awaitItemCount(adapter, 1)
        val holder = createHolder(adapter)
        val originals = listOf(holder.text.textColors, holder.yomiText.textColors, holder.typeText.textColors)
        for (color in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE, null)) {
            adapter.setCandidateTextColor(color)
            adapter.setCandidateItemColors(color, color)
            adapter.onBindViewHolder(holder, 0)
            listOf(holder.text, holder.yomiText, holder.typeText).forEachIndexed { index, label ->
                if (color == null) org.junit.Assert.assertSame(originals[index], label.textColors)
                else assertEquals(color.toInt(), label.currentTextColor)
            }
        }
        adapter.release()
    }

    @Test
    fun taxonomyLabelIsAlwaysVisibleAndClearedOnHolderReuse() {
        val adapter = SuggestionAdapter()
        adapter.setShowDictionaryCandidateLabels(false)
        val holder = createHolder(adapter)
        adapter.suggestions = listOf(candidate("分類語", 1).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PERSON_ACTOR)))
        awaitItemCount(adapter, 1)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("[${holder.itemView.context.getString(com.kazumaproject.markdownhelperkeyboard.R.string.classified_label_person_actor)}]", holder.typeText.text.toString())
        adapter.suggestions = emptyList()
        awaitItemCount(adapter, 0)
        adapter.suggestions = listOf(candidate("123", 23))
        awaitItemCount(adapter, 1)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("", holder.typeText.text.toString())
        adapter.release()
    }

    @Test
    fun userTemplatesDoNotShowFullWidthLabel() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(
            candidate("ありがとうございます", CANDIDATE_TYPE_USER_TEMPLATE),
            candidate("test@example.com", CANDIDATE_TYPE_USER_TEMPLATE),
            candidate("https://example.com", CANDIDATE_TYPE_USER_TEMPLATE),
        )
        awaitItemCount(adapter, 3)
        val holder = createHolder(adapter)

        repeat(3) { position ->
            adapter.onBindViewHolder(holder, position)
            assertEquals("", holder.typeText.text.toString())
            assertNotEquals(holder.itemView.context.getString(R.string.candidate_label_full_width), holder.typeText.text.toString())
        }

        adapter.release()
    }

    @Test
    fun recycledHolderClearsFullWidthLabelForUserTemplate() {
        val adapter = SuggestionAdapter()
        val holder = createHolder(adapter)
        adapter.suggestions = listOf(candidate("ＡＢＣ", 30.toByte()))
        awaitItemCount(adapter, 1)

        adapter.onBindViewHolder(holder, 0)
        assertEquals(holder.itemView.context.getString(R.string.candidate_label_full_width), holder.typeText.text.toString())

        adapter.suggestions = listOf(
            candidate("ありがとうございます", CANDIDATE_TYPE_USER_TEMPLATE)
        )
        awaitBoundText(adapter, holder, "ありがとうございます")

        assertEquals("", holder.typeText.text.toString())
        adapter.release()
    }

    @Test
    fun fullWidthCandidateStillShowsFullWidthLabel() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(candidate("ＡＢＣ", 30.toByte()))
        awaitItemCount(adapter, 1)
        val holder = createHolder(adapter)

        adapter.onBindViewHolder(holder, 0)

        assertEquals(holder.itemView.context.getString(R.string.candidate_label_full_width), holder.typeText.text.toString())
        adapter.release()
    }

    @Test
    fun timeAndEraCandidatesDoNotShowFullWidthLabel() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(
            candidate("12:34", CANDIDATE_TYPE_TIME),
            candidate("令和6年", CANDIDATE_TYPE_ERA)
        )
        awaitItemCount(adapter, 2)
        val holder = createHolder(adapter)

        repeat(2) { position ->
            adapter.onBindViewHolder(holder, position)
            assertEquals("", holder.typeText.text.toString())
            assertNotEquals(holder.itemView.context.getString(R.string.candidate_label_full_width), holder.typeText.text.toString())
        }

        adapter.release()
    }

    @Test
    fun dictionaryCandidateLabelsAreHiddenByDefault() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = dictionaryCandidates()
        awaitItemCount(adapter, 3)
        val holder = createHolder(adapter)

        repeat(3) { position ->
            adapter.onBindViewHolder(holder, position)
            assertEquals("", holder.typeText.text.toString())
        }

        adapter.release()
    }

    @Test
    fun dictionaryCandidateLabelsAreShownWhenEnabled() {
        val adapter = SuggestionAdapter()
        adapter.setShowDictionaryCandidateLabels(true)
        adapter.suggestions = dictionaryCandidates()
        awaitItemCount(adapter, 3)
        val holder = createHolder(adapter)

        val expected = listOf(holder.itemView.context.getString(R.string.candidate_label_learned), holder.itemView.context.getString(R.string.candidate_label_user), holder.itemView.context.getString(R.string.candidate_label_template))
        expected.forEachIndexed { position, label ->
            adapter.onBindViewHolder(holder, position)
            assertEquals(label, holder.typeText.text.toString())
        }

        adapter.release()
    }

    @Test
    fun disablingDictionaryLabelsClearsRecycledHolderText() {
        val adapter = SuggestionAdapter()
        adapter.setShowDictionaryCandidateLabels(true)
        adapter.suggestions = listOf(
            candidate("ありがとうございます", CANDIDATE_TYPE_USER_TEMPLATE)
        )
        awaitItemCount(adapter, 1)
        val holder = createHolder(adapter)

        adapter.onBindViewHolder(holder, 0)
        assertEquals(holder.itemView.context.getString(R.string.candidate_label_template), holder.typeText.text.toString())

        adapter.setShowDictionaryCandidateLabels(false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("", holder.typeText.text.toString())

        adapter.release()
    }

    @Test
    fun numericType20IsNotMislabelledAsLearningCandidate() {
        val adapter = SuggestionAdapter()
        adapter.setShowDictionaryCandidateLabels(true)
        adapter.suggestions = listOf(candidate("₁₂₃", 20.toByte()))
        awaitItemCount(adapter, 1)
        val holder = createHolder(adapter)

        adapter.onBindViewHolder(holder, 0)

        assertEquals("", holder.typeText.text.toString())
        adapter.release()
    }

    @Test
    fun utilityCandidatesShowCalculationAndConversionBadges() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(
            candidate("3", CANDIDATE_TYPE_CALCULATION),
            candidate("1m", CANDIDATE_TYPE_UNIT_CONVERSION),
        )
        awaitItemCount(adapter, 2)
        val holder = createHolder(adapter)

        adapter.onBindViewHolder(holder, 0)
        assertEquals("[Calc]", holder.typeText.text.toString())
        adapter.onBindViewHolder(holder, 1)
        assertEquals("[Convert]", holder.typeText.text.toString())
        adapter.release()
    }

    @Test
    fun characterIndicatorsRefreshWithoutChangingCandidateAndClearOnDisable() {
        val adapter = SuggestionAdapter()
        adapter.suggestions = listOf(candidate("カナ１２ABC", 1))
        awaitItemCount(adapter, 1)
        val holder = createHolder(adapter)
        adapter.setShowCharacterIndicators(true)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("カナ１２ABC", holder.text.text.toString().trim())
        org.junit.Assert.assertTrue(holder.typeText.text.toString().contains("["))
        adapter.setShowCharacterIndicators(false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("", holder.typeText.text.toString())
        assertEquals("カナ１２ABC", adapter.suggestions.single().commitText)
        adapter.release()
    }

    private fun dictionaryCandidates(): List<Candidate> = listOf(
        candidate("学習候補", CANDIDATE_TYPE_LEARNED_DICTIONARY),
        candidate("ユーザー候補", CANDIDATE_TYPE_USER_DICTIONARY),
        candidate("ありがとうございます", CANDIDATE_TYPE_USER_TEMPLATE),
    )

    private fun candidate(string: String, type: Byte): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = string.length.toUByte(),
            score = 0
        )
    }

    private fun createHolder(adapter: SuggestionAdapter): SuggestionAdapter.SuggestionViewHolder {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return adapter.onCreateViewHolder(
            FrameLayout(context),
            SuggestionAdapter.VIEW_TYPE_SUGGESTION
        ) as SuggestionAdapter.SuggestionViewHolder
    }

    private fun awaitItemCount(adapter: SuggestionAdapter, expected: Int) {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            if (adapter.itemCount == expected) return
            Thread.sleep(10)
        }
        assertEquals(expected, adapter.itemCount)
    }

    private fun awaitBoundText(
        adapter: SuggestionAdapter,
        holder: SuggestionAdapter.SuggestionViewHolder,
        expected: String
    ) {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            adapter.onBindViewHolder(holder, 0)
            if (holder.text.text.toString().trim() == expected) return
            Thread.sleep(10)
        }
        assertEquals(expected, holder.text.text.toString().trim())
    }
}
