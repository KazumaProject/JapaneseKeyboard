package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppCompatR
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemSettingFrequentEditBinding

class FrequentSelectedSettingsAdapter(
    private val onRemove: (SettingDestination) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMove: (key: String, direction: Int) -> Boolean,
) : ListAdapter<SettingDestination, FrequentSelectedSettingsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemSettingFrequentEditBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    inner class ViewHolder(
        private val binding: ItemSettingFrequentEditBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(destination: SettingDestination, position: Int, count: Int) {
            val context = binding.root.context
            val summary = destination.editSummary(context)

            binding.settingFrequentCard.apply {
                isClickable = false
                isFocusable = true
                setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurfaceVariant))
                strokeWidth = dp(1)
                setStrokeColor(resolveColor(AppCompatR.attr.colorPrimary))
                contentDescription = listOf(
                    destination.title,
                    summary,
                    context.getString(R.string.setting_frequent_selected),
                    context.getString(R.string.setting_frequent_drag_reorder),
                ).filter { it.isNotBlank() }.joinToString(". ")
            }

            binding.settingFrequentDragHandle.apply {
                isVisible = true
                isEnabled = true
                imageTintList = ColorStateList.valueOf(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
                contentDescription = context.getString(R.string.setting_frequent_drag_reorder)
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this@ViewHolder)
                        true
                    } else {
                        false
                    }
                }
            }

            binding.settingFrequentIcon.apply {
                setImageResource(destination.iconRes)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            }
            binding.settingFrequentTitle.text = destination.title
            binding.settingFrequentSummary.apply {
                text = summary
                isVisible = summary.isNotBlank()
            }
            binding.settingFrequentActionIcon.apply {
                setImageResource(CoreR.drawable.baseline_delete_24)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
                contentDescription = context.getString(
                    R.string.setting_frequent_remove_content_description,
                    destination.title,
                )
                setOnClickListener { onRemove(destination) }
            }

            setAccessibilityMoveActions(destination, position, count)
        }

        private fun setAccessibilityMoveActions(
            destination: SettingDestination,
            position: Int,
            count: Int,
        ) {
            ViewCompat.setAccessibilityDelegate(
                binding.settingFrequentCard,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)

                        if (position > 0) {
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.setting_frequent_action_move_up,
                                    binding.root.context.getString(
                                        R.string.setting_frequent_move_up,
                                        destination.title,
                                    ),
                                )
                            )
                        }
                        if (position in 0 until count - 1) {
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.setting_frequent_action_move_down,
                                    binding.root.context.getString(
                                        R.string.setting_frequent_move_down,
                                        destination.title,
                                    ),
                                )
                            )
                        }
                    }

                    override fun performAccessibilityAction(
                        host: View,
                        action: Int,
                        args: Bundle?,
                    ): Boolean =
                        when (action) {
                            R.id.setting_frequent_action_move_up ->
                                onMove(destination.key, -1)

                            R.id.setting_frequent_action_move_down ->
                                onMove(destination.key, 1)

                            else -> super.performAccessibilityAction(host, action, args)
                        }
                }
            )
        }

        private fun resolveColor(@AttrRes attr: Int): Int =
            binding.root.context.resolveThemeColor(attr)

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }
}

class FrequentAddSettingsAdapter(
    private val onAdd: (SettingDestination) -> Unit,
) : ListAdapter<SettingDestination, FrequentAddSettingsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemSettingFrequentEditBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSettingFrequentEditBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(destination: SettingDestination) {
            val context = binding.root.context
            val summary = destination.editSummary(context)

            binding.settingFrequentCard.apply {
                isClickable = true
                isFocusable = true
                setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurface))
                strokeWidth = dp(1)
                setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
                contentDescription = listOf(
                    destination.title,
                    summary,
                    context.getString(R.string.setting_frequent_available),
                ).filter { it.isNotBlank() }.joinToString(". ")
                setOnClickListener { onAdd(destination) }
            }

            binding.settingFrequentDragHandle.apply {
                isVisible = false
                setOnTouchListener(null)
            }
            binding.settingFrequentIcon.apply {
                setImageResource(destination.iconRes)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            }
            binding.settingFrequentTitle.text = destination.title
            binding.settingFrequentSummary.apply {
                text = summary
                isVisible = summary.isNotBlank()
            }
            binding.settingFrequentActionIcon.apply {
                setImageResource(CoreR.drawable.baseline_add_circle_outline_24)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
                contentDescription = context.getString(
                    R.string.setting_frequent_add_content_description,
                    destination.title,
                )
                setOnClickListener { onAdd(destination) }
            }
        }

        private fun resolveColor(@AttrRes attr: Int): Int =
            binding.root.context.resolveThemeColor(attr)

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }
}

private object DiffCallback : DiffUtil.ItemCallback<SettingDestination>() {
    override fun areItemsTheSame(
        oldItem: SettingDestination,
        newItem: SettingDestination,
    ): Boolean = oldItem.key == newItem.key &&
        oldItem.destination == newItem.destination

    override fun areContentsTheSame(
        oldItem: SettingDestination,
        newItem: SettingDestination,
    ): Boolean = oldItem == newItem
}

private fun android.content.Context.resolveThemeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) {
        ContextCompat.getColor(this, value.resourceId)
    } else {
        value.data
    }
}

private fun SettingDestination.editSummary(context: android.content.Context): String =
    listOf(summary, destination.typeLabel(context))
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun SettingDestinationType.typeLabel(context: android.content.Context): String =
    when (this) {
        is SettingDestinationType.NavDestination ->
            context.getString(R.string.setting_frequent_type_nav)
        is SettingDestinationType.ManagementDestination ->
            context.getString(R.string.setting_frequent_type_management)
        is SettingDestinationType.SwitchPreference ->
            context.getString(R.string.setting_frequent_type_switch)
        is SettingDestinationType.ListPreference ->
            context.getString(R.string.setting_frequent_type_list)
        is SettingDestinationType.SeekBarPreference,
        is SettingDestinationType.IntPreferenceDialog ->
            context.getString(R.string.setting_frequent_type_number)
        is SettingDestinationType.EditTextPreference ->
            context.getString(R.string.setting_frequent_type_text)
    }
