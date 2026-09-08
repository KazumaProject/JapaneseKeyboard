package com.kazumaproject.markdownhelperkeyboard.skin

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.qwerty_keyboard.ui.VariationsPopupView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class KeyboardSkinVariationSelectionTest {
    @Test fun cancellingLabelFadeRestoresTheOriginalStateListAndStopsLaterWrites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = android.widget.TextView(context)
        val original = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(android.graphics.Color.BLUE, android.graphics.Color.MAGENTA))
        label.setTextColor(original)
        val presentation = com.kazumaproject.core.ui.skin.SkinLongPressPresentation()
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        for (skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
            presentation.show(label, skin)
            looper.idleFor(java.time.Duration.ofMillis(100))
            presentation.clear(animated = true)
            looper.idleFor(java.time.Duration.ofMillis(40))
            presentation.clear()
            org.junit.Assert.assertSame(original, label.textColors)
            looper.idleFor(java.time.Duration.ofSeconds(1))
            org.junit.Assert.assertSame(original, label.textColors)
        }
    }

    @Test fun skinRoundTripPreservesThreeColumnSelectionAndCharacterOrder() {
        val context=ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val view=VariationsPopupView(context)
        val chars=listOf('e','è','é','ê','ë','ē','ė','ę','ě','ə')
        val points=listOf(50f to 75f,150f to 75f,250f to 75f,50f to 225f,150f to 225f,
            250f to 225f,50f to 375f,150f to 375f,250f to 375f,50f to 525f)
        for(id in listOf(KeyboardSkinId.DEFAULT,KeyboardSkinId.CUPERTINO_LIGHT,KeyboardSkinId.CUPERTINO_DARK,KeyboardSkinId.DEFAULT)) {
            view.applyPopupViewStyle(PopupViewStyle(100,28f,skinId=id))
            view.setChars(chars)
            view.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY))
            view.layout(0,0,300,600)
            assertEquals(3,view.maxColumns)
            assertEquals(chars,points.map { (x,y) -> view.updateSelection(x,y);view.getSelectedChar() })
            // Empty last-row cells and out-of-window movement retain the last valid selection.
            view.updateSelection(250f,525f);assertEquals('ə',view.getSelectedChar())
            view.updateSelection(-1f,75f);assertEquals('ə',view.getSelectedChar())
        }
    }
    @Test fun leavingSkinRestoresLegacyPopupPresentationBeforeApplyingSavedSettings() {
        val controller=org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java)
        val activity=controller.get()
        activity.setTheme(com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        controller.setup()
        val anchor=View(activity)
        activity.setContentView(anchor)
        anchor.layout(0,0,258,168)
        val bubble=com.kazumaproject.core.ui.key_window.KeyWindowLayout(activity)
        val label=android.widget.TextView(activity).apply {
            text="か";textSize=33f;gravity=android.view.Gravity.TOP;includeFontPadding=true;translationY=2f
        }
        bubble.addView(label,android.widget.FrameLayout.LayoutParams(-1,-1))
        bubble.setPadding(11,12,13,14);bubble.elevation=9f
        val size=label.textSize
        val gravity=label.gravity
        val popup=android.widget.PopupWindow(bubble,258,168,false).apply {elevation=7f;animationStyle=42}
        bubble.skinId=KeyboardSkinId.CUPERTINO_DARK
        com.kazumaproject.core.ui.skin.SkinPopupPlacement.show(popup,bubble,anchor,
            com.kazumaproject.core.ui.skin.PopupDirection.TOP,true)
        popup.dismiss()
        bubble.skinId=KeyboardSkinId.DEFAULT
        assertEquals(listOf(11,12,13,14),listOf(bubble.paddingLeft,bubble.paddingTop,bubble.paddingRight,bubble.paddingBottom))
        assertEquals(9f,bubble.elevation);assertEquals(7f,popup.elevation);assertEquals(42,popup.animationStyle)
        assertEquals(size,label.textSize);assertEquals(2f,label.translationY)
        assertEquals(gravity,label.gravity);assertEquals(true,label.includeFontPadding)
        controller.pause().stop().destroy()
    }

}
