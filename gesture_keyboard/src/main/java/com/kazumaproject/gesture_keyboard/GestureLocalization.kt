package com.kazumaproject.gesture_keyboard

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper

/** IME display contexts can retain the system locale after an app-language change. */
fun localizedGestureContext(context: Context): Context {
    if (Build.VERSION.SDK_INT < 33) return context
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return context
    if (locales.isEmpty || locales == context.resources.configuration.locales) return context
    return ContextThemeWrapper(context.createConfigurationContext(Configuration().apply { setLocales(locales) }), context.theme)
}
