package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.quantity.QuantityDictionary
import timber.log.Timber

object QuantityRuntime {
    @Volatile var dictionary: QuantityDictionary = QuantityDictionary.EMPTY
        private set
    @Volatile private var loadAttempted = false

    val rules: List<List<QuantityDictionary.Feature>>
        get() = if (SystemNgramRuntime.isEnabled()) dictionary.rules else emptyList()

    fun install(value: QuantityDictionary) { dictionary = value }

    fun initialize(context: Context) {
        if (dictionary !== QuantityDictionary.EMPTY || loadAttempted) return
        synchronized(this) {
            if (dictionary !== QuantityDictionary.EMPTY || loadAttempted) return
            loadAttempted = true
            dictionary = runCatching {
                context.assets.open("quantity/quantity.dat").use { QuantityDictionary.read(it.readBytes()) }
            }.onFailure {
                Timber.w(it, "Quantity dictionary is unavailable; retaining ordinary dictionary conversion.")
            }.getOrDefault(QuantityDictionary.EMPTY)
        }
    }
}
