package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewPopupScopeTest {
    @Test fun changingKeyboardOriginAndPreferencesUsesThatKeyboardsTapGeometry() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val view = FlickKeyboardView(activity)
        activity.setContentView(view)
        val config = SumirePopupDefaults.create("は")
        val layout = KeyboardLayout(listOf(KeyData("は", 0, 0, true,
            KeyAction.Text("は"), popupMapConfig = config)), emptyMap(), 1, 1)
        val normal = PopupMapInputOptions()
        val sensitivity = normal.copy(tapSizeMode = PopupTapSizeMode.FLICK_SENSITIVITY)
        view.setFlickSensitivityValue(1)
        view.setPopupMapOptions(sensitivity, KeyboardLayoutOrigin.BUILT_IN)
        view.setPopupMapOptions(normal, KeyboardLayoutOrigin.CUSTOM)
        fun check(origin: KeyboardLayoutOrigin, expected: PopupTapSizeMode) {
            view.setKeyboard(layout, origin = origin)
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 400)
            val key = view.getChildAt(0)
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, key.width / 2f, key.height / 2f, 0)
            key.dispatchTouchEvent(down); down.recycle()
            val controllers = view.javaClass.getDeclaredField("popupMapControllers").apply { isAccessible = true }.get(view) as List<*>
            val controller = controllers.single()!!
            val session = controller.javaClass.getDeclaredField("session").apply { isAccessible = true }.get(controller) as PopupMapSession
            assertEquals(expected == PopupTapSizeMode.FLICK_SENSITIVITY, session.tapArea.followsSensitivity)
            if (expected == PopupTapSizeMode.KEY) assertEquals(PopupBounds.KEY, session.tapArea.bounds)
            else assertTrue(session.tapArea.bounds.width < 1f)
            val cancel = MotionEvent.obtain(0, 1, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            key.dispatchTouchEvent(cancel); cancel.recycle()
        }
        try {
            check(KeyboardLayoutOrigin.BUILT_IN, PopupTapSizeMode.FLICK_SENSITIVITY)
            check(KeyboardLayoutOrigin.CUSTOM, PopupTapSizeMode.KEY)
            view.setPopupMapOptions(sensitivity, KeyboardLayoutOrigin.CUSTOM)
            check(KeyboardLayoutOrigin.CUSTOM, PopupTapSizeMode.FLICK_SENSITIVITY)
            view.setPopupMapOptions(normal, KeyboardLayoutOrigin.BUILT_IN)
            check(KeyboardLayoutOrigin.CUSTOM, PopupTapSizeMode.FLICK_SENSITIVITY)
            check(KeyboardLayoutOrigin.BUILT_IN, PopupTapSizeMode.KEY)
        } finally { activity.finish() }
    }
}
