package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.*
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.*
import android.view.inputmethod.*
import android.view.textservice.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.daasuu.bl.BubbleLayout
import com.google.android.flexbox.*
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.EmojiKigouAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.KigouAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.InputModeSwitch
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.TenKeyInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.TenKeyMapHolder
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.ComposingTextTrackingInputConnection
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_ACTIVITY
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST_ANIMALS_NATURE
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST_FOOD_DRINK
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_LIST_SMILEYS_PEOPLE
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_OBJECT
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.EMOJI_TRAVEL
import com.kazumaproject.markdownhelperkeyboard.ime_service.other.Constants.KAOMOJI
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import jp.co.omronsoft.openwnn.ComposingText
import jp.co.omronsoft.openwnn.JAJP.OpenWnnEngineJAJP
import jp.co.omronsoft.openwnn.StrSegment
import jp.co.omronsoft.openwnn.WnnWord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
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
    @InputBackGroundDispatcher
    lateinit var imeIoDispatcher: CoroutineDispatcher
    @Inject
    lateinit var openWnnEngineJAJP: OpenWnnEngineJAJP
    @Inject
    lateinit var appPreference: AppPreference
    @Inject
    lateinit var composingText: ComposingText
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

    private var mainLayoutBinding: MainLayoutBinding? = null
    private var composingTextTrackingInputConnection: ComposingTextTrackingInputConnection? = null

    private var suggestionAdapter: SuggestionAdapter?= null
    private var emojiKigouAdapter: EmojiKigouAdapter?= null
    private var kigouApdater: KigouAdapter?= null

    private val _currentInputMode = MutableStateFlow<InputMode>(InputMode.ModeJapanese)
    private val _inputString = MutableStateFlow(EMPTY_STRING)
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
    private var isSelectionPositionAtNotEnd = false

    private var _mComposingTextPosition = -1
    private var _selectionEndtPosition = -1

    private var firstXPoint = 0.0f
    private var firstYPoint = 0.0f

    private var hasRequestCursorUpdatesCalled = false
    private var startSelPosInSuggestion = 0
    private var suggestionClickNum = 0
    private var isHenkan = false
    private var selectionReqPosAfterDelete : Int = -1

    private var onLeftKeyLongPressUp = false
    private var onRightKeyLongPressUp = false

    private var onDeleteLongPressUp = false
    private var deleteKeyPressed = false
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
        const val PREDICT_MIN_LENGTH = 1
        const val PREDICT_MAX_LENGTH = 16

        val EMOJI_LIST = EMOJI_LIST_SMILEYS_PEOPLE +
                EMOJI_LIST_ANIMALS_NATURE + EMOJI_LIST_FOOD_DRINK +
                EMOJI_ACTIVITY + EMOJI_TRAVEL + EMOJI_OBJECT

        const val DELAY_TIME = 1024L
        const val SUGGESTION_LIST_SHOW_TIME = 64L
        const val DISPLAY_LEFT_STRING_TIME = 128L
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
        composingTextTrackingInputConnection = ComposingTextTrackingInputConnection.newInstance(currentInputConnection)
        composingTextTrackingInputConnection?.apply {
            requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
            resetComposingText()
        }
        resetAllFlags()
        setCurrentInputType(attribute)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when(newConfig.orientation){
            Configuration.ORIENTATION_PORTRAIT ->{
                composingTextTrackingInputConnection?.finishComposingText()
            }
            Configuration.ORIENTATION_LANDSCAPE ->{
                composingTextTrackingInputConnection?.finishComposingText()
            }
            Configuration.ORIENTATION_UNDEFINED ->{
                composingTextTrackingInputConnection?.finishComposingText()
            }
            Configuration.ORIENTATION_SQUARE -> {
                /** empty body **/
            }
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetAllFlags()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        resetAllFlags()
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        cursorAnchorInfo?.let { info ->
            hasRequestCursorUpdatesCalled = true
            _selectionEndtPosition = info.selectionEnd
            _mComposingTextPosition = info.composingTextStart

            Timber.d("onUpdateCursorAnchorInfo: $info" +
                    "\n${_inputString.value}")

            isSelectionPositionAtNotEnd = _inputString.value.isNotEmpty() &&
                    !info.composingText.isNullOrBlank() &&
                    info.composingText.length != info.selectionEnd - info.composingTextStart

            if (info.selectionStart == info.selectionEnd  && _inputString.value.isNotEmpty() &&   (
                        info.composingTextStart > info.selectionStart
                        )){
                Timber.d("onUpdateCursorAnchorInfo: selection called 1")
                val text = info.composingText
                text?.let { _ ->
                    composingTextTrackingInputConnection?.setSelection(info.composingTextStart,info.composingTextStart)
                }
            }

            if (info.selectionStart == info.selectionEnd  && _inputString.value.isNotEmpty()  && (
                        info.composingTextStart + _inputString.value.length < info.selectionStart
                        )){
                Timber.d("onUpdateCursorAnchorInfo: selection called 2")
                val text = info.composingText
                text?.let { c ->
                    composingTextTrackingInputConnection?.setSelection(info.composingTextStart + c.length,info.composingTextStart + c.length)
                }
            }


            if (info.selectionStart == 0 && info.selectionEnd == 0) {
                onLeftKeyLongPressUp = true
            }
            val text = composingTextTrackingInputConnection?.getExtractedText(ExtractedTextRequest(),0)
            if (text?.text?.length == info.selectionStart && text.text.length == info.selectionEnd){
                onRightKeyLongPressUp = true
            }

            if (_inputString.value.isEmpty() &&
                info.composingTextStart != -1 &&
                info.composingText.isNotEmpty() &&
                shouldResetConvertStringInHiragana
            ){
                Timber.d("onUpdateCursorAnchorInfo: selection called 3")
                _inputString.value = info.composingText.toString()
            }

            if (_inputString.value.isEmpty() &&
                info.composingTextStart != -1 &&
                info.composingText.isNotEmpty() &&
                shouldResetConvertStringInHiraganaLong
            ){
                Timber.d("onUpdateCursorAnchorInfo: selection called drop")
                _inputString.value = info.composingText.toString().dropLast(1)
            }

            if (info.composingText == null && _inputString.value.isNotEmpty()){
                if (currentInputType == InputTypeForIME.TextWebSearchView || currentInputType == InputTypeForIME.TextWebSearchViewFireFox) {
                    _inputString.update { EMPTY_STRING }
                    _suggestionList.update { emptyList() }
                    Timber.d("onUpdateCursorAnchorInfo: text is null")
                }
            }

        }
    }

    override fun onDestroy(){
        super.onDestroy()
        actionInDestroy()
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
                    setSuggestionRecyclerViewVisibility(suggestions.isEmpty())
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
                    if (_currentKeyboardMode.value != KeyboardMode.ModeKigouView) return@collectLatest
                    setTenKeyAndKigouView(it)
                }
            }

            launchInputString()
        }
    }

    private fun setCurrentInputType(attribute: EditorInfo?){
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME(inputType)
            Timber.d("Input type now: $inputType " + "\n$currentInputType")
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
                    composingTextTrackingInputConnection?.requestCursorUpdates(0)
                    hasRequestCursorUpdatesCalled = false
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
                if (hasRequestCursorUpdatesCalled){
                    if (_inputString.value.isNotBlank()){
                        val startPosition = _selectionEndtPosition
                        val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                        val length = _inputString.value.length
                        val composingTextEndPosition = startPosition - composingTextStartPos
                        if (length != composingTextEndPosition && isSelectionPositionAtNotEnd){
                            if (!isHenkan){
                                CoroutineScope(mainDispatcher).launch {
                                    composingTextTrackingInputConnection?.commitText(it,1)
                                    val text = try {
                                        _inputString.value.substring(
                                            startPosition - composingTextStartPos, length
                                        )
                                    }catch (e: Exception){
                                        if (e is CancellationException) throw e
                                        EMPTY_STRING
                                    }
                                    resetSuggestionAdapterFlags()
                                    delay(240L)
                                    _inputString.value = text
                                    _suggestionFlag.update { flag ->
                                        !flag
                                    }
                                }
                            }
                        }else {
                            composingTextTrackingInputConnection?.commitText(it,1)
                            _inputString.value = EMPTY_STRING
                            resetSuggestionAdapterFlags()
                        }
                    }
                }else{
                    if (!isHenkan){
                        composingTextTrackingInputConnection?.commitText(it,1)
                        _inputString.value = EMPTY_STRING
                        resetSuggestionAdapterFlags()
                    }else{
                        composingTextTrackingInputConnection?.commitText(it,1)
                        _inputString.value = EMPTY_STRING
                        resetSuggestionAdapterFlags()
                    }
                }
            }
        }
        val flexboxLayoutManager = FlexboxLayoutManager(this@IMEService).apply {
            flexDirection = FlexDirection.COLUMN
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.STRETCH
        }
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                suggestionAdapter?.let { sugAdapter ->
                    this.adapter = sugAdapter
                    this.layoutManager = flexboxLayoutManager
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
                    composingTextTrackingInputConnection?.commitText(emoji.unicode.convertUnicode(),1)
                }
            }
            kigouApdater?.let { a ->
                a.kigouList = KAOMOJI
                a.setOnItemClickListener { s ->
                    setVibrate()
                    composingTextTrackingInputConnection?.commitText(s,1)
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
        hasRequestCursorUpdatesCalled = false
        suggestionClickNum = 0
        isHenkan = false
        isContinuousTapInputEnabled = false
        deleteKeyPressed = false
        deleteKeyLongKeyPressed = false
        _dakutenPressed.value = false
        _selectionEndtPosition = -1
        _mComposingTextPosition = -1
        englishSpaceKeyPressed = false
        insertCharNotContinue = false
        lastFlickConvertedNextHiragana = false
        onDeleteLongPressUp = false
    }

    private fun actionInDestroy(){
        suggestionAdapter = null
        emojiKigouAdapter = null
        kigouApdater = null
        mainLayoutBinding = null
        composingTextTrackingInputConnection?.closeConnection()
        openWnnEngineJAJP.close()
        scope.coroutineContext.cancelChildren()
    }

    private fun resetSuggestionAdapterFlags(){
        lastFlickConvertedNextHiragana = false
        isContinuousTapInputEnabled = false
        deleteKeyPressed = false
        _dakutenPressed.value = false
        insertCharNotContinue = false
        onDeleteLongPressUp = false
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
    }

    private fun resetFlagsKeyEnter(){
        isHenkan = false
        suggestionClickNum = 0
        deleteKeyPressed = false
        insertCharNotContinue = false
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = false
        isContinuousTapInputEnabled = false
    }

    private fun resetFlagsKeySpace(){
        onDeleteLongPressUp = false
        insertCharNotContinue = false
        _dakutenPressed.value = false
        deleteKeyPressed = false
        isContinuousTapInputEnabled = false
        lastFlickConvertedNextHiragana = false
        englishSpaceKeyPressed = false
    }

    private fun resetFlagsDeleteKey(){
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        insertCharNotContinue = false
        onDeleteLongPressUp = false
        isHenkan = false
    }

    private suspend fun launchInputString() = withContext(imeIoDispatcher){
        _inputString.asStateFlow().collectLatest { inputString ->
            if (inputString.isNotBlank()) {
                when(currentInputType){
                    InputTypeForIME.TextWebSearchView -> if (deleteKeyPressed && !_dakutenPressed.value) return@collectLatest
                    InputTypeForIME.TextWebSearchViewFireFox -> if (deleteKeyPressed && !_dakutenPressed.value) return@collectLatest
                    else ->{ /** empty body **/ }
                }

                /** 入力された文字の selection と composing region を設定する **/
                setComposingTextPreEdit(inputString)
                delay(DELAY_TIME)
                if (isHenkan) return@collectLatest
                if (inputString.isEmpty()) return@collectLatest
                if (onDeleteLongPressUp) return@collectLatest
                if (englishSpaceKeyPressed) return@collectLatest
                if (deleteKeyLongKeyPressed) return@collectLatest

                isContinuousTapInputEnabled = true
                lastFlickConvertedNextHiragana = true
                setComposingTextAfterEdit(inputString)

            } else {
                if (!hasRequestCursorUpdatesCalled) composingTextTrackingInputConnection?.resetComposingText()
                _suggestionList.update { emptyList() }
                setTenkeyIconsEmptyInputString()
            }
        }
    }

    private suspend fun setComposingTextPreEdit( inputString: String ){
        val spannableString = SpannableString(inputString)
        spannableString.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.green)),0,inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val selPosition = if (_selectionEndtPosition < 0) 0 else _selectionEndtPosition
        when{
            deleteKeyPressed &&
                    hasRequestCursorUpdatesCalled ->{
                Timber.d("selection position delete: $selectionReqPosAfterDelete")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selectionReqPosAfterDelete ,selectionReqPosAfterDelete)
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            _dakutenPressed.value &&
                    hasRequestCursorUpdatesCalled ->{
                Timber.d("selection position dakuten: $selPosition")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition ,selPosition )
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            onDeleteLongPressUp &&
                    hasRequestCursorUpdatesCalled ->{
                Timber.d("selection position delete long: $selPosition")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        setSelection(selPosition ,selPosition )
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            !hasRequestCursorUpdatesCalled &&
                    onDeleteLongPressUp ->{
                composingTextTrackingInputConnection?.apply {

                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                    }finally {
                        endBatchEdit()
                        composingTextInsertPosition = inputString.length
                    }
                }

            }
            insertCharNotContinue &&
                    inputString.length != (_selectionEndtPosition - _mComposingTextPosition) &&
                    hasRequestCursorUpdatesCalled &&
                    !isContinuousTapInputEnabled && !lastFlickConvertedNextHiragana
            ->{
                Timber.d("selection position con: $selPosition $inputString $isSelectionPositionAtNotEnd")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition ,selPosition )
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            !hasRequestCursorUpdatesCalled && !deleteKeyPressed ->{
                composingTextTrackingInputConnection?.apply {
                    val position = composingTextInsertPosition + 1
                    val extractedText = getExtractedText(
                        ExtractedTextRequest(),0
                    )
                    extractedText?.apply {

                        Timber.d("selection position not request cursor: \n$inputString" +
                                "\n$position" +
                                "\n$composingText" +
                                "\n${_dakutenPressed.value}" +
                                "\n$startOffset" )
                        beginBatchEdit()
                        try {
                            setComposingText(spannableString,1)
                            delay(32)
                            val startIndex = (startOffset + selectionStart) + 1
                            when{
                                _dakutenPressed.value -> setSelection(startIndex - 1, startIndex - 1)
                                insertCharNotContinue -> setSelection(startIndex - 1, startIndex - 1)
                                else -> setSelection(startIndex, startIndex)
                            }
                        }finally {
                            endBatchEdit()
                            composingTextInsertPosition = when {
                                _dakutenPressed.value -> position - 1
                                insertCharNotContinue -> position - 1
                                else -> position
                            }
                        }

                    }

                }

            }

            !hasRequestCursorUpdatesCalled && deleteKeyPressed ->{
                composingTextTrackingInputConnection?.apply {
                    val position = composingTextInsertPosition - 1
                    val extractedText = getExtractedText(
                        ExtractedTextRequest(),0
                    )
                    extractedText?.text?.let { t ->
                        Timber.d("selection position not request cursor delete request: $inputString" +
                                "\n$position" +
                                "\n$composingText")
                        val composingTextStartPositionNoCursor = t.indexOf(composingText)
                        val selectionPositionNoCursor = position + composingTextStartPositionNoCursor
                        beginBatchEdit()
                        try {
                            setComposingText(spannableString,1)
                            setSelection(selectionPositionNoCursor, selectionPositionNoCursor)
                        }finally {
                            endBatchEdit()
                            composingTextInsertPosition = position
                        }
                    }
                }

            }

            else ->{
                Timber.d("selection position else: $selPosition $inputString $isSelectionPositionAtNotEnd $lastFlickConvertedNextHiragana $isContinuousTapInputEnabled")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition + 1 ,selPosition + 1)
                    }finally {
                        endBatchEdit()
                    }
                }
            }
        }
    }

    private suspend fun setComposingTextAfterEdit(
        inputString: String
    ){
        val spannableString = SpannableString(inputString)
        val selPosition = if (_selectionEndtPosition < 0) 0 else _selectionEndtPosition

        spannableString.apply {
            setSpan(BackgroundColorSpan(getColor(R.color.blue)),0,inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(),0,inputString.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        when{
            deleteKeyPressed &&
                    hasRequestCursorUpdatesCalled ->{
                Timber.d("selection position delete: $selectionReqPosAfterDelete")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition,selPosition)
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            _dakutenPressed.value &&
                    hasRequestCursorUpdatesCalled ->{
                Timber.d("selection position dakuten: $selPosition")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition,selPosition)
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            onDeleteLongPressUp &&
                    hasRequestCursorUpdatesCalled ->{
                Timber.d("selection position delete long: $selPosition $inputString")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        setSelection(selPosition ,selPosition )
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            !hasRequestCursorUpdatesCalled &&
                    onDeleteLongPressUp ->{
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                    }finally {
                        endBatchEdit()
                        composingTextInsertPosition = inputString.length
                    }
                }

            }
            insertCharNotContinue &&
                    inputString.length != (_selectionEndtPosition - _mComposingTextPosition) &&
                    hasRequestCursorUpdatesCalled &&
                    !isContinuousTapInputEnabled && !lastFlickConvertedNextHiragana
            ->{
                Timber.d("selection position con:$inputString $isSelectionPositionAtNotEnd")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition ,selPosition )
                    }finally {
                        endBatchEdit()
                    }
                }
            }

            !hasRequestCursorUpdatesCalled && !deleteKeyPressed ->{
                composingTextTrackingInputConnection?.apply {
                    val position = composingTextInsertPosition
                    val extractedText = getExtractedText(
                        ExtractedTextRequest(),0
                    )
                    extractedText?.apply {

                        Timber.d("selection position not request cursor: \n$inputString" +
                                "\n$position" +
                                "\n$composingText" +
                                "\n${_dakutenPressed.value}" +
                                "\n$startOffset" )
                        beginBatchEdit()
                        try {
                            setComposingText(spannableString,1)
                            delay(32)
                            val startIndex = (startOffset + selectionStart)
                            when{
                                _dakutenPressed.value -> setSelection(startIndex, startIndex)
                                insertCharNotContinue -> setSelection(startIndex, startIndex)
                                else -> setSelection(startIndex, startIndex)
                            }
                        }finally {
                            endBatchEdit()
                            composingTextInsertPosition = when {
                                _dakutenPressed.value -> startOffset + selectionStart
                                insertCharNotContinue -> startOffset + selectionStart
                                else -> startOffset + selectionStart
                            }

                        }

                    }

                }

            }

            !hasRequestCursorUpdatesCalled && deleteKeyPressed ->{
                composingTextTrackingInputConnection?.apply {
                    val position = composingTextInsertPosition
                    val extractedText = getExtractedText(
                        ExtractedTextRequest(),0
                    )
                    extractedText?.text?.let { t ->
                        Timber.d("selection position not request cursor delete request: $inputString" +
                                "\n$position" +
                                "\n$composingText")
                        val composingTextStartPositionNoCursor = t.indexOf(composingText)
                        val selectionPositionNoCursor = position + composingTextStartPositionNoCursor
                        beginBatchEdit()
                        try {
                            setComposingText(spannableString,1)
                            setSelection(selectionPositionNoCursor, selectionPositionNoCursor)
                        }finally {
                            endBatchEdit()
                            composingTextInsertPosition = position
                        }
                    }
                }

            }

            else ->{
                Timber.d("selection position else 2: $selPosition $inputString $isSelectionPositionAtNotEnd $lastFlickConvertedNextHiragana $isContinuousTapInputEnabled")
                composingTextTrackingInputConnection?.apply {
                    beginBatchEdit()
                    try {
                        setComposingText(spannableString,1)
                        if (isSelectionPositionAtNotEnd) setSelection(selPosition,selPosition)
                    }finally {
                        endBatchEdit()
                    }
                }
            }
        }
    }

    private fun setEnterKeyAction(listIterator: ListIterator<String>) = CoroutineScope(mainDispatcher).launch {
        when{
            // First
            !listIterator.hasPrevious() && listIterator.hasNext() ->{
                val startPosition = startSelPosInSuggestion
                val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                val length = _inputString.value.length
                val nextSuggestion = listIterator.next()
                composingTextTrackingInputConnection?.commitText(nextSuggestion,1)
                val text = try {
                    _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )
                }catch (e: Exception){
                    if (e is CancellationException) throw e
                    EMPTY_STRING
                }
                lastFlickConvertedNextHiragana = false
                isContinuousTapInputEnabled = false
                deleteKeyPressed = false
                delay(DISPLAY_LEFT_STRING_TIME)
                _inputString.value = text
                _suggestionFlag.update {  flag ->
                    !flag
                }
            }
            // Middle
            listIterator.hasPrevious() && listIterator.hasNext() ->{
                val startPosition = startSelPosInSuggestion
                val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                val length = _inputString.value.length
                val nextSuggestion = listIterator.next()
                composingTextTrackingInputConnection?.commitText(nextSuggestion,1)
                val text = try {
                    _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )
                }catch (e: Exception){
                    if (e is CancellationException) throw e
                    EMPTY_STRING
                }
                lastFlickConvertedNextHiragana = false
                isContinuousTapInputEnabled = false
                deleteKeyPressed = false
                delay(DISPLAY_LEFT_STRING_TIME)
                _inputString.value = text
                _suggestionFlag.update {  flag ->
                    !flag
                }
            }
            // End
            listIterator.hasPrevious() && !listIterator.hasNext() ->{
                val startPosition = startSelPosInSuggestion
                val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                val length = _inputString.value.length
                val nextSuggestion = listIterator.next()
                composingTextTrackingInputConnection?.commitText(nextSuggestion,1)
                val text = try {
                    _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )
                }catch (e: Exception){
                    if (e is CancellationException) throw e
                    EMPTY_STRING
                }
                lastFlickConvertedNextHiragana = false
                isContinuousTapInputEnabled = false
                deleteKeyPressed = false
                delay(DISPLAY_LEFT_STRING_TIME)
                _inputString.value = text
                _suggestionFlag.update {  flag ->
                    !flag
                }
            }
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

    private suspend fun setSuggestionRecyclerViewVisibility(flag: Boolean){
        mainLayoutBinding?.let { mainView ->
            if (flag){
                mainView.suggestionRecyclerView.visibility = View.INVISIBLE
                delay(100L)
                mainView.suggestionRecyclerView.isVisible = false
            }else{
                mainView.suggestionRecyclerView.isVisible = true
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
            _inputString.value.isNotBlank()
                    && hasRequestCursorUpdatesCalled -> {

                if (suggestionClickNum != 0) return

                setSuggestionForJapanese(mainView)

            }
            _inputString.value.isNotBlank()
                    && !hasRequestCursorUpdatesCalled -> {
                if (suggestionClickNum == 0) {

                    val startPos = 0
                    val endPos = _inputString.value.length
                    val queryTextTmp = _inputString.value.substring(
                        startPos, endPos
                    )

                    Timber.d(
                        "SuggestionClicked: \n" +
                                "start: $startPos\n" +
                                "string: $queryTextTmp"
                    )

                    setSuggestionForJapanese(mainView)

                }
            }

        }
    }

    private suspend fun setSuggestionForJapanese(mainView: MainLayoutBinding) {
        updateSuggestionUI(mainView)
        _suggestionList.value = getSuggestionList()
    }

    private suspend fun getSuggestionList() = CoroutineScope(mainDispatcher).async{
        delay(SUGGESTION_LIST_SHOW_TIME)
        val suggestions = mutableListOf<String>()
        val startPos = 0
        val endPos = if (hasRequestCursorUpdatesCalled){
            if (_selectionEndtPosition - _mComposingTextPosition > _inputString.value.length)
                _inputString.value.length
            else if (_selectionEndtPosition - _mComposingTextPosition < 0)
                0
            else  _selectionEndtPosition - _mComposingTextPosition
        } else {
            if (composingTextTrackingInputConnection == null) 0 else composingTextTrackingInputConnection!!.composingTextInsertPosition
        }

        try {

            val queryText = _inputString.value.substring(
                startPos, endPos
            )

            composingText.apply {
                clear()
                insertStrSegment(
                    ComposingText.LAYER0,
                    ComposingText.LAYER1,
                    StrSegment(queryText)
                )

                if (queryText.length == 1){
                    openWnnEngineJAJP.convert(composingText)
                    for (i in 0 until composingText.size(ComposingText.LAYER2)) {
                        if (0 < openWnnEngineJAJP.makeCandidateListOf(i)) {
                            var word: WnnWord?
                            while (openWnnEngineJAJP.nextCandidate.also { word = it } != null) {
                                suggestions.add(word?.candidate ?: EMPTY_STRING)
                            }
                        }
                    }
                    return@async suggestions.toList()
                }else {
                    openWnnEngineJAJP.predict(composingText, PREDICT_MIN_LENGTH, PREDICT_MAX_LENGTH)
                    var word: WnnWord?
                    while (openWnnEngineJAJP.nextCandidate.also { word = it } != null) {
                        suggestions.add(word?.candidate ?: EMPTY_STRING)
                    }
                }
                return@async suggestions.toList()
            }
        }catch (e: Exception){
            if (e is CancellationException) throw e
        }
        return@async emptyList()
    }.await()

    private fun deleteLongPress() = CoroutineScope(mainDispatcher).launch {
        if (_inputString.value.isNotEmpty()){
            if (hasRequestCursorUpdatesCalled){
                val startPos = if (_selectionEndtPosition - _mComposingTextPosition < 0) 0 else  _selectionEndtPosition - _mComposingTextPosition
                val deleteBefore: String? = try {
                    StringBuilder(_inputString.value)
                        .substring(startPos,
                            _inputString.value.length
                        )
                }catch (e: Exception){
                    null
                }
                while (isActive){
                    composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                    delay(32)
                    if (onDeleteLongPressUp) {
                        isContinuousTapInputEnabled = true
                        lastFlickConvertedNextHiragana = true
                        deleteKeyPressed = false
                        if (_inputString.value.isNotEmpty()){
                            val text = composingTextTrackingInputConnection?.getExtractedText(ExtractedTextRequest(),0)
                            text?.text?.let { t ->
                                if (t.length > _inputString.value.length ){
                                    Timber.d("delete key long press 1: $t \n" +
                                            "${_inputString.value} \n" +
                                            "$_mComposingTextPosition \n" +
                                            "$_selectionEndtPosition")
                                    try {
                                        if (deleteBefore != null ){
                                            _inputString.value = StringBuilder(
                                                t.substring(_mComposingTextPosition, _selectionEndtPosition )
                                            ).append(deleteBefore).
                                            toString()
                                        }else{
                                            _inputString.value = StringBuilder(
                                                t.substring(_mComposingTextPosition, _selectionEndtPosition )
                                            ).toString()
                                        }


                                    }catch (e: Exception){
                                        _inputString.value = EMPTY_STRING
                                        if (e is CancellationException) throw e else {
                                            /*** empty ***/
                                        }
                                    }
                                }else{
                                    if (_inputString.value.length > t.length && _mComposingTextPosition != -1){

                                        Timber.d("delete key long press 2: $t \n" +
                                                "${_inputString.value} \n" +
                                                "$_mComposingTextPosition \n" +
                                                "$_selectionEndtPosition")
                                        if (_mComposingTextPosition == 0 && _selectionEndtPosition == 0){
                                            if (deleteBefore != null){
                                                _inputString.value = deleteBefore
                                            }else{
                                                _inputString.value = EMPTY_STRING
                                            }
                                        }else{
                                            try {
                                                if (deleteBefore != null){
                                                    _inputString.value = StringBuilder(
                                                        t.substring(_mComposingTextPosition, _selectionEndtPosition )
                                                    ).append(deleteBefore).
                                                    toString()
                                                }else{
                                                    _inputString.value = StringBuilder(
                                                        t.substring(_mComposingTextPosition, _selectionEndtPosition )
                                                    ).toString()
                                                }
                                            }catch (e: Exception){
                                                _inputString.value = EMPTY_STRING
                                                if (e is CancellationException) {
                                                    Timber.e("delete key long press : ${e.printStackTrace()}")
                                                    throw e
                                                } else {
                                                    /*** empty ***/
                                                }
                                            }
                                        }
                                    }else{
                                        when{
                                            t.length == _inputString.value.length ->{

                                                Timber.d("delete key long press 3: $t \n${_inputString.value} \n${_mComposingTextPosition} \n${_selectionEndtPosition}")
                                                try {
                                                    if (deleteBefore != null){
                                                        _inputString.value = StringBuilder(
                                                            t.substring(_mComposingTextPosition, _selectionEndtPosition )
                                                        ).append(deleteBefore).
                                                        toString()
                                                    }else{
                                                        _inputString.value = StringBuilder(
                                                            t.substring(_mComposingTextPosition, _selectionEndtPosition )
                                                        ).toString()
                                                    }
                                                }catch (e: Exception){
                                                    _inputString.value = EMPTY_STRING
                                                    if (e is CancellationException) throw e else {
                                                        /** Empty **/
                                                    }
                                                }
                                            }

                                            t.length > _inputString.value.length ->{

                                                Timber.d("delete key long press 4: $t \n${_inputString.value} \n${_mComposingTextPosition} \n${_selectionEndtPosition}")
                                                try {
                                                    _inputString.value = t.substring(_mComposingTextPosition,_selectionEndtPosition)
                                                }catch (e: Exception){
                                                    _inputString.value = EMPTY_STRING
                                                    if (e is CancellationException) throw e else{
                                                        /** Empty **/
                                                    }
                                                }
                                            }

                                            _mComposingTextPosition == -1 ->{
                                                _inputString.value = EMPTY_STRING
                                            }

                                            else ->{
                                                _inputString.value = EMPTY_STRING
                                            }
                                        }

                                    }
                                }
                            }
                            if (text == null){
                                _inputString.value = EMPTY_STRING
                            }
                        }

                        return@launch
                    }
                }
            }else {
                composingTextTrackingInputConnection?.apply {
                    while (isActive){
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                        moveLeftComposingTextPosition()
                        delay(32L)
                        if (onDeleteLongPressUp){
                            deleteSurroundingText(composingTextInsertPosition,0)
                            delay(16L)
                            isContinuousTapInputEnabled = true
                            lastFlickConvertedNextHiragana = true
                            deleteKeyPressed = false
                            _inputString.update {
                                composingText.substring(0,composingTextInsertPosition)
                            }
                            return@launch
                        }
                    }
                }
            }

        }else {
            while (isActive){
                composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                delay(32)
                if (onDeleteLongPressUp) {
                    deleteKeyPressed = false
                    isContinuousTapInputEnabled = false
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
                composingTextTrackingInputConnection?.commitText("\n",1)
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
                composingTextTrackingInputConnection?.apply {
                    Timber.d("Enter key: called 3\n" )
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
            }

            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time, -> {
                composingTextTrackingInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.TextSearchView ->{
                composingTextTrackingInputConnection?.apply {
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
                    CoroutineScope(mainDispatcher).launch {
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
                _selectionEndtPosition - _mComposingTextPosition > 0 &&
                        hasRequestCursorUpdatesCalled && _inputString.value.isNotEmpty()  ->{
                    deleteStringInEditText()
                    deleteKeyPressed = true
                    resetFlagsDeleteKey()
                }
                !hasRequestCursorUpdatesCalled && _inputString.value.isNotEmpty() ->{
                    deleteStringInEditTextRequestCursorUpdatesNotCalled()
                    deleteKeyPressed = true
                    resetFlagsDeleteKey()
                }
                else ->{
                    composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                }
            }
        }

        setOnLongClickListener {
            onDeleteLongPressUp = false
            deleteLongPress()
            _dakutenPressed.value = false
            englishSpaceKeyPressed = false
            insertCharNotContinue = false
            deleteKeyLongKeyPressed = true
            deleteKeyPressed = true
            return@setOnLongClickListener true
        }
    }

    private fun setSpaceKey(imageButton: AppCompatImageButton) = imageButton.apply {
        setOnClickListener {
            if (_suggestionList.value.isNotEmpty() && _inputString.value.isNotEmpty()){
                when(_currentInputMode.value){
                    InputMode.ModeJapanese ->{
                        isHenkan = true
                        setVibrate()
                        setConvertLetterInJapaneseFromButton(_suggestionList.value)
                        suggestionClickNum += 1
                    }
                    else ->{
                        if (_inputString.value.isEmpty()) return@setOnClickListener
                        setVibrate()
                        composingTextTrackingInputConnection?.apply {
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
                composingTextTrackingInputConnection?.apply {
                    commitText(
                        _inputString.value + " ",
                        1
                    )
                }
                _inputString.value = EMPTY_STRING
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
                        if (_inputString.value.isEmpty()) return@setOnLongClickListener true
                        setVibrate()
                        composingTextTrackingInputConnection?.apply {
                            commitText(
                                _inputString.value + " ",
                                1
                            )
                        }
                        _inputString.value = EMPTY_STRING
                    }
                }
            }else {
                if (_inputString.value.isEmpty()) return@setOnLongClickListener true
                setVibrate()
                composingTextTrackingInputConnection?.apply {
                    commitText(
                        _inputString.value + " ",
                        1
                    )
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
                            composingTextTrackingInputConnection?.finishComposingText()
                            _inputString.value = EMPTY_STRING
                        }
                        resetFlagsKeyEnter()
                    }
                    else ->{
                        setVibrate()
                        composingTextTrackingInputConnection?.finishComposingText()
                        _inputString.value = EMPTY_STRING
                        resetFlagsKeyEnter()
                    }
                }
            }else{
                setVibrate()
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
                composingTextTrackingInputConnection?.apply {
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
                    CoroutineScope(mainDispatcher).launch {
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
            when{
                _selectionEndtPosition - _mComposingTextPosition > 0 &&
                        hasRequestCursorUpdatesCalled && _inputString.value.isNotEmpty()  ->{
                    deleteStringInEditText()
                    deleteKeyPressed = true
                    resetFlagsDeleteKey()
                }
                !hasRequestCursorUpdatesCalled && _inputString.value.isNotEmpty() ->{
                    deleteStringInEditTextRequestCursorUpdatesNotCalled()
                    deleteKeyPressed = true
                    resetFlagsDeleteKey()
                }
                else ->{
                    composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                }
            }
        }

        setOnLongClickListener {
            onDeleteLongPressUp = false
            deleteLongPress()
            deleteKeyPressed = true
            deleteKeyLongKeyPressed = true
            _dakutenPressed.value = false
            englishSpaceKeyPressed = false
            insertCharNotContinue = false
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
                        CoroutineScope(mainDispatcher).launch {
                            onLeftKeyLongPressUp = true
                            delay(100)
                            onLeftKeyLongPressUp = false
                            if (hasRequestCursorUpdatesCalled){
                                if (_inputString.value.isNotBlank() && (_selectionEndtPosition - _mComposingTextPosition) > 0)
                                    _suggestionFlag.update {
                                        !it
                                    }
                                else
                                    _suggestionList.value = emptyList()
                            }else{
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
                if (!hasRequestCursorUpdatesCalled) {
                    composingTextTrackingInputConnection?.apply{
                        moveLeftComposingTextPosition()
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                        lastFlickConvertedNextHiragana = true
                        isContinuousTapInputEnabled = true
                    }
                    return@setOnClickListener
                }
                setVibrate()
                if (!isHenkan){
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    englishSpaceKeyPressed = false
                    suggestionClickNum = 0
                    composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                }
            }

            setOnLongClickListener {
                setVibrate()
                if (!hasRequestCursorUpdatesCalled) return@setOnLongClickListener true
                if (!isHenkan){
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    onLeftKeyLongPressUp = false
                    suggestionClickNum = 0
                    if (_inputString.value.isEmpty()){
                        composingTextTrackingInputConnection?.let { inputConnection ->
                            val text = inputConnection.getExtractedText(ExtractedTextRequest(),0)
                            if (text.text.isNotEmpty()) {
                                CoroutineScope(mainDispatcher).launch {
                                    while (isActive){
                                        composingTextTrackingInputConnection?.apply {
                                            if (_inputString.value.isNotEmpty()){
                                                if (_inputString.value.length != 1){
                                                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                                                }
                                            }else{
                                                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                                            }
                                        }
                                        delay(40)
                                        if (onLeftKeyLongPressUp) {

                                            return@launch
                                        }
                                    }
                                }
                            }
                        }
                    }else {
                        CoroutineScope(mainDispatcher).launch {
                            while (isActive){
                                composingTextTrackingInputConnection?.apply {
                                    if (_inputString.value.isNotEmpty()){
                                        if (_inputString.value.length != 1){
                                            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                                        }
                                    }else{
                                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT))
                                    }
                                }
                                delay(40)
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
                        CoroutineScope(mainDispatcher).launch {
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
                if (!hasRequestCursorUpdatesCalled) {
                    composingTextTrackingInputConnection?.apply {
                        moveRightComposingTextPosition()
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
                        lastFlickConvertedNextHiragana = true
                        isContinuousTapInputEnabled = true
                    }
                    return@setOnClickListener
                }
                if (!isHenkan){
                    englishSpaceKeyPressed = false
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    suggestionClickNum = 0
                    composingTextTrackingInputConnection?.apply {
                        if (_inputString.value.isNotBlank()){
                            val text = getExtractedText(ExtractedTextRequest(),0)
                            val beforeCursorText = getTextBeforeCursor(text.text.length,0)
                            val beforeCursorTextLength = beforeCursorText?.length ?: 0
                            val afterCursorText = getTextAfterCursor(text.text.length,0)
                            if (text.text.length != beforeCursorTextLength){
                                afterCursorText?.let { _ ->
                                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
                                }
                            }
                        }else{
                            val text = getExtractedText(ExtractedTextRequest(),0)
                            text?.let { t ->
                                val beforeText = getTextBeforeCursor(t.text.length,0)
                                beforeText?.let { c ->
                                    if (t.text.length != c.length) sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
                                }
                            }
                        }

                    }
                }
            }

            setOnLongClickListener {
                setVibrate()
                if (!hasRequestCursorUpdatesCalled) return@setOnLongClickListener true
                if (!isHenkan){
                    onRightKeyLongPressUp = false
                    suggestionClickNum = 0
                    lastFlickConvertedNextHiragana = true
                    isContinuousTapInputEnabled = true
                    CoroutineScope(mainDispatcher).launch {
                        while (isActive){
                            composingTextTrackingInputConnection?.apply {
                                if (_inputString.value.isNotBlank()){
                                    val text = getExtractedText(ExtractedTextRequest(),0)
                                    val beforeCursorText = getTextBeforeCursor(text.text.length,0)
                                    val beforeCursorTextLength = beforeCursorText?.length ?: 0
                                    val afterCursorText = getTextAfterCursor(text.text.length,0)
                                    if (text.text.length != beforeCursorTextLength){
                                        afterCursorText?.let { _ ->
                                            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
                                        }
                                    }
                                }else{
                                    val text = getExtractedText(ExtractedTextRequest(),0)
                                    text?.let { t ->
                                        val beforeText = getTextBeforeCursor(t.text.length,0)
                                        beforeText?.let { c ->
                                            if (t.text.length != c.length) sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
                                        }
                                    }
                                }

                            }
                            delay(40)
                            if (onRightKeyLongPressUp) {

                                return@launch
                            }
                        }
                    }
                }
                true
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
                                            mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                        }else{
                                            popTextActive.setTextFlickLeftJapanese(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }else {
                                        if (firstYPoint < finalY){
                                            popTextActive.setTextFlickBottomJapanese(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                        }else{
                                            popTextActive.setTextFlickTopJapanese(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }
                                    it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
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
                                            mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                        }else{
                                            popTextActive.setTextFlickLeftEnglish(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }else {
                                        if (firstYPoint < finalY){
                                            popTextActive.setTextFlickBottomEnglish(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                        }else{
                                            popTextActive.setTextFlickTopEnglish(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }
                                    it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
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
                                            mPopupWindowActive.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutActive,it)
                                        }else{
                                            popTextActive.setTextFlickLeftNumber(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }else {
                                        if (firstYPoint < finalY){
                                            popTextActive.setTextFlickBottomNumber(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutActive,it)
                                        }else{
                                            popTextActive.setTextFlickTopNumber(currentTenKeyId)
                                            mPopupWindowActive.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutActive,it)
                                        }
                                    }
                                    it.background = ContextCompat.getDrawable(this,R.drawable.ten_keys_center_bg)
                                    it.setTextColor(ContextCompat.getColor(this,R.color.keyboard_icon_color))
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

                    mPopupWindowTop.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutTop,v)
                    mPopupWindowLeft.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutLeft,v)
                    mPopupWindowBottom.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutBottom,v)
                    mPopupWindowRight.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutRight,v)
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
                                            deleteKeyPressed = false
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
                                            deleteKeyPressed = false
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

                            mPopupWindowTop.setPopUpWindowFlickTop(this@IMEService,bubbleLayoutTop,v)
                            mPopupWindowLeft.setPopUpWindowFlickLeft(this@IMEService,bubbleLayoutLeft,v)
                            mPopupWindowBottom.setPopUpWindowFlickBottom(this@IMEService,bubbleLayoutBottom,v)
                            mPopupWindowRight.setPopUpWindowFlickRight(this@IMEService,bubbleLayoutRight,v)
                        }
                    }
                    false
                }
            }
        }
    }

    /**
     *
     * キーボードのキーのレイアウトをモードにより変更する
     *
     * Change keyboard layout by input mode
     *
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

    private var shouldResetConvertStringInHiragana = false
    private var shouldResetConvertStringInHiraganaLong = false

    private fun deleteStringInEditText() {

        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true

        when(currentInputType){
            InputTypeForIME.TextWebSearchView ->{
                deleteStringInWebSearchView()
            }
            InputTypeForIME.TextWebSearchViewFireFox ->{
                deleteStringInWebSearchViewFireFox()
            }
            else ->{
                deleteStringCommon()
            }
        }

    }
    private fun deleteStringInEditTextRequestCursorUpdatesNotCalled(){
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        try {
            composingTextTrackingInputConnection?.apply {
                if (_inputString.value.isNotEmpty()){
                    if (_inputString.value.length == 1){
                        _inputString.value = EMPTY_STRING
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                    }else{
                        val position = when{
                            composingTextInsertPosition - 1 < 0 -> 0
                            composingText.length <= (composingTextInsertPosition - 1) -> composingText.length - 1
                            else -> composingTextInsertPosition - 1
                        }
                        val sb = StringBuilder()
                        sb.append(composingText).deleteAt(position)
                        _inputString.update {
                            sb.toString()
                        }
                    }
                }else{
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                }
            }
        }catch (e: Exception){
            Timber.e(e.stackTraceToString())
            if (e is CancellationException) throw e
        }
    }

    private var insertCharNotContinue = false

    private fun appendCharToStringBuilder(
        char: Char,
        selPosition: Int,
        insertString: String,
        stringBuilder: StringBuilder
    ){
        if (insertString.length == 1){
            stringBuilder.append(char)
            _inputString.value = stringBuilder.toString()
            Timber.d("key input key input request con 2:  ${_inputString.value}")
        } else {
            try {
                stringBuilder.append(insertString)
                    .deleteAt(selPosition)
                    .insert(selPosition, char)
            }catch (e: Exception){
                if (e is CancellationException) throw e
            }
            _inputString.update {
                stringBuilder.toString()
            }
            Timber.d("key input key input request con 3:  ${_inputString.value} $selPosition")
        }
    }

    private fun deleteStringInWebSearchView() = CoroutineScope(mainDispatcher).launch {
        val composingTextStartingPosition = _selectionEndtPosition - _mComposingTextPosition

        val deletePos = when{
            composingTextStartingPosition - 1 < 0 -> 0
            isHenkan -> _inputString.value.length - 1
            else -> composingTextStartingPosition - 1
        }

        selectionReqPosAfterDelete = if (isHenkan){
            _inputString.value.length - 1
        }else {
            if ((_selectionEndtPosition - 1) < 0) 0 else _selectionEndtPosition - 1
        }

        val stringBuilder = StringBuilder(_inputString.value)

        Timber.d("delete request: $deletePos" +
                "\n$stringBuilder" +
                "\n$composingTextStartingPosition")

        try {
            stringBuilder.deleteAt(
                deletePos
            )
        }catch (e: Exception){
            if (e is CancellationException) throw e
        }

        if (_inputString.value.length == 1){
            composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
            _inputString.value = EMPTY_STRING
        }else{
            composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
            _inputString.update {
                stringBuilder.toString()
            }
            delay(100L)
            _suggestionFlag.update { !it }
        }
    }

    /** Auto Completion Does Not Finish Composing Text After Press Delete **/
    private fun deleteStringInWebSearchViewFireFox(){

        if (_inputString.value.length == 1){
            composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
            _inputString.value = EMPTY_STRING
        }else{
            if (composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL)) == true){
                CoroutineScope(mainDispatcher).launch {
                    val composingTextStartingPosition = _selectionEndtPosition - _mComposingTextPosition
                    val deletePos = when{
                        composingTextStartingPosition - 1 < 0 -> 0
                        isHenkan -> _inputString.value.length - 1
                        else -> composingTextStartingPosition - 1
                    }

                    selectionReqPosAfterDelete = if (isHenkan){
                        _inputString.value.length - 1
                    }else {
                        if ((_selectionEndtPosition - 1) < 0) 0 else _selectionEndtPosition - 1
                    }

                    val stringBuilder = StringBuilder(_inputString.value)

                    val extractedText = composingTextTrackingInputConnection?.getExtractedText(ExtractedTextRequest(),0)
                    extractedText?.text?.let { t ->
                        Timber.d("delete request: $deletePos" +
                                "\n$stringBuilder" +
                                "\n$composingTextStartingPosition" +
                                "\n$t" +
                                "\n${_inputString.value}")

                        if (t.length == stringBuilder.length){composingTextTrackingInputConnection?.finishComposingText()}

                        if (stringBuilder.length <= deletePos){
                            try {
                                stringBuilder.deleteAt(
                                    stringBuilder.length - 1
                                )
                            }catch (e: Exception){
                                if (e is CancellationException) throw e
                            }
                            composingTextTrackingInputConnection?.finishComposingText()
                        }else{
                            try {
                                stringBuilder.deleteAt(
                                    deletePos
                                )
                            }catch (e: Exception){
                                if (e is CancellationException) throw e
                            }
                        }

                        _inputString.update {
                            stringBuilder.toString()
                        }
                    }
                    delay(100L)
                    _suggestionFlag.update { !it }
                }
            }
        }

    }

    private fun deleteStringCommon(){
        val composingTextStartingPosition = _selectionEndtPosition - _mComposingTextPosition

        val deletePos = when{
            composingTextStartingPosition - 1 < 0 -> 0
            isHenkan -> _inputString.value.length - 1
            else -> composingTextStartingPosition - 1
        }

        selectionReqPosAfterDelete = if (isHenkan){
            _inputString.value.length - 1
        }else {
            if ((_selectionEndtPosition - 1) < 0) 0 else _selectionEndtPosition - 1
        }

        if (_inputString.value.length == 1){
            composingTextTrackingInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
            _inputString.value = EMPTY_STRING
        }else{
            val stringBuilder = StringBuilder(_inputString.value)
            try {
                stringBuilder.deleteAt(
                    deletePos
                )
            }catch (e: Exception){
                if (e is CancellationException) throw e
            }

            Timber.d("delete key pressed common: $stringBuilder")

            _inputString.value = stringBuilder.toString()
        }
    }

    private fun setCurrentInputCharacterContinuous(
        char: Char,
        insertString: String,
        sb: StringBuilder
    ) {
        insertCharNotContinue = false
        suggestionClickNum = 0
        deleteKeyPressed = false
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        if (hasRequestCursorUpdatesCalled){
            if (insertString.isNotEmpty()){
                val position = when{
                    (_selectionEndtPosition - _mComposingTextPosition) < 0 -> 0
                    else -> _selectionEndtPosition - _mComposingTextPosition
                }
                try {
                    sb
                        .append(insertString)
                        .insert(position,char)
                }catch (e: Exception){
                    if (e is CancellationException) throw e
                }

                Timber.d("key input request con:  ${_inputString.value} $position $char $_selectionEndtPosition $_mComposingTextPosition")
                _inputString.update {
                    sb.toString()
                }
            }else {
                _inputString.update {
                    char.toString()
                }
            }
        } else {
            if (insertString.isNotEmpty()){
                composingTextTrackingInputConnection?.apply {
                    val position = composingTextInsertPosition
                    try {
                        sb
                            .append(insertString)
                            .insert(position,char)
                    }catch (e: Exception){
                        if (e is CancellationException) throw e
                        Timber.e(e.stackTraceToString())
                    }
                    Timber.d("key input request con not request continueous:  ${_inputString.value} $char $composingText")
                    _inputString.update {
                        sb.toString()
                    }
                }
            }else {
                _inputString.update {
                    char.toString()
                }
            }
        }
    }

    private fun setCurrentInputCharacter(
        char: Char,
        inputForInsert: String,
        sb: StringBuilder
    ) {
        if (hasRequestCursorUpdatesCalled){
            if (inputForInsert.isNotEmpty()){
                val position = when{
                    (_selectionEndtPosition - _mComposingTextPosition - 1) < 0 -> 0
                    else -> _selectionEndtPosition - _mComposingTextPosition - 1
                }
                try {
                    val hiraganaAtInsertPosition = inputForInsert[position]
                    Timber.d("key input request:  $inputForInsert $position $char")
                    hiraganaAtInsertPosition.let { c ->
                        insertCharNotContinue = true

                        if (c.getNextInputChar(char) == null){
                            insertCharNotContinue = false
                            val insertPosition = if ((_selectionEndtPosition - _mComposingTextPosition) < 0) 0 else _selectionEndtPosition - _mComposingTextPosition
                            Timber.d("Add char else not con: $insertPosition")
                            val insertSB = try {
                                sb
                                    .append(inputForInsert)
                                    .insert(insertPosition,char)
                            }catch (e: Exception){
                                null
                            }
                            insertSB?.let { sb2 ->
                                _inputString.value = sb2.toString()
                            }

                        }else {
                            appendCharToStringBuilder(
                                c.getNextInputChar(char)!!,
                                position,
                                inputForInsert,
                                sb
                            )
                        }

                    }
                }catch (e: Exception){
                    if (e is CancellationException) throw e
                }
            }else{
                insertCharNotContinue = false
                Timber.d("Add char else not con 2: $_mComposingTextPosition $_selectionEndtPosition $char")
                _inputString.update {
                    char.toString()
                }
            }
        }else{
            if (inputForInsert.isNotEmpty()){
                try {
                    composingTextTrackingInputConnection?.apply {
                        val position = when{
                            composingTextInsertPosition - 1 < 0 -> 0
                            composingText.length <= (composingTextInsertPosition - 1) -> composingText.length - 1
                            else -> composingTextInsertPosition - 1
                        }
                        val hiraganaAtInsertPosition = composingText[position]
                        Timber.d("key input request not cursor:  ${_inputString.value} $hiraganaAtInsertPosition $position")
                        hiraganaAtInsertPosition.let { c ->
                            insertCharNotContinue = true

                            if (c.getNextInputChar(char) == null){

                                insertCharNotContinue = false
                                val insertPosition = if (composingTextInsertPosition < 0) 0 else composingTextInsertPosition
                                Timber.d("Add char else not con: $insertPosition")
                                val insertSB = try {
                                    sb
                                        .append(inputForInsert)
                                        .insert(insertPosition,char)
                                }catch (e: Exception){
                                    null
                                }
                                insertSB?.let { sb2 ->
                                    _inputString.value = sb2.toString()
                                }

                            }else {
                                appendCharToStringBuilder(
                                    c.getNextInputChar(char)!!,
                                    position,
                                    inputForInsert,
                                    sb
                                )
                            }

                        }
                    }
                }catch (e: Exception){
                    Timber.e(e.stackTraceToString())
                    if (e is CancellationException) throw e
                }
            }else{
                insertCharNotContinue = false
                Timber.d("key input request not cursor: not con 2: $_mComposingTextPosition $_selectionEndtPosition")
                _inputString.update {
                    char.toString()
                }
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
            InputTypeForIME.Time, ->{
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
        if (hasRequestCursorUpdatesCalled){
            if (_inputString.value.length == 1){
                sb.append(inputChar)
                _inputString.value = sb.toString()
            }else {
                val position = if ((_selectionEndtPosition - _mComposingTextPosition - 1) < 0) 0 else _selectionEndtPosition - _mComposingTextPosition - 1
                sb.append(_inputString.value)
                    .deleteAt(position)
                    .insert(position, inputChar)
                _inputString.value = sb.toString()
            }
        }else {
            composingTextTrackingInputConnection?.apply {
                if (composingText.length == 1){
                    sb.append(inputChar)
                    _inputString.value = sb.toString()
                }else {
                    val position = composingTextInsertPosition - 1
                    sb.append(composingText)
                        .deleteAt(position)
                        .insert(position, inputChar)
                    _inputString.value = sb.toString()
                }
            }
        }

    }

    private fun dakutenSmallLetter(
        sb: StringBuilder
    ){
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false
        if (_inputString.value.isNotEmpty()){
            if (hasRequestCursorUpdatesCalled){
                val position = when{
                    (_selectionEndtPosition - _mComposingTextPosition - 1) < 0 -> 0
                    else -> _selectionEndtPosition - _mComposingTextPosition - 1
                }
                val insertPosition = try {
                    _inputString.value[position]
                }catch (e: Exception){
                    null
                }
                insertPosition?.let { c ->
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                    }
                }
            } else {
                composingTextTrackingInputConnection?.apply {
                    val position = if(composingTextInsertPosition - 1 < 0) 0 else composingTextInsertPosition - 1
                    val insertPosition = try {
                        composingText[position]
                    }catch (e: Exception){
                        null
                    }
                    insertPosition?.let { c ->
                        c.getDakutenSmallChar()?.let { dakutenChar ->
                            setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                        }
                    }
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

        if (_inputString.value.isNotEmpty() && hasRequestCursorUpdatesCalled){
            val position = when{
                (_selectionEndtPosition - _mComposingTextPosition - 1) < 0 -> 0
                else -> _selectionEndtPosition - _mComposingTextPosition - 1
            }
            val insertPosition = try {
                _inputString.value[position]
            }catch (e: Exception){
                null
            }
            insertPosition?.let { c ->
                c.getDakutenSmallChar()?.let { dakutenChar ->
                    setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                }
            }

        }
        if (_inputString.value.isNotEmpty() && !hasRequestCursorUpdatesCalled){
            composingTextTrackingInputConnection?.apply {
                val position = if(composingTextInsertPosition - 1 < 0) 0 else composingTextInsertPosition - 1
                val insertPosition = try {
                    composingText[position]
                }catch (e: Exception){
                    null
                }
                insertPosition?.let { c ->
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                    }
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
        deleteKeyPressed = false
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = false
        onDeleteLongPressUp = false
        isContinuousTapInputEnabled = false
        if (isHenkan){
            composingTextTrackingInputConnection?.finishComposingText()
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

        if (hasRequestCursorUpdatesCalled){
            if (suggestionClickNum == 0) startSelPosInSuggestion = _selectionEndtPosition

            when{
                // First
                !listIterator.hasPrevious() && listIterator.hasNext() ->{
                    startSelPosInSuggestion = _selectionEndtPosition

                    val startPosition = startSelPosInSuggestion
                    val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                    val length = _inputString.value.length

                    val nextSuggestion = listIterator.next()

                    val text2 = _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )

                    val spanString = nextSuggestion + text2

                    val endPos = composingTextStartPos + nextSuggestion.length

                    val spannableString2 = SpannableString(spanString)
                    spannableString2.apply {
                        setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    composingTextTrackingInputConnection?.apply {
                        setComposingText(spannableString2,1)
                        setSelection(endPos,endPos)
                    }
                    Timber.d("suggestion space clicked f : \n" +
                            " $nextSuggestion \n" +
                            _inputString.value + "\n" +
                            "$startPosition \n" +
                            "$composingTextStartPos \n" +
                            "$length \n" +
                            "$spanString \n" +
                            "$endPos \n " +
                            text2
                    )

                }
                // Middle
                listIterator.hasPrevious() && listIterator.hasNext() ->{
                    val startPosition = startSelPosInSuggestion
                    val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                    val length = _inputString.value.length

                    val nextSuggestion = listIterator.next()

                    val text2 = _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )

                    val spanString = nextSuggestion + text2

                    val endPos = composingTextStartPos + nextSuggestion.length

                    val spannableString2 = SpannableString(spanString)
                    spannableString2.apply {
                        setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    composingTextTrackingInputConnection?.apply {
                        setComposingText(spannableString2,1)
                        setSelection(endPos,endPos)
                    }
                    Timber.d("suggestion space clicked m: \n" +
                            " $nextSuggestion \n" +
                            _inputString.value + "\n" +
                            "$startPosition \n" +
                            "$composingTextStartPos \n" +
                            "$length \n" +
                            "$spanString \n" +
                            "$endPos \n " +
                            text2
                    )
                }
                // End
                listIterator.hasPrevious() && !listIterator.hasNext() ->{
                    val startPosition = startSelPosInSuggestion
                    val composingTextStartPos = if (_mComposingTextPosition < 0) 0 else _mComposingTextPosition
                    val length = _inputString.value.length

                    val nextSuggestion = suggestions[0]

                    val text2 = _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )

                    val spanString = nextSuggestion + text2

                    val endPos = composingTextStartPos + nextSuggestion.length

                    val spannableString2 = SpannableString(spanString)
                    spannableString2.apply {
                        setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    composingTextTrackingInputConnection?.apply {
                        setComposingText(spannableString2,1)
                        setSelection(endPos,endPos)
                    }

                    suggestionClickNum = 0
                }
            }

        } else {
            if (suggestionClickNum == 0) startSelPosInSuggestion = 0

            when{
                // First
                !listIterator.hasPrevious() && listIterator.hasNext() ->{
                    startSelPosInSuggestion = _inputString.value.length

                    val startPosition = startSelPosInSuggestion
                    val composingTextStartPos = 0
                    val length = _inputString.value.length

                    val nextSuggestion = listIterator.next()

                    val text2 = _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )

                    val spanString = nextSuggestion + text2

                    val endPos = nextSuggestion.length

                    val spannableString2 = SpannableString(spanString)
                    spannableString2.apply {
                        setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(BackgroundColorSpan(getColor(R.color.green)),nextSuggestion.length,spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        //setSpan(UnderlineSpan(),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    composingTextTrackingInputConnection?.apply {
                        setComposingText(spannableString2,1)
                        setSelection(nextSuggestion.length,nextSuggestion.length)
                    }
                    Timber.d("suggestion space clicked f : \n" +
                            " $nextSuggestion \n" +
                            _inputString.value + "\n" +
                            "$startPosition \n" +
                            "$composingTextStartPos \n" +
                            "$length \n" +
                            "$spanString \n" +
                            "$endPos \n " +
                            text2
                    )

                }
                // Middle
                listIterator.hasPrevious() && listIterator.hasNext() ->{
                    val startPosition = startSelPosInSuggestion
                    val composingTextStartPos = 0
                    val length = _inputString.value.length

                    val nextSuggestion = listIterator.next()

                    val text2 = _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )

                    val spanString = nextSuggestion + text2

                    val endPos = nextSuggestion.length

                    val spannableString2 = SpannableString(spanString)
                    spannableString2.apply {
                        setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(BackgroundColorSpan(getColor(R.color.green)),nextSuggestion.length,spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        //setSpan(UnderlineSpan(),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    composingTextTrackingInputConnection?.apply {
                        setComposingText(spannableString2,1)
                        setSelection(nextSuggestion.length,nextSuggestion.length)
                    }
                    Timber.d("suggestion space clicked m: \n" +
                            " $nextSuggestion \n" +
                            _inputString.value + "\n" +
                            "$startPosition \n" +
                            "$composingTextStartPos \n" +
                            "$length \n" +
                            "$spanString \n" +
                            "$endPos \n " +
                            text2
                    )
                }
                // End
                listIterator.hasPrevious() && !listIterator.hasNext() ->{
                    val startPosition = startSelPosInSuggestion
                    val composingTextStartPos = 0
                    val length = _inputString.value.length

                    val nextSuggestion = suggestions[0]

                    val text2 = _inputString.value.substring(
                        startPosition - composingTextStartPos, length
                    )

                    val spanString = nextSuggestion + text2

                    val endPos = nextSuggestion.length

                    val spannableString2 = SpannableString(spanString)
                    spannableString2.apply {
                        setSpan(BackgroundColorSpan(getColor(R.color.orange)),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(BackgroundColorSpan(getColor(R.color.green)),nextSuggestion.length,spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        //setSpan(UnderlineSpan(),0,nextSuggestion.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    composingTextTrackingInputConnection?.apply {
                        setComposingText(spannableString2,1)
                        setSelection(endPos,endPos)
                    }

                    suggestionClickNum = 0
                }
            }

        }


    }
    private fun setNextReturnInputCharacter(){
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false
        val insertForString = _inputString.value
        val sb = StringBuilder()
        if (insertForString.isNotEmpty() ){
            if (hasRequestCursorUpdatesCalled){
                val position = when{
                    (_selectionEndtPosition - _mComposingTextPosition - 1) < 0 -> 0
                    else -> _selectionEndtPosition - _mComposingTextPosition - 1
                }
                val insertPosition = try {
                    _inputString.value[position]
                }catch (e: Exception){
                    null
                }
                insertPosition?.let { c ->
                    c.getNextReturnInputChar()?.let { charForReturn ->
                        appendCharToStringBuilder(
                            charForReturn,
                            position,
                            insertForString,
                            sb
                        )
                    }
                }
            }else {
                composingTextTrackingInputConnection?.apply {
                    val position = if ( composingTextInsertPosition - 1 < 0) 0 else composingTextInsertPosition - 1
                    val insertPosition = try {
                        composingText[position]
                    }catch (e: Exception){
                        null
                    }
                    insertPosition?.let { c ->
                        c.getNextReturnInputChar()?.let { charForReturn ->
                            appendCharToStringBuilder(
                                charForReturn,
                                position,
                                insertForString,
                                sb
                            )
                        }
                    }
                }
            }
        }
        _suggestionFlag.update {
            !it
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
