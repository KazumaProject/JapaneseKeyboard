package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutAction
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutContext
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutKey
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhysicalKeyboardShortcutEditFragment : Fragment() {
    private val viewModel: PhysicalKeyboardShortcutViewModel by viewModels()
    private var currentItem: PhysicalKeyboardShortcutItem? = null
    private lateinit var contextSpinner: Spinner
    private lateinit var keySpinner: Spinner
    private lateinit var actionSpinner: Spinner
    private lateinit var ctrlCheck: CheckBox
    private lateinit var shiftCheck: CheckBox
    private lateinit var altCheck: CheckBox
    private lateinit var metaCheck: CheckBox
    private lateinit var enabledSwitch: Switch
    private var actions: List<PhysicalKeyboardShortcutAction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.physical_keyboard_shortcut_edit_title)
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        contextSpinner = Spinner(context)
        keySpinner = Spinner(context)
        actionSpinner = Spinner(context)
        ctrlCheck = CheckBox(context).apply { text = getString(R.string.physical_keyboard_shortcut_ctrl) }
        shiftCheck = CheckBox(context).apply { text = getString(R.string.physical_keyboard_shortcut_shift) }
        altCheck = CheckBox(context).apply { text = getString(R.string.physical_keyboard_shortcut_alt) }
        metaCheck = CheckBox(context).apply { text = getString(R.string.physical_keyboard_shortcut_meta) }
        enabledSwitch = Switch(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_enabled)
            isChecked = true
        }
        addLabeled(content, getString(R.string.physical_keyboard_shortcut_context), contextSpinner)
        addLabeled(content, getString(R.string.physical_keyboard_shortcut_key), keySpinner)
        content.addView(TextView(context).apply { text = getString(R.string.physical_keyboard_shortcut_modifiers) })
        content.addView(ctrlCheck)
        content.addView(shiftCheck)
        content.addView(altCheck)
        content.addView(metaCheck)
        addLabeled(content, getString(R.string.physical_keyboard_shortcut_action), actionSpinner)
        content.addView(enabledSwitch)
        content.addView(Button(context).apply {
            text = getString(R.string.save_string)
            setOnClickListener { save() }
        })
        content.addView(Button(context).apply {
            text = getString(R.string.delete_string)
            visibility = View.GONE
            tag = "deleteButton"
            setOnClickListener { confirmDelete() }
        })
        return ScrollView(context).apply { addView(content) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu()
        setupSpinners()
        val shortcutId = arguments?.getLong("shortcutId") ?: 0L
        if (shortcutId != 0L) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.shortcut(shortcutId).collectLatest { item ->
                        if (item != null) bind(item)
                    }
                }
            }
        } else {
            updateActionSpinner(PhysicalKeyboardShortcutContext.ANY, null)
        }
    }

    private fun setupSpinners() {
        contextSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PhysicalKeyboardShortcutContext.entries.map { it.label }
        )
        keySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PhysicalKeyboardShortcutKey.entries.map { it.label }
        )
        contextSpinner.setOnItemSelectedListenerCompat {
            updateActionSpinner(selectedContext(), currentItem?.actionId)
        }
    }

    private fun bind(item: PhysicalKeyboardShortcutItem) {
        currentItem = item
        val contextIndex = PhysicalKeyboardShortcutContext.entries.indexOf(PhysicalKeyboardShortcutContext.fromId(item.context))
        if (contextIndex >= 0) contextSpinner.setSelection(contextIndex)
        val keyIndex = PhysicalKeyboardShortcutKey.entries.indexOfFirst { it.keyCode == item.keyCode }
        if (keyIndex >= 0) keySpinner.setSelection(keyIndex)
        ctrlCheck.isChecked = item.ctrl
        shiftCheck.isChecked = item.shift
        altCheck.isChecked = item.alt
        metaCheck.isChecked = item.meta
        enabledSwitch.isChecked = item.enabled
        updateActionSpinner(PhysicalKeyboardShortcutContext.fromId(item.context), item.actionId)
        (view as? ScrollView)?.children?.firstOrNull()?.let { child ->
            if (child is LinearLayout) {
                child.children.firstOrNull { it.tag == "deleteButton" }?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateActionSpinner(
        selectedContext: PhysicalKeyboardShortcutContext,
        selectedActionId: String?
    ) {
        actions = PhysicalKeyboardShortcutAction.availableFor(selectedContext)
        actionSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actions.map { it.label }
        )
        val index = actions.indexOfFirst { it.id == selectedActionId }
        if (index >= 0) actionSpinner.setSelection(index)
    }

    private fun selectedContext(): PhysicalKeyboardShortcutContext {
        return PhysicalKeyboardShortcutContext.entries[contextSpinner.selectedItemPosition.coerceAtLeast(0)]
    }

    private fun selectedKey(): PhysicalKeyboardShortcutKey {
        return PhysicalKeyboardShortcutKey.entries[keySpinner.selectedItemPosition.coerceAtLeast(0)]
    }

    private fun save() {
        val key = selectedKey()
        val action = actions.getOrNull(actionSpinner.selectedItemPosition) ?: return
        val item = PhysicalKeyboardShortcutItem(
            id = currentItem?.id ?: 0,
            context = selectedContext().id,
            keyCode = key.keyCode,
            scanCode = null,
            ctrl = ctrlCheck.isChecked,
            shift = shiftCheck.isChecked,
            alt = altCheck.isChecked,
            meta = metaCheck.isChecked,
            actionId = action.id,
            enabled = enabledSwitch.isChecked,
            sortOrder = currentItem?.sortOrder ?: Int.MAX_VALUE
        )
        viewModel.save(item) { success ->
            if (success) {
                findNavController().popBackStack()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.physical_keyboard_shortcut_duplicate,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmDelete() {
        val item = currentItem ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.delete(item)
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun setupMenu() {
        val menuHost: androidx.core.view.MenuHost = requireActivity()
        menuHost.addMenuProvider(object : androidx.core.view.MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == android.R.id.home) {
                    findNavController().popBackStack()
                    true
                } else {
                    false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun addLabeled(parent: LinearLayout, label: String, child: View) {
        parent.addView(TextView(requireContext()).apply { text = label })
        parent.addView(child)
    }
}
