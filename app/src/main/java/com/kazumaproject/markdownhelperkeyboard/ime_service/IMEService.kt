package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.katakanaToHiragana
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.physical_shift_key.PhysicalShiftKeyCodeMap
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getLastCharacterAsString
import com.kazumaproject.markdownhelperkeyboard.ime_service.listener.SwipeGestureListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.repository.ClickedSymbolRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.BreakIterator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class IMEService : InputMethodService(), LifecycleOwner, InputConnection {

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
    lateinit var clickedSymbolRepository: ClickedSymbolRepository

    @Inject
    lateinit var clipboardUtil: ClipboardUtil

    @Inject
    lateinit var romajiConverter: RomajiKanaConverter

    private var suggestionAdapter: SuggestionAdapter? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var cachedEmoji: List<Emoji>? = null
    private var cachedEmoticons: List<String>? = null
    private var cachedSymbols: List<String>? = null
    private var cachedClickedSymbolHistory: List<ClickedSymbol>? = null

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
    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private val lastFlickConvertedNextHiragana = AtomicBoolean(false)
    private val isContinuousTapInputEnabled = AtomicBoolean(false)
    private val englishSpaceKeyPressed = AtomicBoolean(false)
    private var suggestionClickNum = 0
    private val isHenkan = AtomicBoolean(false)
    private val onLeftKeyLongPressUp = AtomicBoolean(false)
    private val onRightKeyLongPressUp = AtomicBoolean(false)
    private val onDeleteLongPressUp = AtomicBoolean(false)
    private val deleteKeyLongKeyPressed = AtomicBoolean(false)
    private val rightCursorKeyLongKeyPressed = AtomicBoolean(false)
    private val leftCursorKeyLongKeyPressed = AtomicBoolean(false)
    private var isFlickOnlyMode: Boolean? = false
    private var delayTime: Int? = 1000
    private var isLearnDictionaryMode: Boolean? = false
    private var nBest: Int? = 4
    private var isVibration: Boolean? = true
    private var vibrationTimingStr: String? = "both"
    private var mozcUTPersonName: Boolean? = false
    private var mozcUTPlaces: Boolean? = false
    private var mozcUTWiki: Boolean? = false
    private var mozcUTNeologd: Boolean? = false
    private var mozcUTWeb: Boolean? = false

    private var isTablet: Boolean? = false

    private var isSpaceKeyLongPressed = false
    private val _selectMode = MutableStateFlow(false)
    private val selectMode: StateFlow<Boolean> = _selectMode

    private val _cursorMoveMode = MutableStateFlow(false)
    private val cursorMoveMode: StateFlow<Boolean> = _cursorMoveMode
    private var hasConvertedKatakana = false

    // 1. 削除された文字を蓄積するバッファ
    private val deletedBuffer = StringBuilder()

    private var keyboardOrder: List<KeyboardType> = emptyList()

    private var suggestionCache: MutableMap<String, List<Candidate>>? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry

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
        const val LONG_DELAY_TIME = 64L
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        suggestionAdapter = SuggestionAdapter()
    }

    override fun onCreateInputView(): View? {
        Timber.d("onCreateInputView")
        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        val isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        val ctx = if (isDynamicColorsEnable) {
            DynamicColors.wrapContextIfAvailable(
                applicationContext,
                R.style.Theme_MarkdownKeyboard
            )
        } else {
            ContextThemeWrapper(applicationContext, R.style.Theme_MarkdownKeyboard)
        }
        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))
        return mainLayoutBinding?.root.apply {
            val flexboxLayoutManagerColumn = FlexboxLayoutManager(applicationContext).apply {
                flexDirection = FlexDirection.COLUMN
                justifyContent = JustifyContent.SPACE_AROUND
            }
            val flexboxLayoutManagerRow = FlexboxLayoutManager(applicationContext).apply {
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.FLEX_START
            }
            mainLayoutBinding?.let { mainView ->
                if (isDynamicColorsEnable) {
                    mainView.apply {
                        root.setBackgroundResource(
                            com.kazumaproject.core.R.drawable.keyboard_root_material
                        )
                        suggestionViewParent.setBackgroundResource(
                            com.kazumaproject.core.R.drawable.keyboard_root_material
                        )
                        suggestionVisibility.setBackgroundResource(
                            com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material
                        )
                    }
                }
                setupCustomKeyboardListeners(mainView)
                setSuggestionRecyclerView(
                    mainView, flexboxLayoutManagerColumn, flexboxLayoutManagerRow
                )
                setSymbolKeyboard(mainView)
                setQWERTYKeyboard(mainView)
                if (isTablet == true) {
                    setTabletKeyListeners(mainView)
                } else {
                    setTenKeyListeners(mainView)
                }
                if (lifecycle.currentState == Lifecycle.State.CREATED) {
                    startScope(mainView)
                } else {
                    scope.coroutineContext.cancelChildren()
                    startScope(mainView)
                }
            }
        }

    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Timber.d("onUpdate onStartInput called $restarting")
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
            delayTime = time_same_pronounce_typing_preference ?: 1000
            isLearnDictionaryMode = learn_dictionary_preference ?: true
            nBest = n_best_preference ?: 4
            isVibration = vibration_preference ?: true
            vibrationTimingStr = vibration_timing_preference ?: "both"
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
        Timber.d("onUpdate onStartInputView called $restarting")
        setCurrentInputType(editorInfo)
        setClipboardText()
        setKeyboardSize()
        resetKeyboard()
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
        if (mozcUTPersonName == true) kanaKanjiEngine.releasePersonNamesDictionary()
        if (mozcUTPlaces == true) kanaKanjiEngine.releasePlacesDictionary()
        if (mozcUTWiki == true) kanaKanjiEngine.releaseWikiDictionary()
        if (mozcUTNeologd == true) kanaKanjiEngine.releaseNeologdDictionary()
        if (mozcUTWeb == true) kanaKanjiEngine.releaseWebDictionary()
        isFlickOnlyMode = null
        delayTime = null
        isLearnDictionaryMode = null
        nBest = null
        isVibration = null
        vibrationTimingStr = null
        mozcUTPersonName = null
        mozcUTPlaces = null
        mozcUTWiki = null
        mozcUTNeologd = null
        mozcUTWeb = null
        actionInDestroy()
        System.gc()
        isTablet = null
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
        _keyboardSymbolViewState.update { false }
        _selectMode.update { false }
        _cursorMoveMode.update { false }
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
            if (deletedBuffer.isEmpty() && !clipboardUtil.isClipboardTextEmpty()) {
                clipboardUtil.getFirstClipboardTextOrNull()?.let {
                    setPasteEnabled(true)
                    setClipboardPreview(it)
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
                    val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                    val sb = StringBuilder()

                    Timber.d("onKeyDown: $event")

                    event?.let { e ->
                        if (e.isShiftPressed) {
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
                        }
                    }

                    when (keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            when {
                                insertString.isNotEmpty() -> {
                                    if (isHenkan.get()) {
                                        cancelHenkanByLongPressDeleteKey()
                                        return true
                                    } else {
                                        deleteStringCommon(insertString)
                                        resetFlagsDeleteKey()
                                        event?.let { e ->
                                            romajiConverter.handleDelete(e)
                                        }
                                        return true
                                    }
                                }

                                else -> return super.onKeyDown(keyCode, event)
                            }
                        }

                        KeyEvent.KEYCODE_SPACE -> {
                            handleSpaceKeyClick(false, insertString, suggestions, mainView)
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                                return true
                            } else {
                                handleLeftKeyPress(
                                    GestureType.Tap, insertString
                                )
                                romajiConverter.clear()
                            }
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isHenkan.get() && suggestions.isNotEmpty()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                                return true
                            } else {
                                actionInRightKeyPressed(
                                    GestureType.Tap, insertString
                                )
                                romajiConverter.clear()
                            }
                            return true
                        }

                        KeyEvent.KEYCODE_ENTER -> {
                            if (insertString.isNotEmpty()) {
                                handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                            } else {
                                handleEmptyInputEnterKey(mainView)
                            }
                            romajiConverter.clear()
                            return true
                        }
                    }

                    event?.let { e ->
                        romajiConverter.handleKeyEvent(e).let { romajiResult ->
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
                    }
                    return true
                }

                else -> {
                    return super.onKeyDown(keyCode, event)
                }
            }
        }
        return super.onKeyDown(keyCode, event)
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
            setClipboardText()
        } else if (deletedBuffer.isNotEmpty() && selectMode.value && key == Key.SideKeySpace) {
            clearDeletedBufferWithoutResetLayout()
            suggestionAdapter?.setUndoEnabled(false)
            setClipboardText()
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
                        val hankakuPreference = appPreference.space_hankaku_preference ?: false
                        val isHankaku = isFlick || hankakuPreference
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
                            val selectedText = getSelectedText(0)
                            if (!selectedText.isNullOrEmpty()) {
                                clipboardUtil.setClipBoard(selectedText.toString())
                                suggestionAdapter?.apply {
                                    setPasteEnabled(true)
                                    setClipboardPreview(selectedText.toString())
                                }
                            }
                        }
                        /** 切り取り **/
                        Key.KeySA -> {
                            val selectedText = getSelectedText(0)
                            if (!selectedText.isNullOrEmpty()) {
                                clipboardUtil.setClipBoard(selectedText.toString())
                                suggestionAdapter?.apply {
                                    setPasteEnabled(true)
                                    setClipboardPreview(selectedText.toString())
                                    sendKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL
                                        )
                                    )
                                }
                            }
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
            Key.KeyDakutenSmall -> {}
            Key.SideKeyCursorLeft -> {
                handleLeftLongPress()
                leftCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                suggestionAdapter?.setUndoEnabled(false)
                setClipboardText()
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
                setClipboardText()
            }

            Key.SideKeyDelete -> {
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

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                Timber.d("SideKeySpace LongPress: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
                val insertString = inputString.value
                if (insertString.isNotEmpty()) {
                    mainLayoutBinding?.let {
                        if (it.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                            isSpaceKeyLongPressed = true
                            if (hasConvertedKatakana) {
                                _inputString.update { str -> str.katakanaToHiragana() }
                            } else {
                                _inputString.update { str -> str.hiraganaToKatakana() }
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

            Key.SideKeySymbol -> {}
            else -> {}
        }
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
                    if (isTablet == true) {
                        tabletView.isVisible = true
                        tabletView.resetLayout()
                    } else {
                        keyboardView.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeJapanese)
                    }
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                }

                KeyboardType.QWERTY -> {
                    qwertyView.isVisible = true
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                    keyboardView.setCurrentMode(InputMode.ModeEnglish)
                    qwertyView.setRomajiMode(false)
                }

                KeyboardType.ROMAJI -> {
                    qwertyView.isVisible = true
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                    keyboardView.setCurrentMode(InputMode.ModeJapanese)
                    qwertyView.setRomajiMode(true)
                }

                KeyboardType.SUMIRE -> {
                    // SUMIREキーボードの表示はここだけで行う
                    customKeyboardMode = KeyboardInputMode.HIRAGANA
                    val hiraganaLayout = KeyboardDefaultLayouts.createFinalLayout(
                        KeyboardInputMode.HIRAGANA,
                        dynamicKeyStates = mapOf(
                            "enter_key" to 0,
                            "dakuten_toggle_key" to 0
                        )
                    )
                    customLayoutDefault.setKeyboard(hiraganaLayout)
                    customLayoutDefault.isVisible = true
                    keyboardView.setCurrentMode(InputMode.ModeJapanese)
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
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
        val dynamicStates = mapOf(
            "enter_key" to currentEnterKeyIndex,
            "dakuten_toggle_key" to currentDakutenKeyIndex,
            "space_convert_key" to currentSpaceKeyIndex
        )

        val finalLayout = KeyboardDefaultLayouts.createFinalLayout(
            mode = customKeyboardMode,
            dynamicKeyStates = dynamicStates
        )
        mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
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
                setClipboardText()
            }
        }
    }

    private fun setupCustomKeyboardListeners(mainView: MainLayoutBinding) {
        mainView.customLayoutDefault.setOnKeyboardActionListener(object :
            com.kazumaproject.custom_keyboard.view.FlickKeyboardView.OnKeyboardActionListener {

            override fun onKey(text: String) {
                // 通常の文字が入力された場合（変更なし）
                clearDeleteBufferWithView()
                Timber.d("onKey: $text")
                vibrate()
                val insertString = inputString.value
                val sb = StringBuilder()
                text.forEach {
                    handleFlick(char = it, insertString, sb, mainView)
                }
            }

            override fun onActionLongPress(action: KeyAction) {
                // 特殊キーが長押しされた場合
                // 例: Deleteの長押しで文章を大きく削除する、などの実装が可能
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
                        val insertString = inputString.value
                        if (insertString.isNotEmpty()) {
                            mainLayoutBinding?.let {
                                if (it.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                                    isSpaceKeyLongPressed = true
                                    if (hasConvertedKatakana) {
                                        _inputString.update { str -> str.katakanaToHiragana() }
                                    } else {
                                        _inputString.update { str -> str.hiraganaToKatakana() }
                                    }
                                    hasConvertedKatakana = !hasConvertedKatakana
                                }
                            }
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            _cursorMoveMode.update { true }
                            isSpaceKeyLongPressed = true
                        }
                    }

                    KeyAction.Copy -> {

                    }

                    KeyAction.Delete -> {
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

                    KeyAction.MoveCursorLeft -> {}
                    KeyAction.MoveCursorRight -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}

                    KeyAction.SwitchToNextIme -> {
                        switchNextKeyboard()
                        _inputString.update { "" }
                        finishComposingText()
                        setComposingText("", 0)
                    }

                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
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
                    KeyAction.MoveCursorLeft -> {}
                    KeyAction.MoveCursorRight -> {}
                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
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
                    KeyAction.Convert -> {}
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
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        setClipboardText()
                    }

                    KeyAction.MoveCursorRight -> {
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        setClipboardText()
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

                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire(mainView)
                    }

                    KeyAction.ToggleDakuten -> {

                    }
                }
            }

            override fun onFlickActionUpAfterLongPress(action: KeyAction) {
                vibrate()
                Timber.d("onFlickActionUpAfterLongPress: $action")
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert -> {}
                    KeyAction.Copy -> {}
                    KeyAction.Delete -> {}
                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        onLeftKeyLongPressUp.set(true)
                        leftCursorKeyLongKeyPressed.set(false)
                        leftLongPressJob?.cancel()
                        leftLongPressJob = null
                    }

                    KeyAction.MoveCursorRight -> {
                        onRightKeyLongPressUp.set(true)
                        rightCursorKeyLongKeyPressed.set(false)
                        rightLongPressJob?.cancel()
                        rightLongPressJob = null
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Space -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (cursorMoveMode.value) {
                            _cursorMoveMode.update { false }
                        } else {
                            if (!isSpaceKeyLongPressed) {
                                handleSpaceKeyClick(
                                    true,
                                    insertString,
                                    suggestions,
                                    mainView
                                )
                            }
                        }
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire(mainView)
                    }
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
                        switchNextKeyboard()
                        _inputString.update { "" }
                        finishComposingText()
                        setComposingText("", 0)
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
                                val hankakuPreference =
                                    appPreference.space_hankaku_preference ?: false
                                handleSpaceKeyClick(
                                    hankakuPreference,
                                    insertString,
                                    suggestions,
                                    mainView
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
                        onLeftKeyLongPressUp.set(true)
                        leftCursorKeyLongKeyPressed.set(false)
                        leftLongPressJob?.cancel()
                        leftLongPressJob = null
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        if (!rightCursorKeyLongKeyPressed.get()) {
                            actionInRightKeyPressed(GestureType.Tap, insertString)
                        }
                        onRightKeyLongPressUp.set(true)
                        rightCursorKeyLongKeyPressed.set(false)
                        rightLongPressJob?.cancel()
                        rightLongPressJob = null
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
                }
            }
        })
    }

    private fun pasteAction() {
        val allClipboardText = clipboardUtil.getFirstClipboardTextOrNull() ?: ""
        if (allClipboardText.isNotEmpty()) {
            commitText(allClipboardText, 1)
            clearDeletedBufferWithoutResetLayout()
            suggestionAdapter?.setUndoEnabled(false)
            setClipboardText()
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
            post {
                scrollToPosition(0)
            }
        }
    }

    private fun startScope(mainView: MainLayoutBinding) = scope.launch {
        launch {
            var prevFlag: CandidateShowFlag? = null
            suggestionFlag.collectLatest { currentFlag ->
                if (prevFlag == CandidateShowFlag.Idle && currentFlag == CandidateShowFlag.Updating) {
                    animateSuggestionImageViewVisibility(mainView.suggestionVisibility, true)
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
                        if (clipboardUtil.isClipboardTextEmpty()) {
                            suggestionAdapter?.setPasteEnabled(false)
                        } else {
                            suggestionAdapter?.apply {
                                setPasteEnabled(true)
                                setClipboardPreview(
                                    clipboardUtil.getFirstClipboardTextOrNull() ?: ""
                                )
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
            inputString.collectLatest { string ->
                processInputString(string, mainView)
            }
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

        if (mainView.candidatesRowView.isVisible) {
            mainView.candidatesRowView.scrollToPosition(0)
        }

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

    private suspend fun processInputString(
        string: String, mainView: MainLayoutBinding,
    ) {
        Timber.d("launchInputString: inputString: $string stringTail: $stringInTail")
        if (string.isNotEmpty()) {
            val spannableString = SpannableString(string + stringInTail.get())
            val isQwerty = qwertyMode.value
            if (isQwerty == TenKeyQWERTYMode.TenKeyQWERTY) {
                setComposingTextAfterEdit(string, spannableString)
                _suggestionFlag.emit(CandidateShowFlag.Updating)
                return
            }
            setComposingTextPreEdit(string, spannableString)
            _suggestionFlag.emit(CandidateShowFlag.Updating)
            delay((delayTime ?: 1000).toLong())
            val henkanValue = isHenkan.get()
            val deleteLongPressUp = onDeleteLongPressUp.get()
            val englishSpacePressed = englishSpaceKeyPressed.get()
            val deleteKeyLongPressed = deleteKeyLongKeyPressed.get()
            val inputStringAfterDelay = inputString.value
            if (inputStringAfterDelay != string) return
            if (inputStringAfterDelay.isNotEmpty() && !henkanValue && !deleteLongPressUp && !englishSpacePressed && !deleteKeyLongPressed) {
                isContinuousTapInputEnabled.set(true)
                lastFlickConvertedNextHiragana.set(true)
                setComposingTextAfterEdit(string, spannableString)
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
                            setCurrentMode(InputMode.ModeNumber)
                            setSideKeyPreviousState(false)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
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
                            setClipboardText()
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
                        setClipboardText()
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
                    if (symbol.mode == SymbolMode.EMOJI) {
                        CoroutineScope(Dispatchers.IO).launch {
                            clickedSymbolRepository.insert(
                                mode = symbol.mode, symbol = symbol.symbol
                            )
                        }
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
                            switchNextKeyboard()

                            _inputString.update { "" }
                            finishComposingText()
                            setComposingText("", 0)
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

                        else -> {
                            if (mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                                if (insertString.isNotEmpty()) {
                                    sb.append(insertString).append(tap)
                                    _inputString.update {
                                        romajiConverter.convert(sb.toString())
                                    }
                                } else {
                                    tap?.let { c ->
                                        _inputString.update {
                                            romajiConverter.convert(c.toString())
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
            val historyDeferred = async(Dispatchers.IO) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList()
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

            14 -> {
                commitAndClearInput(candidate.string)
            }

            9, 11, 12, 13 -> {
                upsertLearnDictionaryWhenTapCandidate(
                    currentInputMode = currentInputMode,
                    insertString = insertString,
                    candidate = candidate,
                    position = position
                )
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
        if (currentInputMode == InputMode.ModeJapanese && isLearnDictionaryMode == true && position != 0) {
            ioScope.launch {
                try {
                    learnRepository.upsertLearnedData(
                        LearnEntity(input = insertString, out = candidate.string)
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
        if (currentInputMode == InputMode.ModeJapanese && isLearnDictionaryMode == true) {
            ioScope.launch {
                try {
                    learnRepository.upsertLearnedData(LearnEntity(input = input, out = output))
                    learnRepository.upsertLearnedData(
                        LearnEntity(
                            input = insertString, out = candidate.string
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
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        resetKeyboard()
        _keyboardSymbolViewState.update { false }
        learnMultiple.stop()
        stopDeleteLongPress()
        clearDeletedBuffer()
        suggestionAdapter?.setUndoEnabled(false)
        setClipboardText()
        _selectMode.update { false }
        hasConvertedKatakana = false
        romajiConverter.clear()
        resetSumireKeyboardDakutenMode()
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
            // Use TextUtils.getOffsetBefore to handle surrogate pairs
            val lastCharStart = TextUtils.getOffsetBefore(inputString, inputLength)

            spannableString.apply {
                setSpan(
                    BackgroundColorSpan(getColor(com.kazumaproject.core.R.color.green)),
                    0,
                    lastCharStart,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
                setSpan(
                    BackgroundColorSpan(getColor(com.kazumaproject.core.R.color.char_in_edit_color)),
                    lastCharStart,
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
        suggestionAdapter?.suggestions = filtered
        mainView.suggestionRecyclerView.scrollToPosition(0)
        updateUIinHenkan(mainView, insertString)
    }

    private suspend fun getSuggestionList(
        insertString: String,
    ): List<Candidate> {
        val resultFromLearnDatabase = withContext(Dispatchers.IO) {
            learnRepository.findLearnDataByInput(insertString)?.map {
                Candidate(
                    string = it.out,
                    type = (20).toByte(),
                    length = (insertString.length).toUByte(),
                    score = it.score,
                )
            } ?: emptyList()
        }
        val result = if (isLearnDictionaryMode == true) {
            val resultFromEngine = kanaKanjiEngine.getCandidates(
                input = insertString,
                n = nBest ?: 4,
                mozcUtPersonName = mozcUTPersonName,
                mozcUTPlaces = mozcUTPlaces,
                mozcUTWiki = mozcUTWiki,
                mozcUTNeologd = mozcUTNeologd,
                mozcUTWeb = mozcUTWeb,
            )
            resultFromLearnDatabase + resultFromEngine

        } else {
            kanaKanjiEngine.getCandidates(
                input = insertString,
                n = nBest ?: 4,
                mozcUtPersonName = mozcUTPersonName,
                mozcUTPlaces = mozcUTPlaces,
                mozcUTWiki = mozcUTWiki,
                mozcUTNeologd = mozcUTNeologd,
                mozcUTWeb = mozcUTWeb,
            )
        }
        return result.distinctBy { it.string }
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
                                    if (
                                        beforeChar == "ァ゙" ||
                                        beforeChar == "ィ゙" ||
                                        beforeChar == "ゥ゙" ||
                                        beforeChar == "ェ゙" ||
                                        beforeChar == "ォ゙" ||
                                        beforeChar == "ッ゙" ||
                                        beforeChar == "ャ゙" ||
                                        beforeChar == "ュ゙" ||
                                        beforeChar == "ョ゙"
                                    ) {
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
                                if (beforeChar == "ァ゙" ||
                                    beforeChar == "ィ゙" ||
                                    beforeChar == "ゥ゙" ||
                                    beforeChar == "ェ゙" ||
                                    beforeChar == "ォ゙" ||
                                    beforeChar == "ッ゙" ||
                                    beforeChar == "ャ゙" ||
                                    beforeChar == "ュ゙" ||
                                    beforeChar == "ョ゙"
                                ) {
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
        insertString: String,
        mainView: MainLayoutBinding,
        suggestions: List<Candidate>
    ) {
        if (insertString.isNotBlank()) {
            mainView.apply {
                when (mainView.keyboardView.currentInputMode.value) {
                    InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) handleJapaneseModeSpaceKey(
                        this, suggestions, insertString
                    )

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
                            finishInputEnterKey()
                        }
                    }

                    else -> finishInputEnterKey()
                }
            }
        } else {
            mainView.keyboardView.apply {
                when (val inputMode = currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        if (isHenkan.get()) {
                            handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                        } else {
                            finishInputEnterKey()
                        }
                    }

                    else -> finishInputEnterKey()
                }
            }
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
        sb: StringBuilder,
        insertString: String,
        gestureType: GestureType
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
            switchNextKeyboard()
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
            switchNextKeyboard()
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

    private fun setClipboardText() {
        if (!clipboardUtil.isClipboardTextEmpty()) {
            val clipboardText = clipboardUtil.getFirstClipboardTextOrNull() ?: ""
            suggestionAdapter?.setClipboardPreview(clipboardText)
        }
        suggestionAdapter?.setPasteEnabled(
            !clipboardUtil.isClipboardTextEmpty()
        )
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
            // For special keyboards, use specific, fixed heights
            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY -> {
                val height = if (isPortrait) 230 else 200
                (height * density).toInt()
            }

            keyboardSymbolViewState.value -> { // Emoji keyboard state
                val height = if (isPortrait) 320 else 220
                (height * density).toInt()
            }
            // For the default keyboard, use the user's preference.
            // **FIXED**: Clamp the height to the same range as the settings UI (180-280).
            else -> {
                val clampedHeight = heightPref.coerceIn(180, 280)
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
}
