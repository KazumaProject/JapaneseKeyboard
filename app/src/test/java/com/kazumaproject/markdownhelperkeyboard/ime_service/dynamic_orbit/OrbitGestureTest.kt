package com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class OrbitGestureTest {
    private val letters = OrbitGeometry.rows.flatMapIndexed { row, chars ->
        chars.mapIndexedNotNull { vowel, char -> if (char == ' ') null else Triple(row, vowel, char) }
    }
    private fun cycle(g: OrbitGesture, row: Int, vowel: Int): List<Char> {
        val output = mutableListOf<Char>()
        val a = row * 36f
        val b = OrbitGeometry.vowelAngles[vowel]
        val delta = (b - a + 540f) % 360f - 180f
        output += g.move(OrbitGeometry.point(52f, a))
        val steps = ceil(abs(delta) / 5).toInt().coerceAtLeast(1)
        for (step in 1..steps) output += g.move(OrbitGeometry.point(52f, a + delta * step / steps))
        output += g.move(OrbitGeometry.point(76f, b))
        output += g.move(OrbitPoint(0f, 0f))
        return output
    }
    @Test fun everyLetterAndEveryOrderedPairHasABoundedCycle() {
        assertEquals(47, letters.size)
        for ((row, vowel, char) in letters) for ((r2, v2, c2) in letters) {
            val g = OrbitGesture().apply { start() }
            assertEquals(listOf(char, c2), cycle(g, row, vowel) + cycle(g, r2, v2))
            assertEquals(OrbitPhase.NEUTRAL, g.snapshot.phase)
        }
    }
    @Test fun repeatedLettersNeverToggleOrDrift() {
        val g = OrbitGesture().apply { start() }
        val output = (1..100).flatMap { cycle(g, 0, 0) }
        assertEquals("あ".repeat(100), output.joinToString(""))
        assertEquals(OrbitPoint(0f, 0f), g.snapshot.point)
    }
    @Test fun rowAndVowelCanChangeAndCancelWithoutOutput() {
        val g = OrbitGesture().apply { start() }
        g.move(OrbitGeometry.point(30f, 72f))
        assertEquals(2, g.snapshot.row)
        g.move(OrbitGeometry.point(30f, 108f))
        assertEquals(3, g.snapshot.row)
        g.move(OrbitGeometry.point(52f, 108f))
        for (a in 108 downTo 0 step 4) assertTrue(g.move(OrbitGeometry.point(52f, a.toFloat())).isEmpty())
        assertEquals('つ', g.snapshot.preview)
        for (a in 0..45 step 3) g.move(OrbitGeometry.point(52f, a.toFloat()))
        assertEquals('て', g.snapshot.preview)
        assertTrue(g.move(OrbitGeometry.point(35f, 45f)).isEmpty())
        assertEquals(OrbitPhase.ROW, g.snapshot.phase)
        assertTrue(g.move(OrbitPoint(0f, 0f)).isEmpty())
        assertEquals(OrbitPhase.NEUTRAL, g.snapshot.phase)
    }
    @Test fun hysteresisAndOuterBoundaryRequireCrossing() {
        val g = OrbitGesture().apply { start() }
        g.move(OrbitGeometry.point(25f, 0f))
        g.move(OrbitGeometry.point(20f, 0f))
        assertEquals(OrbitPhase.ROW, g.snapshot.phase)
        g.move(OrbitGeometry.point(30f, 20f))
        assertEquals(0, g.snapshot.row)
        g.move(OrbitGeometry.point(30f, 24f))
        assertEquals(1, g.snapshot.row)
        g.cancel(); g.start()
        assertEquals(listOf('う'), g.move(OrbitGeometry.point(90f, 0f)))
        assertTrue(g.move(OrbitGeometry.point(150f, 45f)).isEmpty())
        assertTrue(g.move(OrbitGeometry.point(20f, 0f)).isEmpty())
        assertEquals(OrbitPhase.RETURN, g.snapshot.phase)
        g.move(OrbitPoint(0f, 0f))
        assertEquals(listOf('う'), g.move(OrbitGeometry.point(72f, 0f)))
    }
    @Test fun invalidVowelMustReenterBeforeItCanCommit() {
        val g = OrbitGesture().apply { start() }
        g.move(OrbitGeometry.point(52f, 252f)) // ya row
        for (a in 252..315 step 3) g.move(OrbitGeometry.point(52f, a.toFloat()))
        assertNull(g.snapshot.preview)
        assertTrue(g.move(OrbitGeometry.point(76f, 315f)).isEmpty())
        for (a in 315..360 step 3) assertTrue(g.move(OrbitGeometry.point(76f, a.toFloat())).isEmpty())
        g.move(OrbitGeometry.point(63f, 0f))
        assertEquals(listOf('ゆ'), g.move(OrbitGeometry.point(76f, 0f)))
    }
    @Test fun endingOrCancellingNeverEmitsAnUncommittedPreview() {
        val g = OrbitGesture().apply { start() }
        g.move(OrbitGeometry.point(60f, 0f))
        g.cancel()
        assertTrue(g.move(OrbitGeometry.point(100f, 0f)).isEmpty())
        assertEquals(OrbitPhase.IDLE, g.snapshot.phase)
    }
}
