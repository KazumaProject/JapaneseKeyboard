package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import org.junit.Assert.assertEquals
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

    @Test fun incognitoBindingUsesCandidateThemeColorAndRestoresVectorTint() {
        val ctx = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard,
        )
        val adapter = SuggestionAdapter()
        adapter.setIncognitoIcon(
            ContextCompat.getDrawable(ctx, com.kazumaproject.core.R.drawable.incognito),
        )
        adapter.submitContent(
            CandidateStripContent.EmptyState(
                showShortcutEntry = false,
                quickActions = QuickActionsState(
                    incognitoVisible = true,
                    undoEnabled = false,
                    redoEnabled = false,
                    reconvertEnabled = false,
                    undoText = "",
                    redoText = "",
                ),
                clipboardPreview = null,
                shortcutItems = emptyList(),
                showIntegratedShortcuts = false,
            ),
        )
        repeat(100) {
            if (adapter.itemCount == 0) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
        assertEquals(1, adapter.itemCount)

        val holder = adapter.onCreateViewHolder(
            FrameLayout(ctx),
            SuggestionAdapter.VIEW_TYPE_EMPTY,
        ) as SuggestionAdapter.QuickActionsViewHolder
        val expected = LayoutInflater.from(ctx)
            .inflate(
                com.kazumaproject.markdownhelperkeyboard.R.layout.suggestion_quick_actions_item,
                FrameLayout(ctx),
                false,
            )
            .findViewById<ImageView>(com.kazumaproject.markdownhelperkeyboard.R.id.incognito_icon)
            .apply {
                setImageDrawable(
                    ContextCompat.getDrawable(ctx, com.kazumaproject.core.R.drawable.incognito),
                )
                setColorFilter(Color.MAGENTA, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        adapter.setCandidateEmptyPopupColors(Color.BLACK, Color.MAGENTA)
        adapter.onBindViewHolder(holder, 0)
        assertTrue(
            "Incognito icon should use the candidate empty action text color",
            pixels(expected).sameAs(pixels(holder.incognitoIcon!!)),
        )

        adapter.clearCandidateEmptyPopupColors()
        adapter.onBindViewHolder(holder, 0)
        val vectorDefault = LayoutInflater.from(ctx)
            .inflate(
                com.kazumaproject.markdownhelperkeyboard.R.layout.suggestion_quick_actions_item,
                FrameLayout(ctx),
                false,
            )
            .findViewById<ImageView>(com.kazumaproject.markdownhelperkeyboard.R.id.incognito_icon)
            .apply {
                setImageDrawable(
                    ContextCompat.getDrawable(ctx, com.kazumaproject.core.R.drawable.incognito),
                )
            }
        assertTrue(
            "Clearing the candidate theme should restore the vector's own tint",
            pixels(vectorDefault).sameAs(pixels(holder.incognitoIcon!!)),
        )
        adapter.release()
    }
}
