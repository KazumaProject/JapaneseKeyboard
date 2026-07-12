package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import java.util.concurrent.atomic.AtomicReference

/** Atomic switch used by production loading and enabled/disabled performance comparisons. */
object SystemNgramRuntime {
    private val dictionary = AtomicReference<SystemNgramDictionary>(EmptySystemNgramDictionary)

    fun current(): SystemNgramDictionary = dictionary.get()

    fun install(value: SystemNgramDictionary) {
        dictionary.set(value)
    }

    fun disable() {
        dictionary.set(EmptySystemNgramDictionary)
    }
}
