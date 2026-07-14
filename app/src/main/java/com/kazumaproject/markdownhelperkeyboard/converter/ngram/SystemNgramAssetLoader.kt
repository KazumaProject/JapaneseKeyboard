package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import android.content.Context

object SystemNgramAssetLoader {
    private const val ASSET_PATH = "ngram/system_ngram.dat"

    fun load(context: Context): SystemNgramDictionary = context.assets.open(ASSET_PATH).use { input ->
        val expectedSize = input.available()
        require(expectedSize > 0) { "Empty system n-gram asset" }
        val bytes = ByteArray(expectedSize)
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            require(count > 0) { "Truncated system n-gram asset" }
            offset += count
        }
        require(input.read() == -1) { "System n-gram asset size changed while reading" }
        PackedSystemNgramDictionary.read(bytes)
    }
}
