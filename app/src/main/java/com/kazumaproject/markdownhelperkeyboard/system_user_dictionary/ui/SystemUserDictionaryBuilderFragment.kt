package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSystemUserDictionaryBuilderBinding
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntry
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DateFormat

@AndroidEntryPoint
class SystemUserDictionaryBuilderFragment : Fragment() {

    private val viewModel: SystemUserDictionaryBuilderViewModel by viewModels()

    private var _binding: FragmentSystemUserDictionaryBuilderBinding? = null
    private val binding get() = _binding!!

    private lateinit var idEntries: List<IdDefEntry>
    private lateinit var validContextIdSet: Set<Int>

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportBuiltDictionary(uri)
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    showImportModeDialog { mode ->
                        importDictionary(uri, mode)
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSystemUserDictionaryBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        idEntries = viewModel.getIdDefEntries()
        validContextIdSet = idEntries.map { it.id }.toSet()
        setupMenu()
        setupRecyclerView()
        binding.fabAddEntry.setOnClickListener {
            showEditDialog(null)
        }
        binding.buttonBuildDictionary.setOnClickListener {
            buildDictionary()
        }
        observeEntries()
        updateBuildStatus()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = ""
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.system_user_dictionary_builder_menu, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_delete_built_system_user_dictionary)?.isVisible =
                    viewModel.hasBuiltDictionary()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {

                    R.id.action_export_system_user_dictionary -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import_system_user_dictionary -> {
                        launchImportFilePicker()
                        true
                    }

                    R.id.action_import_from_app_dictionary -> {
                        showImportFromAppDialog()
                        true
                    }

                    R.id.action_delete_all_system_user_dictionary -> {
                        confirmDeleteAll()
                        true
                    }

                    R.id.action_delete_built_system_user_dictionary -> {
                        confirmDeleteBuiltDictionary()
                        true
                    }

                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = SystemUserDictionaryEntryAdapter { entry ->
            showEditDialog(entry)
        }
    }

    private fun observeEntries() {
        viewModel.allEntries.observe(viewLifecycleOwner) { entries ->
            (binding.recyclerViewEntries.adapter as SystemUserDictionaryEntryAdapter).submitList(
                entries
            )
            binding.textViewEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateBuildStatus() {
        val metadata = viewModel.readBuildMetadata()
        binding.textViewBuildStatus.text = if (metadata == null) {
            getString(R.string.system_user_dictionary_not_built)
        } else {
            val formattedTime = DateFormat.getDateTimeInstance().format(metadata.builtAt)
            getString(
                R.string.system_user_dictionary_last_built_format,
                metadata.entryCount,
                formattedTime,
            )
        }
    }

    private fun buildDictionary() {
        viewLifecycleOwner.lifecycleScope.launch {
            val metadata = viewModel.buildDictionary()
            updateBuildStatus()
            requireActivity().invalidateOptionsMenu()
            val message = if (metadata.entryCount == 0) {
                getString(R.string.system_user_dictionary_cleared)
            } else {
                getString(R.string.system_user_dictionary_build_success, metadata.entryCount)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "system_user_dictionary.zip")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "text/plain",
                    "text/tab-separated-values",
                    "text/csv",
                    "application/octet-stream",
                ),
            )
        }
        importLauncher.launch(intent)
    }

    private fun exportBuiltDictionary(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val exported = viewModel.exportBuiltDictionary(uri)
            val messageRes = if (exported) {
                R.string.system_user_dictionary_export_success
            } else {
                R.string.system_user_dictionary_export_failed
            }
            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importDictionary(uri: Uri, mode: SystemUserDictionaryBuilderViewModel.ImportMode) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = viewModel.importDictionary(uri, mode)) {
                is SystemUserDictionaryBuilderViewModel.DictionaryImportResult.BuiltDictionary -> {
                    updateBuildStatus()
                    requireActivity().invalidateOptionsMenu()
                    val message = result.importedEntries?.let {
                        getString(R.string.system_user_dictionary_import_success_with_count, it)
                    } ?: getString(R.string.system_user_dictionary_import_success)
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }

                is SystemUserDictionaryBuilderViewModel.DictionaryImportResult.ExternalDictionary -> {
                    val message = if (result.importedEntries > 0) {
                        getString(
                            R.string.system_user_dictionary_import_external_success,
                            result.importedEntries,
                            result.skippedLines,
                        )
                    } else {
                        getString(
                            R.string.system_user_dictionary_import_external_no_entries,
                            result.skippedLines,
                        )
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }

                is SystemUserDictionaryBuilderViewModel.DictionaryImportResult.InternalDictionary -> {
                    val message = getString(
                        R.string.system_user_dictionary_import_internal_success,
                        result.importedEntries,
                        result.skippedEntries,
                    )
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }


                SystemUserDictionaryBuilderViewModel.DictionaryImportResult.Failed -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.system_user_dictionary_import_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun showImportModeDialog(
        onSelected: (SystemUserDictionaryBuilderViewModel.ImportMode) -> Unit,
    ) {
        val labels = arrayOf(
            getString(R.string.system_user_dictionary_import_mode_append),
            getString(R.string.system_user_dictionary_import_mode_replace),
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.system_user_dictionary_import_mode_title)
            .setItems(labels) { _, which ->
                val mode = if (which == 1) {
                    SystemUserDictionaryBuilderViewModel.ImportMode.REPLACE_ALL
                } else {
                    SystemUserDictionaryBuilderViewModel.ImportMode.APPEND
                }
                onSelected(mode)
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showImportFromAppDialog() {
        val sourceItems = arrayOf(
            getString(R.string.system_user_dictionary_import_source_learn),
            getString(R.string.system_user_dictionary_import_source_user_word),
            getString(R.string.system_user_dictionary_import_source_user_template),
        )
        val checked = booleanArrayOf(true, true, true)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.system_user_dictionary_import_source_title)
            .setMultiChoiceItems(sourceItems, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.next_string) { _, _ ->
                val selected = mutableSetOf<SystemUserDictionaryBuilderViewModel.InternalImportSource>()
                if (checked[0]) selected += SystemUserDictionaryBuilderViewModel.InternalImportSource.LEARN
                if (checked[1]) selected += SystemUserDictionaryBuilderViewModel.InternalImportSource.USER_WORD
                if (checked[2]) selected += SystemUserDictionaryBuilderViewModel.InternalImportSource.USER_TEMPLATE

                if (selected.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        R.string.system_user_dictionary_import_source_empty,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@setPositiveButton
                }

                showImportModeDialog { mode ->
                    importFromInternalSources(selected, mode)
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun importFromInternalSources(
        sources: Set<SystemUserDictionaryBuilderViewModel.InternalImportSource>,
        mode: SystemUserDictionaryBuilderViewModel.ImportMode,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = viewModel.importFromInternalSources(sources, mode)) {
                is SystemUserDictionaryBuilderViewModel.DictionaryImportResult.InternalDictionary -> {
                    val message = getString(
                        R.string.system_user_dictionary_import_internal_success,
                        result.importedEntries,
                        result.skippedEntries,
                    )
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }

                else -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.system_user_dictionary_import_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.system_user_dictionary_delete_all_message)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteAll()
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun confirmDeleteBuiltDictionary() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.system_user_dictionary_delete_built_message)
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = viewModel.clearBuiltDictionary()
                    updateBuildStatus()
                    requireActivity().invalidateOptionsMenu()
                    val messageRes = if (deleted) {
                        R.string.system_user_dictionary_cleared
                    } else {
                        R.string.system_user_dictionary_delete_built_failed
                    }
                    Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showEditDialog(entry: SystemUserDictionaryEntry?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_system_user_dictionary_entry, null)

        val yomiEdit = dialogView.findViewById<EditText>(R.id.edit_text_yomi_dialog)
        val tangoEdit = dialogView.findViewById<EditText>(R.id.edit_text_tango_dialog)
        val scoreEdit = dialogView.findViewById<EditText>(R.id.edit_text_score_dialog)
        val leftIdInput =
            dialogView.findViewById<AutoCompleteTextView>(R.id.auto_complete_left_id_dialog)
        val rightIdInput =
            dialogView.findViewById<AutoCompleteTextView>(R.id.auto_complete_right_id_dialog)

        yomiEdit.setText(entry?.yomi.orEmpty())
        tangoEdit.setText(entry?.tango.orEmpty())
        scoreEdit.setText(
            (entry?.score ?: SystemUserDictionaryBuilderViewModel.DEFAULT_SCORE).toString()
        )
        leftIdInput.setText(
            resolveDisplayText(
                entry?.leftId ?: SystemUserDictionaryBuilderViewModel.DEFAULT_CONTEXT_ID
            ), false
        )
        rightIdInput.setText(
            resolveDisplayText(
                entry?.rightId ?: SystemUserDictionaryBuilderViewModel.DEFAULT_CONTEXT_ID
            ), false
        )
        setupContextIdPicker(leftIdInput, R.string.system_user_dictionary_left_id)
        setupContextIdPicker(rightIdInput, R.string.system_user_dictionary_right_id)

        AlertDialog.Builder(requireContext())
            .setTitle(
                if (entry == null) R.string.system_user_dictionary_add_entry
                else R.string.system_user_dictionary_edit_entry,
            )
            .setView(dialogView)
            .setPositiveButton(R.string.save_string) { _, _ ->
                val yomi = yomiEdit.text.toString().trim()
                val tango = tangoEdit.text.toString().trim()
                val score = scoreEdit.text.toString().trim().toIntOrNull()
                val leftId = parseSelectedId(leftIdInput.text.toString())
                val rightId = parseSelectedId(rightIdInput.text.toString())

                if (!validateInput(yomi, tango, score, leftId, rightId)) {
                    return@setPositiveButton
                }

                val entity = (entry ?: SystemUserDictionaryEntry(
                    yomi = "",
                    tango = "",
                    score = 0,
                    leftId = 0,
                    rightId = 0,
                )).copy(
                    yomi = yomi,
                    tango = tango,
                    score = score!!,
                    leftId = leftId!!,
                    rightId = rightId!!,
                    updatedAt = System.currentTimeMillis(),
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    if (entry == null) {
                        viewModel.insert(entity)
                    } else {
                        viewModel.update(entity)
                    }
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .apply {
                if (entry != null) {
                    setNeutralButton(R.string.delete_string) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.delete(entry.id)
                        }
                    }
                }
            }
            .show()
    }

    private fun setupContextIdPicker(
        inputView: AutoCompleteTextView,
        @StringRes titleRes: Int,
    ) {
        inputView.keyListener = null
        inputView.isCursorVisible = false
        inputView.setOnClickListener {
            showContextIdPickerDialog(titleRes) { selectedId ->
                inputView.setText(resolveDisplayText(selectedId), false)
            }
        }
    }

    private fun showContextIdPickerDialog(
        @StringRes titleRes: Int,
        onSelected: (Int) -> Unit,
    ) {
        val pickerView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_system_user_dictionary_context_id_picker, null)
        val searchEdit = pickerView.findViewById<EditText>(R.id.edit_text_context_id_search)
        val listView = pickerView.findViewById<ListView>(R.id.list_view_context_id)

        val filteredEntries = idEntries.toMutableList()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            filteredEntries.map { it.displayText }.toMutableList(),
        )
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(pickerView)
            .setNegativeButton(R.string.cancel_string, null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            onSelected(filteredEntries[position].id)
            dialog.dismiss()
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString().orEmpty().trim()
                val nextItems = if (keyword.isBlank()) {
                    idEntries
                } else {
                    idEntries.filter { entry ->
                        entry.displayText.contains(keyword, ignoreCase = true)
                    }
                }

                filteredEntries.clear()
                filteredEntries.addAll(nextItems)
                adapter.clear()
                adapter.addAll(nextItems.map { it.displayText })
                adapter.notifyDataSetChanged()
            }
        })

        dialog.show()
    }

    private fun validateInput(
        yomi: String,
        tango: String,
        score: Int?,
        leftId: Int?,
        rightId: Int?,
    ): Boolean {
        when {
            yomi.isBlank() || tango.isBlank() -> {
                Toast.makeText(
                    requireContext(),
                    R.string.enter_word_and_yomi_string,
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            !yomi.all { it in 'ぁ'..'ゖ' || it == 'ー' } -> {
                Toast.makeText(
                    requireContext(),
                    R.string.system_user_dictionary_invalid_yomi,
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            score == null || score !in Short.MIN_VALUE..Short.MAX_VALUE -> {
                Toast.makeText(
                    requireContext(),
                    R.string.system_user_dictionary_invalid_score,
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            leftId == null || !validContextIdSet.contains(leftId) ||
                    rightId == null || !validContextIdSet.contains(rightId) -> {
                Toast.makeText(
                    requireContext(),
                    R.string.system_user_dictionary_invalid_context_id,
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
        }
        return true
    }

    private fun resolveDisplayText(id: Int): String {
        return viewModel.getIdDefEntryById(id)?.displayText ?: id.toString()
    }

    private fun parseSelectedId(value: String): Int? {
        return value.trim().substringBefore(' ').toIntOrNull()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
