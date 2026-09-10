package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomToggleInputStateTest {
    private val state = CustomToggleInputState()

    private fun next(
        key: String,
        values: List<String>,
        outputs: List<String> = values,
        now: Long = 0,
        timeout: Long = 1000,
    ) =
        state.next(key, values, outputs, nowMillis = now, timeoutMillis = timeout)

    @Test
    fun sameKey_cyclesInConfiguredOrder() {
        val values = listOf("あ", "い", "う")

        assertEquals(CustomToggleInputState.Mutation.Append("あ"), next("key", values))
        assertEquals(CustomToggleInputState.Mutation.Replace("あ", "い"), next("key", values))
        assertEquals(CustomToggleInputState.Mutation.Replace("い", "う"), next("key", values))
        assertEquals(CustomToggleInputState.Mutation.Replace("う", "あ"), next("key", values))
    }

    @Test
    fun anotherKey_startsANewSequence() {
        next("first", listOf("a", "b"))
        next("first", listOf("a", "b"))

        assertEquals(
            CustomToggleInputState.Mutation.Append("x"),
            next("second", listOf("x", "y"))
        )
    }

    @Test
    fun singleValue_alwaysAppends() {
        assertEquals(CustomToggleInputState.Mutation.Append("a"), next("key", listOf("a")))
        assertEquals(CustomToggleInputState.Mutation.Append("a"), next("key", listOf("a")))
    }

    @Test
    fun reset_startsSequenceAgain() {
        val values = listOf("a", "b")
        next("key", values)
        next("key", values)
        state.reset()

        assertEquals(CustomToggleInputState.Mutation.Append("a"), next("key", values))
    }

    @Test
    fun transformedOutputs_keepCanonicalSequence() {
        val values = listOf("a", "b", "c")

        assertEquals(
            CustomToggleInputState.Mutation.Append("A"),
            next("key", values, listOf("A", "B", "C"))
        )
        assertEquals(
            CustomToggleInputState.Mutation.Replace("A", "b"),
            next("key", values, listOf("a", "b", "c"))
        )
        assertEquals(
            CustomToggleInputState.Mutation.Replace("b", "C"),
            next("key", values, listOf("A", "B", "C"))
        )
    }

    @Test fun timeoutBoundary_startsNewSequence() {
        for (elapsed in listOf(999L, 1000L, 1001L)) {
            state.reset()
            next("key", listOf("a", "b"), now = 100)
            val expected = if (elapsed < 1000) CustomToggleInputState.Mutation.Replace("a", "b")
                else CustomToggleInputState.Mutation.Append("a")
            assertEquals(expected, next("key", listOf("a", "b"), now = 100 + elapsed))
        }
    }

    @Test fun everyTap_renewsDeadlineAcrossMultipleCycles() {
        val values = listOf("a", "b")
        next("key", values)
        for (i in 1..6) {
            val previous = values[(i - 1) % 2]
            assertEquals(CustomToggleInputState.Mutation.Replace(previous, values[i % 2]),
                next("key", values, now = i * 900L))
        }
        assertEquals(CustomToggleInputState.Mutation.Append("a"), next("key", values, now = 6400))
    }

    @Test fun changedTimeout_isUsedOnNextTap() {
        val values = listOf("a", "b")
        next("key", values, timeout = 1000)
        assertEquals(CustomToggleInputState.Mutation.Append("a"),
            next("key", values, now = 200, timeout = 200))
        assertEquals(CustomToggleInputState.Mutation.Replace("a", "b"),
            next("key", values, now = 900, timeout = 1000))
    }
}
