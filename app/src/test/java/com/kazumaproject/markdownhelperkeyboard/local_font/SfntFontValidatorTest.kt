package com.kazumaproject.markdownhelperkeyboard.local_font

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile

class SfntFontValidatorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun acceptsStaticTrueTypeOpenTypeAndAppleTrueSignatures() {
        listOf("\u0000\u0001\u0000\u0000", "OTTO", "true").forEach { signature ->
            SfntFontValidator.validate(minimalFont(signature))
        }
    }

    @Test fun rejectsCollectionsAndWebFonts() {
        assertIssue("ttcf", FontFormatIssue.COLLECTION)
        assertIssue("wOFF", FontFormatIssue.WEB_FONT)
        assertIssue("wOF2", FontFormatIssue.WEB_FONT)
    }

    @Test fun rejectsUnsupportedMalformedVariableAndDuplicateTables() {
        assertIssue("BAD!", FontFormatIssue.UNSUPPORTED_SIGNATURE)
        expectIssue(FontFormatIssue.MALFORMED) { SfntFontValidator.validate(byteArrayOf(1, 2, 3)) }
        expectIssue(FontFormatIssue.VARIABLE) {
            SfntFontValidator.validate(minimalFont("OTTO", tableTag = "fvar", tableLength = 4))
        }
        expectIssue(FontFormatIssue.MALFORMED) {
            SfntFontValidator.validate(minimalFont("OTTO", tableOffset = 0xfffffff0L, tableLength = 4))
        }
        expectIssue(FontFormatIssue.MALFORMED) {
            SfntFontValidator.validate(duplicateTableFont())
        }
    }

    @Test fun enforcesMaximumSizeBeforeReadingTableData() {
        val atLimit = temporaryFolder.newFile("at-limit.ttf")
        RandomAccessFile(atLimit, "rw").use { raf ->
            raf.setLength(MAX_LOCAL_FONT_BYTES)
            raf.seek(0)
            raf.write(minimalFont("OTTO"))
        }
        SfntFontValidator.validate(atLimit)

        val tooLarge = temporaryFolder.newFile("too-large.ttf")
        RandomAccessFile(tooLarge, "rw").use { raf -> raf.setLength(MAX_LOCAL_FONT_BYTES + 1) }
        expectIssue(FontFormatIssue.TOO_LARGE) { SfntFontValidator.validate(tooLarge) }
    }

    private fun assertIssue(signature: String, issue: FontFormatIssue) =
        expectIssue(issue) { SfntFontValidator.validate(minimalFont(signature)) }

    private fun expectIssue(expected: FontFormatIssue, action: () -> Unit) {
        try {
            action()
            fail("Expected $expected")
        } catch (error: FontValidationException) {
            assertEquals(expected, error.issue)
        }
    }

    private fun minimalFont(
        signature: String,
        tableTag: String = "head",
        tableOffset: Long = 28,
        tableLength: Long = 0,
    ): ByteArray {
        val bytes = ByteArray(maxOf(28, (tableOffset.takeIf { it < 1024 }?.toInt() ?: 28) + tableLength.toInt()))
        signature.toByteArray(Charsets.ISO_8859_1).copyInto(bytes, 0, endIndex = 4)
        putU16(bytes, 4, 1)
        tableTag.toByteArray(Charsets.ISO_8859_1).copyInto(bytes, 12, endIndex = 4)
        putU32(bytes, 20, tableOffset)
        putU32(bytes, 24, tableLength)
        return bytes
    }

    private fun duplicateTableFont(): ByteArray = ByteArray(44).apply {
        "OTTO".toByteArray().copyInto(this, 0)
        putU16(this, 4, 2)
        "head".toByteArray().copyInto(this, 12)
        putU32(this, 20, 44)
        putU32(this, 24, 0)
        "head".toByteArray().copyInto(this, 28)
        putU32(this, 36, 44)
        putU32(this, 40, 0)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
