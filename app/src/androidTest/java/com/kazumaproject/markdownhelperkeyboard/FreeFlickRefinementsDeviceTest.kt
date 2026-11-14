package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.*
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FreeFlickRefinementsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val automation get() = ins.uiAutomation
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    private fun <T> main(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var value: T? = null; ins.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun windows() = WindowInspector.getGlobalWindowViews().flatMap(::all)
    private fun activity() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
    private fun views() = activity()?.window?.decorView?.let(::all).orEmpty()
    private fun nav() = activity()!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private fun await(condition: () -> Boolean) {
        repeat(200) { if (main(condition)) return; SystemClock.sleep(100) }; fail("Free Flick screen did not become ready")
    }
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        SystemClock.sleep(150)
        val file = File(context.getExternalFilesDir("free-flick-review"), "$name.png")
        file.parentFile!!.mkdirs()
        requireNotNull(automation.takeScreenshot()).let { bitmap ->
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }; bitmap.recycle()
        }
    }
    @Before fun setup() {
        require(context.packageName.endsWith(".freeflicksize.popuptest")) { "Use an isolated review install" }
        prefs.edit().clear().putString("keyboard_order_preference","[\"SUMIRE\"]")
            .putBoolean("save_last_used_keyboard",false).putBoolean("keyboard_floating_preference",false)
            .putBoolean("live_conversion_preference",false).putBoolean("learn_dictionary_preference",false)
            .putBoolean("landscape_force_qwerty_preference",false)
            .putBoolean(AppPreference.SUMIRE_SHOW_FLICK_POPUP_KEY,true)
            .putBoolean(AppPreference.SUMIRE_SHOW_LONG_PRESS_POPUP_KEY,true).commit()
        AppPreference.sumire_keyboard_style = SumirePopupDefaults.STYLE
        AppPreference.sumire_input_method = "toggle"
        AppPreference.sumire_keymap_guide_japanese = true
        AppPreference.keyboard_height = 220
    }
    private fun start(destination: Int) {
        ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await { activity()!=null }
        main { nav().navController.navigate(destination) }
    }
    private fun key(keyboard: FlickKeyboardView, label: String): Rect = main {
        val view = (0 until keyboard.childCount).map(keyboard::getChildAt).filterIsInstance<TextView>().single { it.text.toString()==label && it.isShown }
        val p = IntArray(2).also(view::getLocationOnScreen)
        Rect(p[0],p[1],p[0]+view.width,p[1]+view.height)
    }
    private fun event(down: Long, action: Int, key: Rect, x: Float=0f, y: Float=0f) {
        MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,key.exactCenterX()+x*key.width(),key.exactCenterY()+y*key.height(),0).apply {
            source=InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(this,true)) } finally { recycle() }
        }
    }
    private fun shaped(): PopupKeyConfig {
        val initial=SumirePopupDefaults.create("は")
        return initial.withMap(initial.maps.single().let { page -> page.copy(regions=page.regions.mapIndexed { i,r -> when(i) {
            0 -> r.copy(action=PopupAction(text="○"),outline=PopupOutline())
            1 -> r.copy(action=PopupAction(text="△"),outline=PopupOutline(PopupOutline.TRIANGLE))
            else -> r
        } }) })
    }
    @Test fun settingsPreviewUsesRealKeysSavedMapsAndBothPopupPresentations() {
        start(R.id.freeFlickSettingsFragment)
        await { (nav().childFragmentManager.primaryNavigationFragment as? PreferenceFragmentCompat)?.findPreference<FreeFlickPreviewPreference>("sumire_free_flick_preview")!=null }
        main { (nav().childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat).scrollToPreference("sumire_free_flick_preview") }
        await { views().filterIsInstance<FreeFlickKeyboardPreview>().any { it.isShown && it.height>0 } }
        fun preview() = views().filterIsInstance<FreeFlickKeyboardPreview>().single()
        main { assertEquals(11,preview().previewLayout!!.keys.count {it.popupMapConfig!=null}) }
        val source=key(main {preview().keyboard},"は"); var down=SystemClock.uptimeMillis()
        event(down,MotionEvent.ACTION_DOWN,source);SystemClock.sleep(600)
        screenshot("01-settings-default-size")
        event(down,MotionEvent.ACTION_UP,source)
        val config=shaped();SumirePopupRepository(context).save(config)
        prefs.edit().putString(FreeFlickPreferences.PRESENTATION,"guide_above_key").putBoolean(FreeFlickPreferences.NEAREST,false).commit()
        await { preview().previewLayout!!.keys.single {it.popupMapConfig?.keyId=="は"}.popupMapConfig==config }
        val current=key(main {preview().keyboard},"は");down=SystemClock.uptimeMillis()
        event(down,MotionEvent.ACTION_DOWN,current);SystemClock.sleep(600);event(down,MotionEvent.ACTION_MOVE,current,-1f,0f)
        await { windows().filterIsInstance<PopupMapGuideView>().any {it.map==config.maps.single() && it.keyWidth>10f} }
        screenshot("02-settings-above-key-ranges")
        event(down,MotionEvent.ACTION_UP,current,-1f,0f)
        await { views().filterIsInstance<TextView>().any {it.id==R.id.free_flick_preview_result && "○" in it.text} }
        main { activity()!!.finish() }
    }
    @Test fun punctuationEditingAndNewPopupUseTapSize() {
        start(R.id.popupMapEditorFragment)
        await { views().any {it is FreeFlickKeyPicker} }
        main {
            val keyboard=views().filterIsInstance<FreeFlickKeyPicker>().single().keyboard
            (0 until keyboard.childCount).map(keyboard::getChildAt).filterIsInstance<TextView>()
                .single {it.text.toString()==SumirePopupDefaults.PUNCTUATION_KEY}.performClick()
        }
        fun canvas()=views().filterIsInstance<PopupMapEditorCanvas>().single()
        await { canvas().config?.keyId==SumirePopupDefaults.PUNCTUATION_KEY }
        main { views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.popup_add)}.performClick() }
        await { canvas().selectedBounds!=null && canvas().map!!.regions.size==5 }
        main {
            assertEquals(PopupBounds.KEY.width,canvas().selectedBounds!!.width,.0001f)
            assertEquals(PopupBounds.KEY.height,canvas().selectedBounds!!.height,.0001f)
            assertTrue(PopupMapValidator.conflicts(canvas().config!!).isEmpty())
            views().filterIsInstance<TextInputEditText>().single {it.contentDescription==context.getString(R.string.popup_input)}.setText("追加")
            canvas().requestRectangleOnScreen(Rect(0,0,canvas().width,canvas().height),true)
        }
        screenshot("03-editor-punctuation-new-popup")
        main { views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.popup_save)}.performClick() }
        val saved=SumirePopupRepository(context).load(SumirePopupDefaults.PUNCTUATION_KEY)
        assertEquals(5,saved.maps.single().regions.size)
        assertTrue(saved.maps.single().regions.any {it.action.text=="追加" && kotlin.math.abs(it.bounds.width-PopupBounds.KEY.width)<.0001f && kotlin.math.abs(it.bounds.height-PopupBounds.KEY.height)<.0001f})
        main { activity()!!.finish() }
    }
    private fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use {it.readText()}
    @Test fun realImeUsesTheSameGuideAndTheActualShapeHitRanges() {
        val originalIme=shell("settings get secure default_input_method").trim()
        val target="${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val config=shaped();SumirePopupRepository(context).save(config)
        prefs.edit().putString(FreeFlickPreferences.PRESENTATION,"guide_above_key").putBoolean(FreeFlickPreferences.NEAREST,false).commit()
        var host: FastInputHostSession?=null
        try {
            shell("ime enable $target");shell("ime set $target")
            host=FastInputHostSession.launch(context)
            host.onActivity {it.requestImeForEditor()}
            fun keyboard()=windows().filterIsInstance<FlickKeyboardView>().firstOrNull {view -> view.isShown &&
                (0 until view.childCount).map(view::getChildAt).filterIsInstance<TextView>().any {it.text.toString()=="は"} }
            await {keyboard()!=null}
            fun stableKey(): Rect {
                var previous=key(main {keyboard()!!},"は");var stable=0
                repeat(50) {
                    SystemClock.sleep(100);val current=key(main {keyboard()!!},"は")
                    stable=if(current==previous) stable+1 else 0
                    if(stable>=4)return current
                    previous=current
                }
                error("IME key geometry did not settle")
            }
            main {assertNotNull(keyboard()!!.let {view -> (0 until view.childCount).map(view::getChildAt).filterIsInstance<TextView>().singleOrNull {it.text.toString()==SumirePopupDefaults.PUNCTUATION_KEY}})}
            for ((point,expected) in listOf((-1f to 0f) to "○",(-1.45f to -.45f) to "",(0f to -1f) to "△")) {
                host.onActivity {it.restartEditorInput(true)};SystemClock.sleep(250)
                val source=stableKey();val down=SystemClock.uptimeMillis()
                event(down,MotionEvent.ACTION_DOWN,source);SystemClock.sleep(600)
                event(down,MotionEvent.ACTION_MOVE,source,point.first,point.second)
                await {windows().filterIsInstance<PopupMapGuideView>().any {it.map==config.maps.single() && it.keyWidth>10f}}
                main {
                    val guide=windows().filterIsInstance<PopupMapGuideView>().single {it.map==config.maps.single()}
                    assertEquals(source.width().toFloat()/source.height(),guide.keyWidth/guide.keyHeight,.01f)
                    config.maps.single().regions.forEach { region ->
                        assertEquals("Guide contours and physical hits must share the same aspect ratio",region.contains(point.first,point.second,source.width().toFloat(),source.height().toFloat()),
                            region.contains(point.first,point.second,guide.keyWidth,guide.keyHeight))
                    }
                }
                if(expected=="○") screenshot("04-ime-above-key-ranges")
                event(down,MotionEvent.ACTION_UP,source,point.first,point.second)
                SystemClock.sleep(500)
                var actual="";host.onActivity {actual=it.editText.text.toString()}
                assertEquals("Input at $point",expected,actual)
            }
            val punctuation=key(main {keyboard()!!},SumirePopupDefaults.PUNCTUATION_KEY)
            val down=SystemClock.uptimeMillis()
            event(down,MotionEvent.ACTION_DOWN,punctuation);SystemClock.sleep(600)
            event(down,MotionEvent.ACTION_MOVE,punctuation,1f,0f)
            screenshot("05-ime-punctuation")
            event(down,MotionEvent.ACTION_UP,punctuation,1f,0f)
            SystemClock.sleep(500)
            var actual="";host.onActivity {actual=it.editText.text.toString()};assertEquals("△！",actual)
        } finally {
            host?.close();shell("ime set $originalIme")
        }
    }
    private data class KeyImage(val bounds: Rect,val label: String?,val textSize: Float?,val textColor: Int?,val selected: Boolean,val image: Bitmap)
    private fun keyImages(keyboard: FlickKeyboardView)=main {
        (0 until keyboard.childCount).map { index ->
            val view=keyboard.getChildAt(index)
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
            KeyImage(Rect(view.left,view.top,view.right,view.bottom),(view as? TextView)?.text?.toString(),
                (view as? TextView)?.textSize,(view as? TextView)?.currentTextColor,view.isSelected,bitmap)
        }
    }
    @Test fun mapEditorPreviewMatchesActualImeKeyDimensionsAndAppearance() {
        start(R.id.popupMapEditorFragment)
        await {views().filterIsInstance<FreeFlickKeyPicker>().any {it.keyboard.width>0 && it.keyboard.height>0}}
        val picker=main {views().filterIsInstance<FreeFlickKeyPicker>().single()}
        main {picker.keyboard.requestRectangleOnScreen(Rect(0,0,picker.keyboard.width,picker.keyboard.height),true)}
        val editorWidth=main {picker.keyboard.width};val editorHeight=main {picker.keyboard.height}
        assertEquals(context.resources.displayMetrics.widthPixels,editorWidth)
        val editor=keyImages(picker.keyboard)
        screenshot("07-map-editor-keyboard")
        main {activity()!!.finish()}
        val originalIme=shell("settings get secure default_input_method").trim()
        val target="${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        var host: FastInputHostSession?=null
        try {
            shell("ime enable $target");shell("ime set $target")
            host=FastInputHostSession.launch(context);host.onActivity {it.requestImeForEditor()}
            fun keyboard()=windows().filterIsInstance<FlickKeyboardView>().firstOrNull {it.isShown && it.width>0 && it.height>0}
            await {keyboard()!=null}
            SystemClock.sleep(600)
            val ime=main {keyboard()!!}
            assertEquals("Editor and IME keyboard widths",main {ime.width},editorWidth)
            assertEquals("Editor and IME keyboard heights",main {ime.height},editorHeight)
            val actual=keyImages(ime);assertEquals(actual.size,editor.size)
            for(index in editor.indices) {
                val source=editor[index];val input=actual[index]
                assertEquals("Key $index ${source.label} dimensions",input.bounds,source.bounds)
                assertEquals("Key $index text size",input.textSize,source.textSize)
                assertEquals("Key $index text color",input.textColor,source.textColor)
                if(!source.selected) assertTrue("Key $index ${source.label}: the idle key image must match the actual IME",source.image.sameAs(input.image))
                source.image.recycle();input.image.recycle()
            }
            screenshot("06-ime-keyboard-idle")
        } finally {host?.close();shell("ime set $originalIme")}
    }

}
