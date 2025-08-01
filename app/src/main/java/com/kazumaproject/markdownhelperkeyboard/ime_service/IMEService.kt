package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.hardware.input.InputManager
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.physical_shift_key.PhysicalShiftKeyCodeMap
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.core.domain.window.getScreenHeight
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.listeners.ClipboardHistoryToggleListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.toHistoryItem
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getLastCharacterAsString
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isOnlyTwoCharBracketPair
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.FloatingDockListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.FloatingDockView
import com.kazumaproject.markdownhelperkeyboard.ime_service.listener.SwipeGestureListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.repository.ClickedSymbolRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ClipboardHistoryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NgWordRepository
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.extensions.getDakutenFlickLeft
import com.kazumaproject.tenkey.extensions.getDakutenFlickRight
import com.kazumaproject.tenkey.extensions.getDakutenFlickTop
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import com.kazumaproject.tenkey.extensions.getNextInputChar
import com.kazumaproject.tenkey.extensions.getNextReturnInputChar
import com.kazumaproject.tenkey.extensions.isHiragana
import com.kazumaproject.tenkey.extensions.isLatinAlphabet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.BreakIterator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class IMEService : InputMethodService(), LifecycleOwner, InputConnection,
    ClipboardHistoryToggleListener, InputManager.InputDeviceListener {

    @Inject
    lateinit var learnMultiple: LearnMultiple

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var inputMethodManager: InputMethodManager

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var englishEngine: EnglishEngine

    @Inject
    lateinit var learnRepository: LearnRepository

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var userTemplateRepository: UserTemplateRepository

    @Inject
    lateinit var clickedSymbolRepository: ClickedSymbolRepository

    @Inject
    lateinit var clipboardHistoryRepository: ClipboardHistoryRepository

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    @Inject
    lateinit var ngWordRepository: NgWordRepository

    @Inject
    lateinit var clipboardUtil: ClipboardUtil

    private var romajiConverter: RomajiKanaConverter? = null

    private lateinit var clipboardManager: ClipboardManager

    private var isClipboardHistoryFeatureEnabled: Boolean = false
    private val clipboardMutex = Mutex()
    private var isCustomKeyboardTwoWordsOutputEnable: Boolean? = false

    private var floatingCandidateWindow: PopupWindow? = null
    private lateinit var floatingCandidateView: View
    private lateinit var listAdapter: FloatingCandidateListAdapter

    private var floatingDockWindow: PopupWindow? = null
    private lateinit var floatingDockView: FloatingDockView

    /**
     * クリップボードの内容が変更されたときに呼び出されるリスナー。
     */
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        ioScope.launch {
            // ▼▼▼ Mutexで処理ブロックをロックする ▼▼▼
            clipboardMutex.withLock {
                // 1. 現在クリップボードにあるアイテムを取得
                val newItem = clipboardUtil.getPrimaryClipContent()
                val newHistoryItem = newItem.toHistoryItem() ?: return@withLock

                // 2. DBに保存されている最新のアイテムを取得
                val lastSavedItem = clipboardHistoryRepository.getLatestItem()

                // 3. 最新アイテムと比較して、内容が重複していないかチェック
                val isDuplicate = if (lastSavedItem == null) {
                    false
                } else {
                    if (newHistoryItem.itemType == lastSavedItem.itemType) {
                        when (newHistoryItem.itemType) {
                            ItemType.TEXT -> newHistoryItem.textData == lastSavedItem.textData
                            ItemType.IMAGE -> newHistoryItem.imageData?.sameAs(lastSavedItem.imageData)
                                ?: false
                        }
                    } else {
                        false
                    }
                }

                // 4. 重複していなければ、DBに挿入する
                if (!isDuplicate) {
                    Timber.d("LOCKED: New clipboard item detected. Inserting to history.")
                    if (isClipboardHistoryFeatureEnabled) {
                        clipboardHistoryRepository.insert(newHistoryItem)
                    }
                } else {
                    Timber.d("LOCKED: Clipboard item is a duplicate. Skipping insert.")
                }
            }
        }
    }

    private var suggestionAdapter: SuggestionAdapter? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var cachedEmoji: List<Emoji>? = null
    private var cachedEmoticons: List<Emoticon>? = null
    private var cachedSymbols: List<Symbol>? = null
    private var cachedClickedSymbolHistory: List<ClickedSymbol>? = null
    private var currentClipboardItems: List<ClipboardItem> = emptyList()

    private var deleteLongPressJob: Job? = null
    private var rightLongPressJob: Job? = null
    private var leftLongPressJob: Job? = null

    private var mainLayoutBinding: MainLayoutBinding? = null
    private val _inputString = MutableStateFlow("")
    private val inputString = _inputString.asStateFlow()
    private var stringInTail = AtomicReference("")
    private val _dakutenPressed = MutableStateFlow(false)
    private val _suggestionFlag = MutableSharedFlow<CandidateShowFlag>(replay = 0)
    private val suggestionFlag = _suggestionFlag.asSharedFlow()
    private val _suggestionViewStatus = MutableStateFlow(true)
    private val suggestionViewStatus = _suggestionViewStatus.asStateFlow()
    private val _keyboardSymbolViewState = MutableStateFlow(false)
    private val keyboardSymbolViewState: StateFlow<Boolean> = _keyboardSymbolViewState.asStateFlow()
    private val _tenKeyQWERTYMode = MutableStateFlow<TenKeyQWERTYMode>(TenKeyQWERTYMode.Default)
    private val qwertyMode = _tenKeyQWERTYMode.asStateFlow()
    private val _physicalKeyboardEnable = MutableSharedFlow<Boolean>(replay = 1)
    private val physicalKeyboardEnable: SharedFlow<Boolean> = _physicalKeyboardEnable

    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private val lastFlickConvertedNextHiragana = AtomicBoolean(false)
    private val isContinuousTapInputEnabled = AtomicBoolean(false)
    private val englishSpaceKeyPressed = AtomicBoolean(false)
    private var suggestionClickNum = 0
    private val isHenkan = AtomicBoolean(false)
    private val onLeftKeyLongPressUp = AtomicBoolean(false)
    private val onRightKeyLongPressUp = AtomicBoolean(false)
    private val onDeleteLongPressUp = AtomicBoolean(false)
    private var onKeyboardSwitchLongPressUp = false
    private val deleteKeyLongKeyPressed = AtomicBoolean(false)
    private val rightCursorKeyLongKeyPressed = AtomicBoolean(false)
    private val leftCursorKeyLongKeyPressed = AtomicBoolean(false)
    private var isFlickOnlyMode: Boolean? = false
    private var isOmissionSearchEnable: Boolean? = false
    private var delayTime: Int? = 1000
    private var isLearnDictionaryMode: Boolean? = false
    private var isUserDictionaryEnable: Boolean? = false
    private var isUserTemplateEnable: Boolean? = false
    private var hankakuPreference: Boolean? = false
    private var isLiveConversionEnable: Boolean? = false
    private var nBest: Int? = 4
    private var flickSensitivityPreferenceValue: Int? = 100
    private var isVibration: Boolean? = true
    private var vibrationTimingStr: String? = "both"
    private var mozcUTPersonName: Boolean? = false
    private var mozcUTPlaces: Boolean? = false
    private var mozcUTWiki: Boolean? = false
    private var mozcUTNeologd: Boolean? = false
    private var mozcUTWeb: Boolean? = false
    private var sumireInputKeyType: String? = "flick-default"
    private var symbolKeyboardFirstItem: SymbolMode? = SymbolMode.EMOJI
    private var userDictionaryPrefixMatchNumber: Int? = 2
    private var isTablet: Boolean? = false
    private var isNgWordEnable: Boolean? = false
    private val _ngWordsList = MutableStateFlow<List<NgWord>>(emptyList())
    private val ngWordsList: StateFlow<List<NgWord>> = _ngWordsList
    private val _ngPattern = MutableStateFlow("".toRegex())
    private val ngPattern: StateFlow<Regex> = _ngPattern
    private var isPrivateMode = false

    private var keyboardContainer: FrameLayout? = null

    private var isSpaceKeyLongPressed = false
    private val _selectMode = MutableStateFlow(false)
    private val selectMode: StateFlow<Boolean> = _selectMode

    private val _cursorMoveMode = MutableStateFlow(false)
    private val cursorMoveMode: StateFlow<Boolean> = _cursorMoveMode
    private var hasConvertedKatakana = false

    private val deletedBuffer = StringBuilder()

    private var keyboardOrder: List<KeyboardType> = emptyList()

    private var customLayouts: List<CustomKeyboardLayout> = emptyList()

    private var currentNightMode: Int = 0

    private var suggestionCache: MutableMap<String, List<Candidate>>? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var inputManager: InputManager

    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
    }
    private val cachedLogoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.language_24dp
        )
    }
    private val cachedKanaDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.kana_small)
    }
    private val cachedHenkanDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.henkan)
    }

    private val cachedNumberDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.number_small
        )
    }

    private val cachedArrowDropDownDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24
        )
    }

    private val cachedArrowDropUpDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24
        )
    }

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        )
    }

    private val cachedReturnDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        )
    }

    private val cachedTabDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.keyboard_tab_24px
        )
    }

    private val cachedCheckDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_check_24
        )
    }

    private val cachedSearchDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_search_24
        )
    }

    private val cachedEnglishDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.english_small
        )
    }

    companion object {
        private const val LONG_DELAY_TIME = 64L
        private const val DEFAULT_DELAY_MS = 1000L
        private const val PAGE_SIZE: Int = 5
    }

    private var currentPage: Int = 0
    private var currentHighlightIndex: Int = RecyclerView.NO_POSITION
    private var fullSuggestionsList: List<CandidateItem> = emptyList()

    private var initialCursorDetectInFloatingCandidateView = false
    private var initialCursorXPosition: Int = 0

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        suggestionAdapter = SuggestionAdapter().apply {
            onListUpdated = {
                mainLayoutBinding?.apply {
                    suggestionRecyclerView.scrollToPosition(0)
                }
            }
        }
        currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        clipboardManager =
            applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        isClipboardHistoryFeatureEnabled = appPreference.clipboard_history_enable ?: false

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)
        floatingCandidateView = layoutInflater.inflate(R.layout.floating_candidate_layout, null)
        listAdapter = FloatingCandidateListAdapter(
            pageSize = PAGE_SIZE,
        )
        listAdapter.onSuggestionClicked = { suggestion: CandidateItem ->
            commitText(suggestion.word, 1)
            finishComposingText()
        }
        listAdapter.onPagerClicked = {
            goToNextPageForFloatingCandidate()
        }
    }

    override fun onCreateInputView(): View? {
        Timber.d("onCreateInputView")
        // もしコンテナがすでに存在している場合、システムが再追加できるように
        // 古い親から切り離す。
        keyboardContainer?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        // もしコンテナがまだ一度も作成されていない場合（初回起動時）のみ、
        // 作成とセットアップを行う。
        if (keyboardContainer == null) {
            isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
            keyboardContainer = FrameLayout(this)

            // コンテナの内部にキーボードのUIをセットアップする
            setupKeyboardView()
            // 初回のみ実行したい他のセットアップ処理

            mainLayoutBinding?.let { mainView ->
                if (lifecycle.currentState == Lifecycle.State.CREATED) {
                    startScope(mainView)
                } else {
                    scope.coroutineContext.cancelChildren()
                    startScope(mainView)
                }
            }
        } else {
            setupKeyboardView()
            scope.coroutineContext.cancelChildren()
            mainLayoutBinding?.let { mainView ->
                startScope(mainView)
            }
        }
        return keyboardContainer
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Timber.d("onUpdate onStartInput called $restarting ${attribute?.imeOptions}")
        resetAllFlags()
        if (suggestionCache == null) {
            suggestionCache = mutableMapOf()
        }
        _suggestionViewStatus.update { true }
        appPreference.apply {
            keyboardOrder = keyboard_order
            mozcUTPersonName = mozc_ut_person_names_preference ?: false
            mozcUTPlaces = mozc_ut_places_preference ?: false
            mozcUTWiki = mozc_ut_wiki_preference ?: false
            mozcUTNeologd = mozc_ut_neologd_preference ?: false
            mozcUTWeb = mozc_ut_web_preference ?: false
            isFlickOnlyMode = flick_input_only_preference ?: false
            isOmissionSearchEnable = omission_search_preference ?: false
            delayTime = time_same_pronounce_typing_preference ?: 1000
            isLearnDictionaryMode = learn_dictionary_preference ?: true
            isUserDictionaryEnable = user_dictionary_preference ?: true
            isUserTemplateEnable = user_template_preference ?: true
            hankakuPreference = space_hankaku_preference ?: false
            isLiveConversionEnable = live_conversion_preference ?: false
            nBest = n_best_preference ?: 4
            flickSensitivityPreferenceValue = flick_sensitivity_preference ?: 100
            isNgWordEnable = ng_word_preference ?: true
            userDictionaryPrefixMatchNumber = user_dictionary_prefix_match_number_preference ?: 2
            isVibration = vibration_preference ?: true
            vibrationTimingStr = vibration_timing_preference ?: "both"
            sumireInputKeyType = sumire_input_selection_preference ?: "flick-default"
            symbolKeyboardFirstItem = symbol_mode_preference
            isCustomKeyboardTwoWordsOutputEnable = custom_keyboard_two_words_output ?: true
            if (mozcUTPersonName == true) {
                if (!kanaKanjiEngine.isMozcUTPersonDictionariesInitialized()) {
                    kanaKanjiEngine.buildPersonNamesDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTPlaces == true) {
                if (!kanaKanjiEngine.isMozcUTPlacesDictionariesInitialized()) {
                    kanaKanjiEngine.buildPlaceDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTWiki == true) {
                if (!kanaKanjiEngine.isMozcUTWikiDictionariesInitialized()) {
                    kanaKanjiEngine.buildWikiDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTNeologd == true) {
                if (!kanaKanjiEngine.isMozcUTNeologdDictionariesInitialized()) {
                    kanaKanjiEngine.buildNeologdDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTWeb == true) {
                if (!kanaKanjiEngine.isMozcUTWebDictionariesInitialized()) {
                    kanaKanjiEngine.buildWebDictionary(
                        applicationContext
                    )
                }
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        val hasPhysicalKeyboard = inputManager.inputDeviceIds.any { deviceId ->
            isDevicePhysicalKeyboard(inputManager.getInputDevice(deviceId))
        }
        Timber.d("onUpdate onStartInputView called $isPrivateMode $hasPhysicalKeyboard")
        setCurrentInputType(editorInfo)
        updateClipboardPreview()
        if (!hasPhysicalKeyboard) {
            setKeyboardSize()
        } else {
            checkForPhysicalKeyboard(true)
        }
        resetKeyboard()
        keyboardSelectionPopupWindow?.dismiss()
        mainLayoutBinding?.let { mainView ->
            mainView.apply {
                keyboardView.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                tabletView.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                customLayoutDefault.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
            }
        }
        editorInfo?.let { info ->
            if (info.imeOptions == 318767106) {
                isPrivateMode = true
                suggestionAdapter?.setIncognitoIcon(
                    ContextCompat.getDrawable(this, com.kazumaproject.core.R.drawable.incognito)
                )
            } else {
                isPrivateMode = false
                suggestionAdapter?.setIncognitoIcon(null)
            }
        }
        if (hasPhysicalKeyboard) {
            val popupContentView = layoutInflater.inflate(R.layout.floating_candidate_layout, null)
            val recyclerView =
                popupContentView.findViewById<RecyclerView>(R.id.floating_candidate_recycler_view)
            recyclerView.adapter = listAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
            floatingCandidateWindow = PopupWindow(
                popupContentView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                isOutsideTouchable = false
            }

            floatingDockWindow = PopupWindow(
                floatingDockView,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            floatingDockView.setText("あ")
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("onUpdate onFinishInput Called")
        resetAllFlags()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Timber.d("onUpdate onFinishInputView")
        if (isTablet == true) {
            mainLayoutBinding?.tabletView?.isVisible = true
            mainLayoutBinding?.keyboardView?.isVisible = false
        } else {
            if (qwertyMode.value == TenKeyQWERTYMode.Default) {
                mainLayoutBinding?.apply {
                    qwertyView.isVisible = false
                    keyboardView.isVisible = true
                    tabletView.isVisible = false
                }
            } else {
                mainLayoutBinding?.apply {
                    qwertyView.isVisible = true
                    keyboardView.isVisible = false
                    tabletView.isVisible = false
                }
            }
        }
        floatingCandidateWindow?.dismiss()
        floatingDockWindow?.dismiss()
        mainLayoutBinding?.suggestionRecyclerView?.isVisible = true
    }

    override fun onDestroy() {
        Timber.d("onUpdate onDestroy")
        super.onDestroy()
        suggestionAdapter?.release()
        suggestionAdapter = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        suggestionCache = null
        clearSymbols()
        floatingCandidateWindow = null
        floatingDockWindow = null
        keyboardSelectionPopupWindow = null
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        if (mozcUTPersonName == true) kanaKanjiEngine.releasePersonNamesDictionary()
        if (mozcUTPlaces == true) kanaKanjiEngine.releasePlacesDictionary()
        if (mozcUTWiki == true) kanaKanjiEngine.releaseWikiDictionary()
        if (mozcUTNeologd == true) kanaKanjiEngine.releaseNeologdDictionary()
        if (mozcUTWeb == true) kanaKanjiEngine.releaseWebDictionary()
        isFlickOnlyMode = null
        isOmissionSearchEnable = null
        delayTime = null
        isLearnDictionaryMode = null
        isUserDictionaryEnable = null
        isUserTemplateEnable = null
        hankakuPreference = null
        isLiveConversionEnable = null
        nBest = null
        flickSensitivityPreferenceValue = null
        isVibration = null
        vibrationTimingStr = null
        mozcUTPersonName = null
        romajiConverter = null
        mozcUTPlaces = null
        mozcUTWiki = null
        mozcUTNeologd = null
        mozcUTWeb = null
        sumireInputKeyType = null
        isTablet = null
        isNgWordEnable = null
        symbolKeyboardFirstItem = null
        userDictionaryPrefixMatchNumber = null
        isCustomKeyboardTwoWordsOutputEnable = null
        inputManager.unregisterInputDeviceListener(this)
        actionInDestroy()
        System.gc()
        dismissFloatingDock()
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            val inputHeight = window.window?.decorView?.height ?: 0
            outInsets?.contentTopInsets = inputHeight
            outInsets?.visibleTopInsets = inputHeight
            outInsets?.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (listAdapter.currentList.isEmpty()) {
            floatingCandidateWindow?.dismiss()
            return
        }

        if (cursorAnchorInfo == null || floatingCandidateWindow == null) {
            return
        }
        Timber.d("onUpdateCursorAnchorInfo: baseLine:${cursorAnchorInfo.insertionMarkerBaseline}")
        Timber.d("onUpdateCursorAnchorInfo: bottom:${cursorAnchorInfo.insertionMarkerBottom}")
        Timber.d("onUpdateCursorAnchorInfo: top:${cursorAnchorInfo.insertionMarkerTop}")
        Timber.d("onUpdateCursorAnchorInfo: horizontal:${cursorAnchorInfo.insertionMarkerHorizontal}")
        val matrix: Matrix = cursorAnchorInfo.matrix
        // カーソルのローカル座標を取得
        val cursorX = cursorAnchorInfo.insertionMarkerHorizontal
        val cursorY = cursorAnchorInfo.insertionMarkerTop
        // スクリーン座標に変換するための配列
        val screenCoords = floatArrayOf(cursorX, cursorY)
        // 行列を適用してスクリーン座標に変換
        matrix.mapPoints(screenCoords)
        val screenX = screenCoords[0]
        val screenY = screenCoords[1]
        Timber.d("onUpdateCursorAnchorInfo X: $screenX")
        Timber.d("onUpdateCursorAnchorInfo Y: $screenY")

        val x = if (initialCursorDetectInFloatingCandidateView) {
            initialCursorXPosition
        } else {
            (screenX - 32).coerceAtLeast(0f).toInt()
        }
        val y = screenY.toInt()
        initialCursorXPosition = x
        initialCursorDetectInFloatingCandidateView = true
        val currentPopupWindow = floatingCandidateWindow!!
        if (currentPopupWindow.isShowing) {
            // すでに表示されている場合は位置を更新
            currentPopupWindow.update(x, y, -1, -1)
        } else {
            // 表示されていない場合は指定した位置に表示
            currentPopupWindow.showAtLocation(
                window.window?.decorView, // 親ビュー
                Gravity.NO_GRAVITY,      // スクリーン座標で直接指定
                x,                       // x座標
                y                        // y座標
            )
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
        _keyboardSymbolViewState.update { false }
        _selectMode.update { false }
        _cursorMoveMode.update { false }
        keyboardSelectionPopupWindow?.dismiss()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                finishComposingText()
                setComposingText("", 0)
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                finishComposingText()
                setComposingText("", 0)
            }

            Configuration.ORIENTATION_UNDEFINED -> {
                finishComposingText()
                setComposingText("", 0)
            }

            else -> {
                finishComposingText()
                setComposingText("", 0)
            }
        }

        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

        if (newNightMode != currentNightMode) {
            setupKeyboardView()
            currentNightMode = newNightMode
        }

    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    /**
     * FloatingDockViewを非表示にします。
     */
    private fun dismissFloatingDock() {
        if (floatingDockWindow?.isShowing == true) {
            floatingDockWindow?.dismiss()
            floatingDockWindow = null
        }
    }

    private fun setupKeyboardView() {
        Timber.d("setupKeyboardView: Called")
        // Determine the correct, themed context
        val isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        val ctx = if (isDynamicColorsEnable) {
            DynamicColors.wrapContextIfAvailable(
                this, // Use 'this' (the service context), not applicationContext
                R.style.Theme_MarkdownKeyboard
            )
        } else {
            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
        }

        floatingDockView = FloatingDockView(ctx).apply {
            setText("あ")
            setOnFloatingDockListener(object : FloatingDockListener {
                override fun onDockClick() {
                    Timber.d("setOnFloatingDockListener: Dockがクリックされました")
                }

                override fun onIconClick() {
                    Timber.d("setOnFloatingDockListener: Iconがクリックされました")
                    scope.launch {
                        _physicalKeyboardEnable.emit(false)
                    }
                    floatingDockWindow?.dismiss()
                }
            })
        }

        // Inflate the new layout
        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))

        // Ensure the container is not null and add the new view to it
        keyboardContainer?.let { container ->
            container.removeAllViews() // Remove the old keyboard view
            mainLayoutBinding?.root?.let { newRootView ->
                container.addView(newRootView) // Add the newly inflated view

                // All your listener and setup code goes here
                mainLayoutBinding?.let { mainView ->
                    if (isDynamicColorsEnable) {
                        mainView.apply {
                            root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                            suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                            suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                        }
                    }
                    setupCustomKeyboardListeners(mainView)
                    setSuggestionRecyclerView(
                        mainView,
                        FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.COLUMN
                        },
                        FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.ROW
                            justifyContent = JustifyContent.FLEX_START
                        })
                    setSymbolKeyboard(mainView)
                    setQWERTYKeyboard(mainView)
                    if (isTablet == true) {
                        setTabletKeyListeners(mainView)
                    } else {
                        setTenKeyListeners(mainView)
                    }

                    // Restore UI state after recreation
                    setKeyboardSize()
                    updateClipboardPreview()
                    mainLayoutBinding?.suggestionRecyclerView?.isVisible =
                        suggestionViewStatus.value
                }
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )

        Timber.d("onUpdateSelection: $oldSelStart $oldSelEnd $newSelStart $newSelEnd $candidatesStart $candidatesEnd")

        // Skip if composing text is active
        if (candidatesStart != -1 || candidatesEnd != -1) return

        // Show clipboard preview only if nothing was deleted and clipboard has data
        suggestionAdapter?.apply {
            if (deletedBuffer.isEmpty()) {
                // getPrimaryClipContentでクリップボードの内容を取得
                when (val item = clipboardUtil.getPrimaryClipContent()) {
                    is ClipboardItem.Image -> {
                        // 画像だった場合の処理
                        setPasteEnabled(true)
                        // ★新しいメソッドでBitmapをアダプターに渡す
                        setClipboardImagePreview(item.bitmap)
                    }

                    is ClipboardItem.Text -> {
                        // テキストだった場合の処理
                        setPasteEnabled(true)
                        setClipboardPreview(item.text)
                    }

                    is ClipboardItem.Empty -> {
                        // 空だった場合の処理
                        setPasteEnabled(false)
                        setClipboardPreview("") // 念のためプレビューもクリア
                    }
                }
            } else {
                setPasteEnabled(false)
            }
        }

        val tail = stringInTail.get()
        val hasTail = tail.isNotEmpty()
        val caretTop = newSelStart == 0 && newSelEnd == 0

        when {
            // Caret at top and tail exists → clear everything
            hasTail && caretTop -> {
                stringInTail.set("")
                if (_inputString.value.isNotEmpty()) {
                    _inputString.value = ""
                    setComposingText("", 0)
                }
                suggestionAdapter?.suggestions =
                    emptyList() // avoid unnecessary allocations elsewhere
            }

            // Caret moved while tail exists → commit tail
            hasTail -> {
                _inputString.value = tail
                stringInTail.set("")
            }

            // No tail but still holding input → cleanup
            _inputString.value.isNotEmpty() -> {
                _inputString.value = ""
                setComposingText("", 0)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mainLayoutBinding?.let { mainView ->

            when (mainView.keyboardView.currentInputMode.value) {
                InputMode.ModeJapanese -> {
                    val insertString = inputString.value
                    val suggestions = listAdapter.currentList
                    val sb = StringBuilder()

                    event?.let { e ->
                        Timber.d("onKeyDown: ${e.keyCode} $keyCode $e")
                        if (e.isShiftPressed || e.isCapsLockOn) {
                            val char = PhysicalShiftKeyCodeMap.keymap[keyCode]
                            char?.let { c ->
                                if (insertString.isNotEmpty()) {
                                    sb.append(
                                        insertString
                                    ).append(c)
                                    _inputString.update {
                                        sb.toString()
                                    }
                                } else {
                                    _inputString.update {
                                        c.toString()
                                    }
                                }
                                return true
                            }
                            return super.onKeyDown(keyCode, event)
                        } else if (e.isCtrlPressed) {
                            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                                customKeyboardMode = when (customKeyboardMode) {
                                    KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                                    KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                                    KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                                }
                                updateKeyboardLayout()

                                val inputMode = when (customKeyboardMode) {
                                    KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                                    KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                                    KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
                                }
                                val showInputModeText = when (inputMode) {
                                    InputMode.ModeJapanese -> "あ"
                                    InputMode.ModeEnglish -> "A"
                                    InputMode.ModeNumber -> "A"
                                }
                                Timber.d("e.isCtrlPressed Space: $inputMode $showInputModeText")
                                floatingDockView.setText(showInputModeText)
                                mainView.keyboardView.setCurrentMode(inputMode)
                                return true
                            }
                            val char = PhysicalShiftKeyCodeMap.keymap[keyCode]
                            char?.let { c ->
                                Timber.d("isCtrlPressed: $c")
                                when (c) {
                                    'A' -> {
                                        selectAllText()
                                        return true
                                    }

                                    'C' -> {
                                        copyAction()
                                        return true
                                    }

                                    'V' -> {
                                        pasteAction()
                                        return true
                                    }

                                    'X' -> {
                                        cutAction()
                                        return true
                                    }

                                    'W' -> {
                                        showOrHideKeyboard()
                                        return true
                                    }

                                    else -> {

                                    }
                                }
                            }
                        }
                    }

                    when (keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            when {
                                insertString.isNotEmpty() -> {
                                    if (isHenkan.get()) {
                                        cancelHenkanByLongPressDeleteKey()
                                        listAdapter.updateHighlightPosition(-1)
                                        currentHighlightIndex = -1
                                        return true
                                    } else {
                                        deleteStringCommon(insertString)
                                        resetFlagsDeleteKey()
                                        event?.let { e ->
                                            romajiConverter?.handleDelete(e)
                                        }
                                        return true
                                    }
                                }

                                else -> return super.onKeyDown(keyCode, event)
                            }
                        }

                        KeyEvent.KEYCODE_SPACE -> {
                            when (mainView.keyboardView.currentInputMode.value) {
                                InputMode.ModeJapanese -> {
                                    if (insertString.isNotEmpty()) {
                                        isHenkan.set(true)
                                        Timber.d("KEYCODE_SPACE is pressed")
                                        val insertStringEndWithN =
                                            romajiConverter?.flush(insertString)?.first
                                        Timber.d("KEYCODE_SPACE is pressed: $insertStringEndWithN $stringInTail")
                                        if (insertStringEndWithN == null) {
                                            _inputString.update { insertString }
                                            floatingCandidateNextItem(insertString)
                                        } else {
                                            _inputString.update { insertStringEndWithN }
                                            floatingCandidateNextItem(insertString)
                                            scope.launch {
                                                delay(64)
                                                val newSuggestionList =
                                                    suggestionAdapter?.suggestions ?: emptyList()
                                                if (newSuggestionList.isNotEmpty()) handleJapaneseModeSpaceKey(
                                                    mainView,
                                                    newSuggestionList,
                                                    insertStringEndWithN
                                                )
                                            }
                                        }
                                    } else {
                                        if (stringInTail.get().isNotEmpty()) return true
                                        val isFlick = hankakuPreference ?: false
                                        setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
                                    }
                                }

                                else -> {
                                    handleSpaceKeyClick(false, insertString, suggestions.map {
                                        Candidate(
                                            string = it.word,
                                            type = (1).toByte(),
                                            length = insertString.length.toUByte(),
                                            score = 0
                                        )
                                    }, mainView)
                                }
                            }
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isHenkan.get()) {
                                floatingCandidatePreviousItem(insertString)
                                return true
                            } else {
                                handleLeftKeyPress(
                                    GestureType.Tap, insertString
                                )
                                romajiConverter?.clear()
                            }
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isHenkan.get()) {
                                Timber.d("KEYCODE_DPAD_RIGHT: called")
                                floatingCandidateNextItem(insertString)
                                return true
                            } else {
                                actionInRightKeyPressed(
                                    GestureType.Tap, insertString
                                )
                                romajiConverter?.clear()
                            }
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (insertString.isNotEmpty()) {
                                if (isHenkan.get()) {
                                    floatingCandidatePreviousItem(insertString)
                                    return true
                                } else {
                                    when (mainView.keyboardView.currentInputMode.value) {
                                        InputMode.ModeJapanese -> {
                                            if (insertString.isNotEmpty()) {
                                                isHenkan.set(true)
                                                Timber.d("KEYCODE_SPACE is pressed")
                                                val insertStringEndWithN =
                                                    romajiConverter?.flush(insertString)?.first
                                                Timber.d("KEYCODE_SPACE is pressed: $insertStringEndWithN")
                                                if (insertStringEndWithN == null) {
                                                    _inputString.update { insertString }
                                                    floatingCandidateNextItem(insertString)
                                                } else {
                                                    _inputString.update { insertStringEndWithN }
                                                    floatingCandidateNextItem(insertString)
                                                    scope.launch {
                                                        delay(64)
                                                        val newSuggestionList =
                                                            suggestionAdapter?.suggestions
                                                                ?: emptyList()
                                                        if (newSuggestionList.isNotEmpty()) handleJapaneseModeSpaceKey(
                                                            mainView,
                                                            newSuggestionList,
                                                            insertStringEndWithN
                                                        )
                                                    }
                                                }
                                            } else {
                                                if (stringInTail.get().isNotEmpty()) return true
                                                val isFlick = hankakuPreference ?: false
                                                setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
                                            }
                                        }

                                        else -> {
                                            handleSpaceKeyClick(
                                                false,
                                                insertString,
                                                suggestions.map {
                                                    Candidate(
                                                        string = it.word,
                                                        type = (1).toByte(),
                                                        length = insertString.length.toUByte(),
                                                        score = 0
                                                    )
                                                },
                                                mainView
                                            )
                                        }
                                    }
                                    return true
                                }
                            }
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (insertString.isNotEmpty()) {
                                if (isHenkan.get()) {
                                    floatingCandidateNextItem(insertString)
                                    return true
                                } else {
                                    when (mainView.keyboardView.currentInputMode.value) {
                                        InputMode.ModeJapanese -> {
                                            if (insertString.isNotEmpty()) {
                                                isHenkan.set(true)
                                                Timber.d("KEYCODE_SPACE is pressed")
                                                val insertStringEndWithN =
                                                    romajiConverter?.flush(insertString)?.first
                                                Timber.d("KEYCODE_SPACE is pressed: $insertStringEndWithN")
                                                if (insertStringEndWithN == null) {
                                                    _inputString.update { insertString }
                                                    floatingCandidateNextItem(insertString)
                                                } else {
                                                    _inputString.update { insertStringEndWithN }
                                                    floatingCandidateNextItem(insertString)
                                                    scope.launch {
                                                        delay(64)
                                                        val newSuggestionList =
                                                            suggestionAdapter?.suggestions
                                                                ?: emptyList()
                                                        if (newSuggestionList.isNotEmpty()) handleJapaneseModeSpaceKey(
                                                            mainView,
                                                            newSuggestionList,
                                                            insertStringEndWithN
                                                        )
                                                    }
                                                }
                                            } else {
                                                if (stringInTail.get().isNotEmpty()) return true
                                                val isFlick = hankakuPreference ?: false
                                                setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
                                            }
                                        }

                                        else -> {
                                            handleSpaceKeyClick(
                                                false,
                                                insertString,
                                                suggestions.map {
                                                    Candidate(
                                                        string = it.word,
                                                        type = (1).toByte(),
                                                        length = insertString.length.toUByte(),
                                                        score = 0
                                                    )
                                                },
                                                mainView
                                            )
                                        }
                                    }
                                    return true
                                }
                            }
                        }

                        KeyEvent.KEYCODE_ENTER -> {
                            if (insertString.isNotEmpty()) {
                                if (isHenkan.get()) {
                                    floatingCandidateEnterPressed()
                                    return true
                                } else {
                                    handleNonEmptyInputEnterKey(suggestions.map {
                                        Candidate(
                                            string = it.word,
                                            type = (1).toByte(),
                                            length = insertString.length.toUByte(),
                                            score = 0
                                        )
                                    }, mainView, insertString)
                                }
                            } else {
                                handleEmptyInputEnterKey(mainView)
                            }
                            romajiConverter?.clear()
                            return true
                        }

                        KeyEvent.KEYCODE_BACK -> {
                            return super.onKeyDown(keyCode, event)
                        }

                        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z, in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_AT, KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_NUMPAD_DOT -> {
                            event?.let { e ->
                                scope.launch {
                                    _physicalKeyboardEnable.emit(true)
                                }
                                romajiConverter?.handleKeyEvent(e)?.let { romajiResult ->
                                    if (insertString.isNotEmpty()) {
                                        sb.append(
                                            insertString.dropLast((romajiResult.second))
                                        ).append(romajiResult.first)
                                        _inputString.update {
                                            sb.toString()
                                        }
                                    } else {
                                        _inputString.update {
                                            romajiResult.first
                                        }
                                    }
                                }
                                return true
                            }
                            return super.onKeyDown(keyCode, null)
                        }

                        else -> {
                            return super.onKeyDown(keyCode, event)
                        }

                    }
                }

                InputMode.ModeEnglish -> {
                    event?.let { e ->
                        if (e.isCtrlPressed) {
                            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                                customKeyboardMode = when (customKeyboardMode) {
                                    KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                                    KeyboardInputMode.ENGLISH -> KeyboardInputMode.HIRAGANA
                                    KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                                }
                                updateKeyboardLayout()

                                val inputMode = when (customKeyboardMode) {
                                    KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                                    KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                                    KeyboardInputMode.SYMBOLS -> InputMode.ModeJapanese
                                }
                                floatingDockView.setText("あ")
                                mainView.keyboardView.setCurrentMode(inputMode)
                                return true
                            }
                        }
                    }
                    return super.onKeyDown(keyCode, event)
                }

                InputMode.ModeNumber -> {
                    event?.let { e ->
                        if (e.isCtrlPressed) {
                            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                                customKeyboardMode = when (customKeyboardMode) {
                                    KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                                    KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                                    KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                                }
                                updateKeyboardLayout()

                                val inputMode = when (customKeyboardMode) {
                                    KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                                    KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                                    KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
                                }
                                floatingDockView.setText("あ")
                                mainView.keyboardView.setCurrentMode(inputMode)
                                return true
                            }
                        }
                    }
                    return super.onKeyDown(keyCode, event)
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                Timber.d("onKeyUp KEYCODE_ENTER: ${inputString.value} ${isHenkan.get()}")
                if (isHenkan.get()) {
                    if (inputString.value.isNotEmpty()) {
                        return true
                    }
                    isHenkan.set(false)
                }
                return super.onKeyUp(keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun floatingCandidateNextItem(insertString: String) {
        Timber.d("floatingCandidateNextItem called. Current highlight: $currentHighlightIndex ${stringInTail.get()}")
        if (listAdapter.currentList.isEmpty()) return

        val suggestionCount = listAdapter.currentList.size.coerceAtMost(PAGE_SIZE)
        if (suggestionCount == 0) return

        // 次のページが存在するかどうかを確認
        val maxPage = (fullSuggestionsList.size - 1) / PAGE_SIZE
        val hasNextPage = currentPage < maxPage

        // ハイライトがページの最後尾 かつ 次のページが存在する場合
        if (currentHighlightIndex == suggestionCount - 1 && hasNextPage) {
            goToNextPageForFloatingCandidate() // 次のページに移動
            scope.launch {
                delay(64)
                Timber.d("floatingCandidateNextItem hasNextPage: ${listAdapter.getHighlightedItem()}")
                displayComposingTextInHardwareKeyboardConnected(insertString)
            }
        } else if (currentHighlightIndex == suggestionCount - 1 && !hasNextPage) {
            currentPage = -1
            scope.launch {
                delay(64)
                Timber.d("floatingCandidateNextItem hasNextPage: ${listAdapter.getHighlightedItem()}")
                displayComposingTextInHardwareKeyboardConnected(insertString)
            }
        } else {
            // 上記以外の場合は、現在のページ内でハイライトをループさせる
            currentHighlightIndex = if (currentHighlightIndex == RecyclerView.NO_POSITION) {
                0
            } else {
                (currentHighlightIndex + 1) % suggestionCount
            }
            listAdapter.updateHighlightPosition(currentHighlightIndex)
            displayComposingTextInHardwareKeyboardConnected(insertString)
            Timber.d("floatingCandidateNextItem: ${listAdapter.getHighlightedItem()} ${inputString.value} $stringInTail")
        }
    }

    private fun floatingCandidatePreviousItem(insertString: String) {
        if (listAdapter.currentList.isEmpty()) return
        val suggestionCount = listAdapter.currentList.size.coerceAtMost(PAGE_SIZE)
        if (suggestionCount == 0) return
        val hasPreviousPage = currentPage > 0

        if (currentHighlightIndex <= 0 && hasPreviousPage) {
            goToPreviousPageForFloatingCandidate()
            Timber.d("floatingCandidatePreviousItem hasPreviousPage: ${listAdapter.getHighlightedItem()}")
        } else {
            currentHighlightIndex = if (currentHighlightIndex <= 0) {
                suggestionCount - 1
            } else {
                currentHighlightIndex - 1
            }
            listAdapter.updateHighlightPosition(currentHighlightIndex)
            displayComposingTextInHardwareKeyboardConnected(insertString = insertString)
            Timber.d("floatingCandidatePreviousItem: ${listAdapter.getHighlightedItem()}")
        }
    }

    private fun displayComposingTextInHardwareKeyboardConnected(
        insertString: String
    ) {
        val selectedSuggestion = listAdapter.currentList[currentHighlightIndex]
        if (insertString.length > selectedSuggestion.length.toInt()) {
            val subString = insertString.substring(selectedSuggestion.length.toInt())
            stringInTail.set(subString)
            Timber.d("displayComposingTextInHardwareKeyboardConnected: ${selectedSuggestion.word} ${selectedSuggestion.length} $insertString $subString ${insertString.length} ${selectedSuggestion.length.toInt()}")
        }
        val spannableString = SpannableString(selectedSuggestion.word + stringInTail)
        setComposingTextAfterEdit(
            selectedSuggestion.word, spannableString
        )
    }

    private fun floatingCandidateEnterPressed() {
        val selectedSuggestion = listAdapter.getHighlightedItem()
        if (selectedSuggestion != null) {
            val subString = stringInTail.get()
            if (subString.isNotEmpty()) {
                commitText(selectedSuggestion.word, 1)
                updateSuggestionsForFloatingCandidate(emptyList())
                _inputString.update { subString }
                listAdapter.updateHighlightPosition(-1)
                currentHighlightIndex = -1
                scope.launch {
                    delay(64)
                    floatingCandidateNextItem(insertString = subString)
                }
            } else {
                commitText(selectedSuggestion.word, 1)
                updateSuggestionsForFloatingCandidate(emptyList())
            }
        }
    }

    private fun setTenKeyListeners(
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardView.apply {
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Flick: $char $key $gestureType")
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Down -> {
                            when (vibrationTimingStr) {
                                "both" -> {
                                    vibrate()
                                }

                                "press" -> {
                                    vibrate()
                                }

                                "release" -> {

                                }
                            }
                        }

                        GestureType.Tap -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = false,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }


                        else -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = true,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }
                    }
                }
            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    handleLongPress(key)
                    Timber.d("Long Press: $key")
                }
            })
        }
    }

    private fun updateSuggestionsForFloatingCandidate(suggestions: List<CandidateItem>) {
        Timber.d("updateSuggestionsForFloatingCandidate: $suggestions")
        fullSuggestionsList = suggestions
        currentPage = 0
        displayCurrentPage()
    }

    private fun displayCurrentPage() {
        if (fullSuggestionsList.isEmpty()) {
            listAdapter.submitList(emptyList())
            floatingCandidateWindow?.dismiss()
            return
        }

        val startIndex = currentPage * PAGE_SIZE
        val endIndex = (startIndex + PAGE_SIZE).coerceAtMost(fullSuggestionsList.size)
        val suggestionsForPage = fullSuggestionsList.subList(startIndex, endIndex)

        val itemsToShow = mutableListOf<CandidateItem>()
        itemsToShow.addAll(suggestionsForPage)

        val totalPages = (fullSuggestionsList.size + PAGE_SIZE - 1) / PAGE_SIZE
        if (totalPages > 1) {
            val pagerLabel = "▶ (${currentPage + 1}/$totalPages)"
            itemsToShow.add(CandidateItem(word = pagerLabel, length = (1).toUByte()))
        }
        listAdapter.submitList(itemsToShow) {
            listAdapter.updateHighlightPosition(currentHighlightIndex)
            Timber.d("floatingCandidateNextItem (after update): ${listAdapter.getHighlightedItem()}")
        }
    }

    private fun setTabletKeyListeners(
        mainView: MainLayoutBinding
    ) {
        mainView.tabletView.apply {
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Flick: $char $key $gestureType")
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Down -> {
                            when (vibrationTimingStr) {
                                "both" -> {
                                    vibrate()
                                }

                                "press" -> {
                                    vibrate()
                                }

                                "release" -> {

                                }
                            }
                        }

                        GestureType.Tap -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = false,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }

                        else -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = true,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }
                    }
                }

            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    handleLongPress(key)
                }
            })
        }
    }

    private fun handleTapAndFlick(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding
    ) {
        when (vibrationTimingStr) {
            "both" -> {
                vibrate()
            }

            "press" -> {

            }

            "release" -> {
                vibrate()
            }
        }
        if (deletedBuffer.isNotEmpty() && !selectMode.value && key != Key.SideKeyDelete) {
            clearDeletedBuffer()
            suggestionAdapter?.setUndoEnabled(false)
            updateClipboardPreview()
        } else if (deletedBuffer.isNotEmpty() && selectMode.value && key == Key.SideKeySpace) {
            clearDeletedBufferWithoutResetLayout()
            suggestionAdapter?.setUndoEnabled(false)
            updateClipboardPreview()
        }
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {
                if (insertString.isNotEmpty()) {
                    handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                } else {
                    handleEmptyInputEnterKey(mainView)
                }
            }

            Key.KeyDakutenSmall -> {
                handleDakutenSmallLetterKey(
                    sb = sb,
                    isFlick = isFlick,
                    char = char,
                    insertString = insertString,
                    mainView = mainView,
                    gestureType = gestureType
                )
            }

            Key.SideKeyCursorLeft -> {
                if (!leftCursorKeyLongKeyPressed.get()) {
                    handleLeftCursor(gestureType, insertString)
                }
                onLeftKeyLongPressUp.set(true)
                leftCursorKeyLongKeyPressed.set(false)
                leftLongPressJob?.cancel()
                leftLongPressJob = null
            }

            Key.SideKeyCursorRight -> {
                if (!rightCursorKeyLongKeyPressed.get()) {
                    actionInRightKeyPressed(gestureType, insertString)
                }
                onRightKeyLongPressUp.set(true)
                rightCursorKeyLongKeyPressed.set(false)
                rightLongPressJob?.cancel()
                rightLongPressJob = null
            }

            Key.SideKeyDelete -> {
                if (!isFlick) {
                    if (!deleteKeyLongKeyPressed.get()) {
                        handleDeleteKeyTap(insertString, suggestions)
                    }
                }
                stopDeleteLongPress()
            }

            Key.SideKeyInputMode -> {
                setTenkeyIconsInHenkan(insertString, mainView)
            }

            Key.SideKeyPreviousChar -> {
                mainView.keyboardView.let {
                    when (it.currentInputMode.value) {
                        is InputMode.ModeNumber -> {

                        }

                        else -> {
                            if (!isFlick) setNextReturnInputCharacter(insertString)
                        }
                    }
                }
            }

            Key.SideKeySpace -> {
                if (cursorMoveMode.value) {
                    _cursorMoveMode.update { false }
                } else {
                    if (!isSpaceKeyLongPressed) {
                        val isHankaku = isFlick || hankakuPreference == true
                        handleSpaceKeyClick(isHankaku, insertString, suggestions, mainView)
                    }
                }
                isSpaceKeyLongPressed = false
            }

            Key.SideKeySymbol -> {
                vibrate()
                _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                stringInTail.set("")
                finishComposingText()
                setComposingText("", 0)
            }

            else -> {
                /** 選択モード **/
                if (selectMode.value) {
                    when (key) {
                        /** コピー **/
                        Key.KeyA -> {
                            copyAction()
                        }
                        /** 切り取り **/
                        Key.KeySA -> {
                            cutAction()
                        }
                        /** 全て選択 **/
                        Key.KeyMA -> {
                            selectAllText()
                        }

                        /** 共有 **/
                        Key.KeyRA -> {
                            val selectedText = getSelectedText(0)
                            if (!selectedText.isNullOrEmpty()) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, selectedText.toString())
                                }
                                val chooser: Intent =
                                    Intent.createChooser(sendIntent, "Share text via").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                startActivity(chooser)
                                clearSelection()
                            }
                        }
                        /** その他 **/
                        else -> {

                        }
                    }
                } else {
                    if (isFlick) {
                        handleFlick(char, insertString, sb, mainView)
                    } else {
                        handleTap(char, insertString, sb, mainView)
                    }
                }
            }
        }
    }

    private fun handleFlick(
        char: Char?, insertString: String, sb: StringBuilder, mainView: MainLayoutBinding
    ) {
        if (isHenkan.get()) {
            suggestionAdapter?.updateHighlightPosition(-1)
            finishComposingText()
            setComposingText("", 0)
            mainView.root.post {
                isHenkan.set(false)
                char?.let {
                    sendCharFlick(
                        charToSend = it, insertString = "", sb = sb
                    )
                }
                isContinuousTapInputEnabled.set(true)
                lastFlickConvertedNextHiragana.set(true)
            }
        } else {
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        }
    }

    private fun handleTap(
        char: Char?, insertString: String, sb: StringBuilder, mainView: MainLayoutBinding
    ) {
        if (isHenkan.get()) {
            suggestionAdapter?.updateHighlightPosition(-1)
            finishComposingText()
            setComposingText("", 0)
            mainView.root.post {
                isHenkan.set(false)
                char?.let {
                    sendCharTap(
                        charToSend = it, insertString = "", sb = sb
                    )
                }
            }
        } else {
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
        }
    }

    private fun handleLongPress(
        key: Key
    ) {
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {}
            Key.KeyDakutenSmall -> {
                showListPopup()
            }

            Key.SideKeyCursorLeft -> {
                handleLeftLongPress()
                leftCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                suggestionAdapter?.setUndoEnabled(false)
                updateClipboardPreview()
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                suggestionAdapter?.setUndoEnabled(false)
                updateClipboardPreview()
            }

            Key.SideKeyDelete -> {
                handleDeleteLongPress()
            }

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                handleSpaceLongAction()
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private var keyboardSelectionPopupWindow: PopupWindow? = null

    private fun registerNGWord(
        insertString: String, candidate: Candidate, candidatePosition: Int
    ) {
        mainLayoutBinding?.let { mainView ->
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
            val listView = popupView.findViewById<ListView>(R.id.popup_listview)

            // A. Enable single choice mode for the ListView
            listView.choiceMode = ListView.CHOICE_MODE_SINGLE

            val items = listOf(
                "この単語を非表示", "閉じる"
            )

            // B. Use your new custom layout file in the ArrayAdapter
            val adapter = ArrayAdapter(this, R.layout.list_item_layout, items) // Use your layout
            listView.adapter = adapter

            keyboardSelectionPopupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true // Set focusable to true
            )
            listView.setOnItemClickListener { _, _, position, _ ->
                Timber.d("candidate long click: $candidate $candidatePosition")
                when (position) {
                    0 -> {
                        ioScope.launch {
                            val exist = ngWordRepository.exists(
                                yomi = insertString, tango = candidate.string
                            )
                            if (!exist) {
                                ngWordRepository.addNgWord(
                                    yomi = insertString, tango = candidate.string
                                )
                                withContext(Dispatchers.Main) {
                                    _suggestionFlag.emit(CandidateShowFlag.Updating)
                                }
                            }
                        }
                    }

                    1 -> {

                    }

                }
                keyboardSelectionPopupWindow?.dismiss()
            }

            keyboardSelectionPopupWindow?.showAtLocation(
                mainView.suggestionRecyclerView, Gravity.TOP, 0, 0
            )
        }
    }

    private fun showListPopup() {
        onKeyboardSwitchLongPressUp = true
        if (inputString.value.isNotEmpty()) return
        mainLayoutBinding?.let { mainView ->
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
            val listView = popupView.findViewById<ListView>(R.id.popup_listview)

            // A. Enable single choice mode for the ListView
            listView.choiceMode = ListView.CHOICE_MODE_SINGLE

            val currentKeyboardSetting = appPreference.keyboard_order

            val items = currentKeyboardSetting.map {
                when (it) {
                    KeyboardType.TENKEY -> "日本語 - かな"
                    KeyboardType.SUMIRE -> "スミレ入力"
                    KeyboardType.QWERTY -> "英語"
                    KeyboardType.ROMAJI -> "ローマ字入力"
                    KeyboardType.CUSTOM -> "カスタム"
                }
            }

            // B. Use your new custom layout file in the ArrayAdapter
            val adapter = ArrayAdapter(this, R.layout.list_item_layout, items) // Use your layout
            listView.adapter = adapter

            keyboardSelectionPopupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true // Set focusable to true
            )
            listView.setItemChecked(currentKeyboardOrder, true)

            listView.setOnItemClickListener { _, _, position, _ ->
                if (keyboardOrder.isEmpty()) return@setOnItemClickListener
                currentKeyboardOrder = position
                onKeyboardSwitchLongPressUp = false
                val nextType = keyboardOrder[position]
                showKeyboard(nextType)
                keyboardSelectionPopupWindow?.dismiss()
            }

            keyboardSelectionPopupWindow?.setOnDismissListener {
                onKeyboardSwitchLongPressUp = false
            }

            keyboardSelectionPopupWindow?.showAtLocation(mainView.root, Gravity.CENTER, 0, 0)
        }
    }

    private fun handleDeleteLongPress() {
        if (isHenkan.get()) {
            cancelHenkanByLongPressDeleteKey()
            hasConvertedKatakana = isLiveConversionEnable == true
        } else {
            onDeleteLongPressUp.set(true)
            deleteLongPress()
            _dakutenPressed.value = false
            englishSpaceKeyPressed.set(false)
            deleteKeyLongKeyPressed.set(true)
        }
    }

    private fun handleSpaceLongAction() {
        Timber.d("SideKeySpace LongPress: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        val insertString = inputString.value
        if (insertString.isNotEmpty()) {
            mainLayoutBinding?.let {
                if (it.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                    if (isHenkan.get()) return
                    isSpaceKeyLongPressed = true
                    if (hasConvertedKatakana) {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    } else {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    }
                    hasConvertedKatakana = !hasConvertedKatakana
                }
            }
        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            _cursorMoveMode.update { true }
            isSpaceKeyLongPressed = true
        }
        Timber.d("SideKeySpace LongPress after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    /**
     * 全てのキーボードビューを確実に非表示にする
     */
    private fun hideAllKeyboards() {
        mainLayoutBinding?.apply {
            keyboardView.isVisible = false
            qwertyView.isVisible = false
            tabletView.isVisible = false
            customLayoutDefault.isVisible = false
            keyboardSymbolView.isVisible = false
            candidatesRowView.isVisible = false
        }
    }

    /**
     * 指定されたキーボードを表示するための統一された関数
     */
    private fun showKeyboard(type: KeyboardType) {
        hideAllKeyboards() // ★最重要：まず他の全てのキーボードを隠す

        mainLayoutBinding?.apply {
            when (type) {
                KeyboardType.TENKEY -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        if (isTablet == true) {
                            tabletView.isVisible = true
                            tabletView.resetLayout()
                        } else {
                            keyboardView.isVisible = true
                            keyboardView.setCurrentMode(InputMode.ModeJapanese)
                        }
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeNumber)
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.QWERTY -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        qwertyView.isVisible = true
                        keyboardView.isVisible = false
                        customLayoutDefault.isVisible = false
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                        keyboardView.setCurrentMode(InputMode.ModeEnglish)
                        qwertyView.resetQWERTYKeyboard()
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeNumber)
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.ROMAJI -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        qwertyView.isVisible = true
                        keyboardView.isVisible = false
                        customLayoutDefault.isVisible = false
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                        keyboardView.setCurrentMode(InputMode.ModeJapanese)
                        qwertyView.setRomajiKeyboard()
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeNumber)
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.SUMIRE -> {
                    customKeyboardMode = KeyboardInputMode.HIRAGANA
                    customLayoutDefault.isVisible = true
                    keyboardView.setCurrentMode(InputMode.ModeJapanese)
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                        val hiraganaLayout = KeyboardDefaultLayouts.createFinalLayout(
                            mode = KeyboardInputMode.HIRAGANA,
                            dynamicKeyStates = mapOf(
                                "enter_key" to 0, "dakuten_toggle_key" to 0
                            ),
                            inputType = sumireInputKeyType ?: "flick-default",
                            isFlick = isFlickOnlyMode
                        )
                        customLayoutDefault.setKeyboard(hiraganaLayout)
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                    } else {
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                    }
                    qwertyView.isVisible = false
                    keyboardView.isVisible = false
                }

                KeyboardType.CUSTOM -> {
                    setInitialKeyboardTab()
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                    } else {
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                    }
                    customLayoutDefault.isVisible = true
                    keyboardView.setCurrentMode(InputMode.ModeJapanese)
                    qwertyView.isVisible = false
                    keyboardView.isVisible = false
                }
            }
            suggestionRecyclerView.isVisible = true
        }
    }

    private var currentEnterKeyIndex: Int = 0 // 0:改行, 1:実行, 2:確定, 3:変換
    private var currentDakutenKeyIndex: Int = 0 // 0:^_^, 1:゛゜
    private var currentSpaceKeyIndex: Int = 0 // 0: Space, 1: Convert

    private fun updateKeyboardLayout() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value}")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {
                //setInitialKeyboardTab()
            }

            TenKeyQWERTYMode.Default -> {}
            TenKeyQWERTYMode.TenKeyQWERTY -> {}
            TenKeyQWERTYMode.Sumire -> {
                val dynamicStates = mapOf(
                    "enter_key" to currentEnterKeyIndex,
                    "dakuten_toggle_key" to currentDakutenKeyIndex,
                    "space_convert_key" to currentSpaceKeyIndex
                )

                Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")

                val finalLayout = KeyboardDefaultLayouts.createFinalLayout(
                    mode = customKeyboardMode,
                    dynamicKeyStates = dynamicStates,
                    inputType = sumireInputKeyType ?: "flick-default",
                    isFlick = isFlickOnlyMode
                )
                mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
            }

            TenKeyQWERTYMode.Number -> {
                val finalLayout = KeyboardDefaultLayouts.createNumberLayout()
                mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
            }
        }
    }

    private var isCustomLayoutRomajiMode = false

    private fun setInitialKeyboardTab() {
        scope.launch(Dispatchers.IO) {
            if (customLayouts.isEmpty()) {
                return@launch
            }
            val id = customLayouts[0].layoutId
            val dbLayout = keyboardRepository.getFullLayout(id).first()
            val finalLayout = keyboardRepository.convertLayout(dbLayout)

            Timber.d("setInitialKeyboardTab ${dbLayout.isRomaji} ${finalLayout.isRomaji}")
            isCustomLayoutRomajiMode = finalLayout.isRomaji
            withContext(Dispatchers.Main) {
                mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
            }
        }
    }

    private fun setKeyboardTab(pos: Int) {
        scope.launch(Dispatchers.IO) {
            if (customLayouts.isEmpty()) {
                return@launch
            }
            val id = customLayouts[pos].layoutId
            val dbLayout = keyboardRepository.getFullLayout(id).first()
            Timber.d("setKeyboardTab: $id $dbLayout")
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            Timber.d("setKeyboardTab: ${dbLayout.isRomaji} ${finalLayout.isRomaji}")
            isCustomLayoutRomajiMode = finalLayout.isRomaji
            withContext(Dispatchers.Main) {
                mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
            }
        }
    }

    /**
     * 濁点モードを切り替えてキーボードを更新するメソッドの例
     */
    private fun setSumireKeyboardDakutenKey() {
        // 0と1を交互に切り替える
        currentDakutenKeyIndex = 1
        updateKeyboardLayout()
    }

    private fun setSumireKeyboardDakutenKeyEmpty() {
        // 0と1を交互に切り替える
        currentDakutenKeyIndex = 0
        updateKeyboardLayout()
    }

    private fun setSumireKeyboardEnterKey(index: Int) {
        // 0と1を交互に切り替える
        currentEnterKeyIndex = index
        updateKeyboardLayout()
    }

    private fun setSumireKeyboardSpaceKey(index: Int) {
        // 0と1を交互に切り替える
        currentSpaceKeyIndex = index
        updateKeyboardLayout()
    }

    private fun resetSumireKeyboardDakutenMode() {
        currentDakutenKeyIndex = 0
        currentEnterKeyIndex = 0
        currentSpaceKeyIndex = 0
        updateKeyboardLayout()
    }

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    // ▼▼▼ ADD THIS VARIABLE ▼▼▼
    private var customKeyboardMode = KeyboardInputMode.HIRAGANA

    private fun clearDeleteBufferWithView() {
        appPreference.undo_enable_preference?.let {
            if (it && deletedBuffer.isNotEmpty()) {
                clearDeletedBufferWithoutResetLayout()
                suggestionAdapter?.setUndoEnabled(false)
                updateClipboardPreview()
            }
        }
    }

    private fun setupCustomKeyboardListeners(mainView: MainLayoutBinding) {
        mainView.customLayoutDefault.setOnKeyboardActionListener(object :
            com.kazumaproject.custom_keyboard.view.FlickKeyboardView.OnKeyboardActionListener {

            override fun onKey(text: String, isFlick: Boolean) {
                // 通常の文字が入力された場合（変更なし）
                clearDeleteBufferWithView()
                Timber.d("onKey: $text")
                vibrate()

                when (qwertyMode.value) {
                    TenKeyQWERTYMode.Custom -> {
                        if (text.isEmpty()) return
                        if (text.length == 1) {
                            if (isCustomLayoutRomajiMode) {
                                val insertString = inputString.value
                                val sb = StringBuilder()
                                sb.append(insertString).append(text)
                                romajiConverter?.let { converter ->
                                    _inputString.update {
                                        converter.convert(sb.toString())
                                    }
                                }
                            } else {
                                handleOnKeyForSumire(
                                    text, mainView, isFlick
                                )
                            }
                        } else {
                            if (isCustomKeyboardTwoWordsOutputEnable == true) {
                                finishComposingText()
                                setComposingText("", 0)
                                commitText(text, 1)
                            } else {
                                if (isCustomLayoutRomajiMode) {
                                    val insertString = inputString.value
                                    val sb = StringBuilder()
                                    sb.append(insertString).append(text)
                                    romajiConverter?.let { converter ->
                                        _inputString.update {
                                            converter.convert(sb.toString())
                                        }
                                    }
                                } else {
                                    val insertString = inputString.value
                                    val sb = StringBuilder()
                                    sb.append(insertString).append(text)
                                    _inputString.update {
                                        sb.toString()
                                    }
                                }
                            }
                        }
                    }

                    TenKeyQWERTYMode.Sumire -> {
                        handleOnKeyForSumire(
                            text, mainView, isFlick
                        )
                    }

                    TenKeyQWERTYMode.Number -> {
                        handleOnKeyForSumire(
                            text, mainView, isFlick
                        )
                    }

                    else -> {}
                }
            }

            override fun onActionLongPress(action: KeyAction) {
                vibrate()
                clearDeleteBufferWithView()
                Timber.d("onActionLongPress: $action")
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {
                        // 現在のモードに応じて次のモードを決定
                        customKeyboardMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                            KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                            KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                        }
                        updateKeyboardLayout()
                    }

                    KeyAction.Convert, KeyAction.Space -> {
                        handleSpaceLongAction()
                    }

                    KeyAction.Copy -> {

                    }

                    KeyAction.Delete -> {
                        handleDeleteLongPress()
                    }

                    KeyAction.NewLine, KeyAction.Enter, KeyAction.Confirm -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isNotEmpty()) {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        } else {
                            handleEmptyInputEnterKey(mainView)
                        }
                    }

                    is KeyAction.InputText -> {
                        if (action.text == "^_^") {
                            _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                            stringInTail.set("")
                            finishComposingText()
                            setComposingText("", 0)
                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        updateClipboardPreview()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        updateClipboardPreview()
                    }

                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                }
            }

            override fun onActionUpAfterLongPress(action: KeyAction) {
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert, KeyAction.Space -> {
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.Copy -> {}
                    KeyAction.Delete -> {
                        stopDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                }
            }

            override fun onFlickDirectionChanged(direction: FlickDirection) {
                vibrate()
            }

            override fun onFlickActionLongPress(action: KeyAction) {
                Timber.d("onFlickActionLongPress: $action")
                vibrate()
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert -> {
                        handleSpaceLongAction()
                    }

                    KeyAction.Copy -> {
                        val selectedText = getSelectedText(0)
                        if (!selectedText.isNullOrEmpty()) {
                            clipboardUtil.setClipBoard(selectedText.toString())
                            suggestionAdapter?.apply {
                                setPasteEnabled(true)
                                setClipboardPreview(selectedText.toString())
                            }
                        }
                    }

                    KeyAction.Delete -> {}
                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        updateClipboardPreview()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        updateClipboardPreview()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {
                        pasteAction()
                    }

                    KeyAction.SelectAll -> {
                        selectAllText()
                    }

                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Space -> {}

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire(mainView)
                    }

                    KeyAction.ToggleDakuten -> {

                    }

                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                }
            }

            override fun onFlickActionUpAfterLongPress(action: KeyAction) {
                vibrate()
                Timber.d("onFlickActionUpAfterLongPress: $action")
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Copy -> {}
                    KeyAction.Delete -> {}
                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {
                        when (action.text) {
                            "ひらがな小文字" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickLeft()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "半濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickRight()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelRightLongPress()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Convert, KeyAction.Space -> {
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire(mainView)
                    }

                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                }
            }

            override fun onAction(action: KeyAction) {
                vibrate()

                Timber.d("onAction: $action")
                if (action != KeyAction.Delete) {
                    clearDeleteBufferWithView()
                }
                // 特殊キーがタップされた場合
                // ▼▼▼ 変更 ▼▼▼ whenの対象がStringからKeyActionオブジェクトに変わります
                when (action) {
                    is KeyAction.InputText -> {
                        when (action.text) {
                            "^_^" -> {
                                _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                                stringInTail.set("")
                                finishComposingText()
                                setComposingText("", 0)
                            }

                            "ひらがな小文字" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickLeft()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "半濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickRight()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                        }
                    }

                    KeyAction.SwitchToNextIme -> {
                        if (!onKeyboardSwitchLongPressUp) {
                            switchNextKeyboard()
                            _inputString.update { "" }
                            finishComposingText()
                            setComposingText("", 0)
                        }
                    }

                    KeyAction.ChangeInputMode -> {
                        // 現在のモードに応じて次のモードを決定
                        customKeyboardMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                            KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                            KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                        }
                        updateKeyboardLayout()

                        val inputMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                            KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                            KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
                        }
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.Delete -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        handleDeleteKeyTap(insertString, suggestions)
                        stopDeleteLongPress()
                    }

                    KeyAction.NewLine, KeyAction.Enter, KeyAction.Confirm -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isNotEmpty()) {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        } else {
                            handleEmptyInputEnterKey(mainView)
                        }
                    }

                    KeyAction.Convert, KeyAction.Space -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (cursorMoveMode.value) {
                            _cursorMoveMode.update { false }
                        } else {
                            if (!isSpaceKeyLongPressed) {
                                handleSpaceKeyClick(
                                    hankakuPreference ?: false, insertString, suggestions, mainView
                                )
                            }
                        }
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        if (!leftCursorKeyLongKeyPressed.get()) {
                            handleLeftCursor(GestureType.Tap, insertString)
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        if (!rightCursorKeyLongKeyPressed.get()) {
                            actionInRightKeyPressed(GestureType.Tap, insertString)
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.Backspace -> {}
                    KeyAction.Copy -> {
                        val selectedText = getSelectedText(0)
                        if (!selectedText.isNullOrEmpty()) {
                            clipboardUtil.setClipBoard(selectedText.toString())
                            suggestionAdapter?.apply {
                                setPasteEnabled(true)
                                setClipboardPreview(selectedText.toString())
                            }
                        }
                    }

                    KeyAction.Paste -> {
                        pasteAction()
                    }

                    KeyAction.SelectAll -> {
                        selectAllText()
                    }

                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire(mainView)
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire(mainView)
                    }

                    KeyAction.SwitchToEnglishLayout -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        updateKeyboardLayout()
                        val inputMode = InputMode.ModeEnglish
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.SwitchToKanaLayout -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        updateKeyboardLayout()
                        val inputMode = InputMode.ModeJapanese
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.SwitchToNumberLayout -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        updateKeyboardLayout()
                        val inputMode = InputMode.ModeNumber
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }
                }
            }
        })
    }

    private fun handleOnKeyForSumire(
        text: String, mainView: MainLayoutBinding, isFlick: Boolean
    ) {
        val insertString = inputString.value
        val sb = StringBuilder()
        text.forEach {
            if (isFlickOnlyMode == true) {
                handleFlick(char = it, insertString, sb, mainView)
            } else {
                if (isFlick) {
                    handleFlick(char = it, insertString, sb, mainView)
                } else {
                    handleTap(char = it, insertString, sb, mainView)
                }
            }
        }
    }

    private fun cancelLeftLongPress() {
        onLeftKeyLongPressUp.set(true)
        leftCursorKeyLongKeyPressed.set(false)
        leftLongPressJob?.cancel()
        leftLongPressJob = null
    }

    private fun cancelRightLongPress() {
        onRightKeyLongPressUp.set(true)
        rightCursorKeyLongKeyPressed.set(false)
        rightLongPressJob?.cancel()
        rightLongPressJob = null
    }

    private fun copyAction() {
        val selectedText = getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            clipboardUtil.setClipBoard(selectedText.toString())
            suggestionAdapter?.apply {
                setPasteEnabled(true)
                setClipboardPreview(selectedText.toString())
            }
        }
    }

    /**
     * クリップボードからの貼り付けアクション。テキストと画像の両方に対応。
     */
    private fun pasteAction() {
        when (val item = clipboardUtil.getPrimaryClipContent()) {
            is ClipboardItem.Image -> {
                commitBitmap(item.bitmap)
            }

            is ClipboardItem.Text -> {
                if (item.text.isNotEmpty()) {
                    commitText(item.text, 1)
                }
            }

            is ClipboardItem.Empty -> {
                // Do nothing
            }
        }
        clearDeletedBufferWithoutResetLayout()
        suggestionAdapter?.setUndoEnabled(false)
        // ★修正点: UIを正しく更新する新しい関数を呼び出す
        updateClipboardPreview()
    }

    private fun cutAction() {
        val selectedText = getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            clipboardUtil.setClipBoard(selectedText.toString())
            suggestionAdapter?.apply {
                setPasteEnabled(true)
                setClipboardPreview(selectedText.toString())
            }
            sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL
                )
            )
        }
    }

    private fun showOrHideKeyboard() {
        if (isInputViewShown) {
            requestHideSelf(0)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                requestShowSelf(0)
            } else {
                val token = window?.window?.attributes?.token
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInputFromInputMethod(
                    token, InputMethodManager.SHOW_IMPLICIT
                )
            }
        }
    }

    private fun pasteImageAction(bitmap: Bitmap) {
        commitBitmap(bitmap)
        clearDeletedBufferWithoutResetLayout()
        suggestionAdapter?.setUndoEnabled(false)
        // ★修正点: UIを正しく更新する新しい関数を呼び出す
        updateClipboardPreview()
    }

    /**
     * Bitmapを入力先アプリに送信します。
     * この関数を呼び出す前に、FileProviderが正しく設定されている必要があります。
     *
     * @param bitmap 送信するBitmapオブジェクト。
     */
    private fun commitBitmap(bitmap: Bitmap) {
        // ▼▼▼ ログ追加 ▼▼▼
        Timber.d("commitBitmap: 開始")

        // APIレベルが低い場合は何もせずに終了
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Timber.w("このAPIレベルではcommitContentはサポートされていません。")
            return
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // InputConnectionとEditorInfoが有効か確認
        val inputConnection = currentInputConnection
        val editorInfo = currentInputEditorInfo
        if (inputConnection == null || editorInfo == null) {
            Timber.e("commitBitmap: InputConnectionまたはEditorInfoがnullです。処理を中断します。")
            return
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // ターゲットエディタがサポートするMIMEタイプをログに出力
        val supportedMimeTypes = editorInfo.contentMimeTypes ?: emptyArray()
        if (supportedMimeTypes.isEmpty()) {
            Timber.w("commitBitmap: ターゲットエディタはどのMIMEタイプもサポートしていません。")
        } else {
            Timber.d("commitBitmap: ターゲットエディタがサポートするMIMEタイプ: ${supportedMimeTypes.joinToString()}")
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // "image/png"をサポートしているか確認
        val isPngSupported = supportedMimeTypes.any { mimeType ->
            ClipDescription.compareMimeTypes(mimeType, "image/png")
        }
        if (!isPngSupported) {
            Timber.w("commitBitmap: ターゲットエディタは 'image/png' をサポートしていません。")
            // ここで処理を中断するか、別の形式（例: "image/jpeg"）を試すか判断できます
        }

        // 1. Bitmapをキャッシュディレクトリ内のファイルに保存
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs() // ディレクトリが存在することを確認
        val imageFile = File(cachePath, "clipboard_image.png")
        try {
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            // ▼▼▼ ログ追加 ▼▼▼
            Timber.d("commitBitmap: Bitmapをファイルに保存しました: ${imageFile.absolutePath}")
        } catch (e: IOException) {
            Timber.e(e, "Bitmapのファイルへの保存に失敗しました")
            return
        }

        // 2. FileProviderを使用してContent URIを取得
        val contentUri: Uri
        try {
            val authority = "${applicationContext.packageName}.fileprovider"
            contentUri = FileProvider.getUriForFile(this, authority, imageFile)
            // ▼▼▼ ログ追加 ▼▼▼
            Timber.d("commitBitmap: Content URIを取得しました: $contentUri")
        } catch (e: IllegalArgumentException) {
            Timber.e(
                e, "FileProviderが正しく設定されていません。AndroidManifest.xmlを確認してください。"
            )
            return
        }

        // 3. InputContentInfoCompatを作成
        val mimeType = "image/png"
        val description = ClipDescription("Image from keyboard", arrayOf(mimeType))

        // ★★★ 修正点 ★★★
        // linkUri（3番目の引数）にはnullを渡します。
        // この引数はhttp/httpsのウェブURIを要求するため、content:// URIを渡すとクラッシュします。
        val inputContentInfo = InputContentInfoCompat(
            contentUri, description, null // linkUriはnullにする
        )

        // 4. 読み取り権限をターゲットアプリに付与
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION

        // ▼▼▼ ログ追加 ▼▼▼
        Timber.d("commitBitmap: commitContentを呼び出します...")

        // 5. コンテンツをコミット (InputConnectionCompatを使用)
        val didCommit = InputConnectionCompat.commitContent(
            inputConnection,
            editorInfo,
            inputContentInfo,
            flags,
            null // opts (Bundle) は通常nullで問題ありません
        )

        // ▼▼▼ ログ追加 ▼▼▼
        if (didCommit) {
            Timber.d("commitBitmap: コンテンツのコミットに成功しました。")
        } else {
            // このログは元のコードにもありますが、ここに来た場合の直前のログが重要になります
            Timber.e("commitBitmap: コンテンツのコミットに失敗しました。エディタが画像の挿入をサポートしていない可能性があります。")
            commitBitmapViaClipboard(contentUri)
        }
    }

    /**
     * クリップボード経由でBitmapを貼り付けます。
     * commitContentが失敗した場合のフォールバックとして使用します。
     *
     * @param contentUri 貼り付ける画像のContent URI
     */
    private fun commitBitmapViaClipboard(contentUri: Uri) {
        Timber.d("commitBitmapViaClipboard: 開始")
        try {
            val clip = ClipData.newUri(contentResolver, "Image", contentUri)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)

            // 2. ターゲットアプリに読み取り権限を一時的に付与
            // (FileProviderのgrantUriPermissions属性がtrueなら不要な場合もあるが、明示的に行うのが安全)
            grantUriPermission(
                currentInputEditorInfo.packageName,
                contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Timber.d("commitBitmapViaClipboard: クリップボードにコピー完了")

            // 3. 「貼り付け」コマンドを実行
            val didPaste =
                currentInputConnection?.performContextMenuAction(android.R.id.paste) ?: false
            if (didPaste) {
                Timber.d("commitBitmapViaClipboard: 貼り付けコマンドの実行に成功")
            } else {
                Timber.w("commitBitmapViaClipboard: 貼り付けコマンドの実行に失敗")
                // ここでユーザーに「クリップボードにコピーしました。手動で貼り付けてください」と通知するのも良い
            }

        } catch (e: Exception) {
            Timber.e(e, "commitBitmapViaClipboard: 処理中に例外が発生")
        }
    }

    /**
     * ★新しい関数: クリップボードのプレビューUIを更新します。
     * 画像とテキストの両方を判定して、正しくプレビューの状態を設定します。
     */
    private fun updateClipboardPreview() {
        suggestionAdapter?.apply {
            when (val item = clipboardUtil.getPrimaryClipContent()) {
                is ClipboardItem.Image -> {
                    // 画像だった場合
                    setPasteEnabled(true)
                    // ★新しいメソッドを呼び出してBitmapを渡す
                    setClipboardImagePreview(item.bitmap)
                }

                is ClipboardItem.Text -> {
                    // テキストだった場合
                    setPasteEnabled(true)
                    // 既存のメソッドを呼び出す（これにより画像プレビューはクリアされる）
                    setClipboardPreview(item.text)
                }

                is ClipboardItem.Empty -> {
                    // 空だった場合
                    setPasteEnabled(false)
                    setClipboardPreview("")
                }
            }
        }
    }


    private fun dakutenSmallActionForSumire(mainView: MainLayoutBinding) {
        val insertString = inputString.value
        val sb = StringBuilder()
        mainView.keyboardView.let {
            when (it.currentInputMode.value) {
                InputMode.ModeJapanese -> {
                    dakutenSmallLetter(
                        sb, insertString, GestureType.Tap
                    )
                }

                InputMode.ModeEnglish -> {
                    smallConversionEnglish(sb, insertString)
                }

                InputMode.ModeNumber -> {

                }
            }
        }
    }

    private fun smallConversionEnglish(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        }
    }

    private var currentKeyboardOrder = 0

    private fun resetKeyboard() {
        if (keyboardOrder.isEmpty()) return
        currentKeyboardOrder = 0
        showKeyboard(keyboardOrder[0])
    }

    private fun handleLeftCursor(gestureType: GestureType, insertString: String) {
        if (selectMode.value) {
            extendOrShrinkLeftOneChar()
        } else {
            handleLeftKeyPress(gestureType, insertString)
        }
        onLeftKeyLongPressUp.set(true)
    }

    /**
     * テキスト中の「offset」位置から見て、一つ前のグラフェムクラスタ開始位置を返す。
     * 文字列先頭または取得失敗時は 0 を返す。
     */
    private fun previousGraphemeOffset(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val pos = it.preceding(offset)
        return if (pos == BreakIterator.DONE) 0 else pos
    }

    /**
     * テキスト中の「offset」位置から見て、一つ次のグラフェムクラスタ開始位置を返す。
     * 文字列末尾または取得失敗時は text.length を返す。
     */
    private fun nextGraphemeOffset(text: String, offset: Int): Int {
        if (offset >= text.length) return text.length
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val pos = it.following(offset)
        return if (pos == BreakIterator.DONE) text.length else pos
    }

////////////////////////////////////////////////////////////////////////////////
// ─────────────────────────────────────────────────────────────────────────────
//    extendOrShrinkLeftOneChar / extendOrShrinkSelectionRight の修正版
//    （グラフェムクラスタ単位で選択範囲を伸縮）
// ─────────────────────────────────────────────────────────────────────────────
////////////////////////////////////////////////////////////////////////////////

    /** 選択開始時の固定端（アンカー）。-1 は「未設定」を示す */
    private var anchorPos = -1

    /**
     * Shift + ← 相当：左へ「拡張グラフェムクラスタ」1つ分だけ伸ばす／縮める
     */
    private fun extendOrShrinkLeftOneChar() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textStr = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd

        // 0) まだ選択がない（キャレットのみ）
        if (selStart == selEnd) {
            // キャレットが先頭なら何もしない
            if (selStart == 0) return

            // アンカーを現在位置に固定
            anchorPos = selStart

            // 前のグラフェムクラスタ開始位置を取得して選択開始
            val newStart = previousGraphemeOffset(textStr, selStart)

            beginBatchEdit()
            finishComposingText()
            setSelection(newStart, selEnd)
            endBatchEdit()
            return
        }

        // 1) すでに選択がある
        val cursorOnLeft = (anchorPos == selEnd)   // カーソルが選択範囲の左端にあるか
        val cursorOnRight = (anchorPos == selStart) // カーソルが選択範囲の右端にあるか

        when {
            // 1-A: カーソルが左端 → さらに左へ1文字（グラフェム）分伸ばす
            cursorOnLeft -> {
                if (selStart == 0) return
                val newStart = previousGraphemeOffset(textStr, selStart)
                beginBatchEdit()
                finishComposingText()
                setSelection(newStart, selEnd)
                endBatchEdit()
            }

            // 1-B: カーソルが右端 → 右端を1文字（グラフェム）分縮める
            cursorOnRight -> {
                val newEnd = previousGraphemeOffset(textStr, selEnd)
                if (newEnd <= selStart) {
                    // 選択範囲がなくなるのでキャレットのみの状態に戻す
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, selStart)
                    endBatchEdit()
                    anchorPos = -1
                } else {
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, newEnd)
                    endBatchEdit()
                }
            }

            else -> {
                // 状態不整合ならアンカーをリセット
                anchorPos = -1
            }
        }
    }

    /**
     * Shift + → 相当：右へ「拡張グラフェムクラスタ」1つ分だけ伸ばす／縮める
     */
    private fun extendOrShrinkSelectionRight() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textStr = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd
        val textLen = textStr.length

        // 0) まだ選択がない（キャレットのみ）
        if (selStart == selEnd) {
            anchorPos = selStart
            if (selEnd < textLen) {
                val newEnd = nextGraphemeOffset(textStr, selEnd)
                beginBatchEdit()
                finishComposingText()
                setSelection(selStart, newEnd)
                endBatchEdit()
            }
            return
        }

        // 1) すでに選択がある
        val cursorIsOnRight = (anchorPos == selStart)
        if (cursorIsOnRight) {
            // 1-A: カーソルが右端 → さらに右へ1文字（グラフェム）分伸ばす
            if (selEnd < textLen) {
                val newEnd = nextGraphemeOffset(textStr, selEnd)
                beginBatchEdit()
                finishComposingText()
                setSelection(anchorPos, newEnd)
                endBatchEdit()
            }
        } else {
            // 1-B: カーソルが左端 → 左端を1文字（グラフェム）分縮める
            val newStart = nextGraphemeOffset(textStr, selStart)
            if (newStart >= selEnd) {
                // 選択範囲がなくなるのでキャレットのみの状態に戻す
                beginBatchEdit()
                finishComposingText()
                setSelection(selEnd, selEnd)
                endBatchEdit()
                anchorPos = -1
            } else {
                beginBatchEdit()
                finishComposingText()
                setSelection(newStart, selEnd)
                endBatchEdit()
            }
        }
    }

    /**
     * 入力フィールドの全文を全選択する
     */
    private fun selectAllText() {
        if (inputString.value.isNotEmpty()) return
        val request = ExtractedTextRequest()
        // 必要に応じて request.flags を設定（デフォルトで OK）
        val extracted: ExtractedText? = getExtractedText(request, 0)
        val fullText: CharSequence = extracted?.text ?: return
        // 3. テキスト長を取得
        val textLen = fullText.length
        if (textLen == 0) return
        // 4. 選択開始：先頭(0) → 選択終了：全文長
        // ※ beginBatchEdit() / endBatchEdit() で一連の編集をまとめると滑らか
        beginBatchEdit()
        finishComposingText() // もし変換中の文字列があれば確定しておく
        setSelection(0, textLen)
        endBatchEdit()
    }

    private fun clearSelection() {
        // 1. Get the current InputConnection
        val ic = currentInputConnection ?: return

        // 2. Request the extracted text so we know where the selection is
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return

        // 3. Determine where to collapse the cursor.
        //    If there is a selection, `selectionEnd` is the index after the last selected char.
        //    If there is no selection, selStart == selEnd, so this just keeps the cursor where it is.
        val collapsePos = extracted.selectionEnd

        if (collapsePos < 0) return

        // 4. Do a batch edit: finish any composing text, then collapse
        beginBatchEdit()
        finishComposingText()
        ic.setSelection(collapsePos, collapsePos)
        endBatchEdit()
    }

    private fun cancelHenkanByLongPressDeleteKey() {
        val insertString = inputString.value
        val selectedSuggestion = suggestionAdapter?.suggestions?.getOrNull(suggestionClickNum)

        deleteKeyLongKeyPressed.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionClickNum = 0
        isFirstClickHasStringTail = false
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
        isHenkan.set(false)

        val spannableString = if (insertString.length == selectedSuggestion?.length?.toInt()) {
            SpannableString(insertString + stringInTail)
        } else {
            stringInTail.set("")
            SpannableString(insertString)
        }
        setComposingTextAfterEdit(insertString, spannableString)
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            scrollToPosition(0)
        }
    }

    private fun startScope(mainView: MainLayoutBinding) = scope.launch {
        launch {
            var prevFlag: CandidateShowFlag? = null
            suggestionFlag.collectLatest { currentFlag ->
                if (prevFlag == CandidateShowFlag.Idle && currentFlag == CandidateShowFlag.Updating) {
                    when {
                        physicalKeyboardEnable.replayCache.isEmpty() && (mainView.keyboardView.isVisible || mainView.tabletView.isVisible || mainView.qwertyView.isVisible || mainView.customLayoutDefault.isVisible) -> {
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, true
                            )
                        }

                        physicalKeyboardEnable.replayCache.isNotEmpty() && (mainView.keyboardView.isVisible || mainView.tabletView.isVisible || mainView.qwertyView.isVisible || mainView.customLayoutDefault.isVisible) -> {
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, true
                            )
                        }
                    }
                    if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                        mainView.qwertyView.apply {
                            setSpaceKeyText("変換")
                            setReturnKeyText("確定")
                        }
                    }
                    if (mainView.customLayoutDefault.isVisible) {
                        setSumireKeyboardDakutenKey()
                        setSumireKeyboardEnterKey(1)
                        when (mainView.keyboardView.currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                setSumireKeyboardSpaceKey(1)
                            }

                            else -> {}
                        }
                    }
                }
                when (currentFlag) {
                    CandidateShowFlag.Idle -> {
                        suggestionAdapter?.suggestions = emptyList()
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false

                        )
                        if (mainView.customLayoutDefault.isVisible) {
                            resetSumireKeyboardDakutenMode()
                            setSumireKeyboardSpaceKey(0)
                        }
                        suggestionAdapter?.apply {
                            if (deletedBuffer.isEmpty()) {
                                // getPrimaryClipContentでクリップボードの内容を取得
                                when (val item = clipboardUtil.getPrimaryClipContent()) {
                                    is ClipboardItem.Image -> {
                                        // 画像だった場合の処理
                                        setPasteEnabled(true)
                                        // ★新しいメソッドでBitmapをアダプターに渡す
                                        setClipboardImagePreview(item.bitmap)
                                    }

                                    is ClipboardItem.Text -> {
                                        // テキストだった場合の処理
                                        setPasteEnabled(true)
                                        setClipboardPreview(item.text)
                                    }

                                    is ClipboardItem.Empty -> {
                                        // 空だった場合の処理
                                        setPasteEnabled(false)
                                        setClipboardPreview("") // 念のためプレビューもクリア
                                    }
                                }
                            } else {
                                setPasteEnabled(false)
                            }
                        }
                        if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                            mainView.qwertyView.apply {
                                setSpaceKeyText("空白")
                                setReturnKeyText("改行")
                            }
                        }
                    }

                    CandidateShowFlag.Updating -> {
                        val inputString = inputString.value
                        setSuggestionOnView(mainView, inputString)
                    }
                }
                prevFlag = currentFlag
            }
        }

        launch {
            suggestionViewStatus.collectLatest { isVisible ->
                updateSuggestionViewVisibility(mainView, isVisible)
            }
        }

        launch {
            keyboardSymbolViewState.collectLatest { isSymbolKeyboardShow ->
                setKeyboardSize()
                setKeyboardSizeForHeight(mainView)
                mainView.apply {
                    if (isSymbolKeyboardShow) {
                        animateViewVisibility(
                            if (isTablet == true) this.tabletView else this.keyboardView, false
                        )
                        animateViewVisibility(keyboardSymbolView, true)
                        suggestionRecyclerView.isVisible = false
                        setSymbols(mainView)
                        if (customLayoutDefault.isVisible) customLayoutDefault.visibility =
                            View.INVISIBLE
                    } else {
                        animateViewVisibility(
                            if (isTablet == true) this.tabletView else this.keyboardView, true
                        )
                        animateViewVisibility(keyboardSymbolView, false)
                        suggestionRecyclerView.isVisible = true
                        if (customLayoutDefault.isInvisible) customLayoutDefault.visibility =
                            View.VISIBLE
                    }
                }
            }
        }

        launch {
            selectMode.collectLatest { selectMode ->
                mainView.keyboardView.setTextToAllButtonsFromSelectMode(selectMode)
            }
        }

        launch {
            cursorMoveMode.collect { isCursorMoveMode ->
                mainView.keyboardView.setTextToMoveCursorMode(isCursorMoveMode)
            }
        }

        launch {
            qwertyMode.collectLatest {
                when (it) {
                    TenKeyQWERTYMode.Default -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Default, emptyList()
                        )
                        mainView.apply {
                            if (isTablet == true) {
                                tabletView.isVisible = true
                            } else {
                                keyboardView.isVisible = true
                            }
                            qwertyView.isVisible = false
                            customLayoutDefault.isVisible = false
                        }
                    }

                    TenKeyQWERTYMode.TenKeyQWERTY -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.TenKeyQWERTY, emptyList()
                        )
                        mainView.apply {
                            if (isTablet == true) {
                                tabletView.isVisible = false
                            } else {
                                keyboardView.isVisible = false
                            }
                            qwertyView.isVisible = true
                            customLayoutDefault.isVisible = false
                        }
                    }

                    TenKeyQWERTYMode.Custom -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Custom, customLayouts
                        )
                        mainView.apply {
                            if (isTablet == true) {
                                tabletView.isVisible = false
                            } else {
                                keyboardView.isVisible = false
                            }
                            qwertyView.isVisible = false
                            customLayoutDefault.isVisible = true
                        }
                    }

                    TenKeyQWERTYMode.Sumire -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Sumire, emptyList()
                        )
                        mainView.apply {
                            if (isTablet == true) {
                                tabletView.isVisible = false
                            } else {
                                keyboardView.isVisible = false
                            }
                            qwertyView.isVisible = false
                            customLayoutDefault.isVisible = true
                        }
                    }

                    TenKeyQWERTYMode.Number -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Sumire, emptyList()
                        )
                        mainView.apply {
                            if (isTablet == true) {
                                tabletView.isVisible = false
                            } else {
                                keyboardView.isVisible = false
                            }
                            qwertyView.isVisible = false
                            customLayoutDefault.isVisible = true
                        }
                    }
                }
            }
        }

        launch {
            clipboardHistoryRepository.allHistory.collectLatest { historyList ->
                // 1. DBモデルのリストからUIモデルのリストに変換する
                //    このとき、削除に必要な `id` も一緒に渡す
                val uiItems = historyList.map {
                    when (it.itemType) {
                        ItemType.TEXT -> ClipboardItem.Text(id = it.id, text = it.textData ?: "")
                        ItemType.IMAGE -> {
                            it.imageData?.let { bitmap ->
                                ClipboardItem.Image(id = it.id, bitmap = bitmap)
                            } ?: ClipboardItem.Empty
                        }
                    }
                }.filter { it !is ClipboardItem.Empty }

                // 2. 最新のリストをクラスのプロパティにキャッシュする
                currentClipboardItems = uiItems

                // 3. CustomSymbolKeyboardViewの表示を更新する
                //    このメソッドは、クリップボードタブが表示中の場合のみUIを更新する
                mainView.keyboardSymbolView.updateClipboardItems(uiItems)
            }
        }

        launch {
            romajiMapRepository.getActiveMap().map { entity ->
                entity?.mapData ?: romajiMapRepository.getDefaultMapData()
            }.distinctUntilChanged().collectLatest { activeMapData ->
                romajiConverter = RomajiKanaConverter(activeMapData)
            }
        }

        launch {
            keyboardRepository.getLayouts().distinctUntilChanged().collectLatest { layouts ->
                customLayouts = layouts
            }
        }

        launch {
            ngWordRepository.getAllNgWordsFlow().collectLatest { ngWords ->
                _ngWordsList.value = ngWords.distinct()
                _ngPattern.value = ngWords.joinToString("|") { Pattern.quote(it.tango) }.toRegex()
            }
        }

        launch {
            physicalKeyboardEnable.collect { isPhysicalKeyboardEnable ->
                Timber.d("physicalKeyboardEnable: $isPhysicalKeyboardEnable")
                suggestionAdapter?.setPhysicalKeyboardActive(isPhysicalKeyboardEnable)
                if (isPhysicalKeyboardEnable) {
                    suggestionAdapter?.setPhysicalInputModeText(
                        text = when (mainView.keyboardView.currentInputMode.value) {
                            InputMode.ModeJapanese -> "あ"
                            InputMode.ModeEnglish -> "A"
                            InputMode.ModeNumber -> "A"
                        }
                    )
                    (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT
                        params.height = getScreenHeight(this@IMEService)
                        mainView.root.layoutParams = params
                    }
                    mainView.root.alpha = 0f
                    requestCursorUpdates(
                        InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
                    )
                    floatingDockWindow?.apply {
                        if (!this.isShowing) {
                            showAtLocation(
                                mainView.root,
                                Gravity.BOTTOM,
                                0,
                                0
                            )
                        }
                    }

                    listAdapter.updateHighlightPosition(-1)
                    currentHighlightIndex = -1
                    isHenkan.set(false)

                } else {
                    mainView.root.alpha = 1f
                    requestCursorUpdates(0)
                    setKeyboardSize()
                    setKeyboardSizeForHeight(mainView)
                    floatingCandidateWindow?.dismiss()
                    floatingDockWindow?.dismiss()
                }
                Timber.d("isPhysicalKeyboardEnable: $isPhysicalKeyboardEnable")
            }
        }

        launch {
            inputString.collectLatest { string ->
                processInputString(string, mainView)
            }
        }
    }

    private fun setKeyboardSizeForHeight(
        mainView: MainLayoutBinding
    ) {
        val heightPref = appPreference.keyboard_height ?: 280
        val density = resources.displayMetrics.density
        val isPortrait =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val heightPx = when {
            keyboardSymbolViewState.value -> { // Emoji keyboard state
                val height = if (isPortrait) 320 else 220
                (height * density).toInt()
            }

            else -> {
                val clampedHeight = heightPref.coerceIn(180, 420)
                (clampedHeight * density).toInt()
            }
        }
        val keyboardHeight = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (keyboardSymbolViewState.value) heightPx else heightPx + applicationContext.dpToPx(58)
        } else {
            if (isPortrait) {
                if (keyboardSymbolViewState.value) heightPx + applicationContext.dpToPx(50) else heightPx + applicationContext.dpToPx(
                    110
                )
            } else {
                if (keyboardSymbolViewState.value) heightPx else heightPx + applicationContext.dpToPx(
                    65
                )
            }
        }
        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = keyboardHeight
            mainView.root.layoutParams = params
        }
    }

    private fun updateSuggestionViewVisibility(
        mainView: MainLayoutBinding, isVisible: Boolean
    ) {
        val qwertyMode = qwertyMode.value
        animateViewVisibility(
            if (qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY) mainView.qwertyView else if (isTablet == true) mainView.tabletView else mainView.keyboardView,
            isVisible
        )
        animateViewVisibility(mainView.candidatesRowView, !isVisible)
        mainView.candidatesRowView.scrollToPosition(0)
        hideFirstRowCandidatesInFullScreen(mainView)
        if (isVisible) {
            mainLayoutBinding?.apply {
                if (customLayoutDefault.isInvisible) {
                    animateViewVisibility(
                        customLayoutDefault, isVisible = true, true
                    )
                }
            }
        } else {
            mainLayoutBinding?.apply {
                if (customLayoutDefault.isVisible) customLayoutDefault.visibility = View.INVISIBLE
            }
        }

        mainView.suggestionVisibility.apply {
            this.setImageDrawable(if (isVisible) cachedArrowDropDownDrawable else cachedArrowDropUpDrawable)
        }
    }

    private fun animateViewVisibility(
        mainView: View, isVisible: Boolean, withAnimation: Boolean = true
    ) {
        mainView.animate().cancel()

        if (isVisible) {
            mainView.visibility = View.VISIBLE

            if (withAnimation) {
                mainView.translationY = mainView.height.toFloat() // Start from hidden position
                mainView.animate().translationY(0f) // Animate to visible position
                    .setDuration(150).setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else {
                mainView.translationY = 0f
            }
        } else {
            if (withAnimation) {
                mainView.translationY = 0f
                mainView.animate().translationY(mainView.height.toFloat()).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.visibility = View.GONE
                    }.start()
            } else {
                mainView.visibility = View.GONE
            }
        }
    }

    private fun animateSuggestionImageViewVisibility(
        mainView: View, isVisible: Boolean
    ) {
        mainView.post {
            mainView.pivotX = mainView.width / 2f
            mainView.pivotY = mainView.height / 2f

            if (isVisible) {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 0f
                mainView.scaleY = 0f

                mainView.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }.start()
            } else {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 1f
                mainView.scaleY = 1f

                mainView.animate().scaleX(0f).scaleY(0f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.visibility = View.GONE
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }.start()
            }
        }
    }

    private fun hideFirstRowCandidatesInFullScreen(
        mainView: MainLayoutBinding
    ) {
        mainView.candidatesRowView.post {
            if (!mainView.candidatesRowView.canScrollVertically(-1)) {
                val flexboxManager =
                    mainView.candidatesRowView.layoutManager as? FlexboxLayoutManager ?: return@post
                val flexLines = flexboxManager.flexLines

                if (flexLines.isNotEmpty()) {
                    val firstRowHeight = flexLines[0].crossSize
                    mainView.candidatesRowView.scrollBy(0, firstRowHeight)
                }
            }
        }
    }

    private suspend fun processInputString(
        string: String, mainView: MainLayoutBinding,
    ) {
        Timber.d("launchInputString: inputString: $string stringTail: $stringInTail")
        if (string.isNotEmpty()) {
            hasConvertedKatakana = false
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY) {
                handleTenKeyQwertyInput(string)
            } else {
                handleDefaultInput(string)
            }
        } else {
            if (stringInTail.get().isNotEmpty()) {
                setComposingText(stringInTail.get(), 0)
                onLeftKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            } else {
                setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
                onLeftKeyLongPressUp.set(true)
                onRightKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            }
            hasConvertedKatakana = false
            resetInputString()
            initialCursorDetectInFloatingCandidateView = false
            initialCursorXPosition = 0
            if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
                updateSuggestionsForFloatingCandidate(emptyList())
                listAdapter.updateHighlightPosition(-1)
                currentHighlightIndex = -1
            }
            if (isTablet == true) {
                mainView.tabletView.apply {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                }
            } else {
                mainView.keyboardView.apply {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    if (currentInputMode.value == InputMode.ModeNumber) {
                        setBackgroundSmallLetterKey(
                            cachedNumberDrawable
                        )
                    } else {
                        setBackgroundSmallLetterKey(
                            cachedLogoDrawable
                        )
                    }
                }
            }
        }
    }

    /**
     * TenKeyQWERTYモードの入力処理を担当します。
     */
    private suspend fun handleTenKeyQwertyInput(string: String) {
        val spannable = createSpannableWithTail(string)
        _suggestionFlag.emit(CandidateShowFlag.Updating)
        if (!(isLiveConversionEnable == true && isFlickOnlyMode == true)) {
            setComposingTextPreEdit(
                string, spannable
            )
        }
        if (isLiveConversionEnable != true) {
            // ライブ変換が無効な場合は、入力されたテキストをそのまま表示します。
            setComposingTextAfterEdit(string, spannable)
        }
    }

    /**
     * TenKeyQWERTY以外のモードの入力処理を担当します。
     */
    private suspend fun handleDefaultInput(string: String) {
        val spannable = createSpannableWithTail(string)
        if (!(isLiveConversionEnable == true && isFlickOnlyMode == true)) {
            setComposingTextPreEdit(
                string, spannable
            )
        }
        _suggestionFlag.emit(CandidateShowFlag.Updating)
        val timeToDelay = delayTime?.toLong() ?: DEFAULT_DELAY_MS
        delay(timeToDelay)

        // delay中にユーザーの入力が変わった場合は、古い処理を中断します。
        if (inputString.value != string) {
            return
        }

        if (isLiveConversionEnable != true) {
            val shouldCommitOriginalText =
                inputString.value.isNotEmpty() && !isHenkan.get() && !onDeleteLongPressUp.get() && !englishSpaceKeyPressed.get() && !deleteKeyLongKeyPressed.get() && !hasConvertedKatakana

            if (shouldCommitOriginalText) {
                isContinuousTapInputEnabled.set(true)
                lastFlickConvertedNextHiragana.set(true)
                setComposingTextAfterEdit(string, spannable)
            }
        }
    }

    /**
     * サジェスト候補リストの先頭にある文字列を取得し、編集後のテキストとして設定します。
     * このロジックは複数箇所で使われるため、関数として抽出しました。
     */
    private fun applyFirstSuggestion(
        candidate: Candidate
    ) {
        val commitString = if (candidate.type == (15).toByte()) {
            candidate.string.correctReading().first
        } else {
            candidate.string
        }
        val newSpannable = createSpannableWithTail(commitString)
        setComposingTextAfterEdit(commitString, newSpannable)
    }

    /**
     * 末尾文字列を結合したSpannableStringを生成します。
     */
    private fun createSpannableWithTail(text: String): SpannableString {
        return SpannableString(text + stringInTail.get())
    }

    private suspend fun resetInputString() {
        if (!isHenkan.get()) {
            _suggestionFlag.emit(CandidateShowFlag.Idle)
        }
    }

    private fun setCurrentInputType(attribute: EditorInfo?) {
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME(this)
            Timber.d("setCurrentInputType: $currentInputType $inputType")
            if (isTablet == true) {
                mainLayoutBinding?.tabletView?.apply {
                    when (currentInputType) {
                        InputTypeForIME.Text,
                        InputTypeForIME.TextAutoComplete,
                        InputTypeForIME.TextAutoCorrect,
                        InputTypeForIME.TextCapCharacters,
                        InputTypeForIME.TextCapSentences,
                        InputTypeForIME.TextCapWords,
                        InputTypeForIME.TextFilter,
                        InputTypeForIME.TextNoSuggestion,
                        InputTypeForIME.TextPersonName,
                        InputTypeForIME.TextPhonetic,
                        InputTypeForIME.TextWebEditText,
                            -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.TextMultiLine,
                        InputTypeForIME.TextImeMultiLine,
                        InputTypeForIME.TextShortMessage,
                        InputTypeForIME.TextLongMessage,
                            -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedReturnDrawable
                            )
                        }

                        InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedTabDrawable
                            )
                        }

                        InputTypeForIME.TextDone -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedCheckDrawable
                            )
                        }

                        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedSearchDrawable
                            )
                        }

                        InputTypeForIME.TextEditTextInWebView,
                        InputTypeForIME.TextUri,
                        InputTypeForIME.TextPostalAddress,
                        InputTypeForIME.TextWebEmailAddress,
                        InputTypeForIME.TextPassword,
                        InputTypeForIME.TextVisiblePassword,
                        InputTypeForIME.TextWebPassword,
                            -> {
                            currentInputMode.set(InputMode.ModeEnglish)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.Number,
                        InputTypeForIME.NumberDecimal,
                        InputTypeForIME.NumberPassword,
                        InputTypeForIME.NumberSigned,
                        InputTypeForIME.Phone,
                        InputTypeForIME.Date,
                        InputTypeForIME.Datetime,
                        InputTypeForIME.Time,
                            -> {
                            currentInputMode.set(InputMode.ModeNumber)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(false)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                    }
                }
            } else {
                mainLayoutBinding?.keyboardView?.apply {
                    when (currentInputType) {
                        InputTypeForIME.Text,
                        InputTypeForIME.TextAutoComplete,
                        InputTypeForIME.TextAutoCorrect,
                        InputTypeForIME.TextCapCharacters,
                        InputTypeForIME.TextCapSentences,
                        InputTypeForIME.TextCapWords,
                        InputTypeForIME.TextFilter,
                        InputTypeForIME.TextNoSuggestion,
                        InputTypeForIME.TextPersonName,
                        InputTypeForIME.TextPhonetic,
                        InputTypeForIME.TextWebEditText,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.TextMultiLine,
                        InputTypeForIME.TextImeMultiLine,
                        InputTypeForIME.TextShortMessage,
                        InputTypeForIME.TextLongMessage,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedReturnDrawable
                            )
                        }

                        InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedTabDrawable
                            )
                        }

                        InputTypeForIME.TextDone -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedCheckDrawable
                            )
                        }

                        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedSearchDrawable
                            )
                        }

                        InputTypeForIME.TextEditTextInWebView,
                        InputTypeForIME.TextUri,
                        InputTypeForIME.TextPostalAddress,
                        InputTypeForIME.TextWebEmailAddress,
                        InputTypeForIME.TextPassword,
                        InputTypeForIME.TextVisiblePassword,
                        InputTypeForIME.TextWebPassword,
                            -> {
                            setCurrentMode(InputMode.ModeEnglish)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.Number,
                        InputTypeForIME.NumberDecimal,
                        InputTypeForIME.NumberPassword,
                        InputTypeForIME.NumberSigned,
                        InputTypeForIME.Phone,
                        InputTypeForIME.Date,
                        InputTypeForIME.Datetime,
                        InputTypeForIME.Time,
                            -> {
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                        }

                    }
                }
            }
        }
    }

    private fun setSuggestionRecyclerView(
        mainView: MainLayoutBinding,
        flexboxLayoutManagerColumn: FlexboxLayoutManager,
        flexboxLayoutManagerRow: FlexboxLayoutManager
    ) {
        suggestionAdapter?.let { adapter ->
            adapter.setOnItemClickListener { candidate, position ->
                val insertString = inputString.value
                val currentInputMode: InputMode =
                    if (isTablet == true) mainView.tabletView.currentInputMode.get() else mainView.keyboardView.currentInputMode.value
                vibrate()
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position
                )
            }
            adapter.setOnItemLongClickListener { candidate, i ->
                Timber.d("Candidate long tap: $candidate $i")
                val insertString = inputString.value
                if (isNgWordEnable == true) {
                    registerNGWord(
                        insertString = insertString, candidate = candidate, candidatePosition = i
                    )
                }
            }

            adapter.setOnPhysicalKeyboardListener {
                mainView.apply {
                    if (keyboardView.isVisible || customLayoutDefault.isVisible || qwertyView.isVisible || tabletView.isVisible) {
                        suggestionAdapter?.setPhysicalInputModeText(
                            text = when (mainView.keyboardView.currentInputMode.value) {
                                InputMode.ModeJapanese -> "あ"
                                InputMode.ModeEnglish -> "A"
                                InputMode.ModeNumber -> "A"
                            }
                        )
                        hideAllKeyboards()
                        val heightPx = dpToPx(40f)
                        val widthPx = ViewGroup.LayoutParams.MATCH_PARENT
                        (mainView.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                            params.bottomMargin = heightPx
                            mainView.suggestionViewParent.layoutParams = params
                        }
                        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                            params.width = widthPx
                            mainView.root.layoutParams = params
                        }
                    } else {
                        if (keyboardOrder.isEmpty()) return@apply
                        showKeyboard(keyboardOrder[0])
                        setKeyboardSize()
                        suggestionAdapter?.setPhysicalInputModeText("")
                    }
                }
            }

            adapter.setOnItemHelperIconClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        appPreference.undo_enable_preference?.let {
                            if (!it) return@setOnItemHelperIconClickListener
                        }
                        popLastDeletedChar()?.let { c ->
                            commitText(c, 1)
                            suggestionAdapter?.setUndoPreviewText(
                                deletedBuffer.toString()
                            )
                        }
                        if (deletedBuffer.isEmpty()) {
                            suggestionAdapter?.setUndoEnabled(false)
                            updateClipboardPreview()
                            mainView.keyboardView.setSideKeyPreviousDrawable(
                                ContextCompat.getDrawable(
                                    this, com.kazumaproject.core.R.drawable.undo_24px
                                )
                            )
                        }
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        pasteAction()
                    }
                }
            }
            adapter.setOnItemHelperIconLongClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        appPreference.undo_enable_preference?.let {
                            if (!it) return@setOnItemHelperIconLongClickListener
                        }
                        val textToCommit = reverseByGrapheme(deletedBuffer.toString())
                        commitText(textToCommit, 1)
                        clearDeletedBuffer()
                        suggestionAdapter?.setUndoEnabled(false)
                        updateClipboardPreview()
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        clipboardUtil.clearClipboard()
                        adapter.apply {
                            setClipboardPreview("")
                            setPasteEnabled(false)
                        }
                    }
                }
            }
            adapter.setOnCustomLayoutItemClickListener { position ->
                setKeyboardTab(position)
            }
        }
        mainView.suggestionRecyclerView.apply {
            itemAnimator = null
            isFocusable = false
            addOnItemTouchListener(SwipeGestureListener(context = this@IMEService, onSwipeDown = {
                suggestionAdapter?.let { adapter ->
                    if (adapter.suggestions.isNotEmpty() && inputString.value.isNotBlank() && inputString.value.isNotEmpty()) {
                        if (suggestionViewStatus.value) {
                            _suggestionViewStatus.update { false }
                        }
                    }
                }
            }, onSwipeUp = {
                suggestionAdapter?.let { adapter ->
                    if (adapter.suggestions.isNotEmpty() && inputString.value.isNotBlank() && inputString.value.isNotEmpty()) {
                        if (!suggestionViewStatus.value) {
                            _suggestionViewStatus.update { true }
                        }
                    }
                }
            }))
        }

        mainView.candidatesRowView.apply {
            itemAnimator = null
            isFocusable = false
        }

        suggestionAdapter.apply {
            mainView.suggestionRecyclerView.adapter = this
            mainView.suggestionRecyclerView.layoutManager = flexboxLayoutManagerColumn

            mainView.candidatesRowView.adapter = this
            mainView.candidatesRowView.layoutManager = flexboxLayoutManagerRow
        }
        mainView.suggestionVisibility.setOnClickListener {
            _suggestionViewStatus.update { !it }
        }
    }

    private fun setSymbolKeyboard(
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardSymbolView.apply {
            setLifecycleOwner(this@IMEService)
            setOnReturnToTenKeyButtonClickListener(object : ReturnToTenKeyButtonClickListener {
                override fun onClick() {
                    vibrate()
                    _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                    finishComposingText()
                    setComposingText("", 0)
                }
            })
            setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener {
                override fun onClick() {
                    if (!deleteKeyLongKeyPressed.get()) {
                        vibrate()
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    }
                    stopDeleteLongPress()
                }
            })

            setOnDeleteButtonFingerUpListener {
                stopDeleteLongPress()
            }
            setOnDeleteButtonSymbolViewLongClickListener(object :
                DeleteButtonSymbolViewLongClickListener {
                override fun onLongClickListener() {
                    onDeleteLongPressUp.set(true)
                    deleteLongPress()
                    _dakutenPressed.value = false
                    englishSpaceKeyPressed.set(false)
                    deleteKeyLongKeyPressed.set(true)
                }
            })
            /** ここで絵文字を追加 **/
            setOnSymbolRecyclerViewItemClickListener(object : SymbolRecyclerViewItemClickListener {
                override fun onClick(symbol: ClickedSymbol) {
                    vibrate()
                    commitText(symbol.symbol, 1)
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.insert(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnSymbolRecyclerViewItemLongClickListener(object :
                SymbolRecyclerViewItemLongClickListener {
                override fun onLongClick(symbol: ClickedSymbol, position: Int) {
                    vibrate()
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.delete(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnImageItemClickListener { bitmap -> pasteImageAction(bitmap) }
            setOnClipboardItemLongClickListener { item, _ ->
                when (item) {
                    ClipboardItem.Empty -> {}
                    is ClipboardItem.Image -> {
                        vibrate()
                        ioScope.launch {
                            clipboardHistoryRepository.deleteById(item.id)
                        }
                    }

                    is ClipboardItem.Text -> {
                        vibrate()
                        ioScope.launch {
                            clipboardHistoryRepository.deleteById(item.id)
                        }
                    }
                }
            }
            setClipboardHistoryEnabled(isClipboardHistoryFeatureEnabled)
            setOnClipboardHistoryToggleListener(this@IMEService)
        }
    }

    private fun setQWERTYKeyboard(
        mainView: MainLayoutBinding
    ) {
        mainView.qwertyView.apply {
            setOnQWERTYKeyListener(object : QWERTYKeyListener {
                override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) {
                    Timber.d("Pressed Key: $qwertyKey")
                    when (vibrationTimingStr) {
                        "both" -> {
                            vibrate()
                        }

                        "press" -> {
                            vibrate()
                        }

                        "release" -> {

                        }
                    }
                    deleteLongPressJob?.cancel()
                }

                override fun onReleasedQWERTYKey(
                    qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?
                ) {
                    when (vibrationTimingStr) {
                        "both" -> {
                            vibrate()
                        }

                        "press" -> {

                        }

                        "release" -> {

                        }
                    }
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    if (qwertyKey != QWERTYKey.QWERTYKeyDelete) {
                        clearDeletedBuffer()
                        suggestionAdapter?.setUndoEnabled(false)
                    }
                    when (qwertyKey) {
                        QWERTYKey.QWERTYKeyNotSelect -> {}
                        QWERTYKey.QWERTYKeyShift -> {}
                        QWERTYKey.QWERTYKeyDelete -> {
                            if (!deleteKeyLongKeyPressed.get()) {
                                handleDeleteKeyTap(insertString, suggestionList)
                            }
                            stopDeleteLongPress()
                        }

                        QWERTYKey.QWERTYKeySwitchDefaultLayout -> {
                            if (!onKeyboardSwitchLongPressUp) {
                                switchNextKeyboard()
                                _inputString.update { "" }
                                finishComposingText()
                                setComposingText("", 0)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchMode -> {

                        }

                        QWERTYKey.QWERTYKeySpace -> {
                            if (!isSpaceKeyLongPressed) {
                                handleSpaceKeyClickInQWERTY(insertString, mainView, suggestionList)
                            }
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyReturn -> {
                            if (insertString.isNotEmpty()) {
                                handleNonEmptyInputEnterKey(suggestionList, mainView, insertString)
                            } else {
                                handleEmptyInputEnterKey(mainView)
                            }
                        }

                        QWERTYKey.QWERTYKeyCursorLeft -> {
                            Timber.d("QWERTYKey.QWERTYKeyCursorLeft")
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.Tap, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                        }

                        QWERTYKey.QWERTYKeyCursorRight -> {
                            Timber.d("QWERTYKey.QWERTYKeyCursorRight")
                            if (!rightCursorKeyLongKeyPressed.get()) {
                                actionInRightKeyPressed(GestureType.Tap, insertString)
                            }
                            onRightKeyLongPressUp.set(true)
                            rightCursorKeyLongKeyPressed.set(false)
                            rightLongPressJob?.cancel()
                            rightLongPressJob = null
                        }

                        QWERTYKey.QWERTYKeyCursorUp -> {
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.FlickTop, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                        }

                        QWERTYKey.QWERTYKeyCursorDown -> {
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.FlickBottom, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                        }

                        else -> {
                            if (mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                                if (insertString.isNotEmpty()) {
                                    sb.append(insertString).append(tap)
                                    romajiConverter?.let { converter ->
                                        _inputString.update {
                                            converter.convert(sb.toString())
                                        }
                                    }
                                } else {
                                    tap?.let { c ->
                                        romajiConverter?.let { converter ->
                                            _inputString.update {
                                                converter.convert(c.toString())
                                            }
                                        }
                                    }
                                }
                            } else {
                                handleTap(tap, insertString, sb, mainView)
                            }
                            isContinuousTapInputEnabled.set(true)
                            lastFlickConvertedNextHiragana.set(true)
                        }
                    }
                }

                override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) {
                    when (qwertyKey) {
                        QWERTYKey.QWERTYKeyDelete -> {
                            if (isHenkan.get()) {
                                cancelHenkanByLongPressDeleteKey()
                            } else {
                                onDeleteLongPressUp.set(true)
                                deleteLongPress()
                                _dakutenPressed.value = false
                                englishSpaceKeyPressed.set(false)
                                deleteKeyLongKeyPressed.set(true)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchDefaultLayout -> {
                            showListPopup()
                        }

                        QWERTYKey.QWERTYKeySpace -> {
                            if (inputString.value.isEmpty()) {
                                setCursorMode(true)
                                isSpaceKeyLongPressed = true
                            }
                        }

                        else -> {

                        }
                    }
                }
            })
        }
    }

    private suspend fun setSymbols(mainView: MainLayoutBinding) {
        coroutineScope {
            if (cachedEmoji == null || cachedEmoticons == null || cachedSymbols == null) {
                val emojiDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmojiCandidates() }
                val emoticonDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmoticonCandidates() }
                val symbolDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolCandidates() }
                cachedEmoji = emojiDeferred.await()
                cachedEmoticons = emoticonDeferred.await()
                cachedSymbols = symbolDeferred.await()
            }
            val historyDeferred = async(Dispatchers.Default) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = symbolKeyboardFirstItem ?: SymbolMode.EMOJI

        )
    }

    private fun clearSymbols() {
        cachedEmoji = null
        cachedEmoticons = null
        cachedSymbols = null
        cachedClickedSymbolHistory = null
    }

    private fun setCandidateClick(
        candidate: Candidate, insertString: String, currentInputMode: InputMode, position: Int
    ) {
        if (insertString.isNotEmpty()) {
            processCandidate(
                candidate = candidate,
                insertString = insertString,
                currentInputMode = currentInputMode,
                position = position
            )
            setCusrorLeftAfterCloseBracket(candidate.string)
        }
        resetFlagsSuggestionClick()
    }


    /**
     * 削除バッファをまるごとクリアしたいときに呼ぶ
     */
    private fun clearDeletedBuffer() {
        appPreference.undo_enable_preference?.let {
            if (it) {
                deletedBuffer.clear()
                mainLayoutBinding?.keyboardView?.setSideKeyPreviousDrawable(
                    ContextCompat.getDrawable(
                        this, com.kazumaproject.core.R.drawable.undo_24px
                    )
                )
            }
        }
    }

    private fun clearDeletedBufferWithoutResetLayout() {
        appPreference.undo_enable_preference?.let {
            if (it) {
                deletedBuffer.clear()
            }
        }
    }

    private fun reverseByGrapheme(input: String): String {
        appPreference.undo_enable_preference?.let {
            if (!it) return@let
        }
        if (input.isEmpty()) return input

        // BreakIterator を文字（グラフェム）単位で作成
        val it = BreakIterator.getCharacterInstance().also { it.setText(input) }

        // まずはすべてのグラフェムの開始位置をリストに集める
        val boundaries = mutableListOf<Int>()
        var pos = it.first()
        while (pos != BreakIterator.DONE) {
            boundaries.add(pos)
            pos = it.next()
        }
        // boundaries: [0, nextBoundary1, nextBoundary2, ..., input.length]

        // グラフェム単位で部分文字列を取り出し、逆順に連結する
        val sb = StringBuilder(input.length)
        for (i in boundaries.size - 2 downTo 0) {
            val start = boundaries[i]
            val end = boundaries[i + 1]
            sb.append(input.substring(start, end))
        }
        return sb.toString()
    }

    /**
     * サロゲートペア（絵文字）を考慮して、削除バッファの最後の「文字（コードポイント）」を取り出す。
     * 絵文字の場合は2コードユニット、その他は1コードユニットを削除して返す。
     */
    private fun popLastDeletedChar(): String? {
        appPreference.undo_enable_preference?.let {
            if (!it) return@let
        }
        if (deletedBuffer.isEmpty()) return null

        // バッファ全体を String として扱う
        val full = deletedBuffer.toString()
        val endIndex = full.length

        // 最後のグラフェムクラスタ開始位置を BreakIterator で得る
        val startIndex = previousGraphemeOffset(full, endIndex)

        // 「最後の1文字（＝拡張グラフェムクラスタ）」を substring で取り出す
        val lastGrapheme = full.substring(startIndex, endIndex)

        // バッファからその部分を丸ごと削除
        deletedBuffer.delete(startIndex, endIndex)
        return lastGrapheme
    }

    private fun handleExactLengthMatch(
        insertString: String,
        candidateString: String,
        candidate: Candidate,
        currentInputMode: InputMode,
        position: Int
    ) {
        if (!learnMultiple.enabled()) {
            learnMultiple.start()
            learnMultiple.setInput(insertString)
            learnMultiple.setWordToStringBuilder(candidateString)
            upsertLearnDictionaryWhenTapCandidate(
                currentInputMode = currentInputMode,
                insertString = insertString,
                candidate = candidate,
                position = position
            )
        } else {
            learnMultiple.setInput(learnMultiple.getInput() + insertString)
            learnMultiple.setWordToStringBuilder(candidateString)
            upsertLearnDictionaryMultipleTapCandidate(
                currentInputMode = currentInputMode,
                input = learnMultiple.getInput(),
                output = learnMultiple.getInputAndStringBuilder().second,
                candidate = candidate,
                insertString = insertString
            )
        }
        if (stringInTail.get().isNullOrEmpty()) {
            learnMultiple.stop()
        }
    }

    private fun commitAndClearInput(candidateString: String) {
        _inputString.update { "" }
        commitText(candidateString, 1)
    }

    private fun handlePartialOrExcessLength(
        insertString: String, candidateString: String, candidateLength: Int
    ) {
        if (insertString.length > candidateLength) {
            stringInTail.set(insertString.substring(candidateLength))
        }
        commitAndClearInput(candidateString)
    }

    private fun processCandidate(
        candidate: Candidate, insertString: String, currentInputMode: InputMode, position: Int
    ) {
        when (candidate.type.toInt()) {
            15 -> {
                val readingCorrection = candidate.string.correctReading()
                commitAndClearInput(readingCorrection.first)
            }

            9, 11, 12, 13, 14, 28, 30 -> {
                commitAndClearInput(candidate.string)
            }

            else -> {
                if (insertString.length == candidate.length.toInt()) {
                    handleExactLengthMatch(
                        insertString = insertString,
                        candidateString = candidate.string,
                        candidate = candidate,
                        currentInputMode = currentInputMode,
                        position = position
                    )
                } else {
                    handlePartialOrExcessLength(
                        insertString = insertString,
                        candidateString = candidate.string,
                        candidateLength = candidate.length.toInt()
                    )
                }
            }
        }
    }

    private fun upsertLearnDictionaryWhenTapCandidate(
        currentInputMode: InputMode, insertString: String, candidate: Candidate, position: Int
    ) {
        // 1) 学習モードかつ日本語モードかつ position!=0 のみ upsert
        if (currentInputMode == InputMode.ModeJapanese && isLearnDictionaryMode == true && position != 0 && !isPrivateMode) {
            ioScope.launch {
                try {
                    learnRepository.upsertLearnedData(
                        LearnEntity(
                            input = insertString,
                            out = candidate.string,
                            score = ((candidate.score - 500 * position).coerceAtLeast(0)).toShort(),
                            leftId = candidate.leftId,
                            rightId = candidate.rightId
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "upsertLearnDictionary failed")
                }
            }
        }
        // 2) 共通の後処理（入力クリア＋コミット）
        _inputString.update { "" }
        commitText(candidate.string, 1)
    }

    private fun upsertLearnDictionaryMultipleTapCandidate(
        currentInputMode: InputMode,
        input: String,
        output: String,
        candidate: Candidate,
        insertString: String
    ) {
        if (currentInputMode == InputMode.ModeJapanese && isLearnDictionaryMode == true && !isPrivateMode) {
            ioScope.launch {
                try {
                    learnRepository.upsertLearnedData(
                        LearnEntity(
                            input = input,
                            out = output,
                            score = ((candidate.score - 800 * input.length).coerceAtLeast(0)).toShort(),
                            leftId = candidate.leftId,
                            rightId = candidate.rightId
                        )
                    )
                    learnRepository.upsertLearnedData(
                        LearnEntity(
                            input = insertString,
                            out = candidate.string,
                            score = ((candidate.score - 500 * insertString.length).coerceAtLeast(0)).toShort(),
                            leftId = candidate.leftId,
                            rightId = candidate.rightId
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "upsertLearnDictionaryMultipleTap failed")
                }
            }
        }
        // 共通後処理
        _inputString.update { "" }
        commitText(candidate.string, 1)
    }

    private fun resetAllFlags() {
        Timber.d("onUpdate resetAllFlags called")
        _inputString.update { "" }
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
        suggestionAdapter?.suggestions = emptyList()
        stringInTail.set("")
        suggestionClickNum = 0
        isHenkan.set(false)
        isContinuousTapInputEnabled.set(false)
        leftCursorKeyLongKeyPressed.set(false)
        rightCursorKeyLongKeyPressed.set(false)
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isSpaceKeyLongPressed = false
        onKeyboardSwitchLongPressUp = false
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        resetKeyboard()
        _keyboardSymbolViewState.update { false }
        learnMultiple.stop()
        stopDeleteLongPress()
        clearDeletedBuffer()
        suggestionAdapter?.setUndoEnabled(false)
        updateClipboardPreview()
        _selectMode.update { false }
        hasConvertedKatakana = false
        romajiConverter?.clear()
        resetSumireKeyboardDakutenMode()
        initialCursorDetectInFloatingCandidateView = false
        initialCursorXPosition = 0
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        mainLayoutBinding = null
        closeConnection()
        scope.cancel()
        ioScope.cancel()
    }

    private fun resetFlagsSuggestionClick() {
        isHenkan.set(false)
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        _suggestionViewStatus.update { true }
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        _inputString.update { "" }
    }

    private fun resetFlagsEnterKey() {
        isHenkan.set(false)
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        _inputString.update { "" }
    }

    private fun resetFlagsEnterKeyNotHenkan() {
        isHenkan.set(false)
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        _inputString.update { "" }
        stringInTail.set("")
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        learnMultiple.stop()
    }

    private fun resetFlagsKeySpace() {
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        isContinuousTapInputEnabled.set(false)
        lastFlickConvertedNextHiragana.set(false)
        englishSpaceKeyPressed.set(false)
    }

    private fun resetFlagsDeleteKey() {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        isHenkan.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
    }

    private fun setComposingTextPreEdit(
        inputString: String, spannableString: SpannableString
    ) {
        val inputLength = inputString.length
        val tailLength = stringInTail.get().length

        if (isContinuousTapInputEnabled.get() && lastFlickConvertedNextHiragana.get()) {
            spannableString.apply {
                setSpan(
                    BackgroundColorSpan(getColor(com.kazumaproject.core.R.color.green)),
                    0,
                    inputLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
                setSpan(
                    UnderlineSpan(),
                    0,
                    inputLength + tailLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
            }
        } else {
            spannableString.apply {
                setSpan(
                    BackgroundColorSpan(getColor(com.kazumaproject.core.R.color.char_in_edit_color)),
                    0,
                    inputLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
                setSpan(
                    UnderlineSpan(), 0, inputLength + tailLength, Spannable.SPAN_COMPOSING
                )
            }
        }

        Timber.d("launchInputString: setComposingTextPreEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    private fun setComposingTextAfterEdit(
        inputString: String, spannableString: SpannableString
    ) {
        spannableString.apply {
            setSpan(
                BackgroundColorSpan(getColor(com.kazumaproject.core.R.color.blue)),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )
            setSpan(
                UnderlineSpan(),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )
        }
        Timber.d("launchInputString: setComposingTextAfterEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    private fun setEnterKeyAction(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        val index = (suggestionClickNum - 1).coerceAtLeast(0)
        val nextSuggestion = suggestions[index]
        processCandidate(
            candidate = nextSuggestion,
            insertString = insertString,
            currentInputMode = currentInputMode,
            position = index
        )
        resetFlagsEnterKey()
    }

    private fun setTenkeyIconsInHenkan(insertString: String, mainView: MainLayoutBinding) {
        if (isTablet == true) {
            mainView.tabletView.apply {
                when (currentInputMode.get()) {
                    is InputMode.ModeJapanese -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setSideKeyPreviousState(true)
                    }

                    is InputMode.ModeEnglish -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setSideKeyPreviousState(false)
                    }

                    is InputMode.ModeNumber -> {
                        setSideKeyPreviousState(true)
                    }
                }
            }
        } else {
            mainView.keyboardView.apply {
                when (currentInputMode.value) {
                    is InputMode.ModeJapanese -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setSideKeyPreviousState(true)
                        if (insertString.isNotEmpty()) {
                            if (insertString.isNotEmpty() && insertString.last()
                                    .isLatinAlphabet()
                            ) {
                                setBackgroundSmallLetterKey(
                                    cachedEnglishDrawable
                                )
                            } else {
                                setBackgroundSmallLetterKey(
                                    cachedLogoDrawable
                                )
                            }
                        } else {
                            setBackgroundSmallLetterKey(
                                cachedLogoDrawable
                            )
                        }
                    }

                    is InputMode.ModeEnglish -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setBackgroundSmallLetterKey(
                            cachedNumberDrawable
                        )
                        setSideKeyPreviousState(false)
                    }

                    is InputMode.ModeNumber -> {
                        setSideKeyPreviousState(true)
                        if (insertString.isNotEmpty()) {
                            setSideKeySpaceDrawable(
                                cachedHenkanDrawable
                            )
                            if (insertString.last().isHiragana()) {
                                setBackgroundSmallLetterKey(
                                    cachedKanaDrawable
                                )
                            } else {
                                setBackgroundSmallLetterKey(
                                    cachedLogoDrawable
                                )
                            }
                        } else {
                            setSideKeySpaceDrawable(
                                cachedSpaceDrawable
                            )
                            setBackgroundSmallLetterKey(
                                cachedLogoDrawable
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateUIinHenkan(mainView: MainLayoutBinding, insertString: String) {
        if (isTablet == true) {
            mainView.tabletView.apply {
                setSideKeyEnterDrawable(
                    cachedReturnDrawable
                )
                when (currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        setSideKeySpaceDrawable(
                            cachedHenkanDrawable
                        )
                    }

                    InputMode.ModeEnglish -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                    }

                    InputMode.ModeNumber -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                    }
                }
            }
        } else {
            mainView.keyboardView.apply {
                setSideKeyEnterDrawable(
                    cachedReturnDrawable
                )
                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        if (insertString.isNotEmpty() && insertString.last().isHiragana()) {
                            setBackgroundSmallLetterKey(
                                cachedKanaDrawable
                            )
                        } else {
                            setBackgroundSmallLetterKey(
                                cachedLogoDrawable
                            )
                        }
                        setSideKeySpaceDrawable(
                            cachedHenkanDrawable
                        )
                    }

                    InputMode.ModeEnglish -> {

                        if (insertString.isNotEmpty() && insertString.last().isLatinAlphabet()) {
                            setBackgroundSmallLetterKey(
                                cachedEnglishDrawable
                            )
                        } else {
                            setBackgroundSmallLetterKey(
                                cachedLogoDrawable
                            )
                        }
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                    }

                    InputMode.ModeNumber -> {
                        setBackgroundSmallLetterKey(
                            cachedNumberDrawable
                        )
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                    }
                }
            }
        }
    }

    private suspend fun setSuggestionOnView(
        mainView: MainLayoutBinding, inputString: String
    ) {
        if (inputString.isNotEmpty() && suggestionClickNum == 0) {
            setCandidates(mainView, inputString)
        }
    }

    private suspend fun setCandidates(
        mainView: MainLayoutBinding,
        insertString: String,
    ) {
        val candidates = getSuggestionList(insertString)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            updateSuggestionsForFloatingCandidate(filtered.map {
                CandidateItem(
                    word = it.string,
                    length = it.length
                )
            })
        } else {
            suggestionAdapter?.suggestions = filtered
        }
        updateUIinHenkan(mainView, insertString)
        if (isLiveConversionEnable == true && !hasConvertedKatakana) {
            if (isFlickOnlyMode != true) {
                delay(delayTime?.toLong() ?: DEFAULT_DELAY_MS)
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        }
    }

    private suspend fun getSuggestionList(
        insertString: String,
    ): List<Candidate> {
        val resultFromUserDictionary = if (isUserDictionaryEnable == true) {
            withContext(Dispatchers.IO) {
                val prefixMatchNumber = (userDictionaryPrefixMatchNumber ?: 2) - 1
                if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                userDictionaryRepository.searchByReadingPrefixSuspend(
                    prefix = insertString, limit = 4
                ).map {
                    Candidate(
                        string = it.word,
                        type = (28).toByte(),
                        length = (it.reading.length).toUByte(),
                        score = it.posScore
                    )
                }.sortedBy { it.score }
            }
        } else {
            emptyList()
        }

        val resultFromUserTemplate = if (isUserTemplateEnable == true) {
            withContext(Dispatchers.IO) {
                userTemplateRepository.searchByReading(
                    reading = insertString, limit = 8
                ).map {
                    Candidate(
                        string = it.word,
                        type = (30).toByte(),
                        length = (it.reading.length).toUByte(),
                        score = it.posScore
                    )
                }.sortedBy { it.score }
            }
        } else {
            emptyList()
        }
        val ngWords =
            if (isNgWordEnable == true) ngWordsList.value.map { it.tango } else emptyList()
        val engineCandidates = kanaKanjiEngine.getCandidates(
            input = insertString,
            n = nBest ?: 4,
            mozcUtPersonName = mozcUTPersonName,
            mozcUTPlaces = mozcUTPlaces,
            mozcUTWiki = mozcUTWiki,
            mozcUTNeologd = mozcUTNeologd,
            mozcUTWeb = mozcUTWeb,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
            ngWords = ngWords,
            isOmissionSearchEnable = isOmissionSearchEnable ?: false
        )
        val result = resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        return result.filter { candidate ->
            if (ngWords.isEmpty()) {
                true
            } else {
                ngPattern.value.let {
                    !it.containsMatchIn(candidate.string)
                }
            }
        }.distinctBy { it.string }
    }

    private fun deleteLongPress() {
        if (deleteLongPressJob?.isActive == true) return
        val inputStringInBeginning = inputString.value
        deleteLongPressJob = scope.launch {
            while (isActive && deleteKeyLongKeyPressed.get()) {
                val current = inputString.value
                val tailIsEmpty = stringInTail.get().isEmpty()

                if (current.isEmpty()) {
                    if (tailIsEmpty) {
                        appPreference.undo_enable_preference?.let {
                            if (it) {
                                val beforeChar = getLastCharacterAsString(currentInputConnection)
                                if (beforeChar.isNotEmpty()) {
                                    deletedBuffer.append(beforeChar)
                                    if (beforeChar == "ァ゙" || beforeChar == "ィ゙" || beforeChar == "ゥ゙" || beforeChar == "ェ゙" || beforeChar == "ォ゙" || beforeChar == "ッ゙" || beforeChar == "ャ゙" || beforeChar == "ュ゙" || beforeChar == "ョ゙") {
                                        deleteSurroundingTextInCodePoints(2, 0)
                                    }
                                }
                            }
                        }
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    } else {
                        break
                    }
                } else {
                    appPreference.undo_enable_preference?.let {
                        if (it) {
                            val deletedChar = current.last()
                            deletedBuffer.append(deletedChar)
                        }
                    }
                    val newString = current.dropLast(1)
                    _inputString.update { newString }
                    if (newString.isEmpty() && tailIsEmpty) {
                        setComposingText("", 0)
                    }
                }

                delay(LONG_DELAY_TIME)
            }
            // （連続タップ入力解除など）
            enableContinuousTapInput()

            val flag = if (inputString.value.isEmpty()) CandidateShowFlag.Idle
            else CandidateShowFlag.Updating
            _suggestionFlag.emit(flag)

        }
        if (!selectMode.value) {
            deleteLongPressJob?.invokeOnCompletion {
                appPreference.undo_enable_preference?.let {
                    if (it) {
                        if (inputStringInBeginning.isEmpty()) {
                            suggestionAdapter?.apply {
                                suggestionAdapter?.setUndoPreviewText(deletedBuffer.toString())
                                setUndoEnabled(true)
                            }
                            mainLayoutBinding?.keyboardView?.setSideKeyPreviousDrawable(
                                ContextCompat.getDrawable(
                                    this@IMEService,
                                    com.kazumaproject.core.R.drawable.baseline_delete_24
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopDeleteLongPress() {
        deleteKeyLongKeyPressed.set(false)
        onDeleteLongPressUp.set(true)
        deleteLongPressJob?.cancel()
        deleteLongPressJob = null
    }

    private fun enableContinuousTapInput() {
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
    }

    private fun setEnterKeyPress() {
        when (currentInputType) {
            InputTypeForIME.TextMultiLine,
            InputTypeForIME.TextImeMultiLine,
            InputTypeForIME.TextShortMessage,
            InputTypeForIME.TextLongMessage,
                -> {
                commitText("\n", 1)
            }

            InputTypeForIME.None,
            InputTypeForIME.Text,
            InputTypeForIME.TextAutoComplete,
            InputTypeForIME.TextAutoCorrect,
            InputTypeForIME.TextCapCharacters,
            InputTypeForIME.TextCapSentences,
            InputTypeForIME.TextCapWords,
            InputTypeForIME.TextEmailSubject,
            InputTypeForIME.TextFilter,
            InputTypeForIME.TextNoSuggestion,
            InputTypeForIME.TextPersonName,
            InputTypeForIME.TextPhonetic,
            InputTypeForIME.TextWebEditText,
            InputTypeForIME.TextUri,
            InputTypeForIME.TextPostalAddress,
            InputTypeForIME.TextEmailAddress,
            InputTypeForIME.TextWebEmailAddress,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextNotCursorUpdate,
            InputTypeForIME.TextEditTextInWebView,
                -> {
                Timber.d("Enter key: called 3\n")
                sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER
                    )
                )
                sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER
                    )
                )
            }

            InputTypeForIME.TextNextLine -> {
                performEditorAction(EditorInfo.IME_ACTION_NEXT)
            }

            InputTypeForIME.TextDone -> {
                performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                Timber.d(
                    "enter key search: ${EditorInfo.IME_ACTION_SEARCH}" + "\n${currentInputEditorInfo.inputType}" + "\n${currentInputEditorInfo.imeOptions}" + "\n${currentInputEditorInfo.actionId}" + "\n${currentInputEditorInfo.privateImeOptions}"
                )
                performEditorAction(EditorInfo.IME_ACTION_SEARCH)
            }

        }
    }

    private fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>) {
        when {
            insertString.isNotEmpty() -> {
                if (isHenkan.get()) {
                    handleDeleteKeyInHenkan(suggestions, insertString)
                } else {
                    deleteStringCommon(insertString)
                    resetFlagsDeleteKey()
                }
            }

            else -> {
                if (stringInTail.get().isNotEmpty()) return
                if (!selectMode.value) {
                    val beforeChar = getLastCharacterAsString(currentInputConnection)
                    if (beforeChar.isNotEmpty()) {
                        appPreference.undo_enable_preference?.let {
                            if (it) {
                                deletedBuffer.append(beforeChar)
                                mainLayoutBinding?.keyboardView?.setSideKeyPreviousDrawable(
                                    ContextCompat.getDrawable(
                                        this@IMEService,
                                        com.kazumaproject.core.R.drawable.baseline_delete_24
                                    )
                                )
                                suggestionAdapter?.apply {
                                    setUndoEnabled(true)
                                    setUndoPreviewText(deletedBuffer.toString())
                                }
                                if (beforeChar == "ァ゙" || beforeChar == "ィ゙" || beforeChar == "ゥ゙" || beforeChar == "ェ゙" || beforeChar == "ォ゙" || beforeChar == "ッ゙" || beforeChar == "ャ゙" || beforeChar == "ュ゙" || beforeChar == "ョ゙") {
                                    deleteSurroundingTextInCodePoints(2, 0)
                                    return
                                }
                            }
                        }
                    }
                }
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
        }
    }

    private fun handleSpaceKeyClick(
        isFlick: Boolean,
        insertString: String,
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding
    ) {
        if (insertString.isNotBlank()) {
            mainView.apply {
                if (isTablet == true) {
                    tabletView.let { tabletKey ->
                        when (tabletKey.currentInputMode.get()) {
                            InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) handleJapaneseModeSpaceKey(
                                this, suggestions, insertString
                            )

                            else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                        }
                    }
                } else {
                    keyboardView.let { tenkey ->
                        when (tenkey.currentInputMode.value) {
                            InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) handleJapaneseModeSpaceKey(
                                this, suggestions, insertString
                            )

                            else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                        }
                    }
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
        }
        resetFlagsKeySpace()
    }

    private fun handleSpaceKeyClickInQWERTY(
        insertString: String, mainView: MainLayoutBinding, suggestions: List<Candidate>
    ) {
        if (insertString.isNotBlank()) {
            mainView.apply {
                when (mainView.keyboardView.currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        val insertStringEndWithN =
                            romajiConverter?.flush(insertString)?.first
                        if (insertStringEndWithN == null) {
                            _inputString.update { insertString }
                            if (suggestions.isNotEmpty()) handleJapaneseModeSpaceKey(
                                this@apply, suggestions, insertString
                            )
                        } else {
                            _inputString.update { insertStringEndWithN }
                            scope.launch {
                                delay(64)
                                val newSuggestionList =
                                    suggestionAdapter?.suggestions ?: emptyList()
                                if (newSuggestionList.isNotEmpty()) handleJapaneseModeSpaceKey(
                                    this@apply, newSuggestionList, insertStringEndWithN
                                )
                            }
                        }
                    }

                    else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
        }
        resetFlagsKeySpace()
    }


    private fun handleJapaneseModeSpaceKey(
        mainView: MainLayoutBinding, suggestions: List<Candidate>, insertString: String
    ) {
        isHenkan.set(true)
        suggestionClickNum += 1
        suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
        mainView.suggestionRecyclerView.apply {
            smoothScrollToPosition(
                (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
            )
            suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
        }
        setConvertLetterInJapaneseFromButton(suggestions, true, mainView, insertString)
    }

    private fun handleNonEmptyInputEnterKey(
        suggestions: List<Candidate>, mainView: MainLayoutBinding, insertString: String
    ) {
        if (isTablet == true) {
            mainView.tabletView.apply {
                when (val inputMode = currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        if (isHenkan.get()) {
                            handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                        } else {
                            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && mainView.qwertyView.isVisible) {
                                finishInputEnterKey()
                                setCusrorLeftAfterCloseBracket(insertString)
                            } else {
                                finishInputEnterKey()
                                setCusrorLeftAfterCloseBracket(insertString)
                            }
                        }
                    }

                    else -> {
                        finishInputEnterKey()
                        setCusrorLeftAfterCloseBracket(insertString)
                    }
                }
            }
        } else {
            mainView.keyboardView.apply {
                when (val inputMode = currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        if (isHenkan.get()) {
                            handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                        } else {
                            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && mainView.qwertyView.isVisible) {
                                finishInputEnterKey()
                                setCusrorLeftAfterCloseBracket(insertString)
                            } else {
                                finishInputEnterKey()
                                setCusrorLeftAfterCloseBracket(insertString)
                            }
                        }
                    }

                    else -> {
                        finishInputEnterKey()
                        setCusrorLeftAfterCloseBracket(insertString)
                    }
                }
            }
        }
    }

    private fun setCusrorLeftAfterCloseBracket(insertString: String) {
        if (insertString.isOnlyTwoCharBracketPair()) {
            sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                )
            )
        }
    }

    private fun handleDeleteKeyInHenkan(suggestions: List<Candidate>, insertString: String) {
        suggestionClickNum -= 1
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                smoothScrollToPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
                suggestionAdapter?.updateHighlightPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
            }
            setConvertLetterInJapaneseFromButton(suggestions, false, mainView, insertString)
        }
    }

    private fun handleHenkanModeEnterKey(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        if (suggestionClickNum !in suggestions.indices) {
            suggestionClickNum = 0
        }
        setEnterKeyAction(suggestions, currentInputMode, insertString)
    }

    private fun handleEmptyInputEnterKey(mainView: MainLayoutBinding) {
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            setEnterKeyPress()
            isHenkan.set(false)
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun setDrawableToEnterKeyCorrespondingToImeOptions(mainView: MainLayoutBinding) {
        val currentDrawable = when (currentInputType) {
            InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                cachedSearchDrawable
            }

            InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine, InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage -> {
                cachedReturnDrawable
            }

            InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                cachedTabDrawable
            }

            InputTypeForIME.TextDone -> {
                cachedCheckDrawable
            }

            else -> {
                cachedArrowRightDrawable
            }
        }
        if (isTablet == true) {
            mainView.tabletView.setSideKeyEnterDrawable(currentDrawable)
        } else {
            mainView.keyboardView.setSideKeyEnterDrawable(currentDrawable)
        }
    }

    private fun finishInputEnterKey() {
        _inputString.update { "" }
        finishComposingText()
        setComposingText("", 0)
        resetFlagsEnterKeyNotHenkan()
    }

    private fun handleLeftKeyPress(gestureType: GestureType, insertString: String) {
        if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            when (gestureType) {
                GestureType.FlickRight -> {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }

                GestureType.FlickTop -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                }

                GestureType.FlickLeft -> {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }

                GestureType.FlickBottom -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                }

                GestureType.Null -> {}
                GestureType.Down -> {}
                GestureType.Tap -> {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }
            }
        } else if (!isHenkan.get()) {
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            englishSpaceKeyPressed.set(false)
            suggestionClickNum = 0
            if (insertString.isNotEmpty()) {
                val tail = stringInTail.get()
                val stringBuilder = StringBuilder(tail)
                if (insertString.length == 1) {
                    stringInTail.set(stringBuilder.insert(0, insertString.last()).toString())
                    _inputString.update { "" }
                    suggestionAdapter?.suggestions = emptyList()
                } else {
                    stringInTail.set(stringBuilder.insert(0, insertString.last()).toString())
                    _inputString.update { it.dropLast(1) }
                }
            }
        }
    }

    private fun handleLeftLongPress() {
        if (!isHenkan.get()) {
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            onLeftKeyLongPressUp.set(false)
            suggestionClickNum = 0
            asyncLeftLongPress()
        }
    }

    private fun handleRightLongPress() {
        if (!isHenkan.get()) {
            onRightKeyLongPressUp.set(false)
            suggestionClickNum = 0
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            asyncRightLongPress()
        } else {
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        }
    }

    private fun asyncLeftLongPress() {
        if (leftLongPressJob?.isActive == true) return
        leftLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null

            while (isActive && leftCursorKeyLongKeyPressed.get() && !onLeftKeyLongPressUp.get()) {

                val insertString = inputString.value

                // tail があり composing が空 → Idle で抜ける
                if (stringInTail.get().isNotEmpty() && insertString.isEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Idle
                    break
                }

                if (insertString.isNotEmpty()) {
                    updateLeftInputString(insertString)
                } else if (stringInTail.get().isEmpty() && !isCursorAtBeginning()) {
                    if (selectMode.value) {
                        extendOrShrinkLeftOneChar()
                    } else {
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    }
                }

                delay(LONG_DELAY_TIME)
            }
            _suggestionFlag.emit(
                finalSuggestionFlag
                    ?: if (inputString.value.isEmpty()) CandidateShowFlag.Idle else CandidateShowFlag.Updating
            )
        }
    }

    private fun asyncRightLongPress() {
        if (rightLongPressJob?.isActive == true) return
        rightLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null
            while (isActive && rightCursorKeyLongKeyPressed.get() && !onRightKeyLongPressUp.get()) {
                val insertString = inputString.value
                if (stringInTail.get().isEmpty() && insertString.isNotEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Updating
                    break
                }
                actionInRightKeyPressed(insertString)
                delay(LONG_DELAY_TIME)
            }
            _suggestionFlag.emit(
                finalSuggestionFlag
                    ?: if (inputString.value.isNotEmpty()) CandidateShowFlag.Updating else CandidateShowFlag.Idle
            )

        }
    }

    private fun updateLeftInputString(insertString: String) {
        if (insertString.isNotEmpty()) {
            if (insertString.length == 1) {
                stringInTail.set(insertString + stringInTail.get())
                _inputString.update { "" }
                suggestionAdapter?.suggestions = emptyList()
            } else {
                stringInTail.set(insertString.last() + stringInTail.get())
                _inputString.update { it.dropLast(1) }
            }
        }
    }

    private fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) {
        when {
            insertString.isEmpty() -> {
                if (selectMode.value) {
                    extendOrShrinkSelectionRight()
                } else {
                    handleEmptyInputString(gestureType)
                }
            }

            !isHenkan.get() -> handleNonHenkanTap(insertString)
        }
    }

    private fun actionInRightKeyPressed(insertString: String) {
        when {
            insertString.isEmpty() -> handleEmptyInputString()
            !isHenkan.get() -> handleNonHenkan(insertString)
        }
    }

    private fun handleEmptyInputString(gestureType: GestureType) {
        if (stringInTail.get().isEmpty()) {
            when (gestureType) {
                GestureType.FlickRight -> {
                    if (!isCursorAtEnd()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }

                GestureType.FlickTop -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                }

                GestureType.FlickLeft -> {
                    if (!isCursorAtEnd()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }

                GestureType.FlickBottom -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                }

                GestureType.Null -> {}
                GestureType.Down -> {}
                GestureType.Tap -> {
                    if (!isCursorAtEnd()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }
            }
        } else {
            val dropString = stringInTail.get().first()
            stringInTail.set(stringInTail.get().drop(1))
            _inputString.update { dropString.toString() }
        }
    }

    private fun isCursorAtBeginning(): Boolean {
        val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)
        return extractedText?.selectionStart == 0
    }

    private fun isCursorAtEnd(): Boolean {
        if (currentInputConnection != null) {
            val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)
            extractedText?.let {
                val textLength = it.text.length
                val cursorPosition = it.selectionEnd
                return cursorPosition == textLength
            }
        }
        return false
    }

    private fun handleEmptyInputString() {
        if (stringInTail.get().isEmpty()) {
            if (selectMode.value) {
                extendOrShrinkSelectionRight()
            } else {
                if (!isCursorAtEnd()) {
                    sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }
            }
        } else {
            val dropString = stringInTail.get().first()
            stringInTail.set(stringInTail.get().drop(1))
            _inputString.update { dropString.toString() }
        }
    }

    private fun handleNonHenkanTap(insertString: String) {
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionClickNum = 0
        if (stringInTail.get().isNotEmpty()) {
            _inputString.update { insertString + stringInTail.get().first() }
            stringInTail.set(stringInTail.get().drop(1))
        }
    }

    private fun handleNonHenkan(insertString: String) {
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionClickNum = 0
        if (stringInTail.get().isNotEmpty()) {
            _inputString.update { insertString + stringInTail.get()[0] }
            stringInTail.set(stringInTail.get().substring(1))
        }
    }

    private fun appendCharToStringBuilder(
        char: Char, insertString: String, stringBuilder: StringBuilder
    ) {
        if (insertString.length == 1) {
            stringBuilder.append(char)
            _inputString.update { stringBuilder.toString() }
        } else {
            try {
                stringBuilder.append(insertString).deleteCharAt(insertString.lastIndex).append(char)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _inputString.update {
                stringBuilder.toString()
            }
        }
    }

    private fun deleteStringCommon(insertString: String) {
        val length = insertString.length
        when {
            length > 1 -> {
                _inputString.update {
                    it.dropLast(1)
                }
            }

            else -> {
                _inputString.update { "" }
                if (stringInTail.get().isEmpty()) setComposingText("", 0)
            }
        }
    }

    private fun setCurrentInputCharacterContinuous(
        char: Char, insertString: String, sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        if (insertString.isNotEmpty()) {
            sb.append(insertString).append(char)
            _inputString.update {
                sb.toString()
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun setCurrentInputCharacter(
        char: Char, inputForInsert: String, sb: StringBuilder,
    ) {

        if (inputForInsert.isNotEmpty()) {
            val hiraganaAtInsertPosition = inputForInsert.last()
            val nextChar = hiraganaAtInsertPosition.getNextInputChar(char)
            if (nextChar == null) {
                _inputString.update {
                    sb.append(inputForInsert).append(char).toString()
                }
            } else {
                appendCharToStringBuilder(nextChar, inputForInsert, sb)
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun sendCharTap(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                sendKeyChar(charToSend)
            }

            else -> {
                if (isFlickOnlyMode == true) {
                    sendCharFlick(charToSend, insertString, sb)
                    isContinuousTapInputEnabled.set(true)
                    lastFlickConvertedNextHiragana.set(true)
                } else {
                    if (isContinuousTapInputEnabled.get() && lastFlickConvertedNextHiragana.get()) {
                        setCurrentInputCharacterContinuous(
                            charToSend, insertString, sb
                        )
                        lastFlickConvertedNextHiragana.set(false)
                    } else {
                        setKeyTouch(
                            charToSend, insertString, sb
                        )
                    }
                }
            }
        }
    }

    private fun sendCharFlick(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                sendKeyChar(charToSend)
            }

            else -> {
                setCurrentInputCharacterContinuous(
                    charToSend, insertString, sb
                )
            }
        }
    }

    private fun setStringBuilderForConvertStringInHiragana(
        inputChar: Char, sb: StringBuilder, insertString: String
    ) {
        if (insertString.length == 1) {
            sb.append(inputChar)
            _inputString.update {
                sb.toString()
            }
        } else {
            sb.append(insertString).deleteAt(insertString.length - 1).append(inputChar)
            _inputString.update {
                sb.toString()
            }
        }
    }

    private fun dakutenSmallLetter(
        sb: StringBuilder, insertString: String, gestureType: GestureType
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (c.isHiragana()) {
                    when (gestureType) {
                        GestureType.Tap, GestureType.FlickBottom -> {
                            c.getDakutenSmallChar()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        GestureType.FlickLeft -> {
                            c.getDakutenFlickLeft()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        GestureType.FlickRight -> {
                            c.getDakutenFlickRight()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        GestureType.FlickTop -> {
                            c.getDakutenFlickTop()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp && qwertyMode.value != TenKeyQWERTYMode.Custom) {
                switchNextKeyboard()
            }
        }
    }

    // 2) 次のモードに切り替える関数
    fun switchNextKeyboard() {
        if (keyboardOrder.isEmpty()) return

        // モジュール演算で自動的に 0 に戻る
        val nextIndex = (currentKeyboardOrder + 1) % keyboardOrder.size
        val nextType = keyboardOrder[nextIndex]

        // 統一された showKeyboard 関数を呼び出す
        showKeyboard(nextType)

        currentKeyboardOrder = nextIndex

        if (qwertyMode.value == TenKeyQWERTYMode.Number) {
            val type = when (nextType) {
                KeyboardType.TENKEY -> TenKeyQWERTYMode.Default
                KeyboardType.SUMIRE -> TenKeyQWERTYMode.Sumire
                KeyboardType.QWERTY -> TenKeyQWERTYMode.TenKeyQWERTY
                KeyboardType.ROMAJI -> TenKeyQWERTYMode.TenKeyQWERTY
                KeyboardType.CUSTOM -> TenKeyQWERTYMode.Custom
            }
            _tenKeyQWERTYMode.update { type }
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp) {
                switchNextKeyboard()
            }
        }
    }

    private fun handleDakutenSmallLetterKey(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        mainView: MainLayoutBinding,
        gestureType: GestureType
    ) {
        if (isTablet == true) {
            mainView.tabletView.let {
                when (it.currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        dakutenSmallLetter(
                            sb, insertString, gestureType
                        )
                    }

                    InputMode.ModeEnglish -> {
                        smallBigLetterConversionEnglish(sb, insertString)
                    }

                    InputMode.ModeNumber -> {
                        _tenKeyQWERTYMode.update {
                            TenKeyQWERTYMode.TenKeyQWERTY
                        }
                    }
                }
            }
        } else {
            mainView.keyboardView.let {
                when (it.currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        dakutenSmallLetter(
                            sb, insertString, gestureType
                        )
                    }

                    InputMode.ModeEnglish -> {
                        smallBigLetterConversionEnglish(sb, insertString)
                    }

                    InputMode.ModeNumber -> {
                        if (isFlick) {
                            char?.let { c ->
                                sendCharFlick(
                                    charToSend = c, insertString = insertString, sb = sb
                                )
                            }
                            isContinuousTapInputEnabled.set(true)
                            lastFlickConvertedNextHiragana.set(true)
                        } else {
                            char?.let { c ->
                                sendCharTap(
                                    charToSend = c, insertString = insertString, sb = sb
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setKeyTouch(
        key: Char, insertString: String, sb: StringBuilder,
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isContinuousTapInputEnabled.set(false)
        if (isHenkan.get()) {
            finishComposingText()
            setComposingText("", 0)
            _inputString.update {
                key.toString()
            }
            isHenkan.set(false)
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        } else {
            setCurrentInputCharacter(
                key, insertString, sb
            )
        }
    }

    private fun setConvertLetterInJapaneseFromButton(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        mainView: MainLayoutBinding,
        insertString: String
    ) {
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        when {
            !listIterator.hasPrevious() && isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            listIterator.hasNext() && isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }

            listIterator.hasNext() && !isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberNotEmpty(insertString: String) {
        if (stringInTail.get().isNotEmpty()) {
            commitText("$insertString $stringInTail", 1)
            stringInTail.set("")
        } else {
            commitText("$insertString ", 1)
        }
        _inputString.update {
            ""
        }
        if (isHenkan.get()) {
            suggestionAdapter?.suggestions = emptyList()
            isHenkan.set(false)
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberEmpty(isFlick: Boolean) {
        if (stringInTail.get().isNotEmpty()) {
            commitText(" $stringInTail", 1)
            stringInTail.set("")
        } else {
            if (isTablet == true) {
                mainLayoutBinding?.tabletView?.apply {
                    if (currentInputMode.get() == InputMode.ModeJapanese) {
                        if (isFlick) {
                            commitText(" ", 1)
                        } else {
                            commitText("　", 1)
                        }
                    } else {
                        commitText(" ", 1)
                    }
                }
            } else {
                mainLayoutBinding?.keyboardView?.apply {
                    if (currentInputMode.value == InputMode.ModeJapanese) {
                        if (isFlick) {
                            commitText(" ", 1)
                        } else {
                            commitText("　", 1)
                        }
                    } else {
                        commitText(" ", 1)
                    }
                }
            }
        }
        _inputString.update { "" }
        if (isHenkan.get()) {
            suggestionAdapter?.suggestions = emptyList()
            isHenkan.set(false)
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private var isFirstClickHasStringTail = false

    private fun setSuggestionComposingText(suggestions: List<Candidate>, insertString: String) {
        if (suggestionClickNum == 1 && stringInTail.get().isNotEmpty()) {
            isFirstClickHasStringTail = true
        }

        val index = (suggestionClickNum - 1).coerceAtLeast(0)
        if (suggestionClickNum <= 0) suggestionClickNum = 1

        val nextSuggestion = suggestions[index]
        val candidateType = nextSuggestion.type.toInt()
        val suggestionText = nextSuggestion.string
        val suggestionLength = nextSuggestion.length.toInt()

        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            val tail = insertString.substring(suggestionLength)
            if (!isFirstClickHasStringTail) stringInTail.set(tail)
        } else if (candidateType == 15) {
            val (correctedReading) = nextSuggestion.string.correctReading()
            val fullText = correctedReading + stringInTail
            applyComposingText(fullText, correctedReading.length)
            return
        } else {
            if (!isFirstClickHasStringTail) stringInTail.set("")
        }
        val fullText = suggestionText + stringInTail
        applyComposingText(fullText, suggestionText.length)
    }

    private fun applyComposingText(text: String, highlightLength: Int) {
        val spannableString = SpannableString(text)
        spannableString.setSpan(
            BackgroundColorSpan(getColor(com.kazumaproject.core.R.color.orange)),
            0,
            highlightLength,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        setComposingText(spannableString, 1)
    }

    private fun setNextReturnInputCharacter(insertString: String) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        val sb = StringBuilder()
        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                c.getNextReturnInputChar()?.let { charForReturn ->
                    appendCharToStringBuilder(
                        charForReturn, insertString, sb
                    )
                }
            }
        }
    }

    private fun setKeyboardSize() {
        // 1) Early-return if the binding isn't available
        val binding = mainLayoutBinding ?: return

        // 2) Read preferences with the same defaults as the settings screen
        val heightPref = appPreference.keyboard_height ?: 280
        val widthPref = appPreference.keyboard_width ?: 100
        val positionPref = appPreference.keyboard_position ?: true

        // 3) Get screen metrics
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // --- REFACTORED LOGIC ---

        // 4) Determine the final height in pixels
        // This now respects the user's preference for the default keyboard,
        // while still allowing overrides for special keyboards like Emoji or full QWERTY.
        val heightPx = when {
            keyboardSymbolViewState.value -> { // Emoji keyboard state
                val height = if (isPortrait) 320 else 220
                (height * density).toInt()
            }
            // For the default keyboard, use the user's preference.
            // **FIXED**: Clamp the height to the same range as the settings UI (180-280).
            else -> {
                val clampedHeight = heightPref.coerceIn(180, 420)
                (clampedHeight * density).toInt()
            }
        }

        // 5) Determine the final width in pixels
        // **FIXED**: This logic is now simplified and directly reflects the user's percentage
        // choice, removing the complex and incorrect landscape calculation.
        val widthPx = when {
            // Special keyboards and 100% width setting should match the parent
            widthPref == 100 || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || keyboardSymbolViewState.value -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }
            // Otherwise, calculate the width based on the screen percentage
            else -> {
                (screenWidth * (widthPref / 100f)).toInt()
            }
        }

        // 6) Determine gravity for alignment
        val gravity = if (positionPref) {
            Gravity.BOTTOM or Gravity.END
        } else {
            Gravity.BOTTOM or Gravity.START
        }

        // 7) Apply size and gravity to all relevant views
        fun applyHeightAndGravity(view: View) {
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = heightPx
                params.gravity = gravity
                view.layoutParams = params
            }
        }

        // Apply to keyboard views
        if (isTablet == true) {
            applyHeightAndGravity(binding.tabletView)
        } else {
            applyHeightAndGravity(binding.keyboardView)
        }
        applyHeightAndGravity(binding.candidatesRowView)
        applyHeightAndGravity(binding.keyboardSymbolView)
        applyHeightAndGravity(binding.qwertyView)
        applyHeightAndGravity(binding.customLayoutDefault)

        // Adjust the margin for the suggestion view parent
        (binding.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = heightPx
            params.gravity = gravity
            binding.suggestionViewParent.layoutParams = params
        }

        // Finally, update the root view's width and gravity
        (binding.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = widthPx
            params.gravity = gravity
            binding.root.layoutParams = params
        }
    }

    private val vibratorManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        } else null
    }
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else null
    }

    private fun vibrate() {
        if (isVibration == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            val combinedVibration = CombinedVibration.createParallel(vibrationEffect)
            vibratorManager?.vibrate(combinedVibration)
        } else {
            vibrator?.vibrate(2)
        }
    }

    private fun isDevicePhysicalKeyboard(device: InputDevice?): Boolean {
        if (device == null) return false

        // Checks that it's not the software keyboard
        val isNotVirtual = !device.isVirtual

        // Checks that it has keyboard-like buttons
        val hasKeyboardSource = (device.sources and InputDevice.SOURCE_KEYBOARD) != 0

        // THIS IS THE CRITICAL FIX:
        // Checks that it's a full alphabetic keyboard, not just a few buttons.
        val isAlphabetic = device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

        return isNotVirtual && hasKeyboardSource && isAlphabetic
    }

    /**
     * Handles moving to the next page and looping back to the start.
     */
    private fun goToNextPageForFloatingCandidate() {
        if (fullSuggestionsList.isEmpty()) return
        val maxPage = (fullSuggestionsList.size - 1) / PAGE_SIZE
        currentPage = if (currentPage >= maxPage) 0 else currentPage + 1
        currentHighlightIndex = 0
        displayCurrentPage()
    }

    private fun goToPreviousPageForFloatingCandidate() {
        // Only proceed if we are not on the first page
        if (currentPage > 0) {
            currentPage--
            // When moving to a previous page, set the highlight
            // to the last possible position.
            currentHighlightIndex = PAGE_SIZE - 1
            displayCurrentPage()
        }
    }

    private fun checkForPhysicalKeyboard(
        hasPhysicalKeyboard: Boolean
    ) {
        if (hasPhysicalKeyboard) {
            Timber.d("A physical keyboard is connected.")
            floatingDockWindow?.dismiss()
            floatingCandidateWindow?.dismiss()
            scope.launch {
                delay(32)
                _physicalKeyboardEnable.emit(true)
            }
        } else {
            Timber.d("No physical keyboard is connected.")
            floatingDockWindow?.dismiss()
            floatingCandidateWindow?.dismiss()
            scope.launch {
                _physicalKeyboardEnable.emit(false)
            }
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence? {
        return currentInputConnection?.getTextBeforeCursor(p0, p1)
    }

    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence? {
        return currentInputConnection?.getTextAfterCursor(p0, p1)
    }

    override fun getSelectedText(p0: Int): CharSequence? {
        return currentInputConnection?.getSelectedText(p0)
    }

    override fun getCursorCapsMode(p0: Int): Int {
        if (currentInputConnection == null) return 0
        return currentInputConnection.getCursorCapsMode(p0)
    }

    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText? {
        return currentInputConnection.getExtractedText(p0, p1)
    }

    override fun deleteSurroundingText(p0: Int, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.deleteSurroundingText(p0, p1)
    }

    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.deleteSurroundingTextInCodePoints(p0, p1)
    }

    override fun setComposingText(p0: CharSequence?, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.setComposingText(p0, p1)
    }

    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.setComposingRegion(p0, p1)
    }

    override fun finishComposingText(): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.finishComposingText()
    }

    override fun commitText(p0: CharSequence?, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.commitText(p0, p1)
    }

    override fun commitCompletion(p0: CompletionInfo?): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.commitCompletion(p0)
    }

    override fun commitCorrection(p0: CorrectionInfo?): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.commitCorrection(p0)
    }

    override fun setSelection(p0: Int, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.setSelection(p0, p1)
    }

    override fun performEditorAction(p0: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.performEditorAction(p0)
    }

    override fun performContextMenuAction(p0: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.performContextMenuAction(p0)
    }

    override fun beginBatchEdit(): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.endBatchEdit()
    }

    override fun sendKeyEvent(p0: KeyEvent?): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.sendKeyEvent(p0)
    }

    override fun clearMetaKeyStates(p0: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.clearMetaKeyStates(p0)
    }

    override fun reportFullscreenMode(p0: Boolean): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.reportFullscreenMode(p0)
    }

    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.performPrivateCommand(p0, p1)
    }

    override fun requestCursorUpdates(p0: Int): Boolean {
        if (currentInputConnection == null) return false
        return currentInputConnection.requestCursorUpdates(p0)
    }

    override fun getHandler(): Handler? {
        return currentInputConnection?.handler
    }

    override fun closeConnection() {
        if (currentInputConnection == null) return
        return currentInputConnection.closeConnection()
    }

    override fun commitContent(
        inputContent: InputContentInfo, flags: Int, opts: Bundle?
    ): Boolean {
        if (currentInputConnection == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            currentInputConnection.commitContent(inputContent, flags, opts)
        } else {
            false
        }
    }

    override fun onToggled(isEnabled: Boolean) {
        isClipboardHistoryFeatureEnabled = isEnabled
        appPreference.clipboard_history_enable = isEnabled
    }

    override fun onInputDeviceAdded(p0: Int) {
        val device = inputManager.getInputDevice(p0)
        if (isDevicePhysicalKeyboard(device)) {
            Timber.d("Physical keyboard connected: ${device?.name}")
            scope.launch {
                _physicalKeyboardEnable.emit(true)
            }
        }
    }

    override fun onInputDeviceChanged(p0: Int) {
        Timber.d("Input device removed: ID $p0")
        val hasPhysicalKeyboard = inputManager.inputDeviceIds.any { deviceId ->
            isDevicePhysicalKeyboard(inputManager.getInputDevice(deviceId))
        }
        checkForPhysicalKeyboard(hasPhysicalKeyboard)
    }

    override fun onInputDeviceRemoved(p0: Int) {
        val device = inputManager.getInputDevice(p0)
        Timber.d("Input device changed: ${device?.name}")
        scope.launch {
            _physicalKeyboardEnable.emit(false)
        }
    }
}
