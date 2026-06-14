package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal class FluidInputCommandQueue(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val lock = Any()
    private val commands = ArrayDeque<FluidInputCommand>()

    fun offer(command: FluidInputCommand): Boolean = synchronized(lock) {
        if (
            command is FluidInputCommand.Splat &&
            command.kind == FluidSplatKind.Move &&
            command.canReplaceQueuedMove
        ) {
            if (replaceLatestReplaceableMoveForPointerLocked(command)) {
                return@synchronized true
            }
        }

        while (commands.size >= maxSize) {
            val removedMove = removeOldestReplaceableMoveLocked()
            if (!removedMove) {
                if (
                    command is FluidInputCommand.Splat &&
                    command.kind == FluidSplatKind.Move &&
                    command.canReplaceQueuedMove
                ) {
                    return@synchronized false
                }
                break
            }
        }

        commands.addLast(command)
        true
    }

    fun drain(maxCommands: Int = Int.MAX_VALUE): List<FluidInputCommand> = synchronized(lock) {
        if (commands.isEmpty()) return@synchronized emptyList()
        val count = minOf(maxCommands, commands.size)
        val drained = ArrayList<FluidInputCommand>(count)
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

    private fun replaceLatestReplaceableMoveForPointerLocked(command: FluidInputCommand.Splat): Boolean {
        for (index in commands.indices.reversed()) {
            val existing = commands[index]
            if (
                existing is FluidInputCommand.Splat &&
                existing.kind == FluidSplatKind.Move &&
                existing.pointerId == command.pointerId &&
                existing.canReplaceQueuedMove
            ) {
                commands[index] = command
                return true
            }
        }
        return false
    }

    private fun removeOldestReplaceableMoveLocked(): Boolean {
        val index = commands.indexOfFirst {
            it is FluidInputCommand.Splat &&
                it.kind == FluidSplatKind.Move &&
                it.canReplaceQueuedMove
        }
        if (index < 0) return false
        commands.removeAt(index)
        return true
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 96
    }
}
