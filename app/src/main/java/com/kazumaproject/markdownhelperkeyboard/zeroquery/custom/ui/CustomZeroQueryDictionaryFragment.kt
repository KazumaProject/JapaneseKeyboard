package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.DialogCustomZeroQueryEntryBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCustomZeroQueryDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryBackupEntry
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryGroup
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryImportMode
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.adapter.CustomZeroQueryGroupAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.FileOutputStream

@AndroidEntryPoint
class CustomZeroQueryDictionaryFragment : Fragment() {

    private var _binding: FragmentCustomZeroQueryDictionaryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomZeroQueryDictionaryViewModel by viewModels()
    private lateinit var adapter: CustomZeroQueryGroupAdapter
    private val gson = Gson()
    private var pendingImportUri: Uri? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { exportToUri(it) }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingImportUri = result.data?.data
            if (pendingImportUri != null) {
                showImportModeDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomZeroQueryDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupControls()
        observeState()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(
            object : androidx.core.view.MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.custom_zero_query_dictionary_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        android.R.id.home -> {
                            findNavController().popBackStack()
                            true
                        }
                        R.id.action_custom_zero_query_export -> {
                            launchExportPicker()
                            true
                        }
                        R.id.action_custom_zero_query_import -> {
                            launchImportPicker()
                            true
                        }
                        R.id.action_custom_zero_query_delete_all -> {
                            showDeleteAllDialog()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun setupRecyclerView() {
        adapter = CustomZeroQueryGroupAdapter(
            onOpen = { group ->
                findNavController().navigate(
                    R.id.customZeroQueryGroupFragment,
                    bundleOf("lookupKey" to group.lookupKey),
                )
            },
            onAddCandidate = { group -> showEntryDialog(presetGroup = group) },
            onMore = { group, anchor -> showGroupPopup(group, anchor) },
        )
        binding.recyclerViewCustomZeroQueryGroups.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CustomZeroQueryDictionaryFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupControls() {
        binding.editTextCustomZeroQuerySearch.doAfterTextChanged {
            viewModel.updateQuery(it?.toString().orEmpty())
        }
        binding.toggleGroupCustomZeroQueryFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.updateFilter(
                when (checkedId) {
                    R.id.button_custom_zero_query_filter_enabled -> CustomZeroQueryFilter.Enabled
                    R.id.button_custom_zero_query_filter_disabled -> CustomZeroQueryFilter.Disabled
                    else -> CustomZeroQueryFilter.All
                }
            )
        }

        val sortLabels = listOf(
            getString(R.string.custom_zero_query_sort_key),
            getString(R.string.custom_zero_query_sort_updated),
            getString(R.string.custom_zero_query_sort_candidate_count),
        )
        binding.spinnerCustomZeroQuerySort.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            sortLabels,
        )
        binding.spinnerCustomZeroQuerySort.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    viewModel.updateSort(
                        when (position) {
                            1 -> CustomZeroQuerySort.Updated
                            2 -> CustomZeroQuerySort.CandidateCount
                            else -> CustomZeroQuerySort.Key
                        }
                    )
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.fabAddCustomZeroQuery.setOnClickListener {
            showEntryDialog()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        adapter.submitList(state.groups)
                        binding.textCustomZeroQueryEmpty.isVisible = state.groups.isEmpty()
                        binding.recyclerViewCustomZeroQueryGroups.isVisible = state.groups.isNotEmpty()
                        binding.textCustomZeroQueryCount.text = getString(
                            R.string.custom_zero_query_count,
                            state.groups.size,
                            state.totalGroupCount,
                        )
                    }
                }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showEntryDialog(presetGroup: CustomZeroQueryGroup? = null) {
        val dialogBinding = DialogCustomZeroQueryEntryBinding.inflate(layoutInflater)
        presetGroup?.let { group ->
            dialogBinding.editTextCustomZeroQueryKey.setText(group.displayKey)
            dialogBinding.editTextCustomZeroQueryKey.isEnabled = false
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_zero_query_add_entry)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_string, null)
            .setNeutralButton(R.string.custom_zero_query_save_and_add_next, null)
            .setNegativeButton(R.string.cancel_string, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveFromDialog(dialogBinding)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                saveFromDialog(dialogBinding)
                dialogBinding.editTextCustomZeroQueryCandidate.text?.clear()
                dialogBinding.editTextCustomZeroQueryCandidate.requestFocus()
            }
        }
        dialog.show()
    }

    private fun saveFromDialog(dialogBinding: DialogCustomZeroQueryEntryBinding) {
        viewModel.addEntry(
            displayKey = dialogBinding.editTextCustomZeroQueryKey.text?.toString().orEmpty(),
            candidate = dialogBinding.editTextCustomZeroQueryCandidate.text?.toString().orEmpty(),
            enabled = dialogBinding.switchCustomZeroQueryEnabled.isChecked,
        )
    }

    private fun showGroupPopup(group: CustomZeroQueryGroup, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(R.string.custom_zero_query_add_candidate)
            menu.add(R.string.custom_zero_query_open_group)
            menu.add(R.string.custom_zero_query_delete_group)
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    getString(R.string.custom_zero_query_add_candidate) -> {
                        showEntryDialog(group)
                        true
                    }
                    getString(R.string.custom_zero_query_open_group) -> {
                        findNavController().navigate(
                            R.id.customZeroQueryGroupFragment,
                            bundleOf("lookupKey" to group.lookupKey),
                        )
                        true
                    }
                    else -> {
                        showDeleteGroupDialog(group)
                        true
                    }
                }
            }
            show()
        }
    }

    private fun showDeleteGroupDialog(group: CustomZeroQueryGroup) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_zero_query_delete_group)
            .setMessage(getString(R.string.custom_zero_query_delete_group_message, group.displayKey))
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.deleteGroup(group.lookupKey)
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showDeleteAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_zero_query_delete_all_title)
            .setMessage(R.string.custom_zero_query_delete_all_message)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                viewModel.deleteAll()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun launchExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "custom_zero_query_dictionary.json")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun exportToUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = viewModel.exportEntries()
            if (entries.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.there_is_no_word_to_export),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).use { output ->
                    output.write(gson.toJson(entries).toByteArray(Charsets.UTF_8))
                }
            }
            Toast.makeText(
                requireContext(),
                getString(R.string.success_to_export_string),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun showImportModeDialog() {
        val labels = arrayOf(
            getString(R.string.custom_zero_query_import_append_skip),
            getString(R.string.custom_zero_query_import_append_replace),
            getString(R.string.custom_zero_query_import_replace_all),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_zero_query_import_mode_title)
            .setItems(labels) { _, which ->
                val mode = when (which) {
                    1 -> CustomZeroQueryImportMode.AppendReplaceDuplicates
                    2 -> CustomZeroQueryImportMode.ReplaceAll
                    else -> CustomZeroQueryImportMode.AppendSkipDuplicates
                }
                importFromPendingUri(mode)
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun importFromPendingUri(mode: CustomZeroQueryImportMode) {
        val uri = pendingImportUri ?: return
        pendingImportUri = null
        try {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val type = object : TypeToken<List<CustomZeroQueryBackupEntry>>() {}.type
            val entries: List<CustomZeroQueryBackupEntry> = gson.fromJson(json, type)
            viewModel.importEntries(entries, mode)
        } catch (_: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.fail_to_import_string),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
