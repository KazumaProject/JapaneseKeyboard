package com.kazumaproject.markdownhelperkeyboard.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.DraftConstraint
import com.kazumaproject.markdownhelperkeyboard.ime_service.ZenzOutputPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateEvaluationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class DraftReviewStop {
    PASS, WHOLE, EVALUATION_LIMIT, NO_PATH, REPEATED_CONSTRAINT, EVALUATION_ERROR,
}

internal data class DraftReviewResult(
    val text: String,
    val evaluationCount: Int,
    val stop: DraftReviewStop,
)

internal suspend fun reviewZenzaiDraft(
    initialDraft: String,
    evaluationLimit: Int,
    evaluate: suspend (String) -> String,
    search: (DraftConstraint, cancellationCheck: () -> Unit) -> String?,
): DraftReviewResult {
    require(evaluationLimit > 0)
    val context = currentCoroutineContext()
    val seenConstraints = mutableSetOf<DraftConstraint>()
    var draft = initialDraft
    for (attempt in 1..evaluationLimit) {
        context.ensureActive()
        val evaluation = try {
            CandidateEvaluationResult.parse(evaluate(draft))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CandidateEvaluationResult.Error
        }
        context.ensureActive()
        val constraint = when (evaluation) {
            is CandidateEvaluationResult.Pass -> return DraftReviewResult(draft, attempt, DraftReviewStop.PASS)
            is CandidateEvaluationResult.FixRequired -> DraftConstraint(evaluation.prefix, exact = false)
            is CandidateEvaluationResult.WholeResult -> DraftConstraint(evaluation.result, exact = true)
            CandidateEvaluationResult.Error -> return DraftReviewResult(draft, attempt, DraftReviewStop.EVALUATION_ERROR)
        }
        if (!seenConstraints.add(constraint)) {
            return DraftReviewResult(draft, attempt, DraftReviewStop.REPEATED_CONSTRAINT)
        }
        val nextDraft = ZenzOutputPolicy.acceptedTextOrNull(search(constraint, context::ensureActive))
            ?.takeIf(constraint::matches)
        context.ensureActive()
        if (nextDraft == null) {
            return DraftReviewResult(draft, attempt, DraftReviewStop.NO_PATH)
        }
        draft = nextDraft
        if (constraint.exact) return DraftReviewResult(draft, attempt, DraftReviewStop.WHOLE)
    }
    return DraftReviewResult(draft, evaluationLimit, DraftReviewStop.EVALUATION_LIMIT)
}
