package com.kazumaproject.core.domain.touch

import android.util.Log

/**
 * Test-only cross-view trace context for a single rapid-input trial.
 *
 * The context is inactive by default. The debug IME reset protocol assigns a trace ID before a
 * trial, so production input does not pay for logging or expose touch details.
 */
object PointerGestureTrace {
    private const val TAG = "FastInputTrace"

    @Volatile
    private var activeTraceId: String? = null

    fun isActive(): Boolean = activeTraceId != null

    fun begin(traceId: String) {
        activeTraceId = traceId.takeIf(String::isNotBlank)
        log("lifecycle", "begin")
    }

    fun end() {
        log("lifecycle", "end")
        activeTraceId = null
    }

    fun log(stage: String, message: String) {
        val traceId = activeTraceId ?: return
        Log.d(TAG, "traceId=$traceId stage=$stage $message")
    }
}
