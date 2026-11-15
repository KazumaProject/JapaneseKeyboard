package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceCategory

/** Always-open settings headings; each child retains its own visibility condition. */
class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PreferenceCategory(context, attrs) {
    val isExpanded: Boolean get() = true

    init {
        isSelectable = false
        isPersistent = false
        isIconSpaceReserved = false
    }

    // Existing search/highlight callers can request visibility without hiding
    // conditionally unavailable settings or persisting expansion state.
    @Suppress("UNUSED_PARAMETER")
    fun setExpanded(expanded: Boolean, persist: Boolean = true) = Unit

    companion object {
        const val STATE_PREFERENCES = "dictionary_settings_expansion"
    }
}

internal var Preference.conditionVisible: Boolean
    get() = isVisible
    set(value) { isVisible = value }
