package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.core.content.FileProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FreeFlickLayoutsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val repository get() = FreeFlickLayoutRepository(context)
    private fun activity() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun views() = activity()?.window?.decorView?.let(::all).orEmpty()
    private fun global() = WindowInspector.getGlobalWindowViews().flatMap(::all)
    private fun nav() = (activity()!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
    private fun await(test: () -> Boolean) {
        repeat(400) { var ready = false; ins.runOnMainSync { ready = test() }; if (ready) return; SystemClock.sleep(50) }
        fail("Layout UI did not reach the expected state")
    }
    private fun start() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putString("sumire_keyboard_style_preference", SumirePopupDefaults.STYLE).commit()
        ins.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await { runCatching { nav().currentDestination != null }.getOrDefault(false) }
        ins.runOnMainSync { nav().navigate(R.id.freeFlickLayoutsFragment) }
        await { views().filterIsInstance<MaterialCardView>().isNotEmpty() }
    }
    private fun button(res: Int) = ins.runOnMainSync { global().filterIsInstance<MaterialButton>().last { it.text == context.getString(res) }.performClick() }
    private fun name(value: String) {
        await { global().filterIsInstance<TextInputEditText>().isNotEmpty() }
        ins.runOnMainSync { global().filterIsInstance<TextInputEditText>().last().setText(value); global().filterIsInstance<Button>().last { it.id == android.R.id.button1 }.performClick() }
    }
    private fun card(name: String) = views().filterIsInstance<MaterialCardView>().first { all(it).filterIsInstance<TextView>().any { v -> v.text.toString() == name } }
    private fun action(name: String, res: Int) = ins.runOnMainSync { all(card(name)).filterIsInstance<MaterialButton>().first { it.text == context.getString(res) }.performClick() }
    private fun menu(name: String, res: Int) {
        ins.runOnMainSync { all(card(name)).filterIsInstance<MaterialButton>().first { it.text == "⋮" }.performClick() }
        await { global().filterIsInstance<ListView>().any { it.isShown } }
        ins.runOnMainSync {
            val list = global().filterIsInstance<ListView>().last { it.isShown }
            val index = (0 until list.adapter.count).first { i -> all(list.adapter.getView(i, null, list)).filterIsInstance<TextView>().any { it.text == context.getString(res) } }
            list.performItemClick(list.adapter.getView(index, null, list), index, list.adapter.getItemId(index))
        }
    }
    private fun dismiss() = ins.runOnMainSync { global().filterIsInstance<Button>().last { it.id == android.R.id.button1 }.performClick() }
    @Test fun createEditPreviewSelectRenameAndDeleteKeepInactiveChangesIsolated() {
        start()
        try {
            val original = SumirePopupRepository(context).load("は")
            button(R.string.free_flick_layout_new); name("Device layout")
            await { views().filterIsInstance<PopupMapEditorCanvas>().isNotEmpty() }
            ins.runOnMainSync {
                views().filterIsInstance<TextInputEditText>().single { it.contentDescription == context.getString(R.string.popup_input) }.setText("★")
            }
            button(R.string.popup_save)
            assertEquals(original, SumirePopupRepository(context).load("は"))
            ins.runOnMainSync { activity()!!.recreate() }
            await { views().filterIsInstance<PopupMapEditorCanvas>().firstOrNull()?.map?.tapText == "★" }
            ins.runOnMainSync { nav().popBackStack() }
            await { views().filterIsInstance<MaterialCardView>().any { all(it).filterIsInstance<TextView>().any { v -> v.text.toString() == "Device layout" } } }
            action("Device layout", R.string.free_flick_layout_show)
            await { global().filterIsInstance<PopupMapGuideView>().isNotEmpty() }
            ins.runOnMainSync {
                val keyboard = global().filterIsInstance<FreeFlickKeyPicker>().last().keyboard
                (0 until keyboard.childCount).map(keyboard::getChildAt).filterIsInstance<TextView>()
                    .single { it.text.toString() == "は" }.performClick()
            }
            await { global().filterIsInstance<PopupMapGuideView>().lastOrNull()?.map?.tapText == "★" }
            dismiss()
            action("Device layout", R.string.free_flick_layout_select)
            await { SumirePopupRepository(context).load("は").maps.single().tapText == "★" }
            menu("Device layout", R.string.popup_rename); name("Renamed layout")
            await { repository.find(repository.selectedId()).name == "Renamed layout" }
            menu("Renamed layout", R.string.popup_delete)
            await { global().filterIsInstance<TextView>().any { it.text.toString().contains("12") && it.id == android.R.id.message } }
            dismiss()
            await { repository.selectedId() == FreeFlickLayoutRepository.BUILTIN_ID }
            assertEquals(original, SumirePopupRepository(context).load("は"))
        } finally { ins.runOnMainSync { activity()?.finish() } }
    }
    @Test fun documentContractsTransferRealFilesCancelCleanlyAndRejectInvalidImports() {
        start()
        val monitors = mutableListOf<Instrumentation.ActivityMonitor>()
        fun result(action: String, result: Int, file: File?) {
            val data = Intent().apply { if (file != null) this.data = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file) }
            val filter = IntentFilter(action).apply { addDataType("*/*"); addCategory(Intent.CATEGORY_OPENABLE) }
            monitors += ins.addMonitor(filter, Instrumentation.ActivityResult(result, data), true)
        }
        fun clearMonitors() { monitors.forEach(ins::removeMonitor); monitors.clear() }
        try {
            val saved = repository.create("Portable"); ins.runOnMainSync { nav().popBackStack(); nav().navigate(R.id.freeFlickLayoutsFragment) }
            await { views().filterIsInstance<MaterialCardView>().size == 2 }
            val file = File(context.cacheDir, "images/layout-contract.json").apply { parentFile!!.mkdirs(); writeText("") }
            result(Intent.ACTION_CREATE_DOCUMENT, Activity.RESULT_OK, file)
            menu("Portable", R.string.free_flick_layout_export)
            await { file.length() > 100 }
            await { runCatching { FreeFlickLayoutCodec.decode(file.readText()).keys == saved.keys }.getOrDefault(false) }
            clearMonitors()
            val before = repository.list(); val selected = repository.selectedId()
            result(Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_CANCELED, null)
            button(R.string.free_flick_layout_import); SystemClock.sleep(300); clearMonitors()
            assertEquals(before, repository.list())
            result(Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_OK, file)
            button(R.string.free_flick_layout_import)
            await { global().filterIsInstance<TextView>().any { it.id == android.R.id.message && it.text.toString().contains("Portable") } }
            dismiss(); await { repository.list().size == before.size + 1 }
            assertEquals("Portable (2)", repository.list().last().name); assertEquals(selected, repository.selectedId())
            clearMonitors()
            file.writeText("{broken")
            val safe = repository.list()
            result(Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_OK, file)
            button(R.string.free_flick_layout_import); SystemClock.sleep(500)
            assertEquals(safe, repository.list()); assertEquals(selected, repository.selectedId())
            file.delete()
        } finally { clearMonitors(); ins.runOnMainSync { activity()?.finish() } }
    }
}
