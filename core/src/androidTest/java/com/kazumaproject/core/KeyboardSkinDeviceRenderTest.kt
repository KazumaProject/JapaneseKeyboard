package com.kazumaproject.core

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.PopupDirection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated renderer fixture at the reference's 3 px/point; never installs or selects an IME. */
@RunWith(AndroidJUnit4::class)
class KeyboardSkinDeviceRenderTest {
    @Test fun exportReferenceSizedSurfaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { densityDpi = 480; fontScale = 1f }
        val resources = context.createConfigurationContext(config).resources
        val output = File(context.getExternalFilesDir(null), "keyboard-skins").apply { mkdirs() }
        for (id in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
            val skin = requireNotNull(KeyboardSkinRegistry.find(id))
            val states = mapOf("key" to skin.keyDrawable(resources),
                "selected" to skin.popupDrawable(resources, PopupDirection.CENTER, true),
                "flick-up" to skin.popupDrawable(resources, PopupDirection.TOP))
            states.forEach { (name, drawable) ->
                val height = if (name == "flick-up") 220 else 147
                val bitmap = Bitmap.createBitmap(241, height, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                if (name != "flick-up") assertEquals(if (name == "selected") skin.palette.selection else skin.palette.key,
                    bitmap.getPixel(120, 73))
                if (name != "selected") assertEquals(0, bitmap.getPixel(0, 0))
                File(output, "${id.preferenceValue}-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
}
