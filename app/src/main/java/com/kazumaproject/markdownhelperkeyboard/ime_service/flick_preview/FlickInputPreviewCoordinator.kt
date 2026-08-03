package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import com.kazumaproject.core.domain.flick.FlickTextPreviewEvent
import com.kazumaproject.core.domain.flick.FlickTextSelection

internal class FlickInputPreviewCoordinator(
    private val composingTextArbiter: ComposingTextArbiter,
    private val createPreviewText: (String) -> CharSequence = { it },
    private val mutationResolver: (
        FlickMutationSnapshot,
        FlickTextSelection,
    ) -> FlickTextMutation = FlickTextMutationResolver::resolve,
) {
    private data class ActivePreview(
        val gestureId: Long,
        val editorSessionId: Long,
        val source: FlickPreviewSource,
        val snapshot: FlickMutationSnapshot,
        var lastSelection: FlickTextSelection,
        var lastMutation: FlickTextMutation,
        var pendingCommitConsumed: Boolean = false,
        var previewWasReleased: Boolean = false,
    )

    private var active: ActivePreview? = null

    fun onEvent(event: FlickTextPreviewEvent, context: FlickPreviewContext) {
        when (event) {
            is FlickTextPreviewEvent.Started -> start(event, context)
            is FlickTextPreviewEvent.Changed -> update(event, context)
            is FlickTextPreviewEvent.CommitPending -> prepareCommit(event, context)
            is FlickTextPreviewEvent.Finished -> finish(event, context)
            is FlickTextPreviewEvent.Canceled -> cancelGesture(event, context)
        }
    }

    fun consumePendingCommit(text: String, isFlick: Boolean): FlickTextMutation.ReplaceComposingInput? {
        val current = active ?: return null
        if (!current.previewWasReleased || current.pendingCommitConsumed) return null
        val mutation = current.lastMutation as? FlickTextMutation.ReplaceComposingInput ?: return null
        if (mutation.selectedText != text || mutation.isFlick != isFlick) return null
        current.pendingCommitConsumed = true
        return mutation
    }

    fun cancel(restore: Boolean) {
        active?.let { current ->
            if (restore) {
                if (current.previewWasReleased) {
                    composingTextArbiter.restoreCanonical()
                } else {
                    composingTextArbiter.cancelPreviewAndRestore()
                }
            } else {
                composingTextArbiter.releasePreview(leaveDisplayedText = true)
            }
        }
        active = null
    }

    fun resetForEditorSession() {
        active = null
        composingTextArbiter.resetForEditorSession()
    }

    private fun start(
        event: FlickTextPreviewEvent.Started,
        context: FlickPreviewContext,
    ) {
        cancel(restore = true)
        if (!FlickPreviewEligibilityPolicy.isEligible(context)) return

        val snapshot = FlickMutationSnapshot(
            baseInput = context.baseInput,
            isFlickOnlyMode = context.isFlickOnlyMode,
            isContinuousTapInputEnabled = context.isContinuousTapInputEnabled,
            lastFlickConvertedNextHiragana = context.lastFlickConvertedNextHiragana,
        )
        val mutation = mutationResolver(snapshot, event.selection)
        active = ActivePreview(
            gestureId = event.gestureId,
            editorSessionId = context.editorSessionId,
            source = context.source,
            snapshot = snapshot,
            lastSelection = event.selection,
            lastMutation = mutation,
        )
        renderMutation(mutation)
    }

    private fun update(
        event: FlickTextPreviewEvent.Changed,
        context: FlickPreviewContext,
    ) {
        val current = matchingActive(event.gestureId, context) ?: return
        val mutation = mutationResolver(current.snapshot, event.selection)
        current.lastSelection = event.selection
        current.lastMutation = mutation
        renderMutation(mutation)
    }

    private fun prepareCommit(
        event: FlickTextPreviewEvent.CommitPending,
        context: FlickPreviewContext,
    ) {
        val current = matchingActive(event.gestureId, context) ?: return
        val previousMutation = current.lastMutation
        val mutation = mutationResolver(current.snapshot, event.selection)
        current.lastSelection = event.selection
        current.lastMutation = mutation
        current.pendingCommitConsumed = false

        when (mutation) {
            is FlickTextMutation.ReplaceComposingInput -> {
                val previousResult =
                    (previousMutation as? FlickTextMutation.ReplaceComposingInput)?.resultInput
                val needsEditorUpdate = !composingTextArbiter.isPreviewVisible() ||
                        previousResult != mutation.resultInput
                if (needsEditorUpdate && !showPreview(mutation)) {
                    active = null
                    return
                }
                composingTextArbiter.releasePreview(leaveDisplayedText = true)
                current.previewWasReleased = true
            }

            FlickTextMutation.NoTextOutput -> {
                composingTextArbiter.cancelPreviewAndRestore()
                current.previewWasReleased = false
            }
        }
    }

    private fun finish(
        event: FlickTextPreviewEvent.Finished,
        context: FlickPreviewContext,
    ) {
        val current = matchingActive(event.gestureId, context) ?: return
        if (current.previewWasReleased && !current.pendingCommitConsumed) {
            composingTextArbiter.restoreCanonical()
        }
        active = null
    }

    private fun cancelGesture(
        event: FlickTextPreviewEvent.Canceled,
        context: FlickPreviewContext,
    ) {
        matchingActive(event.gestureId, context) ?: return
        composingTextArbiter.cancelPreviewAndRestore()
        active = null
    }

    private fun matchingActive(
        gestureId: Long,
        context: FlickPreviewContext,
    ): ActivePreview? {
        return active?.takeIf {
            it.gestureId == gestureId &&
                    it.editorSessionId == context.editorSessionId &&
                    it.source == context.source
        }
    }

    private fun renderMutation(mutation: FlickTextMutation) {
        when (mutation) {
            is FlickTextMutation.ReplaceComposingInput -> {
                if (!showPreview(mutation)) {
                    active = null
                }
            }

            FlickTextMutation.NoTextOutput -> composingTextArbiter.suspendPreviewAndRestore()
        }
    }

    private fun showPreview(mutation: FlickTextMutation.ReplaceComposingInput): Boolean {
        return composingTextArbiter.showPreview(
            createPreviewText(mutation.resultInput),
            1,
        )
    }
}
