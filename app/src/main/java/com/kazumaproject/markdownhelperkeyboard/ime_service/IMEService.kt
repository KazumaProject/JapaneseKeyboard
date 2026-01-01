package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.extensions.setLayerTypeSolidColor
import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toHankakuKatakana
import com.kazumaproject.core.domain.extensions.toHankakuKigou
import com.kazumaproject.core.domain.extensions.toHiragana
import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.core.domain.extensions.toZenkakuAlphabet
import com.kazumaproject.core.domain.extensions.toZenkakuKatakana
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
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.GridSpacingItemDecoration
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.ShortcutAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME2
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getEnterKeyIndexSumire
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getLastCharacterAsString
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getQWERTYReturnTextInEn
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getQWERTYReturnTextInJp
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllEnglishLetters
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiraganaWithSymbols
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isOnlyTwoCharBracketPair
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isPassword
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.BubbleTextView
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.FloatingDockListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.FloatingDockView
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
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
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.tenkey.extensions.getDakutenFlickLeft
import com.kazumaproject.tenkey.extensions.getDakutenFlickRight
import com.kazumaproject.tenkey.extensions.getDakutenFlickTop
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import com.kazumaproject.tenkey.extensions.getNextInputChar
import com.kazumaproject.tenkey.extensions.getNextReturnInputChar
import com.kazumaproject.tenkey.extensions.isHiragana
import com.kazumaproject.tenkey.extensions.isLatinAlphabet
import com.kazumaproject.zenz.ZenzEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
    lateinit var shortCurRepository: ShortcutRepository

    @Inject
    lateinit var clipboardUtil: ClipboardUtil

    private var zenzEngine: ZenzEngine? = null

    private var shortcutAdapter: ShortcutAdapter? = null

    private var romajiConverter: RomajiKanaConverter? = null

    private lateinit var clipboardManager: ClipboardManager

    private var isClipboardHistoryFeatureEnabled: Boolean = false
    private val clipboardMutex = Mutex()
    private var isCustomKeyboardTwoWordsOutputEnable: Boolean? = false
    private var tenkeyQWERTYSwitchNumber: Boolean? = false
    private var tenkeyQKeymapGuide: Boolean? = false

    private var floatingCandidateWindow: PopupWindow? = null
    private lateinit var floatingCandidateView: View
    private lateinit var listAdapter: FloatingCandidateListAdapter

    private var floatingDockWindow: PopupWindow? = null
    private lateinit var floatingDockView: FloatingDockView

    private var floatingModeSwitchWindow: PopupWindow? = null
    private lateinit var floatingModeSwitchView: BubbleTextView

    private var floatingKeyboardView: PopupWindow? = null
    private var floatingKeyboardBinding: FloatingKeyboardLayoutBinding? = null
    private var isKeyboardFloatingMode: Boolean? = false
    private var isKeyboardRounded: Boolean? = false
    private var bunsetsuSeparation: Boolean? = false
    private var bunsetsuPositionList: List<Int>? = emptyList()
    private var henkanPressedWithBunsetsuDetect: Boolean = false
    private var conversionKeySwipePreference: Boolean? = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

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
    private var suggestionAdapterFull: SuggestionAdapter? = null

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
    private var tenkeyShowIMEButtonPreference: Boolean? = true
    private var qwertyShowIMEButtonPreference: Boolean? = true
    private var qwertyEnableFlickUpPreference: Boolean? = false
    private var qwertyEnableFlickDownPreference: Boolean? = false
    private var qwertyEnableZenkakuSpacePreference: Boolean? = false
    private var qwertyShowPopupWindowPreference: Boolean? = true
    private var qwertyShowCursorButtonsPreference: Boolean? = false
    private var qwertyShowNumberButtonsPreference: Boolean? = false
    private var qwertyShowSwitchRomajiEnglishPreference: Boolean? = false
    private var qwertyShowKutoutenButtonsPreference: Boolean? = false
    private var qwertyShowKeymapSymbolsPreference: Boolean? = false
    private var qwertyRomajiShiftConversionPreference: Boolean? = false
    private var showCandidateInPasswordPreference: Boolean? = true
    private var tabletGojuonLayoutPreference: Boolean? = true
    private var isVibration: Boolean? = true
    private var vibrationTimingStr: String? = "both"
    private var mozcUTPersonName: Boolean? = false
    private var mozcUTPlaces: Boolean? = false
    private var mozcUTWiki: Boolean? = false
    private var mozcUTNeologd: Boolean? = false
    private var mozcUTWeb: Boolean? = false
    private var switchQWERTYPassword: Boolean? = false
    private var shortcutTollbarVisibility: Boolean? = false
    private var clipboardPreviewVisibility: Boolean? = true
    private var clipboardPreviewTapToDelete: Boolean? = false
    private var isDeleteLeftFlickPreference: Boolean? = true
    private var tenkeyHeightPreferenceValue: Int? = 280
    private var tenkeyWidthPreferenceValue: Int? = 100
    private var qwertyHeightPreferenceValue: Int? = 280
    private var qwertyWidthPreferenceValue: Int? = 100
    private var candidateViewHeightPreferenceValue: Int? = 110
    private var candidateViewHeightEmptyPreferenceValue: Int? = 110
    private var tenkeyPositionPreferenceValue: Boolean? = true
    private var tenkeyBottomMarginPreferenceValue: Int? = 0
    private var qwertyPositionPreferenceValue: Boolean? = true
    private var qwertyBottomMarginPreferenceValue: Int? = 0

    private var tenkeyHeightLandScapePreferenceValue: Int? = 280
    private var tenkeyWidthLandScapePreferenceValue: Int? = 100
    private var qwertyHeightLandScapePreferenceValue: Int? = 280
    private var qwertyWidthLandScapePreferenceValue: Int? = 100
    private var candidateViewLandScapeHeightPreferenceValue: Int? = 110
    private var candidateViewLandScapeHeightEmptyPreferenceValue: Int? = 110
    private var tenkeyLandScapePositionPreferenceValue: Boolean? = true
    private var tenkeyLandScapeBottomMarginPreferenceValue: Int? = 0
    private var qwertyLandScapePositionPreferenceValue: Boolean? = true
    private var qwertyLandScapeBottomMarginPreferenceValue: Int? = 0

    private var tenkeyStartMarginPreferenceValue: Int? = 0
    private var tenkeyEndMarginPreferenceValue: Int? = 0
    private var qwertyStartMarginPreferenceValue: Int? = 0
    private var qwertyEndMarginPreferenceValue: Int? = 0

    private var tenkeyLandScapeStartMarginPreferenceValue: Int? = 0
    private var tenkeyLandScapeEndMarginPreferenceValue: Int? = 0
    private var qwertyLandScapeStartMarginPreferenceValue: Int? = 0
    private var qwertyLandScapeEndMarginPreferenceValue: Int? = 0

    private var enableShowLastShownKeyboardInRestart: Boolean? = false
    private var lastSavedKeyboardPosition: Int? = 0

    private var zenzEnableStatePreference: Boolean? = false
    private var zenzProfilePreference: String? = ""
    private var zenzEnableLongPressConversionPreference: Boolean? = false

    private var qwertyKeyVerticalMargin: Float? = 5.0f
    private var qwertyKeyHorizontalGap: Float? = 2.0f
    private var qwertyKeyIndentLarge: Float? = 23.0f
    private var qwertyKeyIndentSmall: Float? = 9.0f
    private var qwertyKeySideMargin: Float? = 4.0f
    private var qwertyKeyTextSize: Float? = 18.0f

    private var keyboardThemeMode: String? = "default"
    private var customThemeBgColor: Int? = Color.WHITE
    private var customThemeKeyColor: Int? = Color.LTGRAY
    private var customThemeSpecialKeyColor: Int? = Color.GRAY
    private var customThemeKeyTextColor: Int? = Color.BLACK
    private var customThemeSpecialKeyTextColor: Int? = Color.BLACK

    private var liquidGlassThemePreference: Boolean? = false
    private var liquidGlassBlurRadiousPreference: Int? = 220
    private var liquidGlassKeyBlurRadiousPreference: Int? = 255

    private var customKeyBorderEnablePreference: Boolean? = false
    private var customKeyBorderEnableColor: Int? = Color.BLACK

    private var customComposingTextPreference: Boolean? = false

    private var inputCompositionBackgroundColor: Int? = "#440099CC".toColorInt()
    private var inputCompositionAfterBackgroundColor: Int? = "#770099CC".toColorInt()
    private var inputCompositionTextColor: Int? = Color.WHITE

    private var inputConversionBackgroundColor: Int? = "#55FF8800".toColorInt()
    private var inputConversionTextColor: Int? = Color.WHITE

    private var enableTypoCorrectionJapaneseFlickKeyboardPreference: Boolean? = false
    private var enableTypoCorrectionQwertyEnglishKeyboardPreference: Boolean? = false

    @Deprecated(
        message = "Use the new input key type management system instead. This field is kept only for backward compatibility."
    )
    private var sumireInputKeyType: String? = "flick-default"
    private var sumireInputKeyLayoutType: String? = "toggle"
    private var sumireInputStyle: String? = "default"
    private var candidateColumns: String? = "1"
    private var candidateColumnsLandscape: String? = "1"
    private var candidateViewHeight: String? = "2"
    private var candidateTabVisibility: Boolean? = false
    private var symbolKeyboardFirstItem: SymbolMode? = SymbolMode.EMOJI
    private var userDictionaryPrefixMatchNumber: Int? = 2
    private var isTablet: Boolean? = false
    private var isNgWordEnable: Boolean? = false
    private var deleteKeyHighLight: Boolean? = true
    private var customKeyboardSuggestionPreference: Boolean? = true
    private var zenzDebounceTimePreference: Int? = 300
    private var zenzMaximumLetterSizePreference: Int? = 32
    private var zenzMaximumContextSizePreference: Int? = 512
    private var zenzMaximumThreadSizePreference: Int? = 4

    private var sumireEnglishQwertyPreference: Boolean? = false
    private var conversionCandidatesRomajiEnablePreference: Boolean? = false

    private var enableZenzRightContextPreference: Boolean? = false

    private var learnFirstCandidateDictionaryPreference: Boolean? = false
    private var enablePredictionSearchLearnDictionaryPreference: Boolean? = false
    private var learnPredictionPreference: Int? = 2
    private var circularFlickWindowScale: Float? = 1.0f

    private var customKeyBorderWidth: Int? = 1

    private var qwertySwitchNumberKeyWithoutNumberPreference: Boolean? = false

    private val _ngWordsList = MutableStateFlow<List<NgWord>>(emptyList())
    private val ngWordsList: StateFlow<List<NgWord>> = _ngWordsList
    private val _ngPattern = MutableStateFlow("".toRegex())
    private val ngPattern: StateFlow<Regex> = _ngPattern
    private var isPrivateMode = false

    private val _keyboardFloatingMode = MutableStateFlow(false)
    private val keyboardFloatingMode = _keyboardFloatingMode.asStateFlow()

    private var keyboardContainer: FrameLayout? = null

    private var isSpaceKeyLongPressed = false
    private val _selectMode = MutableStateFlow(false)
    private val selectMode: StateFlow<Boolean> = _selectMode

    private val _cursorMoveMode = MutableStateFlow(false)
    private val cursorMoveMode: StateFlow<Boolean> = _cursorMoveMode
    private var hasConvertedKatakana = false

    private val deletedBuffer = StringBuilder()

    private var keyboardOrder: List<KeyboardType> = emptyList()
    private var candidateTabOrder: List<CandidateTab> = emptyList()

    private var customLayouts: List<CustomKeyboardLayout> = emptyList()

    private var currentNightMode: Int = 0

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

        private val passwordTypes = setOf(
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextPassword,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.TextVisiblePassword,
        )

        private val passwordTypesWithOutNumber = setOf(
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
        )

        private val numberTypes = setOf(
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
        )

    }

    private var currentPage: Int = 0
    private var currentHighlightIndex: Int = RecyclerView.NO_POSITION
    private var fullSuggestionsList: List<CandidateItem> = emptyList()

    private var initialCursorDetectInFloatingCandidateView = false
    private var initialCursorXPosition: Int = 0

    private var physicalKeyboardFloatingXPosition = 200
    private var physicalKeyboardFloatingYPosition = 150

    private var dismissJob: Job? = null

    private var currentCustomKeyboardPosition = 0

    private var hasHardwareKeyboardConnected: Boolean? = false
    private var currentEnterKeyIndex: Int = 0 // 0:改行, 1:確定,
    private var currentDakutenKeyIndex: Int = 0 // 0:^_^, 1:゛゜
    private var currentSpaceKeyIndex: Int = 0 // 0: Space, 1: Convert
    private var currentKatakanaKeyIndex: Int = 0 // 0: SiwtchToNumber, 1: Katakana

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var systemBottomInset = 0

    private var suppressSuggestions: Boolean = false

    private var countToggleKatakana = 0

    private var hardKeyboardShiftPressd = false

    private var isDefaultRomajiHenkanMap = false

    private var bunsetusMultipleDetect = false

    private val _zenzCandidates = MutableStateFlow<List<ZenzCandidate>>(emptyList())
    private val zenzCandidates: StateFlow<List<ZenzCandidate>> = _zenzCandidates
    private var lastCandidate: String? = ""

    private val _zenzRequest = MutableSharedFlow<String>(
        extraBufferCapacity = 0
    )

    private val zenzRequest = _zenzRequest

    private val lastLocalUpdatedInput = MutableStateFlow("")

    private var addUserDictionaryPopup: PopupWindow? = null

    private var filteredCandidateList: List<Candidate>? = emptyList()

    private var previousTenKeyQWERTYMode: TenKeyQWERTYMode? = null

    private var currentKeyboardOrder = 0

    private data class ImeItem(
        val id: String,                 // imeId (InputMethodInfo.getId())
        val packageName: String,
        val settingsActivity: String?,   // 例: "com.example.ime.SettingsActivity"
        val label: CharSequence
    )

    private sealed class RowItem {
        data class Internal(val type: KeyboardType, val title: String) : RowItem()
        data class External(val ime: ImeItem) : RowItem()
    }

    // 設定値を保持するためのデータクラス
    private data class KeyboardSizePreferences(
        val heightPref: Int,
        val widthPref: Int,
        val bottomMargin: Int,
        val positionIsEnd: Boolean, // true: End, false: Start
        val candidateEmptyHeight: Int,
        val qwertyHeightPref: Int,
        val qwertyWidthPref: Int,
        val qwertyBottomMargin: Int,
        val qwertyPositionIsEnd: Boolean,
        val keyboardMarginStart: Int,
        val keyboardMarginEnd: Int,
        val qwertyMarginStart: Int,
        val qwertyMarginEnd: Int,
    )

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        zenzEngine = providesZenzEngine(this)

        suggestionAdapter = SuggestionAdapter().apply {
            onListUpdated = {
                if (isKeyboardFloatingMode != true) {
                    mainLayoutBinding?.apply {
                        suggestionRecyclerView.scrollToPosition(0)
                    }
                }
            }
        }
        suggestionAdapterFull = SuggestionAdapter()
        shortcutAdapter = ShortcutAdapter()
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
        ioScope.launch {
            customLayouts = keyboardRepository.getLayoutsNotFlow()
            shortCurRepository.initDefaultShortcutsIfNeeded()
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        mainLayoutBinding?.suggestionProgressbar?.isVisible = true
                    }

                    override fun onBeginningOfSpeech() {

                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        mainLayoutBinding?.suggestionProgressbar?.isVisible = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        mainLayoutBinding?.suggestionProgressbar?.isVisible = false
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return
                        _inputString.update { text }
                        mainLayoutBinding?.suggestionProgressbar?.isVisible = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return
                        _inputString.update { text }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
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
        Timber.d("onStartInput: ${Build.MANUFACTURER}")
        Timber.d("onUpdate onStartInput called $restarting ${attribute?.imeOptions}")
        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        resetAllFlags()
        physicalKeyboardFloatingXPosition = 200
        physicalKeyboardFloatingYPosition = 150
        _suggestionViewStatus.update { true }
        appPreference.apply {
            keyboardOrder = keyboard_order
            candidateTabOrder = candidate_tab_order
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
            qwertyShowIMEButtonPreference = qwerty_show_ime_button ?: true
            tenkeyShowIMEButtonPreference = tenkey_show_language_button_preference
            qwertyShowCursorButtonsPreference = qwerty_show_cursor_buttons ?: false
            qwertyShowNumberButtonsPreference = qwerty_show_number_buttons ?: false
            qwertyShowSwitchRomajiEnglishPreference =
                qwerty_show_switch_romaji_english_button ?: true
            qwertyShowPopupWindowPreference = qwerty_show_popup_window ?: true
            qwertyEnableFlickUpPreference = qwerty_enable_flick_up_preference ?: false
            qwertyEnableFlickDownPreference = qwerty_enable_flick_down_preference ?: false
            qwertyEnableZenkakuSpacePreference = qwerty_enable_zenkaku_space_preference ?: false
            qwertyShowKutoutenButtonsPreference = qwerty_show_kutouten_buttons ?: false
            showCandidateInPasswordPreference = show_candidates_password ?: true
            qwertyShowKeymapSymbolsPreference = qwerty_show_keymap_symbols ?: false
            qwertyRomajiShiftConversionPreference = qwerty_romaji_shift_conversion_preference
            tabletGojuonLayoutPreference = tablet_gojuon_layout_preference
            isNgWordEnable = ng_word_preference ?: true
            deleteKeyHighLight = delete_key_high_light_preference ?: true
            customKeyboardSuggestionPreference = custom_keyboard_suggestion_preference ?: true
            userDictionaryPrefixMatchNumber = user_dictionary_prefix_match_number_preference ?: 2
            isVibration = vibration_preference ?: true
            vibrationTimingStr = vibration_timing_preference ?: "both"
            sumireInputKeyType = sumire_input_selection_preference ?: "flick-default"
            sumireInputKeyLayoutType = sumire_input_method
            sumireInputStyle = sumire_keyboard_style
            candidateColumns = candidate_column_preference
            candidateColumnsLandscape = candidate_column_landscape_preference
            candidateTabVisibility = candidate_tab_preference
            symbolKeyboardFirstItem = symbol_mode_preference
            isCustomKeyboardTwoWordsOutputEnable = custom_keyboard_two_words_output ?: true
            tenkeyQWERTYSwitchNumber = tenkey_qwerty_switch_number_layout ?: false
            tenkeyQKeymapGuide = tenkey_keymap_guide_layout ?: false
            isKeyboardFloatingMode = is_floating_mode ?: false
            isKeyboardRounded = keyboard_corner_round_preference
            bunsetsuSeparation = bunsetsu_separation_preference
            conversionKeySwipePreference = conversion_key_swipe_cursor_move_preference
            _keyboardFloatingMode.update { is_floating_mode ?: false }
            switchQWERTYPassword = switch_qwerty_password ?: false
            shortcutTollbarVisibility = shortcut_toolbar_visibility_preference
            isDeleteLeftFlickPreference = delete_key_left_flick_preference
            zenzDebounceTimePreference = zenz_debounce_time_preference ?: 300
            zenzMaximumLetterSizePreference = zenz_maximum_letter_size_preference ?: 32
            zenzMaximumContextSizePreference = zenz_maximum_context_size_preference ?: 512
            zenzMaximumThreadSizePreference = zenz_maximum_thread_size_preference ?: 4
            clipboardPreviewVisibility = clipboard_preview_preference
            clipboardPreviewTapToDelete = clipboard_preview_tap_delete_preference

            tenkeyHeightPreferenceValue = keyboard_height ?: 280
            tenkeyWidthPreferenceValue = keyboard_width ?: 100
            qwertyHeightPreferenceValue = qwerty_keyboard_height ?: 280
            qwertyWidthPreferenceValue = qwerty_keyboard_width ?: 100

            candidateViewHeightPreferenceValue = candidate_view_height_dp ?: 110
            candidateViewHeightEmptyPreferenceValue = candidate_view_empty_height_dp ?: 110

            tenkeyPositionPreferenceValue = keyboard_position ?: true
            tenkeyBottomMarginPreferenceValue = keyboard_vertical_margin_bottom ?: 0
            qwertyPositionPreferenceValue = qwerty_keyboard_position ?: true
            qwertyBottomMarginPreferenceValue = qwerty_keyboard_vertical_margin_bottom ?: 0

            tenkeyStartMarginPreferenceValue = keyboard_margin_start_dp ?: 0
            tenkeyEndMarginPreferenceValue = keyboard_margin_end_dp ?: 0
            qwertyStartMarginPreferenceValue = qwerty_keyboard_margin_start_dp ?: 0
            qwertyEndMarginPreferenceValue = qwerty_keyboard_margin_end_dp ?: 0

            tenkeyLandScapeStartMarginPreferenceValue = keyboard_margin_start_dp_landscape
            tenkeyLandScapeEndMarginPreferenceValue = keyboard_margin_end_dp_landscape
            qwertyLandScapeStartMarginPreferenceValue = qwerty_keyboard_margin_start_dp_landscape
            qwertyLandScapeEndMarginPreferenceValue = qwerty_keyboard_margin_end_dp_landscape

            enableShowLastShownKeyboardInRestart = save_last_used_keyboard_enable_preference
            lastSavedKeyboardPosition = save_last_used_keyboard_position_preference
            if (save_last_used_keyboard_enable_preference) {
                currentKeyboardOrder = save_last_used_keyboard_position_preference
            }

            tenkeyHeightLandScapePreferenceValue = keyboard_height_landscape ?: 280
            tenkeyWidthLandScapePreferenceValue = keyboard_width_landscape ?: 100
            qwertyHeightLandScapePreferenceValue = qwerty_keyboard_height_landscape ?: 280
            qwertyWidthLandScapePreferenceValue = qwerty_keyboard_width_landscape ?: 100

            candidateViewLandScapeHeightPreferenceValue = candidate_view_height_dp_landscape ?: 110
            candidateViewLandScapeHeightEmptyPreferenceValue =
                candidate_view_empty_height_dp_landscape ?: 110

            tenkeyLandScapePositionPreferenceValue = keyboard_position_landscape ?: true
            tenkeyLandScapeBottomMarginPreferenceValue =
                keyboard_vertical_margin_bottom_landscape ?: 0
            qwertyLandScapePositionPreferenceValue = qwerty_keyboard_position_landscape ?: true
            qwertyLandScapeBottomMarginPreferenceValue =
                qwerty_keyboard_vertical_margin_bottom_landscape ?: 0

            zenzEnableStatePreference = enable_zenz_preference
            zenzProfilePreference = zenz_profile_preference
            zenzEnableLongPressConversionPreference = enable_zenz_long_press_preference

            qwertyKeyVerticalMargin = qwerty_key_vertical_margin
            qwertyKeyHorizontalGap = qwerty_key_horizontal_gap
            qwertyKeyIndentLarge = qwerty_key_indent_large
            qwertyKeyIndentSmall = qwerty_key_indent_small
            qwertyKeySideMargin = qwerty_key_side_margin
            qwertyKeyTextSize = qwerty_key_text_size

            keyboardThemeMode = theme_mode
            customThemeBgColor = custom_theme_bg_color
            customThemeKeyColor = custom_theme_key_color
            customThemeSpecialKeyColor = custom_theme_special_key_color
            customThemeKeyTextColor = custom_theme_key_text_color
            customThemeSpecialKeyTextColor = custom_theme_special_key_text_color

            liquidGlassThemePreference = liquid_glass_preference
            liquidGlassBlurRadiousPreference = liquid_glass_blur_radius
            liquidGlassKeyBlurRadiousPreference = liquid_glass_key_alpha
            customKeyBorderEnablePreference = custom_theme_border_enable
            customKeyBorderEnableColor = custom_theme_border_color

            customComposingTextPreference = custom_theme_input_color_enable
            inputCompositionBackgroundColor = custom_theme_pre_edit_bg_color
            inputCompositionTextColor = custom_theme_pre_edit_text_color
            inputConversionBackgroundColor = custom_theme_post_edit_bg_color
            inputConversionTextColor = custom_theme_post_edit_text_color

            inputCompositionAfterBackgroundColor =
                manipulateColor(custom_theme_pre_edit_bg_color, 1.2f)

            sumireEnglishQwertyPreference = sumire_english_qwerty_preference
            conversionCandidatesRomajiEnablePreference =
                conversion_candidates_romaji_enable_preference

            enableZenzRightContextPreference = enable_zenz_right_context_preference

            learnFirstCandidateDictionaryPreference = learn_first_candidate_dictionary_preference
            enablePredictionSearchLearnDictionaryPreference =
                enable_prediction_search_learn_dictionary_preference
            learnPredictionPreference = learn_prediction_preference
            circularFlickWindowScale = circular_flickWindow_scale
            customKeyBorderWidth = custom_theme_border_width
            qwertySwitchNumberKeyWithoutNumberPreference =
                qwerty_switch_number_key_without_number_preference

            enableTypoCorrectionJapaneseFlickKeyboardPreference =
                enable_typo_correction_japanese_flick_keyboard_preference
            enableTypoCorrectionQwertyEnglishKeyboardPreference =
                enable_typo_correction_qwerty_english_keyboard_preference

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
        suggestionAdapter?.updateCustomTabVisibility(customKeyboardSuggestionPreference ?: true)
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        keyboardSelectionPopupWindow?.dismiss()
        addUserDictionaryPopup?.dismiss()
        _keyboardSymbolViewState.update { false }
        _selectMode.update { false }
        _cursorMoveMode.update { false }
        val hasPhysicalKeyboard = inputManager.inputDeviceIds.any { deviceId ->
            isDevicePhysicalKeyboard(inputManager.getInputDevice(deviceId))
        }
        zenzEngine?.setRuntimeConfig(
            nCtx = zenzMaximumContextSizePreference ?: 512,
            nThreads = zenzMaximumThreadSizePreference ?: 4
        )
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapter?.setCandidateTextSize(appPreference.candidate_letter_size ?: 14.0f)
        suggestionAdapterFull?.setCandidateTextSize(appPreference.candidate_letter_size ?: 14.0f)
        suggestionClickNum = 0
        setCurrentInputType(editorInfo)
        suggestionAdapter?.setClipboardDescriptionTextVisibility(
            !(clipboardPreviewTapToDelete ?: false)
        )
        if (qwertyMode.value == TenKeyQWERTYMode.Sumire) {
            mainLayoutBinding?.let { mainView ->
                Timber.d("TenKeyQWERTYMode.Sumire: ${mainView.keyboardView.currentInputMode.value} ${switchQWERTYPassword}")
                when (mainView.keyboardView.currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        updateKeyboardLayout()
                    }

                    InputMode.ModeEnglish -> {
                        if (switchQWERTYPassword == true) {
                            if (currentInputType in passwordTypesWithOutNumber) {
                                mainView.qwertyView.resetQWERTYKeyboard()
                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            } else {
                                if (currentInputType !in numberTypes) {
                                    customKeyboardMode = KeyboardInputMode.ENGLISH
                                    createNewKeyboardLayoutForSumire()
                                }
                            }
                        } else {
                            customKeyboardMode = KeyboardInputMode.ENGLISH
                            createNewKeyboardLayoutForSumire()
                        }
                    }

                    InputMode.ModeNumber -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                    }
                }
            }
        }

        updateClipboardPreview()

        Timber.d("onUpdate onStartInputView called after $isPrivateMode $hasPhysicalKeyboard $currentInputType $restarting ${mainLayoutBinding?.keyboardView?.currentInputMode?.value}　${editorInfo?.inputType} $currentKeyboardOrder ${keyboardOrder[currentKeyboardOrder]}\n${candidateTabVisibility}")
        suppressSuggestions = if (showCandidateInPasswordPreference == true) {
            currentInputType.isPassword()
        } else {
            false
        }

        if (currentInputType in passwordTypesWithOutNumber) {
            if (switchQWERTYPassword == true) {
                Timber.d("current input type in OnStartView passwordTypesWithOutNumber: [$currentInputType] [$restarting] [${mainLayoutBinding?.keyboardView?.currentInputMode?.value}] [${qwertyMode.value}]")
                suggestionAdapter?.updateState(
                    TenKeyQWERTYMode.TenKeyQWERTY, emptyList()
                )
                mainLayoutBinding?.let { mainView ->
                    mainView.apply {
                        if (isTablet == true && tabletGojuonLayoutPreference == true) {
                            tabletView.isVisible = false
                        } else {
                            keyboardView.isVisible = false
                        }
                        customLayoutDefault.isVisible = false
                        qwertyView.setRomajiEnglishSwitchKeyVisibility(false)
                        qwertyView.resetQWERTYKeyboard()
                        qwertyView.isVisible = true
                    }
                }
            } else {
                Timber.d("current input type in OnStartView passwordTypesWithOutNumber else: [$currentInputType] [$restarting]")
                if (isTablet == true) {
                    mainLayoutBinding?.tabletView?.currentInputMode?.set(InputMode.ModeEnglish)
                } else {
                    mainLayoutBinding?.keyboardView?.setCurrentMode(InputMode.ModeEnglish)
                }
            }
        } else {
            Timber.d("current input type in OnStartView not password: [$currentInputType] [$restarting]")
            resetKeyboard()
        }

        if (isKeyboardFloatingMode == true) {
            val isPortrait =
                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val screenWidth = resources.displayMetrics.widthPixels

            val widthPref = if (isPortrait) {
                tenkeyWidthPreferenceValue ?: 100
            } else {
                tenkeyWidthLandScapePreferenceValue ?: 100
            }

            val widthPx = when {
                widthPref == 100 -> {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }

                else -> {
                    (screenWidth * (widthPref / 100f)).toInt()
                }
            }
            changeFloatingKeyboardSize(
                newWidthDp = widthPx
            )

            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                floatingKeyboardLayoutBinding.keyboardViewFloating.applyKeyboardTheme(
                    themeMode = keyboardThemeMode ?: "default",
                    currentNightMode = currentNightMode,
                    isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                    customBgColor = customThemeBgColor ?: Color.WHITE,
                    customKeyColor = customThemeKeyColor ?: Color.WHITE,
                    customSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY,
                    customKeyTextColor = customThemeKeyTextColor ?: Color.BLACK,
                    customSpecialKeyTextColor = customThemeSpecialKeyTextColor ?: Color.BLACK,
                    liquidGlassEnable = liquidGlassThemePreference ?: false,
                    customBorderEnable = customKeyBorderEnablePreference ?: false,
                    customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                    liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                    borderWidth = customKeyBorderWidth ?: 1
                )
                floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
                    setOnFlickListener(object : FlickListener {
                        override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
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
                                    handleTapAndFlickFloating(
                                        key = key,
                                        char = char,
                                        insertString = insertString,
                                        sb = sb,
                                        isFlick = false,
                                        gestureType = gestureType,
                                        suggestions = suggestionList,
                                        floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                                    )
                                }


                                else -> {
                                    handleTapAndFlickFloating(
                                        key = key,
                                        char = char,
                                        insertString = insertString,
                                        sb = sb,
                                        isFlick = true,
                                        gestureType = gestureType,
                                        suggestions = suggestionList,
                                        floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                                    )
                                }
                            }
                        }
                    })
                    setOnLongPressListener(object : LongPressListener {
                        override fun onLongPress(key: Key) {
                            handleLongPressFloating(key)
                            Timber.d("Long Press: $key")
                        }
                    })
                }
                floatingKeyboardLayoutBinding.suggestionRecyclerView.adapter = suggestionAdapter
                floatingKeyboardLayoutBinding.candidatesRowView.adapter = suggestionAdapterFull
                mainLayoutBinding?.suggestionRecyclerView?.adapter = null
                mainLayoutBinding?.candidatesRowView?.adapter = null
            }
        }
        mainLayoutBinding?.let { mainView ->
            if (!hasPhysicalKeyboard) {
                if (isKeyboardFloatingMode == true) {
                    applyFloatingModeState(true)
                } else {
                    setKeyboardSizeSwitchKeyboard(mainView)
                    applyFloatingModeState(false)
                }
            } else {
                checkForPhysicalKeyboard(true)
            }
            mainView.apply {
                if (isKeyboardRounded == true) {
                    when (keyboardThemeMode) {
                        "default" -> {
                            if (DynamicColors.isDynamicColorAvailable()) {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_material_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_material_dark)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_material_dark)
                            } else {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_suggestion)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_suggestion)
                            }
                        }

                        "custom" -> {
                            mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_root)
                            mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_suggestion)
                            mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_suggestion)

                            mainView.root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                            mainView.suggestionViewParent.setDrawableSolidColor(
                                customThemeBgColor ?: Color.WHITE
                            )
                            mainView.candidateTabLayout.setDrawableSolidColor(
                                customThemeBgColor ?: Color.WHITE
                            )
                        }

                        else -> {
                            if (DynamicColors.isDynamicColorAvailable()) {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_material_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_material_dark)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_material_dark)
                            } else {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_suggestion)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.rounded_corners_bg_suggestion)
                            }
                        }
                    }
                } else {
                    when (keyboardThemeMode) {
                        "default" -> {
                            if (DynamicColors.isDynamicColorAvailable()) {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                            } else {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                            }
                        }

                        "custom" -> {
                            mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                            mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                            mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)

                            mainView.root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                            mainView.suggestionViewParent.setDrawableSolidColor(
                                customThemeBgColor ?: Color.WHITE
                            )
                            mainView.candidateTabLayout.setDrawableSolidColor(
                                customThemeBgColor ?: Color.WHITE
                            )
                        }

                        else -> {
                            if (DynamicColors.isDynamicColorAvailable()) {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                            } else {
                                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                            }
                        }
                    }
                }

                if (liquidGlassThemePreference == true) {
                    mainView.root.setDrawableAlpha(liquidGlassBlurRadiousPreference ?: 220)
                    mainView.suggestionViewParent.setDrawableAlpha(0)
                    mainView.candidateTabLayout.setDrawableAlpha(0)
                }

                suggestionRecyclerView.isVisible = true
                suggestionVisibility.isVisible = false
                keyboardView.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                val defaultLetterSize = when (mainView.keyboardView.currentInputMode.value) {
                    InputMode.ModeJapanese -> 17f
                    InputMode.ModeEnglish -> 12f
                    InputMode.ModeNumber -> 16f
                    else -> 17f
                }
                keyboardView.setKeyLetterSize(
                    (appPreference.key_letter_size ?: 0.0f) + defaultLetterSize
                )
                keyboardView.setKeyLetterSizeDelta((appPreference.key_letter_size ?: 0.0f).toInt())
                keyboardView.setLanguageEnableKeyState(tenkeyShowIMEButtonPreference ?: true)
                if (tenkeyShowIMEButtonPreference == true) {
                    keyboardView.setBackgroundSmallLetterKey(cachedLogoDrawable)
                } else {
                    keyboardView.setBackgroundSmallLetterKey(cachedKanaDrawable)
                }

                keyboardView.setFlickGuideEnabled(tenkeyQKeymapGuide ?: false)

                setTabsToTabLayout(mainView)

                suggestionProgressbar.isVisible = false

                tabletView.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                customLayoutDefault.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                qwertyView.setSpecialKeyVisibility(
                    showCursors = qwertyShowCursorButtonsPreference ?: false,
                    showSwitchKey = qwertyShowIMEButtonPreference ?: true,
                    showKutouten = qwertyShowKutoutenButtonsPreference ?: false
                )
                qwertyView.setRomajiEnglishSwitchKeyTextWithStyle(true)
                qwertyView.updateSymbolKeymapState(qwertyShowKeymapSymbolsPreference ?: false)
                qwertyView.updateNumberKeyState(qwertyShowNumberButtonsPreference ?: false)
                qwertyView.setPopUpViewState(qwertyShowPopupWindowPreference ?: true)
                qwertyView.setFlickUpDetectionEnabled(qwertyEnableFlickUpPreference ?: false)
                qwertyView.setFlickDownDetectionEnabled(qwertyEnableFlickDownPreference ?: false)
                qwertyView.setNumberSwitchKeyTextStyle(
                    excludeNumber = qwertySwitchNumberKeyWithoutNumberPreference ?: false
                )
                qwertyView.setSwitchNumberLayoutKeyVisibility(false)
                qwertyView.setDeleteLeftFlickEnabled(isDeleteLeftFlickPreference ?: true)
                qwertyView.setKeyMargins(
                    verticalDp = qwertyKeyVerticalMargin ?: 5.0f,
                    horizontalGapDp = qwertyKeyHorizontalGap ?: 2.0f,
                    indentLargeDp = qwertyKeyIndentLarge ?: 23.0f,
                    indentSmallDp = qwertyKeyIndentSmall ?: 9.0f,
                    sideMarginDp = qwertyKeySideMargin ?: 4.0f,
                    textSizeSp = qwertyKeyTextSize ?: 18.0f
                )
                if (isKeyboardFloatingMode == true) {
                    suggestionRecyclerView.adapter = null
                    candidatesRowView.adapter = null
                } else {
                    suggestionRecyclerView.adapter = suggestionAdapter
                    candidatesRowView.adapter = suggestionAdapterFull
                }
                candidateTabLayout.visibility = View.INVISIBLE
                shortcutToolbarRecyclerview.isVisible = shortcutTollbarVisibility == true
                if (tenkeyQWERTYSwitchNumber == true && mainView.keyboardView.currentInputMode.value == InputMode.ModeEnglish && keyboardOrder[currentKeyboardOrder] == KeyboardType.TENKEY) {
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                    mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(true)
                    mainView.qwertyView.setRomajiMode(false)
                    setKeyboardSizeSwitchKeyboard(mainView)
                }
            }
            setMainSuggestionColumn(mainView)
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
            floatingModeSwitchWindow = PopupWindow(
                floatingModeSwitchView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            floatingModeSwitchWindow?.isTouchable = false
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Timber.d("onUpdate onFinishInputView")
        stopVoiceInput()
        floatingCandidateWindow?.dismiss()
        floatingDockWindow?.dismiss()
        floatingModeSwitchWindow?.dismiss()
        floatingKeyboardView?.dismiss()
    }

    override fun onDestroy() {
        Timber.d("onUpdate onDestroy")
        super.onDestroy()
        mainLayoutBinding?.apply {
            keyboardView.cancelTenKeyScope()
            keyboardSymbolView.release()
        }
        floatingKeyboardBinding?.apply {
            keyboardViewFloating.cancelTenKeyScope()
            floatingSymbolKeyboard.release()
        }
        zenzEngine = null
        suggestionAdapter?.release()
        suggestionAdapter = null
        shortcutAdapter = null
        suggestionAdapterFull = null
        dismissJob = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        clearSymbols()
        floatingCandidateWindow = null
        floatingDockWindow = null
        floatingKeyboardView = null
        floatingModeSwitchWindow = null
        keyboardSelectionPopupWindow = null
        hasHardwareKeyboardConnected = null
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        filteredCandidateList = null
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
        lastCandidate = null
        flickSensitivityPreferenceValue = null
        qwertyShowIMEButtonPreference = null
        tenkeyShowIMEButtonPreference = null
        qwertyShowCursorButtonsPreference = null
        qwertyShowNumberButtonsPreference = null
        qwertyShowSwitchRomajiEnglishPreference = null
        qwertyRomajiShiftConversionPreference = null
        qwertyShowPopupWindowPreference = null
        qwertyEnableFlickUpPreference = null
        qwertyEnableFlickDownPreference = null
        qwertyEnableZenkakuSpacePreference = null
        switchQWERTYPassword = null
        shortcutTollbarVisibility = null
        clipboardPreviewVisibility = null
        clipboardPreviewTapToDelete = null
        isDeleteLeftFlickPreference = null
        qwertyShowKutoutenButtonsPreference = null
        qwertyShowKeymapSymbolsPreference = null
        showCandidateInPasswordPreference = null
        tabletGojuonLayoutPreference = null
        isVibration = null
        tenkeyHeightPreferenceValue = null
        tenkeyWidthPreferenceValue = null
        qwertyHeightPreferenceValue = null
        candidateViewHeightPreferenceValue = null
        candidateViewHeightEmptyPreferenceValue = null
        qwertyWidthPreferenceValue = null
        tenkeyPositionPreferenceValue = null
        tenkeyBottomMarginPreferenceValue = null
        qwertyPositionPreferenceValue = null
        qwertyBottomMarginPreferenceValue = null

        tenkeyStartMarginPreferenceValue = null
        tenkeyEndMarginPreferenceValue = null
        qwertyStartMarginPreferenceValue = null
        qwertyEndMarginPreferenceValue = null

        tenkeyLandScapeStartMarginPreferenceValue = null
        tenkeyLandScapeEndMarginPreferenceValue = null
        qwertyLandScapeStartMarginPreferenceValue = null
        qwertyLandScapeEndMarginPreferenceValue = null

        enableShowLastShownKeyboardInRestart = null
        lastSavedKeyboardPosition = null

        tenkeyHeightLandScapePreferenceValue = null
        tenkeyWidthLandScapePreferenceValue = null
        qwertyHeightLandScapePreferenceValue = null
        candidateViewLandScapeHeightPreferenceValue = null
        candidateViewLandScapeHeightEmptyPreferenceValue = null
        qwertyWidthLandScapePreferenceValue = null
        tenkeyLandScapePositionPreferenceValue = null
        tenkeyLandScapeBottomMarginPreferenceValue = null
        qwertyLandScapePositionPreferenceValue = null
        qwertyLandScapeBottomMarginPreferenceValue = null

        zenzEnableStatePreference = null
        zenzProfilePreference = null
        zenzEnableLongPressConversionPreference = null

        qwertyKeyVerticalMargin = null
        qwertyKeyHorizontalGap = null
        qwertyKeyIndentLarge = null
        qwertyKeyIndentSmall = null
        qwertyKeySideMargin = null
        qwertyKeyTextSize = null

        keyboardThemeMode = null
        customThemeBgColor = null
        customThemeKeyColor = null
        customThemeSpecialKeyColor = null
        customThemeKeyTextColor = null
        customThemeSpecialKeyTextColor = null

        vibrationTimingStr = null
        mozcUTPersonName = null
        romajiConverter = null
        mozcUTPlaces = null
        mozcUTWiki = null
        mozcUTNeologd = null
        mozcUTWeb = null
        sumireInputKeyType = null
        sumireInputKeyLayoutType = null
        sumireInputStyle = null
        candidateColumns = null
        candidateColumnsLandscape = null
        candidateViewHeight = null
        candidateTabVisibility = null
        isTablet = null
        isNgWordEnable = null
        deleteKeyHighLight = null
        customKeyboardSuggestionPreference = null
        zenzDebounceTimePreference = null
        zenzMaximumLetterSizePreference = null
        zenzMaximumContextSizePreference = null
        zenzMaximumThreadSizePreference = null
        symbolKeyboardFirstItem = null
        userDictionaryPrefixMatchNumber = null
        isCustomKeyboardTwoWordsOutputEnable = null
        tenkeyQWERTYSwitchNumber = null
        tenkeyQKeymapGuide = null
        isKeyboardFloatingMode = null
        isKeyboardRounded = null
        bunsetsuSeparation = null
        conversionKeySwipePreference = null
        bunsetsuPositionList = null

        liquidGlassThemePreference = null
        liquidGlassBlurRadiousPreference = null
        liquidGlassKeyBlurRadiousPreference = null
        customKeyBorderEnablePreference = null
        customKeyBorderEnableColor = null

        customComposingTextPreference = null
        inputCompositionBackgroundColor = null
        inputCompositionTextColor = null
        inputCompositionAfterBackgroundColor = null
        inputConversionBackgroundColor = null
        inputConversionTextColor = null

        previousTenKeyQWERTYMode = null

        sumireEnglishQwertyPreference = null
        conversionCandidatesRomajiEnablePreference = null
        enableZenzRightContextPreference = null
        learnFirstCandidateDictionaryPreference = null
        enablePredictionSearchLearnDictionaryPreference = null
        learnPredictionPreference = null
        circularFlickWindowScale = null
        customKeyBorderWidth = null
        qwertySwitchNumberKeyWithoutNumberPreference = null

        enableTypoCorrectionJapaneseFlickKeyboardPreference = null
        enableTypoCorrectionQwertyEnglishKeyboardPreference = null

        inputManager.unregisterInputDeviceListener(this)
        actionInDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        System.gc()
        dismissFloatingDock()
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if ((physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) || isKeyboardFloatingMode == true) {
            val inputHeight = window.window?.decorView?.height ?: 0
            outInsets?.contentTopInsets = inputHeight
            outInsets?.visibleTopInsets = inputHeight
            outInsets?.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onConfigureWindow(win: Window?, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        // Android 12 (API 31) 以上の場合
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            liquidGlassThemePreference == true &&
            isKeyboardFloatingMode != true &&
            hasHardwareKeyboardConnected != true
        ) {
            // 背景のアプリに対してブラーをかける
            win?.setBackgroundBlurRadius(50)
        }
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)

        Timber.d("onUpdateCursorAnchorInfo start: [${cursorAnchorInfo == null}] [${floatingCandidateWindow == null}]")
        val insertString = inputString.value
        if (cursorAnchorInfo == null || floatingCandidateWindow == null || insertString.isEmpty()) {
            return
        }

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
            (screenX - 64).coerceAtLeast(0f).toInt()
        }
        val y = screenY.toInt()

        Timber.d("onUpdateCursorAnchorInfo: baseLine:${cursorAnchorInfo.insertionMarkerBaseline}")
        Timber.d("onUpdateCursorAnchorInfo: bottom:${cursorAnchorInfo.insertionMarkerBottom}")
        Timber.d("onUpdateCursorAnchorInfo: top:${cursorAnchorInfo.insertionMarkerTop}")
        Timber.d("onUpdateCursorAnchorInfo: horizontal:${cursorAnchorInfo.insertionMarkerHorizontal}")

        physicalKeyboardFloatingXPosition = x
        physicalKeyboardFloatingYPosition = y
        initialCursorXPosition = x
        initialCursorDetectInFloatingCandidateView = true
        val currentPopupWindow = floatingCandidateWindow
        currentPopupWindow?.let { currentWindow ->
            Timber.d("onUpdateCursorAnchorInfo window debug: [$physicalKeyboardFloatingXPosition] [$physicalKeyboardFloatingYPosition] [${currentWindow.isShowing}]")
            if (currentWindow.isShowing) {
                currentWindow.update(x, y, -1, -1)
            } else {
                // 表示されていない場合は指定した位置に表示
                currentWindow.showAtLocation(
                    window.window?.decorView, // 親ビュー
                    Gravity.NO_GRAVITY,      // スクリーン座標で直接指定
                    x,                       // x座標
                    y                        // y座標
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_PORTRAIT")
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_LANDSCAPE")
            }

            Configuration.ORIENTATION_UNDEFINED -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_UNDEFINED")
            }

            else -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: else")
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

    private fun updateComposingText(text: String) {
        // 途中経過の表示用（変換中のようなイメージ）
        currentInputConnection?.setComposingText(text, 1)
    }

    private fun commitRecognizedText(text: String) {
        // 確定時：composing を消してから確定文字列を commit
        currentInputConnection?.apply {
            finishComposingText()
            commitText(text, 1)
        }
    }

    private fun startVoiceInput(
        mainView: MainLayoutBinding
    ) {
        Timber.d("startVoiceInput: [$isListening] [$speechRecognizer]")
        mainView.suggestionProgressbar.isVisible = false
        if (isListening) return
        if (speechRecognizer == null) return

        val languageValue: String = when {
            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY -> {
                "en-CA"
            }

            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                if (mainView.qwertyView.getRomajiMode()) {
                    "ja-JP"
                } else {
                    "en-CA"
                }
            }

            isTablet == true -> {
                when (mainView.tabletView.currentInputMode.get()) {
                    InputMode.ModeJapanese -> "ja-JP"
                    InputMode.ModeEnglish -> "en-CA"
                    InputMode.ModeNumber -> "ja-JP"
                }
            }

            isTablet != true -> {
                when (mainView.keyboardView.currentInputMode.value) {
                    InputMode.ModeJapanese -> "ja-JP"
                    InputMode.ModeEnglish -> "en-CA"
                    InputMode.ModeNumber -> "ja-JP"
                }
            }

            else -> {
                "ja-JP"
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 日本語固定にしたければ "ja-JP"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageValue)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: SecurityException) {
            // RECORD_AUDIO が許可されていないなど
            isListening = false
        }
    }

    private fun stopVoiceInput() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        } finally {
            isListening = false
        }
    }

    /**
     * Dynamically changes the size of the floating keyboard view.
     * Pass null for a dimension you don't want to change.
     *
     * @param newWidthDp The desired new width in DP, or null to keep the current width.
     */
    private fun changeFloatingKeyboardSize(newWidthDp: Int? = null) {
        val popupWindow = floatingKeyboardView ?: return

        val newWidthPx = newWidthDp ?: popupWindow.width

        val savedX = appPreference.keyboard_floating_position_x
        val savedY = appPreference.keyboard_floating_position_y

        popupWindow.update(
            savedX, savedY, newWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupKeyboardView() {
        Timber.d("setupKeyboardView: Called")
        val isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        val ctx = when (keyboardThemeMode) {
            "default" -> {
                if (isDynamicColorsEnable) {
                    val seedColor = appPreference.seedColor

                    if (seedColor == 0x00000000) {
                        DynamicColors.wrapContextIfAvailable(this, R.style.Theme_MarkdownKeyboard)
                    } else {
                        val baseThemedContext =
                            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                        val options =
                            DynamicColorsOptions.Builder().setContentBasedSource(seedColor).build()
                        DynamicColors.wrapContextIfAvailable(baseThemedContext, options)
                    }
                } else {
                    ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                }
            }

            "custom" -> {
                ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
            }

            else -> {
                if (isDynamicColorsEnable) {
                    val seedColor = appPreference.seedColor

                    if (seedColor == 0x00000000) {
                        DynamicColors.wrapContextIfAvailable(this, R.style.Theme_MarkdownKeyboard)
                    } else {
                        val baseThemedContext =
                            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                        val options =
                            DynamicColorsOptions.Builder().setContentBasedSource(seedColor).build()
                        DynamicColors.wrapContextIfAvailable(baseThemedContext, options)
                    }
                } else {
                    ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                }
            }
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
                    floatingModeSwitchWindow?.dismiss()
                }
            })
        }

        floatingModeSwitchView = BubbleTextView(ctx).apply {
            text = "あ"
        }

        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))

        floatingKeyboardBinding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(ctx))

        floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
            setFloatingKeyboardListeners(floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding)
            val heightPref = tenkeyHeightPreferenceValue ?: 280
            val widthPref = tenkeyWidthPreferenceValue ?: 100
            val keyboardMarginBottomPref = appPreference.keyboard_vertical_margin_bottom ?: 0

            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels
            val isPortrait =
                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val keyboardMarginBottom = (keyboardMarginBottomPref * density).toInt()
            val heightPx = when {
                keyboardSymbolViewState.value -> {
                    val height = if (isPortrait) 320 else 220
                    (height * density).toInt()
                }

                else -> {
                    val clampedHeight = heightPref.coerceIn(180, 420)
                    (clampedHeight * density).toInt()
                }
            }

            Timber.d("setKeyboardSize: $heightPx $keyboardMarginBottom")

            val widthPx = when {
                widthPref == 100 || keyboardSymbolViewState.value -> {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }

                else -> {
                    (screenWidth * (widthPref / 100f)).toInt()
                }
            }
            if (floatingKeyboardView == null) {
                floatingKeyboardView = PopupWindow(
                    floatingKeyboardLayoutBinding.root,
                    widthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            } else {
                floatingKeyboardView?.dismiss()
                floatingKeyboardView = null

                floatingKeyboardView = PopupWindow(
                    floatingKeyboardLayoutBinding.root,
                    widthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        }

        keyboardContainer?.let { container ->
            container.removeAllViews()
            mainLayoutBinding?.root?.let { newRootView ->
                container.addView(newRootView)
                mainLayoutBinding?.let { mainView ->
                    when (keyboardThemeMode) {
                        "default" -> {
                            if (isDynamicColorsEnable) {
                                mainView.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                    candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                }
                                floatingKeyboardBinding?.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                }
                            }
                        }

                        "custom" -> {
                            mainView.apply {
                                root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                val symbolKeyBg =
                                    customThemeKeyColor ?: Color.WHITE
                                keyboardSymbolView.setKeyboardTheme(
                                    backgroundColor = manipulateColor(symbolKeyBg, 1.2f),
                                    iconColor = customThemeKeyTextColor ?: Color.BLACK,
                                    selectedIconColor = manipulateColor(
                                        customThemeKeyTextColor ?: Color.BLACK, 0.6f
                                    ),
                                    keyBackgroundColor = symbolKeyBg,
                                    liquidGlassEnable = liquidGlassThemePreference ?: false
                                )
                                suggestionAdapter?.setCandidateTextColor(
                                    customThemeKeyTextColor ?: Color.BLACK
                                )
                                suggestionAdapterFull?.setCandidateTextColor(
                                    customThemeKeyTextColor ?: Color.BLACK
                                )
                                suggestionAdapter?.setCandidateEmptyDrawableColor(
                                    customThemeSpecialKeyColor ?: Color.WHITE
                                )

                                suggestionAdapter?.setCandidateEmptyDrawableTextColor(
                                    customThemeSpecialKeyTextColor ?: Color.BLACK
                                )

                                root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                                suggestionViewParent.setDrawableSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )
                                suggestionVisibility.setDrawableSolidColor(
                                    customThemeSpecialKeyColor ?: Color.GRAY
                                )
                                candidateTabLayout.setLayerTypeSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )

                                suggestionVisibility.setColorFilter(
                                    customThemeKeyTextColor ?: Color.BLACK
                                )
                            }
                            floatingKeyboardBinding?.apply {
                                root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)

                                root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                                suggestionViewParent.setDrawableSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )
                                suggestionVisibility.setDrawableSolidColor(
                                    customThemeSpecialKeyColor ?: Color.GRAY
                                )
                            }
                        }

                        else -> {
                            if (isDynamicColorsEnable) {
                                mainView.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                    candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                }
                                floatingKeyboardBinding?.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                }
                            }
                        }
                    }
                    ViewCompat.setOnApplyWindowInsetsListener(mainView.root) { _, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                        systemBottomInset = insets.bottom
                        windowInsets
                    }
                    setCandidateTabLayout(mainView)
                    setupCustomKeyboardListeners(mainView)
                    setSuggestionRecyclerView(
                        mainView, FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.ROW
                            justifyContent = JustifyContent.FLEX_START
                        })
                    setShortCutAdapter(mainView)
                    setSymbolKeyboard(mainView)
                    setQWERTYKeyboard(mainView)
                    if (isTablet == true) {
                        setTabletKeyListeners(mainView)
                    }
                    setTenKeyListeners(mainView)
                    setKeyboardSizeSwitchKeyboard(mainView)
                    updateClipboardPreview()
                    mainView.suggestionRecyclerView.isVisible = suggestionViewStatus.value
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
        // Skip if composing text is active
        if (candidatesStart != -1 || candidatesEnd != -1) {
            return
        }

        Timber.d("onUpdateSelection end called: [${inputString.value}] [${stringInTail.get()}] [${bunsetusMultipleDetect}]")
        if (stringInTail.get().isEmpty()) {
            bunsetusMultipleDetect = false
        }

        // Show clipboard preview only if nothing was deleted and clipboard has data
        suggestionAdapter?.apply {
            if (deletedBuffer.isEmpty()) {
                Timber.d("SuggestionAdapter onUpdateSelection clipboard: ")
                when (val item = clipboardUtil.getPrimaryClipContent()) {
                    is ClipboardItem.Image -> {
                        if (clipboardPreviewVisibility == true) {
                            if (clipboardPreviewTapToDelete != true) {
                                setPasteEnabled(true)
                                setClipboardImagePreview(item.bitmap)
                            }
                        } else {
                            setPasteEnabled(false)
                        }
                    }

                    is ClipboardItem.Text -> {
                        if (clipboardPreviewVisibility == true) {
                            if (clipboardPreviewTapToDelete != true) {
                                setPasteEnabled(true)
                                setClipboardPreview(item.text)
                            }
                        } else {
                            setPasteEnabled(false)
                        }
                    }

                    is ClipboardItem.Empty -> {
                        setPasteEnabled(false)
                        setClipboardPreview("")
                    }
                }
            } else {
                setPasteEnabled(false)
            }
        }

        val tail = stringInTail.get()
        val hasTail = tail.isNotEmpty()
        val caretTop = newSelStart == 0 && newSelEnd == 0

        Timber.d("onUpdateSelection tail: $tail")

        when {

            hasTail && caretTop -> {
                Timber.d("onUpdateSelection hasTail && caretTop: $tail $caretTop")
                stringInTail.set("")
                if (_inputString.value.isNotEmpty()) {
                    _inputString.update { "" }
                    beginBatchEdit()
                    setComposingText("", 0)
                    endBatchEdit()
                }
                suggestionAdapter?.suggestions =
                    emptyList() // avoid unnecessary allocations elsewhere
            }

            // Caret moved while tail exists → commit tail
            hasTail -> {
                Timber.d("onUpdateSelection hasTail : $tail")
                scope.launch {
                    _inputString.update { tail }
                    stringInTail.set("")
                }
            }

            // No tail but still holding input → cleanup
            _inputString.value.isNotEmpty() -> {
                Timber.d("onUpdateSelection _inputString.value.isNotEmpty() : $tail")
                _inputString.update { "" }
                beginBatchEdit()
                setComposingText("", 0)
                endBatchEdit()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mainLayoutBinding?.let { mainView ->
            // モードに応じて処理を振り分ける
            return when (mainView.keyboardView.currentInputMode.value) {
                InputMode.ModeJapanese -> handleJapaneseKeyDown(keyCode, event, mainView)
                InputMode.ModeEnglish -> handleEnglishKeyDown(keyCode, event, mainView)
                InputMode.ModeNumber -> handleNumberKeyDown(keyCode, event, mainView)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

// ---------------------------------------------------------------------------------
// 1. メインのモード別ハンドラ
// ---------------------------------------------------------------------------------

    /**
     * 日本語入力モード時のキーダウン処理
     */
    private fun handleJapaneseKeyDown(
        keyCode: Int, event: KeyEvent?, mainView: MainLayoutBinding
    ): Boolean {

        val insertString = inputString.value
        val suggestions = listAdapter.currentList

        // 1. 修飾キー（Shift, Ctrl）の処理を先に行う
        event?.let { e ->
            if (e.isShiftPressed || e.isCapsLockOn) {
                return handleJapaneseShiftPressed(keyCode, e, insertString)
            }
            if (e.isCtrlPressed) {
                return handleJapaneseCtrlPressed(keyCode, e, mainView, insertString)
            }
        }

        // 2. 通常のキーコード処理
        return when (keyCode) {
            // Fキー (変換)
            KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10 -> handleConversionKeyFloating(
                keyCode,
                insertString
            )

            // モード切替
            KeyEvent.KEYCODE_MUHENKAN -> switchToEnglishModeFloating(mainView)

            // 編集x
            KeyEvent.KEYCODE_DEL -> handleJapaneseDeleteFloating(keyCode, event, insertString)

            // 変換・候補選択
            KeyEvent.KEYCODE_SPACE -> handleJapaneseSpaceFloating(
                mainView, insertString, suggestions
            )

            KeyEvent.KEYCODE_DPAD_LEFT -> handleJapaneseDpadLeft(insertString)
            KeyEvent.KEYCODE_DPAD_RIGHT -> handleJapaneseDpadRight(insertString)
            KeyEvent.KEYCODE_DPAD_UP -> handleJapaneseDpadUp(mainView, insertString, suggestions)
            KeyEvent.KEYCODE_DPAD_DOWN -> handleJapaneseDpadDown(
                mainView, insertString, suggestions
            )

            // 確定
            KeyEvent.KEYCODE_ENTER -> handleJapaneseEnterFloating(
                mainView, insertString, suggestions
            )

            // 無視して親に渡す
            KeyEvent.KEYCODE_BACK -> super.onKeyDown(keyCode, event)

            // 文字入力
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z, in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_AT, KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_NUMPAD_DOT -> {
                handleJapaneseCharacterKeyFloating(
                    keyCode, event, insertString
                )
            }

            // それ以外
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * 英語入力モード時のキーダウン処理
     */
    private fun handleEnglishKeyDown(
        keyCode: Int, event: KeyEvent?, mainView: MainLayoutBinding // mainViewの実際の型に置き換えてください
    ): Boolean {
        event?.let { e ->
            if (e.isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    // 英語モード時のCtrl+Spaceは日本語モードへ
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
                    showFloatingModeSwitchView("あ")
                    return true
                }
            }
            when (keyCode) {
                KeyEvent.KEYCODE_HENKAN -> {
                    // 変換キーで日本語モードへ
                    return switchToHiraganaMode(mainView)
                }

                else -> return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 数字入力モード時のキーダウン処理
     */
    private fun handleNumberKeyDown(
        keyCode: Int, event: KeyEvent?, mainView: MainLayoutBinding // mainViewの実際の型に置き換えてください
    ): Boolean {
        event?.let { e ->
            if (e.isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    // 数字モード時のCtrl+Spaceは日本語モードへ (日本語モードのロジックと同じ)
                    return cycleInputMode(mainView)
                }
            }
            when (keyCode) {
                KeyEvent.KEYCODE_HENKAN -> {
                    // 変換キーで日本語モードへ
                    return switchToHiraganaMode(mainView)
                }

                else -> return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }


// ---------------------------------------------------------------------------------
// 2. 日本語入力のヘルパー関数
// ---------------------------------------------------------------------------------

    private fun handleJapaneseShiftPressed(
        keyCode: Int, event: KeyEvent, insertString: String
    ): Boolean {
        if (event.isCtrlPressed) return super.onKeyDown(keyCode, event)
        hardKeyboardShiftPressd = true
        val char = PhysicalShiftKeyCodeMap.keymap[keyCode]
        char?.let { c ->
            val sb = StringBuilder()
            if (insertString.isNotEmpty()) {
                sb.append(insertString).append(c)
                _inputString.update { sb.toString() }
            } else {
                _inputString.update { c.toString() }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleJapaneseCtrlPressed(
        keyCode: Int, event: KeyEvent, mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return cycleInputMode(mainView)
        }
        if (insertString.isNotEmpty()) return true
        return super.onKeyDown(keyCode, event)
    }

    /**
     * F6-F10の文字種変換処理
     */
    private fun handleConversionKeyFloating(keyCode: Int, insertString: String): Boolean {
        if (insertString.isEmpty()) {
            return true // 元のロジックでは何もせず true を返していた
        }

        Timber.d("onKeyDown: F-Key $keyCode Pressed $insertString")

        val resultString = when (keyCode) {
            KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8 -> {
                if (insertString.isAllEnglishLetters()) {
                    romajiConverter?.let { converter ->
                        val romajiResult = if (isDefaultRomajiHenkanMap) {
                            converter.convertCustomLayout(insertString.lowercase())
                        } else {
                            converter.convert(insertString.lowercase())
                        }
                        when (keyCode) {
                            KeyEvent.KEYCODE_F6 -> romajiResult.toHiragana()
                            KeyEvent.KEYCODE_F7 -> romajiResult.toZenkakuKatakana()
                            KeyEvent.KEYCODE_F8 -> romajiResult.toHankakuKatakana()
                            else -> insertString // ありえない
                        }
                    } ?: insertString
                } else {
                    when (keyCode) {
                        KeyEvent.KEYCODE_F6 -> insertString.toHiragana()
                        KeyEvent.KEYCODE_F7 -> insertString.toZenkakuKatakana()
                        KeyEvent.KEYCODE_F8 -> insertString.toHankakuKatakana()
                        else -> insertString // ありえない
                    }
                }
            }

            KeyEvent.KEYCODE_F9 -> {
                if (insertString.isAllEnglishLetters()) {
                    insertString.lowercase().toZenkakuAlphabet()
                } else {
                    romajiConverter?.hiraganaToRomaji(insertString.toHiragana())
                        ?.toZenkakuAlphabet() ?: insertString
                }
            }

            KeyEvent.KEYCODE_F10 -> {
                if (insertString.isAllEnglishLetters()) {
                    insertString.lowercase().toHankakuAlphabet()
                } else {
                    romajiConverter?.hiraganaToRomaji(insertString.toHiragana())
                        ?.toHankakuAlphabet() ?: insertString
                }
            }

            else -> insertString // 来ないはず
        }

        _inputString.update { resultString }
        return true
    }

    private fun handleJapaneseDeleteFloating(
        keyCode: Int, event: KeyEvent?, insertString: String
    ): Boolean {
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

    private fun handleJapaneseSpaceFloating(
        mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String, suggestions: List<CandidateItem> // suggestionsの実際の型に置き換えてください
    ): Boolean {
        // InputMode.ModeJapanese の KEYCODE_SPACE のロジックをここにペースト
        when (mainView.keyboardView.currentInputMode.value) {
            InputMode.ModeJapanese -> {
                if (insertString.isNotEmpty()) {
                    isHenkan.set(true)
                    Timber.d("KEYCODE_SPACE is pressed")
                    val insertStringEndWithN = if (isDefaultRomajiHenkanMap) {
                        romajiConverter?.flushZenkaku(insertString)?.first
                    } else {
                        romajiConverter?.flush(insertString)?.first
                    }
                    Timber.d("KEYCODE_SPACE is pressed: $insertStringEndWithN $stringInTail")

                    val position = bunsetsuPositionList?.firstOrNull()
                    Timber.d(
                        "handleJapaneseModeSpaceKeyWithBunsetsu called: [$position] [$stringInTail] [$insertStringEndWithN]"
                    )
                    if (insertStringEndWithN == null) {
                        if (position != null && stringInTail.get().isEmpty()) {
                            // 区切り位置がある場合：文字列を分割する
                            val head = insertString.substring(0, position)
                            val tail = insertString.substring(position)
                            _inputString.update { head }
                            stringInTail.set(tail)
                            Timber.d(
                                "handleJapaneseModeSpaceKeyWithBunsetsu called: $bunsetsuPositionList | head: $head, tail: $tail $stringInTail"
                            )
                            isHenkan.set(true)
                            henkanPressedWithBunsetsuDetect = true
                            bunsetsuPositionList?.let {
                                if (it.size > 1) {
                                    bunsetusMultipleDetect = true
                                }
                            }
                            floatingCandidateNextItem(head)
                        } else {
                            floatingCandidateNextItem(insertString)
                            Timber.d(
                                "handleJapaneseModeSpaceKeyWithBunsetsu called: No split position. Full string to tail: $insertString"
                            )
                        }
                    } else {
                        if (position != null && stringInTail.get().isEmpty()) {
                            // 区切り位置がある場合：文字列を分割する
                            val head = insertString.substring(0, position)
                            val tail = insertString.substring(position)
                            _inputString.update { head }
                            stringInTail.set(tail)
                            Timber.d(
                                "handleJapaneseModeSpaceKeyWithBunsetsu called: $bunsetsuPositionList | head: $head, tail: $tail $stringInTail"
                            )
                            isHenkan.set(true)
                            henkanPressedWithBunsetsuDetect = true
                            bunsetsuPositionList?.let {
                                if (it.size > 1) {
                                    bunsetusMultipleDetect = true
                                }
                            }
                            floatingCandidateNextItem(head)
                        } else {
                            _inputString.update { insertStringEndWithN }
                            floatingCandidateNextItem(insertString)
                            scope.launch {
                                delay(64)
                                val newSuggestionList =
                                    suggestionAdapter?.suggestions ?: emptyList()
                                if (newSuggestionList.isNotEmpty()) handleJapaneseModeSpaceKey(
                                    mainView, newSuggestionList, insertStringEndWithN
                                )
                            }
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

    private fun handleJapaneseDpadLeft(insertString: String): Boolean {
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

    private fun handleJapaneseDpadRight(insertString: String): Boolean {
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

    private fun handleJapaneseDpadUp(
        mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String, suggestions: List<CandidateItem> // suggestionsの実際の型に置き換えてください
    ): Boolean {
        if (insertString.isNotEmpty()) {
            if (isHenkan.get()) {
                floatingCandidatePreviousItem(insertString)
                return true
            } else {
                // 非変換時はSpaceキーと同一のロジック
                return handleJapaneseSpaceFloating(mainView, insertString, suggestions)
            }
        }
        // insertStringが空の場合、元のロジックではフォールスルーしていた
        return super.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, null)
    }

    private fun handleJapaneseDpadDown(
        mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String, suggestions: List<CandidateItem> // suggestionsの実際の型に置き換えてください
    ): Boolean {
        if (insertString.isNotEmpty()) {
            if (isHenkan.get()) {
                floatingCandidateNextItem(insertString)
                return true
            } else {
                // 非変換時はSpaceキーと同一のロジック
                return handleJapaneseSpaceFloating(mainView, insertString, suggestions)
            }
        }
        // insertStringが空の場合、元のロジックではフォールスルーしていた
        return super.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, null)
    }

    private fun handleJapaneseEnterFloating(
        mainView: MainLayoutBinding, insertString: String, suggestions: List<CandidateItem>
    ): Boolean {
        if (insertString.isNotEmpty()) {
            if (isHenkan.get()) {
                floatingCandidateEnterPressed()
                romajiConverter?.clear()
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

    private fun handleJapaneseCharacterKeyFloating(
        keyCode: Int, event: KeyEvent?, insertString: String
    ): Boolean {
        event?.let { e ->
            scope.launch {
                _physicalKeyboardEnable.emit(true)
            }
            isKeyboardFloatingMode = false
            val sb = StringBuilder() // ここで宣言

            if (isHenkan.get()) {
                listAdapter.selectHighlightedItem()
                scope.launch {
                    delay(32)
                    val letterConverted = if (isDefaultRomajiHenkanMap) {
                        romajiConverter?.handleKeyEventZenkaku(e)
                    } else {
                        romajiConverter?.handleKeyEvent(e)
                    }
                    letterConverted?.let { romajiResult ->
                        Timber.d("KeyEvent Key Henkan: $e\n$insertString\n${romajiResult.first}")
                        _inputString.update {
                            romajiResult.first
                        }
                    }
                }
                return true
            }
            if (hardKeyboardShiftPressd) {
                val char = PhysicalShiftKeyCodeMap.keymap[keyCode]
                char?.let { c ->
                    if (insertString.isNotEmpty()) {
                        sb.append(
                            insertString
                        ).append(c.lowercase())
                        _inputString.update {
                            sb.toString()
                        }
                    } else {
                        _inputString.update {
                            c.lowercase()
                        }
                    }
                    return true
                }
            } else {
                val letterConverted = if (isDefaultRomajiHenkanMap) {
                    romajiConverter?.handleKeyEventZenkaku(e)
                } else {
                    romajiConverter?.handleKeyEvent(e)
                }
                letterConverted?.let { romajiResult ->
                    Timber.d("onKeyDown: $romajiResult")
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
        return super.onKeyDown(keyCode, null)
    }


// ---------------------------------------------------------------------------------
// 3. 共通ヘルパー関数（モード切替など）
// ---------------------------------------------------------------------------------

    /**
     * Ctrl+Space押下時の入力モードサイクル（日→英→数→日）
     */
    private fun cycleInputMode(mainView: MainLayoutBinding): Boolean {
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

        showFloatingModeSwitchView(showInputModeText)
        finishComposingText()
        _inputString.update { "" }
        return true
    }

    /**
     * ひらがなモード（日本語入力）へ切り替える
     */
    private fun switchToHiraganaMode(mainView: MainLayoutBinding): Boolean {
        customKeyboardMode = KeyboardInputMode.HIRAGANA
        updateKeyboardLayout()
        val inputMode = InputMode.ModeJapanese
        val showInputModeText = "あ"
        Timber.d("switchToHiraganaMode: $inputMode $showInputModeText")
        floatingDockView.setText(showInputModeText)
        mainView.keyboardView.setCurrentMode(inputMode)
        showFloatingModeSwitchView(showInputModeText)
        finishComposingText()
        _inputString.update { "" }
        return true
    }

    /**
     * 英語モードへ切り替える (無変換キー用)
     */
    private fun switchToEnglishModeFloating(mainView: MainLayoutBinding): Boolean {
        customKeyboardMode = KeyboardInputMode.ENGLISH
        updateKeyboardLayout()
        val inputMode = InputMode.ModeEnglish
        val showInputModeText = "A"
        Timber.d("switchToEnglishMode (MUHENKAN): $inputMode $showInputModeText")
        floatingDockView.setText(showInputModeText)
        mainView.keyboardView.setCurrentMode(inputMode)
        showFloatingModeSwitchView(showInputModeText)
        finishComposingText()
        _inputString.update { "" }
        return true
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
                    henkanPressedWithBunsetsuDetect = false
                }
                return super.onKeyUp(keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showFloatingModeSwitchView(showInputModeText: String) {
        // 以前のdismiss処理がスケジュールされていればキャンセルする
        dismissJob?.cancel()

        floatingModeSwitchView.text = showInputModeText
        floatingModeSwitchWindow?.dismiss()
        val modeSwitchPopupWindow = floatingModeSwitchWindow
        modeSwitchPopupWindow?.let { switchWindow ->
            switchWindow.isTouchable = false
            if (switchWindow.isShowing) {
                switchWindow.update(
                    physicalKeyboardFloatingXPosition, physicalKeyboardFloatingYPosition, -1, -1
                )
            } else {
                switchWindow.showAtLocation(
                    window.window?.decorView,
                    Gravity.NO_GRAVITY,
                    physicalKeyboardFloatingXPosition,
                    physicalKeyboardFloatingYPosition
                )
            }
            // 新しいコルーチンを開始し、そのJobを保存する
            dismissJob = scope.launch {
                delay(1500)
                switchWindow.dismiss()
            }
        }
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
            inputString = selectedSuggestion.word,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor ?: getColor(
                    com.kazumaproject.core.R.color.blue
                )
            } else {
                getColor(
                    com.kazumaproject.core.R.color.blue
                )
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
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

    /**
     * フローティングモードの状態に応じて、キーボードの表示/非表示やレイアウトを適用します。
     * @param isFloatingMode フローティングモードが有効かどうかのフラグ
     */
    private fun applyFloatingModeState(isFloatingMode: Boolean) {
        val mainView = mainLayoutBinding ?: return
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            floatingKeyboardView?.dismiss()
            return
        }
        if (isFloatingMode) {
            (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = getScreenHeight(this@IMEService)
                mainView.root.layoutParams = params
            }
            mainView.root.alpha = 0f
            floatingKeyboardView?.apply {
                Timber.d("applyFloatingModeState: isFloatingMode=$isFloatingMode ${this.isShowing}")
                if (!this.isShowing) {
                    val anchorView = window.window?.decorView
                    if (anchorView != null && anchorView.windowToken != null) {
                        val savedX = appPreference.keyboard_floating_position_x
                        val savedY = appPreference.keyboard_floating_position_y
                        if (savedX != -1 && savedY != -1) {
                            showAtLocation(
                                anchorView, Gravity.NO_GRAVITY, savedX, savedY
                            )
                        } else {
                            showAtLocation(
                                anchorView, Gravity.BOTTOM or Gravity.END, 0, 0
                            )
                        }
                    } else {
                        Timber.w("Could not show floating keyboard, window token is not available yet.")
                    }
                }
            }
        } else {
            mainView.root.alpha = 1f
            setKeyboardSizeForHeightForFloatingMode(mainView)
            floatingKeyboardView?.dismiss()
        }
    }

    private fun setTenKeyListeners(
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardView.apply {
            applyKeyboardTheme(
                themeMode = keyboardThemeMode ?: "default",
                currentNightMode = currentNightMode,
                isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                customBgColor = customThemeBgColor ?: Color.WHITE,
                customKeyColor = customThemeKeyColor ?: Color.WHITE,
                customSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY,
                customKeyTextColor = customThemeKeyTextColor ?: Color.BLACK,
                customSpecialKeyTextColor = customThemeSpecialKeyTextColor ?: Color.BLACK,
                liquidGlassEnable = liquidGlassThemePreference ?: false,
                customBorderEnable = customKeyBorderEnablePreference ?: false,
                customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                borderWidth = customKeyBorderWidth ?: 1
            )
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setFloatingKeyboardListeners(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        floatingKeyboardLayoutBinding.apply {
            dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val location = IntArray(2)
                        floatingKeyboardView?.contentView?.getLocationOnScreen(location)
                        initialX = location[0]
                        initialY = location[1]
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val newX = initialX + (event.rawX - initialTouchX)
                        val newY = initialY + (event.rawY - initialTouchY)
                        appPreference.keyboard_floating_position_x = newX.toInt()
                        appPreference.keyboard_floating_position_y = newY.toInt()
                        Timber.d("dragHandle.setOnTouchListene: $newX $newY")
                        floatingKeyboardView?.update(newX.toInt(), newY.toInt(), -1, -1)
                        true
                    }

                    else -> false
                }
            }
            floatingHideKeyboardBtn.setOnClickListener {
                requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS)
            }
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
            Timber.d("onUpdateCursorAnchorInfo displayCurrentPage empty called")
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
            Timber.d("floatingCandidateNextItem (after update): ${listAdapter.getHighlightedItem()} [$itemsToShow]")
        }
    }

    private fun setTabletKeyListeners(
        mainView: MainLayoutBinding
    ) {
        mainView.tabletView.apply {
            applyKeyboardTheme(
                themeMode = keyboardThemeMode ?: "default",
                currentNightMode = currentNightMode,
                isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                customBgColor = customThemeBgColor ?: Color.WHITE,
                customKeyColor = customThemeKeyColor ?: Color.WHITE,
                customSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY,
                customKeyTextColor = customThemeKeyTextColor ?: Color.BLACK,
                customSpecialKeyTextColor = customThemeSpecialKeyTextColor ?: Color.BLACK,
                liquidGlassEnable = liquidGlassThemePreference ?: false,
                customBorderEnable = customKeyBorderEnablePreference ?: false,
                customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                borderWidth = customKeyBorderWidth ?: 1
            )
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
                    if (isHenkan.get()) {
                        handleDeleteKeyInHenkan(suggestions, insertString)
                    } else {
                        handleLeftCursor(gestureType, insertString)
                    }
                }
                onLeftKeyLongPressUp.set(true)
                leftCursorKeyLongKeyPressed.set(false)
                leftLongPressJob?.cancel()
                leftLongPressJob = null
            }

            Key.SideKeyCursorRight -> {
                if (!rightCursorKeyLongKeyPressed.get()) {
                    if (isHenkan.get()) {
                        if (bunsetsuSeparation == true) {
                            handleJapaneseModeSpaceKey(
                                mainView, suggestions, insertString
                            )
                        } else {
                            handleJapaneseModeSpaceKey(
                                mainView, suggestions, insertString
                            )
                        }
                    } else {
                        actionInRightKeyPressed(gestureType, insertString)
                    }
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
                } else {
                    if (gestureType == GestureType.FlickLeft && isDeleteLeftFlickPreference == true) {
                        deleteWordOrSymbolsBeforeCursor(insertString)
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
                        if (gestureType == GestureType.FlickLeft) {
                            val isHankaku = hankakuPreference == true
                            if (isHankaku) {
                                handleSpaceKeyClick(false, insertString, suggestions, mainView)
                            } else {
                                handleSpaceKeyClick(true, insertString, suggestions, mainView)
                            }
                        } else {
                            val isHankaku = hankakuPreference == true
                            handleSpaceKeyClick(isHankaku, insertString, suggestions, mainView)
                        }
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

    private fun handleTapAndFlickFloating(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
        suggestions: List<Candidate>,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
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
                    handleNonEmptyInputEnterKeyFloating(
                        suggestions, floatingKeyboardLayoutBinding, insertString
                    )
                } else {
                    handleEmptyInputEnterKeyFloating(floatingKeyboardLayoutBinding)
                }
            }

            Key.KeyDakutenSmall -> {
                handleDakutenSmallLetterKeyFloating(
                    sb = sb,
                    isFlick = isFlick,
                    char = char,
                    insertString = insertString,
                    floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding,
                    gestureType = gestureType
                )
            }

            Key.SideKeyCursorLeft -> {
                if (!leftCursorKeyLongKeyPressed.get()) {
                    if (isHenkan.get()) {
                        handleDeleteKeyInHenkan(suggestions, insertString)
                    } else {
                        handleLeftCursor(gestureType, insertString)
                    }
                }
                onLeftKeyLongPressUp.set(true)
                leftCursorKeyLongKeyPressed.set(false)
                leftLongPressJob?.cancel()
                leftLongPressJob = null
            }

            Key.SideKeyCursorRight -> {
                if (!rightCursorKeyLongKeyPressed.get()) {
                    if (isHenkan.get()) {
                        handleJapaneseModeSpaceKeyFloating(
                            floatingKeyboardLayoutBinding, suggestions, insertString
                        )
                    } else {
                        actionInRightKeyPressed(gestureType, insertString)
                    }
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
                } else {
                    if (gestureType == GestureType.FlickLeft && isDeleteLeftFlickPreference == true) {
                        deleteWordOrSymbolsBeforeCursor(insertString)
                    }
                }
                stopDeleteLongPress()
            }

            Key.SideKeyInputMode -> {
                setTenkeyIconsInHenkanFloating(insertString, floatingKeyboardLayoutBinding)
            }

            Key.SideKeyPreviousChar -> {
                floatingKeyboardLayoutBinding.keyboardViewFloating.let {
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
                        if (gestureType == GestureType.FlickLeft) {
                            val isHankaku = hankakuPreference == true
                            if (isHankaku) {
                                handleSpaceKeyClickFloating(
                                    false, insertString, suggestions, floatingKeyboardLayoutBinding
                                )
                            } else {
                                handleSpaceKeyClickFloating(
                                    true, insertString, suggestions, floatingKeyboardLayoutBinding
                                )
                            }
                        } else {
                            val isHankaku = hankakuPreference == true
                            handleSpaceKeyClickFloating(
                                isHankaku, insertString, suggestions, floatingKeyboardLayoutBinding
                            )
                        }
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
                        handleFlickFloating(char, insertString, sb, floatingKeyboardLayoutBinding)
                    } else {
                        handleTapFloating(char, insertString, sb, floatingKeyboardLayoutBinding)
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
                henkanPressedWithBunsetsuDetect = false
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

    private fun handleFlickFloating(
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        if (isHenkan.get()) {
            suggestionAdapter?.updateHighlightPosition(-1)
            finishComposingText()
            setComposingText("", 0)
            floatingKeyboardLayoutBinding.root.post {
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
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
                henkanPressedWithBunsetsuDetect = false
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

    private fun handleTapFloating(
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        if (isHenkan.get()) {
            suggestionAdapter?.updateHighlightPosition(-1)
            finishComposingText()
            setComposingText("", 0)
            floatingKeyboardLayoutBinding.root.post {
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
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
                if (tenkeyShowIMEButtonPreference == true) {
                    showListPopup()
                }
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

    private fun handleLongPressFloating(
        key: Key
    ) {
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {}
            Key.KeyDakutenSmall -> {
                //showListPopup()
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
                handleSpaceLongActionFloating()
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

            when (keyboardThemeMode) {
                "custom" -> popupView.setDrawableSolidColor(customThemeKeyColor ?: Color.WHITE)
            }

            val listView = popupView.findViewById<ListView>(R.id.popup_listview)
            listView.choiceMode = ListView.CHOICE_MODE_SINGLE

            // --- 1) 行データを構築（内部→外部の順） ---
            val internalOrder = appPreference.keyboard_order
            val internalRows: List<RowItem.Internal> = internalOrder.map { type ->
                val title = when (type) {
                    KeyboardType.TENKEY -> "日本語 - かな"
                    KeyboardType.SUMIRE -> "スミレ入力"
                    KeyboardType.QWERTY -> "英語"
                    KeyboardType.ROMAJI -> "ローマ字入力"
                    KeyboardType.CUSTOM -> "カスタム"
                }
                RowItem.Internal(type = type, title = title)
            }

            val externalRows: List<RowItem.External> = listEnabledImeItems()
                // 任意: 音声入力など除外したい場合は filter を足す
                // .filter { it.packageName != "com.google.android.tts" }
                .map { RowItem.External(it) }

            val rows: List<RowItem> = internalRows + externalRows
            val internalCount = internalRows.size

            Timber.d("Popup rows size=${rows.size}, internalCount=$internalCount")
            Timber.d("get all IME list: [${listEnabledImeItems()}]")

            // --- 2) 2行表示アダプタ ---
            val adapter = object : ArrayAdapter<RowItem>(
                this@IMEService,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                rows
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text1 = view.findViewById<TextView>(android.R.id.text1)

                    when (val item = getItem(position)!!) {
                        is RowItem.Internal -> {
                            text1.text = item.title
                        }

                        is RowItem.External -> {
                            text1.text = item.ime.label
                        }
                    }

                    // --- custom テーマ対応（選択色・文字色） ---
                    if (keyboardThemeMode == "custom") {
                        val baseBg = customThemeKeyColor ?: Color.WHITE
                        val baseText = customThemeKeyTextColor ?: Color.BLACK

                        val listParent = parent as ListView
                        val checked = listParent.isItemChecked(position)

                        if (checked) {
                            val highlightColor =
                                manipulateColor(customThemeSpecialKeyColor ?: Color.LTGRAY, 1.2f)
                            view.setBackgroundColor(highlightColor)
                            text1.setTextColor(customThemeSpecialKeyTextColor ?: baseText)
                        } else {
                            view.setBackgroundColor(baseBg)
                            text1.setTextColor(baseText)
                        }
                    }

                    return view
                }
            }

            listView.adapter = adapter
            limitListViewVisibleItems(listView, maxVisible = 5)

            // --- 3) PopupWindow ---
            keyboardSelectionPopupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )

            // 既存の「内部キーボードの選択状態」を復元（範囲チェック必須）
            if (currentKeyboardOrder in 0 until internalCount) {
                listView.setItemChecked(currentKeyboardOrder, true)
            }

            // --- 4) クリック処理（内部→今まで通り / 外部→IME切替） ---
            listView.setOnItemClickListener { _, _, position, _ ->
                onKeyboardSwitchLongPressUp = false
                keyboardSelectionPopupWindow?.dismiss()

                when (val row = rows[position]) {
                    is RowItem.Internal -> {
                        // 既存挙動：内部キーボード切替
                        currentKeyboardOrder = position
                        if (enableShowLastShownKeyboardInRestart == true) {
                            appPreference.save_last_used_keyboard_position_preference = position
                        }

                        val nextType = row.type
                        when (nextType) {
                            KeyboardType.TENKEY -> {
                                mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                            }

                            KeyboardType.SUMIRE -> {
                                mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                            }

                            KeyboardType.ROMAJI -> {
                                mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                                mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(false)
                            }

                            KeyboardType.QWERTY -> {
                                mainView.keyboardView.setCurrentMode(InputMode.ModeEnglish)
                                mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(false)
                            }

                            KeyboardType.CUSTOM -> { /* 任意 */
                            }
                        }

                        showKeyboard(nextType)
                        setKeyboardSizeSwitchKeyboard(mainView)
                    }

                    is RowItem.External -> {
                        // 外部IMEへ切替（API 28+ 推奨、失敗時はピッカーへ）
                        val imeId = row.ime.id
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 28) {
                                switchInputMethod(imeId)
                            } else {
                                showKeyboardPicker()
                            }
                        }.onFailure {
                            showKeyboardPicker()
                        }
                    }
                }
            }

            keyboardSelectionPopupWindow?.setOnDismissListener {
                onKeyboardSwitchLongPressUp = false
            }

            keyboardSelectionPopupWindow?.showAtLocation(mainView.root, Gravity.CENTER, 0, 0)
        }
    }

    private fun limitListViewVisibleItems(listView: ListView, maxVisible: Int) {
        val adapter = listView.adapter ?: return
        val visibleCount = minOf(maxVisible, adapter.count)
        if (visibleCount <= 0) return

        var totalHeight = 0

        // 各行を実測して合算（simple_list_item_2 等でもOK）
        for (i in 0 until visibleCount) {
            val itemView = adapter.getView(i, null, listView)

            // 幅が未確定でも高さはだいたい測れる。より厳密にしたいなら widthSpec を調整。
            itemView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += itemView.measuredHeight
        }

        val divider = listView.dividerHeight
        totalHeight += divider * (visibleCount - 1)
        totalHeight += listView.paddingTop + listView.paddingBottom

        listView.layoutParams = listView.layoutParams.apply {
            height = totalHeight
        }

        // スクロールバーを出したい場合（任意）
        listView.isVerticalScrollBarEnabled = true
    }

    private fun InputMethodService.listEnabledImeItems(): List<ImeItem> {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pm = packageManager

        return imm.enabledInputMethodList
            .asSequence()
            // 自分自身を除外（任意）
            .filter { it.packageName != packageName }
            .map { imi: InputMethodInfo ->
                ImeItem(
                    id = imi.id,
                    packageName = imi.packageName,
                    settingsActivity = imi.settingsActivity, // null のIMEもある
                    label = imi.loadLabel(pm)
                )
            }
            .sortedBy { it.label.toString() }
            .toList()
    }

    /**
     * 色の明るさを調整するヘルパー関数
     * @param color 元の色
     * @param factor 1.0より大＝明るく、1.0より小＝暗く
     */
    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }


    private fun showUserTemplateListPopup() {
        onKeyboardSwitchLongPressUp = true
        if (inputString.value.isNotEmpty()) return

        mainLayoutBinding?.let { mainView ->
            ioScope.launch {
                val templates = userTemplateRepository.allTemplatesSuspend()
                val templateNames = templates.map { it.word }

                withContext(Dispatchers.Main) {
                    val inflater =
                        getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val popupView =
                        inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
                    val listView = popupView.findViewById<ListView>(R.id.popup_listview)

                    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

                    val adapter = ArrayAdapter(
                        this@IMEService, R.layout.list_item_layout, templateNames
                    )
                    listView.adapter = adapter

                    keyboardSelectionPopupWindow = PopupWindow(
                        popupView,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        true // Focusable
                    )

                    listView.setOnItemClickListener { _, _, position, _ ->
                        val selectedTemplate = templates[position]
                        val textToCommit = selectedTemplate.word
                        commitText(textToCommit, 1)

                        keyboardSelectionPopupWindow?.dismiss()
                    }

                    keyboardSelectionPopupWindow?.setOnDismissListener {
                        onKeyboardSwitchLongPressUp = false
                    }

                    keyboardSelectionPopupWindow?.showAsDropDown(mainView.shortcutToolbarRecyclerview)
                }
            }
        }
    }

    private fun showCurrentDateListPopup() {
        onKeyboardSwitchLongPressUp = true
        if (inputString.value.isNotEmpty()) return
        val calendar = Calendar.getInstance()
        mainLayoutBinding?.let { mainView ->
            ioScope.launch {
                val currentDates = createDateStrings(calendar)

                withContext(Dispatchers.Main) {
                    val inflater =
                        getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val popupView =
                        inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
                    val listView = popupView.findViewById<ListView>(R.id.popup_listview)

                    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

                    val adapter = ArrayAdapter(
                        this@IMEService, R.layout.list_item_layout, currentDates
                    )
                    listView.adapter = adapter

                    keyboardSelectionPopupWindow = PopupWindow(
                        popupView,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        true // Focusable
                    )

                    listView.setOnItemClickListener { _, _, position, _ ->
                        val selectedDates = currentDates[position]
                        commitText(selectedDates, 1)

                        keyboardSelectionPopupWindow?.dismiss()
                    }

                    keyboardSelectionPopupWindow?.setOnDismissListener {
                        onKeyboardSwitchLongPressUp = false
                    }

                    keyboardSelectionPopupWindow?.showAsDropDown(mainView.shortcutToolbarRecyclerview)
                }
            }
        }
    }

    private fun createDateStrings(calendar: Calendar): List<String> {
        val formatter1 = SimpleDateFormat("M/d", Locale.getDefault())
        val formatter2 = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val formatter3 = SimpleDateFormat("M月d日(EEE)", Locale.getDefault())
        val formatterReiwa =
            "令和${calendar.get(Calendar.YEAR) - 2018}年${calendar.get(Calendar.MONTH) + 1}月${
                calendar.get(Calendar.DAY_OF_MONTH)
            }日"
        val formatterR06 = "R${calendar.get(Calendar.YEAR) - 2018}/${
            String.format(
                Locale.getDefault(), "%02d", calendar.get(Calendar.MONTH) + 1
            )
        }/${String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

        // Candidateオブジェクトを作成せず、文字列を直接リストにして返す
        return listOf(
            formatter1.format(calendar.time),  // M/d
            formatter2.format(calendar.time),  // yyyy/MM/dd
            formatter3.format(calendar.time),  // M月d日(EEE)
            formatterReiwa,                    // 令和n年M月d日
            formatterR06,                      // Rxx/MM/dd
            dayOfWeek                          // EEEE (曜日)
        )
    }

    private fun handleDeleteLongPress() {
        if (isHenkan.get()) {
            cancelHenkanByLongPressDeleteKey()
            hasConvertedKatakana = isLiveConversionEnable == true
            bunsetusMultipleDetect = false
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
            if (zenzEnableLongPressConversionPreference == true) {
                scope.launch {
                    filteredCandidateList = suggestionAdapter?.suggestions
                    val candidates = performZenzRequest(insertString)
                    _zenzCandidates.update { candidates }
                }
                isSpaceKeyLongPressed = true
            } else {
                if (conversionKeySwipePreference == true) {
                    if (!isHenkan.get()) {
                        _cursorMoveMode.update { true }
                        isSpaceKeyLongPressed = true
                    }
                } else {
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
                }
            }

        } else {
            _cursorMoveMode.update { true }
            isSpaceKeyLongPressed = true
        }
        Timber.d("SideKeySpace LongPress after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    private fun handleSpaceLongActionSumire() {
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

    private fun handleSpaceLongActionFloating() {
        Timber.d("SideKeySpace LongPress Floting: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        val insertString = inputString.value
        if (insertString.isNotEmpty()) {
            floatingKeyboardBinding?.let {
                if (it.keyboardViewFloating.currentInputMode.value == InputMode.ModeJapanese) {
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
        Timber.d("SideKeySpace LongPress Floating after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
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
        hideAllKeyboards()
        Timber.d("showKeyboard called: $type")
        mainLayoutBinding?.apply {
            when (type) {
                KeyboardType.TENKEY -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        if (isTablet == true && tabletGojuonLayoutPreference == true) {
                            tabletView.isVisible = true
                            tabletView.resetLayout()
                            keyboardView.isVisible = false
                        } else {
                            keyboardView.isVisible = true
                            tabletView.isVisible = false
                        }
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeNumber)
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
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
                        val qwertyEnterKeyText = currentInputType.getQWERTYReturnTextInEn()
                        qwertyView.resetQWERTYKeyboard(qwertyEnterKeyText)
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeNumber)
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.ROMAJI -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        qwertyView.isVisible = true
                        keyboardView.isVisible = false
                        customLayoutDefault.isVisible = false
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTYRomaji }
                        keyboardView.setCurrentMode(InputMode.ModeJapanese)
                        val qwertyEnterKeyText = currentInputType.getQWERTYReturnTextInJp()
                        qwertyView.setRomajiKeyboard(
                            qwertyEnterKeyText
                        )
                        qwertyView.setRomajiEnglishSwitchKeyVisibility(true)
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        keyboardView.setCurrentMode(InputMode.ModeNumber)
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.SUMIRE -> {
                    Timber.d("showKeyboard keyboard: ${this.keyboardView.currentInputMode.value} [$customKeyboardMode]")
                    if (sumireEnglishQwertyPreference == true && customKeyboardMode == KeyboardInputMode.ENGLISH) {
                        if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            qwertyView.setSwitchNumberLayoutKeyVisibility(true)
                            qwertyView.setRomajiMode(false)
                            setKeyboardSizeSwitchKeyboard(this)
                            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Sumire
                            qwertyView.isVisible = true
                            customLayoutDefault.isVisible = false
                            keyboardView.isVisible = false
                        } else {
                            customLayoutDefault.isVisible = true
                            customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                            qwertyView.isVisible = false
                            keyboardView.isVisible = false

                            customKeyboardMode = when (keyboardView.currentInputMode.value) {
                                InputMode.ModeJapanese -> {
                                    KeyboardInputMode.HIRAGANA
                                }

                                InputMode.ModeEnglish -> {
                                    KeyboardInputMode.ENGLISH
                                }

                                InputMode.ModeNumber -> {
                                    KeyboardInputMode.SYMBOLS
                                }
                            }
                        }
                    } else {
                        customLayoutDefault.isVisible = true
                        if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                            currentEnterKeyIndex = currentInputType.getEnterKeyIndexSumire()
                            val hiraganaLayout = KeyboardDefaultLayouts.createFinalLayout(
                                mode = customKeyboardMode,
                                dynamicKeyStates = mapOf(
                                    "enter_key" to currentEnterKeyIndex,
                                    "dakuten_toggle_key" to currentDakutenKeyIndex,
                                    "katakana_toggle_key" to currentKatakanaKeyIndex,
                                    "space_convert_key" to currentSpaceKeyIndex,
                                ),
                                inputLayoutType = sumireInputKeyLayoutType ?: "toggle",
                                inputStyle = sumireInputStyle ?: "default",
                                isDeleteFlickEnabled = isDeleteLeftFlickPreference ?: true
                            )
                            customLayoutDefault.setKeyboard(hiraganaLayout)
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                        } else {
                            customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        }
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false

                        customKeyboardMode = when (keyboardView.currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                KeyboardInputMode.HIRAGANA
                            }

                            InputMode.ModeEnglish -> {
                                KeyboardInputMode.ENGLISH
                            }

                            InputMode.ModeNumber -> {
                                KeyboardInputMode.SYMBOLS
                            }
                        }
                    }
                }

                KeyboardType.CUSTOM -> {
                    Timber.d("updateKeyboardLayout CUSTOM: $isFlickOnlyMode $sumireInputKeyType")
                    setInitialKeyboardTab()
                    setKeyboardTab(0)
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                    } else {
                        customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                        //_tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
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

    private fun updateKeyboardLayout() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value} $currentEnterKeyIndex")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {
                //setInitialKeyboardTab()
            }

            TenKeyQWERTYMode.Default -> {}
            TenKeyQWERTYMode.TenKeyQWERTY -> {}
            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {}
            TenKeyQWERTYMode.Sumire -> {
                Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")

                mainLayoutBinding?.customLayoutDefault?.apply {
                    updateDynamicKey(
                        keyId = "enter_key", stateIndex = currentEnterKeyIndex
                    )
                    updateDynamicKey(
                        keyId = "dakuten_toggle_key", stateIndex = currentDakutenKeyIndex
                    )
                    updateDynamicKey(
                        keyId = "space_convert_key", stateIndex = currentSpaceKeyIndex
                    )
                    updateDynamicKey(
                        keyId = "katakana_toggle_key", stateIndex = currentKatakanaKeyIndex
                    )
                }
            }

            TenKeyQWERTYMode.Number -> {

            }

        }
    }

    private fun createNewKeyboardLayoutForSumire() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value} $currentEnterKeyIndex")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {
                when (customKeyboardMode) {
                    KeyboardInputMode.HIRAGANA -> {
                        mainLayoutBinding?.let { mainView ->
                            setInitialKeyboardTab()
                            setKeyboardTab(0)
                            mainView.customLayoutDefault.isVisible = true
                            mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                            mainView.qwertyView.isVisible = false
                            mainView.keyboardView.isVisible = false
                        }
                    }

                    KeyboardInputMode.ENGLISH -> {
                        val insertString = inputString.value
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                        mainLayoutBinding?.let { mainView ->
                            mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(true)
                            mainView.qwertyView.setRomajiMode(false)
                            if (insertString.isEmpty()) {
                                setKeyboardSizeSwitchKeyboard(mainView)
                            } else {
                                setKeyboardHeightWithAdditional(mainView)
                            }
                            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Custom
                        }
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        mainLayoutBinding?.customLayoutDefault?.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
                    }

                }
            }

            TenKeyQWERTYMode.Default -> {}
            TenKeyQWERTYMode.TenKeyQWERTY -> {}
            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {}
            TenKeyQWERTYMode.Sumire -> {
                when (customKeyboardMode) {
                    KeyboardInputMode.HIRAGANA -> {
                        val dynamicStates = mapOf(
                            "enter_key" to currentEnterKeyIndex,
                            "dakuten_toggle_key" to currentDakutenKeyIndex,
                            "space_convert_key" to currentSpaceKeyIndex,
                            "katakana_toggle_key" to currentKatakanaKeyIndex
                        )

                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")

                        val finalLayout = KeyboardDefaultLayouts.createFinalLayout(
                            mode = customKeyboardMode,
                            dynamicKeyStates = dynamicStates,
                            inputLayoutType = sumireInputKeyLayoutType ?: "toggle",
                            inputStyle = sumireInputStyle ?: "default",
                            isDeleteFlickEnabled = isDeleteLeftFlickPreference ?: true
                        )
                        mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
                    }

                    KeyboardInputMode.ENGLISH -> {
                        if (sumireEnglishQwertyPreference == true) {
                            val insertString = inputString.value
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            mainLayoutBinding?.let { mainView ->
                                mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(true)
                                mainView.qwertyView.setRomajiMode(false)
                                if (insertString.isEmpty()) {
                                    setKeyboardSizeSwitchKeyboard(mainView)
                                } else {
                                    setKeyboardHeightWithAdditional(mainView)
                                }
                                previousTenKeyQWERTYMode = TenKeyQWERTYMode.Sumire
                            }
                        } else {
                            val dynamicStates = mapOf(
                                "enter_key" to currentEnterKeyIndex,
                                "dakuten_toggle_key" to currentDakutenKeyIndex,
                                "space_convert_key" to currentSpaceKeyIndex,
                                "katakana_toggle_key" to currentKatakanaKeyIndex
                            )

                            Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")

                            val finalLayout = KeyboardDefaultLayouts.createFinalLayout(
                                mode = customKeyboardMode,
                                dynamicKeyStates = dynamicStates,
                                inputLayoutType = sumireInputKeyLayoutType ?: "toggle",
                                inputStyle = sumireInputStyle ?: "default",
                                isDeleteFlickEnabled = isDeleteLeftFlickPreference ?: true
                            )
                            mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
                        }
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        val dynamicStates = mapOf(
                            "enter_key" to currentEnterKeyIndex,
                            "dakuten_toggle_key" to currentDakutenKeyIndex,
                            "space_convert_key" to currentSpaceKeyIndex,
                            "katakana_toggle_key" to currentKatakanaKeyIndex
                        )

                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")

                        val finalLayout = KeyboardDefaultLayouts.createFinalLayout(
                            mode = customKeyboardMode,
                            dynamicKeyStates = dynamicStates,
                            inputLayoutType = sumireInputKeyLayoutType ?: "toggle",
                            inputStyle = sumireInputStyle ?: "default",
                            isDeleteFlickEnabled = isDeleteLeftFlickPreference ?: true
                        )
                        mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
                    }
                }
            }

            TenKeyQWERTYMode.Number -> {

            }

        }
    }

    private var isCustomLayoutRomajiMode = false

    private fun setInitialKeyboardTab() {
        Timber.d("setInitialKeyboardTab")
        scope.launch(Dispatchers.IO) {
            if (customLayouts.isEmpty()) {
                return@launch
            }
            val id = customLayouts[0].layoutId
            val dbLayout = keyboardRepository.getFullLayout(id).first()
            val finalLayout = keyboardRepository.convertLayout(dbLayout)

            isCustomLayoutRomajiMode = finalLayout.isRomaji
            withContext(Dispatchers.Main) {
                mainLayoutBinding?.customLayoutDefault?.setKeyboard(finalLayout)
            }
        }
    }

    private fun setKeyboardTab(pos: Int) {
        currentCustomKeyboardPosition = pos
        scope.launch(Dispatchers.IO) {
            if (customLayouts.isEmpty()) {
                Timber.d("setKeyboardTab: customLayouts.isEmpty()")
                if (customLayouts.isNotEmpty()) {
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
        mainLayoutBinding?.customLayoutDefault?.apply {
            updateDynamicKey(
                keyId = "dakuten_toggle_key", stateIndex = 1
            )
        }
    }

    private fun setSumireKeyboardEnterKey(index: Int) {
        currentEnterKeyIndex = index
        mainLayoutBinding?.customLayoutDefault?.apply {
            updateDynamicKey(
                keyId = "enter_key", stateIndex = index
            )
        }
    }

    private fun setSumireKeyboardSpaceKey(index: Int) {
        currentSpaceKeyIndex = index
        mainLayoutBinding?.customLayoutDefault?.apply {
            updateDynamicKey(
                keyId = "space_convert_key", stateIndex = index
            )
        }
    }

    private fun setSumireKeyboardSwitchNumberAndKatakanaKey(index: Int) {
        currentKatakanaKeyIndex = index
        mainLayoutBinding?.customLayoutDefault?.apply {
            updateDynamicKey(
                keyId = "katakana_toggle_key", stateIndex = index
            )
        }
    }

    private fun resetSumireKeyboardDakutenMode() {
        currentDakutenKeyIndex = 0
        currentEnterKeyIndex = currentInputType.getEnterKeyIndexSumire()
        currentSpaceKeyIndex = 0
        Timber.d("resetSumireKeyboardDakutenMode called: $currentEnterKeyIndex")
        mainLayoutBinding?.customLayoutDefault?.apply {
            updateDynamicKey(
                keyId = "enter_key", stateIndex = currentEnterKeyIndex
            )
            updateDynamicKey(
                keyId = "dakuten_toggle_key", stateIndex = 0
            )
            updateDynamicKey(
                keyId = "space_convert_key", stateIndex = 0
            )
        }
    }

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun launchSettingsActivity(navigationRequest: String) {
        // Create the Intent to launch your MainActivity
        val intent = Intent(this, MainActivity::class.java)

        // Add the flag required to start an Activity from a Service
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Add the specific request as an extra
        intent.putExtra("openSettingActivity", navigationRequest)

        // Start the activity
        startActivity(intent)
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
        mainView.customLayoutDefault.applyKeyboardTheme(
            themeMode = keyboardThemeMode ?: "default",
            currentNightMode = currentNightMode,
            isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
            customBgColor = customThemeBgColor ?: Color.WHITE,
            customKeyColor = customThemeKeyColor ?: Color.WHITE,
            customSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY,
            customKeyTextColor = customThemeKeyTextColor ?: Color.BLACK,
            customSpecialKeyTextColor = customThemeSpecialKeyTextColor ?: Color.BLACK,
            liquidGlassEnable = liquidGlassThemePreference ?: false,
            customBorderEnable = customKeyBorderEnablePreference ?: false,
            customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
            liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
            borderWidth = customKeyBorderWidth ?: 1
        )

        mainView.customLayoutDefault.setAngleAndRange(
            appPreference.getCircularFlickRanges(),
            circularFlickWindowScale ?: 1.0f
        )

        mainView.customLayoutDefault.setOnKeyboardActionListener(object :
            com.kazumaproject.custom_keyboard.view.FlickKeyboardView.OnKeyboardActionListener {

            override fun onKey(text: String, isFlick: Boolean) {
                // 通常の文字が入力された場合（変更なし）
                clearDeleteBufferWithView()
                Timber.d("onKey: [$text] [${qwertyMode.value}] [$isDefaultRomajiHenkanMap]")
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
                                    if (isDefaultRomajiHenkanMap) {
                                        _inputString.update {
                                            converter.convertCustomLayout(sb.toString())
                                        }
                                    } else {
                                        _inputString.update {
                                            converter.convert(sb.toString())
                                        }
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
                                        if (isDefaultRomajiHenkanMap) {
                                            _inputString.update {
                                                converter.convertCustomLayout(sb.toString())
                                            }
                                        } else {
                                            _inputString.update {
                                                converter.convert(sb.toString())
                                            }
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
                        Timber.d("TenKeyQWERTYMode.Sumire: $text $isFlick")
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
                        val insertString = inputString.value
                        if (insertString.isEmpty()) {
                            mainView.customLayoutDefault.setCursorMode(true)
                        } else {
                            if (zenzEnableLongPressConversionPreference == true) {
                                scope.launch {
                                    filteredCandidateList = suggestionAdapter?.suggestions
                                    val candidates = performZenzRequest(insertString)
                                    _zenzCandidates.update { candidates }
                                }
                            } else {
                                if (conversionKeySwipePreference == true) {
                                    if (!isHenkan.get()) {
                                        mainView.customLayoutDefault.setCursorMode(true)
                                    }
                                } else {
                                    handleSpaceLongActionSumire()
                                }
                            }
                        }
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
                            val insertString = inputString.value
                            Timber.d("InputText: emoji: $insertString")
                            if (insertString.isNotEmpty()) {
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            } else {
                                _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                                stringInTail.set("")
                                finishComposingText()
                                setComposingText("", 0)
                            }
                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                            } else {
                                clearDeletedBuffer()
                            }
                        }
                        suggestionAdapter?.setUndoEnabled(false)
                        updateClipboardPreview()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            if (isHenkan.get()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                            } else {
                                clearDeletedBuffer()
                            }
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
                    KeyAction.SwitchToEnglishLayout -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeEnglish
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.SwitchToKanaLayout -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeJapanese
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.SwitchToNumberLayout -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeNumber
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {}
                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {}
                }
            }

            override fun onActionUpAfterLongPress(action: KeyAction) {
                Timber.d("onActionUpAfterLongPress: $action")
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
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {}
                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {
                        stopDeleteLongPress()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.VoiceInput -> {}
                }
            }

            override fun onFlickDirectionChanged(direction: FlickDirection) {
                vibrate()
                Timber.d("onFlickDirectionChanged: $direction")
            }

            override fun onFlickActionLongPress(action: KeyAction) {
                Timber.d("onFlickActionLongPress: $action")
                vibrate()
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert -> {
                        if (zenzEnableLongPressConversionPreference == true) {
                            val insertString = inputString.value
                            scope.launch {
                                filteredCandidateList = suggestionAdapter?.suggestions
                                val candidates = performZenzRequest(insertString)
                                _zenzCandidates.update { candidates }
                            }
                        } else {
                            if (conversionKeySwipePreference == true) {
                                if (!isHenkan.get()) {
                                    mainView.customLayoutDefault.setCursorMode(true)
                                }
                            } else {
                                handleSpaceLongActionSumire()
                            }
                        }
                    }

                    KeyAction.Copy -> {
                        val selectedText = getSelectedText(0)
                        if (!selectedText.isNullOrEmpty()) {
                            clipboardUtil.setClipBoard(selectedText.toString())
                            suggestionAdapter?.apply {
                                if (clipboardPreviewVisibility == true) {
                                    setPasteEnabled(true)
                                    setClipboardPreview(selectedText.toString())
                                } else {
                                    setPasteEnabled(false)
                                }
                            }
                            appPreference.last_pasted_clipboard_text_preference = ""
                        }
                    }

                    KeyAction.Delete -> {
                        handleDeleteLongPress()
                    }

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
                    KeyAction.Space -> {
                        mainView.customLayoutDefault.setCursorMode(true)
                    }

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {

                    }

                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {}
                }
            }

            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
                vibrate()
                Timber.d("onFlickActionUpAfterLongPress: $action $isFlick")
                when (action) {
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Copy -> {}
                    KeyAction.Delete -> {
                        stopDeleteLongPress()
                    }

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
                    KeyAction.Convert, KeyAction.Space -> {
                        mainView.customLayoutDefault.setCursorMode(false)
                        isSpaceKeyLongPressed = false
                        if (inputString.value.isEmpty()) {
                            val isHankaku = hankakuPreference == true
                            val insertString = inputString.value
                            val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                            if (isHankaku) {
                                if (isFlick) {
                                    handleSpaceKeyClick(false, insertString, suggestions, mainView)
                                } else {
                                    handleSpaceKeyClick(true, insertString, suggestions, mainView)
                                }
                            } else {
                                if (isFlick) {
                                    handleSpaceKeyClick(true, insertString, suggestions, mainView)
                                } else {
                                    handleSpaceKeyClick(false, insertString, suggestions, mainView)
                                }
                            }
                        }
                    }

                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {
                        if (isDeleteLeftFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsBeforeCursor(insertString)
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.MoveCursorDown -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.MoveCursorUp -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.Cancel -> {
                        stopDeleteLongPress()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.VoiceInput -> {}
                }
            }

            override fun onAction(action: KeyAction, view: View, isFlick: Boolean) {
                vibrate()

                Timber.d("onAction: $action $isFlick")
                if (action != KeyAction.Delete) {
                    clearDeleteBufferWithView()
                }
                when (action) {
                    is KeyAction.InputText -> {
                        when (action.text) {
                            "^_^" -> {
                                val insertString = inputString.value
                                Timber.d("InputText: emoji: $insertString")
                                if (insertString.isNotEmpty()) {
                                    val sb = StringBuilder()
                                    val c = insertString.last()
                                    c.getDakutenFlickTop()?.let { dakutenChar ->
                                        setStringBuilderForConvertStringInHiragana(
                                            dakutenChar, sb, insertString
                                        )
                                    }
                                } else {
                                    _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                                    stringInTail.set("")
                                    finishComposingText()
                                    setComposingText("", 0)
                                }
                            }

                            ":", "-" -> {
                                val insertString = inputString.value
                                val sb = StringBuilder()
                                sb.append(insertString).append(action.text)
                                _inputString.update {
                                    sb.toString()
                                }
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
                        createNewKeyboardLayoutForSumire()

                        val inputMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                            KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                            KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
                        }
                        if (isTablet == true) {
                            mainView.tabletView.currentInputMode.set(inputMode)
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
                                val isHankaku = hankakuPreference == true
                                if (isHankaku) {
                                    if (isFlick) {
                                        handleSpaceKeyClick(
                                            false, insertString, suggestions, mainView
                                        )
                                    } else {
                                        handleSpaceKeyClick(
                                            true, insertString, suggestions, mainView
                                        )
                                    }
                                } else {
                                    if (isFlick) {
                                        handleSpaceKeyClick(
                                            true, insertString, suggestions, mainView
                                        )
                                    } else {
                                        handleSpaceKeyClick(
                                            false, insertString, suggestions, mainView
                                        )
                                    }
                                }
                            }
                        }
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (!leftCursorKeyLongKeyPressed.get()) {
                            if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                            } else {
                                handleLeftCursor(GestureType.Tap, insertString)
                            }
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (!rightCursorKeyLongKeyPressed.get()) {
                            if (isHenkan.get()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                            } else {
                                actionInRightKeyPressed(GestureType.Tap, insertString)
                            }
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
                                if (clipboardPreviewVisibility == true) {
                                    setPasteEnabled(true)
                                    setClipboardPreview(selectedText.toString())
                                    appPreference.last_pasted_clipboard_text_preference = ""
                                } else {
                                    setPasteEnabled(false)
                                }
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
                    KeyAction.ShowEmojiKeyboard -> {
                        _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                        stringInTail.set("")
                        finishComposingText()
                        setComposingText("", 0)
                        _inputString.update { "" }
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.SwitchToEnglishLayout -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeEnglish
                        if (isTablet == true) {
                            mainView.tabletView.currentInputMode.set(inputMode)
                        }
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.SwitchToKanaLayout -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeJapanese
                        if (isTablet == true) {
                            mainView.tabletView.currentInputMode.set(inputMode)
                        }
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.SwitchToNumberLayout -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeNumber
                        mainView.keyboardView.setCurrentMode(inputMode)
                    }

                    KeyAction.ShiftKey -> {
                        isCustomLayoutRomajiMode = !isCustomLayoutRomajiMode
                        if (view is AppCompatImageButton) {
                            view.setImageDrawable(
                                ContextCompat.getDrawable(
                                    this@IMEService,
                                    if (isCustomLayoutRomajiMode) com.kazumaproject.core.R.drawable.shift_24px else com.kazumaproject.core.R.drawable.shift_fill_24px
                                )
                            )
                        }
                    }

                    KeyAction.MoveCustomKeyboardTab -> {
                        scope.launch {
                            if (customLayouts.isNotEmpty()) {
                                val position =
                                    (currentCustomKeyboardPosition + 1) % customLayouts.size
                                setKeyboardTab(position)
                            }
                        }
                    }

                    KeyAction.ToggleKatakana -> {
                        when (countToggleKatakana) {
                            0 -> {
                                _inputString.update {
                                    it.hiraganaToKatakana()
                                }
                                countToggleKatakana++
                            }

                            1 -> {
                                _inputString.update {
                                    it.toHankakuKatakana()
                                }
                                countToggleKatakana++
                            }

                            2 -> {
                                _inputString.update {
                                    it.toHiragana()
                                }
                                countToggleKatakana = 0
                            }
                        }
                    }

                    KeyAction.DeleteUntilSymbol -> {
                        if (isDeleteLeftFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsBeforeCursor(insertString)
                        }
                    }

                    KeyAction.MoveCursorDown -> {
                        val insertString = inputString.value
                        if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        val insertString = inputString.value
                        if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                        }
                    }

                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {
                        startVoiceInput(mainView)
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
        if (text.isNotEmpty()) {
            if (text.length == 1) {
                text.first().let {
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
            } else {
                sb.append(insertString).append(text)
                _inputString.update {
                    sb.toString()
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
                if (clipboardPreviewVisibility == true) {
                    setPasteEnabled(true)
                    setClipboardPreview(selectedText.toString())
                    appPreference.last_pasted_clipboard_text_preference = ""
                } else {
                    setPasteEnabled(false)
                }
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
                    appPreference.last_pasted_clipboard_text_preference = item.text
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
                if (clipboardPreviewVisibility == true) {
                    setPasteEnabled(true)
                    setClipboardPreview(selectedText.toString())
                    appPreference.last_pasted_clipboard_text_preference = ""
                } else {
                    setPasteEnabled(false)
                }
            }
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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
        Timber.d("SuggestionAdapter Clipboard: updateClipboardPreview")
        suggestionAdapter?.apply {
            when (val item = clipboardUtil.getPrimaryClipContent()) {
                is ClipboardItem.Image -> {
                    if (clipboardPreviewVisibility == true) {
                        if (clipboardPreviewTapToDelete != true) {
                            setPasteEnabled(true)
                            setClipboardImagePreview(item.bitmap)
                        } else {
                            setPasteEnabled(false)
                        }
                    } else {
                        setPasteEnabled(false)
                    }
                }

                is ClipboardItem.Text -> {
                    if (clipboardPreviewVisibility == true) {
                        if (clipboardPreviewTapToDelete != true) {
                            setPasteEnabled(true)
                            if (appPreference.last_pasted_clipboard_text_preference != item.text) {
                                setClipboardPreview(item.text)
                            }
                        }
                    } else {
                        setPasteEnabled(false)
                    }
                }

                is ClipboardItem.Empty -> {
                    setPasteEnabled(false)
                    setClipboardPreview("")
                }
            }
        }
    }

    private fun dakutenSmallActionForSumire() {
        val insertString = inputString.value
        val sb = StringBuilder()
        if (insertString.isNotEmpty()) {
            if (insertString.last().isLatinAlphabet()) {
                smallConversionEnglish(sb, insertString)
            } else if (insertString.last().isHiragana()) {
                dakutenSmallLetter(
                    sb, insertString, GestureType.Tap
                )
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

    private fun resetKeyboard() {
        Timber.d("resetKeyboard called for showKeyboard")
        if (keyboardOrder.isEmpty()) return
        if (enableShowLastShownKeyboardInRestart == true) {
            showKeyboard(keyboardOrder[lastSavedKeyboardPosition ?: 0])
        } else {
            currentKeyboardOrder = 0
            showKeyboard(keyboardOrder[0])
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
        henkanPressedWithBunsetsuDetect = false

        val spannableString = if (insertString.length == selectedSuggestion?.length?.toInt()) {
            SpannableString(insertString + stringInTail)
        } else {
            stringInTail.set("")
            SpannableString(insertString)
        }
        setComposingTextAfterEdit(
            inputString = insertString,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            scrollToPosition(0)
        }
    }

    @OptIn(FlowPreview::class)
    private fun startScope(mainView: MainLayoutBinding) = scope.launch {
        launch {
            var prevFlag: CandidateShowFlag? = null
            suggestionFlag.collectLatest { currentFlag ->
                val insertString = inputString.value
                Timber.d("suggestionFlag CandidateShowFlag.Idle: [$insertString] [$stringInTail] [$prevFlag] [$currentFlag]")
                if (prevFlag == CandidateShowFlag.Idle && currentFlag == CandidateShowFlag.Updating) {
                    when {
                        physicalKeyboardEnable.replayCache.isEmpty() &&
                                isKeyboardFloatingMode == true || (physicalKeyboardEnable.replayCache.isNotEmpty() &&
                                !physicalKeyboardEnable.replayCache.first()) && isKeyboardFloatingMode == true -> {
                            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                                animateSuggestionImageViewVisibility(
                                    floatingKeyboardLayoutBinding.suggestionVisibility, true
                                )
                            }
                        }

                        physicalKeyboardEnable.replayCache.isEmpty() && (mainView.keyboardView.isVisible ||
                                mainView.tabletView.isVisible || mainView.qwertyView.isVisible ||
                                mainView.customLayoutDefault.isVisible) -> {
                            if (!suppressSuggestions) {
                                animateSuggestionImageViewVisibility(
                                    mainView.suggestionVisibility, true
                                )
                            }
                            setKeyboardHeightWithAdditional(mainView)
                        }

                        (physicalKeyboardEnable.replayCache.isNotEmpty() && !physicalKeyboardEnable.replayCache.first()) &&
                                (mainView.keyboardView.isVisible || mainView.tabletView.isVisible ||
                                        mainView.qwertyView.isVisible || mainView.customLayoutDefault.isVisible) -> {
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, true
                            )
                            setKeyboardHeightWithAdditional(mainView)
                        }

                    }
                    if (isKeyboardFloatingMode == true) {
                        floatingKeyboardBinding?.let { floatingKeyboard ->
                            updateUIinHenkanFloating(floatingKeyboard, insertString)
                        }
                    } else {
                        updateUIinHenkan(mainView, insertString)
                    }
                    setSumireKeyboardSwitchNumberAndKatakanaKey(1)
                    if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                        mainView.qwertyView.apply {
                            setSpaceKeyText("変換")
                            setReturnKeyText("確定")
                        }
                    } else if ((qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && mainView.keyboardView.currentInputMode.value == InputMode.ModeEnglish) || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && mainView.keyboardView.currentInputMode.value == InputMode.ModeEnglish) {
                        mainView.qwertyView.setReturnKeyText("done")
                    }
                    if (mainView.customLayoutDefault.isVisible) {
                        setSumireKeyboardDakutenKey()
                        setSumireKeyboardEnterKey(5)
                        when (mainView.keyboardView.currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                setSumireKeyboardSpaceKey(1)
                            }

                            else -> {}
                        }
                    }
                    if (candidateTabVisibility == true) {
                        mainView.candidateTabLayout.isVisible = true
                    }
                    if (shortcutTollbarVisibility == true) {
                        mainView.shortcutToolbarRecyclerview.isInvisible = true
                    } else {
                        mainView.shortcutToolbarRecyclerview.isVisible = false
                    }
                }
                when (currentFlag) {
                    CandidateShowFlag.Idle -> {
                        suggestionAdapter?.suggestions = emptyList()
                        if (stringInTail.get().isEmpty()) {
                            if (isKeyboardFloatingMode == true) {
                                if (!suppressSuggestions) {
                                    floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                                        animateSuggestionImageViewVisibility(
                                            floatingKeyboardLayoutBinding.suggestionVisibility,
                                            false
                                        )
                                    }
                                }
                            } else {
                                if (mainView.suggestionVisibility.isVisible) {
                                    animateSuggestionImageViewVisibility(
                                        mainView.suggestionVisibility, false
                                    )
                                }
                            }
                            if (mainView.customLayoutDefault.isVisible) {
                                resetSumireKeyboardDakutenMode()
                            }
                            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                                mainView.qwertyView.apply {
                                    setSpaceKeyText("空白")
                                    val qwertyEnterKeyText =
                                        currentInputType.getQWERTYReturnTextInJp()
                                    setReturnKeyText(qwertyEnterKeyText)
                                }
                            } else if ((qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && mainView.keyboardView.currentInputMode.value == InputMode.ModeEnglish) || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && mainView.keyboardView.currentInputMode.value == InputMode.ModeEnglish) {
                                val qwertyEnterKeyText = currentInputType.getQWERTYReturnTextInEn()
                                mainView.qwertyView.setReturnKeyText(qwertyEnterKeyText)
                            }
                            setKeyboardHeightDefault(mainView)
                            setSumireKeyboardSwitchNumberAndKatakanaKey(0)
                            countToggleKatakana = 0
                            mainView.candidateTabLayout.isVisible = false
                            val tab = mainView.candidateTabLayout.getTabAt(0)
                            mainView.candidateTabLayout.selectTab(tab)
                            mainView.shortcutToolbarRecyclerview.isVisible =
                                shortcutTollbarVisibility == true
                        }
                        suggestionAdapter?.apply {
                            if (deletedBuffer.isEmpty()) {
                                Timber.d("SuggestionAdapter Clipboard: from coroutine flow")
                                when (val item = clipboardUtil.getPrimaryClipContent()) {
                                    is ClipboardItem.Image -> {
                                        if (clipboardPreviewVisibility == true) {
                                            setPasteEnabled(true)
                                            setClipboardImagePreview(item.bitmap)
                                        } else {
                                            setPasteEnabled(false)
                                        }
                                    }

                                    is ClipboardItem.Text -> {
                                        if (clipboardPreviewVisibility == true) {
                                            if (clipboardPreviewTapToDelete == true) {
                                                if (appPreference.last_pasted_clipboard_text_preference != item.text) {
                                                    setPasteEnabled(true)
                                                    setClipboardPreview(item.text)
                                                } else {
                                                    setPasteEnabled(false)
                                                }
                                            } else {
                                                setPasteEnabled(true)
                                                setClipboardPreview(item.text)
                                            }
                                        } else {
                                            setPasteEnabled(false)
                                        }
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
                    }

                    CandidateShowFlag.Updating -> {
                        setSuggestionOnView(insertString, mainView)
                    }
                }
                prevFlag = currentFlag
            }
        }

        launch {
            suggestionViewStatus.collectLatest { isVisible ->
                Timber.d("suggestionViewStatus: $isVisible")
                updateSuggestionViewVisibility(mainView, isVisible)
            }
        }

        launch {
            keyboardSymbolViewState.collectLatest { isSymbolKeyboardShow ->
                Timber.d("keyboardSymbolViewState: $isSymbolKeyboardShow")
                setKeyboardSizeSwitchKeyboard(mainView)
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                        setSymbolsFloating(floatingKeyboardLayoutBinding)
                        if (isSymbolKeyboardShow) {
                            floatingKeyboardLayoutBinding.keyboardViewFloating.isVisible = false
                            floatingKeyboardLayoutBinding.floatingSymbolKeyboard.isVisible = true
                        } else {
                            floatingKeyboardLayoutBinding.keyboardViewFloating.isVisible = true
                            floatingKeyboardLayoutBinding.floatingSymbolKeyboard.isVisible = false
                        }
                    }
                } else {
                    setKeyboardSizeForHeightSymbol(mainView, isSymbolKeyboardShow)
                }
                mainView.apply {
                    if (shortcutTollbarVisibility == true) {
                        shortcutToolbarRecyclerview.isVisible = !isSymbolKeyboardShow
                    }
                    if (isSymbolKeyboardShow) {
                        when {
                            customLayoutDefault.isVisible -> {
                                customLayoutDefault.visibility = View.INVISIBLE
                            }

                            tabletView.isVisible && tabletGojuonLayoutPreference == true -> {
                                tabletView.visibility = View.INVISIBLE
                            }

                            tabletView.isVisible && tabletGojuonLayoutPreference != true -> {
                                keyboardView.visibility = View.INVISIBLE
                            }

                            keyboardView.isVisible -> {
                                keyboardView.visibility = View.INVISIBLE
                            }

                            qwertyView.isVisible -> {
                                qwertyView.visibility = View.INVISIBLE
                            }
                        }
                        animateViewVisibility(keyboardSymbolView, true)
                        suggestionRecyclerView.isVisible = false
                        setSymbols(mainView)
                    } else {
                        if (isTablet == true) {
                            when {
                                tabletView.isInvisible -> {
                                    tabletView.isVisible = true
                                }

                                qwertyView.isInvisible -> {
                                    qwertyView.isVisible = true
                                }

                                customLayoutDefault.isInvisible -> {
                                    customLayoutDefault.isVisible = true
                                }
                            }
                        } else {
                            when {
                                keyboardView.isInvisible -> {
                                    keyboardView.isVisible = true
                                }

                                qwertyView.isInvisible -> {
                                    qwertyView.isVisible = true
                                }

                                customLayoutDefault.isInvisible -> {
                                    customLayoutDefault.isVisible = true
                                }
                            }
                        }
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
                floatingKeyboardBinding?.keyboardViewFloating?.setTextToMoveCursorMode(
                    isCursorMoveMode
                )
            }
        }

        launch {
            qwertyMode.collectLatest {
                Timber.d("qwertyMode value: $it")
                when (it) {
                    TenKeyQWERTYMode.Default -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Default, emptyList()
                        )
                        mainView.apply {
                            if (isTablet == true && tabletGojuonLayoutPreference == true) {
                                tabletView.isVisible = true
                                keyboardView.isVisible = false
                            } else {
                                keyboardView.isVisible = true
                                tabletView.isVisible = false
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
                            if (isTablet == true && tabletGojuonLayoutPreference == true) {
                                tabletView.isVisible = false
                            } else {
                                keyboardView.isVisible = false
                            }
                            customLayoutDefault.isVisible = false
                            qwertyView.setRomajiEnglishSwitchKeyVisibility(false)
                            qwertyView.isVisible = true
                        }
                    }

                    TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.TenKeyQWERTY, emptyList()
                        )
                        mainView.apply {
                            if (isTablet == true && tabletGojuonLayoutPreference == true) {
                                tabletView.isVisible = false
                            } else {
                                keyboardView.isVisible = false
                            }
                            qwertyView.isVisible = true
                            customLayoutDefault.isVisible = false
                            if (qwertyShowSwitchRomajiEnglishPreference == true) {
                                qwertyView.setRomajiEnglishSwitchKeyVisibility(true)
                            } else {
                                qwertyView.setRomajiEnglishSwitchKeyVisibility(false)
                            }
                        }
                    }

                    TenKeyQWERTYMode.Custom -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Custom, customLayouts
                        )
                        mainView.apply {
                            if (isTablet == true && tabletGojuonLayoutPreference == true) {
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
                            if (isTablet == true && tabletGojuonLayoutPreference == true) {
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
                            if (isTablet == true && tabletGojuonLayoutPreference == true) {
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
                entity?.let {
                    Pair(it.mapData, it.isDeletable)
                } ?: Pair(romajiMapRepository.getDefaultMapData(), false)
            }.distinctUntilChanged().collectLatest { (activeMapData, isDeletable) ->
                val converterMap = if (!isDeletable) {
                    activeMapData.mapKeys { (key, _) -> key.toZenkaku() }
                } else {
                    activeMapData
                }
                isDefaultRomajiHenkanMap = !isDeletable
                romajiConverter = RomajiKanaConverter(converterMap)
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
            keyboardFloatingMode.collectLatest { isFloatingMode ->
                Timber.d("keyboardFloatingMode state changed: $isFloatingMode")
                applyFloatingModeState(isFloatingMode)
            }
        }

        launch {
            physicalKeyboardEnable.collect { isPhysicalKeyboardEnable ->
                Timber.d("physicalKeyboardEnable: $isPhysicalKeyboardEnable")
                if (isPhysicalKeyboardEnable) {
                    val showInputModeText = when (mainView.keyboardView.currentInputMode.value) {
                        InputMode.ModeJapanese -> "あ"
                        InputMode.ModeEnglish -> "A"
                        InputMode.ModeNumber -> "A"
                    }

                    floatingDockView.setText(showInputModeText)
                    floatingKeyboardView?.dismiss()
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
                                mainView.root, Gravity.BOTTOM, 0, 0
                            )
                        }
                    }

                    listAdapter.updateHighlightPosition(-1)
                    currentHighlightIndex = -1
                    isHenkan.set(false)
                    henkanPressedWithBunsetsuDetect = false
                } else {
                    mainView.root.alpha = 1f
                    requestCursorUpdates(0)
                    setKeyboardSizeForHeightPhysicalKeyboard(mainView)
                    floatingCandidateWindow?.dismiss()
                    floatingDockWindow?.dismiss()
                }
                Timber.d("isPhysicalKeyboardEnable: $isPhysicalKeyboardEnable")
            }
        }

        launch {
            shortCurRepository.enabledShortcutsFlow.collectLatest {
                shortcutAdapter?.submitList(it)
            }
        }

        launch {
            zenzRequest
                .debounce((zenzDebounceTimePreference ?: 300).toLong())
                .collectLatest {
                    val zenzCandidates = performZenzRequest(it)
                    lastLocalUpdatedInput.first { completedInput ->
                        completedInput == it
                    }
                    _zenzCandidates.update { zenzCandidates }
                }
        }

        launch {
            zenzCandidates
                .buffer(kotlinx.coroutines.channels.Channel.CONFLATED)
                .collectLatest { resultFromZenz ->
                    val insertString = inputString.value
                    if (insertString.isNotEmpty()) {
                        if (resultFromZenz.isNotEmpty() &&
                            resultFromZenz.first().originalString == insertString
                        ) {
                            val suggestions = filteredCandidateList ?: emptyList()
                            if (suggestions.isNotEmpty() && suggestions.first().length.toInt() == insertString.length) {
                                val resultFromZenzToCandidate = resultFromZenz.map {
                                    Candidate(
                                        string = it.string,
                                        type = it.type,
                                        length = it.length,
                                        score = it.score
                                    )
                                }

                                suggestionAdapter?.suggestions =
                                    (resultFromZenzToCandidate + (suggestions)).distinctBy { it.string }

                                if (isLiveConversionEnable == true && !hasConvertedKatakana) {
                                    isContinuousTapInputEnabled.set(true)
                                    lastFlickConvertedNextHiragana.set(true)
                                    if (!hasConvertedKatakana) applyFirstSuggestion(
                                        resultFromZenzToCandidate.first()
                                    )
                                }
                            }
                        } else {
                            if (inputString.value.isEmpty()) {
                                suggestionAdapter?.suggestions = emptyList()
                                suggestionAdapterFull?.suggestions = emptyList()
                            }
                        }
                    } else {
                        suggestionAdapter?.suggestions = emptyList()
                        suggestionAdapterFull?.suggestions = emptyList()
                    }
                }
        }

        launch {
            inputString.collectLatest { string ->
                processInputString(string, mainView)
            }
        }
    }


    /**
     * Zenzエンジンを使用して変換候補を生成するサスペンド関数
     * collectLatest 内から呼び出されることを想定しています。
     */
    private suspend fun performZenzRequest(
        insertString: String
    ): List<ZenzCandidate> = withContext(Dispatchers.Default) {

        // 2. バリデーション (ひらがな以外や、1文字以下の場合はスキップなど)
        // ※元のロジック: insertString.length == 1 の場合は emptyList
        if (insertString.length <= 1) {
            return@withContext emptyList()
        }

        if (!insertString.isAllHiraganaWithSymbols()) {
            return@withContext emptyList()
        }

        // 3. 文脈（LeftContext）の取得
        // try-catch で安全に処理
        val leftContext = try {
            withContext(Dispatchers.Main) {
                val lastCandidateLength = if (isLiveConversionEnable == true) {
                    lastCandidate?.length ?: 0
                } else {
                    insertString.length
                }
                Timber.d("getLeftContext: $insertString lastCandidateLength:[$lastCandidateLength] suggestion: [${suggestionAdapter?.suggestions?.firstOrNull()?.string ?: ""}] lastCandidate [$lastCandidate]")
                if (enableZenzRightContextPreference == true) {
                    val tmpResult =
                        getLeftContext(inputLength = lastCandidateLength).dropLast(
                            lastCandidateLength
                        )
                    tmpResult.ifEmpty {
                        getRightContext(inputLength = lastCandidateLength)
                    }
                } else {
                    getLeftContext(inputLength = lastCandidateLength).dropLast(lastCandidateLength)
                }
            }
        } catch (e: Exception) {
            Timber.e("Error performZenzRequest leftContext: ${e.stackTraceToString()}")
            ""
        }

        Timber.d("performZenzRequest: $insertString leftContext: [$leftContext]")

        // 4. エンジンによる生成処理
        try {
            // 処理直前にキャンセルされていないかチェック
            ensureActive()

            val stringFromZenz = zenzEngine?.generateWithContextAndConditions(
                profile = zenzProfilePreference ?: "",
                topic = "",
                style = "",
                preference = "",
                leftContext = leftContext.ifEmpty { "" },
                input = insertString.hiraganaToKatakana(),
                maxTokens = zenzMaximumLetterSizePreference ?: 32
            ) ?: ""

            // 生成後もチェック
            ensureActive()

            // 結果を返却
            listOf(
                ZenzCandidate(
                    string = stringFromZenz,
                    type = (33).toByte(),
                    length = (insertString.length).toUByte(),
                    score = 2000,
                    originalString = insertString
                )
            )
        } catch (e: CancellationException) {
            // collectLatestによりキャンセルされた場合はここで再スローして処理を中断させる
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error in zenzEngine generation")
            emptyList()
        }
    }

    private fun getKeyboardSizePreferences(): KeyboardSizePreferences {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (isPortrait) {
            KeyboardSizePreferences(
                heightPref = tenkeyHeightPreferenceValue ?: 280,
                widthPref = tenkeyWidthPreferenceValue ?: 100,
                bottomMargin = tenkeyBottomMarginPreferenceValue ?: 0,
                positionIsEnd = tenkeyPositionPreferenceValue ?: true,
                candidateEmptyHeight = candidateViewHeightEmptyPreferenceValue ?: 110,
                qwertyHeightPref = qwertyHeightPreferenceValue ?: 280,
                qwertyWidthPref = qwertyWidthPreferenceValue ?: 100,
                qwertyBottomMargin = qwertyBottomMarginPreferenceValue ?: 0,
                qwertyPositionIsEnd = qwertyPositionPreferenceValue ?: true,
                keyboardMarginStart = tenkeyStartMarginPreferenceValue ?: 0,
                keyboardMarginEnd = tenkeyEndMarginPreferenceValue ?: 0,
                qwertyMarginStart = qwertyStartMarginPreferenceValue ?: 0,
                qwertyMarginEnd = qwertyEndMarginPreferenceValue ?: 0
            )
        } else {
            KeyboardSizePreferences(
                heightPref = tenkeyHeightLandScapePreferenceValue ?: 280,
                widthPref = tenkeyWidthLandScapePreferenceValue ?: 100,
                bottomMargin = tenkeyLandScapeBottomMarginPreferenceValue ?: 0,
                positionIsEnd = tenkeyLandScapePositionPreferenceValue ?: true,
                candidateEmptyHeight = candidateViewLandScapeHeightEmptyPreferenceValue ?: 110,
                qwertyHeightPref = qwertyHeightLandScapePreferenceValue ?: 280,
                qwertyWidthPref = qwertyWidthLandScapePreferenceValue ?: 100,
                qwertyBottomMargin = qwertyLandScapeBottomMarginPreferenceValue ?: 0,
                qwertyPositionIsEnd = qwertyLandScapePositionPreferenceValue ?: true,
                keyboardMarginStart = tenkeyLandScapeStartMarginPreferenceValue ?: 0,
                keyboardMarginEnd = tenkeyLandScapeEndMarginPreferenceValue ?: 0,
                qwertyMarginStart = qwertyLandScapeStartMarginPreferenceValue ?: 0,
                qwertyMarginEnd = qwertyLandScapeEndMarginPreferenceValue ?: 0
            )
        }
    }

    /**
     * キーボードのレイアウトサイズを計算し、ビューに適用する統合関数
     * @param mainView バインディングオブジェクト
     * @param isSymbolOverride シンボルキーボード状態を強制的に上書きする場合にtrue/falseを指定
     * @param isFloating フローティングモードの場合にtrue
     * @param addCandidateTabHeight 候補タブの高さを追加する場合にtrue
     */
    private fun updateKeyboardLayout(
        mainView: MainLayoutBinding,
        isSymbolOverride: Boolean? = null,
        isFloating: Boolean = false,
        addCandidateTabHeight: Boolean = false
    ) {
        // 1. 設定値の読み込み
        val prefs = getKeyboardSizePreferences()
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val isSymbol = isSymbolOverride ?: keyboardSymbolViewState.value

        // 2. ピクセル値の計算
        val heightPx = when {

            isSymbol || keyboardSymbolViewState.value -> {
                val height = if (isPortrait) 320 else 220
                (height * density).toInt()
            }

            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                val clampedHeight = if (isPortrait) {
                    prefs.qwertyHeightPref.coerceIn(180, 420)
                } else if (isFloating) {
                    prefs.qwertyHeightPref
                } else {
                    prefs.qwertyHeightPref.coerceIn(100, 420)
                }
                (clampedHeight * density).toInt()
            }

            else -> {
                val clampedHeight = if (isPortrait) {
                    prefs.heightPref.coerceIn(180, 420)
                } else if (isFloating) {
                    prefs.heightPref
                } else {
                    prefs.heightPref.coerceIn(60, 420)
                }
                (clampedHeight * density).toInt()
            }
        }

        val widthPx = when {
            prefs.widthPref == 100 -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (screenWidth * (prefs.widthPref / 100f)).toInt()
        }

        val qwertyWidthPx = when {
            prefs.qwertyWidthPref == 100 -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (screenWidth * (prefs.qwertyWidthPref / 100f)).toInt()
        }

        // 3. 最終的な高さ、幅、Gravity、マージンの決定
        val baseKeyboardHeight = if (isPortrait) {
            if (isSymbol) heightPx + applicationContext.dpToPx(50) else heightPx + applicationContext.dpToPx(
                prefs.candidateEmptyHeight
            )
        } else {
            if (isSymbol) heightPx else heightPx + applicationContext.dpToPx(prefs.candidateEmptyHeight)
        }

        val finalKeyboardHeight = when {
            addCandidateTabHeight && candidateTabVisibility == true -> baseKeyboardHeight + mainView.candidateTabLayout.height
            !addCandidateTabHeight && shortcutTollbarVisibility == true && !isSymbol -> baseKeyboardHeight + mainView.shortcutToolbarRecyclerview.height
            else -> baseKeyboardHeight
        }

        val finalKeyboardWidth =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                qwertyWidthPx
            } else {
                widthPx
            }

        val finalStartMargin =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                dpToPx(prefs.qwertyMarginStart)
            } else {
                dpToPx(prefs.keyboardMarginStart)
            }

        val finalEndMargin =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                dpToPx(prefs.qwertyMarginEnd)
            } else {
                dpToPx(prefs.keyboardMarginEnd)
            }

        val finalBottomMargin =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                prefs.qwertyBottomMargin
            } else {
                prefs.bottomMargin
            }

        val positionIsEnd =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                prefs.qwertyPositionIsEnd
            } else {
                prefs.positionIsEnd
            }
        val gravity =
            if (positionIsEnd) (Gravity.BOTTOM or Gravity.END) else (Gravity.BOTTOM or Gravity.START)

        // 4. レイアウトパラメータの適用
        applyKeyboardLayoutParameters(
            mainView = mainView,
            heightPx = heightPx,
            finalKeyboardHeight = finalKeyboardHeight,
            finalKeyboardWidth = finalKeyboardWidth,
            gravity = gravity,
            finalBottomMargin = finalBottomMargin,
            finalStartMargin = finalStartMargin,
            finalEndMargin = finalEndMargin
        )

        if (isSymbol) {
            (mainView.keyboardSymbolView.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
                param.height = heightPx
                param.width = finalKeyboardWidth
                mainView.keyboardSymbolView.layoutParams = param
            }
        }

        if (isTablet == true) {
            (mainView.tabletView.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
                param.height = heightPx
                mainView.keyboardSymbolView.layoutParams = param
            }
        }

        // 5. 個別処理
        if (addCandidateTabHeight) {
            val params = mainView.suggestionVisibility.layoutParams as ConstraintLayout.LayoutParams
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            mainView.suggestionVisibility.layoutParams = params
        }
    }

    /**
     * 計算されたレイアウトパラメータを各ビューに適用するヘルパー関数
     */
    private fun applyKeyboardLayoutParameters(
        mainView: MainLayoutBinding,
        heightPx: Int,
        finalKeyboardHeight: Int,
        finalKeyboardWidth: Int,
        gravity: Int,
        finalBottomMargin: Int,
        finalStartMargin: Int,
        finalEndMargin: Int
    ) {
        if (shortcutTollbarVisibility == true) {
            (mainView.shortcutToolbarRecyclerview.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
                param.bottomMargin = heightPx + mainView.suggestionViewParent.height
                mainView.shortcutToolbarRecyclerview.layoutParams = param
            }
        }

        listOf(
            mainView.suggestionViewParent,
            mainView.keyboardView,
            mainView.customLayoutDefault,
            mainView.qwertyView,
            mainView.candidatesRowView
        ).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (view != mainView.suggestionViewParent) {
                    params.height = heightPx
                } else {
                    params.bottomMargin = heightPx
                }
                params.gravity = gravity
                view.layoutParams = params
            }
        }

        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = finalKeyboardHeight
            params.width = finalKeyboardWidth
            params.bottomMargin = finalBottomMargin
            params.leftMargin = finalStartMargin
            params.rightMargin = finalEndMargin
            params.gravity = gravity
            mainView.root.layoutParams = params
        }

        mainView.root.setPadding(0, 0, 0, systemBottomInset)
    }

    private fun setKeyboardHeightWithAdditionalOriginal(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightWithAdditional called")
        if (currentInputType.isPassword()) return
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val screenWidth = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density

        val heightPref = if (isPortrait) {
            tenkeyHeightPreferenceValue ?: 280
        } else {
            tenkeyHeightLandScapePreferenceValue ?: 220
        }
        val widthPref = if (isPortrait) {
            tenkeyWidthPreferenceValue ?: 100
        } else {
            tenkeyWidthLandScapePreferenceValue ?: 100
        }
        val keyboardBottomMargin = if (isPortrait) {
            tenkeyBottomMarginPreferenceValue ?: 0
        } else {
            tenkeyLandScapeBottomMarginPreferenceValue ?: 0
        }

        val positionPref = if (isPortrait) {
            tenkeyPositionPreferenceValue ?: true
        } else {
            tenkeyLandScapePositionPreferenceValue ?: true
        }
        val qwertyHeightPref = if (isPortrait) {
            qwertyHeightPreferenceValue ?: 280
        } else {
            qwertyHeightLandScapePreferenceValue ?: 220
        }
        val qwertyWidthPref = if (isPortrait) {
            qwertyWidthPreferenceValue ?: 100
        } else {
            qwertyWidthLandScapePreferenceValue ?: 100
        }
        val qwertyPositionPref = if (isPortrait) {
            qwertyPositionPreferenceValue ?: true
        } else {
            qwertyLandScapePositionPreferenceValue ?: true
        }
        val qwertyKeyboardMarginBottomPref = if (isPortrait) {
            qwertyBottomMarginPreferenceValue ?: 0
        } else {
            qwertyLandScapeBottomMarginPreferenceValue ?: 0
        }

        val heightPx = when {
            keyboardSymbolViewState.value -> {
                val height = if (isPortrait) 320 else 220
                (height * density).toInt()
            }

            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                val clampedHeight = qwertyHeightPref.coerceIn(60, 420)
                (clampedHeight * density).toInt()
            }

            else -> {
                val clampedHeight = heightPref.coerceIn(60, 420)
                (clampedHeight * density).toInt()
            }
        }

        val widthPx = when {
            widthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (widthPref / 100f)).toInt()
            }
        }

        val qwertyWidthPx = when {
            qwertyWidthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (qwertyWidthPref / 100f)).toInt()
            }
        }

        val suggestionHeightInDp = if (isPortrait) {
            candidateViewHeightPreferenceValue ?: 110
        } else {
            candidateViewLandScapeHeightPreferenceValue ?: 110
        }

        val keyboardHeight = if (isPortrait) {
            if (keyboardSymbolViewState.value) heightPx + applicationContext.dpToPx(50) else heightPx + applicationContext.dpToPx(
                suggestionHeightInDp
            )
        } else {
            if (keyboardSymbolViewState.value) heightPx else heightPx + applicationContext.dpToPx(
                suggestionHeightInDp
            )
        }

        val finalKeyboardHeight = if (candidateTabVisibility == true) {
            keyboardHeight + mainView.candidateTabLayout.height
        } else {
            keyboardHeight
        }

        val finalKeyboardWidth =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                qwertyWidthPx
            } else {
                widthPx
            }

        val gravity =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                if (qwertyPositionPref) {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.BOTTOM or Gravity.START
                }
            } else {
                if (positionPref) {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.BOTTOM or Gravity.START
                }
            }

        val finalBottomMargin =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                qwertyKeyboardMarginBottomPref
            } else {
                keyboardBottomMargin
            }

        listOf(
            mainView.suggestionViewParent,
            mainView.keyboardView,
            mainView.customLayoutDefault,
            mainView.qwertyView
        ).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (view != mainView.suggestionViewParent) params.height = heightPx
                else params.bottomMargin = heightPx
                params.gravity = gravity
                view.layoutParams = params
            }
        }

        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = finalKeyboardHeight
            params.width = finalKeyboardWidth
            params.bottomMargin = finalBottomMargin
            mainView.root.layoutParams = params
        }

        // Adjust suggestion view constraints since it's no longer attached to the parent bottom
        val params = mainView.suggestionVisibility.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        mainView.suggestionVisibility.layoutParams = params

        mainView.root.setPadding(0, 0, 0, systemBottomInset)
    }

    private fun setKeyboardSizeForHeightPhysicalKeyboard(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeight called $hasHardwareKeyboardConnected")
        updateKeyboardLayout(mainView)
    }

    private fun setKeyboardSizeForHeightSymbol(mainView: MainLayoutBinding, isSymbol: Boolean) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeightSymbol called")
        updateKeyboardLayout(mainView, isSymbolOverride = isSymbol)
    }

    private fun setKeyboardSizeForHeightForFloatingMode(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeightForFloatingMode called")
        updateKeyboardLayout(mainView, isFloating = true)
    }

    private fun setKeyboardHeightWithAdditional(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightWithAdditional called")
        setKeyboardHeightWithAdditionalOriginal(mainView)
    }

    private fun setKeyboardHeightDefault(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightDefault called")
        if (isKeyboardFloatingMode == true) return
        updateKeyboardLayout(mainView)
    }

    private fun setKeyboardSizeSwitchKeyboard(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeSwitchKeyboard called")
        if (isKeyboardFloatingMode == true) return
        updateKeyboardLayout(mainView)
    }

    private fun updateSuggestionViewVisibility(
        mainView: MainLayoutBinding, isVisible: Boolean
    ) {
        if (isKeyboardFloatingMode == true) {
            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                animateViewVisibility(floatingKeyboardLayoutBinding.candidatesRowView, !isVisible)
                floatingKeyboardLayoutBinding.keyboardViewFloating.isVisible = isVisible
                hideFirstRowCandidatesInFullScreenFloating(floatingKeyboardLayoutBinding)
                floatingKeyboardLayoutBinding.candidatesRowView.scrollToPosition(0)
                if (isVisible) {
                    if (floatingKeyboardLayoutBinding.keyboardViewFloating.isInvisible) {
                        animateViewVisibility(
                            floatingKeyboardLayoutBinding.keyboardViewFloating,
                            isVisible = true,
                            true
                        )
                    }
                } else {
                    floatingKeyboardLayoutBinding.keyboardViewFloating.visibility = View.INVISIBLE
                }
                floatingKeyboardLayoutBinding.suggestionVisibility.apply {
                    this.setImageDrawable(if (isVisible) cachedArrowDropDownDrawable else cachedArrowDropUpDrawable)
                }
            }
        }
        animateViewVisibility(mainView.candidatesRowView, !isVisible)
        mainView.candidatesRowView.scrollToPosition(0)
        hideFirstRowCandidatesInFullScreen(mainView)
        if (isVisible) {
            mainLayoutBinding?.apply {
                when {
                    customLayoutDefault.isInvisible -> {
                        animateViewVisibility(
                            customLayoutDefault, isVisible = true, true
                        )
                    }

                    keyboardView.isInvisible -> {
                        animateViewVisibility(
                            keyboardView, isVisible = true, true
                        )
                    }

                    qwertyView.isInvisible -> {
                        animateViewVisibility(
                            qwertyView, isVisible = true, true
                        )
                    }

                    tabletView.isInvisible -> {
                        animateViewVisibility(
                            tabletView, isVisible = true, true
                        )
                    }
                }
            }
        } else {
            mainLayoutBinding?.apply {
                when {
                    keyboardView.isVisible -> keyboardView.visibility = View.INVISIBLE
                    qwertyView.isVisible -> qwertyView.visibility = View.INVISIBLE
                    customLayoutDefault.isVisible -> customLayoutDefault.visibility = View.INVISIBLE
                    tabletView.isVisible -> tabletView.visibility = View.INVISIBLE
                }
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

    private fun hideFirstRowCandidatesInFullScreenFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        floatingKeyboardLayoutBinding.candidatesRowView.post {
            if (!floatingKeyboardLayoutBinding.candidatesRowView.canScrollVertically(-1)) {
                val flexboxManager =
                    floatingKeyboardLayoutBinding.candidatesRowView.layoutManager as? FlexboxLayoutManager
                        ?: return@post
                val flexLines = flexboxManager.flexLines

                if (flexLines.isNotEmpty()) {
                    val firstRowHeight = flexLines[0].crossSize
                    floatingKeyboardLayoutBinding.candidatesRowView.scrollBy(0, firstRowHeight)
                }
            }
        }
    }

    private suspend fun processInputString(
        string: String, mainView: MainLayoutBinding,
    ) {
        Timber.d("launchInputString: inputString: $string stringTail: $stringInTail ${isHenkan.get()} $henkanPressedWithBunsetsuDetect $bunsetsuPositionList [$currentInputType] [${currentInputType in passwordTypes}] [$suppressSuggestions]")
        if (string.isNotEmpty()) {
            hasConvertedKatakana = false
            if (suppressSuggestions) {
                setComposingText(string, 1)
                return
            }
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY) {
                handleTenKeyQwertyInput(string)
            } else {
                handleDefaultInput(string)
            }
        } else {
            Timber.d("setSuggestionOnView auto empty: ${stringInTail.get()} $bunsetusMultipleDetect")
            if (stringInTail.get().isNotEmpty()) {
                setComposingText(stringInTail.get(), 1)
                onLeftKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            } else {
                setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                        setDrawableToEnterKeyCorrespondingToImeOptionsFloating(
                            floatingKeyboardLayoutBinding
                        )
                    }
                }
                onLeftKeyLongPressUp.set(true)
                onRightKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            }
            _zenzCandidates.update { emptyList() }
            hasConvertedKatakana = false
            filteredCandidateList = emptyList()
            resetInputString()
            lastCandidate = ""
            hardKeyboardShiftPressd = false
            initialCursorDetectInFloatingCandidateView = false
            initialCursorXPosition = 0
            if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
                updateSuggestionsForFloatingCandidate(emptyList())
                listAdapter.updateHighlightPosition(-1)
                currentHighlightIndex = -1
            }
            if (isKeyboardFloatingMode == true) {
                floatingKeyboardBinding?.keyboardViewFloating?.apply {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    when (currentInputMode.value) {
                        InputMode.ModeEnglish -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = true
                            )
                        }

                        InputMode.ModeJapanese -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }

                        InputMode.ModeNumber -> {
                            setBackgroundSmallLetterKey(
                                cachedNumberDrawable
                            )
                        }
                    }
                }
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
                    when (currentInputMode.value) {
                        InputMode.ModeEnglish -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = true
                            )
                        }

                        InputMode.ModeJapanese -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }

                        InputMode.ModeNumber -> {
                            setBackgroundSmallLetterKey(
                                cachedNumberDrawable
                            )
                        }
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
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                } else {
                    getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                },
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null
            )
        }
        if (isLiveConversionEnable != true) {
            // ライブ変換が無効な場合は、入力されたテキストをそのまま表示します。
            setComposingTextAfterEdit(
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionAfterBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.blue)
                } else {
                    getColor(com.kazumaproject.core.R.color.blue)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputCompositionTextColor
                } else {
                    null
                }
            )
        }
    }

    /**
     * TenKeyQWERTY以外のモードの入力処理を担当します。
     */
    private suspend fun handleDefaultInput(string: String) {
        val spannable = createSpannableWithTail(string)
        if (!(isLiveConversionEnable == true && isFlickOnlyMode == true)) {
            setComposingTextPreEdit(
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                } else {
                    getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                },
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null
            )
        }
        _suggestionFlag.emit(CandidateShowFlag.Updating)
        val timeToDelay = delayTime?.toLong() ?: DEFAULT_DELAY_MS
        delay(timeToDelay)

        if (inputString.value != string) {
            return
        }

        if (isLiveConversionEnable != true) {
            val shouldCommitOriginalText =
                inputString.value.isNotEmpty() && !isHenkan.get() && !onDeleteLongPressUp.get() && !englishSpaceKeyPressed.get() && !deleteKeyLongKeyPressed.get() && !hasConvertedKatakana

            if (shouldCommitOriginalText) {
                isContinuousTapInputEnabled.set(true)
                lastFlickConvertedNextHiragana.set(true)
                setComposingTextAfterEdit(
                    inputString = string,
                    spannableString = spannable,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
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
        beginBatchEdit()
        val commitString = if (candidate.type == (15).toByte()) {
            candidate.string.correctReading().first
        } else {
            candidate.string
        }
        lastCandidate = commitString
        val newSpannable = createSpannableWithTail(commitString)
        setComposingTextAfterEdit(
            inputString = commitString,
            spannableString = newSpannable,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        endBatchEdit()
    }

    /**
     * 末尾文字列を結合したSpannableStringを生成します。
     */
    private fun createSpannableWithTail(text: String): SpannableString {
        return SpannableString(text + stringInTail.get())
    }

    private suspend fun resetInputString() {
        Timber.d("resetInputString detect: $bunsetusMultipleDetect [${stringInTail.get()}]")
        val henkanActive = isHenkan.get()
        val tailIsEmpty = stringInTail.get().isEmpty()
        val shouldCommitIdle = !bunsetusMultipleDetect || tailIsEmpty
        if (!henkanActive && shouldCommitIdle) {
            _suggestionFlag.emit(CandidateShowFlag.Idle)
        }
    }

    private fun setCurrentInputType(attribute: EditorInfo?) {
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME2(this)
            Timber.d("setCurrentInputType: $currentInputType $inputType ${attribute.hintText} ${attribute.actionId} ${attribute.fieldName} ${attribute.inputType} ")
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
                        InputTypeForIME.TextUri,
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
                        InputTypeForIME.TextUri,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                            setFirstKeyboardType()
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
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedTabDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextDone -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedCheckDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedSearchDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextEmailAddress,
                        InputTypeForIME.TextEditTextInWebView,
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
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                            setFirstKeyboardType()
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

            if (isKeyboardFloatingMode == true) {
                floatingKeyboardBinding?.keyboardViewFloating?.apply {
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
                        InputTypeForIME.TextUri,
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

                        InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
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

                        InputTypeForIME.TextEmailAddress,
                        InputTypeForIME.TextEditTextInWebView,
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
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                    }
                }
            }
        }
    }

    private fun setFirstKeyboardType() {
        if (keyboardOrder.isNotEmpty()) {
            val firstItem = keyboardOrder.first()
            when (firstItem) {
                KeyboardType.TENKEY -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                KeyboardType.SUMIRE -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                KeyboardType.QWERTY -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                KeyboardType.ROMAJI -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTYRomaji }
                KeyboardType.CUSTOM -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
            }
        }
    }

    private fun setTabsToTabLayout(
        mainView: MainLayoutBinding
    ) {
        mainView.candidateTabLayout.removeAllTabs()

        candidateTabOrder.forEach { tabType ->
            val tab = mainView.candidateTabLayout.newTab()
            tab.text = getCandidateTabDisplayName(tabType)
            mainView.candidateTabLayout.addTab(tab)
        }
    }

    private fun getCandidateTabDisplayName(candidateTab: CandidateTab): String {
        return when (candidateTab) {
            CandidateTab.PREDICTION -> "予測"
            CandidateTab.CONVERSION -> "変換"
            CandidateTab.EISUKANA -> "英数カナ"
        }
    }

    private fun setCandidateTabLayout(
        mainView: MainLayoutBinding
    ) {
        mainView.candidateTabLayout.apply {
            when (keyboardThemeMode) {
                "custom" -> {
                    setSelectedTabIndicatorColor(customThemeSpecialKeyTextColor ?: Color.BLACK)
                    setTabTextColors(
                        customThemeKeyTextColor ?: Color.BLACK,
                        customThemeSpecialKeyTextColor ?: Color.BLACK
                    )
                }

                else -> {}
            }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.let { t ->
                        if (t.position > candidateTabOrder.size - 1) return
                        when (candidateTabOrder[t.position]) {
                            CandidateTab.PREDICTION -> {
                                val input = inputString.value
                                if (input.isNotEmpty()) {
                                    ioScope.launch {
                                        setCandidates(input, mainView)
                                        withContext(Dispatchers.Main) {
                                            hideFirstRowCandidatesInFullScreen(mainView)
                                        }
                                    }
                                }
                            }

                            CandidateTab.CONVERSION -> {
                                val input = inputString.value
                                if (input.isNotEmpty()) {
                                    ioScope.launch {
                                        setCandidatesWithoutPrediction(input, mainView)
                                        withContext(Dispatchers.Main) {
                                            hideFirstRowCandidatesInFullScreen(mainView)
                                        }
                                    }
                                }
                            }

                            CandidateTab.EISUKANA -> {
                                val input = inputString.value
                                if (input.isNotEmpty()) {
                                    ioScope.launch {
                                        setCandidatesEnglishKana(input)
                                        withContext(Dispatchers.Main) {
                                            hideFirstRowCandidatesInFullScreen(mainView)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {

                }

                override fun onTabReselected(tab: TabLayout.Tab?) {

                }

            })
        }
    }

    private fun setSuggestionRecyclerView(
        mainView: MainLayoutBinding, flexboxLayoutManagerRow: FlexboxLayoutManager
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
                        setKeyboardSizeSwitchKeyboard(mainView)
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
                        Timber.d("SuggestionAdapter.HelperIcon.PASTE: clicked")
                        pasteAction()
                        if (clipboardPreviewTapToDelete == true) {
                            //clipboardUtil.clearClipboard()
                            adapter.apply {
                                setClipboardPreview("")
                                setPasteEnabled(false)
                            }
                        }
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
        suggestionAdapterFull?.let { adapter ->
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
        }
        mainView.suggestionRecyclerView.apply {
            itemAnimator = null
            isFocusable = false
        }

        mainView.candidatesRowView.apply {
            itemAnimator = null
            isFocusable = false
        }
        suggestionAdapter.apply {
            mainView.candidatesRowView.layoutManager = flexboxLayoutManagerRow
            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                floatingKeyboardLayoutBinding.suggestionRecyclerView.let { sRecyclerView ->
                    sRecyclerView.layoutManager = FlexboxLayoutManager(applicationContext).apply {
                        flexDirection = FlexDirection.COLUMN
                    }
                    sRecyclerView.itemAnimator = null
                    sRecyclerView.isFocusable = false
                }
                floatingKeyboardLayoutBinding.candidatesRowView.let { fullCandidateView ->
                    fullCandidateView.itemAnimator = null
                    fullCandidateView.isFocusable = false
                    fullCandidateView.layoutManager =
                        FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.ROW
                            justifyContent = JustifyContent.FLEX_START
                        }
                }
            }
        }
        mainView.suggestionVisibility.setOnClickListener {
            _suggestionViewStatus.update { !it }
        }
        floatingKeyboardBinding?.suggestionVisibility?.setOnClickListener {
            _suggestionViewStatus.update { !it }
        }
    }

    private fun setMainSuggestionColumn(
        mainView: MainLayoutBinding
    ) {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val columnNum = if (isPortrait) {
            candidateColumns ?: "1"
        } else {
            candidateColumnsLandscape ?: "1"
        }

        val adapter = mainView.suggestionRecyclerView.adapter
        mainView.suggestionRecyclerView.adapter = null

        if (mainView.suggestionRecyclerView.itemDecorationCount > 0) {
            mainView.suggestionRecyclerView.removeItemDecorationAt(0)
        }

        when (columnNum) {
            "1" -> {
                mainView.suggestionRecyclerView.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            }

            "2", "3" -> {
                val spanCount = columnNum.toInt()
                val gridLayoutManager = GridLayoutManager(
                    this@IMEService, spanCount, GridLayoutManager.HORIZONTAL, false
                )

                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            // If the item is the empty view or the custom layout picker,
                            // make it span all columns.
                            SuggestionAdapter.VIEW_TYPE_EMPTY, SuggestionAdapter.VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> spanCount
                            // Otherwise (for regular suggestions), make it span just one column.
                            else -> 1
                        }
                    }
                }

                val spacingInPixels =
                    resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing)

                mainView.suggestionRecyclerView.layoutManager = gridLayoutManager
                mainView.suggestionRecyclerView.addItemDecoration(
                    GridSpacingItemDecoration(
                        spanCount, spacingInPixels, true
                    )
                )
            }
        }
        mainView.suggestionRecyclerView.adapter = adapter
    }

    private fun setShortCutAdapter(
        mainView: MainLayoutBinding
    ) {
        mainView.shortcutToolbarRecyclerview.apply {
            layoutManager =
                LinearLayoutManager(this@IMEService, LinearLayoutManager.HORIZONTAL, false)
            adapter = shortcutAdapter
        }
        when (keyboardThemeMode) {
            "custom" -> {
                shortcutAdapter?.setIconColor(customThemeSpecialKeyTextColor ?: Color.BLACK)
            }

            else -> {
            }
        }
        shortcutAdapter?.onItemClicked = { type ->
            when (type) {
                ShortcutType.SETTINGS -> {
                    launchSettingsActivity("setting_fragment_request")
                }

                ShortcutType.EMOJI -> {
                    vibrate()
                    _keyboardSymbolViewState.value = !_keyboardSymbolViewState.value
                    stringInTail.set("")
                    finishComposingText()
                    setComposingText("", 0)
                }

                ShortcutType.TEMPLATE -> {
                    showUserTemplateListPopup()
                }

                ShortcutType.KEYBOARD_PICKER -> {
                    showKeyboardPicker()
                }

                ShortcutType.SELECT_ALL -> {
                    selectAllText()
                }

                ShortcutType.COPY -> {
                    copyAction()
                }

                ShortcutType.PASTE -> {
                    pasteAction()
                }

                ShortcutType.DATE_PICKER -> {
                    showCurrentDateListPopup()
                }

                ShortcutType.VOICE_INPUT -> {
                    startVoiceInput(mainView)
                }
            }
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
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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

        floatingKeyboardBinding?.floatingSymbolKeyboard?.apply {
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
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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
            applyKeyboardTheme(
                themeMode = keyboardThemeMode ?: "default",
                currentNightMode = currentNightMode,
                isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                customBgColor = customThemeBgColor ?: Color.WHITE,
                customKeyColor = customThemeKeyColor ?: Color.WHITE,
                customSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY,
                customKeyTextColor = customThemeKeyTextColor ?: Color.BLACK,
                customSpecialKeyTextColor = customThemeSpecialKeyTextColor ?: Color.BLACK,
                liquidGlassEnable = liquidGlassThemePreference ?: false,
                customBorderEnable = customKeyBorderEnablePreference ?: false,
                customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                borderWidth = customKeyBorderWidth ?: 1
            )

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
                    Timber.d("onReleasedQWERTYKey: $qwertyKey")
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
                        QWERTYKey.QWERTYKeyShift -> {
                            hardKeyboardShiftPressd = true
                        }

                        QWERTYKey.QWERTYKeyDelete -> {
                            if (!deleteKeyLongKeyPressed.get()) {
                                handleDeleteKeyTap(insertString, suggestionList)
                            }
                            stopDeleteLongPress()
                            if (isDefaultRomajiHenkanMap) {
                                hardKeyboardShiftPressd = false
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchDefaultLayout -> {
                            if (!onKeyboardSwitchLongPressUp) {
                                switchNextKeyboard()
                                _inputString.update { "" }
                                finishComposingText()
                                setComposingText("", 0)
                                mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(false)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchMode -> {

                        }

                        QWERTYKey.QWERTYKeySpace -> {
                            Timber.d("onReleasedQWERTYKey: QWERTYKeySpace $isSpaceKeyLongPressed")
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
                                if (isHenkan.get()) {
                                    val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                                    handleDeleteKeyInHenkan(suggestions, insertString)
                                } else {
                                    handleLeftCursor(GestureType.Tap, insertString)
                                }
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyCursorRight -> {
                            Timber.d("QWERTYKey.QWERTYKeyCursorRight")
                            if (!rightCursorKeyLongKeyPressed.get()) {
                                if (isHenkan.get()) {
                                    val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                                    handleJapaneseModeSpaceKey(
                                        mainView, suggestions, insertString
                                    )
                                } else {
                                    actionInRightKeyPressed(GestureType.Tap, insertString)
                                }
                            }
                            onRightKeyLongPressUp.set(true)
                            rightCursorKeyLongKeyPressed.set(false)
                            rightLongPressJob?.cancel()
                            rightLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyCursorUp -> {
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.FlickTop, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyCursorDown -> {
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.FlickBottom, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeySwitchRomajiEnglish -> {
                            val romajiMode = mainView.qwertyView.getRomajiMode()
                            mainView.qwertyView.setRomajiMode(!romajiMode)
                            if (mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                                mainView.keyboardView.setCurrentMode(InputMode.ModeEnglish)
                                mainView.qwertyView.setRomajiEnglishSwitchKeyTextWithStyle(false)
                            } else {
                                mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                                mainView.qwertyView.setRomajiEnglishSwitchKeyTextWithStyle(true)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchNumberKey -> {
                            if (previousTenKeyQWERTYMode == null) {
                                if (qwertySwitchNumberKeyWithoutNumberPreference == true) {
                                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                                    mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                                    if (insertString.isEmpty()) {
                                        setKeyboardSizeSwitchKeyboard(mainView)
                                    } else {
                                        setKeyboardHeightWithAdditional(mainView)
                                    }
                                } else {
                                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                                    mainView.keyboardView.setCurrentMode(InputMode.ModeNumber)
                                    if (insertString.isEmpty()) {
                                        setKeyboardSizeSwitchKeyboard(mainView)
                                    } else {
                                        setKeyboardHeightWithAdditional(mainView)
                                    }
                                }
                            } else {
                                previousTenKeyQWERTYMode?.let {
                                    when (it) {
                                        TenKeyQWERTYMode.Default -> {
                                            if (qwertySwitchNumberKeyWithoutNumberPreference == true) {
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                                                mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            } else {
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                                                mainView.keyboardView.setCurrentMode(InputMode.ModeNumber)
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            }
                                        }

                                        TenKeyQWERTYMode.Sumire -> {
                                            if (qwertySwitchNumberKeyWithoutNumberPreference == true) {
                                                customKeyboardMode = KeyboardInputMode.HIRAGANA
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                                                createNewKeyboardLayoutForSumire()
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            } else {
                                                customKeyboardMode = KeyboardInputMode.SYMBOLS
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                                                createNewKeyboardLayoutForSumire()
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            }
                                        }

                                        TenKeyQWERTYMode.Custom -> {
                                            customKeyboardMode = KeyboardInputMode.HIRAGANA
                                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                                            createNewKeyboardLayoutForSumire()
                                            if (insertString.isEmpty()) {
                                                setKeyboardSizeSwitchKeyboard(mainView)
                                            } else {
                                                setKeyboardHeightWithAdditional(mainView)
                                            }
                                        }

                                        else -> {
                                            if (qwertySwitchNumberKeyWithoutNumberPreference == true) {
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                                                mainView.keyboardView.setCurrentMode(InputMode.ModeJapanese)
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            } else {
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                                                mainView.keyboardView.setCurrentMode(InputMode.ModeNumber)
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (mainView.keyboardView.currentInputMode.value == InputMode.ModeJapanese) {
                                if (insertString.isNotEmpty()) {
                                    Timber.d("QWERTY romaji not empty: $hardKeyboardShiftPressd $qwertyRomajiShiftConversionPreference")
                                    if (qwertyRomajiShiftConversionPreference == true) {
                                        if (hardKeyboardShiftPressd) {
                                            Timber.d("QWERTY romaji hardKeyboardShiftPressd: $tap")
                                            tap?.let { c ->
                                                val charToAppend =
                                                    if (isDefaultRomajiHenkanMap && c.isLowerCase()) {
                                                        c.toZenkaku()
                                                    } else {
                                                        c
                                                    }
                                                Timber.d("QWERTY romaji : $charToAppend")
                                                sb.append(insertString).append(charToAppend)
                                                romajiConverter?.let { converter ->
                                                    _inputString.update {
                                                        converter.convertQWERTYZenkaku(sb.toString())
                                                    }
                                                }
                                            }
                                        } else {
                                            tap?.let { c ->
                                                val charToAppend = if (isDefaultRomajiHenkanMap) {
                                                    c.toZenkaku()
                                                } else {
                                                    c
                                                }
                                                Timber.d("QWERTY romaji : $charToAppend")
                                                sb.append(insertString).append(charToAppend)
                                                romajiConverter?.let { converter ->
                                                    _inputString.update {
                                                        converter.convertQWERTYZenkaku(sb.toString())
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        if (hardKeyboardShiftPressd) {
                                            Timber.d("QWERTY romaji hardKeyboardShiftPressd: $tap")
                                            handleTap(tap, insertString, sb, mainView)
                                        } else {
                                            tap?.let { c ->
                                                val charToAppend = if (isDefaultRomajiHenkanMap) {
                                                    c.toZenkaku()
                                                } else {
                                                    c
                                                }
                                                Timber.d("QWERTY romaji : $charToAppend")
                                                sb.append(insertString).append(charToAppend)
                                                romajiConverter?.let { converter ->
                                                    _inputString.update {
                                                        converter.convertQWERTYZenkaku(sb.toString())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    tap?.let { c ->
                                        romajiConverter?.let { converter ->
                                            val charToAppend =
                                                if (isDefaultRomajiHenkanMap && !hardKeyboardShiftPressd) {
                                                    c.toZenkaku()
                                                } else {
                                                    c
                                                }
                                            Timber.d("QWERTY romaji 2: $charToAppend")
                                            _inputString.update {
                                                converter.convertQWERTYZenkaku(
                                                    charToAppend.toString()
                                                )
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
                            val insertString = inputString.value
                            if (insertString.isEmpty() || !mainView.qwertyView.getRomajiMode()) {
                                setCursorMode(true)
                                isSpaceKeyLongPressed = true
                            } else {
                                if (zenzEnableLongPressConversionPreference == true) {
                                    scope.launch {
                                        filteredCandidateList = suggestionAdapter?.suggestions
                                        val candidates = performZenzRequest(insertString)
                                        _zenzCandidates.update { candidates }
                                    }
                                    isSpaceKeyLongPressed = true
                                } else {
                                    if (conversionKeySwipePreference == true) {
                                        if (!isHenkan.get()) {
                                            setCursorMode(true)
                                            isSpaceKeyLongPressed = true
                                        }
                                    } else {
                                        setCursorMode(true)
                                        isSpaceKeyLongPressed = true
                                    }
                                }
                            }
                        }

                        QWERTYKey.QWERTYKeyCursorRight -> {
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

                        QWERTYKey.QWERTYKeyCursorLeft -> {
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

                        else -> {

                        }
                    }
                }

                override fun onFlickUPQWERTYKey(
                    qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?
                ) {
                    Timber.d("onFlickUPQWERTYKey: $qwertyKey, $tap, $variations")
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

                    if (qwertyKey != QWERTYKey.QWERTYKeyDelete) {
                        clearDeletedBuffer()
                        suggestionAdapter?.setUndoEnabled(false)
                    }

                    variations?.let { variation ->
                        if (variation.isNotEmpty()) {
                            if (switchQWERTYPassword == true) {
                                if (currentInputType in passwordTypesWithOutNumber) {
                                    handleTap(
                                        variation.first().toHankakuKigou(),
                                        insertString,
                                        sb,
                                        mainView
                                    )
                                } else {
                                    handleTap(variation.first(), insertString, sb, mainView)
                                }
                            } else {
                                handleTap(variation.first(), insertString, sb, mainView)
                            }
                        }
                    }
                }

                override fun onFlickDownQWERTYKey(
                    qwertyKey: QWERTYKey,
                    character: Char
                ) {
                    Timber.d("onFlickDownQWERTYKey: $qwertyKey, $character")
                    when (vibrationTimingStr) {
                        "both" -> {
                            vibrate()
                        }

                        "press" -> {}

                        "release" -> {}
                    }
                    if (qwertyKey != QWERTYKey.QWERTYKeyDelete) {
                        clearDeletedBuffer()
                        suggestionAdapter?.setUndoEnabled(false)
                    }
                    handleTap(character, inputString.value, StringBuilder(), mainView)
                }
            })

            setOnDeleteLeftFlickListener {
                val insertString = inputString.value
                Timber.d("setOnDeleteLeftFlickListener called: [$insertString]")
                deleteWordOrSymbolsBeforeCursor(insertString)
            }
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
        Timber.d("setSymbols: ${cachedEmoji?.size}")
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = symbolKeyboardFirstItem ?: SymbolMode.EMOJI

        )
    }

    private suspend fun setSymbolsFloating(floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding) {
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
        floatingKeyboardLayoutBinding.floatingSymbolKeyboard.setSymbolLists(
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
        Timber.d("setCandidateClick: $candidate")
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
                insertString = insertString,
                position = position
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
        insertString: String, candidate: Candidate
    ) {
        val candidateLength = candidate.length.toInt()
        val candidateString = candidate.string
        if (insertString.length > candidateLength) {
            stringInTail.set(insertString.substring(candidateLength))
            ioScope.launch {
                learnRepository.upsertLearnedData(
                    LearnEntity(
                        input = insertString.substring(0, candidateLength),
                        out = candidateString,
                        score = candidate.score.toShort(),
                        leftId = candidate.leftId,
                        rightId = candidate.rightId
                    )
                )
            }
        }
        commitAndClearInput(candidateString)
    }

    private fun processCandidate(
        candidate: Candidate, insertString: String, currentInputMode: InputMode, position: Int
    ) {
        Timber.d("processCandidate ${candidate.type.toInt()} ${insertString.length == candidate.length.toInt()}")
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
                        insertString = insertString, candidate = candidate
                    )
                }
            }
        }
    }

    private fun upsertLearnDictionaryWhenTapCandidate(
        currentInputMode: InputMode, insertString: String, candidate: Candidate, position: Int
    ) {
        // 1) 学習モードかつ日本語モードかつ position!=0 のみ upsert
        val isFirstCandidateLearn: Boolean =
            if (enablePredictionSearchLearnDictionaryPreference == true) {
                true
            } else {
                position != 0
            }
        if (currentInputMode == InputMode.ModeJapanese && isLearnDictionaryMode == true && isFirstCandidateLearn && !isPrivateMode) {
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
        insertString: String,
        position: Int
    ) {
        if (currentInputMode == InputMode.ModeJapanese && isLearnDictionaryMode == true && !isPrivateMode && position != 0) {
            ioScope.launch {
                try {
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
        currentCustomKeyboardPosition = 0
        filteredCandidateList = emptyList()
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
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
        lastCandidate = ""
        _keyboardSymbolViewState.update { false }
        learnMultiple.stop()
        stopDeleteLongPress()
        clearDeletedBuffer()
        suggestionAdapter?.setUndoEnabled(false)
        updateClipboardPreview()
        _selectMode.update { false }
        hasConvertedKatakana = false
        romajiConverter?.clear()
        hardKeyboardShiftPressd = false
        resetSumireKeyboardDakutenMode()
        initialCursorDetectInFloatingCandidateView = false
        initialCursorXPosition = 0
        countToggleKatakana = 0
        currentEnterKeyIndex = 0
        currentSpaceKeyIndex = 0
        currentKatakanaKeyIndex = 0
        currentDakutenKeyIndex = 0
        bunsetsuPositionList = emptyList()
        henkanPressedWithBunsetsuDetect = false
        bunsetusMultipleDetect = false
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        mainLayoutBinding = null
        floatingKeyboardBinding = null
        closeConnection()
        scope.cancel()
        ioScope.cancel()
    }

    private fun resetFlagsSuggestionClick() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
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
        henkanPressedWithBunsetsuDetect = false
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
        henkanPressedWithBunsetsuDetect = false
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
        henkanPressedWithBunsetsuDetect = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
    }

    /**
     * 編集前（PreEdit）のテキスト装飾を設定する
     * * @param backgroundColor 背景色 (Color Int)
     * @param textColor テキスト色 (Color Int, nullの場合は適用しない)
     */
    private fun setComposingTextPreEdit(
        inputString: String,
        spannableString: SpannableString,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val inputLength = inputString.length
        val tailLength = stringInTail.get().length

        spannableString.apply {
            // 背景色の設定
            setSpan(
                BackgroundColorSpan(backgroundColor),
                0,
                inputLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )

            // テキスト色の設定（指定がある場合のみ）
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    inputLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
            }

            // 下線の設定
            setSpan(
                UnderlineSpan(),
                0,
                inputLength + tailLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )
        }

        Timber.d("launchInputString: setComposingTextPreEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    /**
     * 編集後（AfterEdit）のテキスト装飾を設定する
     */
    private fun setComposingTextAfterEdit(
        inputString: String,
        spannableString: SpannableString,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        // stringInTail が空でなければ、下線の終了位置を延長する
        val underlineEnd = if (stringInTail.get().isNotEmpty()) {
            inputString.length + stringInTail.get().length
        } else {
            inputString.length
        }

        spannableString.apply {
            // 背景色は inputString の部分だけ
            setSpan(
                BackgroundColorSpan(backgroundColor),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )

            // テキスト色の設定
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    inputString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
            }

            // 下線は stringInTail があればそこまで含める
            setSpan(
                UnderlineSpan(),
                0,
                underlineEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )
        }
        setComposingText(spannableString, 1)
    }

    private fun setEnterKeyAction(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        Timber.d("setEnterKeyAction: $insertString ${stringInTail.get()} $bunsetsuPositionList $henkanPressedWithBunsetsuDetect")
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
                        if (tenkeyQWERTYSwitchNumber == true) {
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            mainView.qwertyView.setSwitchNumberLayoutKeyVisibility(true)
                            mainView.qwertyView.setRomajiMode(false)
                            if (insertString.isEmpty()) {
                                setKeyboardSizeSwitchKeyboard(mainView)
                            } else {
                                setKeyboardHeightWithAdditional(mainView)
                            }
                            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Default
                        } else {
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
                                        isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                        isEnglish = false
                                    )
                                }
                            } else {
                                setBackgroundSmallLetterKey(
                                    isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                    isEnglish = false
                                )
                            }
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
                                    isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                    isEnglish = false
                                )
                            }
                        } else {
                            setSideKeySpaceDrawable(
                                cachedSpaceDrawable
                            )
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setTenkeyIconsInHenkanFloating(
        insertString: String, floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
            Timber.d("setTenkeyIconsInHenkanFloating: ${currentInputMode.value}")
            when (currentInputMode.value) {
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
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    } else {
                        setBackgroundSmallLetterKey(
                            isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                            isEnglish = false
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
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    } else {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setBackgroundSmallLetterKey(
                            isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                            isEnglish = false
                        )
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
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
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
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = true
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

    private fun updateUIinHenkanFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding, insertString: String
    ) {
        floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
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
                            isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                            isEnglish = false
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
                            isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                            isEnglish = true
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

    private suspend fun setSuggestionOnView(
        inputString: String, mainView: MainLayoutBinding
    ) {
        Timber.d("setSuggestionOnView: tabPosition first: $inputString $suggestionClickNum")
        if (inputString.isEmpty() || suggestionClickNum > 0) return
        val tabPosition = mainView.candidateTabLayout.selectedTabPosition
        Timber.d("setSuggestionOnView: tabPosition: $tabPosition $bunsetsuPositionList")
        if (candidateTabVisibility == true) {
            if (candidateTabOrder.isNotEmpty() && tabPosition < candidateTabOrder.size) {
                when (candidateTabOrder[tabPosition]) {
                    CandidateTab.PREDICTION -> {
                        setCandidates(inputString, mainView)
                    }

                    CandidateTab.CONVERSION -> {
                        setCandidatesWithoutPrediction(inputString, mainView)
                    }

                    CandidateTab.EISUKANA -> {
                        setCandidatesEnglishKana(inputString)
                    }
                }
            } else {
                setCandidates(inputString, mainView)
            }
        } else {
            setCandidatesOriginal(inputString, mainView)
        }
        Timber.d("setSuggestionOnView auto: $inputString $stringInTail $tabPosition $bunsetsuPositionList ${isHenkan.get()} $henkanPressedWithBunsetsuDetect $bunsetusMultipleDetect")
    }

    private suspend fun setCandidates(
        insertString: String, mainView: MainLayoutBinding
    ) {
        if (zenzEnableStatePreference == true) {
            _zenzRequest.emit(insertString)
        }
        val candidates = getSuggestionList(insertString, mainView)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            if (!suppressSuggestions) {
                updateSuggestionsForFloatingCandidate(filtered.map {
                    CandidateItem(
                        word = it.string, length = it.length
                    )
                })
            }
        } else {
            if (!suppressSuggestions) {
                suggestionAdapter?.suggestions = filtered
                suggestionAdapterFull?.suggestions = filtered
            }
        }

        if (zenzEnableStatePreference == true) {
            filteredCandidateList = filtered
            lastLocalUpdatedInput.emit(insertString)
        }

        if (isLiveConversionEnable == true && !hasConvertedKatakana) {
            if (isFlickOnlyMode != true) {
                delay(delayTime?.toLong() ?: DEFAULT_DELAY_MS)
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        } else if (isLiveConversionEnable != true && !hasConvertedKatakana && henkanPressedWithBunsetsuDetect) {
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        }
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        if (bunsetsuSeparation == true) {
            bunsetsuPositionList?.let {
                if (bunsetusMultipleDetect && it.isNotEmpty()) {
                    handleJapaneseModeSpaceKeyWithBunsetsu(
                        mainView, filtered, insertString
                    )
                }
            }
        }

    }

    private suspend fun setCandidatesOriginal(
        insertString: String, mainView: MainLayoutBinding
    ) {
        if (zenzEnableStatePreference == true) {
            _zenzRequest.emit(insertString)
        }
        val candidates = getSuggestionListOriginal(insertString, mainView)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            if (!suppressSuggestions) {
                updateSuggestionsForFloatingCandidate(filtered.map {
                    CandidateItem(
                        word = it.string, length = it.length
                    )
                })
            }
        } else {
            if (!suppressSuggestions) {
                suggestionAdapter?.suggestions = filtered
                suggestionAdapterFull?.suggestions = filtered
            }

        }

        if (zenzEnableStatePreference == true) {
            filteredCandidateList = filtered
            lastLocalUpdatedInput.emit(insertString)
        }

        if (isLiveConversionEnable == true && !hasConvertedKatakana) {
            if (isFlickOnlyMode != true) {
                delay(delayTime?.toLong() ?: DEFAULT_DELAY_MS)
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        } else if (isLiveConversionEnable != true && !hasConvertedKatakana && henkanPressedWithBunsetsuDetect) {
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        }
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        if (bunsetsuSeparation == true) {
            bunsetsuPositionList?.let {
                if (bunsetusMultipleDetect && it.isNotEmpty()) {
                    handleJapaneseModeSpaceKeyWithBunsetsu(
                        mainView, filtered, insertString
                    )
                }
            }
        }
    }

    private suspend fun setCandidatesWithoutPrediction(
        insertString: String, mainView: MainLayoutBinding
    ) {
        val candidates = getSuggestionListWithoutPrediction(insertString)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            if (!suppressSuggestions) {
                updateSuggestionsForFloatingCandidate(filtered.map {
                    CandidateItem(
                        word = it.string, length = it.length
                    )
                })
            }
        } else {
            if (!suppressSuggestions) {
                suggestionAdapter?.suggestions = filtered
                suggestionAdapterFull?.suggestions = filtered
            }

        }
        if (isLiveConversionEnable == true && !hasConvertedKatakana) {
            if (isFlickOnlyMode != true) {
                delay(delayTime?.toLong() ?: DEFAULT_DELAY_MS)
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        } else if (isLiveConversionEnable != true && !hasConvertedKatakana && henkanPressedWithBunsetsuDetect) {
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        }
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        if (bunsetsuSeparation == true) {
            bunsetsuPositionList?.let {
                if (bunsetusMultipleDetect && it.isNotEmpty()) {
                    handleJapaneseModeSpaceKeyWithBunsetsu(
                        mainView, filtered, insertString
                    )
                }
            }
        }
    }

    private suspend fun setCandidatesEnglishKana(
        insertString: String,
    ) {
        val candidates = getSuggestionListEnglishKana(insertString)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            if (!suppressSuggestions) {
                updateSuggestionsForFloatingCandidate(filtered.map {
                    CandidateItem(
                        word = it.string, length = it.length
                    )
                })
            }
        } else {
            if (!suppressSuggestions) {
                suggestionAdapter?.suggestions = filtered
                suggestionAdapterFull?.suggestions = filtered
            }

        }
        if (isLiveConversionEnable == true && !hasConvertedKatakana) {
            if (isFlickOnlyMode != true) {
                delay(delayTime?.toLong() ?: DEFAULT_DELAY_MS)
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            if (!hasConvertedKatakana && filtered.isNotEmpty()) applyFirstSuggestion(filtered.first())
        }
    }

    private suspend fun getSuggestionListOriginal(
        insertString: String, mainView: MainLayoutBinding
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

        val resultFromLearnDictionary =
            if (enablePredictionSearchLearnDictionaryPreference == true) {
                withContext(Dispatchers.IO) {
                    val prefixMatchNumber = (learnPredictionPreference ?: 2) - 1
                    if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                    learnRepository.predictiveSearchByInput(
                        prefix = insertString, limit = 4
                    ).map {
                        Candidate(
                            string = it.out,
                            type = (34).toByte(),
                            length = (it.input.length).toUByte(),
                            score = it.score.toInt()
                        )
                    }.sortedBy { it.score }
                }
            } else {
                emptyList()
            }

        val ngWords =
            if (isNgWordEnable == true) ngWordsList.value.map { it.tango } else emptyList()

        val enableFlickPref = (enableTypoCorrectionJapaneseFlickKeyboardPreference == true)
        val enableTypoCorrectionJapaneseFlick =
            enableFlickPref && (qwertyMode.value == TenKeyQWERTYMode.Default || qwertyMode.value == TenKeyQWERTYMode.Sumire)
        val enableTypoCorrectionQwertyEnglish =
            (enableTypoCorrectionQwertyEnglishKeyboardPreference == true) &&
                    (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && !mainView.qwertyView.getRomajiMode()))

        val engineCandidates = withContext(Dispatchers.Default) {
            if (bunsetsuSeparation == true) {
                val result = kanaKanjiEngine.getCandidatesOriginalWithBunsetsu(
                    input = insertString,
                    n = nBest ?: 4,
                    mozcUtPersonName = mozcUTPersonName,
                    mozcUTPlaces = mozcUTPlaces,
                    mozcUTWiki = mozcUTWiki,
                    mozcUTNeologd = mozcUTNeologd,
                    mozcUTWeb = mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
                    isOmissionSearchEnable = isOmissionSearchEnable ?: false,
                    enableTypoCorrectionJapaneseFlick = enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = enableTypoCorrectionQwertyEnglish
                )
                bunsetsuPositionList = result.second
                result.first
            } else {
                kanaKanjiEngine.getCandidatesOriginal(
                    input = insertString,
                    n = nBest ?: 4,
                    mozcUtPersonName = mozcUTPersonName,
                    mozcUTPlaces = mozcUTPlaces,
                    mozcUTWiki = mozcUTWiki,
                    mozcUTNeologd = mozcUTNeologd,
                    mozcUTWeb = mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
                    isOmissionSearchEnable = isOmissionSearchEnable ?: false,
                    enableTypoCorrectionJapaneseFlick = enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = enableTypoCorrectionQwertyEnglish
                )
            }
        }

        val result = if (conversionCandidatesRomajiEnablePreference == true) {
            val romajiConversionResultList: List<Candidate> = withContext(Dispatchers.Default) {
                getRomajiCandidates(insertString = insertString)
            }
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates + romajiConversionResultList
        } else {
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        }

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

    private suspend fun getSuggestionList(
        insertString: String,
        mainView: MainLayoutBinding
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

        val resultFromLearnDictionary =
            if (enablePredictionSearchLearnDictionaryPreference == true) {
                withContext(Dispatchers.IO) {
                    val prefixMatchNumber = (learnPredictionPreference ?: 2) - 1
                    if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                    learnRepository.predictiveSearchByInput(
                        prefix = insertString, limit = 4
                    ).map {
                        Candidate(
                            string = it.out,
                            type = (34).toByte(),
                            length = (it.input.length).toUByte(),
                            score = it.score.toInt()
                        )
                    }.sortedBy { it.score }
                }
            } else {
                emptyList()
            }

        val ngWords =
            if (isNgWordEnable == true) ngWordsList.value.map { it.tango } else emptyList()

        val enableFlickPref = (enableTypoCorrectionJapaneseFlickKeyboardPreference == true)
        val enableTypoCorrectionJapaneseFlick =
            enableFlickPref && (qwertyMode.value == TenKeyQWERTYMode.Default || qwertyMode.value == TenKeyQWERTYMode.Sumire)

        val enableTypoCorrectionQwertyEnglish =
            (enableTypoCorrectionQwertyEnglishKeyboardPreference == true) &&
                    (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && !mainView.qwertyView.getRomajiMode()))

        val engineCandidates = withContext(Dispatchers.Default) {
            if (bunsetsuSeparation == true) {
                val candidates = kanaKanjiEngine.getCandidatesWithBunsetsuSeparation(
                    input = insertString,
                    n = nBest ?: 4,
                    mozcUtPersonName = mozcUTPersonName,
                    mozcUTPlaces = mozcUTPlaces,
                    mozcUTWiki = mozcUTWiki,
                    mozcUTNeologd = mozcUTNeologd,
                    mozcUTWeb = mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
                    isOmissionSearchEnable = isOmissionSearchEnable ?: false,
                    enableTypoCorrectionJapaneseFlick = enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = enableTypoCorrectionQwertyEnglish
                )
                bunsetsuPositionList = candidates.second
                Timber.d("handleJapaneseModeSpaceKeyWithBunsetsu: $bunsetsuPositionList ${isHenkan.get()} $ngWords $insertString ${candidates.second}")
                candidates.first
            } else {
                kanaKanjiEngine.getCandidates(
                    input = insertString,
                    n = nBest ?: 4,
                    mozcUtPersonName = mozcUTPersonName,
                    mozcUTPlaces = mozcUTPlaces,
                    mozcUTWiki = mozcUTWiki,
                    mozcUTNeologd = mozcUTNeologd,
                    mozcUTWeb = mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
                    isOmissionSearchEnable = isOmissionSearchEnable ?: false,
                    enableTypoCorrectionJapaneseFlick = enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = enableTypoCorrectionQwertyEnglish
                )
            }
        }
        val result = if (conversionCandidatesRomajiEnablePreference == true) {
            val romajiConversionResultList: List<Candidate> = withContext(Dispatchers.Default) {
                getRomajiCandidates(insertString = insertString)
            }
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates + romajiConversionResultList
        } else {
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        }
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

    private fun getLeftContext(inputLength: Int): String {
        val ic = currentInputConnection ?: return ""
        val lengthToGetTextBeforeCursor = (8 + inputLength).coerceAtMost(64)
        // カーソル前のテキストを取得
        val charSequence = ic.getTextBeforeCursor(lengthToGetTextBeforeCursor, 0)
        val text = charSequence?.toString() ?: ""

        Timber.d("getLeftContext: inputLength [$inputLength] text: [$text]")
        // 改行記号 '\n' があれば、それより後ろの部分だけを返す。
        // 改行がない場合は、テキスト全体が返されます。
        return text.substringAfterLast('\n')
    }

    private fun getRightContext(inputLength: Int): String {
        val ic = currentInputConnection ?: return ""
        val lengthToGetTextAfterCursor = (8 + inputLength).coerceAtMost(64)

        // カーソル後のテキストを取得
        val charSequence = ic.getTextAfterCursor(lengthToGetTextAfterCursor, 0)
        val text = charSequence?.toString() ?: ""

        Timber.d("getRightContext: inputLength [$inputLength] text: [$text]")

        return text.substringBefore('\n')
    }

    private suspend fun getSuggestionListWithoutPrediction(
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

        val resultFromLearnDictionary =
            if (enablePredictionSearchLearnDictionaryPreference == true) {
                withContext(Dispatchers.IO) {
                    val prefixMatchNumber = (learnPredictionPreference ?: 2) - 1
                    if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                    learnRepository.predictiveSearchByInput(
                        prefix = insertString, limit = 4
                    ).map {
                        Candidate(
                            string = it.out,
                            type = (34).toByte(),
                            length = (it.input.length).toUByte(),
                            score = it.score.toInt()
                        )
                    }.sortedBy { it.score }
                }
            } else {
                emptyList()
            }

        val ngWords =
            if (isNgWordEnable == true) ngWordsList.value.map { it.tango } else emptyList()
        val engineCandidates = withContext(Dispatchers.Default) {
            if (bunsetsuSeparation == true) {
                val resultWithBunsetsu = kanaKanjiEngine.getCandidatesWithoutPredictionWithBunsetsu(
                    input = insertString,
                    n = nBest ?: 4,
                    mozcUtPersonName = mozcUTPersonName,
                    mozcUTPlaces = mozcUTPlaces,
                    mozcUTWiki = mozcUTWiki,
                    mozcUTNeologd = mozcUTNeologd,
                    mozcUTWeb = mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
                )
                bunsetsuPositionList = resultWithBunsetsu.second
                resultWithBunsetsu.first
            } else {
                kanaKanjiEngine.getCandidatesWithoutPrediction(
                    input = insertString,
                    n = nBest ?: 4,
                    mozcUtPersonName = mozcUTPersonName,
                    mozcUTPlaces = mozcUTPlaces,
                    mozcUTWiki = mozcUTWiki,
                    mozcUTNeologd = mozcUTNeologd,
                    mozcUTWeb = mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = if (isLearnDictionaryMode == true) learnRepository else null,
                )
            }
        }

        val result = if (conversionCandidatesRomajiEnablePreference == true) {
            val romajiConversionResultList: List<Candidate> = withContext(Dispatchers.Default) {
                getRomajiCandidates(insertString = insertString)
            }
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates + romajiConversionResultList
        } else {
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        }

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

    private fun getSuggestionListEnglishKana(
        insertString: String,
    ): List<Candidate> {
        val engineCandidates = kanaKanjiEngine.getCandidatesEnglishKana(
            input = insertString,
        )
        return engineCandidates.distinctBy { it.string }
    }

    private fun getRomajiCandidates(insertString: String): List<Candidate> {
        val conversionString = romajiConverter?.hiraganaToRomaji(insertString)
        if (conversionString != null) {
            val conversionFirstCapitalString =
                conversionString.replaceFirstChar { it.uppercaseChar() }
            val conversionFirstCapitalStringHankaku =
                conversionFirstCapitalString.toHankakuAlphabet()
            val conversionHankaku = conversionString.toHankakuAlphabet()
            return listOf(
                Candidate(
                    string = conversionString,
                    type = (30).toByte(),
                    length = insertString.length.toUByte(),
                    score = 29000
                ),
                Candidate(
                    string = conversionHankaku,
                    type = (31).toByte(),
                    length = insertString.length.toUByte(),
                    score = 29001
                ),
                Candidate(
                    string = conversionFirstCapitalString,
                    type = (30).toByte(),
                    length = insertString.length.toUByte(),
                    score = 29002
                ),
                Candidate(
                    string = conversionFirstCapitalStringHankaku,
                    type = (31).toByte(),
                    length = insertString.length.toUByte(),
                    score = 29003
                ),
                Candidate(
                    string = conversionString.uppercase(),
                    type = (30).toByte(),
                    length = insertString.length.toUByte(),
                    score = 29004
                ),
                Candidate(
                    string = conversionHankaku.uppercase(),
                    type = (31).toByte(),
                    length = insertString.length.toUByte(),
                    score = 29005
                ),
            )
        } else {
            return emptyList()
        }
    }

    /**
     * カーソル前の文字に応じて、単語または記号1つを削除します。
     * - カーソル直前の文字が指定記号の場合：その記号を1つだけ削除します。
     * - カーソル直前の文字がそれ以外の場合：その単語を末尾まで削除します。
     */
    private fun deleteWordOrSymbolsBeforeCursor(insertString: String) {
        val inputConnection = currentInputConnection ?: return
        if (isHenkan.get()) return
        if (stringInTail.get().isNotEmpty()) return

        if (insertString.isNotEmpty()) {
            _inputString.update { "" }
            setComposingText("", 0)
            finishComposingText()
        } else {
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            if (textBeforeCursor.isEmpty()) return

            val charsToDelete = setOf(
                '。', '、', '！', '？', '「', '」', '『', '』', ',', '.', '!', "?", ' ', '　', '\n'
            )

            var deleteCount = 0

            // カーソル直前の1文字が指定記号かどうかをチェック
            if (textBeforeCursor.last() in charsToDelete) {
                // 記号の場合、1文字だけ削除する
                deleteCount = 1
            } else {
                // 記号でない場合、空白まで遡って単語の長さを数える
                for (char in textBeforeCursor.reversed()) {
                    if (char.isWhitespace() || char in charsToDelete) {
                        // 単語の区切り（空白または記号）が見つかったら停止
                        break
                    }
                    deleteCount++
                }
            }

            if (deleteCount > 0) {
                inputConnection.deleteSurroundingText(deleteCount, 0)
            }
        }
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
                                }
                            }
                        }
                        deleteLastGraphemeOrSelection()
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
        Timber.d("setEnterKeyPress: $currentInputType")
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
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
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
                    if (deleteKeyHighLight == true) {
                        handleDeleteKeyInHenkan(suggestions, insertString)
                    } else {
                        cancelHenkanByLongPressDeleteKey()
                        hasConvertedKatakana = isLiveConversionEnable == true
                    }
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
                                Timber.d("delete: $beforeChar")
                                suggestionAdapter?.apply {
                                    setUndoEnabled(true)
                                    setUndoPreviewText(deletedBuffer.toString())
                                }
                            }
                        }
                    }
                }
                deleteLastGraphemeOrSelection()
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
                            InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) {
                                if (bunsetsuSeparation == true) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(
                                        this, suggestions, insertString
                                    )
                                } else {
                                    handleJapaneseModeSpaceKey(
                                        this, suggestions, insertString
                                    )
                                }
                            }

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

    private fun handleSpaceKeyClickFloating(
        isFlick: Boolean,
        insertString: String,
        suggestions: List<Candidate>,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        if (insertString.isNotBlank()) {
            floatingKeyboardLayoutBinding.keyboardViewFloating.let { tenkey ->
                when (tenkey.currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        if (suggestions.isNotEmpty()) {
                            if (bunsetsuSeparation == true) {
                                handleJapaneseModeSpaceKeyWithBunsetsuFloating(
                                    floatingKeyboardLayoutBinding, suggestions, insertString
                                )
                            } else {
                                handleJapaneseModeSpaceKeyFloating(
                                    floatingKeyboardLayoutBinding, suggestions, insertString
                                )
                            }
                        }
                    }

                    else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
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
                        val insertStringEndWithN = if (isDefaultRomajiHenkanMap) {
                            romajiConverter?.flushZenkaku(insertString)?.first
                        } else {
                            romajiConverter?.flush(insertString)?.first
                        }
                        if (insertStringEndWithN == null) {
                            _inputString.update { insertString }
                            if (suggestions.isNotEmpty()) {
                                if (bunsetsuSeparation == true) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(
                                        this, suggestions, insertString
                                    )
                                } else {
                                    handleJapaneseModeSpaceKey(
                                        this, suggestions, insertString
                                    )
                                }
                            }
                        } else {
                            _inputString.update { insertStringEndWithN }
                            scope.launch {
                                delay(64)
                                val newSuggestionList =
                                    suggestionAdapter?.suggestions ?: emptyList()
                                if (newSuggestionList.isNotEmpty()) {
                                    if (bunsetsuSeparation == true) {
                                        handleJapaneseModeSpaceKeyWithBunsetsu(
                                            mainView, newSuggestionList, insertString
                                        )
                                    } else {
                                        handleJapaneseModeSpaceKey(
                                            mainView, newSuggestionList, insertString
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            val romajiMode = mainView.qwertyView.getRomajiMode()
            Timber.d("handleSpaceKeyClickInQWERTY: $romajiMode")
            if (romajiMode && qwertyEnableZenkakuSpacePreference == true) {
                handleSpaceKeyClick(false, insertString, suggestions, mainView)
            } else {
                setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
            }
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

    private fun handleJapaneseModeSpaceKeyWithBunsetsu(
        mainView: MainLayoutBinding, suggestions: List<Candidate>, insertString: String
    ) {
        val position = bunsetsuPositionList?.firstOrNull()

        if (position != null && stringInTail.get().isEmpty()) {
            // 区切り位置がある場合：文字列を分割する
            val head = insertString.substring(0, position)
            val tail = insertString.substring(position)

            _inputString.update { head }
            stringInTail.set(tail)
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: $bunsetsuPositionList | head: $head, tail: $tail $stringInTail"
            )
            isHenkan.set(true)
            henkanPressedWithBunsetsuDetect = true
            bunsetsuPositionList?.let {
                if (it.size > 1) {
                    bunsetusMultipleDetect = true
                }
            }
        } else {
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
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: No split position. Full string to tail: $insertString"
            )
        }
    }

    private fun handleJapaneseModeSpaceKeyWithBunsetsuFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        suggestions: List<Candidate>,
        insertString: String
    ) {
        val position = bunsetsuPositionList?.firstOrNull()

        if (position != null && stringInTail.get().isEmpty()) {
            // 区切り位置がある場合：文字列を分割する
            val head = insertString.substring(0, position)
            val tail = insertString.substring(position)

            _inputString.update { head }
            stringInTail.set(tail)
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: $bunsetsuPositionList | head: $head, tail: $tail $stringInTail"
            )
            isHenkan.set(true)
            henkanPressedWithBunsetsuDetect = true
        } else {
            isHenkan.set(true)
            suggestionClickNum += 1
            suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
            floatingKeyboardLayoutBinding.suggestionRecyclerView.apply {
                smoothScrollToPosition(
                    (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
                )
                suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
            }
            setConvertLetterInJapaneseFromButtonFloating(
                suggestions,
                true,
                floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding,
                insertString
            )
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: No split position. Full string to tail: $insertString"
            )
        }
    }

    private fun handleJapaneseModeSpaceKeyFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        suggestions: List<Candidate>,
        insertString: String
    ) {
        isHenkan.set(true)
        suggestionClickNum += 1
        suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
        floatingKeyboardLayoutBinding.suggestionRecyclerView.apply {
            smoothScrollToPosition(
                (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
            )
            suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
        }
        setConvertLetterInJapaneseFromButtonFloating(
            suggestions, true, floatingKeyboardLayoutBinding, insertString
        )
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
                            setCusrorLeftAfterCloseBracket(insertString)
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
                            finishInputEnterKey()
                            setCusrorLeftAfterCloseBracket(insertString)
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

    private fun handleNonEmptyInputEnterKeyFloating(
        suggestions: List<Candidate>,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        insertString: String
    ) {
        floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
            when (val inputMode = currentInputMode.value) {
                InputMode.ModeJapanese -> {
                    if (isHenkan.get()) {
                        handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                    } else {
                        finishInputEnterKey()
                        setCusrorLeftAfterCloseBracket(insertString)
                    }
                }

                else -> {
                    finishInputEnterKey()
                    setCusrorLeftAfterCloseBracket(insertString)
                }
            }
        }
    }

    private fun setCusrorLeftAfterCloseBracket(insertString: String) {
        if (insertString.isOnlyTwoCharBracketPair()) {
            moveCursorLeftBySelection()
        }
    }

    private fun moveCursorLeftBySelection() {
        if (currentInputConnection == null) return

        beginBatchEdit()
        try {
            val req = ExtractedTextRequest()
            req.token = 0
            req.flags = 0
            val extractedText = getExtractedText(req, 0)

            if (extractedText != null) {
                val start = extractedText.selectionStart
                if (start > 0) {
                    setSelection(start - 1, start - 1)
                }
            } else {
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
            }
        } catch (e: Exception) {
            Timber.e(e)
        } finally {
            endBatchEdit()
        }
    }

    private fun handleLeftCursorMoveAction() {
        Timber.d("handleLeftCursorMoveAction: called")
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
    }

    private fun handleRightCursorMoveAction() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
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
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        if (candidateTabVisibility == true) {
            mainView.candidateTabLayout.isVisible = false
            val tab = mainView.candidateTabLayout.getTabAt(0)
            mainView.candidateTabLayout.selectTab(tab)
        }
        mainView.shortcutToolbarRecyclerview.isVisible =
            shortcutTollbarVisibility == true
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun handleEmptyInputEnterKeyFloating(floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding) {
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            setEnterKeyPress()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        setDrawableToEnterKeyCorrespondingToImeOptionsFloating(floatingKeyboardLayoutBinding)
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

    private fun setDrawableToEnterKeyCorrespondingToImeOptionsFloating(floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding) {
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
        floatingKeyboardLayoutBinding.keyboardViewFloating.setSideKeyEnterDrawable(currentDrawable)
    }

    private fun finishInputEnterKey() {
        _inputString.update { "" }
        finishComposingText()
        resetFlagsEnterKeyNotHenkan()
    }

    /**
     * Deletes the last grapheme cluster before the cursor or deletes the current selection.
     * This correctly handles complex emojis and user text selections.
     */
    private fun deleteLastGraphemeOrSelection() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun handleLeftKeyPress(gestureType: GestureType, insertString: String) {
        Timber.d("called handleLeftKeyPress $insertString ${stringInTail.get()} $gestureType")
        if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            when (gestureType) {
                GestureType.FlickRight -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                }

                GestureType.FlickTop -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                }

                GestureType.FlickLeft -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
                }

                GestureType.FlickBottom -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                }

                GestureType.Null -> {}
                GestureType.Down -> {}
                GestureType.Tap -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
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
                    if (isKeyboardFloatingMode == true) {
                        floatingKeyboardBinding?.let { mainView ->
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, false
                            )
                        }
                    } else {
                        mainLayoutBinding?.let { mainView ->
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, false
                            )
                        }
                    }
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
        }
    }

    private fun asyncLeftLongPress() {
        Timber.d("asyncLeftLongPress called")
        if (leftLongPressJob?.isActive == true) return
        leftLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null

            while (isActive && leftCursorKeyLongKeyPressed.get() && !onLeftKeyLongPressUp.get()) {

                val insertString = inputString.value

                Timber.d("asyncLeftLongPress called while loop")

                // tail があり composing が空 → Idle で抜ける
                if (stringInTail.get().isNotEmpty() && insertString.isEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Idle
                    handleLeftCursorMoveAction()
                    break
                }

                if (insertString.isNotEmpty()) {
                    updateLeftInputString(insertString)
                } else if (stringInTail.get().isEmpty() && !isCursorAtBeginning()) {
                    if (selectMode.value) {
                        extendOrShrinkLeftOneChar()
                    } else {
                        handleLeftCursorMoveAction()
                    }
                } else {
                    handleLeftCursorMoveAction()
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
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { mainView ->
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false
                        )
                    }
                } else {
                    mainLayoutBinding?.let { mainView ->
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false
                        )
                    }
                }
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
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                }

                GestureType.FlickTop -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                }

                GestureType.FlickLeft -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                }

                GestureType.FlickBottom -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                }

                GestureType.Null -> {}
                GestureType.Down -> {}
                GestureType.Tap -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
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
                handleRightCursorMoveAction()
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
        Timber.d("handleNonHenkan: $insertString ${stringInTail.get()}")
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
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                sendKeyChar(charToSend)
            }

            in passwordTypes -> {
                if (showCandidateInPasswordPreference == true) {
                    sendKeyChar(charToSend)
                } else {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                }
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
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                sendKeyChar(charToSend)
            }

            in passwordTypes -> {
                if (showCandidateInPasswordPreference == true) {
                    sendKeyChar(charToSend)
                } else {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                }
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
            if (!onKeyboardSwitchLongPressUp && qwertyMode.value != TenKeyQWERTYMode.Custom && tenkeyShowIMEButtonPreference == true) {
                switchNextKeyboard()
            }
        }
    }

    private fun dakutenSmallLetterFloating(
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
        }
    }

    // 2) 次のモードに切り替える関数
    fun switchNextKeyboard() {
        if (keyboardOrder.isEmpty()) return

        // モジュール演算で自動的に 0 に戻る
        val nextIndex = (currentKeyboardOrder + 1) % keyboardOrder.size
        val nextType = keyboardOrder[nextIndex]

        when (nextType) {
            KeyboardType.TENKEY -> {
                mainLayoutBinding?.keyboardView?.setCurrentMode(InputMode.ModeJapanese)
            }

            KeyboardType.SUMIRE -> {
                mainLayoutBinding?.keyboardView?.setCurrentMode(InputMode.ModeJapanese)
            }

            KeyboardType.ROMAJI -> {
                mainLayoutBinding?.keyboardView?.setCurrentMode(InputMode.ModeJapanese)
            }

            KeyboardType.QWERTY -> {
                mainLayoutBinding?.keyboardView?.setCurrentMode(InputMode.ModeEnglish)
            }

            KeyboardType.CUSTOM -> {}
        }

        // 統一された showKeyboard 関数を呼び出す
        showKeyboard(nextType)

        currentKeyboardOrder = nextIndex
        if (enableShowLastShownKeyboardInRestart == true) {
            appPreference.save_last_used_keyboard_position_preference = nextIndex
        }

        if (qwertyMode.value == TenKeyQWERTYMode.Number) {
            val type = when (nextType) {
                KeyboardType.TENKEY -> TenKeyQWERTYMode.Default
                KeyboardType.SUMIRE -> TenKeyQWERTYMode.Sumire
                KeyboardType.QWERTY -> TenKeyQWERTYMode.TenKeyQWERTY
                KeyboardType.ROMAJI -> TenKeyQWERTYMode.TenKeyQWERTYRomaji
                KeyboardType.CUSTOM -> TenKeyQWERTYMode.Custom
            }
            _tenKeyQWERTYMode.update { type }
        }

        mainLayoutBinding?.let { mainView ->
            setKeyboardSizeSwitchKeyboard(mainView)
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
            if (!onKeyboardSwitchLongPressUp && tenkeyShowIMEButtonPreference == true) {
                switchNextKeyboard()
            }
        }
    }

    private fun smallBigLetterConversionEnglishFloating(
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

    private fun handleDakutenSmallLetterKeyFloating(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        gestureType: GestureType
    ) {
        floatingKeyboardLayoutBinding.keyboardViewFloating.let {
            when (it.currentInputMode.value) {
                InputMode.ModeJapanese -> {
                    dakutenSmallLetterFloating(
                        sb, insertString, gestureType
                    )
                }

                InputMode.ModeEnglish -> {
                    smallBigLetterConversionEnglishFloating(sb, insertString)
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
            henkanPressedWithBunsetsuDetect = false
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
        Timber.d("setConvertLetterInJapaneseFromButton ${listIterator.hasPrevious()} ${listIterator.hasNext()} $isSpaceKey")
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

    private fun setConvertLetterInJapaneseFromButtonFloating(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        insertString: String
    ) {
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        when {
            !listIterator.hasPrevious() && isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                floatingKeyboardLayoutBinding.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                floatingKeyboardLayoutBinding.suggestionRecyclerView.smoothScrollToPosition(0)
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
        Timber.d("setSpaceKeyActionEnglishAndNumberNotEmpty: $insertString ${stringInTail.get()}")
        if (stringInTail.get().isNotEmpty()) {
            val extractedText = getExtractedText(ExtractedTextRequest(), 0)
            val currentCursorPosition = extractedText?.selectionEnd ?: 0
            commitText("$insertString $stringInTail", 1)
            val newCursorPosition =
                (currentCursorPosition - stringInTail.get().length + 1).coerceAtLeast(0)
            stringInTail.set("")
            setSelection(newCursorPosition, newCursorPosition)
            Timber.d("setSpaceKeyActionEnglishAndNumberNotEmpty: $currentCursorPosition ${extractedText?.text}")
        } else {
            commitText("$insertString ", 1)
        }
        _inputString.update {
            ""
        }
        if (isHenkan.get()) {
            suggestionAdapter?.suggestions = emptyList()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberEmpty(isFlick: Boolean) {
        Timber.d("setSpaceKeyActionEnglishAndNumberEmpty: $isFlick ${stringInTail.get()}")
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
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private var isFirstClickHasStringTail = false

    private fun setSuggestionComposingText(suggestions: List<Candidate>, insertString: String) {
        if (suggestionClickNum == 1 && stringInTail.get().isNotEmpty()) {
            isFirstClickHasStringTail = true
        }

        Timber.d("setSuggestionComposingText: $isFirstClickHasStringTail $suggestionClickNum ${stringInTail.get()}")

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
            applyComposingText(
                text = fullText,
                highlightLength = correctedReading.length,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputConversionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.orange)
                } else {
                    getColor(com.kazumaproject.core.R.color.orange)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputConversionTextColor
                } else {
                    null
                }
            )
            return
        }
        val fullText = suggestionText + stringInTail
        applyComposingText(
            text = fullText,
            highlightLength = suggestionText.length,
            backgroundColor = if (customComposingTextPreference == true) {
                inputConversionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.orange)
            } else {
                getColor(com.kazumaproject.core.R.color.orange)
            },
            textColor = if (customComposingTextPreference == true) {
                inputConversionTextColor
            } else {
                null
            }
        )
    }

    /**
     * ComposingTextを適用する（ハイライト指定あり）
     */
    private fun applyComposingText(
        text: String,
        highlightLength: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val spannableString = SpannableString(text)

        spannableString.apply {
            // 背景色
            setSpan(
                BackgroundColorSpan(backgroundColor),
                0,
                highlightLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // テキスト色
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    highlightLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

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
            hasHardwareKeyboardConnected = true
            floatingDockWindow?.dismiss()
            floatingModeSwitchWindow?.dismiss()
            floatingCandidateWindow?.dismiss()
            scope.launch {
                delay(32)
                _physicalKeyboardEnable.emit(true)
            }
            isKeyboardFloatingMode = false
        } else {
            Timber.d("No physical keyboard is connected.")
            floatingDockWindow?.dismiss()
            floatingCandidateWindow?.dismiss()
            floatingModeSwitchWindow?.dismiss()
            scope.launch {
                _physicalKeyboardEnable.emit(false)
            }
            isKeyboardFloatingMode = appPreference.is_floating_mode ?: false
        }
    }

    private fun providesZenzEngine(context: Context): ZenzEngine {
        val defaultAssetFileName = "ggml-model-Q5_K_M.gguf"
        val defaultDestFile = File(context.filesDir, defaultAssetFileName)

        fun ensureDefaultModelCopied(): File {
            if (!defaultDestFile.exists()) {
                context.assets.open(defaultAssetFileName).use { input ->
                    FileOutputStream(defaultDestFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return defaultDestFile
        }

        fun copyUriToInternalFile(uriString: String): File {
            val uri = uriString.toUri()
            val dest = File(context.filesDir, "zenz_custom_model.gguf")

            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for uri=$uri" }
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            return dest
        }

        val customUri = AppPreference.zenz_model_uri_preference

        // 1) まずユーザー指定があれば試す
        if (customUri.isNotBlank()) {
            try {
                val customFile = copyUriToInternalFile(customUri)
                ZenzEngine.initModel(customFile.absolutePath)
                Timber.d("Zenz model initialized with custom file: ${customFile.absolutePath}")
                return ZenzEngine
            } catch (e: Exception) {
                Timber.e(e, "Zenz Failed to init Zenz with custom model. Fallback to default.")
                // フォールバック継続
            }
        }

        // 2) デフォルトで初期化（ここも失敗し得るので try/catch）
        try {
            val defaultFile = ensureDefaultModelCopied()
            ZenzEngine.initModel(defaultFile.absolutePath)
            Timber.d("Zenz model initialized with default asset file: ${defaultFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Zenz Failed to init Zenz with default model as well.")
            // ここまで失敗する場合は致命的。ZenzEngine が内部で未初期化でもアプリが落ちない設計なら return は可能。
            // 必要ならここで例外を投げる/機能OFF扱いにするなど方針を決める。
        }

        return ZenzEngine
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
            hasHardwareKeyboardConnected = true
            scope.launch {
                _physicalKeyboardEnable.emit(true)
            }
            isKeyboardFloatingMode = false
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
        hasHardwareKeyboardConnected = false
        scope.launch {
            _physicalKeyboardEnable.emit(false)
        }
    }
}
