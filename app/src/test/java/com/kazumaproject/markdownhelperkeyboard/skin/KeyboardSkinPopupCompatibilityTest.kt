package com.kazumaproject.markdownhelperkeyboard.skin

import android.app.Activity
import android.graphics.Point
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.PopupDirection
import com.kazumaproject.core.ui.skin.SkinGuidePopup
import com.kazumaproject.core.ui.skin.SkinPopupPlacement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 29, 35])
class KeyboardSkinPopupCompatibilityTest {
    private class RecordingPopup(content: View) : PopupWindow(content, 100, 60, false) {
        var shown = false
        var position = Point()
        var updates = 0
        var anchoredCalls = 0
        var tokenCalls = 0
        var regularShowAtLocationCalls = 0

        private fun recordAnchoredPosition(anchor: View, xOffset: Int, yOffset: Int) {
            val screen = IntArray(2)
            val inWindow = IntArray(2)
            anchor.getLocationOnScreen(screen)
            anchor.getLocationInWindow(inWindow)
            val windowOrigin = Point(screen[0] - inWindow[0], screen[1] - inWindow[1])
            position = Point(
                screen[0] + xOffset - windowOrigin.x,
                screen[1] + anchor.height + yOffset - windowOrigin.y,
            )
        }

        override fun isShowing() = shown
        override fun dismiss() { shown = false }
        override fun showAsDropDown(anchor: View, xoff: Int, yoff: Int) {
            anchoredCalls++
            recordAnchoredPosition(anchor, xoff, yoff)
            shown = true
        }
        override fun update(anchor: View, xoff: Int, yoff: Int, width: Int, height: Int) {
            recordAnchoredPosition(anchor, xoff, yoff)
            updates++
        }
        override fun update(x: Int, y: Int, width: Int, height: Int) {
            position = Point(x, y)
            updates++
        }
        override fun showAtLocation(parent: View, gravity: Int, x: Int, y: Int) {
            regularShowAtLocationCalls++
            position = Point(x, y)
            shown = true
        }
        fun showAtLocation(_token: IBinder, _gravity: Int, x: Int, y: Int) {
            tokenCalls++
            position = Point(x, y)
            shown = true
        }
    }

    private fun withHost(panel: Boolean = false, block: (Activity, View) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        controller.setup()
        // Model an IME/floating host whose window origin is (40, 200) on screen.
        val anchor = object : View(activity) {
            private val panelToken = Binder()
            private val applicationToken = Binder()
            override fun getLocationOnScreen(out: IntArray) { out[0] = 140; out[1] = 500 }
            override fun getLocationInWindow(out: IntArray) { out[0] = 100; out[1] = 300 }
            override fun getWindowToken() = if (panel) panelToken else super.getWindowToken()
            override fun getApplicationWindowToken() = if (panel) applicationToken else super.getApplicationWindowToken()
        }
        activity.setContentView(anchor)
        activity.window.decorView.layout(0, 0, 1080, 1920)
        anchor.layout(100, 300, 200, 360)
        try { block(activity, anchor) } finally { controller.pause().stop().destroy() }
    }

    private fun expectedUnionPosition(anchor: View): Point {
        val bounds = android.graphics.Rect()
        PopupDirection.entries.forEach {
            bounds.union(com.kazumaproject.core.ui.skin.SkinPopupGeometry.resolve(
                anchor.width, anchor.height, it, true).bounds)
        }
        val screen = IntArray(2)
        val inWindow = IntArray(2)
        anchor.getLocationOnScreen(screen)
        anchor.getLocationInWindow(inWindow)
        return Point(
            screen[0] + bounds.left - (screen[0] - inWindow[0]),
            screen[1] + bounds.top - (screen[1] - inWindow[1]),
        )
    }

    @Test fun flickKeepsOneAnchoredSurfaceAcrossDirectionsAndKeepsInputDisabled() = withHost(panel = true) { activity, anchor ->
        val bubble = KeyWindowLayout(activity).apply { skinId = KeyboardSkinId.CUPERTINO_LIGHT }
        bubble.addView(TextView(activity), FrameLayout.LayoutParams(-1, -1))
        val popup = RecordingPopup(bubble)
        SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.TOP, true)
        val initialPosition = expectedUnionPosition(anchor)
        assertEquals(initialPosition, popup.position)
        assertEquals(0, popup.updates)
        assertSame(popup.contentView, bubble.parent)
        SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.LEFT, true)
        assertEquals(initialPosition, popup.position)
        assertEquals(0, popup.updates)
        anchor.layout(100, 300, 220, 380)
        SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.RIGHT, true)
        assertEquals(expectedUnionPosition(anchor), popup.position)
        assertEquals(1, popup.updates)
        SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.BOTTOM, true)
        assertEquals(expectedUnionPosition(anchor), popup.position)
        assertEquals(1, popup.updates)
        assertEquals(0, popup.regularShowAtLocationCalls)
        if (Build.VERSION.SDK_INT >= 29) assertTrue(popup.anchoredCalls > 0)
        else assertTrue(popup.tokenCalls > 0)
        assertFalse(popup.isTouchable)
        assertFalse(popup.isClippingEnabled)
        assertTrue(popup.isShowing)
        popup.dismiss()
        bubble.skinId = KeyboardSkinId.DEFAULT
        assertSame(bubble, popup.contentView)
        assertTrue(popup.isTouchable)
        assertTrue(popup.isClippingEnabled)
    }

    @Test fun overflowGuideCanBeDismissedAndShownAgainOnEverySupportedApi() = withHost { activity, anchor ->
        activity.window.decorView.layout(0, 0, 50, 50)
        val guide = SkinGuidePopup(activity)
        val skin = requireNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_DARK))
        val labels = mapOf(PopupDirection.CENTER to "か", PopupDirection.TOP to "く")
        repeat(2) {
            guide.show(anchor, skin, labels)
            assertTrue(guide.isShowing)
            val field = guide.javaClass.getDeclaredField("overflowWindow").apply { isAccessible = true }
            val popup = field.get(guide) as PopupWindow
            assertFalse(popup.isTouchable)
            assertFalse(popup.isClippingEnabled)
            guide.select(PopupDirection.TOP)
            guide.dismiss()
            assertFalse(guide.isShowing)
        }
    }
}
