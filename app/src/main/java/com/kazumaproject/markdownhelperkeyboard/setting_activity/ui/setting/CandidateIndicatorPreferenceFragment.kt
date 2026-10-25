package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.*

class CandidateIndicatorPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes = R.xml.pref_candidate_indicator_styles
    private var observing = false
    private var colorDialog: MaterialDialog? = null
    private val settings get() = requireNotNull(preferenceManager.sharedPreferences)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshStyles() }

    override suspend fun preparePreferenceData(context: android.content.Context) {
        CandidateIndicatorSettings.normalize(androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        CandidateIndicatorGroup.entries.forEach { group ->
            for (dark in listOf(false, true)) {
                findPreference<Preference>(CandidateIndicatorSettings.colorKey(group, dark))?.setOnPreferenceClickListener {
                    showColorPicker(group, dark)
                    true
                }
            }
        }
        findPreference<Preference>("candidate_indicator_reset")?.setOnPreferenceClickListener {
            CandidateIndicatorSettings.reset(settings)
            refreshStyles()
            true
        }
        refreshStyles()
    }

    override fun onPreferencesResumed() {
        if (!observing) {
            settings.registerOnSharedPreferenceChangeListener(listener)
            observing = true
        }
        refreshStyles()
    }

    override fun onPause() {
        if (observing) settings.unregisterOnSharedPreferenceChangeListener(listener)
        observing = false
        super.onPause()
    }

    override fun onDestroyView() {
        colorDialog?.dismiss()
        colorDialog = null
        super.onDestroyView()
    }

    private fun refreshStyles() {
        val styles = CandidateIndicatorSettings.read(settings)
        findPreference<CandidateIndicatorPreviewPreference>("candidate_indicator_preview")?.setStyles(styles)
        CandidateIndicatorGroup.entries.forEach { group ->
            findPreference<ListPreference>(CandidateIndicatorSettings.lineKey(group))?.apply {
                // Also normalize invalid values restored from an external settings backup.
                val style = styles[group]
                if (value != style.line.value) value = style.line.value
                summary = getString(style.line.titleRes)
            }
            for (dark in listOf(false, true)) {
                val style = styles[group]
                val color = style.color(requireContext(), group, dark)
                val custom = if (dark) style.darkColor else style.lightColor
                findPreference<Preference>(CandidateIndicatorSettings.colorKey(group, dark))?.summary =
                    if (custom == null) getString(R.string.candidate_indicator_standard_color, "#%08X".format(color))
                    else "#%08X".format(color)
            }
        }
    }

    @android.annotation.SuppressLint("CheckResult")
    private fun showColorPicker(group: CandidateIndicatorGroup, dark: Boolean) {
        val initial = CandidateIndicatorSettings.read(settings)[group].color(requireContext(), group, dark)
        colorDialog?.dismiss()
        colorDialog = MaterialDialog(requireContext()).show {
            title(text = getString(group.titleRes) + " · " + getString(if (dark) R.string.candidate_indicator_color_dark else R.string.candidate_indicator_color_light))
            colorChooser(
                colors = intArrayOf(initial, 0xff00796b.toInt(), 0xff7b1fa2.toInt(), 0xff995500.toInt(),
                    0xff80cbc4.toInt(), 0xffce93d8.toInt(), 0xffffd180.toInt(), 0xff2196f3.toInt(),
                    0xffe91e63.toInt(), android.graphics.Color.BLACK, android.graphics.Color.WHITE),
                initialSelection = initial, allowCustomArgb = true,
            ) { _, color -> settings.edit().putInt(CandidateIndicatorSettings.colorKey(group, dark), color).apply() }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
            neutralButton(R.string.candidate_indicator_standard) {
                settings.edit().remove(CandidateIndicatorSettings.colorKey(group, dark)).apply()
            }
        }
    }
}
