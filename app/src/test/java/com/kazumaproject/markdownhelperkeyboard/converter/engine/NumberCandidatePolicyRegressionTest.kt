package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.Node
import com.kazumaproject.quantity.QuantityDictionary
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class NumberCandidatePolicyRegressionTest {
    private lateinit var previous: QuantityDictionary
    @Before fun loadDictionary() {
        previous = QuantityRuntime.dictionary
        val asset = listOf(File("src/main/assets/quantity/quantity.dat"),
            File("app/src/main/assets/quantity/quantity.dat")).first { it.isFile }
        QuantityRuntime.install(QuantityDictionary.read(asset.readBytes()))
    }
    @After fun restoreDictionary() { QuantityRuntime.install(previous) }

    @Test fun compoundFormsInheritBaseKindForEveryNotationOrder() {
        val cases = listOf(
            Triple(NumberCandidateKind.PEOPLE, "ふたりぶん", listOf("2人分", "２人分", "二人分")),
            Triple(NumberCandidateKind.PEOPLE, "さんにんぶん", listOf("3人分", "３人分", "三人分")),
            Triple(NumberCandidateKind.TIME, "いっぷんかん", listOf("1分間", "１分間", "一分間")),
            Triple(NumberCandidateKind.TIME, "よんふんかん", listOf("4分間", "４分間", "四分間")))
        for ((kind, input, forms) in cases) for (order in NumberCandidateOrder.entries) {
            val config = PredictionConfig(numberCandidateOrder = order)
            assertEquals(input, order.indices.map(forms::get), NumberCandidateGenerator.generate(input, config).map { it.string })
            assertTrue("$input / $order", NumberCandidateGenerator.generate(input, config.copy(
                numberCandidateConfig = NumberCandidateConfig(disabledKinds = setOf(kind)))).isEmpty())
        }
    }

    @Test fun dictionarySuffixesPreserveEveryBuiltInCounterSetting() {
        var checked = 0
        for (counter in BuiltInCounter.entries) {
            val readings = CounterReadingCases.rows.getValue(counter.output).split(" ")
            for (suffix in QuantityRuntime.dictionary.suffixes.filter { it.base == counter.output }) {
                for (reading in readings) {
                    val input = reading + suffix.reading
                    val forms = ValidatedNumber.parseAll(input, NumberCandidateConfig())
                        .filter { it.builtInCounter == counter }.flatMap { it.basicForms }.toSet()
                    assertTrue("$counter / $input", forms.isNotEmpty())
                    val enabled = NumberCandidateGenerator.generate(input, PredictionConfig()).map { it.string }
                    assertTrue("$counter / $input", enabled.containsAll(forms))
                    val disabled = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(
                        disabledCounters = setOf(counter.storageId)))
                    assertTrue("$counter / $input", NumberCandidateGenerator.generate(input, disabled).none { it.string in forms })
                    checked++
                }
            }
        }
        assertTrue("The packaged dictionary must exercise compound counters", checked > 100)
    }

    @Test fun customUnitRemainsIndependentOfLegacyKindAndBuiltInSettings() {
        data class Case(val output: String, val reading: String,
            val kind: NumberCandidateKind? = null, val counter: BuiltInCounter? = null)
        for ((output, reading, kind, counter) in listOf(
            Case("人", "にん", kind = NumberCandidateKind.PEOPLE),
            Case("本", "ほん", counter = BuiltInCounter.LONG_OBJECTS))) {
            val unit = CustomNumberUnit("custom", output, reading)
            val settings = NumberCandidateConfig(units = listOf(unit),
                disabledKinds = setOfNotNull(kind),
                disabledCounters = setOfNotNull(counter?.storageId))
            val input = "さん${reading}ぶん"
            val enabled = NumberCandidateGenerator.generate(input, PredictionConfig(numberCandidateConfig = settings))
            assertTrue(input, enabled.any { it.string == "3${output}分" })
            assertTrue(input, NumberCandidateGenerator.generate(input, PredictionConfig(numberCandidateConfig =
                settings.copy(units = listOf(unit.copy(enabled = false))))).isEmpty())
        }
    }

    @Test fun spansSelectAnEnabledProofWithTheSameSurface() {
        val input = "にほん"
        val unit = CustomNumberUnit("custom", "本", "ほん")
        val node = Node(1, 1, 0, 0, tango = "2本", len = input.length.toShort(),
            yomiUsed = input, sPos = 0, isGeneratedNumber = true)
        for (builtInEnabled in listOf(false, true)) for (customEnabled in listOf(false, true)) {
            val settings = NumberCandidateConfig(units = listOf(unit.copy(enabled = customEnabled)),
                disabledCounters = if (builtInEnabled) emptySet() else setOf(BuiltInCounter.LONG_OBJECTS.storageId))
            val spans = NumberPathPolicy(input, PredictionConfig(numberCandidateConfig = settings)).spans(listOf(node))
            if (!builtInEnabled && !customEnabled) assertTrue(spans.isEmpty()) else {
                assertEquals("builtin=$builtInEnabled custom=$customEnabled", 1, spans.size)
                assertEquals(listOf("2本", "２本", "二本"), spans.single().forms)
                assertEquals(0, spans.single().inputStart)
                assertEquals(input.length, spans.single().inputEnd)
            }
        }
    }
}
