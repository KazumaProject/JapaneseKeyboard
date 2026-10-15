package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import android.content.Context
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Matrix
import android.app.Activity
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class BunsetsuDiffControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val palette = CandidatePanelColors(Color.WHITE, Color.WHITE, Color.BLACK, Color.GRAY, Color.BLUE, Color.WHITE)
    private fun result(reading: String) = listOf("橋", "箸").let { outputs ->
        KanaKanjiQueryResult(outputs.map { Candidate(it, 1, reading.length.toUByte(), 1000) },
            candidateSegmentsByString = outputs.associateWith { listOf(CandidateConversionSegment(0, reading.length, it)) })
    }
    private fun snapshot(controller: BunsetsuDiffController): BunsetsuDiffSnapshot? = ReflectionHelpers.getField(controller, "snapshot")

    @Test fun aLateUncancellableQueryCannotRestoreAClearedEditor() = runTest {
        val reply = CompletableDeferred<KanaKanjiQueryResult>()
        val geometry = mutableListOf<Boolean>()
        val controller = BunsetsuDiffController(context, this, { true },
            { withContext(NonCancellable) { reply.await() } }, { palette }, { geometry.add(it) }, { _, _, _ -> true })
        controller.update("はし", "はし")
        advanceTimeBy(120); runCurrent()
        controller.clear()
        reply.complete(result("はし"))
        advanceUntilIdle()
        assertNull(snapshot(controller))
        assertFalse(geometry.last())
    }

    @Test fun debounceQueriesOnlyTheLatestReadingAndRetainsTheLockedOrderAcrossSelection() = runTest {
        val queries = mutableListOf<String>()
        val controller = BunsetsuDiffController(context, this, { true },
            { queries.add(it); result(it) }, { palette }, {}, { _, _, _ -> true })
        controller.update("は", "は")
        advanceTimeBy(70)
        controller.update("はし", "はし")
        advanceUntilIdle()
        assertEquals(listOf("はし"), queries)
        val before = snapshot(controller)!!
        val selected = before.segments.single().copy(selectedIndex = 1, displayText = "橋")
        controller.updateSelection(listOf(selected))
        assertEquals("橋", snapshot(controller)!!.text)
        assertEquals(before.segments.single().candidates, snapshot(controller)!!.segments.single().candidates)
        assertTrue(ReflectionHelpers.getField(controller, "frozen"))
        controller.updateSelection(listOf(selected.copy(selectedIndex = 2, displayText = "箸")))
        advanceUntilIdle()
        assertEquals(listOf("はし"), queries)
        assertEquals("箸", snapshot(controller)!!.text)
        controller.stop()
    }

    @Test fun disablingEligibilityDiscardsPendingWorkAndStopsGeometryMonitoring() = runTest {
        var enabled = true
        val geometry = mutableListOf<Boolean>()
        val controller = BunsetsuDiffController(context, this, { enabled },
            { result(it) }, { palette }, { geometry.add(it) }, { _, _, _ -> true })
        controller.update("はし", "はし")
        enabled = false
        controller.update("はし", "はし")
        advanceUntilIdle()
        assertNull(snapshot(controller))
        assertFalse(geometry.last())
    }

    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun candidatePlacementNeedsNoEditorReplyAndLocksItsReferenceUntilTheSettingChanges() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val keyboard = View(activity)
        root.addView(keyboard, FrameLayout.LayoutParams(320, 100))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 320, 600)
        keyboard.layout(0, 500, 320, 600)
        var candidateMode = true
        var selectDirectly = true
        var visibleCandidate = "橋"
        val locks = mutableListOf<String?>()
        val monitoring = mutableListOf<Boolean>()
        val selections = mutableListOf<Pair<Int, Int?>>()
        val controller = BunsetsuDiffController(activity, this, { true }, { result(it) }, { palette },
            { monitoring.add(it) }, { _, segment, candidate -> selections.add(segment to candidate); true },
            directSelection = { selectDirectly },
            candidatePlacement = { candidateMode },
            candidateAnchors = { _, locked ->
                locks.add(locked)
                if (locked != null && locked != visibleCandidate) null
                else BunsetsuDiffCandidateAnchors(visibleCandidate, listOf(RectF(30f, 400f, 80f, 420f)))
            })
        fun labels(): List<LinearLayout> = ReflectionHelpers.getField<Map<Int, Any>>(controller, "bubbles")
            .values.map { ReflectionHelpers.getField(it, "view") }
        fun mounted() = ReflectionHelpers.getField<Map<Int, Any>>(controller, "bubbles").values.any {
            ReflectionHelpers.getField<Any?>(it, "params") != null
        }
        try {
            controller.start(keyboard)
            controller.update("はし", "はし")
            advanceUntilIdle()
            assertTrue(mounted())
            assertFalse(monitoring.last())
            controller.onAnchor(null)
            assertTrue(mounted())
            val options = labels().single()
            assertEquals(3, options.childCount)
            assertTrue(options.getChildAt(0).isSelected)
            assertFalse(options.isClickable)
            options.getChildAt(2).performClick()
            assertEquals(listOf(0 to 2), selections)
            val before = snapshot(controller)!!
            controller.updateSelection(listOf(before.segments.single().copy(displayText = "箸", selectedIndex = 2)))
            assertTrue(options.getChildAt(2).isSelected)
            assertFalse(options.getChildAt(0).isSelected)
            options.getChildAt(2).performClick()
            options.getChildAt(0).performClick()
            assertEquals(listOf(0 to 2, 0 to 2, 0 to 0), selections)
            selectDirectly = false
            controller.updateSelection(snapshot(controller)!!.segments)
            assertTrue(options.isClickable)
            assertFalse(options.getChildAt(2).isClickable)
            options.performClick()
            assertEquals(0 to null, selections.last())
            selectDirectly = true
            controller.updateSelection(snapshot(controller)!!.segments)
            assertFalse(options.isClickable)
            assertTrue(options.getChildAt(1).isClickable)
            options.getChildAt(1).performClick()
            assertEquals(0 to 1, selections.last())
            visibleCandidate = "箸"
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("橋", locks.last())
            assertTrue(labels().isEmpty())
            assertNotNull(snapshot(controller))
            candidateMode = false
            controller.onPlacementChanged()
            assertTrue(monitoring.last())
            assertTrue(labels().isEmpty())
            candidateMode = true
            controller.onPlacementChanged()
            assertNull(locks.last())
            assertTrue(mounted())
            assertFalse(monitoring.last())
            val staleOption = labels().single().getChildAt(1)
            controller.clear()
            staleOption.performClick()
            assertEquals(5, selections.size)
        } finally { controller.stop(); activity.finish() }
    }

    @Test fun aMeasuredBubbleWithoutRoomCanReceiveAnotherGeometryReplyBeforeMounting() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val keyboard = View(activity)
        root.addView(keyboard, FrameLayout.LayoutParams(320, 520))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 320, 600)
        keyboard.layout(0, 80, 320, 600)
        val controller = BunsetsuDiffController(activity, this, { true },
            { result(it) }, { palette }, {}, { _, _, _ -> true })
        try {
            controller.start(keyboard)
            controller.update("はし", "はし")
            advanceUntilIdle()
            val reply = CursorAnchorInfo.Builder().setComposingText(0, "はし").setMatrix(Matrix())
                .addCharacterBounds(0, 30f, 50f, 40f, 70f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
                .addCharacterBounds(1, 40f, 50f, 50f, 70f, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION).build()
            controller.onAnchor(reply)
            val bubbles = ReflectionHelpers.getField<Map<Int, Any>>(controller, "bubbles")
            assertEquals(1, bubbles.size)
            val label = ReflectionHelpers.getField<LinearLayout>(bubbles.values.single(), "view")
            assertNotNull(label.layoutParams)
            assertFalse(label.isAttachedToWindow)
            controller.onAnchor(reply)
            assertFalse(label.isAttachedToWindow)
        } finally {
            controller.stop()
            activity.finish()
        }
    }
}
