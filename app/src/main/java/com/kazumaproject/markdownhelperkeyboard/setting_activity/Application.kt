package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Application
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File

@HiltAndroidApp
class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start SharedPreferences' asynchronous disk load before Activity Hilt injection.
        // AppPreference migrations read values synchronously during MainActivity.onCreate.
        preloadSharedPreferences()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        val dexOutputDir: File = codeCacheDir
        dexOutputDir.setReadOnly()
    }

    @Suppress("DEPRECATION")
    private fun preloadSharedPreferences() {
        PreferenceManager.getDefaultSharedPreferences(this)
    }
}
