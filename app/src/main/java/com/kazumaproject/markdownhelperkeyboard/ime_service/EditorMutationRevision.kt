package com.kazumaproject.markdownhelperkeyboard.ime_service

import java.util.concurrent.atomic.AtomicLong

/**
 * Identifies editor mutations that can invalidate asynchronous candidate or composing work.
 *
 * Input text alone is not sufficient: deleting and then re-entering the same text must still
 * reject work that was started before the deletion.
 */
internal class EditorMutationRevision {
    private val value = AtomicLong(0L)

    fun current(): Long = value.get()

    fun advance(): Long = value.incrementAndGet()

    fun restart(): Long = value.incrementAndGet()

    fun isCurrent(revision: Long): Boolean = value.get() == revision
}
