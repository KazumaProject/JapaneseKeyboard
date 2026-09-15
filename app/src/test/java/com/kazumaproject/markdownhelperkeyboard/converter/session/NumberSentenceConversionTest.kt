package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NumberSentenceConversionTest {
    private fun request(input: String, config: PredictionConfig = PredictionConfig(), mode: CandidateQueryMode = CandidateQueryMode.CONVERSION,
        bunsetsu: Boolean = false) = KanaKanjiQueryRequest(input, mode, bunsetsu, 8,
        false, false, false, false, false, repository, null, false, false, false,
        3000, 1900, 20, config, true)

    @Test fun naturalSentencesPreserveWordsAndCounterSpansAcrossModes() = runBlocking {
        val cases = mapOf(
            "よんそくぶん" to "4足分", "さんぼんだけ" to "3本だけ", "ろっぴきいる" to "6匹いる",
            "じゅういっこほしい" to "11個欲しい", "ふたりぶんください" to "2人分ください",
            "ほんをにじゅうさんさつかう" to "本を23冊買う", "にじゅうさんぞくぶんください" to "23足分ください",
            "えんぴつをさんぼんください" to "鉛筆を3本ください", "ねこがろっぴきいる" to "猫が6匹いる",
            "さんまいとにまい" to "3枚と2枚", "にじかんまつ" to "2時間待つ",
            "はつかまで" to "20日まで", "さんじごふんにでる" to "3時5分に出る", "ひゃくえんだけ" to "100円だけ",
        )
        for (backend in ConversionBackend.entries) for (bunsetsu in listOf(false, true)) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in CandidateQueryMode.entries.filter { it != CandidateQueryMode.EISUKANA }) {
                for ((input, expected) in cases) {
                    val result = session.query(request(input, mode = mode, bunsetsu = bunsetsu))
                    val first = result.candidates.first()
                    assertEquals("$backend / $mode / $bunsetsu / $input: ${result.candidates.take(8).map { it.string }}", expected, first.string)
                    assertEquals(expected, first.commitText)
                    assertEquals(input.length, first.length.toInt())
                    val segments = result.candidateSegmentsByString[expected].orEmpty()
                    assertEquals(input.length, segments.last().inputEnd)
                    assertEquals(expected, segments.joinToString("") { it.output })
                    assertEquals(0, segments.first().inputStart)
                    segments.zipWithNext().forEach { (left, right) -> assertEquals(left.inputEnd, right.inputStart) }
                }
            }
        }
    }

    @Test fun counterCategoriesConnectToVerbsAndParticles() = runBlocking {
        val cases = mapOf(
            "ひとつください" to "1つください", "みっかまつ" to "3日待つ", "しがつにいく" to "4月に行く",
            "にねんまつ" to "2年待つ", "にかげつまつ" to "2か月待つ", "にはくする" to "2泊する",
            "にじゅっさいになる" to "20歳になる", "さんばいのむ" to "3杯飲む", "さんかいまわす" to "3回回す",
            "さんがいにいく" to "3階に行く", "さんさつよむ" to "3冊読む", "よにんいる" to "4人いる",
            "さんちゃくかう" to "3着買う", "さんてんとる" to "3点取る", "さんつうおくる" to "3通送る",
            "さんげんある" to "3軒ある", "さんとういる" to "3頭いる", "さんだいうる" to "3台売る",
            "さんけんある" to "3件ある", "さんぱつうつ" to "3発打つ", "さんまいかう" to "3枚買う",
        )
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val failures = mutableListOf<String>()
        for ((input, expected) in cases) {
            val values = session.query(request(input)).candidates.take(8).map { it.string }
            println("COUNTER_CATEGORY $input: $values")
            if (expected !in values) failures += "$input: $values"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun allBuiltInCountersAreAvailableInsideSentences() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        for (counter in BuiltInCounter.entries) {
            val reading = counter.example.substringBefore(" →")
            val forms = ValidatedNumber.parseAll(reading, NumberCandidateConfig()).filter { it.builtInCounter == counter }.flatMap { it.basicForms }
            val input = "あと${reading}だけ"
            val result = session.query(request(input))
            assertTrue("$input / $counter: ${result.candidates.take(8).map { it.string }}", result.candidates.take(8).any { candidate ->
                candidate.length.toInt() == input.length && forms.any { candidate.string.endsWith(it + "だけ") }
            })
        }
    }

    @Test fun sentenceNotationOrderAndIncrementalEditsRespectSettings() = runBlocking {
        val forms = listOf("3本だけ", "３本だけ", "三本だけ")
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (order in NumberCandidateOrder.entries) {
                val config = PredictionConfig(numberCandidateOrder = order)
                val candidates = session.query(request("さんぼんだけ", config)).candidates
                assertEquals("$backend / $order", order.indices.map(forms::get), candidates.map { it.string }.filter { it in forms })
            }
            for (input in listOf("さ", "さん", "さんぼ", "さんぼん", "さんぼんだ", "さんぼんだけ", "さんぼん", "さんぼんください")) {
                val actual = session.query(request(input))
                val fresh = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(request(input))
                assertEquals(input, fresh.candidates.map { it.string to it.score }, actual.candidates.map { it.string to it.score })
                assertEquals(input, fresh.candidateSegmentsByString, actual.candidateSegmentsByString)
            }
            val unit = CustomNumberUnit("test", "独自単位", "たんい", specialReadings = listOf(
                SpecialNumberReading(1, "いったんい", SpecialNumberReadingMode.COMPOSE, "いち"),
                SpecialNumberReading(42, "じゅういったんい")))
            val enabled = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(units = listOf(unit)))
            session.query(request("にじゅういったんい", enabled))
            val disabled = enabled.copy(numberCandidateConfig = enabled.numberCandidateConfig.copy(units = listOf(unit.copy(enabled = false))))
            assertTrue(session.query(request("にじゅういったんいだけ", disabled)).candidates.none { it.string.contains("独自単位") })
            val restored = session.query(request("にじゅういったんいだけ", enabled)).candidates
            assertTrue(restored.take(8).any { it.string == "21独自単位だけ" })
            assertTrue(session.query(request("じゅういったんいだけ", enabled)).candidates.take(8).any { it.string == "42独自単位だけ" })
            val edited = enabled.copy(numberCandidateConfig = enabled.numberCandidateConfig.copy(units = listOf(unit.copy(output = "更新単位"))))
            val updated = session.query(request("にじゅういったんいだけ", edited)).candidates
            assertTrue(updated.take(8).any { it.string == "21更新単位だけ" })
            assertTrue(updated.none { it.string.contains("独自単位") })
        }
    }

    @Test fun numericGraphNodesPreserveExplicitDictionarySources() = runBlocking {
        val userRepository = mock<UserDictionaryRepository>()
        val word = com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord(
            word = "2人", reading = "ふたり", posIndex = 0, posScore = 100)
        whenever(userRepository.commonPrefixSearchInUserDict(any())).thenAnswer { invocation ->
            if ((invocation.arguments[0] as String).startsWith(word.reading)) listOf(word) else emptyList()
        }
        whenever(userRepository.exactMatchesForConversion(any())).thenAnswer { invocation ->
            if (invocation.arguments[0] == word.reading) listOf(word) else emptyList()
        }
        val session = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        for (enabled in listOf(false, true)) {
            val result = session.query(request("ふたりだけ", PredictionConfig(japaneseNumberCandidatesEnabled = enabled))
                .copy(userDictionaryRepository = userRepository))
            val candidate = result.candidates.first { it.string == "2人だけ" }
            assertEquals(com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY, candidate.type)
        }
    }

    @Test fun appendingPastLengthLimitInvalidatesGeneratedNodes() = runBlocking {
        val reading = "に" + "あ".repeat(251)
        val config = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(units = listOf(
            CustomNumberUnit("long", "境界単位", "あ".repeat(251)))))
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            val atLimit = reading + "だけよ"
            assertEquals(255, atLimit.length)
            assertTrue(session.query(request(atLimit, config)).candidates.any { it.string.contains("2境界単位") })
            val overLimit = session.query(request(atLimit + "ね", config)).candidates
            assertTrue(overLimit.none { it.string.contains("境界単位") })
            assertTrue(session.query(request(atLimit, config)).candidates.any { it.string.contains("2境界単位") })
        }
    }

    @Test fun ordinaryWordsKeepTheirDictionaryInterpretation() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        for ((input, expected) in mapOf("さんご" to "産後", "いつかあいたい" to "いつか会いたい", "はっけんした" to "発見した")) {
            assertEquals(input, expected, session.query(request(input)).candidates.first().string)
        }
        for (input in listOf("よしよし", "ごご", "いちいち", "さんさん", "ろくろく", "ごかいした", "いっぱいたべたい")) {
            val on = session.query(request(input)).candidates.map { it.string }
            val off = session.query(request(input, PredictionConfig(japaneseNumberCandidatesEnabled = false))).candidates.map { it.string }
            assertEquals(input, off.first(), on.first())
        }
    }

    companion object {
        private lateinit var engine: KanaKanjiEngine
        private lateinit var repository: UserDictionaryRepository
        @JvmStatic @BeforeClass fun setup() {
            engine = TestEngineFactory.create()
            repository = mock()
            runBlocking {
                whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
                whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
            }
        }
    }
}
