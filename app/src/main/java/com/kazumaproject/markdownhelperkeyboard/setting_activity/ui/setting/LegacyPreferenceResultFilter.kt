package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup

object LegacyPreferenceResultFilter {
    const val ARG_LEGACY_SEARCH_RESULT_MODE = "legacy_search_result_mode"
    const val ARG_LEGACY_SEARCH_TARGET_KEY = "legacy_search_target_key"
    const val ARG_LEGACY_SEARCH_RELATED_KEYS = "legacy_search_related_keys"

    fun filterPreferenceScreen(
        screen: PreferenceGroup,
        targetKey: String,
        relatedKeys: Collection<String>,
    ): Set<String> {
        val visibleKeys = buildVisibleKeys(
            screen = screen,
            targetKey = targetKey,
            relatedKeys = relatedKeys,
        )
        pruneGroup(screen, visibleKeys, isRoot = true)
        return visibleKeys
    }

    private fun buildVisibleKeys(
        screen: PreferenceGroup,
        targetKey: String,
        relatedKeys: Collection<String>,
    ): Set<String> {
        val visibleKeys = (listOf(targetKey) + relatedKeys)
            .filter { it.isNotBlank() }
            .toMutableSet()
        val pending = ArrayDeque(visibleKeys)
        while (pending.isNotEmpty()) {
            val key = pending.removeFirst()
            val dependencyKey = findPreference(screen, key)?.dependency
            if (!dependencyKey.isNullOrBlank() && visibleKeys.add(dependencyKey)) {
                pending.add(dependencyKey)
            }
        }
        return visibleKeys
    }

    private fun pruneGroup(
        group: PreferenceGroup,
        visibleKeys: Set<String>,
        isRoot: Boolean,
    ): Boolean {
        for (index in group.preferenceCount - 1 downTo 0) {
            val preference = group.getPreference(index)
            val keep = when (preference) {
                is PreferenceGroup -> pruneGroup(
                    group = preference,
                    visibleKeys = visibleKeys,
                    isRoot = false,
                )

                else -> preference.key in visibleKeys
            }
            if (!keep) {
                group.removePreference(preference)
            }
        }
        return isRoot || group.key?.let { it in visibleKeys } == true || group.preferenceCount > 0
    }

    private fun findPreference(group: PreferenceGroup, key: String): Preference? {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            if (preference.key == key) return preference
            if (preference is PreferenceGroup) {
                findPreference(preference, key)?.let { return it }
            }
        }
        return null
    }
}

fun PreferenceFragmentCompat.applyLegacySearchResultFilterIfNeeded() {
    val args = arguments ?: return
    if (!args.getBoolean(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_RESULT_MODE, false)) {
        return
    }
    val targetKey = args
        .getString(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_TARGET_KEY)
        ?.takeIf { it.isNotBlank() }
        ?: return
    val relatedKeys = args.getStringArray(
        LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_RELATED_KEYS
    )?.toList().orEmpty()
    preferenceScreen?.let { screen ->
        LegacyPreferenceResultFilter.filterPreferenceScreen(
            screen = screen,
            targetKey = targetKey,
            relatedKeys = relatedKeys,
        )
    }
}
