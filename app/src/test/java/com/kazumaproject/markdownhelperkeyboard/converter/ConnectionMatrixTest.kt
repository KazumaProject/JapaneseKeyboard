package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.random.Random

class ConnectionMatrixTest {

    @Test
    fun shortArrayCostTablePreservesSignedBigEndianValues() {
        val matrixSize = 3
        val values = shortArrayOf(
            Short.MIN_VALUE, -2, -1,
            0, 1, 2,
            127, 1024, Short.MAX_VALUE,
        )
        val costTable = ConnectionMatrix.fromShortArray(values, matrixSize)

        assertEquals(Short.MIN_VALUE.toInt(), costTable.cost(0, 0))
        assertEquals(-1, costTable.cost(0, 2))
        assertEquals(0, costTable.cost(1, 0))
        assertEquals(2, costTable.cost(1, 2))
        assertEquals(Short.MAX_VALUE.toInt(), costTable.cost(2, 2))
    }

    @Test
    fun bundledConnectionMatrixMatchesRawBinaryForEveryEntry() {
        val costTable = loadBundledConnectionMatrix()
        assertEquals(costTable.matrixSize * costTable.matrixSize, costTable.entryCount)

        var entryIndex = 0
        var sawZero = false
        var sawPositive = false

        openBundledConnectionIdRaw { raw, _ ->
            raw.use {
                readRawShorts(it) { expected ->
                    val rid = entryIndex / costTable.matrixSize
                    val lid = entryIndex % costTable.matrixSize
                    val actual = costTable.cost(rid, lid)
                    if (actual != expected.toInt()) {
                        fail("connection cost mismatch at rid=$rid lid=$lid index=$entryIndex: expected=${expected.toInt()} actual=$actual")
                    }
                    if (expected.toInt() == 0) sawZero = true
                    if (expected > 0) sawPositive = true
                    entryIndex++
                }
            }
        }

        assertEquals(costTable.entryCount, entryIndex)
        assertTrue("connectionId.dat should contain zero costs", sawZero)
        assertTrue("connectionId.dat should contain positive costs", sawPositive)
    }

    @Test
    fun bundledConnectionMatrixMatchesFixedAndRandomPositions() {
        val costTable = loadBundledConnectionMatrix()
        val values = readBundledConnectionIds()
        val matrixSize = costTable.matrixSize

        val fixedPositions = buildList {
            add(0 to 0)
            add(0 to matrixSize - 1)
            add(1 to 0)
            add(1 to matrixSize - 1)
            add(matrixSize / 2 to matrixSize / 2)
            add(matrixSize - 1 to 0)
            add(matrixSize - 1 to matrixSize - 1)
        }
        val random = Random(867)
        val randomPositions = List(2048) {
            random.nextInt(matrixSize) to random.nextInt(matrixSize)
        }

        (fixedPositions + randomPositions).forEach { (rid, lid) ->
            val index = rid * matrixSize + lid
            assertEquals(values[index].toInt(), costTable.cost(rid, lid))
        }
    }

    @Test
    fun connectionIdReaderRejectsOddByteSize() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ConnectionIdBuilder().readShortArrayFromBytes(
                inputStream = ByteArrayInputStream(byteArrayOf(0x00)),
                expectedByteSize = 1,
            )
        }

        assertTrue(error.message.orEmpty().contains("not divisible"))
    }

    @Test
    fun connectionIdReaderRejectsEarlyEof() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ConnectionIdBuilder().readShortArrayFromBytes(
                inputStream = ByteArrayInputStream(byteArrayOf(0x00, 0x01)),
                expectedByteSize = 4,
            )
        }

        assertTrue(error.message.orEmpty().contains("ended after 1 entries"))
    }

    @Test
    fun connectionIdReaderRejectsExtraDataWhenExpectedSizeIsKnown() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ConnectionIdBuilder().readShortArrayFromBytes(
                inputStream = ByteArrayInputStream(byteArrayOf(0x00, 0x01, 0x00, 0x02)),
                expectedByteSize = 2,
            )
        }

        assertTrue(error.message.orEmpty().contains("more than 1 entries"))
    }

    private fun loadBundledConnectionMatrix(): ConnectionMatrix.CostTable =
        ConnectionMatrix.fromShortArray(readBundledConnectionIds())

    private fun readBundledConnectionIds(): ShortArray =
        openBundledConnectionIdRaw { raw, byteSize ->
            raw.use {
                ConnectionIdBuilder().readShortArrayFromBytes(
                    inputStream = it,
                    expectedByteSize = byteSize,
                )
            }
        }

    private fun <T> openBundledConnectionIdRaw(block: (InputStream, Long?) -> T): T =
        FileInputStream(File(findAssetsDir(), "connectionId.dat.zip")).use { input ->
            DictionaryBinaryReader.openZipAwareRaw(input, "connectionId.dat.zip", block)
        }

    private fun readRawShorts(inputStream: InputStream, onValue: (Short) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var pendingHighByte = -1
        var byteCount = 0L
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            byteCount += read.toLong()
            for (i in 0 until read) {
                val byte = buffer[i].toInt() and 0xFF
                if (pendingHighByte == -1) {
                    pendingHighByte = byte
                } else {
                    onValue(((pendingHighByte shl 8) or byte).toShort())
                    pendingHighByte = -1
                }
            }
        }
        check(pendingHighByte == -1) {
            "connectionId.dat byte size $byteCount is not divisible by ${Short.SIZE_BYTES}"
        }
    }

    private fun findAssetsDir(): File =
        listOf(
            File("app/src/main/assets"),
            File("src/main/assets"),
        ).firstOrNull { it.exists() } ?: error("Missing assets directory")
}
