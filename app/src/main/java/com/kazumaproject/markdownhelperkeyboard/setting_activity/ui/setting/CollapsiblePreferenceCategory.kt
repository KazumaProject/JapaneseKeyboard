package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.kazumaproject.markdownhelperkeyboard.R
import java.util.IdentityHashMap

/** Conventional settings can collapse groups; new settings use ordinary, always-open headings. */
class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PreferenceCategory(context, attrs) {
    private val collapsible = !PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean("setting_use_new_home_screen_preference", true)
    private var expansionInitialized = false
    private val intrinsicVisibility = IdentityHashMap<Preference, Boolean>()
    private var description: CharSequence? = null
    private var descriptionCaptured = false
    var isExpanded: Boolean = false
        private set

    init {
        isSelectable = collapsible
        isPersistent = false
        isIconSpaceReserved = false
        if (collapsible) {
            layoutResource = androidx.preference.R.layout.preference_material
            widgetLayoutResource = R.layout.preference_group_expander
        }
    }

    override fun isEnabled(): Boolean = collapsible || super.isEnabled()

    override fun onAttached() {
        super.onAttached()
        // AsyncPreferenceFragment can expand a search target before AndroidX binds
        // the screen. Do not overwrite that temporary state during attachment.
        val expanded = if (!collapsible) true else if (expansionInitialized) isExpanded else
            context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE).getBoolean(key, false)
        setExpanded(expanded, persist = false)
    }

    override fun onClick() {
        if (collapsible) setExpanded(!isExpanded)
    }

    fun setExpanded(expanded: Boolean, persist: Boolean = true) {
        if (!descriptionCaptured) { description = summary; descriptionCaptured = true }
        expansionInitialized = true
        isExpanded = expanded || !collapsible
        if (persist && collapsible) {
            context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit().putBoolean(key, expanded).apply()
        }
        for (index in 0 until preferenceCount) {
            val child = getPreference(index)
            val visibleByCondition = intrinsicVisibility.getOrPut(child) { child.isVisible }
            child.isVisible = isExpanded && visibleByCondition
        }
        summary = if (collapsible) {
            val state = context.getString(if (isExpanded) R.string.classified_group_expanded else R.string.classified_group_collapsed)
            description?.takeIf { it.isNotBlank() }?.let { "$it · $state" } ?: state
        } else description
        notifyChanged()
    }

    internal fun setChildConditionVisible(child: Preference, visible: Boolean) {
        intrinsicVisibility[child] = visible
        child.isVisible = visible && isExpanded
    }

    internal fun childConditionVisible(child: Preference): Boolean = intrinsicVisibility[child] ?: child.isVisible

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        if (collapsible) {
            (holder.findViewById(R.id.preference_group_expander) as? TextView)?.text = if (isExpanded) "▾" else "▸"
        }
        holder.itemView.isClickable = collapsible
    }

    companion object {
        const val STATE_PREFERENCES = "dictionary_settings_expansion"
    }
}

/** Keep a preference's own condition separate from its containing heading's state. */
internal var Preference.conditionVisible: Boolean
    get() = (parent as? CollapsiblePreferenceCategory)?.childConditionVisible(this) ?: isVisible
    set(value) {
        val category = parent as? CollapsiblePreferenceCategory
        if (category == null) isVisible = value else category.setChildConditionVisible(this, value)
    }
