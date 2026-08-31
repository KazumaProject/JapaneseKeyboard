package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.utility.Precision

internal enum class PrecisionSelectionMode {
    AUTO,
    DECIMAL_PLACES,
    SIGNIFICANT_DIGITS,
}

@Suppress("DEPRECATION")
internal class PrecisionSelectionState(current: Precision) {
    var mode: PrecisionSelectionMode = when (current) {
        Precision.Auto -> PrecisionSelectionMode.AUTO
        Precision.Integer -> PrecisionSelectionMode.DECIMAL_PLACES
        is Precision.DecimalPlaces -> PrecisionSelectionMode.DECIMAL_PLACES
        is Precision.SignificantDigits -> PrecisionSelectionMode.SIGNIFICANT_DIGITS
    }
        private set

    var decimalPlaces: Int = when (current) {
        Precision.Integer -> 0
        is Precision.DecimalPlaces -> current.places
        else -> DEFAULT_DECIMAL_PLACES
    }
        private set

    var significantDigits: Int = when (current) {
        is Precision.SignificantDigits -> current.digits
        else -> DEFAULT_SIGNIFICANT_DIGITS
    }
        private set

    val minimumDigits: Int
        get() = when (mode) {
            PrecisionSelectionMode.AUTO,
            PrecisionSelectionMode.DECIMAL_PLACES -> Precision.MIN_DECIMAL_PLACES
            PrecisionSelectionMode.SIGNIFICANT_DIGITS -> Precision.MIN_DIGITS
        }

    val maximumDigits: Int
        get() = when (mode) {
            PrecisionSelectionMode.AUTO,
            PrecisionSelectionMode.DECIMAL_PLACES -> Precision.MAX_DECIMAL_PLACES
            PrecisionSelectionMode.SIGNIFICANT_DIGITS -> Precision.MAX_DIGITS
        }

    val selectedDigits: Int
        get() = when (mode) {
            PrecisionSelectionMode.AUTO,
            PrecisionSelectionMode.DECIMAL_PLACES -> decimalPlaces
            PrecisionSelectionMode.SIGNIFICANT_DIGITS -> significantDigits
        }

    fun selectMode(value: PrecisionSelectionMode) {
        mode = value
    }

    fun updateDigits(value: Int) {
        when (mode) {
            PrecisionSelectionMode.AUTO -> Unit
            PrecisionSelectionMode.DECIMAL_PLACES -> {
                decimalPlaces = value.coerceIn(
                    Precision.MIN_DECIMAL_PLACES,
                    Precision.MAX_DECIMAL_PLACES,
                )
            }
            PrecisionSelectionMode.SIGNIFICANT_DIGITS -> {
                significantDigits = value.coerceIn(
                    Precision.MIN_DIGITS,
                    Precision.MAX_DIGITS,
                )
            }
        }
    }

    fun toPrecision(): Precision = when (mode) {
        PrecisionSelectionMode.AUTO -> Precision.Auto
        PrecisionSelectionMode.DECIMAL_PLACES -> Precision.DecimalPlaces(decimalPlaces)
        PrecisionSelectionMode.SIGNIFICANT_DIGITS -> Precision.SignificantDigits(significantDigits)
    }

    companion object {
        const val DEFAULT_DECIMAL_PLACES = 2
        const val DEFAULT_SIGNIFICANT_DIGITS = 6
    }
}

internal object PrecisionSelectionDialog {
    fun create(
        context: Context,
        current: Precision,
        onSelected: (Precision) -> Unit,
    ): AlertDialog {
        val content = LayoutInflater.from(context).inflate(
            R.layout.dialog_utility_precision,
            null,
            false,
        )
        val modeGroup = content.findViewById<RadioGroup>(R.id.utility_precision_mode_group)
        val digitLabel = content.findViewById<TextView>(R.id.utility_precision_digit_label)
        val digitPicker = content.findViewById<NumberPicker>(R.id.utility_precision_digit_picker)
        val state = PrecisionSelectionState(current)

        fun renderMode() {
            digitPicker.setOnValueChangedListener(null)
            digitPicker.minValue = state.minimumDigits
            digitPicker.maxValue = state.maximumDigits
            digitPicker.value = state.selectedDigits
            val digitsEnabled = state.mode != PrecisionSelectionMode.AUTO
            digitPicker.isEnabled = digitsEnabled
            digitLabel.isEnabled = digitsEnabled
            digitLabel.text = when (state.mode) {
                PrecisionSelectionMode.AUTO -> context.getString(R.string.utility_precision_digit_count)
                PrecisionSelectionMode.DECIMAL_PLACES -> context.getString(
                    R.string.utility_precision_mode_decimal_places,
                )
                PrecisionSelectionMode.SIGNIFICANT_DIGITS -> context.getString(
                    R.string.utility_precision_mode_significant_digits,
                )
            }
            digitPicker.setOnValueChangedListener { _, _, newValue ->
                state.updateDigits(newValue)
            }
        }

        val checkedId = when (state.mode) {
            PrecisionSelectionMode.AUTO -> R.id.utility_precision_mode_auto
            PrecisionSelectionMode.DECIMAL_PLACES -> R.id.utility_precision_mode_decimal_places
            PrecisionSelectionMode.SIGNIFICANT_DIGITS ->
                R.id.utility_precision_mode_significant_digits
        }
        modeGroup.check(checkedId)
        renderMode()
        modeGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.utility_precision_mode_decimal_places ->
                    PrecisionSelectionMode.DECIMAL_PLACES
                R.id.utility_precision_mode_significant_digits ->
                    PrecisionSelectionMode.SIGNIFICANT_DIGITS
                else -> PrecisionSelectionMode.AUTO
            }
            state.selectMode(mode)
            renderMode()
        }

        return AlertDialog.Builder(context)
            .setTitle(R.string.utility_precision_dialog_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSelected(state.toPrecision())
            }
            .create()
    }
}

@Suppress("DEPRECATION")
internal fun Context.utilityPrecisionLabel(precision: Precision): String = when (precision) {
    Precision.Auto -> getString(R.string.utility_precision_auto)
    Precision.Integer -> getString(
        R.string.utility_precision_decimal_places,
        0,
    )
    is Precision.DecimalPlaces -> if (precision.places == 1) {
        getString(R.string.utility_precision_decimal_place)
    } else {
        getString(R.string.utility_precision_decimal_places, precision.places)
    }
    is Precision.SignificantDigits -> if (precision.digits == 1) {
        getString(R.string.utility_precision_significant_digit)
    } else {
        getString(R.string.utility_precision_digits, precision.digits)
    }
}
