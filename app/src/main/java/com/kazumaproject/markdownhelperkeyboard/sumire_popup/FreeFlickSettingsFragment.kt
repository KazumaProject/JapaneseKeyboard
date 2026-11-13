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
        findPreference<Preference>("sumire_free_flick_layouts")?.setOnPreferenceClickListener {
            navigateSafely(R.id.freeFlickLayoutsFragment);true
        }
        findPreference<Preference>("sumire_popup_map_editor_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.popupMapEditorFragment);true
        }
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateLayoutSummary()
    }
    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if(key==null || key.startsWith(FreeFlickPreferences.PREFIX) || key.startsWith(SumirePopupRepository.PREFIX)) {
            findPreference<FreeFlickPreviewPreference>("sumire_free_flick_preview")?.updateOptions()
            updateLayoutSummary()
        }
    }
    override fun onResume() { super.onResume(); updateLayoutSummary() }
    private fun updateLayoutSummary() {
        findPreference<Preference>("sumire_free_flick_layouts")?.summary = runCatching {
            val repository = FreeFlickLayoutRepository(requireContext())
            getString(R.string.free_flick_layout_selected, repository.find(repository.selectedId()).name)
        }.getOrElse { getString(R.string.free_flick_layout_storage_error) }
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
            val config = SumirePopupRepository(context).load("は")
            showConfig(config,config.startMapId,null,true)
            onTrial={output,session -> result.text=context.getString(R.string.popup_result)+"："+(output ?: session.selectedText.orEmpty())}
        }.also {frame.addView(it,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))}
    }
    fun updateOptions() {canvas?.let { view ->
        view.inputOptions=FreeFlickPreferences.read(context)
        val config=SumirePopupRepository(context).load("は")
        view.showConfig(config,config.startMapId,null,true)
    }}
}
