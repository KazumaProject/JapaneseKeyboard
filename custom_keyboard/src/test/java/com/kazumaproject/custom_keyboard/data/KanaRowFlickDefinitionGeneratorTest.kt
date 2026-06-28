package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class KanaRowFlickDefinitionGeneratorTest {
    @Test
    fun generatesDakutenRowContract() {
        val definition = KanaRowFlickDefinitionGenerator.create(
            KanaRowSpec(
                id = "kana_ka",
                label = "か",
                number = "2",
                normal = KanaSeries("か", "き", "く", "け", "こ"),
                diacritics = KanaRowDiacritics.Dakuten(
                    dakuten = KanaSeries("が", "ぎ", "ぐ", "げ", "ご")
                ),
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

    @Test
    fun appliesColumnScopedOverrides() {
        val definition = KanaRowFlickDefinitionGenerator.create(
            KanaRowSpec(
                id = "kana_ta",
                label = "た",
                number = "4",
                normal = KanaSeries("た", "ち", "つ", "て", "と"),
                diacritics = KanaRowDiacritics.Dakuten(
                    dakuten = KanaSeries("だ", "ぢ", "づ", "で", "ど")
                ),
                iColumnModeSwitchBoundary = ModeSwitchBoundary.I_COLUMN_DIACRITIC,
                normalOverrides = kanaRowStageOverrides {
                    uColumn {
                        upsert(TfbiFlickDirection.UP_RIGHT, input("っ"))
                    }
                    eColumn {
                        upsert(
                            TfbiFlickDirection.UP_RIGHT,
                            branch(label = "てぃ") {
                                add(TfbiFlickDirection.UP_RIGHT, input("てぃ"))
                                add(TfbiFlickDirection.UP, input("てぃー"))
                            }
                        )
                    }
                },
                dakutenOverrides = kanaRowStageOverrides {
                    uColumn {
                        upsert(TfbiFlickDirection.UP_RIGHT, input("っ"))
                        remove(TfbiFlickDirection.LEFT)
                    }
                    eColumn {
                        upsert(TfbiFlickDirection.DOWN, input("でぃ"))
                        upsert(TfbiFlickDirection.DOWN_LEFT, input("でぃー"))
                    }
                }
            )
        )

        val statefulKey = FlickKeyDefinitionTfbiMapper.toStatefulKey(definition)
        val normalTsu = statefulKey.normalMap.branch(TfbiFlickDirection.UP)
        val normalTe = statefulKey.normalMap.branch(TfbiFlickDirection.RIGHT)
        val dakutenDu = statefulKey.dakutenMap!!.branch(TfbiFlickDirection.UP)
        val dakutenDe = statefulKey.dakutenMap!!.branch(TfbiFlickDirection.RIGHT)

        assertEquals("っ", normalTsu.input(TfbiFlickDirection.UP_RIGHT).char)
        assertEquals(
            "てぃー",
            normalTe.branch(TfbiFlickDirection.UP_RIGHT)
                .input(TfbiFlickDirection.UP)
                .char
        )
        assertEquals("っ", dakutenDu.input(TfbiFlickDirection.UP_RIGHT).char)
        assertFalse(dakutenDu.containsKey(TfbiFlickDirection.LEFT))
        assertEquals("でぃ", dakutenDe.input(TfbiFlickDirection.DOWN).char)
        assertEquals("でぃー", dakutenDe.input(TfbiFlickDirection.DOWN_LEFT).char)
    }

    @Test
    fun generatesHandakutenRowContract() {
        val definition = KanaRowFlickDefinitionGenerator.create(
            KanaRowSpec(
                id = "kana_ha",
                label = "は",
                number = "6",
                normal = KanaSeries("は", "ひ", "ふ", "へ", "ほ"),
                diacritics = KanaRowDiacritics.DakutenAndHandakuten(
                    dakuten = KanaSeries("ば", "び", "ぶ", "べ", "ぼ"),
                    handakuten = KanaSeries("ぱ", "ぴ", "ぷ", "ぺ", "ぽ")
                ),
                iColumnModeSwitchBoundary = ModeSwitchBoundary.I_COLUMN_DIACRITIC
            )
        )

        val statefulKey = FlickKeyDefinitionTfbiMapper.toStatefulKey(definition)
        val normalHi = statefulKey.normalMap.branch(TfbiFlickDirection.LEFT)
        val normalFu = statefulKey.normalMap.branch(TfbiFlickDirection.UP)
        val normalHo = statefulKey.normalMap.branch(TfbiFlickDirection.DOWN)
        val dakutenBu = statefulKey.dakutenMap!!.branch(TfbiFlickDirection.UP)
        val handakutenPi = statefulKey.handakutenMap!!.branch(TfbiFlickDirection.LEFT)
        val handakutenPo = statefulKey.handakutenMap!!.branch(TfbiFlickDirection.DOWN)

        assertEquals("ぴ", normalHi.input(TfbiFlickDirection.UP_LEFT).char)
        assertEquals(KeyMode.HANDAKUTEN, normalHi.input(TfbiFlickDirection.UP_LEFT).triggersMode)
        assertEquals("ぷ", normalFu.input(TfbiFlickDirection.UP_RIGHT).char)
        assertEquals("ぽ", normalHo.input(TfbiFlickDirection.DOWN_LEFT).char)
        assertEquals("ぷ", dakutenBu.input(TfbiFlickDirection.UP_RIGHT).char)
        assertEquals("ぴゅう", handakutenPi.branch(TfbiFlickDirection.UP).input(TfbiFlickDirection.UP_LEFT).char)
        assertEquals("ぽう", handakutenPo.input(TfbiFlickDirection.LEFT).char)
        assertEquals("6", handakutenPo.input(TfbiFlickDirection.UP_LEFT).char)
    }

    @Test
    fun generatesPlainRowContract() {
        val definition = KanaRowFlickDefinitionGenerator.create(
            KanaRowSpec(
                id = "kana_na",
                label = "な",
                number = "5",
                normal = KanaSeries("な", "に", "ぬ", "ね", "の"),
                iColumnModeSwitchBoundary = ModeSwitchBoundary.I_COLUMN_DIACRITIC
            )
        )

        val statefulKey = FlickKeyDefinitionTfbiMapper.toStatefulKey(definition)
        val normalNiNode = statefulKey.normalMap.subMenu(TfbiFlickDirection.LEFT)
        val normalNi = normalNiNode.nextMap
        val normalNu = statefulKey.normalMap.branch(TfbiFlickDirection.UP)
        val normalNo = statefulKey.normalMap.branch(TfbiFlickDirection.DOWN)

        assertNull(statefulKey.dakutenMap)
        assertNull(statefulKey.handakutenMap)
        assertFalse(normalNiNode.cancelOnTap)
        assertEquals(
            "にゅう",
            normalNi.branch(TfbiFlickDirection.UP)
                .input(TfbiFlickDirection.UP_LEFT)
                .char
        )
        assertEquals("にゃ", normalNi.input(TfbiFlickDirection.RIGHT).char)
        assertEquals("ぬう", normalNu.input(TfbiFlickDirection.UP_RIGHT).char)
        assertEquals("のう", normalNo.input(TfbiFlickDirection.DOWN_LEFT).char)
        assertEquals("5", normalNo.input(TfbiFlickDirection.LEFT).char)
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

    private fun input(text: String) = FlickNodeDefinition.Input(
        output = text
    )

    private fun branch(
        label: String? = null,
        block: TestStageBuilder.() -> Unit
    ) = FlickNodeDefinition.Branch(
        label = label,
        stage = FlickStageDefinition(
            entries = buildList {
                TestStageBuilder(this).block()
            }
        )
    )

    private class TestStageBuilder(
        private val entries: MutableList<FlickEntryDefinition>
    ) {
        fun add(
            direction: TfbiFlickDirection,
            node: FlickNodeDefinition
        ) {
            entries += FlickEntryDefinition(direction = direction, node = node)
        }
    }
}
