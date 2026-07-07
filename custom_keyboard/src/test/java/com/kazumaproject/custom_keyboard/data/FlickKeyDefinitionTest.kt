package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickKeyDefinitionTest {
    @Test
    fun mapsHierarchicalDefinitionToTfbiStatefulKey() {
        val definition = FlickKeyDefinition(
            id = "kana_ka",
            label = "か",
            normal = FlickStageDefinition(
                entries = listOf(
                    FlickEntryDefinition(
                        direction = TfbiFlickDirection.TAP,
                        node = FlickNodeDefinition.Input("か")
                    ),
                    FlickEntryDefinition(
                        direction = TfbiFlickDirection.LEFT,
                        node = FlickNodeDefinition.Branch(
                            label = "き",
                            stage = FlickStageDefinition(
                                entries = listOf(
                                    FlickEntryDefinition(
                                        direction = TfbiFlickDirection.LEFT,
                                        node = FlickNodeDefinition.Input(
                                            output = "き",
                                            nextState = FlickKeyState.NORMAL
                                        )
                                    ),
                                    FlickEntryDefinition(
                                        direction = TfbiFlickDirection.DOWN_LEFT,
                                        node = FlickNodeDefinition.Input(
                                            output = "ぎ",
                                            nextState = FlickKeyState.DAKUTEN,
                                            modeSwitchBoundary =
                                                ModeSwitchBoundary.I_COLUMN_DIACRITIC
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val statefulKey = FlickKeyDefinitionTfbiMapper.toStatefulKey(definition)
        val leftNode = statefulKey.normalMap.getValue(TfbiFlickDirection.LEFT)
        val leftMap = (leftNode as TfbiFlickNode.SubMenu).nextMap
        val gi = leftMap.getValue(TfbiFlickDirection.DOWN_LEFT) as TfbiFlickNode.Input

        assertEquals("か", statefulKey.label)
        assertEquals("き", leftNode.label)
        assertEquals("ぎ", gi.char)
        assertEquals(KeyMode.DAKUTEN, gi.triggersMode)
        assertEquals(ModeSwitchBoundary.I_COLUMN_DIACRITIC, gi.modeSwitchBoundary)
    }

    @Test
    fun filtersEntriesByFeatureRecursively() {
        val definition = FlickKeyDefinition(
            id = "kana_a",
            label = "あ",
            normal = FlickStageDefinition(
                entries = listOf(
                    FlickEntryDefinition(
                        direction = TfbiFlickDirection.TAP,
                        node = FlickNodeDefinition.Input("あ")
                    ),
                    FlickEntryDefinition(
                        direction = TfbiFlickDirection.LEFT,
                        node = FlickNodeDefinition.Branch(
                            label = "い",
                            stage = FlickStageDefinition(
                                entries = listOf(
                                    FlickEntryDefinition(
                                        direction = TfbiFlickDirection.LEFT,
                                        node = FlickNodeDefinition.Input(
                                            "い"
                                        )
                                    ),
                                    FlickEntryDefinition(
                                        direction = TfbiFlickDirection.DOWN_LEFT,
                                        node = FlickNodeDefinition.Input(
                                            "ぃ"
                                        ),
                                        requiredFeature =
                                            FlickDefinitionFeature.A_ROW_SECOND_STAGE
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val disabled = definition.enabledFeatures(emptySet())
        val enabled = definition.enabledFeatures(setOf(FlickDefinitionFeature.A_ROW_SECOND_STAGE))
        val disabledLeftStage = (disabled.normal.entries
            .first { it.direction == TfbiFlickDirection.LEFT }
            .node as FlickNodeDefinition.Branch).stage
        val enabledLeftStage = (enabled.normal.entries
            .first { it.direction == TfbiFlickDirection.LEFT }
            .node as FlickNodeDefinition.Branch).stage

        assertFalse(disabledLeftStage.hasDirection(TfbiFlickDirection.DOWN_LEFT))
        assertTrue(enabledLeftStage.hasDirection(TfbiFlickDirection.DOWN_LEFT))
    }

    private fun FlickStageDefinition.hasDirection(direction: TfbiFlickDirection): Boolean {
        return entries.any { it.direction == direction }
    }
}
