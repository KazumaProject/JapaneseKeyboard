package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingFrequentEditBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FrequentSettingsEditFragment : Fragment() {

    private var _binding: FragmentSettingFrequentEditBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreference: AppPreference

    private lateinit var candidates: List<SettingDestination>
    private lateinit var selectedAdapter: FrequentSelectedSettingsAdapter

    private var selectedKeys: MutableList<String> = mutableListOf()
    private var itemTouchHelper: ItemTouchHelper? = null
    private var pendingDragSave = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingFrequentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        candidates = SettingDestinations.frequentCandidates(requireContext())
        selectedKeys = normalizedSelectedKeys().toMutableList()

        setupAdapters()
        setupInputs()
        setupFragmentResultListener()
        renderSelected()
    }

    override fun onDestroyView() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        binding.settingFrequentSelectedRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapters() {
        selectedAdapter = FrequentSelectedSettingsAdapter(
            onRemove = { destination -> remove(destination.key) },
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) },
            onMove = { key, direction -> move(key, direction) },
        )

        binding.settingFrequentSelectedRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), selectedColumnCount())
            adapter = selectedAdapter
        }
        setupDragAndDrop()
    }

    private fun setupDragAndDrop() {
        itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int = makeMovementFlags(
                    ItemTouchHelper.UP or
                        ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or
                        ItemTouchHelper.RIGHT,
                    0,
                )

                override fun isLongPressDragEnabled(): Boolean = false

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    source: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val fromPosition = source.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition
                    if (fromPosition == RecyclerView.NO_POSITION ||
                        toPosition == RecyclerView.NO_POSITION
                    ) {
                        return false
                    }
                    val moved = moveSelectedPosition(fromPosition, toPosition)
                    if (moved) {
                        pendingDragSave = true
                        renderSelected()
                    }
                    return moved
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
                        saveSelection()
                        renderSelected()
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
            helper.attachToRecyclerView(binding.settingFrequentSelectedRecyclerView)
        }
    }

    private fun setupInputs() {
        binding.settingFrequentAddButton.setOnClickListener {
            FrequentSettingsAddBottomSheetFragment
                .newInstance(selectedKeys)
                .show(parentFragmentManager, FrequentSettingsAddBottomSheetFragment::class.java.simpleName)
        }
        binding.settingFrequentEditResetButton.setOnClickListener {
            selectedKeys = SettingDestinations.defaultFrequent(requireContext())
                .map { it.key }
                .toMutableList()
            saveSelection()
            renderSelected()
        }
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener(
            FrequentSettingsAddBottomSheetFragment.REQUEST_KEY_ADD_FREQUENT_SETTING,
            viewLifecycleOwner,
        ) { _, bundle ->
            bundle
                .getString(FrequentSettingsAddBottomSheetFragment.KEY_SETTING_DESTINATION_KEY)
                ?.let { key -> add(key) }
        }
    }

    private fun renderSelected() {
        val selectedDestinations = selectedKeys.mapNotNull { candidateByKey()[it] }
        val countText = getString(
            R.string.setting_frequent_selected_count,
            selectedDestinations.size,
        )
        binding.settingFrequentSelectedCountText.apply {
            text = countText
            contentDescription = countText
        }
        binding.settingFrequentEditEmptyText.isVisible = selectedDestinations.isEmpty()
        binding.settingFrequentSelectedRecyclerView.isVisible = selectedDestinations.isNotEmpty()
        selectedAdapter.submitList(selectedDestinations)
    }

    private fun add(key: String) {
        if (key !in candidateByKey() || key in selectedKeys) return
        selectedKeys = (selectedKeys + key).distinct().toMutableList()
        saveSelection()
        renderSelected()
    }

    private fun remove(key: String) {
        if (key !in selectedKeys) return
        selectedKeys = selectedKeys.filterNot { it == key }.toMutableList()
        saveSelection()
        renderSelected()
    }

    private fun move(key: String, direction: Int): Boolean {
        val currentIndex = selectedKeys.indexOf(key)
        val targetIndex = currentIndex + direction
        if (currentIndex !in selectedKeys.indices || targetIndex !in selectedKeys.indices) {
            return false
        }
        moveSelectedPosition(currentIndex, targetIndex)
        saveSelection()
        renderSelected()
        return true
    }

    private fun moveSelectedPosition(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in selectedKeys.indices || toPosition !in selectedKeys.indices) {
            return false
        }
        if (fromPosition == toPosition) return false
        selectedKeys = selectedKeys.toMutableList().apply {
            val item = removeAt(fromPosition)
            add(toPosition, item)
        }
        return true
    }

    private fun normalizedSelectedKeys(): List<String> {
        val allowedKeys = candidates.map { it.key }.toSet()
        val saved = appPreference.setting_home_frequent_keys
            .filter { it in allowedKeys }
            .distinct()
        val normalized = if (appPreference.has_setting_home_frequent_keys) {
            saved
        } else {
            SettingDestinations.defaultFrequent(requireContext()).map { it.key }
        }.filter { it in allowedKeys }.distinct()
        appPreference.setting_home_frequent_keys = normalized
        return normalized
    }

    private fun saveSelection() {
        val allowedKeys = candidates.map { it.key }.toSet()
        val normalized = selectedKeys.filter { it in allowedKeys }.distinct()
        selectedKeys = normalized.toMutableList()
        appPreference.setting_home_frequent_keys = normalized
    }

    private fun candidateByKey(): Map<String, SettingDestination> =
        candidates.associateBy { it.key }

    private fun selectedColumnCount(): Int =
        if (resources.configuration.screenWidthDp >= 600) 3 else 2
}
