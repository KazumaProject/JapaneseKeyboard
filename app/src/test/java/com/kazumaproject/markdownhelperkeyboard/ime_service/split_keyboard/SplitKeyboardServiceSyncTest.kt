package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Exercises the real service's queued update path, without starting dictionaries or IME windows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitKeyboardServiceSyncTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context)
        it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1; imeOptions = EditorInfo.IME_ACTION_SEARCH })
            .`when`(it).getCurrentInputEditorInfo()
        Reflect.setField(it, "splitController", SplitKeyboardController(context, View(context), {}, {}, {}))
    }
    private val modes = listOf(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Gojuon,
        TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji,
        TenKeyQWERTYMode.Sumire, TenKeyQWERTYMode.Custom)
    private val bindings = SplitSlot.entries.associateWith {
        FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context)).also { binding ->
            binding.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.createFinalLayout(
                KeyboardInputMode.HIRAGANA, emptyMap(), "switch-mode-effective", "default"))
        }
    }
    private val inputs get() = Reflect.getField<MutableMap<SplitSlot, Any>>(service, "splitInputs")
    private val text get() = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
    private fun call(name: String) = Reflect.callInstanceMethod<Unit>(service, name)
    private fun japanese(mode: TenKeyQWERTYMode) = mode != TenKeyQWERTYMode.TenKeyQWERTY
    private fun install(slot: SplitSlot, mode: TenKeyQWERTYMode) {
        // Use Kotlin's default-argument constructor so optional per-pane state retains production defaults.
        val type = Class.forName(IMEService::class.java.name + "\$SplitInputState")
        val constructor = type.declaredConstructors.single { it.parameterTypes.last().name.endsWith("DefaultConstructorMarker") }
        constructor.isAccessible = true
        val args = constructor.parameterTypes.map<Class<*>, Any?> { when (it) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            else -> null
        } }.toTypedArray()
        val required = arrayOf(slot, SplitKeyboardSelection(KeyboardType.TENKEY), bindings.getValue(slot),
            SuggestionAdapter(), mode, if (japanese(mode)) InputMode.ModeJapanese else InputMode.ModeEnglish,
            mode == TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        required.copyInto(args)
        args[args.lastIndex - 1] = -128 // first seven arguments supplied, all remaining arguments defaulted
        inputs[slot] = constructor.newInstance(*args)
    }
    private fun select(slot: SplitSlot, mode: TenKeyQWERTYMode) {
        Reflect.setField(service, "activeSplitSlot", slot)
        Reflect.setField(service, "floatingKeyboardBinding", bindings.getValue(slot))
        Reflect.setField(service, "currentInputModeForSession", if (japanese(mode)) InputMode.ModeJapanese else InputMode.ModeEnglish)
        Reflect.setField(service, "currentQwertyRomajiModeForSession", mode == TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = mode
    }
    private fun assertEnter(slot: SplitSlot, mode: TenKeyQWERTYMode, composing: Boolean) {
        val binding = bindings.getValue(slot)
        when (mode) {
            TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Gojuon -> {
                val view = if (mode == TenKeyQWERTYMode.Default) binding.keyboardViewFloating.findViewById<ImageView>(com.kazumaproject.tenkey.R.id.key_enter)
                    else binding.gojuonViewFloating.findViewById<ImageView>(com.kazumaproject.gojuon_keyboard.R.id.key_enter)
                assertEquals(if (composing) com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
                    else com.kazumaproject.core.R.drawable.baseline_search_24, shadowOf(view.drawable).createdFromResId)
            }
            TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji -> assertEquals(
                if (composing) { if (japanese(mode)) "確定" else "done" } else { if (japanese(mode)) "検索" else "search" },
                binding.qwertyViewFloating.snapshotUiState().enterKeyText)
            else -> {
                val dynamic = Reflect.getField<Map<String, Any>>(binding.customLayoutFloating, "dynamicKeyMap")
                val key = Reflect.getField<com.kazumaproject.custom_keyboard.data.KeyData>(dynamic.getValue("enter_key"), "keyData")
                assertEquals(if (composing) "確定" else "検索", key.label)
            }
        }
    }
    @Test fun serviceDispatchUpdatesAll36PairsWithoutChangingTheInputSource() {
        for (left in modes) for (right in modes) {
            install(SplitSlot.MAIN, left); install(SplitSlot.SUB, right)
            for (source in SplitSlot.entries) {
                select(source, if (source == SplitSlot.MAIN) left else right)
                for (value in listOf("か", "は", "")) {
                    text.value = value
                    Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, source))
                    // Candidate notifications may arrive before or after the key's state save.
                    call("scheduleSplitCandidates")
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEnter(SplitSlot.MAIN, left, value.isNotEmpty())
                    assertEnter(SplitSlot.SUB, right, value.isNotEmpty())
                    assertEquals(source, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
                    assertSame(bindings.getValue(source), Reflect.getField<Any>(service, "floatingKeyboardBinding"))
                }
            }
        }
    }
    @Test fun queuedUpdateReadsLatestTextAndDefersThePaneUnderTheFinger() {
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        call("syncSplitPresentation")
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "touching", true)
        text.value = "か"
        call("scheduleSplitCandidates")
        shadowOf(Looper.getMainLooper()).idle()
        assertEnter(SplitSlot.MAIN, TenKeyQWERTYMode.Default, true)
        assertEnter(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji, false)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "touching", false)
        call("syncSplitPresentation")
        assertEnter(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji, true)
        call("scheduleSplitCandidates")
        text.value = "" // confirmation before the posted candidate refresh executes
        shadowOf(Looper.getMainLooper()).idle()
        assertEnter(SplitSlot.MAIN, TenKeyQWERTYMode.Default, false)
        assertEnter(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji, false)
    }
    @Test fun clipboardBroadcastPreservesEachPanesSymbolTab() {
        val symbolMode = com.kazumaproject.core.data.clicked_symbol.SymbolMode.CLIPBOARD
        val shown = com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState(true)
        for (slot in SplitSlot.entries) {
            install(slot, TenKeyQWERTYMode.Default)
            Reflect.setField(inputs.getValue(slot), "symbolState", shown)
            Reflect.setField(inputs.getValue(slot), "symbolRequest", shown)
            bindings.getValue(slot).floatingSymbolKeyboard.setSymbolLists(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                if (slot == SplitSlot.MAIN) symbolMode else com.kazumaproject.core.data.clicked_symbol.SymbolMode.SYMBOL)
        }
        Reflect.getField<MutableStateFlow<com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState>>(
            service, "_keyboardSymbolViewState").value = shown
        val items = listOf(com.kazumaproject.core.data.clipboard.ClipboardItem.Text(1, "同期テスト"))
        for (source in SplitSlot.entries) {
            select(source, TenKeyQWERTYMode.Default)
            Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, source))
            Reflect.callInstanceMethod<Unit>(service, "updateFloatingClipboardItems", ClassParameter.from(List::class.java, items))
            shadowOf(Looper.getMainLooper()).idle()
            for (slot in SplitSlot.entries) {
                val view = bindings.getValue(slot).floatingSymbolKeyboard
                assertEquals(items, Reflect.getField<List<*>>(view, "clipBoardItems"))
                assertEquals(if (slot == SplitSlot.MAIN) symbolMode else com.kazumaproject.core.data.clicked_symbol.SymbolMode.SYMBOL,
                    Reflect.getField<Any>(view, "currentMode"))
            }
        }
    }

    @Test fun detachedCandidateSurfaceUsesSharedSourceAndRestoresItsOriginalParent() {
        val main = com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding.inflate(LayoutInflater.from(context))
        Reflect.setField(service, "mainLayoutBinding", main)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val source = SuggestionAdapter()
        Reflect.setField(service, "suggestionAdapter", source)
        main.suggestionRecyclerView.adapter = source
        val original = main.suggestionViewParent.parent
        val target = android.widget.LinearLayout(context)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        select(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        fun move(to: android.widget.LinearLayout?) = Reflect.callInstanceMethod<Unit>(service, "moveCandidateSurface",
            ClassParameter.from(android.widget.LinearLayout::class.java, to))
        move(target)
        assertSame(target, main.suggestionViewParent.parent)
        assertSame(source, main.suggestionRecyclerView.adapter)
        assertEquals(SplitSlot.SUB, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
        val firstHost = Reflect.getField<Any>(service, "candidateSurfaceHost")
        move(target)
        assertSame(firstHost, Reflect.getField<Any>(service, "candidateSurfaceHost"))
        move(null)
        assertSame(original, main.suggestionViewParent.parent)
        assertNull(Reflect.getField<Any?>(service, "candidateSurfaceHost"))
        assertEquals(SplitSlot.SUB, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
    }

}
