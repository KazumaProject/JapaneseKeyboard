package com.kazumaproject.markdownhelperkeyboard.morse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.morse_keyboard.MorseMap

class MorseMapListFragment : Fragment() {
    private lateinit var rows: LinearLayout
    private val store get() = MorsePreferences(requireContext())
    private var exportJson: String? = null
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = requireContext().contentResolver.openInputStream(uri)!!.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= 1_048_576)
                    output.write(buffer, 0, read)
                }
                output.toString("UTF-8")
            }
            store.importMaps(json)
            Toast.makeText(requireContext(), R.string.morse_imported, Toast.LENGTH_SHORT).show()
            render()
        }.onFailure { message(R.string.morse_invalid) }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = exportJson
        if (uri != null && json != null) runCatching {
            requireContext().contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(json) }
        }.onFailure { message(R.string.morse_invalid) }
        exportJson = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportJson = savedInstanceState?.getString("export")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putString("export", exportJson)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        body.addView(TextView(context).apply { setText(R.string.morse_map_help); textSize = 15f })
        fun action(title: Int, block: () -> Unit) = body.addView(Button(context).apply { setText(title); setOnClickListener { block() } })
        action(R.string.morse_new_map) {
            val maps = store.allMaps()
            AlertDialog.Builder(context).setTitle(R.string.morse_new_map)
                .setItems((listOf(getString(R.string.morse_empty_map)) + maps.map { it.name }).toTypedArray()) { _, index ->
                    edit(null, maps.getOrNull(index - 1)?.id)
                }.show()
        }
        action(R.string.morse_import) { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        action(R.string.morse_export) {
            exportJson = MorseMapCodec.encode(store.allMaps())
            exportFile.launch("morse-maps.json")
        }
        rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rows)
        return ScrollView(context).apply { addView(body) }
    }
    override fun onResume() { super.onResume(); if (::rows.isInitialized) render() }
    private fun edit(id: String?, base: String? = null) {
        findNavController().navigate(R.id.morseMapEditorFragment, bundleOf("mapId" to id.orEmpty(), "baseId" to base.orEmpty()))
    }
    private fun render() {
        rows.removeAllViews()
        val maps = store.allMaps()
        val enabled = store.enabledIds()
        maps.forEachIndexed { index, map ->
            val card = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
            card.addView(CheckBox(requireContext()).apply {
                text = map.name; textSize = 18f; isChecked = map.id in enabled
                setOnCheckedChangeListener { _, checked ->
                    val next = if (checked) enabled + map.id else enabled - map.id
                    if (next.isEmpty()) { message(R.string.morse_at_least_one); render() }
                    else { store.saveOrder(maps.map { it.id }, next); render() }
                }
            })
            val buttons = LinearLayout(requireContext())
            fun button(text: String, enabled: Boolean = true, action: () -> Unit) {
                buttons.addView(Button(requireContext()).apply {
                    this.text = text; isEnabled = enabled; textSize = 12f; minimumWidth = 0; minWidth = 0
                    setPadding(4, 0, 4, 0); setOnClickListener { action() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            fun move(delta: Int) {
                val order = maps.map { it.id }.toMutableList()
                java.util.Collections.swap(order, index, index + delta)
                store.saveOrder(order, enabled); render()
            }
            button("↑", index > 0) { move(-1) }
            button("↓", index < maps.lastIndex) { move(1) }
            button(getString(if (map.builtIn) R.string.morse_copy else R.string.morse_edit)) {
                if (map.builtIn) edit(null, map.id) else edit(map.id)
            }
            if (!map.builtIn) button(getString(R.string.morse_delete)) {
                AlertDialog.Builder(requireContext()).setMessage(R.string.morse_delete_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.morse_delete) { _, _ -> store.deleteMap(map.id); render() }.show()
            }
            card.addView(buttons); rows.addView(card)
        }
    }
    private fun message(text: Int) = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
}
