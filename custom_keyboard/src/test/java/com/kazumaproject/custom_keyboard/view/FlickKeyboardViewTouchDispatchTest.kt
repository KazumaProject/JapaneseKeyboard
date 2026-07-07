package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private class RecordingKeyboardActionListener : FlickKeyboardView.OnKeyboardActionListener {
        val actions = mutableListOf<KeyAction>()

        override fun onPress(action: KeyAction) = Unit

        override fun onAction(action: KeyAction, isFlick: Boolean) {
            actions += action
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
