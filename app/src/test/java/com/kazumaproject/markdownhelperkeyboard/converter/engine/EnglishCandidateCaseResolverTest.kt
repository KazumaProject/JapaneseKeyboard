package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishCandidateCaseResolverTest {
    @Test
    fun resolvesCandidateCase() {
        val cases = listOf(
            Triple("com", "computer", "computer"),
            Triple("Com", "computer", "Computer"),
            Triple("COM", "computer", "COMPUTER"),
            Triple("cOm", "computer", "cOmputer"),
            Triple("github", "GitHub", "GitHub"),
            Triple("Git", "GitHub", "GitHub"),
            Triple("GIT", "GitHub", "GITHUB"),
            Triple("iphone", "iPhone", "iPhone"),
            Triple("iph", "iPhone", "iPhone"),
            Triple("api", "API", "API"),
            Triple("Api", "API", "API"),
            Triple("API", "API", "API")
        )

        cases.forEach { (input, candidate, expected) ->
            assertEquals("$input + $candidate", expected, resolveCandidateCase(input, candidate))
        }
    }

    @Test
    fun considersOnlyLettersWhenDetectingAllCaps() {
        assertEquals("COMPUTER2", resolveCandidateCase("C2", "computer2"))
        assertEquals("C++LANGUAGE", resolveCandidateCase("C++", "c++language"))
        assertEquals("123computer", resolveCandidateCase("123", "123computer"))
    }

    @Test
    fun putsResolvedCaseFirstAndKeepsFixedPenaltyVariants() {
        assertEquals(
            listOf("Computer" to 0, "computer" to 500, "COMPUTER" to 2000),
            candidateCaseVariants("Com", "computer")
        )
        assertEquals(
            listOf(
                "cOmputer" to 0,
                "computer" to 500,
                "Computer" to 500,
                "COMPUTER" to 2000
            ),
            candidateCaseVariants("cOm", "computer")
        )
        assertEquals(
            listOf("GitHub" to 0, "GITHUB" to 2000),
            candidateCaseVariants("Git", "GitHub")
        )
        assertEquals(
            listOf("GITHUB" to 0, "GitHub" to 500),
            candidateCaseVariants("GIT", "GitHub")
        )
    }
}
