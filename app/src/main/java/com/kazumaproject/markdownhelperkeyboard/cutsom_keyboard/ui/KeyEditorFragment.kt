package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.FlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class KeyEditorFragment :
    Fragment(com.kazumaproject.markdownhelperkeyboard.R.layout.fragment_key_editor) {

    private val viewModel: KeyboardEditorViewModel by viewModels()
    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var flickAdapter: FlickMappingAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyEditorBinding.bind(view)

        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            // 編集対象のキーのデータを一度だけ取得してUIに反映
            val state = viewModel.uiState.filterNotNull().first()
            val keyToEdit =
                state.layout.keys.firstOrNull { it.keyId == state.selectedKeyIdentifier }

            if (keyToEdit != null) {
                binding.keyLabelEdittext.setText(keyToEdit.label)
                // TODO: flickAdapterにフリック情報をセットする
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.buttonDone.setOnClickListener {
            // TODO: UIから現在の値を取得し、KeyDataオブジェクトを作成
            // val updatedKey = ...
            // viewModel.updateKey(updatedKey)
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        flickAdapter = FlickMappingAdapter(
            onDeleteClick = { item ->
                val currentList = flickAdapter.currentList.toMutableList()
                currentList.remove(item)
                flickAdapter.submitList(currentList)
            }
        )
        binding.flickMappingsRecyclerView.adapter = flickAdapter
        binding.flickMappingsRecyclerView.layoutManager = LinearLayoutManager(context)

        binding.buttonAddFlick.setOnClickListener {
            val currentList = flickAdapter.currentList.toMutableList()
            // 新しい空の項目を追加
            currentList.add(FlickMappingItem(direction = FlickDirection.TAP, output = ""))
            flickAdapter.submitList(currentList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
