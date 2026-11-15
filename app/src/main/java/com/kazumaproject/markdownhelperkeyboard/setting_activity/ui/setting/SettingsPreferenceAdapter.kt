package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView
import android.widget.ImageView
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.google.android.material.R as MaterialR

/** Keep native preference widgets, listeners and accessibility, with one settings presentation. */
@android.annotation.SuppressLint("RestrictedApi")
internal class SettingsPreferenceAdapter(group: PreferenceGroup) : PreferenceGroupAdapter(group) {
    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val view = holder.itemView
        val density = view.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val category = getItem(position) is PreferenceCategory
        val studio = AppPreference.setting_use_new_home_screen_preference
        val params = view.layoutParams as RecyclerView.LayoutParams
        params.setMargins(dp(16), if (category) dp(16) else 0, dp(16), if (studio && !category) dp(8) else 0)
        view.layoutParams = params
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val preference = getItem(position)
        val widget = holder.findViewById(android.R.id.widget_frame) as? ViewGroup
        val arrowTag = "settings_navigation_arrow"
        if (widget != null && widget.childCount == 0) widget.addView(ImageView(view.context).apply {
            tag = arrowTag
            setImageResource(com.kazumaproject.core.R.drawable.baseline_arrow_right_24)
            imageTintList = ColorStateList.valueOf(MaterialColors.getColor(view, MaterialR.attr.colorOnSurfaceVariant))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, ViewGroup.LayoutParams(dp(24), dp(24)))
        if (widget?.getChildAt(0)?.tag == arrowTag) {
            widget.visibility = if (preference?.isSelectable == true && (preference.javaClass == Preference::class.java ||
                    preference is ListPreference || preference is EditTextPreference)) View.VISIBLE else View.GONE
        }
        if (category) {
            view.background = null
            return
        }
        val first = studio || position == 0 || getItem(position - 1) is PreferenceCategory
        val last = studio || position == itemCount - 1 || getItem(position + 1) is PreferenceCategory
        val radius = dp(if (studio) 18 else 16).toFloat()
        val top = if (first) radius else 0f
        val bottom = if (last) radius else 0f
        val shape = GradientDrawable().apply {
            cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            setColor(MaterialColors.getColor(view, if (studio) MaterialR.attr.colorSurface else MaterialR.attr.colorSurfaceContainerLow))
            if (studio) setStroke(dp(1).coerceAtLeast(1), MaterialColors.getColor(view, MaterialR.attr.colorOutlineVariant))
        }
        val ripple = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorControlHighlight)
        view.background = RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
        view.minimumHeight = dp(64)
        (holder.findViewById(android.R.id.title) as? TextView)?.apply {
            isSingleLine = false
            maxLines = Int.MAX_VALUE
        }
    }
}
