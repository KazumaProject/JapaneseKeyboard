package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in device probe for settings launch stalls with large user-owned tables.
 *
 * This test deliberately uses the production database name so a later, separately launched IME or
 * settings Activity sees the generated rows. Run it only through
 * `.github/scripts/run-settings-freeze-probe.sh`; the seeding entry point deletes that database.
 */
@RunWith(AndroidJUnit4::class)
class SettingsLaunchFreezeInstrumentedTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun seedSyntheticDatabase() {
        requireProbeEnabled()
        require(arguments.getString(ARG_ALLOW_DESTRUCTIVE_SEED) == "true") {
            "$ARG_ALLOW_DESTRUCTIVE_SEED=true is required because this probe replaces $DATABASE_NAME"
        }
        require(isEmulator() || arguments.getString(ARG_ALLOW_PHYSICAL_RESET) == "true") {
            "Refusing to replace app data on a physical device without $ARG_ALLOW_PHYSICAL_RESET=true"
        }

        val dataset = ProbeDataset.parse(arguments.getString(ARG_DATASET))
        val requestedCount = arguments.getString(ARG_ENTRY_COUNT)?.toIntOrNull() ?: 10_000
        require(requestedCount in ALLOWED_ENTRY_COUNTS) {
            "$ARG_ENTRY_COUNT must be one of ${ALLOWED_ENTRY_COUNTS.sorted()}"
        }
        val entryCount = if (dataset == ProbeDataset.EMPTY) 0 else requestedCount
        val homeMode = HomeMode.parse(arguments.getString(ARG_HOME_MODE))

        check(context.deleteDatabase(DATABASE_NAME) || !context.getDatabasePath(DATABASE_NAME).exists()) {
            "Unable to remove the previous probe database"
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putBoolean(SETTING_HOME_KEY, homeMode == HomeMode.NEW)
            putBoolean(USER_DICTIONARY_ENABLED_KEY, true)
            putBoolean(LEARN_DICTIONARY_ENABLED_KEY, true)
            putBoolean(USER_TEMPLATE_ENABLED_KEY, true)
            putBoolean(TEXT_MACRO_CANDIDATE_ENABLED_KEY, true)
            putInt(ROMAJI_MAP_DATA_VERSION_KEY, 1)
        }

        val startedAt = SystemClock.elapsedRealtime()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
        try {
            database.runInTransaction {
                seedRows(database, dataset, entryCount)
            }
            database.openHelper.writableDatabase.query(
                "PRAGMA wal_checkpoint(TRUNCATE)"
            ).close()
            val actualCounts = readCounts(database)
            assertEquals(entryCountFor(dataset, ProbeDataset.USER, entryCount), actualCounts.user)
            assertEquals(entryCountFor(dataset, ProbeDataset.LEARN, entryCount), actualCounts.learn)
            assertEquals(entryCountFor(dataset, ProbeDataset.TEMPLATE, entryCount), actualCounts.template)
            assertEquals(entryCountFor(dataset, ProbeDataset.MACRO, entryCount), actualCounts.macro)

            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            probeOutputDir().resolve("seed.tsv").writeText(
                "dataset\trequestedCount\tuser\tlearn\ttemplate\tmacro\tdatabaseBytes\tseedMs\n" +
                    "${dataset.argument}\t$entryCount\t${actualCounts.user}\t${actualCounts.learn}\t" +
                    "${actualCounts.template}\t${actualCounts.macro}\t" +
                    "${context.getDatabasePath(DATABASE_NAME).length()}\t$elapsedMs\n"
            )
            writeSqliteStats(database, actualCounts)
        } finally {
            database.close()
        }
    }

    @Test
    fun measureSettingsHomeAndManagementScreens() {
        requireProbeEnabled()
        val dataset = ProbeDataset.parse(arguments.getString(ARG_DATASET))
        val entryCount = if (dataset == ProbeDataset.EMPTY) {
            0
        } else {
            arguments.getString(ARG_ENTRY_COUNT)?.toIntOrNull() ?: 10_000
        }
        require(entryCount in ALLOWED_ENTRY_COUNTS)
        val rounds = arguments.getString(ARG_ROUNDS)?.toIntOrNull() ?: 10
        require(rounds in 1..10) { "$ARG_ROUNDS must be from 1 to 10" }
        val homeMode = HomeMode.parse(arguments.getString(ARG_HOME_MODE))
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putBoolean(SETTING_HOME_KEY, homeMode == HomeMode.NEW)
        }

        val outputDir = probeOutputDir()
        val report = outputDir.resolve("instrumentation-results.tsv")
        report.writeText(
            "dataset\tentryCount\thomeMode\tround\tphase\telapsedMs\tmaxMainStallMs\t" +
                "adapterItems\tstatus\n"
        )
        val monitor = MainThreadStallMonitor(outputDir)
        monitor.start()
        try {
            repeat(rounds) { roundIndex ->
                val round = roundIndex + 1
                runRound(
                    dataset = dataset,
                    entryCount = entryCount,
                    homeMode = homeMode,
                    round = round,
                    report = report,
                    monitor = monitor,
                )
            }
        } finally {
            monitor.close()
        }
    }

    private fun runRound(
        dataset: ProbeDataset,
        entryCount: Int,
        homeMode: HomeMode,
        round: Int,
        report: File,
        monitor: MainThreadStallMonitor,
    ) {
        val resumedPhase = "home_activity_resumed"
        monitor.beginPhase(resumedPhase)
        val launchStartedAt = SystemClock.elapsedRealtime()
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        try {
            val expectedHome = if (homeMode == HomeMode.NEW) {
                R.id.navigation_setting
            } else {
                R.id.settingMainFragment
            }
            awaitDestination(scenario, expectedHome)
            appendResult(
                report,
                dataset,
                entryCount,
                homeMode,
                round,
                resumedPhase,
                SystemClock.elapsedRealtime() - launchStartedAt,
                monitor.finishPhase(),
                adapterItems = -1,
            )

            val firstDrawPhase = "home_first_draw"
            monitor.beginPhase(firstDrawPhase)
            awaitNextFrame(scenario)
            instrumentation.waitForIdleSync()
            appendResult(
                report,
                dataset,
                entryCount,
                homeMode,
                round,
                firstDrawPhase,
                SystemClock.elapsedRealtime() - launchStartedAt,
                monitor.finishPhase(),
                adapterItems = -1,
            )

            MANAGEMENT_SCREENS.forEach { screen ->
                val expectedItems = expectedRows(dataset, screen.dataset, entryCount)
                measureNavigation(
                    scenario,
                    screen,
                    expectedItems,
                    dataset,
                    entryCount,
                    homeMode,
                    round,
                    report,
                    monitor,
                )
                measureBackgroundResume(
                    scenario,
                    screen,
                    expectedItems,
                    dataset,
                    entryCount,
                    homeMode,
                    round,
                    report,
                    monitor,
                )
                measureRecreate(
                    scenario,
                    screen,
                    expectedItems,
                    dataset,
                    entryCount,
                    homeMode,
                    round,
                    report,
                    monitor,
                )
                scenario.onActivity { activity -> activity.mainNavController().popBackStack() }
                awaitDestination(scenario, expectedHome)
            }
        } finally {
            scenario.close()
        }
    }

    private fun measureNavigation(
        scenario: ActivityScenario<MainActivity>,
        screen: ManagementScreen,
        expectedItems: Int,
        dataset: ProbeDataset,
        entryCount: Int,
        homeMode: HomeMode,
        round: Int,
        report: File,
        monitor: MainThreadStallMonitor,
    ) {
        val resumedPhase = "${screen.label}_navigate_resumed"
        monitor.beginPhase(resumedPhase)
        val startedAt = SystemClock.elapsedRealtime()
        scenario.onActivity { activity -> activity.mainNavController().navigate(screen.destinationId) }
        awaitScreenResumed(scenario, screen)
        appendResult(
            report,
            dataset,
            entryCount,
            homeMode,
            round,
            resumedPhase,
            SystemClock.elapsedRealtime() - startedAt,
            monitor.finishPhase(),
            adapterItems = -1,
        )

        val firstDrawPhase = "${screen.label}_first_draw"
        monitor.beginPhase(firstDrawPhase)
        awaitNextFrame(scenario)
        appendResult(
            report,
            dataset,
            entryCount,
            homeMode,
            round,
            firstDrawPhase,
            SystemClock.elapsedRealtime() - startedAt,
            monitor.finishPhase(),
            adapterItems = -1,
        )

        val listReadyPhase = "${screen.label}_list_ready"
        monitor.beginPhase(listReadyPhase)
        val actualItems = awaitScreenItems(scenario, screen, expectedItems)
        appendResult(
            report,
            dataset,
            entryCount,
            homeMode,
            round,
            listReadyPhase,
            SystemClock.elapsedRealtime() - startedAt,
            monitor.finishPhase(),
            actualItems,
        )
    }

    private fun measureBackgroundResume(
        scenario: ActivityScenario<MainActivity>,
        screen: ManagementScreen,
        expectedItems: Int,
        dataset: ProbeDataset,
        entryCount: Int,
        homeMode: HomeMode,
        round: Int,
        report: File,
        monitor: MainThreadStallMonitor,
    ) {
        scenario.moveToState(Lifecycle.State.CREATED)
        val phase = "${screen.label}_resume_list_ready"
        monitor.beginPhase(phase)
        val startedAt = SystemClock.elapsedRealtime()
        scenario.moveToState(Lifecycle.State.RESUMED)
        val actualItems = awaitScreenItems(scenario, screen, expectedItems)
        appendResult(
            report,
            dataset,
            entryCount,
            homeMode,
            round,
            phase,
            SystemClock.elapsedRealtime() - startedAt,
            monitor.finishPhase(),
            actualItems,
        )
    }

    private fun measureRecreate(
        scenario: ActivityScenario<MainActivity>,
        screen: ManagementScreen,
        expectedItems: Int,
        dataset: ProbeDataset,
        entryCount: Int,
        homeMode: HomeMode,
        round: Int,
        report: File,
        monitor: MainThreadStallMonitor,
    ) {
        val phase = "${screen.label}_recreate_list_ready"
        monitor.beginPhase(phase)
        val startedAt = SystemClock.elapsedRealtime()
        scenario.recreate()
        val actualItems = awaitScreenItems(scenario, screen, expectedItems)
        appendResult(
            report,
            dataset,
            entryCount,
            homeMode,
            round,
            phase,
            SystemClock.elapsedRealtime() - startedAt,
            monitor.finishPhase(),
            actualItems,
        )
    }

    private fun awaitDestination(
        scenario: ActivityScenario<MainActivity>,
        destinationId: Int,
    ) {
        awaitCondition("destination $destinationId") {
            var currentDestination: Int? = null
            scenario.onActivity { currentDestination = it.mainNavController().currentDestination?.id }
            currentDestination == destinationId
        }
    }

    private fun awaitNextFrame(scenario: ActivityScenario<MainActivity>) {
        val rendered = AtomicBoolean(false)
        scenario.onActivity { activity ->
            activity.window.decorView.postOnAnimation { rendered.set(true) }
        }
        awaitCondition("next rendered frame") { rendered.get() }
    }

    private fun awaitScreenResumed(
        scenario: ActivityScenario<MainActivity>,
        screen: ManagementScreen,
    ) {
        awaitCondition("${screen.label} RESUMED") {
            var isResumed = false
            scenario.onActivity { activity ->
                val fragment = activity.currentNavigationFragment()
                isResumed = activity.mainNavController().currentDestination?.id == screen.destinationId &&
                    fragment?.lifecycle?.currentState == Lifecycle.State.RESUMED
            }
            isResumed
        }
    }

    private fun awaitScreenItems(
        scenario: ActivityScenario<MainActivity>,
        screen: ManagementScreen,
        expectedItems: Int,
    ): Int {
        var actualItems = -1
        try {
            awaitCondition("${screen.label} adapter count $expectedItems") {
                scenario.onActivity { activity ->
                    val fragment = activity.currentNavigationFragment()
                    val recycler: RecyclerView? = when (screen.dataset) {
                        ProbeDataset.LEARN -> activity.findViewById(
                            R.id.learn_dictionary_recycler_view
                        )
                        ProbeDataset.USER, ProbeDataset.TEMPLATE -> activity.findViewById(
                            R.id.recycler_view_user_words
                        )
                        ProbeDataset.MACRO -> fragment?.view?.findFirstRecyclerView()
                        else -> null
                    }
                    actualItems = recycler?.adapter?.itemCount ?: -1
                }
                actualItems == expectedItems
            }
        } catch (error: AssertionError) {
            writeScreenDiagnostics(scenario, screen, expectedItems, actualItems)
            throw error
        }
        instrumentation.waitForIdleSync()
        return actualItems
    }

    private fun writeScreenDiagnostics(
        scenario: ActivityScenario<MainActivity>,
        screen: ManagementScreen,
        expectedItems: Int,
        actualItems: Int,
    ) {
        var diagnostics = "Activity unavailable"
        scenario.onActivity { activity ->
            val fragment = activity.currentNavigationFragment()
            val recyclerDescriptions = mutableListOf<String>()
            activity.window.decorView.collectRecyclerViewDescriptions(recyclerDescriptions)
            diagnostics = buildString {
                appendLine("screen=${screen.label}")
                appendLine("destination=${activity.mainNavController().currentDestination?.id}")
                appendLine("fragment=${fragment?.javaClass?.name}")
                appendLine("fragmentState=${fragment?.lifecycle?.currentState}")
                appendLine("fragmentView=${fragment?.view?.javaClass?.name}")
                appendLine("expectedItems=$expectedItems")
                appendLine("actualItems=$actualItems")
                recyclerDescriptions.forEach(::appendLine)
            }
        }
        probeOutputDir().resolve("screen-${screen.label}-diagnostics.txt").writeText(diagnostics)
    }

    private fun View.collectRecyclerViewDescriptions(destination: MutableList<String>) {
        if (this is RecyclerView) {
            val resourceName = runCatching { resources.getResourceEntryName(id) }
                .getOrDefault("no-id")
            destination += "recycler=$resourceName adapter=${adapter?.javaClass?.name} " +
                "items=${adapter?.itemCount ?: -1} attached=$isAttachedToWindow"
        }
        if (this !is ViewGroup) return
        for (index in 0 until childCount) {
            getChildAt(index).collectRecyclerViewDescriptions(destination)
        }
    }

    private fun awaitCondition(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        writeThreadDump(
            destination = probeOutputDir().resolve(
                "timeout-${label.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}.txt"
            ),
            phase = label,
            lagMs = UI_TIMEOUT_MS,
        )
        throw AssertionError("Timed out after ${UI_TIMEOUT_MS}ms waiting for $label")
    }

    private fun MainActivity.mainNavController(): NavController {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        return navHost.navController
    }

    private fun MainActivity.currentNavigationFragment() =
        (supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_activity_main
        ) as? NavHostFragment)?.childFragmentManager?.primaryNavigationFragment

    private fun View.findFirstRecyclerView(): RecyclerView? {
        if (this is RecyclerView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findFirstRecyclerView()?.let { return it }
        }
        return null
    }

    private fun appendResult(
        report: File,
        dataset: ProbeDataset,
        entryCount: Int,
        homeMode: HomeMode,
        round: Int,
        phase: String,
        elapsedMs: Long,
        maxMainStallMs: Long,
        adapterItems: Int,
    ) {
        val status = when {
            elapsedMs >= ANR_THRESHOLD_MS || maxMainStallMs >= ANR_THRESHOLD_MS -> "ANR_RISK"
            elapsedMs >= STALL_DUMP_THRESHOLD_MS || maxMainStallMs >= STALL_DUMP_THRESHOLD_MS ->
                "STALL"
            else -> "OK"
        }
        report.appendText(
            "${dataset.argument}\t$entryCount\t${homeMode.argument}\t$round\t$phase\t" +
                "$elapsedMs\t$maxMainStallMs\t$adapterItems\t$status\n"
        )
    }

    private fun seedRows(database: AppDatabase, dataset: ProbeDataset, count: Int) {
        if (dataset.includes(ProbeDataset.USER)) seedUserWords(database, count)
        if (dataset.includes(ProbeDataset.LEARN)) seedLearnedWords(database, count)
        if (dataset.includes(ProbeDataset.TEMPLATE)) seedTemplates(database, count)
        if (dataset.includes(ProbeDataset.MACRO)) seedMacros(database, count)
    }

    private fun seedUserWords(database: AppDatabase, count: Int) {
        val statement = database.openHelper.writableDatabase.compileStatement(
            "INSERT INTO user_word(word, reading, posIndex, posScore) VALUES (?, ?, 0, 4000)"
        )
        repeat(count) { index ->
            statement.clearBindings()
            statement.bindString(1, "probe-user-${index.padded()}")
            statement.bindString(2, generatedReading(index))
            statement.executeInsert()
        }
        statement.close()
    }

    private fun seedLearnedWords(database: AppDatabase, count: Int) {
        val statement = database.openHelper.writableDatabase.compileStatement(
            """
            INSERT INTO learn_table(
                input, out, score, leftId, rightId, usageCount, lastUsedAt, isPhrase
            ) VALUES (?, ?, ?, NULL, NULL, ?, ?, ?)
            """.trimIndent()
        )
        repeat(count) { index ->
            statement.clearBindings()
            statement.bindString(1, generatedReading(index))
            statement.bindString(2, "probe-learn-${index.padded()}")
            statement.bindLong(3, (1000 + index % 10_000).toLong())
            statement.bindLong(4, (1 + index % 20).toLong())
            statement.bindLong(5, index.toLong())
            statement.bindLong(6, if (index % 3 == 0) 1 else 0)
            statement.executeInsert()
        }
        statement.close()
    }

    private fun seedTemplates(database: AppDatabase, count: Int) {
        val statement = database.openHelper.writableDatabase.compileStatement(
            "INSERT INTO user_template(word, reading, posIndex, posScore) VALUES (?, ?, 0, 4000)"
        )
        repeat(count) { index ->
            statement.clearBindings()
            statement.bindString(1, templateBody(index))
            statement.bindString(2, generatedReading(index))
            statement.executeInsert()
        }
        statement.close()
    }

    private fun seedMacros(database: AppDatabase, count: Int) {
        val statement = database.openHelper.writableDatabase.compileStatement(
            "INSERT INTO text_macro(name, reading, body, enabled) VALUES (?, ?, ?, 1)"
        )
        repeat(count) { index ->
            statement.clearBindings()
            statement.bindString(1, "probe-macro-${index.padded()}")
            statement.bindString(2, generatedReading(index))
            statement.bindString(3, if (index % 10 == 0) LONG_MACRO_BODY else SHORT_MACRO_BODY)
            statement.executeInsert()
        }
        statement.close()
    }

    private fun readCounts(database: AppDatabase): TableCounts {
        fun count(table: String): Int {
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                return cursor.getInt(0)
            }
        }
        return TableCounts(
            user = count("user_word"),
            learn = count("learn_table"),
            template = count("user_template"),
            macro = count("text_macro"),
        )
    }

    private fun writeSqliteStats(database: AppDatabase, counts: TableCounts) {
        fun pragma(name: String): String = database.openHelper.readableDatabase
            .query("PRAGMA $name")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "unavailable" }

        probeOutputDir().resolve("sqlite-stats.tsv").writeText(
            "pageCount\tpageSize\tfreelistCount\tjournalMode\tuser\tlearn\ttemplate\tmacro\n" +
                "${pragma("page_count")}\t${pragma("page_size")}\t" +
                "${pragma("freelist_count")}\t${pragma("journal_mode")}\t" +
                "${counts.user}\t${counts.learn}\t${counts.template}\t${counts.macro}\n"
        )
    }

    private fun generatedReading(index: Int): String =
        "a" + index.toString(36).lowercase(Locale.ROOT).padStart(7, '0') + "probe"

    private fun templateBody(index: Int): String {
        val prefix = "probe-template-${index.padded()}-"
        val targetLength = if (index % 10 == 0) 1024 else 64
        return prefix + "x".repeat(max(0, targetLength - prefix.length))
    }

    private fun Int.padded(): String = toString().padStart(8, '0')

    private fun expectedRows(
        dataset: ProbeDataset,
        screenDataset: ProbeDataset,
        count: Int,
    ): Int = if (dataset.includes(screenDataset)) count else 0

    private fun entryCountFor(
        dataset: ProbeDataset,
        member: ProbeDataset,
        count: Int,
    ): Int = if (dataset.includes(member)) count else 0

    private fun probeOutputDir(): File = requireNotNull(context.getExternalFilesDir(null))
        .resolve(OUTPUT_DIRECTORY)
        .apply { mkdirs() }

    private fun requireProbeEnabled() {
        assumeTrue(arguments.getString(ARG_PROBE_ENABLED) == "true")
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.PRODUCT.contains("sdk_gphone")

    private data class TableCounts(
        val user: Int,
        val learn: Int,
        val template: Int,
        val macro: Int,
    )

    private data class ManagementScreen(
        val label: String,
        val destinationId: Int,
        val dataset: ProbeDataset,
    )

    private enum class HomeMode(val argument: String) {
        NEW("new"),
        LEGACY("legacy");

        companion object {
            fun parse(value: String?): HomeMode {
                val argument = value ?: NEW.argument
                return requireNotNull(entries.firstOrNull { it.argument == argument }) {
                    "$ARG_HOME_MODE must be one of ${entries.map { it.argument }}"
                }
            }
        }
    }

    private enum class ProbeDataset(val argument: String) {
        EMPTY("empty"),
        USER("user"),
        LEARN("learn"),
        TEMPLATE("template"),
        MACRO("macro"),
        COMBINED("combined");

        fun includes(member: ProbeDataset): Boolean = this == COMBINED || this == member

        companion object {
            fun parse(value: String?): ProbeDataset {
                val argument = value ?: COMBINED.argument
                return requireNotNull(entries.firstOrNull { it.argument == argument }) {
                    "$ARG_DATASET must be one of ${entries.map { it.argument }}"
                }
            }
        }
    }

    private class MainThreadStallMonitor(private val outputDir: File) : AutoCloseable {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "settings-freeze-watchdog").apply { isDaemon = true }
        }
        private val running = AtomicBoolean(false)
        private val lastMainAckMs = AtomicLong(SystemClock.uptimeMillis())
        private val phaseMaxStallMs = AtomicLong(0)
        private val currentPhase = AtomicReference("idle")
        private val dumpCount = AtomicInteger(0)
        private val dumpedForCurrentStall = AtomicBoolean(false)

        fun start() {
            running.set(true)
            scheduler.scheduleAtFixedRate(::sample, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }

        fun beginPhase(phase: String) {
            currentPhase.set(phase)
            phaseMaxStallMs.set(0)
            lastMainAckMs.set(SystemClock.uptimeMillis())
            mainHandler.post { lastMainAckMs.set(SystemClock.uptimeMillis()) }
        }

        fun finishPhase(): Long = phaseMaxStallMs.get()

        private fun sample() {
            if (!running.get()) return
            val now = SystemClock.uptimeMillis()
            mainHandler.post { lastMainAckMs.set(SystemClock.uptimeMillis()) }
            val lag = now - lastMainAckMs.get()
            phaseMaxStallMs.updateAndGet { current -> max(current, lag) }
            if (lag < STALL_DUMP_THRESHOLD_MS) {
                dumpedForCurrentStall.set(false)
            }
            if (
                lag >= STALL_DUMP_THRESHOLD_MS &&
                dumpedForCurrentStall.compareAndSet(false, true) &&
                dumpCount.get() < MAX_STACK_DUMPS
            ) {
                dumpStacks(lag)
            }
        }

        private fun dumpStacks(lagMs: Long) {
            val number = dumpCount.incrementAndGet()
            val phase = currentPhase.get().replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            writeThreadDump(
                destination = outputDir.resolve(
                    "stack-${number.toString().padStart(2, '0')}-$phase.txt"
                ),
                phase = phase,
                lagMs = lagMs,
            )
        }

        override fun close() {
            running.set(false)
            scheduler.shutdownNow()
        }
    }

    companion object {
        private const val DATABASE_NAME = "learn_database"
        private const val OUTPUT_DIRECTORY = "settings-freeze"
        private const val SETTING_HOME_KEY = "setting_use_new_home_screen_preference"
        private const val USER_DICTIONARY_ENABLED_KEY = "user_dictionary_preference"
        private const val LEARN_DICTIONARY_ENABLED_KEY = "learn_dictionary_preference"
        private const val USER_TEMPLATE_ENABLED_KEY = "user_template_preference"
        private const val TEXT_MACRO_CANDIDATE_ENABLED_KEY = "text_macro_candidate_preference"
        private const val ROMAJI_MAP_DATA_VERSION_KEY = "romaji_map_data_version"
        private const val ARG_PROBE_ENABLED = "settingsFreezeProbe"
        private const val ARG_ALLOW_DESTRUCTIVE_SEED = "allowDestructiveSeed"
        private const val ARG_ALLOW_PHYSICAL_RESET = "allowPhysicalDeviceDataReset"
        private const val ARG_DATASET = "dataset"
        private const val ARG_ENTRY_COUNT = "entryCount"
        private const val ARG_ROUNDS = "rounds"
        private const val ARG_HOME_MODE = "homeMode"
        private const val POLL_INTERVAL_MS = 50L
        private const val UI_TIMEOUT_MS = 10_000L
        private const val HEARTBEAT_INTERVAL_MS = 100L
        private const val STALL_DUMP_THRESHOLD_MS = 1_000L
        private const val ANR_THRESHOLD_MS = 5_000L
        private const val MAX_STACK_DUMPS = 100
        private val ALLOWED_ENTRY_COUNTS = setOf(0, 1_000, 10_000, 50_000)
        private val SHORT_MACRO_BODY = "probe macro body " + "x".repeat(48)
        private val LONG_MACRO_BODY = "probe macro body " + "x".repeat(1008)
        private val MANAGEMENT_SCREENS = listOf(
            ManagementScreen("learn", R.id.navigation_learn_dictionary, ProbeDataset.LEARN),
            ManagementScreen("user", R.id.navigation_user_dictionary, ProbeDataset.USER),
            ManagementScreen("template", R.id.userTemplateFragment, ProbeDataset.TEMPLATE),
            ManagementScreen("macro", R.id.textMacroFragment, ProbeDataset.MACRO),
        )

        private fun writeThreadDump(destination: File, phase: String, lagMs: Long) {
            destination.writeText(
                buildString {
                    appendLine(
                        "phase=$phase lagMs=$lagMs uptimeMs=${SystemClock.uptimeMillis()}"
                    )
                    Thread.getAllStackTraces()
                        .entries
                        .sortedBy { it.key.name }
                        .forEach { (thread, stack) ->
                            appendLine()
                            appendLine(
                                "thread=${thread.name} id=${thread.id} state=${thread.state} " +
                                    "daemon=${thread.isDaemon}"
                            )
                            stack.forEach { frame -> appendLine("  at $frame") }
                        }
                }
            )
        }
    }
}
