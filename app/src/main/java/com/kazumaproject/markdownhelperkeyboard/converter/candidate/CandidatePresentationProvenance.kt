package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.data.floating_candidate.CandidateItem
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** A user-requested translation/prompt result is independent of the original number grammar. */
internal fun Candidate.withUserRequestedTextResult(input: String, output: String, resultType: Byte): Candidate = copy(
    string = output, commitText = output, type = resultType, yomi = input,
    number = null, generatedNumber = false, temporalSource = null, conversionSegments = emptyList(),
    nonNumericSource = input to output, presentation = null, sourceId = null,
)

/** Retain app-only metadata while the core floating UI holds its lightweight display items. */
internal class FloatingCandidateProvenance {
    private class Key(item: CandidateItem, queue: ReferenceQueue<CandidateItem>? = null) : WeakReference<CandidateItem>(item, queue) {
        private val identityHash = System.identityHashCode(item)
        override fun hashCode(): Int = identityHash
        override fun equals(other: Any?): Boolean = this === other ||
            (other is Key && get() != null && get() === other.get())
    }

    private val queue = ReferenceQueue<CandidateItem>()
    private val candidates = HashMap<Key, Candidate>()

    private fun discardReleasedItems() {
        while (true) candidates.remove((queue.poll() as? Key) ?: break)
    }

    @Synchronized fun present(candidate: Candidate, displayWord: String = candidate.string): CandidateItem {
        discardReleasedItems()
        val item = CandidateItem(
            word = displayWord, length = candidate.length, candidateType = candidate.type,
            sourceId = candidate.sourceId, formulaSource = candidate.presentation?.normalizedTex,
            formulaFallbackText = candidate.commitText,
        )
        candidates[Key(item, queue)] = candidate
        return item
    }

    @Synchronized fun resolve(item: CandidateItem): Candidate? {
        discardReleasedItems()
        return candidates[Key(item)]
    }
}
