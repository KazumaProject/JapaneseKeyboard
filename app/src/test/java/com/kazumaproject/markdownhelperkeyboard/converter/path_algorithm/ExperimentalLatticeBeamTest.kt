package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.ExperimentalLatticeBeam
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentalLatticeBeamTest {
    @Test
    fun scoresPartialPathsBeforePruning() = runBlocking {
        val requests = mutableListOf<List<String>>()
        val model = ExperimentalLatticeBeam(1, 100) { prefixes ->
            requests.add(prefixes.toList())
            prefixes.map { if (it.startsWith("再審")) -1f else -4f }.toFloatArray()
        }
        val candidates = model.search(
            graph = mapOf(
                0 to listOf(node("BOS", 0, 0)),
                1 to listOf(node("最新", 0, 1), node("再審", 0, 1, score = 100)),
                2 to listOf(node("の", 1, 1)),
                3 to listOf(node("EOS", 3, 0)),
            ),
            connection = ConnectionMatrix.fromShortArray(ShortArray(1), 1),
            readingLength = 2,
            count = 1,
        )
        assertEquals(listOf(listOf("最新", "再審"), listOf("再審の")), requests)
        assertEquals(listOf("再審の"), candidates)
    }

    private fun node(text: String, start: Int, len: Short, score: Int = 0) =
        Node(l = 0, r = 0, score = score, f = score, tango = text, len = len, yomiUsed = "", sPos = start)
}
