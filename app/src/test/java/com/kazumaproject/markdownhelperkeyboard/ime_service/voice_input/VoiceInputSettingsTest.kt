package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputSettingsTest {
    @Test
    fun defaultsAndEnglishRecognitionPreserveText() {
        val text = " 日本語 hello world　\t\n "
        assertEquals(text, VoiceInputSettings().transform(text, "ja-JP"))
        assertEquals(text, VoiceInputSettings(enabled = true).transform(text, "en-CA"))
        assertEquals(text, VoiceInputSettings(enabled = true).transform(text, ""))
    }

    @Test
    fun enabledDefaultRemovesOnlyAsciiSpacesIncludingMixedEnglishWords() {
        assertEquals(
            "日本語helloworld　\t\n",
            VoiceInputSettings(enabled = true).transform(" 日本語 hello  world　\t\n ", "ja-JP"),
        )
    }

    @Test
    fun eachPresetReplacesEachSpaceWithoutCollapsingRuns() {
        val separators = mapOf(
            VoiceInputSpaceReplacement.REMOVE to "",
            VoiceInputSpaceReplacement.COMMA to "、",
            VoiceInputSpaceReplacement.PERIOD to "。",
            VoiceInputSpaceReplacement.NEWLINE to "\n",
            VoiceInputSpaceReplacement.FULL_WIDTH_SPACE to "　",
        )
        for ((replacement, separator) in separators) {
            val settings = VoiceInputSettings(enabled = true, replacement = replacement)
            assertEquals(
                "${separator}あ${separator}${separator}い${separator}",
                settings.transform(" あ  い ", "ja-JP"),
            )
            assertEquals("あ　い\tう\nえ", settings.transform("あ　い\tう\nえ", "ja-JP"))
            assertEquals("", settings.transform("", "ja-JP"))
        }
    }

    @Test
    fun customTextKeepsWhitespaceAndTreatsReplacementLiterally() {
        val settings = VoiceInputSettings(
            enabled = true,
            replacement = VoiceInputSpaceReplacement.CUSTOM,
            customText = " \$1\\・🙂 ",
        )
        assertEquals("あ \$1\\・🙂 い", settings.transform("あ い", "ja-JP"))
        assertEquals("あい", settings.copy(customText = "").transform("あ い", "ja-JP"))
        assertEquals("あ い", settings.copy(enabled = false).transform("あ い", "ja-JP"))
    }

    @Test
    fun savedValuesResolveAndUnknownValueDefaultsToRemoval() {
        for (replacement in VoiceInputSpaceReplacement.entries) {
            assertEquals(replacement, VoiceInputSpaceReplacement.fromPreference(replacement.preferenceValue))
        }
        assertEquals(VoiceInputSpaceReplacement.REMOVE, VoiceInputSpaceReplacement.fromPreference("unknown"))
        assertEquals(VoiceInputSpaceReplacement.REMOVE, VoiceInputSpaceReplacement.fromPreference(null))
    }
}
