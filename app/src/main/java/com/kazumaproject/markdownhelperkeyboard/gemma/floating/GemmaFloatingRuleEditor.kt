package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.kazumaproject.markdownhelperkeyboard.R

internal class GemmaFloatingRuleEditor(
    private val context: Context,
    private val settings: GemmaFloatingSettings,
    private val changed: () -> Unit,
) {
    fun show() {
        val rules = settings.rules
        AlertDialog.Builder(context).setTitle(R.string.gemma_floating_manage)
            .setItems((rules.map { it.name } + context.getString(R.string.gemma_floating_add)).toTypedArray()) { _, index ->
                val rule = rules.getOrNull(index)
                if (rule == null) edit(null)
                else AlertDialog.Builder(context).setTitle(rule.name)
                    .setItems(arrayOf(context.getString(R.string.gemma_floating_edit), context.getString(R.string.gemma_floating_delete))) { _, action ->
                        if (action == 0) edit(rule)
                        else AlertDialog.Builder(context).setTitle(R.string.gemma_floating_delete)
                            .setMessage(context.getString(R.string.gemma_floating_delete_confirm, rule.name))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok) { _, _ -> settings.delete(rule); changed() }.show()
                    }.show()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun edit(rule: GemmaFloatingRule?) {
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val name = EditText(context).apply {
            hint = context.getString(R.string.gemma_floating_name)
            setSingleLine(true)
            setText(rule?.name.orEmpty())
        }
        val instruction = EditText(context).apply {
            hint = context.getString(R.string.gemma_floating_instruction)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            maxLines = 10
            setText(rule?.instruction.orEmpty())
        }
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(name); addView(instruction)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (rule == null) R.string.gemma_floating_add else R.string.gemma_floating_edit)
            .setView(form).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_string, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when {
                    name.text.isNullOrBlank() -> name.error = context.getString(R.string.gemma_floating_required)
                    instruction.text.isNullOrBlank() -> instruction.error = context.getString(R.string.gemma_floating_required)
                    else -> {
                        settings.save(rule, name.text.toString(), instruction.text.toString())
                        changed()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }
}
