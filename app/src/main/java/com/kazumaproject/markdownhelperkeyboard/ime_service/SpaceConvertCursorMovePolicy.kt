package com.kazumaproject.markdownhelperkeyboard.ime_service

enum class SpaceConvertCursorMoveSource {
    SumireCustomSpace,
    SumireCustomConvert,
    SumireCustomFlickSpace,
    SumireCustomFlickConvert,
    QwertySpace
}

object SpaceConvertCursorMovePolicy {
    fun shouldEnterCursorMoveMode(
        conversionKeySwipeCursorMovePreference: Boolean?,
        hasInputString: Boolean,
        source: SpaceConvertCursorMoveSource
    ): Boolean {
        if (!hasInputString) return true
        return when (source) {
            SpaceConvertCursorMoveSource.SumireCustomSpace,
            SpaceConvertCursorMoveSource.SumireCustomConvert,
            SpaceConvertCursorMoveSource.SumireCustomFlickSpace,
            SpaceConvertCursorMoveSource.SumireCustomFlickConvert,
            SpaceConvertCursorMoveSource.QwertySpace -> {
                conversionKeySwipeCursorMovePreference == true
            }
        }
    }
}
