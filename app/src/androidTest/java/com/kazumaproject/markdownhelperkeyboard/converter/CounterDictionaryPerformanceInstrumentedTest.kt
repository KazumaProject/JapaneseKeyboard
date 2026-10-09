package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.counter.CounterDictionary
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CounterDictionaryPerformanceInstrumentedTest {
    @Test fun measureRuleDictionarySeparatelyAndVerifyAllGoldenCases() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("counterPerf") == "true")
        val label=args.getString("counterPerfLabel") ?: "rules"
        val context=ApplicationProvider.getApplicationContext<Context>()
        val report=StringBuilder()
        report.appendLine("includeAliases=true limit=Int.MAX_VALUE goldenCases=106")
        CounterConversionPerformanceInstrumentedTest.gc()
        val before=CounterConversionPerformanceInstrumentedTest.memory()
        val begin=System.nanoTime()
        val dictionary=context.assets.open("counter/counter_rules.dat").use { CounterDictionary.read(it) }
        val converter=dictionary.converter()
        report.appendLine("firstLoadAndConverterNs=${System.nanoTime()-begin}")
        report.appendLine("assetBytes=${dictionary.byteSize} primitiveIndexBytes=${dictionary.primitiveIndexBytes}")
        CounterConversionPerformanceInstrumentedTest.gc()
        report.appendLine("before=$before after=${CounterConversionPerformanceInstrumentedTest.memory()}")
        val rows=InstrumentationRegistry.getInstrumentation().context.assets.open("counter/cases.tsv").bufferedReader().readLines().drop(1).filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertEquals(106,rows.size)
        rows.forEach { row ->
            val result=converter.convert(row[0])
            if(row[1] == "@reject") assertTrue(row[0], result.candidates.isEmpty())
            else assertTrue(row[0],result.candidates.filter { it.counterId == row[1] }.map { it.value }.containsAll(row.drop(2).filter { it.isNotEmpty() }))
        }
        val inputs=rows.map { it[0] }
        repeat(100) { for(input in inputs) converter.convert(input) }
        val iterations=1000
        val samples=inputs.associateWith { LongArray(iterations) }
        CounterConversionPerformanceInstrumentedTest.gc()
        val allocatedBefore=CounterConversionPerformanceInstrumentedTest.stat("art.gc.bytes-allocated")
        val gcBefore=CounterConversionPerformanceInstrumentedTest.stat("art.gc.gc-count")
        var checksum=0L
        repeat(iterations) { index -> for(input in inputs) {
            val started=System.nanoTime()
            val result=converter.convert(input)
            samples.getValue(input)[index]=System.nanoTime()-started
            checksum+=result.candidates.sumOf { it.value.length }
        } }
        val allocatedAfter=CounterConversionPerformanceInstrumentedTest.stat("art.gc.bytes-allocated")
        val gcAfter=CounterConversionPerformanceInstrumentedTest.stat("art.gc.gc-count")
        report.appendLine("calls=${iterations*inputs.size} warmupRounds=100 checksum=$checksum allocatedBytes=${CounterConversionPerformanceInstrumentedTest.delta(allocatedBefore,allocatedAfter)} gcCount=${CounterConversionPerformanceInstrumentedTest.delta(gcBefore,gcAfter)}")
        samples.forEach { (input,values) ->
            report.appendLine("latency rules $input ${CounterConversionPerformanceInstrumentedTest.stats(values)}")
            report.appendLine("raw rules $input ${values.joinToString(",")}")
        }
        // Amplify retained heap above the noise floor. Report this as an estimate, not PSS/object accounting.
        val instances=arrayOfNulls<com.kazumaproject.counter.CounterConverter>(128)
        CounterConversionPerformanceInstrumentedTest.gc()
        val runtime=Runtime.getRuntime()
        val retainedBefore=runtime.totalMemory()-runtime.freeMemory()
        for(i in instances.indices) instances[i]=context.assets.open("counter/counter_rules.dat").use { CounterDictionary.read(it).converter() }
        CounterConversionPerformanceInstrumentedTest.gc()
        val retainedAfter=runtime.totalMemory()-runtime.freeMemory()
        val check=instances.sumOf { it!!.convert("いっぽん").candidates.first().value.length }
        report.appendLine("retainedHeapEstimateBytesPerDictionaryAndConverter=${(retainedAfter-retainedBefore)/instances.size} retainedBeforeBytes=$retainedBefore retainedAfterBytes=$retainedAfter instances=${instances.size} instanceChecksum=$check")
        File(context.filesDir,"counter-perf").apply { mkdirs() }.resolve("$label.txt").writeText(report.toString())
        println(report.lines().filterNot { it.startsWith("raw ") }.joinToString("\n"))
    }
}
