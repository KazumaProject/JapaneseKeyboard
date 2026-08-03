package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

internal sealed interface CanonicalComposingState {
    data class Text(
        val value: CharSequence,
        val cursorPosition: Int,
    ) : CanonicalComposingState

    data object Finished : CanonicalComposingState
}

/** Keeps transient flick previews separate from the IME's canonical composing state. */
internal class ComposingTextArbiter(
    private val writeComposingText: (CharSequence?, Int) -> Boolean,
    private val finishComposingText: () -> Boolean,
    private val copyText: (CharSequence) -> CharSequence = { it.toString() },
) {
    private var canonicalState: CanonicalComposingState = CanonicalComposingState.Finished
    private var previewVisible = false

    fun setCanonical(text: CharSequence?, cursorPosition: Int): Boolean {
        val copied = copyText(text ?: "")
        canonicalState = CanonicalComposingState.Text(copied, cursorPosition)
        return if (previewVisible) true else writeComposingText(copied, cursorPosition)
    }

    fun showPreview(text: CharSequence, cursorPosition: Int): Boolean {
        val written = writeComposingText(copyText(text), cursorPosition)
        previewVisible = written
        return written
    }

    fun suspendPreviewAndRestore(): Boolean {
        if (!previewVisible) return true
        previewVisible = false
        return restoreCanonical()
    }

    fun releasePreview(leaveDisplayedText: Boolean) {
        if (!previewVisible) return
        previewVisible = false
        if (!leaveDisplayedText) restoreCanonical()
    }

    fun cancelPreviewAndRestore(): Boolean {
        if (!previewVisible) return true
        previewVisible = false
        return restoreCanonical()
    }

    fun restoreCanonical(): Boolean {
        return when (val state = canonicalState) {
            is CanonicalComposingState.Text -> writeComposingText(
                state.value,
                state.cursorPosition,
            )

            // finishComposingText() would commit the currently displayed preview. Replacing the
            // composing region with an empty value removes it and restores the no-composition
            // state without inserting preview text into the editor.
            CanonicalComposingState.Finished -> writeComposingText("", 0)
        }
    }

    fun finishCanonical(): Boolean {
        canonicalState = CanonicalComposingState.Finished
        return if (previewVisible) true else finishComposingText()
    }

    fun markCanonicalFinished() {
        canonicalState = CanonicalComposingState.Finished
    }

    fun resetForEditorSession() {
        previewVisible = false
        canonicalState = CanonicalComposingState.Finished
    }

    fun isPreviewVisible(): Boolean = previewVisible
}
