package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericCandidateProviderTest {

    @Test
    fun notationPreferenceOrdersTheSameFormsForStandaloneNumbers() {
        assertEquals(
            listOf("100", "１００", "百"),
            strings("ひゃく", NumericNotationPreference.HALF_WIDTH_FIRST).take(3),
        )
        assertEquals(
            listOf("１００", "100", "百"),
            strings("ひゃく", NumericNotationPreference.FULL_WIDTH_FIRST).take(3),
        )
        assertEquals(
            listOf("百", "100", "１００"),
            strings("ひゃく", NumericNotationPreference.KANJI_FIRST).take(3),
        )
    }

    @Test
    fun notationPreferenceOrdersTheSameFormsForNumbersWithCounters() {
        val cases = mapOf(
            "ひゃくえん" to listOf("100円", "１００円", "百円"),
            "50えん" to listOf("50円", "５０円", "五十円"),
            "いちじかんはん" to listOf("1時間半", "１時間半", "一時間半"),
            "さんびゃくまい" to listOf("300枚", "３００枚", "三百枚"),
            "にじゅっぷん" to listOf("20分", "２０分", "二十分"),
        )

        cases.forEach { (input, expectedHalfWidthFirst) ->
            assertEquals(
                "$input half-width",
                expectedHalfWidthFirst,
                strings(input, NumericNotationPreference.HALF_WIDTH_FIRST).take(3),
            )
            assertEquals(
                "$input full-width",
                listOf(
                    expectedHalfWidthFirst[1],
                    expectedHalfWidthFirst[0],
                    expectedHalfWidthFirst[2],
                ),
                strings(input, NumericNotationPreference.FULL_WIDTH_FIRST).take(3),
            )
            assertEquals(
                "$input Kanji",
                listOf(
                    expectedHalfWidthFirst[2],
                    expectedHalfWidthFirst[0],
                    expectedHalfWidthFirst[1],
                ),
                strings(input, NumericNotationPreference.KANJI_FIRST).take(3),
            )
        }
    }

    @Test
    fun explicitDigitInputIsNormalizedAndKeepsEveryPrimaryNotation() {
        val candidates = NumericCandidateProvider.generate("１２３４")
        val strings = candidates.map { it.string }

        assertEquals("1234", strings.first())
        assertTrue(strings.containsAll(listOf("1234", "１２３４", "千二百三十四", "1,234")))
        assertTrue(candidates.any { it.string == "1234" && it.type == 31.toByte() })
        assertTrue(candidates.any { it.string == "１２３４" && it.type == 30.toByte() })
    }

    @Test
    fun timeCountersUseTimeTypeOnlyForHalfWidthNumericForm() {
        val candidates = NumericCandidateProvider.generate("にじゅっぷん")

        assertTrue(candidates.any { it.string == "20分" && it.type == CANDIDATE_TYPE_TIME })
        assertTrue(candidates.any { it.string == "２０分" && it.type == 30.toByte() })
        assertFalse(candidates.any { it.string == "20分" && it.type == 30.toByte() })
    }

    @Test
    fun ordinaryWordsThatOnlyLookLikeNumbersAreRejected() {
        listOf(
            "しえん",
            "しじ",
            "ごはん",
            "よしよし",
        ).forEach { input ->
            assertTrue(
                "$input produced ${NumericCandidateProvider.generate(input)}",
                NumericCandidateProvider.generate(input).isEmpty(),
            )
        }
    }

    @Test
    fun unavoidableCounterHomophonesKeepNumericCandidatesButDoNotTakeTheFirstPosition() {
        val input = "にほん"
        assertTrue(NumericCandidateProvider.generate(input).isNotEmpty())
        assertFalse(NumericCandidateProvider.shouldPrioritize(input))
    }

    @Test
    fun valueBasedSymbolsFollowThePrimaryNumericCandidatesSetting() {
        val withoutSymbols = NumericCandidateProvider.generate(
            input = "いち",
            showSymbolCandidates = false,
        )

        assertFalse(withoutSymbols.any { it.string == "①" })
        assertEquals("1", withoutSymbols.first().string)
    }

    @Test
    fun bundledCatalogCoversTheSharedCounterAndUnitVocabulary() {
        val expectedSurfaces = setOf(
            "時", "時間", "分", "秒", "日", "週", "週間", "か月", "月", "年",
            "円", "ドル", "ユーロ", "%", "℃", "mm", "cm", "m", "km", "mg", "g", "kg", "ml", "l",
            "人", "名", "個", "箇", "本", "匹", "羽", "頭", "枚", "冊", "台", "代",
            "軒", "棟", "戸", "件", "点", "杯", "缶", "瓶", "箱", "袋", "粒", "束", "房",
            "着", "足", "面", "通", "部", "巻", "章", "口", "席", "列", "基", "株", "式", "丁",
            "階", "回", "番", "号", "番目", "位", "組", "対", "段", "度", "倍", "割",
        )
        val actualSurfaces = BundledNumericSuffixCatalog.definitions
            .flatMap { it.canonicalSurfaces }
            .toSet()

        assertTrue(expectedSurfaces.all(actualSurfaces::contains))
    }

    @Test
    fun homophonousReadingsKeepEveryCatalogInterpretation() {
        val cases = mapOf(
            "ごかい" to setOf("5階", "5回"),
            "にけん" to setOf("2件", "2軒"),
            "にこ" to setOf("2個", "2戸"),
            "にだい" to setOf("2台", "2代"),
            "にど" to setOf("2℃", "2度"),
        )

        cases.forEach { (input, expected) ->
            val strings = NumericCandidateProvider.generate(input, showSymbolCandidates = false)
                .map { it.string }
            assertTrue("$input: $strings", strings.containsAll(expected))
        }
    }

    @Test
    fun lexicalPriorityPolicyIsGenericAcrossShortHomophonousExpressions() {
        listOf("にほん", "にかい", "にじ", "にけん").forEach { input ->
            assertFalse(input, NumericCandidateProvider.shouldPrioritize(input))
            assertTrue(
                "$input has no numeric interpretation",
                NumericCandidateProvider.generate(input).isNotEmpty(),
            )
        }
    }

    private fun strings(
        input: String,
        preference: NumericNotationPreference,
    ): List<String> = NumericCandidateProvider.generate(
        input = input,
        notationPreference = preference,
        showSymbolCandidates = false,
    ).map { it.string }
}
