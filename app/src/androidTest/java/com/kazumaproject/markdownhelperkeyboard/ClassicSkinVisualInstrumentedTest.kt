package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures both key families in an isolated debug APK for comparison with iOS 6. */
@RunWith(AndroidJUnit4::class)
class ClassicSkinVisualInstrumentedTest {
    @Test fun classicQwertyAndKanaRenderWithoutChangingInputGeometry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val output = File(context.getExternalFilesDir(null), "classic-skin").apply { mkdirs() }
        for (layout in listOf("qwerty", "kana")) {
            ActivityScenario.launch<SkinTestHostActivity>(
                Intent(context, SkinTestHostActivity::class.java)
                    .putExtra("keyboard", layout)
                    .putExtra("skin", "cupertino_classic")
            ).use { scenario ->
                scenario.onActivity { host ->
                    check(host.keyboard.width > 0 && host.keyboard.height > 0)
                    val bitmap = Bitmap.createBitmap(host.keyboard.width, host.keyboard.height,
                        Bitmap.Config.ARGB_8888)
                    host.keyboard.draw(Canvas(bitmap))
                    File(output, "$layout.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
