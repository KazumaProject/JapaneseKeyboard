package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.inputmethodservice.InputMethodService.Insets
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockedCandidateInsetsTest {
    @Test
    fun transparentSpacePassesThroughWhileVisibleKeyboardAndOverlaysRemainTouchable() {
        Robolectric.buildActivity(Activity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val container = FrameLayout(activity)
            val keyboard = View(activity)
            val overlay = View(activity)
            val hidden = View(activity).apply { visibility = View.GONE }
            container.addView(keyboard)
            container.addView(overlay)
            container.addView(hidden)
            activity.setContentView(container)
            container.layout(0, 100, 300, 700)
            overlay.layout(10, 20, 110, 70)
            hidden.layout(0, 0, 300, 600)
            val origin = IntArray(2)
            container.getLocationInWindow(origin)
            val stableTop = origin[1]

            for (keyboardTop in listOf(80, 120, 160, 80)) {
                keyboard.layout(0, keyboardTop, 300, 600)
                val insets = Insets()
                applyDockedCandidateInsets(container, true, insets)
                assertEquals(stableTop, insets.contentTopInsets)
                assertEquals(stableTop, insets.visibleTopInsets)
                assertEquals(Insets.TOUCHABLE_INSETS_REGION, insets.touchableInsets)
                assertFalse(insets.touchableRegion.contains(origin[0] + 150, stableTop + 10))
                assertTrue(insets.touchableRegion.contains(origin[0] + 20, stableTop + 30))
                assertTrue(insets.touchableRegion.contains(origin[0] + 150, stableTop + keyboardTop + 1))
            }

            overlay.visibility = View.GONE
            val visiblePosition = IntArray(2)
            keyboard.getLocationInWindow(visiblePosition)
            val offInsets = Insets()
            applyDockedCandidateInsets(container, false, offInsets)
            assertEquals(visiblePosition[1], offInsets.contentTopInsets)
            assertEquals(visiblePosition[1], offInsets.visibleTopInsets)
        }
    }

    @Test
    fun emptyOrHiddenContainerLeavesFrameworkInsetsAlone() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val container = FrameLayout(context)
        container.addView(View(context))
        val insets = Insets().apply {
            contentTopInsets = 12
            visibleTopInsets = 34
            touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
        applyDockedCandidateInsets(container, true, insets)
        assertEquals(12, insets.contentTopInsets)
        assertEquals(34, insets.visibleTopInsets)
        assertEquals(Insets.TOUCHABLE_INSETS_CONTENT, insets.touchableInsets)
    }
}
