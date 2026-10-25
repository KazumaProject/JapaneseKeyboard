package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
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
        setPadding(dp(4), dp(4), dp(4), dp(4))
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
            textSize = 14f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dp(4), dp(10), dp(4), dp(12))
        })
        if (state.path.isNotEmpty()) {
            addView(MaterialButton(context).apply {
                text = context.getString(R.string.three_stage_back)
                isAllCaps = false
                cornerRadius = dp(10)
                insetTop = 0
                insetBottom = dp(4)
                setPadding(dp(12), 0, dp(12), 0)
                val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
                backgroundTintList = ColorStateList.valueOf(surface)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                strokeColor = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                elevation = 0f
                setOnClickListener { state.back(); render() }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)))
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
                        is TfbiFlickNode.SubMenu -> (node.label ?: "") + "  ›"
                        else -> "—"
                    }
                    addView(MaterialButton(context).apply {
                        val directionLabel = FlickDirectionMapper.toDisplayName(direction, context)
                        val directionColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
                        text = SpannableString("$directionLabel\n$output").apply {
                            setSpan(RelativeSizeSpan(.72f), 0, directionLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(ForegroundColorSpan(directionColor), 0, directionLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (node != null) setSpan(StyleSpan(Typeface.BOLD), directionLabel.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        isAllCaps = false
                        minWidth = 0
                        maxLines = 3
                        textSize = 14f
                        insetTop = dp(3)
                        insetBottom = dp(3)
                        cornerRadius = dp(12)
                        strokeWidth = dp(1)
                        shapeAppearanceModel = ShapeAppearanceModel.builder()
                            .setAllCorners(CornerFamily.ROUNDED, dp(12).toFloat()).build()
                        val assigned = node != null
                        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
                        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
                        val primaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer)
                        val onPrimaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer)
                        backgroundTintList = ColorStateList.valueOf(if (assigned) primaryContainer else surface)
                        setTextColor(if (assigned) onPrimaryContainer else outline)
                        strokeColor = ColorStateList.valueOf(if (assigned) primaryContainer else outline)
                        elevation = 0f
                        contentDescription = "$directionLabel, ${if (node == null) context.getString(R.string.three_stage_unassigned) else output}"
                        setOnClickListener { editSlot(direction) }
                    }, LayoutParams(0, dp(76), 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    })
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
