package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ExactInputCandidatePromoterPerformanceInstrumentedTest {
    @Volatile
    private var listSink: List<Candidate>? = null

    @Volatile
    private var setSink: Set<String>? = null

    @Volatile
    private var wordListSink: List<String>? = null

    @Test
    fun measuresRuleAndCandidateScaleOnAndroidRuntime() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString(PERFORMANCE_PROBE_ARGUMENT) == "true")

        warmUp()
        val ruleResults = listOf(22, 10_000, 100_000).map(::measureRuleScale)
        val candidateResults = listOf(10_000, 100_000).map(::measureCandidateScale)

        val report = buildString {
            appendLine("EXACT_INPUT_PROMOTION_ANDROID_REPORT")
            appendLine(
                "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                    "api=${android.os.Build.VERSION.SDK_INT}",
            )
            appendLine(
                "rules\tsetBuildMs\tsetBuildAllocatedBytes\tsetRetainedHeapDeltaBytes\t" +
                    "hitP50Us\thitP95Us\thitAllocatedBytesPerCall\t" +
                    "missP50Us\tmissP95Us\tmissAllocatedBytesPerCall",
            )
            ruleResults.forEach { appendLine(it.asTsv()) }
            appendLine(
                "candidates\tpromotionP50Ms\tpromotionP95Ms\t" +
                    "promotionAllocatedBytesPerCall\tbytesPerCandidate",
            )
            candidateResults.forEach { appendLine(it.asTsv()) }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, REPORT_DIRECTORY).apply { mkdirs() }
            .resolve(REPORT_FILE)
            .writeText(report)
        println(report)
    }

    private fun warmUp() {
        val candidates = List(64) { index ->
            candidate(if (index == 63) WARMUP_INPUT else "候補-$index")
        }
        val promoter = ExactInputCandidatePromoter(hashSetOf(WARMUP_INPUT))
        repeat(1_000) {
            listSink = promoter.promote(WARMUP_INPUT, candidates)
        }
    }

    private fun measureRuleScale(ruleCount: Int): RuleScaleResult {
        val words = List(ruleCount) { index -> "長期運用語-$index" }
        wordListSink = words
        setSink = null
        forceGc()
        val heapBefore = usedHeapBytes()
        val allocatedBefore = allocatedBytes()
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val rules = HashSet(words)
        val buildElapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs
        val buildAllocatedBytes = allocatedBytes() - allocatedBefore
        setSink = rules
        forceGc()
        val retainedHeapDeltaBytes = usedHeapBytes() - heapBefore

        val target = words.last()
        val candidates = List(64) { index ->
            candidate(if (index == 63) target else "候補-$index")
        }
        val promoter = ExactInputCandidatePromoter(rules)
        repeat(50) {
            listSink = promoter.promote(target, candidates)
        }
        val hitTiming = measureRepeated(300) {
            listSink = promoter.promote(target, candidates)
        }
        val hitAllocatedBytesPerCall = measureAllocatedBytesPerCall(200) {
            listSink = promoter.promote(target, candidates)
        }
        val missTiming = measureRepeated(300) {
            listSink = promoter.promote(UNREGISTERED_INPUT, candidates)
        }
        val missAllocatedBytesPerCall = measureAllocatedBytesPerCall(500) {
            listSink = promoter.promote(UNREGISTERED_INPUT, candidates)
        }

        assertEquals(target, promoter.promote(target, candidates).first().string)
        assertSame(candidates, promoter.promote(UNREGISTERED_INPUT, candidates))
        return RuleScaleResult(
            rules = ruleCount,
            setBuildNs = buildElapsedNs,
            setBuildAllocatedBytes = buildAllocatedBytes,
            setRetainedHeapDeltaBytes = retainedHeapDeltaBytes,
            hitTiming = hitTiming,
            hitAllocatedBytesPerCall = hitAllocatedBytesPerCall,
            missTiming = missTiming,
            missAllocatedBytesPerCall = missAllocatedBytesPerCall,
        )
    }

    private fun measureCandidateScale(candidateCount: Int): CandidateScaleResult {
        val promoter = ExactInputCandidatePromoter(hashSetOf(TARGET_INPUT))
        val candidates = List(candidateCount) { index ->
            candidate(
                string = if (index == candidateCount - 1) TARGET_INPUT else "候補-$index",
                type = if (index % 1_000 == 0) {
                    CANDIDATE_TYPE_LEARNED_DICTIONARY
                } else {
                    1
                },
            )
        }
        repeat(if (candidateCount == 10_000) 30 else 10) {
            listSink = promoter.promote(TARGET_INPUT, candidates)
        }
        val allocatedBytesPerCall = measureAllocatedBytesPerCall(
            iterations = if (candidateCount == 10_000) 20 else 10,
        ) {
            listSink = promoter.promote(TARGET_INPUT, candidates)
        }
        val timing = measureRepeated(if (candidateCount == 10_000) 50 else 20) {
            listSink = promoter.promote(TARGET_INPUT, candidates)
        }
        val result = promoter.promote(TARGET_INPUT, candidates)
        val learnedCount = candidates.count {
            it.type == CANDIDATE_TYPE_LEARNED_DICTIONARY
        }

        assertEquals(candidateCount, result.size)
        assertEquals(TARGET_INPUT, result[learnedCount].string)
        assertTrue(allocatedBytesPerCall > 0L)
        return CandidateScaleResult(
            candidates = candidateCount,
            timing = timing,
            allocatedBytesPerCall = allocatedBytesPerCall,
        )
    }

    private fun measureAllocatedBytesPerCall(
        iterations: Int,
        block: () -> Unit,
    ): Long {
        val before = allocatedBytes()
        repeat(iterations) {
            block()
        }
        return (allocatedBytes() - before) / iterations
    }

    private fun measureRepeated(
        iterations: Int,
        block: () -> Unit,
    ): TimingMeasurement {
        val samples = LongArray(iterations)
        repeat(iterations) { index ->
            val startedNs = SystemClock.elapsedRealtimeNanos()
            block()
            samples[index] = SystemClock.elapsedRealtimeNanos() - startedNs
        }
        samples.sort()
        return TimingMeasurement(
            p50Ns = samples[((samples.size - 1) * 50) / 100],
            p95Ns = samples[((samples.size - 1) * 95) / 100],
        )
    }

    private fun forceGc() {
        repeat(3) {
            Runtime.getRuntime().gc()
            SystemClock.sleep(50)
        }
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun allocatedBytes(): Long =
        checkNotNull(Debug.getRuntimeStat("art.gc.bytes-allocated")).toLong()

    private fun candidate(
        string: String,
        type: Byte = 1,
    ): Candidate = Candidate(
        string = string,
        type = type,
        length = string.length.toUByte(),
        score = 0,
    )

    private data class RuleScaleResult(
        val rules: Int,
        val setBuildNs: Long,
        val setBuildAllocatedBytes: Long,
        val setRetainedHeapDeltaBytes: Long,
        val hitTiming: TimingMeasurement,
        val hitAllocatedBytesPerCall: Long,
        val missTiming: TimingMeasurement,
        val missAllocatedBytesPerCall: Long,
    ) {
        fun asTsv(): String = listOf(
            rules,
            setBuildNs.asMilliseconds(),
            setBuildAllocatedBytes,
            setRetainedHeapDeltaBytes,
            hitTiming.p50Ns.asMicroseconds(),
            hitTiming.p95Ns.asMicroseconds(),
            hitAllocatedBytesPerCall,
            missTiming.p50Ns.asMicroseconds(),
            missTiming.p95Ns.asMicroseconds(),
            missAllocatedBytesPerCall,
        ).joinToString("\t")
    }

    private data class CandidateScaleResult(
        val candidates: Int,
        val timing: TimingMeasurement,
        val allocatedBytesPerCall: Long,
    ) {
        fun asTsv(): String = listOf(
            candidates,
            timing.p50Ns.asMilliseconds(),
            timing.p95Ns.asMilliseconds(),
            allocatedBytesPerCall,
            String.format(
                Locale.ROOT,
                "%.2f",
                allocatedBytesPerCall.toDouble() / candidates,
            ),
        ).joinToString("\t")
    }

    private data class TimingMeasurement(
        val p50Ns: Long,
        val p95Ns: Long,
    )

    private companion object {
        const val PERFORMANCE_PROBE_ARGUMENT = "exactInputPromotionPerfProbe"
        const val REPORT_DIRECTORY = "conversion-perf"
        const val REPORT_FILE = "exact-input-promotion-android.txt"
        const val WARMUP_INPUT = "ウォームアップ"
        const val TARGET_INPUT = "ふ"
        const val UNREGISTERED_INPUT = "登録外"

        fun Long.asMicroseconds(): String =
            String.format(Locale.ROOT, "%.3f", this / 1_000.0)

        fun Long.asMilliseconds(): String =
            String.format(Locale.ROOT, "%.3f", this / 1_000_000.0)
    }
}
