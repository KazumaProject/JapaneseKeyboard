package com.kazumaproject.markdownhelperkeyboard.converter.candidate

enum class CandidateDictionaryOrigin { USER_DICTIONARY, LEARNED_DICTIONARY, USER_TEMPLATE }

fun dictionaryOriginsForType(type: Byte): Set<CandidateDictionaryOrigin> = when (type) {
    CANDIDATE_TYPE_USER_DICTIONARY -> setOf(CandidateDictionaryOrigin.USER_DICTIONARY)
    CANDIDATE_TYPE_LEARNED_DICTIONARY -> setOf(CandidateDictionaryOrigin.LEARNED_DICTIONARY)
    CANDIDATE_TYPE_USER_TEMPLATE -> setOf(CandidateDictionaryOrigin.USER_TEMPLATE)
    else -> emptySet()
}

/** For literal text variants only: display and commit must change together. */
internal fun Candidate.withLiteralText(
    text: String,
    segments: List<CandidateConversionSegment> = emptyList(),
): Candidate = copy(
    string = text,
    commitText = text,
    conversionSegments = segments,
    standaloneUnigramMatched = standaloneUnigramMatched && text == string,
)

/** Width conversion is compositional, so it can preserve exact reading-to-output ranges. */
internal fun Candidate.transformLiteralText(transform: (String) -> String): Candidate =
    withLiteralText(transform(string), conversionSegments.map { it.copy(output = transform(it.output)) })

/** Generated prose is no longer the original dictionary entry or its old formula/ruby path. */
internal fun Candidate.withGeneratedText(text: String, resultType: Byte): Candidate =
    withLiteralText(text).copy(
        type = resultType,
        yomi = null,
        dictionaryOrigins = emptySet(),
        dictionaryCategories = emptySet(),
        presentation = null,
        sourceId = null,
        dateFormat = null,
        standaloneUnigramMatched = false,
    )
