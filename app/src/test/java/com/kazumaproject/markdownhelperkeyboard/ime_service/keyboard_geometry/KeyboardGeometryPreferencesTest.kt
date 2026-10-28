package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardGeometryPreferencesTest {
    @Test fun settingsAreIndependentAndLegacyDefaultsRemainUnchanged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("geometry_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = KeyboardGeometryPreferences(prefs)
        val configured = KeyboardGeometryPreference(
            KeyboardGeometry(splitPercent = 20, curveDegrees = 15, hand = KeyboardCurveHand.LEFT, autoHeight = false),
            GeometryCandidatePlacement.CENTER,
        )
        store.save(GeometryKeyboard.QWERTY, true, configured)
        assertEquals(configured, store.read(GeometryKeyboard.QWERTY, true))
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.QWERTY, false))
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.SUMIRE, true))
        store.reset(GeometryKeyboard.QWERTY, true)
        assertEquals(KeyboardGeometryPreference(), store.read(GeometryKeyboard.QWERTY, true))
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
}
