package com.kazumaproject.markdownhelperkeyboard.converter

import java.lang.ref.WeakReference

/** Identity comparison without keeping a replaced dictionary or cost table alive. */
internal class ConversionCacheIdentity(value: Any) {
    private val reference = WeakReference(value)
    private val identityHash = System.identityHashCode(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversionCacheIdentity) return false
        val value = reference.get() ?: return false
        return value === other.reference.get()
    }

    override fun hashCode(): Int = identityHash
}
