package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.KeyboardLayoutAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.FileOutputStream

@AndroidEntryPoint
class KeyboardListFragment : Fragment(R.layout.fragment_keyboard_list) {

    private val viewModel: KeyboardListViewModel by viewModels()
    private val keyboardEditorViewMode: KeyboardEditorViewModel by viewModels()

    private var _binding: FragmentKeyboardListBinding? = null
    private val binding get() = _binding!!

    // [ADD] Launcher for exporting files
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportLayouts(uri)
                }
            }
        }

    // [ADD] Launcher for importing files
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importLayouts(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardListBinding.bind(view)

        setupMenu() // [ADD] Call menu setup

        val adapter = KeyboardLayoutAdapter(
            onItemClick = { layout ->
                val action =
                    KeyboardListFragmentDirections.actionKeyboardListFragmentToKeyboardEditorFragment(
                        layout.layoutId
                    )
                findNavController().navigate(action)
            },
            onDeleteClick = { layout ->
                showDeleteConfirmationDialog(layout.layoutId)
            },
            onDuplicateClick = { layout ->
                viewModel.duplicateLayout(layout.layoutId)
            }
        )
        binding.keyboardLayoutsRecyclerView.adapter = adapter
        binding.keyboardLayoutsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.keyboardLayoutsRecyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
        binding.fabAddLayout.setOnClickListener {
            val action =
                KeyboardListFragmentDirections.actionKeyboardListFragmentToKeyboardEditorFragment(-1L)
            findNavController().navigate(action)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.layouts.collect { layouts ->
                adapter.submitList(layouts)
            }
        }
    }

    // [ADD] Function to set up the menu
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.keyboard_list_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export_layouts -> {
                        launchExportPicker()
                        true
                    }

                    R.id.action_import_layouts -> {
                        launchImportPicker()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // [ADD] Functions to launch file pickers
    private fun launchExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "keyboard_layouts_backup.json")
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

    // [ADD] Core logic for export and import
    private fun exportLayouts(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val layoutsToExport = keyboardEditorViewMode.getLayoutsForExport()
                if (layoutsToExport.isEmpty()) {
                    Toast.makeText(
                        context,
                        "エクスポートするレイアウトがありません",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val jsonString = Gson().toJson(layoutsToExport)
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(context, "エクスポートが完了しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "エクスポートに失敗しました: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importLayouts(uri: Uri) {
        try {
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                it.reader(Charsets.UTF_8).readText()
            }
            if (jsonString != null) {
                val type = object : TypeToken<List<FullKeyboardLayout>>() {}.type
                val layouts: List<FullKeyboardLayout> = Gson().fromJson(jsonString, type)
                keyboardEditorViewMode.importLayouts(layouts)
                Toast.makeText(
                    context,
                    "${layouts.size}件のレイアウトをインポートしました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "インポートに失敗しました。ファイルが破損しているか形式が正しくありません。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDeleteConfirmationDialog(layoutId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(com.kazumaproject.core.R.string.dialog_delete_title))
            .setMessage(getString(com.kazumaproject.core.R.string.dialog_delete_message))
            .setNegativeButton(getString(com.kazumaproject.core.R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(com.kazumaproject.core.R.string.dialog_delete)) { _, _ ->
                viewModel.deleteLayout(layoutId)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
