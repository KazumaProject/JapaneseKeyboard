package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import androidx.core.text.isDigitsOnly
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank0GetIntArray
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.convertFullWidthToHalfWidth
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.sortByEmojiCategory
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumberExponent
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.preprocessLBSIntoBooleanArray
import com.kazumaproject.toBooleanArray
import com.kazumaproject.toFullWidthDigitsEfficient
import com.kazumaproject.viterbi.FindPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipInputStream

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

    private var personYomiTrie: LOUDSWithTermId? = null
    private var personTangoTrie: LOUDS? = null
    private var personTokenArray: TokenArray? = null

    private var personRank0ArrayLBSYomi: IntArray? = null
    private var personRank1ArrayLBSYomi: IntArray? = null
    private var personRank1ArrayIsLeaf: IntArray? = null
    private var personRank0ArrayTokenArrayBitvector: IntArray? = null
    private var personRank1ArrayTokenArrayBitvector: IntArray? = null
    private var personRank0ArrayLBSTango: IntArray? = null
    private var personRank1ArrayLBSTango: IntArray? = null
    private var personYomiLBSBooleanArray: BooleanArray? = null
    private var personYomiLBSPreprocess: IntArray? = null

    private var placesYomiTrie: LOUDSWithTermId? = null
    private var placesTangoTrie: LOUDS? = null
    private var placesTokenArray: TokenArray? = null

    private var placesRank0ArrayLBSYomi: IntArray? = null
    private var placesRank1ArrayLBSYomi: IntArray? = null
    private var placesRank1ArrayIsLeaf: IntArray? = null
    private var placesRank0ArrayTokenArrayBitvector: IntArray? = null
    private var placesRank1ArrayTokenArrayBitvector: IntArray? = null
    private var placesRank0ArrayLBSTango: IntArray? = null
    private var placesRank1ArrayLBSTango: IntArray? = null
    private var placesYomiLBSBooleanArray: BooleanArray? = null
    private var placesYomiLBSPreprocess: IntArray? = null

    private var wikiYomiTrie: LOUDSWithTermId? = null
    private var wikiTangoTrie: LOUDS? = null
    private var wikiTokenArray: TokenArray? = null

    private var wikiRank0ArrayLBSYomi: IntArray? = null
    private var wikiRank1ArrayLBSYomi: IntArray? = null
    private var wikiRank1ArrayIsLeaf: IntArray? = null
    private var wikiRank0ArrayTokenArrayBitvector: IntArray? = null
    private var wikiRank1ArrayTokenArrayBitvector: IntArray? = null
    private var wikiRank0ArrayLBSTango: IntArray? = null
    private var wikiRank1ArrayLBSTango: IntArray? = null
    private var wikiYomiLBSBooleanArray: BooleanArray? = null
    private var wikiYomiLBSPreprocess: IntArray? = null

    private var neologdYomiTrie: LOUDSWithTermId? = null
    private var neologdTangoTrie: LOUDS? = null
    private var neologdTokenArray: TokenArray? = null

    private var neologdRank0ArrayLBSYomi: IntArray? = null
    private var neologdRank1ArrayLBSYomi: IntArray? = null
    private var neologdRank1ArrayIsLeaf: IntArray? = null
    private var neologdRank0ArrayTokenArrayBitvector: IntArray? = null
    private var neologdRank1ArrayTokenArrayBitvector: IntArray? = null
    private var neologdRank0ArrayLBSTango: IntArray? = null
    private var neologdRank1ArrayLBSTango: IntArray? = null
    private var neologdYomiLBSBooleanArray: BooleanArray? = null
    private var neologdYomiLBSPreprocess: IntArray? = null

    private var webYomiTrie: LOUDSWithTermId? = null
    private var webTangoTrie: LOUDS? = null
    private var webTokenArray: TokenArray? = null

    private var webRank0ArrayLBSYomi: IntArray? = null
    private var webRank1ArrayLBSYomi: IntArray? = null
    private var webRank1ArrayIsLeaf: IntArray? = null
    private var webRank0ArrayTokenArrayBitvector: IntArray? = null
    private var webRank1ArrayTokenArrayBitvector: IntArray? = null
    private var webRank0ArrayLBSTango: IntArray? = null
    private var webRank1ArrayLBSTango: IntArray? = null
    private var webYomiLBSBooleanArray: BooleanArray? = null
    private var webYomiLBSPreprocess: IntArray? = null

    companion object {
        const val SCORE_OFFSET = 8000
        const val SCORE_OFFSET_SMALL = 6000
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

        systemYomiLBSPreprocess: IntArray,
        singleKanjiYomiLBSPreprocess: IntArray,
        emojiYomiLBSPreprocess: IntArray,
        emoticonYomiLBSPreprocess: IntArray,
        symbolYomiLBSPreprocess: IntArray,
        readingCorrectionYomiLBSPreprocess: IntArray,
        kotowazaYomiLBSPreprocess: IntArray
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
        this@KanaKanjiEngine.kotowazaRank0ArrayLBSTango = kotowazaRank0ArrayLBSTango
        this@KanaKanjiEngine.kotowazaRank1ArrayLBSTango = kotowazaRank1ArrayLBSTango
        this@KanaKanjiEngine.kotowazaYomiLBSBooleanArray = kotowazaYomiLBSBooleanArray

        this@KanaKanjiEngine.systemYomiLBSPreprocess = systemYomiLBSPreprocess
        this@KanaKanjiEngine.singleKanjiYomiLBSPreprocess = singleKanjiYomiLBSPreprocess
        this@KanaKanjiEngine.emojiYomiLBSPreprocess = emojiYomiLBSPreprocess
        this@KanaKanjiEngine.emoticonYomiLBSPreprocess = emoticonYomiLBSPreprocess
        this@KanaKanjiEngine.symbolYomiLBSPreprocess = symbolYomiLBSPreprocess
        this@KanaKanjiEngine.readingCorrectionYomiLBSPreprocess = readingCorrectionYomiLBSPreprocess
        this@KanaKanjiEngine.kotowazaYomiLBSPreprocess = kotowazaYomiLBSPreprocess

    }

    fun buildPersonNamesDictionary(context: Context) {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("person_name/tango_person_names.dat")))
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("person_name/yomi_person_names.dat")))
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("person_name/token_person_names.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))

        this.personTangoTrie = LOUDS().readExternalNotCompress(objectInputTango)
        this.personYomiTrie = LOUDSWithTermId().readExternalNotCompress(objectInputYomi)

        this.personTokenArray = TokenArray()
        this.personTokenArray?.readExternal(objectInputTokenArray)
        this.personTokenArray?.readPOSTable(objectInputReadPOSTable)

        this.personRank0ArrayLBSYomi = personYomiTrie?.LBS?.rank0GetIntArray()
        this.personRank1ArrayLBSYomi = personYomiTrie?.LBS?.rank1GetIntArray()
        this.personRank1ArrayIsLeaf = personYomiTrie?.isLeaf?.rank1GetIntArray()
        this.personYomiLBSBooleanArray = personYomiTrie?.LBS?.toBooleanArray()
        this.personYomiLBSPreprocess =
            this.personYomiLBSBooleanArray?.preprocessLBSIntoBooleanArray()
        this.personRank0ArrayTokenArrayBitvector = personTokenArray?.bitvector?.rank0GetIntArray()
        this.personRank1ArrayTokenArrayBitvector = personTokenArray?.bitvector?.rank1GetIntArray()
        this.personRank0ArrayLBSTango = personTangoTrie?.LBS?.rank0GetIntArray()
        this.personRank1ArrayLBSTango = personTangoTrie?.LBS?.rank1GetIntArray()
    }

    fun buildPlaceDictionary(context: Context) {
        val zipInputStreamTango = ZipInputStream(context.assets.open("places/tango_places.dat.zip"))
        zipInputStreamTango.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamTango)).use {
            this.placesTangoTrie = LOUDS().readExternalNotCompress(it)
        }
        val zipInputStreamYomi = ZipInputStream(context.assets.open("places/yomi_places.dat.zip"))
        zipInputStreamYomi.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamYomi)).use {
            this.placesYomiTrie = LOUDSWithTermId().readExternalNotCompress(it)
        }

        this.placesTokenArray = TokenArray()

        ZipInputStream(context.assets.open("places/token_places.dat.zip")).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "token_places.dat") {
                    ObjectInputStream(BufferedInputStream(zipStream)).use { objectInput ->
                        this.placesTokenArray?.readExternal(objectInput)
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))

        this.placesTokenArray?.readPOSTable(objectInputReadPOSTable)

        this.placesRank0ArrayLBSYomi = placesYomiTrie?.LBS?.rank0GetIntArray()
        this.placesRank1ArrayLBSYomi = placesYomiTrie?.LBS?.rank1GetIntArray()
        this.placesRank1ArrayIsLeaf = placesYomiTrie?.isLeaf?.rank1GetIntArray()
        this.placesYomiLBSBooleanArray = placesYomiTrie?.LBS?.toBooleanArray()
        this.placesYomiLBSPreprocess =
            this.placesYomiLBSBooleanArray?.preprocessLBSIntoBooleanArray()
        this.placesRank0ArrayTokenArrayBitvector = placesTokenArray?.bitvector?.rank0GetIntArray()
        this.placesRank1ArrayTokenArrayBitvector = placesTokenArray?.bitvector?.rank1GetIntArray()
        this.placesRank0ArrayLBSTango = placesTangoTrie?.LBS?.rank0GetIntArray()
        this.placesRank1ArrayLBSTango = placesTangoTrie?.LBS?.rank1GetIntArray()
    }

    fun buildWikiDictionary(context: Context) {
        val zipInputStreamTango = ZipInputStream(context.assets.open("wiki/tango_wiki.dat.zip"))
        zipInputStreamTango.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamTango)).use {
            this.wikiTangoTrie = LOUDS().readExternalNotCompress(it)
        }
        val zipInputStreamYomi = ZipInputStream(context.assets.open("wiki/yomi_wiki.dat.zip"))
        zipInputStreamYomi.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamYomi)).use {
            this.wikiYomiTrie = LOUDSWithTermId().readExternalNotCompress(it)
        }

        this.wikiTokenArray = TokenArray()

        ZipInputStream(context.assets.open("wiki/token_wiki.dat.zip")).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "token_wiki.dat") {
                    ObjectInputStream(BufferedInputStream(zipStream)).use { objectInput ->
                        this.wikiTokenArray?.readExternal(objectInput)
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))

        this.wikiTokenArray?.readPOSTable(objectInputReadPOSTable)

        this.wikiRank0ArrayLBSYomi = wikiYomiTrie?.LBS?.rank0GetIntArray()
        this.wikiRank1ArrayLBSYomi = wikiYomiTrie?.LBS?.rank1GetIntArray()
        this.wikiRank1ArrayIsLeaf = wikiYomiTrie?.isLeaf?.rank1GetIntArray()
        this.wikiYomiLBSBooleanArray = wikiYomiTrie?.LBS?.toBooleanArray()
        this.wikiYomiLBSPreprocess = this.wikiYomiLBSBooleanArray?.preprocessLBSIntoBooleanArray()
        this.wikiRank0ArrayTokenArrayBitvector = wikiTokenArray?.bitvector?.rank0GetIntArray()
        this.wikiRank1ArrayTokenArrayBitvector = wikiTokenArray?.bitvector?.rank1GetIntArray()
        this.wikiRank0ArrayLBSTango = wikiTangoTrie?.LBS?.rank0GetIntArray()
        this.wikiRank1ArrayLBSTango = wikiTangoTrie?.LBS?.rank1GetIntArray()
    }

    fun buildNeologdDictionary(context: Context) {
        val zipInputStreamTango =
            ZipInputStream(context.assets.open("neologd/tango_neologd.dat.zip"))
        zipInputStreamTango.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamTango)).use {
            this.neologdTangoTrie = LOUDS().readExternalNotCompress(it)
        }
        val zipInputStreamYomi = ZipInputStream(context.assets.open("neologd/yomi_neologd.dat.zip"))
        zipInputStreamYomi.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamYomi)).use {
            this.neologdYomiTrie = LOUDSWithTermId().readExternalNotCompress(it)
        }

        this.neologdTokenArray = TokenArray()

        ZipInputStream(context.assets.open("neologd/token_neologd.dat.zip")).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "token_neologd.dat") {
                    ObjectInputStream(BufferedInputStream(zipStream)).use { objectInput ->
                        this.neologdTokenArray?.readExternal(objectInput)
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))

        this.neologdTokenArray?.readPOSTable(objectInputReadPOSTable)

        this.neologdRank0ArrayLBSYomi = neologdYomiTrie?.LBS?.rank0GetIntArray()
        this.neologdRank1ArrayLBSYomi = neologdYomiTrie?.LBS?.rank1GetIntArray()
        this.neologdRank1ArrayIsLeaf = neologdYomiTrie?.isLeaf?.rank1GetIntArray()
        this.neologdYomiLBSBooleanArray = neologdYomiTrie?.LBS?.toBooleanArray()
        this.neologdYomiLBSPreprocess =
            this.neologdYomiLBSBooleanArray?.preprocessLBSIntoBooleanArray()
        this.neologdRank0ArrayTokenArrayBitvector = neologdTokenArray?.bitvector?.rank0GetIntArray()
        this.neologdRank1ArrayTokenArrayBitvector = neologdTokenArray?.bitvector?.rank1GetIntArray()
        this.neologdRank0ArrayLBSTango = neologdTangoTrie?.LBS?.rank0GetIntArray()
        this.neologdRank1ArrayLBSTango = neologdTangoTrie?.LBS?.rank1GetIntArray()
    }

    fun buildWebDictionary(context: Context) {
        val zipInputStreamTango =
            ZipInputStream(context.assets.open("web/tango_web.dat.zip"))
        zipInputStreamTango.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamTango)).use {
            this.webTangoTrie = LOUDS().readExternalNotCompress(it)
        }
        val zipInputStreamYomi = ZipInputStream(context.assets.open("web/yomi_web.dat.zip"))
        zipInputStreamYomi.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStreamYomi)).use {
            this.webYomiTrie = LOUDSWithTermId().readExternalNotCompress(it)
        }

        this.webTokenArray = TokenArray()

        ZipInputStream(context.assets.open("web/token_web.dat.zip")).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "token_web.dat") {
                    ObjectInputStream(BufferedInputStream(zipStream)).use { objectInput ->
                        this.webTokenArray?.readExternal(objectInput)
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))

        this.webTokenArray?.readPOSTable(objectInputReadPOSTable)

        this.webRank0ArrayLBSYomi = webYomiTrie?.LBS?.rank0GetIntArray()
        this.webRank1ArrayLBSYomi = webYomiTrie?.LBS?.rank1GetIntArray()
        this.webRank1ArrayIsLeaf = webYomiTrie?.isLeaf?.rank1GetIntArray()
        this.webYomiLBSBooleanArray = webYomiTrie?.LBS?.toBooleanArray()
        this.webYomiLBSPreprocess =
            this.webYomiLBSBooleanArray?.preprocessLBSIntoBooleanArray()
        this.webRank0ArrayTokenArrayBitvector = webTokenArray?.bitvector?.rank0GetIntArray()
        this.webRank1ArrayTokenArrayBitvector = webTokenArray?.bitvector?.rank1GetIntArray()
        this.webRank0ArrayLBSTango = webTangoTrie?.LBS?.rank0GetIntArray()
        this.webRank1ArrayLBSTango = webTangoTrie?.LBS?.rank1GetIntArray()
    }

    fun releasePersonNamesDictionary() {
        this.personTangoTrie = null
        this.personYomiTrie = null
        this.personTokenArray = null
        this.personRank0ArrayLBSYomi = null
        this.personRank1ArrayLBSYomi = null
        this.personRank1ArrayIsLeaf = null
        this.personYomiLBSBooleanArray = null
        this.personYomiLBSPreprocess = null
        this.personRank0ArrayTokenArrayBitvector = null
        this.personRank1ArrayTokenArrayBitvector = null
        this.personRank0ArrayLBSTango = null
        this.personRank1ArrayLBSTango = null
    }

    fun releasePlacesDictionary() {
        this.placesTangoTrie = null
        this.placesYomiTrie = null
        this.placesTokenArray = null
        this.placesRank0ArrayLBSYomi = null
        this.placesRank1ArrayLBSYomi = null
        this.placesRank1ArrayIsLeaf = null
        this.placesYomiLBSBooleanArray = null
        this.placesYomiLBSPreprocess = null
        this.placesRank0ArrayTokenArrayBitvector = null
        this.placesRank1ArrayTokenArrayBitvector = null
        this.placesRank0ArrayLBSTango = null
        this.placesRank1ArrayLBSTango = null
    }

    fun releaseWikiDictionary() {
        this.wikiTangoTrie = null
        this.wikiYomiTrie = null
        this.wikiTokenArray = null
        this.wikiRank0ArrayLBSYomi = null
        this.wikiRank1ArrayLBSYomi = null
        this.wikiRank1ArrayIsLeaf = null
        this.wikiYomiLBSBooleanArray = null
        this.wikiYomiLBSPreprocess = null
        this.wikiRank0ArrayTokenArrayBitvector = null
        this.wikiRank1ArrayTokenArrayBitvector = null
        this.wikiRank0ArrayLBSTango = null
        this.wikiRank1ArrayLBSTango = null
    }

    fun releaseNeologdDictionary() {
        this.neologdTangoTrie = null
        this.neologdYomiTrie = null
        this.neologdTokenArray = null
        this.neologdRank0ArrayLBSYomi = null
        this.neologdRank1ArrayLBSYomi = null
        this.neologdRank1ArrayIsLeaf = null
        this.neologdYomiLBSBooleanArray = null
        this.neologdYomiLBSPreprocess = null
        this.neologdRank0ArrayTokenArrayBitvector = null
        this.neologdRank1ArrayTokenArrayBitvector = null
        this.neologdRank0ArrayLBSTango = null
        this.neologdRank1ArrayLBSTango = null
    }

    fun releaseWebDictionary() {
        this.webTangoTrie = null
        this.webYomiTrie = null
        this.webTokenArray = null
        this.webRank0ArrayLBSYomi = null
        this.webRank1ArrayLBSYomi = null
        this.webRank1ArrayIsLeaf = null
        this.webYomiLBSBooleanArray = null
        this.webYomiLBSPreprocess = null
        this.webRank0ArrayTokenArrayBitvector = null
        this.webRank1ArrayTokenArrayBitvector = null
        this.webRank0ArrayLBSTango = null
        this.webRank1ArrayLBSTango = null
    }

    fun isMozcUTPersonDictionariesInitialized(): Boolean {
        return !(this.personYomiTrie == null || this.personTangoTrie == null || this.personTokenArray == null)
    }

    fun isMozcUTPlacesDictionariesInitialized(): Boolean {
        return !(this.placesYomiTrie == null || this.placesTangoTrie == null || this.placesTokenArray == null)
    }

    fun isMozcUTWikiDictionariesInitialized(): Boolean {
        return !(this.wikiYomiTrie == null || this.wikiTangoTrie == null || this.wikiTokenArray == null)
    }

    fun isMozcUTNeologdDictionariesInitialized(): Boolean {
        return !(this.neologdYomiTrie == null || this.neologdTangoTrie == null || this.neologdTokenArray == null)
    }

    fun isMozcUTWebDictionariesInitialized(): Boolean {
        return !(this.webYomiTrie == null || this.webTangoTrie == null || this.webTokenArray == null)
    }

    suspend fun getCandidates(
        input: String, n: Int, appPreference: AppPreference
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
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess
        )

        val resultNBestFinalDeferred: List<Candidate> = withContext(Dispatchers.Default) {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            val fullWidth: Candidate = withContext(Dispatchers.Default) {
                Candidate(
                    string = input.toFullWidthDigitsEfficient(),
                    type = 22,
                    length = input.length.toUByte(),
                    score = 8000,
                    leftId = 2040,
                    rightId = 2040
                )
            }
            return resultNBestFinalDeferred + fullWidth
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000)
        )

        val emojiCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emojiYomiTrie,
            rank0ArrayLBSYomi = emojiRank0ArrayLBSYomi,
            rank1ArrayLBSYomi = emojiRank1ArrayLBSYomi
        )

        val emoticonCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emoticonYomiTrie,
            rank0ArrayLBSYomi = emoticonRank0ArrayLBSYomi,
            rank1ArrayLBSYomi = emoticonRank1ArrayLBSYomi
        )

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            rank0ArrayLBSYomi = symbolRank0ArrayLBSYomi,
            rank1ArrayLBSYomi = symbolRank1ArrayLBSYomi
        )

        val emojiListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emojiCommonPrefixDeferred,
            yomiTrie = emojiYomiTrie,
            tokenArray = emojiTokenArray,
            tangoTrie = emojiTangoTrie,
            yomiRank1ArrayLBS = emojiRank1ArrayLBSYomi,
            yomiLBSBooleanArray = emojiYomiLBSBooleanArray,
            yomiLBSPreprocess = emojiYomiLBSPreprocess,
            rank1ArrayIsLeaf = emojiRank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector = emojiRank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector = emojiRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = emojiRank0ArrayLBSTango,
            rank1ArrayLBSTango = emojiRank1ArrayLBSTango,
            type = 11
        )

        val emoticonListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emoticonCommonPrefixDeferred,
            yomiTrie = emoticonYomiTrie,
            tokenArray = emoticonTokenArray,
            tangoTrie = emoticonTangoTrie,
            yomiRank1ArrayLBS = emoticonRank1ArrayLBSYomi,
            yomiLBSBooleanArray = emoticonYomiLBSBooleanArray,
            yomiLBSPreprocess = emoticonYomiLBSPreprocess,
            rank1ArrayIsLeaf = emoticonRank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector = emoticonRank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector = emoticonRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = emoticonRank0ArrayLBSTango,
            rank1ArrayLBSTango = emoticonRank1ArrayLBSTango,
            type = 12
        )

        val symbolListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = symbolCommonPrefixDeferred,
            yomiTrie = symbolYomiTrie,
            tokenArray = symbolTokenArray,
            tangoTrie = symbolTangoTrie,
            yomiRank1ArrayLBS = symbolRank1ArrayLBSYomi,
            yomiLBSBooleanArray = symbolYomiLBSBooleanArray,
            yomiLBSPreprocess = symbolYomiLBSPreprocess,
            rank1ArrayIsLeaf = symbolRank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector = symbolRank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector = symbolRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = symbolRank0ArrayLBSTango,
            rank1ArrayLBSTango = symbolRank1ArrayLBSTango,
            type = 13
        )

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            yomiRank1ArrayLBS = singleKanjiRank1ArrayLBSYomi,
            yomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray,
            yomiLBSPreprocess = singleKanjiYomiLBSPreprocess,
            rank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector = singleKanjiRank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector = singleKanjiRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = singleKanjiRank0ArrayLBSTango,
            rank1ArrayLBSTango = singleKanjiRank1ArrayLBSTango,
            type = 7
        )

        val symbolCommonPrefixDeferredHalfWidth =
            if (input.all { !it.isLetterOrDigit() && !it.isWhitespace() }) listOf(input.convertFullWidthToHalfWidth())
            else emptyList()

        val symbolHalfWidthListDeferred =
            if (symbolCommonPrefixDeferredHalfWidth.isEmpty()) emptyList() else deferredFromDictionary(
                commonPrefixListString = symbolCommonPrefixDeferredHalfWidth,
                yomiTrie = symbolYomiTrie,
                tokenArray = symbolTokenArray,
                tangoTrie = symbolTangoTrie,
                yomiRank1ArrayLBS = symbolRank1ArrayLBSYomi,
                yomiLBSBooleanArray = symbolYomiLBSBooleanArray,
                yomiLBSPreprocess = symbolYomiLBSPreprocess,
                rank1ArrayIsLeaf = symbolRank1ArrayIsLeaf,
                rank0ArrayTokenArrayBitvector = symbolRank0ArrayTokenArrayBitvector,
                rank1ArrayTokenArrayBitvector = symbolRank1ArrayTokenArrayBitvector,
                rank0ArrayLBSTango = symbolRank0ArrayLBSTango,
                rank1ArrayLBSTango = symbolRank1ArrayLBSTango,
                type = 21
            )

        if (input.length == 1) return resultNBestFinalDeferred + hirakanaAndKana + emojiListDeferred + emoticonListDeferred + symbolListDeferred + symbolHalfWidthListDeferred + singleKanjiListDeferred

        val yomiPartOfDeferred = withContext(Dispatchers.Default) {
            if (input.length > 16) {
                emptyList()
            } else {
                systemYomiTrie.commonPrefixSearch(
                    str = input,
                    rank0Array = systemRank0ArrayLBSYomi,
                    rank1Array = systemRank1ArrayLBSYomi
                ).asReversed()
            }
        }
        val predictiveSearchDeferred = deferredPrediction(
            input = input,
            yomiTrie = systemYomiTrie,
            rank0ArrayLBSYomi = systemRank0ArrayLBSYomi,
            rank1ArrayLBSYomi = systemRank1ArrayLBSYomi
        )

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            rank0ArrayLBSYomi = readingCorrectionRank0ArrayLBSYomi,
            rank1ArrayLBSYomi = readingCorrectionRank1ArrayLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            rank0ArrayLBSYomi = kotowazaRank0ArrayLBSYomi,
            rank1ArrayLBSYomi = kotowazaRank1ArrayLBSYomi
        )

        val predictiveSearchResultDeferred: List<Candidate> = withContext(Dispatchers.Default) {
            val yomiList = predictiveSearchDeferred.filter { it.length != input.length }
            yomiList.flatMap { yomi ->
                val nodeIndex = systemYomiTrie.getNodeIndex(
                    yomi,
                    systemRank1ArrayLBSYomi,
                    systemYomiLBSBooleanArray,
                    systemYomiLBSPreprocess
                )
                val termId = systemYomiTrie.getTermId(nodeIndex, systemRank1ArrayIsLeaf)

                systemTokenArray.getListDictionaryByYomiTermId(
                    termId, systemRank0ArrayTokenArrayBitvector, systemRank1ArrayTokenArrayBitvector
                ).map { token ->
                    val baseCost = token.wordCost.toInt()
                    val score = when {
                        yomi.length == input.length -> baseCost
                        input.length <= 5 -> baseCost + SCORE_OFFSET * (yomi.length - input.length)
                        else -> baseCost + SCORE_OFFSET_SMALL
                    }
                    Candidate(
                        string = when (token.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> systemTangoTrie.getLetter(
                                token.nodeId, systemRank0ArrayLBSTango, systemRank1ArrayLBSTango
                            )
                        },
                        type = 9,
                        length = yomi.length.toUByte(),
                        score = score,
                        leftId = systemTokenArray.leftIds[token.posTableIndex.toInt()],
                        rightId = systemTokenArray.rightIds[token.posTableIndex.toInt()]
                    )
                }
            }.sortedBy { it.score }.take(n)
        }

        val yomiPartListDeferred: List<Candidate> = withContext(Dispatchers.Default) {
            yomiPartOfDeferred.flatMap { yomi ->
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
                ).map {
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
                }
            }
        }

        val readingCorrectionListDeferred: List<Candidate> = withContext(Dispatchers.Default) {
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
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
                ).map {
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
            }
        }

        val kotowazaListDeferred: List<Candidate> = withContext(Dispatchers.Default) {
            kotowazaCommonPrefixDeferred.flatMap { yomi ->
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
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> kotowazaTangoTrie.getLetterShortArray(
                                it.nodeId, kotowazaRank0ArrayLBSTango, kotowazaRank1ArrayLBSTango
                            )
                        },
                        type = 16,
                        length = yomi.length.toUByte(),
                        score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + SCORE_OFFSET * (yomi.length - input.length),
                        leftId = kotowazaTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = kotowazaTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }
            }
        }

        val listOfDictionaryToday: List<Candidate> = withContext(Dispatchers.Default) {
            when (input) {
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

        val numbersDeferred = withContext(Dispatchers.Default) {
            val numbersList = input.toNumber()
            val numberExponent = input.toNumberExponent()

            if (numbersList != null && numberExponent != null) {
                listOf(
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
                listOf(
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
                }
            } else {
                emptyList()
            }
        }

        appPreference.apply {
            val mozcUTPersonName = mozc_ut_person_names_preference ?: false
            val mozcUTPlaces = mozc_ut_places_preference ?: false
            val mozcUTWiki = mozc_ut_wiki_preference ?: false
            val mozcUTNeologd = mozc_ut_neologd_preference ?: false
            val mozcUTWeb = mozc_ut_web_preference ?: false

            val mozcUTPersonNames =
                if (mozcUTPersonName) getMozcUTPersonNames(input) else emptyList()
            val mozcUTPlacesList = if (mozcUTPlaces) getMozcUTPlace(input) else emptyList()
            val mozcUTWikiList = if (mozcUTWiki) getMozcUTWiki(input) else emptyList()
            val mozcUTNeologdList = if (mozcUTNeologd) getMozcUTNeologd(input) else emptyList()
            val mozcUTWebList = if (mozcUTWeb) getMozcUTWeb(input) else emptyList()

            val resultList = resultNBestFinalDeferred +
                    readingCorrectionListDeferred +
                    predictiveSearchResultDeferred +
                    kotowazaListDeferred +
                    mozcUTPersonNames +
                    mozcUTPlacesList +
                    mozcUTWikiList +
                    mozcUTNeologdList +
                    mozcUTWebList

            return (resultList.sortedBy { it.score } +
                    numbersDeferred +
                    symbolHalfWidthListDeferred +
                    (listOfDictionaryToday + emojiListDeferred + emoticonListDeferred).sortedBy { it.score } +
                    symbolListDeferred +
                    hirakanaAndKana +
                    yomiPartListDeferred +
                    singleKanjiListDeferred)
        }

    }

    fun getSymbolEmojiCandidates(): List<String> = emojiTokenArray.getNodeIds().map {
        emojiTangoTrie.getLetterShortArray(
            it, emojiRank0ArrayLBSTango, emojiRank1ArrayLBSTango
        )
    }.distinct().sortByEmojiCategory()

    fun getSymbolEmoticonCandidates(): List<String> = emoticonTokenArray.getNodeIds().map {
        emoticonTangoTrie.getLetterShortArray(
            it, emoticonRank0ArrayLBSTango, emoticonRank1ArrayLBSTango
        )
    }.distinct()

    fun getSymbolCandidates(): List<String> = symbolTokenArray.getNodeIds().map {
        if (it >= 0) {
            symbolTangoTrie.getLetterShortArray(
                it, symbolRank0ArrayLBSTango, symbolRank1ArrayLBSTango
            )
        } else {
            ""
        }
    }.distinct().filterNot { it.isBlank() }

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
                Locale.getDefault(), "%02d", calendar.get(Calendar.MONTH) + 1
            )
        }/${String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
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

    private suspend fun deferredFromDictionarySymbols(
        input: String,
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        yomiRank1ArrayLBS: ShortArray,
        yomiLBSBooleanArray: BooleanArray,
        yomiLBSPreprocess: IntArray,
        rank1ArrayIsLeaf: ShortArray,
        rank0ArrayTokenArrayBitvector: ShortArray,
        rank1ArrayTokenArrayBitvector: ShortArray,
        rank0ArrayLBSTango: ShortArray,
        rank1ArrayLBSTango: ShortArray,
        type: Byte
    ) = withContext(Dispatchers.Default) {
        commonPrefixListString.flatMap { yomi ->
            if (input.length > yomi.length) return@withContext emptyList()
            val termId = yomiTrie.getTermIdShortArray(
                yomiTrie.getNodeIndex(
                    yomi, yomiRank1ArrayLBS, yomiLBSBooleanArray, yomiLBSPreprocess
                ), rank1ArrayIsLeaf
            )
            tokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, rank0ArrayTokenArrayBitvector, rank1ArrayTokenArrayBitvector
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> tangoTrie.getLetterShortArray(
                            it.nodeId, rank0ArrayLBSTango, rank1ArrayLBSTango
                        )
                    },
                    type = type,
                    length = yomi.length.toUByte(),
                    score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + 1000 * (yomi.length - input.length),
                    leftId = tokenArray.leftIds[it.posTableIndex.toInt()],
                    rightId = tokenArray.rightIds[it.posTableIndex.toInt()]
                )
            }
        }
    }

    private suspend fun deferredFromMozcUTDictionary(
        input: String,
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        yomiRank1ArrayLBS: IntArray,
        yomiLBSBooleanArray: BooleanArray,
        yomiLBSPreprocess: IntArray,
        rank1ArrayIsLeaf: IntArray,
        rank0ArrayTokenArrayBitvector: IntArray,
        rank1ArrayTokenArrayBitvector: IntArray,
        rank0ArrayLBSTango: IntArray,
        rank1ArrayLBSTango: IntArray,
        type: Byte,
        n: Int
    ) = withContext(Dispatchers.Default) {
        commonPrefixListString.flatMap { yomi ->
            if (input.length > yomi.length) return@withContext emptyList()
            val termId = yomiTrie.getTermId(
                yomiTrie.getNodeIndex(
                    yomi, yomiRank1ArrayLBS, yomiLBSBooleanArray, yomiLBSPreprocess
                ), rank1ArrayIsLeaf
            )
            tokenArray.getListDictionaryByYomiTermId(
                termId, rank0ArrayTokenArrayBitvector, rank1ArrayTokenArrayBitvector
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> tangoTrie.getLetter(
                            it.nodeId, rank0ArrayLBSTango, rank1ArrayLBSTango
                        )
                    },
                    type = type,
                    length = yomi.length.toUByte(),
                    score = if (yomi.length == input.length) it.wordCost.toInt() else it.wordCost.toInt() + 1500 * (yomi.length - input.length),
                    leftId = tokenArray.leftIds[it.posTableIndex.toInt()],
                    rightId = tokenArray.rightIds[it.posTableIndex.toInt()]
                )
            }
        }.sortedBy { it.score }.take(n)
    }

    private suspend fun deferredFromDictionary(
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        yomiRank1ArrayLBS: ShortArray,
        yomiLBSBooleanArray: BooleanArray,
        yomiLBSPreprocess: IntArray,
        rank1ArrayIsLeaf: ShortArray,
        rank0ArrayTokenArrayBitvector: ShortArray,
        rank1ArrayTokenArrayBitvector: ShortArray,
        rank0ArrayLBSTango: ShortArray,
        rank1ArrayLBSTango: ShortArray,
        type: Byte
    ) = withContext(Dispatchers.Default) {
        commonPrefixListString.flatMap { yomi ->
            val termId = yomiTrie.getTermIdShortArray(
                yomiTrie.getNodeIndex(
                    yomi, yomiRank1ArrayLBS, yomiLBSBooleanArray, yomiLBSPreprocess
                ), rank1ArrayIsLeaf
            )
            tokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, rank0ArrayTokenArrayBitvector, rank1ArrayTokenArrayBitvector
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> tangoTrie.getLetterShortArray(
                            it.nodeId, rank0ArrayLBSTango, rank1ArrayLBSTango
                        )
                    },
                    type = type,
                    length = yomi.length.toUByte(),
                    score = it.wordCost.toInt(),
                    leftId = tokenArray.leftIds[it.posTableIndex.toInt()],
                    rightId = tokenArray.rightIds[it.posTableIndex.toInt()]
                )
            }
        }
    }

    private suspend fun deferredFromDictionarySingleKanji(
        input: String,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        yomiRank1ArrayLBS: ShortArray,
        yomiLBSBooleanArray: BooleanArray,
        yomiLBSPreprocess: IntArray,
        rank1ArrayIsLeaf: ShortArray,
        rank0ArrayTokenArrayBitvector: ShortArray,
        rank1ArrayTokenArrayBitvector: ShortArray,
        rank0ArrayLBSTango: ShortArray,
        rank1ArrayLBSTango: ShortArray,
        type: Byte
    ) = withContext(Dispatchers.Default) {
        val termId = yomiTrie.getTermIdShortArray(
            yomiTrie.getNodeIndex(
                input, yomiRank1ArrayLBS, yomiLBSBooleanArray, yomiLBSPreprocess
            ), rank1ArrayIsLeaf
        )
        tokenArray.getListDictionaryByYomiTermIdShortArray(
            termId, rank0ArrayTokenArrayBitvector, rank1ArrayTokenArrayBitvector
        ).map {
            Candidate(
                string = when (it.nodeId) {
                    -2 -> input
                    -1 -> input.hiraToKata()
                    else -> tangoTrie.getLetterShortArray(
                        it.nodeId, rank0ArrayLBSTango, rank1ArrayLBSTango
                    )
                },
                type = type,
                length = input.length.toUByte(),
                score = it.wordCost.toInt(),
                leftId = tokenArray.leftIds[it.posTableIndex.toInt()],
                rightId = tokenArray.rightIds[it.posTableIndex.toInt()]
            )
        }

    }

    private fun deferredPrediction(
        input: String,
        yomiTrie: LOUDSWithTermId,
        rank0ArrayLBSYomi: IntArray,
        rank1ArrayLBSYomi: IntArray
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length <= 2) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, rank0Array = rank0ArrayLBSYomi, rank1Array = rank1ArrayLBSYomi
        ).filter {
            when (input.length) {
                in 3..4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length
            }
        }
    }

    private fun deferredPrediction(
        input: String,
        yomiTrie: LOUDSWithTermId,
        rank0ArrayLBSYomi: ShortArray,
        rank1ArrayLBSYomi: ShortArray
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length in 2..3) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, rank0Array = rank0ArrayLBSYomi, rank1Array = rank1ArrayLBSYomi
        ).filter {
            when (input.length) {
                4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length
            }
        }
    }

    private fun deferredPredictionEmojiSymbols(
        input: String,
        yomiTrie: LOUDSWithTermId,
        rank0ArrayLBSYomi: ShortArray,
        rank1ArrayLBSYomi: ShortArray
    ): List<String> {
        if (input.length > 16) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, rank0Array = rank0ArrayLBSYomi, rank1Array = rank1ArrayLBSYomi
        ).filter {
            when (input.length) {
                1 -> it.length == input.length
                in 2..3 -> it.length <= input.length + 1
                4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length
            }
        }
    }

    private fun commonPrefixMozcUT(
        input: String,
        yomiTrie: LOUDSWithTermId,
        rank0ArrayLBSYomi: IntArray,
        rank1ArrayLBSYomi: IntArray
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length in 0..3) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, rank0Array = rank0ArrayLBSYomi, rank1Array = rank1ArrayLBSYomi
        ).filter {
            when (input.length) {
                in 3..4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length || it.length == input.length
            }
        }.asReversed()
    }

    private fun commonPrefixMozcUTWeb(
        input: String,
        yomiTrie: LOUDSWithTermId,
        rank0ArrayLBSYomi: IntArray,
        rank1ArrayLBSYomi: IntArray
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length in 0..2) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, rank0Array = rank0ArrayLBSYomi, rank1Array = rank1ArrayLBSYomi
        ).filter {
            when (input.length) {
                in 3..4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length || it.length == input.length
            }
        }.asReversed()
    }

    suspend fun getMozcUTPersonNames(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUT(
            input = input,
            yomiTrie = personYomiTrie!!,
            rank0ArrayLBSYomi = personRank0ArrayLBSYomi!!,
            rank1ArrayLBSYomi = personRank1ArrayLBSYomi!!
        )
        val listDeferred = deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = personYomiTrie!!,
            tokenArray = personTokenArray!!,
            tangoTrie = personTangoTrie!!,
            yomiRank1ArrayLBS = personRank1ArrayLBSYomi!!,
            yomiLBSBooleanArray = personYomiLBSBooleanArray!!,
            yomiLBSPreprocess = personYomiLBSPreprocess!!,
            rank1ArrayIsLeaf = personRank1ArrayIsLeaf!!,
            rank0ArrayTokenArrayBitvector = personRank0ArrayTokenArrayBitvector!!,
            rank1ArrayTokenArrayBitvector = personRank1ArrayTokenArrayBitvector!!,
            rank0ArrayLBSTango = personRank0ArrayLBSTango!!,
            rank1ArrayLBSTango = personRank1ArrayLBSTango!!,
            type = 23,
            4
        )
        return listDeferred
    }

    suspend fun getMozcUTPlace(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUT(
            input = input,
            yomiTrie = placesYomiTrie!!,
            rank0ArrayLBSYomi = placesRank0ArrayLBSYomi!!,
            rank1ArrayLBSYomi = placesRank1ArrayLBSYomi!!
        )
        val listDeferred = deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = placesYomiTrie!!,
            tokenArray = placesTokenArray!!,
            tangoTrie = placesTangoTrie!!,
            yomiRank1ArrayLBS = placesRank1ArrayLBSYomi!!,
            yomiLBSBooleanArray = placesYomiLBSBooleanArray!!,
            yomiLBSPreprocess = placesYomiLBSPreprocess!!,
            rank1ArrayIsLeaf = placesRank1ArrayIsLeaf!!,
            rank0ArrayTokenArrayBitvector = placesRank0ArrayTokenArrayBitvector!!,
            rank1ArrayTokenArrayBitvector = placesRank1ArrayTokenArrayBitvector!!,
            rank0ArrayLBSTango = placesRank0ArrayLBSTango!!,
            rank1ArrayLBSTango = placesRank1ArrayLBSTango!!,
            type = 24,
            4
        )
        return listDeferred
    }

    private suspend fun getMozcUTWiki(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUTWeb(
            input = input,
            yomiTrie = wikiYomiTrie!!,
            rank0ArrayLBSYomi = wikiRank0ArrayLBSYomi!!,
            rank1ArrayLBSYomi = wikiRank1ArrayLBSYomi!!
        )
        val listDeferred = deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = wikiYomiTrie!!,
            tokenArray = wikiTokenArray!!,
            tangoTrie = wikiTangoTrie!!,
            yomiRank1ArrayLBS = wikiRank1ArrayLBSYomi!!,
            yomiLBSBooleanArray = wikiYomiLBSBooleanArray!!,
            yomiLBSPreprocess = wikiYomiLBSPreprocess!!,
            rank1ArrayIsLeaf = wikiRank1ArrayIsLeaf!!,
            rank0ArrayTokenArrayBitvector = wikiRank0ArrayTokenArrayBitvector!!,
            rank1ArrayTokenArrayBitvector = wikiRank1ArrayTokenArrayBitvector!!,
            rank0ArrayLBSTango = wikiRank0ArrayLBSTango!!,
            rank1ArrayLBSTango = wikiRank1ArrayLBSTango!!,
            type = 25,
            4
        )
        return listDeferred
    }

    private suspend fun getMozcUTNeologd(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUTWeb(
            input = input,
            yomiTrie = neologdYomiTrie!!,
            rank0ArrayLBSYomi = neologdRank0ArrayLBSYomi!!,
            rank1ArrayLBSYomi = neologdRank1ArrayLBSYomi!!
        )
        val listDeferred = deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = neologdYomiTrie!!,
            tokenArray = neologdTokenArray!!,
            tangoTrie = neologdTangoTrie!!,
            yomiRank1ArrayLBS = neologdRank1ArrayLBSYomi!!,
            yomiLBSBooleanArray = neologdYomiLBSBooleanArray!!,
            yomiLBSPreprocess = neologdYomiLBSPreprocess!!,
            rank1ArrayIsLeaf = neologdRank1ArrayIsLeaf!!,
            rank0ArrayTokenArrayBitvector = neologdRank0ArrayTokenArrayBitvector!!,
            rank1ArrayTokenArrayBitvector = neologdRank1ArrayTokenArrayBitvector!!,
            rank0ArrayLBSTango = neologdRank0ArrayLBSTango!!,
            rank1ArrayLBSTango = neologdRank1ArrayLBSTango!!,
            type = 26,
            4
        )
        return listDeferred
    }

    private suspend fun getMozcUTWeb(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUTWeb(
            input = input,
            yomiTrie = webYomiTrie!!,
            rank0ArrayLBSYomi = webRank0ArrayLBSYomi!!,
            rank1ArrayLBSYomi = webRank1ArrayLBSYomi!!
        )
        val listDeferred = deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = webYomiTrie!!,
            tokenArray = webTokenArray!!,
            tangoTrie = webTangoTrie!!,
            yomiRank1ArrayLBS = webRank1ArrayLBSYomi!!,
            yomiLBSBooleanArray = webYomiLBSBooleanArray!!,
            yomiLBSPreprocess = webYomiLBSPreprocess!!,
            rank1ArrayIsLeaf = webRank1ArrayIsLeaf!!,
            rank0ArrayTokenArrayBitvector = webRank0ArrayTokenArrayBitvector!!,
            rank1ArrayTokenArrayBitvector = webRank1ArrayTokenArrayBitvector!!,
            rank0ArrayLBSTango = webRank0ArrayLBSTango!!,
            rank1ArrayLBSTango = webRank1ArrayLBSTango!!,
            type = 27,
            4
        )
        return listDeferred
    }

}
