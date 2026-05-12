package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SumireSpecialKeyActionResolverTest {
    private val specialKey = KeyData(
        label = "del",
        row = 0,
        column = 0,
        isFlickable = false,
        isSpecialKey = true,
        keyId = "delete_key"
    )

    @Test
    fun normalKeyReturnsDefault() {
        val resolver = SumireSpecialKeyActionResolver(
            listOf(entity(SumireSpecialKeyOverrideType.NONE))
        )
        val result = resolver.resolve(
            "toggle",
            "HIRAGANA",
            specialKey.copy(isSpecialKey = false),
            SumireSpecialKeyDirection.TAP
        )
        assertEquals(ResolvedSumireSpecialKeyAction.Default, result)
    }

    @Test
    fun specialKeyWithoutKeyIdReturnsDefault() {
        val resolver = SumireSpecialKeyActionResolver(
            listOf(entity(SumireSpecialKeyOverrideType.NONE))
        )
        val result = resolver.resolve(
            "toggle",
            "HIRAGANA",
            specialKey.copy(keyId = null),
            SumireSpecialKeyDirection.TAP
        )
        assertEquals(ResolvedSumireSpecialKeyAction.Default, result)
    }

    @Test
    fun missingAndDefaultRecordsReturnDefault() {
        assertEquals(
            ResolvedSumireSpecialKeyAction.Default,
            SumireSpecialKeyActionResolver(emptyList()).resolve(
                "toggle",
                "HIRAGANA",
                specialKey,
                SumireSpecialKeyDirection.TAP
            )
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Default,
            SumireSpecialKeyActionResolver(
                listOf(entity(SumireSpecialKeyOverrideType.DEFAULT))
            ).resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        )
    }

    @Test
    fun noneReturnsNone() {
        val result = SumireSpecialKeyActionResolver(
            listOf(entity(SumireSpecialKeyOverrideType.NONE))
        ).resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        assertEquals(ResolvedSumireSpecialKeyAction.None, result)
    }

    @Test
    fun keyActionRestoresActionAndBrokenActionFallsBack() {
        val actionResult = SumireSpecialKeyActionResolver(
            listOf(
                entity(
                    SumireSpecialKeyOverrideType.KEY_ACTION,
                    actionString = "Paste"
                )
            )
        ).resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        assertEquals(ResolvedSumireSpecialKeyAction.Action(KeyAction.Paste), actionResult)

        val brokenResult = SumireSpecialKeyActionResolver(
            listOf(
                entity(
                    SumireSpecialKeyOverrideType.KEY_ACTION,
                    actionString = "not-a-real-action"
                )
            )
        ).resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        assertEquals(ResolvedSumireSpecialKeyAction.Default, brokenResult)
    }

    @Test
    fun keyActionOverrideResolvesTapAndFlickDirectionsIndependently() {
        val resolver = SumireSpecialKeyActionResolver(
            listOf(
                entity(
                    SumireSpecialKeyOverrideType.KEY_ACTION,
                    direction = "TAP",
                    actionString = "Enter"
                ),
                entity(
                    SumireSpecialKeyOverrideType.KEY_ACTION,
                    direction = "UP",
                    actionString = "Delete"
                ),
                entity(
                    SumireSpecialKeyOverrideType.KEY_ACTION,
                    direction = "RIGHT",
                    actionString = "Space"
                )
            )
        )

        assertEquals(
            ResolvedSumireSpecialKeyAction.Action(KeyAction.Enter),
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete),
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.UP)
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Action(KeyAction.Space),
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.RIGHT)
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Default,
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.LEFT)
        )
    }

    @Test
    fun directionMismatchDoesNotLeakIntoTapOrOtherFlicks() {
        val resolver = SumireSpecialKeyActionResolver(
            listOf(
                entity(
                    SumireSpecialKeyOverrideType.KEY_ACTION,
                    direction = "UP",
                    actionString = "Delete"
                )
            )
        )

        assertEquals(
            ResolvedSumireSpecialKeyAction.Default,
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Default,
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.RIGHT)
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete),
            resolver.resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.UP)
        )
    }

    @Test
    fun inputTextReturnsTextOnlyWhenNonEmpty() {
        val textResult = SumireSpecialKeyActionResolver(
            listOf(entity(SumireSpecialKeyOverrideType.INPUT_TEXT, inputText = "abc"))
        ).resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        assertEquals(ResolvedSumireSpecialKeyAction.InputText("abc"), textResult)

        val emptyResult = SumireSpecialKeyActionResolver(
            listOf(entity(SumireSpecialKeyOverrideType.INPUT_TEXT, inputText = ""))
        ).resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        assertEquals(ResolvedSumireSpecialKeyAction.Default, emptyResult)
    }

    @Test
    fun mismatchedScopeIsIgnored() {
        val overrides = listOf(
            entity(SumireSpecialKeyOverrideType.NONE, layoutType = "flick"),
            entity(SumireSpecialKeyOverrideType.NONE, inputMode = "ENGLISH"),
            entity(SumireSpecialKeyOverrideType.NONE, keyId = "enter_key"),
            entity(SumireSpecialKeyOverrideType.NONE, direction = "UP")
        )
        val result = SumireSpecialKeyActionResolver(overrides)
            .resolve("toggle", "HIRAGANA", specialKey, SumireSpecialKeyDirection.TAP)
        assertEquals(ResolvedSumireSpecialKeyAction.Default, result)
    }

    private fun entity(
        overrideType: SumireSpecialKeyOverrideType,
        layoutType: String = "toggle",
        inputMode: String = "HIRAGANA",
        keyId: String = "delete_key",
        direction: String = "TAP",
        actionString: String? = null,
        inputText: String? = null
    ) = SumireSpecialKeyActionOverrideEntity(
        layoutType = layoutType,
        inputMode = inputMode,
        keyId = keyId,
        direction = direction,
        overrideType = overrideType.name,
        actionString = actionString,
        inputText = inputText,
        updatedAt = 1L
    )
}
