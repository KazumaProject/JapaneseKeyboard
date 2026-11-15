package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewCarousel

/** The preview scrolls with appearance settings instead of occupying the settings home. */
internal class SelectedKeyboardPreviewPreference(
    context: Context,
    private val owner: LifecycleOwner,
    private val manager: FragmentManager,
    private val onEdit: () -> Unit,
) : Preference(context) {
    private var carousel: KeyboardPreviewCarousel? = null

    init {
        key = KEY
        order = -100
        isSelectable = false
        isPersistent = false
        layoutResource = R.layout.preference_selected_keyboard_preview
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val frame = holder.findViewById(R.id.setting_home_preview) as FrameLayout
        val summary = holder.findViewById(R.id.setting_home_preview_summary) as TextView
        val existing = frame.getChildAt(0) as? KeyboardPreviewCarousel
        carousel = existing ?: KeyboardPreviewCarousel(context, owner, manager, summary, onEdit).also {
            frame.removeAllViews()
            frame.addView(it)
        }
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
    }

    fun reload() = carousel?.reload()

    companion object {
        const val KEY = "settings_selected_keyboard_preview"
    }
}
