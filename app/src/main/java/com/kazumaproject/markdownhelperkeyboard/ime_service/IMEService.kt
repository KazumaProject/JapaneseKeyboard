package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getDakutenSmallChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextReturnInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isHiragana
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isLatinAlphabet
import com.kazumaproject.markdownhelperkeyboard.ime_service.listener.SwipeGestureListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.SuggestionEvent
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.extensions.KEY_ENGLISH_SIZE
import com.kazumaproject.tenkey.extensions.KEY_JAPANESE_SIZE
import com.kazumaproject.tenkey.extensions.KEY_NUMBER_SIZE
import com.kazumaproject.tenkey.listener.FlickListener
import com.kazumaproject.tenkey.listener.LongPressListener
import com.kazumaproject.tenkey.state.GestureType
import com.kazumaproject.tenkey.state.InputMode
import com.kazumaproject.tenkey.state.Key
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.math.roundToInt

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

    private var suggestionAdapter: SuggestionAdapter? = null

    @Inject
    lateinit var learnRepository: LearnRepository

    @Inject
    lateinit var clipboardUtil: ClipboardUtil

    private var isSuggestionVisible = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var emojiList: List<String> = emptyList()

    private var emoticonList: List<String> = emptyList()

    private var symbolList: List<String> = emptyList()

    private var mainLayoutBinding: MainLayoutBinding? = null
    private val _inputString = MutableStateFlow(EMPTY_STRING)
    private var stringInTail = AtomicReference("")
    private val _dakutenPressed = MutableStateFlow(false)
    private val _suggestionFlag = MutableStateFlow(
        SuggestionEvent(
            flag = CandidateShowFlag.Idle,
        )
    )
    private val _suggestionViewStatus = MutableStateFlow(true)
    private val _keyboardSymbolViewState = MutableStateFlow(false)
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
    private var suggestionCache: MutableMap<String, List<Candidate>>? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var commitAfterTextJob: Job? = null

    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.space_bar)
    }
    private val cachedLogoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.logo_key)
    }
    private val cachedKanaDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.kana_small)
    }
    private val cachedHenkanDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.henkan)
    }

    private val cachedNumberDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.number_small)
    }

    private val cachedArrowDropDownDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.outline_arrow_drop_down_24)
    }

    private val cachedArrowDropUpDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.outline_arrow_drop_up_24)
    }

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.baseline_arrow_right_alt_24)
    }

    private val cachedReturnDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.baseline_keyboard_return_24)
    }

    private val cachedTabDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.keyboard_tab_24px)
    }

    private val cachedCheckDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.baseline_check_24)
    }

    private val cachedSearchDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.baseline_search_24)
    }

    private val cachedEnglishDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, R.drawable.english_small)
    }

    companion object {
        const val EMPTY_STRING = ""
        const val DELAY_TIME = 1000L
        const val LONG_DELAY_TIME = 64L
        const val N_BEST = 4
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        suggestionAdapter = SuggestionAdapter()
    }

    override fun onCreateInputView(): View? {
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
                setTenKeyListeners(mainView)
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
            val mozcUTPersonName = mozc_ut_person_names_preference ?: false
            val mozcUTPlaces = mozc_ut_places_preference ?: false
            val mozcUTWiki = mozc_ut_wiki_preference ?: false
            val mozcUTNeologd = mozc_ut_neologd_preference ?: false
            val mozcUTWeb = mozc_ut_web_preference ?: false
            if (mozcUTPersonName) {
                if (!kanaKanjiEngine.isMozcUTPersonDictionariesInitialized()) {
                    kanaKanjiEngine.buildPersonNamesDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTPlaces) {
                if (!kanaKanjiEngine.isMozcUTPlacesDictionariesInitialized()) {
                    kanaKanjiEngine.buildPlaceDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTWiki) {
                if (!kanaKanjiEngine.isMozcUTWikiDictionariesInitialized()) {
                    kanaKanjiEngine.buildWikiDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTNeologd) {
                if (!kanaKanjiEngine.isMozcUTNeologdDictionariesInitialized()) {
                    kanaKanjiEngine.buildNeologdDictionary(
                        applicationContext
                    )
                }
            }
            if (mozcUTWeb) {
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
        if (!clipboardUtil.isClipboardEmpty()) {
            suggestionAdapter?.suggestions = clipboardUtil.getAllClipboardTexts()
        }
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
        mainLayoutBinding?.keyboardView?.isVisible = true
        mainLayoutBinding?.suggestionRecyclerView?.isVisible = true
    }


    override fun onWindowHidden() {
        super.onWindowHidden()
        Timber.d("onUpdate onWindowHidden")
        resetAllFlags()
    }

    override fun onDestroy() {
        Timber.d("onUpdate onDestroy")
        super.onDestroy()
        suggestionAdapter?.release()
        suggestionAdapter = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        suggestionCache = null
        appPreference.apply {
            val mozcUTPersonNames = mozc_ut_person_names_preference ?: false
            val mozcUTPlaces = mozc_ut_places_preference ?: false
            val mozcUTWiki = mozc_ut_wiki_preference ?: false
            val mozcUTNeologd = mozc_ut_neologd_preference ?: false
            val mozcUTWeb = mozc_ut_web_preference ?: false
            if (mozcUTPersonNames) kanaKanjiEngine.releasePersonNamesDictionary()
            if (mozcUTPlaces) kanaKanjiEngine.releasePlacesDictionary()
            if (mozcUTWiki) kanaKanjiEngine.releaseWikiDictionary()
            if (mozcUTNeologd) kanaKanjiEngine.releaseNeologdDictionary()
            if (mozcUTWeb) kanaKanjiEngine.releaseWebDictionary()
        }
        actionInDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                finishComposingText()
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                finishComposingText()
            }

            Configuration.ORIENTATION_UNDEFINED -> {
                finishComposingText()
            }

            else -> {
                finishComposingText()
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
        if (candidatesStart == -1 && candidatesEnd == -1) {
            if (stringInTail.get().isNotEmpty()) {
                _inputString.update { stringInTail.get() }
                stringInTail.set(EMPTY_STRING)
            } else {
                _inputString.update { EMPTY_STRING }
            }
        }
    }

    private fun setTenKeyListeners(mainView: MainLayoutBinding) {
        mainView.keyboardView.apply {
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Flick: $char $key $gestureType")
                    val insertString = _inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Down -> {
                            if (appPreference.vibration_timing_preference == null) {
                                vibrate()
                            } else {
                                when (appPreference.vibration_timing_preference) {
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
        if (appPreference.vibration_timing_preference == null) {
            vibrate()
        } else {
            when (appPreference.vibration_timing_preference) {
                "both" -> {
                    vibrate()
                }

                "press" -> {

                }

                "release" -> {
                    vibrate()
                }
            }
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
                asyncLeftLongPress().cancel()
            }

            Key.SideKeyCursorRight -> {
                if (!rightCursorKeyLongKeyPressed.get()) {
                    actionInRightKeyPressed(gestureType, insertString)
                }
                onRightKeyLongPressUp.set(true)
                rightCursorKeyLongKeyPressed.set(false)
                asyncRightLongPress().cancel()
            }

            Key.SideKeyDelete -> {
                if (!isFlick) {
                    if (!deleteKeyLongKeyPressed.get()) {
                        handleDeleteKeyTap(insertString, suggestions)
                    }
                }
                onDeleteLongPressUp.set(true)
                deleteKeyLongKeyPressed.set(false)
                deleteLongPress().cancel()
            }

            Key.SideKeyInputMode -> {
                setTenkeyIconsInHenkan(insertString, mainView)
            }

            Key.SideKeyPreviousChar -> {
                mainView.keyboardView.let {
                    when (it.currentInputMode) {
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
                stringInTail.set(EMPTY_STRING)
                finishComposingText()
                mainView.keyboardSymbolView.setTabPosition(0)
            }

            else -> {
                if (isFlick) {
                    handleFlick(char, insertString, sb, mainView)
                } else {
                    handleTap(char, insertString, sb, mainView)
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
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed.set(true)
            }

            Key.SideKeyDelete -> {
                if (isHenkan.get()) {
                    cancelHenkanByLongPressDeleteKey()
                } else {
                    onDeleteLongPressUp.set(false)
                    deleteLongPress()
                    _dakutenPressed.value = false
                    englishSpaceKeyPressed.set(false)
                    deleteKeyLongKeyPressed.set(true)
                }
            }

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                val insertString = _inputString.value
                if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                    isSpaceKeyLongPressed = true
                    showKeyboardPicker()
                }
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private var isSpaceKeyLongPressed = false

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun resetKeyboard() {
        mainLayoutBinding?.apply {
            println("reset keyboard called")
            animateViewVisibility(this.keyboardView, true)
            animateViewVisibility(this.candidatesRowView, false)
            suggestionRecyclerView.isVisible = true
            keyboardView.apply {
                if (currentInputMode == InputMode.ModeNumber) {
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

    private fun handleLeftCursor(gestureType: GestureType, insertString: String) {
        handleLeftKeyPress(gestureType, insertString)
        onLeftKeyLongPressUp.set(true)
    }

    private fun cancelHenkanByLongPressDeleteKey() {
        val insertString = _inputString.value
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
            stringInTail.set(EMPTY_STRING)
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
            _suggestionFlag.asStateFlow().collectLatest {
                when (it.flag) {
                    CandidateShowFlag.Idle -> {
                        suggestionAdapter?.suggestions = emptyList()
                        if (isSuggestionVisible) {
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, false
                            )
                            isSuggestionVisible = false
                        }
                        if (!clipboardUtil.isClipboardEmpty()) {
                            suggestionAdapter?.suggestions = clipboardUtil.getAllClipboardTexts()
                        }
                    }

                    CandidateShowFlag.Updating -> {
                        setSuggestionOnView(mainView)
                        if (!isSuggestionVisible) {
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, true
                            )
                            isSuggestionVisible = true
                        }
                    }
                }
            }
        }

        launch {
            _suggestionViewStatus.asStateFlow().collectLatest { isVisible ->
                updateSuggestionViewVisibility(mainView, isVisible)
            }
        }

        launch {
            _keyboardSymbolViewState.asStateFlow().collectLatest { isSymbolKeyboardShow ->
                mainView.apply {
                    if (isSymbolKeyboardShow) {
                        animateViewVisibility(keyboardView, false)
                        animateViewVisibility(keyboardSymbolView, true)
                        suggestionRecyclerView.isVisible = false
                        setSymbols(mainView)
                    } else {
                        animateViewVisibility(keyboardView, true)
                        animateViewVisibility(keyboardSymbolView, false)
                        suggestionRecyclerView.isVisible = true
                        clearSymbols()
                    }
                }
            }
        }

        launch {
            _inputString.asStateFlow().collectLatest { inputString ->
                processInputString(inputString, mainView)
            }
        }
    }

    private fun updateSuggestionViewVisibility(
        mainView: MainLayoutBinding, isVisible: Boolean
    ) {
        animateViewVisibility(mainView.keyboardView, isVisible)
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
                mainView.animate()
                    .translationY(mainView.height.toFloat())
                    .setDuration(200).setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
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
            mainView.animate().cancel()
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

    private suspend fun processInputString(inputString: String, mainView: MainLayoutBinding) {
        Timber.d("launchInputString: inputString: $inputString stringTail: $stringInTail")
        if (inputString.isNotEmpty()) {
            _suggestionFlag.update { SuggestionEvent(CandidateShowFlag.Updating) }
            val spannableString = SpannableString(inputString + stringInTail)
            setComposingTextPreEdit(inputString, spannableString)
            delay(appPreference.time_same_pronounce_typing_preference?.toLong() ?: DELAY_TIME)
            commitAfterTextJob = CoroutineScope(Dispatchers.Main).launch {
                if (!isHenkan.get() && _inputString.value.isNotEmpty() &&
                    !onDeleteLongPressUp.get() && !englishSpaceKeyPressed.get() &&
                    !deleteKeyLongKeyPressed.get()
                ) {
                    isContinuousTapInputEnabled.set(true)
                    lastFlickConvertedNextHiragana.set(true)
                    setComposingTextAfterEdit(inputString, spannableString)
                }
            }
        } else {
            println("input is empty now!!")
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
            mainView.keyboardView.apply {
                setSideKeySpaceDrawable(
                    cachedSpaceDrawable
                )
                if (currentInputMode == InputMode.ModeNumber) {
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

    private fun resetInputString() {
        if (!isHenkan.get()) {
            _suggestionFlag.update {
                SuggestionEvent(CandidateShowFlag.Idle)
            }
        }
    }

    private fun setCurrentInputType(attribute: EditorInfo?) {
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME(this)
            Timber.d("setCurrentInputType: $currentInputType $inputType")
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
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
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
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(
                            cachedReturnDrawable
                        )
                    }

                    InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(
                            cachedTabDrawable
                        )
                    }

                    InputTypeForIME.TextDone -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(
                            cachedCheckDrawable
                        )
                    }

                    InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
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
                        currentInputMode = InputMode.ModeEnglish
                        setInputModeSwitchState(InputMode.ModeEnglish)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(
                            cachedArrowRightDrawable
                        )
                    }

                    InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
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
                        currentInputMode = InputMode.ModeNumber
                        setInputModeSwitchState(InputMode.ModeNumber)
                        setSideKeyPreviousState(false)
                        this.setSideKeyEnterDrawable(
                            cachedArrowRightDrawable
                        )
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
        suggestionAdapter.apply {
            this?.setOnItemClickListener { candidate, position ->
                val insertString = _inputString.value
                val currentInputMode = mainView.keyboardView.currentInputMode
                vibrate()
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position
                )
            }
            this?.setOnItemLongClickListener { candidate, _ ->
                val insertString = _inputString.value
                setCandidateClipboardLongClick(candidate, insertString)
            }
        }
        mainView.suggestionRecyclerView.apply {
            itemAnimator = null
            isFocusable = false
            addOnItemTouchListener(SwipeGestureListener(context = this@IMEService, onSwipeDown = {
                suggestionAdapter?.let { adapter ->
                    if (adapter.suggestions.isNotEmpty()) {
                        if (_suggestionViewStatus.value) {
                            _suggestionViewStatus.update { !it }
                        }
                    }
                }
            }, onSwipeUp = {}))
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

    private fun setSymbolKeyboard(mainView: MainLayoutBinding) {
        mainView.keyboardSymbolView.apply {
            setLifecycleOwner(this@IMEService)
            setOnReturnToTenKeyButtonClickListener(object : ReturnToTenKeyButtonClickListener {
                override fun onClick() {
                    vibrate()
                    _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                    finishComposingText()
                }
            })
            setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener {
                override fun onClick() {
                    if (!deleteKeyLongKeyPressed.get()) {
                        vibrate()
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    }
                    onDeleteLongPressUp.set(true)
                    deleteKeyLongKeyPressed.set(false)
                    deleteLongPress().cancel()
                }
            })
            setOnDeleteButtonSymbolViewLongClickListener(object :
                DeleteButtonSymbolViewLongClickListener {
                override fun onLongClickListener() {
                    onDeleteLongPressUp.set(false)
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
        commitText(candidateString, 1)
        _inputString.update { EMPTY_STRING }
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
        currentInputMode: InputMode, insertString: String, candidate: Candidate, position: Int
    ) {
        if (currentInputMode != InputMode.ModeJapanese) {
            commitText(candidate.string, 1)
            _inputString.update { EMPTY_STRING }
            return
        }
        val isEnable = appPreference.learn_dictionary_preference

        if (isEnable == null || !isEnable) {
            commitText(candidate.string, 1)
            _inputString.update { EMPTY_STRING }
            return
        }
        if (position != 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val learnData = LearnEntity(input = insertString, out = candidate.string)
                learnRepository.upsertLearnedData(learnData)
            }
        }
        commitText(candidate.string, 1)
        _inputString.update { EMPTY_STRING }
    }

    private fun upsertLearnDictionaryMultipleTapCandidate(
        currentInputMode: InputMode,
        input: String,
        output: String,
        candidate: Candidate,
        insertString: String
    ) {
        if (currentInputMode == InputMode.ModeJapanese) {
            val isEnable = appPreference.learn_dictionary_preference
            if (isEnable == null) {
                commitText(candidate.string, 1)
                _inputString.update { EMPTY_STRING }
            } else {
                appPreference.learn_dictionary_preference?.let { enabledLearnDictionary ->
                    if (enabledLearnDictionary) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val learnData = LearnEntity(
                                input = input, out = output
                            )
                            val learnData2 = LearnEntity(
                                input = insertString, out = candidate.string
                            )
                            learnRepository.apply {
                                upsertLearnedData(learnData)
                                upsertLearnedData(learnData2)
                            }
                        }
                        commitText(candidate.string, 1)
                        _inputString.update { EMPTY_STRING }
                    } else {
                        commitText(candidate.string, 1)
                        _inputString.update { EMPTY_STRING }
                    }
                }
            }

        } else {
            commitText(candidate.string, 1)
            _inputString.update { EMPTY_STRING }
        }
    }

    private fun resetAllFlags() {
        Timber.d("onUpdate resetAllFlags called")
        _inputString.update { EMPTY_STRING }
        suggestionAdapter?.suggestions = emptyList()
        stringInTail.set(EMPTY_STRING)
        suggestionClickNum = 0
        isHenkan.set(false)
        isContinuousTapInputEnabled.set(false)
        deleteKeyLongKeyPressed.set(false)
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
        commitAfterTextJob?.cancel()
        commitAfterTextJob = null
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        mainLayoutBinding = null
        closeConnection()
        scope.cancel()
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
    }

    private fun resetFlagsEnterKey() {
        println("enter key reset is called")
        isHenkan.set(false)
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
    }

    private fun resetFlagsEnterKeyNotHenkan() {
        isHenkan.set(false)
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        _inputString.update { EMPTY_STRING }
        stringInTail.set(EMPTY_STRING)
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
                    BackgroundColorSpan(getColor(R.color.green)),
                    0,
                    inputLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    UnderlineSpan(), 0, inputLength + tailLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            // Use TextUtils.getOffsetBefore to handle surrogate pairs
            val lastCharStart = TextUtils.getOffsetBefore(inputString, inputLength)

            spannableString.apply {
                setSpan(
                    BackgroundColorSpan(getColor(R.color.green)),
                    0,
                    lastCharStart,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    BackgroundColorSpan(getColor(R.color.char_in_edit_color)),
                    lastCharStart,
                    inputLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    UnderlineSpan(), 0, inputLength + tailLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        Timber.d("launchInputString: setComposingTextPreEdit $spannableString")
        beginBatchEdit()
        setComposingText(spannableString, 1)
        endBatchEdit()
    }

    private fun setComposingTextAfterEdit(
        inputString: String, spannableString: SpannableString
    ) {
        spannableString.apply {
            setSpan(
                BackgroundColorSpan(getColor(R.color.blue)),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(UnderlineSpan(), 0, inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Timber.d("launchInputString: setComposingTextAfterEdit $spannableString")
        beginBatchEdit()
        setComposingText(spannableString, 1)
        endBatchEdit()
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
        mainView.keyboardView.apply {
            when (currentInputMode) {
                is InputMode.ModeJapanese -> {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    setSideKeyPreviousState(true)
                    if (insertString.isNotEmpty()) {
                        if (insertString.isNotEmpty() && insertString.last().isLatinAlphabet()) {
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

    private fun updateUIinHenkan(mainView: MainLayoutBinding, insertString: String) {
        mainView.keyboardView.apply {
            setSideKeyEnterDrawable(
                cachedReturnDrawable
            )
            when (currentInputMode) {
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

    private suspend fun setSuggestionOnView(mainView: MainLayoutBinding) {
        if (_inputString.value.isNotEmpty() && suggestionClickNum == 0) {
            val insertString = _inputString.value
            setCandidates(mainView, insertString)
        }
    }

    private suspend fun setCandidates(mainView: MainLayoutBinding, insertString: String) {
        val candidates = getSuggestionList(insertString)
        // Filter in the background if the list could be large
        val filteredCandidates = withContext(Dispatchers.Default) {
            if (stringInTail.get().isNotEmpty()) {
                candidates.filter { it.length.toInt() == insertString.length }
            } else {
                candidates
            }
        }
        // Now update UI on main
        withContext(Dispatchers.Main) {
            suggestionAdapter?.suggestions = filteredCandidates
            mainView.suggestionRecyclerView.scrollToPosition(0)
            updateUIinHenkan(mainView, insertString)
        }
    }

    private suspend fun getSuggestionList(insertString: String): List<Candidate> {
        val resultFromLearnDatabase = learnRepository.findLearnDataByInput(insertString)?.map {
            Candidate(
                string = it.out,
                type = (20).toByte(),
                length = (insertString.length).toUByte(),
                score = it.score,
            )
        } ?: emptyList()

        appPreference.candidate_cache_preference?.let {
            if (it) {
                suggestionCache?.let { cache ->
                    cache[insertString]?.let { cachedResult ->
                        return (resultFromLearnDatabase + cachedResult).distinctBy { candidate -> candidate.string }
                    }
                }
            }
        }
        val result = if (appPreference.learn_dictionary_preference == true) {
            val resultFromEngine = kanaKanjiEngine.getCandidates(
                insertString, appPreference.n_best_preference ?: N_BEST, appPreference
            )
            resultFromLearnDatabase + resultFromEngine
        } else {
            kanaKanjiEngine.getCandidates(
                insertString, appPreference.n_best_preference ?: N_BEST, appPreference
            )
        }
        val distinct = result.distinctBy { it.string }
        appPreference.candidate_cache_preference?.let {
            if (it) {
                suggestionCache?.let { cache ->
                    cache[insertString] = distinct
                }
            }
        }
        return distinct
    }

    private fun deleteLongPress() = scope.launch {
        while (isActive && deleteKeyLongKeyPressed.get() && !onDeleteLongPressUp.get()) {
            val insertString = _inputString.value
            val tailIsEmpty = stringInTail.get().isEmpty()

            if (insertString.isEmpty()) {
                // If there's no composing text, try sending a DEL key event
                // Otherwise, exit the loop because there's nothing to delete
                if (tailIsEmpty) {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                } else {
                    // Nothing left to delete in the composing text, break out
                    break
                }
            } else {
                // Remove the last character from the composing text
                val newString = insertString.dropLast(1)
                _inputString.update { newString }

                // If it becomes empty, we may need to clear any tail text
                if (newString.isEmpty() && tailIsEmpty) {
                    setComposingText("", 0)
                }
            }

            // Add a small delay before the next deletion
            delay(LONG_DELAY_TIME)
        }

        // Once the loop finishes (for any reason),
        // re-enable normal tap input
        enableContinuousTapInput()

        // Update the suggestion flag based on the new composing text
        val finalInsertString = _inputString.value
        val candidateFlag = if (finalInsertString.isEmpty()) {
            CandidateShowFlag.Idle
        } else {
            CandidateShowFlag.Updating
        }
        _suggestionFlag.update { SuggestionEvent(candidateFlag) }
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
                keyboardView.let { tenkey ->
                    when (tenkey.currentInputMode) {
                        InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) handleJapaneseModeSpaceKey(
                            this, suggestions, insertString
                        )

                        else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                    }
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
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
        mainView.keyboardView.apply {
            when (currentInputMode) {
                InputMode.ModeJapanese -> {
                    if (isHenkan.get()) {
                        handleHenkanModeEnterKey(suggestions, currentInputMode, insertString)
                    } else {
                        finishInputEnterKey()
                    }
                }

                else -> finishInputEnterKey()
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
            stringInTail.set(EMPTY_STRING)
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
        mainView.keyboardView.setSideKeyEnterDrawable(currentDrawable)
    }

    private fun finishInputEnterKey() {
        finishComposingText()
        _inputString.update { EMPTY_STRING }
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
                    _inputString.update { EMPTY_STRING }
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

    private fun asyncLeftLongPress() = scope.launch {
        // We'll store a "reason" if we need to emit a specific suggestion flag
        var finalSuggestionFlag: CandidateShowFlag? = null

        while (isActive && leftCursorKeyLongKeyPressed.get() && !onLeftKeyLongPressUp.get()) {
            val insertString = _inputString.value

            // If tail is not empty but there’s no composing text, we stop and emit Idle
            if (stringInTail.get().isNotEmpty() && insertString.isEmpty()) {
                finalSuggestionFlag = CandidateShowFlag.Idle
                break
            }

            if (insertString.isNotEmpty()) {
                // Move a character from the composing text to the "tail" or handle it
                updateLeftInputString(insertString)
            } else {
                // If there's really nothing in either the composing text or the tail,
                // we can move the cursor left using a DPAD event (if we’re not already at the start)
                if (stringInTail.get().isEmpty() && !isCursorAtBeginning()) {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                }
            }

            delay(LONG_DELAY_TIME)
        }

        if (finalSuggestionFlag != null) {
            _suggestionFlag.update { SuggestionEvent(finalSuggestionFlag) }
        } else {
            val insertString = _inputString.value
            if (insertString.isEmpty()) {
                _suggestionFlag.update { SuggestionEvent(CandidateShowFlag.Idle) }
            } else {
                _suggestionFlag.update { SuggestionEvent(CandidateShowFlag.Updating) }
            }
        }
    }

    private fun asyncRightLongPress() = scope.launch {
        // Same pattern: we store a reason for break if needed
        var finalSuggestionFlag: CandidateShowFlag? = null

        while (isActive && rightCursorKeyLongKeyPressed.get() && !onRightKeyLongPressUp.get()) {
            val insertString = _inputString.value

            // If there's composing text but no "tail," we emit "Updating" and stop.
            // (Mimics original logic: break out if stringInTail is empty + inputString is not empty.)
            if (stringInTail.get().isEmpty() && insertString.isNotEmpty()) {
                finalSuggestionFlag = CandidateShowFlag.Updating
                break
            }

            // Perform actual "right key pressed" logic
            actionInRightKeyPressed(insertString)

            delay(LONG_DELAY_TIME)
        }

        if (finalSuggestionFlag != null) {
            _suggestionFlag.update { SuggestionEvent(finalSuggestionFlag) }
        } else {
            val insertString = _inputString.value
            if (insertString.isNotEmpty()) {
                _suggestionFlag.update { SuggestionEvent(CandidateShowFlag.Updating) }
            } else {
                _suggestionFlag.update { SuggestionEvent(CandidateShowFlag.Idle) }
            }
        }
    }


    private fun updateLeftInputString(insertString: String) {
        if (insertString.isNotEmpty()) {
            if (insertString.length == 1) {
                stringInTail.set(insertString + stringInTail.get())
                _inputString.update { EMPTY_STRING }
                suggestionAdapter?.suggestions = emptyList()
            } else {
                stringInTail.set(insertString.last() + stringInTail.get())
                _inputString.update { it.dropLast(1) }
            }
        }
    }

    private fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) {
        when {
            insertString.isEmpty() -> handleEmptyInputString(gestureType)
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
            if (!isCursorAtEnd()) {
                sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                    )
                )
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
                _inputString.update { EMPTY_STRING }
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
        char: Char, inputForInsert: String, sb: StringBuilder
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
        sb: StringBuilder, insertString: String
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
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder, insertString: String
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

    private fun handleDakutenSmallLetterKey(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardView.let {
            when (it.currentInputMode) {
                InputMode.ModeJapanese -> {
                    dakutenSmallLetter(sb, insertString)
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

    private fun setKeyTouch(
        key: Char, insertString: String, sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isContinuousTapInputEnabled.set(false)
        if (isHenkan.get()) {
            finishComposingText()
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
                println("setConvertLetterInJapaneseFromButton space hasPrevious $suggestionClickNum ${suggestions.size}")
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
                println("setConvertLetterInJapaneseFromButton delete hasPrevious $suggestionClickNum ${suggestions.size}")
            }

            listIterator.hasNext() && isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
                println("setConvertLetterInJapaneseFromButton space hasNext $suggestionClickNum ${suggestions.size}")
            }

            listIterator.hasNext() && !isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
                println("setConvertLetterInJapaneseFromButton delete hasNext $suggestionClickNum ${suggestions.size}")
            }
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberNotEmpty(insertString: String) {
        if (stringInTail.get().isNotEmpty()) {
            commitText("$insertString $stringInTail", 1)
            stringInTail.set(EMPTY_STRING)
        } else {
            commitText("$insertString ", 1)
        }
        _inputString.update {
            EMPTY_STRING
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
            stringInTail.set(EMPTY_STRING)
        } else {
            mainLayoutBinding?.keyboardView?.apply {
                if (currentInputMode == InputMode.ModeJapanese) {
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
        _inputString.update { EMPTY_STRING }
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
            if (!isFirstClickHasStringTail) stringInTail.set(EMPTY_STRING)
        }
        val fullText = suggestionText + stringInTail
        applyComposingText(fullText, suggestionText.length)
    }

    private fun applyComposingText(text: String, highlightLength: Int) {
        val spannableString = SpannableString(text)
        spannableString.setSpan(
            BackgroundColorSpan(getColor(R.color.orange)),
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
        val heightFromPreference = appPreference.keyboard_height ?: 280
        val widthFromPreference = appPreference.keyboard_width ?: 100
        val keyboardPositionPreference = appPreference.keyboard_position ?: true
        val density = resources.displayMetrics.density
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val orientation = resources.configuration.orientation

        val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

        val maxLandscapeHeight = 200
        val clampedHeight = if (isPortrait) {
            heightFromPreference.coerceIn(210, 280)
        } else {
            heightFromPreference.coerceAtMost(maxLandscapeHeight)
        }

        val heightInPx = (clampedHeight * density).toInt()

        val widthInPx = if (isPortrait) {
            if (widthFromPreference == 100) ViewGroup.LayoutParams.MATCH_PARENT
            else (screenWidth * (widthFromPreference / 100f)).toInt()
        } else {
            (screenWidth * 0.8).toInt()
        }

        mainLayoutBinding?.apply {
            val normalizedProgress = (clampedHeight - 210) / 70f
            val easedProgress = easeInOutQuad(normalizedProgress)

            val spanCount = if (isPortrait) lerp(2f, 4f, easedProgress).roundToInt() else lerp(
                4f, 5f, easedProgress
            ).roundToInt()
            val padding =
                if (isPortrait) lerp(12f * density, 21f * density, easedProgress).toInt() else lerp(
                    8f * density, 12f * density, easedProgress
                ).toInt()

            val letterSizeJP = lerp(14f, 17f, easedProgress)
            val letterSizeEN = lerp(11f, 14f, easedProgress)
            val letterSizeNUMBER = lerp(15f, 18f, easedProgress)

            keyboardSymbolView.updateSpanCount(spanCount)
            keyboardView.setPaddingToSideKeySymbol(padding)
            KEY_JAPANESE_SIZE = letterSizeJP
            KEY_ENGLISH_SIZE = letterSizeEN
            KEY_NUMBER_SIZE = letterSizeNUMBER

            keyboardView.apply {
                val params = layoutParams as FrameLayout.LayoutParams
                params.apply {
                    height = heightInPx
                    gravity = if (keyboardPositionPreference) {
                        Gravity.BOTTOM or Gravity.END
                    } else {
                        Gravity.BOTTOM or Gravity.START
                    }
                }
                layoutParams = params
            }
            candidatesRowView.apply {
                val params = layoutParams as FrameLayout.LayoutParams
                params.apply {
                    height = heightInPx
                    gravity = if (keyboardPositionPreference) {
                        Gravity.BOTTOM or Gravity.END
                    } else {
                        Gravity.BOTTOM or Gravity.START
                    }
                }
                layoutParams = params
            }
            keyboardSymbolView.apply {
                val params = layoutParams as FrameLayout.LayoutParams
                params.apply {
                    height = heightInPx
                    gravity = if (keyboardPositionPreference) {
                        Gravity.BOTTOM or Gravity.END
                    } else {
                        Gravity.BOTTOM or Gravity.START
                    }
                }
                layoutParams = params
            }
            suggestionViewParent.apply {
                val params = suggestionViewParent.layoutParams as FrameLayout.LayoutParams
                params.apply {
                    bottomMargin = heightInPx
                    gravity = if (keyboardPositionPreference) {
                        Gravity.BOTTOM or Gravity.END
                    } else {
                        Gravity.BOTTOM or Gravity.START
                    }
                }
                layoutParams = params
            }

            root.apply {
                val params = root.layoutParams as FrameLayout.LayoutParams
                params.apply {
                    width = widthInPx
                    gravity = if (keyboardPositionPreference) {
                        Gravity.BOTTOM or Gravity.END
                    } else {
                        Gravity.BOTTOM or Gravity.START
                    }
                }
                layoutParams = params
            }
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    private fun easeInOutQuad(t: Float): Float = if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t

    private fun vibrate() {
        if (appPreference.vibration_preference != true) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(4)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence? {
        println("getTextBeforeCursor")
        return currentInputConnection?.getTextBeforeCursor(p0, p1)
    }

    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence? {
        println("getTextAfterCursor")
        return currentInputConnection?.getTextAfterCursor(p0, p1)
    }

    override fun getSelectedText(p0: Int): CharSequence {
        println("getSelectedText")
        return currentInputConnection.getSelectedText(p0)
    }

    override fun getCursorCapsMode(p0: Int): Int {
        println("getCursorCapsMode")
        if (currentInputConnection == null) return 0
        return currentInputConnection.getCursorCapsMode(p0)
    }

    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText? {
        println("getExtractedText")
        return currentInputConnection.getExtractedText(p0, p1)
    }

    override fun deleteSurroundingText(p0: Int, p1: Int): Boolean {
        println("deleteSurroundingText")
        if (currentInputConnection == null) return false
        return currentInputConnection.deleteSurroundingText(p0, p1)
    }

    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean {
        println("deleteSurroundingTextInCodePoints")
        if (currentInputConnection == null) return false
        return currentInputConnection.deleteSurroundingTextInCodePoints(p0, p1)
    }

    override fun setComposingText(p0: CharSequence?, p1: Int): Boolean {
        println("setComposingText : $p0 $p1 ")
        if (currentInputConnection == null) return false
        return currentInputConnection.setComposingText(p0, p1)
    }

    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        println("setComposingRegion")
        if (currentInputConnection == null) return false
        return currentInputConnection.setComposingRegion(p0, p1)
    }

    override fun finishComposingText(): Boolean {
        if (currentInputConnection == null) return false
        println("finishComposingText")
        return currentInputConnection.finishComposingText()
    }

    override fun commitText(p0: CharSequence?, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        println("commitText")
        return currentInputConnection.commitText(p0, p1)
    }

    override fun commitCompletion(p0: CompletionInfo?): Boolean {
        println("commitCompletion")
        if (currentInputConnection == null) return false
        return currentInputConnection.commitCompletion(p0)
    }

    override fun commitCorrection(p0: CorrectionInfo?): Boolean {
        println("commitCorrection")
        if (currentInputConnection == null) return false
        return currentInputConnection.commitCorrection(p0)
    }

    override fun setSelection(p0: Int, p1: Int): Boolean {
        println("setSelection")
        if (currentInputConnection == null) return false
        return currentInputConnection.setSelection(p0, p1)
    }

    override fun performEditorAction(p0: Int): Boolean {
        println("performEditorAction")
        if (currentInputConnection == null) return false
        return currentInputConnection.performEditorAction(p0)
    }

    override fun performContextMenuAction(p0: Int): Boolean {
        println("performContextMenuAction")
        if (currentInputConnection == null) return false
        return currentInputConnection.performContextMenuAction(p0)
    }

    override fun beginBatchEdit(): Boolean {
        println("beginBatchEdit")
        if (currentInputConnection == null) return false
        return currentInputConnection.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        println("endBatchEdit")
        if (currentInputConnection == null) return false
        return currentInputConnection.endBatchEdit()
    }

    override fun sendKeyEvent(p0: KeyEvent?): Boolean {
        println("sendKeyEvent")
        if (currentInputConnection == null) return false
        return currentInputConnection.sendKeyEvent(p0)
    }

    override fun clearMetaKeyStates(p0: Int): Boolean {
        println("clearMetaKeyStates")
        if (currentInputConnection == null) return false
        return currentInputConnection.clearMetaKeyStates(p0)
    }

    override fun reportFullscreenMode(p0: Boolean): Boolean {
        println("reportFullscreenMode")
        if (currentInputConnection == null) return false
        return currentInputConnection.reportFullscreenMode(p0)
    }

    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean {
        println("performPrivateCommand")
        if (currentInputConnection == null) return false
        return currentInputConnection.performPrivateCommand(p0, p1)
    }

    override fun requestCursorUpdates(p0: Int): Boolean {
        println("requestCursorUpdates")
        if (currentInputConnection == null) return false
        return currentInputConnection.requestCursorUpdates(p0)
    }

    override fun getHandler(): Handler? {
        println("getHandler")
        return currentInputConnection?.handler
    }

    override fun closeConnection() {
        println("closeConnection")
        if (currentInputConnection == null) return
        return currentInputConnection.closeConnection()
    }

    override fun commitContent(
        inputContent: InputContentInfo,
        flags: Int,
        opts: Bundle?
    ): Boolean {
        if (currentInputConnection == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            currentInputConnection.commitContent(inputContent, flags, opts)
        } else {
            false
        }
    }
}
