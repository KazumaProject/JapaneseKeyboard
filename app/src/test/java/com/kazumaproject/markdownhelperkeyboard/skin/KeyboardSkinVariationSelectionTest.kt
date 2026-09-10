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

    @Test fun labelAnimationCompletesAndNewSkinCancelsPreviousFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = android.widget.TextView(context)
        val original = android.content.res.ColorStateList.valueOf(android.graphics.Color.MAGENTA)
        label.setTextColor(original)
        val presentation = com.kazumaproject.core.ui.skin.SkinLongPressPresentation()
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        presentation.show(label, KeyboardSkinId.CUPERTINO_LIGHT)
        looper.idleFor(java.time.Duration.ofMillis(100))
        org.junit.Assert.assertNotEquals(original.defaultColor, label.currentTextColor)
        presentation.clear(animated = true)
        looper.idleFor(java.time.Duration.ofMillis(40))
        presentation.show(label, KeyboardSkinId.CUPERTINO_DARK)
        looper.idleFor(java.time.Duration.ofMillis(400))
        assertEquals(0xff545454.toInt(), label.currentTextColor)
        presentation.clear(animated = true)
        looper.idleFor(java.time.Duration.ofSeconds(1))
        org.junit.Assert.assertSame(original, label.textColors)
    }

    @Test @Config(sdk = [32]) fun disabledSystemAnimationsApplyAndRestoreImmediately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val label = android.widget.TextView(context)
        val original = label.textColors
        val presentation = com.kazumaproject.core.ui.skin.SkinLongPressPresentation()
        presentation.show(label, KeyboardSkinId.CUPERTINO_DARK)
        assertEquals(0xff545454.toInt(), label.currentTextColor)
        presentation.clear(animated = true)
        org.junit.Assert.assertSame(original, label.textColors)
    }

    @Test fun cancellingBeforeGuideDrawPreventsDeferredLabelAnimation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = android.widget.TextView(context)
        val original = label.textColors
        val guide = View(context)
        val presentation = com.kazumaproject.core.ui.skin.SkinLongPressPresentation()
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        presentation.show(label, KeyboardSkinId.CUPERTINO_DARK, guide)
        looper.idleFor(java.time.Duration.ofMillis(100))
        org.junit.Assert.assertSame(original, label.textColors)
        presentation.clear()
        guide.viewTreeObserver.dispatchOnPreDraw()
        looper.idleFor(java.time.Duration.ofSeconds(1))
        org.junit.Assert.assertSame(original, label.textColors)
    }

    @Test fun guideOverlayCanBeRemovedAndReusedWithoutRetainingAWindow() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java)
        val activity = controller.get()
        activity.setTheme(com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        controller.setup()
        val anchor = View(activity)
        activity.setContentView(anchor)
        activity.window.decorView.layout(0, 0, 1080, 2400)
        anchor.layout(300, 900, 558, 1068)
        val popup = com.kazumaproject.core.ui.skin.SkinGuidePopup(activity)
        val skin = requireNotNull(com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_LIGHT))
        val labels = mapOf(com.kazumaproject.core.ui.skin.PopupDirection.CENTER to "な")
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        popup.show(anchor, skin, labels)
        val overflow = popup.javaClass.getDeclaredField("overflowWindow").apply { isAccessible = true }
        org.junit.Assert.assertNull(overflow.get(popup))
        popup.dismiss()
        org.junit.Assert.assertFalse(popup.isShowing)
        looper.idleFor(java.time.Duration.ofMillis(100))
        popup.show(anchor, skin, labels)
        looper.idleFor(java.time.Duration.ofMillis(150))
        org.junit.Assert.assertTrue(popup.isShowing)
        popup.dismiss()
        org.junit.Assert.assertFalse(popup.isShowing)
        // A screen-edge guide now fits inside the safe viewport. Force an actually
        // smaller host window to exercise the floating-window fallback.
        activity.window.decorView.layout(0, 0, 100, 100)
        anchor.layout(0, 0, 258, 168)
        popup.show(anchor, skin, mapOf(
            com.kazumaproject.core.ui.skin.PopupDirection.CENTER to "な",
            com.kazumaproject.core.ui.skin.PopupDirection.TOP to "ぬ",
        ))
        val outside = overflow.get(popup) as android.widget.PopupWindow
        org.junit.Assert.assertTrue(outside.isShowing)
        org.junit.Assert.assertFalse(outside.isTouchable)
        popup.dismiss()
        org.junit.Assert.assertFalse(outside.isShowing)
        controller.pause().stop().destroy()
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
        org.junit.Assert.assertSame(bubble,popup.contentView)
        org.junit.Assert.assertTrue(popup.isTouchable)
        org.junit.Assert.assertTrue(popup.isClippingEnabled)
        assertEquals(size,label.textSize);assertEquals(2f,label.translationY)
        assertEquals(gravity,label.gravity);assertEquals(true,label.includeFontPadding)
        controller.pause().stop().destroy()
    }

}
