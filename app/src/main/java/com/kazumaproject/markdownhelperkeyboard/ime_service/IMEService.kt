package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.transition.Transition
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.daasuu.bl.BubbleLayout
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.android.flexbox.AlignItems
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexWrap
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.EmojiKigouAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.KigouAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.InputModeSwitch
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.TenKeyInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.TenKeyMapHolder
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableEnglishSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableHenkan
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableKanaSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableLanguage
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableNumberSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableOpenBracket
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableReturn
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableRightArrow
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableSpaceBar
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.InputBackGroundDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.IoDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.MainDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpTextActive
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowBottom
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowCenter
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowLeft
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowRight
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowTop
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.SuggestionDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertDp2Px
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertUnicode
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getDakutenSmallChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextReturnInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowBottom
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowCenter
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowFlickBottom
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowFlickLeft
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowFlickRight
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowFlickTop
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowLeft
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowRight
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowTop
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTenKeyTextEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTenKeyTextJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTenKeyTextNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTenKeyTextWhenTapEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTenKeyTextWhenTapJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTenKeyTextWhenTapNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickBottomEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickBottomJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickBottomNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickLeftEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickLeftJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickLeftNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickRightEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickRightJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickRightNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickTopEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickTopJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextFlickTopNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextTapEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextTapJapanese
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setTextTapNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.ImageEffects
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.KAOMOJI
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.ModeInKigou
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.abs


@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class IMEService: InputMethodService() {

    @Inject
    @Named("main_ime_scope")
    lateinit var scope : CoroutineScope
    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher
    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher
    @Inject
    @InputBackGroundDispatcher
    lateinit var imeIoDispatcher: CoroutineDispatcher
    @Inject
    @SuggestionDispatcher
    lateinit var suggestionDispatcher: CoroutineDispatcher

    @Inject
    lateinit var appPreference: AppPreference
    @Inject
    lateinit var tenKeyMap: TenKeyMapHolder
    @Inject
    lateinit var inputMethodManager: InputMethodManager
    @Inject
    @DrawableReturn
    lateinit var drawableReturn: Drawable
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
    @DrawableLanguage
    lateinit var drawableLanguage: Drawable
    @Inject
    @DrawableNumberSmall
    lateinit var drawableNumberSmall : Drawable
    @Inject
    @DrawableOpenBracket
    lateinit var drawableOpenBracket : Drawable
    @Inject
    @PopUpTextActive
    lateinit var mPopupWindowActive: PopupWindow
    private lateinit var bubbleViewActive: BubbleLayout
    private lateinit var popTextActive: MaterialTextView

    @Inject
    @PopUpWindowTop
    lateinit var mPopupWindowTop: PopupWindow
    private lateinit var bubbleViewTop: BubbleLayout
    private lateinit var popTextTop: MaterialTextView
    @Inject
    @PopUpWindowLeft
    lateinit var mPopupWindowLeft: PopupWindow
    private lateinit var bubbleViewLeft: BubbleLayout
    private lateinit var popTextLeft: MaterialTextView
    @Inject
    @PopUpWindowBottom
    lateinit var mPopupWindowBottom: PopupWindow
    private lateinit var bubbleViewBottom: BubbleLayout
    private lateinit var popTextBottom: MaterialTextView
    @Inject
    @PopUpWindowRight
    lateinit var mPopupWindowRight: PopupWindow
    private lateinit var bubbleViewRight: BubbleLayout
    private lateinit var popTextRight: MaterialTextView

    @Inject
    @PopUpWindowCenter
    lateinit var mPopupWindowCenter: PopupWindow
    private lateinit var bubbleViewCenter: BubbleLayout
    private lateinit var popTextCenter: MaterialTextView

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var extractedTextRequest: ExtractedTextRequest

    @Inject
    lateinit var transition: Transition

    private var mainLayoutBinding: MainLayoutBinding? = null
    
    private var suggestionAdapter: SuggestionAdapter?= null
    private var emojiKigouAdapter: EmojiKigouAdapter?= null
    private var kigouApdater: KigouAdapter?= null

    private val _currentInputMode = MutableStateFlow<InputMode>(InputMode.ModeJapanese)
    private val _inputString = MutableStateFlow(EMPTY_STRING)
    private var stringInTail = ""
    private val _currentKeyboardMode = MutableStateFlow<KeyboardMode>(KeyboardMode.ModeTenKeyboard)
    private val _currentModeInKigou = MutableStateFlow<ModeInKigou>(ModeInKigou.Null)
    private val _dakutenPressed = MutableStateFlow(false)
    private val _suggestionList = MutableStateFlow<List<Candidate>>(emptyList())
    private val _suggestionFlag = MutableStateFlow(false)
    private val _suggestionViewStatus = MutableStateFlow(true)

    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private var currentTenKeyId = 0
    private var lastFlickConvertedNextHiragana = false
    private var isContinuousTapInputEnabled = false
    private var englishSpaceKeyPressed = false
    private var tenKeysLongPressed = false

    private var firstXPoint = 0.0f
    private var firstYPoint = 0.0f
    private var suggestionClickNum = 0
    private var isHenkan = false
    private var onLeftKeyLongPressUp = false
    private var onRightKeyLongPressUp = false

    private var onDeleteLongPressUp = false
    private var deleteKeyLongKeyPressed = false

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private val shortVibrationEffect: VibrationEffect by lazy {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    private val shortCombinedVibration: CombinedVibration by lazy {
        CombinedVibration.createParallel(shortVibrationEffect)
    }

    private fun setVibrate(){
        appPreference.vibration_preference?.let {
            if(it) vibratorManager.vibrate(shortCombinedVibration)
        }
    }

    companion object {
        val NUMBER_KEY10_SYMBOL_CHAR = listOf('(',')','[',']')
        const val EMPTY_STRING = ""

        const val DISPLAY_LEFT_STRING_TIME = 16L
        const val DELAY_TIME = 1000L
        const val LONG_DELAY_TIME = 64L
        const val N_BEST = 4
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreateInputView(): View? {
        val ctx = ContextThemeWrapper(applicationContext, R.style.Theme_MarkdownKeyboard)
        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))
        return mainLayoutBinding?.root.apply {
            mainLayoutBinding?.let { mainView ->

                val keyList = listOf<Any>(
                    mainView.keyboardView.key1,
                    mainView.keyboardView.key2,
                    mainView.keyboardView.key3,
                    mainView.keyboardView.key4,
                    mainView.keyboardView.key5,
                    mainView.keyboardView.key6,
                    mainView.keyboardView.key7,
                    mainView.keyboardView.key8,
                    mainView.keyboardView.key9,
                    mainView.keyboardView.key11,
                    mainView.keyboardView.keySmallLetter,
                    mainView.keyboardView.key12
                )
                val flexboxLayoutManager = FlexboxLayoutManager(applicationContext).apply {
                    flexWrap = FlexWrap.WRAP
                    alignItems = AlignItems.STRETCH
                }

                initializeVariables()

                setTenKeyView(keyList)
                setSuggestionRecyclerView(flexboxLayoutManager)
                setKigouView()
                startScope(keyList, flexboxLayoutManager)
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        Timber.d("onUpdate onStartInputView called $restarting")
        currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
        resetAllFlags()
        setCurrentInputType(editorInfo)
        mainLayoutBinding?.keyboardView?.root?.isVisible = true
        mainLayoutBinding?.suggestionRecyclerView?.isVisible = true
        _suggestionViewStatus.update { true }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("onUpdate onFinishInput Called")
        resetAllFlags()
        mainLayoutBinding?.keyboardView?.root?.isVisible = true
        mainLayoutBinding?.suggestionRecyclerView?.isVisible = true
        _suggestionViewStatus.update { true }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
        mainLayoutBinding?.keyboardView?.root?.isVisible = true
        mainLayoutBinding?.suggestionRecyclerView?.isVisible = true
        _suggestionViewStatus.update { true }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        resetAllFlags()
    }
    override fun onDestroy(){
        super.onDestroy()
        actionInDestroy()
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        cursorAnchorInfo?.apply {
            Timber.d("onUpdateCursorAnchorInfo: $composingText ${_inputString.value}")
            if (composingText == null && stringInTail.isNotEmpty()) _inputString.update { EMPTY_STRING }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when(newConfig.orientation){
            Configuration.ORIENTATION_PORTRAIT ->{
                currentInputConnection?.finishComposingText()
            }
            Configuration.ORIENTATION_LANDSCAPE ->{
                currentInputConnection?.finishComposingText()
            }
            Configuration.ORIENTATION_UNDEFINED ->{
                currentInputConnection?.finishComposingText()
            }
            else -> {
                /** empty body **/
            }
        }
    }
    private fun startScope(
        keyList: List<Any>,
        flexboxLayoutManager: FlexboxLayoutManager
    ) = scope.launch {
        mainLayoutBinding?.let { mainView ->
            launch {
                _suggestionFlag.asStateFlow().collectLatest {
                    setSuggestionOnView(mainView)
                    mainView.keyboardView.root.isVisible = true
                }
            }

            launch {
                _suggestionViewStatus.asStateFlow().collectLatest { isVisible ->
                    mainView.keyboardView.root.isVisible = isVisible
                    mainView.suggestionVisibility.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            if (isVisible) R.drawable.outline_arrow_drop_down_24 else R.drawable.outline_arrow_drop_up_24
                        )
                    )
                    mainView.suggestionRecyclerView.apply {
                        layoutManager = flexboxLayoutManager.apply {
                            flexDirection = if (isVisible) FlexDirection.COLUMN else FlexDirection.ROW
                            justifyContent = if (isVisible) JustifyContent.SPACE_AROUND else JustifyContent.FLEX_START
                        }
                    }

                    val marginsSuggestionView = (mainView.suggestionRecyclerView.layoutParams as FrameLayout.LayoutParams).apply {
                        leftMargin = 0
                        topMargin = 0
                        bottomMargin = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            if (isVisible) 200f.convertDp2Px(applicationContext) else 0
                        } else {
                            if (isVisible) 280f.convertDp2Px(applicationContext) else 0
                        }
                        height = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            if (isVisible) 52f.convertDp2Px(applicationContext) else 252f.convertDp2Px(applicationContext)
                        } else {
                            if (isVisible) 54f.convertDp2Px(applicationContext) else 336f.convertDp2Px(applicationContext)
                        }
                    }
                    mainView.suggestionRecyclerView.layoutParams = marginsSuggestionView
                }
            }

            launch {
                _suggestionList.asStateFlow().collectLatest { suggestions ->
                    suggestionAdapter?.suggestions = suggestions
                    mainView.suggestionRecyclerView.scrollToPosition(0)
                    mainView.suggestionVisibility.isVisible = suggestions.isNotEmpty()
                }
            }

            launch {
                _currentInputMode.asStateFlow().collectLatest { state ->
                    setKeyLayoutByInputMode(keyList, state, mainView.keyboardView.keySmallLetter)
                    mainView.keyboardView.keySwitchKeyMode.setInputMode(state)
                    mainView.keyboardView.keySwitchKeyMode.invalidate()
                }
            }

            launch {
                _currentKeyboardMode.asStateFlow().collectLatest {
                    when (it) {
                        is KeyboardMode.ModeTenKeyboard -> {
                            mainView.keyboardView.root.isVisible = true
                            mainView.keyboardKigouView.root.isVisible = false
                        }
                        is KeyboardMode.ModeKigouView -> {
                            mainView.keyboardView.root.isVisible = false
                            mainView.keyboardKigouView.root.isVisible = true
                            _currentModeInKigou.update {
                                ModeInKigou.Emoji
                            }
                        }
                    }
                }
            }

            launch {
                _currentModeInKigou.asStateFlow().collectLatest {
                    setTenKeyAndKigouView(it)
                }
            }

            launchInputString()
        }
    }

    private fun setCurrentInputType(attribute: EditorInfo?){
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME(inputType)
            Timber.d("setCurrentInputType: $currentInputType $inputType")
            when(currentInputType){
                InputTypeForIME.Text,
                InputTypeForIME.TextAutoComplete,
                InputTypeForIME.TextAutoCorrect,
                InputTypeForIME.TextCapCharacters,
                InputTypeForIME.TextCapSentences,
                InputTypeForIME.TextCapWords,
                InputTypeForIME.TextEmailSubject,
                InputTypeForIME.TextFilter,
                InputTypeForIME.TextMultiLine,
                InputTypeForIME.TextImeMultiLine,
                InputTypeForIME.TextShortMessage,
                InputTypeForIME.TextLongMessage,
                InputTypeForIME.TextNoSuggestion,
                InputTypeForIME.TextPersonName,
                InputTypeForIME.TextPhonetic,
                InputTypeForIME.TextWebEditText,
                InputTypeForIME.TextWebSearchView,
                InputTypeForIME.TextWebSearchViewFireFox,
                InputTypeForIME.TextSearchView
                -> {
                    _currentInputMode.value = InputMode.ModeJapanese
                }

                InputTypeForIME.TextEditTextInBookingTDBank,
                InputTypeForIME.TextUri,
                InputTypeForIME.TextPostalAddress,
                InputTypeForIME.TextEmailAddress,
                InputTypeForIME.TextWebEmailAddress,
                InputTypeForIME.TextPassword,
                InputTypeForIME.TextVisiblePassword,
                InputTypeForIME.TextWebPassword,
                ->{
                    _currentInputMode.value = InputMode.ModeEnglish
                }

                InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate ->{
                    currentInputConnection?.requestCursorUpdates(0)
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
                    _currentInputMode.value = InputMode.ModeNumber
                }

            }
        }
    }

    private fun initializeVariables(){
        bubbleViewActive = mPopupWindowActive.contentView.findViewById(R.id.bubble_layout)
        popTextActive = mPopupWindowActive.contentView.findViewById(R.id.popup_text)

        bubbleViewTop = mPopupWindowTop.contentView.findViewById(R.id.bubble_layout_top)
        popTextTop = mPopupWindowTop.contentView.findViewById(R.id.popup_text_top)

        bubbleViewLeft = mPopupWindowLeft.contentView.findViewById(R.id.bubble_layout_left)
        popTextLeft = mPopupWindowLeft.contentView.findViewById(R.id.popup_text_left)

        bubbleViewBottom = mPopupWindowBottom.contentView.findViewById(R.id.bubble_layout_bottom)
        popTextBottom = mPopupWindowBottom.contentView.findViewById(R.id.popup_text_bottom)

        bubbleViewRight = mPopupWindowRight.contentView.findViewById(R.id.bubble_layout_right)
        popTextRight = mPopupWindowRight.contentView.findViewById(R.id.popup_text_right)

        bubbleViewCenter = mPopupWindowCenter.contentView.findViewById(R.id.bubble_layout_center)
        popTextCenter = mPopupWindowCenter.contentView.findViewById(R.id.popup_text_center)

        suggestionAdapter = SuggestionAdapter()
        emojiKigouAdapter = EmojiKigouAdapter()
        kigouApdater = KigouAdapter()
    }

    private fun setTenKeyView(
        keyList: List<Any>
    ){
        mainLayoutBinding?.let { mainView ->
            setSpaceKey(mainView.keyboardView.keySpace)
            setDeleteKey(mainView.keyboardView.keyDelete)
            setLeftKey(mainView.keyboardView.keySoftLeft)
            setRightKey(mainView.keyboardView.keyMoveCursorRight)
            setNextReturnKey(mainView.keyboardView.keyReturn)
            setSwitchModeKey(mainView.keyboardView.keySwitchKeyMode)
            setEnterKey(mainView.keyboardView.keyEnter)
            setLanguageSwitchKey(mainView.keyboardView.keyLanguageSwitch)

            setKeyLayoutByInputMode(
                keyList,
                _currentInputMode.value,
                mainView.keyboardView.keySmallLetter
            )

            setTouchListenerForMainKeys(
                keyList,
                popTextActive,
                bubbleViewActive,
                popTextTop,
                bubbleViewTop,
                popTextLeft,
                bubbleViewLeft,
                popTextBottom,
                bubbleViewBottom,
                popTextRight,
                bubbleViewRight,
                popTextCenter,
                bubbleViewCenter
            )
        }
    }

    private fun setSuggestionRecyclerView(
        flexboxLayoutManager: FlexboxLayoutManager
    ){
        suggestionAdapter?.apply {
            setHasStableIds(true)
            this.setOnItemClickListener {
                setVibrate()
                setCandidateClick(it)
                resetFlagsSuggestionClick()
            }
        }
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                itemAnimator = null
                suggestionAdapter?.let { sugAdapter ->
                    adapter = sugAdapter
                    layoutManager = flexboxLayoutManager.apply {
                        flexDirection = FlexDirection.ROW
                        justifyContent = JustifyContent.FLEX_START
                    }
                }
            }
            mainView.suggestionVisibility.setOnClickListener {
                _suggestionViewStatus.update { !it }
            }
        }
    }

    private fun setCandidateClick(candidate: Candidate) {
        val candidateType = candidate.type.toInt()
        if (candidateType == 2 || candidateType == 5 || candidateType == 7) {
            stringInTail = _inputString.value.substring(candidate.length.toInt())
        }

        if (_inputString.value.isNotBlank()) {
            scope.launch {
                commitCandidateText(candidate.string)
                updateSuggestionList()
                handleStringInTail()
                showSuggestionView()
            }
        }
    }

    private fun commitCandidateText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun updateSuggestionList() {
        _suggestionList.update { emptyList() }
    }

    private suspend fun handleStringInTail() {
        if (stringInTail.isNotEmpty()) {
            delay(DISPLAY_LEFT_STRING_TIME)
            _inputString.update { stringInTail }
            stringInTail = EMPTY_STRING
        } else {
            _inputString.update { EMPTY_STRING }
        }
    }

    private fun showSuggestionView() {
        if (!_suggestionViewStatus.value) {
            _suggestionViewStatus.update { true }
        }
    }
    private fun setKigouView(){
        mainLayoutBinding?.let { mainView ->
            mainView.keyboardKigouView.kigouReturnBtn.setOnClickListener {
                setVibrate()
                _currentKeyboardMode.value = KeyboardMode.ModeTenKeyboard
            }
            mainView.keyboardKigouView.kigouEmojiButton.setOnClickListener {
                setVibrate()
                _currentModeInKigou.value = ModeInKigou.Emoji
            }
            mainView.keyboardKigouView.kigouKaomojiBtn.setOnClickListener {
                setVibrate()
                _currentModeInKigou.value = ModeInKigou.Kaomoji
            }
            setDeleteKeyKigou(mainView.keyboardKigouView.kigouDeleteBtn)
            emojiKigouAdapter?.let { a ->
                a.emojiList = EMOJI_LIST
                a.setOnItemClickListener { emoji ->
                    setVibrate()
                    currentInputConnection?.commitText(emoji.unicode.convertUnicode(),1)
                }
            }
            kigouApdater?.let { a ->
                a.kigouList = KAOMOJI
                a.setOnItemClickListener { s ->
                    setVibrate()
                    currentInputConnection?.commitText(s,1)
                }
            }
        }
    }

    private fun setTenKeyAndKigouView(modeInKigou: ModeInKigou){
        mainLayoutBinding?.let { mainView ->
            when(modeInKigou){
                is ModeInKigou.Emoji ->{
                    emojiKigouAdapter?.let { a ->
                        mainView.keyboardKigouView.kigouRecyclerView.apply {
                            adapter = a
                            layoutManager = GridLayoutManager(applicationContext,6)
                        }
                    }
                    mainView.keyboardKigouView.kigouEmojiButton.isChecked = true
                }
                is ModeInKigou.Kaomoji ->{
                    kigouApdater?.let { a ->
                        mainView.keyboardKigouView.kigouRecyclerView.apply {
                            adapter = a
                            layoutManager = GridLayoutManager(applicationContext,3)
                        }
                    }
                    mainView.keyboardKigouView.kigouKaomojiBtn.isChecked = true
                }
                is ModeInKigou.Null ->{
                    emojiKigouAdapter?.let { a ->
                        mainView.keyboardKigouView.kigouRecyclerView.apply {
                            adapter = a
                            layoutManager = GridLayoutManager(applicationContext,6)
                        }
                    }
                    mainView.keyboardKigouView.kigouEmojiButton.isChecked = true
                }
            }
        }
    }

    private fun resetAllFlags(){
        Timber.d("onUpdate resetAllFlags called")
        _currentKeyboardMode.update { KeyboardMode.ModeTenKeyboard }
        _inputString.update { EMPTY_STRING }
        _suggestionList.update { emptyList() }
        _currentInputMode.update { InputMode.ModeJapanese }
        stringInTail = EMPTY_STRING
        suggestionClickNum = 0
        isHenkan = false
        isContinuousTapInputEnabled = false
        deleteKeyLongKeyPressed = false
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = false
        onDeleteLongPressUp = false
    }

    private fun actionInDestroy(){
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        suggestionAdapter = null
        emojiKigouAdapter = null
        kigouApdater = null
        mainLayoutBinding = null
        currentInputConnection?.closeConnection()
        scope.coroutineContext.cancelChildren()
    }
    private fun resetFlagsSwitchMode(){
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
    }

    private fun resetFlagsSuggestionClick(){
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        _inputString.update { EMPTY_STRING }
    }

    private fun resetFlagsLanguageModeClick(){
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        _inputString.update { EMPTY_STRING }
    }

    private fun resetFlagsEnterKey(){
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        _inputString.update { EMPTY_STRING }
        stringInTail = ""
    }

    private fun resetFlagsKeySpace(){
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        isContinuousTapInputEnabled = false
        lastFlickConvertedNextHiragana = false
        englishSpaceKeyPressed = false
    }

    private fun resetFlagsDeleteKey(){
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        isHenkan = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
    }

    private suspend fun launchInputString() = withContext(scope.coroutineContext){
        _inputString.asStateFlow().collectLatest { inputString ->
            Timber.d("launchInputString: $inputString")

            if (inputString.isNotBlank()) {
                /** 入力された文字の selection と composing region を設定する **/
                val spannableString = SpannableString(inputString + stringInTail)
                setComposingTextPreEdit(inputString, spannableString)
                _suggestionFlag.update { flag -> !flag }
                delay(DELAY_TIME)
                if (!isHenkan && inputString.isNotEmpty() && !onDeleteLongPressUp &&
                    !englishSpaceKeyPressed && !deleteKeyLongKeyPressed) {
                    isContinuousTapInputEnabled = true
                    lastFlickConvertedNextHiragana = true
                    println("current cursor pos: ${getCurrentText()} ${inputString.length}")
                    setComposingTextAfterEdit(inputString, spannableString)
                }
            } else {
                _suggestionList.update { emptyList() }
                setTenkeyIconsEmptyInputString()
                if (stringInTail.isNotEmpty()) {
                    currentInputConnection?.setComposingText(stringInTail, 1)
                }
            }
        }
    }

    private fun getCurrentText(): String? {
        val extractedText: ExtractedText? = currentInputConnection?.getExtractedText(extractedTextRequest, 0)
        return extractedText?.text?.toString()
    }

    private fun setComposingTextPreEdit(
        inputString: String,
        spannableString: SpannableString
    ){
        if (isContinuousTapInputEnabled && lastFlickConvertedNextHiragana){
            spannableString.apply {
                setSpan(BackgroundColorSpan(getColor(R.color.green)),0,inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(UnderlineSpan(),0,inputString.length + stringInTail.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }else{
            spannableString.apply {
                setSpan(BackgroundColorSpan(getColor(R.color.green)),0,inputString.length - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(getColor(R.color.char_in_edit_color)),inputString.length - 1,inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(UnderlineSpan(),0,inputString.length + stringInTail.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        currentInputConnection?. setComposingText(spannableString,1)
    }

    private fun setComposingTextAfterEdit(
        inputString: String,
        spannableString: SpannableString
    ){
        spannableString.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.blue)),0,inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(),0,inputString.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        currentInputConnection?.setComposingText(spannableString,1)
    }

    private fun setEnterKeyAction(listIterator: ListIterator<Candidate>) = scope.launch {
        _suggestionList.update { emptyList() }
        val nextSuggestion = listIterator.next()
        currentInputConnection?.commitText(nextSuggestion.string,1)
        if (stringInTail.isNotEmpty()){
            delay(DISPLAY_LEFT_STRING_TIME)
            _inputString.update { stringInTail }
            stringInTail = EMPTY_STRING
        }else{
            _inputString.update { EMPTY_STRING }
        }
    }

    private suspend fun setTenkeyIconsEmptyInputString() = withContext(mainDispatcher){
        mainLayoutBinding?.let { mainView ->
            mainView.keyboardView.keySpace.apply {
                setImageDrawable(drawableSpaceBar)
            }
            mainView.keyboardView.keyEnter.apply {
                setImageDrawable(drawableRightArrow)
            }
            when(_currentInputMode.value){
                is InputMode.ModeJapanese, is InputMode.ModeEnglish ->{
                    mainView.keyboardView.keySmallLetter.apply {
                        setImageDrawable(drawableLanguage)
                    }
                }
                is InputMode.ModeNumber ->{
                    mainView.keyboardView.keySmallLetter.apply {
                        setImageDrawable(drawableNumberSmall)
                    }
                }
            }
        }
    }

    private suspend fun updateSuggestionUI(mainView: MainLayoutBinding) = withContext(mainDispatcher){
        mainView.keyboardView.keyEnter.apply {
            setImageDrawable(drawableReturn)
        }
        when(_currentInputMode.value){
            InputMode.ModeJapanese ->{
                mainView.keyboardView.keySmallLetter.apply {
                    setImageDrawable(drawableKanaSmall)
                }
                mainView.keyboardView.keySpace.apply {
                    setImageDrawable(drawableHenkan)
                }
            }
            InputMode.ModeEnglish ->{
                mainView.keyboardView.keySmallLetter.apply {
                    setImageDrawable(drawableEnglishSmall)
                }
            }
            InputMode.ModeNumber ->{/** empty body **/}
        }

    }

    private suspend fun setSuggestionOnView(
        mainView: MainLayoutBinding,
    ) {
        when {
            _inputString.value.isNotBlank() -> {
                if (suggestionClickNum != 0) return
                setSuggestionForJapanese(mainView)
            }
        }
    }

    private suspend fun setSuggestionForJapanese(mainView: MainLayoutBinding) {
        updateSuggestionUI(mainView)
        _suggestionList.update {
            getSuggestionList(ioDispatcher)
        }
    }

    private suspend fun getSuggestionList(
        ioDispatcher: CoroutineDispatcher
    )= scope.async{
        val queryText = _inputString.value
        return@async try {
            println("input str: $queryText")
            kanaKanjiEngine.getCandidates(queryText, N_BEST,ioDispatcher)
        }catch (e: Exception){
            if (e is CancellationException) throw e
            println(e.stackTraceToString())
            emptyList()
        }
    }.await()

    private fun deleteLongPress() = scope.launch {
        while (isActive) {
            if (_inputString.value.isNotEmpty()) {
                if (_inputString.value.length == 1) {
                    currentInputConnection?.commitText("", 0)
                    _inputString.update { EMPTY_STRING }
                } else {
                    _inputString.update { it.dropLast(1) }
                }
            } else {
                if (stringInTail.isEmpty()) {
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                }
            }

            if (_inputString.value.isEmpty() && stringInTail.isNotEmpty()) {
                enableContinuousTapInput()
                return@launch
            }

            delay(LONG_DELAY_TIME)

            if (onDeleteLongPressUp) {
                enableContinuousTapInput()
                return@launch
            }
        }
    }

    private fun enableContinuousTapInput() {
        isContinuousTapInputEnabled = true
        lastFlickConvertedNextHiragana = true
    }

    private fun setEnterKeyPress(){
        setVibrate()
        when(currentInputType){

            InputTypeForIME.TextMultiLine,
            InputTypeForIME.TextImeMultiLine ->{
                currentInputConnection?.commitText("\n",1)
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
            InputTypeForIME.TextShortMessage,
            InputTypeForIME.TextLongMessage,
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
            InputTypeForIME.TextWebSearchView,
            InputTypeForIME.TextNotCursorUpdate,
            InputTypeForIME.TextWebSearchViewFireFox,
            InputTypeForIME.TextEditTextInBookingTDBank
            -> {
                currentInputConnection?.apply {
                    Timber.d("Enter key: called 3\n" )
                    sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER
                        )
                    )
                }
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
                currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.TextSearchView ->{
                currentInputConnection?.apply {
                    Timber.d("enter key search: ${EditorInfo.IME_ACTION_SEARCH}" +
                            "\n${currentInputEditorInfo.inputType}" +
                            "\n${currentInputEditorInfo.imeOptions}" +
                            "\n${currentInputEditorInfo.actionId}" +
                            "\n${currentInputEditorInfo.privateImeOptions}")
                    performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                }
            }

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDeleteKey( imageButton: AppCompatImageButton) = imageButton.apply {
        setOnTouchListener { _, event ->
            when(event.action and MotionEvent.ACTION_MASK){
                MotionEvent.ACTION_UP ->{
                    scope.launch {
                        onDeleteLongPressUp = true
                        deleteKeyLongKeyPressed = false
                    }
                }
            }
            return@setOnTouchListener false
        }
        setOnClickListener {
            setVibrate()
            when{
                _inputString.value.isNotEmpty()  ->{
                    deleteStringCommon()
                    resetFlagsDeleteKey()
                }
                else ->{
                    if (stringInTail.isNotEmpty()) return@setOnClickListener
                    currentInputConnection?.apply {
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                    }
                }
            }
        }

        setOnLongClickListener {
            onDeleteLongPressUp = false
            deleteLongPress()
            _dakutenPressed.value = false
            englishSpaceKeyPressed = false
            deleteKeyLongKeyPressed = true
            return@setOnLongClickListener true
        }
    }

    private fun setSpaceKey(imageButton: AppCompatImageButton) = imageButton.apply {
        setOnClickListener {
            handleSpaceKeyClick()
        }
        setOnLongClickListener {
            handleSpaceKeyLongClick()
            true
        }
    }

    private fun handleSpaceKeyClick() {
        setVibrate()
        if (_inputString.value.isNotEmpty()) {
            when (_currentInputMode.value) {
                InputMode.ModeJapanese -> handleJapaneseModeSpaceKey()
                else -> setSpaceKeyActionEnglishAndNumberNotEmpty()
            }
        } else {
            setSpaceKeyActionEnglishAndNumberEmpty()
        }
        resetFlagsKeySpace()
    }

    private fun handleSpaceKeyLongClick() {
        setVibrate()
        if (_suggestionList.value.isNotEmpty() && _inputString.value.isNotEmpty()) {
            when (_currentInputMode.value) {
                InputMode.ModeJapanese -> handleJapaneseModeSpaceKey()
                else -> commitTextWithSpace()
            }
        } else {
            commitTextWithSpace()
        }
        resetFlagsKeySpace()
    }

    private fun handleJapaneseModeSpaceKey() {
        isHenkan = true
        setConvertLetterInJapaneseFromButton(_suggestionList.value)
        suggestionClickNum += 1
    }

    private fun commitTextWithSpace() {
        currentInputConnection?.apply {
            commitText(_inputString.value + " ", 1)
        }
        _inputString.value = EMPTY_STRING
    }

    private fun setEnterKey(imageButton: AppCompatImageButton) = imageButton.apply {
        setOnClickListener {
            setVibrate()
            if (_inputString.value.isNotEmpty()) {
                handleNonEmptyInputEnterKey()
            } else {
                handleEmptyInputEnterKey()
            }
        }
    }

    private fun handleNonEmptyInputEnterKey() {
        when (_currentInputMode.value) {
            InputMode.ModeJapanese -> {
                if (isHenkan) {
                    handleHenkanModeEnterKey()
                } else {
                    finishInputEnterKey()
                }
            }
            else -> finishInputEnterKey()
        }
        resetFlagsEnterKey()
    }

    private fun handleHenkanModeEnterKey() {
        if (suggestionClickNum > _suggestionList.value.size) suggestionClickNum = 0
        val listIterator = _suggestionList.value.listIterator(suggestionClickNum.coerceAtLeast(1) - 1)
        setEnterKeyAction(listIterator)
    }

    private fun handleEmptyInputEnterKey() {
        if (stringInTail.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            stringInTail = EMPTY_STRING
        } else {
            setEnterKeyPress()
            isHenkan = false
            suggestionClickNum = 0
        }
    }

    private fun finishInputEnterKey() {
        currentInputConnection?.finishComposingText()
        _inputString.update { EMPTY_STRING }
    }

    private fun setLanguageSwitchKey(appCompatButton: AppCompatButton) = appCompatButton.apply {
        setOnClickListener {
            setVibrate()
            _currentKeyboardMode.value = KeyboardMode.ModeKigouView
            if (_inputString.value.isNotEmpty()){
                var newPos = _inputString.value.length - (_inputString.value.length  - 1)
                if (newPos < 0 || newPos == 0) newPos = 1
                currentInputConnection?.apply {
                    commitText(_inputString.value,newPos)
                    finishComposingText()
                }
                _inputString.value = EMPTY_STRING
            }
            resetFlagsLanguageModeClick()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDeleteKeyKigou(
        imageButton: AppCompatImageButton?,
    ) = imageButton?.apply {

        setOnTouchListener { _, event ->
            when(event.action and MotionEvent.ACTION_MASK){
                MotionEvent.ACTION_UP ->{
                    scope.launch {
                        onDeleteLongPressUp = true
                        deleteKeyLongKeyPressed = false
                    }
                }
            }
            return@setOnTouchListener false
        }

        setOnClickListener {
            setVibrate()
            if (_inputString.value.isNotEmpty()){
                deleteStringCommon()
                resetFlagsDeleteKey()
            }else{
                currentInputConnection?.apply {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                }
            }
        }

        setOnLongClickListener {
            onDeleteLongPressUp = false
            deleteLongPress()
            deleteKeyLongKeyPressed = true
            _dakutenPressed.value = false
            englishSpaceKeyPressed = false
            lastFlickConvertedNextHiragana = false
            true
        }
    }

    /**
     *   日本語入力 -> 英語 -> 数字 -> 日本語
     *
     *   Input mode:
     *   Japanese -> English -> Numbers -> Japanese
     *
     **/
    private fun setSwitchModeKey(inputModeSwitch: InputModeSwitch?) = inputModeSwitch?.apply {
        setOnClickListener {
            setVibrate()
            when(getCurrentInputMode()){
                is InputMode.ModeJapanese ->{
                    setInputMode(InputMode.ModeEnglish)
                    _currentInputMode.value = InputMode.ModeEnglish
                }
                is InputMode.ModeEnglish ->{
                    setInputMode(InputMode.ModeNumber)
                    _currentInputMode.value = InputMode.ModeNumber
                }
                is InputMode.ModeNumber ->{
                    setInputMode(InputMode.ModeJapanese)
                    _currentInputMode.value = InputMode.ModeJapanese
                }
            }
            resetFlagsSwitchMode()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setLeftKey(leftKey: AppCompatImageButton) {
        leftKey.apply {
            setOnTouchListener { _, event ->
                if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                    scope.launch {
                        onLeftKeyLongPressUp = true
                        delay(100)
                        onLeftKeyLongPressUp = false
                        if (_inputString.value.isBlank() || _inputString.value.isEmpty()) {
                            _suggestionList.value = emptyList()
                        }
                    }
                }
                return@setOnTouchListener false
            }

            setOnClickListener {
                setVibrate()
                handleLeftKeyPress()
            }

            setOnLongClickListener {
                setVibrate()
                handleLeftLongPress()
                true
            }
        }
    }

    private fun handleLeftKeyPress() {

        if (_inputString.value.isEmpty() && stringInTail.isEmpty()) {
            //currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        } else if (!isHenkan) {
            lastFlickConvertedNextHiragana = true
            isContinuousTapInputEnabled = true
            englishSpaceKeyPressed = false
            suggestionClickNum = 0
            if (_inputString.value.isNotEmpty()) {
                stringInTail = StringBuilder(stringInTail)
                    .insert(0, _inputString.value.last())
                    .toString()
                _inputString.update { it.dropLast(1) }
            }
        }
    }

    private fun handleLeftLongPress() {
        if (!isHenkan) {
            lastFlickConvertedNextHiragana = true
            isContinuousTapInputEnabled = true
            onLeftKeyLongPressUp = false
            suggestionClickNum = 0
            scope.launch {
                while (isActive) {
                    if (_inputString.value.isNotEmpty()) updateLeftInputString()
                    delay(LONG_DELAY_TIME)
                    if (onLeftKeyLongPressUp) return@launch
                }
            }
        }
    }

    private fun sendLeftKeyEvents() {
        currentInputConnection?.apply {
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        }
    }

    private fun updateLeftInputString() {
        if (_inputString.value.isNotEmpty()) {
            stringInTail = StringBuilder(stringInTail)
                .insert(0, _inputString.value.last())
                .toString()
            _inputString.update { it.dropLast(1) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setRightKey(rightKey: AppCompatImageButton) {
        rightKey.apply {
            setOnTouchListener { _, event ->
                if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_UP) {
                    scope.launch {
                        onRightKeyLongPressUp = true
                        delay(100)
                        onRightKeyLongPressUp = false
                    }
                }
                false
            }

            setOnClickListener {
                setVibrate()
                actionInRightKeyPressed()
            }

            setOnLongClickListener {
                setVibrate()
                if (!isHenkan) {
                    onRightKeyLongPressUp = false
                    suggestionClickNum = 0
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    scope.launch {
                        while (isActive) {

                            actionInRightKeyPressed()
                            delay(LONG_DELAY_TIME)
                            if (onRightKeyLongPressUp) return@launch
                        }
                    }
                }
                true
            }
        }
    }

    private fun actionInRightKeyPressed() {
        when {
            _inputString.value.isEmpty() -> handleEmptyInputString()
            !isHenkan -> handleNonHenkan()
        }
    }

    private fun handleEmptyInputString() {
        if (stringInTail.isEmpty()) {
            //currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        } else {
            val dropString = stringInTail.first()
            stringInTail = stringInTail.drop(1)
            _inputString.update { dropString.toString() }
        }
    }

    private fun handleNonHenkan() {
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        suggestionClickNum = 0
        if (stringInTail.isNotEmpty()) {
            val dropString = stringInTail.first()
            stringInTail = stringInTail.drop(1)
            _inputString.update { it + dropString }
        }
    }

    /**
     *
     *  前の文字に戻す　あ -> ぉ -> ぇ
     *
     * **/
    private fun setNextReturnKey(
        nextReturn: AppCompatImageButton,
    ){
        nextReturn.setOnClickListener {
            setVibrate()
            when(_currentInputMode.value){
                is InputMode.ModeNumber ->{

                }
                else ->{
                    setNextReturnInputCharacter()

                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListenerForMainKeys(
        keyList: List<Any>,
        popTextActive: MaterialTextView,
        bubbleLayoutActive: BubbleLayout,
        popTextTop: MaterialTextView,
        bubbleLayoutTop: BubbleLayout,
        popTextLeft: MaterialTextView,
        bubbleLayoutLeft: BubbleLayout,
        popTextBottom: MaterialTextView,
        bubbleLayoutBottom: BubbleLayout,
        popTextRight: MaterialTextView,
        bubbleLayoutRight: BubbleLayout,
        popTextCenter: MaterialTextView,
        bubbleLayoutCenter: BubbleLayout
    ){
        keyList.forEach {
            if (it is AppCompatButton){
                it.setOnTouchListener { v, event ->
                    val insertString = _inputString.value
                    val sb = StringBuilder()

                    when(event.action and MotionEvent.ACTION_MASK){
                        MotionEvent.ACTION_DOWN ->{
                            setVibrate()
                            firstXPoint = event.rawX
                            firstYPoint = event.rawY
                            currentTenKeyId = v.id
                            return@setOnTouchListener false
                        }
                        MotionEvent.ACTION_UP ->{
                            if (tenKeysLongPressed){
                                mainLayoutBinding?.root?.let { a ->
                                    ImageEffects.removeBlurEffect(a)
                                }
                                tenKeysLongPressed = false
                            }
                            setVibrate()
                            val finalX = event.rawX
                            val finalY = event.rawY

                            val distanceX = (finalX - firstXPoint)
                            val distanceY = (finalY - firstYPoint)
                            hidePopUpWindowActive()
                            hidePopUpWindowCenter()
                            hidePopUpWindowTop()
                            hidePopUpWindowLeft()
                            hidePopUpWindowBottom()
                            hidePopUpWindowRight()

                            if (currentTenKeyId !in tenKeyMap.keysJapanese) {
                                return@setOnTouchListener false
                            }

                            val keyInfo = when(_currentInputMode.value){
                                is InputMode.ModeJapanese -> tenKeyMap.getTenKeyInfoJapanese(currentTenKeyId)
                                is InputMode.ModeEnglish -> tenKeyMap.getTenKeyInfoEnglish(currentTenKeyId)
                                is InputMode.ModeNumber -> tenKeyMap.getTenKeyInfoNumber(currentTenKeyId)
                            }

                            when{
                                /** Tap **/
                                abs(distanceX) < 100 && abs(distanceY) < 100 ->{
                                    if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) sendCharTap(keyInfo.tap, insertString, sb)
                                    when(_currentInputMode.value){
                                        is InputMode.ModeJapanese ->{
                                            it.setTenKeyTextJapanese(currentTenKeyId)
                                        }
                                        is InputMode.ModeEnglish ->{
                                            it.setTenKeyTextEnglish(currentTenKeyId)
                                        }
                                        is InputMode.ModeNumber ->{
                                            it.setTenKeyTextNumber(currentTenKeyId)
                                        }
                                    }
                                    it.background = ContextCompat.getDrawable(applicationContext,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(applicationContext,R.color.keyboard_icon_color))
                                    currentTenKeyId = 0

                                    return@setOnTouchListener false
                                }
                                /** Flick Right **/
                                abs(distanceX) > abs(distanceY) && firstXPoint < finalX ->{
                                    if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
                                        keyInfo.flickRight?.let { c ->
                                            sendCharFlick(c, insertString, sb)
                                        }
                                    }
                                }
                                /** Flick Left **/
                                abs(distanceX) > abs(distanceY) && firstXPoint >= finalX ->{
                                    if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
                                        if (keyInfo.flickLeft != ' '){
                                            sendCharFlick(keyInfo.flickLeft, insertString, sb)
                                        }
                                    }
                                }
                                /** Flick Bottom **/
                                abs(distanceX) <= abs(distanceY) && firstYPoint < finalY ->{
                                    if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo){
                                        keyInfo.flickBottom?.let { c ->
                                            sendCharFlick(c, insertString, sb)
                                        }
                                    }
                                }
                                /** Flick Top **/
                                abs(distanceX) <= abs(distanceY) && firstYPoint >= finalY ->{
                                    if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
                                        if (keyInfo.flickTop != ' ') sendCharFlick(keyInfo.flickTop, insertString, sb)
                                    }
                                }
                            }
                            lastFlickConvertedNextHiragana = true
                            isContinuousTapInputEnabled = true
                            when(_currentInputMode.value){
                                is InputMode.ModeJapanese ->{
                                    it.setTenKeyTextJapanese(currentTenKeyId)
                                }
                                is InputMode.ModeEnglish ->{
                                    it.setTenKeyTextEnglish(currentTenKeyId)
                                }
                                is InputMode.ModeNumber ->{
                                    it.setTenKeyTextNumber(currentTenKeyId)
                                }
                            }
                            currentTenKeyId = 0
                            it.background = ContextCompat.getDrawable(applicationContext,R.drawable.ten_keys_center_bg)
                            it.setTextColor(ContextCompat.getColor(applicationContext,R.color.keyboard_icon_color))

                            return@setOnTouchListener false
                        }
                        MotionEvent.ACTION_MOVE ->{

                            val finalX = event.rawX
                            val finalY = event.rawY
                            val distanceX = (finalX - firstXPoint)
                            val distanceY = (finalY - firstYPoint)

                            when{
                                /** Tap **/
                                abs(distanceX) < 100 && abs(distanceY) < 100 ->{
                                    when(_currentInputMode.value){
                                        is InputMode.ModeJapanese ->{
                                            popTextActive.setTextTapJapanese(currentTenKeyId)
                                        }
                                        is InputMode.ModeEnglish ->{
                                            popTextActive.setTextTapEnglish(currentTenKeyId)
                                        }
                                        is InputMode.ModeNumber ->{
                                            popTextActive.setTextTapNumber(currentTenKeyId)
                                        }
                                    }
                                    if (!tenKeysLongPressed){
                                        hidePopUpWindowActive()
                                        it.setTextColor(ContextCompat.getColor(applicationContext,R.color.white))
                                        it.background = ContextCompat.getDrawable(applicationContext,R.drawable.ten_key_active_bg)
                                        when(_currentInputMode.value){
                                            is InputMode.ModeJapanese ->{
                                                it.setTenKeyTextWhenTapJapanese(currentTenKeyId)
                                            }
                                            is InputMode.ModeEnglish ->{
                                                it.setTenKeyTextWhenTapEnglish(currentTenKeyId)
                                            }
                                            is InputMode.ModeNumber ->{
                                                it.setTenKeyTextWhenTapNumber(currentTenKeyId)
                                            }
                                        }
                                    }else{
                                        //mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                                        mPopupWindowActive.setPopUpWindowCenter(applicationContext,bubbleLayoutActive,it)
                                    }
                                    return@setOnTouchListener false
                                }
                                /** Flick Right **/
                                abs(distanceX) > abs(distanceY) && firstXPoint < finalX ->{
                                    when(_currentInputMode.value){
                                        is InputMode.ModeJapanese ->{
                                            popTextActive.setTextFlickRightJapanese(currentTenKeyId)
                                            popTextCenter.setTextTapJapanese(currentTenKeyId)
                                        }
                                        is InputMode.ModeEnglish ->{
                                            popTextActive.setTextFlickRightEnglish(currentTenKeyId)
                                            popTextCenter.setTextTapEnglish(currentTenKeyId)
                                        }
                                        is InputMode.ModeNumber ->{
                                            popTextActive.setTextFlickRightNumber(currentTenKeyId)
                                            popTextCenter.setTextTapNumber(currentTenKeyId)
                                        }
                                    }
                                    if (mPopupWindowLeft.isShowing){
                                        mPopupWindowActive.setPopUpWindowRight(applicationContext,bubbleLayoutActive,it)
                                    }else{
                                        mPopupWindowActive.setPopUpWindowFlickRight(applicationContext,bubbleLayoutActive,it)
                                    }
                                }
                                /** Flick Left **/
                                abs(distanceX) > abs(distanceY) && firstXPoint >= finalX ->{
                                    when(_currentInputMode.value){
                                        is InputMode.ModeJapanese ->{
                                            popTextActive.setTextFlickLeftJapanese(currentTenKeyId)
                                            popTextCenter.setTextTapJapanese(currentTenKeyId)
                                        }
                                        is InputMode.ModeEnglish ->{
                                            popTextActive.setTextFlickLeftEnglish(currentTenKeyId)
                                            popTextCenter.setTextTapEnglish(currentTenKeyId)
                                        }
                                        is InputMode.ModeNumber ->{
                                            popTextActive.setTextFlickLeftNumber(currentTenKeyId)
                                            popTextCenter.setTextTapNumber(currentTenKeyId)
                                        }
                                    }
                                    if (mPopupWindowRight.isShowing){
                                        mPopupWindowActive.setPopUpWindowLeft(applicationContext,bubbleLayoutActive,it)
                                    }else{
                                        mPopupWindowActive.setPopUpWindowFlickLeft(applicationContext,bubbleLayoutActive,it)
                                    }
                                }
                                /** Flick Bottom **/
                                abs(distanceX) <= abs(distanceY) && firstYPoint < finalY ->{
                                    when(_currentInputMode.value){
                                        is InputMode.ModeJapanese ->{
                                            popTextActive.setTextFlickBottomJapanese(currentTenKeyId)
                                            popTextCenter.setTextTapJapanese(currentTenKeyId)
                                        }
                                        is InputMode.ModeEnglish ->{
                                            popTextActive.setTextFlickBottomEnglish(currentTenKeyId)
                                            popTextCenter.setTextTapEnglish(currentTenKeyId)
                                        }
                                        is InputMode.ModeNumber ->{
                                            popTextActive.setTextFlickBottomNumber(currentTenKeyId)
                                            popTextCenter.setTextTapNumber(currentTenKeyId)
                                        }
                                    }
                                    if (mPopupWindowTop.isShowing){
                                        mPopupWindowActive.setPopUpWindowBottom(applicationContext,bubbleLayoutActive,it)
                                    }else{
                                        mPopupWindowActive.setPopUpWindowFlickBottom(applicationContext,bubbleLayoutActive,it)
                                    }
                                }
                                /** Flick Top **/
                                abs(distanceX) <= abs(distanceY) && firstYPoint >= finalY ->{
                                    when(_currentInputMode.value){
                                        is InputMode.ModeJapanese ->{
                                            popTextActive.setTextFlickTopJapanese(currentTenKeyId)
                                            popTextCenter.setTextTapJapanese(currentTenKeyId)
                                        }
                                        is InputMode.ModeEnglish ->{
                                            popTextActive.setTextFlickTopEnglish(currentTenKeyId)
                                            popTextCenter.setTextTapEnglish(currentTenKeyId)
                                        }
                                        is InputMode.ModeNumber ->{
                                            popTextActive.setTextFlickTopNumber(currentTenKeyId)
                                            popTextCenter.setTextTapNumber(currentTenKeyId)
                                        }
                                    }
                                    if (mPopupWindowTop.isShowing){
                                        mPopupWindowActive.setPopUpWindowTop(applicationContext,bubbleLayoutActive,it)
                                    }else{
                                        mPopupWindowActive.setPopUpWindowFlickTop(applicationContext,bubbleLayoutActive,it)
                                    }
                                }
                            }
                            setTouchActionInMoveEnd(it)
                            return@setOnTouchListener false
                        }
                        else -> return@setOnTouchListener false
                    }
                }

                it.setOnLongClickListener { v ->
                    tenKeysLongPressed = true
                    when(_currentInputMode.value){
                        is InputMode.ModeJapanese ->{
                            popTextTop.setTextFlickTopJapanese(currentTenKeyId)
                            popTextLeft.setTextFlickLeftJapanese(currentTenKeyId)
                            popTextBottom.setTextFlickBottomJapanese(currentTenKeyId)
                            popTextRight.setTextFlickRightJapanese(currentTenKeyId)
                            popTextCenter.setTextTapJapanese(currentTenKeyId)
                        }
                        is InputMode.ModeEnglish ->{
                            popTextTop.setTextFlickTopEnglish(currentTenKeyId)
                            popTextLeft.setTextFlickLeftEnglish(currentTenKeyId)
                            popTextBottom.setTextFlickBottomEnglish(currentTenKeyId)
                            popTextRight.setTextFlickRightEnglish(currentTenKeyId)
                            popTextCenter.setTextTapEnglish(currentTenKeyId)
                        }
                        is InputMode.ModeNumber ->{
                            popTextTop.setTextFlickTopNumber(currentTenKeyId)
                            popTextLeft.setTextFlickLeftNumber(currentTenKeyId)
                            popTextBottom.setTextFlickBottomNumber(currentTenKeyId)
                            popTextRight.setTextFlickRightNumber(currentTenKeyId)
                            popTextCenter.setTextTapNumber(currentTenKeyId)
                        }
                    }
                    mPopupWindowTop.setPopUpWindowTop(applicationContext,bubbleLayoutTop,v)
                    mPopupWindowLeft.setPopUpWindowLeft(applicationContext,bubbleLayoutLeft,v)
                    mPopupWindowBottom.setPopUpWindowBottom(applicationContext,bubbleLayoutBottom,v)
                    mPopupWindowRight.setPopUpWindowRight(applicationContext,bubbleLayoutRight,v)
                    mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                    mPopupWindowActive.setPopUpWindowCenter(applicationContext,bubbleLayoutActive,it)
                    mainLayoutBinding?.root?.let { a ->
                        ImageEffects.applyBlurEffect(a,8f)
                    }
                    false
                }
            }
            if (it is AppCompatImageButton){
                it.setOnTouchListener { _, event ->
                    val insertString = _inputString.value
                    val sb = StringBuilder()
                    when(event.action and MotionEvent.ACTION_MASK){
                        MotionEvent.ACTION_DOWN ->{
                            setVibrate()
                            firstXPoint = event.rawX
                            firstYPoint = event.rawY
                            return@setOnTouchListener false
                        }
                        MotionEvent.ACTION_UP ->{
                            if (tenKeysLongPressed){
                                mainLayoutBinding?.root?.let { a ->
                                    ImageEffects.removeBlurEffect(a)
                                }
                                tenKeysLongPressed = false
                            }
                            setVibrate()
                            val finalX = event.rawX
                            val finalY = event.rawY

                            val distanceX = (finalX - firstXPoint)
                            val distanceY = (finalY - firstYPoint)

                            when(_currentInputMode.value){
                                is InputMode.ModeJapanese ->{
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        if (insertString.isNotBlank()){
                                            dakutenSmallLetter(sb)
                                        }else{
                                            _inputString.value = EMPTY_STRING
                                        }
                                    }

                                    return@setOnTouchListener false
                                }
                                is InputMode.ModeEnglish ->{
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        if (insertString.isNotBlank()){
                                            smallBigLetterConversionEnglish(sb)
                                        }else{
                                            _inputString.value = EMPTY_STRING
                                        }
                                    }

                                    return@setOnTouchListener false
                                }
                                is InputMode.ModeNumber ->{
                                    hidePopUpWindowActive()
                                    hidePopUpWindowCenter()
                                    hidePopUpWindowTop()
                                    hidePopUpWindowLeft()
                                    hidePopUpWindowBottom()
                                    hidePopUpWindowRight()
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        sendCharFlick(NUMBER_KEY10_SYMBOL_CHAR[0], insertString, sb)
                                        lastFlickConvertedNextHiragana = false
                                        it.setImageDrawable(drawableNumberSmall)
                                        it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(applicationContext,R.color.qwety_key_bg_color))

                                        return@setOnTouchListener false
                                    }
                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX){
                                            sendCharFlick(NUMBER_KEY10_SYMBOL_CHAR[3], insertString, sb)
                                        }else{
                                            sendCharFlick(NUMBER_KEY10_SYMBOL_CHAR[1], insertString, sb)
                                        }
                                    }else{
                                        if (firstYPoint < finalY) {
                                            /** empty body **/
                                        }else{
                                            sendCharFlick(NUMBER_KEY10_SYMBOL_CHAR[2], insertString, sb)
                                        }
                                    }
                                    lastFlickConvertedNextHiragana = false
                                    hidePopUpWindowActive()
                                    it.setImageDrawable(drawableNumberSmall)
                                    it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(applicationContext,R.color.qwety_key_bg_color))
                                    return@setOnTouchListener false
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE ->{
                            if (_currentInputMode.value == InputMode.ModeNumber){
                                val finalX = event.rawX
                                val finalY = event.rawY
                                val distanceX = (finalX - firstXPoint)
                                val distanceY = (finalY - firstYPoint)

                                when{
                                    /** Tap **/
                                    abs(distanceX) < 100 && abs(distanceY) < 100 ->{
                                        popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[0].toString()
                                        if (!tenKeysLongPressed){
                                            mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                                        }
                                        mPopupWindowActive.setPopUpWindowCenter(applicationContext,bubbleLayoutActive,it)
                                        return@setOnTouchListener false
                                    }
                                    /** Flick Right **/
                                    abs(distanceX) > abs(distanceY) && firstXPoint < finalX ->{
                                        popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[2].toString()
                                        popTextCenter.text = NUMBER_KEY10_SYMBOL_CHAR[0].toString()
                                        if (mPopupWindowLeft.isShowing){
                                            mPopupWindowActive.setPopUpWindowRight(applicationContext,bubbleLayoutActive,it)
                                            mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                                        }else{
                                            mPopupWindowActive.setPopUpWindowFlickRight(applicationContext,bubbleLayoutActive,it)
                                        }
                                    }
                                    /** Flick Left **/
                                    abs(distanceX) > abs(distanceY) && firstXPoint >= finalX ->{
                                        popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[1].toString()
                                        popTextCenter.text = NUMBER_KEY10_SYMBOL_CHAR[0].toString()
                                        if (mPopupWindowRight.isShowing){
                                            mPopupWindowActive.setPopUpWindowLeft(applicationContext,bubbleLayoutActive,it)
                                            mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                                        }else{
                                            mPopupWindowActive.setPopUpWindowFlickLeft(applicationContext,bubbleLayoutActive,it)
                                        }
                                    }
                                    /** Flick Bottom **/
                                    abs(distanceX) <= abs(distanceY) && firstYPoint < finalY ->{
                                        popTextActive.text = EMPTY_STRING
                                        popTextCenter.text = NUMBER_KEY10_SYMBOL_CHAR[0].toString()
                                        if (mPopupWindowTop.isShowing){
                                            mPopupWindowActive.setPopUpWindowBottom(applicationContext,bubbleLayoutActive,it)
                                            mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                                        }else{
                                            mPopupWindowActive.setPopUpWindowFlickBottom(applicationContext,bubbleLayoutActive,it)
                                        }
                                    }
                                    /** Flick Top **/
                                    abs(distanceX) <= abs(distanceY) && firstYPoint >= finalY ->{
                                        popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[3].toString()
                                        popTextCenter.text = NUMBER_KEY10_SYMBOL_CHAR[0].toString()
                                        if (mPopupWindowTop.isShowing){
                                            mPopupWindowActive.setPopUpWindowTop(applicationContext,bubbleLayoutActive,it)
                                            mPopupWindowCenter.setPopUpWindowCenter(applicationContext,bubbleLayoutCenter,it)
                                        }else{
                                            mPopupWindowActive.setPopUpWindowFlickTop(applicationContext,bubbleLayoutActive,it)
                                        }
                                    }
                                }
                                it.setImageDrawable(null)
                            }
                            it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(applicationContext,R.color.qwety_key_bg_color))
                            return@setOnTouchListener false
                        }
                        else -> return@setOnTouchListener true
                    }
                }
                it.setOnLongClickListener { v ->
                    tenKeysLongPressed = true
                    if (_currentInputMode.value == InputMode.ModeNumber){
                        popTextTop.text = NUMBER_KEY10_SYMBOL_CHAR[3].toString()
                        popTextLeft.text = NUMBER_KEY10_SYMBOL_CHAR[1].toString()
                        popTextBottom.text = EMPTY_STRING
                        popTextRight.text = NUMBER_KEY10_SYMBOL_CHAR[2].toString()
                        popTextCenter.text = NUMBER_KEY10_SYMBOL_CHAR[0].toString()

                        mPopupWindowTop.setPopUpWindowTop(applicationContext,bubbleLayoutTop,v)
                        mPopupWindowLeft.setPopUpWindowLeft(applicationContext,bubbleLayoutLeft,v)
                        mPopupWindowBottom.setPopUpWindowBottom(applicationContext,bubbleLayoutBottom,v)
                        mPopupWindowRight.setPopUpWindowRight(applicationContext,bubbleLayoutRight,v)

                        mainLayoutBinding?.root?.let { a ->
                            ImageEffects.applyBlurEffect(a,8f)
                        }
                    }
                    false
                }
            }
        }
    }

    /**
     * キーボードのキーのレイアウトをモードにより変更する
     * Change keyboard layout by input mode
     **/
    private fun setKeyLayoutByInputMode(
        keys: List<Any>,
        inputMode: InputMode,
        keySmallLetter: AppCompatImageButton
    ){
        when(inputMode){
            is InputMode.ModeJapanese ->{
                keys.forEachIndexed { index, button ->
                    if (button is AppCompatButton){
                        button.setTenKeyTextJapanese(button.id)
                    }
                    if (button is AppCompatImageButton){
                        when(index){
                            10 -> {
                                keySmallLetter.apply {
                                    setImageDrawable(drawableLanguage)
                                }
                            }
                        }
                    }
                }
            }
            is InputMode.ModeEnglish ->{

                keys.forEachIndexed { index, button ->
                    if (button is AppCompatButton){
                        button.setTenKeyTextEnglish(button.id)
                    }

                    if (button is AppCompatImageButton){
                        when(index){
                            10 -> {
                                keySmallLetter.apply {
                                    setImageDrawable(drawableLanguage)
                                }
                            }
                        }
                    }
                }
            }
            is InputMode.ModeNumber ->{
                keys.forEachIndexed { index, button ->
                    if (button is AppCompatButton){
                        button.setTenKeyTextNumber(button.id)
                    }

                    if (button is AppCompatImageButton){
                        when(index){
                            10 -> {
                                keySmallLetter.apply {
                                    setImageDrawable(drawableNumberSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun appendCharToStringBuilder(
        char: Char,
        insertString: String,
        stringBuilder: StringBuilder
    ){
        if (insertString.length == 1){
            stringBuilder.append(char)
            _inputString.value = stringBuilder.toString()
        } else {
            try {
                stringBuilder.append(insertString)
                    .deleteCharAt(insertString.lastIndex)
                    .append(char)
            }catch (e: Exception){
                if (e is CancellationException) throw e
            }
            _inputString.update {
                stringBuilder.toString()
            }
        }
    }
    
    private fun deleteStringCommon(){
        if (_inputString.value.length == 1){
            _inputString.update { EMPTY_STRING}
            currentInputConnection?.commitText("",1)
        }else{
            _inputString.update { it.dropLast(1)}
        }
    }

    private fun setCurrentInputCharacterContinuous(
        char: Char,
        insertString: String,
        sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        if (insertString.isNotEmpty()){
            sb.append(insertString).append(char)
            _inputString.update {
                sb.toString()
            }
        }else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun setCurrentInputCharacter(
        char: Char,
        inputForInsert: String,
        sb: StringBuilder
    ) {
        if (inputForInsert.isNotEmpty()) {
            val hiraganaAtInsertPosition = inputForInsert.last()
            val nextChar = hiraganaAtInsertPosition.getNextInputChar(char)

            if (nextChar == null) {
                _inputString.value = sb.append(inputForInsert).append(char).toString()
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
        charToSend: Char,
        insertString: String,
        sb: StringBuilder
    ){
        when(currentInputType){
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
            ->{
                sendKeyChar(charToSend)
            }
            else ->{
                if (isContinuousTapInputEnabled && lastFlickConvertedNextHiragana){
                    setCurrentInputCharacterContinuous(
                        charToSend,
                        insertString,
                        sb
                    )
                    lastFlickConvertedNextHiragana = false
                }else {
                    setKeyTouch(
                        charToSend,
                        insertString,
                        sb
                    )
                }
            }
        }
    }

    private fun sendCharFlick(
        charToSend: Char,
        insertString: String,
        sb: StringBuilder
    ){
        when(currentInputType){
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
            else ->{
                setCurrentInputCharacterContinuous(
                    charToSend,
                    insertString,
                    sb
                )
            }
        }
    }

    private fun setStringBuilderForConvertStringInHiragana(
        inputChar: Char,
        sb: StringBuilder
    ){
        if (_inputString.value.length == 1){
            sb.append(inputChar)
            _inputString.value = sb.toString()
        }else {
            sb.append(_inputString.value)
                .deleteAt(_inputString.value.length - 1)
                .append(inputChar)
            _inputString.value = sb.toString()
        }
    }

    private fun dakutenSmallLetter(
        sb: StringBuilder
    ){
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false
        if (_inputString.value.isNotEmpty()){
            val insertPosition = _inputString.value.last()
            insertPosition.let { c ->
                c.getDakutenSmallChar()?.let { dakutenChar ->
                    setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                }
            }
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder
    ){
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false

        if (_inputString.value.isNotEmpty()){
            val insertPosition = _inputString.value.last()
            insertPosition.let { c ->
                c.getDakutenSmallChar()?.let { dakutenChar ->
                    setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                }
            }

        }
    }

    private fun setKeyTouch(
        key: Char,
        insertString: String,
        sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = false
        onDeleteLongPressUp = false
        isContinuousTapInputEnabled = false
        if (isHenkan){
            currentInputConnection?.finishComposingText()
            _inputString.value = key.toString()
            isHenkan = false
        } else {
            setCurrentInputCharacter(
                key,
                insertString,
                sb
            )
        }
    }

    private fun setConvertLetterInJapaneseFromButton(
        suggestions: List<Candidate>,
    ){
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator(suggestionClickNum)
        when{
            listIterator.hasNext() ->{
                setSuggestionComposingText(listIterator)
            }
            !listIterator.hasNext() ->{
                setSuggestionComposingTextIteratorLast(suggestions)
                suggestionClickNum = 0
            }
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberNotEmpty(){
        currentInputConnection?.apply {
            if (stringInTail.isNotEmpty()){
                commitText(_inputString.value + " " + stringInTail, 1)
                stringInTail = EMPTY_STRING
            }else{
                commitText(_inputString.value + " ", 1)
            }
        }
        _inputString.value = EMPTY_STRING
    }

    private fun setSpaceKeyActionEnglishAndNumberEmpty(){
        if (stringInTail.isNotEmpty()){
            currentInputConnection?.apply {
                commitText(" $stringInTail", 1)
                stringInTail = EMPTY_STRING
            }
        }else{
            currentInputConnection?.apply {
                if (_currentInputMode.value == InputMode.ModeJapanese){
                    commitText("　", 1)
                }else{
                    commitText(" ", 1)
                }
            }
        }
        _inputString.value = EMPTY_STRING
    }

    private fun setSuggestionComposingText(listIterator: ListIterator<Candidate>){
        val nextSuggestion = listIterator.next()
        val spannableString2 = SpannableString(nextSuggestion.string + stringInTail)
        spannableString2.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.string.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        currentInputConnection?. setComposingText(spannableString2,1)
    }

    private fun setSuggestionComposingTextIteratorLast(suggestions: List<Candidate>){
        val nextSuggestion = suggestions[0]
        val spannableStringWithSuggestion = SpannableString(nextSuggestion.string + stringInTail)
        spannableStringWithSuggestion.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.string.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        currentInputConnection?.setComposingText(spannableStringWithSuggestion,1)
    }
    private fun setNextReturnInputCharacter(){
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false
        val insertForString = _inputString.value
        val sb = StringBuilder()
        if (insertForString.isNotEmpty() ){
            val insertPosition = _inputString.value.last()
            insertPosition.let { c ->
                c.getNextReturnInputChar()?.let { charForReturn ->
                    appendCharToStringBuilder(
                        charForReturn,
                        insertForString,
                        sb
                    )
                }
            }
        }
    }
    private fun setTouchActionInMoveEnd(appCompatButton: AppCompatButton){
        appCompatButton.apply {
            background = ContextCompat.getDrawable(applicationContext,R.drawable.ten_keys_center_bg)
            if (mPopupWindowTop.isShowing){
                setTextColor(ContextCompat.getColor(applicationContext,R.color.keyboard_icon_color))
            }else{
                setTextColor(ContextCompat.getColor(applicationContext,R.color.qwety_key_bg_color))
            }
        }
    }

    private fun hidePopUpWindowActive(){
        mPopupWindowActive.apply {
            if (isShowing){
                dismiss()
            }
        }
    }

    private fun hidePopUpWindowCenter(){
        mPopupWindowCenter.apply {
            if (isShowing){
                dismiss()
            }
        }
    }

    private fun hidePopUpWindowTop(){
        mPopupWindowTop.apply {
            if (isShowing){
                dismiss()
            }
        }
    }

    private fun hidePopUpWindowLeft(){
        mPopupWindowLeft.apply {
            if (isShowing){
                dismiss()
            }
        }
    }

    private fun hidePopUpWindowBottom(){
        mPopupWindowBottom.apply {
            if (isShowing){
                dismiss()
            }
        }
    }

    private fun hidePopUpWindowRight(){
        mPopupWindowRight.apply {
            if (isShowing){
                dismiss()
            }
        }
    }

}
