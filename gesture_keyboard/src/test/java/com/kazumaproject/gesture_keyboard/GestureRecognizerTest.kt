package com.kazumaproject.gesture_keyboard

import org.junit.Assert.*
import org.junit.Test

class GestureRecognizerTest {
    @Test fun everyOriginalPresetRecognizesAndToleratesTranslationAndUniformScale() {
        val sets = GesturePresets.sets()
        assertEquals(listOf(49, 26, 10), sets.map { it.entries.size })
        sets.forEach { set ->
            set.validate()
            val recognizer = GestureRecognizer.Prepared(set)
            set.entries.forEach { entry ->
                val original = entry.samples.first()
                assertEquals(set.id + " / " + entry.id + " / " + recognizer.ranked(original).take(2),
                    entry.output, recognizer.recognize(original)?.entry?.output)
                val translated = original.map { GesturePoint(it.x * 13f + 200f, it.y * 13f - 150f) }
                assertEquals(entry.output, recognizer.recognize(translated)?.entry?.output)
            }
        }
    }
    @Test fun directionAspectAndSetRemainMeaningful() {
        val right = GesturePresets.path("0,0 10,0")
        val left = right.reversed()
        val vertical = GesturePresets.path("0,0 0,10")
        val entries = listOf(right, left, vertical).mapIndexed { i, points ->
            GestureEntry(i.toString(), GestureOutput.LiteralText(i.toString()), listOf(points))
        }
        val matcher = GestureRecognizer.Prepared(GestureSet("test", "test", entries))
        entries.forEach { assertEquals(it.output, matcher.recognize(it.samples.first())?.entry?.output) }
        val kana = GestureRecognizer.Prepared(GesturePresets.sets().first())
        assertTrue(kana.ranked(right).all { it.entry.output is GestureOutput.Reading })
    }
    @Test fun ambiguousDistinctOutputsAreRejectedButDuplicateSamplesForSameOutputAreNot() {
        val points = GesturePresets.path("0,0 10,5 0,10")
        val entry = GestureEntry("a", GestureOutput.LiteralText("a"), listOf(points, points))
        assertNotNull(GestureRecognizer.Prepared(GestureSet("a", "a", listOf(entry))).recognize(points))
        assertNull(GestureRecognizer.Prepared(GestureSet("a", "a", listOf(entry, entry.copy(id = "b", output = GestureOutput.LiteralText("b"))))).recognize(points))
    }
    @Test fun invalidAndDegeneratePathsAreRejected() {
        assertNull(GestureRecognizer.normalize(emptyList()))
        assertNull(GestureRecognizer.normalize(List(10) { GesturePoint(1f, 1f) }))
        assertNull(GestureRecognizer.normalize(listOf(GesturePoint(Float.NaN, 1f), GesturePoint(1f, 2f))))
        val straight = GesturePresets.path("0,0 1,0 1,0 1,0 10,0")
        val normalized = GestureRecognizer.normalize(straight)!!
        assertEquals(64, normalized.size)
        assertTrue(normalized.all { it.x.isFinite() && it.y.isFinite() })
    }
}
