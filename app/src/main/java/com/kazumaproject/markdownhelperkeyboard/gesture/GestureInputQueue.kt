package com.kazumaproject.markdownhelperkeyboard.gesture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-thread confined: a pending conversion must finish before a following Enter. */
internal class GestureInputQueue(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val jobs = mutableSetOf<Job>()
    private var generation = 0L
    fun enqueue(valid: () -> Boolean, action: suspend () -> Unit) {
        val expected = generation
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                mutex.withLock {
                    if (generation == expected && valid()) action()
                }
            } finally { jobs.remove(job) }
        }
        jobs.add(job)
        job.start()
    }
    fun cancel() {
        generation++
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
    }
}
