package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 6

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CommonPreferenceFragment()
            1 -> ZenzPreferenceFragment()
            2 -> DictionaryPreferenceFragment()
            3 -> KanaPreferenceFragment()
            4 -> QwertyPreferenceFragment()
            5 -> SumirePreferenceFragment()
            6 -> CustomKeyboardPreferenceFragment()
            7 -> TabletPreferenceFragment()
            else -> CommonPreferenceFragment()
        }
    }
}
