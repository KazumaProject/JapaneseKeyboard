package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.custom_keyboard.data.QwertyKeyConfig
import com.kazumaproject.custom_keyboard.data.QwertyKeyLayer
import com.kazumaproject.custom_keyboard.data.QwertyPopupSlot
import com.kazumaproject.markdownhelperkeyboard.R

/** Keeps the whole draft in one immutable model while switching layers or grid cells. */
class QwertyKeyEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    var config = QwertyKeyConfig()
        private set
    var onChanged: (() -> Unit)? = null
    private var shifted = false
    private var binding = false
    private var selectedSlot = -1
    private val normalTab = RadioButton(context).apply { id = generateViewId(); setText(R.string.qwerty_layer_normal) }
    private val shiftTab = RadioButton(context).apply { id = generateViewId(); setText(R.string.qwerty_layer_shift) }
    private val tabs = RadioGroup(context).apply {
        orientation = HORIZONTAL
        addView(normalTab)
        addView(shiftTab)
        check(normalTab.id)
    }
    private val overrideShift = CheckBox(context).apply { setText(R.string.qwerty_custom_shift) }
    private val label = field(R.string.qwerty_key_label)
    private val output = field(R.string.qwerty_tap_output)
    private val preview = field(R.string.qwerty_preview_label)
    private val columns = field(R.string.qwerty_columns).apply { inputType = InputType.TYPE_CLASS_NUMBER }
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val selection = TextView(context)
    private val slotOutput = field(R.string.qwerty_candidate_output)
    private val slotLabel = field(R.string.qwerty_candidate_label)
    private val controls = LinearLayout(context).apply { orientation = VERTICAL }
    private val append = button(R.string.qwerty_add_slot) {
        if (layer().slots.size < QwertyKeyConfig.MAX_SLOTS) {
            val slots = layer().slots + QwertyPopupSlot()
            selectedSlot = slots.lastIndex
            updateLayer(layer().copy(slots = slots))
            render()
        }
    }
    private val before = button(R.string.qwerty_move_before) { moveSlot(-1) }
    private val after = button(R.string.qwerty_move_after) { moveSlot(1) }
    private val clear = button(R.string.qwerty_clear_slot) { changeSlot(QwertyPopupSlot()); render() }
    private val remove = button(R.string.qwerty_remove_slot) {
        val slots = layer().slots.toMutableList()
        if (selectedSlot in slots.indices) {
            slots.removeAt(selectedSlot)
            selectedSlot = selectedSlot.coerceAtMost(slots.lastIndex)
            updateLayer(layer().copy(slots = slots))
            render()
        }
    }
    val isInputValid: Boolean get() = config.isValid() && columns.text.toString().toIntOrNull() in 1..QwertyKeyConfig.MAX_COLUMNS

    init {
        orientation = VERTICAL
        addView(tabs)
        addView(overrideShift)
        listOf(label, output, preview, columns).forEach { addView(it.parent.parent as TextInputLayout) }
        addView(TextView(context).apply { setText(R.string.qwerty_grid_help) })
        addView(grid)
        addView(append)
        addView(selection)
        controls.addView(slotOutput.parent.parent as TextInputLayout)
        controls.addView(slotLabel.parent.parent as TextInputLayout)
        listOf(before, after, clear, remove).forEach(controls::addView)
        addView(controls)
        tabs.setOnCheckedChangeListener { _, id ->
            shifted = id == shiftTab.id
            selectedSlot = -1
            render()
        }
        overrideShift.setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                config = config.copy(shifted = if (checked) config.normal.uppercase() else null)
                render()
                onChanged?.invoke()
            }
        }
        listOf(label, output, preview, columns).forEach { edit ->
            edit.doAfterTextChanged {
                if (!binding) {
                    updateLayer(layer().copy(label = label.text.toString(), output = output.text.toString(),
                        preview = preview.text.toString(), columns = columns.text.toString().toIntOrNull()
                            ?.takeIf { it in 1..QwertyKeyConfig.MAX_COLUMNS } ?: layer().columns))
                    (columns.parent.parent as TextInputLayout).error = if (columns.text.toString().toIntOrNull() in 1..QwertyKeyConfig.MAX_COLUMNS)
                        null else context.getString(R.string.qwerty_columns_error, QwertyKeyConfig.MAX_COLUMNS)
                    renderGrid()
                }
            }
        }
        listOf(slotOutput, slotLabel).forEach { edit ->
            edit.doAfterTextChanged {
                if (!binding) {
                    changeSlot(QwertyPopupSlot(slotOutput.text.toString(), slotLabel.text.toString()))
                    renderGrid()
                }
            }
        }
        render()
    }

    fun setConfig(value: QwertyKeyConfig) {
        config = value
        selectedSlot = -1
        render()
        onChanged?.invoke()
    }

    private fun layer(): QwertyKeyLayer = if (shifted) config.shifted ?: config.normal.uppercase() else config.normal
    private fun editable() = !shifted || config.shifted != null
    private fun updateLayer(value: QwertyKeyLayer) {
        if (!editable()) return
        config = if (shifted) config.copy(shifted = value) else config.copy(normal = value)
        onChanged?.invoke()
    }
    private fun changeSlot(value: QwertyPopupSlot) {
        val slots = layer().slots.toMutableList()
        if (selectedSlot !in slots.indices) return
        slots[selectedSlot] = value
        updateLayer(layer().copy(slots = slots))
    }
    private fun moveSlot(delta: Int) {
        val slots = layer().slots.toMutableList()
        val destination = selectedSlot + delta
        if (selectedSlot !in slots.indices || destination !in slots.indices) return
        val previous = slots[selectedSlot]
        slots[selectedSlot] = slots[destination]
        slots[destination] = previous
        selectedSlot = destination
        updateLayer(layer().copy(slots = slots))
        render()
    }
    private fun render() {
        binding = true
        val value = layer()
        overrideShift.visibility = if (shifted) VISIBLE else GONE
        overrideShift.isChecked = config.shifted != null
        label.setText(value.label)
        output.setText(value.output)
        preview.setText(value.preview)
        columns.setText(value.columns.toString())
        listOf(label, output, preview, columns).forEach { it.isEnabled = editable() }
        append.isEnabled = editable() && value.slots.size < QwertyKeyConfig.MAX_SLOTS
        val slot = value.slots.getOrNull(selectedSlot)
        selection.text = if (slot == null) "" else context.getString(R.string.qwerty_selected_slot,
            selectedSlot / value.columns + 1, selectedSlot % value.columns + 1)
        controls.visibility = if (slot == null) GONE else VISIBLE
        slotOutput.setText(slot?.output.orEmpty())
        slotLabel.setText(slot?.label.orEmpty())
        listOf(slotOutput, slotLabel, before, after, clear, remove).forEach { it.isEnabled = editable() }
        before.isEnabled = editable() && selectedSlot > 0
        after.isEnabled = editable() && selectedSlot < value.slots.lastIndex
        renderGrid()
        binding = false
    }
    private fun renderGrid() {
        grid.removeAllViews()
        val value = layer()
        val rows = value.slots.chunked(value.columns)
        rows.forEachIndexed { rowIndex, slots ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            repeat(value.columns) { col ->
                val index = rowIndex * value.columns + col
                val slot = slots.getOrNull(col)
                row.addView(MaterialButton(context).apply {
                    text = if (slot == null || slot.output.isEmpty()) context.getString(R.string.qwerty_empty_slot) else slot.displayLabel
                    maxLines = 2
                    minWidth = 0
                    minimumWidth = 0
                    contentDescription = context.getString(R.string.qwerty_selected_slot, rowIndex + 1, col + 1) + ": " + text
                    isEnabled = slot != null
                    isCheckable = true
                    isChecked = index == selectedSlot
                    setOnClickListener { selectedSlot = index; render() }
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }
            grid.addView(row)
        }
    }
    private fun field(hintRes: Int): EditText {
        val layout = TextInputLayout(context).apply { hint = context.getString(hintRes) }
        return com.google.android.material.textfield.TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(QwertyKeyConfig.MAX_TEXT_LENGTH))
            layout.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
    private fun button(textRes: Int, action: () -> Unit) = MaterialButton(context).apply {
        setText(textRes)
        setOnClickListener { action() }
    }
}
