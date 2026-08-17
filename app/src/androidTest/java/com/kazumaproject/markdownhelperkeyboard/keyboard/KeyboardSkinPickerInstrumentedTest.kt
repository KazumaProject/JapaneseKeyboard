package com.kazumaproject.markdownhelperkeyboard.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class KeyboardSkinPickerInstrumentedTest {

    @Test
    fun cupertinoLightAndDarkLabelsRenderWithoutEllipsis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java),
        )

        try {
            scenario.onActivity { activity ->
                Navigation.findNavController(
                    activity,
                    R.id.nav_host_fragment_activity_main,
                ).navigate(R.id.keyboardSkinPickerFragment)
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.keyboard_skin_grid)
                    .scrollToPosition(KeyboardSkinId.CUPERTINO_DARK.ordinal)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)

            scenario.onActivity { activity ->
                val grid = activity.findViewById<RecyclerView>(R.id.keyboard_skin_grid)
                assertLabelIsComplete(
                    grid,
                    KeyboardSkinId.CUPERTINO.ordinal,
                    activity.getString(R.string.keyboard_skin_cupertino),
                )
                assertLabelIsComplete(
                    grid,
                    KeyboardSkinId.CUPERTINO_DARK.ordinal,
                    activity.getString(R.string.keyboard_skin_cupertino_dark),
                )

                val root = activity.window.decorView.rootView
                val bitmap = Bitmap.createBitmap(
                    root.width,
                    root.height,
                    Bitmap.Config.ARGB_8888,
                ).also { root.draw(Canvas(it)) }
                val output = File(activity.filesDir, "keyboard-skin-picker-cupertino.png")
                FileOutputStream(output).use { stream ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                bitmap.recycle()
                assertTrue(output.length() > 8_000L)
            }
        } finally {
            scenario.close()
        }
    }

    private fun assertLabelIsComplete(
        grid: RecyclerView,
        position: Int,
        expectedText: String,
    ) {
        val item = grid.findViewHolderForAdapterPosition(position)?.itemView
            ?: grid.layoutManager?.findViewByPosition(position)
        assertNotNull("Skin card $position must be visible", item)
        val label = checkNotNull(item).findViewById<TextView>(R.id.keyboard_skin_name)
        assertEquals(expectedText, label.text.toString())
        assertNotNull(label.layout)
        assertEquals(0, label.layout.getEllipsisCount(0))
    }
}
