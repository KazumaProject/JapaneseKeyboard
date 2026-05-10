package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.layout

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SumireKeyboardLayoutProviderTest {
    @Test
    fun manifest_declaresKeyboardLayoutProviderReceiver() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, SumireKeyboardLayoutReceiver::class.java),
            PackageManager.GET_META_DATA
        )

        assertEquals(
            R.xml.keyboard_layouts,
            receiverInfo.metaData.getInt("android.hardware.input.metadata.KEYBOARD_LAYOUTS")
        )
    }

    @Test
    fun keyboardLayoutResources_exist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        context.resources.getXml(R.xml.keyboard_layouts).use { parser ->
            var layoutCount = 0
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "keyboard-layout"
                ) {
                    layoutCount += 1
                }
            }
            assertTrue(layoutCount >= 2)
        }

        context.resources.openRawResource(R.raw.keyboard_layout_japanese_109a).use {
            assertTrue(it.available() > 0)
        }
        context.resources.openRawResource(R.raw.keyboard_layout_japanese_106).use {
            assertTrue(it.available() > 0)
        }
    }
}
