package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemSettingSearchResultBinding

class SettingSearchAdapter(
    private val editorController: SettingCardEditorController,
    private val onClick: (SettingDestination) -> Unit,
) : ListAdapter<SettingDestination, SettingSearchAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemSettingSearchResultBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun notifyDestinationChanged(destination: SettingDestination) {
        val index = currentList.indexOfFirst {
            it.key == destination.key && it.destination == destination.destination
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    inner class ViewHolder(
        private val binding: ItemSettingSearchResultBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(destination: SettingDestination) {
            val context = binding.root.context
            val categoryTitle = destination.location
                ?: SettingDestinations.categoryTitle(context, destination.category)
            val summary = destination.summary.ifBlank { categoryTitle }
            val currentValue = editorController.currentValueLabel(destination)
            val managementLabel =
                (destination.destination as? SettingDestinationType.ManagementDestination)
                    ?.let { context.getString(R.string.setting_frequent_type_management) }
            val switchTarget = destination.destination as? SettingDestinationType.SwitchPreference
            val isLegacyResult = destination.legacyTarget != null
            val isDirectEditable = !isLegacyResult && editorController.isDirectEditable(destination)

            binding.settingSearchResultIcon.setImageResource(destination.iconRes)
            binding.settingSearchResultTitle.text = destination.title
            binding.settingSearchResultSummary.apply {
                text = summary
                isVisible = summary.isNotBlank()
            }
            binding.settingSearchResultCategory.text = categoryTitle
            binding.settingSearchResultValue.apply {
                text = currentValue?.let {
                    context.getString(R.string.setting_home_current_value, it)
                } ?: managementLabel
                isVisible = currentValue != null || managementLabel != null
            }
            binding.settingSearchResultSwitch.apply {
                isVisible = switchTarget != null && !isLegacyResult
                if (switchTarget != null && !isLegacyResult) {
                    isChecked = editorController.readSwitchPreference(switchTarget)
                    contentDescription = destination.title
                }
            }
            binding.settingSearchResultArrow.isVisible = !isDirectEditable
            binding.root.apply {
                contentDescription = context.getString(
                    R.string.setting_search_result_content_description,
                    destination.title,
                    listOf(summary, currentValue, managementLabel)
                        .filter { !it.isNullOrBlank() }
                        .joinToString(". "),
                    categoryTitle,
                )
                setOnClickListener { onClick(destination) }
            }
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
}
