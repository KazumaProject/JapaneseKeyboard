package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyTextInputBehavior
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.mockito.Mockito.mock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyEditorToggleValidationTest {
    @Test fun centerIsRequiredEvenWithOtherOutputs_andCanBeCorrected() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MarkdownKeyboard)
        val binding = FragmentKeyEditorBinding.inflate(LayoutInflater.from(context))
        val fragment = KeyEditorFragment()
        ReflectionHelpers.setField(fragment, "_binding", binding)
        ReflectionHelpers.setField(fragment, "displayActions", emptyList<Any>())
        ReflectionHelpers.setField(fragment, "currentKeyData", mock(KeyData::class.java))
        ReflectionHelpers.setField(fragment, "selectedTextInputBehavior", KeyTextInputBehavior.TOGGLE)
        binding.keyTypeChipGroup.check(R.id.chip_normal)
        binding.inputStyleChipGroup.check(R.id.chip_petal_flick)
        binding.keyLabelEdittext.setText("あ")
        val items = mutableListOf(FlickMappingItem(direction = FlickDirection.TAP, output = ""), FlickMappingItem(direction = FlickDirection.UP, output = "い"))
        ReflectionHelpers.setField(fragment, "currentToggleFlickItems", items)
        fun validate() = ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateDoneButtonState")

        validate()
        assertFalse(binding.buttonDone.isEnabled)
        assertEquals("中央のトグル出力を設定してください", binding.textCharInputLayout.error.toString())
        // The save guard must return before touching the uninitialized ViewModel/navigation.
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "onDone")
        items[0] = FlickMappingItem(direction = FlickDirection.TAP, output = "あ")
        validate()
        assertTrue(binding.buttonDone.isEnabled)
        assertNull(binding.textCharInputLayout.error)
        items[0] = FlickMappingItem(direction = FlickDirection.TAP, output = "あい")
        validate()
        assertFalse(binding.buttonDone.isEnabled)
    }
}
