package com.kazumaproject.markdownhelperkeyboard.clipboard_history.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentClipboardHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class ClipboardHistoryFragment : Fragment() {

    private val viewModel: ClipboardHistoryViewModel by viewModels()
    private var _binding: FragmentClipboardHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ClipboardHistoryAdapter

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> exportHistory(uri) }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> importHistory(uri) }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClipboardHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.clipboard_history_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export_clipboard -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import_clipboard -> {
                        launchImportFilePicker()
                        true
                    }

                    R.id.action_delete_all_clipboard -> {
                        showDeleteAllConfirmationDialog()
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
        adapter = ClipboardHistoryAdapter { item ->
            when (item.itemType) {
                ItemType.TEXT -> showEditTextDialog(item)
                ItemType.IMAGE -> showImageDialog(item)
            }
        }
        binding.recyclerViewClipboard.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allHistory.collect { history ->
                    adapter.submitList(history)
                }
            }
        }
    }

    private fun showEditTextDialog(item: ClipboardHistoryItem) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text_clipboard_content)
        editText.setText(item.textData)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit_word_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_string)) { _, _ ->
                val updatedText = editText.text.toString()
                viewModel.update(item.copy(textData = updatedText))
                Toast.makeText(context, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .setNeutralButton(getString(R.string.delete_string)) { _, _ ->
                viewModel.delete(item.id)
                Toast.makeText(context, getString(R.string.deleted_string), Toast.LENGTH_SHORT)
                    .show()
            }
            .show()
    }

    private fun showImageDialog(item: ClipboardHistoryItem) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_show_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.image_view_dialog)
        imageView.setImageBitmap(item.imageData)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton(getString(R.string.close), null)
            .setNeutralButton(getString(R.string.delete_string)) { _, _ ->
                viewModel.delete(item.id)
                Toast.makeText(context, getString(R.string.deleted_string), Toast.LENGTH_SHORT)
                    .show()
            }
            .setPositiveButton(getString(R.string.share)) { _, _ ->
                item.imageData?.let {
                    shareImage(it)
                } ?: Toast.makeText(context, getString(R.string.failed_share), Toast.LENGTH_SHORT)
                    .show()
            }
            .show()
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            // 1. 画像をキャッシュディレクトリに一時ファイルとして保存
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs() // ディレクトリを作成
            val file = File(cachePath, "shared_image.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            // 2. FileProviderを使ってファイルのURIを取得
            val authority = "${requireContext().packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(requireContext(), authority, file)

            // 3. 共有インテントを作成
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 読み取り権限を一時的に付与
                setDataAndType(contentUri, requireContext().contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
            }

            // 4. Chooserを起動
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_image)))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, getString(R.string.failed_share), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "clipboard_history_backup.json")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher.launch(intent)
    }

    private fun exportHistory(uri: Uri) {
        try {
            val jsonString = viewModel.exportToJson()
            requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).use { fos ->
                    fos.write(jsonString.toByteArray(Charsets.UTF_8))
                }
            }
            Toast.makeText(
                context,
                getString(R.string.success_to_export_string),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.fail_to_export_string), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun importHistory(uri: Uri) {
        try {
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                it.reader(Charsets.UTF_8).readText()
            }
            if (jsonString != null) {
                val count = viewModel.importFromJson(jsonString)
                Toast.makeText(
                    context,
                    "${count}${getString(R.string.import_item_string)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.fail_to_import_string), Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_all_delete_title))
            .setMessage(getString(R.string.delete_all_clipboard_history))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(context, getString(R.string.deleted_string), Toast.LENGTH_SHORT)
                    .show()
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
