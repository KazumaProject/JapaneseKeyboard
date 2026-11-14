package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.DialogVoiceInputRuleBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input.VoiceInputReplacementRule
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class VoiceInputRuleDialogFragment : DialogFragment() {
    private var binding: DialogVoiceInputRuleBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val fields = DialogVoiceInputRuleBinding.inflate(layoutInflater).also { binding = it }
        val originalSource = arguments?.getString(SOURCE)
        val rule = AppPreference.voice_input_text_replacement_rules.firstOrNull { it.source == originalSource }
        fields.source.setText(savedInstanceState?.getString(SOURCE) ?: rule?.source.orEmpty())
        fields.replacement.setText(savedInstanceState?.getString(REPLACEMENT) ?: rule?.replacement.orEmpty())
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (originalSource == null) R.string.voice_input_text_replacement_add_title else R.string.voice_input_text_replacement_edit_title)
            .setView(fields.root)
            .setPositiveButton(R.string.save_string, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (originalSource != null) {
            builder.setNeutralButton(R.string.delete_string) { _, _ ->
                AppPreference.voice_input_text_replacement_rules = AppPreference.voice_input_text_replacement_rules
                    .filterNot { it.source == originalSource }
                notifyChanged()
            }
        }
        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        val fields = requireNotNull(binding)
        val originalSource = arguments?.getString(SOURCE)
        (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val source = fields.source.text.toString()
            val rules = AppPreference.voice_input_text_replacement_rules.toMutableList()
            fields.sourceLayout.error = when {
                source.isEmpty() -> getString(R.string.voice_input_text_replacement_source_required)
                rules.any { it.source == source && it.source != originalSource } ->
                    getString(R.string.voice_input_text_replacement_source_duplicate)
                else -> null
            }
            if (fields.sourceLayout.error != null) return@setOnClickListener
            val updated = VoiceInputReplacementRule(source, fields.replacement.text.toString())
            val index = rules.indexOfFirst { it.source == originalSource }
            if (index < 0) rules.add(updated) else rules[index] = updated
            AppPreference.voice_input_text_replacement_rules = rules
            notifyChanged()
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.let {
            outState.putString(SOURCE, it.source.text.toString())
            outState.putString(REPLACEMENT, it.replacement.text.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun notifyChanged() = parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())

    companion object {
        const val TAG = "voice_input_rule_editor"
        const val RESULT_KEY = "voice_input_rules_changed"
        private const val SOURCE = "source"
        private const val REPLACEMENT = "replacement"

        fun newInstance(source: String?) = VoiceInputRuleDialogFragment().apply {
            arguments = Bundle().apply { putString(SOURCE, source) }
        }
    }
}
