package com.kazumaproject.core.domain.extensions

import android.widget.PopupWindow

fun PopupWindow.hide() {
    if (this.isShowing) this.dismiss()
}