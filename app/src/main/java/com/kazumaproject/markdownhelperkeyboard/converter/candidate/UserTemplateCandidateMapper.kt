package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate

internal fun UserTemplate.toUserTemplateCandidate(): Candidate {
    return Candidate(
        string = word,
        type = CANDIDATE_TYPE_USER_TEMPLATE,
        length = reading.length.toUByte(),
        score = posScore
    )
}

internal fun List<UserTemplate>.toUserTemplateCandidates(): List<Candidate> {
    return map { it.toUserTemplateCandidate() }
        .sortedBy { it.score }
}
