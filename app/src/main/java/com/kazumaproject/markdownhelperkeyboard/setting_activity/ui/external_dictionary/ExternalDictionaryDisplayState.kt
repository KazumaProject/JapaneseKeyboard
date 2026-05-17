package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary

import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpec
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ValidationStatus

internal val CORE_REPLACEMENT_CATEGORIES = listOf(
    DictionaryCategory.SYSTEM,
    DictionaryCategory.SINGLE_KANJI,
    DictionaryCategory.EMOJI,
    DictionaryCategory.EMOTICON,
    DictionaryCategory.SYMBOL,
    DictionaryCategory.ENGLISH,
)

internal val COMMON_REPLACEMENT_KEYS = listOf(
    DictionaryFileKey.CONNECTION_ID,
    DictionaryFileKey.POS_TABLE,
    DictionaryFileKey.ID_DEF,
)

internal enum class ExternalDictionaryDisplayState {
    BundledInUseNoOverride,
    BundledInUseWithValidOverride,
    ExternalInUse,
    InvalidOverrideBundledFallback,
    PartialOverrideBundledFallback,
}

internal data class ExternalDictionarySwitchState(
    val displayState: ExternalDictionaryDisplayState,
    val switchEnabled: Boolean,
    val switchChecked: Boolean,
)

internal class ExternalDictionaryDisplayStateResolver(
    private val store: DictionaryOverrideStore,
) {
    fun resolveFileDisplayState(key: DictionaryFileKey): ExternalDictionarySwitchState {
        val metadata = store.getOverrideMetadata(key)
        val hasInvalidOverride =
            store.hasOverride(key) && metadata?.validationStatus == ValidationStatus.INVALID
        val hasValidOverride = store.isValidOverride(key)
        val displayState = when {
            hasInvalidOverride -> ExternalDictionaryDisplayState.InvalidOverrideBundledFallback
            hasValidOverride && store.isExternalEnabledForKey(key) ->
                ExternalDictionaryDisplayState.ExternalInUse
            hasValidOverride -> ExternalDictionaryDisplayState.BundledInUseWithValidOverride
            else -> ExternalDictionaryDisplayState.BundledInUseNoOverride
        }
        return ExternalDictionarySwitchState(
            displayState = displayState,
            switchEnabled = hasValidOverride,
            switchChecked = displayState == ExternalDictionaryDisplayState.ExternalInUse,
        )
    }

    fun resolveFileDisplayState(spec: DictionaryFileSpec): ExternalDictionaryDisplayState {
        val fileState = resolveFileDisplayState(spec.key).displayState
        if (!spec.partOfTripleDictionary) return fileState
        val categoryState = resolveCategoryDisplayState(spec.category).displayState
        return if (
            categoryState == ExternalDictionaryDisplayState.ExternalInUse &&
            store.isValidOverride(spec.key)
        ) {
            ExternalDictionaryDisplayState.ExternalInUse
        } else {
            fileState
        }
    }

    fun resolveCategoryDisplayState(category: DictionaryCategory): ExternalDictionarySwitchState {
        val specs = DictionaryFileSpecs.forCategory(category)
        val hasAnyOverride = specs.any { store.hasOverride(it.key) }
        val hasInvalidOverride = specs.any { spec ->
            store.hasOverride(spec.key) &&
                store.getOverrideMetadata(spec.key)?.validationStatus == ValidationStatus.INVALID
        }
        val hasAllValidOverrides = specs.all { store.isValidOverride(it.key) }
        val displayState = when {
            hasInvalidOverride -> ExternalDictionaryDisplayState.InvalidOverrideBundledFallback
            hasAllValidOverrides && store.isExternalEnabledForCategory(category) ->
                ExternalDictionaryDisplayState.ExternalInUse
            hasAllValidOverrides -> ExternalDictionaryDisplayState.BundledInUseWithValidOverride
            hasAnyOverride -> ExternalDictionaryDisplayState.PartialOverrideBundledFallback
            else -> ExternalDictionaryDisplayState.BundledInUseNoOverride
        }
        return ExternalDictionarySwitchState(
            displayState = displayState,
            switchEnabled = hasAllValidOverrides,
            switchChecked = displayState == ExternalDictionaryDisplayState.ExternalInUse,
        )
    }
}
