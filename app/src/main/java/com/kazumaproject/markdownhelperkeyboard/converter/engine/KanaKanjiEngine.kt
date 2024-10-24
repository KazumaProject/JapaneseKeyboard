package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateTemp
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.sortByFaceEmojiFirst
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanjiNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumberExponent
import com.kazumaproject.viterbi.FindPath
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class KanaKanjiEngine {

    private lateinit var graphBuilder: GraphBuilder
    private lateinit var findPath: FindPath

    private lateinit var connectionIds: ShortArray

    private lateinit var systemYomiTrie: LOUDSWithTermId
    private lateinit var systemTangoTrie: LOUDS
    private lateinit var systemTokenArray: TokenArray

    private lateinit var systemRank0ArrayLBSYomi: IntArray
    private lateinit var systemRank1ArrayLBSYomi: IntArray
    private lateinit var systemRank1ArrayIsLeaf: IntArray
    private lateinit var systemRank0ArrayTokenArrayBitvector: IntArray
    private lateinit var systemRank1ArrayTokenArrayBitvector: IntArray
    private lateinit var systemRank0ArrayLBSTango: IntArray
    private lateinit var systemRank1ArrayLBSTango: IntArray
    private lateinit var systemYomiLBSBooleanArray: BooleanArray
    private lateinit var systemYomiLBSPreprocess: IntArray

    private lateinit var singleKanjiYomiTrie: LOUDSWithTermId
    private lateinit var singleKanjiTangoTrie: LOUDS
    private lateinit var singleKanjiTokenArray: TokenArray

    private lateinit var singleKanjiRank0ArrayLBSYomi: ShortArray
    private lateinit var singleKanjiRank1ArrayLBSYomi: ShortArray
    private lateinit var singleKanjiRank1ArrayIsLeaf: ShortArray
    private lateinit var singleKanjiRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var singleKanjiRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var singleKanjiRank0ArrayLBSTango: ShortArray
    private lateinit var singleKanjiRank1ArrayLBSTango: ShortArray
    private lateinit var singleKanjiYomiLBSBooleanArray: BooleanArray
    private lateinit var singleKanjiYomiLBSPreprocess: IntArray

    private lateinit var emojiYomiTrie: LOUDSWithTermId
    private lateinit var emojiTangoTrie: LOUDS
    private lateinit var emojiTokenArray: TokenArray

    private lateinit var emojiRank0ArrayLBSYomi: ShortArray
    private lateinit var emojiRank1ArrayLBSYomi: ShortArray
    private lateinit var emojiRank1ArrayIsLeaf: ShortArray
    private lateinit var emojiRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var emojiRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var emojiRank0ArrayLBSTango: ShortArray
    private lateinit var emojiRank1ArrayLBSTango: ShortArray
    private lateinit var emojiYomiLBSBooleanArray: BooleanArray
    private lateinit var emojiYomiLBSPreprocess: IntArray

    private lateinit var emoticonYomiTrie: LOUDSWithTermId
    private lateinit var emoticonTangoTrie: LOUDS
    private lateinit var emoticonTokenArray: TokenArray

    private lateinit var emoticonRank0ArrayLBSYomi: ShortArray
    private lateinit var emoticonRank1ArrayLBSYomi: ShortArray
    private lateinit var emoticonRank1ArrayIsLeaf: ShortArray
    private lateinit var emoticonRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var emoticonRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var emoticonRank0ArrayLBSTango: ShortArray
    private lateinit var emoticonRank1ArrayLBSTango: ShortArray
    private lateinit var emoticonYomiLBSBooleanArray: BooleanArray
    private lateinit var emoticonYomiLBSPreprocess: IntArray

    private lateinit var symbolYomiTrie: LOUDSWithTermId
    private lateinit var symbolTangoTrie: LOUDS
    private lateinit var symbolTokenArray: TokenArray

    private lateinit var symbolRank0ArrayLBSYomi: ShortArray
    private lateinit var symbolRank1ArrayLBSYomi: ShortArray
    private lateinit var symbolRank1ArrayIsLeaf: ShortArray
    private lateinit var symbolRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var symbolRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var symbolRank0ArrayLBSTango: ShortArray
    private lateinit var symbolRank1ArrayLBSTango: ShortArray
    private lateinit var symbolYomiLBSBooleanArray: BooleanArray
    private lateinit var symbolYomiLBSPreprocess: IntArray

    private lateinit var readingCorrectionYomiTrie: LOUDSWithTermId
    private lateinit var readingCorrectionTangoTrie: LOUDS
    private lateinit var readingCorrectionTokenArray: TokenArray
    private lateinit var readingCorrectionRank0ArrayLBSYomi: ShortArray
    private lateinit var readingCorrectionRank1ArrayLBSYomi: ShortArray
    private lateinit var readingCorrectionRank1ArrayIsLeaf: ShortArray
    private lateinit var readingCorrectionRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var readingCorrectionRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var readingCorrectionRank0ArrayLBSTango: ShortArray
    private lateinit var readingCorrectionRank1ArrayLBSTango: ShortArray
    private lateinit var readingCorrectionYomiLBSBooleanArray: BooleanArray
    private lateinit var readingCorrectionYomiLBSPreprocess: IntArray

    private lateinit var kotowazaYomiTrie: LOUDSWithTermId
    private lateinit var kotowazaTangoTrie: LOUDS
    private lateinit var kotowazaTokenArray: TokenArray
    private lateinit var kotowazaRank0ArrayLBSYomi: ShortArray
    private lateinit var kotowazaRank1ArrayLBSYomi: ShortArray
    private lateinit var kotowazaRank1ArrayIsLeaf: ShortArray
    private lateinit var kotowazaRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var kotowazaRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var kotowazaRank0ArrayLBSTango: ShortArray
    private lateinit var kotowazaRank1ArrayLBSTango: ShortArray
    private lateinit var kotowazaYomiLBSBooleanArray: BooleanArray
    private lateinit var kotowazaYomiLBSPreprocess: IntArray


    companion object {
        const val SCORE_OFFSET = 6500
        const val SCORE_OFFSET_SMALL = 5000
    }

    fun buildEngine(
        graphBuilder: GraphBuilder,
        findPath: FindPath,
        connectionIdList: ShortArray,

        systemTangoTrie: LOUDS,
        systemYomiTrie: LOUDSWithTermId,
        systemTokenArray: TokenArray,
        systemRank0ArrayLBSYomi: IntArray,
        systemRank1ArrayLBSYomi: IntArray,
        systemRank1ArrayIsLeaf: IntArray,
        systemRank0ArrayTokenArrayBitvector: IntArray,
        systemRank1ArrayTokenArrayBitvector: IntArray,
        systemRank0ArrayLBSTango: IntArray,
        systemRank1ArrayLBSTango: IntArray,
        systemYomiLBSBooleanArray: BooleanArray,

        singleKanjiTangoTrie: LOUDS,
        singleKanjiYomiTrie: LOUDSWithTermId,
        singleKanjiTokenArray: TokenArray,
        singleKanjiRank0ArrayLBSYomi: ShortArray,
        singleKanjiRank1ArrayLBSYomi: ShortArray,
        singleKanjiRank1ArrayIsLeaf: ShortArray,
        singleKanjiRank0ArrayTokenArrayBitvector: ShortArray,
        singleKanjiRank1ArrayTokenArrayBitvector: ShortArray,
        singleKanjiRank0ArrayLBSTango: ShortArray,
        singleKanjiRank1ArrayLBSTango: ShortArray,
        singleKanjiYomiLBSBooleanArray: BooleanArray,

        emojiTangoTrie: LOUDS,
        emojiYomiTrie: LOUDSWithTermId,
        emojiTokenArray: TokenArray,
        emojiRank0ArrayLBSYomi: ShortArray,
        emojiRank1ArrayLBSYomi: ShortArray,
        emojiRank1ArrayIsLeaf: ShortArray,
        emojiRank0ArrayTokenArrayBitvector: ShortArray,
        emojiRank1ArrayTokenArrayBitvector: ShortArray,
        emojiRank0ArrayLBSTango: ShortArray,
        emojiRank1ArrayLBSTango: ShortArray,
        emojiYomiLBSBooleanArray: BooleanArray,

        emoticonTangoTrie: LOUDS,
        emoticonYomiTrie: LOUDSWithTermId,
        emoticonTokenArray: TokenArray,
        emoticonRank0ArrayLBSYomi: ShortArray,
        emoticonRank1ArrayLBSYomi: ShortArray,
        emoticonRank1ArrayIsLeaf: ShortArray,
        emoticonRank0ArrayTokenArrayBitvector: ShortArray,
        emoticonRank1ArrayTokenArrayBitvector: ShortArray,
        emoticonRank0ArrayLBSTango: ShortArray,
        emoticonRank1ArrayLBSTango: ShortArray,
        emoticonYomiLBSBooleanArray: BooleanArray,

        symbolTangoTrie: LOUDS,
        symbolYomiTrie: LOUDSWithTermId,
        symbolTokenArray: TokenArray,
        symbolRank0ArrayLBSYomi: ShortArray,
        symbolRank1ArrayLBSYomi: ShortArray,
        symbolRank1ArrayIsLeaf: ShortArray,
        symbolRank0ArrayTokenArrayBitvector: ShortArray,
        symbolRank1ArrayTokenArrayBitvector: ShortArray,
        symbolRank0ArrayLBSTango: ShortArray,
        symbolRank1ArrayLBSTango: ShortArray,
        symbolYomiLBSBooleanArray: BooleanArray,

        readingCorrectionTangoTrie: LOUDS,
        readingCorrectionYomiTrie: LOUDSWithTermId,
        readingCorrectionTokenArray: TokenArray,
        readingCorrectionRank0ArrayLBSYomi: ShortArray,
        readingCorrectionRank1ArrayLBSYomi: ShortArray,
        readingCorrectionRank1ArrayIsLeaf: ShortArray,
        readingCorrectionRank0ArrayTokenArrayBitvector: ShortArray,
        readingCorrectionRank1ArrayTokenArrayBitvector: ShortArray,
        readingCorrectionRank0ArrayLBSTango: ShortArray,
        readingCorrectionRank1ArrayLBSTango: ShortArray,
        readingCorrectionYomiLBSBooleanArray: BooleanArray,

        kotowazaTangoTrie: LOUDS,
        kotowazaYomiTrie: LOUDSWithTermId,
        kotowazaTokenArray: TokenArray,
        kotowazaRank0ArrayLBSYomi: ShortArray,
        kotowazaRank1ArrayLBSYomi: ShortArray,
        kotowazaRank1ArrayIsLeaf: ShortArray,
        kotowazaRank0ArrayTokenArrayBitvector: ShortArray,
        kotowazaRank1ArrayTokenArrayBitvector: ShortArray,
        kotowazaRank0ArrayLBSTango: ShortArray,
        kotowazaRank1ArrayLBSTango: ShortArray,
        kotowazaYomiLBSBooleanArray: BooleanArray,
    ) {
        this@KanaKanjiEngine.graphBuilder = graphBuilder
        this@KanaKanjiEngine.findPath = findPath

        // System
        this@KanaKanjiEngine.connectionIds = connectionIdList
        this@KanaKanjiEngine.systemTangoTrie = systemTangoTrie
        this@KanaKanjiEngine.systemTokenArray = systemTokenArray
        this@KanaKanjiEngine.systemYomiTrie = systemYomiTrie
        this@KanaKanjiEngine.systemRank0ArrayLBSYomi = systemRank0ArrayLBSYomi
        this@KanaKanjiEngine.systemRank1ArrayLBSYomi = systemRank1ArrayLBSYomi
        this@KanaKanjiEngine.systemRank1ArrayIsLeaf = systemRank1ArrayIsLeaf
        this@KanaKanjiEngine.systemRank0ArrayTokenArrayBitvector =
            systemRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.systemRank1ArrayTokenArrayBitvector =
            systemRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.systemRank0ArrayLBSTango = systemRank0ArrayLBSTango
        this@KanaKanjiEngine.systemRank1ArrayLBSTango = systemRank1ArrayLBSTango
        this@KanaKanjiEngine.systemYomiLBSBooleanArray = systemYomiLBSBooleanArray


        // Single Kanji
        this@KanaKanjiEngine.singleKanjiTangoTrie = singleKanjiTangoTrie
        this@KanaKanjiEngine.singleKanjiTokenArray = singleKanjiTokenArray
        this@KanaKanjiEngine.singleKanjiYomiTrie = singleKanjiYomiTrie
        this@KanaKanjiEngine.singleKanjiRank0ArrayLBSYomi = singleKanjiRank0ArrayLBSYomi
        this@KanaKanjiEngine.singleKanjiRank1ArrayLBSYomi = singleKanjiRank1ArrayLBSYomi
        this@KanaKanjiEngine.singleKanjiRank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf
        this@KanaKanjiEngine.singleKanjiRank0ArrayTokenArrayBitvector =
            singleKanjiRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.singleKanjiRank1ArrayTokenArrayBitvector =
            singleKanjiRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.singleKanjiRank0ArrayLBSTango = singleKanjiRank0ArrayLBSTango
        this@KanaKanjiEngine.singleKanjiRank1ArrayLBSTango = singleKanjiRank1ArrayLBSTango
        this@KanaKanjiEngine.singleKanjiYomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray


        // Emoji
        this@KanaKanjiEngine.emojiTangoTrie = emojiTangoTrie
        this@KanaKanjiEngine.emojiTokenArray = emojiTokenArray
        this@KanaKanjiEngine.emojiYomiTrie = emojiYomiTrie
        this@KanaKanjiEngine.emojiRank0ArrayLBSYomi = emojiRank0ArrayLBSYomi
        this@KanaKanjiEngine.emojiRank1ArrayLBSYomi = emojiRank1ArrayLBSYomi
        this@KanaKanjiEngine.emojiRank1ArrayIsLeaf = emojiRank1ArrayIsLeaf
        this@KanaKanjiEngine.emojiRank0ArrayTokenArrayBitvector = emojiRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.emojiRank1ArrayTokenArrayBitvector = emojiRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.emojiRank0ArrayLBSTango = emojiRank0ArrayLBSTango
        this@KanaKanjiEngine.emojiRank1ArrayLBSTango = emojiRank1ArrayLBSTango
        this@KanaKanjiEngine.emojiYomiLBSBooleanArray = emojiYomiLBSBooleanArray

        /** Emoticon **/
        this@KanaKanjiEngine.emoticonTangoTrie = emoticonTangoTrie
        this@KanaKanjiEngine.emoticonTokenArray = emoticonTokenArray
        this@KanaKanjiEngine.emoticonYomiTrie = emoticonYomiTrie
        this@KanaKanjiEngine.emoticonRank0ArrayLBSYomi = emoticonRank0ArrayLBSYomi
        this@KanaKanjiEngine.emoticonRank1ArrayLBSYomi = emoticonRank1ArrayLBSYomi
        this@KanaKanjiEngine.emoticonRank1ArrayIsLeaf = emoticonRank1ArrayIsLeaf
        this@KanaKanjiEngine.emoticonRank0ArrayTokenArrayBitvector =
            emoticonRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.emoticonRank1ArrayTokenArrayBitvector =
            emoticonRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.emoticonRank0ArrayLBSTango = emoticonRank0ArrayLBSTango
        this@KanaKanjiEngine.emoticonRank1ArrayLBSTango = emoticonRank1ArrayLBSTango
        this@KanaKanjiEngine.emoticonYomiLBSBooleanArray = emoticonYomiLBSBooleanArray

        /** Symbol **/
        this@KanaKanjiEngine.symbolTangoTrie = symbolTangoTrie
        this@KanaKanjiEngine.symbolTokenArray = symbolTokenArray
        this@KanaKanjiEngine.symbolYomiTrie = symbolYomiTrie
        this@KanaKanjiEngine.symbolRank0ArrayLBSYomi = symbolRank0ArrayLBSYomi
        this@KanaKanjiEngine.symbolRank1ArrayLBSYomi = symbolRank1ArrayLBSYomi
        this@KanaKanjiEngine.symbolRank1ArrayIsLeaf = symbolRank1ArrayIsLeaf
        this@KanaKanjiEngine.symbolRank0ArrayTokenArrayBitvector =
            symbolRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.symbolRank1ArrayTokenArrayBitvector =
            symbolRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.symbolRank0ArrayLBSTango = symbolRank0ArrayLBSTango
        this@KanaKanjiEngine.symbolRank1ArrayLBSTango = symbolRank1ArrayLBSTango
        this@KanaKanjiEngine.symbolYomiLBSBooleanArray = symbolYomiLBSBooleanArray

        /** Reading Correction **/
        this@KanaKanjiEngine.readingCorrectionTangoTrie = readingCorrectionTangoTrie
        this@KanaKanjiEngine.readingCorrectionTokenArray = readingCorrectionTokenArray
        this@KanaKanjiEngine.readingCorrectionYomiTrie = readingCorrectionYomiTrie
        this@KanaKanjiEngine.readingCorrectionRank0ArrayLBSYomi = readingCorrectionRank0ArrayLBSYomi
        this@KanaKanjiEngine.readingCorrectionRank1ArrayLBSYomi = readingCorrectionRank1ArrayLBSYomi
        this@KanaKanjiEngine.readingCorrectionRank1ArrayIsLeaf = readingCorrectionRank1ArrayIsLeaf
        this@KanaKanjiEngine.readingCorrectionRank0ArrayTokenArrayBitvector =
            readingCorrectionRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.readingCorrectionRank1ArrayTokenArrayBitvector =
            readingCorrectionRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.readingCorrectionRank0ArrayLBSTango =
            readingCorrectionRank0ArrayLBSTango
        this@KanaKanjiEngine.readingCorrectionRank1ArrayLBSTango =
            readingCorrectionRank1ArrayLBSTango
        this@KanaKanjiEngine.readingCorrectionYomiLBSBooleanArray =
            readingCorrectionYomiLBSBooleanArray

        /**  Kotowaza **/
        this@KanaKanjiEngine.kotowazaTangoTrie = kotowazaTangoTrie
        this@KanaKanjiEngine.kotowazaTokenArray = kotowazaTokenArray
        this@KanaKanjiEngine.kotowazaYomiTrie = kotowazaYomiTrie
        this@KanaKanjiEngine.kotowazaRank0ArrayLBSYomi = kotowazaRank0ArrayLBSYomi
        this@KanaKanjiEngine.kotowazaRank1ArrayLBSYomi = kotowazaRank1ArrayLBSYomi
        this@KanaKanjiEngine.kotowazaRank1ArrayIsLeaf = kotowazaRank1ArrayIsLeaf
        this@KanaKanjiEngine.kotowazaRank0ArrayTokenArrayBitvector =
            kotowazaRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.kotowazaRank1ArrayTokenArrayBitvector =
            kotowazaRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.kotowazaRank0ArrayLBSTango =
            kotowazaRank0ArrayLBSTango
        this@KanaKanjiEngine.kotowazaRank1ArrayLBSTango =
            kotowazaRank1ArrayLBSTango
        this@KanaKanjiEngine.kotowazaYomiLBSBooleanArray =
            kotowazaYomiLBSBooleanArray

        this@KanaKanjiEngine.systemYomiLBSPreprocess =
            preprocessLBSInBoolArray(systemYomiLBSBooleanArray)
        this@KanaKanjiEngine.singleKanjiYomiLBSPreprocess =
            preprocessLBSInBoolArray(singleKanjiYomiLBSBooleanArray)
        this@KanaKanjiEngine.emojiYomiLBSPreprocess =
            preprocessLBSInBoolArray(emojiYomiLBSBooleanArray)
        this@KanaKanjiEngine.emoticonYomiLBSPreprocess =
            preprocessLBSInBoolArray(emoticonYomiLBSBooleanArray)
        this@KanaKanjiEngine.symbolYomiLBSPreprocess =
            preprocessLBSInBoolArray(symbolYomiLBSBooleanArray)
        this@KanaKanjiEngine.readingCorrectionYomiLBSPreprocess =
            preprocessLBSInBoolArray(readingCorrectionYomiLBSBooleanArray)
        this@KanaKanjiEngine.kotowazaYomiLBSPreprocess =
            preprocessLBSInBoolArray(kotowazaYomiLBSBooleanArray)

    }

    suspend fun getCandidates(
        input: String, n: Int
    ): List<Candidate> = withContext(Dispatchers.Unconfined) {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray,
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess
        )

        val resultNBestFinalDeferred = async(Dispatchers.Default) {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }.await()

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000)
        )

        if (input.length == 1) {
            val singleKanjiCommonPrefixDeferred = async(Dispatchers.Default) {
                singleKanjiYomiTrie.commonPrefixSearchShortArray(
                    str = input,
                    rank0Array = singleKanjiRank0ArrayLBSYomi,
                    rank1Array = singleKanjiRank1ArrayLBSYomi
                ).asReversed()
            }

            val yomiPartOfDeferred = async(Dispatchers.Default) {
                systemYomiTrie.commonPrefixSearch(
                    str = input,
                    rank0Array = systemRank0ArrayLBSYomi,
                    rank1Array = systemRank1ArrayLBSYomi
                ).asReversed()
            }

            val emojiCommonPrefixDeferred = async(Dispatchers.Default) {
                emojiYomiTrie.commonPrefixSearchShortArray(
                    str = input,
                    rank0Array = emojiRank0ArrayLBSYomi,
                    rank1Array = emojiRank1ArrayLBSYomi
                ).asReversed()
            }

            val emoticonCommonPrefixDeferred = async(Dispatchers.Default) {
                emoticonYomiTrie.commonPrefixSearchShortArray(
                    str = input,
                    rank0Array = emoticonRank0ArrayLBSYomi,
                    rank1Array = emoticonRank1ArrayLBSYomi
                ).asReversed()
            }

            val symbolCommonPrefixDeferred = async(Dispatchers.Default) {
                symbolYomiTrie.commonPrefixSearchShortArray(
                    str = input,
                    rank0Array = symbolRank0ArrayLBSYomi,
                    rank1Array = symbolRank1ArrayLBSYomi
                ).asReversed()
            }

            val singleKanjiListDeferred = async(Dispatchers.Default) {
                singleKanjiCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                    val termId = singleKanjiYomiTrie.getTermIdShortArray(
                        singleKanjiYomiTrie.getNodeIndex(
                            yomi,
                            singleKanjiRank1ArrayLBSYomi,
                            singleKanjiYomiLBSBooleanArray,
                            singleKanjiYomiLBSPreprocess
                        ), singleKanjiRank1ArrayIsLeaf
                    )
                    singleKanjiTokenArray.getListDictionaryByYomiTermIdShortArray(
                        termId,
                        singleKanjiRank0ArrayTokenArrayBitvector,
                        singleKanjiRank1ArrayTokenArrayBitvector
                    ).sortedBy { it.wordCost }.asSequence().map {
                        Candidate(
                            string = when (it.nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> singleKanjiTangoTrie.getLetterShortArray(
                                    it.nodeId,
                                    singleKanjiRank0ArrayLBSTango,
                                    singleKanjiRank1ArrayLBSTango
                                )
                            },
                            type = 7,
                            length = yomi.length.toUByte(),
                            score = it.wordCost.toInt(),
                            leftId = singleKanjiTokenArray.leftIds[it.posTableIndex.toInt()],
                            rightId = singleKanjiTokenArray.rightIds[it.posTableIndex.toInt()]
                        )
                    }
                }.distinctBy { it.string }.toList()
            }

            val emojiListDeferred = async(Dispatchers.Default) {
                emojiCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                    val termId = emojiYomiTrie.getTermIdShortArray(
                        emojiYomiTrie.getNodeIndex(
                            yomi,
                            emojiRank1ArrayLBSYomi,
                            emojiYomiLBSBooleanArray,
                            emojiYomiLBSPreprocess
                        ), emojiRank1ArrayIsLeaf
                    )
                    emojiTokenArray.getListDictionaryByYomiTermIdShortArray(
                        termId,
                        emojiRank0ArrayTokenArrayBitvector,
                        emojiRank1ArrayTokenArrayBitvector
                    ).sortedBy { it.wordCost }.asSequence().map {
                        Candidate(
                            string = when (it.nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> emojiTangoTrie.getLetterShortArray(
                                    it.nodeId, emojiRank0ArrayLBSTango, emojiRank1ArrayLBSTango
                                )
                            },
                            type = 11,
                            length = yomi.length.toUByte(),
                            score = it.wordCost.toInt(),
                            leftId = emojiTokenArray.leftIds[it.posTableIndex.toInt()],
                            rightId = emojiTokenArray.rightIds[it.posTableIndex.toInt()]
                        )
                    }
                }.distinctBy { it.string }.toList()
            }

            val emoticonListDeferred = async(Dispatchers.Default) {
                emoticonCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                    val termId = emoticonYomiTrie.getTermIdShortArray(
                        emoticonYomiTrie.getNodeIndex(
                            yomi,
                            emoticonRank1ArrayLBSYomi,
                            emoticonYomiLBSBooleanArray,
                            emoticonYomiLBSPreprocess
                        ), emoticonRank1ArrayIsLeaf
                    )
                    emoticonTokenArray.getListDictionaryByYomiTermIdShortArray(
                        termId,
                        emoticonRank0ArrayTokenArrayBitvector,
                        emoticonRank1ArrayTokenArrayBitvector
                    ).sortedBy { it.wordCost }.asSequence().map {
                        Candidate(
                            string = when (it.nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> emoticonTangoTrie.getLetterShortArray(
                                    it.nodeId,
                                    emoticonRank0ArrayLBSTango,
                                    emoticonRank1ArrayLBSTango
                                )
                            },
                            type = 12,
                            length = yomi.length.toUByte(),
                            score = it.wordCost.toInt(),
                            leftId = emoticonTokenArray.leftIds[it.posTableIndex.toInt()],
                            rightId = emoticonTokenArray.rightIds[it.posTableIndex.toInt()]
                        )
                    }
                }.distinctBy { it.string }.toList()
            }

            val symbolListDeferred = async(Dispatchers.Default) {
                symbolCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                    val termId = symbolYomiTrie.getTermIdShortArray(
                        symbolYomiTrie.getNodeIndex(
                            yomi,
                            symbolRank1ArrayLBSYomi,
                            symbolYomiLBSBooleanArray,
                            symbolYomiLBSPreprocess
                        ), symbolRank1ArrayIsLeaf
                    )
                    symbolTokenArray.getListDictionaryByYomiTermIdShortArray(
                        termId,
                        symbolRank0ArrayTokenArrayBitvector,
                        symbolRank1ArrayTokenArrayBitvector
                    ).sortedBy { it.wordCost }.asSequence().map {
                        Candidate(
                            string = when (it.nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> symbolTangoTrie.getLetterShortArray(
                                    it.nodeId, symbolRank0ArrayLBSTango, symbolRank1ArrayLBSTango
                                )
                            },
                            type = 13,
                            length = yomi.length.toUByte(),
                            score = it.wordCost.toInt(),
                            leftId = symbolTokenArray.leftIds[it.posTableIndex.toInt()],
                            rightId = symbolTokenArray.rightIds[it.posTableIndex.toInt()]
                        )
                    }
                }.distinctBy { it.string }.toList()
            }

            val longest = yomiPartOfDeferred.await().firstOrNull() ?: input

            val longestListDeferred = async(Dispatchers.Default) {
                val tempFirstStrConversionList = nBestPathForLongest(longest, n * 4)
                return@async tempFirstStrConversionList.map {
                    Candidate(
                        string = it.string,
                        type = 10,
                        length = longest.length.toUByte(),
                        score = it.wordCost,
                        leftId = it.leftId,
                        rightId = it.rightId
                    )
                }
            }

            return@withContext (resultNBestFinalDeferred + longestListDeferred.await() + hirakanaAndKana + emojiListDeferred.await() + emoticonListDeferred.await() + symbolListDeferred.await() + singleKanjiListDeferred.await()).distinctBy { it.string }
        }

        val yomiPartOfDeferred = async(Dispatchers.Default) {
            if (input.length > 16) return@async emptyList()
            systemYomiTrie.commonPrefixSearch(
                str = input,
                rank0Array = systemRank0ArrayLBSYomi,
                rank1Array = systemRank1ArrayLBSYomi
            ).asReversed()
        }

        val predictiveSearchDeferred = async(Dispatchers.Default) {
            systemYomiTrie.predictiveSearch(
                prefix = input,
                rank0Array = systemRank0ArrayLBSYomi,
                rank1Array = systemRank1ArrayLBSYomi
            ).filter {
                if (input.length <= 2) it.length <= input.length + 1 else if (input.length == 3) it.length <= input.length + 2 else it.length > input.length
            }
        }

        val singleKanjiCommonPrefixDeferred = async(Dispatchers.Default) {
            singleKanjiYomiTrie.commonPrefixSearchShortArray(
                str = input,
                rank0Array = singleKanjiRank0ArrayLBSYomi,
                rank1Array = singleKanjiRank1ArrayLBSYomi
            ).asReversed()
        }

        val emojiCommonPrefixDeferred = async(Dispatchers.Default) {
            emojiYomiTrie.predictiveSearch(
                prefix = input,
                rank0Array = emojiRank0ArrayLBSYomi,
                rank1Array = emojiRank1ArrayLBSYomi
            ).asReversed()
        }

        val emoticonCommonPrefixDeferred = async(Dispatchers.Default) {
            emoticonYomiTrie.predictiveSearch(
                prefix = input,
                rank0Array = emoticonRank0ArrayLBSYomi,
                rank1Array = emoticonRank1ArrayLBSYomi
            ).asReversed()
        }

        val symbolCommonPrefixDeferred = async(Dispatchers.Default) {
            symbolYomiTrie.predictiveSearch(
                prefix = input,
                rank0Array = symbolRank0ArrayLBSYomi,
                rank1Array = symbolRank1ArrayLBSYomi
            ).asReversed()
        }

        val readingCorrectionCommonPrefixDeferred = async(Dispatchers.Default) {
            readingCorrectionYomiTrie.predictiveSearch(
                prefix = input,
                rank0Array = readingCorrectionRank0ArrayLBSYomi,
                rank1Array = readingCorrectionRank1ArrayLBSYomi
            ).asReversed().filter {
                if (input.length <= 2) {
                    it.length <= input.length + 1
                } else if (input.length in 3..6) {
                    it.length <= input.length + 2
                } else {
                    it.length >= input.length
                }
            }
        }

        val kotowazaCommonPrefixDeferred = async(Dispatchers.Default) {
            kotowazaYomiTrie.predictiveSearch(
                prefix = input,
                rank0Array = kotowazaRank0ArrayLBSYomi,
                rank1Array = kotowazaRank1ArrayLBSYomi
            ).asReversed().filter {
                if (input.length <= 2) {
                    it.length <= input.length + 1
                } else if (input.length in 3..6) {
                    it.length <= input.length + 2
                } else {
                    it.length >= input.length
                }
            }
        }

        val predictiveSearchResultDeferred = async(Dispatchers.Default) {
            predictiveSearchDeferred.await().asSequence().sortedBy { it.length }
                .filterNot { it.length == input.length }.flatMap { yomi ->
                    val nodeIndex = systemYomiTrie.getNodeIndex(
                        yomi,
                        systemRank1ArrayLBSYomi,
                        systemYomiLBSBooleanArray,
                        systemYomiLBSPreprocess
                    )
                    val termId = systemYomiTrie.getTermId(nodeIndex, systemRank1ArrayIsLeaf)
                    systemTokenArray.getListDictionaryByYomiTermId(
                        termId,
                        systemRank0ArrayTokenArrayBitvector,
                        systemRank1ArrayTokenArrayBitvector
                    ).sortedBy { it.wordCost }.asSequence().map {
                        Candidate(
                            string = when (it.nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> systemTangoTrie.getLetter(
                                    it.nodeId, systemRank0ArrayLBSTango, systemRank1ArrayLBSTango
                                )
                            },
                            type = 9,
                            length = yomi.length.toUByte(),
                            score = if (yomi.length == input.length) it.wordCost.toInt() else if (input.length <= 5) it.wordCost.toInt() + SCORE_OFFSET * (yomi.length - input.length) else it.wordCost.toInt() + SCORE_OFFSET_SMALL,
                            leftId = systemTokenArray.leftIds[it.posTableIndex.toInt()],
                            rightId = systemTokenArray.rightIds[it.posTableIndex.toInt()]
                        )
                    }.distinctBy { it.string }.toList()
                }.distinctBy { it.string }.sortedBy { it.score }.take(8).toList()
        }

        val yomiPartListDeferred = async(Dispatchers.Default) {
            yomiPartOfDeferred.await().asSequence().flatMap { yomi ->
                val termId = systemYomiTrie.getTermId(
                    systemYomiTrie.getNodeIndex(
                        yomi,
                        systemRank1ArrayLBSYomi,
                        systemYomiLBSBooleanArray,
                        systemYomiLBSPreprocess
                    ), systemRank1ArrayIsLeaf
                )
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId, systemRank0ArrayTokenArrayBitvector, systemRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> systemTangoTrie.getLetter(
                                it.nodeId, systemRank0ArrayLBSTango, systemRank1ArrayLBSTango
                            )
                        },
                        type = if (yomi.length == input.length) 2 else 5,
                        length = yomi.length.toUByte(),
                        score = it.wordCost.toInt(),
                        leftId = systemTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = systemTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }.distinctBy { it.string }
            }.distinctBy { it.string }.toList()
        }

        val longest = yomiPartOfDeferred.await().firstOrNull() ?: input
        val secondPartDeferred = if (longest.length < input.length) {
            async(Dispatchers.Default) {
                val tempSecondStr = input.substring(longest.length)
                val tempFirstStrConversionList = nBestPathForLongest(longest, n * 2)
                val tempSecondStrConversionList = nBestPathForLongest(tempSecondStr, n * 2)
                tempFirstStrConversionList.flatMap { item1 ->
                    tempSecondStrConversionList.map { item2 ->
                        Pair(item1.string + item2.string, item1.wordCost + item2.wordCost)
                    }
                }.map { (combinedString, combinedScore) ->
                    Candidate(
                        string = combinedString,
                        type = 6,
                        length = (longest.length + tempSecondStr.length).toUByte(),
                        score = combinedScore + 2500
                    )
                }
            }
        } else {
            async(Dispatchers.Default) {
                val tempFirstStrConversionList = nBestPathForLongest(longest, n * 4)
                return@async tempFirstStrConversionList.map {
                    Candidate(
                        string = it.string,
                        type = 10,
                        length = longest.length.toUByte(),
                        score = it.wordCost + 2000,
                        leftId = it.leftId,
                        rightId = it.rightId
                    )
                }
            }
        }

        val singleKanjiListDeferred = async(Dispatchers.Default) {
            singleKanjiCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                val termId = singleKanjiYomiTrie.getTermIdShortArray(
                    singleKanjiYomiTrie.getNodeIndex(
                        yomi,
                        singleKanjiRank1ArrayLBSYomi,
                        singleKanjiYomiLBSBooleanArray,
                        singleKanjiYomiLBSPreprocess
                    ), singleKanjiRank1ArrayIsLeaf
                )
                singleKanjiTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId,
                    singleKanjiRank0ArrayTokenArrayBitvector,
                    singleKanjiRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> singleKanjiTangoTrie.getLetterShortArray(
                                it.nodeId,
                                singleKanjiRank0ArrayLBSTango,
                                singleKanjiRank1ArrayLBSTango
                            )
                        },
                        type = 7,
                        length = yomi.length.toUByte(),
                        score = it.wordCost.toInt(),
                        leftId = singleKanjiTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = singleKanjiTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }.distinctBy { it.string }.toList()
        }

        val emojiListDeferred = async(Dispatchers.Default) {
            emojiCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                val termId = emojiYomiTrie.getTermIdShortArray(
                    emojiYomiTrie.getNodeIndex(
                        yomi,
                        emojiRank1ArrayLBSYomi,
                        emojiYomiLBSBooleanArray,
                        emojiYomiLBSPreprocess
                    ), emojiRank1ArrayIsLeaf
                )
                emojiTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, emojiRank0ArrayTokenArrayBitvector, emojiRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> emojiTangoTrie.getLetterShortArray(
                                it.nodeId, emojiRank0ArrayLBSTango, emojiRank1ArrayLBSTango
                            )
                        },
                        type = 11,
                        length = yomi.length.toUByte(),
                        score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + 1000 * (yomi.length - input.length),
                        leftId = emojiTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = emojiTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }.distinctBy { it.string }.toList()
        }

        val emoticonListDeferred = async(Dispatchers.Default) {
            emoticonCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                val termId = emoticonYomiTrie.getTermIdShortArray(
                    emoticonYomiTrie.getNodeIndex(
                        yomi,
                        emoticonRank1ArrayLBSYomi,
                        emoticonYomiLBSBooleanArray,
                        emoticonYomiLBSPreprocess
                    ), emoticonRank1ArrayIsLeaf
                )
                emoticonTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId,
                    emoticonRank0ArrayTokenArrayBitvector,
                    emoticonRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> emoticonTangoTrie.getLetterShortArray(
                                it.nodeId, emoticonRank0ArrayLBSTango, emoticonRank1ArrayLBSTango
                            )
                        },
                        type = 12,
                        length = yomi.length.toUByte(),
                        score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + 1000 * (yomi.length - input.length),
                        leftId = emoticonTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = emoticonTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }.distinctBy { it.string }.toList()
        }

        val symbolListDeferred = async(Dispatchers.Default) {
            symbolCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                val termId = symbolYomiTrie.getTermIdShortArray(
                    symbolYomiTrie.getNodeIndex(
                        yomi,
                        symbolRank1ArrayLBSYomi,
                        symbolYomiLBSBooleanArray,
                        symbolYomiLBSPreprocess
                    ), symbolRank1ArrayIsLeaf
                )
                symbolTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, symbolRank0ArrayTokenArrayBitvector, symbolRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> symbolTangoTrie.getLetterShortArray(
                                it.nodeId, symbolRank0ArrayLBSTango, symbolRank1ArrayLBSTango
                            )
                        },
                        type = 13,
                        length = yomi.length.toUByte(),
                        score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + 1000 * (yomi.length - input.length),
                        leftId = symbolTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = symbolTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }.distinctBy { it.string }.toList()
        }

        val readingCorrectionListDeferred = async(Dispatchers.Default) {
            readingCorrectionCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi,
                        readingCorrectionRank1ArrayLBSYomi,
                        readingCorrectionYomiLBSBooleanArray,
                        readingCorrectionYomiLBSPreprocess
                    ), readingCorrectionRank1ArrayIsLeaf
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId,
                    readingCorrectionRank0ArrayTokenArrayBitvector,
                    readingCorrectionRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId,
                                readingCorrectionRank0ArrayLBSTango,
                                readingCorrectionRank1ArrayLBSTango
                            )
                        },
                        type = 15,
                        length = yomi.length.toUByte(),
                        score = if (yomi.length == input.length) it.wordCost.toInt() + 4000 else it.wordCost.toInt() + SCORE_OFFSET * (yomi.length - input.length),
                        leftId = readingCorrectionTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = readingCorrectionTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }.distinctBy { it.string }.toList()
        }

        val kotowazaListDeferred = async(Dispatchers.Default) {
            kotowazaCommonPrefixDeferred.await().asSequence().flatMap { yomi ->
                val termId = kotowazaYomiTrie.getTermIdShortArray(
                    kotowazaYomiTrie.getNodeIndex(
                        yomi,
                        kotowazaRank1ArrayLBSYomi,
                        kotowazaYomiLBSBooleanArray,
                        kotowazaYomiLBSPreprocess
                    ), kotowazaRank1ArrayIsLeaf
                )
                kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId,
                    kotowazaRank0ArrayTokenArrayBitvector,
                    kotowazaRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.asSequence().map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> kotowazaTangoTrie.getLetterShortArray(
                                it.nodeId,
                                kotowazaRank0ArrayLBSTango,
                                kotowazaRank1ArrayLBSTango
                            )
                        },
                        type = 16,
                        length = yomi.length.toUByte(),
                        score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + SCORE_OFFSET * (yomi.length - input.length),
                        leftId = kotowazaTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = kotowazaTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }.distinctBy { it.string }.toList()
        }

        val listOfDictionaryToday: Deferred<List<Candidate>> = async(Dispatchers.Default) {
            return@async when (input) {
                "きょう" -> {
                    val today = Calendar.getInstance()
                    createCandidatesForDate(today, input)
                }

                "きのう" -> {
                    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    createCandidatesForDate(yesterday, input)
                }

                "あした" -> {
                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    createCandidatesForDate(tomorrow, input)
                }

                else -> emptyList()
            }
        }

        val numbersDeferred = async(Dispatchers.Default) {

            val numbersList = input.toNumber()
            val numberExponent = input.toNumberExponent()
            val numberInKanji = input.toKanjiNumber()
            if (numbersList != null && numberExponent != null) {
                return@async listOf(
                    Candidate(
                        string = numbersList.first.toLong().convertToKanjiNotation(),
                        type = 17,
                        length = input.length.toUByte(),
                        score = 8000,
                        leftId = 2040,
                        rightId = 2040
                    )
                ) + numbersList.toList().map {
                    Candidate(
                        string = numbersList.first.addCommasToNumber(),
                        type = 19,
                        length = input.length.toUByte(),
                        score = 8001,
                        leftId = 2040,
                        rightId = 2040
                    )
                } + numbersList.toList().map {
                    Candidate(
                        string = it,
                        type = 18,
                        length = input.length.toUByte(),
                        score = 8002,
                        leftId = 2040,
                        rightId = 2040
                    )
                } + numbersList.toList().map {
                    Candidate(
                        string = numberExponent.first,
                        type = 20,
                        length = input.length.toUByte(),
                        score = 8003,
                        leftId = 2040,
                        rightId = 2040
                    )
                }
            } else if (numbersList != null) {
                if (numberInKanji != null) {
                    return@async listOf(
                        Candidate(
                            string = numberInKanji,
                            type = 21,
                            length = input.length.toUByte(),
                            score = 8000,
                            leftId = 2040,
                            rightId = 2040
                        )
                    ) + numbersList.toList().map {
                        Candidate(
                            string = numbersList.first.addCommasToNumber(),
                            type = 19,
                            length = input.length.toUByte(),
                            score = 8001,
                            leftId = 2040,
                            rightId = 2040
                        )
                    } + numbersList.toList().map {
                        Candidate(
                            string = it,
                            type = 18,
                            length = input.length.toUByte(),
                            score = 8002,
                            leftId = 2040,
                            rightId = 2040
                        )
                    }
                } else {
                    return@async listOf(
                        Candidate(
                            string = numbersList.first.toLong().convertToKanjiNotation(),
                            type = 17,
                            length = input.length.toUByte(),
                            score = 1000,
                            leftId = 2040,
                            rightId = 2040
                        )
                    ) + numbersList.toList().map {
                        Candidate(
                            string = numbersList.first.addCommasToNumber(),
                            type = 19,
                            length = input.length.toUByte(),
                            score = 1001,
                            leftId = 2040,
                            rightId = 2040
                        )
                    } + numbersList.toList().map {
                        Candidate(
                            string = it,
                            type = 18,
                            length = input.length.toUByte(),
                            score = 1002,
                            leftId = 2040,
                            rightId = 2040
                        )
                    }
                }
            } else {
                return@async emptyList()
            }
        }

        val secondPartList = secondPartDeferred.await().sortedBy { it.score }
            .filter { it.score - resultNBestFinalDeferred.first().score < 2000 }

        val secondPartFinalList = if (secondPartList.size >= 5) {
            secondPartList.subList(0, 4)
        } else {
            secondPartList
        }

        return@withContext ((resultNBestFinalDeferred + readingCorrectionListDeferred.await() + predictiveSearchResultDeferred.await() + secondPartFinalList + kotowazaListDeferred.await() + numbersDeferred.await()).sortedBy { it.score } + (listOfDictionaryToday.await() + emojiListDeferred.await() + emoticonListDeferred.await()).sortedBy { it.score } + symbolListDeferred.await() + hirakanaAndKana + yomiPartListDeferred.await() + singleKanjiListDeferred.await()).distinctBy { it.string }
    }

    fun getSymbolEmojiCandidates(): List<String> = emojiTokenArray.getNodeIds().map {
        emojiTangoTrie.getLetterShortArray(
            it,
            emojiRank0ArrayLBSTango,
            emojiRank1ArrayLBSTango
        )
    }.distinct().sortByFaceEmojiFirst()

    fun getSymbolEmoticonCandidates(): List<String> = emoticonTokenArray.getNodeIds().map {
        emoticonTangoTrie.getLetterShortArray(
            it,
            emoticonRank0ArrayLBSTango,
            emoticonRank1ArrayLBSTango
        )
    }.distinct()

    fun getSymbolCandidates():
            List<String> = symbolTokenArray.getNodeIds().map {
        if (it >= 0) {
            symbolTangoTrie.getLetterShortArray(
                it, symbolRank0ArrayLBSTango, symbolRank1ArrayLBSTango
            )
        } else {
            ""
        }
    }.filterNot { it.isBlank() }

    private fun createCandidatesForDate(
        calendar: Calendar, input: String
    ): List<Candidate> {
        val formatter1 = SimpleDateFormat("M/d", Locale.getDefault())
        val formatter2 = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val formatter3 = SimpleDateFormat("M月d日(EEE)", Locale.getDefault())
        val formatterReiwa =
            "令和${calendar.get(Calendar.YEAR) - 2018}年${calendar.get(Calendar.MONTH) + 1}月${
                calendar.get(Calendar.DAY_OF_MONTH)
            }日"
        val formatterR06 = "R${calendar.get(Calendar.YEAR) - 2018}/${
            String.format(
                "%02d", calendar.get(Calendar.MONTH) + 1
            )
        }/${String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

        return listOf(
            Candidate(
                string = formatter1.format(calendar.time),  // M/d format
                type = 14,
                length = input.length.toUByte(),
                score = 4000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatter2.format(calendar.time),  // yyyy/MM/dd format
                type = 14,
                length = input.length.toUByte(),
                score = 4000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatter3.format(calendar.time),  // M月d日(曜日) format
                type = 14,
                length = input.length.toUByte(),
                score = 4000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatterReiwa,  // 令和 format
                type = 14,
                length = input.length.toUByte(),
                score = 4000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatterR06,  // Rxx/MM/dd format
                type = 14,
                length = input.length.toUByte(),
                score = 4000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = dayOfWeek,  // 曜日 format
                type = 14,
                length = input.length.toUByte(),
                score = 4000,
                leftId = 1851,
                rightId = 1851
            )
        )
    }

    suspend fun nBestPath(
        input: String, n: Int
    ): List<Candidate> {
        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray,
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess,
        )
        val result = findPath.backwardAStar(graph, input.length, connectionIds, n)
        result.apply {
            if (!this.map { it.string }.contains(input)) {
                add(
                    Candidate(
                        string = input,
                        type = (3).toByte(),
                        length = (input.length).toUByte(),
                        score = (input.length) * 2000
                    )
                )
            }
            if (!this.map { it.string }.contains(input.hiraToKata())) {
                add(
                    Candidate(
                        string = input.hiraToKata(),
                        type = (3).toByte(),
                        length = (input.length).toUByte(),
                        score = (input.length) * 2000
                    )
                )
            }
        }
        return result
    }

    private fun nBestPathForLongest(
        input: String, n: Int
    ): List<CandidateTemp> {
        val graph = graphBuilder.constructGraphLongest(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray,
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess,
        )
        return findPath.backwardAStarForLongest(graph, input.length, connectionIds, n)
    }

    private fun preprocessLBSInBoolArray(LBSInBoolArray: BooleanArray): IntArray {
        val prefixSum = IntArray(LBSInBoolArray.size + 1)
        for (i in LBSInBoolArray.indices) {
            prefixSum[i + 1] = prefixSum[i] + if (LBSInBoolArray[i]) 0 else 1
        }
        return prefixSum
    }

}