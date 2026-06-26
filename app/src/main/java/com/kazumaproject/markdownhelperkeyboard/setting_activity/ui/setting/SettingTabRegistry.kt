package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

data class SettingTabSpec(
    val key: String,
    val title: (Context) -> String,
    @XmlRes val xmlRes: Int?,
    @IdRes val destinationId: Int,
    val fragmentFactory: () -> Fragment,
)

object SettingTabRegistry {
    const val TAB_COMMON = "common"
    const val TAB_THEME = "theme"
    const val TAB_DICTIONARY = "dictionary"
    const val TAB_KANA = "kana"
    const val TAB_QWERTY = "qwerty"
    const val TAB_SUMIRE = "sumire"
    const val TAB_CUSTOM = "custom"
    const val TAB_TABLET = "tablet"
    const val TAB_HARDWARE_KEYBOARD = "hardware_keyboard"
    const val TAB_ZENZ = "zenz"
    const val TAB_GEMMA = "gemma"
    const val TAB_CONVERSION_ENGINE = "conversion_engine"

    fun createTabs(): List<SettingTabSpec> {
        val tabs = mutableListOf(
            SettingTabSpec(
                key = TAB_COMMON,
                title = { context -> context.getString(R.string.category_common) },
                xmlRes = R.xml.pref_common_legacy,
                destinationId = R.id.legacyCommonPreferenceFragment,
                fragmentFactory = { LegacyCommonPreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_THEME,
                title = { context -> context.getString(R.string.keyboardthemefragment) },
                xmlRes = null,
                destinationId = R.id.keyboardThemeFragment,
                fragmentFactory = { KeyboardThemeFragment() },
            ),
        )

        if (AppVariantConfig.hasZenz) {
            tabs += SettingTabSpec(
                key = TAB_ZENZ,
                title = { "zenz" },
                xmlRes = R.xml.pref_zenz,
                destinationId = R.id.zenzPreferenceFragment,
                fragmentFactory = { ZenzPreferenceFragment() },
            )
        }

        if (AppVariantConfig.hasGemma) {
            tabs += SettingTabSpec(
                key = TAB_GEMMA,
                title = { "Gemma" },
                xmlRes = R.xml.pref_gemma,
                destinationId = R.id.gemmaPreferenceFragment,
                fragmentFactory = { GemmaPreferenceFragment() },
            )
        }

        tabs += SettingTabSpec(
            key = TAB_CONVERSION_ENGINE,
            title = { context -> context.getString(R.string.setting_category_conversion_engine_title) },
            xmlRes = R.xml.pref_conversion_engine,
            destinationId = R.id.conversionEnginePreferenceFragment,
            fragmentFactory = { ConversionEnginePreferenceFragment() },
        )

        tabs += listOf(
            SettingTabSpec(
                key = TAB_DICTIONARY,
                title = { context -> context.getString(R.string.category_dictionary) },
                xmlRes = R.xml.pref_dictionary,
                destinationId = R.id.dictionaryPreferenceFragment,
                fragmentFactory = { DictionaryPreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_KANA,
                title = { context -> context.getString(R.string.category_kana) },
                xmlRes = R.xml.pref_kana,
                destinationId = R.id.kanaPreferenceFragment,
                fragmentFactory = { KanaPreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_QWERTY,
                title = { "QWERTY" },
                xmlRes = R.xml.pref_qwerty,
                destinationId = R.id.qwertyPreferenceFragment,
                fragmentFactory = { QwertyPreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_SUMIRE,
                title = { context -> context.getString(R.string.category_sumire_input_keyboard_title) },
                xmlRes = R.xml.pref_sumire,
                destinationId = R.id.sumirePreferenceFragment,
                fragmentFactory = { SumirePreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_CUSTOM,
                title = { context -> context.getString(R.string.category_custom_keyboard_title) },
                xmlRes = R.xml.pref_custom,
                destinationId = R.id.customKeyboardPreferenceFragment,
                fragmentFactory = { CustomKeyboardPreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_TABLET,
                title = { context -> context.getString(R.string.tablet_preference_category_title) },
                xmlRes = R.xml.pref_tablet,
                destinationId = R.id.tabletPreferenceFragment,
                fragmentFactory = { TabletPreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_HARDWARE_KEYBOARD,
                title = { context -> context.getString(R.string.hardware_keyboard_category_title) },
                xmlRes = R.xml.pref_hardware_keyboard,
                destinationId = R.id.hardwareKeyboardPreferenceFragment,
                fragmentFactory = { HardwareKeyboardPreferenceFragment() },
            ),
        )

        return tabs
    }
}
