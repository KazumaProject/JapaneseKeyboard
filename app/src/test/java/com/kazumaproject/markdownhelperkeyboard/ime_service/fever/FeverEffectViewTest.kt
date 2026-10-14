package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeverEffectViewTest {
    @Test fun allBurstsAndEditingCuesLeaveTheKeyboardPixelsTransparent() {
        for (top in listOf(100f, 400f)) {
            for (event in ((1..9) + listOf(11, 26)).map { FeverEvent(it) } + FeverAward.entries.map { FeverEvent(10, it) } +
                FeverCue.entries.map { FeverEvent(4, cue = it) }) {
                verifyProtectedPixels(event, RectF(0f, top, 500f, 800f))
            }
        }
    }
    @Test fun movingFloatingPanelsStayVisibleWithEffectsOnEverySide() {
        for (box in listOf(RectF(50f, 100f, 350f, 600f), RectF(150f, 200f, 500f, 800f))) {
            verifyProtectedPixels(FeverEvent(100, FeverAward.JACKPOT), box)
        }
    }
    @Test fun energyReachesTheKeysAndCandidatesWithoutCoveringTheirText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keys = listOf(RectF(10f, 200f, 240f, 450f), RectF(260f, 200f, 490f, 450f))
        val view = FeverEffectView(context).apply {
            layout(0, 0, 500, 800); setArena(RectF(0f, 0f, 500f, 800f))
            protectKeys(RectF(0f, 80f, 500f, 800f))
            setKeyEdges(keys, RectF(0f, 80f, 500f, 190f))
            show(FeverEvent(100, FeverAward.JACKPOT), 125f, 325f)
        }
        SystemClock.sleep(300)
        val bitmap = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(500 * 800).also { bitmap.getPixels(it, 0, 500, 0, 0, 500, 800) }
        assertTrue("Key borders must animate as well as the banner", (200 until 450).any { y ->
            (0 until 500).any { x -> Color.alpha(pixels[y * 500 + x]) > 0 }
        })
        assertTrue("Stickers should also reach the key faces", keys.any { box ->
            (box.top.toInt() + 20 until box.bottom.toInt() - 20).any { y ->
                (box.left.toInt() + 20 until box.right.toInt() - 20).any { x -> Color.alpha(pixels[y * 500 + x]) > 0 }
            }
        })
        keys.forEach { box ->
            val inside = RectF(box).apply { inset(6 * context.resources.displayMetrics.density, 6 * context.resources.displayMetrics.density) }
            for (y in inside.top.toInt() until inside.bottom.toInt()) for (x in inside.left.toInt() until inside.right.toInt())
                assertTrue("Sticker overlays must keep the key surface visible", Color.alpha(pixels[y * 500 + x]) <= 96)
        }
        for (y in 80 until 180) for (x in 0 until 500)
            assertTrue("Candidate wash must stay below 8% opacity", Color.alpha(pixels[y * 500 + x]) <= 20)
        assertTrue("Candidate bottom edge should glow", (180 until 190).any { y ->
            (0 until 500).any { x -> Color.alpha(pixels[y * 500 + x]) > 0 }
        })
        view.clear(); bitmap.recycle()
    }
    @Test fun everyInputIncludingAfterTenLightsCapsWithoutChangingTheirLabelSurface() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val skin = requireNotNull(com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(com.kazumaproject.core.domain.skin.KeyboardSkinId.FEVER))
        // TenKey distributes independent key backgrounds through ConstantState.newDrawable().
        val state = requireNotNull(skin.keyDrawable(context.resources).constantState)
        val drawable = state.newDrawable(context.resources).mutate().apply { setBounds(0, 0, 160, 160) }
        val sibling = state.newDrawable(context.resources).mutate().apply { setBounds(0, 0, 90, 80) }
        assertNotSame(drawable, sibling)
        assertEquals(160, drawable.bounds.width())
        fun render(): Bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
        com.kazumaproject.core.ui.skin.FeverKeyPulse.clear()
        val resting = render()
        try {
            for (combo in listOf(1, 2, 3, 4, 5, 9, 11, 26)) {
                com.kazumaproject.core.ui.skin.FeverKeyPulse.pulse(FeverVisualStyle.accent(FeverEvent(combo)), 2)
                SystemClock.sleep(90)
                val lit = render()
                assertFalse("Every input must change the cap lighting, including combo $combo", resting.sameAs(lit))
                for (y in 40 until 120) for (x in 40 until 120)
                    assertEquals("Central label contrast must stay constant", resting.getPixel(x, y), lit.getPixel(x, y))
                lit.recycle()
            }
            SystemClock.sleep(600)
            val expired = render(); assertTrue("Lighting must expire", resting.sameAs(expired)); expired.recycle()
            com.kazumaproject.core.ui.skin.FeverKeyPulse.pulse(Color.MAGENTA, 2, true)
            com.kazumaproject.core.ui.skin.FeverKeyPulse.clear()
            val stopped = render(); assertTrue("Hiding/changing the theme must restore caps immediately", resting.sameAs(stopped)); stopped.recycle()
        } finally { com.kazumaproject.core.ui.skin.FeverKeyPulse.clear(); resting.recycle() }
    }
    private fun verifyProtectedPixels(event: FeverEvent, keys: RectF) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = FeverEffectView(context).apply {
            layout(0, 0, 500, 800)
            setArena(RectF(0f, 0f, 500f, 800f)); protectKeys(keys)
            intensity = 2; show(event, keys.centerX(), keys.centerY())
        }
        SystemClock.sleep(300)
        val bitmap = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(500 * 800).also { bitmap.getPixels(it, 0, 500, 0, 0, 500, 800) }
        assertTrue("Effect must actually render for $event", pixels.any { Color.alpha(it) > 0 })
        for (y in keys.top.toInt() until keys.bottom.toInt()) for (x in keys.left.toInt() until keys.right.toInt()) {
            if (pixels[y * 500 + x] != Color.TRANSPARENT) fail("$event obscures key pixel ($x, $y)")
        }
        view.clear(); bitmap.recycle()
    }
}
