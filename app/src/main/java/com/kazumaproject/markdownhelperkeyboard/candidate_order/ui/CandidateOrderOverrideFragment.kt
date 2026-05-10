package com.kazumaproject.markdownhelperkeyboard.candidate_order.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
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
import com.kazumaproject.markdownhelperkeyboard.candidate_order.adapter.CandidateOrderOverrideAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateOrderOverrideBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CandidateOrderOverrideFragment : Fragment() {

    private var _binding: FragmentCandidateOrderOverrideBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CandidateOrderOverrideViewModel by viewModels()

    private lateinit var adapter: CandidateOrderOverrideAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidateOrderOverrideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupInputs()
        observeUiState()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        adapter = CandidateOrderOverrideAdapter(
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            }
        )

        binding.recyclerViewCandidateOrder.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CandidateOrderOverrideFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                viewModel.moveCandidate(
                    from = viewHolder.bindingAdapterPosition,
                    to = target.bindingAdapterPosition
                )
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = false

            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewCandidateOrder)
    }

    private fun setupInputs() {
        binding.editTextCandidateOrderReading.doAfterTextChanged {
            viewModel.updateReading(it?.toString().orEmpty())
        }
        binding.buttonFetchCandidateOrderCandidates.setOnClickListener {
            viewModel.fetchCandidates()
        }
        binding.buttonSaveCandidateOrder.setOnClickListener {
            viewModel.save()
        }
        binding.buttonDeleteCandidateOrder.setOnClickListener {
            viewModel.deleteCurrentReadingOrder()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.candidates)
                    binding.progressCandidateOrder.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.textCandidateOrderEmpty.visibility =
                        if (!state.isLoading && state.candidates.isEmpty()) View.VISIBLE else View.GONE
                    binding.textCandidateOrderEmpty.text =
                        if (state.reading.isBlank()) {
                            getString(R.string.candidate_order_override_empty)
                        } else {
                            getString(R.string.candidate_order_override_no_candidates)
                        }
                    binding.buttonSaveCandidateOrder.isEnabled =
                        !state.isLoading && state.candidates.isNotEmpty()
                    binding.buttonDeleteCandidateOrder.isEnabled =
                        !state.isLoading && state.reading.isNotBlank()
                    binding.buttonFetchCandidateOrderCandidates.isEnabled = !state.isLoading
                    state.message?.let { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
