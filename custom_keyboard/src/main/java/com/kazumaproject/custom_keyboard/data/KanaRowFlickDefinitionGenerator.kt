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
    val dakuten: KanaSeries? = null,
    val handakuten: KanaSeries? = null,
    val iColumnModeSwitchBoundary: ModeSwitchBoundary = ModeSwitchBoundary.NONE
)

@DslMarker
private annotation class KanaRowFlickDefinitionDsl

object KanaRowFlickDefinitionGenerator {
    fun create(spec: KanaRowSpec): FlickKeyDefinition {
        require(spec.handakuten == null) {
            "Handakuten row generation is not supported yet."
        }
        return FlickKeyDefinition(
            id = spec.id,
            label = spec.label,
            normal = createNormalStage(spec),
            dakuten = spec.dakuten?.let { createDakutenStage(spec, it) },
            handakuten = null
        )
    }

    private fun createNormalStage(spec: KanaRowSpec): FlickStageDefinition = stage {
        add(TAP, input(spec.normal.a))
        spec.dakuten?.let { dakuten ->
            add(UP_RIGHT, dakutenAColumn(spec.normal, dakuten))
        }
        add(LEFT, branch(label = spec.normal.i, cancelOnTap = false) {
            add(LEFT, input(spec.normal.i, nextState = FlickKeyState.NORMAL))
            spec.dakuten?.let { dakuten ->
                add(
                    DOWN_LEFT,
                    input(
                        text = dakuten.i,
                        nextState = FlickKeyState.DAKUTEN,
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
            spec.dakuten?.let { dakuten ->
                add(UP_LEFT, input(dakuten.u, nextState = FlickKeyState.DAKUTEN))
            }
            add(UP_RIGHT, input("${spec.normal.u}う"))
        })
        add(RIGHT, branch(label = spec.normal.e) {
            add(RIGHT, input(spec.normal.e))
            spec.dakuten?.let { dakuten ->
                add(DOWN_RIGHT, input(dakuten.e, nextState = FlickKeyState.DAKUTEN))
            }
        })
        add(DOWN, branch(label = spec.normal.o) {
            add(DOWN, input(spec.normal.o))
            spec.dakuten?.let { dakuten ->
                add(DOWN_RIGHT, input(dakuten.o, nextState = FlickKeyState.DAKUTEN))
            }
            add(DOWN_LEFT, input("${spec.normal.o}う"))
            spec.number?.let { number ->
                add(LEFT, input(number))
            }
        })
    }

    private fun createDakutenStage(
        spec: KanaRowSpec,
        dakuten: KanaSeries
    ): FlickStageDefinition = stage {
        add(TAP, input(spec.normal.a))
        add(UP_RIGHT, dakutenAColumn(spec.normal, dakuten))
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
            add(LEFT, input("${dakuten.u}う"))
        })
        add(RIGHT, branch {
            add(DOWN_RIGHT, input(dakuten.e))
            add(RIGHT, input(spec.normal.e, nextState = FlickKeyState.NORMAL))
        })
        add(DOWN, branch {
            add(DOWN_RIGHT, input(dakuten.o))
            add(DOWN, input(spec.normal.o, nextState = FlickKeyState.NORMAL))
            add(RIGHT, input("${dakuten.o}う"))
        })
    }

    private fun dakutenAColumn(
        normal: KanaSeries,
        dakuten: KanaSeries
    ): FlickNodeDefinition.Branch {
        return branch(label = dakuten.a) {
            add(TAP, input(normal.a))
            add(UP_RIGHT, input(dakuten.a))
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
    }
}
