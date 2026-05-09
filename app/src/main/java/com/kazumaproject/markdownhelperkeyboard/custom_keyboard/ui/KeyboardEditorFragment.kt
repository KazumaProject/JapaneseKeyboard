package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.GridSpan
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.HalfRowPlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionPolicy
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.KeyboardEditorMode
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.NudgeDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view.EditableFlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class KeyboardEditorFragment : Fragment(R.layout.fragment_keyboard_editor),
    EditableFlickKeyboardView.OnKeyEditListener {

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)
    private val args: KeyboardEditorFragmentArgs by navArgs()

    private var _binding: FragmentKeyboardEditorBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardEditorBinding.bind(view)
        setupToolbarAndMenu()
        viewModel.start(args.layoutId)
        setupUIListeners()
        observeViewModel()
    }

    private fun setupToolbarAndMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.edit_keyboard)
            setDisplayHomeAsUpEnabled(true)
        }
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_keyboard_editor, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        viewModel.onCancelEditing()
                        true
                    }

                    R.id.action_save -> {
                        viewModel.saveLayout()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupUIListeners() {
        binding.flickKeyboardView.setOnKeyEditListener(this)
        binding.keyboardNameEdittext.doAfterTextChanged { text ->
            if (text.toString() != viewModel.uiState.value.name) {
                viewModel.updateName(text.toString())
            }
        }
        binding.switchRomaji.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != viewModel.uiState.value.isRomaji) {
                viewModel.updateIsRomaji(isChecked)
            }
        }
        binding.switchDirectMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != viewModel.uiState.value.isDirectMode) {
                viewModel.updateIsDirectMode(isChecked)
            }
        }
        binding.switchUsageModeNumber.setOnCheckedChangeListener { _, isChecked ->
            val usageMode = if (isChecked) {
                KeyboardLayoutUsageMode.Number
            } else {
                KeyboardLayoutUsageMode.Normal
            }
            if (usageMode != viewModel.uiState.value.layout.usageMode) {
                viewModel.setCurrentLayoutUsageMode(viewModel.uiState.value.layoutId, usageMode)
            }
        }
        binding.buttonAddRow.setOnClickListener { viewModel.addRow() }
        binding.buttonRemoveRow.setOnClickListener { viewModel.removeRow() }
        binding.buttonAddCol.setOnClickListener { viewModel.addColumn() }
        binding.buttonRemoveCol.setOnClickListener { viewModel.removeColumn() }
        binding.insertDirectionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.button_insert_direction_row ->
                    viewModel.updateInsertionPolicy(InsertionPolicy.PreferHorizontal)
                R.id.button_insert_direction_column ->
                    viewModel.updateInsertionPolicy(InsertionPolicy.PreferVertical)
            }
        }
        binding.halfRowPlacementGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.button_half_row_upper -> viewModel.setHalfRowPlacement(HalfRowPlacement.Upper)
                R.id.button_half_row_lower -> viewModel.setHalfRowPlacement(HalfRowPlacement.Lower)
            }
        }
        binding.buttonPlaceHalfKey.setOnClickListener {
            viewModel.enterNewKeyPlacementMode(
                GridSpan(rowSpanUnits = 1, columnSpanUnits = 1),
                policy = viewModel.uiState.value.insertionPolicy
            )
        }
        binding.buttonPlaceOneKey.setOnClickListener {
            viewModel.enterNewKeyPlacementMode(
                GridSpan(rowSpanUnits = 2, columnSpanUnits = 2),
                policy = viewModel.uiState.value.insertionPolicy
            )
        }
        binding.buttonPlaceHalfSpacer.setOnClickListener {
            viewModel.enterSpacerPlacementMode(
                GridSpan(rowSpanUnits = 1, columnSpanUnits = 1),
                policy = viewModel.uiState.value.insertionPolicy
            )
        }
        binding.buttonPlaceOneSpacer.setOnClickListener {
            viewModel.enterSpacerPlacementMode(
                GridSpan(rowSpanUnits = 2, columnSpanUnits = 2),
                policy = viewModel.uiState.value.insertionPolicy
            )
        }
        binding.buttonConfirmPlacement.setOnClickListener {
            viewModel.confirmPlacementPreview()
        }
        binding.buttonCancelPlacement.setOnClickListener {
            viewModel.cancelPlacementPreview()
        }
        binding.buttonDeleteSelectedItem.setOnClickListener {
            viewModel.deleteSelectedItem()
        }
        binding.buttonNudgeLeft.setOnClickListener {
            viewModel.nudgePlacementCursor(NudgeDirection.Left)
        }
        binding.buttonNudgeRight.setOnClickListener {
            viewModel.nudgePlacementCursor(NudgeDirection.Right)
        }
        binding.buttonNudgeUp.setOnClickListener {
            viewModel.nudgePlacementCursor(NudgeDirection.Up)
        }
        binding.buttonNudgeDown.setOnClickListener {
            viewModel.nudgePlacementCursor(NudgeDirection.Down)
        }
        binding.buttonCycleTarget.setOnClickListener {
            viewModel.cyclePlacementCursorTarget()
        }

        // ▼▼▼ ここから追加 ▼▼▼
        binding.buttonSelectTemplate.setOnClickListener {
            showTemplateSelectionDialog()
        }
        // ▲▲▲ ここまで追加 ▲▲▲
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                        if (state.navigateBack) {
                            findNavController().popBackStack()
                            viewModel.onDoneNavigating()
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.duplicateNameError) {
                            showDuplicateNameDialog()
                            viewModel.clearDuplicateNameError()
                        }
                    }
                }
            }
        }
    }

    private fun showDuplicateNameDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("キーボード名が重複しています")
            .setMessage("別のキーボード名に修正してください。")
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // ▼▼▼ ここから追加 ▼▼▼
    private fun showTemplateSelectionDialog() {
        val templates = viewModel.availableTemplates
        val templateNames = templates.map { getString(it.nameResId) }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("テンプレートを選択")
            .setItems(templateNames) { dialog, which ->
                // 選択されたテンプレートを取得
                val selectedTemplate = templates[which]
                Timber.d("Template selected: ${getString(selectedTemplate.nameResId)}")
                // ViewModelにテンプレートの適用を指示
                viewModel.applyTemplate(selectedTemplate.layout)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    // ▲▲▲ ここまで追加 ▲▲▲

    private fun updateUi(state: EditorUiState) {
        if (state.isLoading) {
            binding.editModePanel.isVisible = false
            return
        }
        binding.editModePanel.isVisible = true
        if (binding.keyboardNameEdittext.text.toString() != state.name) {
            binding.keyboardNameEdittext.setText(state.name)
            binding.keyboardNameEdittext.setSelection(state.name.length)
        }
        if (binding.switchRomaji.isChecked != state.isRomaji) {
            binding.switchRomaji.isChecked = state.isRomaji
        }
        if (binding.switchDirectMode.isChecked != state.isDirectMode) {
            binding.switchDirectMode.isChecked = state.isDirectMode
        }
        val isNumberUsageMode = state.layout.usageMode == KeyboardLayoutUsageMode.Number
        if (binding.switchUsageModeNumber.isChecked != isNumberUsageMode) {
            binding.switchUsageModeNumber.isChecked = isNumberUsageMode
        }
        applyKeyboardEditorCapabilityVisibility(
            binding = binding,
            capabilities = keyboardEditorCapabilities(state.layout)
        )
        val insertionDirectionButtonId = when (state.insertionPolicy) {
            InsertionPolicy.PreferVertical -> R.id.button_insert_direction_column
            else -> R.id.button_insert_direction_row
        }
        if (binding.insertDirectionGroup.checkedButtonId != insertionDirectionButtonId) {
            binding.insertDirectionGroup.check(insertionDirectionButtonId)
        }
        val addModeButtonId = placementAddModeButtonId(state.editorMode)
        if (addModeButtonId == View.NO_ID) {
            binding.placementSizeControlsGroup.clearChecked()
        } else if (binding.placementSizeControlsGroup.checkedButtonId != addModeButtonId) {
            binding.placementSizeControlsGroup.check(addModeButtonId)
        }
        binding.halfRowPlacementPanel.isVisible = shouldShowHalfRowPlacementControls(
            state = state,
            capabilities = keyboardEditorCapabilities(state.layout)
        )
        val halfRowButtonId = when (state.halfRowPlacement) {
            HalfRowPlacement.Upper -> R.id.button_half_row_upper
            HalfRowPlacement.Lower -> R.id.button_half_row_lower
        }
        if (binding.halfRowPlacementGroup.checkedButtonId != halfRowButtonId) {
            binding.halfRowPlacementGroup.check(halfRowButtonId)
        }
        val isPlacementMode = state.editorMode != KeyboardEditorMode.Normal
        val displayLayout = state.previewLayout ?: state.layout
        binding.flickKeyboardView.setKeyboard(
            layout = displayLayout,
            placementMode = isPlacementMode,
            placementCursor = state.placementCursor,
            insertionPolicy = state.placementCursor?.policy ?: state.insertionPolicy,
            selectedItemId = state.selectedItemId,
            previewInsertedItemId = state.previewInsertedItemId,
            previewMovedItemIds = state.previewMovedItemIds
        )
        binding.buttonConfirmPlacement.isEnabled = state.previewLayout != null
        binding.buttonCancelPlacement.isEnabled = isPlacementMode
        applyKeyboardEditorDeleteSelectionState(binding, state, isPlacementMode)
        binding.buttonNudgeLeft.isEnabled = isPlacementMode && state.placementCursor != null
        binding.buttonNudgeRight.isEnabled = isPlacementMode && state.placementCursor != null
        binding.buttonNudgeUp.isEnabled = isPlacementMode && state.placementCursor != null
        binding.buttonNudgeDown.isEnabled = isPlacementMode && state.placementCursor != null
        binding.buttonCycleTarget.isEnabled = isPlacementMode && state.placementCursor != null
    }

    override fun onKeySelected(keyId: String) {
        Timber.d("onKeySelected: keyId = $keyId")
        if (viewModel.onKeyTappedForSelectionOrEdit(keyId)) {
            findNavController().navigate(R.id.action_keyboardEditorFragment_to_keyEditorFragment)
        }
    }

    override fun onSpacerSelected(spacerId: String) {
        Timber.d("onSpacerSelected: spacerId = $spacerId")
        viewModel.onSpacerTapped(spacerId)
    }

    override fun onKeysSwapped(draggedKeyId: String, targetKeyId: String) {
        Timber.d("onKeysSwapped: dragged=$draggedKeyId, target=$targetKeyId")
        viewModel.swapKeys(draggedKeyId, targetKeyId)
    }

    override fun onRowDeleted(rowIndex: Int) {
        Timber.d("onRowDeleted: rowIndex = $rowIndex")
        viewModel.deleteRowAt(rowIndex)
    }

    override fun onColumnDeleted(columnIndex: Int) {
        Timber.d("onColumnDeleted: columnIndex = $columnIndex")
        viewModel.deleteColumnAt(columnIndex)
    }

    override fun onPlacementPointerTarget(target: InsertionTarget) {
        viewModel.updatePlacementCursorFromPointer(target)
    }

    override fun onPlacementTapTarget(target: InsertionTarget) {
        viewModel.holdPlacementCursorFromTap(target)
    }

    override fun onPlacementDropTarget(target: InsertionTarget) {
        viewModel.holdPlacementCursorFromDrop(target)
    }

    private fun readSpacerPlacement(
        rowEdit: EditText,
        columnEdit: EditText,
        widthEdit: EditText,
        heightEdit: EditText
    ): GridPlacement? {
        val rowUnits = halfCellUnits(rowEdit.text.toString()) ?: return null
        val columnUnits = halfCellUnits(columnEdit.text.toString()) ?: return null
        val columnSpanUnits = halfCellUnits(widthEdit.text.toString()) ?: return null
        val rowSpanUnits = halfCellUnits(heightEdit.text.toString()) ?: return null
        return GridPlacement(rowUnits, columnUnits, rowSpanUnits, columnSpanUnits)
    }

    private fun halfCellUnits(value: String): Int? {
        val parsed = value.toFloatOrNull() ?: return null
        val unitsFloat = parsed * 2f
        val units = kotlin.math.round(unitsFloat).toInt()
        if (kotlin.math.abs(unitsFloat - units) > 0.001f) return null
        return units
    }

    private fun decimalEditText(value: String): EditText {
        return EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setText(value)
        }
    }

    private fun labeledView(label: String, child: View): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = label })
            addView(child)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearSelectedItemForDeletion()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = null
            setDisplayHomeAsUpEnabled(false)
        }
        binding.flickKeyboardView.removeOnKeyEditListener()
        _binding = null
    }
}

internal fun applyKeyboardEditorCapabilityVisibility(
    binding: FragmentKeyboardEditorBinding,
    capabilities: KeyboardEditorCapabilities
) {
    binding.placementSizeControlsGroup.isVisible = capabilities.showHalfCellControls
    binding.insertDirectionPanel.isVisible = capabilities.showInsertionDirectionControls
    binding.rowControlsGroup.isVisible = capabilities.showGridStructuralControls
    binding.columnControlsGroup.isVisible = capabilities.showGridStructuralControls
}

internal fun placementAddModeButtonId(editorMode: KeyboardEditorMode): Int {
    return when (editorMode) {
        KeyboardEditorMode.Normal -> View.NO_ID
        is KeyboardEditorMode.MovingExistingItem -> View.NO_ID
        is KeyboardEditorMode.PlacingNewKey -> when (editorMode.span) {
            GridSpan(rowSpanUnits = 1, columnSpanUnits = 1) -> R.id.button_place_half_key
            GridSpan(rowSpanUnits = 2, columnSpanUnits = 2) -> R.id.button_place_one_key
            else -> View.NO_ID
        }
        is KeyboardEditorMode.PlacingSpacer -> when (editorMode.span) {
            GridSpan(rowSpanUnits = 1, columnSpanUnits = 1) -> R.id.button_place_half_spacer
            GridSpan(rowSpanUnits = 2, columnSpanUnits = 2) -> R.id.button_place_one_spacer
            else -> View.NO_ID
        }
    }
}

internal fun shouldShowHalfRowPlacementControls(
    state: EditorUiState,
    capabilities: KeyboardEditorCapabilities
): Boolean {
    if (!capabilities.showHalfCellControls) return false
    val halfCellSpan = GridSpan(rowSpanUnits = 1, columnSpanUnits = 1)
    return when (val mode = state.editorMode) {
        is KeyboardEditorMode.PlacingNewKey ->
            mode.span == halfCellSpan && mode.policy == InsertionPolicy.PreferHorizontal
        is KeyboardEditorMode.PlacingSpacer ->
            mode.span == halfCellSpan && mode.policy == InsertionPolicy.PreferHorizontal
        KeyboardEditorMode.Normal,
        is KeyboardEditorMode.MovingExistingItem -> false
    }
}

internal fun applyKeyboardEditorDeleteSelectionState(
    binding: FragmentKeyboardEditorBinding,
    state: EditorUiState,
    isPlacementMode: Boolean
) {
    binding.buttonDeleteSelectedItem.isEnabled = !isPlacementMode && state.hasDeletableSelection()
}

internal fun EditorUiState.hasDeletableSelection(): Boolean {
    val selectedId = selectedItemId ?: return false
    return layout.items.any { item ->
        item.id == selectedId || (item is KeyItem && item.keyData.keyId == selectedId)
    }
}
