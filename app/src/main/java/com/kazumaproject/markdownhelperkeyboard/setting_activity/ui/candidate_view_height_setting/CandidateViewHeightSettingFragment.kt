package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateViewHeightSettingBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.GridSpacingItemDecoration
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class CandidateViewHeightSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private lateinit var suggestionAdapter: SuggestionAdapter2
    private lateinit var candidateList: List<Candidate>

    private var _binding: FragmentCandidateViewHeightSettingBinding? = null
    private val binding get() = _binding!!

    // State to track if the candidate list is visible. Set to false for empty by default.
    private var isCandidateListVisible = false

    private val minHeightDp = 30
    private val maxHeightDp = 300
    private val defaultHeightDp = 110

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suggestionAdapter = SuggestionAdapter2()
        candidateList = (1..16).map { index ->
            Candidate(
                string = "候補 $index",
                type = (index % 4).toByte(),
                length = "候補 $index".length.toUByte(),
                score = 100 - index,
                leftId = (index * 10).toShort(),
                rightId = (index * 10 + 1).toShort()
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidateViewHeightSettingBinding.inflate(inflater, container, false)
        setupMenu()
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupResizeHandle()
        setSuggestionView()

        suggestionAdapter.apply {
            setUndoEnabled(false)
            setPasteEnabled(false)

            onListUpdated = {
                applyCurrentDimensions()
            }
        }


        binding.toggleCandidateListButton.setOnClickListener {
            isCandidateListVisible = !isCandidateListVisible
            updateCandidateListAndHeight()
        }

        binding.candidateHeightSettingTenkeyPreview.apply {
            setOnTouchListener { _, _ ->
                false
            }
        }
        // Set initial state
        updateCandidateListAndHeight()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isCandidateListVisible = false
        _binding = null
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
        // Reset height for when candidates are visible
        if (!isCandidateListVisible) {
            appPreference.candidate_view_height_dp = defaultHeightDp
        } else {
            when (appPreference.candidate_column_preference) {
                "1" -> {
                    appPreference.candidate_view_height_dp = defaultHeightDp
                }

                "2" -> {
                    appPreference.candidate_view_height_dp = 165
                }

                "3" -> {
                    appPreference.candidate_view_height_dp = 230
                }
            }
        }
        appPreference.candidate_view_empty_height_dp = defaultHeightDp
        applyCurrentDimensions()
    }

    private fun updateCandidateListAndHeight() {
        if (isCandidateListVisible) {
            suggestionAdapter.suggestions = candidateList
            binding.toggleCandidateListButton.text = "入力時"
        } else {
            suggestionAdapter.suggestions = emptyList()
            binding.toggleCandidateListButton.text = "未入力時"
        }
    }

    private fun setSuggestionView() {
        when (val columnNum = appPreference.candidate_column_preference) {
            "1" -> {
                binding.candidateHeightSettingRecyclerview.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            }

            "2", "3" -> {
                val spanCount = columnNum.toInt()
                val gridLayoutManager = GridLayoutManager(
                    requireContext(), spanCount, GridLayoutManager.HORIZONTAL, false
                )
                val spacingInPixels =
                    resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing)

                binding.candidateHeightSettingRecyclerview.layoutManager =
                    gridLayoutManager
                binding.candidateHeightSettingRecyclerview.addItemDecoration(
                    GridSpacingItemDecoration(
                        spanCount, spacingInPixels, true
                    )
                )
            }
        }
        binding.candidateHeightSettingRecyclerview.apply {
            adapter = suggestionAdapter
        }
    }

    /**
     * 保存された高さ設定をビューに適用する
     */
    private fun applyCurrentDimensions() {
        val heightPrefDp = if (isCandidateListVisible) {
            appPreference.candidate_view_height_dp ?: defaultHeightDp
        } else {
            appPreference.candidate_view_empty_height_dp ?: defaultHeightDp
        }

        val density = resources.displayMetrics.density
        val heightInPx = (heightPrefDp * density).toInt()

        val layoutParams = binding.candidateHeightSettingRecyclerview.layoutParams
        layoutParams.height = heightInPx
        binding.candidateHeightSettingRecyclerview.layoutParams = layoutParams
    }

    /**
     * 高さ変更ハンドルのタッチリスナーを設定する
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandle() {
        var initialY = 0f
        var initialHeight = 0

        val density = resources.displayMetrics.density
        val minHeightPx = minHeightDp * density
        val maxHeightPx = maxHeightDp * density

        binding.handleTop.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialHeight = binding.candidateHeightSettingRecyclerview.height
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight - deltaY).coerceIn(minHeightPx, maxHeightPx)
                    binding.candidateHeightSettingRecyclerview.layoutParams.height =
                        newHeight.toInt()
                    binding.candidateHeightSettingRecyclerview.requestLayout()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    saveHeightPreference()
                    true
                }

                else -> false
            }
        }
    }

    /**
     * 現在のビューの高さを SharedPreferences に保存する
     */
    private fun saveHeightPreference() {
        val density = resources.displayMetrics.density
        val finalHeightDp =
            (binding.candidateHeightSettingRecyclerview.height / density).roundToInt()

        if (isCandidateListVisible) {
            appPreference.candidate_view_height_dp = finalHeightDp
            Timber.d("saveHeightPreference (with candidates): $finalHeightDp dp")
        } else {
            appPreference.candidate_view_empty_height_dp = finalHeightDp
            Timber.d("saveHeightPreference (empty): $finalHeightDp dp")
        }
    }
}
