package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_landscape_setting

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateHeightLandscapeSettingBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.CandidateStripPresentationPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.CandidateStripPresentationState
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.CandidateHeightDefaultsFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.CandidateKeyboardPreviewViews
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.CandidateHeightPreviewGridSpacingDecoration
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.SuggestionAdapter2
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.candidateKeyboardPreviewHeightPx
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.clearItemDecorations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.createCandidateHeightPreviewCandidates
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.renderCandidateKeyboardPreview
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class CandidateHeightLandscapeSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    @Inject
    lateinit var sumireSpecialKeyRepository: SumireSpecialKeyRepository

    private lateinit var suggestionAdapter: SuggestionAdapter2

    private var _binding: FragmentCandidateHeightLandscapeSettingBinding? = null
    private val binding get() = _binding!!

    private var isCandidateListVisible = true
    private var isSyncingHeightControls = false
    private var isSyncingLetterSizeControls = false
    private var isSyncingColumnControls = false
    private var isSyncingPreviewModeControls = false
    private lateinit var inspectorBehavior: BottomSheetBehavior<MaterialCardView>

    private val minHeightDp = 30
    private val maxHeightDp = 300
    private val minCandidateTextSize = 10f
    private val maxCandidateTextSize = 40f
    private val defaultCandidateTextSize = 14.0f

    private val previewCandidates = createCandidateHeightPreviewCandidates()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suggestionAdapter = SuggestionAdapter2()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding =
            FragmentCandidateHeightLandscapeSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appPreference.migrateCandidateHeightPerColumnPreferencesIfNeeded()
        appPreference.syncActiveCandidateVisibleHeightToImePreference(isLandscape = true)

        setupMenu()
        setupAdapter()
        suggestionAdapter.setShowDictionaryCandidateLabels(
            appPreference.show_dictionary_candidate_labels_preference
        )
        setupInspectorBottomSheet()
        setupPreviewModeControls()
        setupColumnControls()
        setupResizeHandle()
        setupHeightSeekBar()
        setupHeightEditText()
        setupCandidateLetterSizeSeekBar()
        setupCandidateLetterSizeEditText()
        setupKeyboardPreview()
        setSuggestionView()

        applyCandidateTextSize(appPreference.candidate_letter_size ?: defaultCandidateTextSize, persist = false)
        updateCandidateListAndHeight()
        applyHeightDp(selectedHeightDp(), persist = false)
        updateInspectorSummary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.candidateHeightSettingRecyclerview.adapter = null
        suggestionAdapter.release()
        _binding = null
    }

    private fun setupMenu() {
        binding.toolbar.setNavigationIcon(AppCompatR.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbar.inflateMenu(R.menu.fragment_reset_menu)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_candidate_default_height -> {
                    findNavController().navigate(
                        R.id.candidateHeightDefaultsFragment,
                        bundleOf(CandidateHeightDefaultsFragment.ARG_IS_LANDSCAPE to true)
                    )
                    true
                }

                R.id.action_reset -> {
                    resetSettings()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupInspectorBottomSheet() {
        inspectorBehavior = BottomSheetBehavior.from(binding.inspectorBottomSheet)
        inspectorBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.toggleInspectorButton.setOnClickListener {
            inspectorBehavior.state = if (
                inspectorBehavior.state == BottomSheetBehavior.STATE_COLLAPSED
            ) {
                BottomSheetBehavior.STATE_HALF_EXPANDED
            } else {
                BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        inspectorBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    updateInspectorToggleLabel(newState)
                    updatePreviewBottomMargin()
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    updatePreviewBottomMargin()
                }
            }
        )
        binding.root.doOnLayout {
            updatePreviewBottomMargin()
        }
        updateInspectorToggleLabel(inspectorBehavior.state)
    }

    /** Keep the preview above the visible Bottom Sheet instead of underneath it. */
    private fun updatePreviewBottomMargin() {
        if (!::inspectorBehavior.isInitialized || binding.previewCanvas.height <= 0) return

        val bottomMargin = (binding.previewCanvas.bottom - binding.inspectorBottomSheet.top)
            .coerceAtLeast(0)
        val layoutParams = binding.candidateHeightSettingContent.layoutParams
            as? FrameLayout.LayoutParams ?: return
        if (layoutParams.bottomMargin == bottomMargin) return

        layoutParams.bottomMargin = bottomMargin
        binding.candidateHeightSettingContent.layoutParams = layoutParams
    }

    private fun updateInspectorToggleLabel(state: Int) {
        binding.toggleInspectorButton.setText(
            if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                R.string.candidate_height_inspector_expand
            } else {
                R.string.candidate_height_inspector_collapse
            }
        )
    }

    private fun setupPreviewModeControls() {
        binding.candidatePreviewModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isSyncingPreviewModeControls) return@addOnButtonCheckedListener
            isCandidateListVisible = checkedId == R.id.candidate_preview_candidates_button
            updateCandidateListAndHeight()
        }
        syncPreviewModeControls()
    }

    private fun syncPreviewModeControls() {
        if (isSyncingPreviewModeControls) return
        isSyncingPreviewModeControls = true
        try {
            binding.candidatePreviewModeToggleGroup.check(
                if (isCandidateListVisible) {
                    R.id.candidate_preview_candidates_button
                } else {
                    R.id.candidate_preview_empty_button
                }
            )
        } finally {
            isSyncingPreviewModeControls = false
        }
    }

    private fun setupAdapter() {
        suggestionAdapter.apply {
            setUndoEnabled(false)
            setPasteEnabled(false)
            onListUpdated = {
                applyCurrentDimensions()
            }
        }
        applyCandidateAdapterPresentation()
        binding.candidateHeightSettingRecyclerview.apply {
            itemAnimator = null
            isFocusable = false
        }
        binding.candidateHeightSettingRecyclerview.adapter = suggestionAdapter
    }

    private fun applyCandidateAdapterPresentation() {
        suggestionAdapter.setShowCandidateYomiForLiveConversion(
            (appPreference.live_conversion_preference ?: false) &&
                (appPreference.live_conversion_candidate_yomi_preference ?: false)
        )
        if (appPreference.theme_mode == "custom") {
            suggestionAdapter.setCandidateTextColor(appPreference.custom_theme_candidate_text_color)
            suggestionAdapter.setCandidateItemColors(
                backgroundColor = appPreference.custom_theme_candidate_item_bg_color,
                pressedColor = appPreference.custom_theme_candidate_item_pressed_bg_color
            )
            suggestionAdapter.setCandidateEmptyPopupColors(
                backgroundColor = appPreference.custom_theme_candidate_empty_popup_bg_color,
                textColor = appPreference.custom_theme_candidate_empty_popup_text_color
            )
        } else {
            suggestionAdapter.clearCandidateEmptyPopupColors()
        }
    }

    private fun setupColumnControls() {
        syncColumnControls()
        binding.candidateColumnToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isSyncingColumnControls) return@addOnButtonCheckedListener
            val column = columnForButtonId(checkedId) ?: return@addOnButtonCheckedListener
            appPreference.setCandidateColumnAndSyncHeight(isLandscape = true, column = column)
            setSuggestionView()
            syncColumnControls()
            applyHeightDp(selectedHeightDp(), persist = false)
            updateInspectorSummary()
        }
    }

    private fun resetSettings() {
        appPreference.resetCandidateHeightSettingsToUserDefaults(isLandscape = true)
        appPreference.syncActiveCandidateVisibleHeightToImePreference(isLandscape = true)
        appPreference.candidate_letter_size = defaultCandidateTextSize
        applyCandidateTextSize(defaultCandidateTextSize, persist = false)
        syncColumnControls()
        updateCandidateListAndHeight()
        applyHeightDp(selectedHeightDp(), persist = false)
        updateInspectorSummary()
    }

    private fun updateCandidateListAndHeight() {
        if (isCandidateListVisible) {
            suggestionAdapter.suggestions = previewCandidates
        } else {
            suggestionAdapter.suggestions = emptyList()
        }
        syncPreviewModeControls()
        binding.candidatePreviewVisibilityButton.isVisible = isCandidateListVisible
        updateCandidateTabPreview()
        updateShortcutToolbarPreview()
        applyHeightDp(selectedHeightDp(), persist = false)
        updateInspectorSummary()
    }

    private fun setSuggestionView() {
        val columnNum = appPreference.getCandidateColumn(isLandscape = true)
        clearItemDecorations(binding.candidateHeightSettingRecyclerview)
        when (columnNum) {
            "1" -> {
                binding.candidateHeightSettingRecyclerview.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            }

            "2", "3" -> {
                val spanCount = columnNum.toInt()
                val gridLayoutManager =
                    GridLayoutManager(
                        requireContext(),
                        spanCount,
                        GridLayoutManager.HORIZONTAL,
                        false
                    ).apply {
                        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                return if (suggestionAdapter.isFullSpanItem(position)) {
                                    spanCount
                                } else {
                                    1
                                }
                            }
                        }
                    }
                binding.candidateHeightSettingRecyclerview.layoutManager = gridLayoutManager
                val spacingInPixels =
                    resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing)
                binding.candidateHeightSettingRecyclerview.addItemDecoration(
                    CandidateHeightPreviewGridSpacingDecoration(
                        spanCount = spanCount,
                        spacing = spacingInPixels,
                        includeEdge = true
                    )
                )
            }
        }
        binding.candidateHeightSettingRecyclerview.adapter = suggestionAdapter
    }

    private fun applyCurrentDimensions() {
        applyHeightDp(selectedHeightDp(), persist = false)
    }

    private fun selectedHeightDp(): Int {
        return if (isCandidateListVisible) {
            appPreference.getCandidateVisibleHeightDp(
                isLandscape = true,
                column = appPreference.getCandidateColumn(isLandscape = true)
            )
        } else {
            appPreference.candidate_view_empty_height_dp_landscape ?: 60
        }
    }

    private fun saveSelectedHeightDp(heightDp: Int) {
        val clamped = heightDp.coerceIn(minHeightDp, maxHeightDp)
        if (isCandidateListVisible) {
            appPreference.setCandidateVisibleHeightDp(
                isLandscape = true,
                column = appPreference.getCandidateColumn(isLandscape = true),
                heightDp = clamped
            )
            appPreference.syncActiveCandidateVisibleHeightToImePreference(isLandscape = true)
        } else {
            appPreference.candidate_view_empty_height_dp_landscape = clamped
        }
    }

    private fun applyHeightDp(heightDp: Int, persist: Boolean) {
        val clamped = heightDp.coerceIn(minHeightDp, maxHeightDp)
        val heightPx = clamped.dpToPx()
        updatePreviewHeightPx(heightPx)
        if (persist) {
            saveSelectedHeightDp(clamped)
        }
        syncHeightControls(clamped)
        updateInspectorSummary()
    }

    private fun updatePreviewHeightPx(candidateHeightPx: Int) {
        val presentation = resolveCandidateHeightPreviewPresentation()
        val keyboardHeightPx = keyboardPreviewHeightPx()
        val independentToolbarHeightPx = presentation.independentShortcutToolbarHeightPx
        binding.keyboardPreviewContainer.layoutParams =
            (binding.keyboardPreviewContainer.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM
                height = keyboardHeightPx
                bottomMargin = 0
            }
        binding.candidatePreviewFrame.layoutParams =
            (binding.candidatePreviewFrame.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM
                height = candidateHeightPx
                bottomMargin = keyboardHeightPx
            }
        binding.independentShortcutToolbarPreviewContainer.layoutParams =
            (binding.independentShortcutToolbarPreviewContainer.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM
                height = independentToolbarHeightPx
                bottomMargin = keyboardHeightPx + candidateHeightPx
            }
        binding.candidateTabPreviewContainer.layoutParams =
            (binding.candidateTabPreviewContainer.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP
                height = presentation.candidateTabOffsetPx.coerceAtLeast(36.dpToPx())
            }
        binding.candidateHeightSettingContent.layoutParams =
            binding.candidateHeightSettingContent.layoutParams.apply {
                height = presentation.candidateTabOffsetPx +
                    candidateHeightPx +
                    independentToolbarHeightPx +
                    keyboardHeightPx
            }
    }

    private fun keyboardPreviewHeightPx(): Int {
        return candidateKeyboardPreviewHeightPx(binding.keyboardPreviewContainer)
    }

    private fun syncHeightControls(heightDp: Int) {
        if (isSyncingHeightControls) return
        isSyncingHeightControls = true
        try {
            val clamped = heightDp.coerceIn(minHeightDp, maxHeightDp)
            binding.candidateHeightSeekbar.value = clamped.toFloat()
            val text = clamped.toString()
            if (binding.candidateHeightEditText.text?.toString() != text) {
                binding.candidateHeightEditText.setText(text)
                binding.candidateHeightEditText.setSelection(text.length)
            }
            binding.candidateHeightInputLayout.error = null
            binding.candidateHeightValueText.text = getString(
                R.string.candidate_height_current_value,
                clamped
            )
        } finally {
            isSyncingHeightControls = false
        }
    }

    private fun setupHeightSeekBar() {
        binding.candidateHeightSeekbar.valueFrom = minHeightDp.toFloat()
        binding.candidateHeightSeekbar.valueTo = maxHeightDp.toFloat()
        binding.candidateHeightSeekbar.stepSize = 1f
        binding.candidateHeightSeekbar.value = selectedHeightDp()
            .coerceIn(minHeightDp, maxHeightDp).toFloat()
        binding.candidateHeightSeekbar.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isSyncingHeightControls) return@addOnChangeListener
            applyHeightDp(value.roundToInt(), persist = false)
        }
        binding.candidateHeightSeekbar.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                override fun onStopTrackingTouch(slider: Slider) {
                    applyHeightDp(slider.value.roundToInt(), persist = true)
                }
            }
        )
    }

    private fun setupHeightEditText() {
        binding.candidateHeightEditText.setOnEditorActionListener { _, _, _ ->
            applyHeightFromEditText()
            false
        }
        binding.candidateHeightEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                applyHeightFromEditText()
            }
        }
    }

    private fun applyHeightFromEditText() {
        if (isSyncingHeightControls) return
        val value = binding.candidateHeightEditText.text?.toString()?.trim()?.toIntOrNull()
        if (value == null) {
            binding.candidateHeightInputLayout.error =
                getString(R.string.candidate_height_invalid_value)
            return
        }
        applyHeightDp(value.coerceIn(minHeightDp, maxHeightDp), persist = true)
    }

    private fun setupCandidateLetterSizeSeekBar() {
        binding.candidateLetterSizeSeekbar.valueFrom = minCandidateTextSize
        binding.candidateLetterSizeSeekbar.valueTo = maxCandidateTextSize
        binding.candidateLetterSizeSeekbar.stepSize = 0.1f
        binding.candidateLetterSizeSeekbar.value =
            (appPreference.candidate_letter_size ?: defaultCandidateTextSize)
                .coerceIn(minCandidateTextSize, maxCandidateTextSize)
        binding.candidateLetterSizeSeekbar.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isSyncingLetterSizeControls) return@addOnChangeListener
            applyCandidateTextSize(value, persist = false)
        }
        binding.candidateLetterSizeSeekbar.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                override fun onStopTrackingTouch(slider: Slider) {
                    applyCandidateTextSize(slider.value, persist = true)
                }
            }
        )
    }

    private fun setupCandidateLetterSizeEditText() {
        binding.candidateLetterSizeEditText.setOnEditorActionListener { _, _, _ ->
            applyCandidateLetterSizeFromEditText()
            false
        }
        binding.candidateLetterSizeEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                applyCandidateLetterSizeFromEditText()
            }
        }
    }

    private fun applyCandidateLetterSizeFromEditText() {
        if (isSyncingLetterSizeControls) return
        val value = binding.candidateLetterSizeEditText.text?.toString()?.trim()?.toFloatOrNull()
        if (value == null) {
            binding.candidateLetterSizeInputLayout.error =
                getString(R.string.candidate_letter_size_invalid_value)
            return
        }
        applyCandidateTextSize(value.coerceIn(minCandidateTextSize, maxCandidateTextSize), persist = true)
    }

    private fun applyCandidateTextSize(size: Float, persist: Boolean) {
        val clamped = ((size.coerceIn(minCandidateTextSize, maxCandidateTextSize) * 10).roundToInt() / 10f)
        suggestionAdapter.setCandidateTextSize(clamped)
        if (persist) {
            appPreference.candidate_letter_size = clamped
        }
        syncCandidateLetterSizeControls(clamped)
        updateInspectorSummary(textSize = clamped)
    }

    private fun syncCandidateLetterSizeControls(size: Float) {
        if (isSyncingLetterSizeControls) return
        isSyncingLetterSizeControls = true
        try {
            val clamped = size.coerceIn(minCandidateTextSize, maxCandidateTextSize)
            binding.candidateLetterSizeSeekbar.value = clamped
            val text = String.format(Locale.US, "%.1f", clamped)
            if (binding.candidateLetterSizeEditText.text?.toString() != text) {
                binding.candidateLetterSizeEditText.setText(text)
                binding.candidateLetterSizeEditText.setSelection(text.length)
            }
            binding.candidateLetterSizeInputLayout.error = null
            binding.candidateLetterSizeValueText.text = getString(
                R.string.candidate_letter_size_current_value,
                clamped
            )
        } finally {
            isSyncingLetterSizeControls = false
        }
    }

    private fun updateInspectorSummary(
        heightDp: Int = selectedHeightDp(),
        textSize: Float = appPreference.candidate_letter_size ?: defaultCandidateTextSize
    ) {
        val clampedTextSize = (textSize
            .coerceIn(minCandidateTextSize, maxCandidateTextSize) * 10)
            .roundToInt() / 10f
        val previewMode = getString(
            if (isCandidateListVisible) {
                R.string.candidate_height_preview_candidates
            } else {
                R.string.candidate_height_preview_empty
            }
        )
        val clampedHeight = heightDp.coerceIn(minHeightDp, maxHeightDp)
        binding.inspectorSummaryText.text = getString(
            R.string.candidate_height_sheet_summary_format,
            previewMode,
            appPreference.getCandidateColumn(isLandscape = true),
            clampedHeight.toString()
        )
        binding.candidateHeightValueText.text = getString(
            R.string.candidate_height_current_value,
            clampedHeight
        )
        binding.candidateLetterSizeValueText.text = getString(
            R.string.candidate_letter_size_current_value,
            clampedTextSize
        )
    }

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
                    binding.handleTop.parent?.requestDisallowInterceptTouchEvent(true)
                    binding.candidateControlsScroll.requestDisallowInterceptTouchEvent(true)
                    initialY = event.rawY
                    initialHeight = binding.candidatePreviewFrame.height
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    binding.handleTop.parent?.requestDisallowInterceptTouchEvent(true)
                    binding.candidateControlsScroll.requestDisallowInterceptTouchEvent(true)
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight - deltaY).coerceIn(minHeightPx, maxHeightPx)
                    val currentHeightDp =
                        (newHeight / density).roundToInt().coerceIn(minHeightDp, maxHeightDp)
                    updatePreviewHeightPx(currentHeightDp.dpToPx())
                    syncHeightControls(currentHeightDp)
                    updateInspectorSummary(currentHeightDp)
                    binding.candidatePreviewFrame.requestLayout()
                    binding.candidateHeightSettingContent.requestLayout()
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    binding.handleTop.parent?.requestDisallowInterceptTouchEvent(false)
                    binding.candidateControlsScroll.requestDisallowInterceptTouchEvent(false)
                    val finalHeightDp = saveHeightPreference()
                    applyHeightDp(finalHeightDp, persist = false)
                    true
                }

                else -> false
            }
        }
    }

    private fun saveHeightPreference(): Int {
        val density = resources.displayMetrics.density
        val heightPx = binding.candidatePreviewFrame.layoutParams.height
            .takeIf { it > 0 }
            ?: binding.candidatePreviewFrame.height
        val finalHeightDp =
            (heightPx / density).roundToInt().coerceIn(minHeightDp, maxHeightDp)

        saveSelectedHeightDp(finalHeightDp)

        Timber.d(
            "saveHeightPreference landscape (%s): %d dp",
            if (isCandidateListVisible) "with candidates" else "empty",
            finalHeightDp
        )
        return finalHeightDp
    }

    private fun syncColumnControls() {
        if (isSyncingColumnControls) return
        isSyncingColumnControls = true
        try {
            binding.candidateColumnToggleGroup.check(
                when (appPreference.getCandidateColumn(isLandscape = true)) {
                    "2" -> R.id.candidate_column_two_button
                    "3" -> R.id.candidate_column_three_button
                    else -> R.id.candidate_column_one_button
                }
            )
        } finally {
            isSyncingColumnControls = false
        }
    }

    private fun columnForButtonId(id: Int): String? =
        when (id) {
            R.id.candidate_column_one_button -> "1"
            R.id.candidate_column_two_button -> "2"
            R.id.candidate_column_three_button -> "3"
            else -> null
        }

    private fun updateCandidateTabPreview() {
        val presentation = resolveCandidateHeightPreviewPresentation()
        binding.candidateTabPreviewContainer.isVisible = presentation.showCandidateTab
        if (!presentation.showCandidateTab) return

        binding.candidateTabPreviewContainer.removeAllViews()
        val tabs = runCatching { appPreference.candidate_tab_order }
            .getOrDefault(listOf(CandidateTab.PREDICTION, CandidateTab.CONVERSION, CandidateTab.EISUKANA))
        tabs.forEachIndexed { index, tab ->
            binding.candidateTabPreviewContainer.addView(
                previewTabView(label = tab.previewLabel(), selected = index == 0),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
    }

    private fun updateShortcutToolbarPreview() {
        val presentation = resolveCandidateHeightPreviewPresentation()
        when {
            presentation.showIndependentShortcutToolbar -> {
                binding.independentShortcutToolbarPreviewContainer.isVisible = true
                populateShortcutToolbarPreview(binding.independentShortcutToolbarPreviewContainer)
            }

            presentation.reserveIndependentShortcutToolbarSpace -> {
                binding.independentShortcutToolbarPreviewContainer.isInvisible = true
            }

            else -> {
                binding.independentShortcutToolbarPreviewContainer.isVisible = false
            }
        }
        suggestionAdapter.setShortcutItems(previewShortcutItems())
        suggestionAdapter.setIntegratedShortcutVisibility(presentation.showIntegratedShortcutItems)
    }

    private data class CandidateHeightPreviewPresentation(
        val showCandidateTab: Boolean,
        val showIndependentShortcutToolbar: Boolean,
        val reserveIndependentShortcutToolbarSpace: Boolean,
        val showIntegratedShortcutItems: Boolean,
        val candidateTabOffsetPx: Int,
        val independentShortcutToolbarHeightPx: Int
    )

    private fun resolveCandidateHeightPreviewPresentation(): CandidateHeightPreviewPresentation {
        val inputStringEmpty = !isCandidateListVisible
        val tailEmpty = true
        val clipboardPreviewShown = false
        val selectionActionsShown = false
        val suggestionsEmpty = !isCandidateListVisible
        val customLayoutPickerShown = false
        val presentation = CandidateStripPresentationPolicy.resolve(
            CandidateStripPresentationState(
                candidateTabVisible = appPreference.candidate_tab_preference,
                candidatesShown = isCandidateListVisible,
                resetCandidateTabSelection = false,
                shortcutToolbarVisible = appPreference.shortcut_toolbar_visibility_preference,
                shortcutToolbarIntegratedInSuggestion =
                    appPreference.shortcut_toolbar_integrated_in_suggestion_preference,
                inputStringEmpty = inputStringEmpty,
                tailEmpty = tailEmpty,
                clipboardPreviewShown = clipboardPreviewShown,
                selectionActionsShown = selectionActionsShown,
                suggestionsEmpty = suggestionsEmpty,
                customLayoutPickerShown = customLayoutPickerShown,
                symbolKeyboardShown = false,
                shortcutToolbarHiddenForCandidates = false
            )
        )
        val independentHeightPx =
            if (
                presentation.showIndependentShortcutToolbar ||
                presentation.reserveIndependentShortcutToolbarSpace
            ) {
                appPreference.shortcut_toolbar_height_dp_preference.dpToPx()
            } else {
                0
            }
        return CandidateHeightPreviewPresentation(
            showCandidateTab = presentation.showCandidateTab,
            showIndependentShortcutToolbar = presentation.showIndependentShortcutToolbar,
            reserveIndependentShortcutToolbarSpace =
                presentation.reserveIndependentShortcutToolbarSpace,
            showIntegratedShortcutItems = presentation.showIntegratedShortcutItems,
            candidateTabOffsetPx = if (presentation.showCandidateTab) 36.dpToPx() else 0,
            independentShortcutToolbarHeightPx = independentHeightPx
        )
    }

    private fun previewShortcutItems(): List<ShortcutType> =
        listOf(
            ShortcutType.SETTINGS,
            ShortcutType.EMOJI,
            ShortcutType.TEMPLATE,
            ShortcutType.KEYBOARD_PICKER,
            ShortcutType.PASTE
        )

    private fun populateShortcutToolbarPreview(container: LinearLayout) {
        container.removeAllViews()
        val toolbarHeightPx = appPreference.shortcut_toolbar_height_dp_preference.dpToPx()
        val iconSizePx = appPreference.resolveShortcutToolbarIconSizeDp().dpToPx()
        val itemWidthPx = maxOf(64.dpToPx(), iconSizePx + 36.dpToPx())
        container.layoutParams = container.layoutParams.apply {
            height = toolbarHeightPx
        }
        previewShortcutItems().forEach { shortcut ->
            val itemView = FrameLayout(requireContext()).apply {
                isClickable = false
                isFocusable = false
            }
            val iconView = AppCompatImageView(requireContext()).apply {
                setImageResource(shortcut.iconResId)
                imageTintList = ColorStateList.valueOf(resolveThemeColor(MaterialR.attr.colorOnSurface))
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                contentDescription = shortcut.description
            }
            itemView.addView(
                iconView,
                FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            )
            container.addView(
                itemView,
                LinearLayout.LayoutParams(itemWidthPx, toolbarHeightPx)
            )
        }
    }

    private fun setupKeyboardPreview() {
        renderCandidateKeyboardPreview(
            fragment = this,
            appPreference = appPreference,
            keyboardRepository = keyboardRepository,
            sumireSpecialKeyRepository = sumireSpecialKeyRepository,
            views = CandidateKeyboardPreviewViews(
                container = binding.keyboardPreviewContainer,
                tenKey = binding.candidateHeightSettingTenkeyPreview,
                gojuon = binding.candidateHeightSettingGojuonPreview,
                qwerty = binding.candidateHeightSettingQwertyPreview,
                flick = binding.candidateHeightSettingFlickPreview
            ),
            isLandscape = true,
            onPreviewLayoutChanged = ::applyCurrentDimensions
        )
    }

    private fun previewTabView(label: String, selected: Boolean): MaterialTextView {
        return MaterialTextView(requireContext()).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(
                resolveThemeColor(
                    if (selected) MaterialR.attr.colorOnPrimary else MaterialR.attr.colorOnSurface
                )
            )
            background = roundedBackground(
                fillColor = resolveThemeColor(
                    if (selected) AppCompatR.attr.colorPrimary else MaterialR.attr.colorSurfaceVariant
                ),
                radiusDp = 6
            )
        }
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int? = null,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.dpToPx().toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(1.dpToPx(), it) }
        }
    }

    private fun CandidateTab.previewLabel(): String =
        when (this) {
            CandidateTab.PREDICTION -> "予測"
            CandidateTab.CONVERSION -> "変換"
            CandidateTab.EISUKANA -> "英数カナ"
        }

    private fun resolveThemeColor(@AttrRes attr: Int): Int {
        val typedArray = requireContext().obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, Color.TRANSPARENT)
        } finally {
            typedArray.recycle()
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).roundToInt()
}
