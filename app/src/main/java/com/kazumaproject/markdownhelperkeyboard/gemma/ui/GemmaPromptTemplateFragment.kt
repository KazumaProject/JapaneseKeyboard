package com.kazumaproject.markdownhelperkeyboard.gemma.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GemmaPromptTemplateFragment : Fragment() {

    private var _binding: FragmentGemmaPromptTemplateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GemmaPromptTemplateViewModel by viewModels()

    private lateinit var adapter: GemmaPromptTemplateAdapter

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
        observeTemplates()
        binding.fabAddTemplate.setOnClickListener {
            showTemplateEditorDialog(null)
        }
    }

    private fun setupRecyclerView() {
        adapter = GemmaPromptTemplateAdapter(
            onToggle = { template, isChecked ->
                viewModel.setEnabled(template, isChecked)
            },
            onEdit = { template ->
                showTemplateEditorDialog(template)
            },
            onDelete = { template ->
                showDeleteConfirmationDialog(template)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun observeTemplates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.templates.collect { templates ->
                    adapter.submitList(templates)
                    binding.textEmpty.isVisible = templates.isEmpty()
                }
            }
        }
    }

    private fun showTemplateEditorDialog(template: GemmaPromptTemplate?) {
        val dialogBinding = DialogEditGemmaPromptTemplateBinding.inflate(layoutInflater)
        dialogBinding.editTextTitle.setText(template?.title.orEmpty())
        dialogBinding.editTextPrompt.setText(template?.prompt.orEmpty())
        dialogBinding.switchEnable.isChecked = template?.isEnabled ?: true

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(
                if (template == null) {
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
                            currentTemplate = template,
                            title = title,
                            prompt = prompt,
                            isEnabled = dialogBinding.switchEnable.isChecked
                        )
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(template: GemmaPromptTemplate) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.gemma_prompt_template_delete_confirm_title, template.title))
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
}
