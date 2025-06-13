package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSelectionBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSelectionFragment : Fragment() {

    private var _binding: FragmentKeyboardSelectionBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreferences: AppPreference

    private val viewModel: KeyboardSelectionViewModel by viewModels()

    private lateinit var keyboardSelectionAdapter: KeyboardSelectionAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupAddButton()
        observeUiState()

        if (savedInstanceState == null) {
            viewModel.setInitialKeyboards(appPreferences.keyboard_order)
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.keyboard_selection_menu, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val editItem = menu.findItem(R.id.action_edit)
                val isEditing = viewModel.uiState.value.isEditing
                editItem.title =
                    if (isEditing) getString(com.kazumaproject.core.R.string.done_text) else getString(
                        R.string.edit_text
                    )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        viewModel.toggleEditMode()
                        // If we are finishing editing, save the latest state
                        if (!viewModel.uiState.value.isEditing) {
                            appPreferences.keyboard_order = viewModel.uiState.value.keyboards
                        }
                        true
                    }

                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        keyboardSelectionAdapter = KeyboardSelectionAdapter(
            onStartDrag = { viewHolder ->
                if (viewModel.uiState.value.isEditing) {
                    itemTouchHelper.startDrag(viewHolder)
                }
            },
            onDeleteClick = { position ->
                val currentList = viewModel.uiState.value.keyboards.toMutableList()
                if (currentList.size > 1) {
                    currentList.removeAt(position)
                    viewModel.updateKeyboardOrder(currentList)
                }
            }
        )

        binding.keyboardSelectionView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = keyboardSelectionAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                val currentList = viewModel.uiState.value.keyboards.toMutableList()
                Collections.swap(currentList, fromPosition, toPosition)
                viewModel.updateKeyboardOrder(currentList)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false // Disable default drag
            override fun isItemViewSwipeEnabled(): Boolean = false // Disable swipe
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.keyboardSelectionView)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    keyboardSelectionAdapter.submitList(state.keyboards)
                    keyboardSelectionAdapter.setEditMode(state.isEditing)
                    // Make the menu redraw itself to update the text
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setupAddButton() {
        binding.addNewKeyboardDialogTrigger.setOnClickListener {
            showAddKeyboardDialog()
        }
    }

    private fun showAddKeyboardDialog() {
        val allKeyboardTypes = KeyboardType.entries.toTypedArray()
        val currentKeyboards = viewModel.uiState.value.keyboards
        val availableKeyboards = allKeyboardTypes.filter { it !in currentKeyboards }

        if (availableKeyboards.isEmpty()) return

        val dialogItems = availableKeyboards.map { getKeyboardDisplayName(it) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(com.kazumaproject.core.R.string.add_keyboard_dialog_title)
            .setItems(dialogItems) { dialog, which ->
                val selectedKeyboard = availableKeyboards[which]
                val newList = currentKeyboards.toMutableList().apply { add(selectedKeyboard) }
                viewModel.updateKeyboardOrder(newList)
                dialog.dismiss()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Save the latest state when the user leaves the screen
        appPreferences.keyboard_order = viewModel.uiState.value.keyboards
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

fun getKeyboardDisplayName(keyboardType: KeyboardType): String {
    return when (keyboardType) {
        KeyboardType.TENKEY -> "日本語 - かな"
        KeyboardType.QWERTY -> "英語(QWERTY)"
        KeyboardType.ROMAJI -> "日本語 - ローマ字"
        KeyboardType.SUMIRE -> "日本語 - スミレ入力"
    }
}
