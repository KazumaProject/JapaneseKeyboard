package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.inputmethod.InputMethodManager
import androidx.room.Room
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.english.EnglishLOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_1_2
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_2_3
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_3_4
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.PressedKeyStatus
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import com.kazumaproject.viterbi.FindPath
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream
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
            AppDatabase::class.java,
            "learn_database"
        )
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
        .build()

    @Singleton
    @Provides
    fun providesLearnDao(db: AppDatabase): LearnDao = db.learnDao()

    @Singleton
    @Provides
    fun provideClickedSymbolDao(db: AppDatabase): ClickedSymbolDao = db.clickedSymbolDao()

    @Singleton
    @Provides
    fun provideUserWordDao(db: AppDatabase): UserWordDao = db.userWordDao()

    @Singleton
    @Provides
    fun providesClipBoardUtil(@ApplicationContext context: Context): ClipboardUtil =
        ClipboardUtil(context)

    @Singleton
    @Provides
    fun providesLearnMultiple(): LearnMultiple = LearnMultiple()

    @Singleton
    @Provides
    fun providesPreference(@ApplicationContext context: Context): AppPreference {
        return AppPreference.apply {
            init(context)
        }
    }

    @Singleton
    @Provides
    fun providesRomajiKanaConverter(): RomajiKanaConverter {
        return RomajiKanaConverter()
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
    @SingleKanjiSuccinctBitVectorLBSYomi
    fun provideSingleKanjiSuccinctBitVectorLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @SingleKanjiSuccinctBitVectorIsLeafYomi
    fun provideSingleKanjiSuccinctBitVectorIsLeafYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @SingleKanjiSuccinctBitVectorTokenArray
    fun provideSingleKanjiSuccinctBitVectorTokenArray(@SingleKanjiTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @SingleKanjiSuccinctBitVectorTangoLBS
    fun provideSSingleKanjiSuccinctBitVectorTangoLBS(@SingleKanjiTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)

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
    @EmojiSuccinctBitVectorLBSYomi
    fun provideEmojiSuccinctBitVectorLBSYomi(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @EmojiSuccinctBitVectorIsLeafYomi
    fun provideEmojiSuccinctBitVectorIsLeafYomi(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @EmojiSuccinctBitVectorTokenArray
    fun provideEmojiSuccinctBitVectorTokenArray(@EmojiTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @EmojiSuccinctBitVectorTangoLBS
    fun provideEmojiSuccinctBitVectorTangoLBS(@EmojiTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)

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
    @EmoticonSuccinctBitVectorLBSYomi
    fun provideEmoticonSuccinctBitVectorLBSYomi(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @EmoticonSuccinctBitVectorIsLeafYomi
    fun provideEmoticonSuccinctBitVectorIsLeafYomi(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @EmoticonSuccinctBitVectorTokenArray
    fun provideEmoticonSuccinctBitVectorTokenArray(@EmoticonTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @EmoticonSuccinctBitVectorTangoLBS
    fun provideEmoticonSuccinctBitVectorTangoLBS(@EmoticonTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)

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
    @SymbolSuccinctBitVectorLBSYomi
    fun provideSymbolSuccinctBitVectorLBSYomi(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @SymbolSuccinctBitVectorIsLeafYomi
    fun provideSymbolSuccinctBitVectorIsLeafYomi(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @SymbolSuccinctBitVectorTokenArray
    fun provideSymbolSuccinctBitVectorTokenArray(@SymbolTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @SymbolSuccinctBitVectorTangoLBS
    fun provideSymbolSuccinctBitVectorTangoLBS(@SymbolTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)


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
    @ReadingCorrectionSuccinctBitVectorLBSYomi
    fun provideReadingCorrectionSuccinctBitVectorLBSYomi(@ReadingCorrectionYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @ReadingCorrectionSuccinctBitVectorIsLeafYomi
    fun provideReadingCorrectionSuccinctBitVectorIsLeafYomi(@ReadingCorrectionYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @ReadingCorrectionSuccinctBitVectorTokenArray
    fun provideReadingCorrectionSuccinctBitVectorTokenArray(@ReadingCorrectionTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @ReadingCorrectionSuccinctBitVectorTangoLBS
    fun provideReadingCorrectionSuccinctBitVectorTangoLBS(@ReadingCorrectionTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)

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
    @KotowazaSuccinctBitVectorLBSYomi
    fun provideKotowazaSuccinctBitVectorLBSYomi(@KotowazaYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.LBS)

    @Singleton
    @Provides
    @KotowazaSuccinctBitVectorIsLeafYomi
    fun provideKotowazaSuccinctBitVectorIsLeafYomi(@KotowazaYomiTrie yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        SuccinctBitVector(yomiTrie.isLeaf)

    @Singleton
    @Provides
    @KotowazaSuccinctBitVectorTokenArray
    fun provideKotowazaSuccinctBitVectorTokenArray(@KotowazaTokenArray tokenArray: TokenArray): SuccinctBitVector =
        SuccinctBitVector(tokenArray.bitvector)

    @Singleton
    @Provides
    @KotowazaSuccinctBitVectorTangoLBS
    fun provideKotowazaSuccinctBitVectorTangoLBS(@KotowazaTangoTrie tangoTrie: LOUDS): SuccinctBitVector =
        SuccinctBitVector(tangoTrie.LBS)

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

        @SingleKanjiTangoTrie singleKanjiTangoTrie: LOUDS,
        @SingleKanjiYomiTrie singleKanjiYomiTrie: LOUDSWithTermId,
        @SingleKanjiTokenArray singleKanjiTokenArray: TokenArray,
        @SingleKanjiSuccinctBitVectorLBSYomi singleKanjiSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @SingleKanjiSuccinctBitVectorIsLeafYomi singleKanjiSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @SingleKanjiSuccinctBitVectorTokenArray singleKanjiSuccinctBitVectorTokenArray: SuccinctBitVector,
        @SingleKanjiSuccinctBitVectorTangoLBS singleKanjiSuccinctBitVectorTangoLBS: SuccinctBitVector,

        @EmojiTangoTrie emojiTangoTrie: LOUDS,
        @EmojiYomiTrie emojiYomiTrie: LOUDSWithTermId,
        @EmojiTokenArray emojiTokenArray: TokenArray,
        @EmojiSuccinctBitVectorLBSYomi emojiSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @EmojiSuccinctBitVectorIsLeafYomi emojiSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @EmojiSuccinctBitVectorTokenArray emojiSuccinctBitVectorTokenArray: SuccinctBitVector,
        @EmojiSuccinctBitVectorTangoLBS emojiSuccinctBitVectorTangoLBS: SuccinctBitVector,

        @EmoticonTangoTrie emoticonTangoTrie: LOUDS,
        @EmoticonYomiTrie emoticonYomiTrie: LOUDSWithTermId,
        @EmoticonTokenArray emoticonTokenArray: TokenArray,
        @EmoticonSuccinctBitVectorLBSYomi emoticonSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @EmoticonSuccinctBitVectorIsLeafYomi emoticonSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @EmoticonSuccinctBitVectorTokenArray emoticonSuccinctBitVectorTokenArray: SuccinctBitVector,
        @EmoticonSuccinctBitVectorTangoLBS emoticonSuccinctBitVectorTangoLBS: SuccinctBitVector,

        @SymbolTangoTrie symbolTangoTrie: LOUDS,
        @SymbolYomiTrie symbolYomiTrie: LOUDSWithTermId,
        @SymbolTokenArray symbolTokenArray: TokenArray,
        @SymbolSuccinctBitVectorLBSYomi symbolSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @SymbolSuccinctBitVectorIsLeafYomi symbolSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @SymbolSuccinctBitVectorTokenArray symbolSuccinctBitVectorTokenArray: SuccinctBitVector,
        @SymbolSuccinctBitVectorTangoLBS symbolSuccinctBitVectorTangoLBS: SuccinctBitVector,

        @ReadingCorrectionTangoTrie readingCorrectionTangoTrie: LOUDS,
        @ReadingCorrectionYomiTrie readingCorrectionYomiTrie: LOUDSWithTermId,
        @ReadingCorrectionTokenArray readingCorrectionTokenArray: TokenArray,
        @ReadingCorrectionSuccinctBitVectorLBSYomi readingCorrectionSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @ReadingCorrectionSuccinctBitVectorIsLeafYomi readingCorrectionSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @ReadingCorrectionSuccinctBitVectorTokenArray readingCorrectionSuccinctBitVectorTokenArray: SuccinctBitVector,
        @ReadingCorrectionSuccinctBitVectorTangoLBS readingCorrectionSuccinctBitVectorTangoLBS: SuccinctBitVector,

        @KotowazaTangoTrie kotowazaTangoTrie: LOUDS,
        @KotowazaYomiTrie kotowazaYomiTrie: LOUDSWithTermId,
        @KotowazaTokenArray kotowazaTokenArray: TokenArray,
        @KotowazaSuccinctBitVectorLBSYomi kotowazaSuccinctBitVectorLBSYomi: SuccinctBitVector,
        @KotowazaSuccinctBitVectorIsLeafYomi kotowazaSuccinctBitVectorIsLeafYomi: SuccinctBitVector,
        @KotowazaSuccinctBitVectorTokenArray kotowazaSuccinctBitVectorTokenArray: SuccinctBitVector,
        @KotowazaSuccinctBitVectorTangoLBS kotowazaSuccinctBitVectorTangoLBS: SuccinctBitVector,
        englishEngine: EnglishEngine
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

            singleKanjiTangoTrie = singleKanjiTangoTrie,
            singleKanjiYomiTrie = singleKanjiYomiTrie,
            singleKanjiTokenArray = singleKanjiTokenArray,
            singleKanjiSuccinctBitVectorLBSYomi = singleKanjiSuccinctBitVectorLBSYomi,
            singleKanjiSuccinctBitVectorIsLeafYomi = singleKanjiSuccinctBitVectorIsLeafYomi,
            singleKanjiSuccinctBitVectorTokenArray = singleKanjiSuccinctBitVectorTokenArray,
            singleKanjiSuccinctBitVectorTangoLBS = singleKanjiSuccinctBitVectorTangoLBS,

            emojiTangoTrie = emojiTangoTrie,
            emojiYomiTrie = emojiYomiTrie,
            emojiTokenArray = emojiTokenArray,
            emojiSuccinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi,
            emojiSuccinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi,
            emojiSuccinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray,
            emojiSuccinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS,

            emoticonTangoTrie = emoticonTangoTrie,
            emoticonYomiTrie = emoticonYomiTrie,
            emoticonTokenArray = emoticonTokenArray,
            emoticonSuccinctBitVectorLBSYomi = emoticonSuccinctBitVectorLBSYomi,
            emoticonSuccinctBitVectorIsLeafYomi = emoticonSuccinctBitVectorIsLeafYomi,
            emoticonSuccinctBitVectorTokenArray = emoticonSuccinctBitVectorTokenArray,
            emoticonSuccinctBitVectorTangoLBS = emoticonSuccinctBitVectorTangoLBS,

            symbolTangoTrie = symbolTangoTrie,
            symbolYomiTrie = symbolYomiTrie,
            symbolTokenArray = symbolTokenArray,
            symbolSuccinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
            symbolSuccinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
            symbolSuccinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
            symbolSuccinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,

            readingCorrectionTangoTrie = readingCorrectionTangoTrie,
            readingCorrectionYomiTrie = readingCorrectionYomiTrie,
            readingCorrectionTokenArray = readingCorrectionTokenArray,
            readingCorrectionSuccinctBitVectorLBSYomi = readingCorrectionSuccinctBitVectorLBSYomi,
            readingCorrectionSuccinctBitVectorIsLeafYomi = readingCorrectionSuccinctBitVectorIsLeafYomi,
            readingCorrectionSuccinctBitVectorTokenArray = readingCorrectionSuccinctBitVectorTokenArray,
            readingCorrectionSuccinctBitVectorTangoLBS = readingCorrectionSuccinctBitVectorTangoLBS,

            kotowazaTangoTrie = kotowazaTangoTrie,
            kotowazaYomiTrie = kotowazaYomiTrie,
            kotowazaTokenArray = kotowazaTokenArray,
            kotowazaSuccinctBitVectorLBSYomi = kotowazaSuccinctBitVectorLBSYomi,
            kotowazaSuccinctBitVectorIsLeafYomi = kotowazaSuccinctBitVectorIsLeafYomi,
            kotowazaSuccinctBitVectorTokenArray = kotowazaSuccinctBitVectorTokenArray,
            kotowazaSuccinctBitVectorTangoLBS = kotowazaSuccinctBitVectorTangoLBS,

            englishEngine = englishEngine
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

    @EnglishDictionary
    @Singleton
    @Provides
    fun provideEnglishLOUDS(@ApplicationContext context: Context): EnglishLOUDS {
        val objectInputEnglish =
            ObjectInputStream(BufferedInputStream(context.assets.open("english/english.dat")))
        return EnglishLOUDS().readExternal(objectInputEnglish)
    }

    @Singleton
    @Provides
    fun providesEnglishEngine(
        @EnglishDictionary englishLOUDS: EnglishLOUDS,
        @EnglishSuccinctBitVectorLBS englishSuccinctBitVectorLBS: SuccinctBitVector,
        @EnglishSuccinctBitVectorIsLeaf englishSuccinctBitVectorIsLeaf: SuccinctBitVector
    ): EnglishEngine {
        val englishEngine = EnglishEngine()
        englishEngine.buildEngine(
            englishLOUDS,
            englishSuccinctBitVectorLBS,
            englishSuccinctBitVectorIsLeaf
        )
        return englishEngine
    }

    @Singleton
    @Provides
    @EnglishSuccinctBitVectorLBS
    fun provideEnglishSuccinctBitVectorLBS(@EnglishDictionary englishLOUDS: EnglishLOUDS): SuccinctBitVector {
        return SuccinctBitVector(englishLOUDS.LBS)
    }

    @Singleton
    @Provides
    @EnglishSuccinctBitVectorIsLeaf
    fun provideEnglishSuccinctBitVectorIsLeaf(@EnglishDictionary englishLOUDS: EnglishLOUDS): SuccinctBitVector {
        return SuccinctBitVector(englishLOUDS.isLeaf)
    }

}
