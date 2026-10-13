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
    fun threeStepPresetsKeepModeSwitchMetadataAndAllThreeModeMaps() {
        layoutTypes.forEach { layoutType ->
            val clockwise = layout("third-flick", layoutType, TfbiFlickRotation.CLOCKWISE)
                .hierarchicalFlickMaps.getValue("は")
            assertEquals("び", clockwise.normalMap.branch(LEFT).input(UP_LEFT).char)
            assertEquals("ぴ", clockwise.normalMap.branch(LEFT).input(DOWN_LEFT).char)
            assertEquals("ぶ", clockwise.normalMap.branch(UP).input(UP_RIGHT).char)
            assertEquals("ぷ", clockwise.normalMap.branch(UP).input(UP_LEFT).char)
            assertEquals("ぼ", clockwise.normalMap.branch(DOWN).input(DOWN_LEFT).char)
            assertEquals("ぽ", clockwise.normalMap.branch(DOWN).input(DOWN_RIGHT).char)
            assertEquals(KeyMode.DAKUTEN, clockwise.normalMap.branch(LEFT).input(UP_LEFT).triggersMode)
            assertEquals(
                ModeSwitchBoundary.I_COLUMN_DIACRITIC,
                clockwise.normalMap.branch(LEFT).input(UP_LEFT).modeSwitchBoundary
            )
            assertEquals("び", clockwise.dakutenMap!!.branch(LEFT).input(UP_LEFT).char)
            assertEquals("ひ", clockwise.dakutenMap!!.branch(LEFT).input(DOWN_LEFT).char)
            assertEquals("ぴ", clockwise.handakutenMap!!.branch(LEFT).input(DOWN_LEFT).char)
            assertEquals("ひ", clockwise.handakutenMap!!.branch(LEFT).input(UP_LEFT).char)

            val counterclockwise = layout("third-flick", layoutType, TfbiFlickRotation.COUNTERCLOCKWISE)
                .hierarchicalFlickMaps.getValue("は")
            assertEquals("べ", counterclockwise.normalMap.branch(RIGHT).input(UP_RIGHT).char)
            assertEquals("ぺ", counterclockwise.normalMap.branch(RIGHT).input(DOWN_RIGHT).char)
            assertEquals("べ", counterclockwise.dakutenMap!!.branch(RIGHT).input(UP_RIGHT).char)
            assertEquals("ぺ", counterclockwise.handakutenMap!!.branch(RIGHT).input(DOWN_RIGHT).char)
        }
    }

    @Test
    fun yoonAndLongVowelsKeepTheirFixedDirections() {
        val original = layout("third-flick").hierarchicalFlickMaps.getValue("か")
        TfbiFlickRotation.entries.forEach { rotation ->
            val rotated = layout("third-flick", rotation = rotation).hierarchicalFlickMaps.getValue("か")
            val kyu = rotated.normalMap.branch(LEFT).subMenu(UP)
            assertEquals("きゅ", kyu.label)
            assertEquals("きゅ", kyu.nextMap.input(UP).char)
            assertEquals("きゅう", kyu.nextMap.input(UP_LEFT).char)
            assertEquals(setOf(UP, RIGHT, DOWN), kyu.parentContinuationDirections)
            assertEquals("きゃ", rotated.normalMap.branch(LEFT).input(RIGHT).char)
            assertEquals("きょ", rotated.normalMap.branch(LEFT).branch(DOWN).input(DOWN).char)
            listOf(UP, RIGHT, DOWN).forEach { direction ->
                assertEquals(original.normalMap.branch(LEFT)[direction], rotated.normalMap.branch(LEFT)[direction])
                assertEquals(original.dakutenMap!!.branch(LEFT)[direction], rotated.dakutenMap!!.branch(LEFT)[direction])
            }
        }
    }

    @Test
    fun relocatedDiagonalSubmenuKeepsItsDirectCommitAndCardinalLongVowel() {
        val counterclockwise = layout("third-flick", rotation = TfbiFlickRotation.COUNTERCLOCKWISE)
            .hierarchicalFlickMaps.getValue("た")
        assertEquals("てぃ", counterclockwise.normalMap.branch(RIGHT).branch(DOWN_RIGHT).input(DOWN_RIGHT).char)
        assertEquals(
            "てぃー",
            counterclockwise.normalMap.branch(RIGHT).branch(DOWN_RIGHT).input(UP).char
        )
        assertEquals("でぃ", counterclockwise.dakutenMap!!.branch(RIGHT).input(DOWN).char)
        assertEquals("でぃー", counterclockwise.dakutenMap!!.branch(RIGHT).input(DOWN_LEFT).char)
        val clockwise = layout("third-flick", rotation = TfbiFlickRotation.CLOCKWISE)
            .hierarchicalFlickMaps.getValue("は")
        assertEquals("ふう", clockwise.normalMap.branch(UP).input(RIGHT).char)
        assertEquals("ふぁ", clockwise.normalMap.branch(UP).branch(LEFT).input(LEFT).char)
        assertEquals("ふぇ", clockwise.normalMap.branch(UP).branch(LEFT).input(RIGHT).char)
    }

    @Test
    fun diagonalSwapRemapsOnlyContinuationMetadataInItsParentStage() {
        val nested = TfbiFlickNode.SubMenu(
            nextMap = mapOf(UP to TfbiFlickNode.Input("きゅ")),
            label = "きゅ",
            cancelOnTap = true,
            parentContinuationDirections = setOf(DOWN_LEFT, RIGHT)
        )
        val rootBranch = TfbiFlickNode.SubMenu(
            nextMap = mapOf(UP to nested, DOWN_LEFT to TfbiFlickNode.Input("ぎ")),
            label = "き",
            cancelOnTap = true,
            parentContinuationDirections = setOf(UP)
        )
        val base = layout("third-flick").copy(
            hierarchicalFlickMaps = mapOf(
                "か" to TfbiFlickNode.StatefulKey(
                    normalMap = mapOf(LEFT to rootBranch), label = "か"
                )
            )
        )
        val result = SumireFlickRotationMapper.apply(base, TfbiFlickRotation.CLOCKWISE)
            .hierarchicalFlickMaps.getValue("か").normalMap.subMenu(LEFT)
        assertEquals(setOf(UP), result.parentContinuationDirections)
        assertEquals(rootBranch.label, result.label)
        assertEquals(rootBranch.cancelOnTap, result.cancelOnTap)
        assertEquals(setOf(UP_LEFT, RIGHT), result.nextMap.subMenu(UP).parentContinuationDirections)
        assertSame(nested.nextMap, result.nextMap.subMenu(UP).nextMap)
        assertEquals(nested.label, result.nextMap.subMenu(UP).label)
        assertEquals(nested.cancelOnTap, result.nextMap.subMenu(UP).cancelOnTap)
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
        private val styles = listOf("second-flick", "third-flick")
    }
}
