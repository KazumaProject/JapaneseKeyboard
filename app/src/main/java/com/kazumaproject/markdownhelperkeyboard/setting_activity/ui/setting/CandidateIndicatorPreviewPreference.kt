package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.CandidateCharacterTextView
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.CandidateIndicatorStyles

class CandidateIndicatorPreviewPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    private var styles = CandidateIndicatorStyles()
    init {
        layoutResource = R.layout.preference_candidate_indicator_preview
        isSelectable = false
    }
    fun setStyles(value: CandidateIndicatorStyles) {
        if (styles == value) return
        styles = value
        notifyChanged()
    }
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        for ((id, dark) in listOf(R.id.candidate_indicator_preview_light to false, R.id.candidate_indicator_preview_dark to true)) {
            (holder.findViewById(id) as CandidateCharacterTextView).apply {
                text = context.getString(R.string.candidate_indicator_example)
                setTextColor(if (dark) Color.WHITE else Color.BLACK)
                setBackgroundColor(if (dark) 0xff202124.toInt() else 0xfffafafa.toInt())
                setCharacterIndicators(text.toString(), true, styles = styles, dark = dark)
            }
        }
    }
}
