package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors

/** Exhaustive, deliberately separate from the small regression suite. No sampling or decoder prefilter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FullDictionaryCandidatePipelineAuditTest {
    @Test fun everySystemReadingTraversesEveryModeBackendSegmentationAndCandidateLimit() = runBlocking<Unit> {
        val engines = List(4) { TestEngineFactory.create() }
        fun field(name: String): Any = requireNotNull(KanaKanjiEngine::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(engines.first()))
        val trie = field("systemYomiTrie") as LOUDSWithTermId
        val bits = field("systemSuccinctBitVectorLBSYomi") as SuccinctBitVector
        val readings = trie.predictiveSearch("", bits)
        assertEquals(747244, readings.size)
        val directory = File("build/reports/number-candidate-full-pipeline").apply { mkdirs() }
        Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { dispatcher ->
            engines.mapIndexed { shard, engine -> async(dispatcher) {
                val repository = org.mockito.Mockito.mock(UserDictionaryRepository::class.java,
                    org.mockito.Mockito.withSettings().stubOnly())
                whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
                whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
                val sessions = ConversionBackend.entries.associateWith { KanaKanjiConversionSession(engine, it) }
                var comparisons = 0L
                var rawCount = 0L
                var rejected = 0L
                fun hasNumeral(text: String): Boolean = text.any { char ->
                    char in "〇零一二三四五六七八九十百千万億兆京" ||
                        Character.getType(char) in setOf(Character.DECIMAL_DIGIT_NUMBER.toInt(),
                            Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt())
                }
                val removals = File(directory, "removed-$shard.tsv").bufferedWriter()
                engine.numberCandidateAuditObserver = { input, before, after ->
                    rawCount += before.size
                    val retained = after.map { Triple(it.string, it.commitText, it.type) }.toHashSet()
                    for (candidate in before) {
                        if (Triple(candidate.string, candidate.commitText, candidate.type) !in retained) {
                            assertNull("Lost independent text transform: $input / $candidate", candidate.nonNumericSource)
                            val single = candidate.conversionSegments.singleOrNull()
                            val exactLexical = single != null && single.reading == input &&
                                single.output == candidate.string && single.leftId != null && single.leftId!!.toInt() !in 2043..2055
                            assertFalse("Lost exact lexical entry: $input / $candidate", exactLexical)
                            assertTrue("Lost ordinary candidate: $input / $candidate",
                                hasNumeral(candidate.string) || hasNumeral(candidate.commitText) ||
                                    candidate.number != null || candidate.temporalSource != null)
                            rejected++
                            removals.appendLine("$input\t${candidate.string}\t${candidate.type}\t${candidate.yomi}")
                        }
                    }
                }
                try {
                    for (index in shard until readings.size step engines.size) {
                        val input = readings[index]
                        val literalOutputs = IndependentLiteralCandidates.englishKeyForms(input)
                        for ((_, session) in sessions) for (mode in CandidateQueryMode.entries)
                            for (bunsetsu in listOf(false, true)) for (limit in listOf(4, 32)) {
                                val request = KanaKanjiQueryRequest(input, mode, bunsetsu, limit,
                                    false, false, false, false, false, repository, null, false, false, false, 3000, 1900, 20)
                                val candidates = session.query(request).candidates
                                assertTrue("Empty candidates: $input/$mode/$bunsetsu/$limit", candidates.isNotEmpty())
                                if (mode == CandidateQueryMode.EISUKANA) for (output in literalOutputs) {
                                    assertTrue("Lost keyboard text transform: $input / $output / $bunsetsu / $limit",
                                        candidates.any { it.string == output && it.commitText == output && !it.generatedNumber })
                                }
                                for (candidate in candidates.filter { it.generatedNumber }) {
                                    assertEquals(input, candidate.number?.reading)
                                    assertEquals(candidate.string, candidate.commitText)
                                }
                                comparisons++
                            }
                        if (index / engines.size % 100 == 0) {
                            // Robolectric retains every Android log entry globally. This audit
                            // checks candidates, not logs; keep diagnostics from filling the heap.
                            if (shard == 0) org.robolectric.shadows.ShadowLog.clear()
                            File(directory, "progress-$shard.txt").writeText("index=$index/${readings.size}\nqueries=$comparisons\nraw=$rawCount\nrejected=$rejected\n")
                            removals.flush()
                        }
                    }
                    File(directory, "result-$shard.txt").writeText("PASS\nqueries=$comparisons\nraw=$rawCount\nrejected=$rejected\n")
                } finally {
                    engine.numberCandidateAuditObserver = null
                    removals.close()
                }
                comparisons
            } }.awaitAll().also { counts ->
                assertEquals(readings.size.toLong() * 32, counts.sum())
                File(directory, "summary.txt").writeText("PASS\nreadings=${readings.size}\nqueries=${counts.sum()}\n")
            }
        }
    }
}
