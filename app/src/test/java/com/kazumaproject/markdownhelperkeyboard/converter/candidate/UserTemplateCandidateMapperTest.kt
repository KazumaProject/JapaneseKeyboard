package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UserTemplateCandidateMapperTest {

    @Test
    fun userTemplateUsesDedicatedCandidateType() {
        val candidate = template(
            word = "ありがとうございます",
            reading = "あり"
        ).toUserTemplateCandidate()

        assertEquals(CANDIDATE_TYPE_USER_TEMPLATE, candidate.type)
        assertNotEquals(30.toByte(), candidate.type)
        assertEquals("ありがとうございます", candidate.string)
        assertEquals(2, candidate.length.toInt())
    }

    @Test
    fun sharedMapperAssignsDedicatedTypeToEveryTemplateAndSortsByScore() {
        val candidates = listOf(
            template("https://example.com", "さいと", score = 300),
            template("ありがとうございます", "あり", score = 100),
            template("test@example.com", "めーる", score = 200),
        ).toUserTemplateCandidates()

        assertEquals(
            listOf("ありがとうございます", "test@example.com", "https://example.com"),
            candidates.map { it.string }
        )
        candidates.forEach { candidate ->
            assertEquals(CANDIDATE_TYPE_USER_TEMPLATE, candidate.type)
            assertNotEquals(30.toByte(), candidate.type)
        }
    }

    private fun template(
        word: String,
        reading: String,
        score: Int = 4000
    ): UserTemplate {
        return UserTemplate(
            word = word,
            reading = reading,
            posIndex = 0,
            posScore = score
        )
    }
}
