package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentFrequentSettingsAddBottomSheetBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class FrequentSettingsAddBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentFrequentSettingsAddBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var candidates: List<SettingDestination>
    private lateinit var adapter: FrequentAddSettingsAdapter

    private var selectedKeys: MutableSet<String> = mutableSetOf()
    private var selectedCategoryFilter: SettingCategory? = null
    private var chipFiltersById: Map<Int, FrequentCategoryFilter> = emptyMap()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFrequentSettingsAddBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedKeys = savedInstanceState
            ?.getStringArrayList(ARG_SELECTED_KEYS)
            ?.toMutableSet()
            ?: arguments
                ?.getStringArrayList(ARG_SELECTED_KEYS)
                ?.toMutableSet()
            ?: mutableSetOf()
        candidates = SettingDestinations.frequentCandidates(requireContext())

        setupAdapter()
        setupCategoryChips()
        setupSearch()
        renderCandidates()
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val targetHeight = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = targetHeight
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            peekHeight = targetHeight
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(ARG_SELECTED_KEYS, ArrayList(selectedKeys))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.frequentSettingsAddRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapter() {
        adapter = FrequentAddSettingsAdapter(
            onAdd = { destination -> add(destination) },
        )
        binding.frequentSettingsAddRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), columnCount())
            adapter = this@FrequentSettingsAddBottomSheetFragment.adapter
        }
    }

    private fun setupCategoryChips() {
        val filters = FrequentSettingFilters.filters(requireContext(), visibleCandidates())
        val idMap = mutableMapOf<Int, FrequentCategoryFilter>()
        binding.frequentSettingsAddCategoryChipGroup.removeAllViews()
        filters.forEachIndexed { index, filter ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = filter.title
                isCheckable = true
                isChecked = index == 0
            }
            binding.frequentSettingsAddCategoryChipGroup.addView(chip)
            idMap[chip.id] = filter
        }
        chipFiltersById = idMap
        binding.frequentSettingsAddCategoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategoryFilter = checkedIds.firstOrNull()?.let { chipFiltersById[it]?.category }
            renderCandidates()
        }
    }

    private fun setupSearch() {
        binding.frequentSettingsAddSearchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderCandidates()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private fun renderCandidates() {
        val availableCandidates = visibleCandidates().filterNot { it.key in selectedKeys }
        val filteredCandidates = FrequentSettingFilters.filter(
            context = requireContext(),
            candidates = availableCandidates,
            selectedCategory = selectedCategoryFilter,
            query = binding.frequentSettingsAddSearchInput.text?.toString().orEmpty(),
        )
        binding.frequentSettingsAddEmptyText.isVisible = filteredCandidates.isEmpty()
        binding.frequentSettingsAddRecyclerView.isVisible = filteredCandidates.isNotEmpty()
        adapter.submitList(filteredCandidates)
    }

    private fun visibleCandidates(): List<SettingDestination> {
        val touchEffectType = AppPreference.keyboard_touch_effect_type_preference
        return candidates.filter { destination ->
            KeyboardTouchEffectSettingVisibility.isVisibleForEffect(
                destination = destination,
                effectType = touchEffectType,
            )
        }
    }

    private fun add(destination: SettingDestination) {
        if (!selectedKeys.add(destination.key)) return
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_ADD_FREQUENT_SETTING,
            bundleOf(KEY_SETTING_DESTINATION_KEY to destination.key),
        )
        renderCandidates()
    }

    private fun columnCount(): Int =
        if (resources.configuration.screenWidthDp >= 600) 2 else 1

    companion object {
        const val REQUEST_KEY_ADD_FREQUENT_SETTING =
            "request_key_add_frequent_setting"
        const val KEY_SETTING_DESTINATION_KEY =
            "key_setting_destination_key"

        private const val ARG_SELECTED_KEYS = "arg_selected_keys"
        private const val SHEET_HEIGHT_RATIO = 0.88f

        fun newInstance(selectedKeys: List<String>): FrequentSettingsAddBottomSheetFragment {
            return FrequentSettingsAddBottomSheetFragment().apply {
                arguments = bundleOf(ARG_SELECTED_KEYS to ArrayList(selectedKeys))
            }
        }
    }
}
