package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.*
import org.junit.Test

class HierarchicalFlickCodecTest {
    @Test fun standardTemplateRoundTripsAllStatesAndGestureMetadata() {
        val maps = KeyboardDefaultLayouts.createThreeStageFlickKanaTemplateLayout().hierarchicalFlickMaps
        assertEquals(11, maps.size)
        maps.values.forEach { definition ->
            assertEquals(definition, HierarchicalFlickCodec.decode(HierarchicalFlickCodec.encode(definition)))
        }
    }

    @Test fun optionalBranchFieldsUseTheirDefaults() {
        val node = HierarchicalFlickCodec.decode("""{"normal":{"RIGHT":{"type":"submenu","children":{"UP":{"type":"input","output":"x"}}}}}""")
        val branch = node.normalMap.values.single() as com.kazumaproject.custom_keyboard.data.TfbiFlickNode.SubMenu
        assertTrue(branch.parentContinuationDirections.isEmpty())
        assertFalse(branch.cancelOnTap)
        assertTrue(HierarchicalFlickCodec.hasOutput(node.normalMap))
    }

    @Test fun fourthStageIsRejectedWithADepthError() {
        val leaf = HierarchicalFlickEntry(type = "input", output = "x")
        val third = HierarchicalFlickEntry(type = "submenu", children = mapOf("UP" to leaf))
        val second = HierarchicalFlickEntry(type = "submenu", children = mapOf("LEFT" to third))
        val first = HierarchicalFlickEntry(type = "submenu", children = mapOf("DOWN" to second))
        val error = runCatching { HierarchicalFlickCodec.toRuntime(HierarchicalFlickDefinition(normal = mapOf("RIGHT" to first))) }.exceptionOrNull()
        assertTrue(error.toString(), error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("three stages"))
    }

    @Test fun invalidNodeDirectionModeAndDepthAreRejected() {
        val invalid = listOf(
            """{"normal":{"RIGHT":{"type":"unknown"}}}""",
            """{"normal":{"INVALID":{"type":"input","output":"x"}}}""",
            """{"normal":{"RIGHT":{"type":"input","output":"x","nextMode":"INVALID"}}}""",
            """{"normal":{"TAP":{"type":"submenu","children":{}}}}""",
            """{"normal":{"RIGHT":{"type":"input"}}}""",
            """{"normal":{"RIGHT":{"type":"submenu","children":{"DOWN":{"type":"submenu","children":{"LEFT":{"type":"submenu","children":{}}}}}}}}"""
        )
        invalid.forEach { json -> assertTrue(json, runCatching { HierarchicalFlickCodec.decode(json) }.isFailure) }
    }
}
