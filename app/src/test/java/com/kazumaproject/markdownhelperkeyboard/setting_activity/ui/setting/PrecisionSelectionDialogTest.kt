package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.NumberPicker
import android.widget.RadioGroup
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.utility.Precision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrecisionSelectionDialogTest {
    @Test
    fun stateKeepsIndependentDefaultsAndSelectionsForBothManualModes() {
        val state = PrecisionSelectionState(Precision.Auto)
        assertEquals(PrecisionSelectionMode.AUTO, state.mode)
        assertEquals(2, state.decimalPlaces)
        assertEquals(6, state.significantDigits)

        state.selectMode(PrecisionSelectionMode.DECIMAL_PLACES)
        state.updateDigits(4)
        state.selectMode(PrecisionSelectionMode.SIGNIFICANT_DIGITS)
        state.updateDigits(8)
        state.selectMode(PrecisionSelectionMode.DECIMAL_PLACES)

        assertEquals(4, state.selectedDigits)
        assertEquals(Precision.DecimalPlaces(4), state.toPrecision())
        state.selectMode(PrecisionSelectionMode.SIGNIFICANT_DIGITS)
        assertEquals(8, state.selectedDigits)
        assertEquals(Precision.SignificantDigits(8), state.toPrecision())
    }

    @Suppress("DEPRECATION")
    @Test
    fun stateMapsLegacyIntegerAndEnforcesModeRanges() {
        val legacy = PrecisionSelectionState(Precision.Integer)
        assertEquals(PrecisionSelectionMode.DECIMAL_PLACES, legacy.mode)
        assertEquals(0, legacy.selectedDigits)
        assertEquals(Precision.DecimalPlaces(0), legacy.toPrecision())

        legacy.updateDigits(99)
        assertEquals(15, legacy.selectedDigits)
        legacy.selectMode(PrecisionSelectionMode.SIGNIFICANT_DIGITS)
        legacy.updateDigits(0)
        assertEquals(1, legacy.selectedDigits)
    }

    @Test
    fun dialogSwitchesModesRetainsDigitsAndAppliesOnlyWithOk() {
        val selected = mutableListOf<Precision>()
        val dialog = PrecisionSelectionDialog.create(
            context = themedContext(),
            current = Precision.Auto,
            onSelected = selected::add,
        )
        dialog.show()
        val modeGroup = dialog.findViewById<RadioGroup>(R.id.utility_precision_mode_group)!!
        val picker = dialog.findViewById<NumberPicker>(R.id.utility_precision_digit_picker)!!

        assertFalse(picker.isEnabled)
        modeGroup.check(R.id.utility_precision_mode_decimal_places)
        assertTrue(picker.isEnabled)
        assertEquals(0, picker.minValue)
        assertEquals(15, picker.maxValue)
        assertEquals(2, picker.value)

        modeGroup.check(R.id.utility_precision_mode_significant_digits)
        assertEquals(1, picker.minValue)
        assertEquals(6, picker.value)
        modeGroup.check(R.id.utility_precision_mode_decimal_places)
        assertEquals(2, picker.value)

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(Precision.DecimalPlaces(2)), selected)

        val cancelled = PrecisionSelectionDialog.create(
            context = themedContext(),
            current = Precision.Auto,
            onSelected = selected::add,
        )
        cancelled.show()
        cancelled.findViewById<RadioGroup>(R.id.utility_precision_mode_group)!!
            .check(R.id.utility_precision_mode_significant_digits)
        cancelled.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(Precision.DecimalPlaces(2)), selected)
    }

    private fun themedContext(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_MarkdownKeyboard,
    )
}
