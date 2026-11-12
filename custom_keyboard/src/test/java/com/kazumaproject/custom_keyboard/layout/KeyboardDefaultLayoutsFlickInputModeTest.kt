package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import java.text.Normalizer
import org.junit.Assert.*
import org.junit.Test

class KeyboardDefaultLayoutsFlickInputModeTest {
    @Test
    fun preferenceValuesRoundTripAndUnknownValuesPreserveAll() {
        SumireFlickInputMode.entries.forEach { mode ->
            assertEquals(mode, SumireFlickInputMode.fromPreferenceValue(mode.preferenceValue))
        }
        assertEquals(SumireFlickInputMode.ALL, SumireFlickInputMode.fromPreferenceValue(null))
        assertEquals(SumireFlickInputMode.ALL, SumireFlickInputMode.fromPreferenceValue("unknown"))
    }















}
