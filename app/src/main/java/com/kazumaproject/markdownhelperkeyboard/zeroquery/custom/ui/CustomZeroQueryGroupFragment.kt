package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.DialogCustomZeroQueryEntryBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCustomZeroQueryGroupBinding
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryEntry
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.adapter.CustomZeroQueryCandidateAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomZeroQueryGroupFragment : Fragment() {

    private var _binding: FragmentCustomZeroQueryGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomZeroQueryGroupViewModel by viewModels()
    private lateinit var adapter: CustomZeroQueryCandidateAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomZeroQueryGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupControls()
        observeState()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(
            object : androidx.core.view.MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.custom_zero_query_group_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        android.R.id.home -> {
                            findNavController().popBackStack()
                            true
                        }
                        R.id.action_custom_zero_query_delete_group -> {
                            showDeleteGroupDialog()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun setupRecyclerView() {
        adapter = CustomZeroQueryCandidateAdapter(
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            onEnabledChanged = { entry, enabled -> viewModel.updateEnabled(entry, enabled) },
            onMore = { entry, anchor -> showCandidatePopup(entry, anchor) },
        )
        binding.recyclerViewCustomZeroQueryCandidates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CustomZeroQueryGroupFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                viewModel.moveCandidate(
                    from = viewHolder.bindingAdapterPosition,
                    to = target.bindingAdapterPosition,
                )
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = false

            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewCustomZeroQueryCandidates)
    }

    private fun setupControls() {
        binding.editTextCustomZeroQueryGroupKey.doAfterTextChanged {
            viewModel.updateKeyDraft(it?.toString().orEmpty())
        }
        binding.buttonSaveCustomZeroQueryGroupKey.setOnClickListener {
            viewModel.saveKey()
        }
        binding.fabAddCustomZeroQueryCandidate.setOnClickListener {
            showCandidateDialog()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (binding.editTextCustomZeroQueryGroupKey.text?.toString() != state.keyDraft) {
                            binding.editTextCustomZeroQueryGroupKey.setText(state.keyDraft)
                            binding.editTextCustomZeroQueryGroupKey.setSelection(state.keyDraft.length)
                        }
                        adapter.submitList(state.entries)
                        binding.textCustomZeroQueryGroupEmpty.isVisible = state.entries.isEmpty()
                        binding.recyclerViewCustomZeroQueryCandidates.isVisible = state.entries.isNotEmpty()
                        binding.textCustomZeroQueryGroupCount.text = getString(
                            R.string.custom_zero_query_group_summary,
                            state.entries.size,
                            state.entries.count { it.enabled },
                        )
                    }
                }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showCandidatePopup(entry: CustomZeroQueryEntry, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(R.string.edit_text)
            menu.add(R.string.delete_string)
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    getString(R.string.edit_text) -> {
                        showCandidateDialog(entry)
                        true
                    }
                    else -> {
                        showDeleteCandidateDialog(entry)
                        true
                    }
                }
            }
            show()
        }
    }

    private fun showCandidateDialog(entry: CustomZeroQueryEntry? = null) {
        val dialogBinding = DialogCustomZeroQueryEntryBinding.inflate(layoutInflater)
        dialogBinding.inputLayoutCustomZeroQueryKey.isVisible = false
        dialogBinding.editTextCustomZeroQueryCandidate.setText(entry?.candidate.orEmpty())
        dialogBinding.switchCustomZeroQueryEnabled.isChecked = entry?.enabled ?: true

        AlertDialog.Builder(requireContext())
            .setTitle(
                if (entry == null) {
                    R.string.custom_zero_query_add_candidate
                } else {
                    R.string.custom_zero_query_edit_candidate
                }
            )
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_string) { _, _ ->
                val candidate = dialogBinding.editTextCustomZeroQueryCandidate.text
                    ?.toString()
                    .orEmpty()
                if (entry == null) {
                    viewModel.addCandidate(
                        candidate = candidate,
                        enabled = dialogBinding.switchCustomZeroQueryEnabled.isChecked,
                    )
                } else {
                    viewModel.updateCandidate(
                        entry = entry,
                        candidate = candidate,
                        enabled = dialogBinding.switchCustomZeroQueryEnabled.isChecked,
                    )
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showDeleteCandidateDialog(entry: CustomZeroQueryEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_zero_query_delete_candidate)
            .setMessage(getString(R.string.custom_zero_query_delete_candidate_message, entry.candidate))
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.deleteEntry(entry)
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showDeleteGroupDialog() {
        val displayKey = viewModel.uiState.value.displayKey
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_zero_query_delete_group)
            .setMessage(getString(R.string.custom_zero_query_delete_group_message, displayKey))
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.deleteGroup()
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
