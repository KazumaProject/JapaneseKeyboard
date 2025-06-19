package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.core.domain.cryptoManager.CryptoManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.adapter.UserWordAdapter
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream

@AndroidEntryPoint
class UserDictionaryFragment : Fragment() {

    private val viewModel: UserDictionaryViewModel by viewModels()

    private var _binding: FragmentUserDictionaryBinding? = null
    private val binding get() = _binding!!

    // Export
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportWords(uri)
                }
            }
        }

    // Import
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importWords(uri)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        setupSpinner()
        resetInputFields()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.user_dictionary_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import -> {
                        launchImportFilePicker()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "user_dictionary_backup.dat")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun exportWords(uri: Uri) {
        viewModel.allWords.value?.let { words ->
            if (words.isEmpty()) {
                Toast.makeText(context, "エクスポートする単語がありません", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            try {
                val jsonString = Gson().toJson(words)
                val encryptedData = CryptoManager.encrypt(jsonString.toByteArray(Charsets.UTF_8))

                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write(encryptedData)
                    }
                }
                Toast.makeText(context, "エクスポートが完了しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importWords(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val encryptedData = inputStream?.readBytes()
            inputStream?.close()

            if (encryptedData != null) {
                val decryptedData = CryptoManager.decrypt(encryptedData)
                val jsonString = String(decryptedData, Charsets.UTF_8)
                val type = object : TypeToken<List<UserWord>>() {}.type
                val words: List<UserWord> = Gson().fromJson(jsonString, type)

                viewModel.insertAll(words)
                Toast.makeText(
                    context,
                    "${words.size}件の単語をインポートしました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "インポートに失敗しました。ファイルが破損しているか、形式が正しくありません。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupSpinner() {
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.posList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun setupRecyclerView() {
        val adapter = UserWordAdapter { userWord ->
            showEditDialog(userWord)
        }
        binding.recyclerViewUserWords.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddWord.setOnClickListener {
            val isCurrentlyGone = binding.cardViewAddWord.isGone
            binding.cardViewAddWord.isGone = !isCurrentlyGone

            if (binding.cardViewAddWord.isGone) {
                binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            } else {
                binding.fabAddWord.setImageResource(com.kazumaproject.core.R.drawable.remove)
                binding.editTextWord.requestFocus()
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.editTextWord, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        binding.buttonAdd.setOnClickListener {
            addWord()
        }
    }

    private fun observeViewModel() {
        viewModel.allWords.observe(viewLifecycleOwner) { words ->
            words?.let {
                (binding.recyclerViewUserWords.adapter as UserWordAdapter).submitList(it)
            }
        }
    }

    private fun addWord() {
        val word = binding.editTextWord.text.toString().trim()
        val reading = binding.editTextReading.text.toString().trim()

        if (word.isEmpty() || reading.isEmpty()) {
            Toast.makeText(context, "単語と読みを入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        val newUserWord =
            UserWord(
                word = word,
                reading = reading,
                posIndex = viewModel.defaultPosIndex,
                posScore = 4000
            )
        viewModel.insert(newUserWord)

        Toast.makeText(context, "「$word」を登録しました", Toast.LENGTH_SHORT).show()

        resetInputFields()

        binding.cardViewAddWord.isGone = true
        binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
        hideKeyboardAndClearFocus()
    }

    private fun showEditDialog(userWord: UserWord) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_word, null)
        val editWord = dialogView.findViewById<EditText>(R.id.edit_text_word_dialog)
        val editReading = dialogView.findViewById<EditText>(R.id.edit_text_reading_dialog)
        val spinnerPosDialog = dialogView.findViewById<Spinner>(R.id.spinner_pos_dialog)
        val editPosScore = dialogView.findViewById<EditText>(R.id.edit_text_pos_score_dialog)

        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.posList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPosDialog.adapter = adapter

        editWord.setText(userWord.word)
        editReading.setText(userWord.reading)
        spinnerPosDialog.setSelection(userWord.posIndex)
        editPosScore.setText(userWord.posScore.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("単語の編集")
            .setView(dialogView)
            .setPositiveButton("更新") { _, _ ->
                val updatedWord = editWord.text.toString().trim()
                val updatedReading = editReading.text.toString().trim()
                val updatedPosIndex = spinnerPosDialog.selectedItemPosition
                val updatedPosScore = editPosScore.text.toString().toIntOrNull()
                    ?: UserDictionaryViewModel.DEFAULT_SCORE

                if (updatedWord.isNotEmpty() && updatedReading.isNotEmpty()) {
                    val updatedUserWord = userWord.copy(
                        word = updatedWord,
                        reading = updatedReading,
                        posIndex = updatedPosIndex,
                        posScore = updatedPosScore
                    )
                    viewModel.update(updatedUserWord)
                    Toast.makeText(context, "更新しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .setNeutralButton("削除") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("削除の確認")
                    .setMessage("「${userWord.word}」を削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        viewModel.delete(userWord.id)
                        Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            .show()
    }

    private fun hideKeyboardAndClearFocus() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    private fun resetInputFields() {
        binding.editTextWord.text?.clear()
        binding.editTextReading.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
