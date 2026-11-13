package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely

class FreeFlickSettingsFragment : AsyncPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    override val preferencesXmlRes = R.xml.pref_free_flick
    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<Preference>("sumire_popup_map_editor_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.popupMapEditorFragment);true
        }
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }
    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if(key==null || key.startsWith(FreeFlickPreferences.PREFIX)) {
            findPreference<FreeFlickPreviewPreference>("sumire_free_flick_preview")?.updateOptions()
        }
    }
    override fun onDestroyView() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }
}

class FreeFlickPreviewPreference @JvmOverloads constructor(context: Context, attributes: AttributeSet?=null) : Preference(context,attributes) {
    private var canvas: PopupMapEditorCanvas? = null
    init {layoutResource=R.layout.free_flick_preview;isSelectable=false}
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val frame=holder.findViewById(R.id.free_flick_preview_canvas) as FrameLayout
        frame.removeAllViews()
        val result=holder.findViewById(R.id.free_flick_preview_result) as TextView
        result.text=context.getString(R.string.popup_result)+"："
        canvas=PopupMapEditorCanvas(context).apply {
            inputOptions=FreeFlickPreferences.read(context)
            showConfig(SumirePopupDefaults.create("は"),"normal",null,true)
            onTrial={output,session -> result.text=context.getString(R.string.popup_result)+"："+(output ?: session.selectedText.orEmpty())}
        }.also {frame.addView(it,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))}
    }
    fun updateOptions() {canvas?.inputOptions=FreeFlickPreferences.read(context);canvas?.invalidate()}
}
