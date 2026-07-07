package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

internal data class CandidateEmptyPopupThemeColors(
    val backgroundColor: Int,
    val textColor: Int,
)

internal fun resolveCandidateEmptyPopupThemeColors(
    popupBackgroundColor: Int?,
    popupTextColor: Int?,
    specialKeyColor: Int?,
    specialKeyTextColor: Int?,
    defaultBackgroundColor: Int,
    defaultTextColor: Int,
): CandidateEmptyPopupThemeColors =
    CandidateEmptyPopupThemeColors(
        backgroundColor = popupBackgroundColor ?: specialKeyColor ?: defaultBackgroundColor,
        textColor = popupTextColor ?: specialKeyTextColor ?: defaultTextColor,
    )
