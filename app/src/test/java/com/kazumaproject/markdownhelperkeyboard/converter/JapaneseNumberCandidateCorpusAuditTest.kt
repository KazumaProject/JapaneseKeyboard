package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.engine.ValidatedNumber
import com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberCandidateOrder
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
                assertEquals(reading, 3, candidates.size)
                assertEquals(reading, fullWidth(candidates.first().string), candidates[1].string)
                matches[reading] = candidates.first().string
            }
        }
        report("dictionary-counter-candidates.tsv", matches.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        report("dictionary-summary.txt", "dictionaryReadings=${readings.size}\ncounterMatches=${matches.size}")
        assertEquals("Audit the complete dictionary, without decoder prefiltering", 747_244, readings.size)
        assertEquals("Review every changed dictionary reading before updating the expected map", reviewed, matches)
    }

    @Test
    fun everyDictionaryReadingMatchesReviewedCardinalsWithoutDecoderPrefiltering() {
        val matches = linkedMapOf<String, String>()
        for (reading in readings) {
            ValidatedNumber.parseReading(reading)?.let { matches[reading] = it.value.toString() }
        }
        report("dictionary-cardinals.tsv", matches.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        val expected = javaClass.getResourceAsStream("/number-candidates/approved-cardinals.tsv")!!
            .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { it.split('\t').let { cells -> cells[0] to cells[1] } } }
        assertEquals(expected, matches)
        assertEquals(747_244, readings.size)
    }

    @Test
    fun finalCandidatesAcrossModesBackendsSegmentationSettingsAndInputEdits() = runBlocking {
        val invalid = listOf("ごぜん", "ぜんご", "いちぜん", "じゅうよ", "にびゃく", "ごぴゃく", "にぜん", "いっまん", "じゅっおく", "じゅ", "ひゃ", "いっ")
        val ordinary = readings.asSequence().filter { it.length in 2..12 && it.all { c -> c in 'ぁ'..'ゖ' } }
            .sortedBy(String::hashCode).take(1000).toList()
        val corpus = (readings.filter { input -> suffixes.any(input::endsWith) } + reviewed.keys + invalid + ordinary).distinct()
        val sessions = ConversionBackend.entries.associateWith { KanaKanjiConversionSession(engine, it) }
        val numeric = Regex("[0-9０-９〇零一二三四五六七八九十百千万億兆京,]+(?:人|円|分|時)?")
        var comparisons = 0
        for ((index, input) in corpus.withIndex()) {
            for ((backend, session) in sessions) for (bunsetsu in listOf(false, true)) for (mode in CandidateQueryMode.entries) {
                val request = KanaKanjiQueryRequest(
                    input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
                    mozcUtPersonName = false, mozcUtPlaces = false, mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
                    userDictionaryRepository = repository, learnRepository = null, omissionSearchEnabled = false,
                    typoCorrectionJapaneseFlickEnabled = false, typoCorrectionQwertyEnglishEnabled = false,
                    typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900, beamWidth = 20, collectCandidateSegments = true,
                )
                val candidates = session.query(request).candidates.distinctBy { it.string }
                val label = "$input/$backend/$mode/bunsetsu=$bunsetsu"
                reviewed[input]?.let { expected ->
                    val half = expected.dropLast(1).toLong()
                    val kanji = independentKanji(half) + expected.last()
                    val forms = listOf(expected, fullWidth(expected), kanji)
                    assertEquals(label, forms, candidates.filter { it.string in forms }.map { it.string })
                }
                if (mode != CandidateQueryMode.EISUKANA) {
                    mapOf("ごぜん" to "午前", "ぜんご" to "前後")[input]?.let { word ->
                        assertTrue("$label missing ordinary word $word", candidates.any { it.string == word })
                    }
                }
                if (input in invalid) for (candidate in candidates) {
                    assertTrue("$label unexpected $candidate", candidate.number == null &&
                        !numeric.matches(candidate.string) && !numeric.matches(candidate.commitText) &&
                        !candidate.string.contains(Regex("[0-9０-９⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉①-⑳❶-❿]")))
                }
                comparisons++
            }
            if (index % 100 == 0) report("pipeline-progress.txt", "readings=${index+1}/${corpus.size}\ncomparisons=$comparisons")
        }
        // Same session receives additions, deletions and configuration changes, not just resets.
        for ((backend, session) in sessions) for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) {
            for (order in NumberCandidateOrder.entries) for (enabled in listOf(false, true)) {
                for (input in listOf("さん", "さんに", "さんにん", "さんに", "ぜんご", "００３", "003")) {
                    val request = KanaKanjiQueryRequest(input, mode, bunsetsu, 4, false, false, false, false, false,
                        repository, null, false, false, false, 3000, 1900, 20,
                        PredictionConfig(japaneseNumberCandidatesEnabled = enabled, numberCandidateOrder = order))
                    val result = session.query(request).candidates.distinctBy { it.string }
                    val forms = when (input) {
                        "さん" -> listOf("3", "３", "三")
                        "さんにん" -> listOf("3人", "３人", "三人")
                        "003", "００３" -> listOf("003", "００３", "三")
                        else -> emptyList()
                    }
                    if (forms.isNotEmpty() && (enabled || input in listOf("003", "００３"))) {
                        assertEquals("$input/$backend/$mode/$bunsetsu/$order/$enabled", order.indices.map(forms::get),
                            result.filter { it.string in forms }.map { it.string })
                    }
                    if (!enabled) assertTrue(result.none { it.generatedNumber && it.number?.origin == com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberInputOrigin.READING })
                }
            }
        }
        report("pipeline-summary.txt", "readings=${corpus.size}\ncomparisons=$comparisons\nresult=PASS")
    }

    @Test
    fun fiveThousandBrokenReadingsCannotBeRescuedByAnyCandidatePath() = runBlocking {
        val numberText = Regex("[0-9０-９⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉①-⑳❶-❿]|^[〇零一二三四五六七八九十百千万億兆京]+$")
        var comparisons = 0
        val failures = linkedSetOf<String>()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (n in 0..999) for (prefix in listOf("にびゃく", "ごぴゃく", "にぜん", "ろくひゃく", "はちせん")) {
                val input = prefix + if (n == 0) "" else com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberGrammarTest.spoken(n)
                for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) {
                    val request = KanaKanjiQueryRequest(input, mode, bunsetsu, 4, false, false, false, false, false,
                        repository, null, false, false, false, 3000, 1900, 20)
                    for (candidate in session.query(request).candidates) {
                        if (candidate.number != null || numberText.containsMatchIn(candidate.string) || numberText.containsMatchIn(candidate.commitText)) {
                            failures += "$input/$backend/$mode/$bunsetsu unexpected $candidate"
                        }
                    }
                    comparisons++
                }
            }
        }
        report("malformed-pipeline-failures.txt", failures.joinToString("\n"))
        assertTrue("${failures.size} unexplained numeric candidates: ${failures.take(20)}", failures.isEmpty())
        assertEquals(80000, comparisons)
        report("malformed-pipeline-summary.txt", "readings=5000\ncomparisons=$comparisons\nresult=PASS")
    }

    @Test
    fun ordinaryWordsCannotProduceNumbersFromTheirPrefixesOrSuffixes() = runBlocking {
        val words = listOf("ごはん", "にほんご", "いちご", "さんぽ", "ごめん", "にせもの", "はちみつ", "くすり", "しごと")
        val numberText = Regex("[0-9０-９⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉①-⑳❶-❿]|^[〇零一二三四五六七八九十百千万億兆京]+$")
        val failures = linkedSetOf<String>()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (input in words) for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) {
                val request = KanaKanjiQueryRequest(input, mode, bunsetsu, 32, false, false, false, false, false,
                    repository, null, false, false, false, 3000, 1900, 20)
                for (candidate in session.query(request).candidates) {
                    if (candidate.number != null || numberText.containsMatchIn(candidate.string) || numberText.containsMatchIn(candidate.commitText)) {
                        failures += "$input/$backend/$mode/$bunsetsu unexpected $candidate"
                    }
                }
            }
        }
        report("ordinary-word-numeric-failures.txt", failures.joinToString("\n"))
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun lexicalWordsThatContainNumeralsAreNotRemoved() = runBlocking {
        val expected = mapOf("しちごさん" to "七五三", "じゅうぶん" to "十分", "にほんご" to "日本語")
        val failures = mutableListOf<String>()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for ((input, word) in expected) for (mode in CandidateQueryMode.entries.filter { it != CandidateQueryMode.EISUKANA }) for (bunsetsu in listOf(false, true)) {
                val request = KanaKanjiQueryRequest(input, mode, bunsetsu, 32, false, false, false, false, false,
                    repository, null, false, false, false, 3000, 1900, 20)
                val candidates = session.query(request).candidates
                if (candidates.none { it.string == word }) failures += "$input/$backend/$mode/$bunsetsu missing $word"
            }
        }
        report("lexical-word-review-failures.txt", failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyValueThrough9999KeepsAllThreeFormsAcrossAllFinalPaths() = runBlocking {
        var comparisons = 0
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (value in 0..9999) {
                val input = com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberGrammarTest.spoken(value)
                val forms = listOf(value.toString(), fullWidth(value.toString()), independentKanji(value.toLong()))
                for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) {
                    val request = KanaKanjiQueryRequest(input, mode, bunsetsu, 4, false, false, false, false, false,
                        repository, null, false, false, false, 3000, 1900, 20)
                    val candidates = session.query(request).candidates.distinctBy { it.string }
                    assertEquals("$value/$input/$backend/$mode/$bunsetsu", forms,
                        candidates.filter { it.string in forms }.map { it.string })
                    for (candidate in candidates.filter { it.string in forms }) {
                        assertEquals(candidate.string, candidate.commitText)
                    }
                    comparisons++
                }
            }
        }
        assertEquals(160000, comparisons)
        report("all-values-pipeline-summary.txt", "values=10000\ncomparisons=$comparisons\nresult=PASS")
    }

    @Test
    fun persistedInvalidHistoryNeverReappearsAndValidDictionaryDuplicatesRetainProof() = runBlocking {
        val history = org.mockito.Mockito.mock(com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository::class.java)
        val invalidSurfaces = listOf("1005", "１００５", "千五", "1,005", "10月5日", "10⁸", "①", "全5", "全５", "全五")
        val entries = invalidSurfaces.map {
            com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity("ぜんご", it, score = -100000)
        } + listOf(
            com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity("ぜんご", "前後", score = -100000),
            com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity("さんにん", "3人", score = -100000))
        whenever(history.findCommonPrefixes(any())).thenAnswer { invocation ->
            entries.filter { (invocation.arguments[0] as String).startsWith(it.input) }
        }
        whenever(history.findExactMatchesForConversion(any())).thenAnswer { invocation ->
            entries.filter { it.input == invocation.arguments[0] }
        }
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) for (enabled in listOf(false, true)) {
                for (input in listOf("ぜん", "ぜんご", "さんにん", "ぜんご")) {
                    val query = KanaKanjiQueryRequest(input, mode, bunsetsu, 4, false, false, false, false, false,
                        repository, history, false, false, false, 3000, 1900, 20,
                        PredictionConfig(japaneseNumberCandidatesEnabled = enabled))
                    val candidates = session.query(query).candidates.distinctBy { it.string }
                    val label = "$input/$backend/$mode/$bunsetsu/$enabled"
                    if (input == "ぜんご") {
                        assertTrue(label, candidates.none { it.string in invalidSurfaces || it.commitText in invalidSurfaces })
                        if (mode != CandidateQueryMode.EISUKANA) assertTrue(label, candidates.any { it.string == "前後" })
                    }
                    if (input == "さんにん" && mode != CandidateQueryMode.EISUKANA) {
                        val number = candidates.first { it.string == "3人" }
                        assertEquals(label, 3L, number.number?.value)
                        assertEquals(label, "さんにん", number.number?.reading)
                        assertTrue(label, !number.generatedNumber)
                    }
                }
            }
        }
        // The backing rows were not deleted or rewritten by visibility filtering.
        assertEquals(12, entries.size)
        org.mockito.Mockito.verify(history, org.mockito.Mockito.never()).deleteAll()
    }

    private fun independentKanji(value: Long): String {
        if (value == 0L) return "〇"
        require(value < 10000)
        return listOf(1000L to "千", 100L to "百", 10L to "十", 1L to "").joinToString("") { (place, suffix) ->
            val digit = (value / place % 10).toInt()
            if (digit == 0) "" else (if (digit == 1 && place > 1) "" else "〇一二三四五六七八九"[digit].toString()) + suffix
        }
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
            "ひとり" to "1人",
            "ふたり" to "2人",
            "いちえん" to "1円",
            "いちじ" to "1時",
            "きゅうえん" to "9円",
            "きゅうふん" to "9分",
            "きゅうにん" to "9人",
            "くじ" to "9時",
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
            "ななにん" to "7人",
            "にえん" to "2円",
            "にじ" to "2時",
            "はちじ" to "8時",
            "はちにん" to "8人",
            "はっぷん" to "8分",
            "ひゃくにん" to "100人",
            "よじ" to "4時",
            "よにん" to "4人",
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
