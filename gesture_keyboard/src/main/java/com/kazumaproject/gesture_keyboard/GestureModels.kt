package com.kazumaproject.gesture_keyboard

data class GesturePoint(val x: Float, val y: Float)
enum class GestureSide { LEFT, RIGHT }
enum class GestureCommand {
    DELETE, SPACE, CONVERT, COMMIT, ENTER, CURSOR_LEFT, CURSOR_RIGHT,
    DAKUTEN, HANDAKUTEN, SMALL_KANA, NEXT_KEYBOARD
}
sealed class GestureOutput {
    data class Reading(val text: String) : GestureOutput()
    data class LiteralText(val text: String) : GestureOutput()
    data class Command(val action: GestureCommand) : GestureOutput()
}
data class GestureEntry(val id: String, val output: GestureOutput, val samples: List<List<GesturePoint>>) {
    fun validate() {
        require(id.isNotBlank() && samples.size in 1..16)
        when (output) {
            is GestureOutput.Reading -> require(output.text.codePointCount(0, output.text.length) == 1 &&
                output.text.singleOrNull()?.let { it in '\u3041'..'\u3096' || it in "ー、。" } == true)
            is GestureOutput.LiteralText -> require(output.text.codePointCount(0, output.text.length) == 1 &&
                output.text.none { it.isISOControl() })
            is GestureOutput.Command -> Unit
        }
        samples.forEach { points ->
            require(points.size in 2..512 && points.all { it.x.isFinite() && it.y.isFinite() && kotlin.math.abs(it.x) <= 100000 && kotlin.math.abs(it.y) <= 100000 })
            require(GestureRecognizer.normalize(points) != null) { "Empty stroke" }
        }
    }
}
data class GestureSet(val id: String, val name: String, val entries: List<GestureEntry>, val builtIn: Boolean = false) {
    fun validate() {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 100 && entries.size <= 256)
        require(entries.map { it.id }.distinct().size == entries.size)
        entries.forEach { it.validate() }
    }
}
data class GestureSessionState(
    val leftSetId: String = GesturePresets.KANA,
    val rightSetId: String = GesturePresets.DIGITS,
    val leftUppercase: Boolean = false,
    val rightUppercase: Boolean = false
) {
    fun setId(side: GestureSide) = if (side == GestureSide.LEFT) leftSetId else rightSetId
    fun uppercase(side: GestureSide) = if (side == GestureSide.LEFT) leftUppercase else rightUppercase
    fun withSet(side: GestureSide, id: String) = if (side == GestureSide.LEFT) copy(leftSetId = id) else copy(rightSetId = id)
    fun toggleCase(side: GestureSide) = if (side == GestureSide.LEFT) copy(leftUppercase = !leftUppercase) else copy(rightUppercase = !rightUppercase)
}
data class GestureControlKey(val id: String, val action: GestureCommand, val labelOverride: String? = null)
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
                listOf(GestureCommand.DAKUTEN, GestureCommand.HANDAKUTEN, GestureCommand.SMALL_KANA, GestureCommand.NEXT_KEYBOARD)
            else listOf(GestureCommand.DELETE, GestureCommand.SPACE, GestureCommand.CONVERT, GestureCommand.ENTER)
            return commands.map { GestureControlKey("default_" + it.name, it) }
        }
    }
}

object GestureLabels {
    fun japanese(language: String) = language == "ja"
    fun command(command: GestureCommand, ja: Boolean): String = when (command) {
        GestureCommand.DELETE -> if (ja) "削除" else "Delete"
        GestureCommand.SPACE -> if (ja) "空白" else "Space"
        GestureCommand.CONVERT -> if (ja) "変換" else "Convert"
        GestureCommand.COMMIT -> if (ja) "確定" else "Commit"
        GestureCommand.ENTER -> "Enter"
        GestureCommand.CURSOR_LEFT -> "←"
        GestureCommand.CURSOR_RIGHT -> "→"
        GestureCommand.DAKUTEN -> "゛"
        GestureCommand.HANDAKUTEN -> "゜"
        GestureCommand.SMALL_KANA -> if (ja) "小書き" else "Small"
        GestureCommand.NEXT_KEYBOARD -> if (ja) "次KB" else "Next KB"
    }
    fun glyph(command: GestureCommand): String = when (command) {
        GestureCommand.DELETE -> "⌫"
        GestureCommand.SPACE -> "␣"
        GestureCommand.CONVERT -> "変"
        GestureCommand.COMMIT -> "✓"
        GestureCommand.ENTER -> "↵"
        GestureCommand.NEXT_KEYBOARD -> "⌨"
        GestureCommand.SMALL_KANA -> "小"
        else -> command(command, true)
    }
    fun output(output: GestureOutput, ja: Boolean) = when (output) {
        is GestureOutput.Reading -> output.text
        is GestureOutput.LiteralText -> output.text
        is GestureOutput.Command -> command(output.action, ja)
    }
}
