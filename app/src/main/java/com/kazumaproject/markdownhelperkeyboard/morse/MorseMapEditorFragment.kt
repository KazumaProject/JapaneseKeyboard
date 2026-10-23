package com.kazumaproject.markdownhelperkeyboard.morse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.morse_keyboard.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MorseMapEditorFragment : Fragment() {
    @Inject lateinit var romajiRepository: RomajiMapRepository
    private lateinit var map: MorseMap
    private val entries = mutableListOf<MorseEntry>()
    private lateinit var name: EditText
    private lateinit var signal: EditText
    private lateinit var value: EditText
    private lateinit var type: Spinner
    private lateinit var command: Spinner
    private lateinit var assignments: LinearLayout
    private lateinit var preview: MorseKeyboardView
    private lateinit var previewText: TextView
    private var editingSignal: String? = null
    private var converter: RomajiKanaConverter? = null
    private val commandLabels = listOf(
        R.string.morse_command_delete, R.string.morse_command_convert, R.string.morse_command_commit,
        R.string.morse_command_space, R.string.morse_command_enter, R.string.morse_command_cursor_left,
        R.string.morse_command_cursor_right, R.string.morse_command_next_map, R.string.morse_command_next_keyboard,
        R.string.morse_command_dakuten, R.string.morse_command_handakuten, R.string.morse_command_small_kana
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = MorsePreferences(requireContext())
        map = savedInstanceState?.getString("draft")?.let { MorseMapCodec.decode(it).single() } ?: run {
            val existing = store.allMaps().firstOrNull { it.id == arguments?.getString("mapId") }
            val base = store.allMaps().firstOrNull { it.id == arguments?.getString("baseId") }
            existing?.takeUnless { it.builtIn } ?: MorseMap(UUID.randomUUID().toString(),
                base?.let { "${it.name} · ${getString(R.string.morse_copy)}" }.orEmpty(), base?.entries.orEmpty())
        }
        entries += map.entries
        lifecycleScope.launch {
            val active = romajiRepository.getActiveMap().first()
            converter = RomajiKanaConverter(active?.mapData ?: romajiRepository.getDefaultMapData())
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 16) }
        fun edit(title: Int) = EditText(context).apply { hint = getString(title); body.addView(this, LinearLayout.LayoutParams(-1, -2)) }
        fun button(title: Int, action: () -> Unit) = body.addView(Button(context).apply { setText(title); setOnClickListener { action() } })
        name = edit(R.string.morse_name).apply { setText(savedInstanceState?.getString("name") ?: map.name) }
        button(R.string.morse_save) {
            runCatching { MorsePreferences(context).saveMap(draft()); findNavController().popBackStack() }
                .onFailure { message(R.string.morse_invalid) }
        }
        signal = edit(R.string.morse_signal).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        val input = LinearLayout(context)
        listOf("・" to ".", "－" to "-", "⌫" to "").forEach { (label, symbol) ->
            input.addView(Button(context).apply { text = label; setOnClickListener {
                signal.setText(if (symbol.isEmpty()) signal.text.toString().dropLast(1) else signal.text.toString() + symbol)
            } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        body.addView(input)
        type = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.morse_reading, R.string.morse_romaji, R.string.morse_text, R.string.morse_command).map(::getString))
        }
        body.addView(type)
        value = edit(R.string.morse_value)
        command = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, commandLabels.map(::getString)) }
        body.addView(command)
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                value.visibility = if (position == 3) View.GONE else View.VISIBLE
                command.visibility = if (position == 3) View.VISIBLE else View.GONE
            }
        }
        button(R.string.morse_add_entry) {
            val pattern = signal.text.toString()
            if (entries.any { it.signal == pattern && it.signal != editingSignal }) { message(R.string.morse_duplicate); return@button }
            val output = when (type.selectedItemPosition) {
                0 -> MorseOutput.Reading(value.text.toString())
                1 -> MorseOutput.Romaji(value.text.toString())
                2 -> MorseOutput.LiteralText(value.text.toString())
                else -> MorseOutput.Command(MorseCommand.entries[command.selectedItemPosition])
            }
            val next = entries.filterNot { it.signal == editingSignal } + MorseEntry(pattern, output)
            runCatching { map.copy(name = name.text.toString().ifBlank { getString(R.string.morse_new_map) }, entries = next).validate() }.onSuccess {
                entries.clear(); entries += next; editingSignal = null
                signal.setText(""); value.setText(""); renderEntries(); updatePreview()
            }.onFailure { message(R.string.morse_invalid) }
        }
        body.addView(TextView(context).apply { setText(R.string.morse_preview_title); textSize = 19f })
        previewText = TextView(context).apply { textSize = 22f; setPadding(8, 8, 8, 8); minHeight = 56 }
        body.addView(previewText)
        preview = MorseKeyboardView(context)
        preview.onOutput = { output ->
            when (output) {
                is MorseOutput.Reading -> previewText.append(output.text)
                is MorseOutput.LiteralText -> previewText.append(output.text)
                is MorseOutput.Romaji -> {
                    val current = previewText.text.toString()
                    previewText.text = converter?.convertCustomLayout(current + output.text) ?: current + output.text
                }
                is MorseOutput.Command -> previewCommand(output.action)
            }
            true
        }
        preview.onCommand = ::previewCommand
        body.addView(preview, LinearLayout.LayoutParams(-1, (290 * resources.displayMetrics.density).toInt()))
        assignments = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(assignments)
        renderEntries(); updatePreview()
        savedInstanceState?.let {
            signal.setText(it.getString("signal")); value.setText(it.getString("value"))
            type.setSelection(it.getInt("type")); command.setSelection(it.getInt("command"))
            editingSignal = it.getString("editing"); previewText.text = it.getString("preview")
        }
        return ScrollView(context).apply { addView(body) }
    }
    private fun draft() = map.copy(name = if (::name.isInitialized) name.text.toString() else map.name, entries = entries.toList(), builtIn = false)
    private fun updatePreview() {
        val config = MorsePreferences(requireContext()).settings().copy(sound = false, vibration = false)
        preview.configure(config, listOf(map.copy(name = name.text.toString().ifBlank { getString(R.string.morse_preview_title) }, entries = entries.toList())), map.id)
    }
    private fun previewCommand(action: MorseCommand) {
        val current = previewText.text.toString()
        previewText.text = when (action) {
            MorseCommand.DELETE -> if (current.isEmpty()) current else current.substring(0, current.offsetByCodePoints(current.length, -1))
            MorseCommand.SPACE, MorseCommand.CONVERT -> "$current "
            MorseCommand.ENTER -> "$current\n"
            MorseCommand.DAKUTEN, MorseCommand.HANDAKUTEN, MorseCommand.SMALL_KANA -> MorseKanaModifier.apply(current, action) ?: current
            else -> current
        }
    }
    private fun renderEntries() {
        assignments.removeAllViews()
        entries.forEach { entry ->
            val row = LinearLayout(requireContext())
            val text = when (val output = entry.output) {
                is MorseOutput.Reading -> "${getString(R.string.morse_reading)}: ${output.text}"
                is MorseOutput.Romaji -> "${getString(R.string.morse_romaji)}: ${output.text}"
                is MorseOutput.LiteralText -> "${getString(R.string.morse_text)}: ${output.text}"
                is MorseOutput.Command -> getString(commandLabels[output.action.ordinal])
            }
            row.addView(Button(requireContext()).apply {
                this.text = "${entry.signal} → $text"; isAllCaps = false
                setOnClickListener {
                    editingSignal = entry.signal; signal.setText(entry.signal)
                    when (val output = entry.output) {
                        is MorseOutput.Reading -> { type.setSelection(0); value.setText(output.text) }
                        is MorseOutput.Romaji -> { type.setSelection(1); value.setText(output.text) }
                        is MorseOutput.LiteralText -> { type.setSelection(2); value.setText(output.text) }
                        is MorseOutput.Command -> { type.setSelection(3); command.setSelection(output.action.ordinal) }
                    }
                    signal.requestFocus()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(requireContext()).apply {
                setText(R.string.morse_delete); setOnClickListener { entries.remove(entry); renderEntries(); updatePreview() }
            }, LinearLayout.LayoutParams(-2, -2))
            assignments.addView(row)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draft", MorseMapCodec.encode(listOf(draft().copy(name = name.text.toString().ifBlank { getString(R.string.morse_new_map) }))))
        outState.putString("name", name.text.toString())
        outState.putString("signal", signal.text.toString()); outState.putString("value", value.text.toString())
        outState.putInt("type", type.selectedItemPosition); outState.putInt("command", command.selectedItemPosition)
        outState.putString("editing", editingSignal); outState.putString("preview", previewText.text.toString())
    }
    override fun onDestroyView() { preview.cancelInput(); super.onDestroyView() }
    private fun message(text: Int) = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
}
