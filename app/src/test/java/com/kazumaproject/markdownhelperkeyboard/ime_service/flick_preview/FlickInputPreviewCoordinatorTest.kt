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
        val fixture = Fixture(createPreviewText = { text -> "decorated[$text]" })

        fixture.coordinator.onEvent(started("あ"), fixture.context())
        fixture.coordinator.onEvent(changed("う"), fixture.context())

        assertEquals(
            listOf("text:decorated[あ]:1", "text:decorated[う]:1"),
            fixture.writes,
        )
    }

    private class Fixture(
        createPreviewText: (String) -> CharSequence = { it },
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
        )

        fun context(baseInput: String = "", sessionId: Long = 1L) = FlickPreviewContext(
            source = FlickPreviewSource.TENKEY,
            editorSessionId = sessionId,
            settingEnabled = true,
            surfaceEligible = true,
            inputBehaviorUsesComposingText = true,
            safeInputType = true,
            isHenkan = false,
            selectMode = false,
            cursorMoveMode = false,
            hasComposingTail = false,
            hasInputConnection = true,
            baseInput = baseInput,
            isFlickOnlyMode = false,
            isContinuousTapInputEnabled = false,
            lastFlickConvertedNextHiragana = false,
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

    private fun commit(text: String) = FlickTextPreviewEvent.CommitPending(
        gestureId = 1L,
        selection = FlickTextSelection(text, false),
    )

    private fun finished() = FlickTextPreviewEvent.Finished(gestureId = 1L)
    private fun canceled() = FlickTextPreviewEvent.Canceled(gestureId = 1L)
}
