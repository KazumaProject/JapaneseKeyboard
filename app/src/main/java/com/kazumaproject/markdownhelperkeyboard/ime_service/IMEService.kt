package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
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
import android.view.ContextThemeWrapper
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.listener.SwipeGestureListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
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
    lateinit var clipboardUtil: ClipboardUtil

    private var suggestionAdapter: SuggestionAdapter? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var emojiList: List<String> = emptyList()

    private var emoticonList: List<String> = emptyList()

    private var symbolList: List<String> = emptyList()

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

    // 1. 削除された文字を蓄積するバッファ
    private val deletedBuffer = StringBuilder()

    private var suggestionCache: MutableMap<String, List<Candidate>>? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
    }
    private val cachedLogoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.logo_key)
    }
    private val cachedKanaDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.kana_small)
    }
    private val cachedHenkanDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.henkan)
    }

    private val cachedNumberDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.number_small
        )
    }

    private val cachedArrowDropDownDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24
        )
    }

    private val cachedArrowDropUpDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24
        )
    }

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        )
    }

    private val cachedReturnDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        )
    }

    private val cachedTabDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.keyboard_tab_24px
        )
    }

    private val cachedCheckDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.baseline_check_24
        )
    }

    private val cachedSearchDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.baseline_search_24
        )
    }

    private val cachedEnglishDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext,
            com.kazumaproject.core.R.drawable.english_small
        )
    }

    companion object {
        const val LONG_DELAY_TIME = 64L
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        suggestionAdapter = SuggestionAdapter()
    }

    override fun onCreateInputView(): View? {
        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        val ctx = ContextThemeWrapper(applicationContext, R.style.Theme_MarkdownKeyboard)
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
        if (!clipboardUtil.isClipboardTextEmpty()) {
            val clipboardText = clipboardUtil.getAllClipboardTexts()
            suggestionAdapter?.setClipboardPreview(clipboardText[0])
        }
        suggestionAdapter?.setPasteEnabled(
            !clipboardUtil.isClipboardTextEmpty()
        )
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
        // 1) 変換中 (composing) は IME 側の処理対象外
        if (candidatesStart != -1 || candidatesEnd != -1) return

        if (deletedBuffer.isEmpty() && !clipboardUtil.isClipboardTextEmpty()) {
            val clipboardText = clipboardUtil.getAllClipboardTexts()[0]
            suggestionAdapter?.apply {
                setPasteEnabled(true)
                setClipboardPreview(clipboardText)
            }
        }else{
            suggestionAdapter?.apply {
                setPasteEnabled(false)
            }
        }
        // 2) 状態スナップショット
        val tail = stringInTail.get()
        val hasTail = tail.isNotEmpty()
        val caretTop = (newSelStart == 0 && newSelEnd == 0)

        // 3) caret が先頭かつ tail を抱えている → キャンセル＆リセット
        if (hasTail && caretTop) {
            stringInTail.set("")
            _inputString.update { "" }
            suggestionAdapter?.suggestions = emptyList()
            setComposingText("", 0)
            return
        }

        // 4) caret が移動済みで tail を抱えている → tail を確定
        if (hasTail) {
            _inputString.update { tail }
            stringInTail.set("")
            return
        }

        // 5) tail 無し & _inputString が残っている → 後片付け
        if (inputString.value.isNotEmpty()) {
            _inputString.update { "" }
            setComposingText("", 0)
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
        if (key != Key.SideKeyDelete) {
            clearDeletedBuffer()
            suggestionAdapter?.setUndoEnabled(false)
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
                    mainView = mainView
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
                if (!isSpaceKeyLongPressed) {
                    handleSpaceKeyClick(isFlick, insertString, suggestions, mainView)
                }
                isSpaceKeyLongPressed = false
            }

            Key.SideKeySymbol -> {
                vibrate()
                _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                stringInTail.set("")
                finishComposingText()
                setComposingText("", 0)
                mainView.keyboardSymbolView.setTabPosition(0)
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
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_DEL
                                        )
                                    )
                                }
                            }
                        }
                        /** 全て選択 **/
                        Key.KeyMA -> {
                            selectAllText()
                        }
                        /** 戻る **/
                        Key.KeyRA -> {
                            _selectMode.update { false }
                            clearSelection()
                        }

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
                clearDeletedBuffer()
                suggestionAdapter?.setUndoEnabled(false)
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed.set(true)
                clearDeletedBuffer()
                suggestionAdapter?.setUndoEnabled(false)
            }

            Key.SideKeyDelete -> {
                if (isHenkan.get()) {
                    cancelHenkanByLongPressDeleteKey()
                } else {
                    if (!selectMode.value) {
                        val beforeChar = getTextBeforeCursor(1, 0)?.toString() ?: ""
                        if (beforeChar.isNotEmpty()) {
                            suggestionAdapter?.apply {
                                setUndoPreviewText("")
                                setUndoEnabled(true)
                            }
                        }
                    }
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
                val insertString = inputString.value
                if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                    isSpaceKeyLongPressed = true
                    //showKeyboardPicker()
                    _selectMode.update { true }
                }
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun resetKeyboard() {
        mainLayoutBinding?.apply {
            animateViewVisibility(
                if (isTablet == true) this.tabletView else this.keyboardView,
                true
            )
            animateViewVisibility(this.candidatesRowView, false)
            suggestionRecyclerView.isVisible = true
            if (isTablet == true) {
                tabletView.resetLayout()
            } else {
                keyboardView.apply {
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

    private fun handleLeftCursor(gestureType: GestureType, insertString: String) {
        if (selectMode.value) {
            extendOrShrinkLeftOneChar()
        } else {
            handleLeftKeyPress(gestureType, insertString)
        }
        onLeftKeyLongPressUp.set(true)
    }

    /** 選択開始時の固定端（アンカー）。-1 は「未設定」を示す */
    private var anchorPos = -1

    /**
     * Shift + ← 相当：左へ 1 文字分だけ「伸ばす or 縮める」
     *   - まだ選択が無い       → キャレットの左 1 文字を選択開始
     *   - カーソルが左端にある → さらに左へ 1 文字伸ばす
     *   - カーソルが右端にある → 右端を 1 文字分戻して縮める
     */
    private fun extendOrShrinkLeftOneChar() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd

        // ---- 0) 選択が無い（キャレットのみ）
        if (selStart == selEnd) {
            if (selStart == 0) return               // 先頭なら何もしない
            anchorPos = selStart                    // 基点を保存
            beginBatchEdit()
            finishComposingText()
            setSelection(selStart - 1, selEnd)      // 左 1 文字を選択
            endBatchEdit()
            return
        }

        // ---- 1) 既に選択がある
        val cursorOnLeft = (anchorPos == selEnd)   // true: カーソル=左端
        val cursorOnRight = (anchorPos == selStart) // true: カーソル=右端

        when {
            cursorOnLeft -> {  // さらに左へ伸ばす
                if (selStart == 0) return
                beginBatchEdit()
                finishComposingText()
                setSelection(selStart - 1, selEnd)
                endBatchEdit()
            }

            cursorOnRight -> { // 右端から 1 文字戻して縮める
                if (selEnd - 1 <= selStart) {
                    // 選択が無くなるので解除＋アンカーリセット
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, selStart)
                    endBatchEdit()
                    anchorPos = -1
                } else {
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, selEnd - 1)
                    endBatchEdit()
                }
            }

            else -> {
                // 不整合時はアンカーをリセット
                anchorPos = -1
            }
        }
    }

    /** Shift+→ を押した（あるいは同等の操作が来た）ときに呼ぶ */
    private fun extendOrShrinkSelectionRight() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd
        val textLen = extracted.text?.length ?: return

        // ----- 0) 選択が無い（カーソルだけ）の状態 -----
        if (selStart == selEnd) {
            anchorPos = selStart                    // ここを基点として保存
            if (selEnd < textLen) {
                beginBatchEdit()
                finishComposingText()
                setSelection(selStart, selEnd + 1)  // 1 文字だけ選択開始
                endBatchEdit()
            }
            return
        }

        // ----- 1) 既に選択がある状態 -----
        // アンカーがどちら側かで分岐
        val cursorIsOnRight = (anchorPos == selStart)   // true: カーソル=右端
        if (cursorIsOnRight) {
            // ---- 1-A カーソルが右端 → さらに右へ伸ばす ----
            if (selEnd < textLen) {
                beginBatchEdit()
                finishComposingText()
                setSelection(anchorPos, selEnd + 1)
                endBatchEdit()
            }
        } else {
            // ---- 1-B カーソルが左端 → 左から 1 文字詰めて縮める ----
            if (selStart + 1 >= selEnd) {
                // 縮め切ったので選択解除
                beginBatchEdit()
                finishComposingText()
                setSelection(selEnd, selEnd)        // キャレットを右端に
                endBatchEdit()
                anchorPos = -1                      // アンカーもクリア
            } else {
                beginBatchEdit()
                finishComposingText()
                setSelection(selStart + 1, selEnd)  // 左端だけ +1
                endBatchEdit()
            }
        }
    }

    /**
     * 入力フィールドの全文を全選択する
     */
    private fun selectAllText() {
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

    fun clearSelection() {
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
                }
                when (currentFlag) {
                    CandidateShowFlag.Idle -> {
                        suggestionAdapter?.suggestions = emptyList()
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false
                        )
                        if (clipboardUtil.isClipboardTextEmpty()) {
                            suggestionAdapter?.setPasteEnabled(false)
                        } else {
                            suggestionAdapter?.apply {
                                setPasteEnabled(true)
                                setClipboardPreview(clipboardUtil.getAllClipboardTexts()[0])
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
            _keyboardSymbolViewState.asStateFlow().collectLatest { isSymbolKeyboardShow ->
                mainView.apply {
                    if (isSymbolKeyboardShow) {
                        animateViewVisibility(
                            if (isTablet == true) this.tabletView else this.keyboardView,
                            false
                        )
                        animateViewVisibility(keyboardSymbolView, true)
                        suggestionRecyclerView.isVisible = false
                        setSymbols(mainView)
                    } else {
                        animateViewVisibility(
                            if (isTablet == true) this.tabletView else this.keyboardView,
                            true
                        )
                        animateViewVisibility(keyboardSymbolView, false)
                        suggestionRecyclerView.isVisible = true
                        clearSymbols()
                    }
                }
            }
        }

        launch {
            qwertyMode.collectLatest { state ->
                setKeyboardSize()
                when (state) {
                    TenKeyQWERTYMode.Default -> {
                        if (isTablet == true) {
                            mainView.tabletView.isVisible = true
                        } else {
                            mainView.keyboardView.isVisible = true
                        }
                        mainView.qwertyView.isVisible = false
                    }

                    TenKeyQWERTYMode.TenKeyQWERTY -> {
                        mainView.qwertyView.isVisible = true
                        if (isTablet == true) {
                            mainView.tabletView.isVisible = false
                        } else {
                            mainView.keyboardView.isVisible = false
                        }
                    }
                }
            }
        }

        launch {
            selectMode.collectLatest { selectMode ->
                mainView.keyboardView.setTextToAllButtons(selectMode)
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
            if (qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY) mainView.qwertyView else
                if (isTablet == true) mainView.tabletView else mainView.keyboardView,
            isVisible
        )
        animateViewVisibility(mainView.candidatesRowView, !isVisible)

        if (mainView.candidatesRowView.isVisible) {
            mainView.candidatesRowView.scrollToPosition(0)
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
            adapter.setOnItemLongClickListener { candidate, _ ->
                val insertString = inputString.value
                setCandidateClipboardLongClick(candidate, insertString)
            }
            adapter.setOnItemHelperIconClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        popLastDeletedChar()?.let { c ->
                            commitText(c.toString(), 1)
                            suggestionAdapter?.setUndoPreviewText(
                                deletedBuffer.toString()
                            )
                        }
                        if (deletedBuffer.isEmpty()) {
                            suggestionAdapter?.setUndoEnabled(false)
                        }
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        val allClipboardTexts = clipboardUtil.getAllClipboardTexts()
                        if (allClipboardTexts.isNotEmpty()) {
                            commitText(allClipboardTexts[0], 1)
                            clearDeletedBuffer()
                            suggestionAdapter?.setUndoEnabled(false)
                        }
                    }
                }
            }
            adapter.setOnItemHelperIconLongClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        commitText(deletedBuffer.reverse().toString(), 1)
                        clearDeletedBuffer()
                        suggestionAdapter?.setUndoEnabled(false)
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
            setOnSymbolRecyclerViewItemClickListener(object : SymbolRecyclerViewItemClickListener {
                override fun onClick(symbol: String) {
                    vibrate()
                    commitText(symbol, 1)
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
                    qwertyKey: QWERTYKey,
                    tap: Char?,
                    variations: List<Char>?
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
                            _tenKeyQWERTYMode.update {
                                TenKeyQWERTYMode.Default
                            }
                            _inputString.update { "" }
                            finishComposingText()
                            setComposingText("", 0)
                        }

                        QWERTYKey.QWERTYKeySwitchMode -> {

                        }

                        QWERTYKey.QWERTYKeySpace -> {
                            if (!isSpaceKeyLongPressed) {
                                handleSpaceKeyClickInQWERTY(insertString, mainView)
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
                            isContinuousTapInputEnabled.set(true)
                            lastFlickConvertedNextHiragana.set(true)
                            handleTap(tap, insertString, sb, mainView)
                        }
                    }
                }

                override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) {
                    when (qwertyKey) {
                        QWERTYKey.QWERTYKeyDelete -> {
                            val beforeChar = getTextBeforeCursor(1, 0)?.toString() ?: ""
                            if (beforeChar.isNotEmpty()) {
                                suggestionAdapter?.setUndoEnabled(true)
                            }
                            onDeleteLongPressUp.set(true)
                            deleteLongPress()
                            _dakutenPressed.value = false
                            englishSpaceKeyPressed.set(false)
                            deleteKeyLongKeyPressed.set(true)
                        }

                        else -> {

                        }
                    }
                }
            })
        }
    }

    private suspend fun setSymbols(mainView: MainLayoutBinding) {
        emojiList = withContext(Dispatchers.Default) {
            kanaKanjiEngine.getSymbolEmojiCandidates()
        }
        emoticonList = withContext(Dispatchers.Default) {
            kanaKanjiEngine.getSymbolEmoticonCandidates()
        }
        symbolList = withContext(Dispatchers.Default) {
            kanaKanjiEngine.getSymbolCandidates()
        }
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList, emoticonList, symbolList, mainView.keyboardSymbolView.getTabPosition()
        )
    }

    private suspend fun clearSymbols() {
        emojiList = withContext(Dispatchers.Default) {
            emptyList()
        }
        emoticonList = withContext(Dispatchers.Default) {
            emptyList()
        }
        symbolList = withContext(Dispatchers.Default) {
            emptyList()
        }
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
        } else {
            commitClipboardText(
                candidate = candidate,
                insertString = insertString,
            )
        }
        resetFlagsSuggestionClick()
    }


    /**
     * 削除バッファをまるごとクリアしたいときに呼ぶ
     */
    fun clearDeletedBuffer() {
        deletedBuffer.clear()
    }

    /**
     * 直近で削除された文字を取得しつつ、バッファからも取り除く。
     * 取り除くとき、新たに最後に削除された文字が変わるので注意。
     */
    private fun popLastDeletedChar(): Char? {
        if (deletedBuffer.isEmpty()) return null
        val lastIndex = deletedBuffer.lastIndex
        val c = deletedBuffer[lastIndex]
        deletedBuffer.deleteCharAt(lastIndex)
        return c
    }

    private fun setCandidateClipboardLongClick(
        candidate: Candidate, insertString: String,
    ) {
        if (insertString.isEmpty() && candidate.type.toInt() == 28) {
            clipboardUtil.clearClipboard()
            suggestionAdapter?.suggestions = emptyList()
        }
        resetFlagsSuggestionClick()
    }

    private fun commitClipboardText(
        candidate: Candidate, insertString: String,
    ) {
        processClipboardCandidate(
            candidate = candidate,
            insertString = insertString,
        )
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

    private fun processClipboardCandidate(
        candidate: Candidate, insertString: String,
    ) {
        if (candidate.type.toInt() == 28) {
            handlePartialOrExcessLength(
                insertString = insertString,
                candidateString = candidate.string,
                candidateLength = candidate.length.toInt()
            )
            clipboardUtil.clearClipboard()
            suggestionAdapter?.suggestions = emptyList()
        }
    }

    private fun upsertLearnDictionaryWhenTapCandidate(
        currentInputMode: InputMode,
        insertString: String,
        candidate: Candidate,
        position: Int
    ) {
        // 1) 学習モードかつ日本語モードかつ position!=0 のみ upsert
        if (currentInputMode == InputMode.ModeJapanese
            && isLearnDictionaryMode == true
            && position != 0
        ) {
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
                            input = insertString,
                            out = candidate.string
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
        _keyboardSymbolViewState.value = false
        learnMultiple.stop()
        stopDeleteLongPress()
        clearDeletedBuffer()
        suggestionAdapter?.setUndoEnabled(false)
        _selectMode.update { false }
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
                        val beforeChar = getTextBeforeCursor(1, 0)?.toString()
                        if (!beforeChar.isNullOrEmpty()) {
                            deletedBuffer.append(beforeChar)
                        }
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    } else {
                        break
                    }
                } else {
                    val deletedChar = current.last()
                    deletedBuffer.append(deletedChar)
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

            val flag = if (inputString.value.isEmpty())
                CandidateShowFlag.Idle
            else
                CandidateShowFlag.Updating
            _suggestionFlag.emit(flag)

        }
        if (!selectMode.value) {
            deleteLongPressJob?.invokeOnCompletion {
                scope.launch(Dispatchers.Main) {
                    if (inputStringInBeginning.isEmpty()) {
                        suggestionAdapter?.apply {
                            suggestionAdapter?.setUndoPreviewText(deletedBuffer.toString())
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
                    val beforeChar = getTextBeforeCursor(1, 0)?.toString() ?: ""
                    if (beforeChar.isNotEmpty()) {
                        deletedBuffer.append(beforeChar)
                        suggestionAdapter?.apply {
                            setUndoEnabled(true)
                            setUndoPreviewText(deletedBuffer.toString())
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
        mainView: MainLayoutBinding
    ) {
        if (insertString.isNotBlank()) {
            mainView.apply {
                setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
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

    private fun switchToQWERTY() {
        setKeyboardSize()
        mainLayoutBinding?.let { mainView ->
            mainView.apply {
                qwertyView.isVisible = true
                keyboardView.isVisible = false
                tabletView.isVisible = false
                keyboardSymbolView.isVisible = false
            }
        }
    }

    private fun switchToTenKey() {
        setKeyboardSize()
        mainLayoutBinding?.let { mainView ->
            mainView.apply {
                qwertyView.isVisible = false
                keyboardView.isVisible = true
            }
        }
    }

    private fun switchToTabletKey() {
        setKeyboardSize()
        mainLayoutBinding?.let { mainView ->
            mainView.apply {
                tabletView.isVisible = false
                keyboardView.isVisible = true
            }
        }
    }

    private fun dakutenSmallLetter(
        sb: StringBuilder, insertString: String, mainView: MainLayoutBinding
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        } else {
            _tenKeyQWERTYMode.update {
                TenKeyQWERTYMode.TenKeyQWERTY
            }
            mainView.qwertyView.resetQWERTYKeyboard()
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder, insertString: String, mainView: MainLayoutBinding
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
            _tenKeyQWERTYMode.update {
                TenKeyQWERTYMode.TenKeyQWERTY
            }
            mainView.qwertyView.resetQWERTYKeyboard()
        }
    }

    private fun handleDakutenSmallLetterKey(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        mainView: MainLayoutBinding
    ) {
        if (isTablet == true) {
            mainView.tabletView.let {
                when (it.currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        dakutenSmallLetter(sb, insertString, mainView)
                    }

                    InputMode.ModeEnglish -> {
                        smallBigLetterConversionEnglish(sb, insertString, mainView)
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
                        dakutenSmallLetter(sb, insertString, mainView)
                    }

                    InputMode.ModeEnglish -> {
                        smallBigLetterConversionEnglish(sb, insertString, mainView)
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
        // 1) Early-return if there's no binding
        val binding = mainLayoutBinding ?: return

        // 2) Read preferences (with defaults)
        val heightPref = appPreference.keyboard_height ?: 280
        val widthPref = appPreference.keyboard_width ?: 100
        val positionPref = appPreference.keyboard_position ?: true

        // 3) Screen metrics
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val isPortrait =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // 4) Clamp height: [210..280] in portrait, ≤ 200 in landscape
        val clampedHeight = if (isPortrait) {
            heightPref.coerceIn(210, 280)
        } else {
            heightPref.coerceAtMost(200)
        }

        val qwertyMode = qwertyMode.value
        val heightPx = if (qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY) {
            if (isPortrait) {
                (280 * density).toInt()
            } else {
                (200 * density).toInt()
            }
        } else {
            (clampedHeight * density).toInt()
        }
        // 5) Compute width in px (or MATCH_PARENT) based on orientation/tablet
        val widthPx = if (isPortrait) {
            if (qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                if (widthPref == 100) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    (screenWidth * (widthPref / 100f)).toInt()
                }
            }
        } else {
            if (qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                if (isTablet == true) {
                    if (widthPref == 100) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        (screenWidth * (widthPref / 100f)).toInt()
                    }
                } else {
                    // On non-tablet landscape, base width is 80% of screen
                    val baseWidth = (screenWidth * 0.8).toInt()
                    if (widthPref == 100) {
                        baseWidth
                    } else {
                        (baseWidth * (widthPref / 100f)).toInt()
                    }
                }
            }
        }

        // 6) Determine gravity once
        val gravity = if (positionPref) {
            Gravity.BOTTOM or Gravity.END
        } else {
            Gravity.BOTTOM or Gravity.START
        }

        // 7) Helper to apply height & gravity to any View whose layoutParams are FrameLayout.LayoutParams
        fun applySize(view: View) {
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = heightPx
                params.gravity = gravity
                view.layoutParams = params
            }
        }

        // 8) Update either tabletView or keyboardView (depending on isTablet)
        if (isTablet == true) {
            applySize(binding.tabletView)
        } else {
            applySize(binding.keyboardView)
        }

        // 9) The candidates row and symbol view use the same height & gravity
        applySize(binding.candidatesRowView)
        applySize(binding.keyboardSymbolView)
        applySize(binding.qwertyView)

        // 10) suggestionViewParent sits above the keyboard, so we adjust bottomMargin instead of height
        (binding.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = heightPx
            params.gravity = gravity
            binding.suggestionViewParent.layoutParams = params
        }

        // 11) Finally, update root’s width and gravity
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
