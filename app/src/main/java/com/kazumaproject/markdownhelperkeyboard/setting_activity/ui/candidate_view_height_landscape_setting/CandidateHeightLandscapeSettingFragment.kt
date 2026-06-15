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
import android.widget.SeekBar
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.R as MaterialR
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateHeightLandscapeSettingBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.CandidateStripPresentationPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.CandidateStripPresentationState
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
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
    private var isSyncingDefaultHeightControls = false
    private var previousBottomNavigationVisibility: Int? = null
    private var previousNavHostBottomToTop = ConstraintLayout.LayoutParams.UNSET
    private var previousNavHostBottomToBottom = ConstraintLayout.LayoutParams.UNSET
    private var previousNavHostBottomMargin = 0
    private var hasPreviousNavHostBottomConstraint = false

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

        hideBottomNavigationForPreview()
        appPreference.migrateCandidateHeightPerColumnPreferencesIfNeeded()
        appPreference.syncActiveCandidateVisibleHeightToImePreference(isLandscape = true)

        setupMenu()
        setupAdapter()
        setupInspectorBottomSheet()
        setupInspectorTabs()
        setupColumnControls()
        setupResizeHandle()
        setupHeightSeekBar()
        setupHeightEditText()
        setupCandidateLetterSizeSeekBar()
        setupCandidateLetterSizeEditText()
        setupDefaultHeightControls()
        setupKeyboardPreview()
        setSuggestionView()

        binding.toggleCandidateListButton.setOnClickListener {
            isCandidateListVisible = !isCandidateListVisible
            updateCandidateListAndHeight()
        }

        applyCandidateTextSize(appPreference.candidate_letter_size ?: defaultCandidateTextSize, persist = false)
        updateCandidateListAndHeight()
        applyHeightDp(selectedHeightDp(), persist = false)
        syncDefaultHeightControls()
        updateInspectorSummary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        restoreBottomNavigationVisibility()
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        binding.candidateHeightSettingRecyclerview.adapter = null
        suggestionAdapter.release()
        _binding = null
    }

    private fun hideBottomNavigationForPreview() {
        val bottomNavigation = activity?.findViewById<View>(R.id.nav_view) ?: return
        if (previousBottomNavigationVisibility == null) {
            previousBottomNavigationVisibility = bottomNavigation.visibility
        }
        bottomNavigation.visibility = View.GONE
        extendNavHostToParentBottom()
    }

    private fun restoreBottomNavigationVisibility() {
        val visibility = previousBottomNavigationVisibility ?: return
        restoreNavHostBottomConstraint()
        activity?.findViewById<View>(R.id.nav_view)?.visibility = visibility
        previousBottomNavigationVisibility = null
    }

    private fun extendNavHostToParentBottom() {
        val container = activity?.findViewById<ConstraintLayout>(R.id.container) ?: return
        val navHost = activity?.findViewById<View>(R.id.nav_host_fragment_activity_main) ?: return
        val layoutParams = navHost.layoutParams as? ConstraintLayout.LayoutParams ?: return
        if (!hasPreviousNavHostBottomConstraint) {
            previousNavHostBottomToTop = layoutParams.bottomToTop
            previousNavHostBottomToBottom = layoutParams.bottomToBottom
            previousNavHostBottomMargin = layoutParams.bottomMargin
            hasPreviousNavHostBottomConstraint = true
        }
        ConstraintSet().apply {
            clone(container)
            clear(R.id.nav_host_fragment_activity_main, ConstraintSet.BOTTOM)
            connect(
                R.id.nav_host_fragment_activity_main,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM
            )
            setMargin(R.id.nav_host_fragment_activity_main, ConstraintSet.BOTTOM, 0)
            applyTo(container)
        }
    }

    private fun restoreNavHostBottomConstraint() {
        if (!hasPreviousNavHostBottomConstraint) return
        val container = activity?.findViewById<ConstraintLayout>(R.id.container) ?: return
        ConstraintSet().apply {
            clone(container)
            clear(R.id.nav_host_fragment_activity_main, ConstraintSet.BOTTOM)
            when {
                previousNavHostBottomToTop != ConstraintLayout.LayoutParams.UNSET -> {
                    connect(
                        R.id.nav_host_fragment_activity_main,
                        ConstraintSet.BOTTOM,
                        previousNavHostBottomToTop,
                        ConstraintSet.TOP
                    )
                }

                previousNavHostBottomToBottom != ConstraintLayout.LayoutParams.UNSET -> {
                    connect(
                        R.id.nav_host_fragment_activity_main,
                        ConstraintSet.BOTTOM,
                        previousNavHostBottomToBottom,
                        ConstraintSet.BOTTOM
                    )
                }
            }
            setMargin(
                R.id.nav_host_fragment_activity_main,
                ConstraintSet.BOTTOM,
                previousNavHostBottomMargin
            )
            applyTo(container)
        }
        hasPreviousNavHostBottomConstraint = false
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        binding.toolbar.setNavigationIcon(AppCompatR.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbar.inflateMenu(R.menu.fragment_reset_menu)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_candidate_default_height -> {
                    openDefaultHeightInspector()
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
        binding.showInspectorButton.setOnClickListener {
            showInspectorPanel()
        }
        binding.closeInspectorButton.setOnClickListener {
            hideInspectorPanel()
        }
        showInspectorPanel()
    }

    private fun setupInspectorTabs() {
        binding.candidateInspectorTabGroup.check(R.id.candidate_tab_height_button)
        binding.candidateInspectorTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showInspectorTab(checkedId)
        }
        showInspectorTab(R.id.candidate_tab_height_button)
    }

    private fun openDefaultHeightInspector() {
        binding.candidateInspectorTabGroup.check(R.id.candidate_tab_default_button)
        syncDefaultHeightControls()
        showInspectorPanel()
    }

    private fun showInspectorPanel() {
        binding.inspectorBottomSheet.isVisible = true
        binding.showInspectorButton.isVisible = false
    }

    private fun hideInspectorPanel() {
        binding.inspectorBottomSheet.isVisible = false
        binding.showInspectorButton.isVisible = true
    }

    private fun showInspectorTab(checkedId: Int) {
        binding.heightControlsContainer.isVisible = checkedId == R.id.candidate_tab_height_button
        binding.textControlsContainer.isVisible = checkedId == R.id.candidate_tab_text_button
        binding.defaultControlsContainer.isVisible =
            checkedId == R.id.candidate_tab_default_button
        if (checkedId == R.id.candidate_tab_default_button) {
            syncDefaultHeightControls()
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
        appPreference.resetCandidateVisibleHeightsToUserDefaults(isLandscape = true)
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
            binding.toggleCandidateListButton.text = getString(R.string.candidate_preview_input_mode)
        } else {
            suggestionAdapter.suggestions = emptyList()
            binding.toggleCandidateListButton.text = getString(R.string.candidate_preview_empty_mode)
        }
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
            appPreference.candidate_view_empty_height_dp_landscape ?: 110
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
                height = independentToolbarHeightPx.coerceAtLeast(36.dpToPx())
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
            binding.candidateHeightSeekbar.progress = clamped - minHeightDp
            val text = clamped.toString()
            if (binding.candidateHeightEditText.text?.toString() != text) {
                binding.candidateHeightEditText.setText(text)
                binding.candidateHeightEditText.setSelection(text.length)
            }
            binding.candidateHeightInputLayout.error = null
        } finally {
            isSyncingHeightControls = false
        }
    }

    private fun setupHeightSeekBar() {
        binding.candidateHeightSeekbar.max = maxHeightDp - minHeightDp
        binding.candidateHeightSeekbar.progress =
            selectedHeightDp().coerceIn(minHeightDp, maxHeightDp) - minHeightDp
        binding.candidateHeightSeekbar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser || isSyncingHeightControls) return
                    applyHeightDp(minHeightDp + progress, persist = true)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
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
        binding.candidateLetterSizeSeekbar.max =
            ((maxCandidateTextSize - minCandidateTextSize) * 10).roundToInt()
        binding.candidateLetterSizeSeekbar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser || isSyncingLetterSizeControls) return
                    val newSize = minCandidateTextSize + progress / 10f
                    applyCandidateTextSize(newSize, persist = true)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
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
        updateInspectorSummary()
    }

    private fun syncCandidateLetterSizeControls(size: Float) {
        if (isSyncingLetterSizeControls) return
        isSyncingLetterSizeControls = true
        try {
            val clamped = size.coerceIn(minCandidateTextSize, maxCandidateTextSize)
            binding.candidateLetterSizeSeekbar.progress =
                ((clamped - minCandidateTextSize) * 10).roundToInt()
            val text = String.format(Locale.US, "%.1f", clamped)
            if (binding.candidateLetterSizeEditText.text?.toString() != text) {
                binding.candidateLetterSizeEditText.setText(text)
                binding.candidateLetterSizeEditText.setSelection(text.length)
            }
            binding.candidateLetterSizeInputLayout.error = null
        } finally {
            isSyncingLetterSizeControls = false
        }
    }

    private fun setupDefaultHeightControls() {
        binding.saveDefaultsButton.setOnClickListener {
            saveDefaultHeightsFromInputs()
        }
        binding.useCurrentDefaultsButton.setOnClickListener {
            appPreference.copyCandidateVisibleHeightsToUserDefaults(isLandscape = true)
            syncDefaultHeightControls()
        }
        binding.restoreFactoryDefaultsButton.setOnClickListener {
            appPreference.resetCandidateDefaultVisibleHeightsToFactoryDefaults(isLandscape = true)
            syncDefaultHeightControls()
        }
        listOf(
            binding.defaultHeightOneEditText,
            binding.defaultHeightTwoEditText,
            binding.defaultHeightThreeEditText
        ).forEach { editText ->
            editText.setOnEditorActionListener { _, _, _ ->
                saveDefaultHeightsFromInputs()
                false
            }
        }
    }

    private fun saveDefaultHeightsFromInputs(): Boolean {
        if (isSyncingDefaultHeightControls) return false
        val one = readDefaultHeightInput(
            binding.defaultHeightOneInputLayout,
            binding.defaultHeightOneEditText
        ) ?: return false
        val two = readDefaultHeightInput(
            binding.defaultHeightTwoInputLayout,
            binding.defaultHeightTwoEditText
        ) ?: return false
        val three = readDefaultHeightInput(
            binding.defaultHeightThreeInputLayout,
            binding.defaultHeightThreeEditText
        ) ?: return false

        appPreference.setCandidateDefaultVisibleHeightDp(
            isLandscape = true,
            column = "1",
            heightDp = one
        )
        appPreference.setCandidateDefaultVisibleHeightDp(
            isLandscape = true,
            column = "2",
            heightDp = two
        )
        appPreference.setCandidateDefaultVisibleHeightDp(
            isLandscape = true,
            column = "3",
            heightDp = three
        )
        syncDefaultHeightControls()
        return true
    }

    private fun readDefaultHeightInput(
        inputLayout: TextInputLayout,
        editText: TextInputEditText
    ): Int? {
        val value = editText.text?.toString()?.trim()?.toIntOrNull()
        if (value == null) {
            inputLayout.error = getString(R.string.candidate_height_invalid_value)
            return null
        }
        inputLayout.error = null
        return value.coerceIn(minHeightDp, maxHeightDp)
    }

    private fun syncDefaultHeightControls() {
        if (isSyncingDefaultHeightControls) return
        isSyncingDefaultHeightControls = true
        try {
            setDefaultHeightText(
                binding.defaultHeightOneInputLayout,
                binding.defaultHeightOneEditText,
                appPreference.getCandidateDefaultVisibleHeightDp(isLandscape = true, column = "1")
            )
            setDefaultHeightText(
                binding.defaultHeightTwoInputLayout,
                binding.defaultHeightTwoEditText,
                appPreference.getCandidateDefaultVisibleHeightDp(isLandscape = true, column = "2")
            )
            setDefaultHeightText(
                binding.defaultHeightThreeInputLayout,
                binding.defaultHeightThreeEditText,
                appPreference.getCandidateDefaultVisibleHeightDp(isLandscape = true, column = "3")
            )
        } finally {
            isSyncingDefaultHeightControls = false
        }
    }

    private fun setDefaultHeightText(
        inputLayout: TextInputLayout,
        editText: TextInputEditText,
        heightDp: Int
    ) {
        val text = heightDp.coerceIn(minHeightDp, maxHeightDp).toString()
        if (editText.text?.toString() != text) {
            editText.setText(text)
            editText.setSelection(text.length)
        }
        inputLayout.error = null
    }

    private fun updateInspectorSummary(heightDp: Int = selectedHeightDp()) {
        val textSize = appPreference.candidate_letter_size ?: defaultCandidateTextSize
        binding.inspectorSummaryText.text = getString(
            R.string.candidate_height_sheet_summary_format,
            appPreference.getCandidateColumn(isLandscape = true),
            heightDp.coerceIn(minHeightDp, maxHeightDp).toString(),
            String.format(Locale.US, "%.1f", textSize)
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
        suggestionAdapter.setIntegratedShortcutVisibility(presentation.showIntegratedShortcut)
    }

    private data class CandidateHeightPreviewPresentation(
        val showCandidateTab: Boolean,
        val showIndependentShortcutToolbar: Boolean,
        val reserveIndependentShortcutToolbarSpace: Boolean,
        val showIntegratedShortcut: Boolean,
        val candidateTabOffsetPx: Int,
        val independentShortcutToolbarHeightPx: Int
    )

    private fun resolveCandidateHeightPreviewPresentation(): CandidateHeightPreviewPresentation {
        val inputStringEmpty = !isCandidateListVisible
        val tailEmpty = true
        val clipboardPreviewShown = false
        val selectedTextGemmaActionsShown = false
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
                selectedTextGemmaActionsShown = selectedTextGemmaActionsShown,
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
                36.dpToPx()
            } else {
                0
            }
        return CandidateHeightPreviewPresentation(
            showCandidateTab = presentation.showCandidateTab,
            showIndependentShortcutToolbar = presentation.showIndependentShortcutToolbar,
            reserveIndependentShortcutToolbarSpace =
                presentation.reserveIndependentShortcutToolbarSpace,
            showIntegratedShortcut = presentation.showIntegratedShortcut,
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
        previewShortcutItems().forEach { shortcut ->
            val button = AppCompatImageButton(requireContext()).apply {
                setImageResource(shortcut.iconResId)
                imageTintList = ColorStateList.valueOf(resolveThemeColor(MaterialR.attr.colorOnSurface))
                background = null
                contentDescription = shortcut.description
                isClickable = false
                isFocusable = false
                setPadding(8.dpToPx(), 6.dpToPx(), 8.dpToPx(), 6.dpToPx())
            }
            container.addView(
                button,
                LinearLayout.LayoutParams(42.dpToPx(), ViewGroup.LayoutParams.MATCH_PARENT)
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
