package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val tabs = SettingTabRegistry.createTabs()

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = tabs[position].fragmentFactory()

    fun getTitle(position: Int, fragment: Fragment): String = tabs[position].title(fragment.requireContext())
}
