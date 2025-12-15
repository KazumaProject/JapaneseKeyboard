package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import android.os.Build
import androidx.core.text.isDigitsOnly
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.convertFullWidthToHalfWidth
import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toHankakuKatakana
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.domain.categorizeEmoji
import com.kazumaproject.domain.sortByEmojiCategory
import com.kazumaproject.domain.toEmoticonCategory
import com.kazumaproject.domain.toSymbolCategory
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsDigit
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsFullWidthNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthNumbersToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.groupAndReplaceJapaneseForNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllEnglishLetters
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllFullWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.replaceJapaneseCharactersForEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toFullWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumberExponent
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSubscriptDigits
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSuperscriptDigits
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.toFullWidthDigitsEfficient
import timber.log.Timber
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

    private lateinit var systemSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var systemSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var systemSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var systemSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var singleKanjiYomiTrie: LOUDSWithTermId
    private lateinit var singleKanjiTangoTrie: LOUDS
    private lateinit var singleKanjiTokenArray: TokenArray

    private lateinit var singleKanjiSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var singleKanjiSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var singleKanjiSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var singleKanjiSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var emojiYomiTrie: LOUDSWithTermId
    private lateinit var emojiTangoTrie: LOUDS
    private lateinit var emojiTokenArray: TokenArray

    private lateinit var emojiSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var emojiSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var emojiSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var emojiSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var emoticonYomiTrie: LOUDSWithTermId
    private lateinit var emoticonTangoTrie: LOUDS
    private lateinit var emoticonTokenArray: TokenArray

    private lateinit var emoticonSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var emoticonSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var emoticonSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var emoticonSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var symbolYomiTrie: LOUDSWithTermId
    private lateinit var symbolTangoTrie: LOUDS
    private lateinit var symbolTokenArray: TokenArray

    private lateinit var symbolSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var symbolSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var symbolSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var symbolSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var readingCorrectionYomiTrie: LOUDSWithTermId
    private lateinit var readingCorrectionTangoTrie: LOUDS
    private lateinit var readingCorrectionTokenArray: TokenArray

    private lateinit var readingCorrectionSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var readingCorrectionSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var readingCorrectionSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var readingCorrectionSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var kotowazaYomiTrie: LOUDSWithTermId
    private lateinit var kotowazaTangoTrie: LOUDS
    private lateinit var kotowazaTokenArray: TokenArray

    private lateinit var kotowazaSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var kotowazaSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var kotowazaSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var kotowazaSuccinctBitVectorTangoLBS: SuccinctBitVector

    private var personYomiTrie: LOUDSWithTermId? = null
    private var personTangoTrie: LOUDS? = null
    private var personTokenArray: TokenArray? = null

    private var personSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var personSuccinctBitVectorIsLeaf: SuccinctBitVector? = null
    private var personSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var personSuccinctBitVectorLBSTango: SuccinctBitVector? = null

    private var placesYomiTrie: LOUDSWithTermId? = null
    private var placesTangoTrie: LOUDS? = null
    private var placesTokenArray: TokenArray? = null

    private var placesSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var placesSuccinctBitVectorIsLeaf: SuccinctBitVector? = null
    private var placesSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var placesSuccinctBitVectorLBSTango: SuccinctBitVector? = null

    private var wikiYomiTrie: LOUDSWithTermId? = null
    private var wikiTangoTrie: LOUDS? = null
    private var wikiTokenArray: TokenArray? = null

    private var wikiSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var wikiSuccinctBitVectorIsLeaf: SuccinctBitVector? = null
    private var wikiSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var wikiSuccinctBitVectorLBSTango: SuccinctBitVector? = null

    private var neologdYomiTrie: LOUDSWithTermId? = null
    private var neologdTangoTrie: LOUDS? = null
    private var neologdTokenArray: TokenArray? = null

    private var neologdSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var neologdSuccinctBitVectorIsLeaf: SuccinctBitVector? = null
    private var neologdSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var neologdSuccinctBitVectorLBSTango: SuccinctBitVector? = null

    private var webYomiTrie: LOUDSWithTermId? = null
    private var webTangoTrie: LOUDS? = null
    private var webTokenArray: TokenArray? = null

    private var webSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var webSuccinctBitVectorIsLeaf: SuccinctBitVector? = null
    private var webSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var webSuccinctBitVectorLBSTango: SuccinctBitVector? = null

    private lateinit var englishEngine: EnglishEngine

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
        systemSuccinctBitVectorLBSYomi: SuccinctBitVector,
        systemSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        systemSuccinctBitVectorTokenArray: SuccinctBitVector,
        systemSuccinctBitVectorTangoLBS: SuccinctBitVector,

        singleKanjiTangoTrie: LOUDS,
        singleKanjiYomiTrie: LOUDSWithTermId,
        singleKanjiTokenArray: TokenArray,
        singleKanjiSuccinctBitVectorLBSYomi: SuccinctBitVector,
        singleKanjiSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        singleKanjiSuccinctBitVectorTokenArray: SuccinctBitVector,
        singleKanjiSuccinctBitVectorTangoLBS: SuccinctBitVector,

        emojiTangoTrie: LOUDS,
        emojiYomiTrie: LOUDSWithTermId,
        emojiTokenArray: TokenArray,
        emojiSuccinctBitVectorLBSYomi: SuccinctBitVector,
        emojiSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        emojiSuccinctBitVectorTokenArray: SuccinctBitVector,
        emojiSuccinctBitVectorTangoLBS: SuccinctBitVector,

        emoticonTangoTrie: LOUDS,
        emoticonYomiTrie: LOUDSWithTermId,
        emoticonTokenArray: TokenArray,
        emoticonSuccinctBitVectorLBSYomi: SuccinctBitVector,
        emoticonSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        emoticonSuccinctBitVectorTokenArray: SuccinctBitVector,
        emoticonSuccinctBitVectorTangoLBS: SuccinctBitVector,

        symbolTangoTrie: LOUDS,
        symbolYomiTrie: LOUDSWithTermId,
        symbolTokenArray: TokenArray,
        symbolSuccinctBitVectorLBSYomi: SuccinctBitVector,
        symbolSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        symbolSuccinctBitVectorTokenArray: SuccinctBitVector,
        symbolSuccinctBitVectorTangoLBS: SuccinctBitVector,

        readingCorrectionTangoTrie: LOUDS,
        readingCorrectionYomiTrie: LOUDSWithTermId,
        readingCorrectionTokenArray: TokenArray,
        readingCorrectionSuccinctBitVectorLBSYomi: SuccinctBitVector,
        readingCorrectionSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        readingCorrectionSuccinctBitVectorTokenArray: SuccinctBitVector,
        readingCorrectionSuccinctBitVectorTangoLBS: SuccinctBitVector,

        kotowazaTangoTrie: LOUDS,
        kotowazaYomiTrie: LOUDSWithTermId,
        kotowazaTokenArray: TokenArray,
        kotowazaSuccinctBitVectorLBSYomi: SuccinctBitVector,
        kotowazaSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        kotowazaSuccinctBitVectorTokenArray: SuccinctBitVector,
        kotowazaSuccinctBitVectorTangoLBS: SuccinctBitVector,
        engineEngine: EnglishEngine
    ) {
        this@KanaKanjiEngine.graphBuilder = graphBuilder
        this@KanaKanjiEngine.findPath = findPath

        // System
        this@KanaKanjiEngine.connectionIds = connectionIdList
        this@KanaKanjiEngine.systemTangoTrie = systemTangoTrie
        this@KanaKanjiEngine.systemTokenArray = systemTokenArray
        this@KanaKanjiEngine.systemYomiTrie = systemYomiTrie
        this@KanaKanjiEngine.systemSuccinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.systemSuccinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.systemSuccinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.systemSuccinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS


        // Single Kanji
        this@KanaKanjiEngine.singleKanjiTangoTrie = singleKanjiTangoTrie
        this@KanaKanjiEngine.singleKanjiTokenArray = singleKanjiTokenArray
        this@KanaKanjiEngine.singleKanjiYomiTrie = singleKanjiYomiTrie
        this@KanaKanjiEngine.singleKanjiSuccinctBitVectorLBSYomi =
            singleKanjiSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.singleKanjiSuccinctBitVectorIsLeafYomi =
            singleKanjiSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.singleKanjiSuccinctBitVectorTokenArray =
            singleKanjiSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.singleKanjiSuccinctBitVectorTangoLBS =
            singleKanjiSuccinctBitVectorTangoLBS

        // Emoji
        this@KanaKanjiEngine.emojiTangoTrie = emojiTangoTrie
        this@KanaKanjiEngine.emojiTokenArray = emojiTokenArray
        this@KanaKanjiEngine.emojiYomiTrie = emojiYomiTrie
        this@KanaKanjiEngine.emojiSuccinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.emojiSuccinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.emojiSuccinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.emojiSuccinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS

        /** Emoticon **/
        this@KanaKanjiEngine.emoticonTangoTrie = emoticonTangoTrie
        this@KanaKanjiEngine.emoticonTokenArray = emoticonTokenArray
        this@KanaKanjiEngine.emoticonYomiTrie = emoticonYomiTrie
        this@KanaKanjiEngine.emoticonSuccinctBitVectorLBSYomi = emoticonSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.emoticonSuccinctBitVectorIsLeafYomi =
            emoticonSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.emoticonSuccinctBitVectorTokenArray =
            emoticonSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.emoticonSuccinctBitVectorTangoLBS = emoticonSuccinctBitVectorTangoLBS

        /** Symbol **/
        this@KanaKanjiEngine.symbolTangoTrie = symbolTangoTrie
        this@KanaKanjiEngine.symbolTokenArray = symbolTokenArray
        this@KanaKanjiEngine.symbolYomiTrie = symbolYomiTrie
        this@KanaKanjiEngine.symbolSuccinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.symbolSuccinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.symbolSuccinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.symbolSuccinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS

        /** Reading Correction **/
        this@KanaKanjiEngine.readingCorrectionTangoTrie = readingCorrectionTangoTrie
        this@KanaKanjiEngine.readingCorrectionTokenArray = readingCorrectionTokenArray
        this@KanaKanjiEngine.readingCorrectionYomiTrie = readingCorrectionYomiTrie
        this@KanaKanjiEngine.readingCorrectionSuccinctBitVectorLBSYomi =
            readingCorrectionSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.readingCorrectionSuccinctBitVectorIsLeafYomi =
            readingCorrectionSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.readingCorrectionSuccinctBitVectorTokenArray =
            readingCorrectionSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.readingCorrectionSuccinctBitVectorTangoLBS =
            readingCorrectionSuccinctBitVectorTangoLBS

        /**  Kotowaza **/
        this@KanaKanjiEngine.kotowazaTangoTrie = kotowazaTangoTrie
        this@KanaKanjiEngine.kotowazaTokenArray = kotowazaTokenArray
        this@KanaKanjiEngine.kotowazaYomiTrie = kotowazaYomiTrie
        this@KanaKanjiEngine.kotowazaSuccinctBitVectorLBSYomi = kotowazaSuccinctBitVectorLBSYomi
        this@KanaKanjiEngine.kotowazaSuccinctBitVectorIsLeafYomi =
            kotowazaSuccinctBitVectorIsLeafYomi
        this@KanaKanjiEngine.kotowazaSuccinctBitVectorTokenArray =
            kotowazaSuccinctBitVectorTokenArray
        this@KanaKanjiEngine.kotowazaSuccinctBitVectorTangoLBS = kotowazaSuccinctBitVectorTangoLBS

        this.englishEngine = engineEngine
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

        this.personSuccinctBitVectorLBSYomi = SuccinctBitVector(personYomiTrie!!.LBS)
        this.personSuccinctBitVectorIsLeaf = SuccinctBitVector(personYomiTrie!!.isLeaf)
        this.personSuccinctBitVectorTokenArray = SuccinctBitVector(personTokenArray!!.bitvector)
        this.personSuccinctBitVectorLBSTango = SuccinctBitVector(personTangoTrie!!.LBS)
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
        this.placesSuccinctBitVectorLBSYomi = SuccinctBitVector(placesYomiTrie!!.LBS)
        this.placesSuccinctBitVectorIsLeaf = SuccinctBitVector(placesYomiTrie!!.isLeaf)
        this.placesSuccinctBitVectorTokenArray = SuccinctBitVector(placesTokenArray!!.bitvector)
        this.placesSuccinctBitVectorLBSTango = SuccinctBitVector(placesTangoTrie!!.LBS)
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

        this.wikiSuccinctBitVectorLBSYomi = SuccinctBitVector(wikiYomiTrie!!.LBS)
        this.wikiSuccinctBitVectorIsLeaf = SuccinctBitVector(wikiYomiTrie!!.isLeaf)
        this.wikiSuccinctBitVectorTokenArray = SuccinctBitVector(wikiTokenArray!!.bitvector)
        this.wikiSuccinctBitVectorLBSTango = SuccinctBitVector(wikiTangoTrie!!.LBS)
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

        this.neologdSuccinctBitVectorLBSYomi = SuccinctBitVector(neologdYomiTrie!!.LBS)
        this.neologdSuccinctBitVectorIsLeaf = SuccinctBitVector(neologdYomiTrie!!.isLeaf)
        this.neologdSuccinctBitVectorTokenArray = SuccinctBitVector(neologdTokenArray!!.bitvector)
        this.neologdSuccinctBitVectorLBSTango = SuccinctBitVector(neologdTangoTrie!!.LBS)
    }

    fun buildWebDictionary(context: Context) {
        val zipInputStreamTango = ZipInputStream(context.assets.open("web/tango_web.dat.zip"))
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

        this.webSuccinctBitVectorLBSYomi = SuccinctBitVector(webYomiTrie!!.LBS)
        this.webSuccinctBitVectorIsLeaf = SuccinctBitVector(webYomiTrie!!.isLeaf)
        this.webSuccinctBitVectorTokenArray = SuccinctBitVector(webTokenArray!!.bitvector)
        this.webSuccinctBitVectorLBSTango = SuccinctBitVector(webTangoTrie!!.LBS)
    }

    fun releasePersonNamesDictionary() {
        this.personTangoTrie = null
        this.personYomiTrie = null
        this.personTokenArray = null
        this.personSuccinctBitVectorLBSYomi = null
        this.personSuccinctBitVectorIsLeaf = null
        this.personSuccinctBitVectorTokenArray = null
        this.personSuccinctBitVectorLBSTango = null
    }

    fun releasePlacesDictionary() {
        this.placesTangoTrie = null
        this.placesYomiTrie = null
        this.placesTokenArray = null
        this.placesSuccinctBitVectorLBSYomi = null
        this.placesSuccinctBitVectorIsLeaf = null
        this.placesSuccinctBitVectorTokenArray = null
        this.placesSuccinctBitVectorLBSTango = null
    }

    fun releaseWikiDictionary() {
        this.wikiTangoTrie = null
        this.wikiYomiTrie = null
        this.wikiTokenArray = null
        this.wikiSuccinctBitVectorLBSYomi = null
        this.wikiSuccinctBitVectorIsLeaf = null
        this.wikiSuccinctBitVectorTokenArray = null
        this.wikiSuccinctBitVectorLBSTango = null
    }

    fun releaseNeologdDictionary() {
        this.neologdTangoTrie = null
        this.neologdYomiTrie = null
        this.neologdTokenArray = null
        this.neologdSuccinctBitVectorLBSYomi = null
        this.neologdSuccinctBitVectorIsLeaf = null
        this.neologdSuccinctBitVectorTokenArray = null
        this.neologdSuccinctBitVectorLBSTango = null
    }

    fun releaseWebDictionary() {
        this.webTangoTrie = null
        this.webYomiTrie = null
        this.webTokenArray = null
        this.webSuccinctBitVectorLBSYomi = null
        this.webSuccinctBitVectorIsLeaf = null
        this.webSuccinctBitVectorTokenArray = null
        this.webSuccinctBitVectorLBSTango = null
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

    suspend fun getCandidatesOriginal(
        input: String,
        n: Int,
        mozcUtPersonName: Boolean?,
        mozcUTPlaces: Boolean?,
        mozcUTWiki: Boolean?,
        mozcUTNeologd: Boolean?,
        mozcUTWeb: Boolean?,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
        isOmissionSearchEnable: Boolean
    ): List<Candidate> {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            wikiYomiTrie = wikiYomiTrie,
            wikiTangoTrie = wikiTangoTrie,
            wikiTokenArray = wikiTokenArray,
            succinctBitVectorLBSWikiYomi = wikiSuccinctBitVectorLBSYomi,
            succinctBitVectorWikiTangoLBS = wikiSuccinctBitVectorLBSTango,
            succinctBitVectorWikiTokenArray = wikiSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafWikiYomi = wikiSuccinctBitVectorIsLeaf,
            webYomiTrie = webYomiTrie,
            webTangoTrie = webTangoTrie,
            webTokenArray = webTokenArray,
            succinctBitVectorLBSwebYomi = webSuccinctBitVectorLBSYomi,
            succinctBitVectorwebTangoLBS = webSuccinctBitVectorLBSTango,
            succinctBitVectorwebTokenArray = webSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafwebYomi = webSuccinctBitVectorIsLeaf,
            personYomiTrie = personYomiTrie,
            personTangoTrie = personTangoTrie,
            personTokenArray = personTokenArray,
            succinctBitVectorLBSpersonYomi = personSuccinctBitVectorLBSYomi,
            succinctBitVectorpersonTangoLBS = personSuccinctBitVectorLBSTango,
            succinctBitVectorpersonTokenArray = personSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafpersonYomi = personSuccinctBitVectorIsLeaf,
            neologdYomiTrie = neologdYomiTrie,
            neologdTangoTrie = neologdTangoTrie,
            neologdTokenArray = neologdTokenArray,
            succinctBitVectorLBSneologdYomi = neologdSuccinctBitVectorLBSYomi,
            succinctBitVectorneologdTangoLBS = neologdSuccinctBitVectorLBSTango,
            succinctBitVectorneologdTokenArray = neologdSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafneologdYomi = neologdSuccinctBitVectorIsLeaf,
            isOmissionSearchEnable = isOmissionSearchEnable
        )

        val resultNBestFinalDeferred: List<Candidate> = if (graph.isEmpty()) {
            listOf(
                Candidate(
                    string = input,
                    type = (1).toByte(),
                    length = input.length.toUByte(),
                    score = 4000
                )
            )
        } else {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            // 1. Generate full-width, time, and date candidates as before.
            val fullWidth = Candidate(
                string = input.toFullWidthDigitsEfficient(),
                type = 22,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val halfWidth = Candidate(
                string = input.convertFullWidthToHalfWidth(),
                type = 31,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val timeConversion = createCandidatesForTime(input)
            val dateConversion = createCandidatesForDateInDigit(input)

            // 2. Correctly generate number-to-Kanji/comma candidates.
            val numberValue = input.toLongOrNull() // Safely convert the digit string to a number.
            val numberCandidates = if (numberValue != null) {
                buildList {
                    // Full Kanji style (e.g., 百二十三)
                    add(
                        Candidate(
                            string = numberValue.toKanji(),
                            type = 17, // Using 17 for Kanji
                            score = 2000,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Comma-separated style (e.g., 1,234)
                    add(
                        Candidate(
                            string = input.addCommasToNumber(),
                            type = 19,
                            score = 8001,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Original number string itself (e.g., 123)
                    add(
                        Candidate(
                            string = input,
                            type = 18,
                            score = 8002,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Mixed Kanji style (e.g., 12万3456)
                    add(
                        Candidate(
                            string = numberValue.convertToKanjiNotation(),
                            type = 23, // Using a different type for this style
                            score = 7900, // Lower score for the mixed style
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                }
            } else {
                emptyList()
            }

            val superscriptCandidate = Candidate(
                string = input.toSuperscriptDigits(),
                type = 21,
                score = 8000,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val subscriptCandidate = Candidate(
                string = input.toSubscriptDigits(),
                type = 20,
                score = 8001,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val valueBasedCandidates = if (numberValue != null) {
                createValueBasedSymbolCandidates(numberValue, input.length.toUByte())
            } else {
                emptyList()
            }

            // 3. Combine and return all generated candidates.
            return resultNBestFinalDeferred + timeConversion + dateConversion + fullWidth + halfWidth + numberCandidates + superscriptCandidate + subscriptCandidate + valueBasedCandidates
        }

        if (input.containsDigit() && input.containsFullWidthNumber()) {
            val resultWithHankaku = addHalfWidthCandidates(resultNBestFinalDeferred)
            val finalList =
                resultWithHankaku.sortedBy { it.score }
            // 3. Combine and return all generated candidates.
            return finalList
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000),
            Candidate(input.toHankakuKatakana(), 31, input.length.toUByte(), 6000)
        )

        val emojiCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emojiYomiTrie,
            succinctBitVector = emojiSuccinctBitVectorLBSYomi
        )

        val emoticonCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emoticonYomiTrie,
            succinctBitVector = emoticonSuccinctBitVectorLBSYomi,
        )

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )

        val emojiListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emojiCommonPrefixDeferred,
            yomiTrie = emojiYomiTrie,
            tokenArray = emojiTokenArray,
            tangoTrie = emojiTangoTrie,
            succinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS,
            type = 11
        )

        val emoticonListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emoticonCommonPrefixDeferred,
            yomiTrie = emoticonYomiTrie,
            tokenArray = emoticonTokenArray,
            tangoTrie = emoticonTangoTrie,
            succinctBitVectorLBSYomi = emoticonSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emoticonSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emoticonSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emoticonSuccinctBitVectorTangoLBS,
            type = 12
        )

        val symbolListDeferred =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                ).filterNot { it.string.containsHentaigana() }
            } else {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                )
            }

        Timber.d("Candidate Symbols: ${symbolListDeferred.map { it.string }}")

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            succinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,
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
                succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                type = 21
            )

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        if (input.length == 1) return resultNBestFinalDeferred + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + emojiListDeferred + emoticonListDeferred + symbolListDeferred + symbolHalfWidthListDeferred + singleKanjiListDeferred

        val yomiPartOfDeferred = if (input.length > 16) {
            emptyList()
        } else {
            systemYomiTrie.commonPrefixSearch(
                str = input, succinctBitVector = systemSuccinctBitVectorLBSYomi
            ).asReversed()
        }

        val predictiveSearchDeferred = deferredPrediction(
            input = input,
            yomiTrie = systemYomiTrie,
            succinctBitVector = systemSuccinctBitVectorLBSYomi
        )

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            succinctBitVector = readingCorrectionSuccinctBitVectorLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            succinctBitVector = kotowazaSuccinctBitVectorLBSYomi
        )

        val predictiveSearchResult: List<Candidate> =
            predictiveSearchDeferred.filter { it.length != input.length }.flatMap { yomi ->
                val nodeIndex = systemYomiTrie.getNodeIndex(
                    yomi, succinctBitVector = systemSuccinctBitVectorLBSYomi
                )
                val termId = systemYomiTrie.getTermId(
                    nodeIndex, systemSuccinctBitVectorIsLeafYomi
                )

                // 2) build Candidates
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId, succinctBitVector = systemSuccinctBitVectorTokenArray
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
                                token.nodeId, systemSuccinctBitVectorTangoLBS
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

        val yomiPartListDeferred: List<Candidate> = yomiPartOfDeferred.flatMap { yomi ->
            val termId = systemYomiTrie.getTermId(
                systemYomiTrie.getNodeIndex(
                    yomi,
                    systemSuccinctBitVectorLBSYomi,
                ), systemSuccinctBitVectorIsLeafYomi
            )
            systemTokenArray.getListDictionaryByYomiTermId(
                termId, succinctBitVector = systemSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> systemTangoTrie.getLetter(
                            it.nodeId, systemSuccinctBitVectorTangoLBS
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

        val readingCorrectionListDeferred: List<Candidate> =
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi, readingCorrectionSuccinctBitVectorLBSYomi
                    ), readingCorrectionSuccinctBitVectorIsLeafYomi
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, readingCorrectionSuccinctBitVectorTokenArray
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId, readingCorrectionSuccinctBitVectorTangoLBS
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

        val kotowazaListDeferred: List<Candidate> = kotowazaCommonPrefixDeferred.flatMap { yomi ->
            val termId = kotowazaYomiTrie.getTermIdShortArray(
                kotowazaYomiTrie.getNodeIndex(
                    yomi, kotowazaSuccinctBitVectorLBSYomi
                ), kotowazaSuccinctBitVectorIsLeafYomi
            )
            kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, kotowazaSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> kotowazaTangoTrie.getLetterShortArray(
                            it.nodeId, kotowazaSuccinctBitVectorTangoLBS
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

        val listOfDictionaryToday: List<Candidate> = when (input) {
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

            "いま" -> {
                val now = Calendar.getInstance()
                createCandidatesForTime(now, input)
            }

            else -> emptyList()
        }

        val convertYearToEra: List<Candidate> = when {
            input.matches(Regex("""\d{1,4}ねん""")) -> {
                val year = input.removeSuffix("ねん").toIntOrNull()
                if (year != null) createCandidatesForEra(year, input) else emptyList()
            }

            else -> emptyList()
        }

        val numbersDeferred = generateNumberCandidates(input)

        val mozcUTPersonNames =
            if (mozcUtPersonName == true) getMozcUTPersonNames(input) else emptyList()
        val mozcUTPlacesList = if (mozcUTPlaces == true) getMozcUTPlace(input) else emptyList()
        val mozcUTWikiList = if (mozcUTWiki == true) getMozcUTWiki(input) else emptyList()
        val mozcUTNeologdList = if (mozcUTNeologd == true) getMozcUTNeologd(input) else emptyList()
        val mozcUTWebList = if (mozcUTWeb == true) getMozcUTWeb(input) else emptyList()

        val resultList =
            resultNBestFinalDeferred + readingCorrectionListDeferred + predictiveSearchResult + mozcUTPersonNames + mozcUTPlacesList + mozcUTWikiList + mozcUTNeologdList + mozcUTWebList + listOfDictionaryToday + numbersDeferred + convertYearToEra

        val resultListFinal =
            resultList.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string })

        return resultListFinal + kotowazaListDeferred + symbolHalfWidthListDeferred + (englishDeferred + englishZenkaku).sortedBy { it.score } + (emojiListDeferred + emoticonListDeferred).sortedBy { it.score } + symbolListDeferred + hirakanaAndKana + yomiPartListDeferred + singleKanjiListDeferred

    }

    suspend fun getCandidatesOriginalWithBunsetsu(
        input: String,
        n: Int,
        mozcUtPersonName: Boolean?,
        mozcUTPlaces: Boolean?,
        mozcUTWiki: Boolean?,
        mozcUTNeologd: Boolean?,
        mozcUTWeb: Boolean?,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
        isOmissionSearchEnable: Boolean
    ): Pair<List<Candidate>, List<Int>> {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            wikiYomiTrie = wikiYomiTrie,
            wikiTangoTrie = wikiTangoTrie,
            wikiTokenArray = wikiTokenArray,
            succinctBitVectorLBSWikiYomi = wikiSuccinctBitVectorLBSYomi,
            succinctBitVectorWikiTangoLBS = wikiSuccinctBitVectorLBSTango,
            succinctBitVectorWikiTokenArray = wikiSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafWikiYomi = wikiSuccinctBitVectorIsLeaf,
            webYomiTrie = webYomiTrie,
            webTangoTrie = webTangoTrie,
            webTokenArray = webTokenArray,
            succinctBitVectorLBSwebYomi = webSuccinctBitVectorLBSYomi,
            succinctBitVectorwebTangoLBS = webSuccinctBitVectorLBSTango,
            succinctBitVectorwebTokenArray = webSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafwebYomi = webSuccinctBitVectorIsLeaf,
            personYomiTrie = personYomiTrie,
            personTangoTrie = personTangoTrie,
            personTokenArray = personTokenArray,
            succinctBitVectorLBSpersonYomi = personSuccinctBitVectorLBSYomi,
            succinctBitVectorpersonTangoLBS = personSuccinctBitVectorLBSTango,
            succinctBitVectorpersonTokenArray = personSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafpersonYomi = personSuccinctBitVectorIsLeaf,
            neologdYomiTrie = neologdYomiTrie,
            neologdTangoTrie = neologdTangoTrie,
            neologdTokenArray = neologdTokenArray,
            succinctBitVectorLBSneologdYomi = neologdSuccinctBitVectorLBSYomi,
            succinctBitVectorneologdTangoLBS = neologdSuccinctBitVectorLBSTango,
            succinctBitVectorneologdTokenArray = neologdSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafneologdYomi = neologdSuccinctBitVectorIsLeaf,
            isOmissionSearchEnable = isOmissionSearchEnable
        )

        val resultNBestFinalDeferred: Pair<List<Candidate>, List<Int>> = if (graph.isEmpty()) {
            Pair(
                listOf(
                    Candidate(
                        string = input,
                        type = (1).toByte(),
                        length = input.length.toUByte(),
                        score = 4000
                    )
                ), emptyList()
            )
        } else {
            findPath.backwardAStarWithBunsetsu(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            // 1. Generate full-width, time, and date candidates as before.
            val fullWidth = Candidate(
                string = input.toFullWidthDigitsEfficient(),
                type = 22,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val halfWidth = Candidate(
                string = input.convertFullWidthToHalfWidth(),
                type = 31,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val timeConversion = createCandidatesForTime(input)
            val dateConversion = createCandidatesForDateInDigit(input)

            // 2. Correctly generate number-to-Kanji/comma candidates.
            val numberValue = input.toLongOrNull() // Safely convert the digit string to a number.
            val numberCandidates = if (numberValue != null) {
                buildList {
                    // Full Kanji style (e.g., 百二十三)
                    add(
                        Candidate(
                            string = numberValue.toKanji(),
                            type = 17, // Using 17 for Kanji
                            score = 2000,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Comma-separated style (e.g., 1,234)
                    add(
                        Candidate(
                            string = input.addCommasToNumber(),
                            type = 19,
                            score = 8001,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Original number string itself (e.g., 123)
                    add(
                        Candidate(
                            string = input,
                            type = 18,
                            score = 8002,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Mixed Kanji style (e.g., 12万3456)
                    add(
                        Candidate(
                            string = numberValue.convertToKanjiNotation(),
                            type = 23, // Using a different type for this style
                            score = 7900, // Lower score for the mixed style
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                }
            } else {
                emptyList()
            }

            val superscriptCandidate = Candidate(
                string = input.toSuperscriptDigits(),
                type = 21,
                score = 8000,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val subscriptCandidate = Candidate(
                string = input.toSubscriptDigits(),
                type = 20,
                score = 8001,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val valueBasedCandidates = if (numberValue != null) {
                createValueBasedSymbolCandidates(numberValue, input.length.toUByte())
            } else {
                emptyList()
            }

            val finalList =
                resultNBestFinalDeferred.first + timeConversion + dateConversion + fullWidth + halfWidth + numberCandidates + superscriptCandidate + subscriptCandidate + valueBasedCandidates
            return Pair(finalList, resultNBestFinalDeferred.second)
        }

        if (input.containsDigit() && input.containsFullWidthNumber()) {
            val resultWithHankaku = addHalfWidthCandidates(resultNBestFinalDeferred)

            val finalList =
                resultWithHankaku.first.sortedBy { it.score }

            // 3. Combine and return all generated candidates.
            return Pair(finalList, resultWithHankaku.second)
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000),
            Candidate(input.toHankakuKatakana(), 31, input.length.toUByte(), 6000)
        )

        val emojiCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emojiYomiTrie,
            succinctBitVector = emojiSuccinctBitVectorLBSYomi
        )

        val emoticonCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emoticonYomiTrie,
            succinctBitVector = emoticonSuccinctBitVectorLBSYomi,
        )

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )

        val emojiListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emojiCommonPrefixDeferred,
            yomiTrie = emojiYomiTrie,
            tokenArray = emojiTokenArray,
            tangoTrie = emojiTangoTrie,
            succinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS,
            type = 11
        )

        val emoticonListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emoticonCommonPrefixDeferred,
            yomiTrie = emoticonYomiTrie,
            tokenArray = emoticonTokenArray,
            tangoTrie = emoticonTangoTrie,
            succinctBitVectorLBSYomi = emoticonSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emoticonSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emoticonSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emoticonSuccinctBitVectorTangoLBS,
            type = 12
        )

        val symbolListDeferred =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                ).filterNot { it.string.containsHentaigana() }
            } else {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                )
            }

        Timber.d("Candidate Symbols: ${symbolListDeferred.map { it.string }}")

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            succinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,
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
                succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                type = 21
            )

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        if (input.length == 1) {
            val finalList =
                resultNBestFinalDeferred.first + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + emojiListDeferred + emoticonListDeferred + symbolListDeferred + symbolHalfWidthListDeferred + singleKanjiListDeferred
            return Pair(finalList, resultNBestFinalDeferred.second)
        }

        val yomiPartOfDeferred = if (input.length > 16) {
            emptyList()
        } else {
            systemYomiTrie.commonPrefixSearch(
                str = input, succinctBitVector = systemSuccinctBitVectorLBSYomi
            ).asReversed()
        }

        val predictiveSearchDeferred = deferredPrediction(
            input = input,
            yomiTrie = systemYomiTrie,
            succinctBitVector = systemSuccinctBitVectorLBSYomi
        )

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            succinctBitVector = readingCorrectionSuccinctBitVectorLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            succinctBitVector = kotowazaSuccinctBitVectorLBSYomi
        )

        val predictiveSearchResult: List<Candidate> =
            predictiveSearchDeferred.filter { it.length != input.length }.flatMap { yomi ->
                val nodeIndex = systemYomiTrie.getNodeIndex(
                    yomi, succinctBitVector = systemSuccinctBitVectorLBSYomi
                )
                val termId = systemYomiTrie.getTermId(
                    nodeIndex, systemSuccinctBitVectorIsLeafYomi
                )

                // 2) build Candidates
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId, succinctBitVector = systemSuccinctBitVectorTokenArray
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
                                token.nodeId, systemSuccinctBitVectorTangoLBS
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

        val yomiPartListDeferred: List<Candidate> = yomiPartOfDeferred.flatMap { yomi ->
            val termId = systemYomiTrie.getTermId(
                systemYomiTrie.getNodeIndex(
                    yomi,
                    systemSuccinctBitVectorLBSYomi,
                ), systemSuccinctBitVectorIsLeafYomi
            )
            systemTokenArray.getListDictionaryByYomiTermId(
                termId, succinctBitVector = systemSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> systemTangoTrie.getLetter(
                            it.nodeId, systemSuccinctBitVectorTangoLBS
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

        val readingCorrectionListDeferred: List<Candidate> =
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi, readingCorrectionSuccinctBitVectorLBSYomi
                    ), readingCorrectionSuccinctBitVectorIsLeafYomi
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, readingCorrectionSuccinctBitVectorTokenArray
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId, readingCorrectionSuccinctBitVectorTangoLBS
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

        val kotowazaListDeferred: List<Candidate> = kotowazaCommonPrefixDeferred.flatMap { yomi ->
            val termId = kotowazaYomiTrie.getTermIdShortArray(
                kotowazaYomiTrie.getNodeIndex(
                    yomi, kotowazaSuccinctBitVectorLBSYomi
                ), kotowazaSuccinctBitVectorIsLeafYomi
            )
            kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, kotowazaSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> kotowazaTangoTrie.getLetterShortArray(
                            it.nodeId, kotowazaSuccinctBitVectorTangoLBS
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

        val listOfDictionaryToday: List<Candidate> = when (input) {
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

            "いま" -> {
                val now = Calendar.getInstance()
                createCandidatesForTime(now, input)
            }

            else -> emptyList()
        }

        val convertYearToEra: List<Candidate> = when {
            input.matches(Regex("""\d{1,4}ねん""")) -> {
                val year = input.removeSuffix("ねん").toIntOrNull()
                if (year != null) createCandidatesForEra(year, input) else emptyList()
            }

            else -> emptyList()
        }

        val numbersDeferred = generateNumberCandidates(input)

        val mozcUTPersonNames =
            if (mozcUtPersonName == true) getMozcUTPersonNames(input) else emptyList()
        val mozcUTPlacesList = if (mozcUTPlaces == true) getMozcUTPlace(input) else emptyList()
        val mozcUTWikiList = if (mozcUTWiki == true) getMozcUTWiki(input) else emptyList()
        val mozcUTNeologdList = if (mozcUTNeologd == true) getMozcUTNeologd(input) else emptyList()
        val mozcUTWebList = if (mozcUTWeb == true) getMozcUTWeb(input) else emptyList()

        val resultList =
            resultNBestFinalDeferred.first + readingCorrectionListDeferred + predictiveSearchResult + mozcUTPersonNames + mozcUTPlacesList + mozcUTWikiList + mozcUTNeologdList + mozcUTWebList + listOfDictionaryToday + numbersDeferred + convertYearToEra

        val resultListFinal =
            resultList.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string }) + kotowazaListDeferred + symbolHalfWidthListDeferred +
                    (englishDeferred + englishZenkaku).sortedBy { it.score } + (emojiListDeferred + emoticonListDeferred).sortedBy { it.score } + symbolListDeferred + hirakanaAndKana + yomiPartListDeferred + singleKanjiListDeferred

        return Pair(resultListFinal, resultNBestFinalDeferred.second)

    }

    suspend fun getCandidatesWithBunsetsuSeparation(
        input: String,
        n: Int,
        mozcUtPersonName: Boolean?,
        mozcUTPlaces: Boolean?,
        mozcUTWiki: Boolean?,
        mozcUTNeologd: Boolean?,
        mozcUTWeb: Boolean?,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
        isOmissionSearchEnable: Boolean,
    ): Pair<List<Candidate>, List<Int>> {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            wikiYomiTrie = wikiYomiTrie,
            wikiTangoTrie = wikiTangoTrie,
            wikiTokenArray = wikiTokenArray,
            succinctBitVectorLBSWikiYomi = wikiSuccinctBitVectorLBSYomi,
            succinctBitVectorWikiTangoLBS = wikiSuccinctBitVectorLBSTango,
            succinctBitVectorWikiTokenArray = wikiSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafWikiYomi = wikiSuccinctBitVectorIsLeaf,
            webYomiTrie = webYomiTrie,
            webTangoTrie = webTangoTrie,
            webTokenArray = webTokenArray,
            succinctBitVectorLBSwebYomi = webSuccinctBitVectorLBSYomi,
            succinctBitVectorwebTangoLBS = webSuccinctBitVectorLBSTango,
            succinctBitVectorwebTokenArray = webSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafwebYomi = webSuccinctBitVectorIsLeaf,
            personYomiTrie = personYomiTrie,
            personTangoTrie = personTangoTrie,
            personTokenArray = personTokenArray,
            succinctBitVectorLBSpersonYomi = personSuccinctBitVectorLBSYomi,
            succinctBitVectorpersonTangoLBS = personSuccinctBitVectorLBSTango,
            succinctBitVectorpersonTokenArray = personSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafpersonYomi = personSuccinctBitVectorIsLeaf,
            neologdYomiTrie = neologdYomiTrie,
            neologdTangoTrie = neologdTangoTrie,
            neologdTokenArray = neologdTokenArray,
            succinctBitVectorLBSneologdYomi = neologdSuccinctBitVectorLBSYomi,
            succinctBitVectorneologdTangoLBS = neologdSuccinctBitVectorLBSTango,
            succinctBitVectorneologdTokenArray = neologdSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafneologdYomi = neologdSuccinctBitVectorIsLeaf,
            isOmissionSearchEnable = isOmissionSearchEnable
        )

        val resultNBestFinalDeferred: Pair<List<Candidate>, List<Int>> = if (graph.isEmpty()) {
            Pair(
                listOf(
                    Candidate(
                        string = input,
                        type = (1).toByte(),
                        length = input.length.toUByte(),
                        score = 4000
                    )
                ), emptyList()
            )
        } else {
            findPath.backwardAStarWithBunsetsu(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            // 1. Generate full-width, time, and date candidates as before.
            val fullWidth = Candidate(
                string = input.toFullWidthDigitsEfficient(),
                type = 22,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val halfWidth = Candidate(
                string = input.convertFullWidthToHalfWidth(),
                type = 31,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val timeConversion = createCandidatesForTime(input)
            val dateConversion = createCandidatesForDateInDigit(input)

            // 2. Correctly generate number-to-Kanji/comma candidates.
            val numberValue = input.toLongOrNull() // Safely convert the digit string to a number.
            val numberCandidates = if (numberValue != null) {
                buildList {
                    // Full Kanji style (e.g., 百二十三)
                    add(
                        Candidate(
                            string = numberValue.toKanji(),
                            type = 17, // Using 17 for Kanji
                            score = 2000,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Comma-separated style (e.g., 1,234)
                    add(
                        Candidate(
                            string = input.addCommasToNumber(),
                            type = 19,
                            score = 8001,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Original number string itself (e.g., 123)
                    add(
                        Candidate(
                            string = input,
                            type = 18,
                            score = 8002,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Mixed Kanji style (e.g., 12万3456)
                    add(
                        Candidate(
                            string = numberValue.convertToKanjiNotation(),
                            type = 23, // Using a different type for this style
                            score = 7900, // Lower score for the mixed style
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                }
            } else {
                emptyList()
            }

            val superscriptCandidate = Candidate(
                string = input.toSuperscriptDigits(),
                type = 21,
                score = 8000,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val subscriptCandidate = Candidate(
                string = input.toSubscriptDigits(),
                type = 20,
                score = 8001,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val valueBasedCandidates = if (numberValue != null) {
                createValueBasedSymbolCandidates(numberValue, input.length.toUByte())
            } else {
                emptyList()
            }

            val finalList =
                resultNBestFinalDeferred.first + timeConversion +
                        dateConversion + fullWidth + halfWidth +
                        numberCandidates + superscriptCandidate + subscriptCandidate + valueBasedCandidates

            // 3. Combine and return all generated candidates.
            return Pair(finalList, resultNBestFinalDeferred.second)
        }

        if (input.containsDigit() && input.containsFullWidthNumber()) {
            val resultWithHankaku = addHalfWidthCandidates(resultNBestFinalDeferred)
            val finalList =
                resultWithHankaku.first.sortedBy { it.score }

            return Pair(finalList, resultWithHankaku.second)
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000),
            Candidate(input.toHankakuKatakana(), 31, input.length.toUByte(), 6000)
        )

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            succinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,
            type = 7
        )

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )

        val symbolListDeferred =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                ).filterNot { it.string.containsHentaigana() }
            } else {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                )
            }

        if (input.length == 1) {
            val finalListOneLetter =
                resultNBestFinalDeferred.first + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + symbolListDeferred + singleKanjiListDeferred
            return Pair(finalListOneLetter, listOf())
        }

        val yomiPartOfDeferred = if (input.length > 16) {
            emptyList()
        } else {
            systemYomiTrie.commonPrefixSearch(
                str = input, succinctBitVector = systemSuccinctBitVectorLBSYomi
            ).asReversed()
        }

        val predictiveSearchDeferred = deferredPrediction(
            input = input,
            yomiTrie = systemYomiTrie,
            succinctBitVector = systemSuccinctBitVectorLBSYomi
        )

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            succinctBitVector = readingCorrectionSuccinctBitVectorLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            succinctBitVector = kotowazaSuccinctBitVectorLBSYomi
        )

        val predictiveSearchResult: List<Candidate> =
            predictiveSearchDeferred.filter { it.length != input.length }.flatMap { yomi ->
                val nodeIndex = systemYomiTrie.getNodeIndex(
                    yomi, succinctBitVector = systemSuccinctBitVectorLBSYomi
                )
                val termId = systemYomiTrie.getTermId(
                    nodeIndex, systemSuccinctBitVectorIsLeafYomi
                )

                // 2) build Candidates
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId, succinctBitVector = systemSuccinctBitVectorTokenArray
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
                                token.nodeId, systemSuccinctBitVectorTangoLBS
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

        val yomiPartListDeferred: List<Candidate> = yomiPartOfDeferred.flatMap { yomi ->
            val termId = systemYomiTrie.getTermId(
                systemYomiTrie.getNodeIndex(
                    yomi,
                    systemSuccinctBitVectorLBSYomi,
                ), systemSuccinctBitVectorIsLeafYomi
            )
            systemTokenArray.getListDictionaryByYomiTermId(
                termId, succinctBitVector = systemSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> systemTangoTrie.getLetter(
                            it.nodeId, systemSuccinctBitVectorTangoLBS
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

        val readingCorrectionListDeferred: List<Candidate> =
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi, readingCorrectionSuccinctBitVectorLBSYomi
                    ), readingCorrectionSuccinctBitVectorIsLeafYomi
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, readingCorrectionSuccinctBitVectorTokenArray
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId, readingCorrectionSuccinctBitVectorTangoLBS
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

        val kotowazaListDeferred: List<Candidate> = kotowazaCommonPrefixDeferred.flatMap { yomi ->
            val termId = kotowazaYomiTrie.getTermIdShortArray(
                kotowazaYomiTrie.getNodeIndex(
                    yomi, kotowazaSuccinctBitVectorLBSYomi
                ), kotowazaSuccinctBitVectorIsLeafYomi
            )
            kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, kotowazaSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> kotowazaTangoTrie.getLetterShortArray(
                            it.nodeId, kotowazaSuccinctBitVectorTangoLBS
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

        val listOfDictionaryToday: List<Candidate> = when (input) {
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

            "いま" -> {
                val now = Calendar.getInstance()
                createCandidatesForTime(now, input)
            }

            else -> emptyList()
        }

        val convertYearToEra: List<Candidate> = when {
            input.matches(Regex("""\d{1,4}ねん""")) -> {
                val year = input.removeSuffix("ねん").toIntOrNull()
                if (year != null) createCandidatesForEra(year, input) else emptyList()
            }

            else -> emptyList()
        }

        val numbersDeferred = generateNumberCandidates(input)

        val mozcUTPersonNames =
            if (mozcUtPersonName == true) getMozcUTPersonNames(input) else emptyList()
        val mozcUTPlacesList = if (mozcUTPlaces == true) getMozcUTPlace(input) else emptyList()
        val mozcUTWikiList = if (mozcUTWiki == true) getMozcUTWiki(input) else emptyList()
        val mozcUTNeologdList = if (mozcUTNeologd == true) getMozcUTNeologd(input) else emptyList()
        val mozcUTWebList = if (mozcUTWeb == true) getMozcUTWeb(input) else emptyList()

        val resultList =
            resultNBestFinalDeferred.first + readingCorrectionListDeferred + predictiveSearchResult + mozcUTPersonNames + mozcUTPlacesList + mozcUTWikiList + mozcUTNeologdList + mozcUTWebList + listOfDictionaryToday + numbersDeferred + convertYearToEra

        val resultListFinal =
            resultList.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string })

        val finalList =
            resultListFinal + kotowazaListDeferred + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + yomiPartListDeferred + symbolListDeferred + singleKanjiListDeferred

        return Pair(finalList, resultNBestFinalDeferred.second)

    }

    suspend fun getCandidates(
        input: String,
        n: Int,
        mozcUtPersonName: Boolean?,
        mozcUTPlaces: Boolean?,
        mozcUTWiki: Boolean?,
        mozcUTNeologd: Boolean?,
        mozcUTWeb: Boolean?,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
        isOmissionSearchEnable: Boolean
    ): List<Candidate> {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            wikiYomiTrie = wikiYomiTrie,
            wikiTangoTrie = wikiTangoTrie,
            wikiTokenArray = wikiTokenArray,
            succinctBitVectorLBSWikiYomi = wikiSuccinctBitVectorLBSYomi,
            succinctBitVectorWikiTangoLBS = wikiSuccinctBitVectorLBSTango,
            succinctBitVectorWikiTokenArray = wikiSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafWikiYomi = wikiSuccinctBitVectorIsLeaf,
            webYomiTrie = webYomiTrie,
            webTangoTrie = webTangoTrie,
            webTokenArray = webTokenArray,
            succinctBitVectorLBSwebYomi = webSuccinctBitVectorLBSYomi,
            succinctBitVectorwebTangoLBS = webSuccinctBitVectorLBSTango,
            succinctBitVectorwebTokenArray = webSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafwebYomi = webSuccinctBitVectorIsLeaf,
            personYomiTrie = personYomiTrie,
            personTangoTrie = personTangoTrie,
            personTokenArray = personTokenArray,
            succinctBitVectorLBSpersonYomi = personSuccinctBitVectorLBSYomi,
            succinctBitVectorpersonTangoLBS = personSuccinctBitVectorLBSTango,
            succinctBitVectorpersonTokenArray = personSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafpersonYomi = personSuccinctBitVectorIsLeaf,
            neologdYomiTrie = neologdYomiTrie,
            neologdTangoTrie = neologdTangoTrie,
            neologdTokenArray = neologdTokenArray,
            succinctBitVectorLBSneologdYomi = neologdSuccinctBitVectorLBSYomi,
            succinctBitVectorneologdTangoLBS = neologdSuccinctBitVectorLBSTango,
            succinctBitVectorneologdTokenArray = neologdSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafneologdYomi = neologdSuccinctBitVectorIsLeaf,
            isOmissionSearchEnable = isOmissionSearchEnable
        )

        val resultNBestFinalDeferred: List<Candidate> = if (graph.isEmpty()) {
            listOf(
                Candidate(
                    string = input,
                    type = (1).toByte(),
                    length = input.length.toUByte(),
                    score = 4000
                )
            )
        } else {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            // 1. Generate full-width, time, and date candidates as before.
            val fullWidth = Candidate(
                string = input.toFullWidthDigitsEfficient(),
                type = 22,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val halfWidth = Candidate(
                string = input.convertFullWidthToHalfWidth(),
                type = 31,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val timeConversion = createCandidatesForTime(input)
            val dateConversion = createCandidatesForDateInDigit(input)

            // 2. Correctly generate number-to-Kanji/comma candidates.
            val numberValue = input.toLongOrNull() // Safely convert the digit string to a number.
            val numberCandidates = if (numberValue != null) {
                buildList {
                    // Full Kanji style (e.g., 百二十三)
                    add(
                        Candidate(
                            string = numberValue.toKanji(),
                            type = 17, // Using 17 for Kanji
                            score = 2000,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Comma-separated style (e.g., 1,234)
                    add(
                        Candidate(
                            string = input.addCommasToNumber(),
                            type = 19,
                            score = 8001,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Original number string itself (e.g., 123)
                    add(
                        Candidate(
                            string = input,
                            type = 18,
                            score = 8002,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Mixed Kanji style (e.g., 12万3456)
                    add(
                        Candidate(
                            string = numberValue.convertToKanjiNotation(),
                            type = 23, // Using a different type for this style
                            score = 7900, // Lower score for the mixed style
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                }
            } else {
                emptyList()
            }

            val superscriptCandidate = Candidate(
                string = input.toSuperscriptDigits(),
                type = 21,
                score = 8000,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val subscriptCandidate = Candidate(
                string = input.toSubscriptDigits(),
                type = 20,
                score = 8001,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val valueBasedCandidates = if (numberValue != null) {
                createValueBasedSymbolCandidates(numberValue, input.length.toUByte())
            } else {
                emptyList()
            }

            // 3. Combine and return all generated candidates.
            return resultNBestFinalDeferred + timeConversion + dateConversion + fullWidth + halfWidth + numberCandidates + superscriptCandidate + subscriptCandidate + valueBasedCandidates
        }

        if (input.containsDigit() && input.containsFullWidthNumber()) {
            val resultWithHankaku = addHalfWidthCandidates(resultNBestFinalDeferred)

            val finalList =
                resultWithHankaku.sortedBy { it.score }

            // 3. Combine and return all generated candidates.
            return finalList
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000),
            Candidate(input.toHankakuKatakana(), 31, input.length.toUByte(), 6000)
        )

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            succinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,
            type = 7
        )

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = input,
                    type = (1).toByte(),
                    length = input.length.toUByte(),
                    score = 4000
                ), Candidate(
                    string = input.replaceFirstChar { it.uppercaseChar() },
                    type = (1).toByte(),
                    length = input.length.toUByte(),
                    score = 4001
                ), Candidate(
                    string = input.uppercase(),
                    type = (1).toByte(),
                    length = input.length.toUByte(),
                    score = 4002
                ), Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )

        val symbolListDeferred =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                ).filterNot { it.string.containsHentaigana() }
            } else {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                )
            }

        if (input.length == 1) return resultNBestFinalDeferred + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + symbolListDeferred + singleKanjiListDeferred

        val yomiPartOfDeferred = if (input.length > 16) {
            emptyList()
        } else {
            systemYomiTrie.commonPrefixSearch(
                str = input, succinctBitVector = systemSuccinctBitVectorLBSYomi
            ).asReversed()
        }

        val predictiveSearchDeferred = deferredPrediction(
            input = input,
            yomiTrie = systemYomiTrie,
            succinctBitVector = systemSuccinctBitVectorLBSYomi
        )

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            succinctBitVector = readingCorrectionSuccinctBitVectorLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            succinctBitVector = kotowazaSuccinctBitVectorLBSYomi
        )

        val predictiveSearchResult: List<Candidate> =
            predictiveSearchDeferred.filter { it.length != input.length }.flatMap { yomi ->
                val nodeIndex = systemYomiTrie.getNodeIndex(
                    yomi, succinctBitVector = systemSuccinctBitVectorLBSYomi
                )
                val termId = systemYomiTrie.getTermId(
                    nodeIndex, systemSuccinctBitVectorIsLeafYomi
                )

                // 2) build Candidates
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId, succinctBitVector = systemSuccinctBitVectorTokenArray
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
                                token.nodeId, systemSuccinctBitVectorTangoLBS
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

        val yomiPartListDeferred: List<Candidate> = yomiPartOfDeferred.flatMap { yomi ->
            val termId = systemYomiTrie.getTermId(
                systemYomiTrie.getNodeIndex(
                    yomi,
                    systemSuccinctBitVectorLBSYomi,
                ), systemSuccinctBitVectorIsLeafYomi
            )
            systemTokenArray.getListDictionaryByYomiTermId(
                termId, succinctBitVector = systemSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> systemTangoTrie.getLetter(
                            it.nodeId, systemSuccinctBitVectorTangoLBS
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

        val readingCorrectionListDeferred: List<Candidate> =
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi, readingCorrectionSuccinctBitVectorLBSYomi
                    ), readingCorrectionSuccinctBitVectorIsLeafYomi
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, readingCorrectionSuccinctBitVectorTokenArray
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId, readingCorrectionSuccinctBitVectorTangoLBS
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

        val kotowazaListDeferred: List<Candidate> = kotowazaCommonPrefixDeferred.flatMap { yomi ->
            val termId = kotowazaYomiTrie.getTermIdShortArray(
                kotowazaYomiTrie.getNodeIndex(
                    yomi, kotowazaSuccinctBitVectorLBSYomi
                ), kotowazaSuccinctBitVectorIsLeafYomi
            )
            kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, kotowazaSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> kotowazaTangoTrie.getLetterShortArray(
                            it.nodeId, kotowazaSuccinctBitVectorTangoLBS
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

        val listOfDictionaryToday: List<Candidate> = when (input) {
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

            "いま" -> {
                val now = Calendar.getInstance()
                createCandidatesForTime(now, input)
            }

            else -> emptyList()
        }

        val convertYearToEra: List<Candidate> = when {
            input.matches(Regex("""\d{1,4}ねん""")) -> {
                val year = input.removeSuffix("ねん").toIntOrNull()
                if (year != null) createCandidatesForEra(year, input) else emptyList()
            }

            else -> emptyList()
        }

        val numbersDeferred = generateNumberCandidates(input)

        val mozcUTPersonNames =
            if (mozcUtPersonName == true) getMozcUTPersonNames(input) else emptyList()
        val mozcUTPlacesList = if (mozcUTPlaces == true) getMozcUTPlace(input) else emptyList()
        val mozcUTWikiList = if (mozcUTWiki == true) getMozcUTWiki(input) else emptyList()
        val mozcUTNeologdList = if (mozcUTNeologd == true) getMozcUTNeologd(input) else emptyList()
        val mozcUTWebList = if (mozcUTWeb == true) getMozcUTWeb(input) else emptyList()

        val resultList =
            resultNBestFinalDeferred + readingCorrectionListDeferred + predictiveSearchResult + mozcUTPersonNames + mozcUTPlacesList + mozcUTWikiList + mozcUTNeologdList + mozcUTWebList + listOfDictionaryToday + numbersDeferred + convertYearToEra

        val resultListFinal =
            resultList.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string })

        return resultListFinal + kotowazaListDeferred + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + yomiPartListDeferred + symbolListDeferred + singleKanjiListDeferred

    }

    suspend fun getCandidatesWithoutPrediction(
        input: String,
        n: Int,
        mozcUtPersonName: Boolean?,
        mozcUTPlaces: Boolean?,
        mozcUTWiki: Boolean?,
        mozcUTNeologd: Boolean?,
        mozcUTWeb: Boolean?,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
    ): List<Candidate> {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            wikiYomiTrie = wikiYomiTrie,
            wikiTangoTrie = wikiTangoTrie,
            wikiTokenArray = wikiTokenArray,
            succinctBitVectorLBSWikiYomi = wikiSuccinctBitVectorLBSYomi,
            succinctBitVectorWikiTangoLBS = wikiSuccinctBitVectorLBSTango,
            succinctBitVectorWikiTokenArray = wikiSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafWikiYomi = wikiSuccinctBitVectorIsLeaf,
            webYomiTrie = webYomiTrie,
            webTangoTrie = webTangoTrie,
            webTokenArray = webTokenArray,
            succinctBitVectorLBSwebYomi = webSuccinctBitVectorLBSYomi,
            succinctBitVectorwebTangoLBS = webSuccinctBitVectorLBSTango,
            succinctBitVectorwebTokenArray = webSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafwebYomi = webSuccinctBitVectorIsLeaf,
            personYomiTrie = personYomiTrie,
            personTangoTrie = personTangoTrie,
            personTokenArray = personTokenArray,
            succinctBitVectorLBSpersonYomi = personSuccinctBitVectorLBSYomi,
            succinctBitVectorpersonTangoLBS = personSuccinctBitVectorLBSTango,
            succinctBitVectorpersonTokenArray = personSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafpersonYomi = personSuccinctBitVectorIsLeaf,
            neologdYomiTrie = neologdYomiTrie,
            neologdTangoTrie = neologdTangoTrie,
            neologdTokenArray = neologdTokenArray,
            succinctBitVectorLBSneologdYomi = neologdSuccinctBitVectorLBSYomi,
            succinctBitVectorneologdTangoLBS = neologdSuccinctBitVectorLBSTango,
            succinctBitVectorneologdTokenArray = neologdSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafneologdYomi = neologdSuccinctBitVectorIsLeaf,
            isOmissionSearchEnable = false
        )

        val resultNBestFinalDeferred: List<Candidate> = if (graph.isEmpty()) {
            listOf(
                Candidate(
                    string = input,
                    type = (1).toByte(),
                    length = input.length.toUByte(),
                    score = 4000
                )
            )
        } else {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            // 1. Generate full-width, time, and date candidates as before.
            val fullWidth = Candidate(
                string = input.toFullWidthDigitsEfficient(),
                type = 22,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val halfWidth = Candidate(
                string = input.convertFullWidthToHalfWidth(),
                type = 31,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val timeConversion = createCandidatesForTime(input)
            val dateConversion = createCandidatesForDateInDigit(input)

            // 2. Correctly generate number-to-Kanji/comma candidates.
            val numberValue = input.toLongOrNull() // Safely convert the digit string to a number.
            val numberCandidates = if (numberValue != null) {
                buildList {
                    // Full Kanji style (e.g., 百二十三)
                    add(
                        Candidate(
                            string = numberValue.toKanji(),
                            type = 17, // Using 17 for Kanji
                            score = 2000,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Comma-separated style (e.g., 1,234)
                    add(
                        Candidate(
                            string = input.addCommasToNumber(),
                            type = 19,
                            score = 8001,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Original number string itself (e.g., 123)
                    add(
                        Candidate(
                            string = input,
                            type = 18,
                            score = 8002,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Mixed Kanji style (e.g., 12万3456)
                    add(
                        Candidate(
                            string = numberValue.convertToKanjiNotation(),
                            type = 23, // Using a different type for this style
                            score = 7900, // Lower score for the mixed style
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                }
            } else {
                emptyList()
            }

            val superscriptCandidate = Candidate(
                string = input.toSuperscriptDigits(),
                type = 21,
                score = 8000,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val subscriptCandidate = Candidate(
                string = input.toSubscriptDigits(),
                type = 20,
                score = 8001,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val valueBasedCandidates = if (numberValue != null) {
                createValueBasedSymbolCandidates(numberValue, input.length.toUByte())
            } else {
                emptyList()
            }

            // 3. Combine and return all generated candidates.
            return resultNBestFinalDeferred + timeConversion + dateConversion + fullWidth + halfWidth + numberCandidates + superscriptCandidate + subscriptCandidate + valueBasedCandidates
        }

        if (input.containsDigit() && input.containsFullWidthNumber()) {
            val resultWithHankaku = addHalfWidthCandidates(resultNBestFinalDeferred)
            val finalList =
                resultWithHankaku.sortedBy { it.score }
            return finalList
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000),
            Candidate(input.toHankakuKatakana(), 31, input.length.toUByte(), 6000)
        )

        val emojiCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emojiYomiTrie,
            succinctBitVector = emojiSuccinctBitVectorLBSYomi
        )

        val emoticonCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emoticonYomiTrie,
            succinctBitVector = emoticonSuccinctBitVectorLBSYomi,
        )

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )

        val emojiListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emojiCommonPrefixDeferred,
            yomiTrie = emojiYomiTrie,
            tokenArray = emojiTokenArray,
            tangoTrie = emojiTangoTrie,
            succinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS,
            type = 11
        )

        val emoticonListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emoticonCommonPrefixDeferred,
            yomiTrie = emoticonYomiTrie,
            tokenArray = emoticonTokenArray,
            tangoTrie = emoticonTangoTrie,
            succinctBitVectorLBSYomi = emoticonSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emoticonSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emoticonSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emoticonSuccinctBitVectorTangoLBS,
            type = 12
        )

        val symbolListDeferred =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                ).filterNot { it.string.containsHentaigana() }
            } else {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                )
            }

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            succinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,
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
                succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                type = 21
            )

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        if (input.length == 1) return resultNBestFinalDeferred + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + emojiListDeferred + emoticonListDeferred + symbolListDeferred + symbolHalfWidthListDeferred + singleKanjiListDeferred

        val yomiPartOfDeferred = if (input.length > 16) {
            emptyList()
        } else {
            systemYomiTrie.commonPrefixSearch(
                str = input, succinctBitVector = systemSuccinctBitVectorLBSYomi
            ).asReversed()
        }

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            succinctBitVector = readingCorrectionSuccinctBitVectorLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            succinctBitVector = kotowazaSuccinctBitVectorLBSYomi
        )

        val yomiPartListDeferred: List<Candidate> = yomiPartOfDeferred.flatMap { yomi ->
            val termId = systemYomiTrie.getTermId(
                systemYomiTrie.getNodeIndex(
                    yomi,
                    systemSuccinctBitVectorLBSYomi,
                ), systemSuccinctBitVectorIsLeafYomi
            )
            systemTokenArray.getListDictionaryByYomiTermId(
                termId, succinctBitVector = systemSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> systemTangoTrie.getLetter(
                            it.nodeId, systemSuccinctBitVectorTangoLBS
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

        val readingCorrectionListDeferred: List<Candidate> =
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi, readingCorrectionSuccinctBitVectorLBSYomi
                    ), readingCorrectionSuccinctBitVectorIsLeafYomi
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, readingCorrectionSuccinctBitVectorTokenArray
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId, readingCorrectionSuccinctBitVectorTangoLBS
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

        val kotowazaListDeferred: List<Candidate> = kotowazaCommonPrefixDeferred.flatMap { yomi ->
            val termId = kotowazaYomiTrie.getTermIdShortArray(
                kotowazaYomiTrie.getNodeIndex(
                    yomi, kotowazaSuccinctBitVectorLBSYomi
                ), kotowazaSuccinctBitVectorIsLeafYomi
            )
            kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, kotowazaSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> kotowazaTangoTrie.getLetterShortArray(
                            it.nodeId, kotowazaSuccinctBitVectorTangoLBS
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

        val listOfDictionaryToday: List<Candidate> = when (input) {
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

            "いま" -> {
                val now = Calendar.getInstance()
                createCandidatesForTime(now, input)
            }

            else -> emptyList()
        }

        val convertYearToEra: List<Candidate> = when {
            input.matches(Regex("""\d{1,4}ねん""")) -> {
                val year = input.removeSuffix("ねん").toIntOrNull()
                if (year != null) createCandidatesForEra(year, input) else emptyList()
            }

            else -> emptyList()
        }

        val numbersDeferred = generateNumberCandidates(input)

        val mozcUTPersonNames =
            if (mozcUtPersonName == true) getMozcUTPersonNames(input) else emptyList()
        val mozcUTPlacesList = if (mozcUTPlaces == true) getMozcUTPlace(input) else emptyList()
        val mozcUTWikiList = if (mozcUTWiki == true) getMozcUTWiki(input) else emptyList()
        val mozcUTNeologdList = if (mozcUTNeologd == true) getMozcUTNeologd(input) else emptyList()
        val mozcUTWebList = if (mozcUTWeb == true) getMozcUTWeb(input) else emptyList()

        val resultList =
            resultNBestFinalDeferred + readingCorrectionListDeferred + mozcUTPersonNames + mozcUTPlacesList + mozcUTWikiList + mozcUTNeologdList + mozcUTWebList + listOfDictionaryToday + numbersDeferred + convertYearToEra

        val resultListFinal =
            resultList.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string })

        return resultListFinal + (englishDeferred + englishZenkaku).sortedBy { it.score } + symbolHalfWidthListDeferred + (emojiListDeferred + emoticonListDeferred).sortedBy { it.score } + symbolListDeferred + kotowazaListDeferred + hirakanaAndKana + yomiPartListDeferred + singleKanjiListDeferred

    }

    suspend fun getCandidatesWithoutPredictionWithBunsetsu(
        input: String,
        n: Int,
        mozcUtPersonName: Boolean?,
        mozcUTPlaces: Boolean?,
        mozcUTWiki: Boolean?,
        mozcUTNeologd: Boolean?,
        mozcUTWeb: Boolean?,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
    ): Pair<List<Candidate>, List<Int>> {

        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            wikiYomiTrie = wikiYomiTrie,
            wikiTangoTrie = wikiTangoTrie,
            wikiTokenArray = wikiTokenArray,
            succinctBitVectorLBSWikiYomi = wikiSuccinctBitVectorLBSYomi,
            succinctBitVectorWikiTangoLBS = wikiSuccinctBitVectorLBSTango,
            succinctBitVectorWikiTokenArray = wikiSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafWikiYomi = wikiSuccinctBitVectorIsLeaf,
            webYomiTrie = webYomiTrie,
            webTangoTrie = webTangoTrie,
            webTokenArray = webTokenArray,
            succinctBitVectorLBSwebYomi = webSuccinctBitVectorLBSYomi,
            succinctBitVectorwebTangoLBS = webSuccinctBitVectorLBSTango,
            succinctBitVectorwebTokenArray = webSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafwebYomi = webSuccinctBitVectorIsLeaf,
            personYomiTrie = personYomiTrie,
            personTangoTrie = personTangoTrie,
            personTokenArray = personTokenArray,
            succinctBitVectorLBSpersonYomi = personSuccinctBitVectorLBSYomi,
            succinctBitVectorpersonTangoLBS = personSuccinctBitVectorLBSTango,
            succinctBitVectorpersonTokenArray = personSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafpersonYomi = personSuccinctBitVectorIsLeaf,
            neologdYomiTrie = neologdYomiTrie,
            neologdTangoTrie = neologdTangoTrie,
            neologdTokenArray = neologdTokenArray,
            succinctBitVectorLBSneologdYomi = neologdSuccinctBitVectorLBSYomi,
            succinctBitVectorneologdTangoLBS = neologdSuccinctBitVectorLBSTango,
            succinctBitVectorneologdTokenArray = neologdSuccinctBitVectorTokenArray,
            succinctBitVectorIsLeafneologdYomi = neologdSuccinctBitVectorIsLeaf,
            isOmissionSearchEnable = false
        )

        val resultNBestFinalDeferred: Pair<List<Candidate>, List<Int>> = if (graph.isEmpty()) {
            Pair(
                listOf(
                    Candidate(
                        string = input,
                        type = (1).toByte(),
                        length = input.length.toUByte(),
                        score = 4000
                    )
                ), emptyList()
            )
        } else {
            findPath.backwardAStarWithBunsetsu(graph, input.length, connectionIds, n)
        }

        if (input.isDigitsOnly()) {
            // 1. Generate full-width, time, and date candidates as before.
            val fullWidth = Candidate(
                string = input.toFullWidthDigitsEfficient(),
                type = 22,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val halfWidth = Candidate(
                string = input.convertFullWidthToHalfWidth(),
                type = 31,
                length = input.length.toUByte(),
                score = 8000,
                leftId = 2040,
                rightId = 2040
            )
            val timeConversion = createCandidatesForTime(input)
            val dateConversion = createCandidatesForDateInDigit(input)

            // 2. Correctly generate number-to-Kanji/comma candidates.
            val numberValue = input.toLongOrNull() // Safely convert the digit string to a number.
            val numberCandidates = if (numberValue != null) {
                buildList {
                    // Full Kanji style (e.g., 百二十三)
                    add(
                        Candidate(
                            string = numberValue.toKanji(),
                            type = 17, // Using 17 for Kanji
                            score = 2000,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Comma-separated style (e.g., 1,234)
                    add(
                        Candidate(
                            string = input.addCommasToNumber(),
                            type = 19,
                            score = 8001,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Original number string itself (e.g., 123)
                    add(
                        Candidate(
                            string = input,
                            type = 18,
                            score = 8002,
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                    // Mixed Kanji style (e.g., 12万3456)
                    add(
                        Candidate(
                            string = numberValue.convertToKanjiNotation(),
                            type = 23, // Using a different type for this style
                            score = 7900, // Lower score for the mixed style
                            length = input.length.toUByte(),
                            leftId = 2040,
                            rightId = 2040
                        )
                    )
                }
            } else {
                emptyList()
            }

            val superscriptCandidate = Candidate(
                string = input.toSuperscriptDigits(),
                type = 21,
                score = 8000,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val subscriptCandidate = Candidate(
                string = input.toSubscriptDigits(),
                type = 20,
                score = 8001,
                length = input.length.toUByte(),
                leftId = 2040,
                rightId = 2040
            )

            val valueBasedCandidates = if (numberValue != null) {
                createValueBasedSymbolCandidates(numberValue, input.length.toUByte())
            } else {
                emptyList()
            }

            val finalList =
                resultNBestFinalDeferred.first + timeConversion + dateConversion + fullWidth + halfWidth + numberCandidates + superscriptCandidate + subscriptCandidate + valueBasedCandidates
            return Pair(finalList, resultNBestFinalDeferred.second)
        }

        if (input.containsDigit() && input.containsFullWidthNumber()) {
            val resultWithHankaku = addHalfWidthCandidates(resultNBestFinalDeferred)

            val finalList =
                resultWithHankaku.first.sortedBy { it.score }

            // 3. Combine and return all generated candidates.
            return Pair(finalList, resultWithHankaku.second)
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000),
            Candidate(input.toHankakuKatakana(), 31, input.length.toUByte(), 6000)
        )

        val emojiCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emojiYomiTrie,
            succinctBitVector = emojiSuccinctBitVectorLBSYomi
        )

        val emoticonCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emoticonYomiTrie,
            succinctBitVector = emoticonSuccinctBitVectorLBSYomi,
        )

        val symbolCommonPrefixDeferred = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )

        val emojiListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emojiCommonPrefixDeferred,
            yomiTrie = emojiYomiTrie,
            tokenArray = emojiTokenArray,
            tangoTrie = emojiTangoTrie,
            succinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS,
            type = 11
        )

        val emoticonListDeferred = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = emoticonCommonPrefixDeferred,
            yomiTrie = emoticonYomiTrie,
            tokenArray = emoticonTokenArray,
            tangoTrie = emoticonTangoTrie,
            succinctBitVectorLBSYomi = emoticonSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emoticonSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emoticonSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emoticonSuccinctBitVectorTangoLBS,
            type = 12
        )

        val symbolListDeferred =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                ).filterNot { it.string.containsHentaigana() }
            } else {
                deferredFromDictionarySymbols(
                    input = input,
                    commonPrefixListString = symbolCommonPrefixDeferred,
                    yomiTrie = symbolYomiTrie,
                    tokenArray = symbolTokenArray,
                    tangoTrie = symbolTangoTrie,
                    succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                    succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                    succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                    succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                    type = 13
                )
            }

        val singleKanjiListDeferred = deferredFromDictionarySingleKanji(
            input = input,
            yomiTrie = singleKanjiYomiTrie,
            tokenArray = singleKanjiTokenArray,
            tangoTrie = singleKanjiTangoTrie,
            succinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,
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
                succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
                succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
                succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
                succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
                type = 21
            )

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        if (input.length == 1) {
            val finalList =
                resultNBestFinalDeferred.first + (englishDeferred + englishZenkaku).sortedBy { it.score } + hirakanaAndKana + emojiListDeferred + emoticonListDeferred + symbolListDeferred + symbolHalfWidthListDeferred + singleKanjiListDeferred
            return Pair(finalList, resultNBestFinalDeferred.second)
        }

        val yomiPartOfDeferred = if (input.length > 16) {
            emptyList()
        } else {
            systemYomiTrie.commonPrefixSearch(
                str = input, succinctBitVector = systemSuccinctBitVectorLBSYomi
            ).asReversed()
        }

        val readingCorrectionCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = readingCorrectionYomiTrie,
            succinctBitVector = readingCorrectionSuccinctBitVectorLBSYomi
        )

        val kotowazaCommonPrefixDeferred = deferredPrediction(
            input = input,
            yomiTrie = kotowazaYomiTrie,
            succinctBitVector = kotowazaSuccinctBitVectorLBSYomi
        )

        val yomiPartListDeferred: List<Candidate> = yomiPartOfDeferred.flatMap { yomi ->
            val termId = systemYomiTrie.getTermId(
                systemYomiTrie.getNodeIndex(
                    yomi,
                    systemSuccinctBitVectorLBSYomi,
                ), systemSuccinctBitVectorIsLeafYomi
            )
            systemTokenArray.getListDictionaryByYomiTermId(
                termId, succinctBitVector = systemSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> systemTangoTrie.getLetter(
                            it.nodeId, systemSuccinctBitVectorTangoLBS
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

        val readingCorrectionListDeferred: List<Candidate> =
            readingCorrectionCommonPrefixDeferred.flatMap { yomi ->
                val termId = readingCorrectionYomiTrie.getTermIdShortArray(
                    readingCorrectionYomiTrie.getNodeIndex(
                        yomi, readingCorrectionSuccinctBitVectorLBSYomi
                    ), readingCorrectionSuccinctBitVectorIsLeafYomi
                )
                readingCorrectionTokenArray.getListDictionaryByYomiTermIdShortArray(
                    termId, readingCorrectionSuccinctBitVectorTokenArray
                ).map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> readingCorrectionTangoTrie.getLetterShortArray(
                                it.nodeId, readingCorrectionSuccinctBitVectorTangoLBS
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

        val kotowazaListDeferred: List<Candidate> = kotowazaCommonPrefixDeferred.flatMap { yomi ->
            val termId = kotowazaYomiTrie.getTermIdShortArray(
                kotowazaYomiTrie.getNodeIndex(
                    yomi, kotowazaSuccinctBitVectorLBSYomi
                ), kotowazaSuccinctBitVectorIsLeafYomi
            )
            kotowazaTokenArray.getListDictionaryByYomiTermIdShortArray(
                termId, kotowazaSuccinctBitVectorTokenArray
            ).map {
                Candidate(
                    string = when (it.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> kotowazaTangoTrie.getLetterShortArray(
                            it.nodeId, kotowazaSuccinctBitVectorTangoLBS
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

        val listOfDictionaryToday: List<Candidate> = when (input) {
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

            "いま" -> {
                val now = Calendar.getInstance()
                createCandidatesForTime(now, input)
            }

            else -> emptyList()
        }

        val convertYearToEra: List<Candidate> = when {
            input.matches(Regex("""\d{1,4}ねん""")) -> {
                val year = input.removeSuffix("ねん").toIntOrNull()
                if (year != null) createCandidatesForEra(year, input) else emptyList()
            }

            else -> emptyList()
        }

        val numbersDeferred = generateNumberCandidates(input)

        val mozcUTPersonNames =
            if (mozcUtPersonName == true) getMozcUTPersonNames(input) else emptyList()
        val mozcUTPlacesList = if (mozcUTPlaces == true) getMozcUTPlace(input) else emptyList()
        val mozcUTWikiList = if (mozcUTWiki == true) getMozcUTWiki(input) else emptyList()
        val mozcUTNeologdList = if (mozcUTNeologd == true) getMozcUTNeologd(input) else emptyList()
        val mozcUTWebList = if (mozcUTWeb == true) getMozcUTWeb(input) else emptyList()

        val resultList =
            resultNBestFinalDeferred.first + readingCorrectionListDeferred + mozcUTPersonNames + mozcUTPlacesList + mozcUTWikiList + mozcUTNeologdList + mozcUTWebList + listOfDictionaryToday + numbersDeferred + convertYearToEra

        val resultListFinal =
            resultList.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string }) + (englishDeferred + englishZenkaku).sortedBy { it.score } + symbolHalfWidthListDeferred + (emojiListDeferred + emoticonListDeferred).sortedBy { it.score } + symbolListDeferred + kotowazaListDeferred + hirakanaAndKana + yomiPartListDeferred + singleKanjiListDeferred

        return Pair(
            resultListFinal, resultNBestFinalDeferred.second
        )

    }

    fun getCandidatesEnglishKana(input: String): List<Candidate> {
        val inputToEnglish = input.replaceJapaneseCharactersForEnglish()
        val inputToNumbers = input.groupAndReplaceJapaneseForNumber()
        val listJapaneseCandidates = listOf(
            Candidate(
                string = input, type = (1).toByte(), length = input.length.toUByte(), score = 3000
            ), Candidate(
                string = input.hiraToKata(),
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = input.toHankakuKatakana(),
                type = (31).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToEnglish,
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToEnglish.replaceFirstChar { it.uppercaseChar() },
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToEnglish.uppercase(),
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToNumbers,
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            )
        )

        val fullWidth = Candidate(
            string = inputToNumbers.toFullWidthDigitsEfficient(),
            type = 22,
            length = input.length.toUByte(),
            score = 8000,
            leftId = 2040,
            rightId = 2040
        )
        val halfWidth = Candidate(
            string = inputToNumbers.convertFullWidthToHalfWidth(),
            type = 31,
            length = input.length.toUByte(),
            score = 8000,
            leftId = 2040,
            rightId = 2040
        )
        val timeConversion = createCandidatesForTime(inputToNumbers)
        val dateConversion = createCandidatesForDateInDigit(inputToNumbers)

        // 2. Correctly generate number-to-Kanji/comma candidates.
        val numberValue =
            inputToNumbers.toLongOrNull() // Safely convert the digit string to a number.
        val numberCandidates = if (numberValue != null) {
            buildList {
                // Full Kanji style (e.g., 百二十三)
                add(
                    Candidate(
                        string = numberValue.toKanji(), type = 17, // Using 17 for Kanji
                        score = 2000, length = input.length.toUByte(), leftId = 2040, rightId = 2040
                    )
                )
                // Comma-separated style (e.g., 1,234)
                add(
                    Candidate(
                        string = inputToNumbers.addCommasToNumber(),
                        type = 19,
                        score = 8001,
                        length = input.length.toUByte(),
                        leftId = 2040,
                        rightId = 2040
                    )
                )
                // Original number string itself (e.g., 123)
                add(
                    Candidate(
                        string = inputToNumbers,
                        type = 18,
                        score = 8002,
                        length = input.length.toUByte(),
                        leftId = 2040,
                        rightId = 2040
                    )
                )
                // Mixed Kanji style (e.g., 12万3456)
                add(
                    Candidate(
                        string = numberValue.convertToKanjiNotation(),
                        type = 23, // Using a different type for this style
                        score = 7900, // Lower score for the mixed style
                        length = input.length.toUByte(),
                        leftId = 2040,
                        rightId = 2040
                    )
                )
            }
        } else {
            emptyList()
        }

        val englishDeferred = if (input.isAllEnglishLetters()) {
            englishEngine.getCandidates(input)
        } else if (input.isAllFullWidthAscii()) {
            englishEngine.getCandidates(input.toHankakuAlphabet())
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        val numbersConverted = listOf(
            fullWidth, halfWidth
        ) + (englishDeferred + englishZenkaku).sortedBy { it.score } + timeConversion + dateConversion + numberCandidates

        return listJapaneseCandidates + numbersConverted
    }


    fun getSymbolEmojiCandidates(): List<Emoji> = emojiTokenArray.getNodeIds().map { nodeId ->
        emojiTangoTrie.getLetterShortArray(nodeId, emojiSuccinctBitVectorTangoLBS)
    }.distinct().map { symbol ->
        Emoji(
            symbol = symbol, category = categorizeEmoji(symbol)
        )
    }.sortByEmojiCategory()

    fun getSymbolEmoticonCandidates(): List<Emoticon> = emoticonTokenArray.getNodeIds().map {
        emoticonTangoTrie.getLetterShortArray(
            it, emoticonSuccinctBitVectorTangoLBS
        )
    }.distinct().map { symbol ->
        Emoticon(
            symbol = symbol, category = symbol.toEmoticonCategory()
        )
    }

    fun getSymbolCandidates(): List<Symbol> {
        // Generate the initial list of symbols
        val initialSymbols = symbolTokenArray.getNodeIds().map {
            if (it >= 0) {
                // Corrected: Restored the original 'symbolTangoTrie' variable name
                symbolTangoTrie.getLetterShortArray(
                    it, symbolSuccinctBitVectorTangoLBS
                )
            } else {
                ""
            }
        }.distinct().filterNot { it.isBlank() }

        val filteredSymbols = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            initialSymbols.filterNot { it.containsHentaigana() }
        } else {
            initialSymbols
        }

        return filteredSymbols.map { symbol ->
            Symbol(
                symbol = symbol, category = symbol.toSymbolCategory()
            )
        }
    }

    /**
     * 文字列に変体仮名が含まれているかを確認する拡張関数
     * 変体仮名のUnicode範囲: U+1B000..U+1B0FF
     */
    private fun String.containsHentaigana(): Boolean {
        return this.codePoints().anyMatch { codePoint ->
            codePoint in 0x1B000..0x1B0FF
        }
    }

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
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatter2.format(calendar.time),  // yyyy/MM/dd format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatter3.format(calendar.time),  // M月d日(曜日) format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatterReiwa,  // 令和 format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatterR06,  // Rxx/MM/dd format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = dayOfWeek,  // 曜日 format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            )
        )
    }

    /**
     * 4桁の数字を時刻の候補に変換する。
     *
     * @param input "0000"から"2959"までの4桁の数字文字列。
     * @return 時刻の候補リスト。条件に合わない場合は空のリストを返す。
     */
    private fun createCandidatesForTime(input: String): List<Candidate> {
        // 入力が4桁の数字でない場合は早期リターン
        if (!input.matches(Regex("""\d{4}"""))) {
            return emptyList()
        }

        val number = input.toInt()

        // 全体の数値が 0 から 2959 の範囲内かチェック
        if (number !in 0..2959) {
            return emptyList()
        }

        // 下2桁（分）が 0 から 59 の範囲内かチェック
        val minutes = number % 100
        if (minutes > 59) { // Redundant 'minutes < 0' check removed
            return emptyList()
        }

        // 時間と分を2桁の文字列として取り出す
        val hoursStr = input.substring(0, 2)
        val minutesStr = input.substring(2, 4)

        val length = input.length.toUByte()

        // 2つのフォーマットの候補を作成
        val candidate1 = Candidate(
            string = "$hoursStr:$minutesStr",
            type = 30,
            length = length,
            score = 8000,
            leftId = 1851,
            rightId = 1851
        )

        val candidate2 = Candidate(
            string = "${hoursStr}時${minutesStr}分",
            type = 30,
            length = length,
            score = 8000,
            leftId = 1851,
            rightId = 1851
        )

        return listOf(candidate1, candidate2)
    }

    /**
     * 3桁または4桁の数字を月日の候補に変換する。
     *
     * @param input "101"から"1231"のような3桁または4桁の数字文字列。
     * @return 月日の候補リスト。条件に合わない場合は空のリストを返す。
     */
    private fun createCandidatesForDateInDigit(input: String): List<Candidate> {
        // 入力が3桁または4桁の数字でない場合は早期リターン
        if (!input.matches(Regex("""\d{3,4}"""))) {
            return emptyList()
        }

        // 最後の2桁を「日」、それより前を「月」として分割
        val dayStr = input.substring(input.length - 2)
        val monthStr = input.substring(0, input.length - 2)

        val month = monthStr.toInt()
        val day = dayStr.toInt()

        // 月が1から12の範囲内かチェック
        if (month !in 1..12) {
            return emptyList()
        }

        // 日が1から31の範囲内かチェック（簡略版）
        // ※より厳密にする場合は、月ごとの日数（30日、31日、閏年など）を考慮する必要があります。
        if (day !in 1..31) {
            return emptyList()
        }

        // 候補の文字列を作成（例: 5月12日）
        // .toInt()で変換しているため、"05"のような先頭のゼロは自動的に除去されます。
        val dateString = "${month}月${day}日"

        val length = input.length.toUByte()

        // 候補を作成
        val candidate = Candidate(
            string = dateString, type = 40, // 時刻(30)とは別のタイプ番号を割り当て（例: 40）
            length = length, score = 8000, leftId = 1851, // 必要に応じて日付用のIDに変更
            rightId = 1851  // 必要に応じて日付用のIDに変更
        )

        return listOf(candidate)
    }

    private fun createCandidatesForEra(year: Int, input: String): List<Candidate> {
        // 元号名、開始年、終了年（null は現在まで）
        data class Era(val name: String, val start: Int, val end: Int?)

        val eras = listOf(
            Era("令和", 2019, null),    // 令和は継続中
            Era("平成", 1989, 2019),    // 平成は1989～2019
            Era("昭和", 1926, 1989),    // 昭和は1926～1989
            Era("大正", 1912, 1926),    // 大正は1912～1926
            Era("明治", 1868, 1912)     // 明治は1868～1912
        )

        val length = input.length.toUByte()

        fun formatEra(eraName: String, eraYear: Int) =
            eraName + if (eraYear == 1) "元年" else "${eraYear}年"

        return eras.filter { (_, start, end) ->
            year >= start && (end == null || year <= end)
        }.map { (name, start, _) ->
            val eraYear = year - start + 1
            Candidate(
                string = formatEra(name, eraYear),
                type = 30,
                length = length,
                score = 70000,
                leftId = 1851,
                rightId = 1851
            )
        }
    }

    private fun createCandidatesForTime(cal: Calendar, input: String): List<Candidate> {
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val minutePadded = minute.toString().padStart(2, '0')

        // 12時間表記と午前/午後
        val meridiem = if (hour24 < 12) "午前" else "午後"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }

        return listOf(
            // 例: "14時5分"
            Candidate(
                string = "${hour24}時${minute}分",
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ),
            // 例: "14:05"
            Candidate(
                string = "${
                    hour24.toString().padStart(2, '0')
                }:$minutePadded",
                type = 14,
                length = input.length.toUByte(),
                score = 7001,
                leftId = 1851,
                rightId = 1851
            ),
            // 例: "午後2時5分"
            Candidate(
                string = "$meridiem${hour12}時${minute}分",
                type = 14,
                length = input.length.toUByte(),
                score = 7003,
                leftId = 1851,
                rightId = 1851
            ),
            Candidate(
                string = "${hour12}時${minute}分",
                type = 14,
                length = input.length.toUByte(),
                score = 7004,
                leftId = 1851,
                rightId = 1851
            ),
            Candidate(
                string = "${
                    hour12.toString().padStart(2, '0')
                }:$minutePadded",
                type = 14,
                length = input.length.toUByte(),
                score = 7002,
                leftId = 1851,
                rightId = 1851
            ),
        )
    }

    private fun deferredFromDictionarySymbols(
        input: String,
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        type: Byte
    ): List<Candidate> {
        return commonPrefixListString.flatMap { yomi ->
            if (input.length > yomi.length) {
                return@flatMap emptyList<Candidate>()
            }
            val termIdArray = yomiTrie.getTermIdShortArray(
                yomiTrie.getNodeIndex(yomi, succinctBitVectorLBSYomi), succinctBitVectorIsLeafYomi
            )
            tokenArray.getListDictionaryByYomiTermIdShortArray(
                termIdArray, succinctBitVectorTokenArray
            ).map { entry ->
                Candidate(
                    string = when (entry.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> tangoTrie.getLetterShortArray(
                            entry.nodeId, succinctBitVectorTangoLBS
                        )
                    },
                    type = type,
                    length = yomi.length.toUByte(),
                    score = entry.wordCost.toInt() + if (yomi.length == input.length) 0
                    else 1000 * (yomi.length - input.length),
                    leftId = tokenArray.leftIds[entry.posTableIndex.toInt()],
                    rightId = tokenArray.rightIds[entry.posTableIndex.toInt()]
                )
            }
        }
    }

    private fun deferredFromMozcUTDictionary(
        input: String,
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        type: Byte,
        n: Int
    ): List<Candidate> = commonPrefixListString.flatMap { yomi ->
        if (input.length > yomi.length) return emptyList()
        val termId = yomiTrie.getTermId(
            yomiTrie.getNodeIndex(
                yomi, succinctBitVectorLBSYomi
            ), succinctBitVectorIsLeafYomi
        )
        tokenArray.getListDictionaryByYomiTermId(
            termId, succinctBitVectorTokenArray
        ).map {
            Candidate(
                string = when (it.nodeId) {
                    -2 -> yomi
                    -1 -> yomi.hiraToKata()
                    else -> tangoTrie.getLetter(
                        it.nodeId, succinctBitVectorTangoLBS
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

    private fun deferredFromDictionary(
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        type: Byte
    ): List<Candidate> = commonPrefixListString.flatMap { yomi ->
        val termId = yomiTrie.getTermIdShortArray(
            yomiTrie.getNodeIndex(
                yomi, succinctBitVectorLBSYomi
            ), succinctBitVectorIsLeafYomi
        )
        tokenArray.getListDictionaryByYomiTermIdShortArray(
            termId, succinctBitVectorTokenArray
        ).map {
            Candidate(
                string = when (it.nodeId) {
                    -2 -> yomi
                    -1 -> yomi.hiraToKata()
                    else -> tangoTrie.getLetterShortArray(
                        it.nodeId, succinctBitVectorTangoLBS
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

    private fun deferredFromDictionarySingleKanji(
        input: String,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        type: Byte
    ): List<Candidate> {
        val termIdArray = yomiTrie.getTermIdShortArray(
            yomiTrie.getNodeIndex(input, succinctBitVectorLBSYomi), succinctBitVectorIsLeafYomi
        )
        return tokenArray.getListDictionaryByYomiTermIdShortArray(
            termIdArray, succinctBitVectorTokenArray
        ).map { entry ->
            Candidate(
                string = when (entry.nodeId) {
                    -2 -> input
                    -1 -> input.hiraToKata()
                    else -> tangoTrie.getLetterShortArray(
                        entry.nodeId, succinctBitVectorTangoLBS
                    )
                },
                type = type,
                length = input.length.toUByte(),
                score = entry.wordCost.toInt(),
                leftId = tokenArray.leftIds[entry.posTableIndex.toInt()],
                rightId = tokenArray.rightIds[entry.posTableIndex.toInt()]
            )
        }
    }

    private fun deferredPrediction(
        input: String, yomiTrie: LOUDSWithTermId, succinctBitVector: SuccinctBitVector
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length <= 2) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, succinctBitVector = succinctBitVector
        ).filter {
            when (input.length) {
                in 3..4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length
            }
        }
    }

    private fun deferredPredictionEmojiSymbols(
        input: String, yomiTrie: LOUDSWithTermId, succinctBitVector: SuccinctBitVector
    ): List<String> {
        if (input.length > 16) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, succinctBitVector = succinctBitVector
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
        input: String, yomiTrie: LOUDSWithTermId, succinctBitVector: SuccinctBitVector
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length in 0..3) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, succinctBitVector = succinctBitVector
        ).filter {
            when (input.length) {
                in 3..4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length || it.length == input.length
            }
        }.asReversed()
    }

    private fun commonPrefixMozcUTWeb(
        input: String, yomiTrie: LOUDSWithTermId, succinctBitVector: SuccinctBitVector
    ): List<String> {
        if (input.length > 16) return emptyList()
        if (input.length in 0..2) return emptyList()
        return yomiTrie.predictiveSearch(
            prefix = input, succinctBitVector = succinctBitVector
        ).filter {
            when (input.length) {
                in 3..4 -> it.length <= input.length + 2
                in 5..6 -> it.length <= input.length + 3
                else -> it.length > input.length || it.length == input.length
            }
        }.asReversed()
    }

    fun getMozcUTPersonNames(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUT(
            input = input,
            yomiTrie = personYomiTrie!!,
            succinctBitVector = personSuccinctBitVectorLBSYomi!!
        )
        return deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = personYomiTrie!!,
            tokenArray = personTokenArray!!,
            tangoTrie = personTangoTrie!!,
            succinctBitVectorLBSYomi = personSuccinctBitVectorLBSYomi!!,
            succinctBitVectorIsLeafYomi = personSuccinctBitVectorIsLeaf!!,
            succinctBitVectorTokenArray = personSuccinctBitVectorTokenArray!!,
            succinctBitVectorTangoLBS = personSuccinctBitVectorLBSTango!!,
            type = 23,
            4
        )
    }

    fun getMozcUTPlace(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUT(
            input = input,
            yomiTrie = placesYomiTrie!!,
            succinctBitVector = placesSuccinctBitVectorLBSYomi!!
        )
        return deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = placesYomiTrie!!,
            tokenArray = placesTokenArray!!,
            tangoTrie = placesTangoTrie!!,
            succinctBitVectorLBSYomi = placesSuccinctBitVectorLBSYomi!!,
            succinctBitVectorIsLeafYomi = placesSuccinctBitVectorIsLeaf!!,
            succinctBitVectorTokenArray = placesSuccinctBitVectorTokenArray!!,
            succinctBitVectorTangoLBS = placesSuccinctBitVectorLBSTango!!,
            type = 24,
            4
        )
    }

    private fun getMozcUTWiki(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUTWeb(
            input = input,
            yomiTrie = wikiYomiTrie!!,
            succinctBitVector = wikiSuccinctBitVectorLBSYomi!!
        )
        return deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = wikiYomiTrie!!,
            tokenArray = wikiTokenArray!!,
            tangoTrie = wikiTangoTrie!!,
            succinctBitVectorLBSYomi = wikiSuccinctBitVectorLBSYomi!!,
            succinctBitVectorIsLeafYomi = wikiSuccinctBitVectorIsLeaf!!,
            succinctBitVectorTokenArray = wikiSuccinctBitVectorTokenArray!!,
            succinctBitVectorTangoLBS = wikiSuccinctBitVectorLBSTango!!,
            type = 25,
            4
        )
    }

    private fun getMozcUTNeologd(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUTWeb(
            input = input,
            yomiTrie = neologdYomiTrie!!,
            succinctBitVector = neologdSuccinctBitVectorLBSYomi!!,
        )
        return deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = neologdYomiTrie!!,
            tokenArray = neologdTokenArray!!,
            tangoTrie = neologdTangoTrie!!,
            succinctBitVectorLBSYomi = neologdSuccinctBitVectorLBSYomi!!,
            succinctBitVectorIsLeafYomi = neologdSuccinctBitVectorIsLeaf!!,
            succinctBitVectorTokenArray = neologdSuccinctBitVectorTokenArray!!,
            succinctBitVectorTangoLBS = neologdSuccinctBitVectorLBSTango!!,
            type = 26,
            4
        )
    }

    private fun getMozcUTWeb(
        input: String
    ): List<Candidate> {
        val commonPrefix = commonPrefixMozcUTWeb(
            input = input,
            yomiTrie = webYomiTrie!!,
            succinctBitVector = webSuccinctBitVectorLBSYomi!!,
        )
        return deferredFromMozcUTDictionary(
            input = input,
            commonPrefixListString = commonPrefix,
            yomiTrie = webYomiTrie!!,
            tokenArray = webTokenArray!!,
            tangoTrie = webTangoTrie!!,
            succinctBitVectorLBSYomi = webSuccinctBitVectorLBSYomi!!,
            succinctBitVectorIsLeafYomi = webSuccinctBitVectorIsLeaf!!,
            succinctBitVectorTokenArray = webSuccinctBitVectorTokenArray!!,
            succinctBitVectorTangoLBS = webSuccinctBitVectorLBSTango!!,
            type = 27,
            4
        )
    }

    private fun generateNumberCandidates(input: String): List<Candidate> {
        val numPair = input.toNumber()
        val expoPair = input.toNumberExponent()

        return if (numPair != null) {
            val (firstNum, secondNum) = numPair // firstNum: 全角, secondNum: 半角
            val numberAsLong = secondNum.toLongOrNull()

            // 候補リストを構築
            val candidates = mutableListOf<Candidate>()

            // 1. 伝統的な漢数字 (例: 十, 百二十三)
            if (numberAsLong != null) {
                candidates.add(
                    Candidate(
                        string = numberAsLong.toKanji(), type = 32, // 新しいタイプ
                        length = input.length.toUByte(), score = 8000, // 優先度を調整
                        leftId = 2040, rightId = 2040
                    )
                )
            }

            // 2. 単位付き漢数字 (例: 1億2345万)
            if (numberAsLong != null) {
                candidates.add(
                    Candidate(
                        string = numberAsLong.convertToKanjiNotation(),
                        type = 17,
                        length = input.length.toUByte(),
                        score = 8000,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
            }

            // 3. 全角・半角数字 (例: １２３, 123)
            listOf(firstNum, secondNum).forEach {
                candidates.add(
                    Candidate(
                        string = it,
                        type = if (it == firstNum) (30).toByte() else (31).toByte(),
                        length = input.length.toUByte(),
                        score = 8002,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
            }

            // 4. カンマ区切り数字 (例: 123,456)
            candidates.add(
                Candidate(
                    string = secondNum.addCommasToNumber(),
                    type = 19,
                    length = input.length.toUByte(),
                    score = 8001,
                    leftId = 2040,
                    rightId = 2040
                )
            )

            // 5. 指数表記 (例: 10⁸)
            if (expoPair != null) {
                candidates.add(
                    Candidate(
                        string = expoPair.first,
                        type = 20,
                        length = input.length.toUByte(),
                        score = 8003,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
            }

            candidates

        } else {
            emptyList()
        }
    }

    /**
     * Candidate リストを処理し、
     * string に全角数字が含まれる場合、それを半角数字に変換した
     * 新しい Candidate をリストに追加します。
     *
     * @param originalData 元の Pair<List<Candidate>, List<Int>>
     * @return 元の候補と、半角変換された候補の両方を含む新しい Pair
     */
    private fun addHalfWidthCandidates(
        originalData: Pair<List<Candidate>, List<Int>>
    ): Pair<List<Candidate>, List<Int>> {

        val originalList = originalData.first
        val intList = originalData.second

        // flatMap を使ってリストを変換・拡張します
        val newList = originalList.flatMap { candidate ->
            // 1. string に全角数字が含まれているかチェック
            if (candidate.string.containsFullWidthNumber()) {

                // 2. 全角数字を半角に変換
                val newString = candidate.string.convertFullWidthNumbersToHalfWidth()

                // 3. 元の候補のコピーを作成し、string だけを新しい文字列に差し替え
                val newCandidate = candidate.copy(
                    string = newString,
                    type = 31,
                    score = candidate.score + 6000
                    // type, length, score など他のプロパティはそのままコピーされます
                )

                // 4. 元の候補 (例: １８時) と、
                //    新しい候補 (例: 18時) の両方をリストとして返す
                listOf(candidate, newCandidate)

            } else {
                // 5. 全角数字を含まない場合は、元の候補だけをリストとして返す
                listOf(candidate)
            }
        }.distinct() // 最後にリスト全体の重複を除去します

        // 6. 新しく作成した候補リストと、元の Int リストで Pair を再構築して返す
        return Pair(newList, intList)
    }

    /**
     * Candidate リストを処理し、
     * string に全角数字が含まれる場合、それを半角数字に変換した
     * 新しい Candidate をリストに追加します。
     *
     * @param originalList 元の List<Candidate>
     * @return 元の候補と、半角変換された候補の両方を含む新しい List
     */
    private fun addHalfWidthCandidates(
        originalList: List<Candidate>
    ): List<Candidate> {

        // flatMap を使ってリストを変換・拡張します
        val newList = originalList.flatMap { candidate ->
            // 1. string に全角数字が含まれているかチェック
            if (candidate.string.containsFullWidthNumber()) {

                // 2. 全角数字を半角に変換
                val newString = candidate.string.convertFullWidthNumbersToHalfWidth()

                // 3. 元の候補のコピーを作成し、string だけを新しい文字列に差し替え
                val newCandidate = candidate.copy(
                    string = newString,
                    type = 31,
                    score = candidate.score + 6000
                    // type, length, score など他のプロパティはそのままコピーされます
                )

                // 4. 元の候補 (例: １８時) と、
                //    新しい候補 (例: 18時) の両方をリストとして返す
                listOf(candidate, newCandidate)

            } else {
                // 5. 全角数字を含まない場合は、元の候補だけをリストとして返す
                listOf(candidate)
            }
        }.distinct() // 最後にリスト全体の重複を除去します

        // 6. 新しく作成した候補リストを返す
        return newList
    }

}
