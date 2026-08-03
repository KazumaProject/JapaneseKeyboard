package com.kazumaproject.core.domain.flick

data class FlickTextSelection(
    val text: String?,
    val isFlick: Boolean,
) {
    fun normalized(): FlickTextSelection = copy(text = text?.takeIf(String::isNotEmpty))
}

sealed interface FlickTextPreviewEvent {
    val gestureId: Long

    data class Started(
        override val gestureId: Long,
        val selection: FlickTextSelection,
    ) : FlickTextPreviewEvent

    data class Changed(
        override val gestureId: Long,
        val selection: FlickTextSelection,
    ) : FlickTextPreviewEvent

    data class CommitPending(
        override val gestureId: Long,
        val selection: FlickTextSelection,
    ) : FlickTextPreviewEvent

    data class Finished(
        override val gestureId: Long,
    ) : FlickTextPreviewEvent

    data class Canceled(
        override val gestureId: Long,
    ) : FlickTextPreviewEvent
}

fun interface FlickTextPreviewListener {
    fun onFlickTextPreview(event: FlickTextPreviewEvent)
}
