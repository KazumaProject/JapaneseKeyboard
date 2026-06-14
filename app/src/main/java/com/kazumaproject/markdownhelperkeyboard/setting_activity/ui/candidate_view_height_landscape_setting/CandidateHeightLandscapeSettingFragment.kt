package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_landscape_setting

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.R as MaterialR
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateHeightLandscapeSettingBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.CandidateHeightPreviewGridSpacingDecoration
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.SuggestionAdapter2
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.clearItemDecorations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.createCandidateHeightPreviewCandidates
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class CandidateHeightLandscapeSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private lateinit var suggestionAdapter: SuggestionAdapter2

    private var _binding: FragmentCandidateHeightLandscapeSettingBinding? = null
    private val binding get() = _binding!!

    private var isCandidateListVisible = true
    private var isSyncingHeightControls = false
    private var isSyncingLetterSizeControls = false
    private var isSyncingColumnControls = false
    private var simpleKeyboardPreview: View? = null

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
        setupColumnControls()
        setupResizeHandle()
        setupHeightSeekBar()
        setupHeightEditText()
        setupCandidateLetterSizeSeekBar()
        setupCandidateLetterSizeEditText()
        setupKeyboardPreview()
        setSuggestionView()

        binding.toggleCandidateListButton.setOnClickListener {
            isCandidateListVisible = !isCandidateListVisible
            updateCandidateListAndHeight()
        }

        applyCandidateTextSize(appPreference.candidate_letter_size ?: defaultCandidateTextSize, persist = false)
        updateCandidateListAndHeight()
        applyHeightDp(selectedHeightDp(), persist = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.candidateHeightSettingRecyclerview.adapter = null
        suggestionAdapter.release()
        simpleKeyboardPreview = null
        _binding = null
    }

    private fun setupMenu() {
        val menuHost: androidx.core.view.MenuHost = requireActivity()
        menuHost.addMenuProvider(object : androidx.core.view.MenuProvider {
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

    private fun setupAdapter() {
        suggestionAdapter.apply {
            setUndoEnabled(false)
            setPasteEnabled(false)
            onListUpdated = {
                applyCurrentDimensions()
            }
        }
        binding.candidateHeightSettingRecyclerview.adapter = suggestionAdapter
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
        }
    }

    private fun resetSettings() {
        appPreference.setCandidateVisibleHeightDp(isLandscape = true, column = "1", heightDp = 110)
        appPreference.setCandidateVisibleHeightDp(isLandscape = true, column = "2", heightDp = 165)
        appPreference.setCandidateVisibleHeightDp(isLandscape = true, column = "3", heightDp = 230)
        appPreference.candidate_view_empty_height_dp_landscape = 110
        appPreference.syncActiveCandidateVisibleHeightToImePreference(isLandscape = true)
        appPreference.candidate_letter_size = defaultCandidateTextSize
        applyCandidateTextSize(defaultCandidateTextSize, persist = false)
        syncColumnControls()
        updateCandidateListAndHeight()
        applyHeightDp(selectedHeightDp(), persist = false)
    }

    private fun updateCandidateListAndHeight() {
        if (isCandidateListVisible) {
            suggestionAdapter.suggestions = previewCandidates
            binding.toggleCandidateListButton.text = getString(R.string.candidate_preview_input_mode)
        } else {
            suggestionAdapter.suggestions = emptyList()
            binding.toggleCandidateListButton.text = getString(R.string.candidate_preview_empty_mode)
        }
        updateCandidateTabPreview()
        updateShortcutToolbarPreview()
        applyHeightDp(selectedHeightDp(), persist = false)
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
                binding.candidateHeightSettingRecyclerview.layoutManager =
                    GridLayoutManager(
                        requireContext(),
                        spanCount,
                        GridLayoutManager.HORIZONTAL,
                        false
                    )
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
    }

    private fun updatePreviewHeightPx(candidateHeightPx: Int) {
        binding.candidatePreviewFrame.layoutParams =
            binding.candidatePreviewFrame.layoutParams.apply {
                height = candidateHeightPx
            }
        binding.candidateHeightSettingContent.layoutParams =
            binding.candidateHeightSettingContent.layoutParams.apply {
                height = candidateHeightPx + shortcutToolbarHeightPx() + keyboardPreviewHeightPx()
            }
    }

    private fun shortcutToolbarHeightPx(): Int {
        return if (binding.independentShortcutToolbarPreviewContainer.isVisible) 36.dpToPx() else 0
    }

    private fun keyboardPreviewHeightPx(): Int {
        val previewHeight = binding.keyboardPreviewContainer.layoutParams.height
        return if (previewHeight > 0) {
            previewHeight
        } else {
            keyboardPreviewHeightDp().dpToPx()
        }
    }

    private fun keyboardPreviewHeightDp(): Int {
        return (appPreference.keyboard_height_landscape ?: 220).coerceIn(140, 360)
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
                    initialHeight = binding.candidatePreviewFrame.height
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight - deltaY).coerceIn(minHeightPx, maxHeightPx)
                    updatePreviewHeightPx(newHeight.toInt())
                    binding.candidatePreviewFrame.requestLayout()
                    binding.candidateHeightSettingContent.requestLayout()
                    true
                }

                MotionEvent.ACTION_UP -> {
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
        val showCandidateTab = appPreference.candidate_tab_preference && isCandidateListVisible
        binding.candidateTabPreviewContainer.isVisible = showCandidateTab
        if (!showCandidateTab) return

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
        val state = resolvePreviewShortcutToolbarState()
        binding.independentShortcutToolbarPreviewContainer.isVisible = state.showIndependentToolbar
        binding.integratedShortcutToolbarPreviewContainer.isVisible = state.showIntegratedShortcuts
        if (state.showIndependentToolbar) {
            populateShortcutToolbarPreview(binding.independentShortcutToolbarPreviewContainer)
        }
        if (state.showIntegratedShortcuts) {
            populateShortcutToolbarPreview(binding.integratedShortcutToolbarPreviewContainer)
        }
    }

    private data class PreviewShortcutToolbarState(
        val showIndependentToolbar: Boolean,
        val showIntegratedShortcuts: Boolean
    )

    private fun resolvePreviewShortcutToolbarState(): PreviewShortcutToolbarState {
        val visible = appPreference.shortcut_toolbar_visibility_preference
        val integrated = appPreference.shortcut_toolbar_integrated_in_suggestion_preference
        if (!visible) {
            return PreviewShortcutToolbarState(
                showIndependentToolbar = false,
                showIntegratedShortcuts = false
            )
        }
        if (!integrated) {
            return PreviewShortcutToolbarState(
                showIndependentToolbar = true,
                showIntegratedShortcuts = false
            )
        }
        val inputStringEmpty = !isCandidateListVisible
        val tailEmpty = true
        val clipboardPreviewShown = false
        val selectedTextGemmaActionsShown = false
        val suggestionsEmpty = !isCandidateListVisible
        val customLayoutPickerShown = false
        val showIntegrated =
            inputStringEmpty &&
                tailEmpty &&
                !clipboardPreviewShown &&
                !selectedTextGemmaActionsShown &&
                suggestionsEmpty &&
                !customLayoutPickerShown
        return PreviewShortcutToolbarState(
            showIndependentToolbar = false,
            showIntegratedShortcuts = showIntegrated
        )
    }

    private fun populateShortcutToolbarPreview(container: LinearLayout) {
        container.removeAllViews()
        listOf(
            ShortcutType.SETTINGS,
            ShortcutType.EMOJI,
            ShortcutType.TEMPLATE,
            ShortcutType.KEYBOARD_PICKER,
            ShortcutType.PASTE
        ).forEach { shortcut ->
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
        val heightPx = keyboardPreviewHeightPx()
        binding.keyboardPreviewContainer.layoutParams =
            binding.keyboardPreviewContainer.layoutParams.apply {
                height = heightPx
            }
        binding.candidateHeightSettingTenkeyPreview.setOnTouchListener { _, _ -> true }
        val previewKeyboardType = appPreference.keyboard_order.firstOrNull() ?: KeyboardType.TENKEY
        if (previewKeyboardType == KeyboardType.TENKEY) {
            binding.candidateHeightSettingTenkeyPreview.isVisible = true
            simpleKeyboardPreview?.isVisible = false
        } else {
            binding.candidateHeightSettingTenkeyPreview.isVisible = false
            val simplePreview = simpleKeyboardPreview ?: createSimpleKeyboardPreview(previewKeyboardType).also {
                it.tag = SIMPLE_KEYBOARD_PREVIEW_TAG
                binding.keyboardPreviewContainer.addView(
                    it,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                simpleKeyboardPreview = it
            }
            simplePreview.isVisible = true
        }
    }

    private fun createSimpleKeyboardPreview(type: KeyboardType): View {
        val rows = when (type) {
            KeyboardType.QWERTY -> listOf(
                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
                listOf("Z", "X", "C", "V", "B", "N", "M")
            )

            KeyboardType.ROMAJI -> listOf(
                listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ"),
                listOf("A", "I", "U", "E", "O", "K", "S", "T", "N"),
                listOf("Shift", "Space", "Enter")
            )

            KeyboardType.SUMIRE -> listOf(
                listOf("あ", "い", "う", "え", "お"),
                listOf("か", "き", "く", "け", "こ"),
                listOf("さ", "し", "す", "せ", "そ")
            )

            KeyboardType.CUSTOM -> listOf(
                listOf("Custom", "Key", "Layout"),
                listOf("かな", "英数", "記号"),
                listOf("Space", "Enter")
            )

            KeyboardType.TENKEY -> emptyList()
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            rows.forEach { row ->
                addView(
                    LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        row.forEach { label ->
                            addView(
                                MaterialTextView(requireContext()).apply {
                                    text = label
                                    gravity = Gravity.CENTER
                                    setTextColor(
                                        ContextCompat.getColor(
                                            requireContext(),
                                            com.kazumaproject.core.R.color.main_text_color
                                        )
                                    )
                                    textSize = 13f
                                    maxLines = 1
                                    background = keyPreviewBackground()
                                },
                                LinearLayout.LayoutParams(0, 0, 1f).apply {
                                    height = 42.dpToPx()
                                    marginStart = 2.dpToPx()
                                    marginEnd = 2.dpToPx()
                                }
                            )
                        }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    ).apply {
                        topMargin = 2.dpToPx()
                        bottomMargin = 2.dpToPx()
                    }
                )
            }
        }
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

    private fun keyPreviewBackground(): GradientDrawable =
        roundedBackground(
            fillColor = resolveThemeColor(MaterialR.attr.colorSurfaceVariant),
            strokeColor = resolveThemeColor(MaterialR.attr.colorOutline),
            radiusDp = 6
        )

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

    companion object {
        private const val SIMPLE_KEYBOARD_PREVIEW_TAG = "simple_keyboard_preview"
    }
}
