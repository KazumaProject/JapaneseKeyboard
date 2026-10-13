package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import java.text.Normalizer

/** Filters three-step kana candidates while retaining ordinary/small kana and symbol keys. */
internal object SumireFlickInputModeMapper {
    private val kanaLabels = setOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ")

    fun apply(layout: KeyboardLayout, mode: SumireFlickInputMode): KeyboardLayout {
        if (mode == SumireFlickInputMode.ALL) return layout
        return layout.copy(
            hierarchicalFlickMaps = layout.hierarchicalFlickMaps.mapValues { (label, key) ->
                if (label !in kanaLabels) key else key.copy(
                    normalMap = filterStage(key.normalMap, mode),
                    dakutenMap = if (mode == SumireFlickInputMode.CONTRACTED) null
                        else key.dakutenMap?.let { filterStage(it, mode) },
                    handakutenMap = if (mode == SumireFlickInputMode.CONTRACTED) null
                        else key.handakutenMap?.let { filterStage(it, mode) }
                )
            }
        )
    }

    private fun filterStage(
        map: Map<TfbiFlickDirection, TfbiFlickNode>,
        mode: SumireFlickInputMode
    ): Map<TfbiFlickDirection, TfbiFlickNode> {
        val filtered = map.mapNotNull { (direction, node) ->
            filterNode(node, mode)?.let { direction to it }
        }.toMap()
        return filtered.mapValues { (_, node) ->
            if (node is TfbiFlickNode.SubMenu) node.copy(
                parentContinuationDirections = node.parentContinuationDirections.intersect(filtered.keys)
            ) else node
        }
    }

    private fun filterNode(node: TfbiFlickNode, mode: SumireFlickInputMode): TfbiFlickNode? = when (node) {
        is TfbiFlickNode.Input -> node.takeIf { isAllowed(it.char, mode) }
        is TfbiFlickNode.SubMenu -> {
            val children = filterStage(node.nextMap, mode)
            if (children.isEmpty() || node.label?.let { !isAllowed(it, mode) } == true) null
            else node.copy(nextMap = children)
        }
        is TfbiFlickNode.StatefulKey -> node.copy(
            normalMap = filterStage(node.normalMap, mode),
            dakutenMap = if (mode == SumireFlickInputMode.CONTRACTED) null
                else node.dakutenMap?.let { filterStage(it, mode) },
            handakutenMap = if (mode == SumireFlickInputMode.CONTRACTED) null
                else node.handakutenMap?.let { filterStage(it, mode) }
        )
    }

    private fun isAllowed(text: String, mode: SumireFlickInputMode): Boolean {
        if (mode == SumireFlickInputMode.ALL) return true
        if (text.isNotEmpty() && text.all { it in '0'..'9' }) return false
        return when (mode) {
            SumireFlickInputMode.ALL -> true
            SumireFlickInputMode.CONTRACTED -> Normalizer.normalize(text, Normalizer.Form.NFD)
                .none { it == '\u3099' || it == '\u309A' }
            SumireFlickInputMode.VOICED -> text.codePointCount(0, text.length) <= 1 ||
                text.none { it in '\u3041'..'\u3096' }
        }
    }
}
