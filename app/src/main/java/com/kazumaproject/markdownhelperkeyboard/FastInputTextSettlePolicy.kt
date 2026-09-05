package com.kazumaproject.markdownhelperkeyboard

/**
 * State machine for deciding when an editor value is safe to use as the result of an asynchronous
 * input trace.
 *
 * With an expected value, a stable partial value is deliberately never terminal. This is
 * important for rapid multi-touch input because InputConnection callbacks can arrive in chunks.
 */
internal class FastInputTextSettlePolicy(
    private val expectedText: String? = null,
    private val stableSamplesRequired: Int = 1,
) {
    private var previousText: String? = null
    private var stableSamples = 0

    init {
        require(stableSamplesRequired > 0) { "stableSamplesRequired must be positive" }
    }

    fun observe(text: String): Boolean {
        if (text == previousText) {
            stableSamples += 1
        } else {
            previousText = text
            stableSamples = 1
        }

        return stableSamples >= stableSamplesRequired &&
            (expectedText == null || text == expectedText)
    }
}
