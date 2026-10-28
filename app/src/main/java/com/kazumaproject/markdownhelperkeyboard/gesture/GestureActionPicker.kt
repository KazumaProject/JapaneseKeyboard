package com.kazumaproject.markdownhelperkeyboard.gesture

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.SpecialKeyActionDropdownAdapter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.groupedSpecialKeyActionOptions
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GestureActionDependencies {
    fun keyboardRepository(): KeyboardRepository
}

/** Reuse the custom/Sumire catalog and category adapter, including keyboard targets. */
internal fun Fragment.showGestureActionPicker(onSelected: (KeyAction) -> Unit) {
    val options = groupedSpecialKeyActionOptions(KeyActionMapper.getDisplayActions(requireContext()).map {
        DisplayActionUi(it.displayName, it.action, it.iconResId)
    }) { getString(it.titleResId) }
    AlertDialog.Builder(requireContext()).setAdapter(SpecialKeyActionDropdownAdapter(requireContext(), options)) { _, index ->
        val action = options[index].action?.action ?: return@setAdapter
        if (action !is KeyAction.MoveToCustomKeyboard) onSelected(action)
        else lifecycleScope.launch {
            val repository = EntryPointAccessors.fromApplication(requireContext().applicationContext,
                GestureActionDependencies::class.java).keyboardRepository()
            val targets = repository.getLayoutsNotFlowEnsuringStableIds()
            if (!isAdded) return@launch
            if (targets.isEmpty()) Toast.makeText(requireContext(), if (resources.configuration.locales[0].language == "ja")
                "カスタムキーボードがありません" else "No custom keyboards", Toast.LENGTH_LONG).show()
            else AlertDialog.Builder(requireContext()).setItems(targets.map { it.name }.toTypedArray()) { _, which ->
                onSelected(KeyAction.MoveToCustomKeyboard(targets[which].stableId))
            }.show()
        }
    }.show()
}
