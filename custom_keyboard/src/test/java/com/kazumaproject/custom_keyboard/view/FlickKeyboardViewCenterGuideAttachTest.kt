package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.controller.CenterGuideFlickInputController
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewCenterGuideAttachTest {

    @Test
    fun centerGuideKeyAttachesTheDedicatedController() {
        val view = FlickKeyboardView(context())
        view.setKeyboard(
            KeyboardLayout(
                keys = listOf(
                    KeyData(
                        label = "あ",
                        row = 0,
                        column = 0,
                        isFlickable = true,
                        keyId = "a",
                        keyType = KeyType.CENTER_GUIDE_FLICK
                    )
                ),
                flickKeyMaps = mapOf(
                    "a" to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("あ"),
                            FlickDirection.UP to FlickAction.Input("い"),
                            FlickDirection.DOWN to FlickAction.Input("お"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("う"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("え")
                        )
                    )
                ),
                columnCount = 1,
                rowCount = 1
            )
        )

        val field = FlickKeyboardView::class.java.getDeclaredField(
            "centerGuideFlickControllers"
        ).apply { isAccessible = true }
        val controllers = field.get(view) as List<*>

        assertEquals(1, controllers.size)
        assertEquals(CenterGuideFlickInputController::class.java, controllers.single()!!::class.java)
    }

    private fun context(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
    )
}
