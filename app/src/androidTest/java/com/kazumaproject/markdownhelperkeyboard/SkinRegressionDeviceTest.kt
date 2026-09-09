package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs unchanged on the pre-skin revision and the corrected PR, on a physical device. */
@RunWith(AndroidJUnit4::class)
class SkinRegressionDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val ctx get() = ins.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val all = mutableListOf<AccessibilityNodeInfo>()
        fun visit(n: AccessibilityNodeInfo) { all.add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::visit) }
        ui.windows.forEach { it.root?.let(::visit) }
        return all
    }
    private fun node(id: String): AccessibilityNodeInfo? = nodes().firstOrNull {
        it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/$id"
    }
    private fun awaitNode(id: String): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + 45000
        while (SystemClock.uptimeMillis() < end) { node(id)?.let { return it }; SystemClock.sleep(100) }
        error("Missing visible $id; " + nodes().filter { it.isVisibleToUser }.joinToString { "${it.viewIdResourceName}=${it.text}/${it.contentDescription}" })
    }
    private fun bounds(n: AccessibilityNodeInfo) = Rect().also(n::getBoundsInScreen)
    private fun gesture(rect: Rect, dx: Float = 0f, dy: Float = 0f, hold: Long = 35) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float, y: Float) {
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(e, true)); e.recycle()
        }
        val x = rect.exactCenterX(); val y = rect.exactCenterY()
        event(MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(hold)
        if (dx != 0f || dy != 0f) { event(MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(80) }
        event(MotionEvent.ACTION_UP, x + dx, y + dy)
        SystemClock.sleep(500)
    }
    private fun tap(id: String) = gesture(bounds(awaitNode(id)))
    private fun text(s: ActivityScenario<FastInputHostActivity>): String { var result = ""; s.onActivity { result = it.editText.text.toString() }; return result }

    @Test fun physicalSkinMatrix() {
        check(!android.os.Build.FINGERPRINT.contains("generic")) { "Physical device required" }
        check(ctx.packageName.startsWith("com.kazumaproject.skinfidelity")) { "Use isolated APK" }
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val target = "${ctx.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        check(oldIme != target) { "Select the original IME before instrumentation restarts the target" }
        val enabled = shell("ime list -s").lines().contains(target)
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        val label = args.getString("label") ?: "fixed"
        val out = File(ctx.getExternalFilesDir(null), "skin-regression/$label").apply { mkdirs() }
        val rows = JSONArray()
        val failures = mutableListOf<String>()
        var navigationInsets = android.graphics.Insets.NONE
        val defaultPixels = mutableMapOf<String, Pair<Rect, IntArray>>()
        val defaultCandidateGeometry = mutableMapOf<String, String>()
        val skins = (args.getString("skins") ?: "default,cupertino_light,cupertino_dark,default").split(',')
        val floating = args.getString("floating") == "true"
        val symbolRootId = if (floating) "floating_symbol_keyboard" else "keyboard_symbol_view"
        val landscape = args.getString("rotation") == "landscape"
        val large = args.getString("layoutSize") == "large"
        val columns = args.getString("columns") ?: "2"
        val tabs = args.getString("tabs") == "true"
        val candidateHeight = args.getString("candidateHeight")?.toInt() ?: if(large)150 else 110
        val candidateEmptyHeight = args.getString("candidateEmptyHeight")?.toInt() ?: if(large)90 else 60
        val keyboards = (args.getString("keyboards") ?: "TENKEY,QWERTY").split(',')
        fun capture(name: String, rootId: String) {
            SystemClock.sleep(500)
            val all = nodes()
            val row = JSONObject().put("name", name)
            for (id in listOf(rootId, "suggestionView_parent", "suggestion_recycler_view", "candidate_tab_layout", "keyboard_symbol_view", "return_jp_keyboard_button", "key_1", "key_a")) {
                all.firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/$id" }?.let {
                    val r = bounds(it); row.put(id, JSONArray(listOf(r.left,r.top,r.right,r.bottom)))
                }
            }
            all.firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "android:id/inputArea" }?.let {
                val r = bounds(it); row.put("inputArea", JSONArray(listOf(r.left,r.top,r.right,r.bottom)))
            }
            val bmp = requireNotNull(ui.takeScreenshot())
            row.put("navigationInsets", JSONArray(listOf(navigationInsets.left, navigationInsets.top, navigationInsets.right, navigationInsets.bottom)))
            val visibleRoot = all.firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/$rootId" }
            if (visibleRoot != null) {
                val r = bounds(visibleRoot)
                check(r.bottom <= bmp.height - navigationInsets.bottom && r.left >= navigationInsets.left && r.right <= bmp.width - navigationInsets.right) { "Keyboard overlaps navigation: $r / $navigationInsets" }
            }
            if (name.endsWith("composing") && name.startsWith("TENKEY") && !floating) {
                val input = all.first { it.isVisibleToUser && it.viewIdResourceName == "android:id/inputArea" }.let(::bounds)
                val candidates = all.filter { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/suggestion_item_text_view" }.map(::bounds)
                row.put("candidateRows", candidates.map { it.centerY() }.distinct().size)
                row.put("candidateBounds", JSONArray(candidates.map { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) }))
                check(candidates.map { it.centerY() }.distinct().size == columns.toInt()) { "Expected $columns candidate rows: $candidates" }
                check(candidates.isNotEmpty() && candidates.all { it.top >= input.top && it.bottom <= bounds(requireNotNull(visibleRoot)).top }) { "Cupertino candidates clipped: $candidates / $input" }
            }
            if (name.contains("cupertino") && name.endsWith("symbol-category")) {
                val modes = bounds(all.first { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/mode_tab_layout" })
                var whitePixels = 0
                for (y in modes.top until modes.bottom) for (x in modes.left + modes.width()/2 until modes.left + modes.width()*3/4) {
                    val pixel = bmp.getPixel(x,y)
                    if (android.graphics.Color.red(pixel)>245 && android.graphics.Color.green(pixel)>245 && android.graphics.Color.blue(pixel)>245) whitePixels++
                }
                check(whitePixels > 20) { "Selected symbol icon is invisible: $whitePixels foreground pixels" }
            }
            if (name.endsWith("composing") && !floating) {
                val tab = all.firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/candidate_tab_layout" }
                check((tab != null) == tabs) { "Candidate tab visibility differs from preference" }
                if (tab != null && name.contains("cupertino")) {
                    val area = bounds(tab)
                    val selected = if (name.contains("cupertino_dark")) 0xff0091ff.toInt() else 0xff0088ff.toInt()
                    val unselected = if (name.contains("cupertino_dark")) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    var selectedPixels=0; var unselectedPixels=0
                    for (y in area.top until area.bottom) for (x in area.left until area.right) {
                        when (bmp.getPixel(x,y)) { selected -> selectedPixels++; unselected -> unselectedPixels++ }
                    }
                    row.put("selectedTabPixels",selectedPixels).put("unselectedTabPixels",unselectedPixels)
                    check(selectedPixels>20 && unselectedPixels>20) { "Tab colors stale: $selectedPixels / $unselectedPixels" }
                }
            }
            if (!floating && (name.endsWith("empty") || name.endsWith("composing"))) {
                val key = name.substringBefore('-') + "-" + name.substringAfterLast('-')
                val geometry = listOf("inputArea", rootId, "suggestionView_parent", "suggestion_recycler_view", "candidate_tab_layout")
                    .joinToString { "$it=${row.optJSONArray(it)}" }
                if (name.contains("-default-")) defaultCandidateGeometry.putIfAbsent(key, geometry)
                else defaultCandidateGeometry[key]?.let { baseline ->
                    check(geometry == baseline) { "Theme changed candidate spacing: $key\nDefault: $baseline\nSkin: $geometry" }
                }
            }
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
            if (name.contains("-default-") && !name.endsWith("FAILURE")) {
                val phase = name.substringAfter("-default-")
                if (phase in listOf("empty", "composing", "committed", "return")) {
                    val areaId = if (floating) "${ctx.packageName}:id/floating_keyboard_content" else "android:id/inputArea"
                    val area = all.firstOrNull { it.isVisibleToUser && it.viewIdResourceName == areaId }?.let(::bounds)
                    if (area != null) {
                        if (!floating) area.bottom = bmp.height - navigationInsets.bottom
                        val pixels = IntArray(area.width() * area.height())
                        bmp.getPixels(pixels,0,area.width(),area.left,area.top,area.width(),area.height())
                        val key = name.substringBefore('-') + "-" + phase
                        val original = defaultPixels.putIfAbsent(key, Rect(area) to pixels)
                        check(original == null || (original.first == area && original.second.contentEquals(pixels))) { "Default pixels changed after skin round trip: $key" }
                    }
                }
            }
            bmp.recycle()
            File(out, "$name-nodes.txt").writeText(all.filter { it.isVisibleToUser }.joinToString("\n") { "${it.viewIdResourceName} ${it.text} ${it.contentDescription} ${bounds(it)}" })
            rows.put(row)
            File(out,"measurements.json").writeText(rows.toString(2))
        }
        try {
            check(ui.setRotation(if (landscape) android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0))
            check(prefs.edit().clear()
                .putString("keyboard_skin_preference",skins.first())
                .putBoolean("save_last_used_keyboard",false)
                .putBoolean("keyboard_floating_preference",floating)
                .putString("candidate_column_preference",columns).putString("candidate_column_landscape_preference",columns)
                .putBoolean("candidate_tab_visibility_preference",tabs)
                .putFloat("candidate_letter_size_preference",args.getString("candidateFont")?.toFloat() ?: 14f)
                .putInt("candidate_view_height_dp_preference",candidateHeight)
                .putInt("candidate_view_empty_height_dp_preference",candidateEmptyHeight)
                .putInt("candidate_view_height_dp_landscape_preference",candidateHeight)
                .putInt("candidate_view_empty_height_dp_landscape_preference",candidateEmptyHeight)
                .putBoolean("shortcut_toolbar_visibility_preference",true)
                .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference",true)
                .putBoolean("clipboard_preview_enable_preference",false).putBoolean("clipboard_history_preference",false)
                .putBoolean("live_conversion_preference",false).putBoolean("enable_ai_conversion_zenz_preference",false)
                .putBoolean("learn_dictionary_preference",false)
                .putBoolean("qwerty_show_emoji_button_preference",true)
                .putBoolean("landscape_force_qwerty_preference",false)
                .putBoolean("landscape_force_qwerty_romaji_preference",false)
                .putBoolean("tenkey_kana_english_qwerty_preference",false)
                .putBoolean("tenkey_restore_input_mode_on_restart_preference",false)
                .putBoolean("flick_input_only_preference",true)
                .commit())
            val startHostBeforeIme = args.getString("startHostBeforeIme") == "true"
            if (!startHostBeforeIme) { shell("ime enable $target"); shell("ime set $target") }
            for (keyboard in keyboards) {
                prefs.edit().putString("keyboard_order_preference","[\"$keyboard\"]").commit()
                ActivityScenario.launch<FastInputHostActivity>(Intent(ctx, FastInputHostActivity::class.java)).use { scenario ->
                    if (startHostBeforeIme) {
                        scenario.onActivity {
                            check((it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) == landscape)
                        }
                        // The launcher may enforce portrait even when user_rotation is 90.
                        // Establish the editor's actual orientation before binding the IME.
                        SystemClock.sleep(500)
                        shell("ime enable $target"); shell("ime set $target")
                    }
                    for ((index,skin) in skins.withIndex()) {
                        val name = "$keyboard-$index-$skin"
                        try {
                            check(prefs.edit().putString("keyboard_skin_preference",skin).commit())
                            scenario.onActivity { it.restartEditorInput(true) }
                            val keyId = if(keyboard=="TENKEY") "key_1" else "key_a"
                            val rootId = if(floating) { if(keyboard=="TENKEY") "keyboard_view_floating" else "qwerty_view_floating" } else if(keyboard=="TENKEY") "keyboard_view" else "qwerty_view"
                            awaitNode(keyId); SystemClock.sleep(1000)
                            scenario.onActivity { navigationInsets = it.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()) }
                            capture("$name-empty",rootId)
                            if (args.getString("timedContinuous") == "true" && keyboard == "TENKEY") {
                                val traces=JSONArray()
                                val keys=(args.getString("continuousKeys") ?: "key_5,key_4,key_6,key_1,key_11").split(',')
                                for (keyName in keys) for (trial in -1..2) for (hold in listOf(60L,1200L)) {
                                    scenario.onActivity { it.restartEditorInput(true) }; awaitNode(keyName); SystemClock.sleep(500)
                                    val keyNode=awaitNode(keyName)
                                    val expected=keyNode.text.toString().trim()
                                    val anchor=bounds(keyNode)
                                    val x=anchor.exactCenterX(); val y=anchor.exactCenterY()
                                    val path=args.getString("continuousPath") ?: "forward"
                                    val points=when(path) {
                                        "reverse" -> listOf(0f to 0f,0f to 0f,0f to .9f,-.9f to 0f,.9f to 0f,0f to -.9f,0f to 0f)
                                        "boundary" -> listOf(0f to 0f,0f to 0f,0f to -.35f,0f to -.1f,0f to -.35f,0f to -.1f,0f to 0f)
                                        "onset" -> listOf(0f to 0f,0f to 0f,0f to -.35f,0f to 0f,.9f to 0f,-.9f to 0f,0f to 0f)
                                        else -> listOf(0f to 0f,0f to 0f,0f to -.9f,.9f to 0f,-.9f to 0f,0f to .9f,0f to 0f)
                                    }
                                    val offsets=if(path=="onset") {
                                        val center=args.getString("continuousOnsetCenterMs")?.toLong() ?: 500L
                                        val onset=center + if(hold<100)-50L else 50L
                                        listOf(0L,onset,onset+80,onset+160,onset+450,onset+950,onset+1450)
                                    } else listOf(0L,hold,hold+150,hold+650,hold+1150,hold+1650,hold+2150)
                                    val down=SystemClock.uptimeMillis()
                                    val events=JSONArray()
                                    val trace=JSONObject().put("name","$name-$keyName-$hold-$trial")
                                        .put("trial",trial).put("hold",hold).put("key",keyName).put("path",path)
                                        .put("offsets",JSONArray(offsets))
                                        .put("anchor",JSONArray(listOf(anchor.left,anchor.top,anchor.right,anchor.bottom)))
                                        .put("events",events)
                                    fun send(action:Int,px:Float,py:Float) {
                                        val time=SystemClock.uptimeMillis()
                                        val start=SystemClock.elapsedRealtimeNanos()
                                        val event=MotionEvent.obtain(down,time,action,px,py,0)
                                        event.source=InputDevice.SOURCE_TOUCHSCREEN
                                        val accepted=ui.injectInputEvent(event,true); event.recycle()
                                        events.put(JSONObject().put("action",action).put("timeMs",time).put("x",px).put("y",py)
                                            .put("elapsedStartNanos",start).put("elapsedEndNanos",SystemClock.elapsedRealtimeNanos()))
                                        check(accepted)
                                    }
                                    var released=false
                                    try {
                                        send(MotionEvent.ACTION_DOWN,x,y)
                                        for (segment in 1 until points.size) {
                                            while(SystemClock.uptimeMillis()-down < offsets[segment]) {
                                                val elapsed=SystemClock.uptimeMillis()-down
                                                val f=((elapsed-offsets[segment-1]).toFloat()/(offsets[segment]-offsets[segment-1])).coerceIn(0f,1f)
                                                val previous=points[segment-1]; val next=points[segment]
                                                if(segment>1) send(MotionEvent.ACTION_MOVE,
                                                    x+(previous.first+(next.first-previous.first)*f)*anchor.width(),
                                                    y+(previous.second+(next.second-previous.second)*f)*anchor.height())
                                                SystemClock.sleep(16)
                                            }
                                            val point=points[segment]
                                            send(MotionEvent.ACTION_MOVE,x+point.first*anchor.width(),y+point.second*anchor.height())
                                        }
                                        SystemClock.sleep(250)
                                        send(MotionEvent.ACTION_UP,x,y); released=true
                                        SystemClock.sleep(500)
                                        trace.put("expected",expected).put("actual",text(scenario))
                                        check(text(scenario)==expected) { "Continuous gesture committed ${text(scenario)} instead of $expected" }
                                    } finally {
                                        if(!released) send(MotionEvent.ACTION_CANCEL,x,y)
                                        traces.put(trace)
                                        File(out,"$name-continuous-traces.json").writeText(traces.toString(2))
                                    }
                                }
                                ins.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS $label $name timed continuous\n") })
                                continue
                            }
                            if (args.getString("continuous") == "true" && keyboard == "TENKEY") {
                                val anchor = bounds(awaitNode("key_5"))
                                for (hold in listOf(60L, 1200L)) {
                                    val down = SystemClock.uptimeMillis()
                                    val x = anchor.exactCenterX(); val y = anchor.exactCenterY()
                                    fun send(action: Int, px: Float, py: Float) {
                                        val e = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,px,py,0)
                                        e.source = InputDevice.SOURCE_TOUCHSCREEN
                                        check(ui.injectInputEvent(e,true)); e.recycle()
                                    }
                                    send(MotionEvent.ACTION_DOWN,x,y)
                                    SystemClock.sleep(hold)
                                    val path = listOf(0f to -0.9f, 0.9f to 0f, -0.9f to 0f, 0f to 0.9f, 0f to 0f)
                                    var previous = 0f to 0f
                                    try {
                                        for ((step,point) in path.withIndex()) {
                                            for (frame in 1..9) {
                                                val f = frame/9f
                                                send(MotionEvent.ACTION_MOVE,
                                                    x+(previous.first+(point.first-previous.first)*f)*anchor.width(),
                                                    y+(previous.second+(point.second-previous.second)*f)*anchor.height())
                                                SystemClock.sleep(16)
                                            }
                                            capture("$name-continuous-$hold-$step",rootId)
                                            previous=point
                                        }
                                    } finally { send(MotionEvent.ACTION_UP,x,y) }
                                    capture("$name-continuous-$hold-up",rootId)
                                    scenario.onActivity { it.restartEditorInput(true) }; awaitNode(keyId); SystemClock.sleep(500)
                                }
                            }
                            tap(keyId)
                            check(text(scenario).isNotEmpty()) { "Tap did not enter text" }
                            capture("$name-composing",rootId)
                            if (args.getString("exerciseTabs") == "true" && keyboard == "TENKEY") {
                                if(tabs) for ((tabIndex,labelText) in listOf("変換","英数カナ","予測").withIndex()) {
                                    val area=bounds(awaitNode("candidate_tab_layout"))
                                    val tab=nodes().first { it.isVisibleToUser && it.text?.toString()==labelText && area.contains(bounds(it)) }
                                    gesture(bounds(tab)); awaitNode("suggestion_item_text_view")
                                    capture("$name-tab-$tabIndex",rootId)
                                }
                                tap("suggestion_visibility"); awaitNode("candidates_row_view")
                                capture("$name-expanded","candidates_row_view")
                                tap("suggestion_visibility"); awaitNode(keyId)
                                capture("$name-collapsed",rootId)
                            }
                            if (args.getString("candidateOnly") == "true") {
                                ins.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS $label $name candidate rows=$columns tabs=$tabs\n") })
                                continue
                            }
                            if(keyboard=="TENKEY") {
                                tap("suggestion_item_text_view")
                                check(text(scenario).isNotEmpty()) { "Candidate did not commit" }
                            }
                            capture("$name-committed",rootId)
                            scenario.onActivity { it.restartEditorInput(true) }; awaitNode(keyId); SystemClock.sleep(500)
                            gesture(bounds(awaitNode(keyId)), dx = if(keyboard=="TENKEY") -65f else 0f, hold=if(keyboard=="TENKEY")35 else 650)
                            check(text(scenario).isNotEmpty()) { "Flick/long press did not enter text" }
                            capture("$name-gesture",rootId)
                            tap("key_delete")
                            check(text(scenario).isEmpty()) { "Delete did not remove input" }
                            capture("$name-delete",rootId)
                            if (keyboard=="TENKEY") {
                                gesture(bounds(awaitNode(keyId)), hold=650)
                                check(text(scenario).isNotEmpty()) { "Tenkey long press did not input" }
                                capture("$name-longpress",rootId)
                            }
                            scenario.onActivity { it.restartEditorInput(true) }; awaitNode(keyId); SystemClock.sleep(500)
                            if(keyboard=="QWERTY") tap("key_emoji") else {
                                val symbolNode = nodes().firstOrNull { it.isVisibleToUser && (it.contentDescription?.toString()=="symbol" || it.text?.toString()=="記号") }
                                if(symbolNode!=null) gesture(bounds(symbolNode)) else tap("sideKey_symbol_mode_container")
                            }
                            awaitNode("return_jp_keyboard_button")
                            capture("$name-symbol", symbolRootId)
                            val symbol = nodes().firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/symbol_text" }
                            check(symbol != null) { "No visible symbol to input" }; gesture(bounds(symbol)); check(text(scenario).isNotEmpty()) { "Symbol did not enter text" }
                            // Select the third mode tab (symbols), then commit an actual symbol.
                            val modes = bounds(awaitNode("mode_tab_layout"))
                            val third = Rect(modes.left + modes.width()/2, modes.top, modes.left + modes.width()*3/4, modes.bottom)
                            gesture(third)
                            capture("$name-symbol-category", symbolRootId)
                            val beforeSymbol = text(scenario)
                            gesture(bounds(awaitNode("symbol_text")))
                            check(text(scenario) != beforeSymbol) { "Symbol category did not commit" }
                            tap("return_jp_keyboard_button"); awaitNode(keyId)
                            capture("$name-return",rootId)
                            ins.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS $label $name\n") })
                        } catch(t: Throwable) {
                            failures.add("$name: ${t.message}")
                            capture("$name-FAILURE", "keyboard_view")
                        }
                    }
                }
            }
        } finally {
            val edit=prefs.edit().clear()
            saved.forEach { (k,v) -> when(v) { is String -> edit.putString(k,v); is Boolean -> edit.putBoolean(k,v); is Int -> edit.putInt(k,v); is Long -> edit.putLong(k,v); is Float -> edit.putFloat(k,v); is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(k,v as Set<String>) } }
            check(edit.commit())
            if(oldIme.isNotBlank() && oldIme!="null") shell("ime set $oldIme")
            if(!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            File(out,"failures.txt").writeText(failures.joinToString("\n"))
        }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }
}
