package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

data class SettingTabSpec(
    val title: (Context) -> String,
    val fragmentFactory: () -> Fragment,
)

object SettingTabRegistry {
    fun createTabs(): List<SettingTabSpec> {
        val tabs = mutableListOf(
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_common) },
                fragmentFactory = { CommonPreferenceFragment() },
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.keyboardthemefragment) },
                fragmentFactory = { KeyboardThemeFragment() },
            ),
        )

        if (AppVariantConfig.hasZenz) {
            tabs += SettingTabSpec(
                title = { "zenz" },
                fragmentFactory = { ZenzPreferenceFragment() },
            )
        }

        if (AppVariantConfig.hasGemma) {
            tabs += SettingTabSpec(
                title = { "Gemma" },
                fragmentFactory = { GemmaPreferenceFragment() },
            )
        }

        tabs += listOf(
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_dictionary) },
                fragmentFactory = { DictionaryPreferenceFragment() },
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_kana) },
                fragmentFactory = { KanaPreferenceFragment() },
            ),
            SettingTabSpec(
                title = { "QWERTY" },
                fragmentFactory = { QwertyPreferenceFragment() },
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_sumire_input_keyboard_title) },
                fragmentFactory = { SumirePreferenceFragment() },
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_custom_keyboard_title) },
                fragmentFactory = { CustomKeyboardPreferenceFragment() },
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.tablet_preference_category_title) },
                fragmentFactory = { TabletPreferenceFragment() },
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.hardware_keyboard_category_title) },
                fragmentFactory = { HardwareKeyboardPreferenceFragment() },
            ),
        )

        return tabs
    }
}
