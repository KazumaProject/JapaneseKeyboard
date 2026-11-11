package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
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















    private fun layout(
        mode: SumireFlickInputMode,
        rotation: TfbiFlickRotation = TfbiFlickRotation.DEFAULT,
        layoutType: String = "flick",
        style: String = "third-flick"
    ) = KeyboardDefaultLayouts.createFinalLayout(
        KeyboardInputMode.HIRAGANA, emptyMap(), layoutType, style,
        tfbiFlickRotation = rotation, sumireFlickInputMode = mode)

    private fun KeyboardLayout.outputs(): List<String> = hierarchicalFlickMaps.values.flatMap { it.outputs() }

    private fun TfbiFlickNode.outputs(): List<String> = when (this) {
        is TfbiFlickNode.Input -> listOf(char)
        is TfbiFlickNode.SubMenu -> nextMap.values.flatMap { it.outputs() }
        is TfbiFlickNode.StatefulKey -> listOfNotNull(normalMap, dakutenMap, handakutenMap)
            .flatMap { it.values.flatMap { node -> node.outputs() } }
    }



    private fun Map<TfbiFlickDirection, TfbiFlickNode>.branch(direction: TfbiFlickDirection) =
        (getValue(direction) as TfbiFlickNode.SubMenu).nextMap

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.input(direction: TfbiFlickDirection) =
        getValue(direction) as TfbiFlickNode.Input

    companion object {
        private val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
    }
}
