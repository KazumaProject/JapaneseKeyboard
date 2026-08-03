package com.kazumaproject.core.domain.flick

class FlickTextPreviewEmitter {
    var listener: FlickTextPreviewListener? = null

    private var nextGestureId = 1L
    private var activeGestureId: Long? = null
    private var lastSelection: FlickTextSelection? = null

    fun begin(selection: FlickTextSelection) {
        cancel()
        val normalized = selection.normalized()
        val gestureId = nextGestureId++
        activeGestureId = gestureId
        lastSelection = normalized
        listener?.onFlickTextPreview(
            FlickTextPreviewEvent.Started(gestureId, normalized)
        )
    }

    fun update(selection: FlickTextSelection) {
        val gestureId = activeGestureId ?: return
        val normalized = selection.normalized()
        if (normalized == lastSelection) return
        lastSelection = normalized
        listener?.onFlickTextPreview(
            FlickTextPreviewEvent.Changed(gestureId, normalized)
        )
    }

    fun commit(selection: FlickTextSelection, dispatch: () -> Unit) {
        val gestureId = activeGestureId
        if (gestureId == null) {
            dispatch()
            return
        }
        val normalized = selection.normalized()
        lastSelection = normalized
        listener?.onFlickTextPreview(
            FlickTextPreviewEvent.CommitPending(gestureId, normalized)
        )
        try {
            dispatch()
        } finally {
            listener?.onFlickTextPreview(FlickTextPreviewEvent.Finished(gestureId))
            clear()
        }
    }

    fun cancel() {
        val gestureId = activeGestureId ?: return
        listener?.onFlickTextPreview(FlickTextPreviewEvent.Canceled(gestureId))
        clear()
    }

    private fun clear() {
        activeGestureId = null
        lastSelection = null
    }
}
