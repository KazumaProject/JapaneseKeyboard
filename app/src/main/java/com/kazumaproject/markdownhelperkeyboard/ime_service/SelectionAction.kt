package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate

/** A selected-text action. Local macro actions never cross into a model-backed branch. */
internal sealed interface SelectionAction {
    data class TextMacro(val id: Long) : SelectionAction
    data object Translate : SelectionAction
    data class CustomPrompt(val template: GemmaPromptTemplate) : SelectionAction
}

internal data class SelectionActionEntry(
    val candidate: Candidate,
    val action: SelectionAction,
)

/** The candidate and action share one ordered entry, so click handling needs no index arithmetic. */
internal data class SelectionActionSession(
    val selectedText: String,
    val entries: List<SelectionActionEntry>,
) {
    fun entryFor(candidate: Candidate, position: Int): SelectionActionEntry? {
        entries.getOrNull(position)?.takeIf { it.candidate == candidate }?.let { return it }
        return entries.firstOrNull { entry ->
                entry.candidate.type == candidate.type &&
                entry.candidate.sourceId == candidate.sourceId &&
                entry.candidate.yomi == candidate.yomi &&
                entry.candidate.string == candidate.string
        }
    }
}

internal object SelectionActionSessionComposer {
    fun compose(
        selectedText: String,
        localMacros: List<SelectionActionEntry>,
        translationAndPrompts: List<SelectionActionEntry>,
    ): SelectionActionSession? = (localMacros + translationAndPrompts)
        .takeIf { it.isNotEmpty() }
        ?.let { SelectionActionSession(selectedText = selectedText, entries = it) }
}
