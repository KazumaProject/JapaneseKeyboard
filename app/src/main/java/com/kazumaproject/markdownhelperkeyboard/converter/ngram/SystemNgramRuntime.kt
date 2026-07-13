package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import android.content.Context
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Process-wide scoreless dictionary gate. A loaded dictionary is never duplicated. */
object SystemNgramRuntime {
    private val dictionary = AtomicReference<SystemNgramDictionary>(EmptySystemNgramDictionary)
    private val enabled = AtomicBoolean(true)
    private val loadAttempted = AtomicBoolean(false)
    private val loadLock = Any()

    fun current(): SystemNgramDictionary =
        if (enabled.get()) dictionary.get() else EmptySystemNgramDictionary

    fun initialize(context: Context, value: Boolean) {
        enabled.set(value)
        if (value) ensureLoaded(context.applicationContext)
    }

    fun setEnabled(context: Context, value: Boolean) {
        if (value) ensureLoaded(context.applicationContext)
        enabled.set(value)
    }

    fun isEnabled(): Boolean = enabled.get()

    fun loadedDictionary(): SystemNgramDictionary = dictionary.get()

    private fun ensureLoaded(context: Context) {
        if (dictionary.get().ruleCount > 0 || loadAttempted.get()) return
        synchronized(loadLock) {
            if (dictionary.get().ruleCount > 0 || !loadAttempted.compareAndSet(false, true)) return
            val loaded = runCatching<SystemNgramDictionary> {
                SystemNgramAssetLoader.load(context)
            }.onFailure {
                Timber.w(it, "System n-gram dictionary is unavailable; continuing without it.")
            }.getOrDefault(EmptySystemNgramDictionary)
            dictionary.set(loaded)
        }
    }

    /** Installs a preloaded dictionary and enables it for tests and measurements. */
    fun install(value: SystemNgramDictionary) {
        dictionary.set(value)
        loadAttempted.set(true)
        enabled.set(true)
    }

    /** Disables matching while retaining an already loaded dictionary for reuse. */
    fun disable() {
        enabled.set(false)
    }

    internal fun resetForTesting() {
        dictionary.set(EmptySystemNgramDictionary)
        enabled.set(true)
        loadAttempted.set(false)
    }
}
