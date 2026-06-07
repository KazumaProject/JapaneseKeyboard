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
    private val onClick: (SettingDestination) -> Unit,
) : ListAdapter<SettingDestination, SettingSearchAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemSettingSearchResultBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSettingSearchResultBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(destination: SettingDestination) {
            val context = binding.root.context
            val categoryTitle = SettingDestinations.categoryTitle(context, destination.category)
            val summary = destination.summary.ifBlank { categoryTitle }

            binding.settingSearchResultIcon.setImageResource(destination.iconRes)
            binding.settingSearchResultTitle.text = destination.title
            binding.settingSearchResultSummary.apply {
                text = summary
                isVisible = summary.isNotBlank()
            }
            binding.settingSearchResultCategory.text = categoryTitle
            binding.root.apply {
                contentDescription = context.getString(
                    R.string.setting_search_result_content_description,
                    destination.title,
                    summary,
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
