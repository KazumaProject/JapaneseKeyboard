package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemCustomKeyboardLayoutBinding

class CustomKeyboardLayoutAdapter :
    ListAdapter<CustomKeyboardLayout, CustomKeyboardLayoutAdapter.CustomKeyboardLayoutViewHolder>(
        DiffCallback
    ) {

    // ViewHolder: 各アイテムの View への参照を保持する
    class CustomKeyboardLayoutViewHolder(private val binding: ItemCustomKeyboardLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(layout: CustomKeyboardLayout) {
            binding.keyboardNameTextView.text = layout.name
            binding.keyboardSizeTextView.text = "${layout.columnCount} x ${layout.rowCount}"
        }
    }

    // ViewHolder が作成されるときの処理
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CustomKeyboardLayoutViewHolder {
        val binding = ItemCustomKeyboardLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CustomKeyboardLayoutViewHolder(binding)
    }

    // ViewHolder にデータがバインド（割り当て）されるときの処理
    override fun onBindViewHolder(holder: CustomKeyboardLayoutViewHolder, position: Int) {
        val currentLayout = getItem(position)
        holder.bind(currentLayout)
    }

    // DiffUtil.ItemCallback: リストの差分を計算するためのオブジェクト
    companion object DiffCallback : DiffUtil.ItemCallback<CustomKeyboardLayout>() {
        override fun areItemsTheSame(
            oldItem: CustomKeyboardLayout,
            newItem: CustomKeyboardLayout
        ): Boolean {
            // アイテムが一意に識別できるか（IDで比較）
            return oldItem.layoutId == newItem.layoutId
        }

        override fun areContentsTheSame(
            oldItem: CustomKeyboardLayout,
            newItem: CustomKeyboardLayout
        ): Boolean {
            // アイテムの内容が同じか（データクラスの equals で比較）
            return oldItem == newItem
        }
    }
}
