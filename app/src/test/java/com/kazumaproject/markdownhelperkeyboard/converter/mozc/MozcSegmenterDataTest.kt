package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MozcSegmenterDataTest {
    @Test
    fun generatedSegmenterDataLoadsRequiredTablesAndPosGroups() {
        val data = MozcTestAssets.segmenterData()

        assertTrue(data.prefixPenalty.size >= 2672)
        assertTrue(data.suffixPenalty.size >= 2672)
        assertTrue(data.boundary.bitArray.isNotEmpty())
        assertEquals(1841, data.posMatcher.getRequiredId("Unknown").toInt())
        assertEquals(2044, data.posMatcher.getRequiredId("Number").toInt())
        assertEquals(1922, data.posMatcher.getRequiredId("FirstName").toInt())
        assertEquals(1923, data.posMatcher.getRequiredId("LastName").toInt())
        assertTrue(data.posMatcher.isInGroup("GeneralNoun", 1851))
    }
}
