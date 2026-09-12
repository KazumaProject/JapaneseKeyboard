package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposingGuideControllerTest {
    @Test fun switchesDetachOldHostBeforeAttachingNewAndPreserveContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean(ComposingGuideSettings.ENABLED, true).commit()
        val controller = ComposingGuideController(context, { true }, {}, {}, { CandidatePanelColors.resolve(context) }, { 48 })
        val windows = mockedWindows(controller)
        val host = mock<View>()
        try {
            controller.start(host)
            controller.update("赤", "あか", true)
            verify(windows.getValue(GuideProfile.INTEGRATED)).update("赤", "あか", true)
            clearInvocations(*windows.values.toTypedArray())
            prefs.edit().putString(ComposingGuideSettings.DISPLAY_MODE, "separate").commit()
            controller.refresh()
            inOrder(*windows.values.toTypedArray()) {
                verify(windows.getValue(GuideProfile.INTEGRATED)).stop()
                verify(windows.getValue(GuideProfile.CANDIDATES)).start(host, ComposingGuideContent("赤", "あか", true))
                verify(windows.getValue(GuideProfile.TEXT)).start(host, ComposingGuideContent("赤", "あか", true))
            }
            verify(windows.getValue(GuideProfile.TEXT)).update("赤", "あか", true)
            clearInvocations(*windows.values.toTypedArray())
            prefs.edit().putBoolean(ComposingGuideSettings.ENABLED, false).commit()
            controller.refresh()
            verify(windows.getValue(GuideProfile.CANDIDATES)).stop()
            verify(windows.getValue(GuideProfile.TEXT), never()).stop()
            verify(windows.getValue(GuideProfile.TEXT), never()).start(any(), any())
        } finally { controller.destroy() }
    }

    @Test fun frontTextPanelCapturesTransferredGestureThroughDismissal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean(ComposingGuideSettings.ENABLED, true)
            .putString(ComposingGuideSettings.DISPLAY_MODE, "separate").commit()
        val controller = ComposingGuideController(context, { true }, {}, {}, { CandidatePanelColors.resolve(context) }, { 48 })
        val windows = mockedWindows(controller)
        val text = windows.getValue(GuideProfile.TEXT)
        val candidates = windows.getValue(GuideProfile.CANDIDATES)
        whenever(text.dispatchInputWindowTouch(any())).thenReturn(true)
        controller.start(mock())
        fun send(action: Int): Boolean {
            val event = MotionEvent.obtain(0, 0, action, 0f, 0f, 0)
            return try { controller.dispatchInputWindowTouch(event) } finally { event.recycle() }
        }
        try {
            assertTrue(send(MotionEvent.ACTION_DOWN))
            controller.stop()
            assertTrue(send(MotionEvent.ACTION_MOVE))
            assertTrue(send(MotionEvent.ACTION_UP))
            assertFalse(send(MotionEvent.ACTION_MOVE))
            verify(candidates, never()).dispatchInputWindowTouch(any())
            verify(text, times(3)).dispatchInputWindowTouch(any())
        } finally { controller.destroy() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockedWindows(controller: ComposingGuideController): Map<GuideProfile, ComposingGuideWindow> {
        val field = ComposingGuideController::class.java.getDeclaredField("windows").apply { isAccessible = true }
        val map = field.get(controller) as MutableMap<GuideProfile, ComposingGuideWindow>
        map.values.forEach { it.destroy() }
        GuideProfile.entries.forEach { map[it] = mock() }
        return map
    }
}
