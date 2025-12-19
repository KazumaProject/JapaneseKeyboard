package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.adapter.UserWordAdapter
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream

@AndroidEntryPoint
class UserDictionaryFragment : Fragment() {

    private val viewModel: UserDictionaryViewModel by viewModels()

    private var _binding: FragmentUserDictionaryBinding? = null
    private val binding get() = _binding!!

    // Export
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportWords(uri)
                }
            }
        }

    // Import
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importWords(uri)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        setupSpinner()
        resetInputFields()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.user_dictionary_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // [CHANGE] メニューからのトグルロジックはここには含めない
                return when (menuItem.itemId) {
                    R.id.action_export -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import -> {
                        launchImportFilePicker()
                        true
                    }

                    R.id.action_other_dict_import ->{
                        showOtherDictImportDialog()
                        true
                    }

                    R.id.action_delete_all -> {
                        showDeleteAllConfirmationDialog()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Set the type to plain text and the extension to .txt
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "user_dictionary_backup.txt")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_all_words_delete_message))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(
                    context,
                    getString(R.string.deleted_all_words_text),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .show()
    }

    private fun exportWords(uri: Uri) {
        viewModel.allWords.value?.let { words ->
            if (words.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.there_is_no_word_to_export),
                    Toast.LENGTH_SHORT
                )
                    .show()
                return
            }
            try {
                // Convert the list of words to a plain JSON string
                val jsonString = Gson().toJson(words)

                // Write the plain JSON string to the output file
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(
                    context,
                    getString(R.string.success_to_export_string),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    getString(R.string.fail_to_export_string),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private enum class OtherDictFormat { AUTO, GOOGLE_JP_INPUT, MICROSOFT_IME }

    private var pendingOtherDictFormat: OtherDictFormat = OtherDictFormat.AUTO

    private val otherDictImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importOtherDict(uri, pendingOtherDictFormat)
                }
            }
        }

    private fun readTextWithCharsetGuess(uri: Uri): String {
        val bytes = requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }

        fun startsWith(vararg sig: Int): Boolean {
            if (bytes.size < sig.size) return false
            for (i in sig.indices) if (bytes[i].toInt() and 0xFF != sig[i]) return false
            return true
        }

        val charset = when {
            startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8                 // UTF-8 BOM
            startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE                     // UTF-16 LE BOM
            startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE                     // UTF-16 BE BOM
            // NUL混入が目立つならUTF-16系の可能性が高い
            bytes.count { it == 0.toByte() } > bytes.size / 10 -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }

        // UTF-8でコケる/文字化けがひどい端末用に Shift_JIS をフォールバック
        return try {
            String(bytes, charset)
        } catch (_: Exception) {
            String(bytes, charset("Shift_JIS"))
        }
    }

    private fun showOtherDictImportDialog() {
        val items = arrayOf("自動判定", "Google日本語入力", "Microsoft IME")
        AlertDialog.Builder(requireContext())
            .setTitle("辞書形式を選択")
            .setItems(items) { _, which ->
                pendingOtherDictFormat = when (which) {
                    1 -> OtherDictFormat.GOOGLE_JP_INPUT
                    2 -> OtherDictFormat.MICROSOFT_IME
                    else -> OtherDictFormat.AUTO
                }
                launchOtherDictFilePicker()
            }
            .show()
    }

    private fun importOtherDict(uri: Uri, format: OtherDictFormat) {
        try {
            val text = readTextWithCharsetGuess(uri)
            val words = parseOtherDictText(text, format)

            if (words.isEmpty()) {
                Toast.makeText(context, "辞書エントリが見つかりませんでした", Toast.LENGTH_LONG).show()
                return
            }

            viewModel.insertAll(words)
            Toast.makeText(context, "${words.size} 件インポートしました", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, getString(R.string.fail_to_import_string), Toast.LENGTH_LONG).show()
        }
    }

    private fun parseOtherDictText(text: String, format: OtherDictFormat): List<UserWord> {
        val lines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split('\n')

        val out = ArrayList<UserWord>(lines.size)
        val seen = HashSet<String>(lines.size)

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // たまにヘッダ/説明が混じるケースの保険
            if (line.startsWith("#")) continue
            if (line.contains("Microsoft IME", ignoreCase = true) && line.count { it == '\t' } < 1) continue

            val cols = line.split('\t')
            if (cols.size < 2) continue

            var reading = cols[0].trim()
            var word = cols[1].trim()
            if (reading.isEmpty() || word.isEmpty()) continue

            // AUTOの場合は軽いヒューリスティックで入れ替え（任意だが安全性が上がる）
            if (format == OtherDictFormat.AUTO) {
                // もし「reading側に漢字が多く、word側がひらがなっぽい」なら入れ替え
                val readingLooksKanji = reading.any { it in '\u4E00'..'\u9FFF' }
                val wordLooksKana = word.all { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it == 'ー' }
                if (readingLooksKanji && wordLooksKana) {
                    val tmp = reading
                    reading = word
                    word = tmp
                }
            }

            val key = "$reading\t$word"
            if (!seen.add(key)) continue

            out.add(
                UserWord(
                    id = 0,
                    word = word,          // 単語（表記）
                    reading = reading,    // よみ
                    posIndex = 0,
                    posScore = 5000
                )
            )
        }

        return out
    }

    private fun launchOtherDictFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            // 端末によっては text/* だと出ないことがあるので保険で
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/tab-separated-values", "text/*"))
        }
        otherDictImportLauncher.launch(intent)
    }

    private fun importWords(uri: Uri) {
        try {
            // Read the bytes from the input file and convert to a string
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                it.reader(Charsets.UTF_8).readText()
            }

            if (jsonString != null) {
                // Convert the JSON string back to a list of UserWord objects
                val type = object : TypeToken<List<UserWord>>() {}.type
                val words: List<UserWord> = Gson().fromJson(jsonString, type)

                // Insert the words into the database
                viewModel.insertAll(words.map {
                    UserWord(
                        id = 0,
                        word = it.word,
                        reading = it.reading,
                        posIndex = it.posIndex,
                        posScore = it.posScore
                    )
                })
                Toast.makeText(
                    context,
                    "${words.size} ${getString(R.string.import_text_string)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                getString(R.string.fail_to_import_string),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupSpinner() {
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.posList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun setupRecyclerView() {
        val adapter = UserWordAdapter { userWord ->
            showEditDialog(userWord)
        }
        binding.recyclerViewUserWords.adapter = adapter
    }

    private fun setupListeners() {
        // [CHANGE 1 START] FABのリスナーを修正
        binding.fabAddWord.setOnClickListener {
            val isCurrentlyGone = binding.cardViewAddWord.isGone
            binding.cardViewAddWord.isGone = !isCurrentlyGone

            // CardViewが表示される場合
            if (!binding.cardViewAddWord.isGone) {
                // 説明文を隠す
                binding.layoutExplanation.isGone = true

                binding.fabAddWord.setImageResource(com.kazumaproject.core.R.drawable.remove)
                binding.editTextWord.requestFocus()
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.editTextWord, InputMethodManager.SHOW_IMPLICIT)
            } else { // CardViewが隠される場合
                binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            }
        }
        // [CHANGE 1 END]

        binding.buttonAdd.setOnClickListener {
            addWord()
        }

        // [CHANGE 2 START] ヘルプアイコンボタンのリスナーを新規追加
        binding.buttonToggleExplanation.setOnClickListener {
            // 説明文の表示/非表示を切り替える
            binding.layoutExplanation.isGone = !binding.layoutExplanation.isGone

            // もし説明文が表示されたら、単語追加CardViewを隠す
            if (!binding.layoutExplanation.isGone) {
                binding.cardViewAddWord.isGone = true
                binding.fabAddWord.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            }
        }
        // [CHANGE 2 END]
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
            Toast.makeText(
                context,
                getString(R.string.enter_word_and_yomi_string),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val newUserWord =
            UserWord(
                word = word,
                reading = reading,
                posIndex = viewModel.defaultPosIndex,
                posScore = 4000
            )
        viewModel.insert(newUserWord)

        Toast.makeText(
            context,
            "「$word${getString(R.string.is_registered_string)}",
            Toast.LENGTH_SHORT
        ).show()

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
            .setTitle(getString(R.string.edit_word))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_string)) { _, _ ->
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
                    Toast.makeText(context, getString(R.string.updated_string), Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .setNeutralButton(getString(R.string.delete_string)) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.confirm_delete_title))
                    .setMessage("「${userWord.word}」${getString(R.string.confirm_to_delete_text)}")
                    .setPositiveButton(getString(R.string.delete_string)) { _, _ ->
                        viewModel.delete(userWord.id)
                        Toast.makeText(
                            context,
                            getString(R.string.deleted_string),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(getString(R.string.cancel_string), null)
                    .show()
            }
            .show()
    }

    private fun hideKeyboardAndClearFocus() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    private fun resetInputFields() {
        binding.editTextWord.text?.clear()
        binding.editTextReading.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
