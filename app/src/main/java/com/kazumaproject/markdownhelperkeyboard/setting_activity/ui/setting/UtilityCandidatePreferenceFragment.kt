package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.utility.Precision
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitCategory
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UtilityCandidatePreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_utility_candidate

    override fun onCommonPreferencesCreated() {
        targetPreferenceKeys.forEach { (key, category) ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.unitTargetSettingsFragment,
                    Bundle().apply { putString(UnitTargetSettingsFragment.ARG_CATEGORY, category.name) },
                )
                true
            }
        }
        findPreference<Preference>("utility_reset_all")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.utility_reset_confirm_title)
                .setMessage(R.string.utility_reset_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    AppPreference.resetUtilityCandidateConfig()
                    preferenceScreen.removeAll()
                    setPreferencesFromResource(preferencesXmlRes, null)
                    onCommonPreferencesCreated()
                    updateTargetSummaries()
                }
                .show()
            true
        }
        updateTargetSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateTargetSummaries()
    }

    private fun updateTargetSummaries() {
        val config = AppPreference.utility_candidate_config
        targetPreferenceKeys.forEach { (key, category) ->
            val settings = config.unitTargets[category].orEmpty()
            findPreference<Preference>(key)?.summary = if (settings.isEmpty()) {
                getString(R.string.utility_targets_disabled)
            } else {
                settings.mapNotNull { setting ->
                    UnitRegistry.Default.findById(setting.unitId)?.let { unit ->
                        val precision = when (val value = setting.precision) {
                            Precision.Auto -> getString(R.string.utility_precision_auto)
                            is Precision.SignificantDigits -> value.digits.toString()
                        }
                        "${unit.symbol} ($precision)"
                    }
                }.joinToString()
            }
        }
    }

    private companion object {
        val targetPreferenceKeys = mapOf(
            "utility_targets_length" to UnitCategory.LENGTH,
            "utility_targets_area" to UnitCategory.AREA,
            "utility_targets_volume" to UnitCategory.VOLUME,
            "utility_targets_mass" to UnitCategory.MASS,
            "utility_targets_temperature" to UnitCategory.TEMPERATURE,
            "utility_targets_speed" to UnitCategory.SPEED,
            "utility_targets_pressure" to UnitCategory.PRESSURE,
            "utility_targets_energy" to UnitCategory.ENERGY,
            "utility_targets_power" to UnitCategory.POWER,
            "utility_targets_time" to UnitCategory.TIME,
            "utility_targets_data" to UnitCategory.DATA,
        )
    }
}
