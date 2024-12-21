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
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
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
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableArrowTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableCheck
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableEnglishSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableHenkan
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableKanaSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableLanguage
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableNumberSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableOpenBracket
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableReturn
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableRightArrow
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableSearch
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableSpaceBar
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.EmojiList
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.EmoticonList
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.MainDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.SymbolList
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getDakutenSmallChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextReturnInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isHiragana
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isLatinAlphabet
import com.kazumaproject.markdownhelperkeyboard.ime_service.listener.SwipeGestureListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.listener.FlickListener
import com.kazumaproject.tenkey.listener.LongPressListener
import com.kazumaproject.tenkey.state.GestureType
import com.kazumaproject.tenkey.state.InputMode
import com.kazumaproject.tenkey.state.Key
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
import javax.inject.Named

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class IMEService : InputMethodService(), LifecycleOwner, InputConnection {

    sealed class CandidateShowFlag {
        object Idle : CandidateShowFlag()
        object Updating : CandidateShowFlag()
    }

    @Inject
    @Named("main_ime_scope")
    lateinit var scope: CoroutineScope

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var inputMethodManager: InputMethodManager

    @Inject
    @DrawableReturn
    lateinit var drawableReturn: Drawable

    @Inject
    @DrawableArrowTab
    lateinit var drawableArrowTab: Drawable

    @Inject
    @DrawableKanaSmall
    lateinit var drawableKanaSmall: Drawable

    @Inject
    @DrawableHenkan
    lateinit var drawableHenkan: Drawable

    @Inject
    @DrawableEnglishSmall
    lateinit var drawableEnglishSmall: Drawable

    @Inject
    @DrawableSpaceBar
    lateinit var drawableSpaceBar: Drawable

    @Inject
    @DrawableRightArrow
    lateinit var drawableRightArrow: Drawable

    @Inject
    @DrawableSearch
    lateinit var drawableSearch: Drawable

    @Inject
    @DrawableCheck
    lateinit var drawableCheck: Drawable

    @Inject
    @DrawableLanguage
    lateinit var drawableLogo: Drawable

    @Inject
    @DrawableNumberSmall
    lateinit var drawableNumberSmall: Drawable

    @Inject
    @DrawableOpenBracket
    lateinit var drawableOpenBracket: Drawable

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var suggestionAdapter: SuggestionAdapter

    @Inject
    @EmojiList
    lateinit var emojiList: List<String>

    @Inject
    @EmoticonList
    lateinit var emoticonList: List<String>

    @Inject
    @SymbolList
    lateinit var symbolList: List<String>

    @Inject
    lateinit var learnRepository: LearnRepository

    private var mainLayoutBinding: MainLayoutBinding? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private val _inputString = MutableStateFlow(EMPTY_STRING)
    private var stringInTail = AtomicReference("")
    private val _dakutenPressed = MutableStateFlow(false)
    private val _suggestionFlag = MutableSharedFlow<CandidateShowFlag>()
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

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private val shortVibrationEffect: VibrationEffect by lazy {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    private val shortCombinedVibration: CombinedVibration by lazy {
        CombinedVibration.createParallel(shortVibrationEffect)
    }

    private fun setVibrate() {
        appPreference.vibration_preference?.let {
            if (it) vibratorManager.vibrate(shortCombinedVibration)
        }
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
        setCurrentInputType(attribute)
        _suggestionViewStatus.update { true }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        Timber.d("onUpdate onStartInputView called $restarting")
        resetKeyboard()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
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
        resetAllFlags()
    }

    override fun onDestroy() {
        Timber.d("onUpdate onDestroy")
        super.onDestroy()
        actionInDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
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
                    val suggestionList = suggestionAdapter.suggestions
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Down -> {
                            setVibrate()
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
        setVibrate()
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {
                if (insertString.isNotEmpty()) {
                    handleNonEmptyInputEnterKey(suggestions, mainView)
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
                setVibrate()
                _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                finishComposingText()
                mainView.keyboardSymbolView.setTabPosition(0)
            }

            else -> {
                if (isFlick) {
                    handleFlick(char, insertString, sb)
                } else {
                    handleTap(char, insertString, sb)
                }
            }
        }
    }

    private fun handleFlick(char: Char?, insertString: String, sb: StringBuilder) {
        if (isHenkan.get()) {
            suggestionAdapter.updateHighlightPosition(-1)
            finishComposingText()
            CoroutineScope(Dispatchers.IO).launch {
                delay(64L)
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

    private fun handleTap(char: Char?, insertString: String, sb: StringBuilder) {
        if (isHenkan.get()) {
            suggestionAdapter.updateHighlightPosition(-1)
            finishComposingText()
            CoroutineScope(Dispatchers.IO).launch {
                delay(64)
                onComposingTextFinished {
                    isHenkan.set(false)
                    char?.let {
                        sendCharTap(
                            charToSend = it, insertString = "", sb = sb
                        )
                    }
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

    private fun onComposingTextFinished(callback: () -> Unit) {
        callback()
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
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                } else {
                    setBackgroundSmallLetterKey(drawableLogo)
                }
            }
        }
    }

    private fun handleLeftCursor(gestureType: GestureType, insertString: String) {
        handleLeftKeyPress(gestureType, insertString)
        onLeftKeyLongPressUp.set(true)
        if (insertString.isBlank() || insertString.isEmpty()) {
            suggestionAdapter.suggestions = emptyList()
        }
    }

    private fun cancelHenkanByLongPressDeleteKey() {
        val insertString = _inputString.value
        val selectedSuggestion = suggestionAdapter.suggestions.getOrNull(suggestionClickNum)

        deleteKeyLongKeyPressed.set(true)
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
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

    private var isSuggestionVisible = false

    private fun startScope(mainView: MainLayoutBinding) = scope.launch {
        withContext(Dispatchers.Main) {
            setSymbols(mainView)
        }
        launch {
            _suggestionFlag.collect {
                if (it == CandidateShowFlag.Idle) {
                    suggestionAdapter.suggestions = emptyList()
                    if (isSuggestionVisible) {
                        animateSuggestionImageViewVisibility(mainView.suggestionVisibility, false)
                        isSuggestionVisible = false
                    }
                } else {
                    setSuggestionOnView(mainView)
                    if (!isSuggestionVisible) {
                        animateSuggestionImageViewVisibility(mainView.suggestionVisibility, true)
                        isSuggestionVisible = true
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
                    } else {
                        animateViewVisibility(keyboardView, true)
                        animateViewVisibility(keyboardSymbolView, false)
                        suggestionRecyclerView.isVisible = true
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
        if (mainView.keyboardView.visibility == (if (isVisible) View.VISIBLE else View.GONE)) return

        animateViewVisibility(mainView.keyboardView, isVisible)
        animateViewVisibility(mainView.candidatesRowView, !isVisible)

        if (mainView.candidatesRowView.isVisible) {
            mainView.candidatesRowView.scrollToPosition(0)
        }

        mainView.suggestionVisibility.apply {
            setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    if (isVisible) R.drawable.outline_arrow_drop_down_24 else R.drawable.outline_arrow_drop_up_24
                )
            )
        }
    }

    private fun animateViewVisibility(
        mainView: View,
        isVisible: Boolean,
        withAnimation: Boolean = true
    ) {
        // Cancel any ongoing animation on the view
        mainView.animate().cancel()

        if (isVisible) {
            mainView.visibility = View.VISIBLE

            if (withAnimation) {
                mainView.translationY = mainView.height.toFloat() // Start from hidden position
                mainView.animate()
                    .translationY(0f) // Animate to visible position
                    .setDuration(150)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            } else {
                mainView.translationY = 0f
            }
        } else {
            if (withAnimation) {
                mainView.translationY = 0f // Start from visible position
                mainView.animate()
                    .translationY(mainView.height.toFloat()) // Animate to hidden position
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        mainView.visibility = View.GONE // Set visibility after animation ends
                    }
                    .start()
            } else {
                mainView.visibility = View.GONE
            }
        }
    }

    private fun animateSuggestionImageViewVisibility(
        mainView: View,
        isVisible: Boolean
    ) {
        mainView.post {
            mainView.animate().cancel()
            mainView.pivotX = mainView.width / 2f
            mainView.pivotY = mainView.height / 2f

            if (isVisible) {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 0f
                mainView.scaleY = 0f

                mainView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }
                    .start()
            } else {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 1f
                mainView.scaleY = 1f

                mainView.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        mainView.visibility = View.GONE
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }
                    .start()
            }
        }
    }

    private suspend fun processInputString(inputString: String, mainView: MainLayoutBinding) {
        Timber.d("launchInputString: inputString: $inputString stringTail: $stringInTail")
        if (inputString.isNotEmpty()) {
            _suggestionFlag.apply {
                emit(CandidateShowFlag.Updating)
            }
            val spannableString = SpannableString(inputString + stringInTail)
            setComposingTextPreEdit(inputString, spannableString)
            delay(DELAY_TIME)
            if (!isHenkan.get() && inputString.isNotEmpty() && !onDeleteLongPressUp.get() && !englishSpaceKeyPressed.get() && !deleteKeyLongKeyPressed.get()) {
                isContinuousTapInputEnabled.set(true)
                lastFlickConvertedNextHiragana.set(true)
                setComposingTextAfterEdit(inputString, spannableString)
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
            mainView.keyboardView.apply {
                setSideKeySpaceDrawable(drawableSpaceBar)
                if (currentInputMode == InputMode.ModeNumber) {
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                } else {
                    setBackgroundSmallLetterKey(drawableLogo)
                }
            }
        }
    }

    private suspend fun resetInputString() {
        if (!isHenkan.get()) {
            _suggestionFlag.apply {
                emit(CandidateShowFlag.Idle)
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
                        this.setSideKeyEnterDrawable(drawableRightArrow)
                    }

                    InputTypeForIME.TextMultiLine,
                    InputTypeForIME.TextImeMultiLine,
                    InputTypeForIME.TextShortMessage,
                    InputTypeForIME.TextLongMessage,
                        -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(drawableReturn)
                    }

                    InputTypeForIME.TextEmailAddress,
                    InputTypeForIME.TextEmailSubject,
                    InputTypeForIME.TextNextLine -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(drawableArrowTab)
                    }

                    InputTypeForIME.TextDone -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(drawableCheck)
                    }

                    InputTypeForIME.TextWebSearchView,
                    InputTypeForIME.TextWebSearchViewFireFox,
                    InputTypeForIME.TextSearchView -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(drawableSearch)
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
                        this.setSideKeyEnterDrawable(drawableRightArrow)
                    }

                    InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                        this.setSideKeyEnterDrawable(drawableRightArrow)
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
                        this.setSideKeyEnterDrawable(drawableRightArrow)
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
            this.setOnItemClickListener {
                val insertString = _inputString.value
                val currentInputMode = mainView.keyboardView.currentInputMode
                setVibrate()
                setCandidateClick(it, insertString, currentInputMode)
            }
        }
        mainView.suggestionRecyclerView.apply {
            itemAnimator = null
            focusable = View.NOT_FOCUSABLE
            addOnItemTouchListener(SwipeGestureListener(context = this@IMEService, onSwipeDown = {
                if (suggestionAdapter.suggestions.isNotEmpty()) {
                    if (_suggestionViewStatus.value) {
                        _suggestionViewStatus.update { !it }
                    }
                }
            }, onSwipeUp = {}))
        }

        mainView.candidatesRowView.apply {
            itemAnimator = null
            focusable = View.NOT_FOCUSABLE
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
                    setVibrate()
                    _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                    finishComposingText()
                }
            })
            setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener {
                override fun onClick() {
                    if (!deleteKeyLongKeyPressed.get()) {
                        setVibrate()
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
                    setVibrate()
                    commitText(symbol, 1)
                }
            })
        }
    }

    private fun setSymbols(mainView: MainLayoutBinding) {
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList,
            emoticonList,
            symbolList,
            0
        )
    }

    private fun setCandidateClick(
        candidate: Candidate,
        insertString: String,
        currentInputMode: InputMode
    ) {
        if (insertString.isNotEmpty()) {
            commitCandidateText(candidate, insertString, currentInputMode)
            resetFlagsSuggestionClick()
        }
    }

    private fun commitCandidateText(
        candidate: Candidate,
        insertString: String,
        currentInputMode: InputMode
    ) {
        val candidateType = candidate.type.toInt()
        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            stringInTail.set(insertString.substring(candidate.length.toInt()))
        } else if (candidateType == 15) {
            val readingCorrection = candidate.string.correctReading()
            if (stringInTail.get().isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (currentInputMode == InputMode.ModeJapanese) {
                        val learnData = LearnEntity(
                            input = _inputString.value,
                            out = readingCorrection.first
                        )
                        learnRepository.upsertLearnedData(learnData)
                    }
                    commitText(readingCorrection.first, 1)
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    if (currentInputMode == InputMode.ModeJapanese) {
                        val learnData = LearnEntity(
                            input = _inputString.value,
                            out = readingCorrection.first
                        )
                        learnRepository.upsertLearnedData(learnData)
                    }
                    commitText(readingCorrection.first, 1)
                    _inputString.value = EMPTY_STRING
                }
            }
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (currentInputMode == InputMode.ModeJapanese) {
                val learnData = LearnEntity(
                    input = _inputString.value,
                    out = candidate.string
                )
                learnRepository.upsertLearnedData(learnData)
            }
            commitText(candidate.string, 1)
            _inputString.value = EMPTY_STRING
        }
    }

    private fun resetAllFlags() {
        Timber.d("onUpdate resetAllFlags called")
        _inputString.update { EMPTY_STRING }
        suggestionAdapter.suggestions = emptyList()
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
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        resetKeyboard()
        _keyboardSymbolViewState.value = false
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        mainLayoutBinding = null
        closeConnection()
        scope.coroutineContext.cancelChildren()
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
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
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
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
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
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
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
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
    }

    private fun setComposingTextPreEdit(
        inputString: String,
        spannableString: SpannableString
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
                    UnderlineSpan(),
                    0,
                    inputLength + tailLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
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
                    UnderlineSpan(),
                    0,
                    inputLength + tailLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
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
                BackgroundColorSpan(getColor(R.color.blue)),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(UnderlineSpan(), 0, inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Timber.d("launchInputString: setComposingTextAfterEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    private fun setEnterKeyAction(suggestions: List<Candidate>) {
        val index = if (suggestionClickNum - 1 < 0) 0 else suggestionClickNum - 1
        val nextSuggestion = suggestions[index]
        if (nextSuggestion.type == (15).toByte()) {
            CoroutineScope(Dispatchers.IO).launch {
                val readingCorrection = nextSuggestion.string.correctReading()
                val learnData = LearnEntity(
                    input = _inputString.value,
                    out = readingCorrection.first
                )
                learnRepository.upsertLearnedData(learnData)
                commitText(readingCorrection.first, 1)
                _inputString.value = EMPTY_STRING
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val learnData = LearnEntity(
                    input = _inputString.value,
                    out = nextSuggestion.string
                )
                learnRepository.upsertLearnedData(learnData)
                commitText(nextSuggestion.string, 1)
                _inputString.value = EMPTY_STRING
            }
        }
        resetFlagsEnterKey()
    }

    private fun setTenkeyIconsInHenkan(insertString: String, mainView: MainLayoutBinding) {
        mainView.keyboardView.apply {
            when (currentInputMode) {
                is InputMode.ModeJapanese -> {
                    setSideKeySpaceDrawable(drawableSpaceBar)
                    setSideKeyPreviousState(true)
                    if (insertString.isNotEmpty()) {
                        if (insertString.isNotEmpty() && insertString.last().isLatinAlphabet()) {
                            setBackgroundSmallLetterKey(drawableEnglishSmall)
                        } else {
                            setBackgroundSmallLetterKey(drawableLogo)
                        }
                    } else {
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                }

                is InputMode.ModeEnglish -> {
                    setSideKeySpaceDrawable(drawableSpaceBar)
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                    setSideKeyPreviousState(false)
                }

                is InputMode.ModeNumber -> {
                    setSideKeyPreviousState(true)
                    if (insertString.isNotEmpty()) {
                        setSideKeySpaceDrawable(drawableHenkan)
                        if (insertString.last().isHiragana()) {
                            setBackgroundSmallLetterKey(drawableKanaSmall)
                        } else {
                            setBackgroundSmallLetterKey(drawableLogo)
                        }
                    } else {
                        setSideKeySpaceDrawable(drawableSpaceBar)
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                }
            }
        }
    }

    private fun updateUIinHenkan(mainView: MainLayoutBinding, insertString: String) {
        mainView.keyboardView.apply {
            setSideKeyEnterDrawable(drawableReturn)
            when (currentInputMode) {
                InputMode.ModeJapanese -> {
                    if (insertString.isNotEmpty() && insertString.last().isHiragana()) {
                        setBackgroundSmallLetterKey(drawableKanaSmall)
                    } else {
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                    setSideKeySpaceDrawable(drawableHenkan)
                }

                InputMode.ModeEnglish -> {

                    if (insertString.isNotEmpty() && insertString.last().isLatinAlphabet()) {
                        setBackgroundSmallLetterKey(drawableEnglishSmall)
                    } else {
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                    setSideKeySpaceDrawable(drawableSpaceBar)
                }

                InputMode.ModeNumber -> {
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                    setSideKeySpaceDrawable(drawableSpaceBar)
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
        val filteredCandidates = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        suggestionAdapter.suggestions = filteredCandidates
        mainView.apply {
            suggestionRecyclerView.scrollToPosition(0)
        }
        updateUIinHenkan(mainView, insertString)
    }

    private suspend fun getSuggestionList(insertString: String): List<Candidate> {
        val resultFromEngine = kanaKanjiEngine.getCandidates(insertString, N_BEST)
        val resultFromLearnDatabase = learnRepository.findLearnDataByInput(insertString)?.map {
            Candidate(
                string = it.out,
                type = (19).toByte(),
                length = (insertString.length).toUByte(),
                score = it.score,
            )
        } ?: emptyList()
        println("get candidate from learn: $resultFromLearnDatabase")
        println("get candidate from engine: $resultFromEngine")
        val result = resultFromLearnDatabase + resultFromEngine
        return result.distinctBy { it.string }
    }

    private fun deleteLongPress() = scope.launch {
        while (isActive) {
            // Cache frequently accessed properties
            val insertString = _inputString.value
            val tailIsEmpty = stringInTail.get().isEmpty()

            // Exit conditions
            if (onDeleteLongPressUp.get() || !deleteKeyLongKeyPressed.get()) {
                enableContinuousTapInput()
                if (_inputString.value.isEmpty()) suggestionAdapter.suggestions = emptyList()
                break
            }

            if (insertString.isEmpty()) {
                if (tailIsEmpty) {
                    // Both insertString and stringInTail are empty
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                } else {
                    // insertString is empty but stringInTail is not
                    enableContinuousTapInput()
                    break
                }
            } else {
                // insertString is not empty
                val newLength = insertString.length - 1
                if (newLength == 0) {
                    if (tailIsEmpty) {
                        setComposingText("", 0)
                    }
                    _inputString.value = EMPTY_STRING
                } else {
                    _inputString.value = insertString.substring(0, newLength)
                }
            }

            delay(LONG_DELAY_TIME)
        }
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

            InputTypeForIME.TextWebSearchView,
            InputTypeForIME.TextWebSearchViewFireFox,
            InputTypeForIME.TextSearchView -> {
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
                        InputMode.ModeJapanese -> handleJapaneseModeSpaceKey(
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
            smoothScrollToPosition((suggestionClickNum - 1).coerceAtLeast(0))
            suggestionAdapter.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
        }
        setConvertLetterInJapaneseFromButton(suggestions, true, mainView, insertString)
    }

    private fun handleNonEmptyInputEnterKey(
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardView.apply {
            when (currentInputMode) {
                InputMode.ModeJapanese -> {
                    if (isHenkan.get()) {
                        handleHenkanModeEnterKey(suggestions)
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
                suggestionAdapter.updateHighlightPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
            }
            setConvertLetterInJapaneseFromButton(suggestions, false, mainView, insertString)
        }
    }

    private fun handleHenkanModeEnterKey(suggestions: List<Candidate>) {
        if (suggestionClickNum !in suggestions.indices) {
            suggestionClickNum = 0
        }
        setEnterKeyAction(suggestions)
    }

    private fun handleEmptyInputEnterKey(mainView: MainLayoutBinding) {
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            stringInTail.set(EMPTY_STRING)
        } else {
            setEnterKeyPress()
            isHenkan.set(false)
            suggestionClickNum = 0
            suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun setDrawableToEnterKeyCorrespondingToImeOptions(mainView: MainLayoutBinding) {
        val currentDrawable = when (currentInputType) {
            InputTypeForIME.TextWebSearchView,
            InputTypeForIME.TextWebSearchViewFireFox,
            InputTypeForIME.TextSearchView -> drawableSearch

            InputTypeForIME.TextMultiLine,
            InputTypeForIME.TextImeMultiLine,
            InputTypeForIME.TextShortMessage,
            InputTypeForIME.TextLongMessage -> drawableReturn

            InputTypeForIME.TextEmailAddress,
            InputTypeForIME.TextEmailSubject,
            InputTypeForIME.TextNextLine -> drawableArrowTab

            InputTypeForIME.TextDone -> drawableCheck
            else -> drawableRightArrow
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
                    _inputString.value = EMPTY_STRING
                    suggestionAdapter.suggestions = emptyList()
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

    private fun asyncLeftLongPress() = scope.launch {
        while (isActive) {
            val insertString = _inputString.value
            if (onLeftKeyLongPressUp.get() || !leftCursorKeyLongKeyPressed.get()) {
                if (_inputString.value.isEmpty()) suggestionAdapter.suggestions = emptyList()
                break
            }
            if (stringInTail.get().isNotEmpty() && insertString.isEmpty()) break
            if (insertString.isNotEmpty()) {
                updateLeftInputString(insertString)
            } else {
                if (stringInTail.get().isEmpty() && !isCursorAtBeginning()) {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                }
            }
            delay(LONG_DELAY_TIME)
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

    private fun asyncRightLongPress() = scope.launch {
        while (isActive) {
            val insertString = _inputString.value
            if (onRightKeyLongPressUp.get() || !rightCursorKeyLongKeyPressed.get()) break
            if (stringInTail.get().isEmpty() && _inputString.value.isNotEmpty()) break
            actionInRightKeyPressed(insertString)
            delay(LONG_DELAY_TIME)
        }
    }

    private fun updateLeftInputString(insertString: String) {
        if (insertString.isNotEmpty()) {
            if (insertString.length == 1) {
                stringInTail.set(insertString + stringInTail.get())
                _inputString.value = EMPTY_STRING
                suggestionAdapter.suggestions = emptyList()
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
            suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
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
                suggestionAdapter.updateHighlightPosition(0)
                println("setConvertLetterInJapaneseFromButton space hasPrevious $suggestionClickNum ${suggestions.size}")
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter.updateHighlightPosition(0)
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

    override fun commitContent(p0: InputContentInfo, p1: Int, p2: Bundle?): Boolean {
        println("commitContent")
        if (currentInputConnection == null) return false
        return currentInputConnection.commitContent(p0, p1, p2)
    }
}
