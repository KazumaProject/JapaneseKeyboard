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
    private var ruleItems: List<NgramRuleItem> = emptyList()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let(::exportToUri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let(::importFromUri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentNgramRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        adapter = NgramRuleAdapter { selected ->
            ruleItems.firstOrNull { it.id == selected.id }?.let { showRuleDialog(it.nodes.size, it) }
        }
        binding.recyclerViewRules.adapter = adapter
        binding.buttonAddTwoNode.setOnClickListener { showRuleDialog(2, null) }
        binding.buttonAddThreeNode.setOnClickListener { showRuleDialog(3, null) }
        binding.buttonAddFourNode.setOnClickListener { showRuleDialog(4, null) }
        binding.buttonAddFiveNode.setOnClickListener { showRuleDialog(5, null) }
        viewModel.rules.observe(viewLifecycleOwner) {
            ruleItems = it
            renderList()
        }
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.ngram_rule_menu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
                R.id.action_export -> true.also { launchExportPicker() }
                R.id.action_import -> true.also { launchImportPicker() }
                R.id.action_delete_all -> true.also { confirmDeleteAll() }
                android.R.id.home -> true.also { findNavController().popBackStack() }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun renderList() {
        val items = ruleItems.map { item ->
            NgramRuleListItem(
                stableId = "${item.nodes.size}-${item.id}",
                title = getString(R.string.ngram_rule_node_count_title, item.nodes.size),
                detail = item.nodes.joinToString(" -> ") { it.toSummary() },
                adjustment = item.adjustment,
                id = item.id,
            )
        }
        adapter.submitList(items)
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showRuleDialog(nodeCount: Int, existing: NgramRuleItem?) {
        require(nodeCount in 2..5)
        val dialogView = layoutInflater.inflate(R.layout.dialog_ngram_rule_editor, null)
        val entries = viewModel.idEntries()
        val nodeViews = createNodeViews(dialogView)
        nodeViews.forEachIndexed { index, views ->
            views.container?.visibility = if (index < nodeCount) View.VISIBLE else View.GONE
            setupIdAutoComplete(views.leftId, entries)
            setupIdAutoComplete(views.rightId, entries)
            views.search.setOnClickListener { showWordSearchDialog(views.word) }
            existing?.nodes?.getOrNull(index)?.let { node ->
                views.word.setText(node.word.orEmpty())
                setIdText(views.leftId, node.leftId)
                setIdText(views.rightId, node.rightId)
            }
        }
        val adjustment = dialogView.findViewById<EditText>(R.id.edit_adjustment)
        existing?.let { adjustment.setText(it.adjustment.toString()) }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ngram_rule_node_count_title, nodeCount))
            .setView(dialogView)
            .setPositiveButton(R.string.save_string) { _, _ ->
                val parsedAdjustment = parseAdjustment(adjustment.text.toString()) ?: return@setPositiveButton
                val validIds = entries.mapTo(HashSet()) { it.id }
                val nodes = nodeViews.take(nodeCount).map { views ->
                    val leftId = parseIdOrNull(views.leftId.text.toString(), validIds)
                    val rightId = parseIdOrNull(views.rightId.text.toString(), validIds)
                    if (leftId == INVALID_ID || rightId == INVALID_ID) return@setPositiveButton
                    NodeFeatureInput(
                        word = views.word.text.toString().trim().ifBlank { null },
                        leftId = leftId,
                        rightId = rightId,
                    )
                }
                viewModel.saveRule(NgramRuleForm(existing?.id, nodes, parsedAdjustment))
            }
            .setNegativeButton(R.string.cancel_string, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete_string) { _, _ -> viewModel.deleteRule(existing.id) }
        }
        builder.show()
    }

    private fun createNodeViews(root: View): List<NodeEditorViews> {
        val containers = listOf<View?>(null, null, root.findViewById(R.id.layout_node_3), root.findViewById(R.id.layout_node_4), root.findViewById(R.id.layout_node_5))
        val words = listOf(R.id.edit_word_1, R.id.edit_word_2, R.id.edit_word_3, R.id.edit_word_4, R.id.edit_word_5)
        val lefts = listOf(R.id.edit_left_id_1, R.id.edit_left_id_2, R.id.edit_left_id_3, R.id.edit_left_id_4, R.id.edit_left_id_5)
        val rights = listOf(R.id.edit_right_id_1, R.id.edit_right_id_2, R.id.edit_right_id_3, R.id.edit_right_id_4, R.id.edit_right_id_5)
        val searches = listOf(R.id.button_search_word_1, R.id.button_search_word_2, R.id.button_search_word_3, R.id.button_search_word_4, R.id.button_search_word_5)
        return words.indices.map { index ->
            NodeEditorViews(
                containers[index],
                root.findViewById(words[index]),
                root.findViewById(lefts[index]),
                root.findViewById(rights[index]),
                root.findViewById(searches[index]),
            )
        }
    }

    private fun showWordSearchDialog(target: EditText) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ngram_word_search, null)
        val searchInput = dialogView.findViewById<EditText>(R.id.edit_search_hiragana)
        val list = dialogView.findViewById<ListView>(R.id.list_suggestions)
        val listAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        list.adapter = listAdapter
        val observer = Observer<List<WordSuggestion>> { suggestions ->
            listAdapter.clear()
            listAdapter.addAll(suggestions.map { "${it.word} (${it.score})" })
        }
        viewModel.wordSuggestions.observe(viewLifecycleOwner, observer)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.ngram_rule_word_search_title)
            .setView(dialogView)
            .setNegativeButton(R.string.ngram_rule_close, null)
            .create()
        dialogView.findViewById<Button>(R.id.button_search).setOnClickListener {
            viewModel.searchWordsByHiragana(searchInput.text.toString())
        }
        list.setOnItemClickListener { _, _, position, _ ->
            viewModel.wordSuggestions.value?.getOrNull(position)?.word?.let(target::setText)
            dialog.dismiss()
        }
        dialog.setOnDismissListener { viewModel.wordSuggestions.removeObserver(observer) }
        dialog.show()
    }

    private fun setupIdAutoComplete(view: AutoCompleteTextView, entries: List<IdDefEntry>) {
        view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, entries.map { it.displayText }))
    }

    private fun setIdText(view: AutoCompleteTextView, id: Int?) {
        if (id == null) view.setText("", false)
        else view.setText(viewModel.idEntries().firstOrNull { it.id == id }?.displayText ?: id.toString(), false)
    }

    private fun parseIdOrNull(raw: String, validIds: Set<Int>): Int? {
        if (raw.isBlank()) return null
        val parsed = raw.trim().substringBefore(' ').toIntOrNull()
        if (parsed == null || parsed !in validIds) {
            Toast.makeText(requireContext(), R.string.system_user_dictionary_invalid_context_id, Toast.LENGTH_SHORT).show()
            return INVALID_ID
        }
        return parsed
    }

    private fun parseAdjustment(raw: String): Int? {
        val value = raw.trim().toIntOrNull()
        if (value == null) {
            Toast.makeText(requireContext(), R.string.ngram_rule_adjustment_invalid, Toast.LENGTH_SHORT).show()
            return null
        }
        if (value !in NgramRuleViewModel.ADJUSTMENT_MIN..NgramRuleViewModel.ADJUSTMENT_MAX) {
            Toast.makeText(requireContext(), R.string.ngram_rule_adjustment_out_of_range, Toast.LENGTH_SHORT).show()
            return null
        }
        return value
    }

    private fun launchExportPicker() = exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, "ngram_rules.json")
    })

    private fun launchImportPicker() = importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    })

    private fun exportToUri(uri: Uri) {
        if (ruleItems.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ngram_rule_nothing_to_export, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val backup = NgramRuleBackup(rules = ruleItems.map { NgramRuleBackupItem(it.nodes, it.adjustment) })
            requireContext().contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use {
                    it.write(NgramRuleBackupCodec.encode(backup).toByteArray(Charsets.UTF_8))
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
            val json = requireContext().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                ?: error("Cannot read N-gram backup")
            viewModel.replaceAll(NgramRuleBackupCodec.decode(json))
        }.onSuccess {
            Toast.makeText(requireContext(), R.string.ngram_rule_import_done, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), R.string.fail_to_import_string, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.ngram_rule_delete_all_message)
            .setPositiveButton(R.string.delete_all) { _, _ -> viewModel.deleteAllRules() }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun NodeFeatureInput.toSummary(): String =
        "word=${word ?: "*"}, L=${leftId ?: "*"}, R=${rightId ?: "*"}"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class NodeEditorViews(
        val container: View?,
        val word: EditText,
        val leftId: AutoCompleteTextView,
        val rightId: AutoCompleteTextView,
        val search: Button,
    )

    companion object {
        private const val INVALID_ID = Int.MIN_VALUE
    }
}
