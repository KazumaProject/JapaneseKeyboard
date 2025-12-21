package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.CustomFlickSuspension
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
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
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingAdapter
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

    private var flickAdapter: FlickMappingAdapter? = null

    // NEW: Special Flick Adapter
    private var specialFlickAdapter: SpecialFlickMappingAdapter? = null

    private var currentKeyData: KeyData? = null

    private var currentFlickItems = mutableListOf<FlickMappingItem>()

    // NEW: special flick items
    private var currentSpecialFlickItems = mutableListOf<SpecialFlickMappingItem>()

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
        setupAdapters()
        setupUIListeners()
        setupInitialState()
    }

    private fun setupAdapters() {
        flickAdapter = FlickMappingAdapter(
            onItemUpdated = { updatedItem ->
                val index = currentFlickItems.indexOfFirst { it.id == updatedItem.id }
                if (index != -1) currentFlickItems[index] = updatedItem
            },
            context = requireContext()
        )
        binding.flickMappingsRecyclerView.adapter = flickAdapter
        binding.flickMappingsRecyclerView.layoutManager = LinearLayoutManager(context)


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

        // NEW: Special flick adapter
        specialFlickAdapter = SpecialFlickMappingAdapter(
            context = requireContext(),
            displayActions = displayActions,
            onItemUpdated = { updated ->
                val idx = currentSpecialFlickItems.indexOfFirst { it.id == updated.id }
                if (idx != -1) {
                    currentSpecialFlickItems[idx] = updated
                    updateDoneButtonState()
                }
            }
        )
        binding.specialFlickMappingsRecyclerView.adapter = specialFlickAdapter
        binding.specialFlickMappingsRecyclerView.layoutManager = LinearLayoutManager(context)
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
                binding.flickEditorGroup.isVisible = false
                binding.twoStepEditorGroup.isVisible = false

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

        binding.keyLabelEdittext.doAfterTextChanged {
            updateDoneButtonState()
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

        binding.keyActionLayout.isVisible = !isFlick
        binding.specialFlickEditorGroup.isVisible = isFlick

        if (isFlick) {
            if (currentSpecialFlickItems.isEmpty()) {
                currentSpecialFlickItems = allowedSpecialFlickDirections
                    .map { dir -> SpecialFlickMappingItem(direction = dir, action = null) }
                    .toMutableList()
            }
            specialFlickAdapter?.submitList(currentSpecialFlickItems.toList())
        }
    }

    private fun handleInputStyleUi() {
        val selectedStyle = binding.inputStyleChipGroup.checkedChipId


        // Petal: label + flick list
        binding.keyLabelLayout.isVisible = true
        binding.flickEditorGroup.isVisible = true

        // TwoStep: 17 mapping list
        binding.twoStepEditorGroup.isVisible = false

        
            if (currentFlickItems.isEmpty()) {
                currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    FlickMappingItem(direction = direction, output = "")
                }.toMutableList()
            }
            flickAdapter?.submitList(currentFlickItems.toList())
        
    }

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

                {
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
                    specialFlickAdapter?.submitList(currentSpecialFlickItems.toList())
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
                binding.flickEditorGroup.isVisible = false
                binding.twoStepEditorGroup.isVisible = false
            } else {
                binding.keyTypeChipGroup.check(R.id.chip_normal)

                // hide special UI
                binding.textSpecialCategoryTitle.isVisible = false
                binding.specialCategoryChipGroup.isVisible = false
                binding.keyActionLayout.isVisible = false
                binding.specialFlickEditorGroup.isVisible = false

                // Input style: petal or two-step
                {
                    binding.inputStyleChipGroup.check(R.id.chip_petal_flick)
                }

                // Restore editors
                {
                    binding.keyLabelEdittext.setText(key.label)

                    val flickMap = state.layout.flickKeyMaps[key.keyId]?.firstOrNull() ?: emptyMap()
                    currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                        val savedAction = flickMap[direction]
                        val output = if (savedAction is FlickAction.Input) savedAction.char else ""
                        FlickMappingItem(direction = direction, output = output)
                    }.toMutableList()
                    flickAdapter?.submitList(currentFlickItems.toList())
                }

                handleInputStyleUi()
            }

            updateDoneButtonState()
        }
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

                    newTwoStepMap = emptyMap()
                }
            }

            else -> {
                isSpecial = false
                newAction = null
                newDrawableResId = null


                
                    newKeyType = KeyType.PETAL_FLICK
                    newLabel = binding.keyLabelEdittext.text.toString()
                    newFlickMap = currentFlickItems
                        .filter { it.output.isNotEmpty() }
                        .associate { it.direction to FlickAction.Input(it.output) }
                
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

        flickAdapter = null
        specialFlickAdapter = null
        _binding = null
    }
}
