package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.transition.Slide
import android.transition.Transition
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.room.Room
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank0GetShortArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.PressedKeyStatus
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDatabase
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.preprocessLBSIntoBooleanArray
import com.kazumaproject.toBooleanArray
import com.kazumaproject.viterbi.FindPath
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun providesLearnDatabase(
        @ApplicationContext context: Context
    ) = Room
        .databaseBuilder(
            context,
            LearnDatabase::class.java,
            "learn_database"
        )
        .build()

    @Singleton
    @Provides
    fun providesLearnDao(db: LearnDatabase): LearnDao = db.learnDao()

    @Singleton
    @Provides
    fun provideSuggestionAdapter(): SuggestionAdapter =
        SuggestionAdapter()

    @Singleton
    @Provides
    fun providesClipBoardUtil(@ApplicationContext context: Context): ClipboardUtil =
        ClipboardUtil(context)

    @Singleton
    @Provides
    fun providesLearnMultiple(): LearnMultiple = LearnMultiple()

    @MainDispatcher
    @Singleton
    @Provides
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Singleton
    @Provides
    fun providesStringBuilder(): StringBuilder = StringBuilder()

    @Singleton
    @Provides
    fun providesTransition(): Transition = Slide(Gravity.BOTTOM)

    @Singleton
    @Provides
    @Named("main_ime_scope")
    fun providesIMEScope(
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob(Job()) + mainDispatcher)

    @Singleton
    @Provides
    fun providesPreference(@ApplicationContext context: Context): AppPreference {
        return AppPreference.apply {
            init(context)
        }
    }

    @Singleton
    @Provides
    @ConnectionIds
    fun provideConnectionIds(@ApplicationContext context: Context): ShortArray {
        ZipInputStream(context.assets.open("connectionId.dat.zip")).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "connectionId.dat") {
                    BufferedInputStream(zipStream).use { inputStream ->
                        return ConnectionIdBuilder().readShortArrayFromBytes(inputStream)
                    }
                }
                entry = zipStream.nextEntry
            }
        }
        throw IllegalArgumentException("connectionId.dat not found in connectionId.zip")
    }

    @SystemTangoTrie
    @Singleton
    @Provides
    fun provideTangoTrie(@ApplicationContext context: Context): LOUDS {
        val zipInputStream = ZipInputStream(context.assets.open("system/tango.dat.zip"))
        zipInputStream.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStream)).use {
            return LOUDS().readExternalNotCompress(it)
        }
    }

    @SystemYomiTrie
    @Singleton
    @Provides
    fun provideYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val zipInputStream = ZipInputStream(context.assets.open("system/yomi.dat.zip"))
        zipInputStream.nextEntry
        ObjectInputStream(BufferedInputStream(zipInputStream)).use {
            return LOUDSWithTermId().readExternalNotCompress(it)
        }
    }

    @SystemTokenArray
    @Singleton
    @Provides
    fun providesTokenArray(@ApplicationContext context: Context): TokenArray {
        val tokenArray = TokenArray()

        // Extract and read `token.dat` from `token.dat.zip`
        ZipInputStream(context.assets.open("system/token.dat.zip")).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "token.dat") { // Ensure we are processing the correct file inside the zip
                    ObjectInputStream(BufferedInputStream(zipStream)).use { objectInput ->
                        tokenArray.readExternal(objectInput) // Load `token.dat` into TokenArray
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        context.assets.open("pos_table.dat").use { inputStream ->
            ObjectInputStream(BufferedInputStream(inputStream)).use { objectInput ->
                tokenArray.readPOSTable(objectInput)
            }
        }

        return tokenArray
    }

    @Singleton
    @Provides
    @SystemSuccinctBitVectorLBSYomi
    fun provideSuccinctBitVectorLBSYomi(@SystemYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @SystemSuccinctBitVectorIsLeafYomi
    fun provideSuccinctBitVectorIsLeaf(@SystemYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @SystemYomiLBSBooleanArray
    fun providesYomiLBSBooleanArray(@SystemYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SystemYomiLBSBooleanArrayPreprocess
    fun providesYomiLBSBooleanArrayPreprocess(@SystemYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @SystemSuccinctBitVectorTokenArray
    fun provideSystemSuccinctBitVectorTokenArray(@SystemTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @SystemSuccinctBitVectorTangoLBS
    fun provideSystemSuccinctBitVectorTangoLBS(@SystemTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)


    @SingleKanjiTangoTrie
    @Singleton
    @Provides
    fun provideSingleKanjiTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/tango_singleKanji.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SingleKanjiYomiTrie
    @Singleton
    @Provides
    fun provideSingleKanjiYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/yomi_singleKanji.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SingleKanjiTokenArray
    @Singleton
    @Provides
    fun providesSingleKanjiTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/token_singleKanji.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayLBSYomi
    fun provideSingleKanjiRank0ArrayLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayLBSYomi
    fun provideSingleKanjiRank1ArrayLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayIsLeafYomi
    fun provideSingleKanjiRank1ArrayIsLeaf(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiYomiLBSBooleanArray
    fun providesSingleKanjiYomiLBSBooleanArray(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SingleKanjiYomiLBSBooleanArrayPreprocess
    fun providesSingleKanjiYomiLBSBooleanArrayPreprocess(@SingleKanjiYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayTokenArrayBitvector
    fun provideSingleKanjiRank0ArrayTokenArrayBitvector(@SingleKanjiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayTokenArrayBitvector
    fun provideSingleKanjiRank1ArrayTokenArrayBitvector(@SingleKanjiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayTangoLBS
    fun provideSingleKanjiRank0ArrayLBSTango(@SingleKanjiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayTangoLBS
    fun provideSingleKanjiRank1ArrayLBSTango(@SingleKanjiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()


    @EmojiTangoTrie
    @Singleton
    @Provides
    fun provideEmojiTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoji/tango_emoji.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @EmojiYomiTrie
    @Singleton
    @Provides
    fun provideEmojiYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoji/yomi_emoji.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @EmojiTokenArray
    @Singleton
    @Provides
    fun providesEmojiTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoji/token_emoji.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @EmojiRank0ArrayLBSYomi
    fun provideEmojiRank0ArrayLBSYomi(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayLBSYomi
    fun provideEmojiRank1ArrayLBSYomi(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayIsLeafYomi
    fun provideEmojiRank1ArrayIsLeaf(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @EmojiYomiLBSBooleanArray
    fun providesEmojiYomiLBSBooleanArray(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @EmojiYomiLBSBooleanArrayPreprocess
    fun providesEmojiYomiLBSBooleanArrayPreprocess(@EmojiYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @EmojiRank0ArrayTokenArrayBitvector
    fun provideEmojiRank0ArrayTokenArrayBitvector(@EmojiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayTokenArrayBitvector
    fun provideEmojiRank1ArrayTokenArrayBitvector(@EmojiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @EmojiRank0ArrayTangoLBS
    fun provideEmojiRank0ArrayLBSTango(@EmojiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayTangoLBS
    fun provideEmojiRank1ArrayLBSTango(@EmojiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()

    @EmoticonTangoTrie
    @Singleton
    @Provides
    fun provideEmoticonTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoticon/tango_emoticon.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @EmoticonYomiTrie
    @Singleton
    @Provides
    fun provideEmoticonYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoticon/yomi_emoticon.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @EmoticonTokenArray
    @Singleton
    @Provides
    fun providesEmoticonTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoticon/token_emoticon.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @EmoticonRank0ArrayLBSYomi
    fun provideEmoticonRank0ArrayLBSYomi(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayLBSYomi
    fun provideEmoticonRank1ArrayLBSYomi(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayIsLeafYomi
    fun provideEmoticonRank1ArrayIsLeaf(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @EmoticonYomiLBSBooleanArray
    fun providesEmoticonYomiLBSBooleanArray(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @EmoticonYomiLBSBooleanArrayPreprocess
    fun providesEmoticonYomiLBSBooleanArrayPreprocess(@EmoticonYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @EmoticonRank0ArrayTokenArrayBitvector
    fun provideEmoticonRank0ArrayTokenArrayBitvector(@EmoticonTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayTokenArrayBitvector
    fun provideEmoticonRank1ArrayTokenArrayBitvector(@EmoticonTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank0ArrayTangoLBS
    fun provideEmoticonRank0ArrayLBSTango(@EmoticonTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayTangoLBS
    fun provideEmoticonRank1ArrayLBSTango(@EmoticonTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()


    @SymbolTangoTrie
    @Singleton
    @Provides
    fun provideSymbolTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("symbol/tango_symbol.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SymbolYomiTrie
    @Singleton
    @Provides
    fun provideSymbolYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("symbol/yomi_symbol.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SymbolTokenArray
    @Singleton
    @Provides
    fun providesSymbolTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("symbol/token_symbol.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SymbolRank0ArrayLBSYomi
    fun provideSymbolRank0ArrayLBSYomi(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayLBSYomi
    fun provideSymbolRank1ArrayLBSYomi(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayIsLeafYomi
    fun provideSymbolRank1ArrayIsLeaf(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @SymbolYomiLBSBooleanArray
    fun providesSymbolYomiLBSBooleanArray(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SymbolYomiLBSBooleanArrayPreprocess
    fun providesSymbolYomiLBSBooleanArrayPreprocess(@SymbolYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @SymbolRank0ArrayTokenArrayBitvector
    fun provideSymbolRank0ArrayTokenArrayBitvector(@SymbolTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayTokenArrayBitvector
    fun provideSymbolRank1ArrayTokenArrayBitvector(@SymbolTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @SymbolRank0ArrayTangoLBS
    fun provideSymbolRank0ArrayLBSTango(@SymbolTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayTangoLBS
    fun provideSymbolRank1ArrayLBSTango(@SymbolTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()


    @ReadingCorrectionTangoTrie
    @Singleton
    @Provides
    fun provideReadingCorrectionTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("reading_correction/tango_reading_correction.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @ReadingCorrectionYomiTrie
    @Singleton
    @Provides
    fun provideReadingCorrectionYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("reading_correction/yomi_reading_correction.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @ReadingCorrectionTokenArray
    @Singleton
    @Provides
    fun providesReadingCorrectionTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("reading_correction/token_reading_correction.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @ReadingCorrectionRank0ArrayLBSYomi
    fun provideReadingCorrectionRank0ArrayLBSYomi(@ReadingCorrectionYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @ReadingCorrectionRank1ArrayLBSYomi
    fun provideReadingCorrectionRank1ArrayLBSYomi(@ReadingCorrectionYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @ReadingCorrectionRank1ArrayIsLeafYomi
    fun provideReadingCorrectionRank1ArrayIsLeaf(@ReadingCorrectionYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @ReadingCorrectionYomiLBSBooleanArray
    fun providesReadingCorrectionYomiLBSBooleanArray(@ReadingCorrectionYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @ReadingCorrectionYomiLBSBooleanArrayPrerpcess
    fun providesReadingCorrectionYomiLBSBooleanArrayPreprocess(@ReadingCorrectionYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @ReadingCorrectionRank0ArrayTokenArrayBitvector
    fun provideReadingCorrectionRank0ArrayTokenArrayBitvector(@ReadingCorrectionTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @ReadingCorrectionRank1ArrayTokenArrayBitvector
    fun provideReadingCorrectionRank1ArrayTokenArrayBitvector(@ReadingCorrectionTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @ReadingCorrectionRank0ArrayTangoLBS
    fun provideReadingCorrectionRank0ArrayLBSTango(@ReadingCorrectionTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @ReadingCorrectionRank1ArrayTangoLBS
    fun provideReadingCorrectionRank1ArrayLBSTango(@ReadingCorrectionTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()

    @KotowazaTangoTrie
    @Singleton
    @Provides
    fun provideKotowazaTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("kotowaza/tango_kotowaza.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @KotowazaYomiTrie
    @Singleton
    @Provides
    fun provideKotowazaYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("kotowaza/yomi_kotowaza.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @KotowazaTokenArray
    @Singleton
    @Provides
    fun providesKotowazaTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("kotowaza/token_kotowaza.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @KotowazaRank0ArrayLBSYomi
    fun provideKotowazaRank0ArrayLBSYomi(@KotowazaYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @KotowazaRank1ArrayLBSYomi
    fun provideKotowazaRank1ArrayLBSYomi(@KotowazaYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @KotowazaRank1ArrayIsLeafYomi
    fun provideKotowazaRank1ArrayIsLeaf(@KotowazaYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @KotowazaYomiLBSBooleanArray
    fun providesKotowazaYomiLBSBooleanArray(@KotowazaYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @KotowazaYomiLBSBooleanArrayPreprocess
    fun providesKotowazaYomiLBSBooleanArrayPreprocess(@KotowazaYomiLBSBooleanArray booleanArray: BooleanArray) =
        booleanArray.preprocessLBSIntoBooleanArray()

    @Singleton
    @Provides
    @KotowazaRank0ArrayTokenArrayBitvector
    fun provideKotowazaRank0ArrayTokenArrayBitvector(@KotowazaTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @KotowazaRank1ArrayTokenArrayBitvector
    fun provideKotowazaRank1ArrayTokenArrayBitvector(@KotowazaTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @KotowazaRank0ArrayTangoLBS
    fun provideKotowazaRank0ArrayLBSTango(@KotowazaTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @KotowazaRank1ArrayTangoLBS
    fun provideKotowazaRank1ArrayLBSTango(@KotowazaTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    fun provideKanaKanjiHenkanEngine(
        @ConnectionIds connectionIds: ShortArray,

        @SystemTangoTrie systemTangoTrie: LOUDS,
        @SystemYomiTrie systemYomiTrie: LOUDSWithTermId,
        @SystemTokenArray systemTokenArray: TokenArray,
        @SystemSuccinctBitVectorLBSYomi systemSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @SystemSuccinctBitVectorIsLeafYomi systemSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @SystemSuccinctBitVectorTokenArray systemSuccinctBitVectorTokenArray: SuccinctBitVector,
        @SystemSuccinctBitVectorTangoLBS systemSuccinctBitVectorTangoLBS: SuccinctBitVector,
        @SystemYomiLBSBooleanArray systemYomiLBSBooleanArray: BooleanArray,

        @SingleKanjiTangoTrie singleKanjiTangoTrie: LOUDS,
        @SingleKanjiYomiTrie singleKanjiYomiTrie: LOUDSWithTermId,
        @SingleKanjiTokenArray singleKanjiTokenArray: TokenArray,
        @SingleKanjiRank0ArrayLBSYomi singleKanjiRank0ArrayLBSYomi: ShortArray,
        @SingleKanjiRank1ArrayLBSYomi singleKanjiRank1ArrayLBSYomi: ShortArray,
        @SingleKanjiRank1ArrayIsLeafYomi singleKanjiRank1ArrayIsLeaf: ShortArray,
        @SingleKanjiRank0ArrayTokenArrayBitvector singleKanjiRank0ArrayTokenArrayBitvector: ShortArray,
        @SingleKanjiRank1ArrayTokenArrayBitvector singleKanjiRank1ArrayTokenArrayBitvector: ShortArray,
        @SingleKanjiRank0ArrayTangoLBS singleKanjiRank0ArrayTangoLBS: ShortArray,
        @SingleKanjiRank1ArrayTangoLBS singleKanjiRank1ArrayTangoLBS: ShortArray,
        @SingleKanjiYomiLBSBooleanArray singleKanjiYomiLBSBooleanArray: BooleanArray,

        @EmojiTangoTrie emojiTangoTrie: LOUDS,
        @EmojiYomiTrie emojiYomiTrie: LOUDSWithTermId,
        @EmojiTokenArray emojiTokenArray: TokenArray,
        @EmojiRank0ArrayLBSYomi emojiRank0ArrayLBSYomi: ShortArray,
        @EmojiRank1ArrayLBSYomi emojiRank1ArrayLBSYomi: ShortArray,
        @EmojiRank1ArrayIsLeafYomi emojiRank1ArrayIsLeaf: ShortArray,
        @EmojiRank0ArrayTokenArrayBitvector emojiRank0ArrayTokenArrayBitvector: ShortArray,
        @EmojiRank1ArrayTokenArrayBitvector emojiRank1ArrayTokenArrayBitvector: ShortArray,
        @EmojiRank0ArrayTangoLBS emojiRank0ArrayTangoLBS: ShortArray,
        @EmojiRank1ArrayTangoLBS emojiRank1ArrayTangoLBS: ShortArray,
        @EmojiYomiLBSBooleanArray emojiYomiLBSBooleanArray: BooleanArray,

        @EmoticonTangoTrie emoticonTangoTrie: LOUDS,
        @EmoticonYomiTrie emoticonYomiTrie: LOUDSWithTermId,
        @EmoticonTokenArray emoticonTokenArray: TokenArray,
        @EmoticonRank0ArrayLBSYomi emoticonRank0ArrayLBSYomi: ShortArray,
        @EmoticonRank1ArrayLBSYomi emoticonRank1ArrayLBSYomi: ShortArray,
        @EmoticonRank1ArrayIsLeafYomi emoticonRank1ArrayIsLeaf: ShortArray,
        @EmoticonRank0ArrayTokenArrayBitvector emoticonRank0ArrayTokenArrayBitvector: ShortArray,
        @EmoticonRank1ArrayTokenArrayBitvector emoticonRank1ArrayTokenArrayBitvector: ShortArray,
        @EmoticonRank0ArrayTangoLBS emoticonRank0ArrayTangoLBS: ShortArray,
        @EmoticonRank1ArrayTangoLBS emoticonRank1ArrayTangoLBS: ShortArray,
        @EmoticonYomiLBSBooleanArray emoticonYomiLBSBooleanArray: BooleanArray,

        @SymbolTangoTrie symbolTangoTrie: LOUDS,
        @SymbolYomiTrie symbolYomiTrie: LOUDSWithTermId,
        @SymbolTokenArray symbolTokenArray: TokenArray,
        @SymbolRank0ArrayLBSYomi symbolRank0ArrayLBSYomi: ShortArray,
        @SymbolRank1ArrayLBSYomi symbolRank1ArrayLBSYomi: ShortArray,
        @SymbolRank1ArrayIsLeafYomi symbolRank1ArrayIsLeaf: ShortArray,
        @SymbolRank0ArrayTokenArrayBitvector symbolRank0ArrayTokenArrayBitvector: ShortArray,
        @SymbolRank1ArrayTokenArrayBitvector symbolRank1ArrayTokenArrayBitvector: ShortArray,
        @SymbolRank0ArrayTangoLBS symbolRank0ArrayTangoLBS: ShortArray,
        @SymbolRank1ArrayTangoLBS symbolRank1ArrayTangoLBS: ShortArray,
        @SymbolYomiLBSBooleanArray symbolYomiLBSBooleanArray: BooleanArray,

        @ReadingCorrectionTangoTrie readingCorrectionTangoTrie: LOUDS,
        @ReadingCorrectionYomiTrie readingCorrectionYomiTrie: LOUDSWithTermId,
        @ReadingCorrectionTokenArray readingCorrectionTokenArray: TokenArray,
        @ReadingCorrectionRank0ArrayLBSYomi readingCorrectionRank0ArrayLBSYomi: ShortArray,
        @ReadingCorrectionRank1ArrayLBSYomi readingCorrectionRank1ArrayLBSYomi: ShortArray,
        @ReadingCorrectionRank1ArrayIsLeafYomi readingCorrectionRank1ArrayIsLeaf: ShortArray,
        @ReadingCorrectionRank0ArrayTokenArrayBitvector readingCorrectionRank0ArrayTokenArrayBitvector: ShortArray,
        @ReadingCorrectionRank1ArrayTokenArrayBitvector readingCorrectionRank1ArrayTokenArrayBitvector: ShortArray,
        @ReadingCorrectionRank0ArrayTangoLBS readingCorrectionRank0ArrayTangoLBS: ShortArray,
        @ReadingCorrectionRank1ArrayTangoLBS readingCorrectionRank1ArrayTangoLBS: ShortArray,
        @ReadingCorrectionYomiLBSBooleanArray readingCorrectionYomiLBSBooleanArray: BooleanArray,

        @KotowazaTangoTrie kotowazaTangoTrie: LOUDS,
        @KotowazaYomiTrie kotowazaYomiTrie: LOUDSWithTermId,
        @KotowazaTokenArray kotowazaTokenArray: TokenArray,
        @KotowazaRank0ArrayLBSYomi kotowazaRank0ArrayLBSYomi: ShortArray,
        @KotowazaRank1ArrayLBSYomi kotowazaRank1ArrayLBSYomi: ShortArray,
        @KotowazaRank1ArrayIsLeafYomi kotowazaRank1ArrayIsLeaf: ShortArray,
        @KotowazaRank0ArrayTokenArrayBitvector kotowazaRank0ArrayTokenArrayBitvector: ShortArray,
        @KotowazaRank1ArrayTokenArrayBitvector kotowazaRank1ArrayTokenArrayBitvector: ShortArray,
        @KotowazaRank0ArrayTangoLBS kotowazaRank0ArrayTangoLBS: ShortArray,
        @KotowazaRank1ArrayTangoLBS kotowazaRank1ArrayTangoLBS: ShortArray,
        @KotowazaYomiLBSBooleanArray kotowazaYomiLBSBooleanArray: BooleanArray,

        @SystemYomiLBSBooleanArrayPreprocess systemYomiLBSBooleanArrayPreprocess: IntArray,
        @SingleKanjiYomiLBSBooleanArrayPreprocess singleKanjiYomiLBSBooleanArrayPreprocess: IntArray,
        @EmojiYomiLBSBooleanArrayPreprocess emojiYomiLBSBooleanArrayPreprocess: IntArray,
        @EmoticonYomiLBSBooleanArrayPreprocess emoticonYomiLBSBooleanArrayPreprocess: IntArray,
        @SymbolYomiLBSBooleanArrayPreprocess symbolYomiLBSBooleanArrayPreprocess: IntArray,
        @ReadingCorrectionYomiLBSBooleanArrayPrerpcess readingCorrectionYomiLBSBooleanArrayPreprocess: IntArray,
        @KotowazaYomiLBSBooleanArrayPreprocess kotowazaYomiLBSBooleanArrayPreprocess: IntArray,
    ): KanaKanjiEngine {
        val kanaKanjiEngine = KanaKanjiEngine()
        val graphBuilder = GraphBuilder()
        val findPath = FindPath()

        kanaKanjiEngine.buildEngine(
            graphBuilder = graphBuilder,
            findPath = findPath,
            connectionIdList = connectionIds,

            systemTangoTrie = systemTangoTrie,
            systemYomiTrie = systemYomiTrie,
            systemTokenArray = systemTokenArray,
            systemSuccinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
            systemSuccinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
            systemSuccinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
            systemSuccinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
            systemYomiLBSBooleanArray = systemYomiLBSBooleanArray,

            singleKanjiTangoTrie = singleKanjiTangoTrie,
            singleKanjiYomiTrie = singleKanjiYomiTrie,
            singleKanjiTokenArray = singleKanjiTokenArray,
            singleKanjiRank0ArrayLBSYomi = singleKanjiRank0ArrayLBSYomi,
            singleKanjiRank1ArrayLBSYomi = singleKanjiRank1ArrayLBSYomi,
            singleKanjiRank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf,
            singleKanjiRank0ArrayTokenArrayBitvector = singleKanjiRank0ArrayTokenArrayBitvector,
            singleKanjiRank1ArrayTokenArrayBitvector = singleKanjiRank1ArrayTokenArrayBitvector,
            singleKanjiRank0ArrayLBSTango = singleKanjiRank0ArrayTangoLBS,
            singleKanjiRank1ArrayLBSTango = singleKanjiRank1ArrayTangoLBS,
            singleKanjiYomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray,

            emojiTangoTrie = emojiTangoTrie,
            emojiYomiTrie = emojiYomiTrie,
            emojiTokenArray = emojiTokenArray,
            emojiRank0ArrayLBSYomi = emojiRank0ArrayLBSYomi,
            emojiRank1ArrayLBSYomi = emojiRank1ArrayLBSYomi,
            emojiRank1ArrayIsLeaf = emojiRank1ArrayIsLeaf,
            emojiRank0ArrayTokenArrayBitvector = emojiRank0ArrayTokenArrayBitvector,
            emojiRank1ArrayTokenArrayBitvector = emojiRank1ArrayTokenArrayBitvector,
            emojiRank0ArrayLBSTango = emojiRank0ArrayTangoLBS,
            emojiRank1ArrayLBSTango = emojiRank1ArrayTangoLBS,
            emojiYomiLBSBooleanArray = emojiYomiLBSBooleanArray,

            emoticonTangoTrie = emoticonTangoTrie,
            emoticonYomiTrie = emoticonYomiTrie,
            emoticonTokenArray = emoticonTokenArray,
            emoticonRank0ArrayLBSYomi = emoticonRank0ArrayLBSYomi,
            emoticonRank1ArrayLBSYomi = emoticonRank1ArrayLBSYomi,
            emoticonRank1ArrayIsLeaf = emoticonRank1ArrayIsLeaf,
            emoticonRank0ArrayTokenArrayBitvector = emoticonRank0ArrayTokenArrayBitvector,
            emoticonRank1ArrayTokenArrayBitvector = emoticonRank1ArrayTokenArrayBitvector,
            emoticonRank0ArrayLBSTango = emoticonRank0ArrayTangoLBS,
            emoticonRank1ArrayLBSTango = emoticonRank1ArrayTangoLBS,
            emoticonYomiLBSBooleanArray = emoticonYomiLBSBooleanArray,

            symbolTangoTrie = symbolTangoTrie,
            symbolYomiTrie = symbolYomiTrie,
            symbolTokenArray = symbolTokenArray,
            symbolRank0ArrayLBSYomi = symbolRank0ArrayLBSYomi,
            symbolRank1ArrayLBSYomi = symbolRank1ArrayLBSYomi,
            symbolRank1ArrayIsLeaf = symbolRank1ArrayIsLeaf,
            symbolRank0ArrayTokenArrayBitvector = symbolRank0ArrayTokenArrayBitvector,
            symbolRank1ArrayTokenArrayBitvector = symbolRank1ArrayTokenArrayBitvector,
            symbolRank0ArrayLBSTango = symbolRank0ArrayTangoLBS,
            symbolRank1ArrayLBSTango = symbolRank1ArrayTangoLBS,
            symbolYomiLBSBooleanArray = symbolYomiLBSBooleanArray,

            readingCorrectionTangoTrie = readingCorrectionTangoTrie,
            readingCorrectionYomiTrie = readingCorrectionYomiTrie,
            readingCorrectionTokenArray = readingCorrectionTokenArray,
            readingCorrectionRank0ArrayLBSYomi = readingCorrectionRank0ArrayLBSYomi,
            readingCorrectionRank1ArrayLBSYomi = readingCorrectionRank1ArrayLBSYomi,
            readingCorrectionRank1ArrayIsLeaf = readingCorrectionRank1ArrayIsLeaf,
            readingCorrectionRank0ArrayTokenArrayBitvector = readingCorrectionRank0ArrayTokenArrayBitvector,
            readingCorrectionRank1ArrayTokenArrayBitvector = readingCorrectionRank1ArrayTokenArrayBitvector,
            readingCorrectionRank0ArrayLBSTango = readingCorrectionRank0ArrayTangoLBS,
            readingCorrectionRank1ArrayLBSTango = readingCorrectionRank1ArrayTangoLBS,
            readingCorrectionYomiLBSBooleanArray = readingCorrectionYomiLBSBooleanArray,

            kotowazaTangoTrie = kotowazaTangoTrie,
            kotowazaYomiTrie = kotowazaYomiTrie,
            kotowazaTokenArray = kotowazaTokenArray,
            kotowazaRank0ArrayLBSYomi = kotowazaRank0ArrayLBSYomi,
            kotowazaRank1ArrayLBSYomi = kotowazaRank1ArrayLBSYomi,
            kotowazaRank1ArrayIsLeaf = kotowazaRank1ArrayIsLeaf,
            kotowazaRank0ArrayTokenArrayBitvector = kotowazaRank0ArrayTokenArrayBitvector,
            kotowazaRank1ArrayTokenArrayBitvector = kotowazaRank1ArrayTokenArrayBitvector,
            kotowazaRank0ArrayLBSTango = kotowazaRank0ArrayTangoLBS,
            kotowazaRank1ArrayLBSTango = kotowazaRank1ArrayTangoLBS,
            kotowazaYomiLBSBooleanArray = kotowazaYomiLBSBooleanArray,

            systemYomiLBSPreprocess = systemYomiLBSBooleanArrayPreprocess,
            singleKanjiYomiLBSPreprocess = singleKanjiYomiLBSBooleanArrayPreprocess,
            emojiYomiLBSPreprocess = emojiYomiLBSBooleanArrayPreprocess,
            emoticonYomiLBSPreprocess = emoticonYomiLBSBooleanArrayPreprocess,
            symbolYomiLBSPreprocess = symbolYomiLBSBooleanArrayPreprocess,
            readingCorrectionYomiLBSPreprocess = readingCorrectionYomiLBSBooleanArrayPreprocess,
            kotowazaYomiLBSPreprocess = kotowazaYomiLBSBooleanArrayPreprocess
        )

        return kanaKanjiEngine
    }

    @Singleton
    @Provides
    fun providePressedKeyStatus(): PressedKeyStatus = PressedKeyStatus()

    @Singleton
    @Provides
    fun providesInputManager(@ApplicationContext context: Context): InputMethodManager =
        context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

}
