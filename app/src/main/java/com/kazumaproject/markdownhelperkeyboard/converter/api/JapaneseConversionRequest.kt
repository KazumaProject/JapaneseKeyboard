package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository

data class JapaneseConversionRequest(
    val input: String,
    val nBest: Int,
    val requestType: JapaneseConversionRequestType,
    val mozcUtPersonName: Boolean?,
    val mozcUTPlaces: Boolean?,
    val mozcUTWiki: Boolean?,
    val mozcUTNeologd: Boolean?,
    val mozcUTWeb: Boolean?,
    val userDictionaryRepository: UserDictionaryRepository,
    val learnRepository: LearnRepository?,
    val isOmissionSearchEnable: Boolean,
    val enableTypoCorrectionJapaneseFlick: Boolean,
    val enableTypoCorrectionQwertyEnglish: Boolean,
    val typoCorrectionOffsetScore: Int,
    val omissionSearchOffsetScore: Int,
)
