package com.kazumaproject.markdownhelperkeyboard.converter.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitTargetSettingsJsonCodecTest {
    private val codec = UnitTargetSettingsJsonCodec()

    @Test
    fun defaultsAndRoundTripPreserveExplicitEmptyCategories() {
        assertEquals(UtilityCandidateConfig.defaultUnitTargets(), codec.decodeOrDefault(null))
        val settings = UtilityCandidateConfig.defaultUnitTargets() +
            (UnitCategory.LENGTH to emptyList())
        assertEquals(settings, codec.decodeOrDefault(codec.encode(settings)))
    }

    @Test
    fun decimalPrecisionRoundTripsWithoutChangingTheDocumentVersion() {
        val settings = UtilityCandidateConfig.defaultUnitTargets() +
            (
                UnitCategory.LENGTH to listOf(
                    UnitTargetSetting(UnitId("length.m"), Precision.DecimalPlaces(2)),
                )
            )

        val encoded = codec.encode(settings)

        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("\"precision\":\"decimal:2\""))
        assertEquals(settings, codec.decodeOrDefault(encoded))
    }

    @Test
    fun legacyIntegerAndNumericPrecisionsRemainReadable() {
        val decoded = codec.decodeOrDefault(
            """{"version":1,"categories":{"length":[
                {"unitId":"length.m","precision":"integer"},
                {"unitId":"length.cm","precision":"1"},
                {"unitId":"length.ft","precision":"2"}
            ]}}""".trimIndent(),
        ).getValue(UnitCategory.LENGTH)

        assertEquals(Precision.DecimalPlaces(0), decoded[0].precision)
        assertEquals(Precision.SignificantDigits(1), decoded[1].precision)
        assertEquals(Precision.SignificantDigits(2), decoded[2].precision)
    }

    @Test
    fun invalidDecimalPrecisionIsDiscarded() {
        val decoded = codec.decodeOrDefault(
            """{"version":1,"categories":{"length":[
                {"unitId":"length.m","precision":"decimal:-1"},
                {"unitId":"length.cm","precision":"decimal:16"},
                {"unitId":"length.ft","precision":"decimal:2"}
            ]}}""".trimIndent(),
        ).getValue(UnitCategory.LENGTH)

        assertEquals(listOf("length.ft"), decoded.map { it.unitId.value })
        assertEquals(Precision.DecimalPlaces(2), decoded.single().precision)
    }

    @Test
    fun corruptDocumentFallsBackToDefaults() {
        assertEquals(UtilityCandidateConfig.defaultUnitTargets(), codec.decodeOrDefault("{"))
        assertEquals(
            UtilityCandidateConfig.defaultUnitTargets(),
            codec.decodeOrDefault("""{"version":999,"categories":{}}"""),
        )
    }

    @Test
    fun invalidDuplicateWrongCategoryAndExcessTargetsAreRepaired() {
        val json = """
            {"version":1,"categories":{"length":[
              {"unitId":"length.m","precision":"6"},
              {"unitId":"length.m","precision":"5"},
              {"unitId":"mass.kg","precision":"6"},
              {"unitId":"unknown.id","precision":"auto"},
              {"unitId":"length.cm","precision":"0"},
              {"unitId":"length.ft","precision":"5"},
              {"unitId":"length.in","precision":"5"},
              {"unitId":"length.km","precision":"5"},
              {"unitId":"length.mi","precision":"5"},
              {"unitId":"length.yd","precision":"5"},
              {"unitId":"length.nmi","precision":"5"},
              {"unitId":"length.mm","precision":"5"},
              {"unitId":"length.nm","precision":"5"},
              {"unitId":"length.um","precision":"5"}
            ]}}
        """.trimIndent()
        val decoded = codec.decodeOrDefault(json)
        assertEquals(
            listOf(
                "length.m",
                "length.ft",
                "length.in",
                "length.km",
                "length.mi",
                "length.yd",
                "length.nmi",
                "length.mm",
            ),
            decoded.getValue(UnitCategory.LENGTH).map { it.unitId.value },
        )
        assertTrue(decoded.getValue(UnitCategory.AREA).isNotEmpty())
    }
}
