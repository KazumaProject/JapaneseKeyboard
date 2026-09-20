package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Point
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.core.ui.skin.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks real WindowManager placement with a non-zero window origin, including Android 7. */
@RunWith(AndroidJUnit4::class)
class SkinPopupCompatibilityInstrumentedTest {
    private fun fittedBubbleLocation(anchor: View, direction: PopupDirection): Point {
        val screen = IntArray(2).also(anchor::getLocationOnScreen)
        val surface = Rect(SkinPopupGeometry.resolve(150, 100, direction, true).bounds)
            .apply { offset(screen[0], screen[1]) }
        val size = Point().also { anchor.display.getRealSize(it) }
        val bars = ViewCompat.getRootWindowInsets(anchor)!!.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val viewport = Rect(bars.left, bars.top, size.x - bars.right, size.y - bars.bottom)
        val dx = if (surface.width() <= viewport.width()) {
            surface.left.coerceIn(viewport.left, viewport.right - surface.width()) - surface.left
        } else viewport.left - surface.left
        val dy = if (surface.height() <= viewport.height()) {
            surface.top.coerceIn(viewport.top, viewport.bottom - surface.height()) - surface.top
        } else viewport.top - surface.top
        surface.offset(dx, dy)
        return Point(surface.left, surface.top)
    }

    @Test fun splitPanelBottomKeyKeepsBottomFlickBubbleAtFittedScreenPosition() {
        val ins = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<SkinTestHostActivity>(Intent(ins.targetContext, SkinTestHostActivity::class.java)).use { scenario ->
            lateinit var manager: WindowManager
            lateinit var panel: FrameLayout
            lateinit var anchor: TextView
            lateinit var popup: PopupWindow
            lateinit var bubble: KeyWindowLayout
            var panelAdded = false
            var popupCreated = false
            var expectedBubble = Point()
            val laidOut = java.util.concurrent.CountDownLatch(1)
            try {
                scenario.onActivity { host ->
                    manager = host.getSystemService(WindowManager::class.java)
                    panel = FrameLayout(host)
                    anchor = TextView(host).apply { text = "か" }
                    panel.addView(anchor, FrameLayout.LayoutParams(150, 100).apply {
                        leftMargin = 35
                        topMargin = 40
                    })
                    val display = Point().also { host.windowManager.defaultDisplay.getRealSize(it) }
                    val params = WindowManager.LayoutParams(
                        220,
                        180,
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        token = host.window.decorView.windowToken
                        gravity = Gravity.TOP or Gravity.LEFT
                        x = 0
                        y = display.y - height - 8
                    }
                    manager.addView(panel, params)
                    panelAdded = true
                    panel.viewTreeObserver.addOnGlobalLayoutListener {
                        if (panel.width == 220 && anchor.width == 150) laidOut.countDown()
                    }
                }
                assertTrue("Bottom panel did not finish layout", laidOut.await(10, java.util.concurrent.TimeUnit.SECONDS))
                ins.waitForIdleSync()
                scenario.onActivity { host ->
                    assertNotNull(anchor.windowToken)
                    assertNotNull(anchor.applicationWindowToken)
                    assertNotSame(anchor.windowToken, anchor.applicationWindowToken)
                    bubble = KeyWindowLayout(host).apply { skinId = KeyboardSkinId.CUPERTINO_LIGHT }
                    bubble.addView(TextView(host).apply { text = "く" }, FrameLayout.LayoutParams(-1, -1))
                    popup = PopupWindow(bubble, 150, 100, false)
                    popupCreated = true
                    expectedBubble = fittedBubbleLocation(anchor, PopupDirection.BOTTOM)
                    SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.BOTTOM, true)
                }
                ins.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(popup.isShowing)
                    assertEquals(expectedBubble, Point().also { point ->
                        val location = IntArray(2).also(bubble::getLocationOnScreen)
                        point.set(location[0], location[1])
                    })
                    assertFalse(popup.isTouchable)
                }
            } finally {
                scenario.onActivity {
                    if (popupCreated) popup.dismiss()
                    if (panelAdded && panel.parent != null) manager.removeViewImmediate(panel)
                }
            }
        }
    }

    @Test fun splitPanelUsesApplicationTokenWithoutHiddenPopupApi() {
        val ins = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<SkinTestHostActivity>(Intent(ins.targetContext, SkinTestHostActivity::class.java)).use { scenario ->
            lateinit var manager: WindowManager
            lateinit var panel: FrameLayout
            lateinit var anchor: TextView
            lateinit var popup: PopupWindow
            lateinit var bubble: KeyWindowLayout
            var panelAdded = false
            var popupCreated = false
            val laidOut = java.util.concurrent.CountDownLatch(1)
            try {
                scenario.onActivity { host ->
                    manager = host.getSystemService(WindowManager::class.java)
                    panel = FrameLayout(host)
                    anchor = TextView(host).apply { text = "か" }
                    panel.addView(anchor, FrameLayout.LayoutParams(150, 100).apply {
                        leftMargin = 35
                        topMargin = 40
                    })
                    val params = WindowManager.LayoutParams(
                        220,
                        180,
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        token = host.window.decorView.windowToken
                        gravity = Gravity.TOP or Gravity.LEFT
                        x = 0
                        y = 700
                    }
                    manager.addView(panel, params)
                    panelAdded = true
                    panel.viewTreeObserver.addOnGlobalLayoutListener {
                        if (panel.width == 220 && anchor.width == 150) laidOut.countDown()
                    }
                }
                assertTrue("Panel did not finish layout", laidOut.await(10, java.util.concurrent.TimeUnit.SECONDS))
                ins.waitForIdleSync()
                scenario.onActivity { host ->
                    assertNotNull(anchor.windowToken)
                    assertNotNull(anchor.applicationWindowToken)
                    assertNotSame(anchor.windowToken, anchor.applicationWindowToken)
                    bubble = KeyWindowLayout(host).apply { skinId = KeyboardSkinId.CUPERTINO_LIGHT }
                    bubble.addView(TextView(host).apply { text = "く" }, FrameLayout.LayoutParams(-1, -1))
                    popup = PopupWindow(bubble, 150, 100, false)
                    popupCreated = true
                }

                val union = android.graphics.Rect()
                PopupDirection.entries.forEach {
                    union.union(SkinPopupGeometry.resolve(150, 100, it, true).bounds)
                }
                var expectedFrame = Point()
                for (direction in PopupDirection.entries) {
                    var expectedBubble = Point()
                    scenario.onActivity {
                        val screen = IntArray(2).also(anchor::getLocationOnScreen)
                        expectedFrame = Point(screen[0] + union.left, screen[1] + union.top)
                        expectedBubble = fittedBubbleLocation(anchor, direction)
                        SkinPopupPlacement.show(popup, bubble, anchor, direction, true)
                    }
                    ins.waitForIdleSync()
                    scenario.onActivity {
                        assertTrue("Popup was not shown for $direction", popup.isShowing)
                        assertEquals(direction, bubble.skinDirection)
                        assertEquals(expectedFrame, Point().also { point ->
                            val location = IntArray(2).also(popup.contentView::getLocationOnScreen)
                            point.set(location[0], location[1])
                        })
                        assertEquals(expectedBubble, Point().also { point ->
                            val location = IntArray(2).also(bubble::getLocationOnScreen)
                            point.set(location[0], location[1])
                        })
                        assertEquals(union.width(), popup.contentView.width)
                        assertEquals(union.height(), popup.contentView.height)
                        assertFalse(popup.isTouchable)
                    }
                }
                scenario.onActivity {
                    popup.dismiss()
                    assertFalse(popup.isShowing)
                }
                lateinit var guide: SkinGuidePopup
                scenario.onActivity { host ->
                    guide = SkinGuidePopup(host)
                    guide.show(
                        anchor,
                        requireNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_DARK)),
                        PopupDirection.entries.associateWith { "か" },
                    )
                }
                ins.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(guide.isShowing)
                    val field = guide.javaClass.getDeclaredField("overflowWindow").apply { isAccessible = true }
                    val overflow = field.get(guide) as PopupWindow
                    assertTrue(overflow.isShowing)
                    guide.dismiss()
                    assertFalse(guide.isShowing)
                }
            } finally {
                scenario.onActivity {
                    if (popupCreated) popup.dismiss()
                    if (panelAdded && panel.parent != null) {
                        manager.removeViewImmediate(panel)
                    }
                }
            }
        }
    }

    @Test fun offsetWindowKeepsAnchoredFlickAndOverflowGuideWorking() {
        val ins = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<SkinTestHostActivity>(Intent(ins.targetContext, SkinTestHostActivity::class.java)).use { scenario ->
            lateinit var anchor: View
            val laidOut = java.util.concurrent.CountDownLatch(1)
            scenario.onActivity { host ->
                host.window.attributes = host.window.attributes.apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = 80; y = 180; width = 240; height = 200
                }
                anchor = TextView(host).apply { text = "か" }
                host.setContentView(FrameLayout(host).apply {
                    addView(anchor, FrameLayout.LayoutParams(150, 100))
                    viewTreeObserver.addOnGlobalLayoutListener {
                        if (width == 240 && anchor.width == 150) laidOut.countDown()
                    }
                })
            }
            assertTrue("Offset host did not finish layout", laidOut.await(10, java.util.concurrent.TimeUnit.SECONDS))
            ins.waitForIdleSync()
            lateinit var popup: PopupWindow
            lateinit var bubble: KeyWindowLayout
            lateinit var guide: SkinGuidePopup
            val cleanup = mutableListOf<() -> Unit>()
            var expected = Point()
            try {
                scenario.onActivity { host ->
                    val screen = IntArray(2).also(anchor::getLocationOnScreen)
                    val local = IntArray(2).also(anchor::getLocationInWindow)
                    assertFalse("Host must have an offset", screen.contentEquals(local))
                    bubble = KeyWindowLayout(host).apply { skinId = KeyboardSkinId.CUPERTINO_LIGHT }
                    bubble.addView(TextView(host).apply { text = "く" }, FrameLayout.LayoutParams(-1, -1))
                    popup = PopupWindow(bubble, 150, 100, false)
                    cleanup.add { popup.dismiss() }
                    val top = SkinPopupGeometry.resolve(150, 100, PopupDirection.TOP, true).bounds
                    expected = Point(screen[0] + top.left, screen[1] + top.top)
                    SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.TOP, true)
                }
                ins.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(expected, Point().also { point ->
                        val p = IntArray(2).also(bubble::getLocationOnScreen)
                        point.set(p[0], p[1])
                    })
                    assertFalse(popup.isTouchable)
                    SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.RIGHT, true)
                }
                ins.waitForIdleSync()
                scenario.onActivity { host ->
                    val screen = IntArray(2).also(anchor::getLocationOnScreen)
                    val right = SkinPopupGeometry.resolve(150, 100, PopupDirection.RIGHT, true).bounds
                    val p = IntArray(2).also(bubble::getLocationOnScreen)
                    assertEquals(Point(screen[0] + right.left, screen[1] + right.top), Point(p[0], p[1]))
                    popup.dismiss()
                    bubble.skinId = KeyboardSkinId.DEFAULT
                    assertSame(bubble, popup.contentView)
                    assertTrue(popup.isTouchable)
                    guide = SkinGuidePopup(host)
                    cleanup.add { guide.dismiss() }
                    val guideScreen = IntArray(2).also(anchor::getLocationOnScreen)
                    val size = Point().also { anchor.display.getRealSize(it) }
                    val bars = ViewCompat.getRootWindowInsets(anchor)!!.getInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    expected = Point((guideScreen[0] - 150).coerceIn(bars.left, size.x - bars.right - 450),
                        (guideScreen[1] - 100).coerceIn(bars.top, size.y - bars.bottom - 300))
                    guide.show(anchor, requireNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_DARK)),
                        listOf(PopupDirection.CENTER, PopupDirection.LEFT, PopupDirection.TOP,
                            PopupDirection.RIGHT, PopupDirection.BOTTOM).associateWith { "か" })
                }
                ins.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(guide.isShowing)
                    val field = guide.javaClass.getDeclaredField("overflowWindow").apply { isAccessible = true }
                    val overflow = field.get(guide) as PopupWindow
                    val p = IntArray(2).also(overflow.contentView::getLocationOnScreen)
                    assertEquals(expected, Point(p[0], p[1]))
                    assertFalse(overflow.isTouchable)
                    guide.dismiss()
                    assertFalse(guide.isShowing)
                }
            } finally {
                scenario.onActivity {
                    cleanup.forEach { it() }
                }
            }
        }
    }
}
