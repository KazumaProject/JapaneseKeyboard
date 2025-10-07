package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey

class KeyboardViewPagerAdapter : RecyclerView.Adapter<KeyboardViewPagerAdapter.ViewHolder>() {

    private val pageLayouts = listOf(
        R.layout.page_tenkey,
        R.layout.page_qwerty
    )

    companion object {
        const val TEN_KEY_PAGE_POSITION = 0
        const val QWERTY_PAGE_POSITION = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (position) {
            TEN_KEY_PAGE_POSITION -> {
                holder.tenKeyView?.setOnTouchListener { _, _ ->
                    true
                }
            }

            QWERTY_PAGE_POSITION -> {
                holder.qwertyView?.setOnTouchListener { _, _ ->
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = pageLayouts.size

    override fun getItemViewType(position: Int): Int {
        return pageLayouts[position]
    }

    // Updated ViewHolder to hold references to the specific keyboard views
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // These can be nullable because a given layout will only have one of them.
        val tenKeyView: TenKey? = itemView.findViewById(R.id.keyboard_view)
        val qwertyView: QWERTYKeyboardView? = itemView.findViewById(R.id.qwerty_view)
    }
}
