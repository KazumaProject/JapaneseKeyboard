package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.roundToInt

internal class SpecialKeyActionDropdownAdapter(
    context: Context,
    private val options: List<SpecialKeyActionOption>
) : ArrayAdapter<String>(
    context,
    android.R.layout.simple_spinner_dropdown_item,
    options.map { it.label }
) {
    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = options.getOrNull(position)?.isHeader == false

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int = if (options[position].isHeader) 1 else 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (!options[position].isHeader) {
            return indentActionView(super.getView(position, convertView, parent))
        }
        return subheaderView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (!options[position].isHeader) {
            return indentActionView(super.getDropDownView(position, convertView, parent))
        }
        return subheaderView(position, convertView, parent)
    }

    private fun indentActionView(view: View): View = view.apply {
        val startPadding = (24 * context.resources.displayMetrics.density).roundToInt()
        setPaddingRelative(startPadding, paddingTop, paddingEnd, paddingBottom)
    }

    private fun subheaderView(position: Int, convertView: View?, parent: ViewGroup): View {
        val header = convertView as? MaterialTextView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_special_key_action_subheader, parent, false) as MaterialTextView
        header.text = options[position].label
        return header
    }
}
