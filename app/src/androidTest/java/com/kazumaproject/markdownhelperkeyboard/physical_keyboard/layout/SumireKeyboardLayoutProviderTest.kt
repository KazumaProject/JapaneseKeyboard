package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.layout

import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun keyboardLayoutResources_registerOnlyJapanese109A() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        context.resources.getXml(R.xml.keyboard_layouts).use { parser ->
            var layoutCount = 0
            val layoutNames = mutableListOf<String>()
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "keyboard-layout"
                ) {
                    layoutCount += 1
                    layoutNames += parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android",
                        "name"
                    )
                }
            }
            assertEquals(1, layoutCount)
            assertEquals(listOf("sumire_japanese_109a"), layoutNames)
            assertFalse(layoutNames.contains("sumire_japanese_" + "106"))
        }

        context.resources.openRawResource(R.raw.keyboard_layout_japanese_109a).use {
            assertTrue(it.available() > 0)
        }
    }

    // Android 15 exposes the native overlay loader as a hidden test API. Run these
    // tests with `am instrument --no-hidden-api-checks -e nativeKeyboardLayout true`
    // to exercise Android's parser,
    // rather than accepting a resource that merely looks like a valid KCM file.
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun japanese109A_nativeOverlayMapsScanCodesAndSymbols() {
        val map = loadNativeOverlay()
        val mappedKey = KeyCharacterMap::class.java.getDeclaredMethod(
            "getMappedKeyOrDefault", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        data class ScanKey(val scan: Int, val generic: Int, val mapped: Int, val base: Char, val shift: Char)
        val keys = listOf(
            ScanKey(3, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_2, '2', '"'),
            ScanKey(7, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_6, '6', '&'),
            ScanKey(12, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_MINUS, '-', '='),
            ScanKey(13, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_EQUALS, '^', '~'),
            ScanKey(26, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_AT, '@', '`'),
            ScanKey(27, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_LEFT_BRACKET, '[', '{'),
            ScanKey(39, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_SEMICOLON, ';', '+'),
            ScanKey(40, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_APOSTROPHE, ':', '*'),
            ScanKey(43, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_RIGHT_BRACKET, ']', '}'),
            ScanKey(89, KeyEvent.KEYCODE_RO, KeyEvent.KEYCODE_RO, '\\', '_'),
            ScanKey(124, KeyEvent.KEYCODE_YEN, KeyEvent.KEYCODE_YEN, '¥', '|')
        )
        keys.forEach { key ->
            val code = mappedKey.invoke(map, key.scan, key.generic) as Int
            assertEquals("scan ${key.scan}", key.mapped, code)
            assertEquals("base scan ${key.scan}", key.base.code, map.get(code, 0))
            assertEquals("shift scan ${key.scan}", key.shift.code, map.get(code, KeyEvent.META_SHIFT_ON))
        }
        assertEquals(
            KeyEvent.KEYCODE_ZENKAKU_HANKAKU,
            mappedKey.invoke(map, 41, KeyEvent.KEYCODE_GRAVE)
        )
        // An OVERLAY must retain keys that it does not redefine.
        assertEquals(' '.code, map.get(KeyEvent.KEYCODE_SPACE, 0))
        assertEquals('\n'.code, map.get(KeyEvent.KEYCODE_ENTER, 0))
    }

    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun japanese109A_nativeOverlayInvertsCapsLockWithShift() {
        val map = loadNativeOverlay()
        ('a'..'z').forEach { letter ->
            val code = KeyEvent.KEYCODE_A + (letter - 'a')
            assertEquals(letter.code, map.get(code, 0))
            assertEquals(letter.uppercaseChar().code, map.get(code, KeyEvent.META_SHIFT_ON))
            assertEquals(letter.uppercaseChar().code, map.get(code, KeyEvent.META_CAPS_LOCK_ON))
            assertEquals(
                "Shift+CapsLock for $letter", letter.code,
                map.get(code, KeyEvent.META_SHIFT_ON or KeyEvent.META_CAPS_LOCK_ON)
            )
        }
    }

    private fun loadNativeOverlay(): KeyCharacterMap {
        assumeTrue(
            "Native KCM tests require --no-hidden-api-checks -e nativeKeyboardLayout true",
            InstrumentationRegistry.getArguments().getString("nativeKeyboardLayout") == "true"
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val overlay = context.resources.openRawResource(R.raw.keyboard_layout_japanese_109a)
            .bufferedReader().use { it.readText() }
        return KeyCharacterMap::class.java.getDeclaredMethod(
            "load", String::class.java, String::class.java
        ).invoke(null, "sumire_japanese_109a_test", overlay) as KeyCharacterMap
    }
}
