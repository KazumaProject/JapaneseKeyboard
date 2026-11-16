package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class FeverWindowTest {
    @Test fun fullScreenFillsTheFrameworkAtMostBudgetAndKeepsContentAtTheBottom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean(FeverSettings.FULL_SCREEN, true).putBoolean(FeverSettings.SOUND, false).commit()
        val controller = FeverController(context) { false }
        val host = FeverWindowFrameLayout(context, controller)
        val content = FrameLayout(context)
        host.addView(content, FrameLayout.LayoutParams(500, 300, Gravity.BOTTOM))
        controller.configure(true, content, content, null, host)
        val width = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.AT_MOST)
        host.measure(width, height); host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        assertEquals(1800, host.height); assertEquals(1500, content.top); assertEquals(300, content.height)
        controller.stop()
        host.measure(width, height); host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        assertEquals(300, host.height); assertEquals(0, content.top)
        controller.release()
    }
    @Test fun ribbonAndFullScreenKeepTheOriginalBodyAtTheSameScreenPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        val controller = FeverController(context) { false }
        val screen = FrameLayout(context)
        val window = FeverWindowFrameLayout(context, controller)
        val content = FeverInputFrameLayout(context, controller)
        val body = FrameLayout(context)
        content.addView(body, FrameLayout.LayoutParams(500, 300))
        window.addView(content, FrameLayout.LayoutParams(500, -2, Gravity.BOTTOM))
        screen.addView(window, FrameLayout.LayoutParams(500, -2, Gravity.BOTTOM))
        fun layout(): Int {
            screen.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
            screen.layout(0, 0, 500, 1800)
            return window.top + content.top + body.top
        }
        assertEquals(1500, layout())
        controller.configure(true, content, body, null, window)
        assertEquals(1500, layout())
        assertTrue("The combo ribbon needs its own space", content.height > body.height)
        prefs.edit().putBoolean(FeverSettings.FULL_SCREEN, true).commit()
        controller.configure(true, content, body, null, window)
        assertEquals(1500, layout()); assertEquals(1800, window.height)
        controller.stop(); assertEquals(1500, layout()); assertEquals(300, content.height)
        controller.release()
    }
    @Test fun finishingCanonicalCompositionRewardsOnlySuccessfulSoftwareConfirmationOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt(FeverSettings.HIT_RATE, 100).commit()
        val controller = FeverController(context) { false }
        val host = FrameLayout(context)
        val events = mutableListOf<FeverEvent>()
        controller.eventObserver = { events.add(it) }
        controller.configure(true, host, host, null)
        controller.composing("text", true)
        controller.begin(); controller.noteConfirmation(); controller.finished(false); controller.end()
        assertTrue(events.isEmpty())
        controller.begin(); controller.noteConfirmation(); controller.finished(true); controller.end()
        assertEquals(FeverAward.JACKPOT, events.single().award)
        controller.begin(); controller.noteConfirmation(); controller.finished(true); controller.end()
        assertEquals(1, events.size); assertEquals(0, controller.combo)
        controller.release()
    }
}
