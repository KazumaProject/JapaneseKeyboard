package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.graphics.drawable.Drawable
import android.transition.Slide
import android.transition.Transition
import android.view.Gravity
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank0GetIntArray
import com.kazumaproject.bitset.rank0GetShortArray
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.PressedKeyStatus
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
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
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideSuggestionAdapter(): SuggestionAdapter = SuggestionAdapter()

    @DefaultDispatcher
    @Singleton
    @Provides
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @IoDispatcher
    @Singleton
    @Provides
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @MainDispatcher
    @Singleton
    @Provides
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @InputBackGroundDispatcher
    @Singleton
    @Provides
    fun providesInputBackgroundDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @SuggestionDispatcher
    @Singleton
    @Provides
    fun providesSuggestionDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

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
    fun extractedText(): ExtractedTextRequest = ExtractedTextRequest()

    @Singleton
    @Provides
    fun provideGraphBuilder(): GraphBuilder = GraphBuilder()

    @Singleton
    @Provides
    fun provideFindPath(): FindPath = FindPath()

    @Singleton
    @Provides
    @ConnectionIds
    fun provideConnectionIds(@ApplicationContext context: Context): ShortArray {
        val input = BufferedInputStream(context.assets.open("connectionId.dat"))
        return ConnectionIdBuilder().readShortArrayFromBytes(input)
    }

    @SystemTangoTrie
    @Singleton
    @Provides
    fun provideTangoTrie(@ApplicationContext context: Context): LOUDS{
        val objectInputTango = ObjectInputStream(BufferedInputStream(context.assets.open("system/tango.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SystemYomiTrie
    @Singleton
    @Provides
    fun provideYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId{
        val objectInputYomi = ObjectInputStream(BufferedInputStream(context.assets.open("system/yomi.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SystemTokenArray
    @Singleton
    @Provides
    fun providesTokenArray(@ApplicationContext context: Context): TokenArray{
        val objectInputTokenArray = ObjectInputStream(BufferedInputStream(context.assets.open("system/token.dat")))
        val objectInputReadPOSTable = ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SystemRank0ArrayLBSYomi
    fun provideRank0ArrayLBSYomi(@SystemYomiTrie yomiTrie: LOUDSWithTermId): IntArray = yomiTrie.LBS.rank0GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayLBSYomi
    fun provideRank1ArrayLBSYomi(@SystemYomiTrie yomiTrie: LOUDSWithTermId): IntArray = yomiTrie.LBS.rank1GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayIsLeafYomi
    fun provideRank1ArrayIsLeaf(@SystemYomiTrie yomiTrie: LOUDSWithTermId): IntArray = yomiTrie.isLeaf.rank1GetIntArray()

    @Singleton
    @Provides
    @SystemYomiLBSBooleanArray
    fun providesYomiLBSBooleanArray(@SystemYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray = yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SystemRank0ArrayTokenArrayBitvector
    fun provideRank0ArrayTokenArrayBitvector(@SystemTokenArray tokenArray: TokenArray): IntArray = tokenArray.bitvector.rank0GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayTokenArrayBitvector
    fun provideRank1ArrayTokenArrayBitvector(@SystemTokenArray tokenArray: TokenArray): IntArray = tokenArray.bitvector.rank1GetIntArray()

    @Singleton
    @Provides
    @SystemRank0ArrayTangoLBS
    fun provideRank0ArrayLBSTango(@SystemTangoTrie tangoTrie: LOUDS): IntArray = tangoTrie.LBS.rank0GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayTangoLBS
    fun provideRank1ArrayLBSTango(@SystemTangoTrie tangoTrie: LOUDS): IntArray = tangoTrie.LBS.rank1GetIntArray()


    @SingleKanjiTangoTrie
    @Singleton
    @Provides
    fun provideSingleKanjiTangoTrie(@ApplicationContext context: Context): LOUDS{
        val objectInputTango = ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/tango_singleKanji.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SingleKanjiYomiTrie
    @Singleton
    @Provides
    fun provideSingleKanjiYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId{
        val objectInputYomi = ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/yomi_singleKanji.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SingleKanjiTokenArray
    @Singleton
    @Provides
    fun providesSingleKanjiTokenArray(@ApplicationContext context: Context): TokenArray{
        val objectInputTokenArray = ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/token_singleKanji.dat")))
        val objectInputReadPOSTable = ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayLBSYomi
    fun provideSingleKanjiRank0ArrayLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray = yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayLBSYomi
    fun provideSingleKanjiRank1ArrayLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray = yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayIsLeafYomi
    fun provideSingleKanjiRank1ArrayIsLeaf(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray = yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiYomiLBSBooleanArray
    fun providesSingleKanjiYomiLBSBooleanArray(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray = yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayTokenArrayBitvector
    fun provideSingleKanjiRank0ArrayTokenArrayBitvector(@SingleKanjiTokenArray tokenArray: TokenArray): ShortArray = tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayTokenArrayBitvector
    fun provideSingleKanjiRank1ArrayTokenArrayBitvector(@SingleKanjiTokenArray tokenArray: TokenArray): ShortArray = tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayTangoLBS
    fun provideSingleKanjiRank0ArrayLBSTango(@SingleKanjiTangoTrie tangoTrie: LOUDS): ShortArray = tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayTangoLBS
    fun provideSingleKanjiRank1ArrayLBSTango(@SingleKanjiTangoTrie tangoTrie: LOUDS): ShortArray = tangoTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    fun provideKanaKanjiHenkanEngine(
        graphBuilder: GraphBuilder,
        findPath: FindPath,
        @ConnectionIds connectionIds: ShortArray,
        @SystemTangoTrie systemTangoTrie: LOUDS,
        @SystemYomiTrie systemYomiTrie: LOUDSWithTermId,
        @SystemTokenArray systemTokenArray: TokenArray,
        @SystemRank0ArrayLBSYomi systemRank0ArrayLBSYomi: IntArray,
        @SystemRank1ArrayLBSYomi systemRank1ArrayLBSYomi: IntArray,
        @SystemRank1ArrayIsLeafYomi systemRank1ArrayIsLeaf: IntArray,
        @SystemRank0ArrayTokenArrayBitvector systemRank0ArrayTokenArrayBitvector: IntArray,
        @SystemRank1ArrayTokenArrayBitvector systemRank1ArrayTokenArrayBitvector: IntArray,
        @SystemRank0ArrayTangoLBS systemRank0ArrayTangoLBS: IntArray,
        @SystemRank1ArrayTangoLBS systemRank1ArrayTangoLBS: IntArray,
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
    ): KanaKanjiEngine {
        val kanaKanjiEngine = KanaKanjiEngine()

        kanaKanjiEngine.buildEngine(
            graphBuilder = graphBuilder,
            findPath = findPath,
            connectionIdList = connectionIds,
            systemTangoTrie = systemTangoTrie,
            systemYomiTrie = systemYomiTrie,
            systemTokenArray = systemTokenArray,
            systemRank0ArrayLBSYomi = systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi =systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf = systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector = systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector = systemRank1ArrayTokenArrayBitvector,
            systemRank0ArrayLBSTango = systemRank0ArrayTangoLBS,
            systemRank1ArrayLBSTango = systemRank1ArrayTangoLBS,
            systemYomiLBSBooleanArray = systemYomiLBSBooleanArray,
            singleKanjiTangoTrie = singleKanjiTangoTrie,
            singleKanjiYomiTrie = singleKanjiYomiTrie,
            singleKanjiTokenArray = singleKanjiTokenArray,
            singleKanjiRank0ArrayLBSYomi = singleKanjiRank0ArrayLBSYomi,
            singleKanjiRank1ArrayLBSYomi =singleKanjiRank1ArrayLBSYomi,
            singleKanjiRank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf,
            singleKanjiRank0ArrayTokenArrayBitvector = singleKanjiRank0ArrayTokenArrayBitvector,
            singleKanjiRank1ArrayTokenArrayBitvector = singleKanjiRank1ArrayTokenArrayBitvector,
            singleKanjiRank0ArrayLBSTango = singleKanjiRank0ArrayTangoLBS,
            singleKanjiRank1ArrayLBSTango = singleKanjiRank1ArrayTangoLBS,
            singleKanjiYomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray,
        )

        return kanaKanjiEngine
    }

    @Singleton
    @Provides
    fun providePressedKeyStatus(): PressedKeyStatus = PressedKeyStatus()

    @Singleton
    @Provides
    fun providesInputManager(@ApplicationContext context: Context) : InputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    @DrawableReturn
    @Provides
    fun providesDrawableReturn(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.baseline_keyboard_return_24)!!

    @DrawableKanaSmall
    @Provides
    fun providesDrawableKanaSmall(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.kana_small)!!

    @DrawableEnglishSmall
    @Provides
    fun providesDrawableEnglishSmall(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.english_small)!!

    @DrawableHenkan
    @Provides
    fun providesDrawableHenkan(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.henkan)!!

    @DrawableSpaceBar
    @Provides
    fun providesDrawableSpaceBar(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.baseline_space_bar_24)!!

    @DrawableRightArrow
    @Provides
    fun providesDrawableRightArrow(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.baseline_arrow_right_alt_24)!!

    @DrawableLanguage
    @Provides
    fun providesDrawableLanguage(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.logo_key)!!

    @DrawableNumberSmall
    @Provides
    fun providesDrawableNumberSmall(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.number_small)!!

    @DrawableOpenBracket
    @Provides
    fun providesDrawableOpenBracket(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        R.drawable.open_bracket)!!

}