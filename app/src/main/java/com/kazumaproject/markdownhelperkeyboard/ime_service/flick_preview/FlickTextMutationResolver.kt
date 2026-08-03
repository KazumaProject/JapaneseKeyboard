package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import com.kazumaproject.core.domain.flick.FlickTextSelection
import com.kazumaproject.tenkey.extensions.getNextInputChar

internal data class FlickMutationSnapshot(
    val baseInput: String,
    val isFlickOnlyMode: Boolean,
    val isContinuousTapInputEnabled: Boolean,
    val lastFlickConvertedNextHiragana: Boolean,
)

internal data class FlickInputEffects(
    val continuousTapInputEnabled: Boolean?,
    val lastFlickConvertedNextHiragana: Boolean?,
)

internal sealed interface FlickTextMutation {
    data class ReplaceComposingInput(
        val selectedText: String,
        val isFlick: Boolean,
        val resultInput: String,
        val effects: FlickInputEffects,
    ) : FlickTextMutation

    data object NoTextOutput : FlickTextMutation
}

internal object FlickTextMutationResolver {
    fun resolve(
        snapshot: FlickMutationSnapshot,
        selection: FlickTextSelection,
    ): FlickTextMutation {
        val selectedText = selection.text?.takeIf(String::isNotEmpty)
            ?: return FlickTextMutation.NoTextOutput

        if (selectedText.length != 1) {
            return FlickTextMutation.ReplaceComposingInput(
                selectedText = selectedText,
                isFlick = selection.isFlick,
                resultInput = snapshot.baseInput + selectedText,
                effects = FlickInputEffects(
                    continuousTapInputEnabled = null,
                    lastFlickConvertedNextHiragana = null,
                ),
            )
        }

        val selectedChar = selectedText.first()
        val shouldAppend = selection.isFlick ||
                snapshot.isFlickOnlyMode ||
                (snapshot.isContinuousTapInputEnabled &&
                        snapshot.lastFlickConvertedNextHiragana)

        val result = if (shouldAppend || snapshot.baseInput.isEmpty()) {
            snapshot.baseInput + selectedChar
        } else {
            val nextChar = snapshot.baseInput.last().getNextInputChar(selectedChar)
            if (nextChar == null) {
                snapshot.baseInput + selectedChar
            } else {
                snapshot.baseInput.dropLast(1) + nextChar
            }
        }

        val effects = when {
            selection.isFlick || snapshot.isFlickOnlyMode -> FlickInputEffects(
                continuousTapInputEnabled = true,
                lastFlickConvertedNextHiragana = true,
            )

            snapshot.isContinuousTapInputEnabled &&
                    snapshot.lastFlickConvertedNextHiragana -> FlickInputEffects(
                continuousTapInputEnabled = true,
                lastFlickConvertedNextHiragana = false,
            )

            else -> FlickInputEffects(
                continuousTapInputEnabled = false,
                lastFlickConvertedNextHiragana = false,
            )
        }

        return FlickTextMutation.ReplaceComposingInput(
            selectedText = selectedText,
            isFlick = selection.isFlick,
            resultInput = result,
            effects = effects,
        )
    }
}
