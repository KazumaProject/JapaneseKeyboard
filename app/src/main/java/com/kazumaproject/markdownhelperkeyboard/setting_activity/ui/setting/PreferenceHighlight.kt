package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.view.View
import android.graphics.drawable.ColorDrawable
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import com.google.android.material.color.MaterialColors

internal fun PreferenceFragmentCompat.expandHighlightedPreferenceAncestors() {
    val targetKey = arguments?.getString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY)
        ?.takeIf { it.isNotBlank() }
        ?: arguments?.getString(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_TARGET_KEY)
        ?: return
    val target = findPreference<Preference>(targetKey)
    if (target is CollapsiblePreferenceCategory) target.setExpanded(true, persist = false)
    var parent = target?.parent
    while (parent != null) {
        if (parent is CollapsiblePreferenceCategory) parent.setExpanded(true, persist = false)
        parent = parent.parent
    }
}

internal fun PreferenceFragmentCompat.scrollToHighlightedPreferenceAfterLayout(view: View) {
    arguments
        ?.getString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY)
        ?.takeIf { it.isNotBlank() }
        ?.let { preferenceKey ->
            view.post {
                runCatching {
                    scrollToPreference(preferenceKey)
                    listView.postDelayed({
                        val adapter = listView.adapter as? PreferenceGroupAdapter ?: return@postDelayed
                        val position = adapter.getPreferenceAdapterPosition(preferenceKey)
                        val item = listView.findViewHolderForAdapterPosition(position)?.itemView ?: return@postDelayed
                        val color = MaterialColors.getColor(item, androidx.appcompat.R.attr.colorPrimary, 0xff6688aa.toInt())
                        val highlight = ColorDrawable(color).apply { alpha = 48; setBounds(0, 0, item.width, item.height) }
                        item.overlay.add(highlight)
                        item.postDelayed({ item.overlay.remove(highlight) }, 1600)
                    }, 180)
                }
            }
        }
}
