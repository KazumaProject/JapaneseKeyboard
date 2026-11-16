package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.ListView
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.ExternalDictionarySettingsFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.qwerty_keyboard.ui.QWERTYButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ClassifiedDictionaryUiInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun views() = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 90000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        error(message)
    }
    private fun screenshot(name: String) {
        ins.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun scrollSettingsTo(fragment: DictionaryPreferenceFragment, key: String) {
        main {
            var parent = fragment.findPreference<androidx.preference.Preference>(key)?.parent
            while (parent != null) {
                if (parent is com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CollapsiblePreferenceCategory) {
                    parent.setExpanded(true, persist = false)
                }
                parent = parent.parent
            }
        }
        ins.waitForIdleSync()
        main {
            val adapter = fragment.listView.adapter as androidx.preference.PreferenceGroup.PreferencePositionCallback
            val position = adapter.getPreferenceAdapterPosition(key)
            check(position >= 0) { "Missing preference: $key" }
            (fragment.listView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
                .scrollToPositionWithOffset(position, 0)
        }
        SystemClock.sleep(400)
    }

    @Test fun everyCategoryLicenseDialogRendersOffline() {
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        shell("ime enable $target")
        try {
        shell("am start -f 0x10008000 -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity")
        lateinit var activity: MainActivity
        await("Settings not ready") {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            val host = resumed?.supportFragmentManager?.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
            val ready = resumed != null && resumed.window.decorView.hasWindowFocus() && MainActivity::class.java.getDeclaredField("isSettingsContentReady")
                .apply { isAccessible = true }.getBoolean(resumed)
            if (!ready || host?.navController?.currentDestination == null) false else { activity = resumed!!; true }
        }
        main { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        main { (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.navigate(R.id.openSourceFragment) }
        lateinit var list: ListView
        try { await("License list not shown") { activity.findViewById<ListView>(R.id.open_source_license_list)?.let { list = it; true } ?: false } } catch (failure: Throwable) {
            screenshot("classified-license-failure.png")
            main { println("CLASSIFIED_SETTINGS_DEST " + (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.currentDestination); println("CLASSIFIED_SETTINGS_TEXT " + descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()) }
            throw failure
        }
        for (spec in ClassifiedDictionaries.all) {
            main {
                val name = activity.getString(spec.nameRes)
                val index = (0 until list.adapter.count).single { list.adapter.getItem(it) == name }
                list.performItemClick(list, index, index.toLong())
            }
            await("${spec.id} notice did not render") { views().filterIsInstance<WebView>().any { it.isShown && it.height >= 200 * context.resources.displayMetrics.density && it.contentHeight > 0 && it.progress == 100 && it.title?.contains(spec.id) == true } }
            if (spec.id in setOf("person/actor", "kampo", "medical")) { SystemClock.sleep(150); screenshot("classified-license-${spec.id.replace('/', '_')}.png") }
            main { views().filterIsInstance<TextView>().first { it.isShown && it.id == android.R.id.button1 }.performClick() }
            await("License dialog did not close") { views().filterIsInstance<WebView>().none { it.isShown } }
        }
        } finally {
            if (!wasEnabled) shell("ime disable $target")
        }
    }

    @Test fun dictionarySettingsExposeAndPersistAllPopulatedIndependentSwitches() {
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to
            (store.isOptionalBundledEnabled(it.category) to store.isExternalEnabledForCategory(it.category)) }
        val settingsPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldHomeMode = settingsPrefs.getBoolean("setting_use_new_home_screen_preference", true)
        var activity: MainActivity? = null
        fun awaitSettingsReady(message: String, condition: () -> Boolean) = await(message) {
            condition() && activity?.window?.decorView?.let { root ->
                descendants(root).none { it.id == R.id.settings_loading_overlay && it.isShown }
            } == true
        }
        try {
            shell("ime enable $target")
            shell("am start -f 0x10008000 -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity")
            awaitSettingsReady("Settings not ready") {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()?.takeIf { it.isSettingsContentReady }?.let { activity = it; true } == true
            }
            lateinit var host: NavHostFragment
            main { host = requireNotNull(activity).supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment }
            val tree = com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.ClassifiedDictionaryNode.load(context)
            for (newHome in listOf(true, false)) {
                settingsPrefs.edit().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
                main {
                    requireNotNull(activity).applySettingHomeModeFromPreference()
                    host.navController.navigate(R.id.classifiedDictionarySettingsFragment)
                }
                lateinit var page: View
                awaitSettingsReady("Dictionary manager not ready") {
                    host.childFragmentManager.primaryNavigationFragment?.view?.takeIf {
                        (it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.classified_list)?.adapter?.itemCount ?: 0) == 7
                    }?.let { page = it; true } == true
                }
                for (spec in ClassifiedDictionaries.all) {
                    val name = context.getString(spec.nameRes)
                    val index = tree.visible(name,
                        com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionarySelectionFilter.ALL,
                        store::isOptionalBundledEnabled).indexOfFirst { it.key == spec.preferenceKey }
                    assertTrue(spec.id, index >= 0)
                    main {
                        page.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.classified_search).setText(name)
                    }
                    val list = page.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.classified_list)
                    fun switch(): com.google.android.material.materialswitch.MaterialSwitch? =
                        descendants(list).filterIsInstance<com.google.android.material.materialswitch.MaterialSwitch>()
                            .firstOrNull { it.contentDescription == name }
                    awaitSettingsReady("${spec.id} switch not displayed") { list.scrollToPosition(index); switch() != null }
                    val original = store.isOptionalBundledEnabled(spec.category)
                    main { switch()!!.performClick() }
                    awaitSettingsReady("${spec.id} change not saved and displayed") { store.isOptionalBundledEnabled(spec.category) == !original && switch()?.isChecked == !original }
                    main { switch()!!.performClick() }
                    awaitSettingsReady("${spec.id} state not restored") { store.isOptionalBundledEnabled(spec.category) == original && switch()?.isChecked == original }
                }
            }
            ClassifiedDictionaries.all.forEach { store.setDisableableCategoryEnabled(it.category, false, true) }
            for (spec in ClassifiedDictionaries.all) {
                main { host.navController.navigate(R.id.externalDictionarySettingsFragment,
                    Bundle().apply { putString("externalDictionaryCategory", spec.category.name) }) }
                var externalSwitch: SwitchPreferenceCompat? = null
                awaitSettingsReady("External ${spec.id} did not open") {
                    val current = host.childFragmentManager.primaryNavigationFragment as? ExternalDictionarySettingsFragment
                    val screen = current?.preferenceScreen
                    val group = screen?.let { (0 until it.preferenceCount).map(it::getPreference) }
                        ?.filterIsInstance<PreferenceCategory>()?.firstOrNull { it.title == context.getString(spec.nameRes) }
                    externalSwitch = group?.let { (0 until it.preferenceCount).map(it::getPreference) }
                        ?.filterIsInstance<SwitchPreferenceCompat>()?.singleOrNull()
                    externalSwitch?.let { !it.isChecked && it.onPreferenceChangeListener != null } == true
                }
                main { val toggle = requireNotNull(externalSwitch); assertTrue(toggle.callChangeListener(true)) }
                awaitSettingsReady("External ${spec.id} did not share ON") { store.isOptionalBundledEnabled(spec.category) }
                main { assertTrue(host.navController.popBackStack()) }
            }
            assertTrue(ClassifiedDictionaries.all.all { store.isOptionalBundledEnabled(it.category) })
        } finally {
            previous.forEach { (category, selection) ->
                store.setOptionalBundledEnabled(category, selection.first)
                store.setExternalEnabledForCategory(category, selection.second)
            }
            settingsPrefs.edit().putBoolean("setting_use_new_home_screen_preference", oldHomeMode).commit()
            main { activity?.finish() }
            if (!wasEnabled) shell("ime disable $target")
        }
    }

    @Test fun actualRomajiImeShowsNewCategoryLabelsAndCommitsCandidates() {
        verifyRomajiLabelsAndCommit()
    }

    @Test fun learnedKampoCandidateKeepsCategoryLabelAndCommits() {
        verifyRomajiLabelsAndCommit(learnedPrediction = false)
    }

    @Test fun predictiveLearnedKampoCandidateKeepsCategoryLabelAndCommits() {
        verifyRomajiLabelsAndCommit(learnedPrediction = true)
    }

    @Test fun exactHomophonesKeepSubcategoryLabelsAndCommitThroughTheRealIme() {
        verifyRomajiLabelsAndCommit(fullyMatched = true)
    }

    @Test fun subdividedPersonEventAndTermCandidatesShowLabelsAndCommit() {
        verifyRomajiLabelsAndCommit(subdivided = true)
    }

    private fun isFullyVisible(view: View): Boolean {
        val visible = android.graphics.Rect()
        val item = view.parent as View
        return item.getGlobalVisibleRect(visible) && visible.width() == item.width && visible.height() == item.height
    }

    private fun verifyRomajiLabelsAndCommit(learnedPrediction: Boolean? = null, fullyMatched: Boolean = false, subdivided: Boolean = false) {
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        check(context.packageName.contains(".classifiedprobe")) { "Use the isolated classifiedprobe APK" }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val testedCategories = if (fullyMatched) ClassifiedDictionaries.all.map { it.category } else if (subdivided)
            listOf(DictionaryCategory.CLASSIFIED_PERSON_ACTOR, DictionaryCategory.CLASSIFIED_EVENT_OCCASION_ORGANIZED_EVENT,
                DictionaryCategory.CLASSIFIED_TERM_GENERAL_FOOD_DRINK_COOKING, DictionaryCategory.CLASSIFIED_TERM_SPECIALIST_INFORMATION_COMMUNICATIONS,
                DictionaryCategory.CLASSIFIED_PERSON_COMEDIAN)
        else listOf(DictionaryCategory.CLASSIFIED_AWARD, DictionaryCategory.CLASSIFIED_KAMPO, DictionaryCategory.CLASSIFIED_MEDICAL)
        val previous = testedCategories.associateWith { store.isOptionalBundledEnabled(it) }
        val oldIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        val learningKeys = listOf("learn_dictionary_preference", "enable_prediction_search_learn_dictionary_preference", "japanese_prediction_enable_preference")
        val previousLearning = learningKeys.associateWith { key ->
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }
        val previousSearch = listOf("n_best_preference", "conversion_beam_width_preference").associateWith { key ->
            if (prefs.contains(key)) prefs.getInt(key, 0) else null
        }
        var repository: LearnRepository? = null
        var previousLearned: LearnEntity? = null
        var seeded = false
        try {
            if (fullyMatched) prefs.edit().putBoolean("learn_dictionary_preference", false)
                .putBoolean("enable_prediction_search_learn_dictionary_preference", false)
                .putBoolean("japanese_prediction_enable_preference", false)
                .putInt("n_best_preference", 2).putInt("conversion_beam_width_preference", 1).commit()
            if (learnedPrediction != null) {
                prefs.edit().putBoolean("learn_dictionary_preference", true)
                    .putBoolean("enable_prediction_search_learn_dictionary_preference", learnedPrediction).commit()
            }
            prefs.edit().putString("keyboard_order_preference", "[\"ROMAJI\"]")
                .putBoolean("save_last_used_keyboard", false).putBoolean("live_conversion_preference", false)
                .putBoolean("show_dictionary_candidate_labels_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false).putBoolean("clipboard_history_preference", false).commit()
            testedCategories.forEach { store.setOptionalBundledEnabled(it, true) }
            shell("ime enable $target"); shell("ime set $target")
            shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity")
            lateinit var activity: FastInputHostActivity
            await("Romaji keyboard not shown") {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().firstOrNull()
                if (resumed == null) false else { activity = resumed; views().filterIsInstance<QWERTYButton>().any { it.isShown && it.text.toString().equals("a", true) } }
            }
            // The keyboard view appears before the editor restart/IME entrance animation completes.
            await("Editor input connection not active") { activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).isActive(activity.editText) }
            ins.waitForIdleSync()
            SystemClock.sleep(2000)
            if (learnedPrediction != null) {
                lateinit var service: IMEService
                main {
                    service = views().mapNotNull { view ->
                        var owner = view.context
                        while (owner is ContextWrapper && owner !is IMEService) owner = owner.baseContext
                        owner as? IMEService
                    }.first()
                }
                repository = service.learnRepository
                runBlocking {
                    previousLearned = repository!!.findLearnDataByInputAndOutput("あせんやく", "阿仙薬")
                    seeded = true
                    repository!!.deleteByInputAndOutput("あせんやく", "阿仙薬")
                    // Seed an actual dictionary candidate's learned score and POS IDs.
                    val entry = LearnEntity(input = "あせんやく", out = "阿仙薬",
                        score = 9_066, leftId = 1851, rightId = 1851)
                    repository!!.insertLearnedData(entry)
                    // Without learned prediction, repeat the normal learning update so the
                    // learned conversion path wins over the classified dictionary path.
                    if (!learnedPrediction) repository!!.upsertLearnedData(entry)
                }
            }
            val samples = if (subdivided) listOf(
                Triple("kuriki", "[芸人]", "くりきひなた"), Triple("kakutatu", "[俳優]", "郭達"), Triple("aomaturi", "[催し・行事]", "アオマツリ"),
                Triple("aigana", "[食品・料理]", "鮎河菜"), Triple("aiaiesu", "[情報・通信]", "IIS"),
            ) else if (fullyMatched) listOf(Triple("syou", "人名", "SHO"), Triple("syou", "人名", "紫耀")) else
                listOf(Triple("aiehusyou", "[賞]", "IF賞"), Triple("asennyaku", "[漢方・生薬]", "阿仙薬"), Triple("asaitami", "[医療]", "朝痛み"))
            for ((input, sampleLabel, expectedSurface) in samples) {
            val categoryId = when (input) {
                "syou" -> "person/personal_name_origin_japanese"
                "kuriki" -> "person/comedian"
                "kakutatu" -> "person/actor"
                "aomaturi" -> "event/occasion/organized_event"
                "aigana" -> "term/general/food_drink_cooking"
                "aiaiesu" -> "term/specialist/information_communications"
                else -> null
            }
            var expectedLabel = sampleLabel
            if (categoryId != null) main {
                // IME service views can use a different configuration from the settings activity.
                val keyboardContext = views().filterIsInstance<QWERTYButton>().first { it.isShown }.context
                val spec = ClassifiedDictionaries.all.single { it.id == categoryId }
                expectedLabel = "[${keyboardContext.getString(spec.labelRes)}]"
            }
            main {
                activity.editText.setText("")
                activity.editText.requestFocus()
            }
            ins.waitForIdleSync()
            SystemClock.sleep(1000)
            var lastKeyUpMs = 0L
            for (letter in input) {
                val point = IntArray(2)
                var x = 0f
                var y = 0f
                main {
                    val key = views().filterIsInstance<QWERTYButton>().first { it.isShown && it.text.toString().equals(letter.toString(), true) }
                    key.getLocationOnScreen(point)
                    x = point[0] + key.width / 2f
                    y = point[1] + key.height / 2f
                }
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    ins.uiAutomation.injectInputEvent(event, true)
                    if (action == MotionEvent.ACTION_UP) lastKeyUpMs = SystemClock.uptimeMillis()
                    event.recycle()
                    SystemClock.sleep(40)
                }
            }
            lateinit var label: TextView
            try { await("Category label $expectedLabel not shown for $input") { views().filterIsInstance<TextView>().firstOrNull { it.isShown && (!fullyMatched || isFullyVisible(it)) && it.id == R.id.suggestion_item_type_text_view && it.text.toString().contains(expectedLabel) && (it.parent as View).findViewById<TextView>(R.id.suggestion_item_text_view).text.toString().trim() == expectedSurface }?.let { label = it; true } ?: false } } catch (failure: Throwable) {
                screenshot("classified-ime-failure.png")
                main { println("CLASSIFIED_IME_EDITOR ${activity.editText.text}"); println("CLASSIFIED_IME_LABELS " + views().filterIsInstance<TextView>().filter { it.id == R.id.suggestion_item_text_view || it.id == R.id.suggestion_item_type_text_view }.map { it.text.toString() }.toList()) }
                throw failure
            }
            // Includes the fixed 40 ms post-key delay and up to 50 ms polling; an observed UI bound, not engine time.
            println("CLASSIFIED_IME_VISIBLE input=$input lastKeyUpToObservedLabelMs=${SystemClock.uptimeMillis() - lastKeyUpMs}")
            screenshot("classified-ime-$input.png")
            var committed = ""
            main {
                val item = label.parent as View
                committed = item.findViewById<TextView>(R.id.suggestion_item_text_view).text.toString().trim()
                item.performClick()
            }
            await("Candidate was not committed") { activity.editText.text.toString() == committed }
            assertEquals(expectedSurface, committed)
            println("CLASSIFIED_IME_COMMIT $expectedLabel $committed")
            val deletePoint = IntArray(2)
            main {
                val key = views().first { it.isShown && it.id == com.kazumaproject.qwerty_keyboard.R.id.key_delete }
                key.getLocationOnScreen(deletePoint)
                deletePoint[0] += key.width / 2
                deletePoint[1] += key.height / 2
            }
            val deleteTime = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(deleteTime, SystemClock.uptimeMillis(), action,
                    deletePoint[0].toFloat(), deletePoint[1].toFloat(), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                ins.uiAutomation.injectInputEvent(event, true)
                event.recycle()
                SystemClock.sleep(40)
            }
            await("Delete did not remove the committed character") { activity.editText.text.toString() == committed.dropLast(1) }
            }
        } finally {
            if (seeded) runBlocking {
                repository!!.deleteByInputAndOutput("あせんやく", "阿仙薬")
                previousLearned?.let { repository!!.insertLearnedData(it) }
            }
            prefs.edit().also { editor ->
                previousSearch.forEach { (key, value) ->
                    if (value == null) editor.remove(key) else editor.putInt(key, value)
                }
                previousLearning.forEach { (key, value) ->
                    if (value == null) editor.remove(key) else editor.putBoolean(key, value)
                }
            }.commit()
            previous.forEach { (category, value) -> store.setOptionalBundledEnabled(category, value) }
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
        }
    }
}
