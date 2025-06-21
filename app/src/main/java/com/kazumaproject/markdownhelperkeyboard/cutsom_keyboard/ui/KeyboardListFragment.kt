package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter.KeyboardLayoutAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class KeyboardListFragment : Fragment(R.layout.fragment_keyboard_list) {

    private val viewModel: KeyboardListViewModel by viewModels()

    private var _binding: FragmentKeyboardListBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            hide()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardListBinding.bind(view)

        // Adapterの初期化
        val adapter = KeyboardLayoutAdapter(
            onItemClick = { layout ->
                // エディタ画面にレイアウトIDを渡して遷移
                val action =
                    KeyboardListFragmentDirections.actionKeyboardListFragmentToKeyboardEditorFragment(
                        layout.layoutId
                    )
                findNavController().navigate(action)
            },
            onDeleteClick = { layout ->
                // 削除前に確認ダイアログを表示
                showDeleteConfirmationDialog(layout.layoutId)
            },
            onDuplicateClick = { layout ->
                // 複製機能を呼び出し
                viewModel.duplicateLayout(layout.layoutId)
            }
        )

        // RecyclerViewの設定
        binding.keyboardLayoutsRecyclerView.adapter = adapter
        binding.keyboardLayoutsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.keyboardLayoutsRecyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        // FAB（追加ボタン）のクリックリスナー
        binding.fabAddLayout.setOnClickListener {
            // エディタ画面にID「-1L」（新規作成を示す）を渡して遷移
            val action =
                KeyboardListFragmentDirections.actionKeyboardListFragmentToKeyboardEditorFragment(-1L)
            findNavController().navigate(action)
        }

        // ViewModelのレイアウトリストを監視し、変更があればAdapterに反映
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.layouts.collectLatest { layouts ->
                adapter.submitList(layouts)
            }
        }
    }

    private fun showDeleteConfirmationDialog(layoutId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(com.kazumaproject.core.R.string.dialog_delete_title))
            .setMessage(getString(com.kazumaproject.core.R.string.dialog_delete_message))
            .setNegativeButton(getString(com.kazumaproject.core.R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(com.kazumaproject.core.R.string.dialog_delete)) { _, _ ->
                viewModel.deleteLayout(layoutId)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
