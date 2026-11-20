package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
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
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.toKeyItem
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewTouchDispatchTest {

    @Test
    fun deleteLongPressStopsWhenFlickChangesAction() {
        val mappings = listOf(
            FlickAction.Action(KeyAction.DoNothing) to listOf(KeyAction.DoNothing),
            FlickAction.Input("") to emptyList(),
            null to listOf(KeyAction.Cancel),
            FlickAction.Action(KeyAction.DeleteAfterCursor) to listOf(KeyAction.DeleteAfterCursor)
        )

        for ((mapping, expectedRelease) in mappings) {
            for (direction in listOf(FlickDirection.UP, FlickDirection.UP_RIGHT)) {
                assertDeleteLongPressCanceled(direction, mapping, expectedRelease)
            }
        }
    }

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

    @Test
    fun scaledKeyboardRejectsTouchesPastVisibleKeyAndAcceptsItsCenter() {
        val listener = RecordingKeyboardActionListener()
        val keyboard = keyboardView(listener)
        keyboard.setKeyboard(pasteKeyLayout())
        layoutKeyboard(keyboard)
        keyboard.pivotX = 0f; keyboard.pivotY = 0f
        keyboard.scaleX = .5f; keyboard.scaleY = .4f
        val key = keyboard.getChildAt(0)
        val location = IntArray(2).also(key::getLocationOnScreen)
        val y = location[1] + key.height * .2f
        val outsideX = location[0] + key.width * .75f
        keyboard.dispatchTouch(MotionEvent.ACTION_DOWN, outsideX, y, 100L, 100L)
        keyboard.dispatchTouch(MotionEvent.ACTION_UP, outsideX, y, 100L, 120L)
        assertTrue(listener.actions.isEmpty())
        val insideX = location[0] + key.width * .25f
        keyboard.dispatchTouch(MotionEvent.ACTION_DOWN, insideX, y, 200L, 200L)
        keyboard.dispatchTouch(MotionEvent.ACTION_UP, insideX, y, 200L, 220L)
        assertTrue(listener.actions == listOf(KeyAction.Paste))
    }

    @Test
    fun mixedFlickCommitsTextAndActionsFromTheSameKey() {
        val listener = RecordingKeyboardActionListener()
        val keyboard = keyboardView(listener)
        keyboard.setKeyboard(mixedFlickLayout())
        layoutKeyboard(keyboard)

        val key = keyboard.getChildAt(0) as AutoSizeButton
        assertEquals("a", key.text.toString())
        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
        assertEquals("A", key.text.toString())
        val x = key.centerX()
        val y = key.centerY()

        fun touch(deltaX: Float, deltaY: Float, time: Long, release: Int = MotionEvent.ACTION_UP) {
            keyboard.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, time, time)
            keyboard.dispatchTouch(MotionEvent.ACTION_MOVE, x + deltaX, y + deltaY, time, time + 10L)
            keyboard.dispatchTouch(release, x + deltaX, y + deltaY, time, time + 20L)
        }

        touch(0f, 0f, 100L)
        touch(-80f, 0f, 200L)
        touch(80f, 0f, 300L)
        touch(0f, -100f, 400L)
        touch(0f, 100f, 500L) // Unassigned
        touch(0f, -100f, 600L, MotionEvent.ACTION_CANCEL)

        assertEquals(
            listOf(KeyAction.Text("a"), KeyAction.Text("left"), KeyAction.MoveCursorRight, KeyAction.Delete),
            listener.actions
        )
    }

    @Test
    fun holdingMixedTextCommitsItOnRelease() {
        val listener = RecordingKeyboardActionListener()
        val keyboard = keyboardView(listener)
        keyboard.setLongPressTimeout(200L)
        keyboard.setKeyboard(mixedFlickLayout())
        layoutKeyboard(keyboard)
        val key = keyboard.getChildAt(0)
        val time = SystemClock.uptimeMillis()

        keyboard.dispatchTouch(MotionEvent.ACTION_DOWN, key.centerX(), key.centerY(), time, time)
        shadowOf(Looper.getMainLooper()).idleFor(210, TimeUnit.MILLISECONDS)
        assertTrue(listener.actions.isEmpty())
        assertTrue(listener.flickLongPresses.isEmpty())
        keyboard.dispatchTouch(MotionEvent.ACTION_UP, key.centerX(), key.centerY(), time, time + 220L)

        assertEquals(listOf(KeyAction.Text("a")), listener.actions)
    }

    private fun mixedFlickLayout(): KeyboardLayout {
        val key = KeyData(
            label = "",
            row = 0,
            column = 0,
            isFlickable = true,
            action = KeyAction.Text("a"),
            keyId = "mixed",
            keyType = KeyType.MIXED_FLICK
        )
        return KeyboardLayout(
            keys = listOf(key),
            flickKeyMaps = mapOf(
                "mixed" to listOf(
                    mapOf(
                        FlickDirection.TAP to FlickAction.Input("a"),
                        FlickDirection.UP_LEFT to FlickAction.Input("left"),
                        FlickDirection.UP_RIGHT to FlickAction.Action(KeyAction.MoveCursorRight),
                        FlickDirection.UP to FlickAction.Action(KeyAction.Delete)
                    )
                )
            ),
            columnCount = 1,
            rowCount = 1
        )
    }

    private fun keyboardView(
        listener: FlickKeyboardView.OnKeyboardActionListener,
        context: Context = ApplicationProvider.getApplicationContext()
    ): FlickKeyboardView {
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        return FlickKeyboardView(themedContext).apply {
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

    private fun assertDeleteLongPressCanceled(
        direction: FlickDirection,
        mapping: FlickAction?,
        expectedRelease: List<KeyAction>
    ) {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val listener = RecordingKeyboardActionListener()
            val keyboard = keyboardView(listener, activity.get()).apply {
                setLongPressTimeout(200L)
                setKeyboard(flexibleDeleteKeyLayout(direction, mapping))
            }
            activity.get().setContentView(keyboard)
            layoutKeyboard(keyboard)
            listener.canceledLongPresses.clear()

            val delete = keyboard.getChildAt(1)
            val origin = IntArray(2).also(keyboard::getLocationOnScreen)
            val x = origin[0] + delete.centerX()
            val y = origin[1] + delete.centerY()
            val targetX = if (direction == FlickDirection.UP) x else x + 100f
            val targetY = if (direction == FlickDirection.UP) y - 100f else y
            val downTime = SystemClock.uptimeMillis()
            val scenario = "$direction with $mapping"

            keyboard.dispatchTouch(MotionEvent.ACTION_DOWN, x, y, downTime, downTime)
            shadowOf(Looper.getMainLooper()).idleFor(210, TimeUnit.MILLISECONDS)
            assertEquals(scenario, listOf(KeyAction.Delete), listener.flickLongPresses)

            keyboard.dispatchTouch(MotionEvent.ACTION_MOVE, targetX, targetY, downTime, downTime + 210L)
            assertEquals(scenario, listOf(KeyAction.Delete), listener.canceledLongPresses)
            keyboard.dispatchTouch(MotionEvent.ACTION_UP, targetX, targetY, downTime, downTime + 220L)
            assertEquals(scenario, expectedRelease, listener.flickLongPressReleases)

            val letter = keyboard.getChildAt(0)
            val letterX = origin[0] + letter.centerX()
            val letterY = origin[1] + letter.centerY()
            val letterDownTime = downTime + 230L
            keyboard.dispatchTouch(MotionEvent.ACTION_DOWN, letterX, letterY, letterDownTime, letterDownTime)
            keyboard.dispatchTouch(MotionEvent.ACTION_UP, letterX, letterY, letterDownTime, letterDownTime + 20L)
            assertEquals(scenario, listOf(KeyAction.Text("a")), listener.actions)
            assertEquals(scenario, listOf(KeyAction.Delete), listener.canceledLongPresses)
        }
    }

    private fun flexibleDeleteKeyLayout(
        direction: FlickDirection,
        mapping: FlickAction?
    ): KeyboardLayout {
        val deleteKeyId = "custom_delete"
        val letterKey = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("a"),
            keyType = KeyType.QWERTY
        )
        val deleteKey = KeyData(
            label = "Delete",
            row = 0,
            column = 1,
            isFlickable = true,
            action = KeyAction.Delete,
            isSpecialKey = true,
            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
            keyId = deleteKeyId,
            keyType = KeyType.CROSS_FLICK
        )
        val actions = buildMap {
            put(FlickDirection.TAP, FlickAction.Action(KeyAction.Delete))
            mapping?.let { put(direction, it) }
        }
        return KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout()
            .copyWithItems(listOf(letterKey.toKeyItem(), deleteKey.toKeyItem()))
            .copy(flickKeyMaps = mapOf(deleteKeyId to listOf(actions)))
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
        val flickLongPresses = mutableListOf<KeyAction>()
        val flickLongPressReleases = mutableListOf<KeyAction>()
        val canceledLongPresses = mutableListOf<KeyAction>()

        override fun onPress(action: KeyAction) = Unit

        override fun onAction(action: KeyAction, isFlick: Boolean) {
            actions += action
            afterAction(action)
        }

        override fun onActionLongPress(action: KeyAction) = Unit

        override fun onActionUpAfterLongPress(action: KeyAction) = Unit

        override fun onFlickDirectionChanged(direction: FlickDirection) = Unit

        override fun onFlickActionLongPress(action: KeyAction) {
            flickLongPresses += action
        }

        override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
            flickLongPressReleases += action
        }

        override fun onLongPressActionCanceled(action: KeyAction) {
            canceledLongPresses += action
        }
    }

    private companion object {
        private const val PASTE_KEY_ID = "paste_action_key"
        private const val PASTE_KEY_LABEL = "PasteActionKey"
    }
}
