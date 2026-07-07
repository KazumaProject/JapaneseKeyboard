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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
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

    private lateinit var rootContainer: FrameLayout
    private lateinit var rootScrollView: ScrollView

    private lateinit var contextSpinner: Spinner
    private lateinit var keySpinner: Spinner
    private lateinit var actionSpinner: Spinner

    private lateinit var ctrlCheck: CheckBox
    private lateinit var shiftCheck: CheckBox
    private lateinit var altCheck: CheckBox
    private lateinit var metaCheck: CheckBox

    private lateinit var enabledSwitch: Switch
    private lateinit var deleteButton: Button

    private var actions: List<PhysicalKeyboardShortcutAction> = emptyList()

    private var navViewLayoutChangeListener: View.OnLayoutChangeListener? = null

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        contextSpinner = Spinner(context)
        keySpinner = Spinner(context)
        actionSpinner = Spinner(context)

        ctrlCheck = CheckBox(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_ctrl)
        }

        shiftCheck = CheckBox(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_shift)
        }

        altCheck = CheckBox(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_alt)
        }

        metaCheck = CheckBox(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_meta)
        }

        enabledSwitch = Switch(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_enabled)
            isChecked = true
        }

        addLabeled(
            parent = content,
            label = getString(R.string.physical_keyboard_shortcut_context),
            child = contextSpinner
        )

        addLabeled(
            parent = content,
            label = getString(R.string.physical_keyboard_shortcut_key),
            child = keySpinner
        )

        content.addView(
            TextView(context).apply {
                text = getString(R.string.physical_keyboard_shortcut_modifiers)
            }
        )

        content.addView(ctrlCheck)
        content.addView(shiftCheck)
        content.addView(altCheck)
        content.addView(metaCheck)

        addLabeled(
            parent = content,
            label = getString(R.string.physical_keyboard_shortcut_action),
            child = actionSpinner
        )

        content.addView(enabledSwitch)

        content.addView(
            Button(context).apply {
                text = getString(R.string.save_string)
                setOnClickListener { save() }
            }
        )

        deleteButton = Button(context).apply {
            text = getString(R.string.delete_string)
            visibility = View.GONE
            setOnClickListener { confirmDelete() }
        }

        content.addView(deleteButton)

        rootScrollView = ScrollView(context).apply {
            clipToPadding = false
            isFillViewport = true

            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        rootContainer = FrameLayout(context).apply {
            addView(
                rootScrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        return rootContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu()
        setupSpinners()
        applyBottomNavigationMargin()

        val shortcutId = arguments?.getLong("shortcutId") ?: 0L

        if (shortcutId != 0L) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.shortcut(shortcutId).collectLatest { item ->
                        if (item != null) {
                            bind(item)
                        }
                    }
                }
            }
        } else {
            updateActionSpinner(
                selectedContext = PhysicalKeyboardShortcutContext.ANY,
                selectedActionId = null
            )
        }
    }

    override fun onDestroyView() {
        val navView = activity?.findViewById<BottomNavigationView>(R.id.nav_view)
        val listener = navViewLayoutChangeListener

        if (navView != null && listener != null) {
            navView.removeOnLayoutChangeListener(listener)
        }

        navViewLayoutChangeListener = null

        super.onDestroyView()
    }

    private fun setupSpinners() {
        contextSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PhysicalKeyboardShortcutContext.entries.map { getString(it.labelResId) }
        )

        keySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PhysicalKeyboardShortcutKey.entries.map { it.displayLabel(requireContext()) }
        )

        contextSpinner.setOnItemSelectedListenerCompat {
            updateActionSpinner(
                selectedContext = selectedContext(),
                selectedActionId = currentItem?.actionId
            )
        }
    }

    private fun bind(item: PhysicalKeyboardShortcutItem) {
        currentItem = item

        val contextIndex = PhysicalKeyboardShortcutContext.entries.indexOf(
            PhysicalKeyboardShortcutContext.fromId(item.context)
        )

        if (contextIndex >= 0) {
            contextSpinner.setSelection(contextIndex)
        }

        val keyIndex = PhysicalKeyboardShortcutKey.entries.indexOfFirst {
            it.keyCode == item.keyCode
        }

        if (keyIndex >= 0) {
            keySpinner.setSelection(keyIndex)
        }

        ctrlCheck.isChecked = item.ctrl
        shiftCheck.isChecked = item.shift
        altCheck.isChecked = item.alt
        metaCheck.isChecked = item.meta
        enabledSwitch.isChecked = item.enabled

        updateActionSpinner(
            selectedContext = PhysicalKeyboardShortcutContext.fromId(item.context),
            selectedActionId = item.actionId
        )

        deleteButton.visibility = View.VISIBLE
    }

    private fun updateActionSpinner(
        selectedContext: PhysicalKeyboardShortcutContext,
        selectedActionId: String?
    ) {
        actions = PhysicalKeyboardShortcutAction.availableFor(selectedContext)

        actionSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actions.map { getString(it.labelResId) }
        )

        val index = actions.indexOfFirst {
            it.id == selectedActionId
        }

        if (index >= 0) {
            actionSpinner.setSelection(index)
        }
    }

    private fun applyBottomNavigationMargin() {
        val navView = requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)

        fun updateBottomMargin() {
            val bottomMargin = if (navView.isVisible && navView.height > 0) {
                navView.height
            } else {
                0
            }

            val params = rootScrollView.layoutParams as FrameLayout.LayoutParams

            if (params.bottomMargin != bottomMargin) {
                params.bottomMargin = bottomMargin
                rootScrollView.layoutParams = params
            }
        }

        rootContainer.doOnLayout {
            updateBottomMargin()
        }

        rootScrollView.doOnLayout {
            updateBottomMargin()
        }

        navView.doOnLayout {
            updateBottomMargin()
        }

        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBottomMargin()
        }

        navView.addOnLayoutChangeListener(listener)
        navViewLayoutChangeListener = listener

        rootContainer.post {
            updateBottomMargin()
        }
    }

    private fun selectedContext(): PhysicalKeyboardShortcutContext {
        val index = contextSpinner.selectedItemPosition.coerceIn(
            0,
            PhysicalKeyboardShortcutContext.entries.lastIndex
        )

        return PhysicalKeyboardShortcutContext.entries[index]
    }

    private fun selectedKey(): PhysicalKeyboardShortcutKey {
        val index = keySpinner.selectedItemPosition.coerceIn(
            0,
            PhysicalKeyboardShortcutKey.entries.lastIndex
        )

        return PhysicalKeyboardShortcutKey.entries[index]
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
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return if (menuItem.itemId == android.R.id.home) {
                        findNavController().popBackStack()
                        true
                    } else {
                        false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun addLabeled(parent: LinearLayout, label: String, child: View) {
        parent.addView(
            TextView(requireContext()).apply {
                text = label
            }
        )

        parent.addView(child)
    }
}
