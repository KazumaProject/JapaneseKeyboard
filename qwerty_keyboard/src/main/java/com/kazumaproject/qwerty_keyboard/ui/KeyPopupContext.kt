package com.kazumaproject.qwerty_keyboard.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo

/** Small, transient key previews and variation windows do not need a hardware renderer. */
internal class KeyPopupContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
        // PopupWindow removes its window synchronously on key release. A busy renderer
        // can otherwise hold that input event in ViewRootImpl.destroyHardwareRenderer.
        // Return a copy so the keyboard and the application's other windows keep their flags.
        flags = flags and ApplicationInfo.FLAG_HARDWARE_ACCELERATED.inv()
    }
}
