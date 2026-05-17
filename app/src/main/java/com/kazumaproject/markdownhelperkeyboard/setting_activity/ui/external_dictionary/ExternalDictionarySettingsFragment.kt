package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityProblem
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityResult
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileRole
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpec
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideMetadata
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryZipImportResult
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

    @Inject
    lateinit var compatibilityValidator: DictionaryCompatibilityValidator

    private var pendingKey: DictionaryFileKey? = null
    private var compatibilityUiState = CompatibilityUiState()
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

    private val openZipDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                importDictionaryZip(uri)
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        renderPreferences()
        refreshCompatibilityUiState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.external_dictionary_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_import_dictionary_zip -> {
                        openZipDocumentLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                                "application/x-zip-compressed",
                                "*/*",
                            )
                        )
                        true
                    }
                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
                val problems = compatibilityUiState.commonKeyProblems[key].orEmpty()
                this.title = switchTitle(getString(spec.displayNameRes))
                summary = buildFileSummary(metadata, switchState.displayState, problems)
                isEnabled = switchState.switchEnabled && problems.isEmpty()
                isChecked = switchState.switchChecked && problems.isEmpty()
                setOnPreferenceChangeListener { _, newValue ->
                    if (newValue == true && problems.isNotEmpty()) {
                        showCompatibilityProblemDialog(problems)
                        return@setOnPreferenceChangeListener false
                    }
                    store.setExternalEnabledForKey(key, newValue == true)
                    toastApplyTiming()
                    refreshCompatibilityUiState()
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
                val problems = compatibilityUiState.categoryProblems[dictionaryCategory].orEmpty()
                this.title = switchTitle(categoryTitle(dictionaryCategory))
                summary = buildCategorySummary(switchState, problems)
                isEnabled = switchState.switchEnabled && problems.isEmpty()
                isChecked = switchState.switchChecked && problems.isEmpty()
                setOnPreferenceChangeListener { _, newValue ->
                    if (newValue == true && problems.isNotEmpty()) {
                        showCompatibilityProblemDialog(problems)
                        return@setOnPreferenceChangeListener false
                    }
                    store.setExternalEnabledForCategory(dictionaryCategory, newValue == true)
                    toastApplyTiming()
                    refreshCompatibilityUiState()
                    true
                }
            })
        } else if (dictionaryCategory.isDisableableBundledDictionary()) {
            val switchState = displayStateResolver.resolveDisableableCategoryDisplayState(dictionaryCategory)
            category.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                val problems = compatibilityUiState.categoryProblems[dictionaryCategory].orEmpty()
                title = getString(R.string.external_dictionary_use_dictionary_switch_title)
                summary = buildDisableableCategorySummary(dictionaryCategory, switchState, problems)
                isEnabled = switchState.switchEnabled
                isChecked = switchState.switchChecked
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue == true
                    store.setOptionalBundledEnabled(dictionaryCategory, enabled)
                    if (enabled && problems.isNotEmpty()) {
                        store.setExternalEnabledForCategory(dictionaryCategory, false)
                        showCompatibilityProblemDialog(problems)
                    }
                    store.setExternalEnabledForCategory(
                        dictionaryCategory,
                        enabled && specs.all { store.isValidOverride(it.key) } && problems.isEmpty(),
                    )
                    toastApplyTiming()
                    refreshCompatibilityUiState()
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
                refreshCompatibilityUiState()
                true
            }
        })
    }

    private fun importOverride(key: DictionaryFileKey, uri: Uri) {
        lifecycleScope.launch {
            val importResult = withContext(Dispatchers.IO) {
                val result = store.saveOverrideFromUri(key, uri)
                val spec = DictionaryFileSpecs.get(key)
                val compatibilityResult = if (result.isValid) {
                    compatibilityValidator.validateActiveState()
                } else {
                    DictionaryCompatibilityResult.Compatible
                }
                val tokenCompatibilityResult = if (result.isValid && spec.role == DictionaryFileRole.TOKEN) {
                    compatibilityValidator.validateCategoryReplacement(spec.category)
                } else {
                    DictionaryCompatibilityResult.Compatible
                }
                val disabledProblems = (
                    disableIncompatibleActiveOverrides(compatibilityResult) +
                        tokenCompatibilityResult.problems()
                    ).distinctBy { it.messageForLog }
                ImportResult(result, disabledProblems)
            }
            val message = if (importResult.validationResult.isValid) {
                if (importResult.disabledProblems.isEmpty()) {
                    getString(R.string.external_dictionary_import_success)
                } else {
                    getString(R.string.external_dictionary_import_success_incompatible_not_applied)
                }
            } else {
                getString(R.string.external_dictionary_import_failed_format, importResult.validationResult.message)
            }
            if (!isAdded || view == null) return@launch
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            if (importResult.disabledProblems.isNotEmpty()) {
                showCompatibilityProblemDialog(importResult.disabledProblems)
            }
            refreshCompatibilityUiState()
        }
    }

    private fun importDictionaryZip(uri: Uri) {
        lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val importResult = withContext(Dispatchers.IO) {
                val result = store.importOverridesFromZipUri(appContext, uri)
                val importedTokenCategories = result.imported
                    .map { DictionaryFileSpecs.get(it.key) }
                    .filter { it.role == DictionaryFileRole.TOKEN }
                    .map { it.category }
                    .distinct()
                val activeProblems = if (result.imported.isNotEmpty()) {
                    disableIncompatibleActiveOverrides(compatibilityValidator.validateActiveState())
                } else {
                    emptyList()
                }
                val categoryProblems = importedTokenCategories
                    .flatMap { compatibilityValidator.validateCategoryReplacement(it).problems() }
                ZipImportWorkResult(
                    result = result,
                    compatibilityProblems = (activeProblems + categoryProblems)
                        .distinctBy { it.messageForLog },
                )
            }
            if (!isAdded || view == null) return@launch
            val resultWithCompatibility = importResult.result.copy(
                incompatible = importResult.result.incompatible +
                    importResult.compatibilityProblems.map { compatibilityProblemDetailLine(it) },
            )
            showZipImportResultDialog(resultWithCompatibility)
            toastApplyTiming()
            refreshCompatibilityUiState()
        }
    }

    private fun confirmResetAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.external_dictionary_reset_all)
            .setMessage(R.string.external_dictionary_reset_all_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                store.removeAllOverrides()
                toastApplyTiming()
                refreshCompatibilityUiState()
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
        compatibilityProblems: List<DictionaryCompatibilityProblem> = emptyList(),
    ): String {
        val compatibilityLines = compatibilitySummaryLines(compatibilityProblems)
        if (metadata == null) {
            return listOf(
                getString(R.string.external_dictionary_state_format, displayStateText(state)),
                *compatibilityLines.toTypedArray(),
            ).filter { it.isNotBlank() }.joinToString("\n")
        }
        val date = DateFormat.format("yyyy-MM-dd HH:mm", Date(metadata.importedAt))
        val size = Formatter.formatFileSize(requireContext(), metadata.size)
        return listOf(
            getString(R.string.external_dictionary_state_format, displayStateText(state)),
            getString(R.string.external_dictionary_external_file_selected_format, metadata.originalFileName),
            getString(R.string.external_dictionary_size_format, size),
            getString(R.string.external_dictionary_imported_format, date),
            metadata.validationMessage,
            *compatibilityLines.toTypedArray(),
        ).joinToString("\n")
    }

    private fun buildCategorySummary(
        switchState: ExternalDictionarySwitchState,
        compatibilityProblems: List<DictionaryCompatibilityProblem> = emptyList(),
    ): String {
        val lines = mutableListOf(
            getString(R.string.external_dictionary_state_format, displayStateText(switchState.displayState))
        )
        when {
            switchState.displayState == ExternalDictionaryDisplayState.BundledInUseWithValidOverride ->
                lines += getString(R.string.external_dictionary_external_file_selected)
            !switchState.switchEnabled ->
                lines += getString(R.string.external_dictionary_no_valid_external_file_selected)
        }
        lines += compatibilitySummaryLines(compatibilityProblems)
        return lines.joinToString("\n")
    }

    private fun buildDisableableCategorySummary(
        category: DictionaryCategory,
        switchState: ExternalDictionarySwitchState,
        compatibilityProblems: List<DictionaryCompatibilityProblem> = emptyList(),
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
        lines += compatibilitySummaryLines(compatibilityProblems)
        return lines.joinToString("\n")
    }

    private fun refreshCompatibilityUiState() {
        lifecycleScope.launch {
            val nextState = withContext(Dispatchers.IO) { buildCompatibilityUiState() }
            if (!isAdded || view == null) return@launch
            compatibilityUiState = nextState
            rerenderPreferencesKeepingScroll()
        }
    }

    private fun buildCompatibilityUiState(): CompatibilityUiState {
        val categoryProblems = DICTIONARY_CATEGORIES
            .filter { it != DictionaryCategory.ENGLISH }
            .associateWith { category ->
                val hasValidTokenOverride = DictionaryFileSpecs.forCategory(category)
                    .firstOrNull { it.role == DictionaryFileRole.TOKEN }
                    ?.let { store.isValidOverride(it.key) }
                    ?: false
                if (hasValidTokenOverride) {
                    compatibilityValidator.validateCategoryReplacement(category).problems()
                } else {
                    emptyList()
                }
            }
            .filterValues { it.isNotEmpty() }
        val commonKeyProblems = COMMON_REPLACEMENT_KEYS
            .filter { store.isValidOverride(it) }
            .associateWith { key ->
                compatibilityValidator.validateActiveState(forceOverrideKeys = setOf(key)).problems()
                    .filter { problem ->
                        problem.affectedFileKey == key ||
                            (key == DictionaryFileKey.POS_TABLE &&
                                problem.requiredFileKeys.contains(DictionaryFileKey.POS_TABLE))
                    }
            }
            .filterValues { it.isNotEmpty() }
        return CompatibilityUiState(categoryProblems, commonKeyProblems)
    }

    private fun disableIncompatibleActiveOverrides(
        result: DictionaryCompatibilityResult,
    ): List<DictionaryCompatibilityProblem> {
        val problems = result.problems()
        if (problems.isEmpty()) return emptyList()
        problems.forEach { problem ->
            val affectedKey = problem.affectedFileKey
            if (affectedKey != null && affectedKey in COMMON_REPLACEMENT_KEYS && store.isExternalEnabledForKey(affectedKey)) {
                store.setExternalEnabledForKey(affectedKey, false)
                return@forEach
            }
            if (
                problem.requiredFileKeys.contains(DictionaryFileKey.POS_TABLE) &&
                store.isExternalEnabledForKey(DictionaryFileKey.POS_TABLE)
            ) {
                store.setExternalEnabledForKey(DictionaryFileKey.POS_TABLE, false)
                return@forEach
            }
            val affectedCategory = problem.affectedCategory
            if (affectedCategory != null && store.isExternalEnabledForCategory(affectedCategory)) {
                store.setExternalEnabledForCategory(affectedCategory, false)
            }
        }
        return problems
    }

    private fun DictionaryCompatibilityResult.problems(): List<DictionaryCompatibilityProblem> =
        when (this) {
            DictionaryCompatibilityResult.Compatible -> emptyList()
            is DictionaryCompatibilityResult.Incompatible -> problems
        }

    private fun compatibilitySummaryLines(
        problems: List<DictionaryCompatibilityProblem>,
    ): List<String> {
        if (problems.isEmpty()) return emptyList()
        return listOf(
            getString(R.string.external_dictionary_compat_imported_but_not_usable),
            getString(R.string.external_dictionary_compat_import_matching_common_files),
            getString(R.string.external_dictionary_compat_dictionary_not_applied),
        ) + problems.map { problem ->
            val detail = getString(problem.messageResId, *problem.messageArgs.toTypedArray())
            val fileName = problem.affectedFileKey?.let { getString(DictionaryFileSpecs.get(it).displayNameRes) }
            val categoryName = problem.affectedCategory?.takeIf { it != DictionaryCategory.COMMON }?.let { categoryTitle(it) }
            listOfNotNull(categoryName, fileName, detail).joinToString(": ")
        }
    }

    private fun showCompatibilityProblemDialog(problems: List<DictionaryCompatibilityProblem>) {
        if (!isAdded || view == null || problems.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.external_dictionary_compat_dialog_title)
            .setMessage(compatibilitySummaryLines(problems).joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showZipImportResultDialog(result: DictionaryZipImportResult) {
        if (!isAdded || view == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.external_dictionary_zip_import_result_title)
            .setMessage(buildZipImportResultMessage(result))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun buildZipImportResultMessage(result: DictionaryZipImportResult): String {
        val lines = mutableListOf(
            getString(R.string.external_dictionary_zip_import_imported_format, result.importedCount),
            getString(R.string.external_dictionary_zip_import_skipped_format, result.skipped.size),
            getString(R.string.external_dictionary_zip_import_failed_format, result.failedCount),
            getString(R.string.external_dictionary_zip_import_total_format, result.totalEntries),
            getString(R.string.external_dictionary_zip_import_recognized_format, result.recognizedEntries),
        )
        if (result.incompatible.isNotEmpty()) {
            lines += getString(R.string.external_dictionary_zip_import_incompatible_format, result.incompatible.size)
        }
        appendResultSection(lines, R.string.external_dictionary_zip_import_imported_section) {
            result.imported.map { "${it.key.name}: ${it.entryName}" }
        }
        appendResultSection(lines, R.string.external_dictionary_zip_import_duplicate_section) {
            result.duplicateKeys.map { "${it.key.name}: ${it.entryNames.joinToString()}" }
        }
        appendResultSection(lines, R.string.external_dictionary_zip_import_failed_section) {
            result.failed.map { "${it.entryName}: ${it.reason}" }
        }
        appendResultSection(lines, R.string.external_dictionary_zip_import_skipped_section) {
            result.skipped.map { "${it.entryName}: ${it.reason}" }
        }
        appendResultSection(lines, R.string.external_dictionary_zip_import_incompatible_section) {
            result.incompatible
        }
        return lines.joinToString("\n")
    }

    private fun appendResultSection(
        lines: MutableList<String>,
        titleResId: Int,
        values: () -> List<String>,
    ) {
        val sectionValues = values()
        if (sectionValues.isEmpty()) return
        lines += ""
        lines += getString(titleResId)
        lines += sectionValues.map { "- $it" }
    }

    private fun compatibilityProblemDetailLine(problem: DictionaryCompatibilityProblem): String {
        val detail = getString(problem.messageResId, *problem.messageArgs.toTypedArray())
        val fileName = problem.affectedFileKey?.let { getString(DictionaryFileSpecs.get(it).displayNameRes) }
        val categoryName = problem.affectedCategory
            ?.takeIf { it != DictionaryCategory.COMMON }
            ?.let { categoryTitle(it) }
        return listOfNotNull(categoryName, fileName, detail).joinToString(": ")
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

private data class CompatibilityUiState(
    val categoryProblems: Map<DictionaryCategory, List<DictionaryCompatibilityProblem>> = emptyMap(),
    val commonKeyProblems: Map<DictionaryFileKey, List<DictionaryCompatibilityProblem>> = emptyMap(),
)

private data class ImportResult(
    val validationResult: com.kazumaproject.markdownhelperkeyboard.dictionary_override.ValidationResult,
    val disabledProblems: List<DictionaryCompatibilityProblem>,
)

private data class ZipImportWorkResult(
    val result: DictionaryZipImportResult,
    val compatibilityProblems: List<DictionaryCompatibilityProblem>,
)

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
