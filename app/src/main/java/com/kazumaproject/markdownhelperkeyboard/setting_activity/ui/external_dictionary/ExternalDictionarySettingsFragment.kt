package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpec
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideMetadata
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ValidationStatus
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.isDisableableBundledDictionary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class ExternalDictionarySettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var store: DictionaryOverrideStore

    private var pendingKey: DictionaryFileKey? = null
    private val displayStateResolver: ExternalDictionaryDisplayStateResolver
        get() = ExternalDictionaryDisplayStateResolver(store)

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val key = pendingKey
            pendingKey = null
            if (uri != null && key != null) {
                importOverride(key, uri)
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        renderPreferences()
    }

    private fun renderPreferences() {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        Preference(requireContext()).apply {
            title = getString(R.string.external_dictionary_apply_timing_title)
            summary = getString(R.string.external_dictionary_apply_timing_summary)
            isSelectable = false
            screen.addPreference(this)
        }

        Preference(requireContext()).apply {
            title = getString(R.string.external_dictionary_reset_all)
            summary = getString(R.string.external_dictionary_reset_all_summary)
            setOnPreferenceClickListener {
                confirmResetAll()
                true
            }
            screen.addPreference(this)
        }

        addSingleFileCategory(
            title = getString(R.string.external_dictionary_category_common),
            keys = COMMON_REPLACEMENT_KEYS
        )

        DICTIONARY_CATEGORIES.forEach { addDictionaryCategory(it) }
    }

    private fun addSingleFileCategory(title: String, keys: List<DictionaryFileKey>) {
        val category = PreferenceCategory(requireContext()).apply { this.title = title }
        preferenceScreen.addPreference(category)
        keys.forEach { key ->
            val spec = DictionaryFileSpecs.get(key)
            val metadata = store.getOverrideMetadata(key)
            val switchState = displayStateResolver.resolveFileDisplayState(key)
            category.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                this.title = switchTitle(getString(spec.displayNameRes))
                summary = buildFileSummary(metadata, switchState.displayState)
                isEnabled = switchState.switchEnabled
                isChecked = switchState.switchChecked
                setOnPreferenceChangeListener { _, newValue ->
                    store.setExternalEnabledForKey(key, newValue == true)
                    toastApplyTiming()
                    rerenderPreferencesKeepingScroll()
                    true
                }
            })
            addFileActions(category, spec)
        }
    }

    private fun addDictionaryCategory(dictionaryCategory: DictionaryCategory) {
        val category = PreferenceCategory(requireContext()).apply {
            title = categoryTitle(dictionaryCategory)
        }
        preferenceScreen.addPreference(category)

        val specs = DictionaryFileSpecs.forCategory(dictionaryCategory)
        if (dictionaryCategory in CORE_REPLACEMENT_CATEGORIES) {
            val switchState = displayStateResolver.resolveCategoryDisplayState(dictionaryCategory)
            category.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                this.title = switchTitle(categoryTitle(dictionaryCategory))
                summary = buildCategorySummary(switchState)
                isEnabled = switchState.switchEnabled
                isChecked = switchState.switchChecked
                setOnPreferenceChangeListener { _, newValue ->
                    store.setExternalEnabledForCategory(dictionaryCategory, newValue == true)
                    toastApplyTiming()
                    rerenderPreferencesKeepingScroll()
                    true
                }
            })
        } else if (dictionaryCategory.isDisableableBundledDictionary()) {
            val switchState = displayStateResolver.resolveDisableableCategoryDisplayState(dictionaryCategory)
            category.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                title = getString(R.string.external_dictionary_use_dictionary_switch_title)
                summary = buildDisableableCategorySummary(dictionaryCategory, switchState)
                isEnabled = switchState.switchEnabled
                isChecked = switchState.switchChecked
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue == true
                    store.setOptionalBundledEnabled(dictionaryCategory, enabled)
                    store.setExternalEnabledForCategory(
                        dictionaryCategory,
                        enabled && specs.all { store.isValidOverride(it.key) },
                    )
                    toastApplyTiming()
                    rerenderPreferencesKeepingScroll()
                    true
                }
            })
        }
        specs.forEach { spec -> addFileActions(category, spec) }
    }

    private fun addFileActions(category: PreferenceCategory, spec: DictionaryFileSpec) {
        val metadata = store.getOverrideMetadata(spec.key)
        val state = displayStateResolver.resolveFileDisplayState(spec)
        category.addPreference(Preference(requireContext()).apply {
            title = getString(R.string.external_dictionary_select_file_format, getString(spec.displayNameRes))
            summary = buildFileSummary(metadata, state)
            setOnPreferenceClickListener {
                pendingKey = spec.key
                openDocumentLauncher.launch(arrayOf("*/*"))
                true
            }
        })
        category.addPreference(Preference(requireContext()).apply {
            title = getString(R.string.external_dictionary_remove_file_format, getString(spec.displayNameRes))
            summary = getString(R.string.external_dictionary_remove_file_summary)
            isEnabled = store.hasOverride(spec.key)
            setOnPreferenceClickListener {
                store.removeOverride(spec.key)
                toastApplyTiming()
                rerenderPreferencesKeepingScroll()
                true
            }
        })
    }

    private fun importOverride(key: DictionaryFileKey, uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                store.saveOverrideFromUri(key, uri)
            }
            val message = if (result.isValid) {
                getString(R.string.external_dictionary_import_success)
            } else {
                getString(R.string.external_dictionary_import_failed_format, result.message)
            }
            if (!isAdded || view == null) return@launch
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            rerenderPreferencesKeepingScroll()
        }
    }

    private fun confirmResetAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.external_dictionary_reset_all)
            .setMessage(R.string.external_dictionary_reset_all_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                store.removeAllOverrides()
                toastApplyTiming()
                rerenderPreferencesKeepingScroll()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun rerenderPreferencesKeepingScroll() {
        if (!isAdded || view == null) return
        val savedState: Parcelable? = listView.layoutManager?.onSaveInstanceState()
        renderPreferences()
        if (savedState != null) {
            listView.post {
                if (isAdded && view != null) {
                    listView.layoutManager?.onRestoreInstanceState(savedState)
                }
            }
        }
    }

    private fun buildFileSummary(
        metadata: DictionaryOverrideMetadata?,
        state: ExternalDictionaryDisplayState,
    ): String {
        if (metadata == null) {
            return getString(R.string.external_dictionary_state_format, displayStateText(state))
        }
        val date = DateFormat.format("yyyy-MM-dd HH:mm", Date(metadata.importedAt))
        val size = Formatter.formatFileSize(requireContext(), metadata.size)
        return listOf(
            getString(R.string.external_dictionary_state_format, displayStateText(state)),
            getString(R.string.external_dictionary_external_file_selected_format, metadata.originalFileName),
            getString(R.string.external_dictionary_size_format, size),
            getString(R.string.external_dictionary_imported_format, date),
            metadata.validationMessage,
        ).joinToString("\n")
    }

    private fun buildCategorySummary(switchState: ExternalDictionarySwitchState): String {
        val lines = mutableListOf(
            getString(R.string.external_dictionary_state_format, displayStateText(switchState.displayState))
        )
        when {
            switchState.displayState == ExternalDictionaryDisplayState.BundledInUseWithValidOverride ->
                lines += getString(R.string.external_dictionary_external_file_selected)
            !switchState.switchEnabled ->
                lines += getString(R.string.external_dictionary_no_valid_external_file_selected)
        }
        return lines.joinToString("\n")
    }

    private fun buildDisableableCategorySummary(
        category: DictionaryCategory,
        switchState: ExternalDictionarySwitchState,
    ): String {
        val lines = mutableListOf(
            getString(R.string.external_dictionary_state_format, displayStateText(switchState.displayState))
        )
        val specs = DictionaryFileSpecs.forCategory(category)
        val hasInvalidOverride = specs.any { spec ->
            store.hasOverride(spec.key) &&
                store.getOverrideMetadata(spec.key)?.validationStatus == ValidationStatus.INVALID
        }
        val hasAnyOverride = specs.any { store.hasOverride(it.key) }
        val hasAllValidOverrides = specs.all { store.isValidOverride(it.key) }
        when {
            hasInvalidOverride ->
                lines += getString(R.string.external_dictionary_switch_on_invalid_uses_bundled)
            hasAnyOverride && !hasAllValidOverrides ->
                lines += getString(R.string.external_dictionary_switch_on_partial_uses_bundled)
            switchState.displayState == ExternalDictionaryDisplayState.BundledInUseWithValidOverride ->
                lines += getString(R.string.external_dictionary_external_file_selected)
        }
        return lines.joinToString("\n")
    }

    private fun displayStateText(state: ExternalDictionaryDisplayState): String {
        val resId = when (state) {
            ExternalDictionaryDisplayState.BundledInUseNoOverride,
            ExternalDictionaryDisplayState.BundledInUseWithValidOverride ->
                R.string.external_dictionary_state_bundled_in_use
            ExternalDictionaryDisplayState.ExternalInUse ->
                R.string.external_dictionary_state_external_in_use
            ExternalDictionaryDisplayState.Disabled ->
                R.string.external_dictionary_state_disabled
            ExternalDictionaryDisplayState.InvalidOverrideBundledFallback ->
                R.string.external_dictionary_state_invalid_bundled_fallback
            ExternalDictionaryDisplayState.PartialOverrideBundledFallback ->
                R.string.external_dictionary_state_partial_bundled_fallback
        }
        return getString(resId)
    }

    private fun switchTitle(dictionaryName: String): String =
        getString(
            R.string.external_dictionary_switch_title_format,
            dictionaryName,
            getString(R.string.external_dictionary_replace_with_external_file),
        )

    private fun categoryTitle(category: DictionaryCategory): String {
        return when (category) {
            DictionaryCategory.SYSTEM -> getString(R.string.external_dictionary_category_system)
            DictionaryCategory.SINGLE_KANJI -> getString(R.string.external_dictionary_category_single_kanji)
            DictionaryCategory.EMOJI -> getString(R.string.external_dictionary_category_emoji)
            DictionaryCategory.EMOTICON -> getString(R.string.external_dictionary_category_emoticon)
            DictionaryCategory.SYMBOL -> getString(R.string.external_dictionary_category_symbol)
            DictionaryCategory.READING_CORRECTION -> "reading_correction"
            DictionaryCategory.KOTOWAZA -> "kotowaza"
            DictionaryCategory.ENGLISH -> getString(R.string.external_dictionary_category_english)
            DictionaryCategory.PERSON_NAME -> getString(R.string.mozc_ut_person_name_title)
            DictionaryCategory.PLACES -> getString(R.string.mozc_ut_places_title)
            DictionaryCategory.WIKI -> getString(R.string.mozc_ut_wiki_title)
            DictionaryCategory.NEOLOGD -> getString(R.string.mozc_ut_neologd_title)
            DictionaryCategory.WEB -> getString(R.string.mozc_ut_web_title)
            DictionaryCategory.COMMON -> getString(R.string.external_dictionary_category_common)
        }
    }

    private fun toastApplyTiming() {
        Toast.makeText(
            requireContext(),
            R.string.external_dictionary_apply_timing_summary,
            Toast.LENGTH_LONG
        ).show()
    }
}

private val DICTIONARY_CATEGORIES = listOf(
    DictionaryCategory.SYSTEM,
    DictionaryCategory.SINGLE_KANJI,
    DictionaryCategory.EMOJI,
    DictionaryCategory.EMOTICON,
    DictionaryCategory.SYMBOL,
    DictionaryCategory.ENGLISH,
    DictionaryCategory.READING_CORRECTION,
    DictionaryCategory.KOTOWAZA,
    DictionaryCategory.PERSON_NAME,
    DictionaryCategory.PLACES,
    DictionaryCategory.WIKI,
    DictionaryCategory.NEOLOGD,
    DictionaryCategory.WEB,
)
