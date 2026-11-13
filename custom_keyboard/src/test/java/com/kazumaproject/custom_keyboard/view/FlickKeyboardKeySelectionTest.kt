package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35])
class FlickKeyboardKeySelectionTest {
    @Test fun kanaAndPunctuationAreSelectableWithoutInputOrSpecialKeySideEffects() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val view=FlickKeyboardView(ContextThemeWrapper(activity,com.google.android.material.R.style.Theme_Material3_DayNight))
        val selected=mutableListOf<String>()
        val actions=mutableListOf<KeyAction>()
        view.setKeySelectionMode(SumirePopupDefaults.rows.keys) { selected+=it;view.selectPreviewKey(it) }
        view.setOnKeyboardActionListener(object: FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) { actions+=action }
            override fun onAction(action: KeyAction,isFlick: Boolean) {actions+=action}
            override fun onActionLongPress(action: KeyAction) {actions+=action}
            override fun onActionUpAfterLongPress(action: KeyAction) {actions+=action}
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) {actions+=action}
            override fun onFlickActionUpAfterLongPress(action: KeyAction,isFlick: Boolean) {actions+=action}
        })
        val layout=KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA,emptyMap(),"toggle",SumirePopupDefaults.STYLE)
        view.setKeyboard(layout,origin=KeyboardLayoutOrigin.BUILT_IN)
        actions.clear() // Layout initialization has its existing cancellation notification.
        activity.setContentView(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY))
        view.layout(0,0,600,400)
        val kana=layout.items.mapIndexedNotNull { index,item -> (item as? KeyItem)?.keyData?.takeIf {it.label in SumirePopupDefaults.rows}?.let {view.getChildAt(index) to it.label} }
        assertEquals(11,kana.size)
        assertTrue(kana.any { it.second == SumirePopupDefaults.PUNCTUATION_KEY })
        for((child,label) in kana) {
            val x=(child.left+child.right)/2f;val y=(child.top+child.bottom)/2f
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val origin=IntArray(2).also(view::getLocationOnScreen)
                val e=MotionEvent.obtain(0,10,action,x+origin[0],y+origin[1],0)
                e.offsetLocation(-origin[0].toFloat(),-origin[1].toFloat())
                view.dispatchTouchEvent(e);e.recycle()
            }
            assertEquals(label,selected.last())
            assertTrue(child.isSelected);assertTrue(child.isPressed)
            assertEquals(1,(0 until view.childCount).count {view.getChildAt(it).isSelected})
        }
        val special=layout.items.mapIndexedNotNull { index,item -> (item as? KeyItem)?.keyData?.takeIf {it.isSpecialKey}?.let {view.getChildAt(index)} }
        assertTrue(special.isNotEmpty())
        special.forEach { assertFalse(it.isEnabled);it.performClick() }
        assertEquals(11,selected.size)
        assertTrue("Selection must never emit input or function actions",actions.isEmpty())
    }
}
