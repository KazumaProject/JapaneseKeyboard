package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.CinematicWaveSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectQuality
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SprayPaintSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

internal data class KeyboardTouchEffectPreferenceVisibility(
    val showQuality: Boolean,
    val showColorMode: Boolean,
    val showFixedColor: Boolean,
    val showPalette: Boolean,
    val showCinematicWaveSettings: Boolean,
    val showCinematicWaveCustomColors: Boolean,
    val showCinematicWaveSecondaryColor: Boolean
)

internal fun resolveKeyboardTouchEffectPreferenceVisibility(
    effectType: String,
    colorMode: String
): KeyboardTouchEffectPreferenceVisibility {
    val normalizedEffect = KeyboardTouchEffectType.normalize(effectType)
    val isInk = KeyboardTouchEffectType.isLiquidInk(normalizedEffect) ||
        KeyboardTouchEffectType.isAuroraInk(normalizedEffect)
    val isSprayPaint = KeyboardTouchEffectType.isSprayPaint(normalizedEffect)
    val isLuminousBlob = KeyboardTouchEffectType.isLuminousBlob(normalizedEffect)
    val isCinematicWave = KeyboardTouchEffectType.isCinematicWave(normalizedEffect)
    val isEffectEnabled = KeyboardTouchEffectType.isEnabled(normalizedEffect)
    val supportsColor = isInk || isSprayPaint || isLuminousBlob
    val isCinematicCustom =
        colorMode == CinematicWaveSettings.COLOR_MODE_CUSTOM || colorMode == "custom"
    return KeyboardTouchEffectPreferenceVisibility(
        showQuality = isEffectEnabled && !isCinematicWave,
        showColorMode = supportsColor,
        showFixedColor = supportsColor && colorMode == "fixed",
        showPalette = isSprayPaint,
        showCinematicWaveSettings = isCinematicWave,
        showCinematicWaveCustomColors = isCinematicWave && isCinematicCustom,
        showCinematicWaveSecondaryColor = isCinematicWave && isCinematicCustom
    )
}

@AndroidEntryPoint
open class CommonPreferenceFragment : PreferenceFragmentCompat() {

    companion object {
        const val ARG_HIGHLIGHT_PREFERENCE_KEY = "highlightPreferenceKey"
        private const val LONG_PRESS_TIMEOUT_MIN_MS = 100
        private const val LONG_PRESS_TIMEOUT_MAX_MS = 2000
        private const val LONG_PRESS_TIMEOUT_DEFAULT_MS = 300
    }

    @get:XmlRes
    protected open val preferencesXmlRes: Int = R.xml.pref_common

    @Inject
    lateinit var appPreference: AppPreference

    private var count = 0

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val json = AppPreference.exportAllToJson()
                writeTextToUri(uri, json)
            }.onSuccess {
                toast("Backup exported")
            }.onFailure {
                toast("Export failed: ${it.message}")
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val json = readTextFromUri(uri)
                AppPreference.importAllFromJson(json, replaceAll = true)

                // 旧→新キー移行などがあるなら復元後に実行
                AppPreference.migrateSumirePreferenceIfNeeded()
            }.onSuccess {
                toast("Backup imported")
                requireActivity().recreate()
            }.onFailure {
                toast("Import failed: ${it.message}")
            }
        }

    private val keyboardBackgroundImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                appPreference.keyboard_background_image_uri = uri.toString()
            }.onSuccess {
                toast(getString(R.string.keyboard_background_image_saved))
            }.onFailure {
                toast(
                    getString(
                        R.string.keyboard_background_image_failed,
                        it.message ?: "unknown"
                    )
                )
            }
            updateKeyboardBackgroundImagePreferenceState()
        }

    private val keyboardBackgroundVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val oldUri = appPreference.keyboard_background_video_uri
                if (oldUri.isNotBlank() && oldUri != uri.toString()) {
                    requireContext().contentResolver.releasePersistableUriPermission(
                        oldUri.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                appPreference.keyboard_background_video_uri = uri.toString()
            }.onSuccess {
                toast(getString(R.string.keyboard_background_video_saved))
            }.onFailure {
                toast(
                    getString(
                        R.string.keyboard_background_video_failed,
                        it.message ?: "unknown"
                    )
                )
            }
            updateKeyboardBackgroundVideoPreferenceState()
        }

    // ヘルパーを class 内に追記
    private fun readTextFromUri(uri: Uri): String {
        val cr = requireContext().contentResolver
        cr.openInputStream(uri).use { input ->
            if (input == null) error("Cannot open input stream")
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
                val sb = StringBuilder()
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    sb.append(line).append('\n')
                }
                return sb.toString()
            }
        }
    }

    private fun writeTextToUri(uri: Uri, text: String) {
        val cr = requireContext().contentResolver
        cr.openOutputStream(uri).use { out ->
            if (out == null) error("Cannot open output stream")
            OutputStreamWriter(out, Charsets.UTF_8).use { w ->
                w.write(text)
                w.flush()
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun updateKeyboardBackgroundImagePreferenceState() {
        val uriString = appPreference.keyboard_background_image_uri
        val selectPreference = findPreference<Preference>("keyboard_background_image_select_preference")
        val clearPreference = findPreference<Preference>("keyboard_background_image_clear_preference")

        if (uriString.isBlank()) {
            selectPreference?.summary = getString(R.string.keyboard_background_image_not_set)
            clearPreference?.isEnabled = false
            return
        }

        val displayName = runCatching {
            uriString.toUri().lastPathSegment ?: uriString
        }.getOrDefault(uriString)
        selectPreference?.summary = getString(
            R.string.keyboard_background_image_selected_summary,
            displayName
        )
        clearPreference?.isEnabled = true
    }

    private fun releaseKeyboardBackgroundUriPermissionIfNeeded() {
        val uriString = appPreference.keyboard_background_image_uri
        if (uriString.isBlank()) return
        runCatching {
            requireContext().contentResolver.releasePersistableUriPermission(
                uriString.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun updateKeyboardBackgroundVideoPreferenceState() {
        val uriString = appPreference.keyboard_background_video_uri
        val selectPreference = findPreference<Preference>("keyboard_background_video_select_preference")
        val clearPreference = findPreference<Preference>("keyboard_background_video_clear_preference")

        if (uriString.isBlank()) {
            selectPreference?.summary = getString(R.string.keyboard_background_video_not_set)
            clearPreference?.isEnabled = false
            return
        }

        val displayName = runCatching {
            uriString.toUri().lastPathSegment ?: uriString
        }.getOrDefault(uriString)
        selectPreference?.summary = getString(
            R.string.keyboard_background_video_selected_summary,
            displayName
        )
        clearPreference?.isEnabled = true
    }

    private fun releaseKeyboardBackgroundVideoUriPermissionIfNeeded() {
        val uriString = appPreference.keyboard_background_video_uri
        if (uriString.isBlank()) return
        runCatching {
            requireContext().contentResolver.releasePersistableUriPermission(
                uriString.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun updateKeyboardTouchEffectPreferenceState(
        effectType: String = appPreference.keyboard_touch_effect_type_preference,
        colorMode: String = appPreference.keyboard_touch_effect_color_mode_preference,
        cinematicWaveColorMode: String =
            appPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference,
        cinematicWaveSecondaryAuto: Boolean =
            appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference
    ) {
        val normalizedEffect = KeyboardTouchEffectType.normalize(effectType)
        val normalizedCinematicColorMode =
            CinematicWaveSettings.normalizeColorMode(cinematicWaveColorMode)
        val visibility = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = normalizedEffect,
            colorMode = if (KeyboardTouchEffectType.isCinematicWave(normalizedEffect)) {
                normalizedCinematicColorMode
            } else {
                colorMode
            }
        )
        findPreference<ListPreference>("keyboard_touch_effect_quality_preference")?.isVisible =
            visibility.showQuality
        findPreference<ListPreference>("keyboard_touch_effect_color_mode_preference")?.isVisible =
            visibility.showColorMode

        val fixedColorPreference =
            findPreference<Preference>("keyboard_touch_effect_color_preference")

        fixedColorPreference?.isVisible = visibility.showFixedColor

        fixedColorPreference?.summary = if (visibility.showFixedColor) {
            getString(
                R.string.keyboard_touch_effect_color_summary_current,
                String.format("#%08X", appPreference.keyboard_touch_effect_color_preference)
            )
        } else {
            getString(R.string.keyboard_touch_effect_color_summary)
        }

        findPreference<ListPreference>("keyboard_touch_effect_palette_preference")?.isVisible =
            visibility.showPalette

        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_color_mode_preference"
        )?.isVisible = visibility.showCinematicWaveSettings

        val showCinematicCustomColors = visibility.showCinematicWaveCustomColors
        val primaryPreference =
            findPreference<Preference>(
                "keyboard_touch_effect_cinematic_wave_primary_color_preference"
            )
        primaryPreference?.isVisible = showCinematicCustomColors
        primaryPreference?.summary = if (showCinematicCustomColors) {
            getString(
                R.string.keyboard_touch_effect_cinematic_wave_primary_color_summary_current,
                String.format(
                    "#%08X",
                    appPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference
                )
            )
        } else {
            getString(R.string.keyboard_touch_effect_cinematic_wave_primary_color_summary)
        }

        findPreference<SwitchPreferenceCompat>(
            "keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference"
        )?.isVisible = showCinematicCustomColors

        val secondaryPreference =
            findPreference<Preference>(
                "keyboard_touch_effect_cinematic_wave_secondary_color_preference"
            )
        val showSecondaryColor = showCinematicCustomColors && !cinematicWaveSecondaryAuto
        secondaryPreference?.isVisible = showSecondaryColor
        secondaryPreference?.summary = if (showSecondaryColor) {
            getString(
                R.string.keyboard_touch_effect_cinematic_wave_secondary_color_summary_current,
                String.format(
                    "#%08X",
                    appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference
                )
            )
        } else {
            getString(R.string.keyboard_touch_effect_cinematic_wave_secondary_color_summary)
        }

        findPreference<SeekBarPreference>(
            "keyboard_touch_effect_cinematic_wave_opacity_percent_preference"
        )?.isVisible = visibility.showCinematicWaveSettings
        findPreference<SeekBarPreference>(
            "keyboard_touch_effect_cinematic_wave_intensity_percent_preference"
        )?.isVisible = visibility.showCinematicWaveSettings
        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_motion_preference"
        )?.isVisible = visibility.showCinematicWaveSettings
        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_touch_response_preference"
        )?.isVisible = visibility.showCinematicWaveSettings
        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_quality_preference"
        )?.isVisible = visibility.showCinematicWaveSettings
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(preferencesXmlRes, rootKey)

        val packageInfo = requireContext().packageManager.getPackageInfo(
            requireContext().packageName, 0
        )

        val languageSwitchPreference =
            findPreference<SwitchPreferenceCompat>("app_setting_language_preference")
        languageSwitchPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val state = newValue as Boolean
                if (state) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                true
            }
        }

        findPreference<Preference>("pref_backup_export")?.setOnPreferenceClickListener {
            val fileName = "sumire_prefs_backup_${System.currentTimeMillis()}.json"
            exportLauncher.launch(fileName)
            true
        }

        findPreference<Preference>("pref_backup_import")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*"))
            true
        }

        val candidateColumnListPreference =
            findPreference<ListPreference>("candidate_column_preference")
        candidateColumnListPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    appPreference.setCandidateColumnAndSyncHeight(
                        isLandscape = false,
                        column = newValue
                    )
                }
                true
            }
        }

        findPreference<ListPreference>("candidate_column_landscape_preference")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    appPreference.setCandidateColumnAndSyncHeight(
                        isLandscape = true,
                        column = newValue
                    )
                }
                true
            }
        }

        val appVersionPreference = findPreference<Preference>("app_version_preference")
        appVersionPreference?.apply {
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.longVersionCode}"
            } else {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.versionCode}"
            }
            setOnPreferenceClickListener {
                count += 1
                true
            }
        }

        val customRomajiPreference = findPreference<Preference>("custom_romaji_preference")
        customRomajiPreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.romajiMapFragment
            )
            true
        }

        val shortCutToolbarItemSettingPreference = findPreference<Preference>(
            "shortcut_toolbar_item_preference"
        )
        shortCutToolbarItemSettingPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.shortcutSettingFragment
                )
                true
            }
        }

        findPreference<Preference>("shortcut_toolbar_size_setting_fragment_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.shortcutToolbarSizeSettingFragment
                )
                true
            }
        }

        val candidateTabOrderPreference =
            findPreference<Preference>("candidate_tab_order_preference")
        candidateTabOrderPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.candidateTabOrderFragment
                )
                true
            }
        }

        val keyboardSelectionPreference =
            findPreference<Preference>("keyboard_selection_preference")

        keyboardSelectionPreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.keyboardSelectionFragment
            )
            true
        }

        val keyboardLetterSizePreference =
            findPreference<Preference>("keyboard_key_letter_size_fragment_preference")

        keyboardLetterSizePreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.keyCandidateLetterSizeFragment
            )
            true
        }

        findPreference<Preference>("keyboard_background_image_select_preference")?.apply {
            setOnPreferenceClickListener {
                keyboardBackgroundImageLauncher.launch(arrayOf("image/*"))
                true
            }
        }

        findPreference<ListPreference>("keyboard_background_image_display_mode_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        findPreference<Preference>("keyboard_background_image_clear_preference")?.apply {
            setOnPreferenceClickListener {
                releaseKeyboardBackgroundUriPermissionIfNeeded()
                appPreference.keyboard_background_image_uri = ""
                toast(getString(R.string.keyboard_background_image_cleared))
                updateKeyboardBackgroundImagePreferenceState()
                true
            }
        }

        findPreference<Preference>("keyboard_background_video_select_preference")?.apply {
            setOnPreferenceClickListener {
                keyboardBackgroundVideoLauncher.launch(arrayOf("video/*"))
                true
            }
        }

        findPreference<ListPreference>("keyboard_background_video_quality_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        findPreference<ListPreference>("keyboard_touch_effect_type_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedEffect = appPreference.keyboard_touch_effect_type_preference
            if (value != normalizedEffect) {
                value = normalizedEffect
            }
            setOnPreferenceChangeListener { _, newValue ->
                val nextEffect = KeyboardTouchEffectType.normalize(newValue as? String)
                appPreference.keyboard_touch_effect_type_preference = nextEffect
                updateKeyboardTouchEffectPreferenceState(effectType = nextEffect)
                true
            }
        }

        findPreference<ListPreference>("keyboard_touch_effect_quality_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedQuality = appPreference.keyboard_touch_effect_quality_preference
            if (value != normalizedQuality) {
                value = normalizedQuality
            }
            setOnPreferenceChangeListener { _, newValue ->
                val nextQuality = KeyboardTouchEffectQuality.normalize(newValue as? String)
                appPreference.keyboard_touch_effect_quality_preference = nextQuality
                true
            }
        }

        findPreference<Preference>("keyboard_background_video_clear_preference")?.apply {
            setOnPreferenceClickListener {
                releaseKeyboardBackgroundVideoUriPermissionIfNeeded()
                appPreference.keyboard_background_video_uri = ""
                toast(getString(R.string.keyboard_background_video_cleared))
                updateKeyboardBackgroundVideoPreferenceState()
                true
            }
        }

        findPreference<ListPreference>("keyboard_touch_effect_color_mode_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedMode = appPreference.keyboard_touch_effect_color_mode_preference
            if (value != normalizedMode) {
                value = normalizedMode
            }
            setOnPreferenceChangeListener { _, newValue ->
                val nextMode = when (newValue as? String) {
                    "fixed" -> "fixed"
                    "palette" -> "palette"
                    "theme" -> "theme"
                    else -> "random"
                }
                appPreference.keyboard_touch_effect_color_mode_preference = nextMode
                updateKeyboardTouchEffectPreferenceState(colorMode = nextMode)
                true
            }
        }

        findPreference<Preference>("keyboard_touch_effect_color_preference")?.apply {
            setOnPreferenceClickListener {
                showKeyboardTouchEffectColorPickerDialog()
                true
            }
        }

        findPreference<ListPreference>("keyboard_touch_effect_palette_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedPalette = appPreference.keyboard_touch_effect_palette_preference
            if (value != normalizedPalette) {
                value = normalizedPalette
            }
            setOnPreferenceChangeListener { _, newValue ->
                val nextPalette = SprayPaintSettings.normalizePalette(newValue as? String)
                appPreference.keyboard_touch_effect_palette_preference = nextPalette
                true
            }
        }

        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_color_mode_preference"
        )?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedMode =
                appPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference
            if (value != normalizedMode) {
                value = normalizedMode
            }
            setOnPreferenceChangeListener { _, newValue ->
                val nextMode = CinematicWaveSettings.normalizeColorMode(newValue as? String)
                appPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference =
                    nextMode
                updateKeyboardTouchEffectPreferenceState(cinematicWaveColorMode = nextMode)
                true
            }
        }

        findPreference<Preference>(
            "keyboard_touch_effect_cinematic_wave_primary_color_preference"
        )?.apply {
            setOnPreferenceClickListener {
                showCinematicWaveColorPickerDialog(primary = true)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(
            "keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference"
        )?.apply {
            isChecked =
                appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: true
                appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference =
                    enabled
                updateKeyboardTouchEffectPreferenceState(cinematicWaveSecondaryAuto = enabled)
                true
            }
        }

        findPreference<Preference>(
            "keyboard_touch_effect_cinematic_wave_secondary_color_preference"
        )?.apply {
            setOnPreferenceClickListener {
                showCinematicWaveColorPickerDialog(primary = false)
                true
            }
        }

        findPreference<SeekBarPreference>(
            "keyboard_touch_effect_cinematic_wave_opacity_percent_preference"
        )?.apply {
            value = appPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference
            setOnPreferenceChangeListener { _, newValue ->
                val nextValue = (newValue as? Int ?: value).coerceIn(18, 68)
                appPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference =
                    nextValue
                true
            }
        }

        findPreference<SeekBarPreference>(
            "keyboard_touch_effect_cinematic_wave_intensity_percent_preference"
        )?.apply {
            value = appPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference
            setOnPreferenceChangeListener { _, newValue ->
                val nextValue = (newValue as? Int ?: value).coerceIn(35, 180)
                appPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference =
                    nextValue
                true
            }
        }

        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_motion_preference"
        )?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedMotion =
                appPreference.keyboard_touch_effect_cinematic_wave_motion_preference
            if (value != normalizedMotion) {
                value = normalizedMotion
            }
            setOnPreferenceChangeListener { _, newValue ->
                appPreference.keyboard_touch_effect_cinematic_wave_motion_preference =
                    CinematicWaveSettings.normalizeMotion(newValue as? String)
                true
            }
        }

        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_touch_response_preference"
        )?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedResponse =
                appPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference
            if (value != normalizedResponse) {
                value = normalizedResponse
            }
            setOnPreferenceChangeListener { _, newValue ->
                appPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference =
                    CinematicWaveSettings.normalizeTouchResponse(newValue as? String)
                true
            }
        }

        findPreference<ListPreference>(
            "keyboard_touch_effect_cinematic_wave_quality_preference"
        )?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val normalizedQuality =
                appPreference.keyboard_touch_effect_cinematic_wave_quality_preference
            if (value != normalizedQuality) {
                value = normalizedQuality
            }
            setOnPreferenceChangeListener { _, newValue ->
                appPreference.keyboard_touch_effect_cinematic_wave_quality_preference =
                    CinematicWaveSettings.normalizeQuality(newValue as? String)
                true
            }
        }

        updateKeyboardBackgroundImagePreferenceState()
        updateKeyboardBackgroundVideoPreferenceState()
        updateKeyboardTouchEffectPreferenceState()

        val keyboardSizeLandscapePreference =
            findPreference<Preference>("keyboard_screen_landscape_preference")

        keyboardSizeLandscapePreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.keyboardSizeLandscapeFragment
            )
            true
        }

        val candidateHeightFragmentSetting =
            findPreference<Preference>("candidate_view_height_setting_fragment_preference")
        candidateHeightFragmentSetting?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.candidateViewHeightSettingFragment
                )
                true
            }
        }

        val candidateHeightLandscapeFragmentSetting =
            findPreference<Preference>("candidate_view_height_landscape_setting_fragment_preference")
        candidateHeightLandscapeFragmentSetting?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.candidateHeightLandscapeSettingFragment
                )
                true
            }
        }

        val clipBoardHistoryPreference =
            findPreference<Preference>("clipboard_history_preference_fragment")
        clipBoardHistoryPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.clipboardHistoryFragment
                )
                true
            }
        }

        val symbolKeyboardOrderPreference = findPreference<ListPreference>("symbol_mode_preference")
        symbolKeyboardOrderPreference?.apply {
            summary = when (value) {
                "EMOJI" -> getString(R.string.emoji)
                "EMOTICON" -> getString(R.string.emoticon)
                "SYMBOL" -> getString(R.string.symbol)
                "CLIPBOARD" -> getString(R.string.clipboard_history)
                else -> getString(R.string.choose_initial_symbol_keyboard_open)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = when (newValue) {
                    "EMOJI" -> getString(R.string.emoji)
                    "EMOTICON" -> getString(R.string.emoticon)
                    "SYMBOL" -> getString(R.string.symbol)
                    "CLIPBOARD" -> getString(R.string.clipboard_history)
                    else -> getString(R.string.choose_initial_symbol_keyboard_open)
                }
                true
            }
        }

        findPreference<ListPreference>("default_emoji_skin_tone_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        syncDefaultEmojiSkinTonePreference()

        findPreference<SeekBarPreference>("flick_sensitivity_preference")?.apply {
            summary = when (this.value) {
                in 0..50 -> getString(R.string.sensitivity_very_high)
                in 51..90 -> getString(R.string.sensitivity_high)
                in 91..110 -> getString(R.string.sensitivity_normal)
                in 111..150 -> getString(R.string.sensitivity_less)
                in 151..200 -> getString(R.string.sensitivity_low)
                else -> ""
            }
            setOnPreferenceChangeListener { pref, newValue ->
                val sbp = pref as SeekBarPreference
                val raw = (newValue as Int)
                val inc = sbp.seekBarIncrement
                val rounded = (raw + inc / 2) / inc * inc
                summary = when (rounded) {
                    in 0..50 -> getString(R.string.sensitivity_very_high)
                    in 51..90 -> getString(R.string.sensitivity_high)
                    in 91..110 -> getString(R.string.sensitivity_normal)
                    in 111..150 -> getString(R.string.sensitivity_less)
                    in 151..200 -> getString(R.string.sensitivity_low)
                    else -> ""
                }
                return@setOnPreferenceChangeListener if (rounded != raw) {
                    sbp.value = rounded
                    false
                } else {
                    true
                }
            }
        }

        findPreference<SeekBarPreference>("key_sound_volume_percent_preference")?.apply {
            updateKeySoundVolumeSummary(value)
            setOnPreferenceChangeListener { _, newValue ->
                updateKeySoundVolumeSummary(newValue as Int)
                true
            }
        }

        findPreference<Preference>("long_press_timeout_preference")?.apply {
            updateLongPressTimeoutSummary()
            setOnPreferenceClickListener {
                showLongPressTimeoutDialog()
                true
            }
        }

        val keyboardUndoEnablePreference =
            findPreference<SwitchPreferenceCompat>("undo_enable_preference")
        keyboardUndoEnablePreference?.apply {
            appPreference.undo_enable_preference?.let {
                this.summary = if (it) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary = if (newValue == true) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
                true
            }
        }

        findPreference<Preference>("delete_key_flick_left_targets_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.deleteKeyFlickTargetsFragment
                )
                true
            }
        }

        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.apply {
            updateCursorMoveTargetPairsSummary()
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.cursorMoveTargetPairsFragment
                )
                true
            }
        }

        val keyboardSettingPreference = findPreference<Preference>("keyboard_screen_preference")

        keyboardSettingPreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.keyboardSettingFragment
            )
            true
        }

        val openSourcePreference = findPreference<Preference>("preference_open_source")

        openSourcePreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.openSourceFragment
            )
            true
        }

        val seedColorPickerPreference =
            findPreference<Preference>("keyboard_theme_fragment_preference")
        seedColorPickerPreference?.apply {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setOnPreferenceClickListener {
                showColorPickerDialog()
                true
            }
        }

        setupRoutePreferences()
        onCommonPreferencesCreated()
        applyLegacySearchResultFilterIfNeeded()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollToHighlightedPreferenceAfterLayout(view)
    }

    protected open fun onCommonPreferencesCreated() = Unit

    private fun setupRoutePreferences() {
        val routeTargets = mapOf(
            "setting_route_keyboard_display" to R.id.keyboardDisplayPreferenceFragment,
            "setting_route_input_method" to R.id.inputMethodPreferenceFragment,
            "setting_route_candidate_conversion" to R.id.candidateConversionPreferenceFragment,
            "setting_route_dictionary" to R.id.dictionaryPreferenceFragment,
            "setting_route_ai_conversion" to R.id.aiConversionPreferenceFragment,
            "setting_route_clipboard_shortcut" to R.id.clipboardShortcutPreferenceFragment,
            "setting_route_operation_feedback" to R.id.operationFeedbackPreferenceFragment,
            "setting_route_general_info" to R.id.generalInfoPreferenceFragment,
            "setting_route_advanced" to R.id.advancedPreferenceFragment,
            "setting_route_legacy_settings" to R.id.settingMainFragment,
            "setting_route_keyboard_theme" to R.id.keyboardThemeFragment,
            "setting_route_kana_preferences" to R.id.kanaPreferenceFragment,
            "setting_route_qwerty_preferences" to R.id.qwertyPreferenceFragment,
            "setting_route_sumire_preferences" to R.id.sumirePreferenceFragment,
            "setting_route_custom_keyboard_preferences" to R.id.customKeyboardPreferenceFragment,
            "setting_route_tablet_preferences" to R.id.tabletPreferenceFragment,
            "setting_route_hardware_keyboard_preferences" to R.id.hardwareKeyboardPreferenceFragment,
            "setting_route_common_preferences" to R.id.commonPreferenceFragment,
            "setting_route_zenz_preferences" to R.id.zenzPreferenceFragment,
            "setting_route_gemma_preferences" to R.id.gemmaPreferenceFragment,
        )

        routeTargets.forEach { (key, destinationId) ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                navigateSafely(destinationId)
                true
            }
        }

        findPreference<Preference>("setting_route_zenz_preferences")?.isVisible =
            AppVariantConfig.hasZenz
        findPreference<Preference>("setting_route_gemma_preferences")?.isVisible =
            AppVariantConfig.hasGemma
    }

    override fun onResume() {
        super.onResume()
        syncDefaultEmojiSkinTonePreference()
        updateCursorMoveTargetPairsSummary()
    }

    // リーク対策: RecyclerViewの参照を断ち切る
    override fun onDestroyView() {
        try {
            listView.adapter = null
        } catch (e: Exception) {
            // Viewが生成されていない場合などを考慮して例外は無視
        }
        super.onDestroyView()
    }

    private fun syncDefaultEmojiSkinTonePreference() {
        findPreference<ListPreference>("default_emoji_skin_tone_preference")?.apply {
            val savedSkinTone = appPreference.default_emoji_skin_tone_preference
            if (value != savedSkinTone) {
                value = savedSkinTone
            }
        }
    }

    private fun updateCursorMoveTargetPairsSummary() {
        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.summary =
            getString(
                R.string.cursor_move_target_pairs_summary_current,
                appPreference.cursor_move_after_commit_target_pairs_preference.joinToString(" ")
                    .ifBlank { getString(R.string.keyboard_background_image_not_set) }
            )
    }

    @SuppressLint("CheckResult")
    private fun showColorPickerDialog() {
        val initialColor = appPreference.seedColor
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_theme_dialog_title))
            colorChooser(
                colors = intArrayOf(
                    0x00000000,
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.violet),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange2
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_dark
                    ),
                ),
                initialSelection = initialColor,
                allowCustomArgb = true
            ) { _, color ->
                appPreference.seedColor = color
                requireActivity().recreate()
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

    @SuppressLint("CheckResult")
    private fun showKeyboardTouchEffectColorPickerDialog() {
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_touch_effect_color_title))
            colorChooser(
                colors = intArrayOf(
                    Color.rgb(17, 17, 17),
                    Color.rgb(38, 70, 120),
                    Color.rgb(180, 48, 42),
                    Color.rgb(50, 110, 78),
                    Color.rgb(120, 70, 150)
                ),
                initialSelection = appPreference.keyboard_touch_effect_color_preference,
                allowCustomArgb = true
            ) { _, color ->
                appPreference.keyboard_touch_effect_color_preference = color
                updateKeyboardTouchEffectPreferenceState(colorMode = "fixed")
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

    @SuppressLint("CheckResult")
    private fun showCinematicWaveColorPickerDialog(primary: Boolean) {
        val titleRes = if (primary) {
            R.string.keyboard_touch_effect_cinematic_wave_primary_color_title
        } else {
            R.string.keyboard_touch_effect_cinematic_wave_secondary_color_title
        }
        val initialColor = if (primary) {
            appPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference
        } else {
            appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference
        }

        MaterialDialog(requireContext()).show {
            title(text = getString(titleRes))
            colorChooser(
                colors = intArrayOf(
                    Color.rgb(65, 217, 255),
                    Color.rgb(139, 92, 255),
                    Color.rgb(210, 62, 134),
                    Color.rgb(255, 174, 64),
                    Color.rgb(140, 170, 190)
                ),
                initialSelection = initialColor,
                allowCustomArgb = true
            ) { _, color ->
                if (primary) {
                    appPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference =
                        color
                } else {
                    appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference =
                        color
                }
                appPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference =
                    CinematicWaveSettings.COLOR_MODE_CUSTOM
                findPreference<ListPreference>(
                    "keyboard_touch_effect_cinematic_wave_color_mode_preference"
                )?.value = CinematicWaveSettings.COLOR_MODE_CUSTOM
                updateKeyboardTouchEffectPreferenceState(
                    cinematicWaveColorMode = CinematicWaveSettings.COLOR_MODE_CUSTOM
                )
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

    private fun updateKeySoundVolumeSummary(value: Int) {
        findPreference<SeekBarPreference>("key_sound_volume_percent_preference")?.summary =
            if (value == 0) {
                getString(R.string.key_sound_volume_system_default)
            } else {
                getString(R.string.key_sound_volume_percent, value)
            }
    }

    private fun updateLongPressTimeoutSummary() {
        val value =
            (appPreference.long_press_timeout_preference ?: LONG_PRESS_TIMEOUT_DEFAULT_MS)
                .coerceIn(LONG_PRESS_TIMEOUT_MIN_MS, LONG_PRESS_TIMEOUT_MAX_MS)
        findPreference<Preference>("long_press_timeout_preference")?.summary =
            getString(R.string.long_press_timeout_preference_value, value)
    }

    private fun showLongPressTimeoutDialog() {
        val dialogView =
            layoutInflater.inflate(R.layout.dialog_long_press_timeout_preference, null)
        val valueText =
            dialogView.findViewById<TextView>(R.id.long_press_timeout_value_text)
        val seekBar =
            dialogView.findViewById<SeekBar>(R.id.long_press_timeout_seekbar)

        val initialValue =
            (appPreference.long_press_timeout_preference ?: LONG_PRESS_TIMEOUT_DEFAULT_MS)
                .coerceIn(LONG_PRESS_TIMEOUT_MIN_MS, LONG_PRESS_TIMEOUT_MAX_MS)

        fun updateLabel(value: Int) {
            valueText.text = getString(R.string.long_press_timeout_preference_value, value)
        }

        seekBar.max = LONG_PRESS_TIMEOUT_MAX_MS - LONG_PRESS_TIMEOUT_MIN_MS
        seekBar.progress = initialValue - LONG_PRESS_TIMEOUT_MIN_MS
        updateLabel(initialValue)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress + LONG_PRESS_TIMEOUT_MIN_MS)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.long_press_timeout_preference_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                appPreference.long_press_timeout_preference =
                    seekBar.progress + LONG_PRESS_TIMEOUT_MIN_MS
                updateLongPressTimeoutSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.reset_to_default, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                seekBar.progress = LONG_PRESS_TIMEOUT_DEFAULT_MS - LONG_PRESS_TIMEOUT_MIN_MS
            }
        }
        dialog.show()
    }

}
