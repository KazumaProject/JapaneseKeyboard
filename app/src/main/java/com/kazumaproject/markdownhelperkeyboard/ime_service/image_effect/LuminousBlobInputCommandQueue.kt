package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal class LuminousBlobInputCommandQueue(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val lock = Any()
    private val commands = ArrayDeque<LuminousBlobInputCommand>()

    fun offer(command: LuminousBlobInputCommand): Boolean = synchronized(lock) {
        if (
            command is LuminousBlobInputCommand.Pointer &&
            command.kind == LuminousBlobInputKind.Move
        ) {
            val replaced = replaceLatestMoveForPointerLocked(command)
            if (replaced) return@synchronized true
        }

        while (commands.size >= maxSize) {
            val removedMove = removeOldestMoveLocked()
            if (!removedMove) {
                if (
                    command is LuminousBlobInputCommand.Pointer &&
                    command.kind == LuminousBlobInputKind.Move
                ) {
                    return@synchronized false
                }
                break
            }
        }

        commands.addLast(command)
        true
    }

    fun drain(maxCommands: Int = Int.MAX_VALUE): List<LuminousBlobInputCommand> =
        synchronized(lock) {
            if (commands.isEmpty()) return@synchronized emptyList()
            val count = minOf(maxCommands, commands.size)
            val drained = ArrayList<LuminousBlobInputCommand>(count)
            repeat(count) {
                drained.add(commands.removeFirst())
            }
            drained
        }

    fun clear() = synchronized(lock) {
        commands.clear()
    }

    fun sizeForTesting(): Int = synchronized(lock) {
        commands.size
    }

    private fun replaceLatestMoveForPointerLocked(
        command: LuminousBlobInputCommand.Pointer
    ): Boolean {
        for (index in commands.indices.reversed()) {
            val existing = commands[index]
            if (
                existing is LuminousBlobInputCommand.Pointer &&
                existing.kind == LuminousBlobInputKind.Move &&
                existing.pointerId == command.pointerId
            ) {
                commands[index] = command.copy(
                    previousX = existing.previousX,
                    previousY = existing.previousY
                )
                return true
            }
        }
        return false
    }

    private fun removeOldestMoveLocked(): Boolean {
        val index = commands.indexOfFirst {
            it is LuminousBlobInputCommand.Pointer && it.kind == LuminousBlobInputKind.Move
        }
        if (index < 0) return false
        commands.removeAt(index)
        return true
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 96
    }
}
