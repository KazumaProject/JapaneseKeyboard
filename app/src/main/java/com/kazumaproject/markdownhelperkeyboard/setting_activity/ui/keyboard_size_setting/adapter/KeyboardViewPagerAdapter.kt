package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R

class KeyboardViewPagerAdapter : RecyclerView.Adapter<KeyboardViewPagerAdapter.ViewHolder>() {

    // 表示するページのレイアウトリスト
    private val pageLayouts = listOf(
        R.layout.page_tenkey,
        R.layout.page_qwerty // QWERTYレイアウトを追加
    )

    companion object {
        const val TEN_KEY_PAGE_POSITION = 0
        const val QWERTY_PAGE_POSITION = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 必要に応じて、各ページのビューに対する処理をここに記述
    }

    override fun getItemCount(): Int = pageLayouts.size

    override fun getItemViewType(position: Int): Int {
        return pageLayouts[position]
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
