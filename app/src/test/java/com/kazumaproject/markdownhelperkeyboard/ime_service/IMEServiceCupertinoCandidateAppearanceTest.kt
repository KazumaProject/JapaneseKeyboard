package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.ShortcutAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.InlineSuggestionToggle
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidateSurfaceHost
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IMEServiceCupertinoCandidateAppearanceTest {
    private class Fixture {
        // Exercise the service's appearance paths without starting dictionaries or input jobs.
        val service = Robolectric.buildService(IMEService::class.java).get()
        val context = ContextThemeWrapper(service, R.style.Theme_MarkdownKeyboard)
        val binding = MainLayoutBinding.inflate(LayoutInflater.from(context))
        val adapter = SuggestionAdapter(Executor { it.run() })
        val fullAdapter = SuggestionAdapter(Executor { it.run() })
        val shortcuts = ShortcutAdapter()

        init {
            set("mainLayoutBinding", binding)
            set("suggestionAdapter", adapter)
            set("suggestionAdapterFull", fullAdapter)
            set("shortcutAdapter", shortcuts)
            set("listAdapter", FloatingCandidateListAdapter(10))
            set("candidateTabOrder", CandidateTab.entries.toList())
        }

        fun set(name: String, value: Any?) {
            IMEService::class.java.getDeclaredField(name).apply { isAccessible = true }
                .set(service, value)
        }

        fun call(name: String, bindingArgument: Boolean = false) {
            val types = if (bindingArgument) arrayOf(MainLayoutBinding::class.java) else emptyArray()
            IMEService::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }
                .invoke(service, *if (bindingArgument) arrayOf(binding) else emptyArray())
        }

        fun selectSkin(skin: KeyboardSkinId) {
            AppPreference.init(context)
            val preferences = ImePreferencesSnapshot.from(AppPreference)
                .copy(keyboardSkin = skin).withKeyboardSkinAppearance()
            set("keyboardSkinId", skin)
            for (name in listOf("keyboardThemeMode", "customThemeCandidateTextColor",
                "customThemeCandidateItemBgColor", "customThemeCandidateItemPressedBgColor",
                "customThemeShortcutIconColor", "customThemeSpecialKeyColor",
                "customThemeKeyTextColor", "customThemeCandidateEmptyPopupBgColor",
                "customThemeCandidateEmptyPopupTextColor")) {
                set(name, ImeKeyboardAppearance::class.java.getDeclaredField(name)
                    .apply { isAccessible = true }.get(preferences.appearance))
            }
        }

        fun close() {
            adapter.release()
            fullAdapter.release()
        }
    }

    @Test fun classicTabsKeepSegmentedBackgroundAfterInputViewRebuildsThem() {
        val fixture = Fixture()
        try {
            fixture.set("keyboardSkinId", KeyboardSkinId.CUPERTINO_CLASSIC)
            fixture.call("applyCandidateAppearance") // Cold inflation: no tabs exist yet.
            repeat(3) {
                fixture.call("setTabsToTabLayout", true)
                val layout = fixture.binding.candidateTabLayout
                val strip = layout.getChildAt(0) as ViewGroup
                assertEquals(3, strip.childCount)
                repeat(strip.childCount) { index ->
                    val view = strip.getChildAt(index)
                    assertTrue("Rebuilt tab $index lost Classic background", view.background is StateListDrawable)
                    val background = view.background as StateListDrawable
                    background.state = intArrayOf()
                    val normal = background.current
                    background.state = intArrayOf(android.R.attr.state_selected)
                    assertNotSame("Tab selection must change its background", normal, background.current)
                }
            }
        } finally { fixture.close() }
    }

    @Test fun returningFromColdClassicRestoresTabPaddingAndRipple() {
        val fixture = Fixture()
        try {
            val baseline = com.google.android.material.tabs.TabLayout(fixture.context).apply {
                addTab(newTab().setText("予測"))
            }
            val expected = (baseline.getChildAt(0) as ViewGroup).getChildAt(0)
            val originalRipple = fixture.binding.candidateTabLayout.tabRippleColor
            val originalIndicator = fixture.binding.candidateTabLayout.tabSelectedIndicator
            fixture.set("keyboardSkinId", KeyboardSkinId.CUPERTINO_CLASSIC)
            fixture.call("applyCandidateAppearance")
            fixture.call("setTabsToTabLayout", true)
            fixture.set("keyboardSkinId", KeyboardSkinId.DEFAULT)
            fixture.set("keyboardThemeMode", "default")
            fixture.call("applyCandidateAppearance")
            val layout = fixture.binding.candidateTabLayout
            val actual = (layout.getChildAt(0) as ViewGroup).getChildAt(0)
            assertEquals(expected.paddingLeft, actual.paddingLeft)
            assertEquals(expected.paddingRight, actual.paddingRight)
            assertEquals(expected.background?.javaClass, actual.background?.javaClass)
            assertEquals(originalRipple, layout.tabRippleColor)
            assertSame("The transparent Classic indicator must not survive theme restoration",
                originalIndicator, layout.tabSelectedIndicator)
        } finally { fixture.close() }
    }

    @Test fun floatingCandidateTabsAreRestyledAfterRebuildAndReturnToDockedClassic() {
        val fixture = Fixture()
        try {
            fixture.set("keyboardSkinId", KeyboardSkinId.CUPERTINO_CLASSIC)
            fixture.call("setTabsToTabLayout", true)
            val binding = fixture.binding
            val host = CandidateSurfaceHost(binding.shortcutToolbarRecyclerview,
                binding.candidateTabLayout, binding.suggestionViewParent,
                binding.suggestionRecyclerView, binding.candidatesRowView)
            host.setColors(CandidatePanelColors.resolve(fixture.context, cupertinoClassic = true))
            host.attach(LinearLayout(fixture.context))
            fixture.set("candidateSurfaceHost", host)
            repeat(2) {
                fixture.call("setTabsToTabLayout", true)
                repeat(binding.candidateTabLayout.tabCount) { index ->
                    assertTrue(binding.candidateTabLayout.getTabAt(index)!!.customView!!.background is StateListDrawable)
                }
            }
            host.detach()
            fixture.set("candidateSurfaceHost", null)
            fixture.call("applyCandidateAppearance")
            val strip = binding.candidateTabLayout.getChildAt(0) as ViewGroup
            repeat(strip.childCount) { index ->
                assertNull(binding.candidateTabLayout.getTabAt(index)!!.customView)
                assertTrue(strip.getChildAt(index).background is StateListDrawable)
            }
        } finally { fixture.close() }
    }

    @Test fun returningFromSkinsRestoresDefaultAndCustomInlineColors() {
        val fixture = Fixture()
        try {
            AppPreference.init(fixture.context)
            val holder = fixture.adapter.onCreateViewHolder(FrameLayout(fixture.context),
                SuggestionAdapter.VIEW_TYPE_INLINE_TOGGLE) as SuggestionAdapter.InlineSuggestionToggleViewHolder
            fixture.adapter.submitContent(CandidateStripContent.Candidates(
                listOf(Candidate("候補", 1, 2u, 0)),
                InlineSuggestionToggle("切り替え", iconResId = R.drawable.inline_suggestion_key_24,
                    iconBackgroundResId = com.kazumaproject.core.R.drawable.suggestion_icon_bg),
            ))
            drain(fixture.adapter, 2)
            for (mode in listOf("default", "custom")) {
                PreferenceManager.getDefaultSharedPreferences(fixture.context).edit()
                    .putString("keyboard_theme_mode_preference", mode)
                    .putInt("custom_theme_candidate_text_color", Color.MAGENTA).commit()
                fixture.selectSkin(KeyboardSkinId.DEFAULT)
                fixture.call("applyCandidateAppearance")
                fixture.adapter.onBindViewHolder(holder, 0)
                val original = iconPixels(holder.badgeIcon)
                val originalButtonTint = fixture.binding.suggestionVisibility.imageTintList
                for (skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT,
                    KeyboardSkinId.CUPERTINO_DARK, KeyboardSkinId.CUPERTINO_CLASSIC)) {
                    fixture.selectSkin(skin)
                    fixture.call("applyCandidateAppearance")
                    fixture.adapter.onBindViewHolder(holder, 0)
                }
                fixture.selectSkin(KeyboardSkinId.DEFAULT)
                fixture.call("applyCandidateAppearance")
                fixture.adapter.onBindViewHolder(holder, 0)
                val restored = iconPixels(holder.badgeIcon)
                // Native vector tint removal changes a few antialiased edge channels by
                // one level. Compare geometry and every color channel with that tolerance.
                assertTrue("$mode inline colors did not restore", original.indices.all { index ->
                    listOf(0, 8, 16, 24).all { shift ->
                        kotlin.math.abs(((original[index] ushr shift) and 255) -
                            ((restored[index] ushr shift) and 255)) <= 1
                    }
                })
                assertNull(holder.badgeIcon.backgroundTintList)
                assertNull(fixture.binding.suggestionVisibility.backgroundTintList)
                assertEquals(originalButtonTint, fixture.binding.suggestionVisibility.imageTintList)
            }
        } finally { fixture.close() }
    }

    @Test @Config(qualifiers = "notnight")
    fun cupertinoCandidatesAndBothInlineIconsUseSkinColorsInDayMode() = checkSkinColors()

    @Test @Config(qualifiers = "night")
    fun cupertinoCandidatesAndBothInlineIconsUseSkinColorsInNightMode() = checkSkinColors()

    private fun checkSkinColors() {
        val fixture = Fixture()
        try {
            AppPreference.init(fixture.context)
            PreferenceManager.getDefaultSharedPreferences(fixture.context).edit()
                .putString("keyboard_theme_mode_preference", "custom")
                .putInt("custom_theme_candidate_text_color", Color.MAGENTA)
                .putInt("custom_theme_shortcut_icon_color", Color.GREEN).commit()
            for (skin in listOf(KeyboardSkinId.CUPERTINO_CLASSIC,
                KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
                fixture.selectSkin(skin)
                fixture.call("applyCandidateAppearance")
                fixture.call("setShortCutAdapter", true) // Must not overwrite the skin later.
                val colors = CandidatePanelColors.resolve(fixture.context,
                    KeyboardSkinRegistry.find(skin)!!.palette,
                    cupertinoClassic = skin == KeyboardSkinId.CUPERTINO_CLASSIC)
                for (adapter in listOf(fixture.adapter, fixture.fullAdapter)) {
                    val parent = FrameLayout(fixture.context)
                    val toggleHolder = adapter.onCreateViewHolder(parent,
                        SuggestionAdapter.VIEW_TYPE_INLINE_TOGGLE) as SuggestionAdapter.InlineSuggestionToggleViewHolder
                    for (icon in listOf(R.drawable.more_horiz_24px, R.drawable.inline_suggestion_key_24)) {
                        adapter.submitContent(CandidateStripContent.Candidates(
                            listOf(Candidate("候補", 1, 2u, 0)),
                            InlineSuggestionToggle("切り替え", iconResId = icon,
                                iconBackgroundResId = if (icon == R.drawable.inline_suggestion_key_24)
                                    com.kazumaproject.core.R.drawable.suggestion_icon_bg else null),
                        ))
                        drain(adapter, 2)
                        val toggleIndex = (0 until adapter.itemCount).first {
                            adapter.getItemViewType(it) == SuggestionAdapter.VIEW_TYPE_INLINE_TOGGLE
                        }
                        adapter.onBindViewHolder(toggleHolder, toggleIndex)
                        assertEquals("$skin inline tint", colors.icon, toggleHolder.badgeIcon.imageTintList?.defaultColor)
                        if (icon == R.drawable.inline_suggestion_key_24) {
                            assertEquals("$skin inline badge background", KeyboardSkinRegistry.find(skin)!!.palette.key,
                                toggleHolder.badgeIcon.backgroundTintList?.defaultColor)
                        } else assertNull(toggleHolder.badgeIcon.background)
                        assertTrue("$skin inline pixels", iconPixels(toggleHolder.badgeIcon).count { it == colors.icon } > 20)
                        val candidateIndex = (0 until adapter.itemCount).first {
                            adapter.getItemViewType(it) == SuggestionAdapter.VIEW_TYPE_SUGGESTION
                        }
                        val candidateHolder = adapter.onCreateViewHolder(parent,
                            SuggestionAdapter.VIEW_TYPE_SUGGESTION) as SuggestionAdapter.SuggestionViewHolder
                        adapter.onBindViewHolder(candidateHolder, candidateIndex)
                        assertEquals("$skin candidate text", colors.text, candidateHolder.text.currentTextColor)
                    }
                }
                fixture.shortcuts.submitList(listOf(ShortcutType.CLIP_BOARD))
                shadowOf(Looper.getMainLooper()).idle()
                val shortcutHolder = fixture.shortcuts.onCreateViewHolder(FrameLayout(fixture.context), 0)
                fixture.shortcuts.onBindViewHolder(shortcutHolder, 0)
                assertNotNull("$skin toolbar tint", shortcutHolder.imageView.colorFilter)
                val button = fixture.binding.suggestionVisibility
                assertEquals("$skin expand tint", colors.text, button.imageTintList!!.defaultColor)
            }
        } finally { fixture.close() }
    }

    private fun iconPixels(view: android.widget.ImageView): IntArray {
        view.layout(0, 0, 96, 96)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(96 * 96).also {
            bitmap.getPixels(it, 0, 96, 0, 0, 96, 96)
            bitmap.recycle()
        }
    }

    private fun drain(adapter: RecyclerView.Adapter<*>, expected: Int) {
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(expected, adapter.itemCount)
    }
}
