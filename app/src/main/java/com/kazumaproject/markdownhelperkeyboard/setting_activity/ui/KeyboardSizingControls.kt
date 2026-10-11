package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.R

internal enum class SizingField { CENTER, GUIDE, WIDTH, HEIGHT, ICON, SPECIAL }
internal data class KeyboardSizingValues(
    val center: Float, val guide: Float, val width: Float, val height: Float,
    val icon: Float = 80f, val special: Float = 12f,
) {
    fun value(field: SizingField): Float = when (field) {
        SizingField.CENTER -> center; SizingField.GUIDE -> guide
        SizingField.WIDTH -> width; SizingField.HEIGHT -> height
        SizingField.ICON -> icon; SizingField.SPECIAL -> special
    }
    fun with(field: SizingField, value: Float): KeyboardSizingValues = when (field) {
        SizingField.CENTER -> copy(center = value); SizingField.GUIDE -> copy(guide = value)
        SizingField.WIDTH -> copy(width = value); SizingField.HEIGHT -> copy(height = value)
        SizingField.ICON -> copy(icon = value); SizingField.SPECIAL -> copy(special = value)
    }
}

/** Owns view state only. Programmatic slider updates never write preferences. */
internal class KeyboardSizingControls(
    private val root: View,
    savedState: Bundle?,
    private val tenkey: Boolean,
    private val modeSpecific: Boolean = true,
    private val read: (KeyboardInputMode, Boolean) -> KeyboardSizingValues,
    private val save: (KeyboardInputMode, SizingField, Float) -> Unit,
    private val render: (KeyboardInputMode, Boolean, KeyboardSizingValues) -> Unit,
    private val reset: (KeyboardInputMode?) -> Unit,
    private val inherited: (KeyboardInputMode) -> Boolean,
) {
    private val modeButtons = root.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes)
    private val guideSwitch = root.findViewById<MaterialSwitch>(R.id.sizingGuidePreview)
    private var mode = savedState?.getString("sizingMode")?.let { name ->
        KeyboardInputMode.entries.firstOrNull { it.name == name }
    } ?: KeyboardInputMode.HIRAGANA
    private var values = read(mode, savedState?.getBoolean("sizingGuides") ?: false)
    private val fields = linkedMapOf(
        SizingField.CENTER to (R.id.sizingCenter to R.id.sizingCenterValue),
        SizingField.GUIDE to (R.id.sizingGuide to R.id.sizingGuideValue),
        SizingField.WIDTH to (R.id.sizingWidth to R.id.sizingWidthValue),
        SizingField.HEIGHT to (R.id.sizingHeight to R.id.sizingHeightValue),
    ).apply {
        if (!tenkey) {
            put(SizingField.ICON, R.id.sizingIcon to R.id.sizingIconValue)
            put(SizingField.SPECIAL, R.id.sizingSpecial to R.id.sizingSpecialValue)
        }
    }

    init {
        guideSwitch.isChecked = savedState?.getBoolean("sizingGuides") ?: false
        fields.forEach { (field, ids) ->
            val slider = root.findViewById<Slider>(ids.first)
            val range = when (field) {
                SizingField.CENTER -> if (tenkey) 12f..40f else 8f..32f
                SizingField.GUIDE -> 6f..16f
                SizingField.SPECIAL -> 8f..32f
                else -> if (tenkey) 60f..140f else 40f..220f
            }
            slider.valueFrom = range.start
            slider.valueTo = range.endInclusive
            slider.stepSize = if (field in listOf(SizingField.WIDTH, SizingField.HEIGHT, SizingField.ICON) ||
                (field == SizingField.GUIDE && !modeSpecific)) 1f else 0f
            slider.setLabelFormatter { format(field, it) }
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    values = values.with(field, value)
                    if (!slider.isPressed) save(mode, field, value)
                    updateLabels()
                    render(mode, guideSwitch.isChecked, values)
                }
            }
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit
                override fun onStopTrackingTouch(slider: Slider) {
                    save(mode, field, slider.value)
                    updateLabels()
                }
            })

        }
        modeButtons.check(buttonFor(mode))
        modeButtons.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                mode = when (id) {
                    R.id.sizingEnglish -> KeyboardInputMode.ENGLISH
                    R.id.sizingNumbers -> KeyboardInputMode.SYMBOLS
                    else -> KeyboardInputMode.HIRAGANA
                }
                reload()
            }
        }
        guideSwitch.setOnCheckedChangeListener { _, _ -> reload() }
        if (!modeSpecific) {
            modeButtons.visibility = View.GONE
            root.findViewById<View>(R.id.sizingModeLabel).visibility = View.GONE
            root.findViewById<View>(R.id.sizingResetMode).visibility = View.GONE
        }
        root.findViewById<MaterialButton>(R.id.sizingResetMode).setOnClickListener {
            reset(mode); reload()
        }
        root.findViewById<MaterialButton>(R.id.sizingResetAll).setOnClickListener {
            MaterialAlertDialogBuilder(root.context)
                .setTitle(R.string.sizing_reset_all).setMessage(R.string.sizing_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sizing_reset) { _, _ -> reset(null); reload() }.show()
        }
        reload()
    }

    fun saveState(out: Bundle) {
        out.putString("sizingMode", mode.name)
        out.putBoolean("sizingGuides", guideSwitch.isChecked)
    }

    private fun reload() {
        values = read(mode, guideSwitch.isChecked)
        fields.forEach { (field, ids) ->
            val slider = root.findViewById<Slider>(ids.first)
            slider.value = values.value(field).coerceIn(slider.valueFrom, slider.valueTo)
        }
        updateLabels()
        render(mode, guideSwitch.isChecked, values)
    }

    private fun updateLabels() {
        fields.forEach { (field, ids) ->
            root.findViewById<TextView>(ids.second).text = format(field, values.value(field))
        }
        root.findViewById<View>(R.id.sizingInherited).visibility =
            if (modeSpecific && inherited(mode)) View.VISIBLE else View.GONE
    }

    private fun format(field: SizingField, value: Float): String = root.context.getString(
        if (field in listOf(SizingField.WIDTH, SizingField.HEIGHT, SizingField.ICON))
            R.string.sizing_percent else R.string.sizing_sp,
        if (field in listOf(SizingField.WIDTH, SizingField.HEIGHT, SizingField.ICON)) value.toInt() else value,
    )

    private fun buttonFor(mode: KeyboardInputMode): Int = when (mode) {
        KeyboardInputMode.HIRAGANA -> R.id.sizingJapanese
        KeyboardInputMode.ENGLISH -> R.id.sizingEnglish
        KeyboardInputMode.SYMBOLS -> R.id.sizingNumbers
    }
}
