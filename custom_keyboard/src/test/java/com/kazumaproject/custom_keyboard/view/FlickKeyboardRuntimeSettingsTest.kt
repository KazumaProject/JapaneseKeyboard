package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardRuntimeSettingsTest {

    @Test
    fun sensitivityAndLongPressUpdatesPreserveExistingViewAndController() {
        val keyboardView = FlickKeyboardView(context())
        keyboardView.setKeyboard(standardLayout())
        val childBefore = keyboardView.getChildAt(0)
        val controllerBefore = keyboardView.standardController()

        keyboardView.setFlickSensitivityValue(1)
        keyboardView.setLongPressTimeout(100L)
        keyboardView.setFlickSensitivityValue(200)
        keyboardView.setLongPressTimeout(2_000L)

        assertSame(childBefore, keyboardView.getChildAt(0))
        assertSame(controllerBefore, keyboardView.standardController())
    }

    private fun standardLayout(): KeyboardLayout {
        val key = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = true,
            keyId = "a",
            keyType = KeyType.STANDARD_FLICK
        )
        return KeyboardLayout(
            keys = listOf(key),
            flickKeyMaps = mapOf(
                "a" to listOf(
                    mapOf(
                        FlickDirection.TAP to FlickAction.Input("a"),
                        FlickDirection.UP_RIGHT_FAR to FlickAction.Input("b")
                    )
                )
            ),
            columnCount = 1,
            rowCount = 1
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun FlickKeyboardView.standardController(): Any {
        val field = FlickKeyboardView::class.java.getDeclaredField("standardFlickControllers")
        field.isAccessible = true
        return (field.get(this) as List<Any>).single()
    }

    private fun context(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
    }
}
