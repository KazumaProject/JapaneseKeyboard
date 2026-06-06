package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
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
    private lateinit var availableAdapter: FrequentAvailableSettingsAdapter

    private var selectedKeys: MutableList<String> = mutableListOf()
    private var selectedCategoryFilter: SettingCategory? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var pendingDragSave = false
    private var chipFiltersById: Map<Int, FrequentCategoryFilter> = emptyMap()

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
        setupCategoryChips()
        setupInputs()
        renderAll()
    }

    override fun onDestroyView() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        binding.settingFrequentSelectedRecyclerView.adapter = null
        binding.settingFrequentAvailableRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapters() {
        selectedAdapter = FrequentSelectedSettingsAdapter(
            onRemove = { destination -> remove(destination.key) },
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) },
            onMove = { key, direction -> move(key, direction) },
        )
        availableAdapter = FrequentAvailableSettingsAdapter(
            onAdd = { destination -> add(destination.key) },
        )

        binding.settingFrequentSelectedRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), selectedColumnCount())
            adapter = selectedAdapter
        }
        binding.settingFrequentAvailableRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), availableColumnCount())
            adapter = availableAdapter
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

    private fun setupCategoryChips() {
        val filters = buildCategoryFilters()
        val idMap = mutableMapOf<Int, FrequentCategoryFilter>()
        binding.settingFrequentCategoryChipGroup.removeAllViews()
        filters.forEachIndexed { index, filter ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = filter.title
                isCheckable = true
                isChecked = index == 0
            }
            binding.settingFrequentCategoryChipGroup.addView(chip)
            idMap[chip.id] = filter
        }
        chipFiltersById = idMap
        binding.settingFrequentCategoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategoryFilter = checkedIds.firstOrNull()?.let { chipFiltersById[it]?.category }
            renderAvailable()
        }
    }

    private fun setupInputs() {
        binding.settingFrequentEditResetButton.setOnClickListener {
            selectedKeys = SettingDestinations.defaultFrequent(requireContext())
                .map { it.key }
                .toMutableList()
            saveSelection()
            renderAll()
        }
        binding.settingFrequentAvailableSearchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderAvailable()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private fun renderAll() {
        renderSelected()
        renderAvailable()
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

    private fun renderAvailable() {
        val selectedKeySet = selectedKeys.toSet()
        val availableCandidates = candidates
            .filterNot { it.key in selectedKeySet }
            .filter { destination ->
                selectedCategoryFilter == null ||
                    destination.frequentFilterCategory() == selectedCategoryFilter
            }
        val query = binding.settingFrequentAvailableSearchInput.text?.toString().orEmpty()
        val filteredCandidates = SettingSearchIndex.filter(
            context = requireContext(),
            destinations = availableCandidates,
            query = query,
        )
        binding.settingFrequentAvailableEmptyText.isVisible = filteredCandidates.isEmpty()
        binding.settingFrequentAvailableRecyclerView.isVisible = filteredCandidates.isNotEmpty()
        availableAdapter.submitList(filteredCandidates)
    }

    private fun add(key: String) {
        if (key !in candidateByKey() || key in selectedKeys) return
        selectedKeys = (selectedKeys + key).distinct().toMutableList()
        saveSelection()
        renderAll()
    }

    private fun remove(key: String) {
        if (key !in selectedKeys) return
        selectedKeys = selectedKeys.filterNot { it == key }.toMutableList()
        saveSelection()
        renderAll()
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

    private fun availableColumnCount(): Int =
        if (resources.configuration.screenWidthDp >= 600) 2 else 1

    private fun buildCategoryFilters(): List<FrequentCategoryFilter> {
        val availableCategories = candidates.map { it.frequentFilterCategory() }.toSet()
        val desiredCategories = listOf(
            SettingCategory.KEYBOARD_DISPLAY,
            SettingCategory.INPUT_METHOD,
            SettingCategory.CANDIDATE_CONVERSION,
            SettingCategory.DICTIONARY,
            SettingCategory.AI_CONVERSION,
            SettingCategory.CLIPBOARD_SHORTCUT,
            SettingCategory.OPERATION_FEEDBACK,
        )
        return buildList {
            add(FrequentCategoryFilter(getString(R.string.setting_frequent_category_all), null))
            desiredCategories
                .filter { it in availableCategories }
                .forEach { category ->
                    add(
                        FrequentCategoryFilter(
                            title = SettingDestinations.categoryTitle(requireContext(), category),
                            category = category,
                        )
                    )
                }
        }
    }

    private fun SettingDestination.frequentFilterCategory(): SettingCategory {
        if (category != SettingCategory.FREQUENT) return category
        val destinationId = when (val target = destination) {
            is SettingDestinationType.NavDestination -> target.destinationId
        }
        return when (destinationId) {
            R.id.keyboardDisplayPreferenceFragment,
            R.id.keyboardSettingFragment,
            R.id.keyboardSizeLandscapeFragment,
            R.id.keyboardThemeFragment,
            R.id.candidateViewHeightSettingFragment,
            R.id.candidateHeightLandscapeSettingFragment,
            R.id.keyCandidateLetterSizeFragment,
            -> SettingCategory.KEYBOARD_DISPLAY

            R.id.inputMethodPreferenceFragment,
            R.id.keyboardSelectionFragment,
            R.id.kanaPreferenceFragment,
            R.id.qwertyPreferenceFragment,
            R.id.sumirePreferenceFragment,
            R.id.customKeyboardPreferenceFragment,
            R.id.tabletPreferenceFragment,
            R.id.hardwareKeyboardPreferenceFragment,
            R.id.tenKeyCandidateLetterSizeFragment,
            R.id.tenKeyPopupStyleSettingFragment,
            R.id.qwertyMarginSettingFragment,
            R.id.qwertyNumberKeyFlickSettingFragment,
            R.id.qwertyPopupStyleSettingFragment,
            R.id.flickKeyboardPopupStyleListFragment,
            R.id.flickKeyboardSizeSettingsFragment,
            R.id.circularFlickSettingsFragment,
            R.id.circularSlotActionSettingFragment,
            R.id.sumireSpecialKeyEditorFragment,
            -> SettingCategory.INPUT_METHOD

            R.id.candidateConversionPreferenceFragment,
            R.id.candidateTabOrderFragment,
            R.id.candidateOrderOverrideFragment,
            -> SettingCategory.CANDIDATE_CONVERSION

            R.id.dictionaryPreferenceFragment,
            R.id.systemUserDictionaryBuilderFragment,
            R.id.externalDictionarySettingsFragment,
            R.id.ngramRuleFragment,
            R.id.ngWordFragment,
            -> SettingCategory.DICTIONARY

            R.id.aiConversionPreferenceFragment,
            R.id.zenzPreferenceFragment,
            R.id.gemmaPreferenceFragment,
            R.id.gemmaPromptTemplateFragment,
            -> SettingCategory.AI_CONVERSION

            R.id.clipboardShortcutPreferenceFragment,
            R.id.clipboardHistoryFragment,
            R.id.shortcutSettingFragment,
            -> SettingCategory.CLIPBOARD_SHORTCUT

            R.id.operationFeedbackPreferenceFragment,
            R.id.deleteKeyFlickTargetsFragment,
            R.id.cursorMoveTargetPairsFragment,
            R.id.physicalKeyboardShortcutListFragment,
            -> SettingCategory.OPERATION_FEEDBACK

            else -> SettingCategory.ADVANCED
        }
    }

    private data class FrequentCategoryFilter(
        val title: String,
        val category: SettingCategory?,
    )
}
