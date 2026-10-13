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

    @Test
    fun allKeepsTheExistingFullLayoutIncludingDigitsAndVoicedYoon() {
        val original = KeyboardDefaultLayouts.createFinalLayout(
            KeyboardInputMode.HIRAGANA, emptyMap(), "flick", "third-flick")
        assertSame(original, SumireFlickInputModeMapper.apply(original, SumireFlickInputMode.ALL))
        assertEquals(original, layout(SumireFlickInputMode.ALL))
        val outputs = original.outputs()
        assertTrue(outputs.containsAll(listOf("きゅ", "きゅう", "ぎゅ", "ぎゅう", "ぴょ", "ぴょう")))
        assertTrue(outputs.containsAll(('0'..'9').map(Char::toString)))
    }

    @Test
    fun contractedKeepsYoonAndLongVowelsWithoutVoicedOutputsOrStates() {
        val result = layout(SumireFlickInputMode.CONTRACTED)
        val ka = result.hierarchicalFlickMaps.getValue("か")
        assertEquals("きゅ", ka.normalMap.branch(LEFT).branch(UP).input(UP).char)
        assertEquals("きゅう", ka.normalMap.branch(LEFT).branch(UP).input(UP_LEFT).char)
        assertEquals("きょ", ka.normalMap.branch(LEFT).branch(DOWN).input(DOWN).char)
        assertFalse(ka.normalMap.containsKey(UP_RIGHT))
        assertNull(ka.dakutenMap)
        assertNull(ka.handakutenMap)
        assertFalse(result.outputs().any { text ->
            Normalizer.normalize(text, Normalizer.Form.NFD).any { it == '\u3099' || it == '\u309A' }
        })
        assertFalse(result.outputs().contains("ぎゅう"))
        assertValidStages(result)
    }

    @Test
    fun voicedKeepsDiacriticsAndSmallKanaWithoutCompositeOutputs() {
        val result = layout(SumireFlickInputMode.VOICED)
        val ka = result.hierarchicalFlickMaps.getValue("か")
        assertEquals("ぎ", ka.normalMap.branch(LEFT).input(DOWN_LEFT).char)
        assertNotNull(ka.dakutenMap)
        assertFalse(ka.normalMap.branch(LEFT).containsKey(UP))
        assertFalse(ka.normalMap.branch(LEFT).containsKey(RIGHT))
        assertFalse(ka.normalMap.branch(LEFT).containsKey(DOWN))
        val outputs = result.outputs()
        assertTrue(outputs.containsAll(listOf("が", "ぱ", "び", "ぷ", "ゔ", "っ")))
        assertFalse(outputs.any { it.codePointCount(0, it.length) > 1 && it.any { char -> char in '\u3041'..'\u3096' } })
        assertFalse(outputs.contains("てぃ"))
        assertValidStages(result)
    }

    @Test
    fun limitedModesRemoveDigitExtrasAndKeepOrdinarySmallKanaAndSymbols() {
        for (mode in listOf(SumireFlickInputMode.CONTRACTED, SumireFlickInputMode.VOICED)) {
            val result = layout(mode)
            assertFalse(result.outputs().any { it.isNotEmpty() && it.all { char -> char in '0'..'9' } })
            assertTrue(result.outputs().containsAll(listOf("あ", "い", "う", "え", "お",
                "ぁ", "ぃ", "ぅ", "ぇ", "ぉ", "ゃ", "ゅ", "ょ", "ゎ", "っ")))
            assertEquals(layout(SumireFlickInputMode.ALL).hierarchicalFlickMaps["、。?!"],
                result.hierarchicalFlickMaps["、。?!"])
            assertEquals(layout(SumireFlickInputMode.ALL).flickKeyMaps, result.flickKeyMaps)
        }
    }

    @Test
    fun contractedDoesNotMoveRemainingExtensionsWhenRotationChanges() {
        for (layoutType in layoutTypes) {
            val original = layout(SumireFlickInputMode.CONTRACTED, layoutType = layoutType)
            for (rotation in TfbiFlickRotation.entries) {
                assertEquals(original, layout(SumireFlickInputMode.CONTRACTED, rotation, layoutType))
            }
        }
    }

    @Test
    fun secondFlickIgnoresTheThreeStepInputModeAndKeepsRotation() {
        for (layoutType in layoutTypes) for (rotation in TfbiFlickRotation.entries) {
            val original = layout(SumireFlickInputMode.ALL, rotation, layoutType, "second-flick")
            for (mode in SumireFlickInputMode.entries) {
                assertEquals(original, layout(mode, rotation, layoutType, "second-flick"))
            }
            val gi = if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT
            assertEquals("ぎ", original.twoStepFlickKeyMaps.getValue("か").getValue(LEFT).getValue(gi))
        }
    }

    @Test
    fun threeStepModesApplyToAllLayoutTypesWithoutChangingOtherStylesOrLanguages() {
        for (layoutType in layoutTypes) {
            for (mode in SumireFlickInputMode.entries) {
                val result = layout(mode, layoutType = layoutType)
                assertTrue(result.outputs().contains("き"))
                assertEquals(mode != SumireFlickInputMode.VOICED, result.outputs().contains("きゅう"))
                assertEquals(mode != SumireFlickInputMode.CONTRACTED, result.outputs().contains("ぎ"))
                for (language in KeyboardInputMode.entries) for (style in listOf("default", "sumire", "third-flick")) {
                    if (language == KeyboardInputMode.HIRAGANA && style == "third-flick") continue
                    fun create(inputMode: SumireFlickInputMode) = KeyboardDefaultLayouts.createFinalLayout(
                        language, emptyMap(), layoutType, style, sumireFlickInputMode = inputMode)
                    assertEquals(create(SumireFlickInputMode.ALL), create(mode))
                }
            }
        }
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

    private fun assertValidStages(layout: KeyboardLayout) {
        fun verify(map: Map<TfbiFlickDirection, TfbiFlickNode>) {
            assertTrue(map.isNotEmpty())
            map.values.filterIsInstance<TfbiFlickNode.SubMenu>().forEach { submenu ->
                assertTrue(map.keys.containsAll(submenu.parentContinuationDirections))
                verify(submenu.nextMap)
            }
        }
        layout.hierarchicalFlickMaps.values.forEach { key ->
            listOfNotNull(key.normalMap, key.dakutenMap, key.handakutenMap).forEach(::verify)
        }
    }

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.branch(direction: TfbiFlickDirection) =
        (getValue(direction) as TfbiFlickNode.SubMenu).nextMap

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.input(direction: TfbiFlickDirection) =
        getValue(direction) as TfbiFlickNode.Input

    companion object {
        private val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
    }
}
