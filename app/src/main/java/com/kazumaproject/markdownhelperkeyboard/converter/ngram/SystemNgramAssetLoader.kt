package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import android.content.Context
import java.io.FileNotFoundException

object SystemNgramAssetLoader {
    private const val NGRAM_ASSET_PATH = "ngram/system_ngram.dat"
    private const val UNIGRAM_ASSET_PATH = "ngram/system_ngram_unigram.dat"

    fun load(context: Context): SystemNgramDictionary {
        val dictionaries = buildList {
            add(loadAsset(context, NGRAM_ASSET_PATH))
            try {
                add(loadAsset(context, UNIGRAM_ASSET_PATH))
            } catch (_: FileNotFoundException) {
                // Keep compatibility with builds which predate the optional unigram asset.
            }
        }
        return if (dictionaries.size == 1) {
            dictionaries.single()
        } else {
            CompositeSystemNgramDictionary(dictionaries)
        }
    }

    private fun loadAsset(context: Context, assetPath: String): SystemNgramDictionary =
        context.assets.open(assetPath).use { input ->
            val expectedSize = input.available()
            require(expectedSize > 0) { "Empty system n-gram asset: $assetPath" }
            val bytes = ByteArray(expectedSize)
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                require(count > 0) { "Truncated system n-gram asset: $assetPath" }
                offset += count
            }
            require(input.read() == -1) {
                "System n-gram asset size changed while reading: $assetPath"
            }
            PackedSystemNgramDictionary.read(bytes)
        }
}
