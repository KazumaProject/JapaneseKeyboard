package com.kazumaproject.markdownhelperkeyboard.short_cut

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemShortcutSettingBinding
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.EditableShortcut

class ShortcutSettingAdapter(
    private val onToggle: (Int, Boolean) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<EditableShortcut, ShortcutSettingAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemShortcutSettingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            // スイッチのリスナー
            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onToggle(position, isChecked)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShortcutSettingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textTitle.text = item.type.description
            iconImage.setImageResource(item.type.iconResId)

            // リスナーを一時的に外して無限ループ防止
            switchEnable.setOnCheckedChangeListener(null)
            switchEnable.isChecked = item.isEnabled
            switchEnable.setOnCheckedChangeListener { _, isChecked ->
                onToggle(holder.bindingAdapterPosition, isChecked)
            }

            // ハンドルのタッチイベントでドラッグ開始
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<EditableShortcut>() {
        override fun areItemsTheSame(
            oldItem: EditableShortcut,
            newItem: EditableShortcut
        ): Boolean {
            return oldItem.type.id == newItem.type.id
        }

        override fun areContentsTheSame(
            oldItem: EditableShortcut,
            newItem: EditableShortcut
        ): Boolean {
            return oldItem == newItem
        }
    }
}
