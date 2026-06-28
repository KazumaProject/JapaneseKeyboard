package com.kazumaproject.custom_keyboard.data

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

data class KanaSeries(
    val a: String,
    val i: String,
    val u: String,
    val e: String,
    val o: String
)

data class KanaRowSpec(
    val id: String,
    val label: String,
    val number: String? = null,
    val normal: KanaSeries,
    val diacritics: KanaRowDiacritics = KanaRowDiacritics.None,
    val iColumnModeSwitchBoundary: ModeSwitchBoundary = ModeSwitchBoundary.NONE,
    val normalOverrides: KanaRowStageOverrides = KanaRowStageOverrides(),
    val dakutenOverrides: KanaRowStageOverrides = KanaRowStageOverrides(),
    val handakutenOverrides: KanaRowStageOverrides = KanaRowStageOverrides()
)

sealed interface KanaRowDiacritics {
    val dakuten: KanaSeries?
    val handakuten: KanaSeries?

    data object None : KanaRowDiacritics {
        override val dakuten: KanaSeries? = null
        override val handakuten: KanaSeries? = null
    }

    data class Dakuten(
        override val dakuten: KanaSeries
    ) : KanaRowDiacritics {
        override val handakuten: KanaSeries? = null
    }

    data class DakutenAndHandakuten(
        override val dakuten: KanaSeries,
        override val handakuten: KanaSeries
    ) : KanaRowDiacritics
}

data class KanaRowStageOverrides(
    val uColumn: KanaRowColumnOverrides = KanaRowColumnOverrides(),
    val eColumn: KanaRowColumnOverrides = KanaRowColumnOverrides(),
    val oColumn: KanaRowColumnOverrides = KanaRowColumnOverrides()
)

data class KanaRowColumnOverrides(
    val upserts: List<FlickEntryDefinition> = emptyList(),
    val removals: Set<TfbiFlickDirection> = emptySet()
)

fun kanaRowStageOverrides(
    block: KanaRowStageOverridesBuilder.() -> Unit
): KanaRowStageOverrides = KanaRowStageOverridesBuilder().apply(block).build()

@KanaRowFlickDefinitionDsl
class KanaRowStageOverridesBuilder {
    private val uColumnBuilder = KanaRowColumnOverridesBuilder()
    private val eColumnBuilder = KanaRowColumnOverridesBuilder()
    private val oColumnBuilder = KanaRowColumnOverridesBuilder()

    fun uColumn(block: KanaRowColumnOverridesBuilder.() -> Unit) {
        uColumnBuilder.apply(block)
    }

    fun eColumn(block: KanaRowColumnOverridesBuilder.() -> Unit) {
        eColumnBuilder.apply(block)
    }

    fun oColumn(block: KanaRowColumnOverridesBuilder.() -> Unit) {
        oColumnBuilder.apply(block)
    }

    fun build(): KanaRowStageOverrides = KanaRowStageOverrides(
        uColumn = uColumnBuilder.build(),
        eColumn = eColumnBuilder.build(),
        oColumn = oColumnBuilder.build()
    )
}

@KanaRowFlickDefinitionDsl
class KanaRowColumnOverridesBuilder {
    private val upserts = mutableListOf<FlickEntryDefinition>()
    private val removals = mutableSetOf<TfbiFlickDirection>()

    fun upsert(direction: TfbiFlickDirection, node: FlickNodeDefinition) {
        upserts += FlickEntryDefinition(direction = direction, node = node)
    }

    fun remove(direction: TfbiFlickDirection) {
        removals += direction
    }

    fun build(): KanaRowColumnOverrides = KanaRowColumnOverrides(
        upserts = upserts.toList(),
        removals = removals.toSet()
    )
}

@DslMarker
annotation class KanaRowFlickDefinitionDsl

object KanaRowFlickDefinitionGenerator {
    fun create(spec: KanaRowSpec): FlickKeyDefinition {
        return FlickKeyDefinition(
            id = spec.id,
            label = spec.label,
            normal = createNormalStage(spec),
            dakuten = spec.diacritics.dakuten?.let { createDakutenStage(spec, it) },
            handakuten = createHandakutenStageOrNull(spec)
        )
    }

    private fun createHandakutenStageOrNull(spec: KanaRowSpec): FlickStageDefinition? {
        return when (val diacritics = spec.diacritics) {
            KanaRowDiacritics.None,
            is KanaRowDiacritics.Dakuten -> null

            is KanaRowDiacritics.DakutenAndHandakuten -> createHandakutenStage(
                spec = spec,
                dakuten = diacritics.dakuten,
                handakuten = diacritics.handakuten
            )
        }
    }

    private fun createNormalStage(spec: KanaRowSpec): FlickStageDefinition = stage {
        add(TAP, input(spec.normal.a))
        spec.diacritics.dakuten?.let { dakuten ->
            add(UP_RIGHT, modeAColumn(spec.normal.a, dakuten.a, UP_RIGHT))
        }
        spec.diacritics.handakuten?.let { handakuten ->
            add(UP_LEFT, modeAColumn(spec.normal.a, handakuten.a, UP_LEFT))
        }
        add(LEFT, branch(label = spec.normal.i, cancelOnTap = false) {
            add(LEFT, input(spec.normal.i, nextState = FlickKeyState.NORMAL))
            spec.diacritics.dakuten?.let { dakuten ->
                add(
                    DOWN_LEFT,
                    input(
                        text = dakuten.i,
                        nextState = FlickKeyState.DAKUTEN,
                        modeSwitchBoundary = spec.iColumnModeSwitchBoundary
                    )
                )
            }
            spec.diacritics.handakuten?.let { handakuten ->
                add(
                    UP_LEFT,
                    input(
                        text = handakuten.i,
                        nextState = FlickKeyState.HANDAKUTEN,
                        modeSwitchBoundary = spec.iColumnModeSwitchBoundary
                    )
                )
            }
            add(UP, yoonBranch("${spec.normal.i}ゅ"))
            add(RIGHT, input("${spec.normal.i}ゃ"))
            add(DOWN, yoonBranch("${spec.normal.i}ょ"))
        })
        add(UP, branch(label = spec.normal.u) {
            add(UP, input(spec.normal.u))
            spec.diacritics.dakuten?.let { dakuten ->
                add(UP_LEFT, input(dakuten.u, nextState = FlickKeyState.DAKUTEN))
            }
            spec.diacritics.handakuten?.let { handakuten ->
                add(UP_RIGHT, input(handakuten.u, nextState = FlickKeyState.HANDAKUTEN))
            } ?: add(UP_RIGHT, input("${spec.normal.u}う"))
            applyOverrides(spec.normalOverrides.uColumn)
        })
        add(RIGHT, branch(label = spec.normal.e) {
            add(RIGHT, input(spec.normal.e))
            spec.diacritics.dakuten?.let { dakuten ->
                add(DOWN_RIGHT, input(dakuten.e, nextState = FlickKeyState.DAKUTEN))
            }
            spec.diacritics.handakuten?.let { handakuten ->
                add(UP_RIGHT, input(handakuten.e, nextState = FlickKeyState.HANDAKUTEN))
            }
            applyOverrides(spec.normalOverrides.eColumn)
        })
        add(DOWN, branch(label = spec.normal.o) {
            add(DOWN, input(spec.normal.o))
            spec.diacritics.dakuten?.let { dakuten ->
                add(DOWN_RIGHT, input(dakuten.o, nextState = FlickKeyState.DAKUTEN))
            }
            spec.diacritics.handakuten?.let { handakuten ->
                add(DOWN_LEFT, input(handakuten.o, nextState = FlickKeyState.HANDAKUTEN))
            } ?: add(DOWN_LEFT, input("${spec.normal.o}う"))
            spec.number?.let { number ->
                add(LEFT, input(number))
            }
            applyOverrides(spec.normalOverrides.oColumn)
        })
    }

    private fun createDakutenStage(
        spec: KanaRowSpec,
        dakuten: KanaSeries
    ): FlickStageDefinition = stage {
        add(TAP, input(spec.normal.a))
        add(UP_RIGHT, modeAColumn(spec.normal.a, dakuten.a, UP_RIGHT))
        spec.diacritics.handakuten?.let { handakuten ->
            add(UP_LEFT, modeAColumn(spec.normal.a, handakuten.a, UP_LEFT))
        }
        add(LEFT, branch(cancelOnTap = false) {
            add(DOWN_LEFT, input(dakuten.i, nextState = FlickKeyState.DAKUTEN))
            add(UP_LEFT, input(spec.normal.i, nextState = FlickKeyState.NORMAL))
            add(UP, yoonBranch("${dakuten.i}ゅ"))
            add(RIGHT, input("${dakuten.i}ゃ"))
            add(DOWN, yoonBranch("${dakuten.i}ょ"))
        })
        add(UP, branch {
            add(UP_LEFT, input(dakuten.u))
            add(UP, input(spec.normal.u, nextState = FlickKeyState.NORMAL))
            spec.diacritics.handakuten?.let { handakuten ->
                add(UP_RIGHT, input(handakuten.u, nextState = FlickKeyState.HANDAKUTEN))
            }
            add(LEFT, input("${dakuten.u}う"))
            applyOverrides(spec.dakutenOverrides.uColumn)
        })
        add(RIGHT, branch {
            add(DOWN_RIGHT, input(dakuten.e))
            add(RIGHT, input(spec.normal.e, nextState = FlickKeyState.NORMAL))
            spec.diacritics.handakuten?.let { handakuten ->
                add(UP_RIGHT, input(handakuten.e, nextState = FlickKeyState.HANDAKUTEN))
            }
            applyOverrides(spec.dakutenOverrides.eColumn)
        })
        add(DOWN, branch {
            add(DOWN_RIGHT, input(dakuten.o))
            add(DOWN, input(spec.normal.o, nextState = FlickKeyState.NORMAL))
            spec.diacritics.handakuten?.let { handakuten ->
                add(DOWN_LEFT, input(handakuten.o, nextState = FlickKeyState.HANDAKUTEN))
            }
            add(RIGHT, input("${dakuten.o}う"))
            applyOverrides(spec.dakutenOverrides.oColumn)
        })
    }

    private fun createHandakutenStage(
        spec: KanaRowSpec,
        dakuten: KanaSeries,
        handakuten: KanaSeries
    ): FlickStageDefinition = stage {
        add(TAP, input(spec.normal.a))
        add(UP_RIGHT, modeAColumn(spec.normal.a, dakuten.a, UP_RIGHT))
        add(UP_LEFT, modeAColumn(spec.normal.a, handakuten.a, UP_LEFT))
        add(LEFT, branch(cancelOnTap = false) {
            add(UP_LEFT, input(handakuten.i, nextState = FlickKeyState.HANDAKUTEN))
            add(DOWN_LEFT, input(spec.normal.i, nextState = FlickKeyState.NORMAL))
            add(UP, yoonBranch("${handakuten.i}ゅ"))
            add(RIGHT, input("${handakuten.i}ゃ"))
            add(DOWN, yoonBranch("${handakuten.i}ょ"))
        })
        add(UP, branch {
            add(UP, input(spec.normal.u, nextState = FlickKeyState.NORMAL))
            add(UP_LEFT, input(dakuten.u, nextState = FlickKeyState.DAKUTEN))
            add(UP_RIGHT, input(handakuten.u, nextState = FlickKeyState.HANDAKUTEN))
            add(RIGHT, input("${handakuten.u}う"))
            applyOverrides(spec.handakutenOverrides.uColumn)
        })
        add(RIGHT, branch {
            add(RIGHT, input(spec.normal.e, nextState = FlickKeyState.NORMAL))
            add(DOWN_RIGHT, input(dakuten.e, nextState = FlickKeyState.DAKUTEN))
            add(UP_RIGHT, input(handakuten.e, nextState = FlickKeyState.HANDAKUTEN))
            applyOverrides(spec.handakutenOverrides.eColumn)
        })
        add(DOWN, branch {
            add(DOWN, input(spec.normal.o, nextState = FlickKeyState.NORMAL))
            add(DOWN_RIGHT, input(dakuten.o, nextState = FlickKeyState.DAKUTEN))
            add(DOWN_LEFT, input(handakuten.o, nextState = FlickKeyState.HANDAKUTEN))
            add(LEFT, input("${handakuten.o}う"))
            spec.number?.let { number ->
                add(UP_LEFT, input(number))
            }
            applyOverrides(spec.handakutenOverrides.oColumn)
        })
    }

    private fun modeAColumn(
        normal: String,
        mode: String,
        modeDirection: TfbiFlickDirection
    ): FlickNodeDefinition.Branch {
        return branch(label = mode) {
            add(TAP, input(normal))
            add(modeDirection, input(mode))
        }
    }

    private fun yoonBranch(text: String): FlickNodeDefinition.Branch {
        val longDirection = if (text.endsWith("ょ")) {
            DOWN_LEFT
        } else {
            UP_LEFT
        }
        val primaryDirection = if (text.endsWith("ょ")) {
            DOWN
        } else {
            UP
        }
        return branch(label = text) {
            add(primaryDirection, input(text))
            add(longDirection, input("${text}う"))
        }
    }

    private fun stage(
        block: FlickStageBuilder.() -> Unit
    ): FlickStageDefinition = FlickStageDefinition(
        entries = buildList {
            FlickStageBuilder(this).block()
        }
    )

    private fun input(
        text: String,
        nextState: FlickKeyState? = null,
        modeSwitchBoundary: ModeSwitchBoundary = ModeSwitchBoundary.NONE
    ): FlickNodeDefinition.Input = FlickNodeDefinition.Input(
        output = text,
        nextState = nextState,
        modeSwitchBoundary = modeSwitchBoundary
    )

    private fun branch(
        label: String? = null,
        cancelOnTap: Boolean = true,
        block: FlickStageBuilder.() -> Unit
    ): FlickNodeDefinition.Branch = FlickNodeDefinition.Branch(
        label = label,
        stage = stage(block),
        cancelOnTap = cancelOnTap
    )

    @KanaRowFlickDefinitionDsl
    private class FlickStageBuilder(
        private val entries: MutableList<FlickEntryDefinition>
    ) {
        fun add(
            direction: TfbiFlickDirection,
            node: FlickNodeDefinition
        ) {
            entries += FlickEntryDefinition(direction, node)
        }

        fun applyOverrides(overrides: KanaRowColumnOverrides) {
            if (overrides.removals.isNotEmpty()) {
                entries.removeAll { entry -> entry.direction in overrides.removals }
            }
            overrides.upserts.forEach { entry ->
                entries.upsertInPlace(entry)
            }
        }

        private fun MutableList<FlickEntryDefinition>.upsertInPlace(
            entry: FlickEntryDefinition
        ) {
            val index = indexOfFirst { current -> current.direction == entry.direction }
            if (index >= 0) {
                this[index] = entry
            } else {
                this += entry
            }
        }
    }
}
