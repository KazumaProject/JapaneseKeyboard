package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

internal data class SettingsOverviewSection(@param:StringRes val title: Int, val entries: List<SettingDestination>)

internal object SettingsOverview {
    fun sections(context: Context): List<SettingsOverviewSection> {
        val categories = SettingDestinations.categories(context).associateBy { it.key }
        fun entries(vararg keys: String) = keys.mapNotNull(categories::get)
        return listOf(
            SettingsOverviewSection(R.string.settings_overview_keyboard, entries(
                "setting_route_input_method", "setting_route_keyboard_display", "setting_route_morse", "setting_route_split_keyboard",
            )),
            SettingsOverviewSection(R.string.settings_overview_input, entries(
                "setting_route_candidate_conversion", "setting_route_conversion_engine", "setting_route_dictionary", "setting_route_voice_input",
                *if (AppVariantConfig.hasZenz || AppVariantConfig.hasGemma) arrayOf("setting_route_ai_conversion") else emptyArray(),
            )),
            SettingsOverviewSection(R.string.settings_overview_other, entries(
                "setting_route_operation_feedback", "setting_route_clipboard_shortcut", "setting_route_backup",
                "setting_route_app_info", "setting_route_advanced",
            )),
        )
    }
}
