package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Test-only window: exercises production Sumire views without replacing the user's IME. */
class SumireNearestKeyTestActivity : Activity() {
    lateinit var keyboard: FlickKeyboardView
    private lateinit var root: FrameLayout
    private lateinit var definition: KeyboardLayout
    val actions = CopyOnWriteArrayList<KeyAction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun configure(scale: Int, floating: Boolean) {
        root.removeAllViews()
        actions.clear()
        definition = KeyboardDefaultLayouts.createFinalLayout(
            KeyboardInputMode.HIRAGANA, emptyMap(), "toggle", "default"
        )
        keyboard = FlickKeyboardView(this).apply {
            applyKeySizing(scale, scale, 100, 14f, 14f)
            setKeyboard(definition, KeyHitTestMode.NEAREST_KEY)
            setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
                override fun onActionLongPress(action: KeyAction) = Unit
                override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) = Unit
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
            })
        }
        val width = if (floating) (resources.displayMetrics.widthPixels * 0.75f).toInt() else -1
        root.addView(keyboard, FrameLayout.LayoutParams(width, if (floating) 600 else 800).apply {
            gravity = if (floating) Gravity.CENTER else Gravity.BOTTOM
        })
    }

    fun keyBounds(label: String): Rect {
        val index = definition.items.indexOfFirst { it is KeyItem && it.keyData.label == label }
        check(index >= 0) { "Missing Sumire key $label" }
        val key = keyboard.getChildAt(index)
        val origin = IntArray(2).also(key::getLocationOnScreen)
        return Rect(origin[0], origin[1], origin[0] + key.width, origin[1] + key.height)
    }
}

@RunWith(AndroidJUnit4::class)
class SumireNearestKeyInstrumentedTest {
    @Test fun realSumireLayout_acceptsGapTapsAtBothSizesAndWindowPositions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(
            instrumentation.context, SumireNearestKeyTestActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SumireNearestKeyTestActivity
        var count = 0
        try {
            for (floating in listOf(false, true)) for (scale in listOf(100, 0)) {
                instrumentation.runOnMainSync { activity.configure(scale, floating) }
                instrumentation.waitForIdleSync()
                val deadline = SystemClock.uptimeMillis() + 3000
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        ready = activity.hasWindowFocus() && activity.keyboard.width > 0 &&
                            activity.keyboard.getChildAt(0).width > 0
                    }
                    if (!ready) SystemClock.sleep(25)
                }
                assertTrue("Test window must be focused and laid out; unlock the device first", ready)
                lateinit var a: Rect
                lateinit var ka: Rect
                lateinit var ta: Rect
                instrumentation.runOnMainSync {
                    a = activity.keyBounds("あ")
                    ka = activity.keyBounds("か")
                    ta = activity.keyBounds("た")
                }
                assertTrue("Expected horizontal gap: a=$a ka=$ka", a.right < ka.left)
                assertTrue("Expected vertical gap: a=$a ta=$ta", a.bottom < ta.top)
                val midX = (a.exactCenterX() + ka.exactCenterX()) / 2f
                val midY = (a.exactCenterY() + ta.exactCenterY()) / 2f
                val points = listOf(
                    Triple(a.exactCenterX(), a.exactCenterY(), "あ"),
                    Triple(ka.exactCenterX(), ka.exactCenterY(), "か"),
                    Triple(midX - 1, a.exactCenterY(), "あ"),
                    Triple(midX + 1, a.exactCenterY(), "か"),
                    Triple(a.exactCenterX(), midY - 1, "あ"),
                    Triple(a.exactCenterX(), midY + 1, "た"),
                    Triple(midX - 1, midY - 1, "あ"),
                    Triple(midX + 1, midY + 1, "な")
                )
                assertTrue(midX >= a.right && midX < ka.left)
                assertTrue(midY >= a.bottom && midY < ta.top)
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                val output = File(instrumentation.context.getExternalFilesDir(null),
                    "sumire-nearest-$scale-$floating.png")
                output.outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
                for ((x, y, expected) in points) repeat(3) {
                    activity.actions.clear()
                    val down = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                        finally { event.recycle() }
                        if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
                    }
                    instrumentation.waitForIdleSync()
                    assertEquals("scale=$scale floating=$floating ($x,$y)",
                        listOf(KeyAction.Text(expected)), activity.actions.toList())
                    count++
                }
            }
            println("SUMIRE_NEAREST_KEY_RESULT taps=$count failures=0")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
