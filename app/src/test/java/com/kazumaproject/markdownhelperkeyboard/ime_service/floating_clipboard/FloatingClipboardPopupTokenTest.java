package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.recyclerview.widget.RecyclerView;

import com.kazumaproject.core.data.clipboard.ClipboardItem;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;

import kotlin.Unit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 35}, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class FloatingClipboardPopupTokenTest {
    private static RecyclerView recycler(View root) {
        if (root instanceof RecyclerView) return (RecyclerView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                RecyclerView result = recycler(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static class ExpandedFrameAnchor extends View {
        ExpandedFrameAnchor(Context context) {
            super(context);
        }

        @Override
        public void getWindowVisibleDisplayFrame(Rect frame) {
            frame.set(-10000, -10000, 10000, 10000);
        }

        @Override
        public void getLocationOnScreen(int[] location) {
            location[0] = 769;
            location[1] = 830;
        }
    }

    @Test
    @Config(sdk = 24)
    public void expandedNoLimitsFrameCannotPlaceMenuOutsidePhysicalDisplay() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context context = new ContextThemeWrapper(
                activity, com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard);
        View parent = activity.getWindow().getDecorView();
        WindowManager manager = context.getSystemService(WindowManager.class);
        Shadows.shadowOf(manager.getDefaultDisplay()).setRealWidth(2400);
        Shadows.shadowOf(manager.getDefaultDisplay()).setRealHeight(1080);
        Point size = new Point();
        manager.getDefaultDisplay().getRealSize(size);
        assertEquals(new Point(2400, 1080), size);
        FloatingClipboardView panel = new FloatingClipboardView(
                context, () -> Unit.INSTANCE, event -> Unit.INSTANCE,
                () -> Unit.INSTANCE, enabled -> Unit.INSTANCE, (item, action) -> Unit.INSTANCE);
        panel.setApplicationWindowView(parent);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1100, 900, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.token = parent.getWindowToken();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        manager.addView(panel, params);
        try {
            View anchor = new ExpandedFrameAnchor(context);
            panel.getContentContainer().addView(anchor, new FrameLayout.LayoutParams(736, 168));
            anchor.layout(0, 0, 736, 168);
            assertNotSame(parent.getWindowToken(), anchor.getWindowToken());
            Method showMenu = FloatingClipboardView.class.getDeclaredMethod(
                    "showMenu", View.class, ClipboardItem.class);
            showMenu.setAccessible(true);
            showMenu.invoke(panel, anchor, new ClipboardItem.Text(7L, "item", false));
            PopupWindow menu = Shadows.shadowOf(activity.getApplication()).getLatestPopupWindow();
            assertNotNull(menu);
            WindowManager.LayoutParams requested = (WindowManager.LayoutParams)
                    menu.getContentView().getRootView().getLayoutParams();
            int[] parentScreen = new int[2];
            int[] parentWindow = new int[2];
            parent.getLocationOnScreen(parentScreen);
            parent.getLocationInWindow(parentWindow);
            int left = requested.x + parentScreen[0] - parentWindow[0];
            int top = requested.y + parentScreen[1] - parentWindow[1];
            Rect requestedBounds = new Rect(left, top,
                    left + requested.width, top + requested.height);
            Rect physicalDisplay = new Rect(0, 0, size.x, size.y);
            assertTrue("An expanded window frame must not override the physical display: "
                    + requestedBounds, physicalDisplay.contains(requestedBounds));
            assertEquals("A row near the bottom needs its menu above the row",
                    830, requestedBounds.bottom);
        } finally {
            panel.dismissMenu();
            manager.removeViewImmediate(panel);
            activity.finish();
        }
    }

    @Test
    public void menuUsesParentWindowTokenForNestedClipboardPanel() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context context = new ContextThemeWrapper(
                activity, com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard);
        View parent = activity.getWindow().getDecorView();
        WindowManager manager = context.getSystemService(WindowManager.class);
        FloatingClipboardView panel = new FloatingClipboardView(
                context, () -> Unit.INSTANCE, event -> Unit.INSTANCE,
                () -> Unit.INSTANCE, enabled -> Unit.INSTANCE, (item, action) -> Unit.INSTANCE);
        panel.setApplicationWindowView(parent);
        panel.updateItems(Collections.singletonList(new ClipboardItem.Text(7L, "item", false)));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                500, 500, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.token = parent.getWindowToken();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 30;
        params.y = 40;
        manager.addView(panel, params);
        try {
            panel.measure(
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
            panel.layout(0, 0, 500, 500);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            RecyclerView recycler = recycler(panel);
            assertNotNull(recycler);
            @SuppressWarnings("unchecked")
            RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                    (RecyclerView.Adapter<RecyclerView.ViewHolder>) recycler.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(
                    recycler, adapter.getItemViewType(1));
            adapter.bindViewHolder(holder, 1);
            View row = holder.itemView;
            panel.getContentContainer().addView(row, new FrameLayout.LayoutParams(220, 80));
            row.layout(0, 120, 220, 200);
            assertNotNull(row.getWindowToken());
            assertNotSame(parent.getWindowToken(), row.getWindowToken());
            assertSame(parent.getWindowToken(), row.getApplicationWindowToken());
            assertTrue(row.performLongClick());
            PopupWindow menu = Shadows.shadowOf(activity.getApplication()).getLatestPopupWindow();
            assertNotNull(menu);
            WindowManager.LayoutParams popupParams = (WindowManager.LayoutParams)
                    menu.getContentView().getRootView().getLayoutParams();
            assertSame("Nested clipboard menus must attach to the parent window",
                    parent.getWindowToken(), popupParams.token);
            assertFalse(menu.isFocusable());
            assertTrue(menu.isOutsideTouchable());
            if (Build.VERSION.SDK_INT < 26) {
                assertFalse("The IME content frame must not clip a display-positioned menu",
                        menu.isClippingEnabled());
                assertTrue((popupParams.flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0);
                Rect display = new Rect();
                row.getWindowVisibleDisplayFrame(display);
                int[] parentScreen = new int[2];
                int[] parentWindow = new int[2];
                parent.getLocationOnScreen(parentScreen);
                parent.getLocationInWindow(parentWindow);
                int left = popupParams.x + parentScreen[0] - parentWindow[0];
                int top = popupParams.y + parentScreen[1] - parentWindow[1];
                Rect requestedBounds = new Rect(left, top,
                        left + popupParams.width, top + popupParams.height);
                assertTrue("The menu still requests bounds within the visible display: "
                        + requestedBounds + " display=" + display, display.contains(requestedBounds));
            }
        } finally {
            panel.dismissMenu();
            manager.removeViewImmediate(panel);
            activity.finish();
        }
    }
}
