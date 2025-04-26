package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_learn

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentLearnDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.learning.adapter.LearnDictionaryAdapter
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DictionaryLearnFragment : Fragment() {

    private var _binding: FragmentLearnDictionaryBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var learnRepository: LearnRepository

    lateinit var learnDictionaryAdapter: LearnDictionaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        learnDictionaryAdapter = LearnDictionaryAdapter()
        setupRecyclerView()
        setupResetButton()
        observeDictionaryData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.learnDictionaryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = learnDictionaryAdapter
        }

        learnDictionaryAdapter.setOnItemLongClickListener { input ->
            showConfirmationDialog(message = buildSpannableMessage("よみ：", input),
                positiveAction = { deleteByInput(input) })
        }

        learnDictionaryAdapter.setOnItemChildrenLongClickListener { input, output ->
            showConfirmationDialog(message = buildSpannableMessage("単語：", output),
                positiveAction = { deleteByInputAndOutput(input, output) })
        }
    }

    private fun setupResetButton() {
        binding.resetLearnDictionaryButton.setOnClickListener {
            showConfirmationDialog(message = "学習辞書を削除します。\n本当に全て削除しますか？",
                positiveAction = { deleteAll() })
        }
    }

    private fun observeDictionaryData() {
        lifecycleScope.launch {
            learnRepository.all().collectLatest { data ->
                binding.resetLearnDictionaryButton.isVisible = data.isNotEmpty()
                val transformedData = data.groupBy { it.input }.toSortedMap(compareBy { it })
                    .map { (key, value) -> key to value.map { it.out } }
                learnDictionaryAdapter.learnDataList = transformedData
            }
        }
    }

    private fun showConfirmationDialog(
        message: CharSequence, positiveAction: suspend () -> Unit
    ) {
        val dialog =
            AlertDialog.Builder(requireContext()).setTitle("削除の確認").setMessage(message)
                .setPositiveButton("はい") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        binding.progressBarLearnDictionaryFragment.isVisible = true
                        positiveAction()
                        binding.progressBarLearnDictionaryFragment.isVisible = false
                    }
                }.setNegativeButton("いいえ", null).show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.enter_key_bg))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.main_text_color))
    }

    private fun buildSpannableMessage(prefix: String, content: String): SpannableStringBuilder {
        return SpannableStringBuilder().append(prefix).append(
            content, ForegroundColorSpan(
                ContextCompat.getColor(requireContext(), R.color.enter_key_bg)
            ), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        ).append("を削除します。\n本当に辞書から削除しますか？")
    }

    private suspend fun deleteByInput(input: String) {
        learnRepository.deleteByInput(input)
    }

    private suspend fun deleteByInputAndOutput(input: String, output: String) {
        learnRepository.deleteByInputAndOutput(input = input, output = output)
    }

    private suspend fun deleteAll() {
        learnRepository.deleteAll()
    }

}
