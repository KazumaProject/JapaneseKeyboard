package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_learn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentLearnDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.learning.adapter.LearnDictionaryAdapter
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class DictionaryLearnFragment : Fragment() {

    private var _binding: FragmentLearnDictionaryBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var learnRepository: LearnRepository

    private lateinit var learnDictionaryAdapter: LearnDictionaryAdapter

    // Export
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportLearnedData(uri)
                }
            }
        }

    // Import
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importLearnedData(uri)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        learnDictionaryAdapter = LearnDictionaryAdapter()

        setupMenu()
        setupRecyclerView()
        observeDictionaryData()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.learn_dictionary_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export_learn -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import_learn -> {
                        launchImportFilePicker()
                        true
                    }

                    R.id.action_delete_all_learn -> {
                        showDeleteAllConfirmationDialog()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        binding.learnDictionaryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = learnDictionaryAdapter
        }

        learnDictionaryAdapter.setOnItemChildrenClickListener { input, output ->
            showEditDeleteDialog(input, output)
        }

        learnDictionaryAdapter.setOnItemLongClickListener { input ->
            showConfirmationDialog(
                message = buildSpannableMessage(getString(R.string.yomi_edit_text_title), input),
                positiveAction = { deleteByInput(input) })
        }

        learnDictionaryAdapter.setOnItemChildrenLongClickListener { input, output ->
            showConfirmationDialog(
                message = buildSpannableMessage(getString(R.string.word_edit_text_title), output),
                positiveAction = { deleteByInputAndOutput(input, output) })
        }
    }

    private fun observeDictionaryData() {
        lifecycleScope.launch {
            learnRepository.all().collectLatest { data ->
                val transformedData = data.groupBy { it.input }.toSortedMap(compareBy { it })
                    .map { (key, value) -> key to value.map { it.out } }
                learnDictionaryAdapter.learnDataList = transformedData
            }
        }
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "learned_dictionary_backup.json")
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
            .setTitle(getString(R.string.reset_learning_dictionary_dialog_title))
            .setMessage(getString(R.string.reset_learning_dictionary_dialog_message))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                lifecycleScope.launch { deleteAll() }
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .show()
    }

    private fun exportLearnedData(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBarLearnDictionaryFragment.isVisible = true
            val wordsToExport = learnRepository.all().first()
            if (wordsToExport.isEmpty()) {
                Toast.makeText(context, "エクスポートする単語がありません", Toast.LENGTH_SHORT)
                    .show()
                binding.progressBarLearnDictionaryFragment.isVisible = false
                return@launch
            }
            try {
                val jsonString = Gson().toJson(wordsToExport)
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
                e.printStackTrace()
                Toast.makeText(
                    context,
                    getString(R.string.fail_to_export_string),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBarLearnDictionaryFragment.isVisible = false
            }
        }
    }

    private fun importLearnedData(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBarLearnDictionaryFragment.isVisible = true
            try {
                val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                    it.reader(Charsets.UTF_8).readText()
                }

                if (jsonString != null) {
                    val type = object : TypeToken<List<LearnEntity>>() {}.type
                    val words: List<LearnEntity> = Gson().fromJson(jsonString, type)
                    val wordsToInsert = words.map { it.copy(id = null) }
                    learnRepository.insertAll(wordsToInsert)
                    Toast.makeText(
                        context,
                        "${words.size}${getString(R.string.import_text_string)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    getString(R.string.fail_to_import_string),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBarLearnDictionaryFragment.isVisible = false
            }
        }
    }

    private fun showEditDeleteDialog(input: String, output: String) {
        lifecycleScope.launch {
            val entity = learnRepository.findLearnDataByInputAndOutput(input, output)
            if (entity == null) {
                Toast.makeText(
                    context,
                    getString(R.string.data_not_found_string),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val context = requireContext()
            val dialogView =
                LayoutInflater.from(context).inflate(R.layout.dialog_edit_learn_entry, null)
            val editTextYomi = dialogView.findViewById<TextInputEditText>(R.id.edit_text_input)
            val editTextTango = dialogView.findViewById<TextInputEditText>(R.id.edit_text_output)
            val editTextScore = dialogView.findViewById<TextInputEditText>(R.id.edit_text_score)

            editTextYomi.setText(entity.input)
            editTextTango.setText(entity.out)
            editTextScore.setText(entity.score.toString())

            val dialog = AlertDialog.Builder(context)
                .setTitle(getString(R.string.learn_dictionary_edit_dialog_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.save_string)) { _, _ ->
                    val newInput = editTextYomi.text.toString()
                    val newOutput = editTextTango.text.toString()
                    val newScore = editTextScore.text.toString().toShortOrNull() ?: entity.score

                    if (newInput.isNotBlank() && newOutput.isNotBlank()) {
                        updateLearnData(entity, newInput, newOutput, newScore)
                    }
                }
                .setNegativeButton(getString(R.string.cancel_string), null)
                .setNeutralButton(getString(R.string.delete_string)) { _, _ ->
                    showConfirmationDialog(
                        message = buildSpannableMessage(
                            getString(R.string.word_edit_text_title),
                            output
                        ),
                        positiveAction = { deleteByInputAndOutput(input, output) }
                    )
                }
                .show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(
                    ContextCompat.getColor(
                        context,
                        com.kazumaproject.core.R.color.enter_key_bg
                    )
                )
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(
                    ContextCompat.getColor(
                        context,
                        com.kazumaproject.core.R.color.main_text_color
                    )
                )
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setTextColor(
                    ContextCompat.getColor(
                        context,
                        com.kazumaproject.core.R.color.main_text_color
                    )
                )
        }
    }

    private fun updateLearnData(
        originalEntity: LearnEntity,
        newInput: String,
        newOutput: String,
        newScore: Short
    ) {
        lifecycleScope.launch {
            binding.progressBarLearnDictionaryFragment.isVisible = true
            val updatedEntity =
                originalEntity.copy(input = newInput, out = newOutput, score = newScore)
            learnRepository.update(updatedEntity)
            binding.progressBarLearnDictionaryFragment.isVisible = false
        }
    }

    private fun showConfirmationDialog(
        message: CharSequence, positiveAction: suspend () -> Unit
    ) {
        val dialog =
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.confirm_delete_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.yes_string)) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        binding.progressBarLearnDictionaryFragment.isVisible = true
                        positiveAction()
                        binding.progressBarLearnDictionaryFragment.isVisible = false
                    }
                }.setNegativeButton(getString(R.string.no_string), null).show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.enter_key_bg
                )
            )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.main_text_color
                )
            )
    }

    private fun buildSpannableMessage(prefix: String, content: String): SpannableStringBuilder {
        return SpannableStringBuilder().append(prefix).append(
            content, ForegroundColorSpan(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.enter_key_bg
                )
            ), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        ).append(getString(R.string.confirm_delete_message))
    }

    private suspend fun deleteByInput(input: String) {
        learnRepository.deleteByInput(input)
    }

    private suspend fun deleteByInputAndOutput(input: String, output: String) {
        learnRepository.deleteByInputAndOutput(input = input, output = output)
    }

    private suspend fun deleteAll() {
        learnRepository.deleteAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
