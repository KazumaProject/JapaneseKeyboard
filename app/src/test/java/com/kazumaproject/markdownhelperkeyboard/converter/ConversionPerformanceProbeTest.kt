package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Method
import java.security.MessageDigest
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversionPerformanceProbeTest {

    @Test
    fun measureBunsetsuConversionPerformance() = runBlocking {
        assumeTrue(
            System.getProperty("conversionPerfProbe") == "true" ||
                System.getenv("CONVERSION_PERF_PROBE") == "true",
        )

        val label = System.getProperty("conversionPerfLabel")
            ?: System.getenv("CONVERSION_PERF_LABEL")
            ?: "run"
        val warmup = System.getProperty("conversionPerfWarmup")?.toIntOrNull()
            ?: System.getenv("CONVERSION_PERF_WARMUP")?.toIntOrNull()
            ?: 20
        val iterations = System.getProperty("conversionPerfIterations")?.toIntOrNull()
            ?: System.getenv("CONVERSION_PERF_ITERATIONS")?.toIntOrNull()
            ?: 100
        val inputs = listOf(
            "きょう",
            "きょうはいいてんきですね",
            "とうきょうとちよだく",
            "けいたいでにほんごをへんかんする",
            "わたしはきのうともだちとえきまえであいました",
            "このあぷりのへんかんこうほをこうそくにしたい",
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = productionDictionaryReader(context)
        lateinit var connectionMatrix: ConnectionMatrix.CostTable
        val coldConnectionMatrixInitNs = measureNanoTime {
            connectionMatrix = reader.loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)
        }
        val cachedConnectionMatrix = reader.loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)
        lateinit var engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
        val coldEngineInitNs = measureNanoTime {
            engine = TestEngineFactory.create(connectionMatrix = connectionMatrix)
        }
        val userDictionaryRepository = mock<UserDictionaryRepository>()
        whenever(userDictionaryRepository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())

        val firstResults = LinkedHashMap<String, List<Candidate>>()
        val firstConversionNs = inputs.associateWith { input ->
            measureNanoTime {
                firstResults[input] = engine.convertForProbe(input, userDictionaryRepository)
            }
        }

        repeat(warmup) {
            inputs.forEach { input ->
                engine.convertForProbe(input, userDictionaryRepository)
            }
        }

        System.gc()
        Thread.sleep(100)

        val allocationMeter = currentThreadAllocationMeter()
        val allocatedBefore = allocationMeter?.currentAllocatedBytes() ?: -1L
        val usedHeapBefore = usedHeapBytes()
        val results = LinkedHashMap<String, List<Candidate>>()
        val warmConversionNs = inputs.associateWith { input ->
            measureNanoTime {
                repeat(iterations) {
                    results[input] = engine.convertForProbe(input, userDictionaryRepository)
                }
            }
        }
        val continuousInput = "このあぷりのへんかんこうほをこうそくにしたい"
        var continuousResult: List<Candidate> = emptyList()
        val continuousElapsedNs = measureNanoTime {
            repeat(iterations) {
                continuousResult = engine.convertForProbe(continuousInput, userDictionaryRepository)
            }
        }
        val usedHeapAfter = usedHeapBytes()
        val allocatedAfter = allocationMeter?.currentAllocatedBytes() ?: -1L
        val conversions = iterations * (inputs.size + 1)
        val allocatedBytes = if (allocatedBefore >= 0 && allocatedAfter >= allocatedBefore) {
            allocatedAfter - allocatedBefore
        } else {
            -1L
        }

        val report = buildString {
            appendLine("label=$label")
            appendLine("warmup=$warmup")
            appendLine("iterations=$iterations")
            appendLine("inputCount=${inputs.size}")
            appendLine("conversions=$conversions")
            appendLine("connectionMatrixColdInitNs=$coldConnectionMatrixInitNs")
            appendLine("engineColdInitNs=$coldEngineInitNs")
            appendLine("connectionMatrixCachedSameInstance=${connectionMatrix === cachedConnectionMatrix}")
            appendLine("connectionMatrixMatrixSize=${connectionMatrix.matrixSize}")
            appendLine("connectionMatrixEntryCount=${connectionMatrix.entryCount}")
            appendLine("totalAllocatedBytes=$allocatedBytes")
            appendLine("avgAllocatedBytes=${if (allocatedBytes >= 0) allocatedBytes / conversions else -1}")
            appendLine("heapGrowthBytes=${usedHeapAfter - usedHeapBefore}")
            appendLine("fingerprint=${results.fingerprint()}")
            appendLine("firstConversionUs")
            firstConversionNs.forEach { (input, elapsedNs) ->
                appendLine("$input\t${elapsedNs / 1_000.0}")
            }
            appendLine("warmAvgConversionUs")
            warmConversionNs.forEach { (input, elapsedNs) ->
                appendLine("$input\t${elapsedNs / iterations / 1_000.0}")
            }
            appendLine("sameInputContinuousAvgUs")
            appendLine("$continuousInput\t${continuousElapsedNs / iterations / 1_000.0}")
            appendLine("firstCandidates")
            firstResults.forEach { (input, candidates) ->
                append(input)
                append('\t')
                append(candidates.take(12).joinToString(" / ") { "${it.string}:${it.score}" })
                appendLine()
            }
            appendLine("warmCandidates")
            results.forEach { (input, candidates) ->
                append(input)
                append('\t')
                append(candidates.take(12).joinToString(" / ") { "${it.string}:${it.score}" })
                appendLine()
            }
            appendLine("sameInputContinuousCandidates")
            append(continuousInput)
            append('\t')
            append(continuousResult.take(12).joinToString(" / ") { "${it.string}:${it.score}" })
            appendLine()
        }

        val reportDir = File("build/reports/conversion-perf").apply { mkdirs() }
        File(reportDir, "$label.txt").writeText(report)
    }

    private fun productionDictionaryReader(context: Context): DictionaryBinaryReader {
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        return DictionaryBinaryReader(
            resolver = DictionarySourceResolver(context, store),
            store = store,
        )
    }

    private suspend fun com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine.convertForProbe(
        input: String,
        userDictionaryRepository: UserDictionaryRepository,
    ): List<Candidate> =
        getCandidatesWithBunsetsuSeparation(
            input = input,
            n = 4,
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
            beamWidth = 20,
        ).candidates

    private class AllocationMeter(
        private val bean: Any,
        private val method: Method,
    ) {
        fun currentAllocatedBytes(): Long =
            (method.invoke(bean) as? Long) ?: -1L
    }

    private fun currentThreadAllocationMeter(): AllocationMeter? = runCatching {
        val factoryClass = Class.forName("java.lang.management.ManagementFactory")
        val bean = factoryClass.getMethod("getThreadMXBean").invoke(null)
            ?: return@runCatching null
        val threadMxBeanClass = Class.forName("com.sun.management.ThreadMXBean")
        if (!threadMxBeanClass.isInstance(bean)) return@runCatching null
        val supported = threadMxBeanClass.getMethod("isThreadAllocatedMemorySupported")
            .invoke(bean) as? Boolean ?: false
        if (!supported) return@runCatching null

        val enabledMethod = threadMxBeanClass.getMethod("isThreadAllocatedMemoryEnabled")
        val setEnabledMethod = threadMxBeanClass.getMethod(
            "setThreadAllocatedMemoryEnabled",
            java.lang.Boolean.TYPE,
        )
        if (enabledMethod.invoke(bean) != true) {
            setEnabledMethod.invoke(bean, true)
        }

        val getAllocatedMethod = threadMxBeanClass.getMethod("getCurrentThreadAllocatedBytes")
        AllocationMeter(bean, getAllocatedMethod)
    }.getOrNull()

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun Map<String, List<Candidate>>.fingerprint(): String {
        val payload = entries.joinToString("\n") { (input, candidates) ->
            input + "\t" + candidates.joinToString("\u001f") {
                listOf(
                    it.string,
                    it.type.toString(),
                    it.length.toString(),
                    it.score.toString(),
                    it.yomi.orEmpty(),
                    it.leftId?.toString().orEmpty(),
                    it.rightId?.toString().orEmpty(),
                ).joinToString("\u001e")
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
