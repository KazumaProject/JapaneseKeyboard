package com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary

import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper

class RuntimeMozcUserDictionary(
    private val userDictionaryRepository: UserDictionaryRepository,
) {
    suspend fun lookupPrefix(
        key: String,
        callback: (MozcDictionaryToken) -> Unit,
    ) {
        userDictionaryRepository.commonPrefixSearchInUserDict(key).forEach { userWord ->
            val contextId = PosMapper.getContextIdForPos(userWord.posIndex)
            callback(
                MozcDictionaryToken(
                    key = userWord.reading,
                    value = userWord.word,
                    lid = contextId,
                    rid = contextId,
                    cost = userWord.posScore,
                    source = MozcDictionarySource.USER,
                )
            )
        }
    }
}

class RuntimeMozcLearnDictionary(
    private val learnRepository: LearnRepository?,
) {
    suspend fun lookupPrefix(
        key: String,
        callback: (MozcDictionaryToken) -> Unit,
    ) {
        learnRepository?.findCommonPrefixes(key).orEmpty().forEach { learnedWord ->
            callback(
                MozcDictionaryToken(
                    key = learnedWord.input,
                    value = learnedWord.out,
                    lid = learnedWord.leftId ?: 1851,
                    rid = learnedWord.rightId ?: 1851,
                    cost = learnedWord.score.toInt(),
                    source = MozcDictionarySource.LEARN,
                )
            )
        }
    }
}
