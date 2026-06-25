package com.kazumaproject.markdownhelperkeyboard.converter.bunsetsu

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JapaneseBunsetsuTraceGenerationTest {

    @Test
    fun generateJapaneseBunsetsuKotlinTraceAndQualityReport() = runBlocking {
        val inputs = loadFixtureInputs()
        assertEquals(15, inputs.size)
        assertFalse(inputs.any { input -> input.any { it.isAsciiLetter() } })

        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = KanaKanjiEngine().apply {
            applyDictionaryOverrideState(context)
            setPrivateField("graphBuilder", GraphBuilder())
            setPrivateField("findPath", FindPath())
        }
        val userDictionaryRepository = UserDictionaryRepository(EmptyUserWordDao)

        val kotlinRows = inputs.map { input ->
            val result = engine.getCandidatesOriginalWithBunsetsu(
                input = input,
                n = 10,
                mozcUtPersonName = false,
                mozcUTPlaces = false,
                mozcUTWiki = false,
                mozcUTNeologd = false,
                mozcUTWeb = false,
                userDictionaryRepository = userDictionaryRepository,
                learnRepository = null,
                isOmissionSearchEnable = false,
                enableTypoCorrectionJapaneseFlick = false,
                enableTypoCorrectionQwertyEnglish = false,
                typoCorrectionOffsetScore = 3000,
                omissionSearchOffsetScore = 1900,
            )
            JapaneseBunsetsuTraceRow(
                input = input,
                top1Value = result.candidates.firstOrNull()?.string.orEmpty(),
                top10Values = result.candidates.take(10).map { it.string },
                boundary = result.primarySplitPositions,
                candidateDetails = result.candidates.take(10).map { candidate ->
                    JapaneseBunsetsuCandidateDetail(
                        key = candidate.yomi.orEmpty(),
                        value = candidate.string,
                        contentKey = candidate.japaneseCandidateIdentity?.contentKey.orEmpty(),
                        contentValue = candidate.japaneseCandidateIdentity?.contentValue.orEmpty(),
                        lid = candidate.leftId?.toInt(),
                        rid = candidate.rightId?.toInt(),
                        cost = candidate.score,
                        wordCost = candidate.japaneseCandidateIdentity?.wordCost,
                        structureCost = candidate.japaneseCandidateIdentity?.structureCost,
                        splitPattern = candidate.japaneseCandidateIdentity?.splitPattern.orEmpty(),
                        candidateSource = candidate.japaneseCandidateIdentity?.candidateSource?.name.orEmpty(),
                    )
                },
                firstDivergenceStage = "KOTLIN_TRACE",
                reason = "Kotlin trace generated from KanaKanjiEngine.getCandidatesOriginalWithBunsetsu",
            )
        }

        val outputDir = reportDir()
        outputDir.mkdirs()
        writeKotlinTrace(File(outputDir, "kotlin_trace.tsv"), kotlinRows)
        writeQualityReport(
            file = File(outputDir, "japanese_bunsetsu_quality_report.tsv"),
            inputs = inputs,
            upstreamRows = readOptionalUpstreamTrace(),
            kotlinRows = kotlinRows,
        )

        assertEquals(inputs.size, kotlinRows.size)
    }

    private fun KanaKanjiEngine.setPrivateField(name: String, value: Any) {
        KanaKanjiEngine::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    private fun loadFixtureInputs(): List<String> {
        val stream = javaClass.classLoader?.getResourceAsStream("japanese_bunsetsu/inputs.tsv")
            ?: error("japanese_bunsetsu/inputs.tsv not found")
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    private fun reportDir(): File {
        val fromProperty = System.getProperty("japaneseBunsetsuReportDir")
        return if (fromProperty.isNullOrBlank()) {
            File("build/reports/japanese-bunsetsu")
        } else {
            File(fromProperty)
        }
    }

    private fun readOptionalUpstreamTrace(): Map<String, JapaneseBunsetsuTraceRow> {
        val path = System.getProperty("upstreamJapaneseBunsetsuTrace")
        if (path.isNullOrBlank()) return emptyMap()
        val file = File(path)
        if (!file.exists()) return emptyMap()
        return readTrace(file).associateBy { it.input }
    }

    private fun writeKotlinTrace(file: File, rows: List<JapaneseBunsetsuTraceRow>) {
        file.parentFile?.mkdirs()
        file.printWriter().use { out ->
            out.println("input\ttop1_value\ttop10_values\tboundary\tfirst_divergence_stage\treason")
            rows.forEach { row ->
                out.println(
                    listOf(
                        row.input,
                        row.top1Value,
                        row.top10Values.joinToString("|"),
                        row.boundary.joinToString(","),
                        row.firstDivergenceStage,
                        row.reason,
                    ).joinToString("\t") { it.escapeTsv() }
                )
            }
        }
    }

    private fun writeQualityReport(
        file: File,
        inputs: List<String>,
        upstreamRows: Map<String, JapaneseBunsetsuTraceRow>,
        kotlinRows: List<JapaneseBunsetsuTraceRow>,
    ) {
        val kotlinByInput = kotlinRows.associateBy { it.input }
        file.parentFile?.mkdirs()
        file.printWriter().use { out ->
            out.println(REPORT_HEADER.joinToString("\t"))
            inputs.forEach { input ->
                val upstream = upstreamRows[input]
                val kotlin = kotlinByInput.getValue(input)
                val top1Match = upstream?.top1Value == kotlin.top1Value
                val top10Contains = upstream?.top10Values?.firstOrNull()?.let {
                    it in kotlin.top10Values
                } ?: false
                val boundaryMatch = upstream?.boundary == kotlin.boundary
                val firstMissing = upstream?.top10Values
                    ?.firstOrNull { it !in kotlin.top10Values }
                    .orEmpty()
                val firstDivergenceStage = when {
                    upstream == null -> "UPSTREAM_TRACE_MISSING"
                    !top1Match -> "TOP1"
                    !top10Contains -> "TOP10"
                    !boundaryMatch -> "BOUNDARY"
                    else -> "MATCH"
                }
                val reason = when {
                    upstream == null ->
                        "Supply -DupstreamJapaneseBunsetsuTrace=/path/to/upstream_trace.tsv generated from mozc-master"

                    firstDivergenceStage == "MATCH" -> "Kotlin trace matches supplied upstream trace"
                    firstDivergenceStage == "TOP1" -> "top1 differs"
                    firstDivergenceStage == "TOP10" -> "upstream top1 is missing from Kotlin top10"
                    else -> "boundary differs"
                }

                val detail = kotlin.candidateDetails.firstOrNull()
                out.println(
                    listOf(
                        input,
                        upstream?.top1Value.orEmpty(),
                        kotlin.top1Value,
                        top1Match.toString(),
                        upstream?.top10Values.orEmpty().joinToString("|"),
                        kotlin.top10Values.joinToString("|"),
                        upstream?.boundary.orEmpty().joinToString(","),
                        kotlin.boundary.joinToString(","),
                        boundaryMatch.toString(),
                        firstDivergenceStage,
                        firstMissing,
                        reason,
                        detail?.key.orEmpty(),
                        detail?.value.orEmpty(),
                        detail?.contentKey.orEmpty(),
                        detail?.contentValue.orEmpty(),
                        detail?.lid?.toString().orEmpty(),
                        detail?.rid?.toString().orEmpty(),
                        detail?.cost?.toString().orEmpty(),
                        detail?.wordCost?.toString().orEmpty(),
                        detail?.structureCost?.toString().orEmpty(),
                        detail?.splitPattern.orEmpty().joinToString(","),
                        detail?.candidateSource.orEmpty(),
                        "",
                    ).joinToString("\t") { it.escapeTsv() }
                )
            }
        }
    }

    private fun readTrace(file: File): List<JapaneseBunsetsuTraceRow> {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split('\t')
        val index = header.withIndex().associate { it.value to it.index }
        return lines.drop(1).map { line ->
            val columns = line.split('\t')
            JapaneseBunsetsuTraceRow(
                input = columns.getOrEmpty(index, "input"),
                top1Value = columns.getOrEmpty(index, "top1_value"),
                top10Values = columns.getOrEmpty(index, "top10_values")
                    .split('|')
                    .filter { it.isNotEmpty() },
                boundary = columns.getOrEmpty(index, "boundary")
                    .split(',')
                    .mapNotNull { it.toIntOrNull() },
                firstDivergenceStage = columns.getOrEmpty(index, "first_divergence_stage"),
                reason = columns.getOrEmpty(index, "reason"),
            )
        }
    }

    private fun List<String>.getOrEmpty(index: Map<String, Int>, key: String): String =
        index[key]?.let { getOrNull(it) }.orEmpty()

    private fun String.escapeTsv(): String =
        replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    private data class JapaneseBunsetsuTraceRow(
        val input: String,
        val top1Value: String,
        val top10Values: List<String>,
        val boundary: List<Int>,
        val candidateDetails: List<JapaneseBunsetsuCandidateDetail> = emptyList(),
        val firstDivergenceStage: String,
        val reason: String,
    )

    private data class JapaneseBunsetsuCandidateDetail(
        val key: String,
        val value: String,
        val contentKey: String,
        val contentValue: String,
        val lid: Int?,
        val rid: Int?,
        val cost: Int?,
        val wordCost: Int?,
        val structureCost: Int?,
        val splitPattern: List<Int>,
        val candidateSource: String,
    )

    private object EmptyUserWordDao : UserWordDao {
        override fun getAll(): LiveData<List<UserWord>> = MutableLiveData(emptyList())
        override suspend fun getAllSuspend(): List<UserWord> = emptyList()
        override fun searchByReadingPrefix(prefix: String): LiveData<List<UserWord>> =
            MutableLiveData(emptyList())

        override suspend fun searchByReadingPrefixSuspend(prefix: String, limit: Int): List<UserWord> =
            emptyList()

        override suspend fun searchByReadingExactSuspend(reading: String): List<UserWord> = emptyList()
        override suspend fun existsDuplicateForUpdate(
            word: String,
            reading: String,
            excludeId: Int,
        ): Boolean = false

        override suspend fun commonPrefixSearchInUserDict(inputStr: String): List<UserWord> = emptyList()
        override suspend fun insert(userWord: UserWord) = Unit
        override suspend fun insertAll(words: List<UserWord>) = Unit
        override suspend fun update(userWord: UserWord) = Unit
        override suspend fun delete(id: Int) = Unit
        override suspend fun deleteAll() = Unit
    }

    private companion object {
        val REPORT_HEADER = listOf(
            "input",
            "upstream_top1_value",
            "kotlin_top1_value",
            "top1_match",
            "upstream_top10_values",
            "kotlin_top10_values",
            "upstream_boundary",
            "kotlin_boundary",
            "boundary_match",
            "first_divergence_stage",
            "first_missing_candidate",
            "reason",
            "key",
            "value",
            "content_key",
            "content_value",
            "lid",
            "rid",
            "cost",
            "wcost",
            "structure_cost",
            "split_pattern",
            "candidate_source",
            "rewriter_action",
        )
    }
}
