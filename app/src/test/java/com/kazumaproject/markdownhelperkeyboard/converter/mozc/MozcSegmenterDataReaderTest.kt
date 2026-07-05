package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

class MozcSegmenterDataReaderTest {

    @Test
    fun readsBundledSegmenterData() {
        val data = FileInputStream(asset("mozc/segmenter.dat")).use {
            MozcSegmenterDataReader().read(it)
        }

        assertTrue(data.lNumElements > 0)
        assertTrue(data.rNumElements > 0)
        assertTrue(data.lTable.isNotEmpty())
        assertTrue(data.rTable.isNotEmpty())
        assertTrue(data.bitArrayData.isNotEmpty())
        assertTrue(data.boundaryData.isNotEmpty())
    }

    @Test
    fun rejectsInvalidMagic() {
        val bytes = asset("mozc/segmenter.dat").readBytes()
        bytes[0] = 'B'.code.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            MozcSegmenterDataReader().read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun segmenterRejectsOutOfRangeIds() {
        val data = FileInputStream(asset("mozc/segmenter.dat")).use {
            MozcSegmenterDataReader().read(it)
        }
        val segmenter = MozcSegmenter(data)

        assertThrows(IllegalArgumentException::class.java) {
            segmenter.isBoundary(rid = data.lTable.size, lid = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            segmenter.isBoundary(rid = 0, lid = data.rTable.size)
        }
    }

    @Test
    fun readsNodeAttributeTable() {
        val table = FileInputStream(asset("mozc/node_attribute_by_lid.dat")).use {
            MozcNodeAttributeTableReader().read(it)
        }

        assertEquals(1, table.attributesFor(16))
        assertEquals(0, table.attributesFor(Int.MAX_VALUE))
    }

    private fun asset(path: String): File {
        val candidates = listOf(
            File("app/src/main/assets", path),
            File("src/main/assets", path),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing test asset: $path")
    }
}
