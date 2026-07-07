package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class KanaRowFlickDefinitionGeneratorTest {
    @Test
    fun generatesKaRowDefinitionWithNormalDakutenAndNumberCandidates() {
        val definition = KanaRowFlickDefinitionGenerator.create(
            KanaRowSpec(
                id = "kana_ka",
                label = "か",
                number = "2",
                normal = KanaSeries("か", "き", "く", "け", "こ"),
                dakuten = KanaSeries("が", "ぎ", "ぐ", "げ", "ご"),
                iColumnModeSwitchBoundary = ModeSwitchBoundary.I_COLUMN_DIACRITIC
            )
        )

        val statefulKey = FlickKeyDefinitionTfbiMapper.toStatefulKey(definition)
        val normalKiNode = statefulKey.normalMap.subMenu(TfbiFlickDirection.LEFT)
        val normalKi = normalKiNode.nextMap
        val gi = normalKi.input(TfbiFlickDirection.DOWN_LEFT)
        val normalKyo = normalKi.branch(TfbiFlickDirection.DOWN)
        val normalKo = statefulKey.normalMap.branch(TfbiFlickDirection.DOWN)
        val dakutenMap = requireNotNull(statefulKey.dakutenMap)
        val dakutenGi = dakutenMap.branch(TfbiFlickDirection.LEFT)
        val dakutenGyo = dakutenGi.branch(TfbiFlickDirection.DOWN)
        val dakutenGu = dakutenMap.branch(TfbiFlickDirection.UP)
        val dakutenGo = dakutenMap.branch(TfbiFlickDirection.DOWN)
        val kiFromDakuten = dakutenGi.input(TfbiFlickDirection.UP_LEFT)
        val kuFromDakuten = dakutenGu.input(TfbiFlickDirection.UP)

        assertEquals("か", statefulKey.label)
        assertNull(statefulKey.handakutenMap)
        assertEquals("きょう", normalKyo.input(TfbiFlickDirection.DOWN_LEFT).char)
        assertEquals("2", normalKo.input(TfbiFlickDirection.LEFT).char)
        assertFalse(normalKiNode.cancelOnTap)
        assertEquals("ぎ", gi.char)
        assertEquals(KeyMode.DAKUTEN, gi.triggersMode)
        assertEquals(ModeSwitchBoundary.I_COLUMN_DIACRITIC, gi.modeSwitchBoundary)
        assertEquals("き", kiFromDakuten.char)
        assertEquals(KeyMode.NORMAL, kiFromDakuten.triggersMode)
        assertEquals("く", kuFromDakuten.char)
        assertEquals(KeyMode.NORMAL, kuFromDakuten.triggersMode)
        assertEquals("ぎょう", dakutenGyo.input(TfbiFlickDirection.DOWN_LEFT).char)
        assertEquals("ごう", dakutenGo.input(TfbiFlickDirection.RIGHT).char)
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
