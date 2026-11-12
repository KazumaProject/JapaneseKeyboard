package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.DOWN
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.DOWN_LEFT
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.DOWN_RIGHT
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.LEFT
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.RIGHT
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.TAP
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.UP
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.UP_LEFT
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.UP_RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.kazumaproject.custom_keyboard.data.BuiltInTwoStepCharacters
import com.kazumaproject.custom_keyboard.data.BuiltInTwoStepKey
import org.junit.Test

class BuiltInTwoStepRotationTest {
    @Test
    fun preferenceValuesRoundTripAndUnknownValuesUseDefault() {
        TfbiFlickRotation.entries.forEach { rotation ->
            assertEquals(rotation, TfbiFlickRotation.fromPreferenceValue(rotation.preferenceValue))
        }
        assertEquals(TfbiFlickRotation.DEFAULT, TfbiFlickRotation.fromPreferenceValue(null))
        assertEquals(TfbiFlickRotation.DEFAULT, TfbiFlickRotation.fromPreferenceValue("unknown"))
    }

    @Test
    fun defaultKeepsExistingMapsForEverySumireLayoutType() {
        layoutTypes.forEach { layoutType ->
            styles.forEach { style ->
                val original = layout(style, layoutType)
                assertTrue(original.twoStepFlickKeyMaps.isEmpty())
                assertEquals(original, layout(style, layoutType, TfbiFlickRotation.DEFAULT))
            }
        }
    }

    @Test
    fun twoStepPresetsPlaceDakutenOnTheRequestedSideForAllLayoutTypes() {
        // Expected corners are from the installed Arte preset guides, independent of the mapper.
        layoutTypes.forEach { layoutType ->
            val clockwise = layout("second-flick", layoutType, TfbiFlickRotation.CLOCKWISE)
                .builtInCharacters().getValue("か")
            assertEquals("ぎ", clockwise.getValue(LEFT).getValue(UP_LEFT))
            assertEquals("ぐ", clockwise.getValue(UP).getValue(UP_RIGHT))
            assertEquals("げ", clockwise.getValue(RIGHT).getValue(DOWN_RIGHT))
            assertEquals("ご", clockwise.getValue(DOWN).getValue(DOWN_LEFT))
            assertEquals("が", clockwise.getValue(UP_RIGHT).getValue(UP_RIGHT))

            val counterclockwise = layout("second-flick", layoutType, TfbiFlickRotation.COUNTERCLOCKWISE)
                .builtInCharacters().getValue("か")
            assertEquals("ぎ", counterclockwise.getValue(LEFT).getValue(DOWN_LEFT))
            assertEquals("ぐ", counterclockwise.getValue(UP).getValue(UP_LEFT))
            assertEquals("げ", counterclockwise.getValue(RIGHT).getValue(UP_RIGHT))
            assertEquals("ご", counterclockwise.getValue(DOWN).getValue(DOWN_RIGHT))
            assertEquals("が", counterclockwise.getValue(UP_RIGHT).getValue(UP_RIGHT))
        }
    }

    @Test
    fun aYaWaRowsKeepTheirSmallKanaAndOtherCandidates() {
        styles.forEach { style ->
            val original = layout(style)
            TfbiFlickRotation.entries.forEach { rotation ->
                val rotated = layout(style, rotation = rotation)
                listOf("あ", "や", "わ").forEach { label ->
                    assertEquals(original.builtInCharacters()[label], rotated.builtInCharacters()[label])
                    assertEquals(original.preservedFlickDefinitions[label], rotated.preservedFlickDefinitions[label])
                }
            }
        }
    }









    @Test
    fun rotationsKeepAllOutputsSpecialKeysAndPunctuation() {
        layoutTypes.forEach { layoutType ->
            styles.forEach { style ->
                val original = layout(style, layoutType)
                TfbiFlickRotation.entries.forEach { rotation ->
                    val rotated = layout(style, layoutType, rotation)
                    assertEquals(original.keys, rotated.keys)
                    assertEquals(original.flickKeyMaps, rotated.flickKeyMaps)
                    assertEquals(original.builtInCharacters()["、。?!"], rotated.builtInCharacters()["、。?!"])
                    assertEquals(original.preservedFlickDefinitions["、。?!"], rotated.preservedFlickDefinitions["、。?!"])
                    original.builtInCharacters().forEach { (key, firstMap) ->
                        assertEquals(firstMap.keys, rotated.builtInCharacters().getValue(key).keys)
                        firstMap.forEach { (first, secondMap) ->
                            assertEquals(
                                secondMap.values.groupingBy { it }.eachCount(),
                                rotated.builtInCharacters().getValue(key).getValue(first)
                                    .values.groupingBy { it }.eachCount()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun rotationDoesNotChangeOtherStylesOrInputModes() {
        KeyboardInputMode.entries.forEach { mode ->
            listOf("default", "sumire", "second-flick", "third-flick").forEach styleLoop@{ style ->
                if (mode == KeyboardInputMode.HIRAGANA && style in styles) return@styleLoop
                layoutTypes.forEach { layoutType ->
                    assertEquals(
                        layout(style, layoutType, mode = mode),
                        layout(style, layoutType, TfbiFlickRotation.CLOCKWISE, mode)
                    )
                }
            }
        }
    }

    private fun KeyboardLayout.builtInCharacters() = keys.mapNotNull { key ->
        key.builtInTwoStepKey?.let { key.label to BuiltInTwoStepCharacters.guideMap(it, builtInTwoStepRotation) }
    }.toMap()

    private fun layout(
        style: String,
        layoutType: String = "flick",
        rotation: TfbiFlickRotation = TfbiFlickRotation.DEFAULT,
        mode: KeyboardInputMode = KeyboardInputMode.HIRAGANA
    ): KeyboardLayout = KeyboardDefaultLayouts.createFinalLayout(
        mode = mode,
        dynamicKeyStates = emptyMap(),
        inputLayoutType = layoutType,
        inputStyle = style,
        tfbiFlickRotation = rotation
    )

    companion object {
        private val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
        private val styles = listOf("second-flick")
    }
}
