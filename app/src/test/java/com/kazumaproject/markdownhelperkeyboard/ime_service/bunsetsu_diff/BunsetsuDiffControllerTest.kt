package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.app.Activity
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.widget.FrameLayout
import android.widget.TextView
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
            { withContext(NonCancellable) { reply.await() } }, { palette }, { geometry.add(it) }, { _, _ -> true })
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
            { queries.add(it); result(it) }, { palette }, {}, { _, _ -> true })
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
            { result(it) }, { palette }, { geometry.add(it) }, { _, _ -> true })
        controller.update("はし", "はし")
        enabled = false
        controller.update("はし", "はし")
        advanceUntilIdle()
        assertNull(snapshot(controller))
        assertFalse(geometry.last())
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
            { result(it) }, { palette }, {}, { _, _ -> true })
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
            val label = ReflectionHelpers.getField<TextView>(bubbles.values.single(), "view")
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
