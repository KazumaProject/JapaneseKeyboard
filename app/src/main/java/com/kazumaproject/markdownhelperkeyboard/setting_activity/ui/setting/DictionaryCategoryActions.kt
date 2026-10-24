package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory

/** Uses the preference hierarchy so parent actions include all nested categories. */
internal class DictionaryCategoryActions(
    private val screen: PreferenceGroup,
    private val isEnabled: (DictionaryCategory) -> Boolean,
    private val update: (List<DictionaryCategory>, Boolean) -> Unit,
) {
    private val actions = mutableListOf<Pair<Preference, List<DictionaryCategory>>>()

    fun bind() {
        val specsByKey = ClassifiedDictionaries.all.associateBy { it.preferenceKey }
        fun visit(group: PreferenceGroup): List<DictionaryCategory> {
            // Snapshot children before inserting the action row.
            val categories = (0 until group.preferenceCount).map { group.getPreference(it) }
                .flatMap { child ->
                    if (child is PreferenceGroup) visit(child)
                    else listOfNotNull(specsByKey[child.key]?.category)
                }
            if (group is CollapsiblePreferenceCategory && categories.isNotEmpty()) {
                val action = Preference(group.context).apply {
                    key = "${group.key}_bulk_toggle"
                    setTitle(R.string.classified_group_bulk_title)
                    isPersistent = false
                    isIconSpaceReserved = false
                    order = -1
                    isVisible = group.isExpanded
                    setOnPreferenceClickListener {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(group.title)
                            .setItems(arrayOf(
                                context.getString(R.string.classified_group_enable_all),
                                context.getString(R.string.classified_group_disable_all),
                            )) { _, which ->
                                update(categories, which == 0)
                                refresh()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        true
                    }
                }
                group.addPreference(action)
                actions += action to categories
            }
            return categories
        }
        visit(screen)
        refresh()
    }

    fun refresh() {
        actions.forEach { (action, categories) ->
            action.summary = action.context.getString(
                R.string.classified_group_enabled_count, categories.count(isEnabled), categories.size,
            )
        }
    }
}
