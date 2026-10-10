package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.core.domain.flick.FlickInputEvidence
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionInput
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionKind
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickCorrectionEngineTest {
    private val repository = runBlocking { mock<UserDictionaryRepository>().also {
        whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(it.exactMatchesForConversion(any())).thenReturn(emptyList())
    } }

    private fun request(input: String, enhanced: Boolean, mode: CandidateQueryMode = CandidateQueryMode.PREDICTION,
                        bunsetsu: Boolean = true, evidence: List<FlickInputEvidence?> = emptyList(),
                        legacyCorrection: Boolean = false) = KanaKanjiQueryRequest(
        input, mode, bunsetsu, 4, false, false, false, false, false, repository, null,
        false, legacyCorrection, false, 3000, 1900, 20, collectCandidateSegments = true,
        flickCorrectionInput = if (enhanced) FlickCorrectionInput(input, evidence) else null,
    )

    private suspend fun freshQuery(session: KanaKanjiConversionSession, request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        // The legacy API also caches appends. Clear only its global caches to obtain a real
        // full recomputation while preserving the separate incremental session under test.
        for ((component, cache) in listOf("graphBuilder" to "cachedGraph", "findPath" to "forwardDpCache")) {
            val value = engine.javaClass.getDeclaredField(component).apply { isAccessible = true }.get(engine)
            value.javaClass.getDeclaredField(cache).apply { isAccessible = true }.set(value, null)
        }
        return session.query(request)
    }

    @Test fun regressionCasesKeepLiteralFirstChoicesAndRecoverMistakesBetterThanLegacyCorrection() = runBlocking {
        val corpus = listOf(File("src/androidTest/assets/flick_correction_corpus.tsv"),
            File("app/src/androidTest/assets/flick_correction_corpus.tsv")).first { it.isFile }
            .readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        var unchanged = 0
        val normal = corpus.filter { it[0] == "normal" }
        val changes = ArrayList<String>()
        for (row in normal) {
            val before = session.query(request(row[1], false)).candidates.firstOrNull()?.string
            val after = session.query(request(row[1], true)).candidates.firstOrNull()?.string
            if (before == after) unchanged++ else changes.add("${row[1]}: $before -> $after")
        }
        var legacyRecovered = 0
        var improvedRecovered = 0
        val misses = ArrayList<String>()
        val errors = corpus.filter { it[0] != "normal" }
        for (row in errors) {
            val legacy = session.query(request(row[1], false, legacyCorrection = true)).candidates
            val improved = session.query(request(row[1], true)).candidates
            fun recovered(candidates: List<Candidate>) = candidates.take(3).any { it.yomi == row[2] }
            if (recovered(legacy)) legacyRecovered++
            if (recovered(improved)) improvedRecovered++ else misses.add("${row[1]} -> ${row[2]}: ${improved.take(5)}")
        }
        println("Flick correction evaluation: normal=$unchanged/${normal.size}, legacyTop3=$legacyRecovered/${errors.size}, improvedTop3=$improvedRecovered/${errors.size}")
        println("Normal changes: $changes")
        println("Top3 misses: $misses")
        assertTrue("Normal first candidates changed: $changes", unchanged * 100 >= normal.size * 99)
        assertTrue("Top3 recovery must improve: $misses", improvedRecovered > legacyRecovered)
    }

    @Test fun allFiveExamplesCarryCorrectionTypeAndConsumeExactlyTheTypedReading() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        for ((input, reading) in listOf("かした" to "あした", "あしと" to "あした",
            "こんちは" to "こんにちは", "こんにちちは" to "こんにちは", "よしろく" to "よろしく")) {
            for (mode in listOf(CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                val result = session.query(request(input, true, mode))
                val corrected = result.candidates.firstOrNull { it.yomi == reading && it.flickCorrection != null }
                assertNotNull("$input/$mode: ${result.candidates.take(8)}", corrected)
                assertEquals(input.length, corrected!!.length.toInt())
                assertEquals(CANDIDATE_TYPE_FLICK_TYPO_CORRECTION, corrected.type)
                val path = result.candidateSegmentsByString[corrected.string]!!
                assertEquals(input.length, path.last().inputEnd)
                assertEquals(reading, path.joinToString("") { it.correctedReading.orEmpty() })
            }
        }
    }

    @Test fun productionDictionaryKeepsAMissingCharacterMatchWithinTheBoundedSearch() {
        val trie = engine.javaClass.getDeclaredField("systemYomiTrie").apply { isAccessible = true }
            .get(engine) as LOUDSWithTermId
        val results = trie.commonPrefixSearchWithFlickCorrection("こんちは", 0, SuccinctBitVector(trie.LBS),
            FlickCorrectionInput("こんちは"), false)
        assertTrue("Missing greeting was discarded: ${results.map { it.yomi }}",
            results.any { it.yomi == "こんにちは" && it.consumedLength == 4 })
    }

    @Test fun completionKeepsItsReadingWithoutAcquiringACorrectionLabel() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val input = "こんにち"
        for (enabled in listOf(false, true)) {
            val candidates = session.query(request(input, enabled)).candidates
            val completion = candidates.firstOrNull { it.yomi == "こんにちは" }
            assertNotNull("Completion reading is missing: $candidates", completion)
            assertNull(completion!!.flickCorrection)
            assertEquals(9.toByte(), completion.type)
        }
    }

    @Test fun selectingACorrectionImprovesItsNextRankWithoutChangingTheLiteralFirstChoice() = runBlocking {
        val stored = mutableListOf<LearnEntity>()
        val dao = mock<LearnDao>()
        org.mockito.kotlin.whenever(dao.findByInputPrefix(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenAnswer { invocation -> stored.filter { it.input.startsWith(invocation.getArgument<String>(0)) } }
        org.mockito.kotlin.whenever(dao.insertAll(org.mockito.kotlin.any())).thenAnswer { invocation ->
            stored.addAll(invocation.getArgument<List<LearnEntity>>(0))
            Unit
        }
        org.mockito.kotlin.whenever(dao.deleteAll()).thenAnswer { stored.clear(); Unit }
        val memory = LearnRepository(dao)
        val session = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        val input = "かした"
        val query = request(input, true).copy(learnRepository = memory)
        val before = session.query(query).candidates
        val selected = before.drop(3).first { candidate ->
            candidate.length.toInt() == input.length && candidate.flickCorrection?.edits?.singleOrNull()?.kind in
                setOf(FlickCorrectionKind.KEY, FlickCorrectionKind.DIRECTION) &&
                candidate.flickCorrection!!.costUnits <= 3000
        }
        val learning = ConversionLearningSession().apply {
            beginIfNeeded(input)
            record(LearningFragment(checkNotNull(selected.yomi), selected.commitText, selected.score,
                before.indexOf(selected), corrected = true))
        }
        val entries = learning.finish(false, 123L)
        assertTrue(entries.all { it.input == selected.yomi })
        memory.insertAll(entries)
        val after = session.query(query).candidates
        assertEquals(before.first(), after.first())
        assertEquals(selected, after[1])
        assertEquals(CANDIDATE_TYPE_FLICK_TYPO_CORRECTION, after[1].type)
        assertEquals(input.length, after[1].length.toInt())

        val off = session.query(request(input, false).copy(learnRepository = memory)).candidates
        assertTrue(off.none { it.flickCorrection != null })

        memory.deleteAll()
        assertEquals(before, session.query(query).candidates)
        memory.insertAll(listOf(entries.single().copy(out = "別の表記")))
        assertEquals(before, session.query(query).candidates)
    }

    @Test fun incrementalAppendDeleteReplacementAndGestureRevisionMatchColdQueries() = runBlocking {
        for (bunsetsu in listOf(false, true)) {
            val incremental = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
            val cold = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
            val inputs = listOf("こ", "こん", "こんち", "こんちは", "こんちはせんせい", "こんち",
                "こんにちは", "こんにちちは", "よしろく", "") +
                listOf("こんにちはせんせい", "おはようございます", "よしろくおねがいします",
                    "このあぷりのへんかんこうほをえらびます").flatMap { phrase ->
                    (1..phrase.length).map { phrase.take(it) }
                }
            for (withGestures in listOf(false, true)) for (input in inputs) {
                for (mode in listOf(CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                    val evidence = if (withGestures) input.map { FlickInputEvidence(it, mapOf('に' to 0.6f)) } else emptyList()
                    val query = request(input, true, mode, bunsetsu, evidence)
                    val warm = incremental.query(query)
                    val fresh = freshQuery(cold, query)
                    assertEquals("$input/$mode/$bunsetsu", fresh.candidates, warm.candidates)
                    assertEquals(fresh.candidateSegmentsByString, warm.candidateSegmentsByString)
                }
                org.mockito.Mockito.clearInvocations(repository)
            }
            val input = "こんちは"
            for (factor in listOf(0.6f, 1.3f, 0.8f)) {
                val revised = request(input, true, evidence = input.map { FlickInputEvidence(it, mapOf('に' to factor)) })
                assertEquals(freshQuery(cold, revised).candidates, incremental.query(revised).candidates)
            }
        }
    }

    @Test fun correctedPartialCandidateConsumesTypedPrefixAndLeavesTheOriginalTail() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val input = "こんちはせんせい"
        for (bunsetsu in listOf(false, true)) {
            val result = session.query(request(input, true, bunsetsu = bunsetsu))
            val partial = result.candidates.firstOrNull {
                it.yomi == "こんにちは" && it.flickCorrection?.originalType == 5.toByte()
            }
            assertNotNull("The corrected prefix must remain selectable: " + result.candidates, partial)
            assertEquals(CANDIDATE_TYPE_FLICK_TYPO_CORRECTION, partial!!.type)
            assertEquals(4, partial.length.toInt())
            assertEquals("せんせい", input.substring(partial.length.toInt()))
            assertTrue(partial.flickCorrection!!.edits.all { it.inputEnd <= partial.length.toInt() })
        }
    }

    @Test fun cancelledCorrectedAppendMatchesColdQueryOnRecovery() = runBlocking {
        for (bunsetsu in listOf(false, true)) {
            val incremental = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
            val cold = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
            incremental.query(request("こんちは", true, bunsetsu = bunsetsu))
            incremental.setAfterForwardDpForTest {
                throw kotlinx.coroutines.CancellationException("controlled corrected append cancellation")
            }
            try {
                incremental.query(request("こんちはせ", true, bunsetsu = bunsetsu))
                fail("The append must reach the controlled cancellation")
            } catch (_: kotlinx.coroutines.CancellationException) {
                // The completed graph and the two decoders must recover consistently.
            } finally {
                incremental.setAfterForwardDpForTest(null)
            }
            val query = request("こんちはせんせい", true, bunsetsu = bunsetsu)
            val recovered = incremental.query(query)
            val fresh = freshQuery(cold, query)
            assertEquals(fresh.candidates, recovered.candidates)
            assertEquals(fresh.candidateSegmentsByString, recovered.candidateSegmentsByString)
        }
    }

    @Test fun correctedBunsetsuKeepsItsReadingWhenAlternativesAreLoaded() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val input = "こんちは"
        val result = session.query(request(input, true, CandidateQueryMode.CONVERSION))
        val candidate = result.candidates.first { it.yomi == "こんにちは" && it.flickCorrection != null }
        val snapshot = BunsetsuConversionSnapshot(input, listOf(candidate), result.candidateSegmentsByString)
        val segment = buildConvertedBunsetsuSegments(input, emptyList(), snapshot).single()
        assertEquals("こんにちは", segment.initialCandidate!!.yomi)
        assertEquals(CANDIDATE_TYPE_FLICK_TYPO_CORRECTION,
            mergeBunsetsuCandidates(segment, emptyList()).candidates.first().type)
    }

    companion object { private val engine by lazy { TestEngineFactory.create() } }
}
