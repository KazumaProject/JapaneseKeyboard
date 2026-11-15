package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewTarget
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewView
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardTrialDialog
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.keyboardPreviewTargetForFamily
import kotlinx.coroutines.launch
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

class CandidatePreviewPreference @JvmOverloads constructor(context: Context, attrs: android.util.AttributeSet? = null) : Preference(context, attrs) {
    internal var keyboard = PreviewKeyboard.TENKEY
    internal var landscape = false
    internal fun refresh() = notifyChanged()
    init { layoutResource = R.layout.preference_candidate_display_preview; isSelectable = false }
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val container = holder.findViewById(R.id.candidate_display_preview_container) as LinearLayout
        container.removeAllViews()
        val target = KeyboardPreviewTarget(KeyboardType.valueOf(keyboard.name))
        container.addView(KeyboardPreviewView(context, target).apply {
            this.landscape = this@CandidatePreviewPreference.landscape
            respectOrientationOverride = false
            respectSavedInputMode = false
        },
            LinearLayout.LayoutParams(-1, -2))
        container.addView(MaterialButton(context).apply {
            setText(R.string.keyboard_preview_try)
            setOnClickListener {
                val fragment = holder.itemView.findFragment<Fragment>()
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    KeyboardTrialDialog.show(fragment.childFragmentManager, keyboardPreviewTargetForFamily(context, target.type))
                }
            }
        })
    }
}
