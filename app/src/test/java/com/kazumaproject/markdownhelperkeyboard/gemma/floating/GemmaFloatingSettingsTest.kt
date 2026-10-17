package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.google.gson.Gson
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaFloatingSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun reset() { AppPreference.init(context); preferences.edit().clear().commit() }

    private val legacyModeless = GemmaFloatingRule("modeless", "モードレス", "Convert Japanese or romaji Japanese to natural kanji/kana. Keep English words and sentences in English; never transliterate English into katakana. In mixed input, convert only Japanese portions. Preserve English names, code, URLs, numbers and formatting. Correct obvious typos without changing meaning.")

    @Test fun defaultsOfferEnglishAndJapaneseTranslation() {
        val settings = GemmaFloatingSettings(preferences)
        settings.ensureDefaults()
        assertEquals(listOf("english", "japanese"), settings.rules.map { it.id })
        assertEquals(listOf("英訳", "日本語訳"), settings.rules.map { it.name })
        assertTrue(settings.rules.last().instruction.contains("Translate the input into natural Japanese"))
    }

    @Test fun untouchedLegacyModelessMigratesAndKeepsSelectionAcrossReload() {
        val english = GemmaFloatingSettings.defaults().first()
        preferences.edit().putString(GemmaFloatingSettings.RULES, Gson().toJson(listOf(english, legacyModeless)))
            .putString(GemmaFloatingSettings.SELECTED, "modeless").commit()
        repeat(2) {
            val settings = GemmaFloatingSettings(preferences)
            assertEquals(GemmaFloatingSettings.defaults(), settings.rules)
            assertEquals("japanese", settings.selectedRule?.id)
        }
    }

    @Test fun editedModelessAndDeletedPresetsArePreserved() {
        val edited = legacyModeless.copy(instruction = "My custom instruction")
        preferences.edit().putString(GemmaFloatingSettings.RULES, Gson().toJson(listOf(edited)))
            .putString(GemmaFloatingSettings.SELECTED, edited.id).commit()
        val settings = GemmaFloatingSettings(preferences)
        settings.ensureDefaults()
        assertEquals(listOf(edited), settings.rules)
        assertEquals(edited, settings.selectedRule)
    }

    @Test fun migrationPreservesCustomRulesAndDoesNotDuplicateExistingJapaneseRule() {
        val custom = GemmaFloatingRule("custom", "Custom", "My instruction")
        val japanese = GemmaFloatingSettings.defaults().last().copy(instruction = "My Japanese instruction")
        preferences.edit().putString(GemmaFloatingSettings.RULES, Gson().toJson(listOf(custom, legacyModeless, japanese)))
            .putString(GemmaFloatingSettings.SELECTED, custom.id).commit()
        val settings = GemmaFloatingSettings(preferences)
        assertEquals(listOf(custom, japanese), settings.rules)
        assertEquals(custom, settings.selectedRule)
    }

    @Test fun defaultsAreOffAndDeletedPresetsStayDeleted() {
        val settings = GemmaFloatingSettings(preferences)
        assertFalse(settings.enabled)
        assertEquals("english", settings.selectedRule?.id)
        settings.rules.forEach(settings::delete)
        GemmaFloatingSettings(preferences).ensureDefaults()
        assertTrue(GemmaFloatingSettings(preferences).rules.isEmpty())
        assertNull(settings.selectedRule)
    }

    @Test fun addEditSelectAndDeleteSurviveReload() {
        val settings = GemmaFloatingSettings(preferences)
        settings.save(null, "Polite", "Use polite Japanese.")
        val added = settings.rules.last()
        settings.select(added.id)
        settings.save(added, "Formal", "Use formal Japanese.")
        val restored = GemmaFloatingSettings(preferences)
        assertEquals("Formal", restored.selectedRule?.name)
        assertEquals("Use formal Japanese.", restored.selectedRule?.instruction)
        restored.delete(requireNotNull(restored.selectedRule))
        assertNull(GemmaFloatingSettings(preferences).selectedRule)
    }

    @Test fun rulesSelectionAndEnabledRoundTripThroughExistingBackup() {
        val settings = GemmaFloatingSettings(preferences)
        settings.save(null, "Multiline", "Return two lines.")
        settings.select(settings.rules.last().id)
        preferences.edit().putBoolean(GemmaFloatingSettings.ENABLED, true).commit()
        val backup = AppPreference.exportAllToJson()
        preferences.edit().clear().commit()
        AppPreference.importAllFromJson(backup)
        val restored = GemmaFloatingSettings(preferences)
        assertTrue(restored.enabled)
        assertEquals("Multiline", restored.selectedRule?.name)
        assertEquals(3, restored.rules.size)
    }

    @Test fun persistedRuleFieldNamesMatchTheExistingBackupSchema() {
        val settings = GemmaFloatingSettings(preferences)
        settings.save(null, "Formatting", "Preserve whitespace.")
        val stored = JsonParser.parseString(preferences.getString(GemmaFloatingSettings.RULES, null))
            .asJsonArray.last().asJsonObject
        assertEquals(setOf("id", "name", "instruction"), stored.keySet())

        preferences.edit().putString(GemmaFloatingSettings.RULES,
            """[{"id":"custom","name":"Formatting","instruction":"Preserve whitespace."}]""")
            .putString(GemmaFloatingSettings.SELECTED, "custom").commit()
        assertEquals(GemmaFloatingRule("custom", "Formatting", "Preserve whitespace."),
            GemmaFloatingSettings(preferences).selectedRule)
    }

    @Test fun malformedRulesDoNotRunAnUnrelatedPreset() {
        preferences.edit().putString(GemmaFloatingSettings.RULES, "[null, {}, {\"id\":null}]")
            .putString(GemmaFloatingSettings.SELECTED, "english").commit()
        assertTrue(GemmaFloatingSettings(preferences).rules.isEmpty())
        assertNull(GemmaFloatingSettings(preferences).selectedRule)
    }

    @Test fun promptKeepsMultilineInputAndUserInstructionSeparate() {
        val prompt = GemmaFloatingPrompt.build("one\ntwo", "Translate into Japanese.")
        assertTrue(prompt.contains("<input>\none\ntwo\n</input>"))
        assertTrue(prompt.contains("Instruction: Translate into Japanese."))
        assertTrue(prompt.contains("Preserve meaningful line breaks"))
    }

    @Test fun systemRolesAreOnlyUsedForKnownGemma4Models() {
        assertTrue(GemmaFloatingPrompt.supportsSystemInstruction("gemma4_e2b_it"))
        assertTrue(GemmaFloatingPrompt.supportsSystemInstruction("gemma4_e4b_it"))
        assertFalse(GemmaFloatingPrompt.supportsSystemInstruction("gemma3_1b_it"))
        assertFalse(GemmaFloatingPrompt.supportsSystemInstruction("gemma3n_e2b_it"))
        assertFalse(GemmaFloatingPrompt.supportsSystemInstruction("custom_model"))
    }
}
