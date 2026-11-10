package com.kazumaproject.markdownhelperkeyboard.morse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.morse_keyboard.MorseControl
import com.kazumaproject.morse_keyboard.MorseControlLayout
import com.kazumaproject.morse_keyboard.MorseControlPlacement
import com.kazumaproject.morse_keyboard.MorseKeyboardView

/** Changes are saved immediately and use the same view as the running IME. */
class MorseLayoutFragment : Fragment() {
    private lateinit var preview: MorseKeyboardView
    private lateinit var selectedLabel: TextView
    private lateinit var remove: MaterialButton
    private lateinit var reverse: MaterialSwitch
    private lateinit var placement: MaterialButton
    private var selected = MorseControl.BOUNDARY
    private val store get() = MorsePreferences(requireContext())
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(key: MorseControl) = getString(key.labelResource).replace('\n', ' ')

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        selected = state?.getString("selected")?.let { runCatching { MorseControl.valueOf(it) }.getOrNull() } ?: MorseControl.BOUNDARY
        val context = requireContext()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(24)) }
        body.addView(TextView(context).apply { setText(R.string.morse_layout_editor_help); textSize = 15f })
        preview = MorseKeyboardView(context).apply {
            inputAllowed = { false }
            onControlSelected = { selected = it; render() }
            onControlMove = { source, group ->
                val layout = store.controlLayout()
                val groups = if (layout.placement == MorseControlPlacement.BOTH_SIDES && layout.rows.size == 1)
                    layout.rows.single().chunked((layout.rows.single().size + 1) / 2) else layout.rows
                val rows = groups.map { (it - source).toMutableList() }.toMutableList()
                while (rows.size <= group) rows.add(mutableListOf())
                if (rows[group].size < 6) {
                    rows[group].add(source); selected = source
                    save(layout.copy(rows = rows.filter { it.isNotEmpty() }))
                }
            }
            onControlSwap = { source, target ->
                val layout = store.controlLayout()
                selected = source
                save(layout.copy(rows = layout.rows.map { row -> row.map {
                    when (it) { source -> target; target -> source; else -> it }
                } }))
            }
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        selectedLabel = TextView(context).apply { textSize = 19f }
        body.addView(selectedLabel)
        remove = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { setText(R.string.morse_hide_key); setOnClickListener {
            val layout = store.controlLayout()
            save(layout.copy(rows = layout.rows.map { it - selected }.filter { it.isNotEmpty() }))
            selected = MorseControl.BOUNDARY; render()
        } }
        body.addView(remove)
        body.addView(MaterialButton(context).apply { setText(R.string.morse_add_key); setOnClickListener {
            val layout = store.controlLayout()
            val unused = MorseControl.entries.filter { it !in layout.rows.flatten() }
            if (unused.isEmpty() || layout.rows.sumOf { it.size } == 12) { full(); return@setOnClickListener }
            AlertDialog.Builder(context).setTitle(R.string.morse_add_key).setItems(unused.map(::label).toTypedArray()) { _, index ->
                val rows = layout.rows.map { it.toMutableList() }.toMutableList()
                var target = rows.indexOfLast { it.size < 6 }
                if (target < 0) { rows.add(mutableListOf()); target = rows.lastIndex }
                selected = unused[index]; rows[target].add(selected)
                save(layout.copy(rows = rows))
            }.show()
        } })
        reverse = MaterialSwitch(context).apply { setText(R.string.morse_reverse_title); setOnCheckedChangeListener { _, checked ->
            store.saveReverse(checked); render()
        } }
        placement = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { isAllCaps = false; setOnClickListener {
            val options = resources.getStringArray(R.array.morse_control_placements)
            AlertDialog.Builder(context).setTitle(R.string.morse_control_placement)
                .setSingleChoiceItems(options, store.controlLayout().placement.ordinal) { dialog, index ->
                    val position = MorseControlPlacement.entries[index]
                    val layout = store.controlLayout()
                    val rows = if (position == MorseControlPlacement.BOTH_SIDES && layout.rows.size == 1)
                        layout.rows.single().chunked((layout.rows.single().size + 1) / 2) else layout.rows
                    dialog.dismiss()
                    save(layout.copy(rows = rows, abovePads = position == MorseControlPlacement.TOP, placement = position))
                }.show()
        } }
        body.addView(reverse, LinearLayout.LayoutParams(-1,dp(56)))
        body.addView(placement, LinearLayout.LayoutParams(-1,dp(56)))
        body.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { setText(R.string.morse_reset_layout); setOnClickListener {
            selected = MorseControl.BOUNDARY
            store.saveReverse(false); save(MorseControlLayout())
        } })
        render()
        return ScrollView(context).apply { addView(body) }
    }
    private fun save(layout: MorseControlLayout) { store.saveControlLayout(layout); render() }
    private fun render() {
        val config = store.settings().copy(sound = false, vibration = false)
        val maps = store.enabledMaps()
        preview.configure(config, maps, store.selectedMapId)
        preview.highlightControl = selected
        preview.layoutParams.height = dp(if (config.controlLayout.placement.isSide) config.minimumHeightDp.coerceAtLeast(356)
            else (config.minimumHeightDp + if (config.controlLayout.rows.size == 1) 48 else 0).coerceAtLeast(260))
        preview.requestLayout()
        selectedLabel.text = getString(R.string.morse_selected_key, label(selected))
        remove.isEnabled = !selected.required
        reverse.isChecked = config.reverse
        placement.text = getString(R.string.morse_control_placement_value,
            resources.getStringArray(R.array.morse_control_placements)[config.controlLayout.placement.ordinal])
    }
    private fun full() = Toast.makeText(requireContext(), R.string.morse_layout_full, Toast.LENGTH_SHORT).show()
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("selected", selected.name) }
    override fun onDestroyView() { preview.cancelInput(); super.onDestroyView() }
}
