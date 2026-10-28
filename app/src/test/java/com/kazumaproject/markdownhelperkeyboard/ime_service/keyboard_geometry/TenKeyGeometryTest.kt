package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.app.Activity
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import com.kazumaproject.core.ui.geometry.KeyboardViewGeometry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TenKeyGeometryTest {
    @Test fun curvedKeyUsesItsOwnFlickAxesInBothTouchModes() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        val keyboard = TenKey(activity, Robolectric.buildAttributeSet().build())
        keyboard.applyKeyboardTheme(
            themeMode = "default", currentNightMode = 16, isDynamicColorEnabled = false,
            customBgColor = -1, customKeyColor = -1, customSpecialKeyColor = -1,
            customKeyTextColor = -16777216, customSpecialKeyTextColor = -16777216,
            liquidGlassEnable = false, customBorderEnable = false, customBorderColor = 0,
            liquidGlassKeyAlphaEnable = 255, borderWidth = 1,
        )
        activity.setContentView(FrameLayout(activity).apply { addView(keyboard) })
        try {
            for (hand in KeyboardCurveHand.entries) for (independent in listOf(false, true)) {
                val events = mutableListOf<Pair<GestureType, Key>>()
                keyboard.setOnFlickListener(object : FlickListener {
                    override fun onFlick(gestureType: GestureType, key: Key, char: Char?) { events += gestureType to key }
                })
                keyboard.setIndependentMultiTouchEnabled(independent)
                keyboard.setFlickGuideEnabled(false)
                keyboard.setKeyboardGeometry(KeyboardGeometry(curveDegrees = 25, hand = hand), 500)
                keyboard.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
                keyboard.layout(0, 0, 720, 500)
                val key = keyboard.findViewById<View>(com.kazumaproject.tenkey.R.id.key_1)
                val bounds = RectF(0f, 0f, key.width.toFloat(), key.height.toFloat())
                key.matrix.mapRect(bounds); bounds.offset(key.left.toFloat(), key.top.toFloat())
                assertTrue("hand=$hand bounds=$bounds", bounds.left >= -0.1f && bounds.right <= 720.1f && bounds.top >= -0.1f && bounds.bottom <= 500.1f)
                val (cx, cy) = KeyboardViewGeometry.centerInParent(key)
                val location = IntArray(2).also(keyboard::getLocationOnScreen)
                val angle = Math.toRadians(key.rotation.toDouble())
                val x = location[0] + cx
                val y = location[1] + cy
                val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(0, 20, MotionEvent.ACTION_UP, x - 100f * cos(angle).toFloat(), y - 100f * sin(angle).toFloat(), 0)
                try { keyboard.onTouch(keyboard, down); keyboard.onTouch(keyboard, up) } finally { down.recycle(); up.recycle() }
                assertTrue("hand=$hand independent=$independent events=$events", events.contains(GestureType.FlickLeft to Key.KeyA))
                assertEquals(GestureType.Down to Key.KeyA, events.first())
            }
        } finally { activity.finish() }
    }
}
