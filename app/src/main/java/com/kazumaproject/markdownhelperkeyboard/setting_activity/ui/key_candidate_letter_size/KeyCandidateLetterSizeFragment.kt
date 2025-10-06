package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.key_candidate_letter_size

import android.annotation.SuppressLint
import android.content.res.Configuration
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
import com.kazumaproject.core.domain.extensions.dpToPx
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

    private val minIconPadding = 0
    private val maxIconPadding = 64
    private val defaultIconPadding = 50

    private val maxSwitchKeyModePadding = 64
    private val defaultSwitchKeyModePadding = 24

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
        setKeyboardSize()
        setupRecyclerView()
        setupPreviewData()
        setupKeyLetterSizeSeekBar()
        setupCandidateLetterSizeSeekBar()
        setupKeyIconPaddingSeekBar()
        setupKeySwitchKeyModePaddingSeekBar()
        setupMenu()

        binding.tenkeyLetterSizePreview.apply {
            isClickable = false
            isFocusable = false
            setOnClickListener { }
            setOnLongClickListener { false }
            setOnTouchListener { _, _ -> true }
        }
    }

    private fun setKeyboardSize() {
        val heightPref = appPreference.keyboard_height ?: 280
        val widthPref = appPreference.keyboard_width ?: 280
        val density = resources.displayMetrics.density
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val screenWidth = resources.displayMetrics.widthPixels
        val positionPref = appPreference.keyboard_position ?: true
        val clampedHeight = heightPref.coerceIn(180, 420)

        val heightPx = (clampedHeight * density).toInt()

        val widthPx = when {
            widthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (widthPref / 100f)).toInt()
            }
        }
        val keyboardHeight = if (isPortrait) {
            heightPx + requireContext().dpToPx(
                appPreference.candidate_view_empty_height_dp ?: 110
            )
        } else {
            heightPx + requireContext().dpToPx(
                appPreference.candidate_view_empty_height_dp ?: 110
            )
        }

        (binding.suggestionLetterSizeRecyclerview.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { params ->
            params.width = widthPx
            if (positionPref) {
                params.startToStart = -1
                params.endToEnd =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                params.endToEnd = -1
                params.startToStart =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }

            binding.suggestionLetterSizeRecyclerview.layoutParams = params
        }

        (binding.tenkeyLetterSizePreview.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { params ->
            params.width = widthPx
            params.height = keyboardHeight
            params.bottomMargin = 56
            params.bottomToBottom =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            if (positionPref) {
                params.startToStart = -1
                params.endToEnd =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                params.endToEnd = -1
                params.startToStart =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            binding.tenkeyLetterSizePreview.layoutParams = params
        }

    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.fragment_reset_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_reset -> {
                        resetSettings()
                        true
                    }

                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
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

        appPreference.key_icon_padding = 0
        val actualPadding = defaultIconPadding
        val iconPaddingProgress =
            (100 * (maxIconPadding - actualPadding) / (maxIconPadding - minIconPadding)).toInt()
        binding.keyIconPaddingSeekbar.progress = iconPaddingProgress
        binding.tenkeyLetterSizePreview.setKeyIconPadding(actualPadding)

        appPreference.key_switch_key_mode_padding = defaultSwitchKeyModePadding
        val switchKeyModeProgress = maxSwitchKeyModePadding - defaultSwitchKeyModePadding
        binding.keySwitchKeyModePaddingSeekbar.progress = switchKeyModeProgress
        binding.tenkeyLetterSizePreview.setKeySwitchKeyModePadding(defaultSwitchKeyModePadding)
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

    /**
     * Sets up the SeekBar for adjusting key icon padding based on a delta
     * from the default value.
     */
    private fun setupKeyIconPaddingSeekBar() {
        binding.keyIconPaddingSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newPadding =
                        maxIconPadding - (progress.toFloat() / 100f) * (maxIconPadding - minIconPadding)
                    binding.tenkeyLetterSizePreview.setKeyIconPadding(newPadding.toInt())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val newPadding =
                        maxIconPadding - (it.progress.toFloat() / 100f) * (maxIconPadding - minIconPadding)
                    val paddingDelta = newPadding - defaultIconPadding
                    appPreference.key_icon_padding = paddingDelta.toInt()
                }
            }
        })

        // Configure the SeekBar's initial state
        binding.keyIconPaddingSeekbar.max = 100
        val savedDelta = appPreference.key_icon_padding ?: 0
        val actualPadding = defaultIconPadding + savedDelta

        // ★ 変更：実際のパディング値からprogressを逆算する式も反転させる
        val iconPaddingProgress =
            (100 * (maxIconPadding - actualPadding) / (maxIconPadding - minIconPadding)).toInt()
        binding.keyIconPaddingSeekbar.progress = iconPaddingProgress

        // Update the preview with the initial value
        binding.tenkeyLetterSizePreview.post {
            binding.tenkeyLetterSizePreview.setKeyIconPadding(actualPadding.toInt())
        }
    }

    /**
     * Controls padding for ONLY the keySwitchKeyMode key.
     * Saves the setting as an absolute value.
     */
    private fun setupKeySwitchKeyModePaddingSeekBar() {
        binding.keySwitchKeyModePaddingSeekbar.max = maxSwitchKeyModePadding
        binding.keySwitchKeyModePaddingSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newPadding = maxSwitchKeyModePadding - progress
                    binding.tenkeyLetterSizePreview.setKeySwitchKeyModePadding(newPadding)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val newPadding = maxSwitchKeyModePadding - it.progress
                    appPreference.key_switch_key_mode_padding = newPadding
                }
            }
        })

        val savedPadding = appPreference.key_switch_key_mode_padding ?: defaultSwitchKeyModePadding
        binding.keySwitchKeyModePaddingSeekbar.progress = maxSwitchKeyModePadding - savedPadding
        binding.tenkeyLetterSizePreview.post {
            binding.tenkeyLetterSizePreview.setKeySwitchKeyModePadding(savedPadding)
        }
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
