package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

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
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.TwoStepMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KeyEditorFragment : Fragment(R.layout.fragment_key_editor) {

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)

    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private var flickAdapter: FlickMappingAdapter? = null
    private var twoStepAdapter: TwoStepMappingAdapter? = null

    private var currentKeyData: KeyData? = null

    private var currentFlickItems = mutableListOf<FlickMappingItem>()
    private var currentTwoStepItems = mutableListOf<TwoStepMappingItem>()

    private lateinit var keyActionAdapter: ArrayAdapter<String>

    private var currentColSpan: Int = 1
    private var currentRowSpan: Int = 1
    private var maxColSpan: Int = 1
    private var maxRowSpan: Int = 1

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

        twoStepAdapter = TwoStepMappingAdapter(
            onItemUpdated = { updatedItem ->
                val index = currentTwoStepItems.indexOfFirst { it.id == updatedItem.id }
                if (index != -1) {
                    currentTwoStepItems[index] = updatedItem
                    updateDoneButtonState()
                }
            }
        )
        binding.twoStepMappingsRecyclerView.adapter = twoStepAdapter
        binding.twoStepMappingsRecyclerView.layoutManager = LinearLayoutManager(context)

        val actionDisplayNames =
            KeyActionMapper.getDisplayActions(requireContext()).map { it.displayName }
        keyActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actionDisplayNames
        )
        binding.keyActionSpinner.setAdapter(keyActionAdapter)
    }

    private fun setupUIListeners() {
        binding.buttonDone.setOnClickListener { onDone() }

        binding.keyTypeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedChipId = checkedIds.first()
            val isSpecialKey = selectedChipId == R.id.chip_special

            binding.keyActionLayout.isVisible = isSpecialKey
            binding.textInputStyleTitle.isVisible = !isSpecialKey
            binding.inputStyleChipGroup.isVisible = !isSpecialKey

            if (isSpecialKey) {
                binding.keyLabelLayout.isVisible = false
                binding.flickEditorGroup.isVisible = false
                binding.twoStepEditorGroup.isVisible = false
            } else {
                // normal key: input style (petal / two-step) will control which editor is visible
                handleInputStyleUi()
            }

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

    private fun handleInputStyleUi() {
        val selectedStyle = binding.inputStyleChipGroup.checkedChipId
        val isTwoStep = selectedStyle == R.id.chip_two_step_flick

        // Petal: label + flick list
        binding.keyLabelLayout.isVisible = !isTwoStep
        binding.flickEditorGroup.isVisible = !isTwoStep

        // TwoStep: 17 mapping list
        binding.twoStepEditorGroup.isVisible = isTwoStep

        if (!isTwoStep) {
            if (currentFlickItems.isEmpty()) {
                currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    FlickMappingItem(direction = direction, output = "")
                }.toMutableList()
            }
            flickAdapter?.submitList(currentFlickItems.toList())
        } else {
            if (currentTwoStepItems.isEmpty()) {
                currentTwoStepItems = createDefaultTwoStepItems()
            }
            twoStepAdapter?.submitList(currentTwoStepItems.toList())
        }
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
                binding.keyActionSpinner.text.isNotEmpty()
            }

            R.id.chip_normal -> {
                val isTwoStep =
                    binding.inputStyleChipGroup.checkedChipId == R.id.chip_two_step_flick
                if (isTwoStep) {
                    // TwoStep: base (TAP->TAP) must be filled
                    val base =
                        currentTwoStepItems.firstOrNull { it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP }?.output
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
                key.action?.let { currentAction ->
                    val displayAction =
                        KeyActionMapper.getDisplayActions(requireContext())
                            .find { it.action == currentAction }
                    if (displayAction != null) {
                        binding.keyActionSpinner.setText(displayAction.displayName, false)
                    }
                }
            } else {
                binding.keyTypeChipGroup.check(R.id.chip_normal)

                // Input style: petal or two-step
                if (key.keyType == KeyType.TWO_STEP_FLICK) {
                    binding.inputStyleChipGroup.check(R.id.chip_two_step_flick)
                } else {
                    binding.inputStyleChipGroup.check(R.id.chip_petal_flick)
                }

                // Restore editors
                if (key.keyType == KeyType.TWO_STEP_FLICK) {
                    // Restore from layout.twoStepFlickKeyMaps
                    val map = state.layout.twoStepFlickKeyMaps[key.keyId] ?: emptyMap()
                    currentTwoStepItems = createDefaultTwoStepItems()

                    val base = map[TfbiFlickDirection.TAP]?.get(TfbiFlickDirection.TAP).orEmpty()

                    fun setItem(
                        first: TfbiFlickDirection,
                        second: TfbiFlickDirection,
                        value: String
                    ) {
                        val idx =
                            currentTwoStepItems.indexOfFirst { it.first == first && it.second == second }
                        if (idx != -1) {
                            currentTwoStepItems[idx] = currentTwoStepItems[idx].copy(output = value)
                        }
                    }

                    setItem(TfbiFlickDirection.TAP, TfbiFlickDirection.TAP, base)

                    // diagonals
                    setItem(
                        TfbiFlickDirection.UP_LEFT,
                        TfbiFlickDirection.UP_LEFT,
                        map[TfbiFlickDirection.UP_LEFT]?.get(TfbiFlickDirection.UP_LEFT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.DOWN_LEFT,
                        TfbiFlickDirection.DOWN_LEFT,
                        map[TfbiFlickDirection.DOWN_LEFT]?.get(TfbiFlickDirection.DOWN_LEFT)
                            .orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.UP_RIGHT,
                        TfbiFlickDirection.UP_RIGHT,
                        map[TfbiFlickDirection.UP_RIGHT]?.get(TfbiFlickDirection.UP_RIGHT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.DOWN_RIGHT,
                        TfbiFlickDirection.DOWN_RIGHT,
                        map[TfbiFlickDirection.DOWN_RIGHT]?.get(TfbiFlickDirection.DOWN_RIGHT)
                            .orEmpty()
                    )

                    // cardinals
                    setItem(
                        TfbiFlickDirection.LEFT,
                        TfbiFlickDirection.LEFT,
                        map[TfbiFlickDirection.LEFT]?.get(TfbiFlickDirection.LEFT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.LEFT,
                        TfbiFlickDirection.UP_LEFT,
                        map[TfbiFlickDirection.LEFT]?.get(TfbiFlickDirection.UP_LEFT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.LEFT,
                        TfbiFlickDirection.DOWN_LEFT,
                        map[TfbiFlickDirection.LEFT]?.get(TfbiFlickDirection.DOWN_LEFT).orEmpty()
                    )

                    setItem(
                        TfbiFlickDirection.RIGHT,
                        TfbiFlickDirection.RIGHT,
                        map[TfbiFlickDirection.RIGHT]?.get(TfbiFlickDirection.RIGHT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.RIGHT,
                        TfbiFlickDirection.UP_RIGHT,
                        map[TfbiFlickDirection.RIGHT]?.get(TfbiFlickDirection.UP_RIGHT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.RIGHT,
                        TfbiFlickDirection.DOWN_RIGHT,
                        map[TfbiFlickDirection.RIGHT]?.get(TfbiFlickDirection.DOWN_RIGHT).orEmpty()
                    )

                    setItem(
                        TfbiFlickDirection.UP,
                        TfbiFlickDirection.UP,
                        map[TfbiFlickDirection.UP]?.get(TfbiFlickDirection.UP).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.UP,
                        TfbiFlickDirection.UP_LEFT,
                        map[TfbiFlickDirection.UP]?.get(TfbiFlickDirection.UP_LEFT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.UP,
                        TfbiFlickDirection.UP_RIGHT,
                        map[TfbiFlickDirection.UP]?.get(TfbiFlickDirection.UP_RIGHT).orEmpty()
                    )

                    setItem(
                        TfbiFlickDirection.DOWN,
                        TfbiFlickDirection.DOWN,
                        map[TfbiFlickDirection.DOWN]?.get(TfbiFlickDirection.DOWN).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.DOWN,
                        TfbiFlickDirection.DOWN_LEFT,
                        map[TfbiFlickDirection.DOWN]?.get(TfbiFlickDirection.DOWN_LEFT).orEmpty()
                    )
                    setItem(
                        TfbiFlickDirection.DOWN,
                        TfbiFlickDirection.DOWN_RIGHT,
                        map[TfbiFlickDirection.DOWN]?.get(TfbiFlickDirection.DOWN_RIGHT).orEmpty()
                    )

                    twoStepAdapter?.submitList(currentTwoStepItems.toList())
                } else {
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

    private fun createDefaultTwoStepItems(): MutableList<TwoStepMappingItem> {
        val items = mutableListOf<TwoStepMappingItem>()

        // base
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.TAP,
                second = TfbiFlickDirection.TAP,
                output = ""
            )
        )

        // diagonals (4)
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.UP_LEFT,
                second = TfbiFlickDirection.UP_LEFT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.DOWN_LEFT,
                second = TfbiFlickDirection.DOWN_LEFT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.UP_RIGHT,
                second = TfbiFlickDirection.UP_RIGHT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.DOWN_RIGHT,
                second = TfbiFlickDirection.DOWN_RIGHT,
                output = ""
            )
        )

        // LEFT (3)
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.LEFT,
                second = TfbiFlickDirection.LEFT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.LEFT,
                second = TfbiFlickDirection.UP_LEFT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.LEFT,
                second = TfbiFlickDirection.DOWN_LEFT,
                output = ""
            )
        )

        // RIGHT (3)
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.RIGHT,
                second = TfbiFlickDirection.RIGHT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.RIGHT,
                second = TfbiFlickDirection.UP_RIGHT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.RIGHT,
                second = TfbiFlickDirection.DOWN_RIGHT,
                output = ""
            )
        )

        // UP (3)
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.UP,
                second = TfbiFlickDirection.UP,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.UP,
                second = TfbiFlickDirection.UP_LEFT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.UP,
                second = TfbiFlickDirection.UP_RIGHT,
                output = ""
            )
        )

        // DOWN (3)
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.DOWN,
                second = TfbiFlickDirection.DOWN,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.DOWN,
                second = TfbiFlickDirection.DOWN_LEFT,
                output = ""
            )
        )
        items.add(
            TwoStepMappingItem(
                first = TfbiFlickDirection.DOWN,
                second = TfbiFlickDirection.DOWN_RIGHT,
                output = ""
            )
        )

        return items
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
                isSpecial = true
                newKeyType = KeyType.NORMAL

                val selectedText = binding.keyActionSpinner.text.toString()
                val selectedDisplayAction =
                    KeyActionMapper.getDisplayActions(requireContext())
                        .firstOrNull { it.displayName == selectedText }

                newAction = selectedDisplayAction?.action
                newDrawableResId = selectedDisplayAction?.iconResId
                newLabel = if (newDrawableResId != null) "" else selectedDisplayAction?.displayName
                    ?: "ACTION"
            }

            else -> {
                isSpecial = false
                newAction = null
                newDrawableResId = null

                val isTwoStep =
                    binding.inputStyleChipGroup.checkedChipId == R.id.chip_two_step_flick

                if (!isTwoStep) {
                    newKeyType = KeyType.PETAL_FLICK
                    newLabel = binding.keyLabelEdittext.text.toString()
                    newFlickMap = currentFlickItems
                        .filter { it.output.isNotEmpty() }
                        .associate { it.direction to FlickAction.Input(it.output) }
                } else {
                    newKeyType = KeyType.TWO_STEP_FLICK

                    // Build nested map from 17 items
                    val base =
                        currentTwoStepItems.firstOrNull { it.first == TfbiFlickDirection.TAP && it.second == TfbiFlickDirection.TAP }?.output.orEmpty()
                    newLabel = base

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
            isFlickable = !isSpecial && (newKeyType != KeyType.NORMAL),
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
        twoStepAdapter = null
        _binding = null
    }
}
