package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorMutationRevisionTest {

    @Test
    fun advanceInvalidatesEarlierAsyncWork() {
        val revision = EditorMutationRevision()
        val beforeDelete = revision.current()

        revision.advance()

        assertFalse(revision.isCurrent(beforeDelete))
        assertTrue(revision.isCurrent(revision.current()))
    }

    @Test
    fun retypingTheSameTextStillGetsANewRevision() {
        val revision = EditorMutationRevision()
        val firstInput = revision.current()

        revision.advance()
        val afterDelete = revision.current()
        revision.advance()

        assertNotEquals(firstInput, afterDelete)
        assertNotEquals(afterDelete, revision.current())
        assertFalse(revision.isCurrent(firstInput))
    }
}
