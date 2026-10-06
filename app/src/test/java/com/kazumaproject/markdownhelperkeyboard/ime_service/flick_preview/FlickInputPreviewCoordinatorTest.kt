package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import com.kazumaproject.core.domain.flick.FlickTextPreviewEvent
import com.kazumaproject.core.domain.flick.FlickTextSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlickInputPreviewCoordinatorTest {
    @Test
    fun downMoveCancelOnlyChangesEditorPreview() {
        val fixture = Fixture()
        fixture.arbiter.setCanonical("か", 1)

        fixture.coordinator.onEvent(started("あ"), fixture.context(baseInput = "か"))
        fixture.coordinator.onEvent(changed("う"), fixture.context(baseInput = "か"))
        fixture.coordinator.onEvent(canceled(), fixture.context(baseInput = "か"))

        assertEquals(
            listOf("text:か:1", "text:かあ:1", "text:かう:1", "text:か:1"),
            fixture.writes,
        )
    }

    @Test
    fun matchingCommitCanConsumeExactPreviewMutation() {
        val fixture = Fixture()
        fixture.arbiter.setCanonical("あ", 1)
        fixture.coordinator.onEvent(started("あ"), fixture.context(baseInput = "あ"))
        fixture.coordinator.onEvent(commit("あ"), fixture.context(baseInput = "あ"))

        val mutation = fixture.coordinator.consumePendingCommit("あ", false)
        fixture.coordinator.onEvent(finished(), fixture.context(baseInput = "あ"))

        assertNotNull(mutation)
        assertEquals("い", mutation?.resultInput)
        assertEquals("text:い:1", fixture.writes.last())
    }

    @Test
    fun unconsumedCommitRestoresCanonicalOnFinished() {
        val fixture = Fixture()
        fixture.arbiter.setCanonical("か", 1)
        fixture.coordinator.onEvent(started("あ"), fixture.context(baseInput = "か"))
        fixture.coordinator.onEvent(commit("あ"), fixture.context(baseInput = "か"))
        fixture.coordinator.onEvent(finished(), fixture.context(baseInput = "か"))

        assertEquals("text:か:1", fixture.writes.last())
    }

    @Test
    fun staleSessionAndMismatchedCommitAreIgnored() {
        val fixture = Fixture()
        fixture.arbiter.setCanonical("か", 1)
        fixture.coordinator.onEvent(started("あ"), fixture.context(baseInput = "か"))
        fixture.coordinator.onEvent(changed("う"), fixture.context(sessionId = 2L))

        assertNull(fixture.coordinator.consumePendingCommit("別", false))
        assertEquals("text:かあ:1", fixture.writes.last())
    }

    @Test
    fun downAndMoveUsePreviewTextFactory() {
        val fixture = Fixture(
            createPreviewText = { text, tail -> "decorated[$text|$tail]" }
        )

        fixture.coordinator.onEvent(started("あ"), fixture.context())
        fixture.coordinator.onEvent(changed("う"), fixture.context())

        assertEquals(
            listOf("text:decorated[あ|]:1", "text:decorated[う|]:1"),
            fixture.writes,
        )
    }

    @Test
    fun tailIsRenderedOnDownAndMoveButExcludedFromCommittedInputMutation() {
        val fixture = Fixture(
            createPreviewText = { text, tail -> "$text$tail" }
        )
        val context = fixture.context(baseInput = "か", composingTail = "な")
        fixture.arbiter.setCanonical("かな", 1)

        fixture.coordinator.onEvent(started("あ"), context)
        fixture.coordinator.onEvent(changed("う"), context)
        fixture.coordinator.onEvent(commit("う", isFlick = true), context)
        val mutation = fixture.coordinator.consumePendingCommit("う", isFlick = true)
        fixture.coordinator.onEvent(finished(), context)

        assertEquals(
            listOf("text:かな:1", "text:かあな:1", "text:かうな:1"),
            fixture.writes,
        )
        assertEquals("かう", mutation?.resultInput)
    }

    @Test
    fun delayCombinesUpdatesAndStillPreviewsWhileHeld() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        val context = fixture.context(delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        fixture.coordinator.onEvent(changed("う"), context)
        assertEquals(emptyList<String>(), fixture.writes)
        scheduled.tick()
        assertEquals(listOf("text:う:1"), fixture.writes)
        fixture.coordinator.onEvent(changed("え"), context)
        scheduled.tick()
        assertEquals(listOf("text:う:1", "text:え:1"), fixture.writes)
    }

    @Test
    fun releaseFlushesUnrenderedFinalSelectionExactlyOnce() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        val context = fixture.context(delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        scheduled.tick()
        fixture.coordinator.onEvent(changed("う"), context)
        fixture.coordinator.onEvent(commit("う", true), context)
        assertNotNull(fixture.coordinator.consumePendingCommit("う", true))
        assertNull(fixture.coordinator.consumePendingCommit("う", true))
        fixture.coordinator.onEvent(finished(), context)
        scheduled.tick(includeCanceled = true)
        assertEquals(listOf("text:あ:1", "text:う:1"), fixture.writes)
    }

    @Test
    fun cancelBeforeTimeoutDoesNotInsertAnything() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        val context = fixture.context(delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        fixture.coordinator.onEvent(canceled(), context)
        scheduled.tick(includeCanceled = true)
        assertEquals(emptyList<String>(), fixture.writes)
    }

    @Test
    fun canceledDisplayedPreviewRestoresCanonicalAndDropsPendingUpdate() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        fixture.arbiter.setCanonical("か", 1)
        val context = fixture.context(baseInput = "か", delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        scheduled.tick()
        fixture.coordinator.onEvent(changed("う"), context)
        fixture.coordinator.onEvent(canceled(), context)
        scheduled.tick(includeCanceled = true)
        assertEquals(listOf("text:か:1", "text:かあ:1", "text:か:1"), fixture.writes)
    }

    @Test
    fun resetAndInvalidConnectionPreventQueuedWrites() {
        for (reset in listOf(false, true)) {
            val scheduled = ScheduledRenders()
            var valid = true
            val fixture = Fixture(scheduled = scheduled, valid = { valid })
            fixture.coordinator.onEvent(started("あ"), fixture.context(delayMillis = 80))
            if (reset) fixture.coordinator.resetForEditorSession() else valid = false
            scheduled.tick(includeCanceled = true)
            assertEquals(emptyList<String>(), fixture.writes)
        }
    }

    @Test
    fun newGestureCannotReceivePreviousScheduledRender() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        val context = fixture.context(delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        fixture.coordinator.onEvent(FlickTextPreviewEvent.Started(2L, FlickTextSelection("か", false)), context)
        scheduled.tick(includeCanceled = true)
        assertEquals(listOf("text:か:1"), fixture.writes)
    }

    @Test
    fun zeroDelayStillWritesImmediatelyWithSchedulerInstalled() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        fixture.coordinator.onEvent(started("あ"), fixture.context())
        fixture.coordinator.onEvent(changed("う"), fixture.context())
        assertEquals(listOf("text:あ:1", "text:う:1"), fixture.writes)
        scheduled.tick()
        assertEquals(2, fixture.writes.size)
    }

    @Test
    fun connectionChangeNeverRestoresIntoReplacementEditor() {
        val scheduled = ScheduledRenders()
        var valid = true
        val fixture = Fixture(scheduled = scheduled, valid = { valid })
        val context = fixture.context(delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        scheduled.tick()
        fixture.coordinator.onEvent(changed("う"), context)
        valid = false
        fixture.coordinator.cancel(restore = true)
        scheduled.tick(includeCanceled = true)
        assertEquals(listOf("text:あ:1"), fixture.writes)
    }

    @Test
    fun eachDirectionChangeRestartsTheQuietPeriod() {
        var now = 0L
        data class Timer(val due: Long, val render: () -> Unit)
        val timers = mutableListOf<Timer>()
        val writes = mutableListOf<String>()
        val arbiter = ComposingTextArbiter({ text, _ -> writes += text.toString(); true }, { true })
        val coordinator = FlickInputPreviewCoordinator(arbiter, schedulePreviewRender = { delay, render ->
            timers += Timer(now + delay, render)
            // Exercise even canceled callbacks: token validation must suppress stale timers.
            val cancel: () -> Unit = {}
            cancel
        })
        val context = Fixture().context(delayMillis = 80)
        fun advance(time: Long) {
            now = time
            val ready = timers.filter { it.due <= now }
            timers.removeAll(ready.toSet())
            ready.forEach { it.render() }
        }
        coordinator.onEvent(started("あ"), context)
        advance(70)
        coordinator.onEvent(changed("う"), context)
        advance(80)
        assertEquals(emptyList<String>(), writes)
        advance(149)
        assertEquals(emptyList<String>(), writes)
        advance(150)
        assertEquals(listOf("う"), writes)
    }

    @Test
    fun shortGestureFlushesFinalTextWithoutWaitingForTimer() {
        val timers = ScheduledRenders()
        val fixture = Fixture(scheduled = timers)
        val context = fixture.context(delayMillis = 80)
        fixture.coordinator.onEvent(started("あ"), context)
        fixture.coordinator.onEvent(changed("う"), context)
        fixture.coordinator.onEvent(commit("う", true), context)
        assertNotNull(fixture.coordinator.consumePendingCommit("う", true))
        fixture.coordinator.onEvent(finished(), context)
        timers.tick(includeCanceled = true)
        assertEquals(listOf("text:う:1"), fixture.writes)
    }

    @Test
    fun configuredDelayIsPassedToScheduler() {
        val scheduled = ScheduledRenders()
        val fixture = Fixture(scheduled = scheduled)
        val context = fixture.context(delayMillis = 15)
        fixture.coordinator.onEvent(started("あ"), context)
        fixture.coordinator.onEvent(changed("う"), context)
        assertEquals(listOf(15L, 15L), scheduled.delays)
        assertEquals(emptyList<String>(), fixture.writes)
    }

    private class ScheduledRenders {
        private data class Work(val render: () -> Unit, var canceled: Boolean = false)
        private val work = mutableListOf<Work>()
        val delays = mutableListOf<Long>()
        fun schedule(delayMillis: Long, render: () -> Unit): () -> Unit {
            delays += delayMillis
            val item = Work(render)
            work += item
            return { item.canceled = true }
        }
        fun tick(includeCanceled: Boolean = false) {
            val ready = work.toList()
            work.clear()
            ready.filter { includeCanceled || !it.canceled }.forEach { it.render() }
        }
    }

    private class Fixture(
        createPreviewText: (String, String) -> CharSequence = { text, _ -> text },
        scheduled: ScheduledRenders? = null,
        valid: () -> Boolean = { true },
    ) {
        val writes = mutableListOf<String>()
        val arbiter = ComposingTextArbiter(
            writeComposingText = { text, cursor ->
                writes += "text:$text:$cursor"
                true
            },
            finishComposingText = {
                writes += "finish"
                true
            },
        )
        val coordinator = FlickInputPreviewCoordinator(
            composingTextArbiter = arbiter,
            createPreviewText = createPreviewText,
            schedulePreviewRender = scheduled?.let { it::schedule },
            isContextCurrent = { valid() },
        )

        fun context(
            baseInput: String = "",
            sessionId: Long = 1L,
            composingTail: String = "",
            delayMillis: Long = 0,
        ) = FlickPreviewContext(
            source = FlickPreviewSource.TENKEY,
            editorSessionId = sessionId,
            settingEnabled = true,
            surfaceEligible = true,
            inputBehaviorUsesComposingText = true,
            safeInputType = true,
            isHenkan = false,
            selectMode = false,
            cursorMoveMode = false,
            composingTail = composingTail,
            hasInputConnection = true,
            baseInput = baseInput,
            isFlickOnlyMode = false,
            isContinuousTapInputEnabled = false,
            lastFlickConvertedNextHiragana = false,
            previewDelayMillis = delayMillis,
        )
    }

    private fun started(text: String) = FlickTextPreviewEvent.Started(
        gestureId = 1L,
        selection = FlickTextSelection(text, false),
    )

    private fun changed(text: String) = FlickTextPreviewEvent.Changed(
        gestureId = 1L,
        selection = FlickTextSelection(text, true),
    )

    private fun commit(
        text: String,
        isFlick: Boolean = false,
    ) = FlickTextPreviewEvent.CommitPending(
        gestureId = 1L,
        selection = FlickTextSelection(text, isFlick),
    )

    private fun finished() = FlickTextPreviewEvent.Finished(gestureId = 1L)
    private fun canceled() = FlickTextPreviewEvent.Canceled(gestureId = 1L)
}
