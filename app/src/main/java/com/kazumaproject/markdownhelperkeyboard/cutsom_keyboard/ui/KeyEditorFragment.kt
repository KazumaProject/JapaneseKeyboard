package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.FlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class KeyEditorFragment : Fragment(R.layout.fragment_key_editor) {

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)

    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var flickAdapter: FlickMappingAdapter
    private var currentKeyData: KeyData? = null
    private var currentFlickItems = mutableListOf<FlickMappingItem>()
    private lateinit var keyActionAdapter: ArrayAdapter<String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyEditorBinding.bind(view)

        // ▼▼▼ 先にボタンを無効化 ▼▼▼
        binding.buttonDone.isEnabled = false

        setupAdapters()
        setupUIListeners()
        setupInitialState()
    }

    private fun setupAdapters() {
        flickAdapter = FlickMappingAdapter(onItemUpdated = { updatedItem ->
            val index = currentFlickItems.indexOfFirst { it.id == updatedItem.id }
            if (index != -1) {
                currentFlickItems[index] = updatedItem
            }
        })
        binding.flickMappingsRecyclerView.adapter = flickAdapter
        binding.flickMappingsRecyclerView.layoutManager = LinearLayoutManager(context)

        val actionDisplayNames = KeyActionMapper.displayActions.map { it.displayName }
        keyActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actionDisplayNames
        )
        binding.keyActionSpinner.setAdapter(keyActionAdapter)
    }

    private fun setupUIListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.buttonDone.setOnClickListener { onDone() }

        binding.keyTypeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedChipId = checkedIds.first()
            val isSpecialKey = selectedChipId == R.id.chip_special

            binding.keyLabelLayout.isVisible = !isSpecialKey
            binding.keyActionLayout.isVisible = isSpecialKey
            binding.flickEditorGroup.isVisible = !isSpecialKey

            // ▼▼▼ Chip変更時にボタンの状態を更新 ▼▼▼
            updateDoneButtonState()
        }

        // ▼▼▼ 各入力欄の変更を監視してボタンの状態を更新 ▼▼▼
        binding.keyLabelEdittext.doAfterTextChanged {
            updateDoneButtonState()
        }
        binding.keyActionSpinner.doAfterTextChanged {
            updateDoneButtonState()
        }
    }

    // ▼▼▼ 「完了」ボタンの有効/無効を切り替える関数を新設 ▼▼▼
    private fun updateDoneButtonState() {
        val isEnabled = when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                // 「特別なキー」の場合、アクションが選択されているか
                binding.keyActionSpinner.text.isNotEmpty()
            }

            R.id.chip_normal -> {
                // 「通常キー」の場合、表示ラベルが入力されているか
                binding.keyLabelEdittext.text.toString().isNotEmpty()
            }

            else -> false // 何も選択されていない場合
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
            binding.keyLabelEdittext.setText(key.label)

            if (key.isSpecialKey) {
                binding.keyTypeChipGroup.check(R.id.chip_special)
                key.action?.let { currentAction ->
                    val displayAction =
                        KeyActionMapper.displayActions.find { it.action == currentAction }
                    if (displayAction != null) {
                        binding.keyActionSpinner.setText(displayAction.displayName, false)
                    }
                }
            } else {
                binding.keyTypeChipGroup.check(R.id.chip_normal)

                val flickMap = state.layout.flickKeyMaps[key.keyId]?.firstOrNull() ?: emptyMap()
                currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    val savedAction = flickMap[direction]
                    val output = if (savedAction is FlickAction.Input) savedAction.char else ""
                    FlickMappingItem(direction = direction, output = output)
                }.toMutableList()
                flickAdapter.submitList(currentFlickItems)
            }

            // ▼▼▼ 初期状態設定後に、一度ボタンの状態を更新 ▼▼▼
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
        val newDrawableResId: Int?

        when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                isSpecial = true
                newKeyType = KeyType.NORMAL

                val selectedText = binding.keyActionSpinner.text.toString()
                val selectedDisplayAction =
                    KeyActionMapper.displayActions.firstOrNull { it.displayName == selectedText }

                Timber.d("KeyEditor: Spinner text is '$selectedText', Found action object is '$selectedDisplayAction'")

                newAction = selectedDisplayAction?.action
                newDrawableResId = selectedDisplayAction?.iconResId

                // アイコンがある場合はラベルを空にし、ない場合はアクションの表示名を表示ラベルとする
                newLabel = if (newDrawableResId != null) "" else selectedDisplayAction?.displayName
                    ?: "ACTION"
            }

            else -> { // chip_normal
                isSpecial = false
                newAction = null
                newKeyType = KeyType.PETAL_FLICK
                newDrawableResId = null
                newLabel = binding.keyLabelEdittext.text.toString()
                newFlickMap = currentFlickItems.filter { it.output.isNotEmpty() }
                    .associate { it.direction to FlickAction.Input(it.output) }
            }
        }

        val updatedKey = originalKey.copy(
            label = newLabel,
            keyType = newKeyType,
            isSpecialKey = isSpecial,
            action = newAction,
            isFlickable = !isSpecial && newFlickMap.isNotEmpty(),
            drawableResId = newDrawableResId
        )

        viewModel.updateKeyAndFlicks(updatedKey, newFlickMap)
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.doneNavigatingToKeyEditor()
        _binding = null
    }
}
