package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

class DictionaryMemoryFootprintTest {

    @Test
    fun measureBundledDictionaryLoadFootprint() {
        assumeTrue(
            System.getProperty("dictionary.memory.measure") == "true" ||
                System.getenv("DICTIONARY_MEMORY_MEASURE") == "true"
        )

        val assetsDir = findAssetsDir()
        val posTable = readPosTable(assetsDir)
        val retained = mutableListOf<Any>()
        val report = mutableListOf<Pair<String, Long>>()

        report += "start" to usedHeapBytes()
        retained.add(posTable)
        report += "posTable" to usedHeapBytes()
        retained.add(loadConnectionMatrix(assetsDir))
        report += "connectionMatrix" to usedHeapBytes()

        retained.addAll(listOf(
            loadTriple(assetsDir, posTable, "system/tango.dat.zip", "system/yomi.dat.zip", "system/token.dat.zip"),
        ))
        report += "system" to usedHeapBytes()

        retained.addAll(listOf(
            loadTriple(assetsDir, posTable, "single_kanji/tango_singleKanji.dat", "single_kanji/yomi_singleKanji.dat", "single_kanji/token_singleKanji.dat"),
            loadTriple(assetsDir, posTable, "emoji/tango_emoji.dat", "emoji/yomi_emoji.dat", "emoji/token_emoji.dat"),
            loadTriple(assetsDir, posTable, "emoticon/tango_emoticon.dat", "emoticon/yomi_emoticon.dat", "emoticon/token_emoticon.dat"),
            loadTriple(assetsDir, posTable, "symbol/tango_symbol.dat", "symbol/yomi_symbol.dat", "symbol/token_symbol.dat"),
            loadTriple(assetsDir, posTable, "reading_correction/tango_reading_correction.dat", "reading_correction/yomi_reading_correction.dat", "reading_correction/token_reading_correction.dat"),
            loadTriple(assetsDir, posTable, "kotowaza/tango_kotowaza.dat", "kotowaza/yomi_kotowaza.dat", "kotowaza/token_kotowaza.dat"),
        ))
        report += "coreWithSmallDictionaries" to usedHeapBytes()

        retained.addAll(listOf(
            loadTriple(assetsDir, posTable, "person_name/tango_person_names.dat", "person_name/yomi_person_names.dat", "person_name/token_person_names.dat"),
            loadTriple(assetsDir, posTable, "places/tango_places.dat.zip", "places/yomi_places.dat.zip", "places/token_places.dat.zip"),
            loadTriple(assetsDir, posTable, "wiki/tango_wiki.dat.zip", "wiki/yomi_wiki.dat.zip", "wiki/token_wiki.dat.zip"),
            loadTriple(assetsDir, posTable, "neologd/tango_neologd.dat.zip", "neologd/yomi_neologd.dat.zip", "neologd/token_neologd.dat.zip"),
            loadTriple(assetsDir, posTable, "web/tango_web.dat.zip", "web/yomi_web.dat.zip", "web/token_web.dat.zip"),
        ))
        report += "allBundledJapanese" to usedHeapBytes()

        val renderedReport = memoryReport(report)
        println(renderedReport)
        writeReport(assetsDir, report, renderedReport)
        check(retained.isNotEmpty())
    }

    private fun loadTriple(
        assetsDir: File,
        posTable: PosTableFootprint,
        tangoPath: String,
        yomiPath: String,
        tokenPath: String,
    ): TripleDictionaryFootprint {
        val tangoTrie = assetsDir.open(tangoPath).use { input ->
            DictionaryBinaryReader.openZipAwareObject(input, tangoPath).use {
                LOUDS().readExternalNotCompress(it)
            }
        }
        val yomiTrie = assetsDir.open(yomiPath).use { input ->
            DictionaryBinaryReader.openZipAwareObject(input, yomiPath).use {
                LOUDSWithTermId().readExternalNotCompress(it)
            }
        }
        val tokenArray = TokenArray().also { tokenArray ->
            assetsDir.open(tokenPath).use { input ->
                DictionaryBinaryReader.openZipAwareObject(input, tokenPath).use {
                    tokenArray.readExternal(it)
                }
            }
            tokenArray.setPOSTable(posTable.leftIds, posTable.rightIds)
        }

        return TripleDictionaryFootprint(
            tangoTrie = tangoTrie,
            yomiTrie = yomiTrie,
            tokenArray = tokenArray,
            yomiLbs = SuccinctBitVector(yomiTrie.LBS),
            yomiLeaf = SuccinctBitVector(yomiTrie.isLeaf),
            tokenBitVector = SuccinctBitVector(tokenArray.bitvector),
            tangoLbs = SuccinctBitVector(tangoTrie.LBS),
        )
    }

    private fun loadConnectionMatrix(assetsDir: File): ConnectionMatrix.CostTable =
        assetsDir.open("connectionId.dat.zip").use { input ->
            DictionaryBinaryReader.openZipAwareRaw(input, "connectionId.dat.zip") { raw, byteSize ->
                raw.use {
                    ConnectionMatrix.fromShortArray(
                        ConnectionIdBuilder().readShortArrayFromBytes(
                            inputStream = it,
                            expectedByteSize = byteSize,
                        )
                    )
                }
            }
        }

    private fun readPosTable(assetsDir: File): PosTableFootprint =
        assetsDir.open("pos_table.dat").use { input ->
            DictionaryBinaryReader.openZipAwareObject(input, "pos_table.dat").use { objectInput ->
                PosTableFootprint(
                    leftIds = objectInput.readObject() as ShortArray,
                    rightIds = objectInput.readObject() as ShortArray,
                )
            }
        }

    private fun File.open(path: String): InputStream = File(this, path).inputStream()

    private fun usedHeapBytes(): Long {
        repeat(4) {
            System.gc()
            Thread.sleep(75)
        }
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun memoryReport(report: List<Pair<String, Long>>): String {
        val start = report.first().second
        return buildString {
            appendLine("DICTIONARY_MEMORY_REPORT")
            report.forEach { (label, bytes) ->
                appendLine("$label=${bytes.toMiBString()} delta=${(bytes - start).toMiBString()}")
            }
        }
    }

    private fun writeReport(assetsDir: File, report: List<Pair<String, Long>>, renderedReport: String) {
        val outputDir = File(appDir(assetsDir), "build/reports/dictionary-memory")
        outputDir.mkdirs()
        File(outputDir, "bundled-footprint.tsv").writeText(
            buildString {
                appendLine("# $renderedReport".replace("\n", "\n# ").trimEnd())
                appendLine("label\theapMiB\tdeltaMiB")
                val start = report.first().second
                report.forEach { (label, bytes) ->
                    appendLine(
                        "$label\t${bytes.toMiBNumber()}\t${(bytes - start).toMiBNumber()}"
                    )
                }
            }
        )
    }

    private fun Long.toMiBString(): String = "%.2f MiB".format(this.toDouble() / 1024.0 / 1024.0)

    private fun Long.toMiBNumber(): String = "%.2f".format(this.toDouble() / 1024.0 / 1024.0)

    private fun appDir(assetsDir: File): File =
        assetsDir.parentFile?.parentFile?.parentFile
            ?: error("Cannot resolve app dir from $assetsDir")

    private fun findAssetsDir(): File {
        var current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(current, "app/src/main/assets")
            if (candidate.isDirectory) return candidate
            current = current.parentFile ?: error("Cannot find app/src/main/assets from ${System.getProperty("user.dir")}")
        }
        error("Cannot find app/src/main/assets from ${System.getProperty("user.dir")}")
    }

    private data class TripleDictionaryFootprint(
        val tangoTrie: LOUDS,
        val yomiTrie: LOUDSWithTermId,
        val tokenArray: TokenArray,
        val yomiLbs: SuccinctBitVector,
        val yomiLeaf: SuccinctBitVector,
        val tokenBitVector: SuccinctBitVector,
        val tangoLbs: SuccinctBitVector,
    )

    private data class PosTableFootprint(
        val leftIds: ShortArray,
        val rightIds: ShortArray,
    )
}
