package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionarySourceResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DictionaryOverrideStore,
) {
    fun openForKey(key: DictionaryFileKey): InputStream =
        if (shouldUseOverride(key)) openOverrideForKey(key) else openBundledForKey(key)

    fun openBundledForKey(key: DictionaryFileKey): InputStream =
        context.assets.open(DictionaryFileSpecs.get(key).bundledAssetPath)

    fun openOverrideForKey(key: DictionaryFileKey): InputStream = store.openOverride(key)

    fun shouldUseOverride(key: DictionaryFileKey): Boolean {
        val spec = DictionaryFileSpecs.get(key)
        if (!store.isValidOverride(key)) return false
        return if (spec.partOfTripleDictionary) {
            shouldUseOverrideCategory(spec.category)
        } else {
            store.isExternalEnabledForKey(key)
        }
    }

    fun shouldUseOverrideCategory(category: DictionaryCategory): Boolean =
        store.isExternalEnabledForCategory(category) && isTripleComplete(category)

    fun isTripleComplete(category: DictionaryCategory): Boolean =
        DictionaryFileSpecs.forCategory(category).all { store.isValidOverride(it.key) }

    fun resolveCategoryLoadState(category: DictionaryCategory): DictionaryCategoryLoadState {
        val specs = DictionaryFileSpecs.forCategory(category)
        val hasAny = specs.any { store.hasOverride(it.key) }
        val hasAll = specs.all { store.hasOverride(it.key) }
        val validAll = specs.all { store.isValidOverride(it.key) }
        val hasInvalid = specs.any {
            store.hasOverride(it.key) &&
                (store.getOverrideMetadata(it.key)?.validationStatus == ValidationStatus.INVALID)
        }

        if (hasInvalid) return DictionaryCategoryLoadState.Invalid
        if (specs.all { !it.partOfTripleDictionary }) {
            return DictionaryCategoryLoadState.Bundled
        }
        if (hasAny && (!hasAll || !validAll)) return DictionaryCategoryLoadState.Partial
        if (validAll && store.isExternalEnabledForCategory(category)) return DictionaryCategoryLoadState.User
        if (category.isOptionalMozcUt()) {
            return if (store.isOptionalBundledEnabled(category)) {
                DictionaryCategoryLoadState.Bundled
            } else {
                DictionaryCategoryLoadState.Disabled
            }
        }
        return DictionaryCategoryLoadState.Bundled
    }
}
