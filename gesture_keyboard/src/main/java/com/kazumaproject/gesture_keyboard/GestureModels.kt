package com.kazumaproject.gesture_keyboard

import android.content.Context
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper

data class GesturePoint(val x: Float, val y: Float)
enum class GestureSide { LEFT, RIGHT }
sealed class GestureOutput {
    data class Reading(val text: String) : GestureOutput()
    data class LiteralText(val text: String) : GestureOutput()
    data class Romaji(val text: String) : GestureOutput()
    data class Command(val action: KeyAction) : GestureOutput()
}
data class GestureEntry(val id: String, val output: GestureOutput, val samples: List<List<GesturePoint>>) {
    fun validate() {
        require(id.isNotBlank() && samples.size in 1..16)
        when (output) {
            is GestureOutput.Reading -> require(output.text.codePointCount(0, output.text.length) == 1 &&
                output.text.singleOrNull()?.let { it in '\u3041'..'\u3096' || it in "ー、。" } == true)
            is GestureOutput.LiteralText -> require(output.text.codePointCount(0, output.text.length) == 1 &&
                output.text.none { it.isISOControl() })
            is GestureOutput.Romaji -> require(output.text.isNotEmpty() && output.text.all { it in 'a'..'z' || it in 'A'..'Z' })
            is GestureOutput.Command -> require(KeyActionMapper.fromKeyAction(output.action) != null)
        }
        samples.forEach { points ->
            require(points.size in 2..512 && points.all { it.x.isFinite() && it.y.isFinite() && kotlin.math.abs(it.x) <= 100000 && kotlin.math.abs(it.y) <= 100000 })
            require(GestureRecognizer.normalize(points) != null) { "Empty stroke" }
        }
    }
}
data class GestureSet(val id: String, val name: String, val entries: List<GestureEntry>, val builtIn: Boolean = false, val isRomaji: Boolean = false) {
    fun validate() {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 100 && entries.size <= 256)
        require(entries.map { it.id }.distinct().size == entries.size)
        entries.forEach { it.validate() }
    }
}
/** Bounds are relative to the drawing canvas, excluding the special-key columns. */
data class GestureBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun valid() = listOf(x, y, width, height).all { it.isFinite() } &&
        x >= 0f && y >= 0f && width >= .2f && height >= .25f &&
        x + width <= 1.00001f && y + height <= 1.00001f
    fun overlaps(other: GestureBounds) = x < other.x + other.width - .00001f &&
        x + width > other.x + .00001f && y < other.y + other.height - .00001f &&
        y + height > other.y + .00001f
}
data class GesturePadState(
    val id: String,
    val setId: String,
    val bounds: GestureBounds,
    val uppercase: Boolean = false,
    val shift: Boolean = false,
    val direct: Boolean = false,
    val romajiOverride: Boolean? = null
)
data class GestureSessionState(
    val pads: List<GesturePadState> = defaults(2),
    val activePadId: String = pads.first().id
) {
    fun validate() {
        require(pads.size in 1..4 && pads.map { it.id }.distinct().size == pads.size)
        require(pads.all { it.id.isNotBlank() && it.setId.isNotBlank() && it.bounds.valid() })
        require(pads.indices.all { i -> (i + 1 until pads.size).none { pads[i].bounds.overlaps(pads[it].bounds) } })
        require(pads.any { it.id == activePadId })
    }
    fun activePad() = pads.firstOrNull { it.id == activePadId } ?: pads.first()
    fun update(id: String, transform: (GesturePadState) -> GesturePadState) =
        copy(pads = pads.map { if (it.id == id) transform(it) else it })
    fun withSet(id: String, set: String) = update(id) { it.copy(setId = set, shift = false, romajiOverride = null) }
    fun withCount(count: Int): GestureSessionState {
        require(count in 1..4)
        val next = defaults(count).mapIndexed { index, default ->
            pads.getOrNull(index)?.copy(bounds = default.bounds) ?: default
        }
        return copy(pads = next, activePadId = activePadId.takeIf { id -> next.any { it.id == id } } ?: next.first().id)
    }
    companion object {
        fun defaults(count: Int): List<GesturePadState> {
            require(count in 1..4)
            val bounds = when (count) {
                1 -> listOf(GestureBounds(0f, 0f, 1f, 1f))
                2 -> listOf(GestureBounds(0f, 0f, .5f, 1f), GestureBounds(.5f, 0f, .5f, 1f))
                3 -> listOf(GestureBounds(0f, 0f, .5f, .5f), GestureBounds(.5f, 0f, .5f, .5f), GestureBounds(0f, .5f, 1f, .5f))
                else -> listOf(GestureBounds(0f, 0f, .5f, .5f), GestureBounds(.5f, 0f, .5f, .5f), GestureBounds(0f, .5f, .5f, .5f), GestureBounds(.5f, .5f, .5f, .5f))
            }
            val sets = listOf(GesturePresets.KANA, GesturePresets.DIGITS, GesturePresets.LATIN, GesturePresets.ROMAJI)
            return bounds.mapIndexed { index, rect -> GesturePadState("pad_${index + 1}", sets[index], rect) }
        }
    }
}
data class GestureControlKey(val id: String, val action: KeyAction, val labelOverride: String? = null)
data class GestureControlLayout(
    val leftKeys: List<GestureControlKey> = defaults(GestureSide.LEFT),
    val rightKeys: List<GestureControlKey> = defaults(GestureSide.RIGHT)
) {
    fun keys(side: GestureSide) = if (side == GestureSide.LEFT) leftKeys else rightKeys
    fun validate() {
        val keys = leftKeys + rightKeys
        require(keys.size <= 128 && keys.all { it.id.isNotBlank() && (it.labelOverride?.length ?: 0) <= 40 })
        require(keys.map { it.id }.distinct().size == keys.size)
    }
    fun withKeys(side: GestureSide, keys: List<GestureControlKey>) =
        if (side == GestureSide.LEFT) copy(leftKeys = keys) else copy(rightKeys = keys)
    /** index is an insertion gap in the destination BEFORE removing the source. */
    fun move(id: String, side: GestureSide, index: Int): GestureControlLayout {
        val sourceSide = GestureSide.entries.firstOrNull { s -> keys(s).any { it.id == id } } ?: return this
        val sourceIndex = keys(sourceSide).indexOfFirst { it.id == id }
        val key = keys(sourceSide)[sourceIndex]
        val removed = withKeys(sourceSide, keys(sourceSide).filterNot { it.id == id })
        val target = removed.keys(side).toMutableList()
        val adjusted = index - if (sourceSide == side && sourceIndex < index) 1 else 0
        target.add(adjusted.coerceIn(0, target.size), key)
        return removed.withKeys(side, target).also { it.validate() }
    }
    companion object {
        private fun defaults(side: GestureSide): List<GestureControlKey> {
            val commands = if (side == GestureSide.LEFT)
                listOf(KeyAction.ToggleDakutenOnly, KeyAction.ToggleHandakutenOnly, KeyAction.ToggleDakuten, KeyAction.SwitchToNextIme)
            else listOf(KeyAction.Delete, KeyAction.Space, KeyAction.Convert, KeyAction.Enter)
            return commands.map { GestureControlKey("default_" + KeyActionMapper.fromKeyAction(it), it) }
        }
    }
}

object GestureLabels {
    fun command(action: KeyAction, context: Context): String =
        KeyActionMapper.getDisplayActions(context).firstOrNull { it.action == action || (it.action is KeyAction.MoveToCustomKeyboard && action is KeyAction.MoveToCustomKeyboard) }?.displayName
            ?: when (action) {
                is KeyAction.InputText -> action.text
                is KeyAction.Text -> action.text
                else -> KeyActionMapper.fromKeyAction(action).orEmpty()
            }
    fun output(output: GestureOutput, context: Context) = when (output) {
        is GestureOutput.Reading -> output.text
        is GestureOutput.LiteralText -> output.text
        is GestureOutput.Romaji -> output.text
        is GestureOutput.Command -> command(output.action, context)
    }
}
