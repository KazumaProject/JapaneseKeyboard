package com.kazumaproject.custom_keyboard.controller

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.PopupMapView
import com.kazumaproject.custom_keyboard.view.SumireFlickRotationTestActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production keyboard touch router and native popup windows without changing the IME. */
@RunWith(AndroidJUnit4::class)
class FreeFlickPopupPositionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun sensitivityKeepsPopupPlacementAndDirectHitsKeyRelative() {
        val intent = Intent(instrumentation.context, SumireFlickRotationTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SumireFlickRotationTestActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.configure(SumirePopupDefaults.STYLE, TfbiFlickRotation.DEFAULT, TfbiPopupPresentationMode.LEGACY_GRID)
                val layout = KeyboardDefaultLayouts.createFinalLayout(
                    KeyboardInputMode.HIRAGANA, emptyMap(), "flick", SumirePopupDefaults.STYLE)
                val config = PopupKeyConfig("か", "normal", listOf(PopupMap("normal", "通常", "か", listOf(
                    PopupRegion("small", PopupBounds(.7f, -.1f, .9f, .1f), PopupAction(text = "○"))))))
                activity.keyboard.setKeyboard(layout.copyWithKeys(layout.keys.map {
                    if (it.label == "か") it.copy(popupMapConfig = config) else it
                }), origin = KeyboardLayoutOrigin.BUILT_IN)
            }
            instrumentation.waitForIdleSync()
            for (presentation in PopupMapPresentation.entries) for (sensitivity in listOf(1, 100, 200)) {
                scenario.onActivity {
                    it.keyboard.setPopupMapOptions(PopupMapInputOptions(nearestEnabled = false,
                        presentation = presentation, tapSizeMode = PopupTapSizeMode.FLICK_SENSITIVITY))
                    it.keyboard.setFlickSensitivityValue(sensitivity)
                }
                event(scenario, MotionEvent.ACTION_DOWN, 0f, 0f)
                val centered = popupOrigin(scenario, presentation)
                event(scenario, MotionEvent.ACTION_CANCEL, 0f, 0f)
                event(scenario, MotionEvent.ACTION_DOWN, -.375f, .25f)
                val offCenter = popupOrigin(scenario, presentation)
                assertEquals("$presentation / $sensitivity: popup X", centered.first, offCenter.first, 1f)
                assertEquals("$presentation / $sensitivity: popup Y", centered.second, offCenter.second, 1f)
                event(scenario, MotionEvent.ACTION_UP, -.375f, .25f)
                scenario.onActivity { assertEquals(listOf(KeyAction.Text("か")), it.actions) }

                event(scenario, MotionEvent.ACTION_DOWN, -.375f, .25f)
                event(scenario, MotionEvent.ACTION_MOVE, .8f, 0f)
                event(scenario, MotionEvent.ACTION_UP, .8f, 0f)
                scenario.onActivity { assertEquals("$presentation / $sensitivity: direct hit",
                    listOf(KeyAction.Text("○")), it.actions) }
            }
        }
    }

    private fun event(scenario: ActivityScenario<SumireFlickRotationTestActivity>, action: Int, x: Float, y: Float) {
        scenario.onActivity { activity ->
            if (action == MotionEvent.ACTION_DOWN) activity.actions.clear()
            val key = activity.kaKey()
            val keyLocation = IntArray(2).also(key::getLocationOnScreen)
            val keyboardLocation = IntArray(2).also(activity.keyboard::getLocationOnScreen)
            val event = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), action,
                keyLocation[0] + (.5f + x) * key.width,
                keyLocation[1] + (.5f + y) * key.height, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            event.offsetLocation(-keyboardLocation[0].toFloat(), -keyboardLocation[1].toFloat())
            try { assertTrue(activity.keyboard.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun popupOrigin(scenario: ActivityScenario<SumireFlickRotationTestActivity>, presentation: PopupMapPresentation): Pair<Float, Float> {
        var origin: Pair<Float, Float>? = null
        scenario.onActivity { activity ->
            val controllers = field(activity.keyboard, "popupMapControllers") as List<*>
            val controller = controllers.filterNotNull().single { field(it, "anchor") === activity.kaKey() }
            val drawing: PopupMapView
            val window: PopupWindow
            if (presentation == PopupMapPresentation.AROUND_KEY) {
                drawing = field(controller, "content") as PopupMapView
                window = field(controller, "popup") as PopupWindow
            } else {
                val host = field(controller, "guideHost")!!
                drawing = field(host, "view") as PopupMapView
                window = field(host, "window") as PopupWindow
            }
            assertTrue("Native popup must be showing", window.isShowing)
            assertTrue(drawing.isAttachedToWindow)
            val location = IntArray(2).also(drawing::getLocationOnScreen)
            origin = location[0] + drawing.originX to location[1] + drawing.originY
        }
        return requireNotNull(origin)
    }
}
