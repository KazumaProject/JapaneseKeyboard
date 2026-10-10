package com.kazumaproject.markdownhelperkeyboard.tenkey

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.doOnLayout
import com.kazumaproject.core.domain.flick.FlickInputEvidence
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.FlickEvidenceTestHostActivity
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.kazumaproject.tenkey.R as TenKeyR

@RunWith(AndroidJUnit4::class)
class TenKeyFlickEvidenceInstrumentedTest {
    @Test fun completedTapHasEvidenceAndOldListenerStillReceivesItsCharacter() {
        withKeyboard { keyboard ->
            var received: Char? = null
            keyboard.setFlickCorrectionEvidenceEnabled(true)
            keyboard.setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) { if (char != null) received = char }
            })
            tap(keyboard, TenKeyR.id.key_2)
            assertEquals('か', received)
            var evidence: FlickInputEvidence? = null
            keyboard.setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) = Unit
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?, sample: FlickInputEvidence?) {
                    if (char != null) evidence = sample
                }
            })
            tap(keyboard, TenKeyR.id.key_2)
            assertEquals('か', evidence!!.observedChar)
            assertTrue(evidence!!.alternativeCostFactors.isNotEmpty())
            keyboard.setFlickCorrectionEvidenceEnabled(false)
            evidence = null
            tap(keyboard, TenKeyR.id.key_2)
            assertNull(evidence)
        }
    }

    @Test fun independentPointersKeepTheEvidenceForTheirOwnKeys() {
        withKeyboard { keyboard ->
            keyboard.setFlickCorrectionEvidenceEnabled(true)
            keyboard.setIndependentMultiTouchEnabled(true)
            val received = ArrayList<Pair<Char, Char>>()
            keyboard.setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) = Unit
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?, evidence: FlickInputEvidence?) {
                    if (char != null && evidence != null) received.add(char to evidence.observedChar)
                }
            })
            val ka = center(keyboard.findViewById(TenKeyR.id.key_2))
            val ra = center(keyboard.findViewById(TenKeyR.id.key_9))
            val time = SystemClock.uptimeMillis()
            send(keyboard, time, MotionEvent.ACTION_DOWN, listOf(0), listOf(ka))
            send(keyboard, time, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(0, 1), listOf(ka, ra))
            send(keyboard, time, MotionEvent.ACTION_POINTER_UP, listOf(0, 1), listOf(ka, ra))
            send(keyboard, time, MotionEvent.ACTION_UP, listOf(1), listOf(ra))
            assertEquals(listOf('か' to 'か', 'ら' to 'ら'), received)
        }
    }

    @Test fun floatingAndNarrowKeyboardUseScreenCoordinatesForThePressedKey() {
        for (scale in listOf(1f, 0.8f)) withKeyboard(floating = true, scale = scale) { keyboard ->
            keyboard.setFlickCorrectionEvidenceEnabled(true)
            var sample: FlickInputEvidence? = null
            keyboard.setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) = Unit
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?, evidence: FlickInputEvidence?) {
                    if (char != null) sample = evidence
                }
            })
            tap(keyboard, TenKeyR.id.key_2)
            assertNotNull("Floating tap did not produce evidence", sample)
            assertEquals('か', sample!!.observedChar)
            assertEquals(0.5f, sample!!.costFactor('か'), 0.001f)
            assertTrue(sample!!.costFactor('あ') > 0.85f)
        }
    }

    private fun withKeyboard(floating: Boolean = false, scale: Float = 1f, block: (TenKey) -> Unit) {
        ActivityScenario.launch(FlickEvidenceTestHostActivity::class.java).use { scenario ->
            lateinit var keyboard: TenKey
            var popup: PopupWindow? = null
            val layoutReady = CountDownLatch(1)
            scenario.onActivity { activity ->
                val themed = androidx.appcompat.view.ContextThemeWrapper(activity,
                    com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
                keyboard = MainLayoutBinding.inflate(android.view.LayoutInflater.from(themed)).keyboardView
                (keyboard.parent as ViewGroup).removeView(keyboard)
                keyboard.visibility = View.VISIBLE
                keyboard.pivotX = 0f
                keyboard.pivotY = 0f
                keyboard.scaleX = scale
                keyboard.scaleY = scale
                keyboard.applyKeyboardTheme("default", android.content.res.Configuration.UI_MODE_NIGHT_NO,
                    false, android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.WHITE,
                    android.graphics.Color.BLACK, android.graphics.Color.BLACK, false, false,
                    android.graphics.Color.BLACK, 255, 1)
                keyboard.setLongPressTimeout(10000)
                // These tests exercise touch geometry. Suppress icon drawing so unrelated
                // drawable invalidations cannot keep Instrumentation's idle barrier busy.
                fun clearIcons(view: View) {
                    if (view is android.widget.ImageView) view.setImageDrawable(null)
                    if (view is ViewGroup) for (index in 0 until view.childCount) clearIcons(view.getChildAt(index))
                }
                clearIcons(keyboard)
                keyboard.doOnLayout { keyboard.postOnAnimation { layoutReady.countDown() } }
                val host = FrameLayout(activity)
                activity.setContentView(host)
                if (floating) {
                    popup = PopupWindow(keyboard, (activity.resources.displayMetrics.widthPixels * 0.7f).toInt(), 600)
                        .also { it.showAtLocation(host, Gravity.TOP or Gravity.LEFT, 40, 250) }
                } else host.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600))
            }
            assertTrue("Keyboard was not laid out", layoutReady.await(5, TimeUnit.SECONDS))
            try { onMain { block(keyboard) } }
            finally { onMain {
                popup?.dismiss()
                (keyboard.parent as? ViewGroup)?.removeView(keyboard)
            } }
        }
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try { block() } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private fun center(view: View): Pair<Float, Float> {
        assertTrue("Key has no bounds: ${view.width} x ${view.height}", view.width > 0 && view.height > 0)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val bounds = android.graphics.Rect()
        assertTrue("Key is not visible", view.getGlobalVisibleRect(bounds))
        return (location[0] + bounds.width() / 2f) to (location[1] + bounds.height() / 2f)
    }

    private fun tap(keyboard: TenKey, id: Int) {
        val point = center(keyboard.findViewById(id))
        val time = SystemClock.uptimeMillis()
        send(keyboard, time, MotionEvent.ACTION_DOWN, listOf(0), listOf(point))
        send(keyboard, time, MotionEvent.ACTION_UP, listOf(0), listOf(point))
    }

    private fun send(keyboard: TenKey, time: Long, action: Int, ids: List<Int>, points: List<Pair<Float, Float>>) {
        val properties = ids.map { id -> MotionEvent.PointerProperties().apply {
            this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = points.map { (x, y) -> MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = 1f; size = 1f
        } }.toTypedArray()
        MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, ids.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0).also {
            keyboard.onTouch(keyboard, it)
            it.recycle()
        }
    }
}
