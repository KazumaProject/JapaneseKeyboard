package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.BunsetsuTestDatabaseEntryPoint
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the real registration screens and Room, without clearing existing dictionaries. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DictionaryWordWhitespaceInstrumentedTest {
    @Test fun userDictionaryAddsAndEditsWordsAccordingToTheCurrentSetting() = checkScreen(false)
    @Test fun templatesAddAndEditWordsAccordingToTheCurrentSetting() = checkScreen(true)

    private fun checkScreen(template: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = AppPreference.PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY
        val previous = preferences.all[key] as? Boolean
        AppPreference.init(context)
        val database = EntryPointAccessors.fromApplication(
            context.applicationContext, BunsetsuTestDatabaseEntryPoint::class.java,
        ).database()
        val readings = mutableListOf<String>()
        fun rows(reading: String): List<Pair<Int, String>> = runBlocking {
            if (template) database.userTemplateDao().searchByReadingExactSuspend(reading, 100)
                .map { it.id to it.word }
            else database.userWordDao().searchByReadingExactSuspend(reading)
                .map { it.id to it.word }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { activity ->
                    val nav = activity.supportFragmentManager.findFragmentById(
                        R.id.nav_host_fragment_activity_main,
                    ) as NavHostFragment
                    nav.navController.navigate(
                        if (template) R.id.userTemplateFragment else R.id.navigation_user_dictionary,
                    )
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                for (preserve in listOf(false, true)) {
                    val reading = "くうはく" + UUID.randomUUID().toString().replace("-", "")
                    readings.add(reading)
                    // Open first, then change the preference: saves must use the current value.
                    scenario.onActivity { it.findViewById<View>(R.id.fab_add_word).performClick() }
                    AppPreference.preserve_dictionary_word_whitespace_preference = preserve
                    fun add(word: String, inputReading: String) {
                        scenario.onActivity { activity ->
                            activity.findViewById<EditText>(R.id.edit_text_word).setText(word)
                            activity.findViewById<EditText>(R.id.edit_text_reading).setText(inputReading)
                            activity.findViewById<View>(R.id.button_add).performClick()
                        }
                    }
                    add("　 ", reading)
                    assertTrue(rows(reading).isEmpty())
                    add("き", "　 ")
                    assertTrue(rows(reading).isEmpty())
                    add("　き ", " $reading　")
                    val initialWord = if (preserve) "　き " else "き"
                    await { rows(reading).singleOrNull()?.second == initialWord }
                    val id = rows(reading).single().first

                    // Toggling the setting must never modify already stored words.
                    AppPreference.preserve_dictionary_word_whitespace_preference = !preserve
                    assertEquals(initialWord, rows(reading).single().second)
                    scenario.onActivity { activity ->
                        activity.findViewById<EditText>(
                            if (template) R.id.edit_text_search_user_template
                            else R.id.edit_text_search_user_dictionary,
                        ).setText(reading)
                    }
                    for (editPreserve in listOf(true, false)) {
                        await {
                            var clicked = false
                            scenario.onActivity { activity ->
                                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_view_user_words)
                                if (recycler.adapter?.itemCount == 1) {
                                    val holder = recycler.findViewHolderForAdapterPosition(0)
                                    clicked = holder?.itemView?.performClick() == true
                                }
                            }
                            clicked
                        }
                        AppPreference.preserve_dictionary_word_whitespace_preference = editPreserve
                        InstrumentationRegistry.getInstrumentation().runOnMainSync {
                            val dialog = WindowInspector.getGlobalWindowViews().single {
                                it.findViewById<EditText>(R.id.edit_text_word_dialog) != null
                            }
                            dialog.findViewById<EditText>(R.id.edit_text_word_dialog).setText("き ")
                            dialog.findViewById<EditText>(R.id.edit_text_reading_dialog).setText(" $reading　")
                            dialog.findViewById<View>(android.R.id.button1).performClick()
                        }
                        await { rows(reading).singleOrNull()?.second == if (editPreserve) "き " else "き" }
                        assertEquals(id, rows(reading).single().first)
                    }
                }
            }
        } finally {
            runBlocking {
                for (reading in readings) {
                    for ((id, _) in rows(reading)) {
                        if (template) database.userTemplateDao().delete(id)
                        else database.userWordDao().delete(id)
                    }
                }
            }
            preferences.edit().apply {
                if (previous == null) remove(key) else putBoolean(key, previous)
            }.commit()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(20)
        }
        assertTrue("Dictionary screen did not reach the expected state", condition())
    }
}
