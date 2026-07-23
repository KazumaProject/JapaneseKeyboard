package com.kazumaproject.markdownhelperkeyboard.gemma.media

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaMediaResultStore @Inject constructor() {
    private val pending = AtomicReference<String?>(null)

    fun put(text: String) {
        pending.set(text)
    }

    fun consume(): String? = pending.getAndSet(null)
}
