package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.markdownhelperkeyboard.R
import java.util.UUID

data class SpecialFlickMappingItem(
    val id: String = UUID.randomUUID().toString(),
    val direction: FlickDirection,
    val action: KeyAction? = null
)

/**
 * UI側で扱う「表示名/アクション/アイコン」の安定型
 * KeyEditorFragment 側で KeyActionMapper の返却値をここに変換して渡す想定
 */
data class DisplayActionUi(
    val displayName: String,
    val action: KeyAction,
    val iconResId: Int?
)

class SpecialFlickMappingAdapter(
    private val context: Context,
    private val displayActions: List<DisplayActionUi>,
    private val onItemUpdated: (SpecialFlickMappingItem) -> Unit
) : ListAdapter<SpecialFlickMappingItem, SpecialFlickMappingAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SpecialFlickMappingItem>() {
            override fun areItemsTheSame(oldItem: SpecialFlickMappingItem, newItem: SpecialFlickMappingItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SpecialFlickMappingItem, newItem: SpecialFlickMappingItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textDirection: TextView = itemView.findViewById(R.id.text_direction)
        val spinner: AutoCompleteTextView = itemView.findViewById(R.id.action_spinner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_special_flick_mapping, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        val label = when(item.direction){
            FlickDirection.TAP -> context.getString(com.kazumaproject.custom_keyboard.R.string.tap)
            FlickDirection.UP_LEFT_FAR -> context.getString(com.kazumaproject.custom_keyboard.R.string.flick_left)
            FlickDirection.UP_LEFT -> context.getString(com.kazumaproject.custom_keyboard.R.string.flick_left)
            FlickDirection.UP -> context.getString(com.kazumaproject.custom_keyboard.R.string.flick_top)
            FlickDirection.UP_RIGHT -> context.getString(com.kazumaproject.custom_keyboard.R.string.flick_right)
            FlickDirection.UP_RIGHT_FAR -> context.getString(com.kazumaproject.custom_keyboard.R.string.flick_right)
            FlickDirection.DOWN -> context.getString(com.kazumaproject.custom_keyboard.R.string.flick_bottom)
        }

        holder.textDirection.text = label

        val names = mutableListOf<String>().apply {
            add("") // empty = no action
            addAll(displayActions.map { it.displayName })
        }

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )
        holder.spinner.setAdapter(adapter)

        val currentName = item.action?.let { act ->
            displayActions.firstOrNull { it.action == act }?.displayName
        }.orEmpty()
        holder.spinner.setText(currentName, false)

        holder.spinner.setOnItemClickListener { _, _, idx, _ ->
            val selectedAction = if (idx == 0) null else displayActions[idx - 1].action
            onItemUpdated(item.copy(action = selectedAction))
        }
    }
}
