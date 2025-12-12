package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment

class SettingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 9

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CommonPreferenceFragment()
            1 -> KeyboardThemeFragment()
            2 -> ZenzPreferenceFragment()
            3 -> DictionaryPreferenceFragment()
            4 -> KanaPreferenceFragment()
            5 -> QwertyPreferenceFragment()
            6 -> SumirePreferenceFragment()
            7 -> CustomKeyboardPreferenceFragment()
            8 -> TabletPreferenceFragment()
            else -> CommonPreferenceFragment()
        }
    }
}
