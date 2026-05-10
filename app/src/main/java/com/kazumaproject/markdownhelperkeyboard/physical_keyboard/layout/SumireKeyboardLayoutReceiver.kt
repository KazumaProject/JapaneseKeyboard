package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.layout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SumireKeyboardLayoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Android discovers keyboard layouts from manifest metadata; no runtime work is required.
    }
}

