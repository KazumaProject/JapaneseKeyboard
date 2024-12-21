package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_learn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentLearnDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.learning.adapter.LearnDictionaryAdapter
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DictionaryLearnFragment : Fragment() {

    private var _binding: FragmentLearnDictionaryBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var learnRepository: LearnRepository

    @Inject
    lateinit var learnDictionaryAdapter: LearnDictionaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.learnDictionaryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = learnDictionaryAdapter
        }
        binding.resetLearnDictionaryButton.setOnClickListener {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("削除の確認")
                .setMessage("学習辞書を削除します。\n本当に全て削除しますか？")
                .setPositiveButton("はい") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        learnRepository.deleteAll()
                    }
                }
                .setNegativeButton("いいえ", null)
                .show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.enter_key_bg))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.main_text_color))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                learnRepository.all().collectLatest { data ->
                    binding.resetLearnDictionaryButton.isVisible = data.isNotEmpty()
                    println("Dictionary data: $data")
                    val transformedData = data
                        .groupBy { it.input }
                        .toSortedMap(compareBy { it })
                        .map { (key, value) ->
                            key to value.map { it.out }
                        }
                    println("Dictionary data transformed: $transformedData")
                    learnDictionaryAdapter.learnDataList = transformedData
                }
            }
        }
    }

}