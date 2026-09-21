package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 30], qualifiers = "w500dp-h1000dp-mdpi")
class FloatingKeyboardWindowTest {
    @Test fun resizeAndDismissKeepPopupLifecycleAndReleaseWindow() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(activity))
        val panel = FloatingKeyboardPanel(binding, {}, { _, _, _ -> })
        val popup = FloatingKeyboardWindow(panel, 300)
        var dismissals = 0
        popup.setOnDismissListener { dismissals++ }
        try {
            assertFalse(popup.isShowing)
            popup.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.LEFT, 40, 80)
            assertTrue(popup.isShowing)
            assertSame(panel, popup.contentView)
            assertNotNull(panel.parent)
            popup.update(240, ViewGroup.LayoutParams.WRAP_CONTENT)
            assertEquals(240, popup.width)
            assertEquals(240, panel.measuredWidth)
            popup.update(20, 60, -1, -1)
            assertEquals(240, popup.width)
            popup.dismiss()
            assertFalse(popup.isShowing)
            assertNull(panel.parent)
            popup.dismiss()
            assertEquals(1, dismissals)
            popup.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.LEFT, 20, 60)
            assertTrue(popup.isShowing)
            popup.dismiss()
            assertEquals(2, dismissals)
        } finally {
            popup.dismiss()
            activity.finish()
        }
    }
}
