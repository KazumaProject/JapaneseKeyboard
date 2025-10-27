package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_tab_order.ui

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
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateTabOrderBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_tab_order.adapter.CandidateTabOrderAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_tab_order.data.CandidateTabOrderViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class CandidateTabOrderFragment : Fragment() {

    private var _binding: FragmentCandidateTabOrderBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreferences: AppPreference

    private val viewModel: CandidateTabOrderViewModel by viewModels()

    private lateinit var candidateTabOrderAdapter: CandidateTabOrderAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidateTabOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupAddButton()
        observeUiState()

        if (savedInstanceState == null) {
            viewModel.setInitialTabs(appPreferences.candidate_tab_order)
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
                        if (!viewModel.uiState.value.isEditing) {
                            appPreferences.candidate_tab_order =
                                viewModel.uiState.value.candidateTabs
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
        candidateTabOrderAdapter = CandidateTabOrderAdapter(
            onStartDrag = { viewHolder ->
                if (viewModel.uiState.value.isEditing) {
                    itemTouchHelper.startDrag(viewHolder)
                }
            },
            onDeleteClick = { position ->
                val currentList = viewModel.uiState.value.candidateTabs.toMutableList()
                if (currentList.size > 1) {
                    currentList.removeAt(position)
                    viewModel.updateTabOrder(currentList)
                }
            }
        )

        binding.candidateTabOrderView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = candidateTabOrderAdapter
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
                val currentList = viewModel.uiState.value.candidateTabs.toMutableList()
                Collections.swap(currentList, fromPosition, toPosition)
                viewModel.updateTabOrder(currentList)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.candidateTabOrderView)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    candidateTabOrderAdapter.submitList(state.candidateTabs)
                    candidateTabOrderAdapter.setEditMode(state.isEditing)
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setupAddButton() {
        binding.addNewTabDialogTrigger.setOnClickListener {
            showAddTabDialog()
        }
    }

    private fun showAddTabDialog() {
        val allTabTypes = CandidateTab.entries.toTypedArray()
        val currentTabs = viewModel.uiState.value.candidateTabs
        val availableTabs = allTabTypes.filter { it !in currentTabs }

        if (availableTabs.isEmpty()) return

        val dialogItems = availableTabs.map { getCandidateTabDisplayName(it) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(com.kazumaproject.core.R.string.add_keyboard_dialog_title) // 必要なら文字列リソースを変更
            .setItems(dialogItems) { dialog, which ->
                val selectedTab = availableTabs[which]
                val newList = currentTabs.toMutableList().apply { add(selectedTab) }
                viewModel.updateTabOrder(newList)
                dialog.dismiss()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        appPreferences.candidate_tab_order = viewModel.uiState.value.candidateTabs
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getCandidateTabDisplayName(candidateTab: CandidateTab): String {
        return when (candidateTab) {
            CandidateTab.PREDICTION -> "予測変換"
            CandidateTab.CONVERSION -> "通常変換"
            CandidateTab.EISUKANA -> "英数・かな"
        }
    }
}
