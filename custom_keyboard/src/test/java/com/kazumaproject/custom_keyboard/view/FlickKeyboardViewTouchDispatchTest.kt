package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.DoubleTapBinding
import com.kazumaproject.custom_keyboard.data.DoubleTapPolicy
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewTouchDispatchTest {

    @Test
    fun setKeyboard_clearsActiveMotionTarget_doesNotDispatchStaleActionUp() {
        val listener = RecordingKeyboardActionListener()
        val keyboardView = keyboardView(listener)
        keyboardView.setKeyboard(pasteKeyLayout())
        layoutKeyboard(keyboardView)

        val pasteKey = keyboardView.getChildAt(0)
        val x = pasteKey.centerX()
        val y = pasteKey.centerY()
        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, downTime = 100L, eventTime = 100L)

        keyboardView.setKeyboard(pasteKeyLayout())
        layoutKeyboard(keyboardView)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, downTime = 100L, eventTime = 120L)

        assertFalse(listener.actions.contains(KeyAction.Paste))
    }

    @Test
    fun touchOutsideKeyHitRect_doesNotFallbackToNearestPasteKey() {
        val listener = RecordingKeyboardActionListener()
        val keyboardView = keyboardView(listener)
        keyboardView.setKeyboard(pasteKeyLayout())
        layoutKeyboard(keyboardView)

        val pasteKey = keyboardView.getChildAt(0)
        val pasteHitRect = Rect().also(pasteKey::getHitRect)
        val x = pasteKey.right + ((keyboardView.width - pasteKey.right) / 2f)
        val y = pasteKey.centerY()

        assertFalse(pasteHitRect.contains(x.toInt(), y.toInt()))

        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, downTime = 200L, eventTime = 200L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, downTime = 200L, eventTime = 220L)

        assertFalse(listener.actions.contains(KeyAction.Paste))
    }

    @Test
    fun normalShiftKey_doubleTap_dispatchesShiftThenCapsLock() {
        val listener = RecordingKeyboardActionListener()
        val keyboardView = keyboardView(listener)
        keyboardView.setKeyboard(shiftKeyLayout())
        layoutKeyboard(keyboardView)
        val shiftKey = keyboardView.getChildAt(0)
        val x = shiftKey.centerX()
        val y = shiftKey.centerY()

        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, 100L, 100L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, 100L, 120L)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, 220L, 220L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, 220L, 240L)

        assertTrue(listener.actions == listOf(KeyAction.ShiftKey, KeyAction.CapLockKey))
    }

    @Test
    fun characterCaseRefreshAfterFirstShiftTap_preservesSecondTapRecognition() {
        lateinit var keyboardView: FlickKeyboardView
        val listener = RecordingKeyboardActionListener { action ->
            if (action == KeyAction.ShiftKey) {
                keyboardView.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
            }
        }
        keyboardView = keyboardView(listener)
        keyboardView.setKeyboard(shiftKeyLayout())
        layoutKeyboard(keyboardView)
        val shiftKey = keyboardView.getChildAt(0)
        val x = shiftKey.centerX()
        val y = shiftKey.centerY()

        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, 300L, 300L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, 300L, 320L)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, 420L, 420L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, 420L, 440L)

        assertTrue(listener.actions == listOf(KeyAction.ShiftKey, KeyAction.CapLockKey))
    }

    @Test
    fun normalCharacterKey_ignoresDoubleTapBindingAndDispatchesBothTaps() {
        val listener = RecordingKeyboardActionListener()
        val keyboardView = keyboardView(listener)
        keyboardView.setKeyboard(normalCharacterKeyWithInvalidBindingLayout())
        layoutKeyboard(keyboardView)
        val characterKey = keyboardView.getChildAt(0)
        val x = characterKey.centerX()
        val y = characterKey.centerY()

        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, 500L, 500L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, 500L, 520L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, 620L, 620L)
        keyboardView.dispatchTouch(MotionEvent.ACTION_UP, x, y, 620L, 640L)

        assertTrue(
            listener.actions == listOf(
                KeyAction.Text("a"),
                KeyAction.Text("a")
            )
        )
    }

    private fun keyboardView(
        listener: FlickKeyboardView.OnKeyboardActionListener
    ): FlickKeyboardView {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        return FlickKeyboardView(context).apply {
            setOnKeyboardActionListener(listener)
        }
    }

    private fun pasteKeyLayout(): KeyboardLayout {
        val pasteKey = KeyData(
            label = PASTE_KEY_LABEL,
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Paste,
            isSpecialKey = true,
            keyId = PASTE_KEY_ID,
            keyType = KeyType.CROSS_FLICK
        )
        return KeyboardLayout(
            keys = listOf(pasteKey),
            flickKeyMaps = mapOf(
                PASTE_KEY_ID to listOf(
                    mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Paste))
                )
            ),
            columnCount = 2,
            rowCount = 1
        )
    }

    private fun shiftKeyLayout(): KeyboardLayout {
        val shiftKey = KeyData(
            label = "",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.ShiftKey,
            isSpecialKey = true,
            keyId = "shift",
            keyType = KeyType.NORMAL,
            doubleTapBinding = DoubleTapBinding(
                KeyAction.CapLockKey,
                DoubleTapPolicy.PROMOTE
            )
        )
        return KeyboardLayout(
            keys = listOf(shiftKey),
            flickKeyMaps = emptyMap(),
            columnCount = 1,
            rowCount = 1
        )
    }

    private fun normalCharacterKeyWithInvalidBindingLayout(): KeyboardLayout {
        val characterKey = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("a"),
            isSpecialKey = false,
            keyId = "a",
            keyType = KeyType.NORMAL,
            doubleTapBinding = DoubleTapBinding(
                KeyAction.Copy,
                DoubleTapPolicy.PROMOTE
            )
        )
        return KeyboardLayout(
            keys = listOf(characterKey),
            flickKeyMaps = emptyMap(),
            columnCount = 1,
            rowCount = 1
        )
    }

    private fun layoutKeyboard(keyboardView: FlickKeyboardView) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        keyboardView.measure(widthSpec, heightSpec)
        keyboardView.layout(0, 0, 240, 120)
    }

    private fun FlickKeyboardView.dispatchTouch(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        onTouchEvent(event)
        event.recycle()
    }

    private fun View.centerX(): Float = left + width / 2f

    private fun View.centerY(): Float = top + height / 2f

    private class RecordingKeyboardActionListener(
        private val afterAction: (KeyAction) -> Unit = {}
    ) : FlickKeyboardView.OnKeyboardActionListener {
        val actions = mutableListOf<KeyAction>()

        override fun onPress(action: KeyAction) = Unit

        override fun onAction(action: KeyAction, isFlick: Boolean) {
            actions += action
            afterAction(action)
        }

        override fun onActionLongPress(action: KeyAction) = Unit

        override fun onActionUpAfterLongPress(action: KeyAction) = Unit

        override fun onFlickDirectionChanged(direction: FlickDirection) = Unit

        override fun onFlickActionLongPress(action: KeyAction) = Unit

        override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
    }

    private companion object {
        private const val PASTE_KEY_ID = "paste_action_key"
        private const val PASTE_KEY_LABEL = "PasteActionKey"
    }
}
