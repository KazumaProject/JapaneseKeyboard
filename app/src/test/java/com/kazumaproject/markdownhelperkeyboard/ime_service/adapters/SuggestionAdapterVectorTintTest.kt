package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestionAdapterVectorTintTest {
    private fun pixels(view: ImageView): Bitmap {
        view.layout(0,0,96,96)
        return Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    @Test fun defaultBindingKeepsTheVectorsOwnTintBeforeAndAfterSkin() {
        val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.EmptyState(
            showShortcutEntry=false,
            quickActions=QuickActionsState(false,false,false,false,"",""),
            clipboardPreview=null,
            shortcutItems=listOf(ShortcutType.CLIP_BOARD),
            showIntegratedShortcuts=true,
        ))
        repeat(100) { if(adapter.itemCount==0) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) } }
        val index = (0 until adapter.itemCount).first { adapter.getItemViewType(it)==SuggestionAdapter.VIEW_TYPE_SHORTCUT }
        val holder=adapter.onCreateViewHolder(FrameLayout(ctx),SuggestionAdapter.VIEW_TYPE_SHORTCUT) as SuggestionAdapter.ShortcutViewHolder
        val expected=ImageView(ctx).apply { scaleType=ImageView.ScaleType.CENTER_INSIDE; setImageResource(ShortcutType.CLIP_BOARD.iconResId) }
        adapter.onBindViewHolder(holder,index)
        assertTrue("Default cleared vector XML tint",pixels(expected).sameAs(pixels(holder.imageView)))
        adapter.setShortcutIconColor(android.graphics.Color.WHITE)
        adapter.onBindViewHolder(holder,index)
        adapter.setShortcutIconColor(null)
        adapter.onBindViewHolder(holder,index)
        assertTrue("Skin tint was retained",pixels(expected).sameAs(pixels(holder.imageView)))
        adapter.release()
    }
}
