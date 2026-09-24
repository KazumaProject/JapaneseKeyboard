package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue

internal fun ActivityScenario<MainActivity>.awaitSettingsContentReady(timeoutMillis: Long = 10_000) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var ready = false
    while (!ready && SystemClock.uptimeMillis() < deadline) {
        onActivity { ready = it.isSettingsContentReady }
        if (!ready) SystemClock.sleep(20)
    }
    assertTrue("Settings content did not initialize", ready)
}
