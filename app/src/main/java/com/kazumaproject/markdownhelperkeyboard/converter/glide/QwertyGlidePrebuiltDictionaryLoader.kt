package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class QwertyGlidePrebuiltDictionaryLoader internal constructor(
    private val openBundledIndex: () -> InputStream,
    private val isBundledEnglishDictionaryActive: () -> Boolean,
    private val reader: QwertyGlideIndexBinaryReader,
) {
    constructor(resolver: DictionarySourceResolver) : this(
        openBundledIndex = { resolver.openBundledForKey(DictionaryFileKey.ENGLISH_QWERTY_GLIDE_INDEX) },
        isBundledEnglishDictionaryActive = {
            !resolver.shouldUseOverrideCategory(DictionaryCategory.ENGLISH)
        },
        reader = QwertyGlideIndexBinaryReader()
    )

    fun load(): QwertyGlidePrebuiltLoadResult {
        if (!isBundledEnglishDictionaryActive()) {
            return QwertyGlidePrebuiltLoadResult.NotAvailable(
                "external English dictionary override active"
            )
        }
        return try {
            val entries = openBundledIndex().use { input -> reader.read(input) }
            QwertyGlidePrebuiltLoadResult.Loaded(
                QwertyGlideIndexedDictionaryProvider.fromIndexedEntries(entries)
            )
        } catch (error: QwertyGlideIndexFormatException) {
            QwertyGlidePrebuiltLoadResult.Invalid(
                reason = error.message ?: error::class.java.simpleName,
                cause = error
            )
        } catch (error: FileNotFoundException) {
            QwertyGlidePrebuiltLoadResult.NotAvailable(
                reason = error.message ?: "bundled qwerty glide index missing"
            )
        } catch (error: IOException) {
            QwertyGlidePrebuiltLoadResult.NotAvailable(
                reason = error.message ?: error::class.java.simpleName,
                cause = error
            )
        } catch (error: Exception) {
            QwertyGlidePrebuiltLoadResult.Invalid(
                reason = error.message ?: error::class.java.simpleName,
                cause = error
            )
        }
    }
}

sealed class QwertyGlidePrebuiltLoadResult {
    data class Loaded(val provider: QwertyGlideIndexedDictionaryProvider) :
        QwertyGlidePrebuiltLoadResult()

    data class NotAvailable(
        val reason: String,
        val cause: Throwable? = null
    ) : QwertyGlidePrebuiltLoadResult()

    data class Invalid(
        val reason: String,
        val cause: Throwable? = null
    ) : QwertyGlidePrebuiltLoadResult()
}
