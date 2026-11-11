package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.ModeSwitchBoundary
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
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
import org.junit.Assert.assertSame
import org.junit.Test

class KeyboardDefaultLayoutsFlickRotationTest {
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
                assertSame(original, SumireFlickRotationMapper.apply(original, TfbiFlickRotation.DEFAULT))
                assertEquals(original, layout(style, layoutType, TfbiFlickRotation.DEFAULT))
            }
        }
    }

    @Test
    fun twoStepPresetsPlaceDakutenOnTheRequestedSideForAllLayoutTypes() {
        // Expected corners are from the installed Arte preset guides, independent of the mapper.
        layoutTypes.forEach { layoutType ->
            val clockwise = layout("second-flick", layoutType, TfbiFlickRotation.CLOCKWISE)
                .twoStepFlickKeyMaps.getValue("か")
            assertEquals("ぎ", clockwise.getValue(LEFT).getValue(UP_LEFT))
            assertEquals("ぐ", clockwise.getValue(UP).getValue(UP_RIGHT))
            assertEquals("げ", clockwise.getValue(RIGHT).getValue(DOWN_RIGHT))
            assertEquals("ご", clockwise.getValue(DOWN).getValue(DOWN_LEFT))
            assertEquals("が", clockwise.getValue(UP_RIGHT).getValue(UP_RIGHT))

            val counterclockwise = layout("second-flick", layoutType, TfbiFlickRotation.COUNTERCLOCKWISE)
                .twoStepFlickKeyMaps.getValue("か")
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
                    assertEquals(original.twoStepFlickKeyMaps[label], rotated.twoStepFlickKeyMaps[label])
                    assertEquals(original.hierarchicalFlickMaps[label], rotated.hierarchicalFlickMaps[label])
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
                    assertEquals(original.twoStepFlickKeyMaps["、。?!"], rotated.twoStepFlickKeyMaps["、。?!"])
                    assertEquals(original.hierarchicalFlickMaps["、。?!"], rotated.hierarchicalFlickMaps["、。?!"])
                    original.twoStepFlickKeyMaps.forEach { (key, firstMap) ->
                        assertEquals(firstMap.keys, rotated.twoStepFlickKeyMaps.getValue(key).keys)
                        firstMap.forEach { (first, secondMap) ->
                            assertEquals(
                                secondMap.values.groupingBy { it }.eachCount(),
                                rotated.twoStepFlickKeyMaps.getValue(key).getValue(first)
                                    .values.groupingBy { it }.eachCount()
                            )
                        }
                    }
                    original.hierarchicalFlickMaps.forEach { (key, node) ->
                        val rotatedNode = rotated.hierarchicalFlickMaps.getValue(key)
                        assertEquals(node.normalMap.keys, rotatedNode.normalMap.keys)
                        assertEquals(node.inputs().groupingBy { it }.eachCount(), rotatedNode.inputs().groupingBy { it }.eachCount())
                        listOf(TAP, UP_LEFT, UP_RIGHT).forEach { direction ->
                            assertEquals(node.normalMap[direction], rotatedNode.normalMap[direction])
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

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.subMenu(direction: TfbiFlickDirection) =
        getValue(direction) as TfbiFlickNode.SubMenu

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.branch(direction: TfbiFlickDirection) =
        subMenu(direction).nextMap

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.input(direction: TfbiFlickDirection) =
        getValue(direction) as TfbiFlickNode.Input

    private fun TfbiFlickNode.inputs(): List<TfbiFlickNode.Input> = when (this) {
        is TfbiFlickNode.Input -> listOf(this)
        is TfbiFlickNode.SubMenu -> nextMap.values.flatMap { it.inputs() }
        is TfbiFlickNode.StatefulKey ->
            listOfNotNull(normalMap, dakutenMap, handakutenMap)
                .flatMap { map -> map.values.flatMap { it.inputs() } }
    }

    companion object {
        private val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
        private val styles = listOf("second-flick")
    }
}
