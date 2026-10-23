package com.kazumaproject.morse_keyboard

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper

/** IME display contexts can retain the system locale after an app-language change. */
fun localizedMorseContext(context: Context): Context {
    if (Build.VERSION.SDK_INT < 33) return context
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return context
    if (locales.isEmpty || locales == context.resources.configuration.locales) return context
    val configuration = Configuration().apply { setLocales(locales) }
    return ContextThemeWrapper(context.createConfigurationContext(configuration), context.theme)
}
