package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.content.Context
import android.app.Activity
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardGeometryPreferencesTest {
    @Test fun sideCandidateSettingsPreserveDefaultsAndAreIndependent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("geometry_side_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = KeyboardGeometryPreferences(prefs)
        assertEquals(25, store.read(GeometryKeyboard.TENKEY, false).sideCandidateWidthPercent)
        assertFalse(store.read(GeometryKeyboard.TENKEY, false).sideCandidateVerticalText)
        val configured = KeyboardGeometryPreference(candidates = GeometryCandidatePlacement.LEFT,
            sideCandidateWidthPercent = 40, sideCandidateVerticalText = true)
        store.save(GeometryKeyboard.TENKEY, false, configured)
        assertEquals(configured, store.read(GeometryKeyboard.TENKEY, false))
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.TENKEY, true))
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.SUMIRE, false))
        store.reset(GeometryKeyboard.TENKEY, false)
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.TENKEY, false))
        prefs.edit().putInt("keyboard_geometry_tenkey_portrait_candidate_width_percent", 999)
            .putString("keyboard_geometry_tenkey_portrait_candidate_vertical_text", "invalid").commit()
        assertEquals(SideCandidateSizing.MAX_PERCENT, store.read(GeometryKeyboard.TENKEY, false).sideCandidateWidthPercent)
        assertFalse(store.read(GeometryKeyboard.TENKEY, false).sideCandidateVerticalText)
    }

    @Test fun sideWidthPreservesTheDefaultAndProtectsBothRegions() {
        assertEquals(0, SideCandidateSizing.width(255, 1f, 25))
        assertEquals(64, SideCandidateSizing.width(256, 1f, 25))
        assertEquals(80, SideCandidateSizing.width(320, 1f, 25))
        assertEquals(64, SideCandidateSizing.width(320, 1f, 15))
        assertEquals(128, SideCandidateSizing.width(320, 1f, 50))
        assertEquals(200, SideCandidateSizing.width(400, 1f, 50))
    }

    @Test fun verticalColumnsFollowWidthAndReserveRoomForLargeText() {
        assertEquals(1, SideCandidateSizing.columnCount(64, 1f, 14f))
        assertEquals(1, SideCandidateSizing.columnCount(127, 1f, 14f))
        assertEquals(2, SideCandidateSizing.columnCount(128, 1f, 14f))
        assertEquals(3, SideCandidateSizing.columnCount(200, 1f, 14f))
        assertEquals(1, SideCandidateSizing.columnCount(200, 1f, 80f))
    }

    @Test fun verticalTextKeepsGraphemeClustersTogether() {
        assertEquals("今\n日\nが\n👨‍👩‍👧‍👦", verticalCandidateText("今日が👨‍👩‍👧‍👦"))
        assertEquals("", verticalCandidateText(""))
    }

    @Test fun settingsAreIndependentAndLegacyDefaultsRemainUnchanged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("geometry_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = KeyboardGeometryPreferences(prefs)
        val configured = KeyboardGeometryPreference(
            KeyboardGeometry(splitPercent = 20, curveDegrees = 15, hand = KeyboardCurveHand.LEFT, autoHeight = false,
                curveDirection = KeyboardCurveDirection.UP),
            GeometryCandidatePlacement.CENTER,
        )
        store.save(GeometryKeyboard.QWERTY, true, configured)
        assertEquals(configured, store.read(GeometryKeyboard.QWERTY, true))
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.QWERTY, false))
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.SUMIRE, true))
        store.reset(GeometryKeyboard.QWERTY, true)
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.QWERTY, true))
    }

    @Test fun absentOrInvalidCurveDirectionKeepsTheExistingDirection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("geometry_direction_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("keyboard_geometry_qwerty_portrait_curve", 15).commit()
        val store = KeyboardGeometryPreferences(prefs)
        assertEquals(KeyboardCurveDirection.DOWN, store.read(GeometryKeyboard.QWERTY, false).geometry.curveDirection)
        prefs.edit().putString("keyboard_geometry_qwerty_portrait_curve_direction", "invalid").commit()
        assertEquals(KeyboardCurveDirection.DOWN, store.read(GeometryKeyboard.QWERTY, false).geometry.curveDirection)
        assertEquals(15, store.read(GeometryKeyboard.QWERTY, false).geometry.curveDegrees)
    }

    @Test fun invalidCandidateModesCannotLeakBetweenKeyboardTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("geometry_bad_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("keyboard_geometry_tenkey_portrait_candidates", "CENTER").commit()
        assertEquals(GeometryCandidatePlacement.TOP, KeyboardGeometryPreferences(prefs).read(GeometryKeyboard.TENKEY, false).candidates)
    }

    @Test fun candidateListCanMoveAndReturnWithItsOriginalLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = FrameLayout(context)
        val originalParent = FrameLayout(context)
        val recycler = RecyclerView(context)
        val originalManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recycler.layoutManager = originalManager
        val originalParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 50)
        originalParent.addView(recycler, originalParams)
        root.addView(originalParent)
        var restored = 0
        val host = GeometryCandidateHost(root, recycler) {
            assertSame(originalParent, recycler.parent)
            restored++
        }
        host.show(Rect(100, 20, 230, 260))
        assertTrue(host.attached)
        assertEquals(RecyclerView.VERTICAL, (recycler.layoutManager as LinearLayoutManager).orientation)
        assertNotSame(originalParent, recycler.parent)
        val density = context.resources.displayMetrics.density
        host.show(Rect(0, 0, (200 * density).toInt(), 260), verticalText = true)
        val grid = recycler.layoutManager as GridLayoutManager
        assertEquals(3, grid.spanCount)
        host.show(Rect(0, 0, (80 * density).toInt(), 260), verticalText = true)
        assertSame(grid, recycler.layoutManager)
        assertEquals(1, grid.spanCount)
        host.show(Rect(0, 0, (80 * density).toInt(), 260), verticalText = false)
        assertFalse(recycler.layoutManager is GridLayoutManager)
        assertTrue(host.detach())
        assertSame(originalParent, recycler.parent)
        assertSame(originalManager, recycler.layoutManager)
        assertSame(originalParams, recycler.layoutParams)
        assertFalse(host.detach())
        assertEquals(1, restored)
    }

    @Test fun candidateBoundsFollowScaledFloatingKeyboardAncestors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = FrameLayout(context)
        val scaled = FrameLayout(context)
        val body = View(context)
        host.addView(scaled)
        scaled.addView(body)
        host.layout(0, 0, 600, 600)
        scaled.layout(20, 40, 420, 440)
        scaled.pivotX = 0f; scaled.pivotY = 0f; scaled.scaleX = .5f; scaled.scaleY = .5f
        body.layout(40, 60, 240, 260)
        assertEquals(Rect(40, 70, 140, 170), geometryBoundsInHost(body, host, RectF(0f, 0f, 200f, 200f)))
    }

    @Test fun candidateTouchExclusionFollowsScaledFloatingAncestors() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            val outer = FrameLayout(activity)
            val scaled = FrameLayout(activity).apply { pivotX = 0f; pivotY = 0f; scaleX = .5f; scaleY = .5f }
            val root = FrameLayout(activity)
            val originalParent = FrameLayout(activity)
            val recycler = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            originalParent.addView(recycler)
            root.addView(originalParent)
            scaled.addView(root)
            outer.addView(scaled)
            activity.setContentView(outer)
            val candidateHost = GeometryCandidateHost(root, recycler)
            candidateHost.show(Rect(40, 60, 240, 260))
            outer.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            outer.layout(0, 0, 600, 600)
            val location = IntArray(2).also((recycler.parent as View)::getLocationOnScreen)
            assertTrue(candidateHost.containsScreenPoint(location[0] + 50f, location[1] + 50f))
            assertFalse(candidateHost.containsScreenPoint(location[0] + 150f, location[1] + 50f))
        } finally { activity.finish() }
    }
}
