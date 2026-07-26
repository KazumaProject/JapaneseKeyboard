package com.kazumaproject.markdownhelperkeyboard.gemma.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.DialogEditGemmaPromptTemplateBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentGemmaPromptTemplateBinding
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaBuiltInActions
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputLanguage
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaTaskKind
import com.kazumaproject.markdownhelperkeyboard.gemma.database.modality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.output
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GemmaPromptTemplateFragment : Fragment() {

    private var _binding: FragmentGemmaPromptTemplateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GemmaPromptTemplateViewModel by viewModels()

    private lateinit var adapter: GemmaPromptTemplateAdapter
    private var allTemplates: List<GemmaPromptTemplate> = emptyList()
    private var modalityFilter: GemmaInputModality? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGemmaPromptTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        observeTemplates()
        setupMenu()
        binding.fabAddTemplate.setOnClickListener {
            showTemplateEditorDialog(currentTemplate = null, initialTemplate = null)
        }
    }

    private fun setupRecyclerView() {
        adapter = GemmaPromptTemplateAdapter(
            onToggle = { template, isChecked ->
                viewModel.setEnabled(template, isChecked)
            },
            onEdit = { template ->
                showTemplateEditorDialog(
                    currentTemplate = template.takeUnless { it.isBuiltIn },
                    initialTemplate = template,
                )
            },
            onDelete = { template ->
                showDeleteConfirmationDialog(template)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        binding.gemmaActionFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            modalityFilter = when (checkedIds.firstOrNull()) {
                R.id.gemma_action_filter_text -> GemmaInputModality.TEXT
                R.id.gemma_action_filter_image -> GemmaInputModality.IMAGE
                R.id.gemma_action_filter_audio -> GemmaInputModality.AUDIO
                else -> null
            }
            submitFilteredTemplates()
        }
    }

    private fun observeTemplates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.templates.collect { templates ->
                    allTemplates = templates
                    submitFilteredTemplates()
                }
            }
        }
    }

    private fun submitFilteredTemplates() {
        val filtered = modalityFilter?.let { modality ->
            allTemplates.filter { it.modality() == modality }
        } ?: allTemplates
        adapter.submitList(filtered)
        binding.textEmpty.isVisible = filtered.isEmpty()
    }

    private fun showTemplateEditorDialog(
        currentTemplate: GemmaPromptTemplate?,
        initialTemplate: GemmaPromptTemplate?,
    ) {
        val dialogBinding = DialogEditGemmaPromptTemplateBinding.inflate(layoutInflater)
        dialogBinding.editTextTitle.setText(initialTemplate?.title.orEmpty())
        dialogBinding.editTextPrompt.setText(initialTemplate?.prompt.orEmpty())
        dialogBinding.switchEnable.isChecked = initialTemplate?.isEnabled ?: true

        val modalities = GemmaInputModality.entries
        dialogBinding.spinnerModality.adapter = simpleSpinnerAdapter(
            listOf(
                getString(R.string.gemma_action_modality_text),
                getString(R.string.gemma_action_modality_image),
                getString(R.string.gemma_action_modality_audio),
            )
        )
        dialogBinding.spinnerModality.setSelection(
            modalities.indexOf(initialTemplate?.modality() ?: modalityFilter ?: GemmaInputModality.TEXT)
        )

        val outputModes = GemmaOutputMode.entries
        dialogBinding.spinnerOutputMode.adapter = simpleSpinnerAdapter(
            listOf(
                getString(R.string.gemma_action_output_single),
                getString(R.string.gemma_action_output_multiline),
                getString(R.string.gemma_action_output_candidates),
            )
        )
        dialogBinding.spinnerOutputMode.setSelection(
            outputModes.indexOf(initialTemplate?.output() ?: GemmaOutputMode.SINGLE_TEXT)
        )

        val outputLanguages = GemmaOutputLanguage.entries
        dialogBinding.spinnerOutputLanguage.adapter = simpleSpinnerAdapter(
            listOf(
                getString(R.string.gemma_action_language_auto),
                getString(R.string.gemma_action_language_japanese),
                getString(R.string.gemma_action_language_english),
            )
        )
        dialogBinding.spinnerOutputLanguage.setSelection(
            outputLanguages.indexOfFirst { it.name == initialTemplate?.outputLanguage }
                .takeIf { it >= 0 } ?: 0
        )

        val starters = listOf<GemmaPromptTemplate?>(null) + GemmaBuiltInActions.all()
        dialogBinding.spinnerStarter.adapter = simpleSpinnerAdapter(
            starters.map { starter ->
                starter?.let { "${it.title} (${modalityLabel(it.modality())})" }
                    ?: getString(R.string.gemma_action_starter_custom)
            }
        )
        dialogBinding.spinnerStarter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val starter = starters.getOrNull(position) ?: return
                dialogBinding.editTextTitle.setText(starter.title)
                dialogBinding.editTextPrompt.setText(starter.prompt)
                dialogBinding.spinnerModality.setSelection(modalities.indexOf(starter.modality()))
                dialogBinding.spinnerOutputMode.setSelection(outputModes.indexOf(starter.output()))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(
                if (currentTemplate == null) {
                    R.string.gemma_prompt_template_dialog_title_add
                } else {
                    R.string.gemma_prompt_template_dialog_title_edit
                }
            )
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel_string, null)
            .setPositiveButton(R.string.save_string, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.editTextTitle.text?.toString()?.trim().orEmpty()
                val prompt = dialogBinding.editTextPrompt.text?.toString()?.trim().orEmpty()
                when {
                    title.isEmpty() -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.gemma_prompt_template_title_required),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    prompt.isEmpty() -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.gemma_prompt_template_prompt_required),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {
                        viewModel.saveTemplate(
                            currentTemplate = currentTemplate,
                            title = title,
                            prompt = prompt,
                            isEnabled = dialogBinding.switchEnable.isChecked,
                            inputModality = modalities[dialogBinding.spinnerModality.selectedItemPosition],
                            taskKind = GemmaTaskKind.CUSTOM,
                            outputMode = outputModes[dialogBinding.spinnerOutputMode.selectedItemPosition],
                            outputLanguage = outputLanguages[dialogBinding.spinnerOutputLanguage.selectedItemPosition],
                            candidateCount = if (
                                outputModes[dialogBinding.spinnerOutputMode.selectedItemPosition] ==
                                GemmaOutputMode.CANDIDATE_LIST
                            ) 3 else 1,
                            showInActionMenu = dialogBinding.switchEnable.isChecked,
                        )
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun simpleSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            items,
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun modalityLabel(modality: GemmaInputModality): String = when (modality) {
        GemmaInputModality.TEXT -> getString(R.string.gemma_action_modality_text)
        GemmaInputModality.IMAGE -> getString(R.string.gemma_action_modality_image)
        GemmaInputModality.AUDIO -> getString(R.string.gemma_action_modality_audio)
    }

    private fun showDeleteConfirmationDialog(template: GemmaPromptTemplate) {
        AlertDialog.Builder(requireContext())
            .setTitle(
                getString(
                    R.string.gemma_prompt_template_delete_confirm_title,
                    template.title
                )
            )
            .setMessage(getString(R.string.gemma_prompt_template_delete_confirm_message))
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.deleteTemplate(template)
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}
