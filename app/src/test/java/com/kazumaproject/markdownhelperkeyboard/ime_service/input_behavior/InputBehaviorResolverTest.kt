package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import org.junit.Assert.assertEquals
import org.junit.Test

class InputBehaviorResolverTest {

    @Test
    fun typeNullDefaultResolvesDirectCommit() {
        assertEquals(
            ResolvedInputBehavior.DIRECT_COMMIT,
            resolver(TypeNullInputBehaviorSetting.DEFAULT).resolve(InputTypeForIME.TypeNull)
        )
    }

    @Test
    fun typeNullDirectCommitResolvesDirectCommit() {
        assertEquals(
            ResolvedInputBehavior.DIRECT_COMMIT,
            resolver(TypeNullInputBehaviorSetting.DIRECT_COMMIT).resolve(InputTypeForIME.TypeNull)
        )
    }

    @Test
    fun typeNullComposingTextResolvesComposingText() {
        assertEquals(
            ResolvedInputBehavior.COMPOSING_TEXT,
            resolver(TypeNullInputBehaviorSetting.COMPOSING_TEXT).resolve(InputTypeForIME.TypeNull)
        )
    }

    @Test
    fun nonTypeNullAlwaysResolvesComposingText() {
        val inputTypes = listOf(
            InputTypeForIME.Text,
            InputTypeForIME.TextPassword,
            InputTypeForIME.Number,
            InputTypeForIME.Phone,
        )

        TypeNullInputBehaviorSetting.entries.forEach { setting ->
            val resolver = resolver(setting)
            inputTypes.forEach { inputType ->
                assertEquals(
                    "$inputType with $setting",
                    ResolvedInputBehavior.COMPOSING_TEXT,
                    resolver.resolve(inputType)
                )
            }
        }
    }

    @Test
    fun invalidPreferenceValueFallsBackToDefault() {
        assertEquals(
            TypeNullInputBehaviorSetting.DEFAULT,
            TypeNullInputBehaviorSetting.fromPreferenceValue("unexpected")
        )
        assertEquals(
            TypeNullInputBehaviorSetting.DEFAULT,
            TypeNullInputBehaviorSetting.fromPreferenceValue(null)
        )
    }

    private fun resolver(setting: TypeNullInputBehaviorSetting): InputBehaviorResolver {
        return InputBehaviorResolver { setting }
    }
}
