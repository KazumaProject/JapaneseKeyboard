package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.view.Gravity
import android.view.View
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
    @Test fun offsetWindowKeepsFlickAndOverflowGuideAtTheirScreenAnchors() {
        val ins = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<SkinFidelityHostActivity>(Intent(ins.targetContext, SkinFidelityHostActivity::class.java)).use { scenario ->
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
                    val union = Rect()
                    PopupDirection.entries.forEach { union.union(SkinPopupGeometry.resolve(150, 100, it, true).bounds) }
                    expected = Point(screen[0] + union.left, screen[1] + union.top)
                    SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.TOP, true)
                }
                ins.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(expected, Point().also { point ->
                        val p = IntArray(2).also(popup.contentView::getLocationOnScreen)
                        point.set(p[0], p[1])
                    })
                    assertFalse(popup.isTouchable)
                    SkinPopupPlacement.show(popup, bubble, anchor, PopupDirection.RIGHT, true)
                }
                ins.waitForIdleSync()
                scenario.onActivity { host ->
                    val p = IntArray(2).also(popup.contentView::getLocationOnScreen)
                    assertEquals(expected, Point(p[0], p[1]))
                    popup.dismiss()
                    bubble.skinId = KeyboardSkinId.DEFAULT
                    assertSame(bubble, popup.contentView)
                    assertTrue(popup.isTouchable)
                    guide = SkinGuidePopup(host)
                    cleanup.add { guide.dismiss() }
                    val screen = IntArray(2).also(anchor::getLocationOnScreen)
                    val size = Point().also { anchor.display.getRealSize(it) }
                    val bars = ViewCompat.getRootWindowInsets(anchor)!!.getInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    expected = Point((screen[0] - 150).coerceIn(bars.left, size.x - bars.right - 450),
                        (screen[1] - 100).coerceIn(bars.top, size.y - bars.bottom - 300))
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
