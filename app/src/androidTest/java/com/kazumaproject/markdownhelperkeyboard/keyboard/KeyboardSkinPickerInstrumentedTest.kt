package com.kazumaproject.markdownhelperkeyboard.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
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
            awaitSkinGrid(scenario, instrumentation)

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

    @Test
    fun tactileConceptSkinsCanAllBeSelectedAndPersisted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val previousSkin = preferences.getString(KEYBOARD_SKIN_KEY, KeyboardSkinId.DEFAULT.preferenceValue)
        val skins = listOf(
            KeyboardSkinId.SUMI_HANSHI,
            KeyboardSkinId.LETTERPRESS,
            KeyboardSkinId.PORCELAIN,
            KeyboardSkinId.URUSHI,
            KeyboardSkinId.CHALKBOARD,
            KeyboardSkinId.LINEN,
            KeyboardSkinId.MONOCHROME_LCD,
        )
        val nameResources = listOf(
            R.string.keyboard_skin_sumi_hanshi,
            R.string.keyboard_skin_letterpress,
            R.string.keyboard_skin_porcelain,
            R.string.keyboard_skin_urushi,
            R.string.keyboard_skin_chalkboard,
            R.string.keyboard_skin_linen,
            R.string.keyboard_skin_monochrome_lcd,
        )
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))

        try {
            scenario.onActivity { activity ->
                Navigation.findNavController(
                    activity,
                    R.id.nav_host_fragment_activity_main,
                ).navigate(R.id.keyboardSkinPickerFragment)
            }
            awaitSkinGrid(scenario, instrumentation)

            skins.forEachIndexed { index, skin ->
                scenario.onActivity { activity ->
                    activity.findViewById<RecyclerView>(R.id.keyboard_skin_grid)
                        .scrollToPosition(skin.ordinal)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(120)
                scenario.onActivity { activity ->
                    val grid = activity.findViewById<RecyclerView>(R.id.keyboard_skin_grid)
                    val item = grid.findViewHolderForAdapterPosition(skin.ordinal)?.itemView
                        ?: grid.layoutManager?.findViewByPosition(skin.ordinal)
                    assertNotNull("Skin card ${skin.name} must be visible", item)
                    val label = checkNotNull(item).findViewById<TextView>(R.id.keyboard_skin_name)
                    assertEquals(activity.getString(nameResources[index]), label.text.toString())
                    assertLabelHasNoEllipsis(label)
                    checkNotNull(item).performClick()
                }
                instrumentation.waitForIdleSync()
                assertEquals(
                    skin.preferenceValue,
                    preferences.getString(KEYBOARD_SKIN_KEY, null),
                )
            }
            SystemClock.sleep(240)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                captureActivity(activity, "keyboard-skin-picker-new-materials.png")
            }
        } finally {
            preferences.edit().putString(KEYBOARD_SKIN_KEY, previousSkin).commit()
            scenario.close()
        }
    }

    @Test
    fun legacySkinArraysStaySynchronizedWithCatalog() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val values = context.resources.getStringArray(R.array.keyboard_skin_values).toList()
        val entries = context.resources.getStringArray(R.array.keyboard_skin_entries).toList()

        assertEquals(KeyboardSkinId.entries.map { it.preferenceValue }, values)
        assertEquals(KeyboardSkinId.entries.size, entries.size)
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
        assertLabelHasNoEllipsis(label)
    }

    private fun assertLabelHasNoEllipsis(label: TextView) {
        val layout = label.layout
        assertNotNull(layout)
        checkNotNull(layout)
        for (line in 0 until layout.lineCount) {
            assertEquals(0, layout.getEllipsisCount(line))
        }
        assertEquals(label.text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun captureActivity(activity: MainActivity, fileName: String) {
        val root = activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(
            root.width,
            root.height,
            Bitmap.Config.ARGB_8888,
        ).also { root.draw(Canvas(it)) }
        val output = File(activity.filesDir, fileName)
        FileOutputStream(output).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        assertTrue(output.length() > 8_000L)
    }

    private fun awaitSkinGrid(
        scenario: ActivityScenario<MainActivity>,
        instrumentation: android.app.Instrumentation,
    ) {
        repeat(GRID_WAIT_ATTEMPTS) {
            instrumentation.waitForIdleSync()
            var gridReady = false
            scenario.onActivity { activity ->
                gridReady = activity.findViewById<RecyclerView>(R.id.keyboard_skin_grid) != null
            }
            if (gridReady) return
            SystemClock.sleep(GRID_WAIT_STEP_MS)
        }
        throw AssertionError("Keyboard skin grid did not appear")
    }

    companion object {
        private const val KEYBOARD_SKIN_KEY = "keyboard_skin_preference"
        private const val GRID_WAIT_ATTEMPTS = 30
        private const val GRID_WAIT_STEP_MS = 100L
    }
}
