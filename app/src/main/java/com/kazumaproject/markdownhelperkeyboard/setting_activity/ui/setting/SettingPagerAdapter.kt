package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment

class SettingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 10

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CommonPreferenceFragment()
            1 -> KeyboardThemeFragment()
            2 -> ZenzPreferenceFragment()
            3 -> GemmaPreferenceFragment()
            4 -> DictionaryPreferenceFragment()
            5 -> KanaPreferenceFragment()
            6 -> QwertyPreferenceFragment()
            7 -> SumirePreferenceFragment()
            8 -> CustomKeyboardPreferenceFragment()
            9 -> TabletPreferenceFragment()
            else -> CommonPreferenceFragment()
        }
    }
}
