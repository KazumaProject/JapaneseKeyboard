package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentHostCallback
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyIconRef
import com.kazumaproject.custom_keyboard.data.KeyIconType
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MixedFlickKeyEditorTest {
    @Test
    fun eachCellCanSwitchBetweenTextActionAndUnassigned() {
        val draft = MixedFlickEditorState.fromKey(blankKey, emptyMap())
        val (fragment, binding) = editor(draft)
        assertFalse(binding.buttonDone.isEnabled)
        assertFalse(binding.textCharInputLayout.isVisible)
        assertFalse(binding.keyIconOverrideGroup.isVisible)

        binding.chipMixedText.performClick()
        assertTrue(binding.textCharInputLayout.isVisible)
        assertFalse(binding.keyIconOverrideGroup.isVisible)
        binding.textCharEdittext.setText("かな")
        assertTrue(binding.buttonDone.isEnabled)

        binding.flickGridEditorView.onCellSelected?.invoke(CellMode.SpecialFlick(FlickDirection.UP))
        binding.chipMixedAction.performClick()
        assertFalse(binding.textCharInputLayout.isVisible)
        assertTrue(binding.specialFlickEditorGroup.isVisible)
        val deleteLabel = KeyActionMapper.getDisplayActions(binding.root.context)
            .first { it.action == KeyAction.Delete }.displayName
        binding.specialFlickMappingsRecyclerView.select(deleteLabel)
        assertTrue(binding.keyIconOverrideGroup.isVisible)
        assertEquals(MixedFlickSlot.Action(KeyAction.Delete), draft.slots[FlickDirection.UP])
        val chosen = KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "remove")
        ReflectionHelpers.callInstanceMethod<Unit>(
            fragment, "replaceSelectedIcon", ClassParameter.from(KeyIconRef::class.java, chosen)
        )
        val saved = Bundle()
        fragment.onSaveInstanceState(saved)
        val restored = MixedFlickEditorState.fromBundle(requireNotNull(saved.getBundle("mixedFlickDraft")))
        assertEquals(MixedFlickSlot.Action(KeyAction.Delete, "remove"), restored.slots[FlickDirection.UP])
        binding.buttonClearIconOverride.performClick()
        assertEquals(MixedFlickSlot.Action(KeyAction.Delete), draft.slots[FlickDirection.UP])
        assertEquals(MixedFlickSlot.Text("かな"), draft.slots[FlickDirection.TAP])

        binding.chipMixedUnassigned.performClick()
        assertFalse(binding.keyIconOverrideGroup.isVisible)
        assertEquals(MixedFlickSlot.Unassigned, draft.slots[FlickDirection.UP])
        assertTrue(binding.buttonDone.isEnabled)
        binding.flickGridEditorView.onCellSelected?.invoke(CellMode.SpecialFlick(FlickDirection.TAP))
        binding.chipMixedUnassigned.performClick()
        assertFalse(binding.buttonDone.isEnabled)
    }

    @Test
    fun switchTargetsAndSelectionSurviveSavingTheDraft() {
        val move = KeyAction.MoveToCustomKeyboard("first")
        val draft = MixedFlickEditorState.fromKey(
            blankKey.copy(keyType = KeyType.MIXED_FLICK, action = move),
            mapOf(
                FlickDirection.TAP to FlickAction.Action(move),
                FlickDirection.UP_RIGHT to FlickAction.Action(move)
            )
        )
        val (fragment, binding) = editor(draft)
        binding.flickGridEditorView.onCellSelected?.invoke(CellMode.SpecialFlick(FlickDirection.UP_RIGHT))
        binding.mixedFlickTargetSpinner.select("Second")
        binding.mixedKeyLabelEdittext.setText("Switch")

        val saved = Bundle()
        fragment.onSaveInstanceState(saved)
        val restored = MixedFlickEditorState.fromBundle(requireNotNull(saved.getBundle("mixedFlickDraft")))
        val (_, restoredBinding) = editor(restored)
        assertEquals("Switch", restoredBinding.mixedKeyLabelEdittext.text.toString())
        assertEquals(CellMode.SpecialFlick(FlickDirection.UP_RIGHT), restoredBinding.flickGridEditorView.getCurrentMode())
        assertEquals("Second", restoredBinding.mixedFlickTargetSpinner.text.toString())
        assertEquals(MixedFlickSlot.Action(move), restored.slots[FlickDirection.TAP])

        restoredBinding.flickGridEditorView.onCellSelected?.invoke(CellMode.SpecialFlick(FlickDirection.TAP))
        assertEquals("First", restoredBinding.mixedFlickTargetSpinner.text.toString())
        assertTrue(restoredBinding.buttonDone.isEnabled)
    }

    @Test
    fun convertingAPetalKeyRetainsItsTapAndNormalizesLeftAndRight() {
        val draft = MixedFlickEditorState.fromKey(
            blankKey.copy(label = "Label", keyType = KeyType.PETAL_FLICK, action = KeyAction.Text("center")),
            mapOf(
                FlickDirection.UP_LEFT_FAR to FlickAction.Input("left"),
                FlickDirection.UP_RIGHT_FAR to FlickAction.Input("right")
            )
        )
        assertEquals("Label", draft.label)
        assertEquals(MixedFlickSlot.Text("center"), draft.slots[FlickDirection.TAP])
        assertEquals(MixedFlickSlot.Text("left"), draft.slots[FlickDirection.UP_LEFT])
        assertEquals(MixedFlickSlot.Text("right"), draft.slots[FlickDirection.UP_RIGHT])
    }

    private fun editor(draft: MixedFlickEditorState): Pair<KeyEditorFragment, FragmentKeyEditorBinding> {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MarkdownKeyboard)
        val binding = FragmentKeyEditorBinding.inflate(LayoutInflater.from(context))
        val fragment = KeyEditorFragment()
        val host = object : FragmentHostCallback<Any>(context, Handler(Looper.getMainLooper()), 0) {
            override fun onGetHost(): Any = this
        }
        ReflectionHelpers.setField(fragment, "mHost", host)
        ReflectionHelpers.setField(fragment, "_binding", binding)
        ReflectionHelpers.setField(fragment, "mixedFlickDraft", draft)
        ReflectionHelpers.setField(
            fragment,
            "customKeyboardTargets",
            listOf(
                CustomKeyboardLayout(name = "First", columnCount = 1, rowCount = 1, stableId = "first"),
                CustomKeyboardLayout(name = "Second", columnCount = 1, rowCount = 1, stableId = "second")
            )
        )
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "setupGridEditor")
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "setupUIListeners")
        ReflectionHelpers.callInstanceMethod<Unit>(
            fragment,
            "refreshCustomKeyboardTargetOptions",
            ClassParameter.from(String::class.java, null)
        )
        binding.keyTypeChipGroup.check(R.id.chip_mixed)
        return fragment to binding
    }

    private fun AutoCompleteTextView.select(label: String) {
        val position = (0 until adapter.count).first { adapter.getItem(it) == label }
        onItemClickListener.onItemClick(null, null, position, position.toLong())
    }

    private val blankKey = KeyData(label = "", row = 0, column = 0, isFlickable = false)
}
