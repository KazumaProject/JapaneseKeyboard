package com.kazumaproject.markdownhelperkeyboard.short_cut.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentShortcutSettingBinding
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutToolbarSelectedAdapter
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShortcutSettingFragment : Fragment() {

    private var _binding: FragmentShortcutSettingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShortcutSettingViewModel by viewModels()

    private lateinit var adapter: ShortcutToolbarSelectedAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var pendingDragSave = false
    private var latestState = ShortcutToolbarEditUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShortcutSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupInputs()
        setupFragmentResultListener()
        observeViewModel()
    }

    override fun onDestroyView() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        binding.shortcutSelectedRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        adapter = ShortcutToolbarSelectedAdapter(
            onRemove = { type ->
                viewModel.removeShortcut(type)
                viewModel.save()
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onMove = { type, direction ->
                moveByDirection(type, direction)
            },
        )

        binding.shortcutSelectedRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShortcutSettingFragment.adapter
        }

        itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val fromPosition = viewHolder.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition
                    if (fromPosition == RecyclerView.NO_POSITION ||
                        toPosition == RecyclerView.NO_POSITION
                    ) {
                        return false
                    }
                    viewModel.moveShortcut(fromPosition, toPosition)
                    pendingDragSave = true
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.alpha = 1f
                    if (pendingDragSave) {
                        pendingDragSave = false
                        viewModel.save()
                    }
                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int,
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.alpha = 0.92f
                    }
                }
            }
        ).also { helper ->
            helper.attachToRecyclerView(binding.shortcutSelectedRecyclerView)
        }
    }

    private fun setupInputs() {
        binding.shortcutAddButton.setOnClickListener {
            ShortcutAddBottomSheetFragment
                .newInstance(latestState.selected.map { it.id })
                .show(parentFragmentManager, ShortcutAddBottomSheetFragment::class.java.simpleName)
        }
        binding.shortcutResetButton.setOnClickListener {
            viewModel.resetToDefault()
            viewModel.save()
        }
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener(
            ShortcutAddBottomSheetFragment.REQUEST_KEY_ADD_SHORTCUT,
            viewLifecycleOwner,
        ) { _, bundle ->
            bundle
                .getString(ShortcutAddBottomSheetFragment.KEY_SHORTCUT_TYPE_ID)
                ?.let(ShortcutType::fromId)
                ?.let { type ->
                    viewModel.addShortcut(type)
                    viewModel.save()
                }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        latestState = state
                        renderState(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ShortcutToolbarEditEvent.CannotRemoveLastShortcut -> {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.shortcut_toolbar_cannot_remove_last,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: ShortcutToolbarEditUiState) {
        val countText = getString(R.string.shortcut_toolbar_selected_count, state.selected.size)
        binding.shortcutSelectedCountText.apply {
            text = countText
            contentDescription = countText
        }
        binding.shortcutEmptyText.isVisible = state.selected.isEmpty()
        binding.shortcutSelectedRecyclerView.isVisible = state.selected.isNotEmpty()
        adapter.setCanRemove(state.canRemove)
        adapter.submitList(state.selected)
    }

    private fun moveByDirection(type: ShortcutType, direction: Int): Boolean {
        val currentIndex = latestState.selected.indexOf(type)
        val targetIndex = currentIndex + direction
        if (currentIndex !in latestState.selected.indices ||
            targetIndex !in latestState.selected.indices
        ) {
            return false
        }
        viewModel.moveShortcut(currentIndex, targetIndex)
        viewModel.save()
        return true
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

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
