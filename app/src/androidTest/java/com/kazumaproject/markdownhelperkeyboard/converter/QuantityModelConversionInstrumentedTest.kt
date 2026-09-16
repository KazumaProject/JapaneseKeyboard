package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuantityModelConversionInstrumentedTest {
    private fun entryPoint() = EntryPointAccessors.fromApplication(
        ApplicationProvider.getApplicationContext<Context>(), KanaKanjiEngineEntryPoint::class.java)

    private fun request(input: String, entry: KanaKanjiEngineEntryPoint, bunsetsu: Boolean = false,
                        config: PredictionConfig = PredictionConfig()) = KanaKanjiQueryRequest(
        input, CandidateQueryMode.CONVERSION, bunsetsu, 8, false, false, false, false, false,
        entry.userDictionaryRepository(), null, false, false, false, 3000, 1900, 20, config, true)

    @Test fun previouslyLostQuantitiesKeepTheirBasicForms() = runBlocking {
        val entry = entryPoint()
        val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), ConversionBackend.LEGACY)
        val rows = InstrumentationRegistry.getInstrumentation().context.assets.open("quantity-regressions.tsv")
            .bufferedReader().use { it.readLines() }.filter { !it.startsWith("#") && it.isNotBlank() }
        assertEquals(78, rows.size)
        for (row in rows) {
            val (input, forms) = row.split('\t')
            val result = session.query(request(input, entry))
            assertNotNull("The released scoring model must load", QuantityRuntime.scoringModel)
            val expected = forms.split('|')
            assertTrue("$input: ${result.candidates.map { it.string }}", result.candidates.any { candidate ->
                expected.any { form -> Regex("(?<![0-9０-９〇零一二三四五六七八九十百千万億兆京])" + Regex.escape(form))
                    .containsMatchIn(candidate.string) }
            })
        }
    }

    @Test fun missingOptionalModelPreservesCompatibilityCandidates() = runBlocking {
        val entry = entryPoint()
        val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), ConversionBackend.LEGACY)
        session.query(request("あ", entry))
        val previous = QuantityRuntime.scoringModel
        assertNotNull(previous)
        try {
            QuantityRuntime.installScoringModel(null, "", "")
            val rows = InstrumentationRegistry.getInstrumentation().context.assets.open("quantity-regressions.tsv")
                .bufferedReader().use { it.readLines() }.filter { !it.startsWith("#") && it.isNotBlank() }
            for (row in rows) {
                val (input, forms) = row.split('\t')
                val candidates = session.query(request(input, entry)).candidates
                assertTrue("Compatibility: $input: ${candidates.map { it.string }}", candidates.any { candidate ->
                    forms.split('|').any { form -> Regex("(?<![0-9０-９〇零一二三四五六七八九十百千万億兆京])" + Regex.escape(form))
                        .containsMatchIn(candidate.string) }
                })
            }
        } finally { QuantityRuntime.installScoringModel(previous, previous!!.posFingerprint, previous.connectionFingerprint) }
    }

    @Test fun semanticRankingAndCommitSegmentsAgreeAcrossBackendsAndNotationOrders() = runBlocking {
        val entry = entryPoint()
        val cases = linkedMapOf(
            "さんびゃくごじゅうえんつかう" to "350円使う",
            "さんびゃくごじゅうにえんをつかった" to "352円を使った",
            "じゅうにほんをつかう" to "12本を使う",
            "よんふんかんまつ" to "4分間待つ",
            "にじゅうさんじごふんにでる" to "23時5分に出る",
            "くるまにだいにわかれてのる" to "車2台に分かれて乗る",
            "とらっくのにだいにのる" to "トラックの荷台に乗る",
            "ごかいだけ" to "誤解だけ", "はっけんだけ" to "発見だけ",
            "いつかだけ" to "いつかだけ", "にほんだけ" to "日本だけ", "きゅうえんする" to "救援する",
        )
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
            for (bunsetsu in listOf(false, true)) for (order in NumberCandidateOrder.entries) for ((input, expected) in cases) {
                val config = PredictionConfig(numberCandidateOrder = order)
                val result = session.query(request(input, entry, bunsetsu, config))
                val first = result.candidates.first()
                assertEquals("$backend/$bunsetsu/$order/$input: ${result.candidates.take(8).map { it.string }}",
                    expected, NumberPathPolicy(input, config).key(first.string, first.numberSpans))
                assertEquals(first.string, first.commitText)
                val segments = result.candidateSegmentsByString[first.string].orEmpty()
                assertTrue("Missing commit segments for $input", segments.isNotEmpty())
                assertEquals(0, segments.first().inputStart)
                assertEquals(input.length, segments.last().inputEnd)
                assertTrue(segments.zipWithNext().all { it.first.inputEnd == it.second.inputStart })
                assertEquals(first.commitText, segments.joinToString("") { it.output })
            }
        }
    }

    @Test fun ambiguousVehicleReadingRetainsBothInterpretations() = runBlocking {
        val entry = entryPoint()
        for (backend in ConversionBackend.entries) {
            val input = "にだいにのる"
            val result = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend).query(request(input, entry))
            val policy = NumberPathPolicy(input, PredictionConfig())
            val outputs = result.candidates.map { policy.key(it.string, it.numberSpans) }
            assertTrue(outputs.toString(), "2台に乗る" in outputs)
            assertTrue(outputs.toString(), "荷台に乗る" in outputs)
        }
    }
}
