package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.markdownhelperkeyboard.R

/** Inspect every saved map without selecting it; Try runs the existing gesture engine. */
class FreeFlickLayoutPreviewDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = runCatching { FreeFlickLayoutRepository(requireContext()).find(requireArguments().getString(FreeFlickLayoutRepository.ARG_LAYOUT_ID)!!) }.getOrNull()
            ?: return MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.free_flick_layout_operation_error).setPositiveButton(android.R.string.ok, null).create()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(8)) }
        val keyPicker = Spinner(requireContext()).apply { contentDescription = getString(R.string.popup_key) }
        val mapPicker = Spinner(requireContext()).apply { contentDescription = getString(R.string.popup_map) }
        val result = TextView(requireContext()).apply { textSize = 15f; setPadding(0, dp(6), 0, dp(6)) }
        val guide = PopupMapGuideView(requireContext()).apply { sourceKeyWidth = 100f; sourceKeyHeight = 64f; tapActive = false }
        val trial = PopupMapEditorCanvas(requireContext()).apply { inputOptions = FreeFlickPreferences.read(requireContext()); visibility = View.GONE }
        val stage = FrameLayout(requireContext()).apply {
            addView(guide, FrameLayout.LayoutParams(-1, -1)); addView(trial, FrameLayout.LayoutParams(-1, -1))
        }
        val toggle = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        var keyIndex = savedInstanceState?.getInt("key")?.coerceIn(layout.keys.indices) ?: 0
        var mapIndex = savedInstanceState?.getInt("map") ?: 0
        var trying = savedInstanceState?.getBoolean("trying") ?: false
        var entered = ""
        var binding = false
        fun show() {
            val config = layout.keys[keyIndex]
            mapIndex = mapIndex.coerceIn(config.maps.indices)
            guide.config = config; guide.map = config.maps[mapIndex]; guide.invalidate()
            trial.showConfig(config, config.startMapId, null, true)
            guide.visibility = if (trying) View.GONE else View.VISIBLE
            trial.visibility = if (trying) View.VISIBLE else View.GONE
            mapPicker.isEnabled = !trying
            toggle.setText(if (trying) R.string.free_flick_layout_show else R.string.popup_try)
            result.text = getString(if (trying) R.string.popup_try_hint else R.string.free_flick_layout_preview_hint)
            requireArguments().putInt("key", keyIndex); requireArguments().putInt("map", mapIndex); requireArguments().putBoolean("trying", trying)
        }
        fun maps() {
            binding = true
            mapPicker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, layout.keys[keyIndex].maps.map { it.name })
            mapPicker.setSelection(mapIndex.coerceIn(layout.keys[keyIndex].maps.indices))
            binding = false; show()
        }
        keyPicker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, layout.keys.map { it.keyId })
        keyPicker.setSelection(keyIndex)
        keyPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == keyIndex) return
                keyIndex = position; mapIndex = 0; entered = ""; maps()
            }
        }
        mapPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding || position == mapIndex) return
                mapIndex = position; show()
            }
        }
        toggle.setOnClickListener { trying = !trying; entered = ""; show() }
        trial.onTrial = { text, session ->
            if (text != null) entered += text
            result.text = "${getString(R.string.popup_result)}：$entered\n" + session.mapPath.joinToString(" → ") { id -> layout.keys[keyIndex].map(id)?.name.orEmpty() }
        }
        body.addView(keyPicker); body.addView(mapPicker)
        body.addView(stage, LinearLayout.LayoutParams(-1, dp(240)))
        body.addView(result); body.addView(toggle)
        maps()
        return MaterialAlertDialogBuilder(requireContext()).setTitle(layout.name).setView(body).setPositiveButton(android.R.string.ok, null).create()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        for (key in listOf("key", "map")) outState.putInt(key, requireArguments().getInt(key))
        outState.putBoolean("trying", requireArguments().getBoolean("trying"))
        super.onSaveInstanceState(outState)
    }
    companion object {
        fun create(id: String) = FreeFlickLayoutPreviewDialog().apply { arguments = bundleOf(FreeFlickLayoutRepository.ARG_LAYOUT_ID to id) }
    }
}
