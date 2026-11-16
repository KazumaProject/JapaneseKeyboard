package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.custom_keyboard.data.QwertyKeyConfig
import com.kazumaproject.custom_keyboard.data.QwertyKeyLayer
import com.kazumaproject.custom_keyboard.data.QwertyPopupSlot
import com.kazumaproject.markdownhelperkeyboard.R
import com.google.android.material.R as MaterialR

/** The previews and their editors share one draft, including blank popup positions. */
class QwertyKeyEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    var config = QwertyKeyConfig()
        private set
    var onChanged: (() -> Unit)? = null
    private var shifted = false
    private var binding = false
    private var selectedSlot = -1
    private var detailsExpanded = false
    private val normalTab = RadioButton(context).apply { id = generateViewId(); setText(R.string.qwerty_layer_normal) }
    private val shiftTab = RadioButton(context).apply { id = generateViewId(); setText(R.string.qwerty_layer_shift) }
    private val tabs = RadioGroup(context).apply {
        orientation = HORIZONTAL
        addView(normalTab, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(shiftTab, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        check(normalTab.id)
    }
    private val overrideShift = CheckBox(context).apply { setText(R.string.qwerty_custom_shift) }
    private val shiftHelp = text(R.string.qwerty_shift_help)
    private val label = field(R.string.qwerty_key_label, optional = true)
    private val output = field(R.string.qwerty_tap_output)
    private val preview = field(R.string.qwerty_preview_label, optional = true)
    private val details = vertical().apply {
        listOf(output, label, preview).forEach { addView(it.container()) }
    }
    private val keyPreview = previewButton(false)
    private val pressPreview = previewButton(true)
    private val tapResult = text().apply { gravity = Gravity.CENTER }
    private val editKey = button(R.string.qwerty_edit_key) {
        detailsExpanded = !detailsExpanded
        renderDetails()
    }
    private val columns = field(R.string.qwerty_columns).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
        isSingleLine = true
    }
    private val fewerColumns = button(R.string.qwerty_fewer_columns) { changeColumns(-1) }.apply {
        contentDescription = context.getString(R.string.qwerty_fewer_columns_description)
    }
    private val moreColumns = button(R.string.qwerty_more_columns) { changeColumns(1) }.apply {
        contentDescription = context.getString(R.string.qwerty_more_columns_description)
    }
    private val grid = vertical().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
    private val gridScroll = HorizontalScrollView(context).apply {
        isFillViewport = true
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private val emptyGrid = text(R.string.qwerty_no_candidates)
    private val gridSurface = MaterialCardView(context).apply {
        cardElevation = 0f
        strokeWidth = 0
        radius = dp(12).toFloat()
        setCardBackgroundColor(MaterialColors.getColor(this, MaterialR.attr.colorSurfaceContainer))
        addView(gridScroll)
    }
    private val selection = text().apply { setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleSmall) }
    private val slotOutput = field(R.string.qwerty_candidate_output)
    private val slotLabel = field(R.string.qwerty_candidate_label, optional = true)
    private val slotResult = text()
    private val controls = vertical()
    private val append = button(R.string.qwerty_add_slot) {
        if (editable() && layer().slots.size < QwertyKeyConfig.MAX_SLOTS) {
            val slots = layer().slots + QwertyPopupSlot()
            selectedSlot = slots.lastIndex
            updateLayer(layer().copy(slots = slots))
            render()
            slotOutput.requestFocus()
        }
    }
    private val before = button(R.string.qwerty_move_before) { moveSlot(-1) }
    private val after = button(R.string.qwerty_move_after) { moveSlot(1) }
    private val clear = button(R.string.qwerty_clear_slot) { changeSlot(QwertyPopupSlot()); render() }
    private val remove = button(R.string.qwerty_remove_slot) {
        if (editable()) {
            val slots = layer().slots.toMutableList()
            if (selectedSlot in slots.indices) {
                slots.removeAt(selectedSlot)
                selectedSlot = selectedSlot.coerceAtMost(slots.lastIndex)
                updateLayer(layer().copy(slots = slots))
                render()
            }
        }
    }
    val isInputValid: Boolean get() = config.isValid() && validColumns()

    init {
        orientation = VERTICAL
        addView(tabs)
        addView(overrideShift)
        addView(shiftHelp)
        addView(heading(R.string.qwerty_key_preview_title))
        val previews = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            listOf(R.string.qwerty_key_appearance to keyPreview,
                R.string.qwerty_press_appearance to pressPreview).forEach { (title, button) ->
                addView(vertical().apply {
                    gravity = Gravity.CENTER
                    addView(text(title).apply { gravity = Gravity.CENTER })
                    addView(button, LayoutParams(dp(88), dp(80)))
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        addView(MaterialCardView(context).apply {
            cardElevation = 0f
            strokeWidth = 0
            radius = dp(16).toFloat()
            setCardBackgroundColor(MaterialColors.getColor(this, MaterialR.attr.colorSurfaceContainer))
            addView(previews)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(tapResult)
        addView(editKey)
        addView(details)
        addView(heading(R.string.qwerty_popup_preview_title))
        addView(text(R.string.qwerty_gesture_help))
        addView(text(R.string.qwerty_grid_help))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(fewerColumns, LayoutParams(dp(56), LayoutParams.WRAP_CONTENT))
            addView(columns.container(), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(moreColumns, LayoutParams(dp(56), LayoutParams.WRAP_CONTENT))
        })
        addView(emptyGrid)
        addView(gridSurface)
        addView(append)
        controls.addView(selection)
        controls.addView(slotOutput.container())
        controls.addView(slotLabel.container())
        controls.addView(slotResult)
        controls.addView(actionRow(before, after))
        controls.addView(actionRow(clear, remove))
        addView(controls)
        tabs.setOnCheckedChangeListener { _, id ->
            if (!binding) {
                shifted = id == shiftTab.id
                selectedSlot = -1
                render(bindColumns = true)
                onChanged?.invoke()
            }
        }
        overrideShift.setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                config = config.copy(shifted = if (checked) config.normal.uppercase() else null)
                selectedSlot = -1
                render(bindColumns = true)
                onChanged?.invoke()
            }
        }
        listOf(label, output, preview).forEach { edit ->
            edit.doAfterTextChanged {
                if (!binding && editable()) {
                    updateLayer(layer().copy(label = label.text.toString(), output = output.text.toString(),
                        preview = preview.text.toString()))
                    renderPreviews()
                }
            }
        }
        columns.doAfterTextChanged {
            if (!binding && editable()) {
                if (validColumns()) updateLayer(layer().copy(columns = columns.text.toString().toInt()))
                renderColumns()
                renderSelectionLabel()
                renderGrid()
                onChanged?.invoke()
            }
        }
        listOf(slotOutput, slotLabel).forEach { edit ->
            edit.doAfterTextChanged {
                if (!binding && editable()) {
                    changeSlot(QwertyPopupSlot(slotOutput.text.toString(), slotLabel.text.toString()))
                    renderSlotResult()
                    renderGrid()
                }
            }
        }
        render(bindColumns = true)
    }

    fun setConfig(value: QwertyKeyConfig) {
        config = value
        selectedSlot = -1
        detailsExpanded = value.normal.output.isEmpty()
        render(bindColumns = true)
        onChanged?.invoke()
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable("parent", super.onSaveInstanceState())
        putBoolean("shifted", shifted)
        putInt("selectedSlot", selectedSlot)
        putBoolean("detailsExpanded", detailsExpanded)
        putString("columns", columns.text.toString())
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        @Suppress("DEPRECATION")
        super.onRestoreInstanceState(state.getParcelable("parent"))
        binding = true
        shifted = state.getBoolean("shifted")
        tabs.check(if (shifted) shiftTab.id else normalTab.id)
        selectedSlot = state.getInt("selectedSlot", -1).takeIf { it in layer().slots.indices } ?: -1
        detailsExpanded = state.getBoolean("detailsExpanded")
        render(bindColumns = true)
        columns.setText(state.getString("columns", layer().columns.toString()))
        onChanged?.invoke()
    }

    private fun layer(): QwertyKeyLayer = if (shifted) config.shifted ?: config.normal.uppercase() else config.normal
    private fun editable() = !shifted || config.shifted != null
    private fun validColumns() = columns.text.toString().toIntOrNull() in 1..QwertyKeyConfig.MAX_COLUMNS
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
    private fun changeColumns(delta: Int) {
        columns.setText((layer().columns + delta).coerceIn(1, QwertyKeyConfig.MAX_COLUMNS).toString())
    }
    private fun moveSlot(delta: Int) {
        if (!editable()) return
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
    private fun render(bindColumns: Boolean = false) {
        binding = true
        val value = layer()
        overrideShift.isVisible = shifted
        shiftHelp.isVisible = shifted && config.shifted == null
        overrideShift.isChecked = config.shifted != null
        label.setText(value.label)
        output.setText(value.output)
        preview.setText(value.preview)
        if (bindColumns) columns.setText(value.columns.toString())
        listOf(label, output, preview, columns).forEach { it.isEnabled = editable() }
        append.isEnabled = editable() && value.slots.size < QwertyKeyConfig.MAX_SLOTS
        val slot = value.slots.getOrNull(selectedSlot)
        controls.isVisible = slot != null
        renderSelectionLabel()
        slotOutput.setText(slot?.output.orEmpty())
        slotLabel.setText(slot?.label.orEmpty())
        listOf(slotOutput, slotLabel, clear, remove).forEach { it.isEnabled = editable() }
        before.isEnabled = editable() && selectedSlot > 0
        after.isEnabled = editable() && selectedSlot < value.slots.lastIndex
        renderDetails()
        renderPreviews()
        renderColumns()
        renderSlotResult()
        renderGrid()
        binding = false
    }
    private fun renderDetails() {
        details.isVisible = detailsExpanded
        editKey.setText(if (detailsExpanded) R.string.qwerty_close_key_editor else R.string.qwerty_edit_key)
    }
    private fun renderPreviews() {
        val value = layer()
        keyPreview.text = value.displayLabel.ifEmpty { context.getString(R.string.qwerty_unset) }
        pressPreview.text = value.previewLabel.ifEmpty { context.getString(R.string.qwerty_unset) }
        keyPreview.contentDescription = context.getString(R.string.qwerty_edit_appearance, keyPreview.text)
        pressPreview.contentDescription = context.getString(R.string.qwerty_edit_press_appearance, pressPreview.text)
        tapResult.text = context.getString(R.string.qwerty_tap_result,
            value.output.ifEmpty { context.getString(R.string.qwerty_unset) })
        output.container().error = if (value.output.isEmpty()) context.getString(R.string.qwerty_output_required) else null
    }
    private fun renderColumns() {
        columns.container().error = if (validColumns()) null else
            context.getString(R.string.qwerty_columns_error, QwertyKeyConfig.MAX_COLUMNS)
        fewerColumns.isEnabled = editable() && (!validColumns() || layer().columns > 1)
        moreColumns.isEnabled = editable() && (!validColumns() || layer().columns < QwertyKeyConfig.MAX_COLUMNS)
    }
    private fun renderSelectionLabel() {
        selection.text = if (selectedSlot !in layer().slots.indices) "" else context.getString(
            R.string.qwerty_selected_slot, selectedSlot / layer().columns + 1, selectedSlot % layer().columns + 1)
    }
    private fun renderSlotResult() {
        slotResult.text = layer().slots.getOrNull(selectedSlot)?.output?.takeIf { it.isNotEmpty() }
            ?.let { context.getString(R.string.qwerty_candidate_result, it) }
            ?: context.getString(R.string.qwerty_blank_result)
    }
    private fun renderGrid() {
        grid.removeAllViews()
        val value = layer()
        emptyGrid.isVisible = value.slots.isEmpty()
        gridSurface.isVisible = value.slots.isNotEmpty()
        // Wide popups retain usable touch targets rather than squeezing twelve cells into the screen.
        grid.minimumWidth = dp(56) * value.columns + dp(16)
        value.slots.chunked(value.columns).forEachIndexed { rowIndex, slots ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            repeat(value.columns) { col ->
                val index = rowIndex * value.columns + col
                val slot = slots.getOrNull(col)
                val cell = MaterialButton(context, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                    text = if (slot == null) "" else if (slot.output.isEmpty())
                        context.getString(R.string.qwerty_empty_slot) else slot.displayLabel
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    minWidth = 0
                    minimumWidth = 0
                    minimumHeight = dp(56)
                    cornerRadius = dp(8)
                    setPadding(dp(4), 0, dp(4), 0)
                    isCheckable = true
                    isChecked = index == selectedSlot
                    isEnabled = slot != null
                    importantForAccessibility = if (slot == null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    contentDescription = context.getString(R.string.qwerty_cell_description,
                        rowIndex + 1, col + 1, text,
                        slot?.output?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.qwerty_blank_result))
                    strokeWidth = if (slot?.output?.isEmpty() == true) dp(1) else 0
                    if (slot?.output?.isNotEmpty() == true) {
                        setBackgroundColor(MaterialColors.getColor(this, MaterialR.attr.colorSurface))
                        setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSurface))
                    }
                    if (isChecked) {
                        setBackgroundColor(MaterialColors.getColor(this, MaterialR.attr.colorSecondaryContainer))
                        setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSecondaryContainer))
                    }
                    setOnClickListener { selectedSlot = index; render() }
                }
                // The unfilled end of the last popup row is not a saved blank slot.
                cell.visibility = if (slot == null) INVISIBLE else VISIBLE
                row.addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                })
            }
            grid.addView(row)
        }
    }
    private fun field(hintRes: Int, optional: Boolean = false): EditText {
        val layout = TextInputLayout(context).apply {
            hint = context.getString(hintRes)
            if (optional) {
                placeholderText = context.getString(R.string.qwerty_same_as_input)
                helperText = context.getString(R.string.qwerty_display_fallback)
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        return TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(QwertyKeyConfig.MAX_TEXT_LENGTH))
            layout.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
    private fun EditText.container() = parent.parent as TextInputLayout
    private fun vertical() = LinearLayout(context).apply { orientation = VERTICAL }
    private fun text(textRes: Int? = null) = TextView(context).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        setPadding(0, dp(8), 0, dp(8))
        if (textRes != null) setText(textRes)
    }
    private fun heading(textRes: Int) = text(textRes).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleMedium)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }
    private fun actionRow(vararg buttons: MaterialButton) = LinearLayout(context).apply {
        buttons.forEach { addView(it, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }) }
    }
    private fun previewButton(pressed: Boolean) = MaterialButton(context, null,
        if (pressed) MaterialR.attr.materialButtonStyle else MaterialR.attr.materialButtonOutlinedStyle).apply {
        textSize = 28f
        cornerRadius = dp(if (pressed) 16 else 10)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { detailsExpanded = true; renderDetails() }
    }
    private fun button(textRes: Int, action: () -> Unit) = MaterialButton(context, null,
        MaterialR.attr.materialButtonOutlinedStyle).apply {
        setText(textRes)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
