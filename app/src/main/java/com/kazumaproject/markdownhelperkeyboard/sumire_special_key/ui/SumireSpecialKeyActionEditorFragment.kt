package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.custom_keyboard.data.DisplayAction
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSumireSpecialKeyActionEditorBinding
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyOverrideType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SumireSpecialKeyActionEditorFragment :
    Fragment(R.layout.fragment_sumire_special_key_action_editor) {
    private val viewModel: SumireSpecialKeyActionEditorViewModel by viewModels()
    private var _binding: FragmentSumireSpecialKeyActionEditorBinding? = null
    private val binding get() = _binding!!

    private val rows = mutableMapOf<SumireSpecialKeyDirection, DirectionRowViews>()
    private var selectedDirection: SumireSpecialKeyDirection = SumireSpecialKeyDirection.TAP
    private val displayActions: List<DisplayAction> by lazy {
        KeyActionMapper.getDisplayActions(requireContext())
            .filter { KeyActionMapper.fromKeyAction(it.action) != null }
    }
    private val displayActionsByString: Map<String, DisplayAction> by lazy {
        displayActions.mapNotNull { action ->
            KeyActionMapper.fromKeyAction(action.action)?.let { it to action }
        }.toMap()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSumireSpecialKeyActionEditorBinding.bind(view)
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.sumire_special_key_action_editor_title)
        setupRows()
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.resetKeyButton.setOnClickListener { viewModel.resetThisKey() }
        observeViewModel()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupRows() {
        rows.clear()
        binding.actionRowsContainer.removeAllViews()
        SumireSpecialKeyDirection.entries.forEach { direction ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 20, 16, 20)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedDirection = direction
                    renderActionList()
                    renderRows(viewModel.uiState.value)
                }
            }
            val icon = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 16
                }
                visibility = View.GONE
            }
            val textColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val title = TextView(requireContext()).apply {
                textSize = 14f
            }
            val action = TextView(requireContext()).apply {
                textSize = 18f
            }
            val status = TextView(requireContext()).apply {
                textSize = 12f
            }
            textColumn.addView(title)
            textColumn.addView(action)
            textColumn.addView(status)
            row.addView(icon)
            row.addView(textColumn)
            rows[direction] = DirectionRowViews(row, icon, title, action, status)
            binding.actionRowsContainer.addView(row)
        }
        renderActionList()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.keyIdText.text = state.keyId
                    renderRows(state)
                    if (state.navigateBack) {
                        findNavController().popBackStack()
                        viewModel.onDoneNavigating()
                    }
                }
            }
        }
    }

    private fun SumireSpecialKeyDirection.displayLabel(): String {
        return when (this) {
            SumireSpecialKeyDirection.TAP -> "Tap"
            SumireSpecialKeyDirection.UP -> "上"
            SumireSpecialKeyDirection.RIGHT -> "右"
            SumireSpecialKeyDirection.DOWN -> "下"
            SumireSpecialKeyDirection.LEFT -> "左"
        }
    }

    private fun renderRows(state: SumireSpecialKeyActionEditorUiState) {
        rows.forEach { (direction, views) ->
            val draft = state.drafts[direction] ?: SumireSpecialKeyActionDraft()
            val action = draft.actionForDisplay() ?: state.defaultActions[direction]
            val display = action?.displayAction()
            val actionName = display?.displayName ?: action?.fallbackDisplayName().orEmpty()
            views.root.isSelected = direction == selectedDirection
            views.title.text = direction.displayLabel()
            views.action.text = actionName.ifBlank { getString(R.string.sumire_special_key_no_default_action) }
            views.status.text = if (draft.overrideType == SumireSpecialKeyOverrideType.KEY_ACTION) {
                getString(R.string.sumire_special_key_override_status)
            } else {
                getString(R.string.sumire_special_key_default_status)
            }
            val iconResId = display?.iconResId
            views.icon.isVisible = iconResId != null
            if (iconResId != null) {
                views.icon.setImageResource(iconResId)
            }
        }
    }

    private fun renderActionList() {
        binding.actionListTitle.text = getString(
            R.string.sumire_special_key_action_list_title,
            selectedDirection.displayLabel()
        )
        binding.actionListContainer.removeAllViews()
        binding.actionListContainer.addView(actionListRow(
            title = getString(R.string.sumire_special_key_use_default),
            iconResId = null,
            onClick = { viewModel.setDefault(selectedDirection) }
        ))
        displayActions.forEach { displayAction ->
            binding.actionListContainer.addView(actionListRow(
                title = displayAction.displayName,
                iconResId = displayAction.iconResId,
                onClick = { viewModel.setKeyAction(selectedDirection, displayAction.action) }
            ))
        }
    }

    private fun actionListRow(
        title: String,
        iconResId: Int?,
        onClick: () -> Unit
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 18, 16, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val icon = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 16
                }
                isVisible = iconResId != null
                iconResId?.let { setImageResource(it) }
            }
            val text = TextView(requireContext()).apply {
                this.text = title
                textSize = 16f
            }
            addView(icon)
            addView(text)
        }
    }

    private fun SumireSpecialKeyActionDraft.actionForDisplay(): KeyAction? {
        if (overrideType != SumireSpecialKeyOverrideType.KEY_ACTION) return null
        return KeyActionMapper.toKeyAction(actionString)
    }

    private fun KeyAction.displayAction(): DisplayAction? {
        val actionString = KeyActionMapper.fromKeyAction(this) ?: return null
        return displayActionsByString[actionString]
    }

    private fun KeyAction.fallbackDisplayName(): String {
        return when (this) {
            is KeyAction.InputText -> text
            is KeyAction.Text -> text
            else -> KeyActionMapper.fromKeyAction(this) ?: this::class.simpleName.orEmpty()
        }
    }

    private data class DirectionRowViews(
        val root: View,
        val icon: ImageView,
        val title: TextView,
        val action: TextView,
        val status: TextView
    )
}
