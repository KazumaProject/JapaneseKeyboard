package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.MotionEvent
import androidx.annotation.ChecksSdkIntAtLeast
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private const val CANDIDATE_PERF_LOG_TAG = "CandidatePerf"
private val asyncTraceCookie = if (BuildConfig.DEBUG) AtomicInteger() else null

@ChecksSdkIntAtLeast(api = 29)
internal fun isInputPipelineTracing(): Boolean =
    BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()

internal fun nextCandidateTraceCookie(): Int = checkNotNull(asyncTraceCookie).incrementAndGet()

internal inline fun <T> traceDebugSection(name: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()
    if (Build.VERSION.SDK_INT >= 29 && !Trace.isEnabled()) return block()

    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

internal suspend inline fun <T> traceDebugAsync(
    name: () -> String,
    block: suspend () -> T,
): T {
    if (!BuildConfig.DEBUG || !isInputPipelineTracing()) return block()

    val section = name()
    val cookie = nextCandidateTraceCookie()
    Trace.beginAsyncSection(section, cookie)
    return try {
        block()
    } finally {
        Trace.endAsyncSection(section, cookie)
    }
}

internal inline fun <T> traceKeyboardTouch(event: MotionEvent, block: () -> T): T {
    if (!BuildConfig.DEBUG || !isInputPipelineTracing()) return block()

    val action = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> "Sumire T01 touch DOWN"
        MotionEvent.ACTION_POINTER_DOWN -> "Sumire T01 pointer DOWN"
        MotionEvent.ACTION_MOVE -> "Sumire T02 touch MOVE"
        MotionEvent.ACTION_UP -> "Sumire T02 touch UP"
        MotionEvent.ACTION_POINTER_UP -> "Sumire T02 pointer UP"
        MotionEvent.ACTION_CANCEL -> "Sumire T02 touch CANCEL"
        else -> return block()
    }
    val deliveryMillis = (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0L)
    Trace.setCounter("Sumire input delivery ms", deliveryMillis)
    return traceDebugSection(action, block)
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
    return try {
        traceDebugAsync({ name }) { block() }
    } finally {
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        Log.d(CANDIDATE_PERF_LOG_TAG, "$name ${String.format(Locale.US, "%.2f", elapsedMs)}ms")
    }
}
