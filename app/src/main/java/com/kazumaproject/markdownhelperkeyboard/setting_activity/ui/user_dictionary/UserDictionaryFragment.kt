package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.adapter.UserWordAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserDictionaryFragment : Fragment() {

    private val viewModel: UserDictionaryViewModel by viewModels()

    private var _binding: FragmentUserDictionaryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        setupSpinner()
        resetInputFields() // 初期状態を設定
    }

    private fun setupSpinner() {
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.posList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPos.adapter = adapter
    }

    private fun setupRecyclerView() {
        val adapter = UserWordAdapter { userWord ->
            showEditDialog(userWord)
        }
        binding.recyclerViewUserWords.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddWord.setOnClickListener {
            val isCurrentlyGone = binding.cardViewAddWord.isGone
            binding.cardViewAddWord.isGone = !isCurrentlyGone

            if (binding.cardViewAddWord.isGone) {
                binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            } else {
                binding.fabAddWord.setImageResource(com.kazumaproject.core.R.drawable.remove)
                binding.editTextWord.requestFocus()
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.editTextWord, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        binding.buttonDetails.setOnClickListener {
            binding.layoutDetails.isGone = !binding.layoutDetails.isGone
            binding.buttonDetails.text = if (binding.layoutDetails.isGone) "詳細" else "隠す"
        }

        binding.buttonAdd.setOnClickListener {
            addWord()
        }
    }

    private fun observeViewModel() {
        viewModel.allWords.observe(viewLifecycleOwner) { words ->
            words?.let {
                (binding.recyclerViewUserWords.adapter as UserWordAdapter).submitList(it)
            }
        }
    }

    private fun addWord() {
        val word = binding.editTextWord.text.toString().trim()
        val reading = binding.editTextReading.text.toString().trim()

        if (word.isEmpty() || reading.isEmpty()) {
            Toast.makeText(context, "単語と読みを入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        val posIndex = binding.spinnerPos.selectedItemPosition
        val posScoreText = binding.editTextPosScore.text.toString()
        // デフォルトスコアを使用
        val posScore = posScoreText.toIntOrNull() ?: UserDictionaryViewModel.DEFAULT_SCORE

        val newUserWord =
            UserWord(word = word, reading = reading, posIndex = posIndex, posScore = posScore)
        viewModel.insert(newUserWord)

        Toast.makeText(context, "「$word」を登録しました", Toast.LENGTH_SHORT).show()

        resetInputFields()

        binding.cardViewAddWord.isGone = true
        binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
        hideKeyboardAndClearFocus()
    }

    private fun showEditDialog(userWord: UserWord) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_word, null)
        val editWord = dialogView.findViewById<EditText>(R.id.edit_text_word_dialog)
        val editReading = dialogView.findViewById<EditText>(R.id.edit_text_reading_dialog)
        val spinnerPosDialog = dialogView.findViewById<Spinner>(R.id.spinner_pos_dialog)
        val editPosScore = dialogView.findViewById<EditText>(R.id.edit_text_pos_score_dialog)

        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.posList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPosDialog.adapter = adapter

        editWord.setText(userWord.word)
        editReading.setText(userWord.reading)
        spinnerPosDialog.setSelection(userWord.posIndex)
        editPosScore.setText(userWord.posScore.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("単語の編集")
            .setView(dialogView)
            .setPositiveButton("更新") { _, _ ->
                val updatedWord = editWord.text.toString().trim()
                val updatedReading = editReading.text.toString().trim()
                val updatedPosIndex = spinnerPosDialog.selectedItemPosition
                val updatedPosScore = editPosScore.text.toString().toIntOrNull()
                    ?: UserDictionaryViewModel.DEFAULT_SCORE

                if (updatedWord.isNotEmpty() && updatedReading.isNotEmpty()) {
                    val updatedUserWord = userWord.copy(
                        word = updatedWord,
                        reading = updatedReading,
                        posIndex = updatedPosIndex,
                        posScore = updatedPosScore
                    )
                    viewModel.update(updatedUserWord)
                    Toast.makeText(context, "更新しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .setNeutralButton("削除") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("削除の確認")
                    .setMessage("「${userWord.word}」を削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        viewModel.delete(userWord.id)
                        Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            .show()
    }

    private fun hideKeyboardAndClearFocus() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    /**
     * 入力フィールドをデフォルト値にリセットする
     */
    private fun resetInputFields() {
        binding.editTextWord.text?.clear()
        binding.editTextReading.text?.clear()
        binding.editTextPosScore.setText(UserDictionaryViewModel.DEFAULT_SCORE.toString())
        binding.spinnerPos.setSelection(viewModel.defaultPosIndex)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
