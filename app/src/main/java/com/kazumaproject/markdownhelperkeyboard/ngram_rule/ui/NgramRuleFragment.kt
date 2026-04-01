package com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentNgramRuleBinding
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntry
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream

@AndroidEntryPoint
class NgramRuleFragment : Fragment() {

    private val viewModel: NgramRuleViewModel by viewModels()

    private var _binding: FragmentNgramRuleBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NgramRuleAdapter

    private var twoNodeItems: List<TwoNodeRuleItem> = emptyList()
    private var threeNodeItems: List<ThreeNodeRuleItem> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { exportToUri(it) }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importFromUri(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNgramRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupButtons()
        observeRules()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.ngram_rule_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export -> {
                        launchExportPicker()
                        true
                    }

                    R.id.action_import -> {
                        launchImportPicker()
                        true
                    }

                    R.id.action_delete_all -> {
                        confirmDeleteAll()
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
        adapter = NgramRuleAdapter { item ->
            when (item.kind) {
                NgramRuleKind.TWO_NODE -> {
                    twoNodeItems.firstOrNull { it.id == item.id }?.let { showTwoNodeDialog(it) }
                }

                NgramRuleKind.THREE_NODE -> {
                    threeNodeItems.firstOrNull { it.id == item.id }?.let { showThreeNodeDialog(it) }
                }
            }
        }
        binding.recyclerViewRules.adapter = adapter
    }

    private fun setupButtons() {
        binding.buttonAddTwoNode.setOnClickListener { showTwoNodeDialog(null) }
        binding.buttonAddThreeNode.setOnClickListener { showThreeNodeDialog(null) }
    }

    private fun observeRules() {
        viewModel.twoNodeRules.observe(viewLifecycleOwner) { rules ->
            twoNodeItems = rules
            renderList()
        }
        viewModel.threeNodeRules.observe(viewLifecycleOwner) { rules ->
            threeNodeItems = rules
            renderList()
        }
    }

    private fun renderList() {
        val items = buildList {
            addAll(twoNodeItems.map { item ->
                NgramRuleListItem(
                    stableId = "two-${item.id}",
                    title = getString(R.string.ngram_rule_two_node_title),
                    detail = "${item.prev.toSummary()} -> ${item.current.toSummary()}",
                    adjustment = item.adjustment,
                    kind = NgramRuleKind.TWO_NODE,
                    id = item.id,
                )
            })
            addAll(threeNodeItems.map { item ->
                NgramRuleListItem(
                    stableId = "three-${item.id}",
                    title = getString(R.string.ngram_rule_three_node_title),
                    detail = "${item.first.toSummary()} -> ${item.second.toSummary()} -> ${item.third.toSummary()}",
                    adjustment = item.adjustment,
                    kind = NgramRuleKind.THREE_NODE,
                    id = item.id,
                )
            })
        }
        adapter.submitList(items)
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showTwoNodeDialog(existing: TwoNodeRuleItem?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ngram_rule_editor, null)
        val idEntries = viewModel.idEntries()

        val editWord1 = dialogView.findViewById<EditText>(R.id.edit_word_1)
        val editLeft1 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_left_id_1)
        val editRight1 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_right_id_1)

        val editWord2 = dialogView.findViewById<EditText>(R.id.edit_word_2)
        val editLeft2 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_left_id_2)
        val editRight2 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_right_id_2)

        val node3Layout = dialogView.findViewById<View>(R.id.layout_node_3)
        node3Layout.visibility = View.GONE

        val editAdjustment = dialogView.findViewById<EditText>(R.id.edit_adjustment)

        setupIdAutoComplete(editLeft1, idEntries)
        setupIdAutoComplete(editRight1, idEntries)
        setupIdAutoComplete(editLeft2, idEntries)
        setupIdAutoComplete(editRight2, idEntries)

        existing?.let { item ->
            editWord1.setText(item.prev.word.orEmpty())
            setIdText(editLeft1, item.prev.leftId)
            setIdText(editRight1, item.prev.rightId)
            editWord2.setText(item.current.word.orEmpty())
            setIdText(editLeft2, item.current.leftId)
            setIdText(editRight2, item.current.rightId)
            editAdjustment.setText(item.adjustment.toString())
        }

        dialogView.findViewById<Button>(R.id.button_search_word_1).setOnClickListener {
            showWordSearchDialog(editWord1)
        }
        dialogView.findViewById<Button>(R.id.button_search_word_2).setOnClickListener {
            showWordSearchDialog(editWord2)
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.ngram_rule_add_two_node else R.string.ngram_rule_edit_two_node)
            .setView(dialogView)
            .setPositiveButton(R.string.save_string) { _, _ ->
                val adjustment = parseAdjustment(editAdjustment.text.toString()) ?: return@setPositiveButton
                val validIds = idEntries.map { it.id }.toSet()
                val left1 = parseIdOrNull(editLeft1.text.toString(), validIds)
                val right1 = parseIdOrNull(editRight1.text.toString(), validIds)
                val left2 = parseIdOrNull(editLeft2.text.toString(), validIds)
                val right2 = parseIdOrNull(editRight2.text.toString(), validIds)
                if (left1 == INVALID_ID || right1 == INVALID_ID || left2 == INVALID_ID || right2 == INVALID_ID) {
                    return@setPositiveButton
                }
                viewModel.saveTwoNodeRule(
                    TwoNodeRuleForm(
                        id = existing?.id,
                        prev = NodeFeatureInput(
                            word = editWord1.text.toString().trim().ifBlank { null },
                            leftId = left1,
                            rightId = right1,
                        ),
                        current = NodeFeatureInput(
                            word = editWord2.text.toString().trim().ifBlank { null },
                            leftId = left2,
                            rightId = right2,
                        ),
                        adjustment = adjustment,
                    )
                )
            }
            .setNegativeButton(R.string.cancel_string, null)

        if (existing != null) {
            builder.setNeutralButton(R.string.delete_string) { _, _ ->
                viewModel.deleteTwoNodeRule(existing.id)
            }
        }

        builder.show()
    }

    private fun showThreeNodeDialog(existing: ThreeNodeRuleItem?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ngram_rule_editor, null)
        val idEntries = viewModel.idEntries()

        val editWord1 = dialogView.findViewById<EditText>(R.id.edit_word_1)
        val editLeft1 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_left_id_1)
        val editRight1 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_right_id_1)

        val editWord2 = dialogView.findViewById<EditText>(R.id.edit_word_2)
        val editLeft2 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_left_id_2)
        val editRight2 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_right_id_2)

        val editWord3 = dialogView.findViewById<EditText>(R.id.edit_word_3)
        val editLeft3 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_left_id_3)
        val editRight3 = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_right_id_3)

        val editAdjustment = dialogView.findViewById<EditText>(R.id.edit_adjustment)

        setupIdAutoComplete(editLeft1, idEntries)
        setupIdAutoComplete(editRight1, idEntries)
        setupIdAutoComplete(editLeft2, idEntries)
        setupIdAutoComplete(editRight2, idEntries)
        setupIdAutoComplete(editLeft3, idEntries)
        setupIdAutoComplete(editRight3, idEntries)

        existing?.let { item ->
            editWord1.setText(item.first.word.orEmpty())
            setIdText(editLeft1, item.first.leftId)
            setIdText(editRight1, item.first.rightId)
            editWord2.setText(item.second.word.orEmpty())
            setIdText(editLeft2, item.second.leftId)
            setIdText(editRight2, item.second.rightId)
            editWord3.setText(item.third.word.orEmpty())
            setIdText(editLeft3, item.third.leftId)
            setIdText(editRight3, item.third.rightId)
            editAdjustment.setText(item.adjustment.toString())
        }

        dialogView.findViewById<Button>(R.id.button_search_word_1).setOnClickListener {
            showWordSearchDialog(editWord1)
        }
        dialogView.findViewById<Button>(R.id.button_search_word_2).setOnClickListener {
            showWordSearchDialog(editWord2)
        }
        dialogView.findViewById<Button>(R.id.button_search_word_3).setOnClickListener {
            showWordSearchDialog(editWord3)
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.ngram_rule_add_three_node else R.string.ngram_rule_edit_three_node)
            .setView(dialogView)
            .setPositiveButton(R.string.save_string) { _, _ ->
                val adjustment = parseAdjustment(editAdjustment.text.toString()) ?: return@setPositiveButton
                val validIds = idEntries.map { it.id }.toSet()
                val left1 = parseIdOrNull(editLeft1.text.toString(), validIds)
                val right1 = parseIdOrNull(editRight1.text.toString(), validIds)
                val left2 = parseIdOrNull(editLeft2.text.toString(), validIds)
                val right2 = parseIdOrNull(editRight2.text.toString(), validIds)
                val left3 = parseIdOrNull(editLeft3.text.toString(), validIds)
                val right3 = parseIdOrNull(editRight3.text.toString(), validIds)
                if (
                    left1 == INVALID_ID || right1 == INVALID_ID ||
                    left2 == INVALID_ID || right2 == INVALID_ID ||
                    left3 == INVALID_ID || right3 == INVALID_ID
                ) {
                    return@setPositiveButton
                }
                viewModel.saveThreeNodeRule(
                    ThreeNodeRuleForm(
                        id = existing?.id,
                        first = NodeFeatureInput(editWord1.text.toString().trim().ifBlank { null }, left1, right1),
                        second = NodeFeatureInput(editWord2.text.toString().trim().ifBlank { null }, left2, right2),
                        third = NodeFeatureInput(editWord3.text.toString().trim().ifBlank { null }, left3, right3),
                        adjustment = adjustment,
                    )
                )
            }
            .setNegativeButton(R.string.cancel_string, null)

        if (existing != null) {
            builder.setNeutralButton(R.string.delete_string) { _, _ ->
                viewModel.deleteThreeNodeRule(existing.id)
            }
        }

        builder.show()
    }

    private fun showWordSearchDialog(target: EditText) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ngram_word_search, null)
        val editSearch = dialogView.findViewById<EditText>(R.id.edit_search_hiragana)
        val buttonSearch = dialogView.findViewById<Button>(R.id.button_search)
        val listView = dialogView.findViewById<ListView>(R.id.list_suggestions)
        val listAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        listView.adapter = listAdapter

        val observer = Observer<List<WordSuggestion>> { list ->
            listAdapter.clear()
            listAdapter.addAll(list.map { "${it.word} (${it.score})" })
        }
        viewModel.wordSuggestions.observe(viewLifecycleOwner, observer)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.ngram_rule_word_search_title)
            .setView(dialogView)
            .setNegativeButton(R.string.ngram_rule_close, null)
            .create()

        buttonSearch.setOnClickListener {
            viewModel.searchWordsByHiragana(editSearch.text.toString())
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val picked = viewModel.wordSuggestions.value?.getOrNull(position)?.word ?: return@setOnItemClickListener
            target.setText(picked)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            viewModel.wordSuggestions.removeObserver(observer)
        }

        dialog.show()
    }

    private fun setupIdAutoComplete(view: AutoCompleteTextView, entries: List<IdDefEntry>) {
        val values = entries.map { it.displayText }
        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, values)
        view.setAdapter(arrayAdapter)
    }

    private fun setIdText(view: AutoCompleteTextView, id: Int?) {
        if (id == null) {
            view.setText("", false)
            return
        }
        val entry = viewModel.idEntries().firstOrNull { it.id == id }
        view.setText(entry?.displayText ?: id.toString(), false)
    }

    private fun parseIdOrNull(raw: String, validIds: Set<Int>): Int? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val parsed = text.substringBefore(' ').toIntOrNull()
        if (parsed == null || !validIds.contains(parsed)) {
            Toast.makeText(requireContext(), R.string.system_user_dictionary_invalid_context_id, Toast.LENGTH_SHORT)
                .show()
            return INVALID_ID
        }
        return parsed
    }

    private fun parseAdjustment(raw: String): Int? {
        val value = raw.trim().toIntOrNull()
        if (value == null) {
            Toast.makeText(requireContext(), R.string.ngram_rule_adjustment_invalid, Toast.LENGTH_SHORT)
                .show()
            return null
        }
        if (value !in NgramRuleViewModel.ADJUSTMENT_MIN..NgramRuleViewModel.ADJUSTMENT_MAX) {
            Toast.makeText(requireContext(), R.string.ngram_rule_adjustment_out_of_range, Toast.LENGTH_SHORT)
                .show()
            return null
        }
        return value
    }

    private fun launchExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "ngram_rules.json")
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
        val backup = NgramRuleBackup(
            twoNodeRules = twoNodeItems.map {
                TwoNodeRuleBackup(prev = it.prev, current = it.current, adjustment = it.adjustment)
            },
            threeNodeRules = threeNodeItems.map {
                ThreeNodeRuleBackup(first = it.first, second = it.second, third = it.third, adjustment = it.adjustment)
            },
        )

        if (backup.twoNodeRules.isEmpty() && backup.threeNodeRules.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ngram_rule_nothing_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val json = Gson().toJson(backup)
            requireContext().contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                }
            }
        }.onSuccess {
            Toast.makeText(requireContext(), R.string.success_to_export_string, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), R.string.fail_to_export_string, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        runCatching {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: return
            val type = object : TypeToken<NgramRuleBackup>() {}.type
            val backup: NgramRuleBackup = Gson().fromJson(json, type)
            viewModel.replaceAll(backup)
            Toast.makeText(requireContext(), R.string.ngram_rule_import_done, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), R.string.fail_to_import_string, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.ngram_rule_delete_all_message)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                viewModel.deleteAllRules()
                Toast.makeText(requireContext(), R.string.deleted_string, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun NodeFeatureInput.toSummary(): String {
        val w = word ?: "*"
        val l = leftId?.toString() ?: "*"
        val r = rightId?.toString() ?: "*"
        return "word=$w, L=$l, R=$r"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val INVALID_ID = Int.MIN_VALUE
    }
}

