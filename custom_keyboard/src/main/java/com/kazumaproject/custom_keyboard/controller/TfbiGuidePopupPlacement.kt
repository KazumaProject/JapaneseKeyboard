package com.kazumaproject.custom_keyboard.controller

internal data class TfbiGuidePopupPlacement(
    val panelLeft: Int,
    val panelTop: Int,
    val arrowLeft: Int,
    val arrowTop: Int
)

internal fun resolveTfbiGuidePopupPlacement(
    keyLeft: Int,
    keyTop: Int,
    keyWidth: Int,
    panelWidth: Int,
    panelHeight: Int,
    hostWidth: Int,
    hostHeight: Int,
    gap: Int
): TfbiGuidePopupPlacement {
    var panelLeft = keyLeft + (keyWidth - panelWidth) / 2
    var panelTop = keyTop - panelHeight - gap
    if (panelTop < 0) {
        // The IME root can begin below the app content. Keep the guide at the root's top
        // so it remains above the pressed key instead of falling below it.
        panelTop = 0
    }

    panelLeft = panelLeft.coerceIn(0, (hostWidth - panelWidth).coerceAtLeast(0))
    panelTop = panelTop.coerceIn(0, (hostHeight - panelHeight).coerceAtLeast(0))
    return TfbiGuidePopupPlacement(
        panelLeft = panelLeft,
        panelTop = panelTop,
        arrowLeft = keyLeft,
        arrowTop = keyTop
    )
}
