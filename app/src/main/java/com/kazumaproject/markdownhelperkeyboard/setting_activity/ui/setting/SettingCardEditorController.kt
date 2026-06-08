package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import com.google.android.material.R as MaterialR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlin.math.abs

class SettingCardEditorController(
    private val context: Context,
    private val appPreference: AppPreference = AppPreference,
) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun handleClick(
        destination: SettingDestination,
        onChanged: () -> Unit,
    ): Boolean {
        return when (val target = destination.destination) {
            is SettingDestinationType.NavDestination,
            is SettingDestinationType.ManagementDestination -> false

            is SettingDestinationType.SwitchPreference -> {
                writeSwitchPreference(target, !readSwitchPreference(target))
                onChanged()
                true
            }

            is SettingDestinationType.ListPreference -> {
                showListPreferenceDialog(destination, target, onChanged)
                true
            }

            is SettingDestinationType.SeekBarPreference -> {
                showSeekBarPreferenceDialog(destination, target, onChanged)
                true
            }

            is SettingDestinationType.EditTextPreference -> {
                showEditTextPreferenceDialog(destination, target, onChanged)
                true
            }

            is SettingDestinationType.IntPreferenceDialog -> {
                showIntPreferenceDialog(destination, target, onChanged)
                true
            }
        }
    }

    fun isDirectEditable(destination: SettingDestination): Boolean =
        when (destination.destination) {
            is SettingDestinationType.SwitchPreference,
            is SettingDestinationType.ListPreference,
            is SettingDestinationType.SeekBarPreference,
            is SettingDestinationType.EditTextPreference,
            is SettingDestinationType.IntPreferenceDialog -> true

            is SettingDestinationType.NavDestination,
            is SettingDestinationType.ManagementDestination -> false
        }

    fun readSwitchPreference(target: SettingDestinationType.SwitchPreference): Boolean =
        safeGetBoolean(target.preferenceKey, target.defaultValue)

    fun currentValueLabel(destination: SettingDestination): String? =
        when (val target = destination.destination) {
            is SettingDestinationType.NavDestination,
            is SettingDestinationType.ManagementDestination -> null

            is SettingDestinationType.SwitchPreference ->
                statusLabel(readSwitchPreference(target))

            is SettingDestinationType.ListPreference ->
                listPreferenceLabel(target)

            is SettingDestinationType.SeekBarPreference ->
                formatNumberPreferenceValue(
                    preferenceKey = target.preferenceKey,
                    value = readIntPreference(target.preferenceKey, target.defaultValue),
                )

            is SettingDestinationType.EditTextPreference -> {
                val value = readStringPreference(target.preferenceKey, target.defaultValue)
                when {
                    value.isBlank() -> context.getString(R.string.setting_value_empty)
                    target.obscureValue -> "******"
                    else -> value
                }
            }

            is SettingDestinationType.IntPreferenceDialog ->
                formatNumberPreferenceValue(
                    preferenceKey = target.preferenceKey,
                    value = readIntPreference(target.preferenceKey, target.defaultValue),
                    unit = target.unit,
                )
        }

    private fun showListPreferenceDialog(
        destination: SettingDestination,
        target: SettingDestinationType.ListPreference,
        onChanged: () -> Unit,
    ) {
        val entries = runCatching { context.resources.getStringArray(target.entriesResId) }
            .getOrNull()
            ?: return
        val entryValues = runCatching { context.resources.getStringArray(target.entryValuesResId) }
            .getOrNull()
            ?: return
        if (entries.isEmpty() || entries.size != entryValues.size) return

        val currentValue = readStringPreference(target.preferenceKey, target.defaultValue)
        val checkedIndex = entryValues.indexOf(currentValue)
            .takeIf { it >= 0 }
            ?: entryValues.indexOf(target.defaultValue).takeIf { it >= 0 }
            ?: 0

        MaterialAlertDialogBuilder(context)
            .setTitle(destination.title)
            .setSingleChoiceItems(entries, checkedIndex) { dialog, which ->
                entryValues.getOrNull(which)?.let { value ->
                    writeStringPreference(target.preferenceKey, value)
                    onChanged()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSeekBarPreferenceDialog(
        destination: SettingDestination,
        target: SettingDestinationType.SeekBarPreference,
        onChanged: () -> Unit,
    ) {
        showNumberPreferenceDialog(
            title = destination.title,
            preferenceKey = target.preferenceKey,
            min = target.min,
            max = target.max,
            step = target.increment,
            defaultValue = target.defaultValue,
            currentValue = readIntPreference(target.preferenceKey, target.defaultValue),
            unit = "",
            onSave = { value ->
                writeIntPreference(target.preferenceKey, value)
                onChanged()
            },
        )
    }

    private fun showIntPreferenceDialog(
        destination: SettingDestination,
        target: SettingDestinationType.IntPreferenceDialog,
        onChanged: () -> Unit,
    ) {
        showNumberPreferenceDialog(
            title = destination.title,
            preferenceKey = target.preferenceKey,
            min = target.min,
            max = target.max,
            step = target.step,
            defaultValue = target.defaultValue,
            currentValue = readIntPreference(target.preferenceKey, target.defaultValue),
            unit = target.unit,
            onSave = { value ->
                writeIntPreference(target.preferenceKey, value)
                onChanged()
            },
        )
    }

    private fun showNumberPreferenceDialog(
        title: String,
        preferenceKey: String,
        min: Int,
        max: Int,
        step: Int,
        defaultValue: Int,
        currentValue: Int,
        unit: String,
        onSave: (Int) -> Unit,
    ) {
        val selectableValues = selectableIntValues(min, max, step)
        if (selectableValues.isEmpty()) return
        val initialIndex = selectableValues.indexOf(closestSelectableValue(selectableValues, currentValue))
            .takeIf { it >= 0 } ?: 0

        val dialogContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
        }
        val valueText = TextView(context).apply {
            setTextColor(context.resolveThemeColor(MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val seekBar = SeekBar(context).apply {
            this.max = selectableValues.lastIndex
            progress = initialIndex
        }

        fun updateLabel(index: Int) {
            val safeIndex = index.coerceIn(selectableValues.indices)
            valueText.text = formatNumberPreferenceValue(
                preferenceKey = preferenceKey,
                value = selectableValues[safeIndex],
                unit = unit,
            )
        }

        updateLabel(initialIndex)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        dialogContent.addView(
            valueText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
        dialogContent.addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
            }
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogContent)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSave(selectableValues[seekBar.progress])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.reset_to_default, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val defaultIndex = selectableValues.indexOf(
                    closestSelectableValue(selectableValues, defaultValue)
                ).takeIf { it >= 0 } ?: 0
                seekBar.progress = defaultIndex
            }
        }
        dialog.show()
    }

    private fun showEditTextPreferenceDialog(
        destination: SettingDestination,
        target: SettingDestinationType.EditTextPreference,
        onChanged: () -> Unit,
    ) {
        val editText = EditText(context).apply {
            setText(readStringPreference(target.preferenceKey, target.defaultValue))
            setSelection(text?.length ?: 0)
            minLines = 1
            maxLines = 4
            inputType = if (target.obscureValue) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
            addView(
                editText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(destination.title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                writeStringPreference(target.preferenceKey, editText.text?.toString().orEmpty())
                onChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun listPreferenceLabel(target: SettingDestinationType.ListPreference): String {
        val entries = runCatching { context.resources.getStringArray(target.entriesResId) }
            .getOrNull()
            ?: return readStringPreference(target.preferenceKey, target.defaultValue)
        val entryValues = runCatching { context.resources.getStringArray(target.entryValuesResId) }
            .getOrNull()
            ?: return readStringPreference(target.preferenceKey, target.defaultValue)
        val currentValue = readStringPreference(target.preferenceKey, target.defaultValue)
        val index = entryValues.indexOf(currentValue)
        return if (index >= 0) {
            entries.getOrNull(index) ?: currentValue
        } else {
            currentValue
        }
    }

    private fun readStringPreference(preferenceKey: String, defaultValue: String): String =
        runCatching {
            preferences.getString(preferenceKey, defaultValue) ?: defaultValue
        }.getOrDefault(defaultValue)

    private fun writeStringPreference(preferenceKey: String, value: String) {
        preferences.edit()
            .putString(preferenceKey, value)
            .apply()
        if (preferenceKey == "candidate_column_preference") {
            appPreference.candidate_view_height_dp = when (value) {
                "1" -> 110
                "2" -> 165
                "3" -> 230
                else -> appPreference.candidate_view_height_dp ?: 110
            }
        }
    }

    private fun readIntPreference(preferenceKey: String, defaultValue: Int): Int =
        runCatching {
            preferences.getInt(preferenceKey, defaultValue)
        }.getOrDefault(defaultValue)

    private fun writeIntPreference(preferenceKey: String, value: Int) {
        preferences.edit()
            .putInt(preferenceKey, value)
            .apply()
    }

    private fun writeSwitchPreference(
        target: SettingDestinationType.SwitchPreference,
        value: Boolean,
    ) {
        preferences.edit()
            .putBoolean(target.preferenceKey, value)
            .apply()
        if (target.preferenceKey == "app_setting_language_preference") {
            if (value) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }

    private fun safeGetBoolean(preferenceKey: String, defaultValue: Boolean): Boolean =
        runCatching {
            preferences.getBoolean(preferenceKey, defaultValue)
        }.getOrDefault(defaultValue)

    private fun selectableIntValues(min: Int, max: Int, step: Int): List<Int> {
        val safeStep = step.coerceAtLeast(1)
        if (max < min) return emptyList()
        val values = mutableListOf<Int>()
        var value = min
        while (value <= max) {
            values.add(value)
            val next = value + safeStep
            if (next <= value) break
            value = next
        }
        if (values.lastOrNull() != max) {
            values.add(max)
        }
        return values.distinct()
    }

    private fun closestSelectableValue(selectableValues: List<Int>, value: Int): Int =
        selectableValues.minByOrNull { abs(it - value) } ?: value

    private fun formatNumberPreferenceValue(
        preferenceKey: String,
        value: Int,
        unit: String = "",
    ): String {
        return when (preferenceKey) {
            "long_press_timeout_preference" ->
                context.getString(R.string.long_press_timeout_preference_value, value)

            "flick_sensitivity_preference" ->
                "$value (${flickSensitivityLabel(value)})"

            "key_sound_volume_percent_preference" ->
                if (value == 0) {
                    context.getString(R.string.key_sound_volume_system_default)
                } else {
                    context.getString(R.string.key_sound_volume_percent, value)
                }

            "zenz_debounce_time_preference" -> "$value ms"

            else -> if (unit.isBlank()) {
                value.toString()
            } else {
                "$value $unit"
            }
        }
    }

    private fun flickSensitivityLabel(value: Int): String =
        when (value) {
            in 0..50 -> context.getString(R.string.sensitivity_very_high)
            in 51..90 -> context.getString(R.string.sensitivity_high)
            in 91..110 -> context.getString(R.string.sensitivity_normal)
            in 111..150 -> context.getString(R.string.sensitivity_less)
            in 151..200 -> context.getString(R.string.sensitivity_low)
            else -> ""
        }

    private fun statusLabel(enabled: Boolean): String =
        if (enabled) {
            context.getString(R.string.setting_status_enabled)
        } else {
            context.getString(R.string.setting_status_disabled)
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun Context.resolveThemeColor(@AttrRes attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) {
            ContextCompat.getColor(this, value.resourceId)
        } else {
            value.data
        }
    }
}
