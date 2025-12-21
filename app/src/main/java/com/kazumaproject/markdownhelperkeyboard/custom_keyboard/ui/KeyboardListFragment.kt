package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.KeyboardLayoutAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.OutputStreamWriter
import java.io.StringReader

@AndroidEntryPoint
class KeyboardListFragment : Fragment(R.layout.fragment_keyboard_list) {

    private val viewModel: KeyboardListViewModel by viewModels()
    private val keyboardEditorViewMode: KeyboardEditorViewModel by viewModels()

    private var _binding: FragmentKeyboardListBinding? = null
    private val binding get() = _binding!!

    private var itemTouchHelper: ItemTouchHelper? = null

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

        setupMenu()

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
            },
            onStartDrag = { vh ->
                itemTouchHelper?.startDrag(vh)
            }
        )

        binding.keyboardLayoutsRecyclerView.adapter = adapter
        binding.keyboardLayoutsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.keyboardLayoutsRecyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        // ★追加: ItemTouchHelper
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false // ハンドルのみで開始
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (from == to) return false

                val current = adapter.currentList.toMutableList()
                val moved = current.removeAt(from)
                current.add(to, moved)

                // ListAdapter なので新しいList参照で submit
                adapter.submitList(current)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                // ★ドロップ後に永続化
                val idsInDisplayOrder = adapter.currentList.map { it.layoutId }
                viewModel.updateLayoutOrder(idsInDisplayOrder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }

        itemTouchHelper = ItemTouchHelper(callback).also {
            it.attachToRecyclerView(binding.keyboardLayoutsRecyclerView)
        }

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
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        importLauncher.launch(intent)
    }

    private val exportGson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create()
    }

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

                val jsonString = exportGson.toJson(layoutsToExport)

                requireContext().contentResolver.openOutputStream(uri, "w")?.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                        writer.write(jsonString)
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes =
                    requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: run {
                            Toast.makeText(
                                context,
                                "ファイルを読み込めませんでした",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }

                val jsonString = bytes.toString(Charsets.UTF_8)
                    .trimStart('\uFEFF')
                    .replace("\u0000", "")

                val type = object : TypeToken<List<FullKeyboardLayout>>() {}.type

                val gson = GsonBuilder()
                    .setLenient()
                    .create()

                val reader = JsonReader(StringReader(jsonString)).apply {
                    isLenient = true
                }

                val layouts: List<FullKeyboardLayout> = gson.fromJson(reader, type) ?: emptyList()

                if (layouts.isEmpty()) {
                    Toast.makeText(context, "インポート対象が空です", Toast.LENGTH_LONG).show()
                    return@launch
                }

                keyboardEditorViewMode.importLayouts(layouts)
                Toast.makeText(
                    context,
                    "${layouts.size}件のレイアウトをインポートしました",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "インポートに失敗しました。ファイルが破損しているか形式が正しくありません。",
                    Toast.LENGTH_LONG
                ).show()
                Timber.e(e, "importLayouts failed")
            }
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
        itemTouchHelper = null
    }
}
