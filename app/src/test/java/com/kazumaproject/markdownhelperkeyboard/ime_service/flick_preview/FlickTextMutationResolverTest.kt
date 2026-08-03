package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import com.kazumaproject.core.domain.flick.FlickTextSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class FlickTextMutationResolverTest {
    @Test
    fun emptyInputTapAppendsCharacter() {
        val mutation = resolve(baseInput = "", text = "あ", isFlick = false)

        assertEquals("あ", mutation.resultInput)
        assertEquals(false, mutation.effects.continuousTapInputEnabled)
        assertEquals(false, mutation.effects.lastFlickConvertedNextHiragana)
    }

    @Test
    fun repeatedTapUsesExistingToggleRule() {
        val mutation = resolve(baseInput = "あ", text = "あ", isFlick = false)

        assertEquals("い", mutation.resultInput)
    }

    @Test
    fun flickAndFlickOnlyTapAppend() {
        assertEquals(
            "かあ",
            resolve(baseInput = "か", text = "あ", isFlick = true).resultInput,
        )
        assertEquals(
            "かあ",
            resolve(
                baseInput = "か",
                text = "あ",
                isFlick = false,
                isFlickOnlyMode = true,
            ).resultInput,
        )
    }

    @Test
    fun firstTapAfterFlickAppendsThenClearsLastFlickFlag() {
        val mutation = resolve(
            baseInput = "か",
            text = "あ",
            isFlick = false,
            continuous = true,
            lastFlick = true,
        )

        assertEquals("かあ", mutation.resultInput)
        assertEquals(true, mutation.effects.continuousTapInputEnabled)
        assertEquals(false, mutation.effects.lastFlickConvertedNextHiragana)
    }

    @Test
    fun multiCharacterSumireOutputAppendsWithoutChangingTapFlags() {
        val mutation = resolve(baseInput = "か", text = "abc", isFlick = true)

        assertEquals("かabc", mutation.resultInput)
        assertEquals(null, mutation.effects.continuousTapInputEnabled)
        assertEquals(null, mutation.effects.lastFlickConvertedNextHiragana)
    }

    private fun resolve(
        baseInput: String,
        text: String,
        isFlick: Boolean,
        isFlickOnlyMode: Boolean = false,
        continuous: Boolean = false,
        lastFlick: Boolean = false,
    ): FlickTextMutation.ReplaceComposingInput {
        return FlickTextMutationResolver.resolve(
            snapshot = FlickMutationSnapshot(
                baseInput = baseInput,
                isFlickOnlyMode = isFlickOnlyMode,
                isContinuousTapInputEnabled = continuous,
                lastFlickConvertedNextHiragana = lastFlick,
            ),
            selection = FlickTextSelection(text, isFlick),
        ) as FlickTextMutation.ReplaceComposingInput
    }
}
