package com.kazumaproject.morse_keyboard

fun interface MorseCancellation { fun cancel() }
interface MorseScheduler {
    fun now(): Long
    fun after(delayMs: Long, action: () -> Unit): MorseCancellation
}

/** One session, independent of Android views, audio availability and wall-clock changes. */
class MorseEngine(
    private val scheduler: MorseScheduler,
    var map: MorseMap,
    var settings: MorseSettings = MorseSettings(),
    private val onOutput: (MorseOutput) -> Boolean,
    private val onPattern: (String) -> Unit = {},
    private val onTone: (Boolean) -> Unit = {},
) {
    var pattern: String = ""
        private set
    private var dot = false
    private var dash = false
    private var dotMemory = false
    private var dashMemory = false
    private var running = false
    private var sounding = false
    private var squeezed = false
    private var last: Char? = null
    private var straightDown = 0L
    private var pulseEndAt = 0L
    private var lastActivityAt = scheduler.now()
    private var elementTask: MorseCancellation? = null
    private var boundaryTask: MorseCancellation? = null
    private var generation = 0L

    fun pressed(dotPressed: Boolean, dashPressed: Boolean) {
        if (dot == dotPressed && dash == dashPressed) return
        boundaryTask?.cancel()
        lastActivityAt = scheduler.now()
        val dotDown = dotPressed && !dot
        val dashDown = dashPressed && !dash
        val wasDown = dot || dash
        dot = dotPressed; dash = dashPressed
        when (settings.style) {
            MorseInputStyle.TAP -> {
                if (dotDown) pulse('.')
                if (dashDown) pulse('-')
                if (!dot && !dash) armBoundary()
            }
            MorseInputStyle.STRAIGHT -> {
                if (!wasDown && (dot || dash)) { straightDown = scheduler.now(); tone(true) }
                if (wasDown && !dot && !dash) {
                    tone(false)
                    append(if (scheduler.now() - straightDown >= settings.straightThresholdMs) '-' else '.')
                    armBoundary()
                }
            }
            else -> {
                if (dotDown) dotMemory = true
                if (dashDown) dashMemory = true
                if (dot && dash) squeezed = true
                if (!dot && !dash && squeezed) {
                    dotMemory = false; dashMemory = false
                    if (settings.style == MorseInputStyle.PADDLE_B && sounding) {
                        if (last == '-') dotMemory = true else dashMemory = true
                    }
                    squeezed = false
                }
                if (!running) nextElement()
            }
        }
    }

    fun addSignal(signal: Char) { require(signal == '.' || signal == '-'); cancel(); append(signal); armBoundary() }

    private fun append(signal: Char) {
        if (pattern.length < 64) { pattern += signal; onPattern(pattern) }
        else cancel()
    }

    private fun pulse(signal: Char) {
        if (pattern.length >= 64) return
        append(signal)
        elementTask?.cancel()
        tone(true)
        pulseEndAt = scheduler.now() + settings.unitMs * if (signal == '-') 3 else 1
        val epoch = generation
        elementTask = scheduler.after(settings.unitMs * if (signal == '-') 3 else 1) {
            if (epoch == generation) tone(false)
        }
    }

    private fun nextElement() {
        val signal = when {
            last == '.' && (dash || dashMemory) -> '-'
            last == '-' && (dot || dotMemory) -> '.'
            dot || dotMemory -> '.'
            dash || dashMemory -> '-'
            else -> null
        }
        if (signal == null) {
            running = false; last = null
            armBoundary()
            return
        }
        if (pattern.length >= 64) { running = false; tone(false); return }
        running = true; last = signal
        if (signal == '.') dotMemory = false else dashMemory = false
        append(signal)
        tone(true)
        val epoch = generation
        elementTask = scheduler.after(settings.unitMs * if (signal == '-') 3 else 1) {
            if (epoch == generation) {
                tone(false)
                elementTask = scheduler.after(settings.unitMs) { if (epoch == generation) nextElement() }
            }
        }
    }

    private fun tone(on: Boolean) {
        if (sounding != on) lastActivityAt = scheduler.now()
        sounding = on; onTone(on)
    }
    private fun armBoundary() {
        boundaryTask?.cancel()
        if (!settings.autoBoundary || dot || dash || running || pattern.isEmpty()) return
        val epoch = generation
        val completedAt = if (settings.style == MorseInputStyle.TAP) maxOf(lastActivityAt, pulseEndAt) else lastActivityAt
        boundaryTask = scheduler.after((completedAt + settings.boundaryMs - scheduler.now()).coerceAtLeast(0)) {
            if (epoch == generation && !dot && !dash && !running) boundary()
        }
    }

    fun boundary(): Boolean {
        cancel()
        val output = map.decode(pattern) ?: return false
        if (!onOutput(output)) return false
        pattern = ""; onPattern(pattern)
        return true
    }

    fun deleteSignal(): Boolean {
        if (pattern.isEmpty()) return false
        cancel(); pattern = pattern.dropLast(1); onPattern(pattern)
        return true
    }

    fun restorePattern(signal: String) {
        require(signal.isEmpty() || signal.matches(Regex("[.-]{1,64}")))
        cancel(); pattern = signal; onPattern(pattern)
    }

    fun cancel(clearPattern: Boolean = false) {
        generation++
        lastActivityAt = scheduler.now(); pulseEndAt = 0
        elementTask?.cancel(); boundaryTask?.cancel()
        elementTask = null; boundaryTask = null
        dot = false; dash = false; dotMemory = false; dashMemory = false
        running = false; squeezed = false; last = null
        tone(false)
        if (clearPattern) { pattern = ""; onPattern(pattern) }
    }
}
