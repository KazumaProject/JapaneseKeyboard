package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.robolectric.Robolectric
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.inputmethod.CursorAnchorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.BunsetsuSegmentState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class BunsetsuDiffGeometryTest {
    private val snapshot = BunsetsuDiffSnapshot("きょうは", listOf(
        BunsetsuSegmentState("きょう", "今日"), BunsetsuSegmentState("は", "は")), listOf(3))
    private fun info(omit: Int = -1, invisible: Int = -1, text: String = snapshot.text): CursorAnchorInfo {
        val builder = CursorAnchorInfo.Builder().setComposingText(7, text)
            .setMatrix(Matrix().apply { setTranslate(100f, 200f) })
        text.indices.filter { it != omit }.forEach { i ->
            builder.addCharacterBounds(7 + i, i * 10f, 0f, i * 10f + 10f, 20f,
                if (i == invisible) CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION else CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
        }
        return builder.build()
    }

    @Test fun usesAbsoluteUtf16IndicesAndMapsLocalCoordinatesToScreen() {
        assertEquals(listOf(RectF(100f, 200f, 120f, 220f), RectF(120f, 200f, 130f, 220f)), bunsetsuDiffAnchors(info(), snapshot))
    }

    @Test fun missingOrInvisibleCharacterHidesOnlyItsSegmentAndStaleTextIsRejected() {
        assertNull(bunsetsuDiffAnchors(info(omit = 1), snapshot)!![0])
        assertNotNull(bunsetsuDiffAnchors(info(omit = 1), snapshot)!![1])
        assertNull(bunsetsuDiffAnchors(info(invisible = 2), snapshot)!![1])
        assertNull(bunsetsuDiffAnchors(info(text = "古い文字"), snapshot))
    }

    @Test fun bubblesAvoidTextOtherPanelsAndScreenEdges() {
        val anchor = RectF(270f, 200f, 300f, 220f)
        val area = Rect(0, 0, 320, 500)
        val first = placeBunsetsuDiffBubble(anchor, 180, 50, area, emptyList(), 8)!!
        assertTrue(area.contains(first))
        assertTrue(isBunsetsuDiffBubbleAdjacent(first, anchor, 8))
        assertFalse(isBunsetsuDiffBubbleAdjacent(first, RectF(0f, 400f, 20f, 420f), 8))
        assertFalse(RectF.intersects(RectF(first), anchor))
        val second = placeBunsetsuDiffBubble(anchor, 180, 50, area, listOf(first), 8)!!
        assertFalse(Rect.intersects(first, second))
        assertEquals(anchor.bottom.toInt() + 8, second.top)
        assertNull(placeBunsetsuDiffBubble(anchor, 180, 50, area, listOf(area), 8))
        assertNull(placeBunsetsuDiffBubble(RectF(0f, 600f, 10f, 620f), 180, 50, area, emptyList(), 8))
    }

    @Test fun candidateModeStaysAboveTheStripAndHidesIfOnlyTheLowerPositionIsFree() {
        val anchor = RectF(100f, 100f, 150f, 120f)
        val area = Rect(0, 0, 320, 500)
        val above = placeBunsetsuDiffBubble(anchor, 100, 60, area, emptyList(), 8, aboveOnly = true)!!
        assertEquals(92, above.bottom)
        assertNull(placeBunsetsuDiffBubble(anchor, 100, 60, area, listOf(above), 8, aboveOnly = true))
    }

    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun candidateTextGeometryUsesRenderedPaddingAndRejectsClippedOrMissingText() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val label = TextView(activity).apply {
            text = "    昨日は公園で写真を撮りました    "
            textSize = 16f
            setSingleLine(true)
            setPadding(12, 6, 12, 6)
        }
        root.addView(label, FrameLayout.LayoutParams(600, 60))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 640, 600)
        label.layout(20, 100, 620, 160)
        try {
            val candidate = "昨日は公園で写真を撮りました"
            val ranges = listOf(0..2, 3..5, 6..8, 9..13)
            val anchors = bunsetsuDiffCandidateTextAnchors(label, candidate, ranges)!!
            assertTrue("anchors=$anchors label=${label.width}x${label.height} horizontal=${label.layout.getPrimaryHorizontal(4)}..${label.layout.getPrimaryHorizontal(18)} lines=${label.layout.lineCount}", anchors.all { it != null })
            val location = IntArray(2).also(label::getLocationOnScreen)
            assertEquals(location[0] + label.totalPaddingLeft + label.layout.getPrimaryHorizontal(4), anchors[0]!!.left)
            assertTrue(anchors.zipWithNext().all { (a, b) -> a!!.right <= b!!.left })
            assertNull(bunsetsuDiffCandidateTextAnchors(label, "存在しない候補", ranges))
            label.layout(20, 100, 80, 160)
            assertTrue(bunsetsuDiffCandidateTextAnchors(label, candidate, ranges)!!.any { it == null })
            label.visibility = View.GONE
            assertNull(bunsetsuDiffCandidateTextAnchors(label, candidate, ranges))
        } finally { activity.finish() }
    }

    @Test fun aWrappedSegmentUsesItsFirstLineInsteadOfTheEmptySpaceBetweenLines() {
        val builder = CursorAnchorInfo.Builder().setComposingText(0, snapshot.text).setMatrix(Matrix())
        builder.addCharacterBounds(0, 80f, 0f, 100f, 20f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
        builder.addCharacterBounds(1, 0f, 30f, 20f, 50f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
        builder.addCharacterBounds(2, 20f, 30f, 40f, 50f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
        assertEquals(RectF(80f, 0f, 100f, 20f), bunsetsuDiffAnchors(builder.build(), snapshot)!![0])
    }
}
