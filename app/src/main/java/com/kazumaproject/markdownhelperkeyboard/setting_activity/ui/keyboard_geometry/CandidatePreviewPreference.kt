package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateDictionaryOrigin
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.KeyboardSurfaceSizePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.KeyboardSurfaceSizeTarget
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.SuggestionAdapter2
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.createCandidateHeightPreviewCandidates
import androidx.preference.PreferenceManager

/** Passive previews: sample candidates do not send input to the IME. */
class CandidatePreviewPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    internal var keyboard = PreviewKeyboard.TENKEY
    internal var landscape = false
    private var adapter: SuggestionAdapter2? = null

    init {
        layoutResource = R.layout.preference_candidate_display_preview
        isSelectable = false
    }

    internal fun refresh() = notifyChanged()

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val container = holder.findViewById(R.id.candidate_display_preview_container) as LinearLayout
        adapter?.release()
        container.removeAllViews()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val geometryKeyboard = when (keyboard) {
            PreviewKeyboard.SUMIRE -> GeometryKeyboard.SUMIRE
            PreviewKeyboard.QWERTY, PreviewKeyboard.ROMAJI -> GeometryKeyboard.QWERTY
            else -> GeometryKeyboard.TENKEY
        }
        val sizeTarget = when (keyboard) {
            PreviewKeyboard.SUMIRE -> KeyboardSurfaceSizeTarget.SUMIRE
            PreviewKeyboard.QWERTY -> KeyboardSurfaceSizeTarget.QWERTY
            PreviewKeyboard.ROMAJI -> KeyboardSurfaceSizeTarget.ROMAJI
            else -> KeyboardSurfaceSizeTarget.TENKEY
        }
        val size = KeyboardSurfaceSizePreferences(preferences).read(sizeTarget, landscape)
        val geometry = KeyboardGeometryPreferences(preferences).read(geometryKeyboard, landscape)
        val preview = GeometryPreview(context, keyboard).apply {
            update(geometry, landscape, size.widthPercent.coerceIn(1, 100), size.positionEnd, size)
            contentDescription = context.getString(R.string.keyboard_geometry_preview_description)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        container.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(TextView(context).apply {
            setText(R.string.candidate_display_examples)
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        val skin = KeyboardSkinRegistry.find(AppPreference.keyboardSkin)
        val skinColors = skin?.let {
            CandidatePanelColors.resolve(context, it.palette, cupertinoClassic = AppPreference.keyboardSkin == KeyboardSkinId.CUPERTINO_CLASSIC)
        }
        val sampleAdapter = SuggestionAdapter2().apply {
            setUndoEnabled(false)
            setPasteEnabled(false)
            setKeyboardFont(KeyboardFontApplicator.processSnapshot)
            setCandidateTextSize(AppPreference.candidate_letter_size ?: 14f)
            setShowCharacterIndicators(AppPreference.candidate_character_type_indicators_preference)
            setShowCharacterTypeLabels(AppPreference.candidate_character_type_labels_preference)
            setCharacterIndicatorStyles(AppPreference.candidateIndicatorStyles)
            setShowDictionaryCandidateLabels(AppPreference.show_dictionary_candidate_labels_preference)
            setCandidateYomiMode(AppPreference.live_conversion_candidate_yomi_mode)
            setCandidateYomiTextSize(AppPreference.live_conversion_candidate_yomi_size.toFloat())
            setShowCandidateYomiForLiveConversion((AppPreference.live_conversion_preference ?: false) && (AppPreference.live_conversion_candidate_yomi_preference ?: false))
            if (skin != null && skinColors != null) {
                setCandidateTextColor(skinColors.text)
                setCandidateItemColors(if (skinColors.cupertinoClassic) Color.TRANSPARENT else skin.palette.background, skinColors.pressed)
                setCandidateEmptyPopupColors(skinColors.background, skinColors.text)
            } else if (AppPreference.theme_mode == "custom") {
                setCandidateTextColor(AppPreference.custom_theme_candidate_text_color)
                setCandidateItemColors(AppPreference.custom_theme_candidate_item_bg_color, AppPreference.custom_theme_candidate_item_pressed_bg_color)
                setCandidateEmptyPopupColors(AppPreference.custom_theme_candidate_empty_popup_bg_color, AppPreference.custom_theme_candidate_empty_popup_text_color)
            }
            suggestions = createCandidateHeightPreviewCandidates().mapIndexed { index, candidate ->
                if (index == 0) candidate.copy(dictionaryOrigins = setOf(CandidateDictionaryOrigin.USER_DICTIONARY)) else candidate
            }
        }
        adapter = sampleAdapter
        if (AppPreference.candidate_tab_preference) {
            val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            AppPreference.candidate_tab_order.forEachIndexed { index, tab ->
                val title = when (tab) {
                    CandidateTab.PREDICTION -> R.string.candidate_display_tab_prediction
                    CandidateTab.CONVERSION -> R.string.candidate_display_tab_conversion
                    CandidateTab.EISUKANA -> R.string.candidate_display_tab_alphanumeric
                }
                tabs.addView(TextView(context).apply {
                    setText(title); gravity = Gravity.CENTER
                    alpha = if (index == 0) 1f else .7f
                    setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            container.addView(tabs)
        }
        val rows = AppPreference.getCandidateColumn(landscape).toIntOrNull()?.coerceIn(1, 3) ?: 1
        val height = AppPreference.getCandidateVisibleHeightDp(landscape, rows.toString()).coerceIn(30, 180)
        val recycler = RecyclerView(context).apply {
            skinColors?.let { setBackgroundColor(it.background) }
            layoutManager = if (rows == 1) LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                else GridLayoutManager(context, rows, RecyclerView.HORIZONTAL, false)
            adapter = sampleAdapter
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, _ -> true }
        }
        container.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (height * context.resources.displayMetrics.density).toInt()))
    }

    override fun onDetached() {
        adapter?.release()
        adapter = null
        super.onDetached()
    }
}
