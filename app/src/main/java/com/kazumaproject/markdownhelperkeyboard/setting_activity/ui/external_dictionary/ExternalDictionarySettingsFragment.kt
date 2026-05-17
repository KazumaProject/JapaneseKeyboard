package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary

import android.net.Uri
import android.os.Bundle
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
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpec
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideMetadata
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ValidationStatus
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

    @Inject
    lateinit var resolver: DictionarySourceResolver

    private var pendingKey: DictionaryFileKey? = null

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
            keys = listOf(
                DictionaryFileKey.CONNECTION_ID,
                DictionaryFileKey.POS_TABLE,
                DictionaryFileKey.ID_DEF,
            )
        )

        addTripleGroup(
            getString(R.string.external_dictionary_group_core),
            listOf(
                DictionaryCategory.SYSTEM,
                DictionaryCategory.SINGLE_KANJI,
                DictionaryCategory.EMOJI,
                DictionaryCategory.EMOTICON,
                DictionaryCategory.SYMBOL,
                DictionaryCategory.READING_CORRECTION,
                DictionaryCategory.KOTOWAZA,
            )
        )

        addTripleGroup(getString(R.string.external_dictionary_group_english), listOf(DictionaryCategory.ENGLISH))

        addTripleGroup(
            getString(R.string.external_dictionary_group_optional_mozc_ut),
            listOf(
                DictionaryCategory.PERSON_NAME,
                DictionaryCategory.PLACES,
                DictionaryCategory.WIKI,
                DictionaryCategory.NEOLOGD,
                DictionaryCategory.WEB,
            )
        )
    }

    private fun addSingleFileCategory(title: String, keys: List<DictionaryFileKey>) {
        val category = PreferenceCategory(requireContext()).apply { this.title = title }
        preferenceScreen.addPreference(category)
        keys.forEach { key ->
            val spec = DictionaryFileSpecs.get(key)
            val metadata = store.getOverrideMetadata(key)
            val valid = store.isValidOverride(key)
            category.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                this.title = getString(spec.displayNameRes)
                summary = metadataSummary(metadata, if (valid) "User selected" else "Bundled")
                isEnabled = valid
                isChecked = store.isExternalEnabledForKey(key)
                setOnPreferenceChangeListener { _, newValue ->
                    store.setExternalEnabledForKey(key, newValue == true)
                    toastApplyTiming()
                    renderPreferences()
                    true
                }
            })
            addFileActions(category, spec)
        }
    }

    private fun addTripleGroup(title: String, categories: List<DictionaryCategory>) {
        val group = PreferenceCategory(requireContext()).apply { this.title = title }
        preferenceScreen.addPreference(group)
        categories.forEach { dictionaryCategory ->
            val specs = DictionaryFileSpecs.forCategory(dictionaryCategory)
            val state = resolver.resolveCategoryLoadState(dictionaryCategory)
            group.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                this.title = categoryTitle(dictionaryCategory)
                summary = getString(R.string.external_dictionary_state_format, state.name)
                isEnabled = specs.all { store.isValidOverride(it.key) }
                isChecked = store.isExternalEnabledForCategory(dictionaryCategory)
                setOnPreferenceChangeListener { _, newValue ->
                    store.setExternalEnabledForCategory(dictionaryCategory, newValue == true)
                    toastApplyTiming()
                    renderPreferences()
                    true
                }
            })
            specs.forEach { spec -> addFileActions(group, spec) }
            group.addPreference(Preference(requireContext()).apply {
                this.title = ""
                isSelectable = false
            })
        }
    }

    private fun addFileActions(category: PreferenceCategory, spec: DictionaryFileSpec) {
        val metadata = store.getOverrideMetadata(spec.key)
        val status = fileStatus(metadata)
        category.addPreference(Preference(requireContext()).apply {
            title = getString(R.string.external_dictionary_select_file_format, getString(spec.displayNameRes))
            summary = metadataSummary(metadata, status)
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
                renderPreferences()
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
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            renderPreferences()
        }
    }

    private fun confirmResetAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.external_dictionary_reset_all)
            .setMessage(R.string.external_dictionary_reset_all_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                store.removeAllOverrides()
                toastApplyTiming()
                renderPreferences()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun fileStatus(metadata: DictionaryOverrideMetadata?): String {
        return when (metadata?.validationStatus) {
            ValidationStatus.VALID -> "User selected"
            ValidationStatus.INVALID -> "Invalid"
            null -> "Missing"
        }
    }

    private fun metadataSummary(metadata: DictionaryOverrideMetadata?, status: String): String {
        if (metadata == null) return status
        val date = DateFormat.format("yyyy-MM-dd HH:mm", Date(metadata.importedAt))
        val size = Formatter.formatFileSize(requireContext(), metadata.size)
        return listOf(
            status,
            metadata.originalFileName,
            size,
            date,
            metadata.validationMessage,
        ).joinToString("\n")
    }

    private fun categoryTitle(category: DictionaryCategory): String {
        return when (category) {
            DictionaryCategory.SYSTEM -> "system"
            DictionaryCategory.SINGLE_KANJI -> "single_kanji"
            DictionaryCategory.EMOJI -> "emoji"
            DictionaryCategory.EMOTICON -> "emoticon"
            DictionaryCategory.SYMBOL -> "symbol"
            DictionaryCategory.READING_CORRECTION -> "reading_correction"
            DictionaryCategory.KOTOWAZA -> "kotowaza"
            DictionaryCategory.ENGLISH -> "english"
            DictionaryCategory.PERSON_NAME -> "person_name"
            DictionaryCategory.PLACES -> "places"
            DictionaryCategory.WIKI -> "wiki"
            DictionaryCategory.NEOLOGD -> "neologd"
            DictionaryCategory.WEB -> "web"
            DictionaryCategory.COMMON -> "common"
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
