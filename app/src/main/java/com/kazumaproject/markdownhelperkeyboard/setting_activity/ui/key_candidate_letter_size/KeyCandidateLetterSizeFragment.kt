package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.key_candidate_letter_size

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyCandidateLetterSizeBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyCandidateLetterSizeFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyCandidateLetterSizeBinding? = null
    private val binding get() = _binding!!
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val minKeyTextSize = 12f
    private val maxKeyTextSize = 40f
    private val minCandidateTextSize = 10f
    private val maxCandidateTextSize = 40f
    private val defaultKeyTextSize = 17.0f
    private val defaultCandidateTextSize = 14.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suggestionAdapter = SuggestionAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyCandidateLetterSizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupPreviewData()
        setupKeyLetterSizeSeekBar()
        setupCandidateLetterSizeSeekBar()
        setupMenu() // ★ Call the new menu setup function

        binding.tenkeyLetterSizePreview.apply {
            isClickable = false
            isFocusable = false
            setOnClickListener { }
            setOnLongClickListener { false }
            setOnTouchListener { _, _ -> true }
        }
    }

    // ★ New function to set up the menu using the modern API
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.fragment_reset_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.action_reset -> {
                        resetSettings()
                        true // Consume the event
                    }

                    else -> false // Let the system handle other items
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun resetSettings() {
        // Reset key letter size
        appPreference.key_letter_size = 0.0f
        val keyProgress =
            (100 * (defaultKeyTextSize - minKeyTextSize) / (maxKeyTextSize - minKeyTextSize)).toInt()
        binding.keyLetterSizeSeekbar.progress = keyProgress
        binding.tenkeyLetterSizePreview.setKeyLetterSize(defaultKeyTextSize)

        // Reset candidate letter size
        appPreference.candidate_letter_size = defaultCandidateTextSize
        val candidateProgress =
            (100 * (defaultCandidateTextSize - minCandidateTextSize) / (maxCandidateTextSize - minCandidateTextSize)).toInt()
        binding.candidateLetterSizeSeekbar.progress = candidateProgress
        suggestionAdapter.setCandidateTextSize(defaultCandidateTextSize)
    }

    private fun setupKeyLetterSizeSeekBar() {
        binding.keyLetterSizeSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newSize =
                        minKeyTextSize + (progress.toFloat() / 100f) * (maxKeyTextSize - minKeyTextSize)
                    binding.tenkeyLetterSizePreview.setKeyLetterSize(newSize)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val newSize =
                        minKeyTextSize + (it.progress.toFloat() / 100f) * (maxKeyTextSize - minKeyTextSize)
                    val sizeDelta = newSize - defaultKeyTextSize
                    appPreference.key_letter_size = sizeDelta
                }
            }
        })

        binding.keyLetterSizeSeekbar.max = 100
        val savedDelta = appPreference.key_letter_size ?: 0.0f
        val actualSize = defaultKeyTextSize + savedDelta
        val keyProgress =
            (100 * (actualSize - minKeyTextSize) / (maxKeyTextSize - minKeyTextSize)).toInt()
        binding.keyLetterSizeSeekbar.progress = keyProgress
        binding.tenkeyLetterSizePreview.post {
            binding.tenkeyLetterSizePreview.setKeyLetterSize(actualSize)
        }
    }

    private fun setupCandidateLetterSizeSeekBar() {
        binding.candidateLetterSizeSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newSize =
                        minCandidateTextSize + (progress.toFloat() / 100f) * (maxCandidateTextSize - minCandidateTextSize)
                    suggestionAdapter.setCandidateTextSize(newSize)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val newSize =
                        minCandidateTextSize + (it.progress.toFloat() / 100f) * (maxCandidateTextSize - minCandidateTextSize)
                    appPreference.candidate_letter_size = newSize
                }
            }
        })

        binding.candidateLetterSizeSeekbar.max = 100
        val savedCandidateSize = appPreference.candidate_letter_size ?: defaultCandidateTextSize
        val candidateProgress =
            (100 * (savedCandidateSize - minCandidateTextSize) / (maxCandidateTextSize - minCandidateTextSize)).toInt()
        binding.candidateLetterSizeSeekbar.progress = candidateProgress
        suggestionAdapter.setCandidateTextSize(savedCandidateSize)
    }

    private fun setupRecyclerView() {
        binding.suggestionLetterSizeRecyclerview.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = suggestionAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupPreviewData() {
        val previewCandidates = listOf(
            Candidate(
                string = "プレビュー",
                type = 1.toByte(),
                length = (5).toUByte(),
                score = 4000
            ),
            Candidate(
                string = "文字サイズ",
                type = 1.toByte(),
                length = (5).toUByte(),
                score = 4001
            ),
            Candidate(string = "候補", type = 1.toByte(), length = (2).toUByte(), score = 4002)
        )
        suggestionAdapter.suggestions = previewCandidates
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.suggestionLetterSizeRecyclerview.adapter = null
        _binding = null
    }
}
