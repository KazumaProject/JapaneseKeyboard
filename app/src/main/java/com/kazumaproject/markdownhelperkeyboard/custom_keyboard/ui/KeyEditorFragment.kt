package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.toCircularFlickMap
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickSlotActionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.CircularFlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.CircularFlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private enum class OutputEditMode {
    NORMAL,
    LONG_PRESS
}

private data class CustomKeyboardTargetOption(
    val label: String,
    val stableId: String,
    val isValid: Boolean
)

@AndroidEntryPoint
class KeyEditorFragment : Fragment(R.layout.fragment_key_editor) {

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    @Inject
    lateinit var appPreference: AppPreference

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)

    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private var currentKeyData: KeyData? = null

    private var currentFlickItems = mutableListOf<FlickMappingItem>()
    private var currentLongPressFlickItems = mutableListOf<FlickMappingItem>()
    private var currentTwoStepItems = mutableListOf<TwoStepMappingItem>()
    private var currentTwoStepLongPressItems = mutableListOf<TwoStepMappingItem>()
    private var currentSpecialFlickItems = mutableListOf<SpecialFlickMappingItem>()
    private var currentCircularFlickMaps = mutableListOf<MutableList<CircularFlickMappingItem>>()
    private var currentCircularMapIndex = 0
    private var outputEditMode: OutputEditMode = OutputEditMode.NORMAL
    private var isUpdatingCharEditText = false

    // 現在選択中のセルモード
    private var currentCellMode: CellMode? = null

    private lateinit var keyActionAdapter: ArrayAdapter<String>
    private lateinit var customKeyboardTargetAdapter: ArrayAdapter<String>
    private lateinit var circularFlickAdapter: CircularFlickMappingAdapter
    private lateinit var circularMapAdapter: ArrayAdapter<String>

    // NEW: UI-friendly display actions (avoids depending on unknown internal display type)
    private lateinit var displayActions: List<DisplayActionUi>
    private lateinit var specialFlickDisplayActions: List<DisplayActionUi>
    private var customKeyboardTargets: List<CustomKeyboardLayout> = emptyList()
    private var customKeyboardTargetOptions: List<CustomKeyboardTargetOption> = emptyList()
    private var selectedTargetCustomKeyboardStableId: String? = null

    private var currentColSpan: Int = 1
    private var currentRowSpan: Int = 1
    private var maxColSpan: Int = 1
    private var maxRowSpan: Int = 1

    // NEW: allowed directions for special-flick category (5 directions)
    private val allowedSpecialFlickDirections: List<FlickDirection> = listOf(
        FlickDirection.TAP,
        FlickDirection.UP,
        FlickDirection.DOWN,
        FlickDirection.UP_LEFT,
        FlickDirection.UP_RIGHT
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyEditorBinding.bind(view)

        binding.buttonDone.isEnabled = false
        setupToolbarAndMenu()
        setupGridEditor()
        setupUIListeners()
        setupInitialState()
    }

    private fun setupGridEditor() {
        // Convert KeyActionMapper display actions into stable UI list
        val raw = KeyActionMapper.getDisplayActions(requireContext())
        displayActions = raw.map { DisplayActionUi(it.displayName, it.action, it.iconResId) }
        specialFlickDisplayActions = displayActions
            .filterNot { it.action is KeyAction.MoveToCustomKeyboard }

        val actionDisplayNames = displayActions.map { it.displayName }
        keyActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actionDisplayNames
        )
        binding.keyActionSpinner.setAdapter(keyActionAdapter)

        // 特殊フリック用アクションスピナー（セル選択後に表示）
        val specialActionNames = mutableListOf("").apply {
            addAll(specialFlickDisplayActions.map { it.displayName })
        }
        val specialActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            specialActionNames
        )
        binding.specialFlickMappingsRecyclerView.setAdapter(specialActionAdapter)

        customKeyboardTargetAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            mutableListOf<String>()
        )
        binding.customKeyboardTargetSpinner.setAdapter(customKeyboardTargetAdapter)

        circularFlickAdapter = CircularFlickMappingAdapter { updated ->
            val currentMap = currentCircularFlickMaps.getOrNull(currentCircularMapIndex)
                ?: return@CircularFlickMappingAdapter
            val idx = currentMap.indexOfFirst { it.direction == updated.direction }
            if (idx != -1) {
                currentMap[idx] = updated
                updateDoneButtonState()
            }
        }
        binding.circularFlickMappingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = circularFlickAdapter
        }
        circularMapAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            mutableListOf<String>()
        )
        binding.spinnerCircularMap.adapter = circularMapAdapter

        // グリッドのセル選択コールバック
        binding.flickGridEditorView.onCellSelected = { mode ->
            currentCellMode = mode
            showEditorForMode(mode)
        }

        // 文字入力欄の変更コールバック
        binding.textCharEdittext.doAfterTextChanged { editable ->
            if (isUpdatingCharEditText) return@doAfterTextChanged
            val mode = currentCellMode ?: return@doAfterTextChanged
            val text = editable?.toString() ?: ""
            when (mode) {
                is CellMode.Petal -> {
                    val items = currentPetalItems()
                    val idx = items.indexOfFirst { it.direction == mode.direction }
                    if (idx != -1) items[idx] = items[idx].copy(output = text)
                    binding.flickGridEditorView.updateCellLabel(mode, text)
                }
                is CellMode.TwoStepFirst -> {
                    val items = currentTwoStepItemsForOutputMode()
                    updateTwoStepOutput(items, mode.first, mode.first, text)
                    binding.flickGridEditorView.refreshTwoStepLabels(items.toList())
                    updateDoneButtonState()
                }
                is CellMode.TwoStepSecond -> {
                    val items = currentTwoStepItemsForOutputMode()
                    updateTwoStepOutput(items, mode.first, mode.second, text)
                    binding.flickGridEditorView.refreshTwoStepLabels(items.toList())
                    updateDoneButtonState()
                }
                else -> Unit
            }
        }

        // 特殊フリック用アクション選択コールバック
        binding.specialFlickMappingsRecyclerView.setOnItemClickListener { _, _, idx, _ ->
            val mode = currentCellMode as? CellMode.SpecialFlick ?: return@setOnItemClickListener
            val selectedAction = if (idx == 0) null else specialFlickDisplayActions[idx - 1].action
            val itemIdx = currentSpecialFlickItems.indexOfFirst { it.direction == mode.direction }
            if (itemIdx != -1) {
                currentSpecialFlickItems[itemIdx] = currentSpecialFlickItems[itemIdx].copy(action = selectedAction)
                val displayAction = selectedAction?.let { act -> displayActionForAction(act) }
                binding.flickGridEditorView.updateCellIcon(
                    mode,
                    displayAction?.iconResId,
                    displayAction?.displayName ?: ""
                )
                updateDoneButtonState()
            }
        }
    }

    private fun setupUIListeners() {
        binding.buttonDone.setOnClickListener { onDone() }

        binding.keyTypeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedChipId = checkedIds.first()
            val isSpecialKey = selectedChipId == R.id.chip_special

            // Special key: show category selector (single/flick)
            binding.textSpecialCategoryTitle.isVisible = isSpecialKey
            binding.specialCategoryChipGroup.isVisible = isSpecialKey

            // Normal UI blocks
            binding.textInputStyleTitle.isVisible = !isSpecialKey
            binding.inputStyleChipGroup.isVisible = !isSpecialKey
            binding.textOutputModeTitle.isVisible = !isSpecialKey
            binding.outputModeChipGroup.isVisible = !isSpecialKey

            if (isSpecialKey) {
                // Default category = single if none selected yet
                if (binding.specialCategoryChipGroup.checkedChipId == View.NO_ID) {
                    binding.specialCategoryChipGroup.check(R.id.chip_special_single)
                }

                // hide normal editors
                binding.keyLabelLayout.isVisible = false
                binding.flickGridEditorView.isVisible = false
                binding.textSelectedDirection.isVisible = false
                binding.textCharInputLayout.isVisible = false

                // show the right special editor
                handleSpecialCategoryUi()
            } else {
                // hide special editors
                binding.keyActionLayout.isVisible = false
                binding.customKeyboardTargetLayout.isVisible = false
                binding.specialFlickEditorGroup.isVisible = false

                // normal key: input style controls which editor is visible
                handleInputStyleUi()
            }

            updateDoneButtonState()
        }

        // NEW: category change for special key
        binding.specialCategoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            handleSpecialCategoryUi()
            updateDoneButtonState()
        }

        binding.inputStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            handleInputStyleUi()
            updateDoneButtonState()
        }

        binding.spinnerCircularMap.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == currentCircularMapIndex) return
                currentCircularMapIndex = position
                refreshCircularEditor()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.btnCircularMapAdd.setOnClickListener {
            currentCircularFlickMaps.add(createDefaultCircularItems())
            currentCircularMapIndex = currentCircularFlickMaps.lastIndex
            refreshCircularMapSelector()
            refreshCircularEditor()
            updateDoneButtonState()
        }

        binding.btnCircularMapDuplicate.setOnClickListener {
            val source = currentCircularFlickMaps.getOrNull(currentCircularMapIndex)
                ?: createDefaultCircularItems()
            currentCircularFlickMaps.add(source.map { it.copy(id = java.util.UUID.randomUUID().toString()) }.toMutableList())
            currentCircularMapIndex = currentCircularFlickMaps.lastIndex
            refreshCircularMapSelector()
            refreshCircularEditor()
            updateDoneButtonState()
        }

        binding.btnCircularMapDelete.setOnClickListener {
            if (currentCircularFlickMaps.size <= 1) return@setOnClickListener
            currentCircularFlickMaps.removeAt(currentCircularMapIndex)
            currentCircularMapIndex = currentCircularMapIndex.coerceAtMost(currentCircularFlickMaps.lastIndex)
            refreshCircularMapSelector()
            refreshCircularEditor()
            updateDoneButtonState()
        }

        binding.outputModeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            outputEditMode = if (checkedIds.first() == R.id.chip_long_press_output) {
                OutputEditMode.LONG_PRESS
            } else {
                OutputEditMode.NORMAL
            }
            handleInputStyleUi()
            updateDoneButtonState()
        }

        binding.keyLabelEdittext.doAfterTextChanged { text ->
            updateDoneButtonState()
            // ペタルフリックの中央セルラベルをリアルタイム更新
            if (binding.inputStyleChipGroup.checkedChipId == R.id.chip_petal_flick && !isLongPressOutputMode()) {
                val label = text?.toString() ?: ""
                val tapOutput = currentFlickItems.firstOrNull { it.direction == FlickDirection.TAP }?.output ?: ""
                binding.flickGridEditorView.updateCellLabel(
                    CellMode.Petal(FlickDirection.TAP),
                    label.ifEmpty { tapOutput }
                )
            }
        }

        binding.keyActionSpinner.doAfterTextChanged {
            updateCustomKeyboardTargetVisibility()
            updateDoneButtonState()
        }

        binding.customKeyboardTargetSpinner.setOnItemClickListener { _, _, position, _ ->
            val option = customKeyboardTargetOptions.getOrNull(position)
            selectedTargetCustomKeyboardStableId = option
                ?.takeIf { it.isValid }
                ?.stableId
            updateDoneButtonState()
        }

        binding.btnColPlus.setOnClickListener {
            if (currentColSpan < maxColSpan) {
                currentColSpan++
                updateSizeDisplay()
            }
        }
        binding.btnColMinus.setOnClickListener {
            if (currentColSpan > 1) {
                currentColSpan--
                updateSizeDisplay()
            }
        }
        binding.btnRowPlus.setOnClickListener {
            if (currentRowSpan < maxRowSpan) {
                currentRowSpan++
                updateSizeDisplay()
            }
        }
        binding.btnRowMinus.setOnClickListener {
            if (currentRowSpan > 1) {
                currentRowSpan--
                updateSizeDisplay()
            }
        }
    }

    private fun handleSpecialCategoryUi() {
        val isFlick = binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_flick

        binding.keyLabelLayout.isVisible = false
        binding.keyActionLayout.isVisible = !isFlick
        binding.customKeyboardTargetLayout.isVisible = false
        binding.flickGridEditorView.isVisible = isFlick
        binding.specialFlickEditorGroup.isVisible = false
        binding.textSelectedDirection.isVisible = false
        binding.textCharInputLayout.isVisible = false
        currentCellMode = null

        if (isFlick) {
            if (currentSpecialFlickItems.isEmpty()) {
                currentSpecialFlickItems = allowedSpecialFlickDirections
                    .map { dir -> SpecialFlickMappingItem(direction = dir, action = null) }
                    .toMutableList()
            }
            binding.flickGridEditorView.setSpecialFlickContent(
                currentSpecialFlickItems.toList(),
                specialFlickDisplayActions
            )
            binding.flickGridEditorView.selectInitialCell()
        } else {
            updateCustomKeyboardTargetVisibility()
        }
    }

    private fun handleInputStyleUi() {
        val selectedStyle = binding.inputStyleChipGroup.checkedChipId
        val isTwoStep = selectedStyle == R.id.chip_two_step_flick
        val isCircular = selectedStyle == R.id.chip_circular_flick

        if (binding.outputModeChipGroup.checkedChipId == View.NO_ID) {
            binding.outputModeChipGroup.check(R.id.chip_normal_output)
        }

        binding.keyLabelLayout.isVisible = true
        binding.customKeyboardTargetLayout.isVisible = false
        binding.flickGridEditorView.isVisible = true
        binding.circularFlickEditorGroup.isVisible = false
        binding.textSelectedDirection.isVisible = false
        binding.textCharInputLayout.isVisible = false
        binding.specialFlickEditorGroup.isVisible = false
        currentCellMode = null

        if (isCircular) {
            binding.flickGridEditorView.isVisible = false
            binding.textSelectedDirection.isVisible = false
            binding.textCharInputLayout.isVisible = false
            binding.circularFlickEditorGroup.isVisible = true
            if (currentCircularFlickMaps.isEmpty()) {
                currentCircularFlickMaps.add(createDefaultCircularItems())
            }
            refreshCircularMapSelector()
            refreshCircularEditor()
        } else if (!isTwoStep) {
            if (currentFlickItems.isEmpty()) {
                currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    FlickMappingItem(direction = direction, output = "")
                }.toMutableList()
            }
            if (currentLongPressFlickItems.isEmpty()) {
                currentLongPressFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    FlickMappingItem(direction = direction, output = "")
                }.toMutableList()
            }
            val keyLabel = binding.keyLabelEdittext.text.toString()
            val centerLabel = if (isLongPressOutputMode()) "" else keyLabel
            binding.flickGridEditorView.setPetalContent(
                currentPetalItems().toList(),
                displayActions,
                centerLabel
            )
        } else {
            if (currentTwoStepItems.isEmpty()) {
                currentTwoStepItems = createDefaultTwoStepItems()
            }
            if (currentTwoStepLongPressItems.isEmpty()) {
                currentTwoStepLongPressItems = createDefaultTwoStepItems()
            }
            binding.flickGridEditorView.setTwoStepContent(
                currentTwoStepItemsForOutputMode().toList(),
                displayActions
            )
        }

        if (!isCircular) {
            binding.flickGridEditorView.selectInitialCell()
        }
    }

    private fun createDefaultCircularItems(
        source: Map<CircularFlickDirection, FlickAction> = emptyMap()
    ): MutableList<CircularFlickMappingItem> {
        val directions = listOf(CircularFlickDirection.TAP) +
            CircularFlickDirection.slots(appPreference.circularFlickDirectionCount)
        return directions.map { direction ->
            val (actionType, output) = CircularFlickSlotActionMapper.fromFlickAction(
                direction = direction,
                action = source[direction]
            )
            CircularFlickMappingItem(
                direction = direction,
                actionType = actionType,
                output = output
            )
        }.toMutableList()
    }

    private fun refreshCircularMapSelector() {
        circularMapAdapter.clear()
        circularMapAdapter.addAll(currentCircularFlickMaps.indices.map { "Map ${it + 1}" })
        circularMapAdapter.notifyDataSetChanged()
        if (currentCircularFlickMaps.isNotEmpty()) {
            binding.spinnerCircularMap.setSelection(currentCircularMapIndex, false)
        }
        binding.btnCircularMapDelete.isEnabled = currentCircularFlickMaps.size > 1
    }

    private fun refreshCircularEditor() {
        val currentItems = currentCircularFlickMaps
            .getOrNull(currentCircularMapIndex)
            ?: createDefaultCircularItems().also { currentCircularFlickMaps.add(it) }

        val visibleDirections = (listOf(CircularFlickDirection.TAP) +
            CircularFlickDirection.slots(appPreference.circularFlickDirectionCount)).toSet()
        val normalizedItems = currentItems
            .filter { visibleDirections.contains(it.direction) }
        circularFlickAdapter.submitList(normalizedItems)
    }

    /**
     * セル選択時に入力欄を表示し、現在の値をセットする
     */
    private fun showEditorForMode(mode: CellMode) {
        val directionLabel = when (mode) {
            is CellMode.Petal -> FlickDirectionMapper.toDisplayName(mode.direction, requireContext())
            is CellMode.SpecialFlick -> FlickDirectionMapper.toDisplayName(mode.direction, requireContext())
            is CellMode.TwoStepFirst -> FlickDirectionMapper.toDisplayName(mode.first, requireContext())
            is CellMode.TwoStepSecond -> "${FlickDirectionMapper.toDisplayName(mode.first, requireContext())} → ${
                FlickDirectionMapper.toDisplayName(mode.second, requireContext())
            }"
        }

        binding.textSelectedDirection.text = if (isLongPressOutputMode() && mode !is CellMode.SpecialFlick) {
            "長押し: $directionLabel"
        } else {
            getString(R.string.direction_action_label, directionLabel)
        }
        binding.textSelectedDirection.isVisible = true

        when (mode) {
            is CellMode.Petal -> {
                val value = currentPetalItems().firstOrNull { it.direction == mode.direction }?.output ?: ""
                binding.textCharInputLayout.hint = if (isLongPressOutputMode()) {
                    "長押し時の出力"
                } else {
                    getString(R.string.two_step_output_label)
                }
                binding.textCharInputLayout.isVisible = true
                binding.specialFlickEditorGroup.isVisible = false
                setCharEditorValue(value)
            }
            is CellMode.TwoStepFirst -> {
                val value = currentTwoStepItemsForOutputMode().firstOrNull {
                    it.first == mode.first && it.second == mode.first
                }?.output ?: ""
                binding.textCharInputLayout.hint = if (isLongPressOutputMode()) {
                    "長押し時の出力"
                } else {
                    getString(R.string.two_step_output_label)
                }
                binding.textCharInputLayout.isVisible = true
                binding.specialFlickEditorGroup.isVisible = false
                setCharEditorValue(value)
            }
            is CellMode.TwoStepSecond -> {
                val value = currentTwoStepItemsForOutputMode().firstOrNull {
                    it.first == mode.first && it.second == mode.second
                }?.output ?: ""
                binding.textCharInputLayout.hint = if (isLongPressOutputMode()) {
                    "長押し時の出力"
                } else {
                    getString(R.string.two_step_output_label)
                }
                binding.textCharInputLayout.isVisible = true
                binding.specialFlickEditorGroup.isVisible = false
                setCharEditorValue(value)
            }
            is CellMode.SpecialFlick -> {
                val currentAction = currentSpecialFlickItems
                    .firstOrNull { it.direction == mode.direction }?.action
                val currentName = currentAction?.let { act ->
                    specialFlickDisplayActions.firstOrNull { it.action == act }?.displayName
                }.orEmpty()
                binding.textCharInputLayout.isVisible = false
                binding.specialFlickEditorGroup.isVisible = true
                binding.specialFlickMappingsRecyclerView.setText(currentName, false)
            }
        }
    }

    private fun isLongPressOutputMode(): Boolean =
        outputEditMode == OutputEditMode.LONG_PRESS &&
                binding.outputModeChipGroup.checkedChipId == R.id.chip_long_press_output

    private fun currentPetalItems(): MutableList<FlickMappingItem> =
        if (isLongPressOutputMode()) currentLongPressFlickItems else currentFlickItems

    private fun currentTwoStepItemsForOutputMode(): MutableList<TwoStepMappingItem> =
        if (isLongPressOutputMode()) currentTwoStepLongPressItems else currentTwoStepItems

    private fun updateTwoStepOutput(
        items: MutableList<TwoStepMappingItem>,
        first: TfbiFlickDirection,
        second: TfbiFlickDirection,
        output: String
    ) {
        val idx = items.indexOfFirst { it.first == first && it.second == second }
        if (idx != -1) {
            items[idx] = items[idx].copy(output = output)
        }
    }

    private fun setCharEditorValue(value: String) {
        val editText = binding.textCharEdittext
        if (editText.text.toString() == value) return

        isUpdatingCharEditText = true
        editText.setText(value)
        editText.setSelection(value.length)
        isUpdatingCharEditText = false
    }

    private fun tfbiToDisplayName(dir: TfbiFlickDirection): String =
        FlickDirectionMapper.toDisplayName(dir, requireContext())

    private fun updateSizeDisplay() {
        binding.textColSpan.text = currentColSpan.toString()
        binding.textRowSpan.text = currentRowSpan.toString()

        binding.btnColPlus.isEnabled = currentColSpan < maxColSpan
        binding.btnColMinus.isEnabled = currentColSpan > 1
        binding.btnRowPlus.isEnabled = currentRowSpan < maxRowSpan
        binding.btnRowMinus.isEnabled = currentRowSpan > 1
    }

    private fun setupToolbarAndMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.edit_key)
            setDisplayHomeAsUpEnabled(true)
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            }

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

    private fun displayActionForAction(action: KeyAction): DisplayActionUi? {
        return when (action) {
            is KeyAction.MoveToCustomKeyboard ->
                displayActions.firstOrNull { it.action is KeyAction.MoveToCustomKeyboard }

            else -> displayActions.firstOrNull { it.action == action }
        }
    }

    private fun selectedSingleDisplayAction(): DisplayActionUi? {
        val selectedText = binding.keyActionSpinner.text.toString()
        return displayActions.firstOrNull { it.displayName == selectedText }
    }

    private fun isMoveToCustomKeyboardSelected(): Boolean {
        return selectedSingleDisplayAction()?.action is KeyAction.MoveToCustomKeyboard
    }

    private fun buildTargetOptions(
        targets: List<CustomKeyboardLayout>,
        deletedStableId: String? = null
    ): List<CustomKeyboardTargetOption> {
        val totalByName = targets.groupingBy { it.name }.eachCount()
        val seenByName = mutableMapOf<String, Int>()
        val validOptions = targets.map { layout ->
            val seenCount = (seenByName[layout.name] ?: 0) + 1
            seenByName[layout.name] = seenCount
            val label = if ((totalByName[layout.name] ?: 0) > 1) {
                "${layout.name} ($seenCount)"
            } else {
                layout.name
            }
            CustomKeyboardTargetOption(
                label = label,
                stableId = layout.stableId,
                isValid = true
            )
        }

        val deletedOption = deletedStableId
            ?.takeIf { it.isNotBlank() && validOptions.none { option -> option.stableId == it } }
            ?.let {
                CustomKeyboardTargetOption(
                    label = getString(R.string.deleted_custom_keyboard_target),
                    stableId = it,
                    isValid = false
                )
            }

        return if (deletedOption != null) validOptions + deletedOption else validOptions
    }

    private suspend fun loadCustomKeyboardTargets() {
        customKeyboardTargets = keyboardRepository.getLayoutsNotFlowEnsuringStableIds()
        refreshCustomKeyboardTargetOptions()
    }

    private fun refreshCustomKeyboardTargetOptions(deletedStableId: String? = null) {
        customKeyboardTargetOptions = buildTargetOptions(customKeyboardTargets, deletedStableId)
        customKeyboardTargetAdapter.clear()
        customKeyboardTargetAdapter.addAll(customKeyboardTargetOptions.map { it.label })
        customKeyboardTargetAdapter.notifyDataSetChanged()
    }

    private fun selectTargetCustomKeyboard(stableId: String?) {
        val id = stableId.orEmpty()
        if (id.isBlank()) {
            selectedTargetCustomKeyboardStableId = null
            binding.customKeyboardTargetSpinner.setText("", false)
            return
        }

        var option = customKeyboardTargetOptions.firstOrNull { it.stableId == id }
        if (option == null) {
            refreshCustomKeyboardTargetOptions(deletedStableId = id)
            option = customKeyboardTargetOptions.firstOrNull { it.stableId == id }
        }

        selectedTargetCustomKeyboardStableId = option
            ?.takeIf { it.isValid }
            ?.stableId
        binding.customKeyboardTargetSpinner.setText(option?.label.orEmpty(), false)
    }

    private fun updateCustomKeyboardTargetVisibility() {
        val shouldShow = binding.keyTypeChipGroup.checkedChipId == R.id.chip_special &&
                binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_single &&
                isMoveToCustomKeyboardSelected()

        binding.customKeyboardTargetLayout.isVisible = shouldShow
        if (!shouldShow) {
            return
        }

        if (selectedTargetCustomKeyboardStableId.isNullOrBlank()) {
            val firstValid = customKeyboardTargetOptions.firstOrNull { it.isValid }
            if (firstValid != null && binding.customKeyboardTargetSpinner.text.isNullOrEmpty()) {
                selectedTargetCustomKeyboardStableId = firstValid.stableId
                binding.customKeyboardTargetSpinner.setText(firstValid.label, false)
            }
        }
    }

    private fun updateDoneButtonState() {
        val isEnabled = when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                val isFlick =
                    binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_flick
                if (!isFlick) {
                    if (isMoveToCustomKeyboardSelected()) {
                        selectedTargetCustomKeyboardStableId
                            ?.takeIf { stableId ->
                                customKeyboardTargets.any { it.stableId == stableId }
                            }
                            ?.isNotBlank() == true
                    } else {
                        binding.keyActionSpinner.text.isNotEmpty()
                    }
                } else {
                    // Special flick: TAP must be selected
                    val tapAction = currentSpecialFlickItems
                        .firstOrNull { it.direction == FlickDirection.TAP }
                        ?.action
                    tapAction != null
                }
            }

            R.id.chip_normal -> {
                val isTwoStep =
                    binding.inputStyleChipGroup.checkedChipId == R.id.chip_two_step_flick
                if (isTwoStep) {
                    // TwoStep: base (TAP->TAP) must be filled
                    val base = currentTwoStepItems
                        .firstOrNull { it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP }
                        ?.output
                    !base.isNullOrEmpty()
                } else {
                    // Petal: label must be filled
                    binding.keyLabelEdittext.text.toString().isNotEmpty()
                }
            }

            else -> false
        }
        binding.buttonDone.isEnabled = isEnabled
    }

    private fun setupInitialState() {
        viewLifecycleOwner.lifecycleScope.launch {
            loadCustomKeyboardTargets()
            val state = viewModel.uiState.filterNotNull().first()

            currentKeyData =
                state.layout.keys.firstOrNull { it.keyId == state.selectedKeyIdentifier }

            if (currentKeyData == null) {
                findNavController().popBackStack()
                return@launch
            }

            val key = currentKeyData!!

            currentColSpan = key.colSpan
            currentRowSpan = key.rowSpan
            maxColSpan = state.layout.columnCount - key.column
            maxRowSpan = state.layout.rowCount - key.row
            updateSizeDisplay()

            // Key type: special / normal
            if (key.isSpecialKey) {
                binding.keyTypeChipGroup.check(R.id.chip_special)

                // Decide category: SINGLE or FLICK
                val flickMap = state.layout.flickKeyMaps[key.keyId]?.firstOrNull() ?: emptyMap()
                val isSpecialFlick = (key.keyType == KeyType.CROSS_FLICK) ||
                        flickMap.values.any { it is FlickAction.Action }

                // show category UI
                binding.textSpecialCategoryTitle.isVisible = true
                binding.specialCategoryChipGroup.isVisible = true
                binding.textOutputModeTitle.isVisible = false
                binding.outputModeChipGroup.isVisible = false

                if (isSpecialFlick) {
                    binding.specialCategoryChipGroup.check(R.id.chip_special_flick)

                    currentSpecialFlickItems = allowedSpecialFlickDirections.map { dir ->
                        val saved = flickMap[dir] as? FlickAction.Action
                        SpecialFlickMappingItem(direction = dir, action = saved?.action)
                    }.toMutableList()

                    handleSpecialCategoryUi()
                } else {
                    binding.specialCategoryChipGroup.check(R.id.chip_special_single)
                    handleSpecialCategoryUi()

                    key.action?.let { currentAction ->
                        val displayAction = displayActionForAction(currentAction)
                        if (displayAction != null) {
                            binding.keyActionSpinner.setText(displayAction.displayName, false)
                        }
                        if (currentAction is KeyAction.MoveToCustomKeyboard) {
                            selectTargetCustomKeyboard(currentAction.stableId)
                        }
                        updateCustomKeyboardTargetVisibility()
                    }
                }

                // hide normal editors for special
                binding.keyLabelLayout.isVisible = false
                binding.flickGridEditorView.isVisible =
                    (binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_flick)
            } else {
                binding.keyTypeChipGroup.check(R.id.chip_normal)

                // hide special UI
                binding.textSpecialCategoryTitle.isVisible = false
                binding.specialCategoryChipGroup.isVisible = false
                binding.keyActionLayout.isVisible = false
                binding.textOutputModeTitle.isVisible = true
                binding.outputModeChipGroup.isVisible = true
                binding.outputModeChipGroup.check(R.id.chip_normal_output)
                outputEditMode = OutputEditMode.NORMAL

                // Input style: petal or two-step
                if (key.keyType == KeyType.TWO_STEP_FLICK) {
                    binding.inputStyleChipGroup.check(R.id.chip_two_step_flick)
                } else if (key.keyType == KeyType.CIRCULAR_FLICK) {
                    binding.inputStyleChipGroup.check(R.id.chip_circular_flick)
                } else {
                    binding.inputStyleChipGroup.check(R.id.chip_petal_flick)
                }

                // Restore editors
                if (key.keyType == KeyType.TWO_STEP_FLICK) {
                    binding.keyLabelEdittext.setText(key.label)

                    // Restore from layout.twoStepFlickKeyMaps
                    val map = state.layout.twoStepFlickKeyMaps[key.keyId] ?: emptyMap()
                    currentTwoStepItems = createDefaultTwoStepItems()
                    applyTwoStepOutputs(currentTwoStepItems, map)

                    val longPressMap = state.layout.twoStepLongPressKeyMaps[key.keyId] ?: emptyMap()
                    currentTwoStepLongPressItems = createDefaultTwoStepItems()
                    applyTwoStepOutputs(currentTwoStepLongPressItems, longPressMap)

                    // グリッドはhandleInputStyleUi()で更新
                } else if (key.keyType == KeyType.CIRCULAR_FLICK) {
                    binding.keyLabelEdittext.setText(key.label)
                    val circularMaps = state.layout.circularFlickKeyMaps[key.keyId]
                        ?: state.layout.flickKeyMaps[key.keyId]?.map { it.toCircularFlickMap() }
                        ?: listOf(emptyMap())
                    currentCircularFlickMaps = circularMaps
                        .map { createDefaultCircularItems(it) }
                        .toMutableList()
                    currentCircularMapIndex = 0
                } else {
                    binding.keyLabelEdittext.setText(key.label)

                    val flickMap = state.layout.flickKeyMaps[key.keyId]?.firstOrNull() ?: emptyMap()
                    currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                        val savedAction = flickMap[direction]
                        val output = if (savedAction is FlickAction.Input) savedAction.char else ""
                        FlickMappingItem(direction = direction, output = output)
                    }.toMutableList()

                    val longPressFlickMap = state.layout.longPressFlickKeyMaps[key.keyId] ?: emptyMap()
                    currentLongPressFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                        FlickMappingItem(
                            direction = direction,
                            output = longPressFlickMap[direction].orEmpty()
                        )
                    }.toMutableList()
                }

                handleInputStyleUi()
            }

            updateDoneButtonState()
        }
    }

    private fun createDefaultTwoStepItems(): MutableList<TwoStepMappingItem> {
        return TwoStepMappingItem.ALLOWED_TWO_STEP_PAIRS.map { (first, second) ->
            TwoStepMappingItem(first = first, second = second, output = "")
        }.toMutableList()
    }

    private fun applyTwoStepOutputs(
        items: MutableList<TwoStepMappingItem>,
        outputs: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>
    ) {
        TwoStepMappingItem.ALLOWED_TWO_STEP_PAIRS.forEach { (first, second) ->
            val value = outputs[first]?.get(second).orEmpty()
            if (value.isNotEmpty()) {
                updateTwoStepOutput(items, first, second, value)
            }
        }
    }

    private fun buildTwoStepOutputMap(
        items: List<TwoStepMappingItem>,
        includeBaseFallback: Boolean
    ): Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>> {
        val firstMap = mutableMapOf<TfbiFlickDirection, MutableMap<TfbiFlickDirection, String>>()
        val base = items.firstOrNull {
            it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP
        }?.output.orEmpty()

        items
            .filter { it.output.isNotEmpty() }
            .forEach { item ->
                val inner = firstMap.getOrPut(item.first) { mutableMapOf() }
                if (includeBaseFallback &&
                    item.first != TfbiFlickDirection.TAP &&
                    item.second != TfbiFlickDirection.TAP &&
                    base.isNotEmpty()
                ) {
                    inner[TfbiFlickDirection.TAP] = base
                }
                inner[item.second] = item.output
            }

        return firstMap.mapValues { it.value.toMap() }
    }

    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->

        }

    private fun onDone() {
        val originalKey = currentKeyData ?: return

        val newLabel: String
        val newKeyType: KeyType
        val isSpecial: Boolean
        val newAction: KeyAction?
        var newFlickMap: Map<FlickDirection, FlickAction> = emptyMap()
        var newCircularFlickMaps: List<Map<CircularFlickDirection, FlickAction>> = emptyList()
        var newTwoStepMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>> = emptyMap()
        val newLongPressFlickMap: Map<FlickDirection, String>
        val newTwoStepLongPressMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>
        val newDrawableResId: Int?

        when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                val isFlick =
                    binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_flick

                isSpecial = true

                if (!isFlick) {
                    // Special: SINGLE (existing behavior)
                    newKeyType = KeyType.NORMAL

                    val selectedDisplayAction = selectedSingleDisplayAction()

                    newAction = if (selectedDisplayAction?.action is KeyAction.MoveToCustomKeyboard) {
                        val stableId = selectedTargetCustomKeyboardStableId
                            ?.takeIf { id -> customKeyboardTargets.any { it.stableId == id } }
                            ?: return
                        KeyAction.MoveToCustomKeyboard(stableId)
                    } else {
                        selectedDisplayAction?.action
                    }
                    newDrawableResId = selectedDisplayAction?.iconResId
                    newLabel =
                        if (newDrawableResId != null) "" else selectedDisplayAction?.displayName
                            ?: "ACTION"

                    if (newAction == KeyAction.VoiceInput) {
                        val context = requireContext()
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    newFlickMap = emptyMap()
                    newTwoStepMap = emptyMap()
                    newLongPressFlickMap = emptyMap()
                    newTwoStepLongPressMap = emptyMap()
                } else {
                    // Special: FLICK (NEW) -> KeyType.CROSS_FLICK + store KeyAction as FlickAction.Action
                    newKeyType = KeyType.CROSS_FLICK

                    val tapAction = currentSpecialFlickItems
                        .firstOrNull { it.direction == FlickDirection.TAP }
                        ?.action

                    // Done button guarantees tapAction != null, but keep safe
                    newAction = tapAction ?: KeyAction.Delete

                    val tapDisplay = displayActions.firstOrNull { it.action == newAction }
                    newDrawableResId = tapDisplay?.iconResId

                    Timber.d("KeyEditorFragment onDone: [$newAction] [$newDrawableResId]")
                    newLabel =
                        newAction.toString()

                    // Build flick map from 5 items (save only non-null)
                    newFlickMap = currentSpecialFlickItems
                        .mapNotNull { item ->
                            val act = item.action ?: return@mapNotNull null
                            val display = displayActions.firstOrNull { it.action == act }

                            Timber.d("KeyEditorFragment onDone: act=$act, iconFromDisplayActions=${display?.iconResId} [${item.direction}]")

                            item.direction to FlickAction.Action(
                                action = act,
                                label = null,
                                drawableResId = display?.iconResId
                            )
                        }
                        .toMap()

                    if (newAction == KeyAction.VoiceInput) {
                        val context = requireContext()
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    newTwoStepMap = emptyMap()
                    newLongPressFlickMap = emptyMap()
                    newTwoStepLongPressMap = emptyMap()
                }
            }

            else -> {
                isSpecial = false
                newAction = null
                newDrawableResId = null

                val isTwoStep =
                    binding.inputStyleChipGroup.checkedChipId == R.id.chip_two_step_flick
                val isCircular =
                    binding.inputStyleChipGroup.checkedChipId == R.id.chip_circular_flick

                if (isCircular) {
                    newKeyType = KeyType.CIRCULAR_FLICK
                    newLabel = binding.keyLabelEdittext.text.toString()
                    newCircularFlickMaps = currentCircularFlickMaps.map { items ->
                        items
                            .mapNotNull { item ->
                                val action = CircularFlickSlotActionMapper.toFlickAction(
                                    actionType = item.actionType,
                                    output = item.output
                                ) ?: return@mapNotNull null
                                item.direction to action
                            }
                            .toMap()
                    }.ifEmpty {
                        listOf(emptyMap())
                    }
                    newFlickMap = emptyMap()
                    newLongPressFlickMap = emptyMap()
                    newTwoStepLongPressMap = emptyMap()
                } else if (!isTwoStep) {
                    newKeyType = KeyType.PETAL_FLICK
                    newLabel = binding.keyLabelEdittext.text.toString()
                    newFlickMap = currentFlickItems
                        .filter { it.output.isNotEmpty() }
                        .associate { it.direction to FlickAction.Input(it.output) }
                    newLongPressFlickMap = currentLongPressFlickItems
                        .filter { it.output.isNotEmpty() }
                        .associate { it.direction to it.output }
                    newTwoStepLongPressMap = emptyMap()
                } else {
                    newKeyType = KeyType.TWO_STEP_FLICK

                    // Build nested map from 17 items
                    val base =
                        currentTwoStepItems.firstOrNull { it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP }
                            ?.output.orEmpty()
                    val configuredLabel = binding.keyLabelEdittext.text.toString().trim()
                    newLabel = configuredLabel.ifEmpty { base }

                    newTwoStepMap = buildTwoStepOutputMap(
                        items = currentTwoStepItems,
                        includeBaseFallback = true
                    )
                    newLongPressFlickMap = emptyMap()
                    newTwoStepLongPressMap = buildTwoStepOutputMap(
                        items = currentTwoStepLongPressItems,
                        includeBaseFallback = false
                    )
                }
            }
        }

        val updatedKey = originalKey.copy(
            label = newLabel,
            keyType = newKeyType,
            isSpecialKey = isSpecial,
            action = newAction,
            // IMPORTANT: special flick should still be flickable (KeyType != NORMAL)
            isFlickable = (newKeyType != KeyType.NORMAL),
            drawableResId = newDrawableResId,
            rowSpan = currentRowSpan,
            colSpan = currentColSpan
        )

        viewModel.updateKeyAndMappings(
            updatedKey,
            newFlickMap,
            newTwoStepMap,
            newLongPressFlickMap,
            newTwoStepLongPressMap,
            newCircularFlickMaps
        )
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = null
            setDisplayHomeAsUpEnabled(false)
        }
        viewModel.doneNavigatingToKeyEditor()

        _binding = null
    }
}
