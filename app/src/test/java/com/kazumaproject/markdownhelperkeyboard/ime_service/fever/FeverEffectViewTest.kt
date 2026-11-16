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
    @Test fun characterFamiliesChangeBothTheMovingSilhouetteAndTheDominantColor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val masks = mutableListOf<Set<Int>>()
        val shades = mutableSetOf<Int>()
        for (combo in 1..5) {
            val view = FeverEffectView(context).apply {
                layout(0, 0, 500, 800); setArena(RectF(0f, 0f, 500f, 800f))
                protectKeys(RectF(0f, 100f, 500f, 800f))
                setKeyEdges(listOf(RectF(0f, 100f, 500f, 800f)))
                show(FeverEvent(combo), 250f, 450f)
            }
            SystemClock.sleep(260)
            val bitmap = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            // Only the key face: captions/rainbow borders cannot satisfy this assertion.
            masks.add(buildSet<Int> {
                for (y in 110 until 790) for (x in 10 until 490)
                    if (Color.alpha(bitmap.getPixel(x, y)) > 12) add(y * 500 + x)
            })
            shades += FeverVisualStyle.accent(FeverEvent(combo))
            bitmap.recycle(); view.clear()
        }
        assertEquals(5, shades.size)
        masks.forEach { assertTrue("Each family must reach the key face", it.size > 50) }
        for (a in masks.indices) for (b in 0 until a) {
            val overlap = masks[a].intersect(masks[b]).size.toFloat() / masks[a].union(masks[b]).size
            assertTrue("Families $a/$b must have different trajectories, not only captions: $overlap", overlap < .7f)
        }
    }

    @Test fun launchTravelAndExitChangeTheSilhouetteWithoutAnotherInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = FeverEffectView(context).apply {
            layout(0, 0, 500, 800); setArena(RectF(0f, 0f, 500f, 800f))
            show(FeverEvent(10, FeverAward.COMBO), 250f, 400f)
        }
        fun silhouette(): Set<Int> {
            val bitmap = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return buildSet {
                // Exclude the persistent meter and the outer rim: test objects travelling inside.
                for (y in 100 until 700) for (x in 30 until 470)
                    if (Color.alpha(bitmap.getPixel(x, y)) > 80) add(y * 500 + x)
            }.also { bitmap.recycle() }
        }
        SystemClock.sleep(100); val launch = silhouette()
        SystemClock.sleep(180); val travelling = silhouette()
        SystemClock.sleep(300); val confetti = silhouette()
        assertTrue(launch.size > 100)
        for ((before, after) in listOf(launch to travelling, travelling to confetti)) {
            assertTrue("Motion must move occupied pixels, rather than only recolor a static shape",
                after.minus(before).size > 300 && before.minus(after).size > 300)
        }
        SystemClock.sleep(1600)
        view.clear()
        assertTrue("Teardown must stop every animated object", silhouette().isEmpty())
    }

    @Test fun capAndHeldPopupCarrySevenHuesAndMovingHighlightsWithAnimatorsDisabled() {
        val method = android.animation.ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
        method.isAccessible = true
        val previous = android.animation.ValueAnimator.getDurationScale()
        method.invoke(null, 0f)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val skin = requireNotNull(com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(com.kazumaproject.core.domain.skin.KeyboardSkinId.FEVER))
        fun render(drawable: android.graphics.drawable.Drawable): Bitmap {
            drawable.setBounds(0, 0, 240, 160)
            return Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
        }
        try {
            assertFalse(android.animation.ValueAnimator.areAnimatorsEnabled())
            com.kazumaproject.core.ui.skin.FeverKeyPulse.clear()
            val key = render(skin.keyDrawable(context.resources))
            val hues = mutableSetOf<Int>()
            val hsv = FloatArray(3)
            for (y in 0 until key.height) for (x in 0 until key.width) {
                Color.colorToHSV(key.getPixel(x, y), hsv)
                if (hsv[1] > .25f && hsv[2] > .45f) hues.add((hsv[0] / (360f / 7)).toInt())
            }
            assertEquals("All seven hues must exist together on the fine cap rim: $hues", 7, hues.size)
            key.recycle()
            val state = requireNotNull(skin.popupDrawable(context.resources, com.kazumaproject.core.ui.skin.PopupDirection.CENTER).constantState)
            val popup = state.newDrawable(context.resources).mutate()
            // The real IME starts the pulse on Down, including before a character is committed.
            com.kazumaproject.core.ui.skin.FeverKeyPulse.pulse(Color.CYAN, 2)
            // The finger can stay held longer than a character pulse. Popup frames must continue.
            SystemClock.sleep(750); val first = render(popup)
            SystemClock.sleep(170); val second = render(popup)
            assertFalse("A held popup must keep travelling, even at animator scale zero", first.sameAs(second))
            for (y in 40 until 120) for (x in 60 until 180)
                assertEquals("Popup label surface must stay stable", first.getPixel(x, y), second.getPixel(x, y))
            first.recycle(); second.recycle()
            com.kazumaproject.core.ui.skin.FeverKeyPulse.clear()
            val stopped = render(popup)
            SystemClock.sleep(180); val still = render(popup)
            assertTrue("Password/teardown clear must also stop popup animation", stopped.sameAs(still))
            stopped.recycle(); still.recycle()
        } finally {
            com.kazumaproject.core.ui.skin.FeverKeyPulse.clear()
            method.invoke(null, previous)
        }
    }
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
            show(FeverEvent(100, cue = FeverCue.CANDIDATE), 125f, 325f)
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
            assertTrue("Candidate decorations must remain translucent", Color.alpha(pixels[y * 500 + x]) <= 125)
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
    @Test fun boundCandidatesGetTheSameReactiveBorderAndRestoreTheirOriginalTheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter()
        val candidate = com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(string = "候補", type = 1, length = 2u, score = 0, yomi = "こうほ")
        adapter.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.Candidates(listOf(candidate)))
        val end = System.nanoTime() + 2_000_000_000L
        while (adapter.itemCount == 0 && System.nanoTime() < end) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        assertTrue(adapter.itemCount > 0)
        val holder = adapter.createViewHolder(android.widget.FrameLayout(context), adapter.getItemViewType(0))
        fun render(): Bitmap {
            holder.itemView.background.setBounds(0, 0, 240, 100)
            return Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888).also { holder.itemView.background.draw(Canvas(it)) }
        }
        adapter.onBindViewHolder(holder, 0)
        val original = render()
        try {
            adapter.setFeverCandidateBorders(true); adapter.onBindViewHolder(holder, 0)
            val resting = render()
            assertFalse(original.sameAs(resting))
            com.kazumaproject.core.ui.skin.FeverKeyPulse.pulse(Color.MAGENTA, 2)
            val reacting = render(); assertFalse("Typing must light the actual candidate background", resting.sameAs(reacting)); reacting.recycle()
            com.kazumaproject.core.ui.skin.FeverKeyPulse.clear()
            adapter.setFeverCandidateBorders(false); adapter.onBindViewHolder(holder, 0)
            val restored = render(); assertTrue("Other themes must get their original candidate background", original.sameAs(restored)); restored.recycle()
            assertEquals("候補", holder.itemView.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.suggestion_item_text_view).text.toString().trim())
            resting.recycle()
        } finally { com.kazumaproject.core.ui.skin.FeverKeyPulse.clear(); original.recycle(); adapter.release() }
    }
    @Test fun downMoveAndReleaseRenderWithoutACharacterEventAndStopOnClear() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = FeverEffectView(context).apply {
            layout(0, 0, 500, 500); setArena(RectF(0f, 0f, 500f, 500f))
            protectKeys(RectF(0f, 0f, 500f, 500f)); setKeyEdges(listOf(RectF(10f, 150f, 490f, 450f)))
        }
        fun render(): Bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        for ((index, action) in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP).withIndex()) {
            view.touchPhase(action, 150f + index * 40, 250f, Color.CYAN)
            SystemClock.sleep(30)
            val bitmap = render()
            assertTrue("Phase $action must render before any text is committed", (180 until 350).any { y -> (100 until 350).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 } })
            bitmap.recycle()
        }
        view.clear()
        val clean = render(); val pixels = IntArray(500 * 500).also { clean.getPixels(it, 0, 500, 0, 0, 500, 500) }
        assertTrue("No touch trail may survive IME teardown", pixels.all { it == Color.TRANSPARENT }); clean.recycle()
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
