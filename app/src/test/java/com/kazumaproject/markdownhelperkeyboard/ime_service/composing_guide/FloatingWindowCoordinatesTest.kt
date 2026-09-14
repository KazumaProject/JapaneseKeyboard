package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.graphics.Insets
import android.graphics.Rect
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingWindowCoordinatesTest {
    @Test fun insetsAndNonzeroMetricOriginAreAppliedExactlyOnce() {
        val manager = mock<WindowManager>()
        val insets = WindowInsets.Builder().setInsetsIgnoringVisibility(WindowInsets.Type.systemBars(),
            Insets.of(126, 74, 0, 5)).build()
        whenever(manager.currentWindowMetrics).thenReturn(WindowMetrics(Rect(100, 200, 2500, 1280), insets))
        val coordinates = FloatingWindowCoordinates(manager)
        val safe = coordinates.safeArea(View(ApplicationProvider.getApplicationContext()))
        assertEquals(Rect(226, 274, 2500, 1275), safe)
        val params = WindowManager.LayoutParams()
        coordinates.configure(params)
        coordinates.position(params, safe.left, safe.top)
        assertEquals(126, params.x)
        assertEquals(74, params.y)
        assertEquals(0, params.fitInsetsTypes)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, params.layoutInDisplayCutoutMode)
    }
}
