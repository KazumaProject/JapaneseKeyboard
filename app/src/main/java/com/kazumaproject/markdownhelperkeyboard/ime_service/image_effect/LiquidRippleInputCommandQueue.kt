package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal class LiquidRippleInputCommandQueue(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val lock = Any()
    private val commands = ArrayDeque<LiquidRippleInputCommand>()

    fun offer(command: LiquidRippleInputCommand): Boolean = synchronized(lock) {
        if (
            command is LiquidRippleInputCommand.Impulse &&
            command.kind == LiquidRippleImpulseKind.Move
        ) {
            val replaced = replaceLatestMoveForPointerLocked(command)
            if (replaced) return@synchronized true
        }

        while (commands.size >= maxSize) {
            val removedMove = removeOldestMoveLocked()
            if (!removedMove) {
                if (
                    command is LiquidRippleInputCommand.Impulse &&
                    command.kind == LiquidRippleImpulseKind.Move
                ) {
                    return@synchronized false
                }
                break
            }
        }

        commands.addLast(command)
        true
    }

    fun drain(maxCommands: Int = Int.MAX_VALUE): List<LiquidRippleInputCommand> =
        synchronized(lock) {
            if (commands.isEmpty()) return@synchronized emptyList()
            val count = minOf(maxCommands, commands.size)
            val drained = ArrayList<LiquidRippleInputCommand>(count)
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
        command: LiquidRippleInputCommand.Impulse
    ): Boolean {
        for (index in commands.indices.reversed()) {
            val existing = commands[index]
            if (
                existing is LiquidRippleInputCommand.Impulse &&
                existing.kind == LiquidRippleImpulseKind.Move &&
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
            it is LiquidRippleInputCommand.Impulse && it.kind == LiquidRippleImpulseKind.Move
        }
        if (index < 0) return false
        commands.removeAt(index)
        return true
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 96
    }
}
