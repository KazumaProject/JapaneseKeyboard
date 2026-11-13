package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.markdownhelperkeyboard.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FreeFlickLayoutsViewModel : ViewModel() {
    var importCandidate: FreeFlickLayout? = null
}

class FreeFlickLayoutsFragment : Fragment() {
    private val model: FreeFlickLayoutsViewModel by viewModels()
    private lateinit var repository: FreeFlickLayoutRepository
    private lateinit var content: LinearLayout
    private var exportId: String? = null
    private var importingDialog: androidx.appcompat.app.AlertDialog? = null
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId; exportId = null
        if (uri != null && id != null) transfer(R.string.free_flick_layout_exported) {
            val json = FreeFlickLayoutCodec.encode(repository.find(id))
            requireNotNull(requireContext().contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readImport(uri)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String) = TextView(requireContext()).apply { text = value; textSize = 15f; setPadding(0, dp(6), 0, dp(6)) }
    private fun button(title: Int, action: () -> Unit) = MaterialButton(requireContext(), null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { setText(title); setOnClickListener { action() } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportId = savedInstanceState?.getString("exportId")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportId", exportId)
        super.onSaveInstanceState(outState)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        repository = FreeFlickLayoutRepository(requireContext())
        content = column().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
        return NestedScrollView(requireContext()).apply { isFillViewport = true; addView(content) }
    }
    override fun onResume() {
        super.onResume()
        refresh()
        if (model.importCandidate != null) confirmImport()
    }
    override fun onDestroyView() {
        importingDialog?.dismiss(); importingDialog = null
        super.onDestroyView()
    }
    private fun refresh() {
        content.removeAllViews()
        try {
            val layouts = repository.list(); val selected = repository.selectedId()
            content.addView(text(getString(R.string.free_flick_layout_selected, repository.find(selected).name)).apply { textSize = 20f })
            content.addView(text(getString(R.string.free_flick_layout_scope)))
            content.addView(button(R.string.free_flick_layout_new) {
                nameDialog(getString(R.string.free_flick_layout_new), "") { name -> edit(repository.create(name).id) }
            })
            content.addView(button(R.string.free_flick_layout_snapshot) {
                nameDialog(getString(R.string.free_flick_layout_snapshot), "") { name -> repository.snapshot(name); refresh() }
            })
            content.addView(button(R.string.free_flick_layout_import) { importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
            layouts.forEach { layout ->
                val body = column().apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
                body.addView(text(layout.name).apply { textSize = 20f })
                body.addView(text(if (layout.id == selected) getString(R.string.free_flick_layout_in_use) else getString(R.string.free_flick_layout_saved)))
                body.addView(text(getString(R.string.free_flick_layout_counts, layout.keys.size, layout.keys.sumOf { it.maps.size }, layout.keys.sumOf { key -> key.maps.sumOf { it.regions.size } })))
                body.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(button(R.string.free_flick_layout_show) { FreeFlickLayoutPreviewDialog.create(layout.id).show(childFragmentManager, "layout-preview") }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(button(R.string.popup_edit) {
                        if (layout.id == FreeFlickLayoutRepository.BUILTIN_ID) nameDialog(getString(R.string.popup_duplicate), "") { name -> edit(repository.duplicate(layout.id, name).id) }
                        else edit(layout.id)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(button(R.string.free_flick_layout_select) { attempt { repository.select(layout.id); refresh() } }.apply { isEnabled = layout.id != selected }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "⋮"; contentDescription = getString(R.string.free_flick_layout_actions, layout.name)
                        minWidth = dp(40); minimumWidth = dp(40); setPadding(0, 0, 0, 0)
                        setOnClickListener { menu(this, layout) }
                    }, LinearLayout.LayoutParams(dp(44), -2))
                })
                content.addView(MaterialCardView(requireContext()).apply { addView(body) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            }
        } catch (_: Exception) {
            content.addView(text(getString(R.string.free_flick_layout_storage_error)))
            content.addView(button(R.string.free_flick_layout_retry) { refresh() })
        }
    }
    private fun edit(id: String) = findNavController().navigate(R.id.popupMapEditorFragment, bundleOf(FreeFlickLayoutRepository.ARG_LAYOUT_ID to id))
    private fun menu(anchor: View, layout: FreeFlickLayout) {
        PopupMenu(requireContext(), anchor).apply {
            if (layout.id != FreeFlickLayoutRepository.BUILTIN_ID) menu.add(0, R.string.popup_rename, 0, R.string.popup_rename)
            menu.add(0, R.string.popup_duplicate, 1, R.string.popup_duplicate)
            menu.add(0, R.string.free_flick_layout_export, 2, R.string.free_flick_layout_export)
            if (layout.id != FreeFlickLayoutRepository.BUILTIN_ID) menu.add(0, R.string.popup_delete, 3, R.string.popup_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.string.popup_rename -> nameDialog(getString(R.string.popup_rename), layout.name) { repository.rename(layout.id, it); refresh() }
                    R.string.popup_duplicate -> nameDialog(getString(R.string.popup_duplicate), "") { repository.duplicate(layout.id, it); refresh() }
                    R.string.free_flick_layout_export -> { exportId = layout.id; exportFile.launch("free-flick-${layout.name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")}.json") }
                    R.string.popup_delete -> MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.popup_delete)
                        .setMessage(getString(if (repository.selectedId() == layout.id) R.string.free_flick_layout_delete_selected else R.string.free_flick_layout_delete, layout.name))
                        .setNegativeButton(R.string.popup_cancel, null).setPositiveButton(R.string.popup_delete) { _, _ -> attempt { repository.delete(layout.id); refresh() } }.show()
                }
                true
            }; show()
        }
    }
    private fun nameDialog(title: String, name: String, save: (String) -> Unit) {
        val input = TextInputEditText(requireContext()).apply { setText(name); setSingleLine(); filters = arrayOf(android.text.InputFilter.LengthFilter(64)) }
        val field = TextInputLayout(requireContext()).apply { hint = getString(R.string.free_flick_layout_name); setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(field).setNegativeButton(R.string.popup_cancel, null).setPositiveButton(R.string.popup_save, null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            try { save(input.text.toString().trim()); dialog.dismiss() }
            catch (_: Exception) { field.error = getString(R.string.free_flick_layout_name_error) }
        }}; dialog.show()
    }
    private fun confirmImport() {
        if (importingDialog?.isShowing == true) return
        val candidate = model.importCandidate ?: return
        importingDialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.free_flick_layout_import)
            .setMessage(getString(R.string.free_flick_layout_import_confirm, candidate.name))
            .setNegativeButton(R.string.popup_cancel) { _, _ -> model.importCandidate = null }
            .setOnCancelListener { model.importCandidate = null }
            .setPositiveButton(R.string.free_flick_layout_add) { _, _ ->
                attempt { repository.importLayout(candidate); refresh(); toast(R.string.free_flick_layout_imported) }
                model.importCandidate = null
            }.show()
    }
    private fun readImport(uri: Uri) {
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                model.importCandidate = withContext(Dispatchers.IO) { requireNotNull(resolver.openInputStream(uri)).use(FreeFlickLayoutCodec::read) }
                confirmImport()
            } catch (e: Exception) { if (e is CancellationException) throw e; toast(R.string.free_flick_layout_import_error) }
        }
    }
    private fun transfer(success: Int, block: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { block() }; toast(success) }
            catch (e: Exception) { if (e is CancellationException) throw e; toast(R.string.free_flick_layout_transfer_error) }
        }
    }
    private fun attempt(block: () -> Unit) {
        try { block() } catch (_: Exception) { toast(R.string.free_flick_layout_operation_error) }
    }
    private fun toast(message: Int) { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show() }
}
