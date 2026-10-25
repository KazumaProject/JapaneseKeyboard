package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper

class HierarchicalFlickEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private var state = HierarchicalFlickEditorState(TfbiFlickNode.StatefulKey(emptyMap(), label = ""))
    var onDefinitionChanged: (() -> Unit)? = null
    val definition: TfbiFlickNode.StatefulKey get() = state.definition

    init { orientation = VERTICAL }

    fun setDefinition(value: TfbiFlickNode.StatefulKey) {
        state = HierarchicalFlickEditorState(value)
        render()
    }

    fun hasOutput(): Boolean = state.hasNormalOutput()

    private fun render() {
        removeAllViews()
        val modes = state.availableModes
        if (modes.size > 1) {
            addView(Spinner(context).apply {
                contentDescription = context.getString(R.string.three_stage_state)
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    modes.map { context.getString(when (it) {
                        KeyMode.NORMAL -> R.string.three_stage_normal
                        KeyMode.DAKUTEN -> R.string.three_stage_dakuten
                        KeyMode.HANDAKUTEN -> R.string.three_stage_handakuten
                    }) })
                setSelection(modes.indexOf(state.mode))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (parent?.parent === this@HierarchicalFlickEditorView && state.mode != modes[position]) {
                            state.selectMode(modes[position])
                            render()
                        }
                    }
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }
        addView(TextView(context).apply {
            text = context.getString(R.string.three_stage_path, state.path.size + 1,
                state.path.joinToString(" → ") { FlickDirectionMapper.toDisplayName(it, context) })
            setPadding(0, dp(8), 0, dp(8))
        })
        if (state.path.isNotEmpty()) {
            addView(MaterialButton(context).apply {
                text = context.getString(R.string.three_stage_back)
                setOnClickListener { state.back(); render() }
            })
        }
        val directions = listOf(
            TfbiFlickDirection.UP_LEFT, TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT,
            TfbiFlickDirection.LEFT, TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT,
            TfbiFlickDirection.DOWN_LEFT, TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT
        )
        val map = state.currentMap()
        directions.chunked(3).forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { direction ->
                    val node = map[direction]
                    val output = when (node) {
                        is TfbiFlickNode.Input -> node.char
                        is TfbiFlickNode.SubMenu -> (node.label ?: "") + " ›"
                        else -> context.getString(R.string.three_stage_unassigned)
                    }
                    addView(MaterialButton(context).apply {
                        text = FlickDirectionMapper.toDisplayName(direction, context) + "\n" + output
                        isAllCaps = false
                        minWidth = 0
                        maxLines = 3
                        contentDescription = text
                        setOnClickListener { editSlot(direction) }
                    }, LayoutParams(0, dp(88), 1f))
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun editSlot(direction: TfbiFlickDirection) {
        val node = state.currentMap()[direction]
        val choices = mutableListOf<Pair<Int, () -> Unit>>()
        if (node is TfbiFlickNode.SubMenu) {
            choices += R.string.three_stage_open to { state.open(direction); render() }
            choices += R.string.three_stage_branch_label to {
                editText(node.label.orEmpty(), R.string.three_stage_branch_label) { state.setBranchLabel(direction, it); changed() }
            }
        }
        choices += R.string.three_stage_output to {
            editText((node as? TfbiFlickNode.Input)?.char.orEmpty()) {
                state.setOutput(direction, it); changed()
            }
        }
        if (node !is TfbiFlickNode.SubMenu && direction != TfbiFlickDirection.TAP && direction != state.path.lastOrNull() && state.path.size < 2) {
            choices += R.string.three_stage_add_branch to {
                state.makeBranch(direction)
                state.open(direction)
                changed()
            }
        }
        if (node != null) choices += R.string.three_stage_clear to { state.delete(direction); changed() }
        AlertDialog.Builder(context)
            .setTitle(FlickDirectionMapper.toDisplayName(direction, context))
            .setItems(choices.map { context.getString(it.first) }.toTypedArray()) { _, index -> choices[index].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editText(value: String, titleRes: Int = R.string.three_stage_output, save: (String) -> Unit) {
        val input = TextInputEditText(context).apply { setText(value); setSelection(value.length) }
        val layout = TextInputLayout(context).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ -> save(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun changed() { render(); onDefinitionChanged?.invoke() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
