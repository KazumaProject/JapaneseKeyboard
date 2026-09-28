package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceCustomKeyboardEmptyAreaInputTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun emptyAreaInputDefaultsOffAndPersistsThroughBackup() {
        assertFalse(AppPreference.custom_keyboard_input_in_empty_areas_preference)

        AppPreference.custom_keyboard_input_in_empty_areas_preference = true
        val backup = AppPreference.exportAllToJson()
        assertTrue(AppPreference.custom_keyboard_input_in_empty_areas_preference)

        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        assertFalse("Backups without this new value keep the default off", AppPreference.custom_keyboard_input_in_empty_areas_preference)
        AppPreference.importAllFromJson(backup)
        assertTrue(AppPreference.custom_keyboard_input_in_empty_areas_preference)
    }

    @Test
    fun emptyAreaInputAppearsInNewAndLegacyCustomKeyboardSettings() {
        val key = AppPreference.CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_KEY
        val newItem = SettingSearchIndex.searchable(context).firstOrNull { it.key == key }
            ?: error("Empty area input setting is missing from new settings")
        val newSetting = newItem.destination as SettingDestinationType.SwitchPreference
        assertFalse(newSetting.defaultValue)
        assertEquals(
            R.id.customKeyboardPreferenceFragment,
            SettingDestinations.destinationId(newItem.destination)
        )

        val legacyItem = SettingSearchIndex.legacySearchable(context).firstOrNull { it.key == key }
            ?: error("Empty area input setting is missing from legacy settings")
        assertEquals(
            R.id.customKeyboardPreferenceFragment,
            SettingDestinations.destinationId(legacyItem.destination)
        )
    }

    @Test
    fun emptyAreaInputDescriptionsAreShortAndExplainBothStates() {
        val japaneseContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.JAPANESE)
            }
        )
        assertEquals(
            "空白セルやスペーサーでは入力しません。",
            japaneseContext.getString(R.string.custom_keyboard_input_in_empty_areas_summary_off)
        )
        assertEquals(
            "空白セルやスペーサーを押すと、最も近いキーを入力します。",
            japaneseContext.getString(R.string.custom_keyboard_input_in_empty_areas_summary_on)
        )
        assertEquals(
            "Empty cells and spacers do not accept input.",
            context.getString(R.string.custom_keyboard_input_in_empty_areas_summary_off)
        )
        assertEquals(
            "Touches in empty cells and spacers use the nearest key.",
            context.getString(R.string.custom_keyboard_input_in_empty_areas_summary_on)
        )
    }

    @Test
    fun emptyAreaInputSettingUpdatesAlreadyDisplayedCustomKeyboards() {
        val source = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()
        val runtimeKeys = source.substringAfter(
            "private val runtimeInputPreferenceKeys = setOf("
        ).substringBefore(")")
        val syncBody = source.substringAfter(
            "private fun syncCustomKeyboardHitTestPreference()"
        ).substringBefore("private fun applyImePreferences(")

        assertTrue(
            runtimeKeys.contains("AppPreference.CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_KEY")
        )
        assertTrue(syncBody.contains("setKeyHitTestMode(hitTestMode)"))
    }

    private fun mainFile(relativePath: String): File {
        val candidates = listOf(
            File("app/src/main/$relativePath"),
            File("src/main/$relativePath")
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate $relativePath")
    }
}
