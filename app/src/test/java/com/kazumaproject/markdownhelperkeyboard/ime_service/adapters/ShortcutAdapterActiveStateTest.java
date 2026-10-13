package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable;
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
public class ShortcutAdapterActiveStateTest {
    private static int resourceId(Drawable drawable) {
        return drawable instanceof KeyboardFontGlyphDrawable
                ? ((KeyboardFontGlyphDrawable) drawable).getResourceId()
                : Shadows.shadowOf(drawable).getCreatedFromResId();
    }

    private static class Fixture {
        final RecyclerView recyclerView;
        final ShortcutAdapter adapter = new ShortcutAdapter();
        final ShortcutType type;
        final List<ShortcutType> clicked = new ArrayList<>();
        final List<Integer> touchActions = new ArrayList<>();
        final ShortcutAdapter.ViewHolder holder;
        final float x;
        final float y;
        long downTime;

        Fixture(ShortcutType type) {
            this.type = type;
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            Context context = new ContextThemeWrapper(
                    activity, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
            recyclerView = new RecyclerView(context);
            recyclerView.setLayoutManager(new LinearLayoutManager(
                    context, RecyclerView.HORIZONTAL, false));
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            adapter.setOnItemClicked(clickedType -> {
                clicked.add(clickedType);
                return Unit.INSTANCE;
            });
            adapter.setShortcutToolbarSize(64, 32);
            recyclerView.setAdapter(adapter);
            activity.setContentView(recyclerView);
            adapter.submitList(Collections.singletonList(type));
            layout();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            layout();
            holder = (ShortcutAdapter.ViewHolder) recyclerView.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            holder.itemView.setOnTouchListener((view, event) -> {
                touchActions.add(event.getActionMasked());
                return false;
            });
            x = holder.itemView.getLeft() + holder.itemView.getWidth() / 2f;
            y = holder.itemView.getTop() + holder.itemView.getHeight() / 2f;
            assertTrue(holder.itemView.isAttachedToWindow());
        }

        void layout() {
            recyclerView.measure(
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
            recyclerView.layout(0, 0, 500, 96);
        }

        void touch(int action) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), action, x, y, 0);
            try {
                assertTrue(recyclerView.dispatchTouchEvent(event));
            } finally {
                event.recycle();
            }
        }

        void activate() {
            adapter.setActiveShortcutTypes(Collections.singleton(type));
        }

        void finishTap() {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
            touch(MotionEvent.ACTION_UP);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertSame("An active icon update must preserve the touch target for " + type,
                    holder, recyclerView.findViewHolderForAdapterPosition(0));
            assertEquals(Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), touchActions);
            assertEquals(Collections.singletonList(type), clicked);
        }
    }

    @Test
    public void activeUpdateDuringHeldTouchKeepsClick() {
        for (ShortcutType type : Arrays.asList(ShortcutType.KEYBOARD_LAYOUT_EDIT, ShortcutType.CLIP_BOARD)) {
            Fixture fixture = new Fixture(type);
            fixture.touch(MotionEvent.ACTION_DOWN);
            fixture.activate();
            fixture.layout();
            fixture.finishTap();
        }
    }

    @Test
    public void pendingActiveUpdateBeforeDownKeepsClick() {
        for (ShortcutType type : Arrays.asList(ShortcutType.KEYBOARD_LAYOUT_EDIT, ShortcutType.CLIP_BOARD)) {
            Fixture fixture = new Fixture(type);
            fixture.activate();
            fixture.touch(MotionEvent.ACTION_DOWN);
            fixture.layout();
            fixture.finishTap();
        }
    }

    @Test
    public void activePayloadsPreserveGeometryWhileMixedAndEmptyPayloadsUseFullBind() {
        Fixture fixture = new Fixture(ShortcutType.KEYBOARD_LAYOUT_EDIT);
        List<Object> payloads = new ArrayList<>();
        fixture.adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                if (payload != null) payloads.add(payload);
            }
        });
        ShortcutAdapter.ViewHolder holder = fixture.holder;
        fixture.adapter.setIconColor(Color.MAGENTA);
        fixture.adapter.onBindViewHolder(holder, 0);
        fixture.activate();
        assertEquals(1, payloads.size());
        Object activePayload = payloads.get(0);
        Object previousDrawable = holder.getImageView().getDrawable();
        fixture.adapter.onBindViewHolder(holder, 0, Arrays.asList(activePayload, activePayload));
        assertNotSame(previousDrawable, holder.getImageView().getDrawable());
        assertEquals(ShortcutType.KEYBOARD_LAYOUT_EDIT.getActiveIconResId().intValue(),
                resourceId(holder.getImageView().getDrawable()));
        assertNotNull("Changing the active icon must retain its configured tint",
                holder.getImageView().getColorFilter());
        assertEquals(ShortcutType.KEYBOARD_LAYOUT_EDIT.getDescription(),
                holder.itemView.getContentDescription());
        assertEquals(64, holder.itemView.getLayoutParams().height);

        fixture.adapter.setShortcutToolbarSize(88, 40);
        fixture.adapter.onBindViewHolder(holder, 0, Collections.singletonList(activePayload));
        assertEquals("An active-only bind must not apply unrelated pending geometry",
                64, holder.itemView.getLayoutParams().height);
        fixture.adapter.onBindViewHolder(holder, 0, Arrays.asList(activePayload, new Object()));
        assertEquals(88, holder.itemView.getLayoutParams().height);
        assertEquals(40, holder.getImageView().getLayoutParams().width);

        fixture.adapter.setShortcutToolbarSize(100, 44);
        fixture.adapter.setIconColor(null);
        fixture.adapter.onBindViewHolder(holder, 0, Collections.emptyList());
        assertEquals(100, holder.itemView.getLayoutParams().height);
        assertEquals(44, holder.getImageView().getLayoutParams().height);
        assertNull(holder.getImageView().getColorFilter());
        fixture.adapter.setActiveShortcutTypes(Collections.emptySet());
        fixture.adapter.onBindViewHolder(holder, 0, Collections.singletonList(activePayload));
        assertEquals(ShortcutType.KEYBOARD_LAYOUT_EDIT.getIconResId(),
                resourceId(holder.getImageView().getDrawable()));
        assertNull(holder.getImageView().getColorFilter());

        fixture.activate();
        ShortcutAdapter.ViewHolder rebound = fixture.adapter.onCreateViewHolder(fixture.recyclerView, 0);
        fixture.adapter.onBindViewHolder(rebound, 0);
        assertEquals("A newly created holder must receive the active state without a payload",
                ShortcutType.KEYBOARD_LAYOUT_EDIT.getActiveIconResId().intValue(),
                resourceId(rebound.getImageView().getDrawable()));
    }

    @Test
    public void activatingAnotherShortcutDoesNotRebindTheExistingActiveShortcut() {
        ShortcutAdapter adapter = new ShortcutAdapter();
        adapter.submitList(Arrays.asList(ShortcutType.KEYBOARD_LAYOUT_EDIT, ShortcutType.KEYBOARD_FLOATING_TOGGLE));
        adapter.setActiveShortcutTypes(Collections.singleton(ShortcutType.KEYBOARD_LAYOUT_EDIT));
        List<Integer> updated = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeChanged(int start, int count, Object payload) {
                assertNotNull(payload);
                for (int i = start; i < start + count; i++) updated.add(i);
            }
        });
        adapter.setActiveShortcutTypes(new HashSet<>(Arrays.asList(
                ShortcutType.KEYBOARD_LAYOUT_EDIT, ShortcutType.KEYBOARD_FLOATING_TOGGLE)));
        assertEquals(Collections.singletonList(1), updated);
    }
}
