package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroImportPlan
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroCompiler
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroContext
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorBlock
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorDocument
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroSyntaxException
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TextMacroFragment : Fragment() {
    private val viewModel: TextMacroViewModel by viewModels()
    private lateinit var adapter: TextMacroAdapter

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { viewModel.exportJson() }
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter()
                        .use { it.write(json) }
                }
            }.onSuccess {
                toast(R.string.text_macro_export_success)
            }.onFailure { showError(it) }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)!!.bufferedReader()
                        .use { it.readText() }
                }
                withContext(Dispatchers.IO) { viewModel.prepareImport(json) }
            }.onSuccess(::confirmImport)
                .onFailure(::showError)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        setHasOptionsMenu(true)
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), 0)
        }
        root.addView(TextView(context).apply {
            text = getString(R.string.text_macro_sensitive_warning)
            setTextColor(context.getColor(com.kazumaproject.core.R.color.red))
            setPadding(0, 0, 0, dp(8))
        })
        val search = EditText(context).apply {
            hint = getString(R.string.text_macro_search_hint)
            isSingleLine = true
            addTextChangedListener { viewModel.setQuery(it?.toString().orEmpty()) }
        }
        root.addView(search, matchWrap())
        root.addView(Button(context).apply {
            text = getString(R.string.text_macro_add)
            setOnClickListener { showEditor(null) }
        }, matchWrap())
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        root.addView(recycler, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        adapter = TextMacroAdapter(
            onEdit = ::showEditor,
            onEnabledChanged = { macro, enabled ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { viewModel.setEnabled(macro.id, enabled) }.onFailure(::showError)
                }
            },
        )
        recycler.adapter = adapter
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.macros.collect(adapter::submitList)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.user_template_menu, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_export -> {
            exportLauncher.launch("text_macro_backup.json")
            true
        }

        R.id.action_import -> {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
            true
        }

        R.id.action_delete_all -> {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_all_delete_title)
                .setMessage(R.string.text_macro_delete_all_confirm)
                .setPositiveButton(R.string.delete_all) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch { viewModel.deleteAll() }
                }
                .setNegativeButton(R.string.cancel_string, null)
                .show()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun showEditor(macro: TextMacro?) {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }
        val name = EditText(context).apply {
            hint = getString(R.string.text_macro_name)
            setText(macro?.name.orEmpty())
            isSingleLine = true
        }
        val reading = EditText(context).apply {
            hint = getString(R.string.text_macro_reading_optional)
            setText(macro?.reading.orEmpty())
            isSingleLine = true
        }
        val body = EditText(context).apply {
            hint = getString(R.string.text_macro_body)
            setText(macro?.body.orEmpty())
            gravity = android.view.Gravity.TOP
            minLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val enabled = Switch(context).apply {
            text = getString(R.string.text_macro_enabled)
            isChecked = macro?.enabled ?: true
        }
        val error = TextView(context).apply {
            setTextColor(context.getColor(com.kazumaproject.core.R.color.red))
        }
        val preview = TextView(context).apply {
            setPadding(0, dp(8), 0, dp(8))
            setTextIsSelectable(true)
        }
        content.addView(name, matchWrap())
        content.addView(reading, matchWrap())
        content.addView(body, matchWrap())
        content.addView(Button(context).apply {
            text = getString(R.string.text_macro_open_visual_editor)
            setOnClickListener { showVisualStructureEditor(body) }
        }, matchWrap())
        content.addView(buildVariableButtons(body), matchWrap())
        content.addView(enabled, matchWrap())
        content.addView(error, matchWrap())
        content.addView(TextView(context).apply {
            text = getString(R.string.text_macro_preview_sample_notice)
        }, matchWrap())
        content.addView(preview, matchWrap())

        fun updatePreview() {
            try {
                val expanded = TextMacroCompiler.compile(body.text.toString()).expand(
                    TextMacroContext(
                        selection = getString(R.string.text_macro_sample_selection),
                        clipboard = getString(R.string.text_macro_sample_clipboard),
                        app = getString(R.string.text_macro_sample_app),
                    )
                )
                error.text = ""
                preview.text = expanded.text
            } catch (exception: TextMacroSyntaxException) {
                error.text = getString(
                    R.string.text_macro_syntax_error_position,
                    exception.position,
                    exception.message,
                )
                preview.text = ""
            }
        }
        body.addTextChangedListener { updatePreview() }
        updatePreview()

        val scroll = ScrollView(context).apply { addView(content) }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (macro == null) R.string.text_macro_add else R.string.text_macro_edit)
            .setView(scroll)
            .setPositiveButton(R.string.save_string, null)
            .setNegativeButton(R.string.cancel_string, null)
            .apply {
                if (macro != null) setNeutralButton(R.string.delete_string, null)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = TextMacro(
                    id = macro?.id ?: 0,
                    name = name.text.toString(),
                    reading = reading.text.toString(),
                    body = body.text.toString(),
                    enabled = enabled.isChecked,
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { viewModel.save(candidate) }
                        .onSuccess { dialog.dismiss() }
                        .onFailure { exception ->
                            error.text = exception.message ?: getString(R.string.text_macro_save_failed)
                        }
                }
            }
            if (macro != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.confirm_delete_title)
                        .setMessage(getString(R.string.text_macro_delete_confirm, macro.name))
                        .setPositiveButton(R.string.delete_string) { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch { viewModel.delete(macro.id) }
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel_string, null)
                        .show()
                }
            }
            name.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    private fun buildVariableButtons(body: EditText): View {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "{date}", "{time}", "{selection}", "{clipboard}", "{app}", "{newline}", "{cursor}"
        ).forEach { token ->
            row.addView(Button(requireContext()).apply {
                text = token
                setOnClickListener {
                    val start = body.selectionStart.coerceAtLeast(0)
                    val end = body.selectionEnd.coerceAtLeast(start)
                    body.text.replace(start, end, token)
                    body.setSelection(start + token.length)
                }
            })
        }
        return HorizontalScrollView(requireContext()).apply { addView(row) }
    }

    private fun showVisualStructureEditor(bodyEditor: EditText) {
        var document = try {
            TextMacroEditorDocument.parse(bodyEditor.text.toString())
        } catch (exception: TextMacroSyntaxException) {
            showError(exception)
            return
        }
        val context = requireContext()
        val blocksContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        lateinit var render: () -> Unit

        fun insertTokenAt(index: Int) {
            showTokenPicker(null) { token ->
                document = document.add(token, index)
                render()
            }
        }

        render = {
            blocksContainer.removeAllViews()
            blocksContainer.addView(TextView(context).apply {
                text = getString(R.string.text_macro_visual_editor_help)
                setPadding(0, 0, 0, dp(8))
            }, matchWrap())
            document.blocks.forEachIndexed { index, block ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, dp(3))
                }
                row.addView(TextView(context).apply {
                    text = when (block) {
                        is TextMacroEditorBlock.Text -> getString(
                            R.string.text_macro_visual_text_block,
                            block.value.replace("\n", "↵").take(24),
                        )
                        is TextMacroEditorBlock.Token -> block.source
                    }
                    setPadding(dp(8), 0, dp(8), 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Button(context).apply {
                    text = "↑"
                    isEnabled = index > 0
                    contentDescription = getString(R.string.text_macro_move_up)
                    setOnClickListener {
                        document = document.move(index, index - 1)
                        render()
                    }
                })
                row.addView(Button(context).apply {
                    text = "↓"
                    isEnabled = index < document.blocks.lastIndex
                    contentDescription = getString(R.string.text_macro_move_down)
                    setOnClickListener {
                        document = document.move(index, index + 1)
                        render()
                    }
                })
                row.addView(Button(context).apply {
                    text = getString(R.string.text_macro_block_edit)
                    setOnClickListener {
                        when (block) {
                            is TextMacroEditorBlock.Text -> showTextBlockEditor(block) { edited ->
                                document = document.replace(index, edited)
                                render()
                            }
                            is TextMacroEditorBlock.Token -> showTokenPicker(block) { edited ->
                                document = document.replace(index, edited)
                                render()
                            }
                        }
                    }
                })
                row.addView(Button(context).apply {
                    text = "×"
                    contentDescription = getString(R.string.delete_string)
                    setOnClickListener {
                        document = document.remove(index)
                        render()
                    }
                })
                blocksContainer.addView(row, matchWrap())
                blocksContainer.addView(Button(context).apply {
                    text = getString(R.string.text_macro_insert_here)
                    setOnClickListener { insertTokenAt(index + 1) }
                }, matchWrap())
            }
            if (document.blocks.isEmpty()) {
                blocksContainer.addView(Button(context).apply {
                    text = getString(R.string.text_macro_add_variable)
                    setOnClickListener { insertTokenAt(0) }
                }, matchWrap())
            }
            blocksContainer.addView(Button(context).apply {
                text = getString(R.string.text_macro_add_text_block)
                setOnClickListener {
                    showTextBlockEditor(TextMacroEditorBlock.Text("")) { added ->
                        document = document.add(added)
                        render()
                    }
                }
            }, matchWrap())
        }
        render()

        val scroll = ScrollView(context).apply { addView(blocksContainer) }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.text_macro_visual_editor_title)
            .setView(scroll)
            .setPositiveButton(R.string.text_macro_apply_structure, null)
            .setNegativeButton(R.string.cancel_string, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val source = document.toSource()
                runCatching { TextMacroCompiler.compile(source) }
                    .onSuccess {
                        bodyEditor.setText(source)
                        bodyEditor.setSelection(source.length)
                        dialog.dismiss()
                    }
                    .onFailure(::showError)
            }
        }
        dialog.show()
    }

    private fun showTextBlockEditor(
        initial: TextMacroEditorBlock.Text,
        onSave: (TextMacroEditorBlock.Text) -> Unit,
    ) {
        val input = EditText(requireContext()).apply {
            setText(initial.value)
            minLines = 3
            gravity = android.view.Gravity.TOP
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_macro_text_block)
            .setView(input)
            .setPositiveButton(R.string.save_string) { _, _ ->
                onSave(TextMacroEditorBlock.Text(input.text.toString()))
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showTokenPicker(
        initial: TextMacroEditorBlock.Token?,
        onSave: (TextMacroEditorBlock.Token) -> Unit,
    ) {
        val names = arrayOf("date", "time", "selection", "clipboard", "app", "newline", "cursor")
        val labels = names.map { "{$it}" }.toTypedArray()
        val selected = names.indexOf(initial?.name).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_macro_choose_variable)
            .setSingleChoiceItems(labels, selected, null)
            .setPositiveButton(R.string.next_string) { dialog, _ ->
                val list = (dialog as AlertDialog).listView
                val name = names[list.checkedItemPosition.coerceAtLeast(0)]
                if (name == "date" || name == "time") {
                    showDateTimePatternEditor(name, initial?.argument, onSave)
                } else {
                    onSave(TextMacroEditorBlock.Token(name))
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showDateTimePatternEditor(
        name: String,
        initialPattern: String?,
        onSave: (TextMacroEditorBlock.Token) -> Unit,
    ) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.text_macro_pattern_optional)
            setText(initialPattern.orEmpty())
            isSingleLine = true
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (name == "date") R.string.text_macro_date_pattern else R.string.text_macro_time_pattern)
            .setView(input)
            .setPositiveButton(R.string.save_string) { _, _ ->
                val pattern = input.text.toString().takeIf(String::isNotBlank)
                onSave(TextMacroEditorBlock.Token(name, pattern))
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun confirmImport(plan: TextMacroImportPlan) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_title)
            .setMessage(getString(R.string.text_macro_import_confirm, plan.added, plan.overwritten))
            .setPositiveButton(R.string.import_title) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { withContext(Dispatchers.IO) { viewModel.applyImport(plan) } }
                        .onSuccess { toast(R.string.text_macro_import_success) }
                        .onFailure(::showError)
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showError(throwable: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.text_macro_operation_failed, throwable.message.orEmpty()),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun toast(message: Int) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
