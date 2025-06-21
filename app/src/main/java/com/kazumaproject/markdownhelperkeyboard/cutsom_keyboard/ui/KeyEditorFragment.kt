package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.FlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class KeyEditorFragment : Fragment(R.layout.fragment_key_editor) {

    private val viewModel: KeyboardEditorViewModel by viewModels()

    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var flickAdapter: FlickMappingAdapter
    private var currentKeyData: KeyData? = null
    private var currentFlickItems = mutableListOf<FlickMappingItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyEditorBinding.bind(view)

        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            // 編集対象のキーのデータを一度だけ取得してUIに反映
            val state = viewModel.uiState.filterNotNull().first()
            currentKeyData =
                state.layout.keys.firstOrNull { it.keyId == state.selectedKeyIdentifier }

            if (currentKeyData == null) {
                // 万が一キーが見つからなかった場合は画面を閉じる
                findNavController().popBackStack()
                return@launch
            }

            binding.keyLabelEdittext.setText(currentKeyData!!.label)

            when (currentKeyData!!.keyType) {
                KeyType.NORMAL -> binding.keyTypeChipGroup.check(R.id.chip_normal)
                KeyType.PETAL_FLICK -> binding.keyTypeChipGroup.check(R.id.chip_petal)
                // 他のKeyTypeも同様に設定
                else -> binding.keyTypeChipGroup.check(R.id.chip_normal)
            }

            // FlickMapをAdapter用のリストに変換
            val flickMap =
                state.layout.flickKeyMaps[currentKeyData!!.keyId]?.firstOrNull() ?: emptyMap()
            currentFlickItems = flickMap.map { (direction, action) ->
                FlickMappingItem(
                    direction = direction,
                    output = if (action is FlickAction.Input) action.char else ""
                )
            }.toMutableList()
            flickAdapter.submitList(currentFlickItems)
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.buttonDone.setOnClickListener { onDone() }
    }

    private fun onDone() {
        val originalKey = currentKeyData ?: return

        val newLabel = binding.keyLabelEdittext.text.toString()
        val newKeyType = when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_petal -> KeyType.PETAL_FLICK
            else -> KeyType.NORMAL
        }

        // AdapterのリストからFlickMapを再構築
        val newFlickMap = flickAdapter.currentList.associate {
            it.direction to FlickAction.Input(it.output) as FlickAction
        }

        val updatedKey = originalKey.copy(
            label = newLabel, keyType = newKeyType, isFlickable = newKeyType != KeyType.NORMAL
        )

        viewModel.updateKeyAndFlicks(updatedKey, newFlickMap)
        findNavController().popBackStack()
    }

    private fun setupRecyclerView() {
        flickAdapter = FlickMappingAdapter(onItemUpdated = { updatedItem ->
            // アダプター内でアイテムが更新されたら、ローカルリストも更新
            val index = currentFlickItems.indexOfFirst { it.id == updatedItem.id }
            if (index != -1) {
                currentFlickItems[index] = updatedItem
            }
        }, onItemDeleted = { itemToDelete ->
            currentFlickItems.remove(itemToDelete)
            flickAdapter.submitList(currentFlickItems.toList()) // 変更をUIに反映
        })
        binding.flickMappingsRecyclerView.adapter = flickAdapter
        binding.flickMappingsRecyclerView.layoutManager = LinearLayoutManager(context)

        binding.buttonAddFlick.setOnClickListener {
            currentFlickItems.add(FlickMappingItem(direction = FlickDirection.TAP, output = ""))
            flickAdapter.submitList(currentFlickItems.toList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
