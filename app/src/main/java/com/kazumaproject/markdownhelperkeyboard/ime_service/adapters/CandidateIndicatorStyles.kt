package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.kazumaproject.markdownhelperkeyboard.R

enum class CandidateIndicatorLineStyle(val value: String, val titleRes: Int) {
    SOLID("solid", R.string.candidate_indicator_solid),
    DOUBLE("double", R.string.candidate_indicator_double),
    DOTTED("dotted", R.string.candidate_indicator_dotted),
    DASHED("dashed", R.string.candidate_indicator_dashed),
    NONE("none", R.string.candidate_indicator_none),
}

enum class CandidateIndicatorGroup(val key: String, val titleRes: Int, val defaultLine: CandidateIndicatorLineStyle, val colorRes: Int) {
    KATAKANA("katakana", R.string.candidate_indicator_katakana, CandidateIndicatorLineStyle.SOLID, R.color.candidate_character_katakana),
    FULL_WIDTH("full_width", R.string.candidate_indicator_full_width, CandidateIndicatorLineStyle.DOUBLE, R.color.candidate_character_full_width),
    HALF_WIDTH("half_width", R.string.candidate_indicator_half_width, CandidateIndicatorLineStyle.DOTTED, R.color.candidate_character_half_width),
    HALF_KATAKANA("half_katakana", R.string.candidate_indicator_half_katakana, CandidateIndicatorLineStyle.DASHED, R.color.candidate_character_half_width),
}

data class CandidateIndicatorStyle(
    val line: CandidateIndicatorLineStyle,
    val lightColor: Int? = null,
    val darkColor: Int? = null,
) {
    fun color(context: Context, group: CandidateIndicatorGroup, dark: Boolean): Int {
        (if (dark) darkColor else lightColor)?.let { return it }
        if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) == dark) {
            return context.getColor(group.colorRes)
        }
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(config).getColor(group.colorRes)
    }
}

data class CandidateIndicatorStyles(
    val katakana: CandidateIndicatorStyle = CandidateIndicatorStyle(CandidateIndicatorGroup.KATAKANA.defaultLine),
    val fullWidth: CandidateIndicatorStyle = CandidateIndicatorStyle(CandidateIndicatorGroup.FULL_WIDTH.defaultLine),
    val halfWidth: CandidateIndicatorStyle = CandidateIndicatorStyle(CandidateIndicatorGroup.HALF_WIDTH.defaultLine),
    val halfKatakana: CandidateIndicatorStyle = CandidateIndicatorStyle(CandidateIndicatorGroup.HALF_KATAKANA.defaultLine),
) {
    operator fun get(group: CandidateIndicatorGroup): CandidateIndicatorStyle = when (group) {
        CandidateIndicatorGroup.KATAKANA -> katakana
        CandidateIndicatorGroup.FULL_WIDTH -> fullWidth
        CandidateIndicatorGroup.HALF_WIDTH -> halfWidth
        CandidateIndicatorGroup.HALF_KATAKANA -> halfKatakana
    }
}

/** Stable, independent keys: resetting styles never changes the feature's enabled switch. */
object CandidateIndicatorSettings {
    fun lineKey(group: CandidateIndicatorGroup) = "candidate_character_${group.key}_line_style"
    fun colorKey(group: CandidateIndicatorGroup, dark: Boolean) =
        "candidate_character_${group.key}_color_${if (dark) "dark" else "light"}"

    /** Sanitize imported values before AndroidX ListPreference reads their persisted types. */
    fun normalize(preferences: SharedPreferences) {
        val values = preferences.all
        val invalid = CandidateIndicatorGroup.entries.flatMap { group ->
            buildList {
                val line = lineKey(group)
                if (line in values && CandidateIndicatorLineStyle.entries.none { it.value == values[line] }) add(line)
                for (dark in listOf(false, true)) {
                    val key = colorKey(group, dark)
                    if (key in values && values[key] !is Int) add(key)
                }
            }
        }
        if (invalid.isNotEmpty()) preferences.edit().apply { invalid.forEach(::remove) }.apply()
    }

    fun read(preferences: SharedPreferences): CandidateIndicatorStyles {
        fun style(group: CandidateIndicatorGroup): CandidateIndicatorStyle {
            val value = runCatching { preferences.getString(lineKey(group), null) }.getOrNull()
            fun color(dark: Boolean): Int? = colorKey(group, dark).let { key ->
                if (preferences.contains(key)) runCatching { preferences.getInt(key, 0) }.getOrNull() else null
            }
            return CandidateIndicatorStyle(
                CandidateIndicatorLineStyle.entries.firstOrNull { it.value == value } ?: group.defaultLine,
                color(false), color(true),
            )
        }
        return CandidateIndicatorStyles(style(CandidateIndicatorGroup.KATAKANA), style(CandidateIndicatorGroup.FULL_WIDTH),
            style(CandidateIndicatorGroup.HALF_WIDTH), style(CandidateIndicatorGroup.HALF_KATAKANA))
    }

    fun reset(preferences: SharedPreferences) {
        preferences.edit().apply {
            CandidateIndicatorGroup.entries.forEach { group ->
                remove(lineKey(group)); remove(colorKey(group, false)); remove(colorKey(group, true))
            }
        }.apply()
    }
}
