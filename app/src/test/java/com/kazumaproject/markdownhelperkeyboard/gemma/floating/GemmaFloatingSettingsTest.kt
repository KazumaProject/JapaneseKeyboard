package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
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
