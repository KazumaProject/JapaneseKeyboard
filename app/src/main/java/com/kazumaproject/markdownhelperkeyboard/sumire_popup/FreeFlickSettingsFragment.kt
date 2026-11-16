package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyDataSource
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely

@AndroidEntryPoint
class FreeFlickSettingsFragment : AsyncPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    @Inject lateinit var specialKeys: SumireSpecialKeyDataSource
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(specialKeys.observeAllActionOverrides(), specialKeys.observeAllPlacementOverrides()) { actions, placements ->
                    actions to placements
                }.collect { (actions, placements) ->
                    findPreference<FreeFlickPreviewPreference>("sumire_free_flick_preview")?.setSpecialKeyOverrides(actions, placements)
                }
            }
        }
    }
    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if(key==null || key.startsWith(FreeFlickPreferences.PREFIX) || key.startsWith(SumirePopupRepository.PREFIX)) {
            findPreference<FreeFlickPreviewPreference>("sumire_free_flick_preview")?.updateOptions()
            updateLayoutSummary()
        }
    }
    override fun onResume() {
        super.onResume(); updateLayoutSummary()
        findPreference<FreeFlickPreviewPreference>("sumire_free_flick_preview")?.updateOptions()
    }
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
    private var preview: FreeFlickKeyboardPreview? = null
    private var actions = emptyList<SumireSpecialKeyActionOverrideEntity>()
    private var placements = emptyList<SumireSpecialKeyPlacementOverrideEntity>()
    init {layoutResource=R.layout.free_flick_preview;isSelectable=false}
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val frame=holder.findViewById(R.id.free_flick_preview_canvas) as FrameLayout
        frame.removeAllViews()
        val result=holder.findViewById(R.id.free_flick_preview_result) as TextView
        result.text=context.getString(R.string.popup_result)+"："
        preview=FreeFlickKeyboardPreview(context).apply {
            onText={output -> result.text=context.getString(R.string.popup_result)+"："+output}
        }.also {
            frame.addView(it,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            it.setSpecialKeyOverrides(actions,placements)
            frame.layoutParams.height=it.layoutParams.height
        }
    }
    fun updateOptions() { preview?.refresh() }
    fun setSpecialKeyOverrides(actionOverrides: List<SumireSpecialKeyActionOverrideEntity>,
        placementOverrides: List<SumireSpecialKeyPlacementOverrideEntity>) {
        actions=actionOverrides;placements=placementOverrides
        preview?.setSpecialKeyOverrides(actions,placements)
    }
}
