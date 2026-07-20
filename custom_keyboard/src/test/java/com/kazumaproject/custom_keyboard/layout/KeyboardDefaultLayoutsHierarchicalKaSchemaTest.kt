package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardDefaultLayoutsHierarchicalKaSchemaTest {
    @Test
    fun defaultHierarchicalLayoutKeepsRowSpecificCandidates() {
        val hierarchicalFlickMaps = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = emptyMap(),
            inputLayoutType = "flick",
            inputStyle = "third-flick"
        ).hierarchicalFlickMaps

        val aKey = hierarchicalFlickMaps.getValue("あ")
        val taKey = hierarchicalFlickMaps.getValue("た")
        val haKey = hierarchicalFlickMaps.getValue("は")
        val yaKey = hierarchicalFlickMaps.getValue("や")
        val waKey = hierarchicalFlickMaps.getValue("わ")
        val symbolKey = hierarchicalFlickMaps.getValue("、。?!")

        assertEquals(
            setOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "、。?!"),
            hierarchicalFlickMaps.keys
        )

        assertEquals(
            "ぁ",
            aKey.normalMap.branch(TfbiFlickDirection.UP_RIGHT)
                .input(TfbiFlickDirection.UP_RIGHT)
                .char
        )
        assertEquals(
            "ゔ",
            aKey.normalMap.branch(TfbiFlickDirection.UP)
                .input(TfbiFlickDirection.UP_RIGHT)
                .char
        )
        assertEquals(
            "1",
            aKey.normalMap.branch(TfbiFlickDirection.DOWN)
                .input(TfbiFlickDirection.DOWN_LEFT)
                .char
        )
        assertEquals(
            "っ",
            taKey.normalMap.branch(TfbiFlickDirection.UP)
                .input(TfbiFlickDirection.UP_RIGHT)
                .char
        )
        assertEquals(
            "てぃー",
            taKey.normalMap.branch(TfbiFlickDirection.RIGHT)
                .branch(TfbiFlickDirection.UP_RIGHT)
                .input(TfbiFlickDirection.UP)
                .char
        )
        assertEquals(
            "でぃ",
            taKey.dakutenMap!!.branch(TfbiFlickDirection.RIGHT)
                .input(TfbiFlickDirection.DOWN)
                .char
        )
        assertEquals(
            "ふぇ",
            haKey.normalMap.branch(TfbiFlickDirection.UP)
                .branch(TfbiFlickDirection.LEFT)
                .input(TfbiFlickDirection.RIGHT)
                .char
        )
        assertEquals(
            "ほう",
            haKey.normalMap.branch(TfbiFlickDirection.DOWN)
                .input(TfbiFlickDirection.RIGHT)
                .char
        )
        assertEquals(
            "ぷ",
            haKey.dakutenMap!!.branch(TfbiFlickDirection.UP)
                .input(TfbiFlickDirection.UP_RIGHT)
                .char
        )
        assertEquals(
            "ぽう",
            haKey.handakutenMap!!.branch(TfbiFlickDirection.DOWN)
                .input(TfbiFlickDirection.LEFT)
                .char
        )
        assertEquals(
            "「",
            yaKey.normalMap.branch(TfbiFlickDirection.LEFT)
                .input(TfbiFlickDirection.DOWN_LEFT)
                .char
        )
        assertEquals(
            "ゅ",
            yaKey.normalMap.branch(TfbiFlickDirection.UP)
                .input(TfbiFlickDirection.UP_LEFT)
                .char
        )
        assertEquals(
            "8",
            yaKey.normalMap.branch(TfbiFlickDirection.DOWN)
                .input(TfbiFlickDirection.LEFT)
                .char
        )
        assertEquals(
            "ゎ",
            waKey.normalMap.branch(TfbiFlickDirection.UP_RIGHT)
                .input(TfbiFlickDirection.UP_RIGHT)
                .char
        )
        assertEquals(
            "0",
            waKey.normalMap.branch(TfbiFlickDirection.DOWN)
                .input(TfbiFlickDirection.DOWN_LEFT)
                .char
        )
        assertEquals(
            "：",
            symbolKey.normalMap.branch(TfbiFlickDirection.UP)
                .input(TfbiFlickDirection.UP_LEFT)
                .char
        )
        assertEquals(
            "！",
            symbolKey.normalMap.branch(TfbiFlickDirection.RIGHT)
                .input(TfbiFlickDirection.RIGHT)
                .char
        )
    }

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.branch(
        direction: TfbiFlickDirection
    ): Map<TfbiFlickDirection, TfbiFlickNode> {
        return subMenu(direction).nextMap
    }

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.subMenu(
        direction: TfbiFlickDirection
    ): TfbiFlickNode.SubMenu {
        return getValue(direction) as TfbiFlickNode.SubMenu
    }

    private fun Map<TfbiFlickDirection, TfbiFlickNode>.input(
        direction: TfbiFlickDirection
    ): TfbiFlickNode.Input {
        return getValue(direction) as TfbiFlickNode.Input
    }
}
