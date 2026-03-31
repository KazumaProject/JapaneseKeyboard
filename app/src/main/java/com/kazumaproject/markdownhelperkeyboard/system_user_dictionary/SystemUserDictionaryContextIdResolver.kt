package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import java.util.Locale

class SystemUserDictionaryContextIdResolver(
    private val idEntries: List<IdDefEntry>,
    private val defaultContextId: Int,
) {

    private val posPatterns = listOf(
        PosPattern(listOf("人名", "person", "name"), listOf("名詞", "人名")),
        PosPattern(listOf("地名", "地域", "place"), listOf("名詞", "固有名詞", "地域")),
        PosPattern(listOf("固有名詞", "proper"), listOf("名詞", "固有名詞")),
        PosPattern(listOf("名詞", "noun"), listOf("名詞")),
        PosPattern(listOf("動詞", "verb"), listOf("動詞")),
        PosPattern(listOf("形容詞", "adjective"), listOf("形容詞")),
        PosPattern(listOf("副詞", "adverb"), listOf("副詞")),
        PosPattern(listOf("助動詞", "auxiliary"), listOf("助動詞")),
        PosPattern(listOf("助詞", "particle"), listOf("助詞")),
        PosPattern(listOf("感動詞", "interjection"), listOf("感動詞", "その他,間投")),
        PosPattern(listOf("接続詞", "conjunction"), listOf("接続詞")),
        PosPattern(listOf("接頭詞", "prefix"), listOf("接頭詞")),
        PosPattern(listOf("連体詞", "adnominal"), listOf("連体詞")),
        PosPattern(listOf("記号", "symbol"), listOf("記号")),
    )

    private data class PosPattern(
        val hintKeywords: List<String>,
        val idLabelKeywords: List<String>,
    )

    fun resolve(posHint: String?): Pair<Int, Int> {
        if (posHint.isNullOrBlank()) return defaultContextId to defaultContextId

        val normalizedHint = posHint.lowercase(Locale.ROOT)
        val matchedPattern = posPatterns.firstOrNull { pattern ->
            pattern.hintKeywords.any { keyword -> normalizedHint.contains(keyword) }
        } ?: return defaultContextId to defaultContextId

        val candidateId = idEntries
            .firstOrNull { entry ->
                matchedPattern.idLabelKeywords.all { keyword -> entry.label.contains(keyword) }
            }
            ?.id
            ?: idEntries.firstOrNull { entry ->
                matchedPattern.idLabelKeywords.any { keyword -> entry.label.contains(keyword) }
            }?.id
            ?: defaultContextId

        return candidateId to candidateId
    }

    fun resolveFromPosIndex(posIndex: Int): Pair<Int, Int> {
        val posLabel = when (posIndex) {
            0 -> "noun"
            1 -> "verb"
            2 -> "adjective"
            3 -> "adverb"
            4 -> "auxiliary"
            5 -> "particle"
            6 -> "interjection"
            7 -> "conjunction"
            8 -> "prefix"
            9 -> "symbol"
            10 -> "adnominal"
            else -> null
        }
        return resolve(posLabel)
    }
}

