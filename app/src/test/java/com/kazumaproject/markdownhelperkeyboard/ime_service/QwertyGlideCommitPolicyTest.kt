package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_GLIDE_CANDIDATE_TYPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideCommitPolicyTest {
    @Test
    fun tapQwertyGlideCandidateCommitsCandidateStringWithoutTail() {
        val decision = QwertyGlideCommitPolicy.resolveTapCommitDecision(
            candidate = candidate("hero", QWERTY_GLIDE_CANDIDATE_TYPE, length = 4),
            insertString = "hello"
        )

        assertTrue(decision is QwertyGlideTapCommitDecision.CommitQwertyGlideCandidate)
        assertEquals(
            "hero",
            (decision as QwertyGlideTapCommitDecision.CommitQwertyGlideCandidate).commitText
        )
    }

    @Test
    fun normalCandidateWithExcessInputKeepsTailInStandardFlow() {
        val decision = QwertyGlideCommitPolicy.resolveTapCommitDecision(
            candidate = candidate("hero", type = 1, length = 4),
            insertString = "hello"
        )

        assertTrue(decision is QwertyGlideTapCommitDecision.UseStandardCandidateFlow)
        assertEquals(
            "o",
            (decision as QwertyGlideTapCommitDecision.UseStandardCandidateFlow).tailForPartialOrExcess
        )
    }

    @Test
    fun preferenceOneFalseSkipsPreviousGlideCommitOnNewGlide() {
        val decision = previousDecision(
            commitPrevious = false,
            insertSpace = true
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Skip, decision)
    }

    @Test
    fun preferenceOneTrueAndPreferenceTwoFalseCommitsPreviousCandidateWithoutSpace() {
        val decision = previousDecision(
            commitPrevious = true,
            insertSpace = false
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Commit("hello"), decision)
    }

    @Test
    fun preferenceOneTrueAndPreferenceTwoTrueCommitsPreviousCandidateWithSpace() {
        val decision = previousDecision(
            commitPrevious = true,
            insertSpace = true
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Commit("hello "), decision)
    }

    @Test
    fun nonQwertyFirstCandidateSkipsPreviousGlideCommitOnNewGlide() {
        val decision = previousDecision(
            commitPrevious = true,
            insertSpace = true,
            firstCandidate = candidate("なにか", type = 1, length = 3),
            inputString = "なにか",
            currentQwertyGlideCompositionText = "なにか"
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Skip, decision)
    }

    @Test
    fun nonEmptyStringInTailSkipsPreviousGlideCommitOnNewGlide() {
        val decision = previousDecision(
            commitPrevious = true,
            stringInTail = "tail"
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Skip, decision)
    }

    @Test
    fun romajiModeSkipsPreviousGlideCommitOnNewGlide() {
        val decision = previousDecision(
            commitPrevious = true,
            currentQwertyRomajiModeForSession = true
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Skip, decision)
    }

    @Test
    fun glideInputDisabledSkipsPreviousGlideCommitOnNewGlide() {
        val decision = previousDecision(
            qwertyGlideInputPreference = false,
            commitPrevious = true
        )

        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Skip, decision)
    }

    @Test
    fun type36IsNotQwertyGlideCandidate() {
        val type36Candidate = candidate("hello", type = 36.toByte(), length = 5)

        assertFalse(QwertyGlideCommitPolicy.isQwertyGlideCandidate(type36Candidate))
        val tapDecision = QwertyGlideCommitPolicy.resolveTapCommitDecision(
            candidate = type36Candidate,
            insertString = "hello"
        )
        assertTrue(tapDecision is QwertyGlideTapCommitDecision.UseStandardCandidateFlow)
        assertNull(
            (tapDecision as QwertyGlideTapCommitDecision.UseStandardCandidateFlow).tailForPartialOrExcess
        )
        val previousDecision = previousDecision(
            commitPrevious = true,
            firstCandidate = type36Candidate
        )
        assertEquals(QwertyGlidePreviousCandidateCommitDecision.Skip, previousDecision)
    }

    private fun previousDecision(
        qwertyGlideInputPreference: Boolean = true,
        commitPrevious: Boolean,
        insertSpace: Boolean = false,
        inputString: String = "hello",
        stringInTail: String = "",
        currentQwertyRomajiModeForSession: Boolean = false,
        firstCandidate: Candidate? = candidate("hello", QWERTY_GLIDE_CANDIDATE_TYPE, 5),
        currentQwertyGlideCompositionText: String? = "hello"
    ): QwertyGlidePreviousCandidateCommitDecision {
        return QwertyGlideCommitPolicy.resolvePreviousGlideCommitDecision(
            qwertyGlideInputPreference = qwertyGlideInputPreference,
            qwertyGlideCommitPreviousCandidateOnNewGlidePreference = commitPrevious,
            qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference = insertSpace,
            inputString = inputString,
            stringInTail = stringInTail,
            currentQwertyRomajiModeForSession = currentQwertyRomajiModeForSession,
            firstCandidate = firstCandidate,
            currentQwertyGlideCompositionText = currentQwertyGlideCompositionText
        )
    }

    private fun candidate(string: String, type: Byte, length: Int): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = length.toUByte(),
            score = 1000
        )
    }
}
