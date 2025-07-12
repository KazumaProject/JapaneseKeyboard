package com.kazumaproject.markdownhelperkeyboard.ng_word.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentNgWordBinding
import com.kazumaproject.markdownhelperkeyboard.ng_word.adapter.NgWordAdapter
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream

@AndroidEntryPoint
class NgWordFragment : Fragment() {

    private val viewModel: NgWordViewModel by viewModels()
    private var _binding: FragmentNgWordBinding? = null
    private val binding get() = _binding!!

    // Export launcher
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { exportToUri(it) }
        }
    }

    // Import launcher
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importFromUri(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNgWordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.ng_word_menu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_export -> {
                        launchExportPicker()
                        true
                    }

                    R.id.action_import -> {
                        launchImportPicker()
                        true
                    }

                    R.id.action_delete_all -> {
                        showDeleteAllDialog()
                        true
                    }

                    android.R.id.home -> {
                        findNavController().popBackStack()
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
            putExtra(Intent.EXTRA_TITLE, "ng_words.json")
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

    private fun showDeleteAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("すべて削除")
            .setMessage("すべてのNGワードを削除します。よろしいですか？")
            .setPositiveButton("削除") { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun exportToUri(uri: Uri) {
        val list = viewModel.allNgWords.value ?: emptyList()
        if (list.isEmpty()) {
            Toast.makeText(context, "エクスポートする項目がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val json = Gson().toJson(list)
        requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
            }
        }
        Toast.makeText(context, "エクスポート完了", Toast.LENGTH_SHORT).show()
    }

    private fun importFromUri(uri: Uri) {
        try {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.readText() ?: return
            val type = object : TypeToken<List<NgWord>>() {}.type
            val list: List<NgWord> = Gson().fromJson(json, type)
            viewModel.insertAll(list.map { NgWord(yomi = it.yomi, tango = it.tango) })
            Toast.makeText(context, "インポート完了: ${list.size} 件", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "インポートに失敗しました", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFab() {
        binding.fabAddNgWord.setOnClickListener {
            val cv = binding.cardViewAddNgWord
            cv.isGone = !cv.isGone
            if (!cv.isGone) {
                binding.editTextYomi.requestFocus()
                (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(binding.editTextYomi, 0)
                binding.buttonAddNgWord.setOnClickListener { addNgWord() }
            } else {
                hideKeyboardAndClearFocus()
            }
        }
    }

    private fun addNgWord() {
        val yomi = binding.editTextYomi.text.toString().trim()
        val tango = binding.editTextTango.text.toString().trim()
        if (yomi.isEmpty() || tango.isEmpty()) {
            Toast.makeText(context, "読みと単語を入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.insert(yomi, tango)
        hideKeyboardAndClearFocus()
        binding.cardViewAddNgWord.isGone = true
    }

    private fun setupRecyclerView() {
        val adapter = NgWordAdapter { ng ->
            showEditDialog(ng)
        }
        binding.recyclerViewNgWords.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.allNgWords.observe(viewLifecycleOwner) { list ->
            (binding.recyclerViewNgWords.adapter as NgWordAdapter)
                .submitList(list)
        }
    }

    private fun showEditDialog(item: NgWord) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_ng_word, null)
        val etYomi = v.findViewById<EditText>(R.id.edit_text_yomi_dialog)
        val etTango = v.findViewById<EditText>(R.id.edit_text_tango_dialog)
        etYomi.setText(item.yomi)
        etTango.setText(item.tango)

        AlertDialog.Builder(requireContext())
            .setTitle("編集／削除")
            .setView(v)
            .setPositiveButton("更新") { _, _ ->
                val newY = etYomi.text.toString().trim()
                val newT = etTango.text.toString().trim()
                if (newY.isNotEmpty() && newT.isNotEmpty()) {
                    viewModel.update(item.copy(yomi = newY, tango = newT))
                }
            }
            .setNeutralButton("削除") { _, _ ->
                viewModel.delete(item)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun hideKeyboardAndClearFocus() {
        (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
