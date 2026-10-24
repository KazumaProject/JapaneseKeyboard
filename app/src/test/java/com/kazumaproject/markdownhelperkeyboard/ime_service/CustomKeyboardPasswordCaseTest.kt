package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.ImportableKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonImporter
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.renderCustomKeyboardToggles
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.TenKey
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomKeyboardPasswordCaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val editorActions = mutableListOf<Int>()
    private val editor = object : BaseInputConnection(View(context), true) {
        private val content = SpannableStringBuilder().apply { Selection.setSelection(this, 0) }
        override fun getEditable() = content
        override fun performEditorAction(actionCode: Int): Boolean {
            editorActions.add(actionCode)
            return true
        }
    }
    private val activityController = Robolectric.buildActivity(Activity::class.java).setup()
    private val binding = mock<MainLayoutBinding>().also {
        val keyboard = mock<TenKey>()
        doReturn(MutableStateFlow(InputMode.ModeJapanese)).`when`(keyboard).currentInputMode
        ReflectionHelpers.setField(it, "keyboardView", keyboard)
    }
    private val service = run {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        // Lazy service delegates capture the original instance, so attach before spying.
        spy(IMEService().apply {
            appPreference = AppPreference
            ReflectionHelpers.callInstanceMethod<Unit>(
                this,
                "attachBaseContext",
                ClassParameter.from(Context::class.java, context),
            )
        })
    }.also {
        doReturn(editor).`when`(it).getCurrentInputConnection()
        ReflectionHelpers.setField(it, "listAdapter", mock<com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter>())
        ReflectionHelpers.setField(it, "showCandidateInPasswordPreference", true)
        ReflectionHelpers.setField(it, "suppressSuggestions", true)
        ReflectionHelpers.setField(it, "isCustomLayoutDirectMode", false)
        ReflectionHelpers.setField(it, "isCustomLayoutRomajiMode", false)
        ReflectionHelpers.setField(it, "currentInputBehavior", ResolvedInputBehavior.COMPOSING_TEXT)
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(it, "_tenKeyQWERTYMode").value =
            TenKeyQWERTYMode.Custom
        ReflectionHelpers.setField(
            it,
            "composingTextArbiter",
            ComposingTextArbiter(
                writeComposingText = { text, cursor -> editor.setComposingText(text, cursor) },
                finishComposingText = { editor.finishComposingText() },
            ),
        )
    }

    private val parsedMainEnglish: ImportableKeyboardLayout by lazy {
        val json = javaClass.getResourceAsStream("/custom_keyboard/password_main_english_layout.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing メイン英語 regression fixture")
        when (val result = KeyboardLayoutJsonImporter.parse(json)) {
            is KeyboardLayoutImportResult.Success -> result.layouts.single()
            is KeyboardLayoutImportResult.PartialSuccess -> result.layouts.single()
            is KeyboardLayoutImportResult.Failure -> error("Could not parse メイン英語 fixture: ${result.error}")
        }
    }

    private val repository = KeyboardRepository(mock<KeyboardLayoutDao>())

    private val keyboardLayout: KeyboardLayout by lazy {
        val imported = parsedMainEnglish
        assertEquals("メイン英語", imported.layout.name)
        assertEquals(false, imported.layout.isRomaji)
        assertEquals(false, imported.layout.isDirectMode)
        val fullLayout = FullKeyboardLayout(
            layout = imported.layout,
            keysWithFlicks = imported.keysWithFlicks.map { key ->
                KeyWithFlicks(
                    key = key.key,
                    flicks = key.flicks,
                    circularFlicks = key.circularFlicks,
                    twoStepFlicks = key.twoStepFlicks,
                    longPressFlicks = key.longPressFlicks,
                    twoStepLongPressFlicks = key.twoStepLongPressFlicks,
                )
            },
            spacers = imported.spacers,
        )
        val convertToUiModel = KeyboardRepository::class.java.getDeclaredMethod(
            "convertToUiModel",
            FullKeyboardLayout::class.java,
        ).apply { isAccessible = true }
        val converted = convertToUiModel.invoke(repository, fullLayout) as KeyboardLayout
        repository.convertLayout(converted)
    }

    private val flickView: FlickKeyboardView by lazy {
        val view = FlickKeyboardView(
            ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
            ),
        )
        ReflectionHelpers.callInstanceMethod<Unit>(
            service,
            "configureFlickKeyboardView",
            ClassParameter.from(FlickKeyboardView::class.java, view),
            ClassParameter.from(MainLayoutBinding::class.java, binding),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        )
        activityController.get().setContentView(view)
        view.setKeyboard(keyboardLayout)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(KEYBOARD_WIDTH, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(KEYBOARD_HEIGHT, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, KEYBOARD_WIDTH, KEYBOARD_HEIGHT)
        view
    }

    private val input
        get() = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")

    private val shift
        get() = ReflectionHelpers.getField<CustomKeyboardShiftState>(
            service,
            "customKeyboardShiftState",
        )

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(
            service,
            "kanaKanjiConversionDispatcher",
        ).close()
    }

    @Test
    fun importedMainEnglish_hasCaseToggleTapDoubleTapAndCapsUpMappings() {
        val imported = parsedMainEnglish
        assertEquals(false, imported.layout.isRomaji)
        assertEquals(false, imported.layout.isDirectMode)

        val caseKey = imported.keysWithFlicks.single {
            it.key.row == CASE_KEY_ROW && it.key.column == CASE_KEY_COLUMN
        }
        assertEquals("ToggleCase", caseKey.key.label)
        assertEquals(KeyType.CROSS_FLICK, caseKey.key.keyType)
        assertEquals(KeyAction.ToggleCase, KeyActionMapper.toKeyAction(caseKey.key.action))
        assertEquals(KeyAction.ToggleCase, KeyActionMapper.toKeyAction(caseKey.key.doubleTapAction))
        assertEquals("EXCLUSIVE", caseKey.key.doubleTapPolicy)
        assertEquals(
            "TOGGLE_CASE",
            caseKey.flicks.single { it.flickDirection == FlickDirection.TAP }.actionType,
        )
        assertEquals(
            "CapLockKey",
            caseKey.flicks.single { it.flickDirection == FlickDirection.UP }.actionType,
        )
    }

    @Test
    fun passwordFields_toggleCaseUppercasesTheComposingCharacter() {
        for (type in PASSWORD_TYPES) {
            reset(type)
            tap(ABC_ROW, ABC_COLUMN)
            assertEquals("a", editor.editable.toString())
            tap(CASE_KEY_ROW, CASE_KEY_COLUMN)
            assertEquals("A", editor.editable.toString())
            assertEquals("A", input.value)
            assertEquals(
                "ローマ字OFF/direct OFF の $type で a/A タップ後はShift OFFを維持する",
                CustomKeyboardShiftState.OFF,
                shift,
            )
        }
    }

    @Test
    fun passwordFields_toggleCaseUppercasesCharacterProducedByActualUpFlick() {
        for (type in PASSWORD_TYPES) {
            reset(type)
            flickUp(ABC_ROW, ABC_COLUMN)
            assertEquals("c", editor.editable.toString())
            tap(CASE_KEY_ROW, CASE_KEY_COLUMN)
            assertEquals("C", editor.editable.toString())
            assertEquals("C", input.value)
        }
    }
    @Test
    fun passwordFields_capsUpFlickLocksUppercaseUntilToggledOff() {
        for (type in PASSWORD_TYPES) {
            reset(type)
            flickUp(CASE_KEY_ROW, CASE_KEY_COLUMN)
            assertEquals(CustomKeyboardShiftState.LOCKED, shift)
            tap(ABC_ROW, ABC_COLUMN)
            tap(DEF_ROW, DEF_COLUMN)
            assertEquals("AD", editor.editable.toString())
            assertEquals(CustomKeyboardShiftState.LOCKED, shift)

            flickUp(CASE_KEY_ROW, CASE_KEY_COLUMN)
            assertEquals(CustomKeyboardShiftState.OFF, shift)
            tap(ABC_ROW, ABC_COLUMN)
            assertEquals("ADa", editor.editable.toString())
        }
    }

    @Test
    fun directMode_passwordCharacterCommitsWithoutComposingText() {
        reset(InputTypeForIME.TextPassword, directMode = true)
        tap(ABC_ROW, ABC_COLUMN)
        assertEquals("a", editor.editable.toString())
        assertEquals("", input.value)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
    }

    @Test
    fun nonCustomPasswordKeyboard_keepsImmediateCharacterCommit() {
        reset(InputTypeForIME.TextPassword)
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(
            service,
            "_tenKeyQWERTYMode",
        ).value = TenKeyQWERTYMode.Sumire
        ReflectionHelpers.callInstanceMethod<Unit>(
            service,
            "sendCharFlick",
            ClassParameter.from(Char::class.javaPrimitiveType, 'a'),
            ClassParameter.from(String::class.java, ""),
            ClassParameter.from(StringBuilder::class.java, StringBuilder()),
        )
        assertEquals("a", editor.editable.toString())
        assertEquals("", input.value)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
    }

    @Test
    fun passwordEnter_commitsUppercaseAndPerformsEditorActionInOnePress() {
        for (type in PASSWORD_TYPES) {
            reset(type)
            tap(ABC_ROW, ABC_COLUMN)
            tap(CASE_KEY_ROW, CASE_KEY_COLUMN)
            tap(3, 4) // Enter from メイン英語.
            assertEquals("A", editor.editable.toString())
            assertEquals(listOf(EditorInfo.IME_ACTION_DONE), editorActions)
            assertEquals("", input.value)
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
        }
    }

    @Test
    fun passwordEnter_commitsLiteralCalculationWithoutWaitingForTheRenderer() {
        for (type in PASSWORD_TYPES) {
            reset(type)
            // Exercise individual custom text actions, including symbols from other layouts.
            // Leave the composing renderer pending to catch lost or duplicated final input.
            typeText("=1+2", render = false)
            tap(3, 4)
            assertEquals("=1+2", editor.editable.toString())
            assertEquals(listOf(EditorInfo.IME_ACTION_DONE), editorActions)
            assertEquals("", input.value)
        }
    }

    @Test
    fun passwordForceNewLine_commitsLiteralTextAndNewlineInsteadOfEditorAction() {
        reset(InputTypeForIME.TextPassword)
        typeText("a", render = false)
        action(KeyAction.ForceNewLine)
        assertEquals("a\n", editor.editable.toString())
        assertEquals(emptyList<Int>(), editorActions)
        assertEquals("", input.value)
    }

    @Test
    fun passwordSpace_actualLayoutCommitsTextAndHalfWidthSpace() {
        reset(InputTypeForIME.TextPassword)
        tap(ABC_ROW, ABC_COLUMN)
        tap(2, 4) // CommitAndInsertSpace from メイン英語.
        assertEquals("a ", editor.editable.toString())
        assertEquals("", input.value)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.editable))
    }

    @Test
    fun passwordSpace_normalSpaceKeepsWidthSettingWithoutCandidates() {
        for (isFlick in listOf(false, true)) {
            reset(InputTypeForIME.TextPassword)
            tap(ABC_ROW, ABC_COLUMN)
            ReflectionHelpers.callInstanceMethod<Unit>(
                service,
                "handleSpaceKeyClick",
                ClassParameter.from(Boolean::class.javaPrimitiveType, isFlick),
                ClassParameter.from(String::class.java, input.value),
                ClassParameter.from(List::class.java, emptyList<Any>()),
                ClassParameter.from(MainLayoutBinding::class.java, binding),
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
            )
            assertEquals(if (isFlick) "a " else "a　", editor.editable.toString())
            assertEquals("", input.value)
        }
    }

    private fun action(action: KeyAction) {
        ReflectionHelpers.getField<FlickKeyboardView.OnKeyboardActionListener>(flickView, "listener")
            .onAction(action, false)
    }

    private fun typeText(text: String, render: Boolean) {
        text.forEach { action(KeyAction.Text(it.toString())) }
        if (render) renderComposition()
    }
    private fun reset(type: InputTypeForIME, directMode: Boolean = false) {
        ReflectionHelpers.setField(service, "currentInputType", type)
        ReflectionHelpers.setField(service, "isCustomLayoutDirectMode", directMode)
        ReflectionHelpers.setField(service, "isCustomLayoutRomajiMode", false)
        ReflectionHelpers.setField(service, "currentInputBehavior", ResolvedInputBehavior.COMPOSING_TEXT)
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(
            service,
            "_tenKeyQWERTYMode",
        ).value = TenKeyQWERTYMode.Custom
        ReflectionHelpers.setField(service, "customKeyboardShiftState", CustomKeyboardShiftState.OFF)
        doReturn(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
        }).`when`(service).getCurrentInputEditorInfo()
        editorActions.clear()
        input.value = ""
        editor.editable.clear()
        Selection.setSelection(editor.editable, 0)
        renderToggles()
    }

    private fun tap(row: Int, column: Int) = gesture(row, column, upward = false)

    private fun flickUp(row: Int, column: Int) = gesture(row, column, upward = true)

    private fun gesture(row: Int, column: Int, upward: Boolean) {
        val keyIndex = if (keyboardLayout.items.isNotEmpty()) {
            keyboardLayout.items.indexOfFirst {
                it is KeyItem && it.keyData.row == row && it.keyData.column == column
            }
        } else {
            keyboardLayout.keys.indexOfFirst { it.row == row && it.column == column }
        }
        assertTrue("Missing key at $row,$column", keyIndex >= 0)
        val key = flickView.getChildAt(keyIndex)
        assertTrue("Key must be laid out", key.width > 0 && key.height > 0)
        val x = key.left + key.width / 2f
        val y = key.top + key.height / 2f
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, x, y, downTime, downTime)
        if (upward) {
            touch(MotionEvent.ACTION_MOVE, x, y - FLICK_DISTANCE, downTime, downTime + 5L)
            touch(MotionEvent.ACTION_UP, x, y - FLICK_DISTANCE, downTime, downTime + 10L)
        } else {
            touch(MotionEvent.ACTION_UP, x, y, downTime, downTime + 10L)
        }
        // a/A uses EXCLUSIVE double-tap dispatch, so wait for its single-tap decision.
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        renderComposition()
        renderToggles()
    }

    private fun renderComposition() {
        if (input.value.isNotEmpty()) {
            ReflectionHelpers.callInstanceMethod<Unit>(
                service,
                "processInputString",
                ClassParameter.from(String::class.java, input.value),
                ClassParameter.from(MainLayoutBinding::class.java, binding),
            )
        }
    }

    private fun touch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        val origin = IntArray(2).also(flickView::getLocationOnScreen)
        MotionEvent.obtain(downTime, eventTime, action, x + origin[0], y + origin[1], 0).also { event ->
            event.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
            flickView.onTouchEvent(event)
            event.recycle()
        }
    }

    private fun renderToggles() {
        renderCustomKeyboardToggles(
            flickView,
            shift,
            direct = ReflectionHelpers.getField(service, "isCustomLayoutDirectMode"),
            romaji = ReflectionHelpers.getField(service, "isCustomLayoutRomajiMode"),
        )
    }

    companion object {
        private const val KEYBOARD_WIDTH = 500
        private const val KEYBOARD_HEIGHT = 400
        private const val FLICK_DISTANCE = 70f
        private const val CASE_KEY_ROW = 3
        private const val CASE_KEY_COLUMN = 1
        private const val ABC_ROW = 0
        private const val ABC_COLUMN = 2
        private const val DEF_ROW = 0
        private const val DEF_COLUMN = 3
        private val PASSWORD_TYPES = listOf(
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextVisiblePassword,
        )
    }
}
