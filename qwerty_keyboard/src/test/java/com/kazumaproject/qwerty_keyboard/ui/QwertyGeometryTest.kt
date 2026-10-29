package com.kazumaproject.qwerty_keyboard.ui

import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.graphics.RectF
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.geometry.KeyboardViewGeometry
import com.kazumaproject.qwerty_keyboard.R
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideInputListener
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class QwertyGeometryTest {
    @Test fun unsplitCurvedKeyboardStillRecognizesGlideAcrossTransformedKeys() =
        verifyUnsplitGlide(KeyboardCurveDirection.DOWN)

    @Test fun reverseCurvedKeyboardStillRecognizesGlideAcrossTransformedKeys() =
        verifyUnsplitGlide(KeyboardCurveDirection.UP)

    private fun verifyUnsplitGlide(direction: KeyboardCurveDirection) {
        val keyboard = keyboard()
        var started = false
        var ended = false
        var proximity: QwertyKeyboardProximityInfo? = null
        keyboard.setQwertyGlideInputListener(object : QwertyGlideInputListener {
            override fun onQwertyGlideStarted() { started = true }
            override fun onQwertyGlideUpdated(inputPointers: QwertyInputPointers, proximityInfo: QwertyKeyboardProximityInfo) = Unit
            override fun onQwertyGlideEnded(inputPointers: QwertyInputPointers, proximityInfo: QwertyKeyboardProximityInfo) {
                ended = true; proximity = proximityInfo
            }
            override fun onQwertyGlideCancelled() = Unit
        })
        keyboard.setQwertyGlideInputMode(true)
        keyboard.setKeyboardGeometry(KeyboardGeometry(curveDegrees = 20, curveDirection = direction), 500)
        layout(keyboard)
        val keys = listOf(R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r).map { keyboard.findViewById<View>(it) }
        keys.forEachIndexed { index, key ->
            val (x, y) = KeyboardViewGeometry.centerInParent(key)
            val event = MotionEvent.obtain(0L, index * 100L, if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE, x, y, 0)
            try { keyboard.onTouchEvent(event) } finally { event.recycle() }
        }
        val (x, y) = KeyboardViewGeometry.centerInParent(keys.last())
        val up = MotionEvent.obtain(0L, 400L, MotionEvent.ACTION_UP, x, y, 0)
        try { keyboard.onTouchEvent(up) } finally { up.recycle() }
        assertTrue(started)
        assertTrue(ended)
        val key = requireNotNull(proximity).keys.first { it.char == 'r' }
        assertEquals(x, key.centerX, .01f)
        assertEquals(y, key.centerY, .01f)
    }

    @Test fun rotatedCornersStayInsideAtLargeAnglesAndDifferentWidths() {
        for (width in listOf(400, 700, 1080)) for (split in listOf(0, 40)) for (direction in KeyboardCurveDirection.entries) {
            val keyboard = keyboard()
            keyboard.setKeyboardGeometry(KeyboardGeometry(splitPercent = split, curveDegrees = 25, curveDirection = direction), 500)
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, width, 500)
            val views = listOf(R.id.key_q, R.id.key_t, R.id.key_y, R.id.key_p, R.id.key_z, R.id.key_m, R.id.key_space)
                .map { keyboard.findViewById<View>(it) }
            for (key in views) {
                val bounds = RectF(0f, 0f, key.width.toFloat(), key.height.toFloat())
                key.matrix.mapRect(bounds)
                bounds.offset(key.left.toFloat(), key.top.toFloat())
                assertTrue("width=$width split=$split bounds=$bounds", bounds.left >= -0.1f && bounds.right <= width + 0.1f)
                assertTrue("width=$width split=$split bounds=$bounds", bounds.top >= -0.1f && bounds.bottom <= 500.1f)
            }
        }
    }

    @Test fun changingGeometryCancelsAnyOutstandingGlideDecode() {
        val keyboard = keyboard()
        var cancellations = 0
        keyboard.setQwertyGlideInputListener(object : QwertyGlideInputListener {
            override fun onQwertyGlideStarted() = Unit
            override fun onQwertyGlideUpdated(inputPointers: QwertyInputPointers, proximityInfo: QwertyKeyboardProximityInfo) = Unit
            override fun onQwertyGlideEnded(inputPointers: QwertyInputPointers, proximityInfo: QwertyKeyboardProximityInfo) = Unit
            override fun onQwertyGlideCancelled() { cancellations++ }
        })
        keyboard.setQwertyGlideInputMode(true)
        keyboard.setKeyboardGeometry(KeyboardGeometry(), 500)
        assertEquals(0, cancellations)
        keyboard.setKeyboardGeometry(KeyboardGeometry(splitPercent = 20), 500)
        assertTrue(cancellations > 0)
    }
    @Test fun curveChangesKeyPositionsAndReportsExtraHeightWithoutChangingBaselineHeight() {
        val keyboard = keyboard()
        keyboard.setKeyboardGeometry(KeyboardGeometry(curveDegrees = 25), 500)
        layout(keyboard)
        val required = keyboard.javaClass.getDeclaredField("lastRequiredGeometryHeight").apply { isAccessible = true }.getInt(keyboard)
        val key = keyboard.findViewById<View>(R.id.key_t)
        assertTrue("The inner key should follow the curved layout", key.rotation > 0f)
        assertTrue("The outer bounds should need extra height; required=$required", required > 500)
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(required, View.MeasureSpec.EXACTLY))
        keyboard.layout(0, 0, 1080, required)
        assertEquals(500, keyboard.javaClass.getDeclaredField("geometryBaseHeight").apply { isAccessible = true }.getInt(keyboard))
        val (x, y) = KeyboardViewGeometry.centerInParent(key)
        assertTrue(x in 0f..1080f && y in 0f..required.toFloat())
        keyboard.setKeyboardGeometry(KeyboardGeometry(), 500)
        layout(keyboard)
        assertEquals(0f, key.rotation, .001f)
        assertEquals(0f, key.translationX, .001f)
        assertEquals(0f, key.translationY, .001f)
    }

    @Test fun splitDisablesGlideWithoutChangingItsRequestedStateAndRestoresIt() {
        val keyboard = keyboard()
        keyboard.setQwertyGlideInputMode(true)
        keyboard.setKeyboardGeometry(KeyboardGeometry(splitPercent = 20, curveDegrees = 10), 500)
        layout(keyboard)
        assertTrue(keyboard.effectiveSplitGapPx() > 0)
        assertFalse(glideEnabled(keyboard))
        keyboard.setKeyboardGeometry(KeyboardGeometry(), 500)
        layout(keyboard)
        assertEquals(0f, keyboard.effectiveSplitGapPx(), .001f)
        assertTrue(glideEnabled(keyboard))
    }

    @Test fun bothSpaceKeysUseTheSameActionAndTheGapDoesNotPressAKey() {
        val keyboard = keyboard()
        val pressed = mutableListOf<QWERTYKey>()
        keyboard.setOnQWERTYKeyListener(object : QWERTYKeyListener {
            override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) { pressed += qwertyKey }
            override fun onReleasedQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) = Unit
            override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) = Unit
            override fun onFlickUPQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) = Unit
            override fun onFlickDownQWERTYKey(qwertyKey: QWERTYKey, character: Char) = Unit
        })
        keyboard.setKeyboardGeometry(KeyboardGeometry(splitPercent = 20, curveDegrees = 10), 500)
        layout(keyboard)
        val original = keyboard.findViewById<View>(R.id.key_space)
        val mirror = keyboard.javaClass.getDeclaredField("splitSpaceView").apply { isAccessible = true }.get(keyboard) as View
        for (key in listOf(original, mirror)) {
            val (x, y) = KeyboardViewGeometry.centerInParent(key)
            touch(keyboard, MotionEvent.ACTION_DOWN, x, y)
            touch(keyboard, MotionEvent.ACTION_UP, x, y)
        }
        assertEquals(listOf(QWERTYKey.QWERTYKeySpace, QWERTYKey.QWERTYKeySpace), pressed)
        touch(keyboard, MotionEvent.ACTION_DOWN, keyboard.width / 2f, keyboard.height / 2f)
        touch(keyboard, MotionEvent.ACTION_UP, keyboard.width / 2f, keyboard.height / 2f)
        assertEquals(2, pressed.size)
    }

    private fun keyboard() = QWERTYKeyboardView(ContextThemeWrapper(RuntimeEnvironment.getApplication(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)).apply { setPopUpViewState(false) }
    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 500)
    }
    private fun glideEnabled(view: QWERTYKeyboardView) = view.javaClass.getDeclaredField("qwertyGlideInputMode").apply { isAccessible = true }.getBoolean(view)
    private fun touch(view: QWERTYKeyboardView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 1L, action, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
}
