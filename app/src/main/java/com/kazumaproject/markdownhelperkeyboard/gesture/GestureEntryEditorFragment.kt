package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import java.util.UUID

class GestureEntryEditorFragment : GestureScreenFragment() {
    private val samples = mutableListOf<List<GesturePoint>>()
    private var outputType = 0
    private var outputText = ""
    private var command: KeyAction = KeyAction.Delete
    private var replaceNext = false
    private var replaceSelected = false
    private var selectedSample = 0
    private val undo = java.util.ArrayDeque<List<List<GesturePoint>>>()
    private lateinit var sampleSelector: Spinner
    private fun remember() { if (undo.size == 20) undo.removeFirst(); undo.addLast(samples.toList()) }
    private var entryId = ""
    private lateinit var pad: GesturePadView
    private lateinit var count: android.widget.TextView
    override fun build(state: Bundle?) {
        val set = store.sets().firstOrNull { it.id == arguments?.getString("setId") }
        if (set == null || set.builtIn) { text(t("編集できない文字セットです", "This set cannot be edited")); return }
        val original = set.entries.firstOrNull { it.id == arguments?.getString("entryId") }
        entryId = state?.getString("entryId") ?: original?.id ?: UUID.randomUUID().toString()
        if (state != null) {
            outputType = state.getInt("type"); outputText = state.getString("text").orEmpty()
            command = requireNotNull(KeyActionMapper.toKeyAction(state.getString("command", "Delete")))
            replaceNext = state.getBoolean("replace")
            replaceSelected = state.getBoolean("replaceSelected"); selectedSample = state.getInt("selected")
            repeat(state.getInt("samples")) { i -> state.getFloatArray("sample_" + i)?.let { values ->
                samples.add(values.toList().chunked(2).map { GesturePoint(it[0], it[1]) })
            } }
        } else if (original != null) {
            samples.addAll(original.samples)
            when (val output = original.output) {
                is GestureOutput.Reading -> { outputType = 0; outputText = output.text }
                is GestureOutput.LiteralText -> { outputType = 1; outputText = output.text }
                is GestureOutput.Romaji -> error("Unexpected runtime output")
                is GestureOutput.Command -> { outputType = 2; command = output.action }
            }
        }
        if (state == null) arguments?.getFloatArray("practiceSample")?.let { coordinates ->
            if (samples.size < 16 && coordinates.size >= 4) {
                samples.add(coordinates.toList().chunked(2).map { GesturePoint(it[0], it[1]) })
                selectedSample = samples.lastIndex
            }
        }
        text(t("一筆で描くと見本を追加します。●は始点、矢印は方向です。", "Draw one stroke to add a sample. Dot: start. Arrow: direction."))
        val types = Spinner(requireContext())
        types.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf(t("かな1文字（変換可能）", "Kana character (composing)"), t("英数字・記号1文字（直接入力）", "Letter / digit / symbol (literal)"), t("基本操作", "Basic command")))
        types.setSelection(outputType); content.addView(types)
        val output = EditText(requireContext()).apply {
            hint = t("割り当てる1文字", "One output character"); setSingleLine(); setText(outputText)
            doAfterTextChanged { outputText = it.toString() }
        }
        content.addView(output)
        val commands = android.widget.Button(requireContext()).apply {
            isAllCaps = false
            text = GestureLabels.command(command, requireContext())
            setOnClickListener { showGestureActionPicker { action -> command = action; text = GestureLabels.command(action, requireContext()) } }
        }
        content.addView(commands)
        types.onItemSelectedListener = selected {
            outputType = it; output.visibility = if (it == 2) View.GONE else View.VISIBLE
            commands.visibility = if (it == 2) View.VISIBLE else View.GONE
        }
        count = text("")
        sampleSelector = Spinner(requireContext())
        sampleSelector.onItemSelectedListener = selected { index ->
            selectedSample = index
            if (::pad.isInitialized && !replaceNext && !replaceSelected) pad.guide = samples.getOrNull(index)
        }
        content.addView(sampleSelector)
        pad = GesturePadView(requireContext()).apply {
            setBackgroundColor(0xffe8edf6.toInt())
            onStroke = { points ->
                if (replaceNext || replaceSelected || samples.size < 16) {
                    remember()
                    when {
                        replaceNext -> { samples.clear(); samples.add(points); selectedSample = 0 }
                        replaceSelected && samples.isNotEmpty() -> samples[selectedSample] = points
                        else -> { samples.add(points); selectedSample = samples.lastIndex }
                    }
                    replaceNext = false; replaceSelected = false
                } else attempt { error(t("見本は16個まで登録できます", "Up to 16 samples")) }
                refresh()
            }
        }
        content.addView(pad, LinearLayout.LayoutParams(-1, dp(220)))
        button(t("すべての見本を再録（次の一筆で置換）", "Replace all samples with the next stroke")) { replaceNext = true; replaceSelected = false; pad.guide = null; refresh() }
        button(t("選択した見本を再録", "Replace selected sample")) {
            if (samples.isNotEmpty()) { replaceSelected = true; replaceNext = false; pad.guide = null; refresh() }
        }
        button(t("選択した見本を削除", "Remove selected sample")) {
            if (samples.isNotEmpty()) { remember(); samples.removeAt(selectedSample); selectedSample = selectedSample.coerceAtMost((samples.size - 1).coerceAtLeast(0)); refresh() }
        }
        button(t("最後の見本を削除", "Remove last sample")) {
            if (samples.isNotEmpty()) { remember(); samples.removeAt(samples.lastIndex); selectedSample = (samples.size - 1).coerceAtLeast(0); refresh() }
        }
        button(t("見本の変更を取り消す", "Undo sample change")) {
            if (undo.isNotEmpty()) { samples.clear(); samples.addAll(undo.removeLast()); selectedSample = (samples.size - 1).coerceAtLeast(0); replaceNext = false; replaceSelected = false; refresh() }
        }
        button(t("保存", "Save")) {
            val entry = GestureEntry(entryId, when (outputType) {
                0 -> GestureOutput.Reading(outputText)
                1 -> GestureOutput.LiteralText(outputText)
                else -> GestureOutput.Command(command)
            }, samples.toList())
            if (!attempt { entry.validate() }) return@button
            val current = store.sets().firstOrNull { it.id == set.id && !it.builtIn } ?: return@button
            val other = current.entries.filterNot { it.id == entryId }
            val collisions = gestureConflicts(current, entry, store.maxDistance())
            fun save() { if (attempt { store.saveSet(current.copy(entries = other + entry)) }) findNavController().popBackStack() }
            if (collisions.isEmpty()) save()
            else {
                val comparison = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                comparison.addView(android.widget.TextView(requireContext()).apply {
                    text = t("似た線は候補選択になります。見本を比較して保存してください。", "Similar strokes require choosing a match. Compare the samples before saving.")
                })
                fun example(label: String, points: List<GesturePoint>) {
                    comparison.addView(android.widget.TextView(requireContext()).apply { text = label })
                    comparison.addView(GesturePadView(requireContext()).apply {
                        guide = points; captureAllowed = { false }; inkColor = android.graphics.Color.DKGRAY
                    }, LinearLayout.LayoutParams(-1, dp(110)))
                }
                example(t("登録する線", "New sample"), entry.samples[selectedSample.coerceAtMost(entry.samples.lastIndex)])
                collisions.take(3).forEach { example(GestureLabels.output(it.entry.output, requireContext()), it.entry.samples.first()) }
                AlertDialog.Builder(requireContext()).setTitle(t("似たジェスチャーがあります", "Similar gesture"))
                    .setView(android.widget.ScrollView(requireContext()).apply { addView(comparison) })
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok) { _, _ -> save() }.show()
            }
        }
        refresh()
    }
    private fun refresh() {
        count.text = samples.size.toString() + t(" 見本", " samples") + when { replaceNext -> t(" · 次の一筆で全置換", " · next stroke replaces all"); replaceSelected -> t(" · 次の一筆で選択を置換", " · next stroke replaces selected"); else -> "" }
        selectedSample = selectedSample.coerceIn(0, (samples.size - 1).coerceAtLeast(0))
        sampleSelector.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            samples.indices.map { t("見本 ", "Sample ") + (it + 1) })
        sampleSelector.setSelection(selectedSample)
        pad.guide = if (replaceNext || replaceSelected) null else samples.getOrNull(selectedSample)
    }
    private fun selected(action: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { action(position) }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("entryId", entryId); outState.putInt("type", outputType); outState.putString("text", outputText)
        outState.putString("command", KeyActionMapper.fromKeyAction(command)); outState.putBoolean("replace", replaceNext); outState.putBoolean("replaceSelected", replaceSelected); outState.putInt("selected", selectedSample); outState.putInt("samples", samples.size)
        samples.forEachIndexed { i, points -> outState.putFloatArray("sample_" + i, points.flatMap { listOf(it.x, it.y) }.toFloatArray()) }
    }
    override fun onDestroyView() { if (::pad.isInitialized) pad.cancelStroke(); samples.clear(); super.onDestroyView() }
}
