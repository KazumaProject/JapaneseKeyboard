package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank0GetIntArray
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.TenKeyMap
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.TenKeyMapHolder
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.viterbi.FindPath
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    @DefaultDispatcher
    @Provides
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @IoDispatcher
    @Provides
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @MainDispatcher
    @Provides
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @InputBackGroundDispatcher
    @Provides
    fun providesInputBackgroundDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @KeyInputDispatcher
    @Provides
    fun providesIKeyInputDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @SuggestionDispatcher
    @Provides
    fun providesSuggestionDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @CursorMoveDispatcher
    @Provides
    fun providesCursorMoveDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @DeleteLongDispatcher
    @Provides
    fun providesDeleteLongDispatcher(): CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @Singleton
    @Provides
    fun providesStringBuilder(): StringBuilder = StringBuilder()

    @Singleton
    @Provides
    fun providesSupervisorJob(): CompletableJob = SupervisorJob()

    @Singleton
    @Provides
    @Named("main_ime_scope")
    fun providesIMEScope(
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
        supervisorJob: CompletableJob
    ): CoroutineScope = CoroutineScope(supervisorJob + mainDispatcher)


    @Singleton
    @Provides
    fun providesTenKeyMap(): TenKeyMapHolder = TenKeyMap()

    @Singleton
    @Provides
    fun providesPreference(@ApplicationContext context: Context): AppPreference {
        return AppPreference.apply {
            init(context)
        }
    }

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

    @Singleton
    @Provides
    fun provideTangoTrie(@ApplicationContext context: Context): LOUDS{
        val objectInputTango = ObjectInputStream(BufferedInputStream(context.assets.open("tango.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @Singleton
    @Provides
    fun provideYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId{
        val objectInputYomi = ObjectInputStream(BufferedInputStream(context.assets.open("yomi.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @Singleton
    @Provides
    fun providesTokenArray(@ApplicationContext context: Context): TokenArray{
        val objectInputTokenArray = ObjectInputStream(BufferedInputStream(context.assets.open("token.dat")))
        val objectInputReadPOSTable = ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @Rank0ArrayLBSYomi
    fun provideRank0ArrayLBSYomi(yomiTrie: LOUDSWithTermId): IntArray = yomiTrie.LBS.rank0GetIntArray()

    @Singleton
    @Provides
    @Rank1ArrayLBSYomi
    fun provideRank1ArrayLBSYomi(yomiTrie: LOUDSWithTermId): IntArray = yomiTrie.LBS.rank1GetIntArray()

    @Singleton
    @Provides
    @Rank1ArrayIsLeafYomi
    fun provideRank1ArrayIsLeaf(yomiTrie: LOUDSWithTermId): IntArray = yomiTrie.isLeaf.rank1GetIntArray()

    @Singleton
    @Provides
    @Rank0ArrayTokenArrayBitvector
    fun provideRank0ArrayTokenArrayBitvector(tokenArray: TokenArray): IntArray = tokenArray.bitvector.rank0GetIntArray()

    @Singleton
    @Provides
    @Rank1ArrayTokenArrayBitvector
    fun provideRank1ArrayTokenArrayBitvector(tokenArray: TokenArray): IntArray = tokenArray.bitvector.rank1GetIntArray()

    @Singleton
    @Provides
    @Rank0ArrayTangoLBS
    fun provideRank0ArrayLBSTango(tangoTrie: LOUDS): IntArray = tangoTrie.LBS.rank0GetIntArray()

    @Singleton
    @Provides
    @Rank1ArrayTangoLBS
    fun provideRank1ArrayLBSTango(tangoTrie: LOUDS): IntArray = tangoTrie.LBS.rank1GetIntArray()

    @Singleton
    @Provides
    fun provideKanaKanjiHenkanEngine(
        graphBuilder: GraphBuilder,
        findPath: FindPath,
        @ConnectionIds connectionIds: ShortArray,
        tangoTrie: LOUDS,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        @Rank0ArrayLBSYomi rank0ArrayLBSYomi: IntArray,
        @Rank1ArrayLBSYomi rank1ArrayLBSYomi: IntArray,
        @Rank1ArrayIsLeafYomi rank1ArrayIsLeaf: IntArray,
        @Rank0ArrayTokenArrayBitvector rank0ArrayTokenArrayBitvector: IntArray,
        @Rank1ArrayTokenArrayBitvector rank1ArrayTokenArrayBitvector: IntArray,
        @Rank0ArrayTangoLBS rank0ArrayTangoLBS: IntArray,
        @Rank1ArrayTangoLBS rank1ArrayTangoLBS: IntArray
    ): KanaKanjiEngine {
        val kanaKanjiEngine = KanaKanjiEngine()

        kanaKanjiEngine.buildEngine(
            graphBuilder = graphBuilder,
            findPath = findPath,
            connectionIdList = connectionIds,
            tangoTrie = tangoTrie,
            yomiTrie = yomiTrie,
            tokenArray = tokenArray,
            rank0ArrayLBSYomi = rank0ArrayLBSYomi,
            rank1ArrayLBSYomi =rank1ArrayLBSYomi,
            rank1ArrayIsLeaf = rank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector = rank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector = rank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = rank0ArrayTangoLBS,
            rank1ArrayLBSTango = rank1ArrayTangoLBS
        )

        return kanaKanjiEngine
    }

    @Singleton
    @Provides
    fun providesInputManager(@ApplicationContext context: Context) : InputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    @DrawableReturn
    @Provides
    fun providesDrawableReturn(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.baseline_keyboard_return_24)!!

    @DrawableKanaSmall
    @Provides
    fun providesDrawableKanaSmall(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.kana_small)!!

    @DrawableEnglishSmall
    @Provides
    fun providesDrawableEnglishSmall(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.english_small)!!

    @DrawableHenkan
    @Provides
    fun providesDrawableHenkan(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.henkan)!!

    @DrawableSpaceBar
    @Provides
    fun providesDrawableSpaceBar(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.baseline_space_bar_24)!!

    @DrawableRightArrow
    @Provides
    fun providesDrawableRightArrow(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.baseline_arrow_right_alt_24)!!

    @DrawableLanguage
    @Provides
    fun providesDrawableLanguage(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.baseline_language_24)!!

    @DrawableNumberSmall
    @Provides
    fun providesDrawableNumberSmall(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.number_small)!!

    @DrawableOpenBracket
    @Provides
    fun providesDrawableOpenBracket(@ApplicationContext context: Context): Drawable = ContextCompat.getDrawable(context,
        com.kazumaproject.markdownhelperkeyboard.R.drawable.open_bracket)!!

    @SuppressLint("InflateParams")
    @PopUpTextActive
    @Provides
    fun providesPopUpWindowActive(@ApplicationContext context: Context): PopupWindow{
        val mPopupWindow = PopupWindow(context)
        val popupView = LayoutInflater
            .from(context)
            .inflate(R.layout.popup_layout,null)
        mPopupWindow.contentView = popupView
        return mPopupWindow
    }

    @SuppressLint("InflateParams")
    @PopUpWindowTop
    @Provides
    fun providesPopUpWindowTop(@ApplicationContext context: Context): PopupWindow{
        val mPopupWindow = PopupWindow(context)
        val popupView = LayoutInflater
            .from(context)
            .inflate(R.layout.popup_layout_top,null)
        mPopupWindow.contentView = popupView
        return mPopupWindow
    }
    @SuppressLint("InflateParams")
    @PopUpWindowLeft
    @Provides
    fun providesPopUpWindowLeft(@ApplicationContext context: Context): PopupWindow{
        val mPopupWindow = PopupWindow(context)
        val popupView = LayoutInflater
            .from(context)
            .inflate(R.layout.popup_window_left,null)
        mPopupWindow.contentView = popupView
        return mPopupWindow
    }

    @SuppressLint("InflateParams")
    @PopUpWindowBottom
    @Provides
    fun providesPopUpWindowBottom(@ApplicationContext context: Context): PopupWindow{
        val mPopupWindow = PopupWindow(context)
        val popupView = LayoutInflater
            .from(context)
            .inflate(R.layout.popup_layout_bottom,null)
        mPopupWindow.contentView = popupView
        return mPopupWindow
    }

    @SuppressLint("InflateParams")
    @PopUpWindowRight
    @Provides
    fun providesPopUpWindowRight(@ApplicationContext context: Context): PopupWindow{
        val mPopupWindow = PopupWindow(context)
        val popupView = LayoutInflater
            .from(context)
            .inflate(R.layout.popup_layout_right,null)
        mPopupWindow.contentView = popupView
        return mPopupWindow
    }

}