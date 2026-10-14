package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class FloatingCursorViewTest {
    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get()
        .apply { setTheme(R.style.Theme_MarkdownKeyboard) }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun measure(view: View, width: Int, height: Int) {
        val density = view.resources.displayMetrics.density
        val w = (width * density).toInt(); val h = (height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
    }
    private fun touch(view: View, action: Int) {
        val event = MotionEvent.obtain(0, 0, action, view.width / 2f, view.height / 2f, 0)
        view.dispatchTouchEvent(event); event.recycle()
    }

    @Test fun minimumSizeKeepsArrowsAndSelectionAtLeast48DpAndOmitsTheTitle() {
        val activity = activity()
        val view = FloatingCursorView(activity, {}, {}, {}, {}, {})
        for (editing in listOf(false, true)) {
            view.setEditing(editing)
            measure(view, FloatingCursorView.MIN_WIDTH, FloatingCursorView.MIN_HEIGHT + if (editing) FloatingCursorView.EDIT_EXTRA else 0)
            val descriptions = listOf(R.string.floating_cursor_left, R.string.floating_cursor_right,
                R.string.floating_cursor_up, R.string.floating_cursor_down)
            for (resource in descriptions) {
                val button = descendants(view).first { it.contentDescription == activity.getString(resource) }
                assertTrue(button.width >= 48 * activity.resources.displayMetrics.density)
                assertTrue(button.height >= 48 * activity.resources.displayMetrics.density)
            }
            val pad = descendants(view).first { it.contentDescription == activity.getString(R.string.floating_cursor_trackpad) }
            assertTrue(pad.width >= 96 * activity.resources.displayMetrics.density)
        }
        assertFalse(descendants(view).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.floating_cursor_title) })
        activity.finish()
    }

    @Test fun cancellationStopsARealHoldAndItsReleaseDoesNotBecomeATap() {
        val activity = activity()
        val moves = mutableListOf<CursorDirection>()
        val view = FloatingCursorView(activity, {}, {}, {}, { moves += it }, {})
        measure(view, 360, 300)
        val arrow = descendants(view).first { it.contentDescription == activity.getString(R.string.floating_cursor_left) }
        touch(arrow, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 70))
        assertTrue(moves.isNotEmpty())
        view.cancelInput()
        val count = moves.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        touch(arrow, MotionEvent.ACTION_UP)
        assertEquals(count, moves.size)
        activity.finish()
    }

    @Test fun unavailableSelectionAndLayoutEditingRejectActions() {
        val activity = activity()
        var selections = 0; var moves = 0
        val view = FloatingCursorView(activity, {}, {}, {}, { moves++ }, { selections++ })
        view.updateSelection(false, false)
        val button = descendants(view).first { it.contentDescription == activity.getString(R.string.floating_cursor_selection_unavailable) }
        assertFalse(button.isEnabled)
        button.performClick()
        assertEquals(0, selections)
        view.setControlsEnabled(false)
        descendants(view).first { it.contentDescription == activity.getString(R.string.floating_cursor_left) }.performClick()
        assertEquals(0, moves)
        activity.finish()
    }

    @Test fun optionalShortcutEnabledStateAndGeometryUseExistingBackup() {
        val activity = activity()
        AppPreference.init(activity)
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val settings = FloatingCursorSettings(preferences)
        assertFalse(settings.enabled)
        assertFalse(ShortcutType.FLOATING_CURSOR in ShortcutRepository(mock<ShortcutDao>()).defaultShortcuts)
        assertEquals(ShortcutType.FLOATING_CURSOR, ShortcutType.fromId("floating_cursor"))
        settings.enabled = true
        preferences.edit().putFloat("floating_cursor_portrait_width", 400f).putFloat("floating_cursor_landscape_width", 500f).apply()
        val backup = AppPreference.exportAllToJson(listOf("floating_cursor"))
        preferences.edit().clear().apply()
        assertEquals(listOf("floating_cursor"), AppPreference.importAllFromJson(backup))
        assertTrue(settings.enabled)
        assertEquals(400f, settings.load(false).widthDp)
        assertEquals(500f, settings.load(true).widthDp)
        settings.resetPlacement()
        assertTrue(settings.enabled)
        assertEquals(360f, settings.load(false).widthDp)
        assertEquals(360f, settings.load(true).widthDp)
        activity.finish()
    }
}
