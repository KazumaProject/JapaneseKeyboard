package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.HierarchicalFlickCodec
import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/** Immutable tree edits retain untouched branches and the template's gesture metadata. */
class HierarchicalFlickEditorState(definition: TfbiFlickNode.StatefulKey) {
    var definition = definition
        private set
    var mode: KeyMode = KeyMode.NORMAL
        private set
    var path: List<TfbiFlickDirection> = emptyList()
        private set

    val availableModes: List<KeyMode>
        get() = listOfNotNull(
            KeyMode.NORMAL,
            KeyMode.DAKUTEN.takeIf { definition.dakutenMap != null },
            KeyMode.HANDAKUTEN.takeIf { definition.handakutenMap != null }
        )

    fun selectMode(value: KeyMode) {
        require(value in availableModes)
        mode = value
        path = emptyList()
    }

    fun currentMap(): Map<TfbiFlickDirection, TfbiFlickNode> {
        var map = rootMap()
        path.forEach { map = (map.getValue(it) as TfbiFlickNode.SubMenu).nextMap }
        return map
    }

    fun open(direction: TfbiFlickDirection) {
        require(path.size < 2 && currentMap()[direction] is TfbiFlickNode.SubMenu)
        path = path + direction
    }

    fun back() { path = path.dropLast(1) }

    fun setOutput(direction: TfbiFlickDirection, output: String) {
        val old = currentMap()[direction] as? TfbiFlickNode.Input
        replace(direction, output.takeIf { it.isNotEmpty() }?.let {
            old?.copy(char = it) ?: TfbiFlickNode.Input(it)
        })
    }

    fun makeBranch(direction: TfbiFlickDirection) {
        require(direction != TfbiFlickDirection.TAP && direction != path.lastOrNull() && path.size < 2)
        val old = currentMap()[direction]
        if (old is TfbiFlickNode.SubMenu) return
        replace(direction, TfbiFlickNode.SubMenu(
            // The runtime keeps the entry direction highlighted when opening a stage.
            nextMap = if (old is TfbiFlickNode.Input) mapOf(direction to old) else emptyMap(),
            label = (old as? TfbiFlickNode.Input)?.char,
            cancelOnTap = true
        ))
    }

    fun setBranchLabel(direction: TfbiFlickDirection, label: String) {
        val old = currentMap().getValue(direction) as TfbiFlickNode.SubMenu
        replace(direction, old.copy(label = label.takeIf { it.isNotEmpty() }))
    }

    fun delete(direction: TfbiFlickDirection) = replace(direction, null)

    fun hasNormalOutput(): Boolean = HierarchicalFlickCodec.hasOutput(definition.normalMap)

    private fun rootMap() = when (mode) {
        KeyMode.NORMAL -> definition.normalMap
        KeyMode.DAKUTEN -> requireNotNull(definition.dakutenMap)
        KeyMode.HANDAKUTEN -> requireNotNull(definition.handakutenMap)
    }

    private fun replace(direction: TfbiFlickDirection, node: TfbiFlickNode?) {
        fun edit(map: Map<TfbiFlickDirection, TfbiFlickNode>, depth: Int): Map<TfbiFlickDirection, TfbiFlickNode> {
            val result = map.toMutableMap()
            if (depth == path.size) {
                if (node == null) result.remove(direction) else result[direction] = node
            } else {
                val entry = path[depth]
                val branch = map.getValue(entry) as TfbiFlickNode.SubMenu
                result[entry] = branch.copy(nextMap = edit(branch.nextMap, depth + 1))
            }
            return result
        }
        val edited = edit(rootMap(), 0)
        definition = when (mode) {
            KeyMode.NORMAL -> definition.copy(normalMap = edited)
            KeyMode.DAKUTEN -> definition.copy(dakutenMap = edited)
            KeyMode.HANDAKUTEN -> definition.copy(handakutenMap = edited)
        }
    }
}
