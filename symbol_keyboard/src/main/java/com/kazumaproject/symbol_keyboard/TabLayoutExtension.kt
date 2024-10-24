package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout

fun TabLayout.Tab.setBackGroundTint(context: Context) {
    val iconView = this.customView?.findViewById<ImageView>(R.id.tab_icon)
    iconView?.setColorFilter(
        ContextCompat.getColor(context, R.color.tab_selected),
        android.graphics.PorterDuff.Mode.SRC_IN
    )
}