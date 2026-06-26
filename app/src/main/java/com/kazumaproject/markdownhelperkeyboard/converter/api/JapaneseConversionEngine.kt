package com.kazumaproject.markdownhelperkeyboard.converter.api

interface JapaneseConversionEngine {
    suspend fun convert(request: JapaneseConversionRequest): JapaneseConversionResult
}
