package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExactInputCandidatePromoterPerformanceTest {
    @Volatile
    private var listSink: List<Candidate>? = null

    @Volatile
    private var setSink: Set<String>? = null

    private val allocationMeter = ThreadAllocationMeter()

    @Test
    fun measuresTenThousandAndHundredThousandPromotionRules() {
        val warmupCandidates = List(64) { index ->
            candidate(if (index == 63) "ウォームアップ" else "候補-$index")
        }
        val warmupPromoter = ExactInputCandidatePromoter(setOf("ウォームアップ"))
        repeat(500) {
            listSink = warmupPromoter.promote("ウォームアップ", warmupCandidates)
        }

        listOf(22, 10_000, 100_000).forEach { ruleCount ->
            val words = List(ruleCount) { index -> "長期運用語-$index" }
            val setBuild = measureOnce {
                HashSet(words)
            }
            val rules = setBuild.value
            setSink = rules
            val target = words.last()
            val candidates = List(64) { index ->
                candidate(if (index == 63) target else "候補-$index")
            }
            val promoter = ExactInputCandidatePromoter(rules)
            repeat(20) {
                listSink = promoter.promote(target, candidates)
            }
            val hitTiming = measureRepeated(200) {
                listSink = promoter.promote(target, candidates)
            }
            val missAllocation = measureOnce {
                promoter.promote("登録外", candidates)
            }
            val missTiming = measureRepeated(200) {
                listSink = promoter.promote("登録外", candidates)
            }

            println(
                "EXACT_PROMOTION_RULE_SCALE " +
                    "rules=$ruleCount " +
                    "setBuildMs=${setBuild.elapsedNs.toMilliseconds()} " +
                    "setIndexAllocatedBytes=${setBuild.allocatedBytes} " +
                    "hitMedianUs=${hitTiming.medianNs.toMicroseconds()} " +
                    "hitP95Us=${hitTiming.p95Ns.toMicroseconds()} " +
                    "missMedianUs=${missTiming.medianNs.toMicroseconds()} " +
                    "missP95Us=${missTiming.p95Ns.toMicroseconds()} " +
                    "missAllocatedBytes=${missAllocation.allocatedBytes}",
            )

            assertEquals(target, promoter.promote(target, candidates).first().string)
            assertSame(candidates, missAllocation.value)
            assertTrue(setBuild.allocatedBytes > 0)
            assertTrue(hitTiming.medianNs < 100_000_000L)
            assertTrue(missAllocation.allocatedBytes < 1_024L)
        }
    }

    @Test
    fun measuresTenThousandAndHundredThousandCandidates() {
        val promoter = ExactInputCandidatePromoter(setOf("ふ"))
        val scenarios = listOf(10_000, 100_000).associateWith { candidateCount ->
            List(candidateCount) { index ->
                candidate(
                    string = if (index == candidateCount - 1) "ふ" else "候補-$index",
                    type = if (index % 1_000 == 0) {
                        CANDIDATE_TYPE_LEARNED_DICTIONARY
                    } else {
                        1
                    },
                )
            }
        }
        repeat(30) {
            listSink = promoter.promote("ふ", checkNotNull(scenarios[100_000]))
        }

        scenarios.forEach { (candidateCount, candidates) ->
            repeat(if (candidateCount == 10_000) 20 else 5) {
                listSink = promoter.promote("ふ", candidates)
            }
            val allocation = measureOnce {
                promoter.promote("ふ", candidates)
            }
            listSink = allocation.value
            val timing = measureRepeated(if (candidateCount == 10_000) 50 else 15) {
                listSink = promoter.promote("ふ", candidates)
            }
            val learnedCount = candidates.count {
                it.type == CANDIDATE_TYPE_LEARNED_DICTIONARY
            }

            println(
                "EXACT_PROMOTION_CANDIDATE_SCALE " +
                    "candidates=$candidateCount " +
                    "medianMs=${timing.medianNs.toMilliseconds()} " +
                    "p95Ms=${timing.p95Ns.toMilliseconds()} " +
                    "allocatedBytes=${allocation.allocatedBytes} " +
                    "bytesPerCandidate=" +
                    String.format(
                        Locale.ROOT,
                        "%.2f",
                        allocation.allocatedBytes.toDouble() / candidateCount,
                    ),
            )

            assertEquals(candidateCount, listSink?.size)
            assertEquals("ふ", listSink?.get(learnedCount)?.string)
            assertTrue(timing.medianNs < 100_000_000L)
            assertTrue(allocation.allocatedBytes in 1..candidateCount * 16L)
        }
    }

    private fun candidate(
        string: String,
        type: Byte = 1,
    ): Candidate = Candidate(
        string = string,
        type = type,
        length = string.length.toUByte(),
        score = 0,
    )

    private fun <T> measureOnce(block: () -> T): AllocationMeasurement<T> {
        val allocatedBefore = allocationMeter.currentThreadAllocatedBytes()
        val startedNs = System.nanoTime()
        val value = block()
        val elapsedNs = System.nanoTime() - startedNs
        val allocatedBytes =
            allocationMeter.currentThreadAllocatedBytes() - allocatedBefore
        return AllocationMeasurement(
            value = value,
            elapsedNs = elapsedNs,
            allocatedBytes = allocatedBytes,
        )
    }

    private fun measureRepeated(
        iterations: Int,
        block: () -> Unit,
    ): TimingMeasurement {
        val samples = LongArray(iterations)
        repeat(iterations) { index ->
            val startedNs = System.nanoTime()
            block()
            samples[index] = System.nanoTime() - startedNs
        }
        samples.sort()
        return TimingMeasurement(
            medianNs = samples[samples.size / 2],
            p95Ns = samples[((samples.size - 1) * 95) / 100],
        )
    }

    private fun Long.toMicroseconds(): String =
        String.format(Locale.ROOT, "%.3f", this / 1_000.0)

    private fun Long.toMilliseconds(): String =
        String.format(Locale.ROOT, "%.3f", this / 1_000_000.0)

    private data class AllocationMeasurement<T>(
        val value: T,
        val elapsedNs: Long,
        val allocatedBytes: Long,
    )

    private data class TimingMeasurement(
        val medianNs: Long,
        val p95Ns: Long,
    )

    /**
     * Android's unit-test compile classpath does not expose java.lang.management, even though
     * these tests run on a HotSpot JVM. Reflection keeps the benchmark out of production code
     * while still using the JVM's precise per-thread allocation counter at test runtime.
     */
    private class ThreadAllocationMeter {
        private val bean: Any
        private val allocatedBytesMethod: java.lang.reflect.Method

        init {
            val managementFactoryClass =
                Class.forName("java.lang.management.ManagementFactory")
            bean = requireNotNull(
                managementFactoryClass
                    .getMethod("getThreadMXBean")
                    .invoke(null)
            )

            val threadMxBeanClass = Class.forName("com.sun.management.ThreadMXBean")
            check(
                threadMxBeanClass
                    .getMethod("isThreadAllocatedMemorySupported")
                    .invoke(bean) as Boolean
            ) {
                "The test JVM must support per-thread allocation measurement"
            }
            if (
                !(threadMxBeanClass
                    .getMethod("isThreadAllocatedMemoryEnabled")
                    .invoke(bean) as Boolean)
            ) {
                threadMxBeanClass
                    .getMethod(
                        "setThreadAllocatedMemoryEnabled",
                        Boolean::class.javaPrimitiveType,
                    )
                    .invoke(bean, true)
            }
            allocatedBytesMethod = threadMxBeanClass.getMethod(
                "getThreadAllocatedBytes",
                Long::class.javaPrimitiveType,
            )
        }

        @Suppress("DEPRECATION")
        fun currentThreadAllocatedBytes(): Long =
            allocatedBytesMethod.invoke(bean, Thread.currentThread().id) as Long
    }
}
