package com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation

import android.content.Context
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry

internal data class SettingsNavigationItem(val key: String, val title: String, val destination: Int, val icon: Int)

internal fun settingsNavigationCatalog(context: Context): List<SettingsNavigationItem> {
    val core = listOf(
        SettingsNavigationItem("home", context.getString(R.string.settings_navigation_home), R.id.navigation_setting, CoreR.drawable.baseline_settings_24),
        SettingsNavigationItem("search", context.getString(R.string.setting_search_title), R.id.settingSearchFragment, CoreR.drawable.baseline_search_24),
        SettingsNavigationItem("selection", context.getString(R.string.keyboard_selection_preference_title), R.id.keyboardSelectionFragment, CoreR.drawable.keyboard_24px),
        SettingsNavigationItem("appearance", context.getString(R.string.settings_navigation_appearance), R.id.keyboardDisplayPreferenceFragment, CoreR.drawable.keyboard_24px),
        SettingsNavigationItem("learn", context.getString(R.string.learn_dictionary_fragment_title), R.id.navigation_learn_dictionary, CoreR.drawable.table_lamp_24px),
        SettingsNavigationItem("user", context.getString(R.string.user_dictionary_title), R.id.navigation_user_dictionary, CoreR.drawable.dictionary_24px),
        SettingsNavigationItem("templates", context.getString(R.string.user_template), R.id.userTemplateFragment, CoreR.drawable.book_3_24px),
        SettingsNavigationItem("custom", context.getString(R.string.custom_keyboard_fragment_label), R.id.keyboardListFragment, CoreR.drawable.keyboard_24px),
    )
    val routes = (SettingDestinations.categories(context) + SettingDestinations.management(context)).mapNotNull { item ->
        val destination = when (val route = item.destination) {
            is SettingDestinationType.NavDestination -> route.destinationId
            is SettingDestinationType.ManagementDestination -> route.destinationId
            else -> return@mapNotNull null
        }
        SettingsNavigationItem("route:${item.key}", item.title, destination, item.iconRes)
    }
    val tabs = SettingTabRegistry.createTabs().map {
        SettingsNavigationItem("tab:${it.key}", it.title(context), it.destinationId, CoreR.drawable.baseline_settings_24)
    }
    return (core + routes + tabs).distinctBy { it.destination }
}

internal data class SettingsNavigationConfig(
    val drawer: List<String>, val bottom: List<String>,
)

internal class SettingsNavigationStore(context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val catalog = settingsNavigationCatalog(context)
    val defaults get() = SettingsNavigationConfig(DEFAULT_DRAWER, DEFAULT_BOTTOM)

    fun read(): SettingsNavigationConfig = runCatching {
        val json = preferences.getString(KEY, null) ?: return defaults
        val data = com.google.gson.JsonParser.parseString(json).asJsonObject
        fun keys(name: String) = data.getAsJsonArray(name).map { it.asString }.filter(String::isNotBlank).distinct()
        SettingsNavigationConfig(keys("drawer"), keys("bottom"))
    }.getOrElse { defaults }

    fun save(config: SettingsNavigationConfig) {
        val data = com.google.gson.JsonObject()
        for ((name, keys) in listOf("drawer" to config.drawer, "bottom" to config.bottom)) {
            data.add(name, com.google.gson.JsonArray().apply { keys.distinct().forEach { add(it) } })
        }
        preferences.edit().putString(KEY, data.toString()).apply()
    }
    fun items(keys: List<String>) = keys.mapNotNull { key -> catalog.find { it.key == key } }
    fun bottom(config: SettingsNavigationConfig = read()) = items(config.bottom).take(MAX_BOTTOM)
    companion object {
        const val KEY = "settings_navigation_v1"
        const val MAX_BOTTOM = 5
        val DEFAULT_DRAWER = listOf("home", "search", "selection", "appearance", "learn", "user", "templates", "custom")
        val DEFAULT_BOTTOM = listOf("home", "learn", "user", "templates", "custom")
    }
}
