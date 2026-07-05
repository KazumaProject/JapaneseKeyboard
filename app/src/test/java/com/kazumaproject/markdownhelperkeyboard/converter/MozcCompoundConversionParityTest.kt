package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTableReader
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterDataReader
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

class MozcCompoundConversionParityTest {

    @Test
    fun minyuuryokuji_graphContainsRequiredNodes() {
        val trace = engine.convertWithTrace("みにゅうりょくじ")

        assertTrue(
            trace.graphNodes.any {
                it.yomiUsed == "みにゅうりょく" && it.tango == "未入力"
            },
        )
        assertTrue(
            trace.graphNodes.any {
                it.yomiUsed == "じ" && it.tango == "時"
            },
        )
    }

    @Test
    fun minyuuryokuji_boundaryDoesNotRejectMiNyuryokuPlusJi() {
        val trace = engine.convertWithTrace("みにゅうりょくじ")

        assertTrue(
            trace.boundaryEvents.filter {
                it.leftTango == "未入力" || it.rightTango == "時"
            }.take(30).joinToString("\n"),
            trace.boundaryEvents.any {
                it.leftTango == "未入力" &&
                    it.rightTango == "時" &&
                    it.result != "INVALID"
            },
        )
    }

    @Test
    fun minyuuryokuji_candidatesContainMiNyuryokuJi() {
        val trace = engine.convertWithTrace("みにゅうりょくじ")

        assertTrue(
            trace.finalCandidates.take(40).joinToString("\n"),
            trace.finalCandidates.map { it.candidate }.contains("未入力時"),
        )
        assertTrue(
            trace.finalCandidates.filter { it.candidate.contains("入力") || it.candidate.contains("時") }
                .take(40)
                .joinToString("\n"),
            trace.finalCandidates.any {
                it.candidate == "未入力時" && it.path.containsAll(listOf("未入力", "時"))
            },
        )
    }

    @Test
    fun minyuuryokuji_candidatesRankMiNyuryokuJiFirst() {
        val trace = engine.convertWithTrace("みにゅうりょくじ")

        assertEquals(
            trace.finalCandidates.take(10).joinToString("\n"),
            "未入力時",
            trace.finalCandidates.first().candidate,
        )
    }

    companion object {
        private lateinit var engine: KanaKanjiEngine

        @JvmStatic
        @BeforeClass
        fun setUpEngine() {
            engine = TestEngineFactory.create()
        }
    }
}

private object TestEngineFactory {
    private val assetsDir: File by lazy {
        listOf(
            File("app/src/main/assets"),
            File("src/main/assets"),
        ).firstOrNull { it.exists() } ?: error("Missing assets directory")
    }

    fun create(): KanaKanjiEngine {
        val englishEngine = mock<EnglishEngine>()
        whenever(englishEngine.getCandidates(any(), any())).thenReturn(emptyList<Candidate>())

        val system = loadTriple("system/tango.dat.zip", "system/yomi.dat.zip", "system/token.dat.zip")
        val singleKanji = loadTriple(
            "single_kanji/tango_singleKanji.dat",
            "single_kanji/yomi_singleKanji.dat",
            "single_kanji/token_singleKanji.dat",
        )
        val emoji = loadTriple("emoji/tango_emoji.dat", "emoji/yomi_emoji.dat", "emoji/token_emoji.dat")
        val emoticon = loadTriple(
            "emoticon/tango_emoticon.dat",
            "emoticon/yomi_emoticon.dat",
            "emoticon/token_emoticon.dat",
        )
        val symbol = loadTriple("symbol/tango_symbol.dat", "symbol/yomi_symbol.dat", "symbol/token_symbol.dat")
        val readingCorrection = loadTriple(
            "reading_correction/tango_reading_correction.dat",
            "reading_correction/yomi_reading_correction.dat",
            "reading_correction/token_reading_correction.dat",
        )
        val kotowaza = loadTriple(
            "kotowaza/tango_kotowaza.dat",
            "kotowaza/yomi_kotowaza.dat",
            "kotowaza/token_kotowaza.dat",
        )
        val segmenter = assetInput("mozc/segmenter.dat") {
            MozcSegmenter(MozcSegmenterDataReader().read(it))
        }
        val nodeAttributeTable = assetInput("mozc/node_attribute_by_lid.dat") {
            MozcNodeAttributeTableReader().read(it)
        }

        return KanaKanjiEngine().also {
            it.buildEngine(
                graphBuilder = GraphBuilder(),
                findPath = FindPath(),
                connectionIdList = readConnectionIds(),
                systemTangoTrie = system.tangoTrie,
                systemYomiTrie = system.yomiTrie,
                systemTokenArray = system.tokenArray,
                systemSuccinctBitVectorLBSYomi = system.succinctBitVectorLBSYomi,
                systemSuccinctBitVectorIsLeafYomi = system.succinctBitVectorIsLeafYomi,
                systemSuccinctBitVectorTokenArray = system.succinctBitVectorTokenArray,
                systemSuccinctBitVectorTangoLBS = system.succinctBitVectorTangoLBS,
                singleKanjiTangoTrie = singleKanji.tangoTrie,
                singleKanjiYomiTrie = singleKanji.yomiTrie,
                singleKanjiTokenArray = singleKanji.tokenArray,
                singleKanjiSuccinctBitVectorLBSYomi = singleKanji.succinctBitVectorLBSYomi,
                singleKanjiSuccinctBitVectorIsLeafYomi = singleKanji.succinctBitVectorIsLeafYomi,
                singleKanjiSuccinctBitVectorTokenArray = singleKanji.succinctBitVectorTokenArray,
                singleKanjiSuccinctBitVectorTangoLBS = singleKanji.succinctBitVectorTangoLBS,
                emojiTangoTrie = emoji.tangoTrie,
                emojiYomiTrie = emoji.yomiTrie,
                emojiTokenArray = emoji.tokenArray,
                emojiSuccinctBitVectorLBSYomi = emoji.succinctBitVectorLBSYomi,
                emojiSuccinctBitVectorIsLeafYomi = emoji.succinctBitVectorIsLeafYomi,
                emojiSuccinctBitVectorTokenArray = emoji.succinctBitVectorTokenArray,
                emojiSuccinctBitVectorTangoLBS = emoji.succinctBitVectorTangoLBS,
                emoticonTangoTrie = emoticon.tangoTrie,
                emoticonYomiTrie = emoticon.yomiTrie,
                emoticonTokenArray = emoticon.tokenArray,
                emoticonSuccinctBitVectorLBSYomi = emoticon.succinctBitVectorLBSYomi,
                emoticonSuccinctBitVectorIsLeafYomi = emoticon.succinctBitVectorIsLeafYomi,
                emoticonSuccinctBitVectorTokenArray = emoticon.succinctBitVectorTokenArray,
                emoticonSuccinctBitVectorTangoLBS = emoticon.succinctBitVectorTangoLBS,
                symbolTangoTrie = symbol.tangoTrie,
                symbolYomiTrie = symbol.yomiTrie,
                symbolTokenArray = symbol.tokenArray,
                symbolSuccinctBitVectorLBSYomi = symbol.succinctBitVectorLBSYomi,
                symbolSuccinctBitVectorIsLeafYomi = symbol.succinctBitVectorIsLeafYomi,
                symbolSuccinctBitVectorTokenArray = symbol.succinctBitVectorTokenArray,
                symbolSuccinctBitVectorTangoLBS = symbol.succinctBitVectorTangoLBS,
                readingCorrectionTangoTrie = readingCorrection.tangoTrie,
                readingCorrectionYomiTrie = readingCorrection.yomiTrie,
                readingCorrectionTokenArray = readingCorrection.tokenArray,
                readingCorrectionSuccinctBitVectorLBSYomi = readingCorrection.succinctBitVectorLBSYomi,
                readingCorrectionSuccinctBitVectorIsLeafYomi = readingCorrection.succinctBitVectorIsLeafYomi,
                readingCorrectionSuccinctBitVectorTokenArray = readingCorrection.succinctBitVectorTokenArray,
                readingCorrectionSuccinctBitVectorTangoLBS = readingCorrection.succinctBitVectorTangoLBS,
                kotowazaTangoTrie = kotowaza.tangoTrie,
                kotowazaYomiTrie = kotowaza.yomiTrie,
                kotowazaTokenArray = kotowaza.tokenArray,
                kotowazaSuccinctBitVectorLBSYomi = kotowaza.succinctBitVectorLBSYomi,
                kotowazaSuccinctBitVectorIsLeafYomi = kotowaza.succinctBitVectorIsLeafYomi,
                kotowazaSuccinctBitVectorTokenArray = kotowaza.succinctBitVectorTokenArray,
                kotowazaSuccinctBitVectorTangoLBS = kotowaza.succinctBitVectorTangoLBS,
                engineEngine = englishEngine,
                mozcSegmenter = segmenter,
                mozcNodeAttributeTable = nodeAttributeTable,
                mozcDictionaryActive = true,
            )
        }
    }

    private fun loadTriple(
        tangoPath: String,
        yomiPath: String,
        tokenPath: String,
    ): TripleData {
        val tangoTrie = readObject(tangoPath) { LOUDS().readExternalNotCompress(it) }
        val yomiTrie = readObject(yomiPath) { LOUDSWithTermId().readExternalNotCompress(it) }
        val tokenArray = readObject(tokenPath) { input ->
            TokenArray().also { it.readExternal(input) }
        }
        readObject("pos_table.dat") { tokenArray.readPOSTable(it) }
        return TripleData(
            tangoTrie = tangoTrie,
            yomiTrie = yomiTrie,
            tokenArray = tokenArray,
            succinctBitVectorLBSYomi = SuccinctBitVector(yomiTrie.LBS),
            succinctBitVectorIsLeafYomi = SuccinctBitVector(yomiTrie.isLeaf),
            succinctBitVectorTokenArray = SuccinctBitVector(tokenArray.bitvector),
            succinctBitVectorTangoLBS = SuccinctBitVector(tangoTrie.LBS),
        )
    }

    private fun readConnectionIds(): ShortArray =
        assetInput("connectionId.dat.zip") {
            ConnectionIdBuilder().readShortArrayFromBytes(it)
        }

    private fun <T> readObject(path: String, block: (ObjectInputStream) -> T): T =
        assetInput(path) { input ->
            ObjectInputStream(BufferedInputStream(input)).use(block)
        }

    private fun <T> assetInput(path: String, block: (InputStream) -> T): T {
        val fileInput = FileInputStream(File(assetsDir, path))
        val input: InputStream = if (path.endsWith(".zip")) {
            ZipInputStream(BufferedInputStream(fileInput)).also {
                checkNotNull(it.nextEntry) { "Zip asset has no entries: $path" }
            }
        } else {
            BufferedInputStream(fileInput)
        }
        return input.use(block)
    }

    private data class TripleData(
        val tangoTrie: LOUDS,
        val yomiTrie: LOUDSWithTermId,
        val tokenArray: TokenArray,
        val succinctBitVectorLBSYomi: SuccinctBitVector,
        val succinctBitVectorIsLeafYomi: SuccinctBitVector,
        val succinctBitVectorTokenArray: SuccinctBitVector,
        val succinctBitVectorTangoLBS: SuccinctBitVector,
    )
}
