package com.kazumaproject.markdownhelperkeyboard.learning.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionLearningSessionTest {
    @Test
    fun separatedConversionLearnsSegmentsAndCompletePhrase() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("しうんすみか")
        session.record(fragment("しうん", "紫雲", index = 2))
        session.record(fragment("すみ", "清", index = 1))
        session.record(fragment("か", "夏", index = 3))

        val entries = session.finish(learnFirstCandidate = false, timestamp = 123L)

        assertTrue(entries.any { it.input == "しうん" && it.out == "紫雲" })
        assertTrue(entries.any { it.input == "すみ" && it.out == "清" })
        assertTrue(entries.any { it.input == "か" && it.out == "夏" })
        assertTrue(
            entries.any {
                it.input == "しうんすみか" && it.out == "紫雲清夏" && it.isPhrase
            }
        )
    }

    @Test
    fun partialPredictionKeepsOriginalReadingUntilLastFragment() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("しうんすみか")
        session.record(fragment("しうん", "紫雲", index = 1))
        session.record(fragment("すみ", "清", index = 2))
        session.record(fragment("か", "夏", index = 1))

        val entries = session.finish(learnFirstCandidate = false)

        assertTrue(entries.any { it.input == "しうんすみ" && it.out == "紫雲清" })
        assertTrue(entries.any { it.input == "しうんすみか" && it.out == "紫雲清夏" })
    }

    @Test
    fun firstCandidatesFollowDedicatedPreference() {
        fun finished(learnFirst: Boolean): List<com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity> {
            val session = ConversionLearningSession()
            session.beginIfNeeded("しうんすみか")
            session.record(fragment("しうん", "紫雲", index = 0))
            session.record(fragment("すみか", "清夏", index = 0))
            return session.finish(learnFirstCandidate = learnFirst)
        }

        assertTrue(finished(learnFirst = false).isEmpty())
        assertEquals("紫雲清夏", finished(learnFirst = true).single { it.isPhrase }.out)
    }

    private fun fragment(reading: String, output: String, index: Int) = LearningFragment(
        reading = reading,
        output = output,
        candidateScore = 40_000,
        candidateIndex = index,
    )
}
