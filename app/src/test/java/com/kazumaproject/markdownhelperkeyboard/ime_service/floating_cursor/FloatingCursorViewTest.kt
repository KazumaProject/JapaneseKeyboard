package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class FloatingCursorViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun px(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
    private fun children(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
    private fun panel(moves: MutableList<CursorDirection>) = FloatingCursorView(context, {}, {}, {}, moves::add, {}).apply {
        measure(View.MeasureSpec.makeMeasureSpec(px(296), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(284), View.MeasureSpec.EXACTLY))
        layout(0, 0, measuredWidth, measuredHeight)
    }
    private fun touch(view: View, action: Int, x: Float = 1f, y: Float = 1f) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        view.dispatchTouchEvent(event); event.recycle()
    }

    @Test fun smallestPanelKeepsDirectionsAndTrackpadAtLeast48dp() {
        val panel = panel(mutableListOf())
        val arrows = children(panel).filterIsInstance<Button>().filter { it.contentDescription?.toString()?.startsWith("Move cursor") == true }
        assertEquals(4, arrows.size)
        arrows.forEach { assertTrue(it.width >= px(48)); assertTrue(it.height >= px(48)) }
        val pad = children(panel).filterIsInstance<TextView>().first { it.text == context.getString(R.string.floating_cursor_trackpad) }
        assertTrue(pad.width >= px(96)); assertTrue(pad.height >= px(144))
    }

    @Test fun disabledSelectionDoesNotToggleAndCancellationStopsRepeat() {
        var toggled = false
        val moves = mutableListOf<CursorDirection>()
        val panel = FloatingCursorView(context, {}, {}, {}, moves::add, { toggled = true })
        panel.updateSelection(false, false)
        val button = children(panel).filterIsInstance<Button>().first { it.text == context.getString(R.string.floating_cursor_selection_unavailable) }
        assertFalse(button.isEnabled)
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP)
        assertFalse(toggled)
        val left = children(panel).first { it.contentDescription == context.getString(R.string.floating_cursor_left) }
        touch(left, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertTrue(moves.isNotEmpty())
        val count = moves.size
        panel.cancelInput()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(count, moves.size)
    }

    @Test fun liftingArrowOwnerStopsRepeatEvenWhenAnotherFingerRemains() {
        val moves = mutableListOf<CursorDirection>()
        val panel = panel(moves)
        val left = children(panel).first { it.contentDescription == context.getString(R.string.floating_cursor_left) }
        touch(left, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        val before = moves.size
        val event = MotionEvent.obtain(0, 700, MotionEvent.ACTION_POINTER_UP, 2,
            Array(2) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } },
            Array(2) { MotionEvent.PointerCoords().apply { x = 1f; y = 1f; pressure = 1f; size = 1f } },
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        left.dispatchTouchEvent(event); event.recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(before > 0); assertEquals(before, moves.size)
    }

    @Test fun trackpadOwnerCancellationAndNewTouchDoNotReuseDistance() {
        val moves = mutableListOf<CursorDirection>()
        val panel = panel(moves)
        val pad = children(panel).first { it.contentDescription == context.getString(R.string.floating_cursor_trackpad) }
        touch(pad, MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(pad, MotionEvent.ACTION_MOVE, 10f + px(19), 10f)
        touch(pad, MotionEvent.ACTION_CANCEL)
        touch(pad, MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(pad, MotionEvent.ACTION_MOVE, 10f + px(20), 10f)
        touch(pad, MotionEvent.ACTION_UP)
        assertEquals(listOf(CursorDirection.RIGHT), moves)
    }

    @Test fun geometryAndEnablementRoundTripThroughExistingBackupAndResetIndependently() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean(FloatingCursorSettings.ENABLED, true).commit()
        val settings = FloatingCursorSettings(prefs)
        val portrait = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuidePlacement(.2f, .3f, 350f, 310f)
        val landscape = portrait.copy(xFraction = .8f, widthDp = 400f)
        settings.save(false, portrait); settings.save(true, landscape)
        val backup = AppPreference.exportAllToJson()
        prefs.edit().clear().commit()
        AppPreference.importAllFromJson(backup)
        assertTrue(settings.enabled)
        assertEquals(portrait, settings.load(false)); assertEquals(landscape, settings.load(true))
        FloatingCursorSettings.reset(prefs)
        assertTrue(settings.enabled)
        assertEquals(320f, settings.load(false).widthDp)
        assertEquals(320f, settings.load(true).widthDp)
    }
}
