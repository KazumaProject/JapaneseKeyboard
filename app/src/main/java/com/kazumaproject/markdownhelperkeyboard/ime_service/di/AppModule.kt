package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.inputmethod.InputMethodManager
import androidx.room.Room
import androidx.preference.PreferenceManager
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryDao
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTableReader
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterDataReader
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapDao
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_10_11
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_11_12
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_12_13
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_13_14
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_14_15
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_15_16
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_16_17
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_17_18
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_18_19
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_19_20
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_1_2
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_20_21
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_21_22
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_22_23
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_23_24
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_24_25
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_25_26
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_26_27
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_27_28
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_28_29
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_29_30
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_30_31
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_31_32
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_32_33
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_33_34
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_34_35
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_35_36
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_36_37
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_37_38
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_2_3
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_3_4
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_4_5
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_5_6
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_6_7
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_7_8
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_8_9
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase.Companion.MIGRATION_9_10
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTargetDao
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplateDao
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.PressedKeyStatus
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordDao
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.NgramRuleScorerManager
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.NgramRuleDao
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutDao
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideDao
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideDao
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryDao
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplateDao
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
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
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
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
    fun provideKeyboardLayoutDao(db: AppDatabase): KeyboardLayoutDao = db.keyboardLayoutDao()

    @Singleton
    @Provides
    fun providesUserTemplateDao(db: AppDatabase): UserTemplateDao = db.userTemplateDao()

    @Singleton
    @Provides
    fun providesClipBoardHistoryDao(db: AppDatabase): ClipboardHistoryDao = db.clipboardHistoryDao()

    @Singleton
    @Provides
    fun providesRomajiMapDao(db: AppDatabase): RomajiMapDao = db.romajiMapDao()

    @Singleton
    @Provides
    fun providesNgWordDao(db: AppDatabase): NgWordDao = db.ngWordDao()

    @Singleton
    @Provides
    fun providesShortCutDao(db: AppDatabase): ShortcutDao = db.shortcutDao()

    @Singleton
    @Provides
    fun providesSystemUserDictionaryDao(db: AppDatabase): SystemUserDictionaryDao =
        db.systemUserDictionaryDao()

    @Singleton
    @Provides
    fun providesNgramRuleDao(db: AppDatabase): NgramRuleDao = db.ngramRuleDao()

    @Singleton
    @Provides
    fun providesGemmaPromptTemplateDao(db: AppDatabase): GemmaPromptTemplateDao =
        db.gemmaPromptTemplateDao()

    @Singleton
    @Provides
    fun providesDeleteKeyFlickDeleteTargetDao(db: AppDatabase): DeleteKeyFlickDeleteTargetDao =
        db.deleteKeyFlickDeleteTargetDao()

    @Singleton
    @Provides
    fun providesPhysicalKeyboardShortcutDao(db: AppDatabase): PhysicalKeyboardShortcutDao =
        db.physicalKeyboardShortcutDao()

    @Singleton
    @Provides
    fun providesCandidateOrderOverrideDao(db: AppDatabase): CandidateOrderOverrideDao =
        db.candidateOrderOverrideDao()

    @Singleton
    @Provides
    fun providesSumireSpecialKeyActionOverrideDao(
        db: AppDatabase
    ): SumireSpecialKeyActionOverrideDao = db.sumireSpecialKeyActionOverrideDao()

    @Singleton
    @Provides
    fun providesSumireSpecialKeyPlacementOverrideDao(
        db: AppDatabase
    ): SumireSpecialKeyPlacementOverrideDao = db.sumireSpecialKeyPlacementOverrideDao()

    @Singleton
    @Provides
    fun providesCustomZeroQueryDao(db: AppDatabase): CustomZeroQueryDao =
        db.customZeroQueryDao()

    @Singleton
    @Provides
    fun provideActiveRomajiMapFlow(repository: RomajiMapRepository): Flow<Map<String, Pair<String, Int>>> {
        return repository.getActiveMap()
            .map { entity ->
                val mapData = entity?.mapData
                if (mapData.isNullOrEmpty()) repository.getDefaultMapData() else mapData
            }
    }

    @Singleton
    @Provides
    fun providesLearnMultiple(): LearnMultiple = LearnMultiple()

    @Singleton
    @Provides
    fun providesPreference(@ApplicationContext context: Context): AppPreference {
        return AppPreference.apply {
            init(context)
            migrateSumirePreferenceIfNeeded()
        }
    }

    @Singleton
    @Provides
    fun providesClipboardUtil(@ApplicationContext context: Context): ClipboardUtil =
        ClipboardUtil(context)

    @Singleton
    @Provides
    @ConnectionIds
    fun provideConnectionMatrix(reader: DictionaryBinaryReader): ConnectionMatrix.CostTable {
        return reader.loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)
    }

    @SystemTangoTrie
    @Singleton
    @Provides
    fun provideTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.SYSTEM_TANGO)
    }

    @SystemYomiTrie
    @Singleton
    @Provides
    fun provideYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.SYSTEM_YOMI)
    }

    @SystemTokenArray
    @Singleton
    @Provides
    fun providesTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.SYSTEM_TOKEN)
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
    fun provideSingleKanjiTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.SINGLE_KANJI_TANGO)
    }

    @SingleKanjiYomiTrie
    @Singleton
    @Provides
    fun provideSingleKanjiYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.SINGLE_KANJI_YOMI)
    }

    @SingleKanjiTokenArray
    @Singleton
    @Provides
    fun providesSingleKanjiTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.SINGLE_KANJI_TOKEN)
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
    fun provideEmojiTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.EMOJI_TANGO)
    }

    @EmojiYomiTrie
    @Singleton
    @Provides
    fun provideEmojiYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.EMOJI_YOMI)
    }

    @EmojiTokenArray
    @Singleton
    @Provides
    fun providesEmojiTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.EMOJI_TOKEN)
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
    fun provideEmoticonTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.EMOTICON_TANGO)
    }

    @EmoticonYomiTrie
    @Singleton
    @Provides
    fun provideEmoticonYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.EMOTICON_YOMI)
    }

    @EmoticonTokenArray
    @Singleton
    @Provides
    fun providesEmoticonTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.EMOTICON_TOKEN)
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
    fun provideSymbolTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.SYMBOL_TANGO)
    }

    @SymbolYomiTrie
    @Singleton
    @Provides
    fun provideSymbolYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.SYMBOL_YOMI)
    }

    @SymbolTokenArray
    @Singleton
    @Provides
    fun providesSymbolTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.SYMBOL_TOKEN)
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
    fun provideReadingCorrectionTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.READING_CORRECTION_TANGO)
    }

    @ReadingCorrectionYomiTrie
    @Singleton
    @Provides
    fun provideReadingCorrectionYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.READING_CORRECTION_YOMI)
    }

    @ReadingCorrectionTokenArray
    @Singleton
    @Provides
    fun providesReadingCorrectionTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.READING_CORRECTION_TOKEN)
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
    fun provideKotowazaTangoTrie(reader: DictionaryBinaryReader): LOUDS {
        return reader.loadLouds(DictionaryFileKey.KOTOWAZA_TANGO)
    }

    @KotowazaYomiTrie
    @Singleton
    @Provides
    fun provideKotowazaYomiTrie(reader: DictionaryBinaryReader): LOUDSWithTermId {
        return reader.loadLoudsWithTermId(DictionaryFileKey.KOTOWAZA_YOMI)
    }

    @KotowazaTokenArray
    @Singleton
    @Provides
    fun providesKotowazaTokenArray(reader: DictionaryBinaryReader): TokenArray {
        return reader.loadTokenArray(DictionaryFileKey.KOTOWAZA_TOKEN)
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
        @ConnectionIds connectionMatrix: ConnectionMatrix.CostTable,

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
        englishEngine: EnglishEngine,
        ngramRuleScorerManager: NgramRuleScorerManager,
        dictionaryBinaryReader: DictionaryBinaryReader,
        @ApplicationContext context: Context,
    ): KanaKanjiEngine {
        val kanaKanjiEngine = KanaKanjiEngine()
        val graphBuilder = GraphBuilder()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val systemNgramEnabled = preferences.getBoolean(
            "system_ngram_dictionary_enable_preference",
            true,
        )
        val customNgramEnabled = preferences.getBoolean(
            "custom_ngram_dictionary_enable_preference",
            true,
        )
        SystemNgramRuntime.initialize(context, systemNgramEnabled)
        ngramRuleScorerManager.setEnabled(customNgramEnabled)
        val findPath = FindPath(
            ngramRuleScorerProvider = ngramRuleScorerManager::currentScorer,
            systemNgramDictionaryProvider = SystemNgramRuntime::current,
        )
        val bundledMozcDictionaryActive =
            dictionaryBinaryReader.resolveCategoryLoadState(DictionaryCategory.SYSTEM) == DictionaryCategoryLoadState.Bundled
        val mozcSegmenter = if (bundledMozcDictionaryActive) {
            runCatching {
                context.assets.open("mozc/segmenter.dat").use { input ->
                    MozcSegmenter(MozcSegmenterDataReader().read(input))
                }
            }.onFailure {
                Timber.w(it, "Mozc segmenter asset is unavailable. Falling back to legacy conversion path.")
            }.getOrNull()
        } else {
            null
        }
        val mozcNodeAttributeTable = if (bundledMozcDictionaryActive) {
            runCatching {
                context.assets.open("mozc/node_attribute_by_lid.dat").use { input ->
                    MozcNodeAttributeTableReader().read(input)
                }
            }.onFailure {
                Timber.w(it, "Mozc node attribute asset is unavailable. Falling back to legacy conversion path.")
            }.getOrNull()
        } else {
            null
        }

        kanaKanjiEngine.buildEngine(
            graphBuilder = graphBuilder,
            findPath = findPath,
            connectionMatrix = connectionMatrix,

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
            engineEngine = englishEngine,
            mozcSegmenter = mozcSegmenter,
            mozcNodeAttributeTable = mozcNodeAttributeTable,
            mozcDictionaryActive = bundledMozcDictionaryActive &&
                mozcSegmenter != null &&
                mozcNodeAttributeTable != null,
        )
        kanaKanjiEngine.setDictionaryBinaryReader(dictionaryBinaryReader)

        return kanaKanjiEngine
    }

    @Singleton
    @Provides
    fun providePressedKeyStatus(): PressedKeyStatus = PressedKeyStatus()

    @Singleton
    @Provides
    fun providesInputManager(@ApplicationContext context: Context): InputMethodManager =
        context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    @Provides
    @Singleton
    @EnglishReadingLOUDS
    fun provideEnglishReadingLOUDS(reader: DictionaryBinaryReader): com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId {
        return reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
    }

    @EnglishWordLOUDS
    @Singleton
    @Provides
    fun provideEnglishWordLOUDS(reader: DictionaryBinaryReader): com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS {
        return reader.loadEnglishWord(DictionaryFileKey.ENGLISH_WORD)
    }

    @Provides
    @Singleton
    @EnglishTokenArray
    fun provideEnglishTokenArray(reader: DictionaryBinaryReader): com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray {
        return reader.loadEnglishToken(DictionaryFileKey.ENGLISH_TOKEN)
    }

    @Singleton
    @Provides
    fun providesEnglishEngine(
        @EnglishReadingLOUDS englishReadingLOUDS: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId,
        @EnglishWordLOUDS englishWordLOUDS: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS,
        @EnglishTokenArray englishTokenArray: com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray,
        @EnglishSuccinctBitVectorLBSReading englishSuccinctBitVectorLBSReading: SuccinctBitVector,
        @EnglishSuccinctBitVectorLBSWord englishSuccinctBitVectorLBSWord: SuccinctBitVector,
        @EnglishSuccinctBitVectorTokenArray englishSuccinctBitVectorLBSTokenArray: SuccinctBitVector,
        @EnglishSuccinctBitVectorReadingIsLeaf englishSuccinctBitVectorLBSReadingIsLeaf: SuccinctBitVector
    ): EnglishEngine {
        val englishEngine = EnglishEngine()
        englishEngine.buildEngine(
            englishReadingLOUDS = englishReadingLOUDS,
            englishWordLOUDS = englishWordLOUDS,
            englishTokenArray = englishTokenArray,
            englishSuccinctBitVectorLBSReading = englishSuccinctBitVectorLBSReading,
            englishSuccinctBitVectorLBSWord = englishSuccinctBitVectorLBSWord,
            englishSuccinctBitVectorTokenArray = englishSuccinctBitVectorLBSTokenArray,
            englishSuccinctBitVectorReadingIsLeaf = englishSuccinctBitVectorLBSReadingIsLeaf

        )
        return englishEngine
    }

    @Singleton
    @Provides
    @EnglishSuccinctBitVectorLBSReading
    fun provideEnglishSuccinctBitVectorLBS(@EnglishReadingLOUDS englishLOUDS: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId): SuccinctBitVector {
        return SuccinctBitVector(englishLOUDS.LBS)
    }

    @Singleton
    @Provides
    @EnglishSuccinctBitVectorReadingIsLeaf
    fun provideEnglishSuccinctBitVectorIsLeaf(@EnglishReadingLOUDS englishLOUDS: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId): SuccinctBitVector {
        return SuccinctBitVector(englishLOUDS.isLeaf)
    }

    @Singleton
    @Provides
    @EnglishSuccinctBitVectorLBSWord
    fun provideEnglishSuccinctBitVectorWord(@EnglishWordLOUDS englishLOUDS: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS): SuccinctBitVector {
        return SuccinctBitVector(englishLOUDS.LBS)
    }

    @Singleton
    @Provides
    @EnglishSuccinctBitVectorTokenArray
    fun provideEnglishSuccinctBitVectorTokenArray(@EnglishTokenArray englishTokenArray: com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray): SuccinctBitVector {
        Timber.d("provideEnglishSuccinctBitVectorTokenArray: ${englishTokenArray.bitvector.size()}")
        return SuccinctBitVector(englishTokenArray.bitvector)
    }

}
