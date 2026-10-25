package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.databinding.ViewHierarchicalFlickEditorBinding

class HierarchicalFlickEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private var state = HierarchicalFlickEditorState(TfbiFlickNode.StatefulKey(emptyMap(), label = ""))
    private val binding = ViewHierarchicalFlickEditorBinding.inflate(LayoutInflater.from(context), this)
    private var selectedDirection = TfbiFlickDirection.TAP
    private var updatingInput = false
    var onDefinitionChanged: (() -> Unit)? = null
    val definition: TfbiFlickNode.StatefulKey get() = state.definition

    init {
        orientation = VERTICAL
        binding.hierarchicalGrid.onHierarchicalDirectionSelected = { direction ->
            selectedDirection = direction
            renderSelection()
        }
        binding.hierarchicalInput.doAfterTextChanged {
            if (!updatingInput) {
                val value = it.toString()
                if (state.currentMap()[selectedDirection] is TfbiFlickNode.SubMenu) {
                    state.setBranchLabel(selectedDirection, value)
                } else {
                    state.setOutput(selectedDirection, value)
                }
                renderGrid()
                onDefinitionChanged?.invoke()
            }
        }
        binding.hierarchicalBack.setOnClickListener {
            val parentDirection = state.path.lastOrNull() ?: return@setOnClickListener
            state.back()
            selectedDirection = parentDirection
            render()
        }
        binding.hierarchicalBranch.setOnClickListener {
            if (state.currentMap()[selectedDirection] !is TfbiFlickNode.SubMenu) {
                state.makeBranch(selectedDirection)
                onDefinitionChanged?.invoke()
            }
            state.open(selectedDirection)
            selectedDirection = TfbiFlickDirection.TAP
            render()
        }
        binding.hierarchicalClear.setOnClickListener {
            state.delete(selectedDirection)
            render()
            onDefinitionChanged?.invoke()
        }
    }

    fun setDefinition(value: TfbiFlickNode.StatefulKey) {
        state = HierarchicalFlickEditorState(value)
        selectedDirection = TfbiFlickDirection.TAP
        val modes = state.availableModes
        binding.hierarchicalMode.onItemSelectedListener = null
        binding.hierarchicalMode.isVisible = modes.size > 1
        binding.hierarchicalMode.adapter = ArrayAdapter(context,
            android.R.layout.simple_spinner_dropdown_item, modes.map {
                context.getString(when (it) {
                    KeyMode.NORMAL -> R.string.three_stage_normal
                    KeyMode.DAKUTEN -> R.string.three_stage_dakuten
                    KeyMode.HANDAKUTEN -> R.string.three_stage_handakuten
                })
            })
        binding.hierarchicalMode.setSelection(0)
        binding.hierarchicalMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (state.mode != modes[position]) {
                    state.selectMode(modes[position])
                    selectedDirection = TfbiFlickDirection.TAP
                    render()
                }
            }
        }
        render()
    }

    fun hasOutput(): Boolean = state.hasNormalOutput()

    private fun render() {
        binding.hierarchicalPath.text = context.getString(R.string.three_stage_path,
            state.path.size + 1, state.path.joinToString(" → ") {
                FlickDirectionMapper.toDisplayName(it, context)
            })
        binding.hierarchicalBack.isVisible = state.path.isNotEmpty()
        renderGrid()
        renderSelection()
    }

    private fun renderGrid() {
        binding.hierarchicalGrid.setHierarchicalContent(state.currentMap().mapValues { (_, node) ->
            when (node) {
                is TfbiFlickNode.Input -> node.char
                is TfbiFlickNode.SubMenu -> node.label.orEmpty() + " ›"
                else -> ""
            }
        }, selectedDirection)
    }

    private fun renderSelection() {
        val node = state.currentMap()[selectedDirection]
        val isBranch = node is TfbiFlickNode.SubMenu
        binding.hierarchicalSelectedDirection.text = context.getString(R.string.direction_action_label,
            FlickDirectionMapper.toDisplayName(selectedDirection, context))
        binding.hierarchicalInputLayout.hint = context.getString(
            if (isBranch) R.string.three_stage_branch_label else R.string.two_step_output_label)
        updatingInput = true
        binding.hierarchicalInput.setText(when (node) {
            is TfbiFlickNode.Input -> node.char
            is TfbiFlickNode.SubMenu -> node.label.orEmpty()
            else -> ""
        })
        binding.hierarchicalInput.setSelection(binding.hierarchicalInput.length())
        updatingInput = false
        binding.hierarchicalBranch.isVisible = isBranch || (
            selectedDirection != TfbiFlickDirection.TAP &&
                selectedDirection != state.path.lastOrNull() && state.path.size < 2)
        binding.hierarchicalBranch.setText(
            if (isBranch) R.string.three_stage_open else R.string.three_stage_add_branch)
        binding.hierarchicalClear.isVisible = isBranch
    }
}
