package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SumireInputLifecycleTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val context = ContextThemeWrapper(activity, R.style.Theme_MarkdownKeyboard)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context)
        it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1 }).`when`(it).getCurrentInputEditorInfo()
    }
    private val binding = MainLayoutBinding.inflate(LayoutInflater.from(context)).also {
        activity.setContentView(it.root)
        Reflect.setField(service, "mainLayoutBinding", it)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Sumire
        Reflect.callInstanceMethod<Unit>(service, "setupCustomKeyboardListeners", ClassParameter.from(MainLayoutBinding::class.java, it))
        Reflect.callInstanceMethod<Unit>(service, "setSumireLayoutTo", ClassParameter.from(FlickKeyboardView::class.java, it.customLayoutDefault))
    }
    private val listener get() = Reflect.getField<FlickKeyboardView.OnKeyboardActionListener>(binding.customLayoutDefault, "listener")

    @Test fun deletionUpdatesDakutenActionEvenWhenSuggestionsAreSuppressed() {
        Reflect.setField(service, "suppressSuggestions", true)
        val input = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
        fun processInput() = Reflect.callInstanceMethod<Unit>(service, "processInputString",
            ClassParameter.from(String::class.java, input.value),
            ClassParameter.from(MainLayoutBinding::class.java, binding))
        fun dakutenAction() = requireNotNull(keys().single { it.keyId == "dakuten_toggle_key" }.action)

        listener.onAction(KeyAction.Text("あかな"), false)
        processInput()
        assertEquals(KeyAction.InputText("^_^"), dakutenAction())
        listener.onAction(KeyAction.Delete, false)
        processInput()
        assertEquals("あか", input.value)
        assertEquals(KeyAction.ToggleDakuten, dakutenAction())
        listener.onAction(dakutenAction(), false)
        assertEquals("あが", input.value)

        input.value = ""
        listener.onAction(KeyAction.Text("なか"), false)
        processInput()
        assertEquals(KeyAction.ToggleDakuten, dakutenAction())
        listener.onAction(KeyAction.Delete, false)
        processInput()
        assertEquals("な", input.value)
        assertEquals(KeyAction.InputText("^_^"), dakutenAction())
    }

    @Test fun tapAndLongPressCycleBothTheLayoutAndSessionInputMode() {
        for (longPress in listOf(false, true)) {
            Reflect.setField(service, "customKeyboardMode", KeyboardInputMode.HIRAGANA)
            for ((mode, session, label) in listOf(
                Triple(KeyboardInputMode.ENGLISH, InputMode.ModeEnglish, "ABC"),
                Triple(KeyboardInputMode.SYMBOLS, InputMode.ModeNumber, "1"),
                Triple(KeyboardInputMode.HIRAGANA, InputMode.ModeJapanese, "あ"))) {
                if (longPress) listener.onActionLongPress(KeyAction.ChangeInputMode)
                else listener.onAction(KeyAction.ChangeInputMode, false)
                assertEquals(mode, Reflect.getField<KeyboardInputMode>(service, "customKeyboardMode"))
                assertEquals(session, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
                assertTrue("mode=$mode longPress=$longPress labels=${keys().map { it.label }}",
                    keys().any { it.label.lineSequence().first() == label })
            }
        }
    }

    @Test fun heldCustomKeyboardSwitchRunsOnReleaseInsideTheKey() {
        val source = CustomKeyboardLayout(layoutId = 1L, name = "Source", columnCount = 1, rowCount = 1)
        val target = source.copy(layoutId = 2L, name = "Target", stableId = "target")
        Reflect.setField(service, "customLayouts", listOf(source, target))
        Reflect.setField(service, "currentCustomKeyboardStableId", source.stableId)
        service.keyboardRepository = mock(KeyboardRepository::class.java).also {
            doReturn(emptyFlow<KeyboardLayout>()).`when`(it).getFullLayout(target.layoutId)
        }
        val key = displayCustomActionKey(KeyAction.MoveToCustomKeyboard(target.stableId))

        val draggedHold = hold(key)
        touch(key, MotionEvent.ACTION_MOVE, draggedHold, y = -100f)
        touch(key, MotionEvent.ACTION_UP, draggedHold, y = -100f)
        assertEquals(source.stableId, Reflect.getField<String>(service, "currentCustomKeyboardStableId"))

        val hold = hold(key)
        assertEquals(source.stableId, Reflect.getField<String>(service, "currentCustomKeyboardStableId"))
        touch(key, MotionEvent.ACTION_UP, hold)
        assertEquals(target.stableId, Reflect.getField<String>(service, "currentCustomKeyboardStableId"))
    }

    @Test fun heldCustomFlickActionRunsOnceOnRelease() {
        val key = displayCustomActionKey(
            KeyAction.DoNothing,
            mapOf(FlickDirection.UP to FlickAction.Action(KeyAction.ShowEmojiKeyboard))
        )
        val symbols = Reflect.getField<MutableStateFlow<SymbolKeyboardState>>(service, "_keyboardSymbolViewState")

        val hold = hold(key)
        touch(key, MotionEvent.ACTION_MOVE, hold, y = -100f)
        assertFalse(symbols.value.isShown)
        touch(key, MotionEvent.ACTION_UP, hold, y = -100f)
        assertTrue(symbols.value.isShown)
    }

    @Test fun heldCustomDeleteRepeatsUntilRelease() {
        val key = displayCustomActionKey(KeyAction.Delete)
        val repeat = Reflect.getField<AtomicBoolean>(service, "deleteKeyLongKeyPressed")
        val input = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
        input.value = "abcd"

        val hold = hold(key)
        assertTrue(repeat.get())
        val textAtRelease = input.value
        assertTrue(textAtRelease.length < "abcd".length)

        touch(key, MotionEvent.ACTION_UP, hold)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertFalse(repeat.get())
        assertEquals(textAtRelease, input.value)
    }

    @Test fun renderingCurrentSurfaceKeepsControllersAndAnActiveKeyPressed() {
        binding.customLayoutDefault.visibility = View.VISIBLE
        val key = binding.customLayoutDefault.getChildAt(0)
        key.isPressed = true
        val controllers = Reflect.getField<List<Any>>(binding.customLayoutDefault, "crossFlickControllers").toList()
        Reflect.callInstanceMethod<Unit>(service, "renderCurrentKeyboardSurface")
        assertTrue(key.isPressed)
        assertSame(key, binding.customLayoutDefault.getChildAt(0))
        assertEquals(controllers, Reflect.getField<List<Any>>(binding.customLayoutDefault, "crossFlickControllers"))
    }

    private fun displayCustomActionKey(
        action: KeyAction,
        flicks: Map<FlickDirection, FlickAction> = emptyMap()
    ): View {
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Custom
        val id = "custom_special_key"
        val key = KeyData(
            label = "Special", row = 0, column = 0, isFlickable = flicks.isNotEmpty(),
            action = action, isSpecialKey = true, keyId = id,
            keyType = if (flicks.isEmpty()) KeyType.NORMAL else KeyType.CROSS_FLICK
        )
        val maps = if (flicks.isEmpty()) emptyMap()
            else mapOf(id to listOf(flicks + (FlickDirection.TAP to FlickAction.Action(action))))
        val keyboard = binding.customLayoutDefault
        keyboard.clearSumireSpecialKeyActionResolver()
        keyboard.visibility = View.VISIBLE
        keyboard.setKeyboard(KeyboardLayout(listOf(key), maps, columnCount = 1, rowCount = 1))
        keyboard.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        )
        keyboard.layout(0, 0, 200, 100)
        return keyboard.getChildAt(0)
    }

    private fun hold(key: View): Long {
        val downTime = SystemClock.uptimeMillis()
        touch(key, MotionEvent.ACTION_DOWN, downTime)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(310))
        return downTime
    }

    private fun touch(key: View, action: Int, downTime: Long, y: Float = key.height / 2f) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, key.width / 2f, y, 0)
        try {
            key.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun keys() = Reflect.getField<List<Any>>(binding.customLayoutDefault, "keyInfos").map {
        Reflect.getField<KeyData>(it, "keyData")
    }
}
