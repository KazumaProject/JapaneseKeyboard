package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_GLIDE_CANDIDATE_TYPE

sealed class QwertyGlideTapCommitDecision {
    data class CommitQwertyGlideCandidate(val commitText: String) : QwertyGlideTapCommitDecision()
    data class UseStandardCandidateFlow(val tailForPartialOrExcess: String?) :
        QwertyGlideTapCommitDecision()
}

sealed class QwertyGlidePreviousCandidateCommitDecision {
    data class Commit(val commitText: String) : QwertyGlidePreviousCandidateCommitDecision()
    data object Skip : QwertyGlidePreviousCandidateCommitDecision()
}

object QwertyGlideCommitPolicy {
    fun isQwertyGlideCandidate(candidate: Candidate): Boolean {
        return candidate.type == QWERTY_GLIDE_CANDIDATE_TYPE
    }

    fun resolveTapCommitDecision(
        candidate: Candidate,
        insertString: String
    ): QwertyGlideTapCommitDecision {
        if (isQwertyGlideCandidate(candidate)) {
            return QwertyGlideTapCommitDecision.CommitQwertyGlideCandidate(candidate.string)
        }
        val tail = if (insertString.length > candidate.length.toInt()) {
            insertString.substring(candidate.length.toInt())
        } else {
            null
        }
        return QwertyGlideTapCommitDecision.UseStandardCandidateFlow(tail)
    }

    fun shouldCommitPreviousGlideCandidateOnNewGlide(
        qwertyGlideInputPreference: Boolean,
        qwertyGlideCommitPreviousCandidateOnNewGlidePreference: Boolean,
        inputString: String,
        stringInTail: String,
        currentQwertyRomajiModeForSession: Boolean,
        firstCandidate: Candidate?,
        currentQwertyGlideCompositionText: String?
    ): Boolean {
        if (!qwertyGlideInputPreference) return false
        if (!qwertyGlideCommitPreviousCandidateOnNewGlidePreference) return false
        if (inputString.isEmpty()) return false
        if (stringInTail.isNotEmpty()) return false
        if (currentQwertyRomajiModeForSession) return false
        val candidate = firstCandidate ?: return false
        if (!isQwertyGlideCandidate(candidate)) return false
        if (currentQwertyGlideCompositionText == null) return false
        return inputString == candidate.string && currentQwertyGlideCompositionText == candidate.string
    }

    fun resolvePreviousGlideCommitText(
        firstCandidate: Candidate,
        qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference: Boolean
    ): String {
        return if (qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference) {
            firstCandidate.string + " "
        } else {
            firstCandidate.string
        }
    }

    fun resolvePreviousGlideCommitDecision(
        qwertyGlideInputPreference: Boolean,
        qwertyGlideCommitPreviousCandidateOnNewGlidePreference: Boolean,
        qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference: Boolean,
        inputString: String,
        stringInTail: String,
        currentQwertyRomajiModeForSession: Boolean,
        firstCandidate: Candidate?,
        currentQwertyGlideCompositionText: String?
    ): QwertyGlidePreviousCandidateCommitDecision {
        if (!shouldCommitPreviousGlideCandidateOnNewGlide(
                qwertyGlideInputPreference = qwertyGlideInputPreference,
                qwertyGlideCommitPreviousCandidateOnNewGlidePreference =
                    qwertyGlideCommitPreviousCandidateOnNewGlidePreference,
                inputString = inputString,
                stringInTail = stringInTail,
                currentQwertyRomajiModeForSession = currentQwertyRomajiModeForSession,
                firstCandidate = firstCandidate,
                currentQwertyGlideCompositionText = currentQwertyGlideCompositionText
            )
        ) {
            return QwertyGlidePreviousCandidateCommitDecision.Skip
        }
        val candidate = requireNotNull(firstCandidate)
        return QwertyGlidePreviousCandidateCommitDecision.Commit(
            resolvePreviousGlideCommitText(
                firstCandidate = candidate,
                qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference =
                    qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference
            )
        )
    }
}
