package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSystemUserDictionaryBuilderBinding
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntry
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SystemUserDictionaryBuilderFragment : Fragment() {

    private val viewModel: SystemUserDictionaryBuilderViewModel by viewModels()

    private var _binding: FragmentSystemUserDictionaryBuilderBinding? = null
    private val binding get() = _binding!!

    private lateinit var idEntries: List<IdDefEntry>

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

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.system_user_dictionary_builder_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_build_system_user_dictionary -> {
                        buildDictionary()
                        true
                    }

                    R.id.action_delete_all_system_user_dictionary -> {
                        confirmDeleteAll()
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
            (binding.recyclerViewEntries.adapter as SystemUserDictionaryEntryAdapter).submitList(entries)
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
            val message = if (metadata.entryCount == 0) {
                getString(R.string.system_user_dictionary_cleared)
            } else {
                getString(R.string.system_user_dictionary_build_success, metadata.entryCount)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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

    private fun showEditDialog(entry: SystemUserDictionaryEntry?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_system_user_dictionary_entry, null)

        val yomiEdit = dialogView.findViewById<EditText>(R.id.edit_text_yomi_dialog)
        val tangoEdit = dialogView.findViewById<EditText>(R.id.edit_text_tango_dialog)
        val scoreEdit = dialogView.findViewById<EditText>(R.id.edit_text_score_dialog)
        val leftIdInput = dialogView.findViewById<AutoCompleteTextView>(R.id.auto_complete_left_id_dialog)
        val rightIdInput = dialogView.findViewById<AutoCompleteTextView>(R.id.auto_complete_right_id_dialog)

        val dropdownItems = idEntries.map { it.displayText }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dropdownItems)
        leftIdInput.setAdapter(adapter)
        rightIdInput.setAdapter(adapter)
        leftIdInput.threshold = 0
        rightIdInput.threshold = 0
        leftIdInput.setOnClickListener { leftIdInput.showDropDown() }
        rightIdInput.setOnClickListener { rightIdInput.showDropDown() }

        yomiEdit.setText(entry?.yomi.orEmpty())
        tangoEdit.setText(entry?.tango.orEmpty())
        scoreEdit.setText((entry?.score ?: SystemUserDictionaryBuilderViewModel.DEFAULT_SCORE).toString())
        leftIdInput.setText(resolveDisplayText(entry?.leftId ?: SystemUserDictionaryBuilderViewModel.DEFAULT_CONTEXT_ID), false)
        rightIdInput.setText(resolveDisplayText(entry?.rightId ?: SystemUserDictionaryBuilderViewModel.DEFAULT_CONTEXT_ID), false)

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

    private fun validateInput(
        yomi: String,
        tango: String,
        score: Int?,
        leftId: Int?,
        rightId: Int?,
    ): Boolean {
        when {
            yomi.isBlank() || tango.isBlank() -> {
                Toast.makeText(requireContext(), R.string.enter_word_and_yomi_string, Toast.LENGTH_SHORT).show()
                return false
            }

            !yomi.all { it in 'ぁ'..'ゖ' || it == 'ー' } -> {
                Toast.makeText(requireContext(), R.string.system_user_dictionary_invalid_yomi, Toast.LENGTH_SHORT).show()
                return false
            }

            score == null || score !in Short.MIN_VALUE..Short.MAX_VALUE -> {
                Toast.makeText(requireContext(), R.string.system_user_dictionary_invalid_score, Toast.LENGTH_SHORT).show()
                return false
            }

            leftId == null || leftId !in 0..2670 || rightId == null || rightId !in 0..2670 -> {
                Toast.makeText(requireContext(), R.string.system_user_dictionary_invalid_context_id, Toast.LENGTH_SHORT).show()
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
