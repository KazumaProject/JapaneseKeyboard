package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import java.util.Locale

private const val CANDIDATE_PERF_LOG_TAG = "CandidatePerf"

internal inline fun <T> traceDebugSection(name: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()

    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

internal inline fun <T> measureDebugSection(name: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()

    val start = SystemClock.elapsedRealtimeNanos()
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        Log.d(CANDIDATE_PERF_LOG_TAG, "$name ${String.format(Locale.US, "%.2f", elapsedMs)}ms")
    }
}

internal suspend inline fun <T> measureDebugStage(
    name: String,
    crossinline block: suspend () -> T
): T {
    if (!BuildConfig.DEBUG) return block()

    val start = SystemClock.elapsedRealtimeNanos()
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        Log.d(CANDIDATE_PERF_LOG_TAG, "$name ${String.format(Locale.US, "%.2f", elapsedMs)}ms")
    }
}
