package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord

internal fun UserWord.toUserDictionaryCandidate(): Candidate = Candidate(
    string = word,
    type = CANDIDATE_TYPE_USER_DICTIONARY,
    length = reading.length.toUByte(),
    score = posScore,
    yomi = reading,
)
