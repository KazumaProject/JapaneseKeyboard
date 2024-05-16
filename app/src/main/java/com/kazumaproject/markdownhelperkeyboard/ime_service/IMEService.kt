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
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.daasuu.bl.BubbleLayout
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.R
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
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowLeft
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowRight
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.PopUpWindowTop
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertUnicode
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getDakutenSmallChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextReturnInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.setPopUpWindowBottom
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
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_ACTIVITY
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST_ANIMALS_NATURE
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST_FOOD_DRINK
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST_SMILEYS_PEOPLE
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_OBJECT
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_TRAVEL
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.KAOMOJI
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.ModeInKigou
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.viterbi.FindPath
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
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var findPath: FindPath

    @Inject
    lateinit var graphBuilder: GraphBuilder

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
    private val _suggestionList = MutableStateFlow<List<String>>(emptyList())
    private val _suggestionFlag = MutableStateFlow(false)

    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private var currentTenKeyId = 0
    private var lastFlickConvertedNextHiragana = false
    private var isContinuousTapInputEnabled = false
    private var englishSpaceKeyPressed = false

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

        val EMOJI_LIST = EMOJI_LIST_SMILEYS_PEOPLE +
                EMOJI_LIST_ANIMALS_NATURE + EMOJI_LIST_FOOD_DRINK +
                EMOJI_ACTIVITY + EMOJI_TRAVEL + EMOJI_OBJECT

        const val DISPLAY_LEFT_STRING_TIME = 64L
        const val N_BEST = 32
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreateInputView(): View? {
        val ctx = ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
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
                initializeVariables()

                setTenKeyView(keyList)
                setSuggestionRecyclerView()
                setKigouView()
                startScope(keyList)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Timber.d("onStartInput: $restarting")
        currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
        resetAllFlags()
        setCurrentInputType(attribute)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("onFinishInput:")
        resetAllFlags()
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
            Timber.d("onUpdateCursorAnchorInfo: $composingText")
            if (currentInputType == InputTypeForIME.TextWebSearchView || currentInputType == InputTypeForIME.TextWebSearchViewFireFox){
                if (composingText == null) _inputString.update { EMPTY_STRING }
            }
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
            Configuration.ORIENTATION_SQUARE -> {
                /** empty body **/
            }
        }
    }

    private fun startScope(keyList: List<Any>) = scope.launch {
        mainLayoutBinding?.let { mainView ->
            launch {
                _suggestionFlag.asStateFlow().collectLatest {
                    setSuggestionOnView(mainView)
                }
            }

            launch {
                _suggestionList.asStateFlow().collectLatest { suggestions ->
                    suggestionAdapter?.suggestions = suggestions
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
                    when(it){
                        is KeyboardMode.ModeTenKeyboard ->{
                            mainView.keyboardView.root.isVisible = true
                            mainView.keyboardKigouView.root.isVisible = false
                        }
                        is KeyboardMode.ModeKigouView ->{
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
                InputTypeForIME.Time, -> {
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
            )
        }
    }

    private fun setSuggestionRecyclerView(){
        suggestionAdapter?.apply {
            this.setOnItemClickListener {
                setVibrate()
                setSuggestionAdapterClick(it)
                resetFlagsKeyEnter()
            }
        }
        val flexboxLayoutManager = FlexboxLayoutManager(this).apply {
            flexDirection = FlexDirection.COLUMN
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.STRETCH
        }
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                suggestionAdapter?.let { sugAdapter ->
                    adapter = sugAdapter
                    layoutManager = flexboxLayoutManager
                }
            }
        }
    }

    private fun setSuggestionAdapterClick(string: String){
        if (_inputString.value.isNotBlank()){
            CoroutineScope(ioDispatcher).launch {
                currentInputConnection?.commitText(string,1)
                _suggestionList.update { emptyList() }
                if (stringInTail.isNotEmpty()){
                    delay(DISPLAY_LEFT_STRING_TIME)
                    _inputString.update { stringInTail }
                    stringInTail = EMPTY_STRING
                }else{
                    _inputString.update { EMPTY_STRING }
                }
                _suggestionFlag.update { flag ->
                    !flag
                }
            }
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
                            layoutManager = GridLayoutManager(this@IMEService,6)
                        }
                    }
                    mainView.keyboardKigouView.kigouEmojiButton.isChecked = true
                }
                is ModeInKigou.Kaomoji ->{
                    kigouApdater?.let { a ->
                        mainView.keyboardKigouView.kigouRecyclerView.apply {
                            adapter = a
                            layoutManager = GridLayoutManager(this@IMEService,3)
                        }
                    }
                    mainView.keyboardKigouView.kigouKaomojiBtn.isChecked = true
                }
                is ModeInKigou.Null ->{
                    emojiKigouAdapter?.let { a ->
                        mainView.keyboardKigouView.kigouRecyclerView.apply {
                            adapter = a
                            layoutManager = GridLayoutManager(this@IMEService,6)
                        }
                    }
                    mainView.keyboardKigouView.kigouEmojiButton.isChecked = true
                }
            }
        }
    }

    private fun resetAllFlags(){
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
    private fun resetFlagsKeyEnter(){
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
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

    private suspend fun launchInputString() = withContext(imeIoDispatcher){
        _inputString.asStateFlow().collectLatest { inputString ->
            Timber.d("launchInputString: $inputString")
            if (inputString.isNotBlank()) {
                /** 入力された文字の selection と composing region を設定する **/
                val spannableString = SpannableString(inputString + stringInTail)
                setComposingTextPreEdit(inputString, spannableString)
                //delay(DELAY_TIME)
                if (isHenkan) return@collectLatest
                if (inputString.isEmpty()) return@collectLatest
                if (onDeleteLongPressUp) return@collectLatest
                if (englishSpaceKeyPressed) return@collectLatest
                if (deleteKeyLongKeyPressed) return@collectLatest
                isContinuousTapInputEnabled = true
                lastFlickConvertedNextHiragana = true
                setComposingTextAfterEdit(inputString, spannableString)
            } else {
                _suggestionList.update { emptyList() }
                setTenkeyIconsEmptyInputString()
                if (stringInTail.isNotEmpty()) currentInputConnection?.setComposingText(stringInTail,1)
            }
        }
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

    private fun setEnterKeyAction(listIterator: ListIterator<String>) = CoroutineScope(ioDispatcher).launch {
        _suggestionList.update { emptyList() }
        val nextSuggestion = listIterator.next()
        currentInputConnection?.commitText(nextSuggestion,1)
        if (stringInTail.isNotEmpty()){
            delay(DISPLAY_LEFT_STRING_TIME)
            _inputString.update { stringInTail }
            stringInTail = EMPTY_STRING
        }else{
            _inputString.update { EMPTY_STRING }
        }
        _suggestionFlag.update { flag -> !flag }
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
        _suggestionList.update { getSuggestionList() }
    }

    private suspend fun getSuggestionList() = CoroutineScope(ioDispatcher).async{
        val queryText = _inputString.value
        try {
            return@async kanaKanjiEngine.nBestPath(queryText, N_BEST)
        }catch (e: Exception){
            Timber.e(e.stackTraceToString())
        }
        return@async emptyList()
    }.await()

    private fun deleteLongPress() = CoroutineScope(ioDispatcher).launch {
        if (_inputString.value.isNotEmpty()){
            while (isActive){
                if (_inputString.value.length == 1){
                    currentInputConnection?.commitText("",0)
                    _inputString.update { EMPTY_STRING }
                }else{
                    _inputString.update { it.dropLast(1) }
                }
                if (_inputString.value.isEmpty()) {
                    if (stringInTail.isNotEmpty()) {
                        isContinuousTapInputEnabled = true
                        lastFlickConvertedNextHiragana = true
                        return@launch
                    }
                }
                delay(32)
                if (onDeleteLongPressUp) {
                    isContinuousTapInputEnabled = true
                    lastFlickConvertedNextHiragana = true
                    return@launch
                }
            }
        }else {
            while (isActive){
                if (stringInTail.isNotEmpty()) return@launch
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                delay(32)
                if (onDeleteLongPressUp) {
                    isContinuousTapInputEnabled = true
                    lastFlickConvertedNextHiragana = true
                    return@launch
                }
            }
        }
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
            InputTypeForIME.Time, -> {
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
                    CoroutineScope(ioDispatcher).launch {
                        onDeleteLongPressUp = true
                        delay(200)
                        onDeleteLongPressUp = false
                        deleteKeyLongKeyPressed = false
                        _suggestionFlag.update {
                            !it
                        }
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
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL))
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
            if (_inputString.value.isNotEmpty()){
                when(_currentInputMode.value){
                    InputMode.ModeJapanese ->{
                        isHenkan = true
                        setVibrate()
                        setConvertLetterInJapaneseFromButton(_suggestionList.value)
                        suggestionClickNum += 1
                    }
                    else ->{
                        setVibrate()
                        setSpaceKeyActionEnglishAndNumberNotEmpty()
                    }
                }
            }else {
                setVibrate()
                setSpaceKeyActionEnglishAndNumberEmpty()
            }
            resetFlagsKeySpace()
        }
        setOnLongClickListener {
            if (_suggestionList.value.isNotEmpty() && _inputString.value.isNotEmpty()){
                when(_currentInputMode.value){
                    InputMode.ModeJapanese ->{
                        isHenkan = true
                        setVibrate()
                        setConvertLetterInJapaneseFromButton(_suggestionList.value)
                        suggestionClickNum += 1
                    }
                    else ->{
                        setVibrate()
                        currentInputConnection?.apply {
                            commitText(
                                _inputString.value + " ",
                                1
                            )
                        }
                        _inputString.value = EMPTY_STRING
                    }
                }
            }else {
                setVibrate()
                currentInputConnection?.apply {
                    commitText(" ", 1)
                }
            }
            resetFlagsKeySpace()
            true
        }
    }

    private fun setEnterKey(imageButton: AppCompatImageButton) = imageButton.apply {
        setOnClickListener {
            if (_inputString.value.isNotEmpty()){
                when(_currentInputMode.value){
                    InputMode.ModeJapanese ->{
                        setVibrate()
                        if (isHenkan){
                            setVibrate()
                            if (suggestionClickNum > _suggestionList.value.size) suggestionClickNum = 0
                            val listIterator = if (suggestionClickNum > 0) _suggestionList.value.listIterator(suggestionClickNum - 1) else {
                                _suggestionList.value.listIterator(suggestionClickNum)
                            }
                            setEnterKeyAction(listIterator)
                        }else {
                            currentInputConnection?.finishComposingText()
                            _inputString.value = EMPTY_STRING
                        }
                        resetFlagsKeyEnter()
                    }
                    else ->{
                        setVibrate()
                        currentInputConnection?.finishComposingText()
                        _inputString.value = EMPTY_STRING
                        resetFlagsKeyEnter()
                    }
                }
            }else{
                setVibrate()
                if (stringInTail.isNotEmpty()){
                    currentInputConnection?.finishComposingText()
                    stringInTail = EMPTY_STRING
                    return@setOnClickListener
                }
                setEnterKeyPress()
                isHenkan = false
                suggestionClickNum = 0
            }
        }
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
            resetFlagsKeyEnter()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDeleteKeyKigou(
        imageButton: AppCompatImageButton?,
    ) = imageButton?.apply {

        setOnTouchListener { _, event ->
            when(event.action and MotionEvent.ACTION_MASK){
                MotionEvent.ACTION_UP ->{
                    CoroutineScope(ioDispatcher).launch {
                        onDeleteLongPressUp = true
                        delay(200)
                        onDeleteLongPressUp = false
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
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL))
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
            resetFlagsKeyEnter()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setLeftKey(
        leftKey: AppCompatImageButton,
    ){
        leftKey.apply {
            setOnTouchListener { _, event ->
                when(event.action and MotionEvent.ACTION_MASK){
                    MotionEvent.ACTION_UP ->{
                        CoroutineScope(ioDispatcher).launch {
                            onLeftKeyLongPressUp = true
                            delay(100)
                            onLeftKeyLongPressUp = false
                            if (_inputString.value.isNotBlank())
                                _suggestionFlag.update {
                                    !it
                                }
                            else
                                _suggestionList.value = emptyList()
                        }
                    }
                }
                return@setOnTouchListener false
            }

            setOnClickListener {
                setVibrate()
                if (_inputString.value.isEmpty() && stringInTail.isEmpty()){
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                }else{
                    if (!isHenkan){
                        lastFlickConvertedNextHiragana = true
                        isContinuousTapInputEnabled = true
                        englishSpaceKeyPressed = false
                        suggestionClickNum = 0
                        if (_inputString.value.isEmpty()) return@setOnClickListener
                        stringInTail = StringBuilder(stringInTail)
                            .insert(0,_inputString.value.last())
                            .toString()
                        _inputString.update { it.dropLast(1) }
                    }
                }
            }

            setOnLongClickListener {
                setVibrate()
                if (!isHenkan){
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    onLeftKeyLongPressUp = false
                    suggestionClickNum = 0
                    if (_inputString.value.isEmpty()){
                        CoroutineScope(ioDispatcher).launch {
                            while (isActive){
                                currentInputConnection?.apply {
                                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT))
                                }
                                delay(36)
                                if (onLeftKeyLongPressUp) {
                                    return@launch
                                }
                            }
                        }
                    }else {
                        CoroutineScope(ioDispatcher).launch {
                            while (isActive){
                                if (_inputString.value.isNotEmpty()){
                                    stringInTail = StringBuilder(stringInTail)
                                        .insert(0,_inputString.value.last())
                                        .toString()
                                    _inputString.update { it.dropLast(1) }
                                }
                                delay(36)
                                if (onLeftKeyLongPressUp) {
                                    return@launch
                                }
                            }
                        }
                    }
                }
                true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setRightKey(
        rightKey: AppCompatImageButton,
    ){
        rightKey.apply {
            setOnTouchListener { _, event ->
                when(event.action and MotionEvent.ACTION_MASK){
                    MotionEvent.ACTION_UP ->{
                        CoroutineScope(ioDispatcher).launch {
                            onRightKeyLongPressUp = true
                            delay(100)
                            onRightKeyLongPressUp = false
                            if (_inputString.value.isNotBlank()) {
                                _suggestionFlag.update {
                                    !it
                                }
                            }
                        }
                    }
                }
                return@setOnTouchListener false
            }

            setOnClickListener {
                setVibrate()
                actionInRightKeyPressed()
            }

            setOnLongClickListener {
                setVibrate()
                if (!isHenkan){
                    onRightKeyLongPressUp = false
                    suggestionClickNum = 0
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    CoroutineScope(ioDispatcher).launch {
                        while (isActive){
                            actionInRightKeyPressed()
                            delay(36)
                            if (onRightKeyLongPressUp) return@launch
                        }
                    }
                }
                true
            }
        }
    }

    private fun actionInRightKeyPressed(){
        if (_inputString.value.isEmpty()){
            if (stringInTail.isEmpty()){
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
            }else{
                val dropString = stringInTail.first()
                stringInTail = stringInTail.drop(1)
                _inputString.update { dropString.toString() }
            }
        }else{
            if (!isHenkan){
                englishSpaceKeyPressed = false
                lastFlickConvertedNextHiragana = true
                isContinuousTapInputEnabled = true
                suggestionClickNum = 0
                if (stringInTail.isNotEmpty()){
                    val dropString = stringInTail.first()
                    stringInTail = stringInTail.drop(1)
                    _inputString.update { it + dropString }
                }
            }
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
                            
                            setVibrate()
                            val finalX = event.rawX
                            val finalY = event.rawY

                            val distanceX = (finalX - firstXPoint)
                            val distanceY = (finalY - firstYPoint)
                            hidePopUpWindowActive()
                            hidePopUpWindowTop()
                            hidePopUpWindowLeft()
                            hidePopUpWindowBottom()
                            hidePopUpWindowRight()
                            if (currentTenKeyId !in tenKeyMap.keysJapanese) {
                                return@setOnTouchListener false
                            }
                            when(_currentInputMode.value){
                                is InputMode.ModeJapanese ->{
                                    val keyInfoJapanese = tenKeyMap.getTenKeyInfoJapanese(currentTenKeyId)
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        if (keyInfoJapanese is TenKeyInfo.TenKeyTapFlickInfo){
                                            sendCharTap(keyInfoJapanese.tap, insertString, sb)
                                        }
                                        it.setTenKeyTextJapanese(currentTenKeyId)
                                        _suggestionFlag.update { flag ->
                                            !flag
                                        }
                                        it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                        it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
                                        currentTenKeyId = 0
                                        return@setOnTouchListener false
                                    }

                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            if (keyInfoJapanese is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoJapanese.flickRight, insertString, sb)
                                            }
                                        }else {
                                            if (keyInfoJapanese is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoJapanese.flickLeft, insertString, sb)
                                            }
                                        }
                                    }else {
                                        if (firstYPoint < finalY) {
                                            if (keyInfoJapanese is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoJapanese.flickBottom, insertString, sb)
                                            }
                                        }else{
                                            if (keyInfoJapanese is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoJapanese.flickTop, insertString, sb)
                                            }
                                        }
                                    }
                                    lastFlickConvertedNextHiragana = true
                                    isContinuousTapInputEnabled = true
                                    it.setTenKeyTextJapanese(currentTenKeyId)
                                    _suggestionFlag.update { flag ->
                                        !flag
                                    }
                                    currentTenKeyId = 0
                                    it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
                                    return@setOnTouchListener false

                                }
                                is InputMode.ModeEnglish ->{
                                    val keyInfoEnglish = tenKeyMap.getTenKeyInfoEnglish(currentTenKeyId)
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        if (keyInfoEnglish is TenKeyInfo.TenKeyTapFlickInfo){
                                            sendCharTap(keyInfoEnglish.tap, insertString, sb)
                                        }
                                        _suggestionFlag.update { flag ->
                                            !flag
                                        }
                                        it.setTenKeyTextEnglish(currentTenKeyId)
                                        it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                        it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
                                        currentTenKeyId = 0
                                        return@setOnTouchListener false
                                    }

                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            if (keyInfoEnglish is TenKeyInfo.TenKeyTapFlickInfo){
                                                if (keyInfoEnglish.flickRight == ' ') {
                                                    it.setTenKeyTextEnglish(currentTenKeyId)
                                                    currentTenKeyId = 0
                                                    return@setOnTouchListener false
                                                }
                                                sendCharFlick(keyInfoEnglish.flickRight, insertString, sb)
                                            }
                                        }else {
                                            if (keyInfoEnglish is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoEnglish.flickLeft, insertString, sb)
                                            }
                                        }
                                    }else{
                                        if (firstYPoint < finalY) {
                                            if (keyInfoEnglish is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoEnglish.flickBottom, insertString, sb)
                                            }
                                        }else{
                                            if (keyInfoEnglish is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoEnglish.flickTop, insertString, sb)
                                            }
                                        }
                                    }
                                    lastFlickConvertedNextHiragana = true
                                    isContinuousTapInputEnabled = true
                                    it.setTenKeyTextEnglish(currentTenKeyId)
                                    _suggestionFlag.update { flag ->
                                        !flag
                                    }
                                    currentTenKeyId = 0
                                    it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
                                    return@setOnTouchListener false

                                }
                                is InputMode.ModeNumber ->{
                                    val keyInfoNumber = tenKeyMap.getTenKeyInfoNumber(currentTenKeyId)
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        if (keyInfoNumber is TenKeyInfo.TenKeyTapFlickInfo){
                                            sendCharFlick(keyInfoNumber.tap, insertString, sb)
                                        }
                                        it.setTenKeyTextNumber(currentTenKeyId)
                                        _suggestionFlag.update { flag ->
                                            !flag
                                        }
                                        currentTenKeyId = 0
                                        it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                        it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
                                        return@setOnTouchListener false
                                    }

                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            if (keyInfoNumber is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoNumber.flickRight, insertString, sb)
                                            }
                                        }else {
                                            if (keyInfoNumber is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoNumber.flickLeft, insertString, sb)
                                            }
                                        }
                                    }else {
                                        if (firstYPoint < finalY) {
                                            /** empty body **/
                                        }else{
                                            if (keyInfoNumber is TenKeyInfo.TenKeyTapFlickInfo){
                                                sendCharFlick(keyInfoNumber.flickTop, insertString, sb)
                                            }
                                        }
                                    }
                                    lastFlickConvertedNextHiragana = true
                                    isContinuousTapInputEnabled = true
                                    it.setTenKeyTextNumber(currentTenKeyId)
                                    _suggestionFlag.update { flag ->
                                        !flag
                                    }
                                    currentTenKeyId = 0
                                    it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
                                    return@setOnTouchListener false
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE ->{

                            val finalX = event.rawX
                            val finalY = event.rawY
                            val distanceX = (finalX - firstXPoint)
                            val distanceY = (finalY - firstYPoint)

                            when(_currentInputMode.value){
                                is InputMode.ModeJapanese ->{
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        it.setTextColor(ContextCompat.getColor(this,R.color.white))
                                        it.background = ContextCompat.getDrawable(this,R.drawable.ten_key_active_bg)
                                        it.setTenKeyTextWhenTapJapanese(currentTenKeyId)
                                        return@setOnTouchListener false
                                    }
                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            popTextActive.setTextFlickRightJapanese(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowRight(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }else{
                                            popTextActive.setTextFlickLeftJapanese(currentTenKeyId)
                                            if (mPopupWindowRight.isShowing){
                                                mPopupWindowActive.setPopUpWindowLeft(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }
                                    }else {
                                        if (firstYPoint < finalY){
                                            popTextActive.setTextFlickBottomJapanese(currentTenKeyId)
                                            if (mPopupWindowTop.isShowing){
                                                mPopupWindowActive.setPopUpWindowBottom(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }else{
                                            popTextActive.setTextFlickTopJapanese(currentTenKeyId)
                                            if (mPopupWindowBottom.isShowing){
                                                mPopupWindowActive.setPopUpWindowTop(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }
                                    }
                                    setTouchActionInMoveEnd(it)
                                    return@setOnTouchListener false
                                }
                                is InputMode.ModeEnglish ->{
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        it.setTextColor(ContextCompat.getColor(this,R.color.white))
                                        it.background = ContextCompat.getDrawable(this,R.drawable.ten_key_active_bg)
                                        it.setTenKeyTextWhenTapEnglish(currentTenKeyId)
                                        return@setOnTouchListener false
                                    }

                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            popTextActive.setTextFlickRightEnglish(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowRight(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }else{
                                            popTextActive.setTextFlickLeftEnglish(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowLeft(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }
                                    }else {
                                        if (firstYPoint < finalY){
                                            popTextActive.setTextFlickBottomEnglish(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowBottom(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }else{
                                            popTextActive.setTextFlickTopEnglish(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowTop(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }
                                    }
                                    setTouchActionInMoveEnd(it)
                                    return@setOnTouchListener false
                                }
                                is InputMode.ModeNumber ->{
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        it.setTextColor(ContextCompat.getColor(this,R.color.white))
                                        it.setTenKeyTextWhenTapNumber(currentTenKeyId)
                                        it.background = ContextCompat.getDrawable(this,R.drawable.ten_key_active_bg)
                                        return@setOnTouchListener false
                                    }

                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            popTextActive.setTextFlickRightNumber(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowRight(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }else{
                                            popTextActive.setTextFlickLeftNumber(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowLeft(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }
                                    }else {
                                        if (firstYPoint < finalY){
                                            popTextActive.setTextFlickBottomNumber(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowBottom(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }else{
                                            popTextActive.setTextFlickTopNumber(currentTenKeyId)
                                            if (mPopupWindowLeft.isShowing){
                                                mPopupWindowActive.setPopUpWindowTop(this@IMEService,bubbleLayoutActive,it)
                                            }else{
                                                mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                            }
                                        }
                                    }
                                    setTouchActionInMoveEnd(it)
                                    return@setOnTouchListener false
                                }
                            }
                        }
                        else -> return@setOnTouchListener false
                    }
                }
                it.setOnLongClickListener { v ->
                    Timber.d("long click detect")
                    hidePopUpWindowActive()
                    hidePopUpWindowTop()
                    hidePopUpWindowLeft()
                    hidePopUpWindowBottom()
                    hidePopUpWindowRight()

                    when(_currentInputMode.value){
                        is InputMode.ModeJapanese ->{
                            popTextTop.setTextFlickTopJapanese(currentTenKeyId)
                            popTextLeft.setTextFlickLeftJapanese(currentTenKeyId)
                            popTextBottom.setTextFlickBottomJapanese(currentTenKeyId)
                            popTextRight.setTextFlickRightJapanese(currentTenKeyId)
                        }
                        is InputMode.ModeEnglish ->{
                            popTextTop.setTextFlickTopEnglish(currentTenKeyId)
                            popTextLeft.setTextFlickLeftEnglish(currentTenKeyId)
                            popTextBottom.setTextFlickBottomEnglish(currentTenKeyId)
                            popTextRight.setTextFlickRightEnglish(currentTenKeyId)
                        }
                        is InputMode.ModeNumber ->{
                            popTextTop.setTextFlickTopNumber(currentTenKeyId)
                            popTextLeft.setTextFlickLeftNumber(currentTenKeyId)
                            popTextBottom.setTextFlickBottomNumber(currentTenKeyId)
                            popTextRight.setTextFlickRightNumber(currentTenKeyId)
                        }
                    }

                    mPopupWindowTop.setPopUpWindowTop(this@IMEService,bubbleLayoutTop,v)
                    mPopupWindowLeft.setPopUpWindowLeft(this@IMEService,bubbleLayoutLeft,v)
                    mPopupWindowBottom.setPopUpWindowBottom(this@IMEService,bubbleLayoutBottom,v)
                    mPopupWindowRight.setPopUpWindowRight(this@IMEService,bubbleLayoutRight,v)
                    false
                }
            }
            if (it is AppCompatImageButton){
                it.setOnTouchListener { v, event ->
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
                                            inputMethodManager.showInputMethodPicker()
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
                                            inputMethodManager.showInputMethodPicker()
                                        }
                                    }
                                    return@setOnTouchListener false
                                }
                                is InputMode.ModeNumber ->{
                                    hidePopUpWindowActive()
                                    hidePopUpWindowTop()
                                    hidePopUpWindowLeft()
                                    hidePopUpWindowBottom()
                                    hidePopUpWindowRight()
                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        hidePopUpWindowActive()
                                        sendCharFlick(NUMBER_KEY10_SYMBOL_CHAR[0], insertString, sb)
                                        lastFlickConvertedNextHiragana = false
                                        _suggestionFlag.update { flag ->
                                            !flag
                                        }
                                        it.setImageDrawable(drawableNumberSmall)
                                        it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this,R.color.qwety_key_bg_color))
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
                                    _suggestionFlag.update { flag ->
                                        !flag
                                    }
                                    hidePopUpWindowActive()
                                    it.setImageDrawable(drawableNumberSmall)
                                    it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this,R.color.qwety_key_bg_color))
                                    return@setOnTouchListener false
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE ->{
                            when(_currentInputMode.value){
                                is InputMode.ModeJapanese ->{

                                }
                                is InputMode.ModeEnglish ->{

                                }
                                is InputMode.ModeNumber ->{
                                    val finalX = event.rawX
                                    val finalY = event.rawY
                                    val distanceX = (finalX - firstXPoint)
                                    val distanceY = (finalY - firstYPoint)

                                    if (abs(distanceX) < 100 && abs(distanceY) < 100){
                                        when(v.id){
                                            R.id.key_small_letter -> {
                                                popTextActive.text = EMPTY_STRING
                                                it.setImageDrawable(drawableOpenBracket)
                                                it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this,R.color.popup_bg_active))
                                            }
                                        }
                                        hidePopUpWindowActive()
                                        return@setOnTouchListener false
                                    }

                                    if (abs(distanceX) > abs(distanceY)) {
                                        if (firstXPoint < finalX) {
                                            when(v.id){
                                                R.id.key_small_letter -> {
                                                    popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[3].toString()
                                                }
                                            }
                                            mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                        } else {
                                            when(v.id){
                                                R.id.key_small_letter -> {
                                                    popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[1].toString()
                                                }
                                            }
                                            mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    } else {
                                        if (firstYPoint < finalY) {
                                            when(v.id){
                                                R.id.key_small_letter -> {
                                                    popTextActive.text = EMPTY_STRING
                                                }
                                            }
                                            mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                        } else {
                                            when(v.id){
                                                R.id.key_small_letter -> {
                                                    popTextActive.text = NUMBER_KEY10_SYMBOL_CHAR[2].toString()
                                                }
                                            }
                                            mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }
                                    it.setImageDrawable(null)
                                }
                            }
                            it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this,R.color.qwety_key_bg_color))
                            return@setOnTouchListener false
                        }
                        else -> return@setOnTouchListener true
                    }
                }
                it.setOnLongClickListener { v ->
                    Timber.d("long click detect")

                    when(_currentInputMode.value){
                        is InputMode.ModeJapanese ->{
                        }
                        is InputMode.ModeEnglish ->{
                        }
                        is InputMode.ModeNumber ->{
                            hidePopUpWindowActive()
                            hidePopUpWindowTop()
                            hidePopUpWindowLeft()
                            hidePopUpWindowBottom()
                            hidePopUpWindowRight()

                            popTextTop.text = NUMBER_KEY10_SYMBOL_CHAR[3].toString()
                            popTextLeft.text = NUMBER_KEY10_SYMBOL_CHAR[1].toString()
                            popTextBottom.text = EMPTY_STRING
                            popTextRight.text = NUMBER_KEY10_SYMBOL_CHAR[2].toString()

                            mPopupWindowTop.setPopUpWindowTop(this@IMEService,bubbleLayoutTop,v)
                            mPopupWindowLeft.setPopUpWindowLeft(this@IMEService,bubbleLayoutLeft,v)
                            mPopupWindowBottom.setPopUpWindowBottom(this@IMEService,bubbleLayoutBottom,v)
                            mPopupWindowRight.setPopUpWindowRight(this@IMEService,bubbleLayoutRight,v)
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
        if (inputForInsert.isNotEmpty()){
            try {
                val hiraganaAtInsertPosition = inputForInsert.last()
                hiraganaAtInsertPosition.let { c ->
                    if (c.getNextInputChar(char) == null){
                        _inputString.value = sb.append(inputForInsert).append(char).toString()
                    }else {
                        appendCharToStringBuilder(
                            c.getNextInputChar(char)!!,
                            inputForInsert,
                            sb
                        )
                    }

                }
            }catch (e: Exception){
                if (e is CancellationException) throw e
            }
        }else{
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
            InputTypeForIME.Time, ->{
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
        _suggestionFlag.update {
            !it
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
        _suggestionFlag.update {
            !it
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
        suggestions: List<String>,
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
                commitText(" ", 1)
            }
        }
        _inputString.value = EMPTY_STRING
    }

    private fun setSuggestionComposingText(listIterator: ListIterator<String>){
        val nextSuggestion = listIterator.next()
        val spannableString2 = SpannableString(nextSuggestion + stringInTail)
        spannableString2.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        currentInputConnection?. setComposingText(spannableString2,1)
    }

    private fun setSuggestionComposingTextIteratorLast(suggestions: List<String>){
        val nextSuggestion = suggestions[0]
        val spannableStringWithSuggestion = SpannableString(nextSuggestion + stringInTail)
        spannableStringWithSuggestion.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        _suggestionFlag.update {
            !it
        }
    }
    private fun setTouchActionInMoveEnd(appCompatButton: AppCompatButton){
        appCompatButton.apply {
            background = ContextCompat.getDrawable(this@IMEService,R.drawable.ten_keys_center_bg)
            if (mPopupWindowTop.isShowing){
                setTextColor(ContextCompat.getColor(this@IMEService,R.color.keyboard_icon_color))
            }else{
                setTextColor(ContextCompat.getColor(this@IMEService,R.color.qwety_key_bg_color))
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
