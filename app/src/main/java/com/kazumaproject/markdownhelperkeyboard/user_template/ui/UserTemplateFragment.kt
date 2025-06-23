package com.kazumaproject.markdownhelperkeyboard.user_template.ui

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserTemplateBinding
import com.kazumaproject.markdownhelperkeyboard.user_template.adapter.UserTemplateAdapter
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream

@AndroidEntryPoint
class UserTemplateFragment : Fragment() {

    private val viewModel: UserTemplateViewModel by viewModels()

    private var _binding: FragmentUserTemplateBinding? = null
    private val binding get() = _binding!!

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportTemplates(uri)
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importTemplates(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            show()
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        resetInputFields()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // You can create a new menu file or reuse the dictionary one
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

                    R.id.action_delete_all -> {
                        showDeleteAllConfirmationDialog()
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
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "user_template_backup.txt")
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

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("全件削除の確認")
            .setMessage("登録されているすべての定型文を削除します。この操作は元に戻せません。よろしいですか？")
            .setPositiveButton("すべて削除") { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(context, "すべての定型文を削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun exportTemplates(uri: Uri) {
        viewModel.allTemplates.value?.let { templates ->
            if (templates.isEmpty()) {
                Toast.makeText(context, "エクスポートする定型文がありません", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            try {
                val jsonString = Gson().toJson(templates)
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(context, "エクスポートが完了しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importTemplates(uri: Uri) {
        try {
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                it.reader(Charsets.UTF_8).readText()
            }

            if (jsonString != null) {
                val type = object : TypeToken<List<UserTemplate>>() {}.type
                val templates: List<UserTemplate> = Gson().fromJson(jsonString, type)
                viewModel.insertAll(templates.map {
                    UserTemplate(
                        id = 0,
                        word = it.word,
                        reading = it.reading,
                        posIndex = it.posIndex,
                        posScore = it.posScore
                    )
                })
                Toast.makeText(
                    context,
                    "${templates.size}件の定型文をインポートしました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "インポートに失敗しました。ファイル形式が正しくありません。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupRecyclerView() {
        val adapter = UserTemplateAdapter { userTemplate ->
            showEditDialog(userTemplate)
        }
        binding.recyclerViewUserWords.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddWord.setOnClickListener {
            val isCurrentlyGone = binding.cardViewAddWord.isGone
            binding.cardViewAddWord.isGone = !isCurrentlyGone

            if (!binding.cardViewAddWord.isGone) { // CardView is now VISIBLE
                binding.layoutExplanation.isGone = true // Hide explanation
                binding.fabAddWord.setImageResource(com.kazumaproject.core.R.drawable.remove)
                binding.editTextWord.requestFocus()
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.editTextWord, InputMethodManager.SHOW_IMPLICIT)
            } else { // CardView is now GONE
                binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            }
        }

        binding.buttonAdd.setOnClickListener {
            addTemplate()
        }

        binding.buttonToggleExplanation.setOnClickListener {
            binding.layoutExplanation.isGone = !binding.layoutExplanation.isGone
            if (!binding.layoutExplanation.isGone) { // Explanation is now VISIBLE
                binding.cardViewAddWord.isGone = true // Hide add card
                binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.allTemplates.observe(viewLifecycleOwner) { templates ->
            templates?.let {
                (binding.recyclerViewUserWords.adapter as UserTemplateAdapter).submitList(it)
            }
        }
    }

    private fun addTemplate() {
        val word = binding.editTextWord.text.toString().trim()
        val reading = binding.editTextReading.text.toString().trim()

        if (word.isEmpty() || reading.isEmpty()) {
            Toast.makeText(context, "定型文と読みを入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        // [CHANGE] Use default values from the ViewModel
        val newUserTemplate =
            UserTemplate(
                word = word,
                reading = reading,
                posIndex = viewModel.defaultPosIndex,
                posScore = UserTemplateViewModel.DEFAULT_SCORE
            )
        viewModel.insert(newUserTemplate)

        Toast.makeText(context, "「$reading」を登録しました", Toast.LENGTH_SHORT).show()

        resetInputFields()
        binding.cardViewAddWord.isGone = true
        binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
        hideKeyboardAndClearFocus()
    }

    private fun showEditDialog(userTemplate: UserTemplate) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_word, null)
        val editWord = dialogView.findViewById<EditText>(R.id.edit_text_word_dialog)
        val editReading = dialogView.findViewById<EditText>(R.id.edit_text_reading_dialog)

        // [CHANGE] Get references to the POS and Score views
        val spinnerPosDialog = dialogView.findViewById<Spinner>(R.id.spinner_pos_dialog)
        val editPosScore = dialogView.findViewById<EditText>(R.id.edit_text_pos_score_dialog)

        // [CHANGE] Create and set the adapter for the Spinner
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.posList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPosDialog.adapter = adapter

        // [CHANGE] Set the initial values for all fields
        editWord.setText(userTemplate.word)
        editReading.setText(userTemplate.reading)
        spinnerPosDialog.setSelection(userTemplate.posIndex)
        editPosScore.setText(userTemplate.posScore.toString())

        // [CHANGE] Make the views visible (remove the .visibility = GONE lines)

        AlertDialog.Builder(requireContext())
            .setTitle("定型文の編集")
            .setView(dialogView)
            .setPositiveButton("更新") { _, _ ->
                val updatedWord = editWord.text.toString().trim()
                val updatedReading = editReading.text.toString().trim()

                // [CHANGE] Get the updated values from the dialog views
                val updatedPosIndex = spinnerPosDialog.selectedItemPosition
                val updatedPosScore = editPosScore.text.toString().toIntOrNull()
                    ?: UserTemplateViewModel.DEFAULT_SCORE // Use default if input is invalid

                if (updatedWord.isNotEmpty() && updatedReading.isNotEmpty()) {
                    // [CHANGE] Create the updated object with all new values
                    val updatedTemplate =
                        userTemplate.copy(
                            word = updatedWord,
                            reading = updatedReading,
                            posIndex = updatedPosIndex,
                            posScore = updatedPosScore
                        )
                    viewModel.update(updatedTemplate)
                    Toast.makeText(context, "更新しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .setNeutralButton("削除") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("削除の確認")
                    .setMessage("「${userTemplate.reading}」を削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        viewModel.delete(userTemplate.id)
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
