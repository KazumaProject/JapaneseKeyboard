package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputReplacementRuleTest {
    @Test
    fun rulesAreOptInAndIndependentOfJapaneseSpaceReplacement() {
        val settings = VoiceInputSettings(textReplacementRules = listOf(rule("僕", "ぼく")))
        assertEquals("僕 は 僕", settings.transform("僕 は 僕", "ja-JP"))
        val enabled = settings.copy(textReplacementEnabled = true)
        assertEquals("ぼく は ぼく", enabled.transform("僕 は 僕", "ja-JP"))
        assertEquals("ぼくはぼく", enabled.copy(enabled = true).transform("僕 は 僕", "ja-JP"))
        assertEquals("ぼく は ぼく", enabled.copy(enabled = true).transform("僕 は 僕", "en-CA"))
    }

    @Test
    fun longestMatchWinsAndInsertedTextIsNeverReplacedAgain() {
        val settings = settings(rule("僕", "ぼく"), rule("僕たち", "ぼくら"), rule("ぼく", "私"))
        assertEquals("ぼくらとぼくと私", settings.transform("僕たちと僕とぼく", "ja-JP"))
        assertEquals("ぼくぼく", settings.transform("僕僕", "ja-JP"))
        assertEquals("", settings.transform("", "ja-JP"))
    }

    @Test
    fun replacementsAreLiteralCaseSensitiveAndCanDeleteOrPreserveWhitespace() {
        val settings = settings(rule("a.b", "\$1\\🙂"), rule("消去", ""), rule("僕", " ぼく\n"))
        assertEquals("\$1\\🙂 A.B axb  ぼく\n", settings.transform("a.b A.B axb 消去僕", "en-CA"))
        assertEquals("🙂🙂", settings(rule("🙂", "🙂🙂")).transform("🙂", "ja-JP"))
        assertEquals(" ぼく\n", settings.copy(enabled = true).transform(" 僕 ", "ja-JP"))
    }

    @Test
    fun emptySourcesAreIgnoredAndDuplicateSourcesUseFirstRule() {
        val settings = settings(rule("", "invalid"), rule("僕", "ぼく"), rule("僕", "私"))
        assertEquals("ぼく", settings.transform("僕", "ja-JP"))
    }

    @Test
    fun codecRoundTripsUnicodeDeletionAndLiteralCharacters() {
        val rules = listOf(rule("僕", "ぼく"), rule("\"=→\\🙂", " \$1\n\t"), rule("消去", ""))
        assertEquals(rules, VoiceInputReplacementRuleCodec.decode(VoiceInputReplacementRuleCodec.encode(rules)))
    }

    @Test
    fun codecIgnoresInvalidRecordsAndRejectsInvalidDocuments() {
        assertEquals(emptyList<VoiceInputReplacementRule>(), VoiceInputReplacementRuleCodec.decode("not json"))
        assertEquals(emptyList<VoiceInputReplacementRule>(), VoiceInputReplacementRuleCodec.decode("{}"))
        assertEquals(emptyList<VoiceInputReplacementRule>(), VoiceInputReplacementRuleCodec.decode("null"))
        assertEquals(listOf(rule("僕", "ぼく"), rule("消去", "")), VoiceInputReplacementRuleCodec.decode(
            """[null, 1, {}, {"source":"","replacement":"x"}, {"source":1,"replacement":"x"},
                {"source":"僕","replacement":"ぼく"}, {"source":"僕","replacement":"私"},
                {"source":"消去","replacement":""}, {"source":"無効","replacement":null}]"""
        ))
    }

    private fun rule(source: String, replacement: String) = VoiceInputReplacementRule(source, replacement)

    private fun settings(vararg rules: VoiceInputReplacementRule) = VoiceInputSettings(
        textReplacementEnabled = true, textReplacementRules = rules.toList(),
    )
}
