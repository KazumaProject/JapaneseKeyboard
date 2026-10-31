package com.kazumaproject.qwerty_keyboard.ui

import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import com.kazumaproject.core.ui.geometry.KeyboardViewGeometry
import com.kazumaproject.qwerty_keyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QwertyGeometryInstrumentedTest {
    @Test fun splitCentersAndBothSpaceKeysUseTheDisplayedCoordinates() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val keyboard = QWERTYKeyboardView(ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().targetContext,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            ))
            val pressed = mutableListOf<QWERTYKey>()
            keyboard.setPopUpViewState(false)
            keyboard.setOnQWERTYKeyListener(object : QWERTYKeyListener {
                override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) { pressed += qwertyKey }
                override fun onReleasedQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) = Unit
                override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) = Unit
                override fun onFlickUPQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) = Unit
                override fun onFlickDownQWERTYKey(qwertyKey: QWERTYKey, character: Char) = Unit
            })
            keyboard.setKeyboardGeometry(KeyboardGeometry(splitPercent = 20, curveDegrees = 20), 500)
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, 1080, 500)
            val mirror = keyboard.javaClass.getDeclaredField("splitSpaceView").apply { isAccessible = true }.get(keyboard) as View
            val keys = listOf(keyboard.findViewById<View>(R.id.key_q), keyboard.findViewById(R.id.key_p), keyboard.findViewById(R.id.key_space), mirror)
            keys.forEach { key ->
                val (x, y) = KeyboardViewGeometry.centerInParent(key)
                val down = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(100L, 120L, MotionEvent.ACTION_UP, x, y, 0)
                try { keyboard.onTouchEvent(down); keyboard.onTouchEvent(up) } finally { down.recycle(); up.recycle() }
            }
            assertTrue(keyboard.effectiveSplitGapPx() > 0f)
            assertEquals(listOf(QWERTYKey.QWERTYKeyQ, QWERTYKey.QWERTYKeyP, QWERTYKey.QWERTYKeySpace, QWERTYKey.QWERTYKeySpace), pressed)
        }
    }
}
