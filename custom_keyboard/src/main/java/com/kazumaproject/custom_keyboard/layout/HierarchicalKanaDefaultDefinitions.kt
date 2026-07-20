package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickEntryDefinition
import com.kazumaproject.custom_keyboard.data.FlickKeyDefinition
import com.kazumaproject.custom_keyboard.data.FlickKeyDefinitionTfbiMapper
import com.kazumaproject.custom_keyboard.data.FlickKeyState
import com.kazumaproject.custom_keyboard.data.FlickNodeDefinition
import com.kazumaproject.custom_keyboard.data.FlickStageDefinition
import com.kazumaproject.custom_keyboard.data.KanaRowDiacritics
import com.kazumaproject.custom_keyboard.data.KanaRowFlickDefinitionGenerator
import com.kazumaproject.custom_keyboard.data.KanaRowSpec
import com.kazumaproject.custom_keyboard.data.KanaRowStageOverrides
import com.kazumaproject.custom_keyboard.data.KanaSeries
import com.kazumaproject.custom_keyboard.data.ModeSwitchBoundary
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.data.kanaRowStageOverrides
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

internal object HierarchicalKanaDefaultDefinitions {
    fun createFlickMaps(): Map<String, TfbiFlickNode.StatefulKey> = mapOf(
        "あ" to createStatefulKey(createHierarchicalARowKeyDefinition()),
        "か" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_ka",
                label = "か",
                number = "2",
                normal = KanaSeries("か", "き", "く", "け", "こ"),
                diacritics = KanaRowDiacritics.Dakuten(
                    dakuten = KanaSeries("が", "ぎ", "ぐ", "げ", "ご")
                )
            )
        ),
        "さ" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_sa",
                label = "さ",
                number = "3",
                normal = KanaSeries("さ", "し", "す", "せ", "そ"),
                diacritics = KanaRowDiacritics.Dakuten(
                    dakuten = KanaSeries("ざ", "じ", "ず", "ぜ", "ぞ")
                )
            )
        ),
        "た" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_ta",
                label = "た",
                number = "4",
                normal = KanaSeries("た", "ち", "つ", "て", "と"),
                diacritics = KanaRowDiacritics.Dakuten(
                    dakuten = KanaSeries("だ", "ぢ", "づ", "で", "ど")
                ),
                normalOverrides = createTaRowNormalOverrides(),
                dakutenOverrides = createTaRowDakutenOverrides()
            )
        ),
        "な" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_na",
                label = "な",
                number = "5",
                normal = KanaSeries("な", "に", "ぬ", "ね", "の")
            )
        ),
        "は" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_ha",
                label = "は",
                number = "6",
                normal = KanaSeries("は", "ひ", "ふ", "へ", "ほ"),
                diacritics = KanaRowDiacritics.DakutenAndHandakuten(
                    dakuten = KanaSeries("ば", "び", "ぶ", "べ", "ぼ"),
                    handakuten = KanaSeries("ぱ", "ぴ", "ぷ", "ぺ", "ぽ")
                ),
                normalOverrides = createHaRowNormalOverrides()
            )
        ),
        "ま" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_ma",
                label = "ま",
                number = "7",
                normal = KanaSeries("ま", "み", "む", "め", "も")
            )
        ),
        "や" to createStatefulKey(createHierarchicalYaRowKeyDefinition()),
        "ら" to createStatefulKey(
            createHierarchicalKanaRowKeyDefinition(
                id = "kana_ra",
                label = "ら",
                number = "9",
                normal = KanaSeries("ら", "り", "る", "れ", "ろ")
            )
        ),
        "わ" to createStatefulKey(createHierarchicalWaRowKeyDefinition()),
        "、。?!" to createStatefulKey(createHierarchicalSymbolKeyDefinition())
    )

    private fun createStatefulKey(
        definition: FlickKeyDefinition
    ): TfbiFlickNode.StatefulKey = FlickKeyDefinitionTfbiMapper.toStatefulKey(definition)

    private fun createHierarchicalKanaRowKeyDefinition(
        id: String,
        label: String,
        number: String? = null,
        normal: KanaSeries,
        diacritics: KanaRowDiacritics = KanaRowDiacritics.None,
        normalOverrides: KanaRowStageOverrides = KanaRowStageOverrides(),
        dakutenOverrides: KanaRowStageOverrides = KanaRowStageOverrides(),
        handakutenOverrides: KanaRowStageOverrides = KanaRowStageOverrides()
    ) = KanaRowFlickDefinitionGenerator.create(
        KanaRowSpec(
            id = id,
            label = label,
            number = number,
            normal = normal,
            diacritics = diacritics,
            iColumnModeSwitchBoundary = ModeSwitchBoundary.I_COLUMN_DIACRITIC,
            normalOverrides = normalOverrides,
            dakutenOverrides = dakutenOverrides,
            handakutenOverrides = handakutenOverrides
        )
    )

    private fun createTaRowNormalOverrides() = kanaRowStageOverrides {
        uColumn {
            upsert(TfbiFlickDirection.UP_RIGHT, kanaRowInput("っ"))
        }
        eColumn {
            upsert(
                TfbiFlickDirection.UP_RIGHT,
                kanaRowBranch(label = "てぃ") {
                    add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("てぃ"))
                    add(TfbiFlickDirection.UP, kanaRowInput("てぃー"))
                }
            )
        }
    }

    private fun createTaRowDakutenOverrides() = kanaRowStageOverrides {
        uColumn {
            upsert(TfbiFlickDirection.UP_RIGHT, kanaRowInput("っ"))
            remove(TfbiFlickDirection.LEFT)
        }
        eColumn {
            upsert(TfbiFlickDirection.DOWN, kanaRowInput("でぃ"))
            upsert(TfbiFlickDirection.DOWN_LEFT, kanaRowInput("でぃー"))
        }
    }

    private fun createHaRowNormalOverrides() = kanaRowStageOverrides {
        uColumn {
            upsert(TfbiFlickDirection.RIGHT, kanaRowInput("ふう"))
            upsert(
                TfbiFlickDirection.LEFT,
                kanaRowBranch(label = "ふぁ") {
                    add(TfbiFlickDirection.LEFT, kanaRowInput("ふぁ"))
                    add(TfbiFlickDirection.UP, kanaRowInput("ふぃ"))
                    add(TfbiFlickDirection.RIGHT, kanaRowInput("ふぇ"))
                    add(TfbiFlickDirection.DOWN, kanaRowInput("ふぉ"))
                }
            )
        }
        oColumn {
            upsert(TfbiFlickDirection.RIGHT, kanaRowInput("ほう"))
        }
    }

    private fun createStaticFlickKeyDefinition(
        id: String,
        label: String,
        block: KanaRowStageBuilder.() -> Unit
    ) = FlickKeyDefinition(
        id = id,
        label = label,
        normal = kanaRowStage(block)
    )

    private fun createHierarchicalARowKeyDefinition() = createStaticFlickKeyDefinition(
        id = "kana_a",
        label = "あ"
    ) {
        add(TfbiFlickDirection.TAP, kanaRowInput("あ"))
        add(TfbiFlickDirection.UP_RIGHT, kanaRowBranch(label = "ぁ") {
            add(TfbiFlickDirection.TAP, kanaRowInput("あ"))
            add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("ぁ"))
        })
        add(TfbiFlickDirection.LEFT, kanaRowBranch(label = "い") {
            add(TfbiFlickDirection.LEFT, kanaRowInput("い"))
            add(TfbiFlickDirection.DOWN_LEFT, kanaRowInput("ぃ"))
        })
        add(TfbiFlickDirection.UP, kanaRowBranch(label = "う") {
            add(TfbiFlickDirection.UP, kanaRowInput("う"))
            add(TfbiFlickDirection.UP_LEFT, kanaRowInput("ぅ"))
            add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("ゔ"))
        })
        add(TfbiFlickDirection.RIGHT, kanaRowBranch(label = "え") {
            add(TfbiFlickDirection.RIGHT, kanaRowInput("え"))
            add(TfbiFlickDirection.DOWN_RIGHT, kanaRowInput("ぇ"))
        })
        add(TfbiFlickDirection.DOWN, kanaRowBranch(label = "お") {
            add(TfbiFlickDirection.DOWN, kanaRowInput("お"))
            add(TfbiFlickDirection.DOWN_RIGHT, kanaRowInput("ぉ"))
            add(TfbiFlickDirection.DOWN_LEFT, kanaRowInput("1"))
        })
    }

    private fun createHierarchicalYaRowKeyDefinition() = createStaticFlickKeyDefinition(
        id = "kana_ya",
        label = "や"
    ) {
        add(TfbiFlickDirection.TAP, kanaRowInput("や"))
        add(TfbiFlickDirection.UP_RIGHT, kanaRowBranch(label = "ゃ") {
            add(TfbiFlickDirection.TAP, kanaRowInput("や"))
            add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("ゃ"))
        })
        add(TfbiFlickDirection.LEFT, kanaRowBranch(label = "(") {
            add(TfbiFlickDirection.LEFT, kanaRowInput("("))
            add(TfbiFlickDirection.DOWN_LEFT, kanaRowInput("「"))
        })
        add(TfbiFlickDirection.UP, kanaRowBranch(label = "ゆ") {
            add(TfbiFlickDirection.UP, kanaRowInput("ゆ"))
            add(TfbiFlickDirection.UP_LEFT, kanaRowInput("ゅ"))
            add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("ゆう"))
        })
        add(TfbiFlickDirection.RIGHT, kanaRowBranch(label = ")") {
            add(TfbiFlickDirection.RIGHT, kanaRowInput(")"))
            add(TfbiFlickDirection.DOWN_RIGHT, kanaRowInput("」"))
        })
        add(TfbiFlickDirection.DOWN, kanaRowBranch(label = "よ") {
            add(TfbiFlickDirection.DOWN, kanaRowInput("よ"))
            add(TfbiFlickDirection.DOWN_RIGHT, kanaRowInput("ょ"))
            add(TfbiFlickDirection.DOWN_LEFT, kanaRowInput("よう"))
            add(TfbiFlickDirection.LEFT, kanaRowInput("8"))
        })
    }

    private fun createHierarchicalWaRowKeyDefinition() = createStaticFlickKeyDefinition(
        id = "kana_wa",
        label = "わ"
    ) {
        add(TfbiFlickDirection.TAP, kanaRowInput("わ"))
        add(TfbiFlickDirection.UP_RIGHT, kanaRowBranch(label = "ゎ") {
            add(TfbiFlickDirection.TAP, kanaRowInput("わ"))
            add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("ゎ"))
        })
        add(TfbiFlickDirection.LEFT, kanaRowBranch(label = "を") {
            add(TfbiFlickDirection.LEFT, kanaRowInput("を"))
        })
        add(TfbiFlickDirection.UP, kanaRowBranch(label = "ん") {
            add(TfbiFlickDirection.UP, kanaRowInput("ん"))
        })
        add(TfbiFlickDirection.RIGHT, kanaRowBranch(label = "ー") {
            add(TfbiFlickDirection.RIGHT, kanaRowInput("ー"))
        })
        add(TfbiFlickDirection.DOWN, kanaRowBranch(label = "〜") {
            add(TfbiFlickDirection.DOWN, kanaRowInput("〜"))
            add(TfbiFlickDirection.DOWN_LEFT, kanaRowInput("0"))
        })
    }

    private fun createHierarchicalSymbolKeyDefinition() = createStaticFlickKeyDefinition(
        id = "kana_symbol",
        label = "、"
    ) {
        add(TfbiFlickDirection.TAP, kanaRowInput("、"))
        add(TfbiFlickDirection.LEFT, kanaRowBranch(label = "。") {
            add(TfbiFlickDirection.LEFT, kanaRowInput("。"))
        })
        add(TfbiFlickDirection.UP, kanaRowBranch(label = "？") {
            add(TfbiFlickDirection.UP, kanaRowInput("？"))
            add(TfbiFlickDirection.UP_LEFT, kanaRowInput("："))
            add(TfbiFlickDirection.UP_RIGHT, kanaRowInput("・"))
        })
        add(TfbiFlickDirection.RIGHT, kanaRowBranch(label = "！") {
            add(TfbiFlickDirection.RIGHT, kanaRowInput("！"))
        })
        add(TfbiFlickDirection.DOWN, kanaRowBranch(label = "…") {
            add(TfbiFlickDirection.DOWN, kanaRowInput("…"))
        })
    }

    private fun kanaRowInput(
        text: String,
        nextState: FlickKeyState? = null,
        modeSwitchBoundary: ModeSwitchBoundary = ModeSwitchBoundary.NONE
    ) = FlickNodeDefinition.Input(
        output = text,
        nextState = nextState,
        modeSwitchBoundary = modeSwitchBoundary
    )

    private fun kanaRowBranch(
        label: String? = null,
        cancelOnTap: Boolean = true,
        block: KanaRowStageBuilder.() -> Unit
    ) = FlickNodeDefinition.Branch(
        label = label,
        stage = kanaRowStage(block),
        cancelOnTap = cancelOnTap
    )

    private fun kanaRowStage(
        block: KanaRowStageBuilder.() -> Unit
    ) = FlickStageDefinition(
        entries = buildList {
            KanaRowStageBuilder(this).block()
        }
    )

    private class KanaRowStageBuilder(
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
