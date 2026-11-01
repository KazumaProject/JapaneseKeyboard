package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceGroup
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.showKeyboardThemeColorPicker
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.conditionVisible
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class CandidateDisplayPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes = R.xml.pref_candidate_display
    private lateinit var geometries: KeyboardGeometryPreferences
    private var keyboard = PreviewKeyboard.TENKEY
    private var landscape = false
    private var requestedPreferenceKey: String? = null
    private val verticalOnly = setOf("candidate_display_right_to_left", "candidate_display_wrap", "candidate_display_spacing", "candidate_display_line_spacing")

    private val colors = listOf(
        "theme_custom_candidate_text_color",
        "theme_custom_candidate_item_bg_color",
        "theme_custom_candidate_item_pressed_bg_color",
        "theme_custom_candidate_empty_popup_bg_color",
        "theme_custom_candidate_empty_popup_text_color",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geometries = KeyboardGeometryPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        keyboard = (savedInstanceState?.getString("candidateKeyboard") ?: arguments?.getString("geometryKeyboard"))
            ?.let { runCatching { PreviewKeyboard.valueOf(it) }.getOrNull() }
            ?.takeIf { it in setOf(PreviewKeyboard.TENKEY, PreviewKeyboard.SUMIRE, PreviewKeyboard.QWERTY, PreviewKeyboard.ROMAJI) }
            ?: PreviewKeyboard.TENKEY
        requestedPreferenceKey = savedInstanceState?.getString("requestedPreferenceKey")
            ?: arguments?.getString(ARG_HIGHLIGHT_PREFERENCE_KEY)
        landscape = savedInstanceState?.getBoolean("candidateLandscape")
            ?: arguments?.takeIf { it.containsKey("isLandscape") }?.getBoolean("isLandscape")
            ?: when (requestedPreferenceKey) {
                "candidate_view_height_landscape_setting_fragment_preference", "candidate_column_landscape_preference" -> true
                "candidate_view_height_setting_fragment_preference", "candidate_column_preference" -> false
                else -> resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("candidateKeyboard", keyboard.name)
        outState.putBoolean("candidateLandscape", landscape)
        outState.putString("requestedPreferenceKey", requestedPreferenceKey)
        super.onSaveInstanceState(outState)
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        super.onPreferencesReady(savedInstanceState, rootKey)
        fun removeUnusedIconSpace(group: PreferenceGroup) {
            for (i in 0 until group.preferenceCount) {
                val item = group.getPreference(i)
                item.isIconSpaceReserved = false
                if (item is PreferenceGroup) removeUnusedIconSpace(item)
            }
        }
        preferenceScreen?.let(::removeUnusedIconSpace)
        findPreference<ListPreference>("candidate_display_keyboard")?.setOnPreferenceChangeListener { _, value ->
            keyboard = PreviewKeyboard.valueOf(value as String); syncGeometry(); true
        }
        findPreference<ListPreference>("candidate_display_orientation")?.setOnPreferenceChangeListener { _, value ->
            landscape = value == "landscape"; syncGeometry(); true
        }
        findPreference<ListPreference>("candidate_display_placement")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(candidates = GeometryCandidatePlacement.valueOf(value as String)) }; true
        }
        findPreference<SeekBarPreference>("candidate_display_width")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(sideCandidateWidthPercent = value as Int) }; true
        }
        findPreference<SwitchPreferenceCompat>("candidate_display_vertical")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(sideCandidateVerticalText = value as Boolean) }; true
        }
        findPreference<SwitchPreferenceCompat>("candidate_display_right_to_left")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(sideCandidateRightToLeft = value as Boolean) }; true
        }
        findPreference<SwitchPreferenceCompat>("candidate_display_wrap")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(sideCandidateWrapLongText = value as Boolean) }; true
        }
        findPreference<SeekBarPreference>("candidate_display_spacing")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(sideCandidateSpacingDp = value as Int) }; true
        }
        findPreference<SeekBarPreference>("candidate_display_line_spacing")?.setOnPreferenceChangeListener { _, value ->
            saveGeometry { copy(sideCandidateVerticalSpacingDp = value as Int) }; true
        }
        findPreference<Preference>("candidate_display_unavailable")?.setOnPreferenceClickListener {
            val target = geometryKeyboard()
            val geometry = geometries.read(target, landscape)
            val key = when {
                target == GeometryKeyboard.QWERTY -> "candidate_display_keyboard"
                geometry.candidates == GeometryCandidatePlacement.TOP -> "candidate_display_placement"
                requestedPreferenceKey in verticalOnly && !geometry.sideCandidateVerticalText -> "candidate_display_vertical"
                else -> "candidate_display_placement"
            }
            findPreference<Preference>(key)?.performClick()
            true
        }
        findPreference<Preference>("candidate_display_reset_position")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.candidate_display_reset_confirm)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.keyboard_geometry_reset) { _, _ ->
                    geometries.resetCandidateDisplay(geometryKeyboard(), landscape); syncGeometry()
                }.show()
            true
        }
        findPreference<Preference>("keyboard_key_letter_size_fragment_preference")?.setOnPreferenceClickListener { showTextSizeDialog(); true }
        findPreference<Preference>("candidate_display_reset_text")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.candidate_display_reset_text_summary)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.keyboard_geometry_reset) { _, _ ->
                    AppPreference.key_letter_size = 0f
                    AppPreference.candidate_letter_size = 14f
                    refreshPreview()
                }.show()
            true
        }
        findPreference<Preference>("settings_theme_enable_live_conversion_link")?.setOnPreferenceClickListener {
            navigateSafely(R.id.conversionEnginePreferenceFragment, bundleOf(ARG_HIGHLIGHT_PREFERENCE_KEY to "live_conversion_preference")); true
        }
        findPreference<Preference>("candidate_display_enable_colors")?.setOnPreferenceClickListener {
            if (AppPreference.keyboardSkin != KeyboardSkinId.DEFAULT) navigateSafely(R.id.keyboardSkinSelectionFragment)
            else navigateSafely(R.id.keyboardThemeFragment, bundleOf(ARG_HIGHLIGHT_PREFERENCE_KEY to "theme_custom"))
            true
        }
        colors.forEach { key ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener { showColorDialog(key); true }
        }
        syncGeometry()
        syncDependencies()
    }

    override fun onPreferencesResumed() {
        super.onPreferencesResumed()
        syncGeometry()
        syncDependencies()
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (view != null) {
            syncDependencies()
            refreshPreview()
        }
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(listener)
        super.onStop()
    }

    private fun geometryKeyboard() = when (keyboard) {
        PreviewKeyboard.SUMIRE -> GeometryKeyboard.SUMIRE
        PreviewKeyboard.QWERTY, PreviewKeyboard.ROMAJI -> GeometryKeyboard.QWERTY
        else -> GeometryKeyboard.TENKEY
    }

    private fun saveGeometry(change: KeyboardGeometryPreference.() -> KeyboardGeometryPreference) {
        val target = geometryKeyboard()
        geometries.saveCandidateDisplay(target, landscape, geometries.read(target, landscape).change())
        syncGeometry()
    }

    private fun syncGeometry() {
        if (preferenceScreen == null) return
        val target = geometryKeyboard()
        val value = geometries.read(target, landscape)
        findPreference<ListPreference>("candidate_display_keyboard")?.value = keyboard.name
        findPreference<ListPreference>("candidate_display_orientation")?.value = if (landscape) "landscape" else "portrait"
        findPreference<ListPreference>("candidate_display_placement")?.apply { this.value = value.candidates.name; isEnabled = target != GeometryKeyboard.QWERTY }
        findPreference<Preference>("candidate_display_unavailable")?.conditionVisible = true
        val side = target != GeometryKeyboard.QWERTY && value.candidates != GeometryCandidatePlacement.TOP
        val vertical = side && value.sideCandidateVerticalText
        val highlighted = arguments?.getString(ARG_HIGHLIGHT_PREFERENCE_KEY)
        val sideOnly = verticalOnly + setOf("candidate_display_width", "candidate_display_vertical")
        val targetUnavailable = requestedPreferenceKey in sideOnly && (!side || (requestedPreferenceKey in verticalOnly && !vertical))
        findPreference<Preference>("candidate_display_unavailable")?.apply {
            if (side && requestedPreferenceKey in verticalOnly && !vertical) {
                setTitle(R.string.candidate_display_enable_vertical)
                setSummary(R.string.candidate_display_enable_vertical_summary)
            } else {
                setTitle(R.string.candidate_display_unavailable_title)
                setSummary(R.string.candidate_display_unavailable)
            }
        }
        if (targetUnavailable) {
            arguments?.putString(ARG_HIGHLIGHT_PREFERENCE_KEY, "candidate_display_unavailable")
        } else if (highlighted == "candidate_display_unavailable" && requestedPreferenceKey != null) {
            arguments?.putString(ARG_HIGHLIGHT_PREFERENCE_KEY, requestedPreferenceKey)
            view?.post { requestedPreferenceKey?.let(::scrollToPreference) }
        }
        findPreference<SeekBarPreference>("candidate_display_width")?.apply { this.value = value.sideCandidateWidthPercent; conditionVisible = side }
        findPreference<SwitchPreferenceCompat>("candidate_display_vertical")?.apply { isChecked = value.sideCandidateVerticalText; conditionVisible = side }
        findPreference<SwitchPreferenceCompat>("candidate_display_right_to_left")?.apply { isChecked = value.sideCandidateRightToLeft; conditionVisible = vertical }
        findPreference<SwitchPreferenceCompat>("candidate_display_wrap")?.apply { isChecked = value.sideCandidateWrapLongText; conditionVisible = vertical }
        findPreference<SeekBarPreference>("candidate_display_spacing")?.apply { this.value = value.sideCandidateSpacingDp; conditionVisible = vertical }
        findPreference<SeekBarPreference>("candidate_display_line_spacing")?.apply { this.value = value.sideCandidateVerticalSpacingDp; conditionVisible = vertical }
        findPreference<Preference>("candidate_display_reset_position")?.isEnabled = target != GeometryKeyboard.QWERTY
        refreshPreview()
    }

    private fun syncDependencies() {
        if (preferenceScreen == null) return
        val live = AppPreference.live_conversion_preference ?: false
        findPreference<Preference>("live_conversion_candidate_yomi_preference")?.isEnabled = live
        findPreference<Preference>("settings_theme_enable_live_conversion_link")?.conditionVisible = !live
        val custom = AppPreference.theme_mode == "custom" && AppPreference.keyboardSkin == KeyboardSkinId.DEFAULT
        findPreference<Preference>("candidate_display_enable_colors")?.conditionVisible = !custom
        colors.forEach { key ->
            findPreference<Preference>(key)?.apply {
                isEnabled = custom
                summary = String.format(Locale.ROOT, "#%08X", getColor(key))
            }
        }
        findPreference<Preference>("keyboard_key_letter_size_fragment_preference")?.summary =
            String.format(Locale.getDefault(), "%.1f sp", AppPreference.candidate_letter_size ?: 14f)
    }

    private fun refreshPreview() {
        findPreference<CandidatePreviewPreference>("candidate_display_preview")?.apply {
            this.keyboard = this@CandidateDisplayPreferenceFragment.keyboard
            this.landscape = this@CandidateDisplayPreferenceFragment.landscape
            refresh()
        }
    }

    private fun showTextSizeDialog() {
        val context = requireContext()
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0) }
        val label = TextView(context)
        val sample = TextView(context).apply { text = context.getString(R.string.keyboard_geometry_example_hello) }
        val seek = SeekBar(context).apply { max = 300; progress = (((AppPreference.candidate_letter_size ?: 14f) - 10f) * 10f).toInt().coerceIn(0, 300) }
        fun update() { val size = 10f + seek.progress / 10f; label.text = String.format(Locale.getDefault(), "%.1f sp", size); sample.textSize = size }
        update(); box.addView(sample); box.addView(label); box.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = update()
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        MaterialAlertDialogBuilder(context).setTitle(R.string.candidate_display_text_size).setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> AppPreference.candidate_letter_size = 10f + seek.progress / 10f; syncDependencies(); refreshPreview() }
            .show()
    }

    private fun getColor(key: String): Int = when (key) {
        colors[0] -> AppPreference.custom_theme_candidate_text_color
        colors[1] -> AppPreference.custom_theme_candidate_item_bg_color
        colors[2] -> AppPreference.getCustomThemeCandidateItemPressedBgColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.qwety_key_bg_color))
        colors[3] -> AppPreference.custom_theme_candidate_empty_popup_bg_color
        else -> AppPreference.custom_theme_candidate_empty_popup_text_color
    }

    private fun saveColor(key: String, color: Int) {
        when (key) {
            colors[0] -> AppPreference.custom_theme_candidate_text_color = color
            colors[1] -> AppPreference.custom_theme_candidate_item_bg_color = color
            colors[2] -> AppPreference.custom_theme_candidate_item_pressed_bg_color = color
            colors[3] -> AppPreference.custom_theme_candidate_empty_popup_bg_color = color
            colors[4] -> AppPreference.custom_theme_candidate_empty_popup_text_color = color
        }
    }

    private fun showColorDialog(key: String) {
        showKeyboardThemeColorPicker(requireContext(), getColor(key)) { color ->
            saveColor(key, color)
            syncDependencies()
            refreshPreview()
        }
    }
}
