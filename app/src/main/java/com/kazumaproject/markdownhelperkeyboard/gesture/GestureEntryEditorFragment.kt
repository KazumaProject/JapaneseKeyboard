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
import java.util.UUID

class GestureEntryEditorFragment : GestureScreenFragment() {
    private val samples = mutableListOf<List<GesturePoint>>()
    private var outputType = 0
    private var outputText = ""
    private var command = GestureCommand.DELETE
    private var replaceNext = false
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
            command = GestureCommand.valueOf(state.getString("command", GestureCommand.DELETE.name))
            replaceNext = state.getBoolean("replace")
            repeat(state.getInt("samples")) { i -> state.getFloatArray("sample_" + i)?.let { values ->
                samples.add(values.toList().chunked(2).map { GesturePoint(it[0], it[1]) })
            } }
        } else if (original != null) {
            samples.addAll(original.samples)
            when (val output = original.output) {
                is GestureOutput.Reading -> { outputType = 0; outputText = output.text }
                is GestureOutput.LiteralText -> { outputType = 1; outputText = output.text }
                is GestureOutput.Command -> { outputType = 2; command = output.action }
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
        val commands = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, GestureCommand.entries.map { GestureLabels.command(it, ja) })
            setSelection(command.ordinal)
            onItemSelectedListener = selected { command = GestureCommand.entries[it] }
        }
        content.addView(commands)
        types.onItemSelectedListener = selected {
            outputType = it; output.visibility = if (it == 2) View.GONE else View.VISIBLE
            commands.visibility = if (it == 2) View.VISIBLE else View.GONE
        }
        count = text("")
        pad = GesturePadView(requireContext()).apply {
            setBackgroundColor(0xffe8edf6.toInt())
            onStroke = { points ->
                if (replaceNext) { samples.clear(); replaceNext = false }
                if (samples.size < 16) samples.add(points)
                else attempt { error(t("見本は16個まで登録できます", "Up to 16 samples")) }
                refresh()
            }
        }
        content.addView(pad, LinearLayout.LayoutParams(-1, dp(220)))
        button(t("すべての見本を再録（次の一筆で置換）", "Replace all samples with the next stroke")) { replaceNext = true; pad.guide = null; refresh() }
        button(t("最後の見本を削除", "Remove last sample")) { if (samples.isNotEmpty()) samples.removeAt(samples.lastIndex); refresh() }
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
            else AlertDialog.Builder(requireContext()).setTitle(t("似たジェスチャーがあります", "Similar gesture"))
                .setMessage(collisions.map { GestureLabels.output(it.entry.output, ja) }.distinct().joinToString(", ") +
                    t("\n曖昧な線は入力されません。保存しますか？", "\nAmbiguous strokes will not be entered. Save anyway?"))
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok) { _, _ -> save() }.show()
        }
        refresh()
    }
    private fun refresh() {
        count.text = samples.size.toString() + t(" 見本", " samples") + if (replaceNext) t(" · 次の一筆で置換", " · next stroke replaces all") else ""
        pad.guide = if (replaceNext) null else samples.lastOrNull()
    }
    private fun selected(action: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { action(position) }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("entryId", entryId); outState.putInt("type", outputType); outState.putString("text", outputText)
        outState.putString("command", command.name); outState.putBoolean("replace", replaceNext); outState.putInt("samples", samples.size)
        samples.forEachIndexed { i, points -> outState.putFloatArray("sample_" + i, points.flatMap { listOf(it.x, it.y) }.toFloatArray()) }
    }
    override fun onDestroyView() { if (::pad.isInitialized) pad.cancelStroke(); samples.clear(); super.onDestroyView() }
}
