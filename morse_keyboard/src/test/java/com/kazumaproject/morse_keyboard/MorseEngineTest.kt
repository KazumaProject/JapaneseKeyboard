package com.kazumaproject.morse_keyboard

import org.junit.Assert.*
import org.junit.Test

class MorseEngineTest {
    private class Clock : MorseScheduler {
        data class Task(val at: Long, val order: Int, val action: () -> Unit, var cancelled: Boolean = false)
        private var time = 0L
        private val tasks = mutableListOf<Task>()
        private var sequence = 0
        override fun now() = time
        override fun after(delayMs: Long, action: () -> Unit): MorseCancellation {
            val task = Task(time + delayMs, sequence++, action)
            tasks += task
            return MorseCancellation { task.cancelled = true }
        }
        fun advance(ms: Long) {
            val end = time + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= end }.minWithOrNull(compareBy<Task> { it.at }.thenBy { it.order }) ?: break
                tasks.remove(next); time = next.at; next.action()
            }
            time = end
        }
    }
    private class Session(style: MorseInputStyle = MorseInputStyle.PADDLE_A, auto: Boolean = true) {
        val clock = Clock()
        val outputs = mutableListOf<MorseOutput>()
        val tones = mutableListOf<Boolean>()
        val engine = MorseEngine(clock, MorsePresets.maps().last(), MorseSettings(style = style, autoBoundary = auto),
            onOutput = { outputs += it; true }, onTone = { tones += it })
    }
    @Test fun tapDoesNotDecodePrefixesUntilBoundary() {
        val s = Session(MorseInputStyle.TAP, false)
        s.engine.pressed(true, false); s.engine.pressed(false, false)
        s.clock.advance(2000)
        assertEquals(".", s.engine.pattern); assertTrue(s.outputs.isEmpty())
        s.engine.pressed(true, false); s.engine.pressed(false, false)
        assertEquals("..", s.engine.pattern)
        assertTrue(s.engine.boundary())
        assertEquals(listOf(MorseOutput.LiteralText("i")), s.outputs)
    }
    @Test fun overlappingTapsKeepPressOrderAndDoNotRepeat() {
        val s = Session(MorseInputStyle.TAP, false)
        s.engine.pressed(false, true); s.engine.pressed(true, true)
        s.clock.advance(1000); s.engine.pressed(true, false); s.engine.pressed(false, false)
        assertEquals("-.", s.engine.pattern)
    }
    @Test fun paddleHoldGeneratesTimedDotsAndStopsAfterRelease() {
        val s = Session()
        s.engine.pressed(true, false)
        s.clock.advance(321)
        assertEquals("...", s.engine.pattern)
        s.engine.pressed(false, false)
        s.clock.advance(678)
        assertTrue(s.outputs.isEmpty())
        s.clock.advance(1)
        assertEquals(listOf(MorseOutput.LiteralText("s")), s.outputs)
    }
    @Test fun squeezeReleaseDistinguishesAAndB() {
        for ((style, pattern) in listOf(MorseInputStyle.PADDLE_A to ".", MorseInputStyle.PADDLE_B to ".-")) {
            val s = Session(style, false)
            s.engine.pressed(true, false); s.clock.advance(10)
            s.engine.pressed(true, true); s.clock.advance(10)
            s.engine.pressed(false, false); s.clock.advance(500)
            assertEquals(style.name, pattern, s.engine.pattern)
            assertEquals(false, s.tones.last())
        }
    }
    @Test fun briefOppositePaddleIsRememberedWhileLongElementFinishes() {
        val s = Session(auto = false)
        s.engine.pressed(false, true); s.clock.advance(10)
        s.engine.pressed(true, true); s.clock.advance(10)
        s.engine.pressed(false, true); s.clock.advance(300)
        assertEquals("-.", s.engine.pattern)
        s.engine.pressed(false, false)
    }
    @Test fun bothHeldAlternateWithDotDashRatioAndElementGaps() {
        val s = Session(auto = false)
        s.engine.pressed(true, false); s.engine.pressed(true, true)
        s.clock.advance(159); assertEquals(".", s.engine.pattern)
        s.clock.advance(1); assertEquals(".-", s.engine.pattern)
        s.clock.advance(319); assertEquals(".-", s.engine.pattern)
        s.clock.advance(1); assertEquals(".-.", s.engine.pattern)
    }
    @Test fun straightKeyThresholdIsInclusiveAndOnePressCreatesOneElement() {
        for ((duration, expected) in listOf(159L to ".", 160L to "-", 1000L to "-")) {
            val s = Session(MorseInputStyle.STRAIGHT, false)
            s.engine.pressed(true, false); s.clock.advance(duration)
            assertEquals("", s.engine.pattern)
            s.engine.pressed(false, false)
            assertEquals(expected, s.engine.pattern)
        }
    }
    @Test fun automaticTapBoundaryWaitsForToneToFinish() {
        val s = Session(MorseInputStyle.TAP)
        s.engine.pressed(false, true); s.clock.advance(1); s.engine.pressed(false, false)
        s.clock.advance(838); assertTrue(s.outputs.isEmpty())
        s.clock.advance(1); assertEquals(listOf(MorseOutput.LiteralText("t")), s.outputs)
    }
    @Test fun releasingDuringPaddleGapStartsThePauseFromTheRelease() {
        val s = Session()
        s.engine.pressed(true, false); s.clock.advance(100)
        s.engine.pressed(false, false)
        s.clock.advance(599); assertTrue(s.outputs.isEmpty())
        s.clock.advance(1); assertEquals(listOf(MorseOutput.LiteralText("e")), s.outputs)
    }
    @Test fun maximumLengthSignalStillDecodesAfterHeldPaddleIsReleased() {
        val s = Session()
        val code = ".".repeat(64)
        s.engine.map = MorseMap("long", "Long", listOf(MorseEntry(code, MorseOutput.LiteralText("done"))))
        s.engine.pressed(true, false); s.clock.advance(12000)
        assertEquals(code, s.engine.pattern); assertTrue(s.outputs.isEmpty())
        s.engine.pressed(false, false); s.clock.advance(600)
        assertEquals(listOf(MorseOutput.LiteralText("done")), s.outputs)
    }
    @Test fun cancelledSessionCannotEmitIntoNextEditor() {
        val s = Session()
        s.engine.pressed(true, false); s.engine.cancel(clearPattern = true)
        s.clock.advance(5000)
        assertTrue(s.outputs.isEmpty()); assertEquals("", s.engine.pattern); assertFalse(s.tones.last())
    }
    @Test fun cancellationPreservesEditableSignalButCancelsAutomaticCommit() {
        val s = Session(MorseInputStyle.TAP)
        s.engine.pressed(true, false); s.engine.pressed(false, false); s.engine.cancel()
        s.clock.advance(2000)
        assertEquals(".", s.engine.pattern); assertTrue(s.outputs.isEmpty())
        assertTrue(s.engine.deleteSignal()); assertEquals("", s.engine.pattern)
    }
    @Test fun unknownSignalAndRejectedModifierRemainEditable() {
        val s = Session(auto = false)
        s.engine.restorePattern("........")
        assertFalse(s.engine.boundary()); assertEquals("........", s.engine.pattern)
        val e = MorseEngine(s.clock, MorsePresets.maps().first(), onOutput = { false })
        e.restorePattern(".."); assertFalse(e.boundary()); assertEquals("..", e.pattern)
    }
    @Test fun standardMapsHaveUniqueSignalsAndKnownDifferentMeanings() {
        val maps = MorsePresets.maps()
        maps.forEach { it.validate() }
        assertEquals(MorseOutput.Reading("い"), maps[0].decode(".-"))
        assertEquals(MorseOutput.Reading("か"), maps[0].decode(".-.."))
        assertEquals(MorseOutput.Reading("ん"), maps[0].decode(".-.-."))
        assertEquals(MorseOutput.Reading("ー"), maps[0].decode(".--.-"))
        assertEquals(MorseOutput.Romaji("a"), maps[1].decode(".-"))
        assertEquals(MorseOutput.LiteralText("a"), maps[2].decode(".-"))
        assertTrue(maps[2].continuations(".").any { it.signal == ".." })
    }
    @Test fun kanaModifiersOnlyEditEligibleUncommittedKana() {
        assertEquals("なが", MorseKanaModifier.apply("なか", MorseCommand.DAKUTEN))
        assertEquals("ぱ", MorseKanaModifier.apply("ば", MorseCommand.HANDAKUTEN))
        assertEquals("ゃ", MorseKanaModifier.apply("や", MorseCommand.SMALL_KANA))
        assertEquals("つ", MorseKanaModifier.apply("っ", MorseCommand.SMALL_KANA))
        assertNull(MorseKanaModifier.apply("", MorseCommand.DAKUTEN))
        assertNull(MorseKanaModifier.apply("a", MorseCommand.DAKUTEN))
    }
}
