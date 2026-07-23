package com.kazumaproject.markdownhelperkeyboard.learning.session

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy

data class LearningFragment(
    val reading: String,
    val output: String,
    val candidateScore: Int,
    val candidateIndex: Int,
    val leftId: Short? = null,
    val rightId: Short? = null,
    val explicitlySelected: Boolean = false,
)

/** Collects every committed fragment until the original reading has been fully committed. */
class ConversionLearningSession {
    private var originalReading: String? = null
    private val fragments = mutableListOf<LearningFragment>()

    val isActive: Boolean
        get() = originalReading != null

    fun beginIfNeeded(reading: String) {
        if (originalReading == null && reading.isNotEmpty()) {
            originalReading = reading
        }
    }

    fun record(fragment: LearningFragment) {
        if (originalReading == null || fragment.reading.isEmpty() || fragment.output.isEmpty()) return
        fragments += fragment
    }

    fun finish(
        learnFirstCandidate: Boolean,
        timestamp: Long = System.currentTimeMillis(),
    ): List<LearnEntity> {
        val reading = originalReading
        val recorded = fragments.toList()
        cancel()
        if (reading.isNullOrEmpty() || recorded.isEmpty()) return emptyList()

        val learnable = recorded.filter {
            learnFirstCandidate || it.explicitlySelected || it.candidateIndex != 0
        }
        if (learnable.isEmpty()) return emptyList()

        val segmentEntries = learnable.map { fragment ->
            fragment.toEntity(timestamp = timestamp, isPhrase = false)
        }
        val cumulativePhraseEntries = recorded.indices.drop(1).mapNotNull { lastIndex ->
            val phraseFragments = recorded.take(lastIndex + 1)
            val containsLearnableSelection = phraseFragments.any {
                learnFirstCandidate || it.explicitlySelected || it.candidateIndex != 0
            }
            if (!containsLearnableSelection) return@mapNotNull null
            LearnEntity(
                input = phraseFragments.joinToString(separator = "") { it.reading },
                out = phraseFragments.joinToString(separator = "") { it.output },
                score = phraseScore(phraseFragments),
                leftId = phraseFragments.first().leftId,
                rightId = phraseFragments.last().rightId,
                usageCount = 1,
                lastUsedAt = timestamp,
                isPhrase = true,
            )
        }
        val completeOutput = recorded.joinToString(separator = "") { it.output }
        val completeEntry = LearnEntity(
            input = reading,
            out = completeOutput,
            score = phraseScore(recorded),
            leftId = recorded.firstOrNull()?.leftId,
            rightId = recorded.lastOrNull()?.rightId,
            usageCount = 1,
            lastUsedAt = timestamp,
            isPhrase = recorded.size > 1 || reading != recorded.first().reading,
        )

        return (segmentEntries + cumulativePhraseEntries + completeEntry)
            .distinctBy { it.input to it.out }
    }

    fun cancel() {
        originalReading = null
        fragments.clear()
    }

    private fun LearningFragment.toEntity(timestamp: Long, isPhrase: Boolean) = LearnEntity(
        input = reading,
        out = output,
        score = LearningScorePolicy.initial(candidateScore, candidateIndex),
        leftId = leftId,
        rightId = rightId,
        usageCount = 1,
        lastUsedAt = timestamp,
        isPhrase = isPhrase,
    )

    private fun phraseScore(fragments: List<LearningFragment>): Int =
        LearningScorePolicy.phrase(
            fragments.map {
                LearningScorePolicy.initial(it.candidateScore, it.candidateIndex)
            }
        )
}
