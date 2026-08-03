package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposingTextArbiterTest {
    @Test
    fun canonicalWritesAreHeldWhilePreviewIsVisibleAndCancelRestoresLatest() {
        val writes = mutableListOf<String>()
        val arbiter = arbiter(writes)
        arbiter.setCanonical("か", 1)
        arbiter.showPreview("かあ", 1)

        assertTrue(arbiter.setCanonical("最新", 1))
        assertEquals(listOf("text:か:1", "text:かあ:1"), writes)

        arbiter.cancelPreviewAndRestore()
        assertEquals("text:最新:1", writes.last())
    }

    @Test
    fun suspendedPreviewRestoresCanonicalAndCanResume() {
        val writes = mutableListOf<String>()
        val arbiter = arbiter(writes)
        arbiter.setCanonical("か", 1)
        arbiter.showPreview("かあ", 1)
        arbiter.suspendPreviewAndRestore()
        arbiter.showPreview("かう", 1)

        assertEquals(
            listOf("text:か:1", "text:かあ:1", "text:か:1", "text:かう:1"),
            writes,
        )
    }

    @Test
    fun releaseKeepsPreviewDisplayed() {
        val writes = mutableListOf<String>()
        val arbiter = arbiter(writes)
        arbiter.setCanonical("か", 1)
        arbiter.showPreview("かあ", 1)

        arbiter.releasePreview(leaveDisplayedText = true)

        assertEquals("text:かあ:1", writes.last())
        assertEquals(2, writes.size)
    }

    @Test
    fun cancelFromNoCanonicalCompositionRemovesPreviewInsteadOfFinishingIt() {
        val writes = mutableListOf<String>()
        val arbiter = arbiter(writes)
        arbiter.showPreview("あ", 1)

        arbiter.cancelPreviewAndRestore()

        assertEquals(listOf("text:あ:1", "text::0"), writes)
    }

    private fun arbiter(writes: MutableList<String>) = ComposingTextArbiter(
        writeComposingText = { text, cursor ->
            writes += "text:$text:$cursor"
            true
        },
        finishComposingText = {
            writes += "finish"
            true
        },
    )
}
