package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.KeyEvent
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TenkeyPunctuationSizingDeviceTest {
    @Test fun punctuationGuideShouldFollowTheGuideSlider() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val ctx = ins.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val keys = listOf("tenkey_hiragana_key_text_size_sp", "tenkey_hiragana_guide_text_size_sp")
        val previous = prefs.all.filterKeys { it in keys }
        prefs.edit().putFloat(keys[0], 24f).putFloat(keys[1], 6f).commit()
        val activity = ins.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun waitFor(id: Int) {
            val end = SystemClock.uptimeMillis() + 20000
            while (SystemClock.uptimeMillis() < end) {
                var present = false
                ins.runOnMainSync { present = activity.findViewById<android.view.View>(id) != null }
                if (present) return
                SystemClock.sleep(100)
            }
            error("Missing view $id")
        }
        fun screenshot(name: String) {
            ins.runOnMainSync {
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                (host.childFragmentManager.primaryNavigationFragment?.view as? androidx.core.widget.NestedScrollView)?.scrollTo(0,0)
            }
            SystemClock.sleep(300)
            ins.uiAutomation.takeScreenshot()?.let { b ->
                File(ctx.filesDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
                b.recycle()
            }
        }
        fun guideSp(id: Int): Float {
            val key = activity.findViewById<TextView>(id)
            val text = key.text as Spanned
            val paint = TextPaint(key.paint)
            text.getSpans(if (id == com.kazumaproject.tenkey.R.id.key_12) 0 else 2, if (id == com.kazumaproject.tenkey.R.id.key_12) 1 else 3,MetricAffectingSpan::class.java).forEach { it.updateMeasureState(paint) }
            return paint.textSize / ctx.resources.displayMetrics.scaledDensity
        }
        try {
            waitFor(R.id.nav_host_fragment_activity_main)
            ins.runOnMainSync {
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                host.navController.navigate(R.id.tenKeyCandidateLetterSizeFragment)
            }
            waitFor(R.id.sizingGuidePreview)
            var beforeA = 0f; var beforePunctuation = 0f
            ins.runOnMainSync {
                activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked = true
                beforeA = guideSp(com.kazumaproject.tenkey.R.id.key_1)
                beforePunctuation = guideSp(com.kazumaproject.tenkey.R.id.key_12)
            }
            screenshot("tenkey-punctuation-guide6.png")
            ins.runOnMainSync {
                val slider = activity.findViewById<Slider>(R.id.sizingGuide)
                slider.requestFocus()
                repeat(10) {
                    slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                }
                val a = guideSp(com.kazumaproject.tenkey.R.id.key_1)
                val punctuation = guideSp(com.kazumaproject.tenkey.R.id.key_12)
                File(ctx.filesDir, "tenkey-punctuation-guide-measurements.txt").writeText(
                    "API=${android.os.Build.VERSION.SDK_INT}\nslider=${slider.value}\nsaved=${prefs.getFloat(keys[1],-1f)}\nkey1_top_before=$beforeA sp\nkey1_top_after=$a sp\nkey12_question_before=$beforePunctuation sp\nkey12_question_after=$punctuation sp\n")
                android.util.Log.i("TenkeyPunctuationSizing", "slider=${slider.value} key1=$a key12=$punctuation before=$beforePunctuation")
            }
            screenshot("tenkey-punctuation-guide16.png")
            ins.runOnMainSync {
                assertEquals(16f, activity.findViewById<Slider>(R.id.sizingGuide).value, .01f)
                assertEquals(16f, guideSp(com.kazumaproject.tenkey.R.id.key_1), .6f)
                assertEquals("Punctuation '?' guide must use the selected 16 sp",16f,guideSp(com.kazumaproject.tenkey.R.id.key_12), .6f)
            }
        } finally {
            prefs.edit().apply {
                keys.forEach { remove(it) }; previous.forEach { (k,v) -> putFloat(k,v as Float) }
            }.commit()
            ins.runOnMainSync { activity.finish() }
        }
    }
}
