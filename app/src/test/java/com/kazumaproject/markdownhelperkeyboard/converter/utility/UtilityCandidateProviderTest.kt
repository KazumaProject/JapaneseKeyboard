package com.kazumaproject.markdownhelperkeyboard.converter.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UtilityCandidateProviderTest {
    private val provider = UtilityCandidateProvider()

    @Test
    fun acceptanceExamplesAreAvailableWithDefaultSettings() {
        assertEquals("1412.5", texts("1250*1.13=").first())
        assertEquals("10.4394m", texts("411in").first())
        assertEquals("0°C", texts("32f").first())
        assertTrue(texts("5ft3in").toString(), "160.02cm" in texts("5ft3in"))
        assertTrue("7.72lb" in texts("3.5kg"))
    }

    @Test
    fun calculationRequiresExactlyOneBoundaryEquals() {
        assertFalse(provider.provide("1+2").hasCandidates)
        assertFalse(provider.provide("=1+2=").hasCandidates)
        assertFalse(provider.provide("1=2").hasCandidates)
        assertFalse(provider.provide("1+=").hasCandidates)
        assertEquals(UtilityTrigger.EXPLICIT_CALCULATION, provider.provide("=1+2").trigger)
        assertEquals(listOf("3", "3=1+2"), texts("=1+2"))
    }

    @Test
    fun precedencePowerUnaryImplicitMultiplicationAndScientificNotation() {
        assertEquals("14", texts("2+3*4=").first())
        assertEquals("512", texts("2^3^2=").first())
        assertEquals("-4", texts("-2^2=").first())
        assertEquals("0.25", texts("2^-2=").first())
        assertEquals("14", texts("2(3+4)=").first())
        assertEquals("2000", texts("2e3=").first())
    }

    @Test
    fun calculatorPercentAndModAreSupported() {
        assertEquals("110", texts("100+10%=").first())
        assertEquals("90", texts("100-10%=").first())
        assertEquals("10", texts("100*10%=").first())
        assertEquals("1000", texts("100/10%=").first())
        assertEquals("1", texts("10 mod 3=").first())
    }

    @Test
    fun functionsAndAngleModesAreSupported() {
        assertEquals("3", texts("sqrt(9)=").first())
        assertEquals("120", texts("5!=").first())
        assertEquals("1", texts("sin(90)=").first())
        val radians = UtilityCandidateConfig(angleMode = AngleMode.RADIANS)
        assertEquals("1", provider.provide("sin(pi/2)=", radians).candidates.first().text)
        assertEquals("90", texts("asin(1)=").first())
    }

    @Test
    fun integerPrecisionAppliesToCalculationExpressionAndUnitCandidates() {
        val calculationConfig = UtilityCandidateConfig(calculationPrecision = Precision.Integer)
        assertEquals(
            listOf("1", "1÷2=1"),
            provider.provide("1/2=", calculationConfig).candidates.map { it.text },
        )

        val unitConfig = UtilityCandidateConfig(
            unitTargets = UtilityCandidateConfig.defaultUnitTargets() +
                (
                    UnitCategory.LENGTH to listOf(
                        UnitTargetSetting(UnitId("length.cm"), Precision.Integer),
                    )
                ),
        )
        assertEquals(
            listOf("3cm"),
            provider.provide("1in", unitConfig).candidates.map { it.text },
        )
    }

    @Test
    fun fullWidthInputAndGroupingAreNormalized() {
        assertEquals("7", texts("１２３÷３＋２×２－３８＝").first())
        assertEquals("1234.5", texts("1,200+34.5=").first())
    }

    @Test
    fun invalidAndOversizedCalculationInputsReturnNoCandidate() {
        listOf("1/0=", "sqrt(-1)=", "log(0)=", "(-1)^0.5=").forEach {
            assertFalse(it, provider.provide(it).hasCandidates)
        }
        assertFalse(provider.provide("1".repeat(65) + "=").hasCandidates)
        assertFalse(provider.provide("(".repeat(33) + "1" + ")".repeat(33) + "=").hasCandidates)
        assertFalse(provider.provide("1".repeat(256)).hasCandidates)
    }

    @Test
    fun explicitAndAutomaticConversionTriggersAreDistinct() {
        assertEquals(UtilityTrigger.AUTOMATIC_UNIT_CONVERSION, provider.provide("411in").trigger)
        assertEquals(UtilityTrigger.EXPLICIT_UNIT_CONVERSION, provider.provide("411in>m").trigger)
        assertEquals(listOf("10.4394m"), texts("411in → m"))
        assertEquals(listOf("10.4394m"), texts("411in to m"))
        assertFalse(provider.provide("411in tom").hasCandidates)
    }

    @Test
    fun compoundUnitsAndSameDimensionAdditionAreSupported() {
        assertTrue("160.02cm" in texts("5'3\""))
        assertTrue("5400s" in texts("1h30min"))
        assertTrue("5400s" in texts("1h30m"))
        assertEquals(listOf("1.0668m"), texts("3ft+6in>m"))
        assertFalse(provider.provide("100km/2h").hasCandidates)
        assertFalse(provider.provide("2m*3m").hasCandidates)
        assertFalse(provider.provide("1m+2s").hasCandidates)
    }

    @Test
    fun temperatureAliasesAndAffineRestrictionsWork() {
        assertEquals("0°C", texts("32°F").first())
        assertEquals("0°C", texts("華氏32度").first())
        assertEquals("68°F", texts("摂氏20度").first { it.endsWith("°F") })
        assertFalse(provider.provide("20°C+10°C").hasCandidates)
    }

    @Test
    fun areaVolumeDataAndRegionalAliasesWork() {
        assertEquals(texts("1m2>ft2"), texts("1m^2>ft²"))
        assertEquals("1000mL", texts("1L>mL").single())
        assertEquals("1024B", texts("1KiB>B").single())
        assertEquals("1000B", texts("1kB>B").single())

        val japan = UtilityCandidateConfig(regionalUnitProfile = RegionalUnitProfile.JAPAN)
        val us = UtilityCandidateConfig(regionalUnitProfile = RegionalUnitProfile.UNITED_STATES)
        val uk = UtilityCandidateConfig(regionalUnitProfile = RegionalUnitProfile.UNITED_KINGDOM)
        assertEquals("200mL", provider.provide("1cup>mL", japan).candidates.single().text)
        assertEquals("236.588mL", provider.provide("1cup>mL", us).candidates.single().text)
        assertEquals("284.131mL", provider.provide("1cup>mL", uk).candidates.single().text)
        assertEquals("3785.41mL", provider.provide("1galUS>mL", uk).candidates.single().text)
    }

    @Test
    fun categoryCanDisableAutomaticTargetsWithoutDisablingExplicitConversion() {
        val config = UtilityCandidateConfig(
            unitTargets = UtilityCandidateConfig.defaultUnitTargets() +
                (UnitCategory.LENGTH to emptyList())
        )
        assertFalse(provider.provide("411in", config).hasCandidates)
        assertEquals("10.4394m", provider.provide("411in>m", config).candidates.single().text)
    }

    @Test
    fun automaticConversionReturnsEightDistinctTargetsInConfiguredOrder() {
        val targetIds = listOf(
            "length.m",
            "length.cm",
            "length.ft",
            "length.in",
            "length.km",
            "length.mi",
            "length.yd",
            "length.nmi",
        )
        val config = UtilityCandidateConfig(
            unitTargets = UtilityCandidateConfig.defaultUnitTargets() +
                (UnitCategory.LENGTH to targetIds.map { UnitTargetSetting(UnitId(it)) })
        )

        val candidates = provider.provide("1寸", config).candidates.map { it.text }

        assertEquals(8, candidates.size)
        assertEquals(candidates.size, candidates.distinct().size)
        val expectedSymbols = listOf("m", "cm", "ft", "in", "km", "mi", "yd", "nmi")
        candidates.zip(expectedSymbols).forEach { (candidate, symbol) ->
            assertTrue("$candidate should end with $symbol", candidate.endsWith(symbol))
        }
    }

    @Test
    fun fixedSeedRandomInputsNeverEscapeAsExceptions() {
        val random = Random(0x51C0DE)
        val alphabet = "0123456789+-*/^%=().,abcdefghijklmnopqrstuvwxyzπ°尺寸華氏摂氏→ "
        repeat(2_000) {
            val input = buildString {
                repeat(random.nextInt(0, 256)) { append(alphabet.random(random)) }
            }
            provider.provide(input)
        }
    }

    @Test
    fun maximumAcceptedInputStaysWithinOneFrameOnAverage() {
        val input = " ".repeat(198) + "1m"
        repeat(50) { provider.provide(input) }

        val iterations = 500
        val startedAt = System.nanoTime()
        repeat(iterations) {
            assertTrue(provider.provide(input).hasCandidates)
        }
        val averageMillis = (System.nanoTime() - startedAt) / iterations / 1_000_000.0
        assertTrue("average=${averageMillis}ms", averageMillis < 1_000.0 / 60.0)
    }

    private fun texts(input: String): List<String> = provider.provide(input).candidates.map { it.text }
}
