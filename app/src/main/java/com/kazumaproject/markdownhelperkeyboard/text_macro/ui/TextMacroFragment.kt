package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
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
            }.onFailure(::showError)
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
        root.addView(EditText(context).apply {
            hint = getString(R.string.text_macro_search_hint)
            isSingleLine = true
            addTextChangedListener { viewModel.setQuery(it?.toString().orEmpty()) }
        }, matchWrap())
        root.addView(Button(context).apply {
            text = getString(R.string.text_macro_add)
            setOnClickListener { openEditor(0L) }
        }, matchWrap())
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        root.addView(
            recycler,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        adapter = TextMacroAdapter(
            onEdit = { openEditor(it.id) },
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

    private fun openEditor(macroId: Long) {
        navigateSafely(
            R.id.textMacroEditorFragment,
            TextMacroEditorNavigation.arguments(macroId),
        )
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

    private fun toast(message: Int) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
