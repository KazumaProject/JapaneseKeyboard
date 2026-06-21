package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * Definition-layer model for generating runtime [TfbiFlickNode] maps.
 */
data class FlickKeyDefinition(
    val id: String,
    val label: String,
    val normal: FlickStageDefinition,
    val dakuten: FlickStageDefinition? = null,
    val handakuten: FlickStageDefinition? = null
)

enum class FlickKeyState {
    NORMAL,
    DAKUTEN,
    HANDAKUTEN
}

enum class FlickDefinitionFeature {
    A_ROW_SECOND_STAGE
}

data class FlickStageDefinition(
    val entries: List<FlickEntryDefinition>
)

data class FlickEntryDefinition(
    val direction: TfbiFlickDirection,
    val node: FlickNodeDefinition,
    val requiredFeature: FlickDefinitionFeature? = null
)

sealed class FlickNodeDefinition {
    data class Input(
        val output: String,
        val nextState: FlickKeyState? = null,
        val modeSwitchBoundary: ModeSwitchBoundary = ModeSwitchBoundary.NONE
    ) : FlickNodeDefinition()

    data class Branch(
        val label: String? = null,
        val stage: FlickStageDefinition,
        val cancelOnTap: Boolean = true
    ) : FlickNodeDefinition()
}

fun FlickKeyDefinition.enabledFeatures(
    features: Set<FlickDefinitionFeature>
): FlickKeyDefinition = copy(
    normal = normal.enabledFeatures(features),
    dakuten = dakuten?.enabledFeatures(features),
    handakuten = handakuten?.enabledFeatures(features)
)

fun FlickStageDefinition.enabledFeatures(
    features: Set<FlickDefinitionFeature>
): FlickStageDefinition {
    return copy(
        entries = entries
            .filter { entry -> entry.requiredFeature == null || entry.requiredFeature in features }
            .map { entry ->
                entry.copy(
                    node = when (val node = entry.node) {
                        is FlickNodeDefinition.Branch ->
                            node.copy(stage = node.stage.enabledFeatures(features))

                        is FlickNodeDefinition.Input -> node
                    }
                )
            }
    )
}

object FlickKeyDefinitionTfbiMapper {
    fun toStatefulKey(definition: FlickKeyDefinition): TfbiFlickNode.StatefulKey {
        return TfbiFlickNode.StatefulKey(
            normalMap = definition.normal.toTfbiMap(),
            dakutenMap = definition.dakuten?.toTfbiMap(),
            handakutenMap = definition.handakuten?.toTfbiMap(),
            label = definition.label
        )
    }

    private fun FlickStageDefinition.toTfbiMap(): Map<TfbiFlickDirection, TfbiFlickNode> {
        return entries.associate { entry ->
            entry.direction to entry.node.toTfbiNode()
        }
    }

    private fun FlickNodeDefinition.toTfbiNode(): TfbiFlickNode {
        return when (this) {
            is FlickNodeDefinition.Input -> TfbiFlickNode.Input(
                char = output,
                triggersMode = nextState?.toKeyMode(),
                modeSwitchBoundary = modeSwitchBoundary
            )

            is FlickNodeDefinition.Branch -> TfbiFlickNode.SubMenu(
                nextMap = stage.toTfbiMap(),
                label = label,
                cancelOnTap = cancelOnTap
            )
        }
    }

    private fun FlickKeyState.toKeyMode(): KeyMode {
        return when (this) {
            FlickKeyState.NORMAL -> KeyMode.NORMAL
            FlickKeyState.DAKUTEN -> KeyMode.DAKUTEN
            FlickKeyState.HANDAKUTEN -> KeyMode.HANDAKUTEN
        }
    }
}
