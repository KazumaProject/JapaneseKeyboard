package com.kazumaproject.markdownhelperkeyboard

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Waits for the host's lifecycle, rather than for the entire application's message queue to idle.
 * The IME remains active during the matrix. Every main-thread action and lifecycle wait is bounded;
 * focus, editor readiness, IME geometry and committed text are checked by the matrix itself.
 */
internal class FastInputHostSession private constructor(private val token: String) : AutoCloseable {
    private var closed = false

    fun onActivity(action: (FastInputHostActivity) -> Unit) {
        check(!closed) { "Host session is closed" }
        await("resumed host") {
            val activity = activities(Stage.RESUMED).singleOrNull() ?: return@await false
            action(activity)
            true
        }
    }

    fun recreate() {
        var previous: FastInputHostActivity? = null
        onActivity {
            previous = it
            it.recreate()
        }
        await("recreated host") {
            activities(Stage.RESUMED).any { it !== previous }
        }
    }

    override fun close() {
        if (closed) return
        try {
            onMain { activities().forEach { it.finish() } }
            await("destroyed host") { activities().isEmpty() }
        } finally {
            closed = true
        }
    }

    // Called only on the main thread. The token follows recreation but excludes unrelated hosts.
    private fun activities(vararg stages: Stage): List<FastInputHostActivity> {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val included = stages.toList().ifEmpty { Stage.values().filter { it != Stage.DESTROYED } }
        return included.flatMap { monitor.getActivitiesInStage(it) }
            .filterIsInstance<FastInputHostActivity>()
            .filter { it.intent.getStringExtra(SESSION_EXTRA) == token }
            .distinct()
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        do {
            if (onMain(condition)) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out after ${TIMEOUT_MS}ms waiting for $description")
    }

    companion object {
        private const val SESSION_EXTRA = "fast_input_host_session"
        private const val TIMEOUT_MS = 10_000L

        fun launch(context: Context): FastInputHostSession {
            val session = FastInputHostSession(UUID.randomUUID().toString())
            try {
                onMain {
                    context.startActivity(
                        Intent(context, FastInputHostActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(SESSION_EXTRA, session.token)
                    )
                }
                session.await("launched host") { session.activities(Stage.RESUMED).size == 1 }
                return session
            } catch (error: Throwable) {
                runCatching { session.close() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }

        private fun <T> onMain(action: () -> T): T {
            check(Looper.myLooper() != Looper.getMainLooper()) {
                "Host session actions must be called from the instrumentation thread"
            }
            val task = FutureTask(action)
            check(Handler(Looper.getMainLooper()).post(task)) { "Main looper rejected host action" }
            return try {
                task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (error: TimeoutException) {
                task.cancel(false)
                throw AssertionError("Main thread did not complete host action in ${TIMEOUT_MS}ms", error)
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        }
    }
}
