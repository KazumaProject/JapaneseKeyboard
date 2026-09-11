package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JapaneseNumberCandidateCorpusAuditTest {
    @Test
    fun everySystemDictionaryReadingMatchesTheReviewedCounterAllowlist() {
        val generate = KanaKanjiEngine::class.java.getDeclaredMethod(
            "createCandidatesForJapaneseNumberWithUnit", String::class.java,
        ).apply { isAccessible = true }
        val matches = linkedMapOf<String, String>()
        for (reading in readings) {
            @Suppress("UNCHECKED_CAST")
            val candidates = generate.invoke(engine, reading) as List<Candidate>
            if (candidates.isNotEmpty()) {
                assertEquals(reading, 2, candidates.size)
                assertEquals(reading, fullWidth(candidates.first().string), candidates.last().string)
                matches[reading] = candidates.first().string
            }
        }
        report("dictionary-counter-candidates.tsv", matches.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        report("dictionary-summary.txt", "dictionaryReadings=${readings.size}\ncounterMatches=${matches.size}")
        assertTrue("System dictionary corpus unexpectedly small", readings.size > 700_000)
        assertEquals("Review every changed dictionary reading before updating the expected map", reviewed, matches)
    }

    @Test
    fun allCounterEndingDictionaryReadingsAddOnlyReviewedNumbersAcrossEveryPath() = runBlocking {
        val risky = readings.filter { input -> suffixes.any(input::endsWith) }
        val ordinary = readings.asSequence()
            .filter { it.length in 2..12 && it.all { char -> char in 'ぁ'..'ゖ' } }
            .filter { input -> suffixes.none(input::endsWith) && input.toNumber() == null }
            .sortedBy(String::hashCode).take(1_000).toList()
        val corpus = risky + ordinary
        // Independent engines receive identical histories. OFF/ON repeats in one
        // incremental session can otherwise compare different traversal states.
        val baselineEngine = TestEngineFactory.create()
        val baselineSessions = ConversionBackend.entries.associateWith { KanaKanjiConversionSession(baselineEngine, it) }
        val sessions = ConversionBackend.entries.associateWith { KanaKanjiConversionSession(engine, it) }
        var comparisons = 0
        val equalCostDifferences = mutableListOf<String>()
        val started = System.nanoTime()
        corpus.forEachIndexed { index, input ->
            for ((backend, session) in sessions) {
                for (bunsetsu in listOf(false, true)) {
                    for (mode in CandidateQueryMode.entries) {
                        val request = KanaKanjiQueryRequest(
                            input = input, mode = mode, bunsetsuSeparation = bunsetsu,
                            n = 4, mozcUtPersonName = false, mozcUtPlaces = false,
                            mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
                            userDictionaryRepository = repository, learnRepository = null,
                            omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                            typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                            omissionSearchOffsetScore = 1900, beamWidth = 20,
                            collectCandidateSegments = true,
                        )
                        val baseline = baselineSessions.getValue(backend).query(request.copy(predictionConfig = PredictionConfig(
                            japaneseNumberCandidatesEnabled = false,
                        )))
                        val enabled = session.query(request)
                        val remaining = enabled.candidates.toMutableList()
                        val label = "$input/$mode/$backend/bunsetsu=$bunsetsu"
                        reviewed[input]?.let { expected ->
                            val generated = mutableListOf(expected to 8000, fullWidth(expected) to 8001)
                            if (mode == CandidateQueryMode.EISUKANA) generated += expected to 3000
                            for ((value, score) in generated) {
                                val position = remaining.indexOfFirst { it.string == value && it.score == score }
                                assertTrue("$label missing $value", position >= 0)
                                remaining.removeAt(position)
                            }
                        }
                        // FindPath breaks equal-cost path ties using identityHashCode(Node).
                        // Independently built graphs (including OFF/OFF controls) may select
                        // different lexical paths at a tie. Keep all deterministic candidates
                        // exact; for type 1 paths require identical ordered score/length profiles.
                        assertEquals("$label changed deterministic candidates",
                            baseline.candidates.filter { it.type.toInt() != 1 },
                            remaining.filter { it.type.toInt() != 1 })
                        assertEquals("$label changed lexical path scores",
                            baseline.candidates.filter { it.type.toInt() == 1 }.map { it.score to it.length },
                            remaining.filter { it.type.toInt() == 1 }.map { it.score to it.length })
                        if (baseline.candidates != remaining) {
                            equalCostDifferences += label
                        } else {
                            assertEquals(label, baseline.candidateSegmentsByString, enabled.candidateSegmentsByString)
                            assertEquals(label, baseline.bunsetsuResult?.splitPatterns, enabled.bunsetsuResult?.splitPatterns)
                            assertEquals(label, baseline.bunsetsuResult?.splitPatternByCandidateString, enabled.bunsetsuResult?.splitPatternByCandidateString)
                        }
                        // A lexical tie must never conceal a newly injected numeric candidate.
                        val numericCounter = Regex("[0-9０-９,]+[時分人円]")
                        assertEquals("$label unexpected numeric lexical candidate",
                            baseline.candidates.filter { numericCounter.matches(it.string) },
                            remaining.filter { numericCounter.matches(it.string) })
                        comparisons++
                    }
                }
            }
            if (index % 100 == 0 || index == corpus.lastIndex) {
                report("pipeline-progress.txt", "readings=${index + 1}/${corpus.size}\ncomparisons=$comparisons\nelapsedSeconds=${(System.nanoTime() - started) / 1_000_000_000}")
            }
        }
        report("equal-cost-path-differences.txt", equalCostDifferences.joinToString("\n"))
        report("pipeline-summary.txt", "counterEndingReadings=${risky.size}\nordinaryReadings=${ordinary.size}\ncomparisons=$comparisons\nequalCostPathDifferences=${equalCostDifferences.size}\nresult=PASS")
    }

    private fun fullWidth(value: String) = value.map {
        if (it in '0'..'9') it + 0xFEE0 else it
    }.joinToString("")

    private fun report(name: String, value: String) {
        val dir = File("build/reports/number-candidate-audit").apply { mkdirs() }
        File(dir, name).writeText(value + "\n")
    }

    companion object {
        private lateinit var engine: KanaKanjiEngine
        private lateinit var readings: List<String>
        private lateinit var repository: UserDictionaryRepository
        // Manually reviewed dictionary matches. Do not derive expected values from the generator.
        private val reviewed = linkedMapOf(
            "いちえん" to "1円",
            "いちじ" to "1時",
            "いちにん" to "1人",
            "きゅうえん" to "9円",
            "きゅうじ" to "9時",
            "きゅうふん" to "9分",
            "きゅうにん" to "9人",
            "くじ" to "9時",
            "くにん" to "9人",
            "ごえん" to "5円",
            "ごじ" to "5時",
            "ごじゅうにん" to "50人",
            "ごふん" to "5分",
            "ごにん" to "5人",
            "さんじ" to "3時",
            "さんにん" to "3人",
            "さんびゃくにん" to "300人",
            "さんぷん" to "3分",
            "さんえん" to "3円",
            "さんふん" to "3分",
            "しちじ" to "7時",
            "しちにん" to "7人",
            "じっぷん" to "10分",
            "じゅうじ" to "10時",
            "じゅうにん" to "10人",
            "じゅうえん" to "10円",
            "せんにん" to "1000人",
            "せんえん" to "1000円",
            "ぜろじ" to "0時",
            "ぜろえん" to "0円",
            "ぜろにん" to "0人",
            "ななじ" to "7時",
            "ななにん" to "7人",
            "にえん" to "2円",
            "にじ" to "2時",
            "ににん" to "2人",
            "はちじ" to "8時",
            "はちにん" to "8人",
            "はっぷん" to "8分",
            "ひゃくにん" to "100人",
            "よじ" to "4時",
            "よにん" to "4人",
            "よんじ" to "4時",
            "よんにん" to "4人",
            "よんふん" to "4分",
            "よんぷん" to "4分",
            "れいえん" to "0円",
            "れいじ" to "0時",
            "れいにん" to "0人",
            "ろくえん" to "6円",
            "ろくじ" to "6時",
            "ろくじゅうにん" to "60人",
            "ろくにん" to "6人",
        )
        private val suffixes = listOf("じ", "にん", "えん", "ふん", "ぷん")

        @JvmStatic @BeforeClass
        fun loadCorpus() {
            engine = TestEngineFactory.create()
            fun field(name: String): Any = requireNotNull(KanaKanjiEngine::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(engine))
            val trie = field("systemYomiTrie") as LOUDSWithTermId
            val bits = field("systemSuccinctBitVectorLBSYomi") as SuccinctBitVector
            readings = trie.predictiveSearch("", bits)
            repository = org.mockito.Mockito.mock(
                UserDictionaryRepository::class.java,
                org.mockito.Mockito.withSettings().stubOnly(),
            )
            runBlocking {
                whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
                whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
            }
        }
    }
}
