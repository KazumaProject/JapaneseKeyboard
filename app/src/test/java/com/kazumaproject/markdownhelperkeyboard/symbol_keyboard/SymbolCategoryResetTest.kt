package com.kazumaproject.markdownhelperkeyboard.symbol_keyboard

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.data.symbol.SymbolCategory
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class SymbolCategoryResetTest {
    @Test
    fun reopeningEachModeResetsCategorySelectionAndScroll() {
        withKeyboard { keyboard, categories, _ ->
            for (withHistory in listOf(false, true)) {
                for (mode in symbolModes) {
                    populate(keyboard, mode, withHistory)
                    layout(keyboard)
                    scrollToLastCategory(categories)

                    keyboard.visibility = View.GONE
                    populate(keyboard, mode, withHistory)
                    keyboard.visibility = View.VISIBLE
                    layout(keyboard)

                    assertEquals("$mode history=$withHistory selection", 0, categories.selectedTabPosition)
                    assertEquals("$mode history=$withHistory scroll", 0, categories.scrollX)
                }
            }
        }
    }

    @Test
    fun switchingModesResetsCategorySelectionAndScroll() {
        assertModeSwitchResetsCategory()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun switchingModesInLandscapeResetsCategorySelectionAndScroll() {
        assertModeSwitchResetsCategory()
    }

    @Test
    fun categoryChosenBeforeLayoutRemainsSelectedAndVisible() {
        withKeyboard { keyboard, categories, modes ->
            populate(keyboard, SymbolMode.SYMBOL, withHistory = true)
            layout(keyboard)
            modes.getTabAt(SymbolMode.EMOJI.ordinal)!!.select()
            val last = categories.tabCount - 1
            categories.getTabAt(last)!!.select()
            layout(keyboard)

            assertEquals(last, categories.selectedTabPosition)
            assertTrue(categories.scrollX > 0)
        }
    }

    private fun assertModeSwitchResetsCategory() {
        withKeyboard { keyboard, categories, modes ->
            populate(keyboard, SymbolMode.EMOJI, withHistory = true)
            layout(keyboard)
            for (mode in listOf(SymbolMode.EMOTICON, SymbolMode.SYMBOL, SymbolMode.EMOJI)) {
                scrollToLastCategory(categories)
                modes.getTabAt(mode.ordinal)!!.select()
                layout(keyboard)

                assertEquals("$mode selection", 0, categories.selectedTabPosition)
                assertEquals("$mode scroll", 0, categories.scrollX)
            }
        }
    }

    private fun withKeyboard(block: (CustomSymbolKeyboardView, TabLayout, TabLayout) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val keyboard = CustomSymbolKeyboardView(
                ContextThemeWrapper(activity, R.style.Theme_MarkdownKeyboard)
            )
            activity.setContentView(keyboard)
            block(
                keyboard,
                keyboard.findViewById(com.kazumaproject.symbol_keyboard.R.id.category_tab_layout),
                keyboard.findViewById(com.kazumaproject.symbol_keyboard.R.id.mode_tab_layout),
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun populate(keyboard: CustomSymbolKeyboardView, mode: SymbolMode, withHistory: Boolean) {
        keyboard.setSymbolLists(
            emojiList = EmojiCategory.entries.map { Emoji("😀", it) },
            emoticons = EmoticonCategory.entries.map { Emoticon("(^_^)", it) },
            symbols = SymbolCategory.entries.map { Symbol("☆", it) },
            clipBoardItems = emptyList(),
            symbolsHistory = if (withHistory) {
                symbolModes.map { ClickedSymbol(mode = it, symbol = "history") }
            } else emptyList(),
            symbolMode = mode,
        )
    }

    private fun scrollToLastCategory(categories: TabLayout) {
        val last = categories.tabCount - 1
        categories.getTabAt(last)!!.select()
        categories.setScrollPosition(last, 0f, true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        if (categories.getChildAt(0).width > categories.width) {
            assertTrue("Fixture must scroll beyond the first category", categories.scrollX > 0)
        }
    }

    private fun layout(keyboard: CustomSymbolKeyboardView) {
        val density = keyboard.resources.displayMetrics.density
        keyboard.measure(
            View.MeasureSpec.makeMeasureSpec(
                (keyboard.resources.configuration.screenWidthDp * density).toInt(),
                View.MeasureSpec.EXACTLY,
            ),
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        keyboard.layout(0, 0, keyboard.measuredWidth, keyboard.measuredHeight)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }

    private val symbolModes = listOf(SymbolMode.EMOJI, SymbolMode.EMOTICON, SymbolMode.SYMBOL)
}
