package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Application
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class Application : Application(){
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG){
            Timber.plant(Timber.DebugTree())
        }
    }
}