package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomToggleInputStateTest {
    private val state = CustomToggleInputState()

    @Test
    fun sameKey_cyclesInConfiguredOrder() {
        val values = listOf("あ", "い", "う")

        assertEquals(CustomToggleInputState.Mutation.Append("あ"), state.next("key", values))
        assertEquals(CustomToggleInputState.Mutation.Replace("あ", "い"), state.next("key", values))
        assertEquals(CustomToggleInputState.Mutation.Replace("い", "う"), state.next("key", values))
        assertEquals(CustomToggleInputState.Mutation.Replace("う", "あ"), state.next("key", values))
    }

    @Test
    fun anotherKey_startsANewSequence() {
        state.next("first", listOf("a", "b"))
        state.next("first", listOf("a", "b"))

        assertEquals(
            CustomToggleInputState.Mutation.Append("x"),
            state.next("second", listOf("x", "y"))
        )
    }

    @Test
    fun singleValue_alwaysAppends() {
        assertEquals(CustomToggleInputState.Mutation.Append("a"), state.next("key", listOf("a")))
        assertEquals(CustomToggleInputState.Mutation.Append("a"), state.next("key", listOf("a")))
    }

    @Test
    fun reset_startsSequenceAgain() {
        val values = listOf("a", "b")
        state.next("key", values)
        state.next("key", values)
        state.reset()

        assertEquals(CustomToggleInputState.Mutation.Append("a"), state.next("key", values))
    }

    @Test
    fun transformedOutputs_keepCanonicalSequence() {
        val values = listOf("a", "b", "c")

        assertEquals(
            CustomToggleInputState.Mutation.Append("A"),
            state.next("key", values, listOf("A", "B", "C"))
        )
        assertEquals(
            CustomToggleInputState.Mutation.Replace("A", "b"),
            state.next("key", values, listOf("a", "b", "c"))
        )
        assertEquals(
            CustomToggleInputState.Mutation.Replace("b", "C"),
            state.next("key", values, listOf("A", "B", "C"))
        )
    }
}
