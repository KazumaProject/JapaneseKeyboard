package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
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
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class KeyEditorFragment : Fragment(R.layout.fragment_key_editor) {

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)

    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private var currentKeyData: KeyData? = null

    private var currentFlickItems = mutableListOf<FlickMappingItem>()
    private var currentTwoStepItems = mutableListOf<TwoStepMappingItem>()
    private var currentSpecialFlickItems = mutableListOf<SpecialFlickMappingItem>()

    // 現在選択中のセルモード
    private var currentCellMode: CellMode? = null

    private lateinit var keyActionAdapter: ArrayAdapter<String>

    // NEW: UI-friendly display actions (avoids depending on unknown internal display type)
    private lateinit var displayActions: List<DisplayActionUi>

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

        val actionDisplayNames = displayActions.map { it.displayName }
        keyActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actionDisplayNames
        )
        binding.keyActionSpinner.setAdapter(keyActionAdapter)

        // 特殊フリック用アクションスピナー（セル選択後に表示）
        val specialActionNames = mutableListOf("").apply { addAll(actionDisplayNames) }
        val specialActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            specialActionNames
        )
        binding.specialFlickMappingsRecyclerView.setAdapter(specialActionAdapter)

        // グリッドのセル選択コールバック
        binding.flickGridEditorView.onCellSelected = { mode ->
            currentCellMode = mode
            showEditorForMode(mode)
        }

        // 文字入力欄の変更コールバック
        binding.textCharEdittext.doAfterTextChanged { editable ->
            val mode = currentCellMode ?: return@doAfterTextChanged
            val text = editable?.toString() ?: ""
            when (mode) {
                is CellMode.Petal -> {
                    val idx = currentFlickItems.indexOfFirst { it.direction == mode.direction }
                    if (idx != -1) currentFlickItems[idx] = currentFlickItems[idx].copy(output = text)
                    binding.flickGridEditorView.updateCellLabel(mode, text)
                }
                is CellMode.TwoStepFirst -> {
                    val idx = currentTwoStepItems.indexOfFirst {
                        it.first == mode.first && it.second == mode.first
                    }
                    if (idx != -1) currentTwoStepItems[idx] = currentTwoStepItems[idx].copy(output = text)
                    binding.flickGridEditorView.refreshTwoStepLabels(currentTwoStepItems.toList())
                    updateDoneButtonState()
                }
                is CellMode.TwoStepSecond -> {
                    val idx = currentTwoStepItems.indexOfFirst {
                        it.first == mode.first && it.second == mode.second
                    }
                    if (idx != -1) currentTwoStepItems[idx] = currentTwoStepItems[idx].copy(output = text)
                    binding.flickGridEditorView.refreshTwoStepLabels(currentTwoStepItems.toList())
                    updateDoneButtonState()
                }
                else -> Unit
            }
        }

        // 特殊フリック用アクション選択コールバック
        binding.specialFlickMappingsRecyclerView.setOnItemClickListener { _, _, idx, _ ->
            val mode = currentCellMode as? CellMode.SpecialFlick ?: return@setOnItemClickListener
            val selectedAction = if (idx == 0) null else displayActions[idx - 1].action
            val itemIdx = currentSpecialFlickItems.indexOfFirst { it.direction == mode.direction }
            if (itemIdx != -1) {
                currentSpecialFlickItems[itemIdx] = currentSpecialFlickItems[itemIdx].copy(action = selectedAction)
                val displayAction = selectedAction?.let { act -> displayActions.firstOrNull { it.action == act } }
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

        binding.keyLabelEdittext.doAfterTextChanged { text ->
            updateDoneButtonState()
            // フリックの中央セルラベルをリアルタイム更新
            if (binding.inputStyleChipGroup.checkedChipId == R.id.chip_cross_flick) {
                val label = text?.toString() ?: ""
                val tapOutput = currentFlickItems.firstOrNull { it.direction == FlickDirection.TAP }?.output ?: ""
                binding.flickGridEditorView.updateCellLabel(
                    CellMode.Petal(FlickDirection.TAP),
                    label.ifEmpty { tapOutput }
                )
            }
        }

        binding.keyActionSpinner.doAfterTextChanged {
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
                displayActions
            )
            binding.flickGridEditorView.selectInitialCell()
        }
    }

    private fun handleInputStyleUi() {
        val selectedStyle = binding.inputStyleChipGroup.checkedChipId
        val isTwoStep = selectedStyle == R.id.chip_two_step_flick

        binding.keyLabelLayout.isVisible = true
        binding.flickGridEditorView.isVisible = true
        binding.textSelectedDirection.isVisible = false
        binding.textCharInputLayout.isVisible = false
        binding.specialFlickEditorGroup.isVisible = false
        currentCellMode = null

        if (!isTwoStep) {
            if (currentFlickItems.isEmpty()) {
                currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    FlickMappingItem(direction = direction, output = "")
                }.toMutableList()
            }
            val keyLabel = binding.keyLabelEdittext.text.toString()
            binding.flickGridEditorView.setPetalContent(currentFlickItems.toList(), displayActions, keyLabel)
        } else {
            if (currentTwoStepItems.isEmpty()) {
                currentTwoStepItems = createDefaultTwoStepItems()
            }
            binding.flickGridEditorView.setTwoStepContent(currentTwoStepItems.toList(), displayActions)
        }
        binding.flickGridEditorView.selectInitialCell()
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

        binding.textSelectedDirection.text = getString(R.string.direction_action_label, directionLabel)
        binding.textSelectedDirection.isVisible = true

        when (mode) {
            is CellMode.Petal -> {
                val value = currentFlickItems.firstOrNull { it.direction == mode.direction }?.output ?: ""
                binding.textCharInputLayout.hint = getString(R.string.two_step_output_label)
                binding.textCharInputLayout.isVisible = true
                binding.specialFlickEditorGroup.isVisible = false
                val editText = binding.textCharEdittext
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(value.length)
                }
            }
            is CellMode.TwoStepFirst -> {
                val value = currentTwoStepItems.firstOrNull {
                    it.first == mode.first && it.second == mode.first
                }?.output ?: ""
                binding.textCharInputLayout.hint = getString(R.string.two_step_output_label)
                binding.textCharInputLayout.isVisible = true
                binding.specialFlickEditorGroup.isVisible = false
                val editText = binding.textCharEdittext
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(value.length)
                }
            }
            is CellMode.TwoStepSecond -> {
                val value = currentTwoStepItems.firstOrNull {
                    it.first == mode.first && it.second == mode.second
                }?.output ?: ""
                binding.textCharInputLayout.hint = getString(R.string.two_step_output_label)
                binding.textCharInputLayout.isVisible = true
                binding.specialFlickEditorGroup.isVisible = false
                val editText = binding.textCharEdittext
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(value.length)
                }
            }
            is CellMode.SpecialFlick -> {
                val currentAction = currentSpecialFlickItems
                    .firstOrNull { it.direction == mode.direction }?.action
                val currentName = currentAction?.let { act ->
                    displayActions.firstOrNull { it.action == act }?.displayName
                }.orEmpty()
                binding.textCharInputLayout.isVisible = false
                binding.specialFlickEditorGroup.isVisible = true
                binding.specialFlickMappingsRecyclerView.setText(currentName, false)
            }
        }
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

    private fun updateDoneButtonState() {
        val isEnabled = when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                val isFlick =
                    binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_flick
                if (!isFlick) {
                    binding.keyActionSpinner.text.isNotEmpty()
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
                        val displayAction = displayActions.find { it.action == currentAction }
                        if (displayAction != null) {
                            binding.keyActionSpinner.setText(displayAction.displayName, false)
                        }
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

                // Input style: petal or two-step
                if (key.keyType == KeyType.TWO_STEP_FLICK) {
                    binding.inputStyleChipGroup.check(R.id.chip_two_step_flick)
                } else {
                    binding.inputStyleChipGroup.check(R.id.chip_cross_flick)
                }

                // Restore editors
                if (key.keyType == KeyType.TWO_STEP_FLICK) {
                    binding.keyLabelEdittext.setText(key.label)

                    // Restore from layout.twoStepFlickKeyMaps
                    val map = state.layout.twoStepFlickKeyMaps[key.keyId] ?: emptyMap()
                    currentTwoStepItems = createDefaultTwoStepItems()

                    TwoStepMappingItem.ALLOWED_TWO_STEP_PAIRS.forEach { (first, second) ->
                        val value = map[first]?.get(second).orEmpty()
                        if (value.isNotEmpty()) {
                            val idx = currentTwoStepItems.indexOfFirst { it.first == first && it.second == second }
                            if (idx != -1) {
                                currentTwoStepItems[idx] = currentTwoStepItems[idx].copy(output = value)
                            }
                        }
                    }

                    // グリッドはhandleInputStyleUi()で更新
                }
 else {
                    binding.keyLabelEdittext.setText(key.label)

                    val flickMap = state.layout.flickKeyMaps[key.keyId]?.firstOrNull() ?: emptyMap()
                    currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                        val savedAction = flickMap[direction]
                        val output = if (savedAction is FlickAction.Input) savedAction.char else ""
                        FlickMappingItem(direction = direction, output = output)
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

    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

        }

    private fun onDone() {
        val originalKey = currentKeyData ?: return

        val newLabel: String
        val newKeyType: KeyType
        val isSpecial: Boolean
        val newAction: KeyAction?
        var newFlickMap: Map<FlickDirection, FlickAction> = emptyMap()
        var newTwoStepMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>> = emptyMap()
        val newDrawableResId: Int?

        when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                val isFlick =
                    binding.specialCategoryChipGroup.checkedChipId == R.id.chip_special_flick

                isSpecial = true

                if (!isFlick) {
                    // Special: SINGLE (existing behavior)
                    newKeyType = KeyType.NORMAL

                    val selectedText = binding.keyActionSpinner.text.toString()
                    val selectedDisplayAction =
                        displayActions.firstOrNull { it.displayName == selectedText }

                    newAction = selectedDisplayAction?.action
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
                }
            }

            else -> {
                isSpecial = false
                newAction = null
                newDrawableResId = null

                val isTwoStep =
                    binding.inputStyleChipGroup.checkedChipId == R.id.chip_two_step_flick

                if (!isTwoStep) {
                    newKeyType = KeyType.CROSS_FLICK
                    newLabel = binding.keyLabelEdittext.text.toString()
                    newFlickMap = currentFlickItems
                        .filter { it.output.isNotEmpty() }
                        .associate { it.direction to FlickAction.Input(it.output) }
                } else {
                    newKeyType = KeyType.TWO_STEP_FLICK

                    // Build nested map from 17 items
                    val base =
                        currentTwoStepItems.firstOrNull { it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP }
                            ?.output.orEmpty()
                    val configuredLabel = binding.keyLabelEdittext.text.toString().trim()
                    newLabel = configuredLabel.ifEmpty { base }

                    val firstMap =
                        mutableMapOf<TfbiFlickDirection, MutableMap<TfbiFlickDirection, String>>()

                    // base
                    if (base.isNotEmpty()) {
                        firstMap.getOrPut(TfbiFlickDirection.TAP) { mutableMapOf() }[TfbiFlickDirection.TAP] =
                            base
                    }

                    // other 16 (store only if filled)
                    currentTwoStepItems
                        .filterNot { it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP }
                        .forEach { item ->
                            if (item.output.isNotEmpty()) {
                                val inner = firstMap.getOrPut(item.first) { mutableMapOf() }
                                // ensure TAP fallback to base for this first-direction
                                if (base.isNotEmpty()) {
                                    inner[TfbiFlickDirection.TAP] = base
                                }
                                inner[item.second] = item.output
                            }
                        }

                    newTwoStepMap = firstMap.mapValues { it.value.toMap() }
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

        viewModel.updateKeyAndMappings(updatedKey, newFlickMap, newTwoStepMap)
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
