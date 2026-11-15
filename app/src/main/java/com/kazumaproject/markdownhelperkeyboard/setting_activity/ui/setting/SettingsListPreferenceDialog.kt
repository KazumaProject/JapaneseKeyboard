package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Uses the real preference listener and value; cancellation never writes a setting. */
@Suppress("DEPRECATION")
class SettingsListPreferenceDialog : DialogFragment() {
    private var choicesJob: Job? = null
    private var choices: LinearLayout? = null
    private var heading: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        heading = TextView(context).apply {
            text = requireArguments().getCharSequence(TITLE)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setPadding(0, 0, 0, dp(12))
        }.also(layout::addView)
        val options = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ProgressBar(context))
        }
        choices = options
        layout.addView(ScrollView(context).apply { addView(options) }, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(android.R.string.cancel)
            setOnClickListener { dismiss() }
        })
        return BottomSheetDialog(context, theme).apply {
            setContentView(layout)
            layout.layoutParams.height = (resources.displayMetrics.heightPixels * .75f).toInt()
        }
    }

    override fun onStart() {
        super.onStart()
        choicesJob?.cancel()
        choicesJob = lifecycleScope.launch {
            val owner = requireNotNull(targetFragment as? PreferenceFragmentCompat)
            // Restored dialogs can start before AsyncPreferenceFragment has published its hierarchy.
            while (owner.preferenceScreen == null) delay(16)
            val key = requireNotNull(requireArguments().getString(KEY))
            val preference = owner.findPreference<ListPreference>(key)
            if (preference == null) {
                dismiss()
                return@launch
            }
            heading?.text = preference.title
            val options = choices ?: return@launch
            options.removeAllViews()
            preference.entries.orEmpty().forEachIndexed { index, entry ->
                options.addView(MaterialRadioButton(requireContext()).apply {
                    text = entry
                    isChecked = preference.entryValues?.getOrNull(index)?.toString() == preference.value
                    minimumHeight = (56 * resources.displayMetrics.density).toInt()
                    isEnabled = preference.isEnabled
                    setOnClickListener {
                        preference.entryValues?.getOrNull(index)?.toString()?.let { value ->
                            if (preference.callChangeListener(value)) preference.value = value
                        }
                        dismiss()
                    }
                }, ViewGroup.LayoutParams(-1, -2))
            }
        }
    }

    override fun onDestroyView() {
        choicesJob?.cancel()
        choicesJob = null
        choices = null
        heading = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY = "preferenceKey"
        private const val TITLE = "preferenceTitle"
        internal const val TAG = "androidx.preference.PreferenceFragment.DIALOG"
        fun forPreference(preference: ListPreference) = SettingsListPreferenceDialog().apply {
            arguments = bundleOf(KEY to preference.key, TITLE to preference.title)
        }
    }
}
