package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

data class HandwritingPoint(
    val x: Float,
    val y: Float,
)

data class HandwritingStroke(
    val points: List<HandwritingPoint>,
)

class HandwritingStrokeStore(
    private val maxHistoryEntries: Int = 64,
) {
    private var currentStrokes: List<HandwritingStroke> = emptyList()
    private val undoSnapshots = ArrayDeque<List<HandwritingStroke>>()
    private val redoSnapshots = ArrayDeque<List<HandwritingStroke>>()

    var revision: Long = 0L
        private set

    val strokes: List<HandwritingStroke>
        get() = currentStrokes

    val isEmpty: Boolean
        get() = currentStrokes.isEmpty()

    val canUndo: Boolean
        get() = undoSnapshots.isNotEmpty()

    val canRedo: Boolean
        get() = redoSnapshots.isNotEmpty()

    fun addStroke(points: List<HandwritingPoint>): Boolean {
        if (points.isEmpty()) return false
        mutate {
            currentStrokes = currentStrokes + HandwritingStroke(points.toList())
        }
        return true
    }

    fun clear(): Boolean {
        if (currentStrokes.isEmpty()) return false
        mutate {
            currentStrokes = emptyList()
        }
        return true
    }

    fun undo(): Boolean {
        if (undoSnapshots.isEmpty()) return false
        pushBounded(redoSnapshots, currentStrokes)
        currentStrokes = undoSnapshots.removeLast()
        revision += 1L
        return true
    }

    fun redo(): Boolean {
        if (redoSnapshots.isEmpty()) return false
        pushBounded(undoSnapshots, currentStrokes)
        currentStrokes = redoSnapshots.removeLast()
        revision += 1L
        return true
    }

    fun reset() {
        currentStrokes = emptyList()
        undoSnapshots.clear()
        redoSnapshots.clear()
        revision += 1L
    }

    private inline fun mutate(block: () -> Unit) {
        pushBounded(undoSnapshots, currentStrokes)
        block()
        redoSnapshots.clear()
        revision += 1L
    }

    private fun pushBounded(
        target: ArrayDeque<List<HandwritingStroke>>,
        snapshot: List<HandwritingStroke>,
    ) {
        target.addLast(snapshot)
        while (target.size > maxHistoryEntries.coerceAtLeast(1)) {
            target.removeFirst()
        }
    }
}
