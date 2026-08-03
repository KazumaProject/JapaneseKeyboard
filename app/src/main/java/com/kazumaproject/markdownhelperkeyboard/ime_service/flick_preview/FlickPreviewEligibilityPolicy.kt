package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

internal enum class FlickPreviewSource {
    TENKEY,
    SUMIRE,
}

internal data class FlickPreviewContext(
    val source: FlickPreviewSource,
    val editorSessionId: Long,
    val settingEnabled: Boolean,
    val surfaceEligible: Boolean,
    val inputBehaviorUsesComposingText: Boolean,
    val safeInputType: Boolean,
    val isHenkan: Boolean,
    val selectMode: Boolean,
    val cursorMoveMode: Boolean,
    val composingTail: String,
    val hasInputConnection: Boolean,
    val baseInput: String,
    val isFlickOnlyMode: Boolean,
    val isContinuousTapInputEnabled: Boolean,
    val lastFlickConvertedNextHiragana: Boolean,
)

internal object FlickPreviewEligibilityPolicy {
    fun isEligible(context: FlickPreviewContext): Boolean {
        return context.settingEnabled &&
                context.surfaceEligible &&
                context.inputBehaviorUsesComposingText &&
                context.safeInputType &&
                !context.isHenkan &&
                !context.selectMode &&
                !context.cursorMoveMode &&
                context.hasInputConnection
    }
}
