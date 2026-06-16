package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal class CinematicWaveInputCommandQueue(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val lock = Any()
    private val commands = ArrayDeque<CinematicWaveInputCommand>()

    fun offer(command: CinematicWaveInputCommand): Boolean = synchronized(lock) {
        if (
            command is CinematicWaveInputCommand.Pointer &&
            command.kind == CinematicWaveInputKind.Move &&
            replaceLatestMoveForPointerLocked(command)
        ) {
            return@synchronized true
        }

        while (commands.size >= maxSize) {
            val removedMove = removeOldestMoveLocked()
            if (!removedMove) {
                if (
                    command is CinematicWaveInputCommand.Pointer &&
                    command.kind == CinematicWaveInputKind.Move
                ) {
                    return@synchronized false
                }
                break
            }
        }

        commands.addLast(command)
        true
    }

    fun drain(maxCommands: Int = Int.MAX_VALUE): List<CinematicWaveInputCommand> =
        synchronized(lock) {
            if (commands.isEmpty()) return@synchronized emptyList()
            val count = minOf(maxCommands, commands.size)
            val drained = ArrayList<CinematicWaveInputCommand>(count)
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
        command: CinematicWaveInputCommand.Pointer
    ): Boolean {
        for (index in commands.indices.reversed()) {
            val existing = commands[index]
            if (
                existing is CinematicWaveInputCommand.Pointer &&
                existing.kind == CinematicWaveInputKind.Move &&
                existing.pointerId == command.pointerId
            ) {
                commands[index] = command
                return true
            }
        }
        return false
    }

    private fun removeOldestMoveLocked(): Boolean {
        val index = commands.indexOfFirst {
            it is CinematicWaveInputCommand.Pointer &&
                it.kind == CinematicWaveInputKind.Move
        }
        if (index < 0) return false
        commands.removeAt(index)
        return true
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 96
    }
}
