package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.ModeSwitchBoundary
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/** Stable storage/backup representation, independent of sealed runtime classes and R8 names. */
data class HierarchicalFlickDefinition(
    @SerializedName("label") val label: String = "",
    @SerializedName("normal") val normal: Map<String, HierarchicalFlickEntry> = emptyMap(),
    @SerializedName("dakuten") val dakuten: Map<String, HierarchicalFlickEntry>? = null,
    @SerializedName("handakuten") val handakuten: Map<String, HierarchicalFlickEntry>? = null
)

data class HierarchicalFlickEntry(
    @SerializedName("type") val type: String? = null,
    @SerializedName("output") val output: String? = null,
    @SerializedName("nextMode") val nextMode: String? = null,
    @SerializedName("modeSwitchBoundary") val modeSwitchBoundary: String? = null,
    @SerializedName("children") val children: Map<String, HierarchicalFlickEntry>? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("cancelOnTap") val cancelOnTap: Boolean = false,
    @SerializedName("parentContinuationDirections") val parentContinuationDirections: Set<String> = emptySet()
)

object HierarchicalFlickCodec {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun encode(node: TfbiFlickNode.StatefulKey): String = gson.toJson(fromRuntime(node))

    fun decode(json: String): TfbiFlickNode.StatefulKey =
        toRuntime(requireNotNull(gson.fromJson(json, HierarchicalFlickDefinition::class.java)))

    fun fromRuntime(node: TfbiFlickNode.StatefulKey): HierarchicalFlickDefinition =
        HierarchicalFlickDefinition(
            label = node.label,
            normal = fromMap(node.normalMap),
            dakuten = node.dakutenMap?.let(::fromMap),
            handakuten = node.handakutenMap?.let(::fromMap)
        )

    fun toRuntime(definition: HierarchicalFlickDefinition): TfbiFlickNode.StatefulKey =
        TfbiFlickNode.StatefulKey(
            label = definition.label,
            normalMap = toMap(definition.normal, 1),
            dakutenMap = definition.dakuten?.let { toMap(it, 1) },
            handakutenMap = definition.handakuten?.let { toMap(it, 1) }
        )

    fun hasOutput(map: Map<TfbiFlickDirection, TfbiFlickNode>): Boolean = map.values.any {
        when (it) {
            is TfbiFlickNode.Input -> it.char.isNotEmpty()
            is TfbiFlickNode.SubMenu -> hasOutput(it.nextMap)
            is TfbiFlickNode.StatefulKey -> false
        }
    }

    private fun fromMap(map: Map<TfbiFlickDirection, TfbiFlickNode>): Map<String, HierarchicalFlickEntry> =
        map.mapKeys { it.key.name }.mapValues { (_, node) ->
            when (node) {
                is TfbiFlickNode.Input -> HierarchicalFlickEntry(
                    type = "input", output = node.char, nextMode = node.triggersMode?.name,
                    modeSwitchBoundary = node.modeSwitchBoundary.name
                )
                is TfbiFlickNode.SubMenu -> HierarchicalFlickEntry(
                    type = "submenu", children = fromMap(node.nextMap), label = node.label,
                    cancelOnTap = node.cancelOnTap,
                    parentContinuationDirections = node.parentContinuationDirections.map { it.name }.toSet()
                )
                is TfbiFlickNode.StatefulKey -> error("StatefulKey cannot be nested")
            }
        }

    private fun toMap(map: Map<String, HierarchicalFlickEntry>, depth: Int): Map<TfbiFlickDirection, TfbiFlickNode> {
        require(depth <= 3) { "A custom flick key supports at most three stages" }
        return map.map { (direction, entry) ->
            val parsedDirection = TfbiFlickDirection.valueOf(direction)
            val node = when (entry.type) {
                "input" -> {
                    require(entry.children == null) { "Input cannot contain children" }
                    TfbiFlickNode.Input(
                        char = requireNotNull(entry.output),
                        triggersMode = entry.nextMode?.let { KeyMode.valueOf(it) },
                        modeSwitchBoundary = entry.modeSwitchBoundary?.let { ModeSwitchBoundary.valueOf(it) }
                            ?: ModeSwitchBoundary.NONE
                    )
                }
                "submenu" -> {
                    require(parsedDirection != TfbiFlickDirection.TAP) { "Tap cannot open a submenu" }
                    TfbiFlickNode.SubMenu(
                        nextMap = toMap(requireNotNull(entry.children), depth + 1),
                        label = entry.label,
                        cancelOnTap = entry.cancelOnTap,
                        parentContinuationDirections = entry.parentContinuationDirections.map {
                            TfbiFlickDirection.valueOf(it)
                        }.toSet()
                    )
                }
                else -> error("Unknown hierarchical flick entry type: ${entry.type}")
            }
            parsedDirection to node
        }.toMap()
    }
}
