package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorFragment

/** Edits a key draft without leaving the key editor or writing a built-in layout. */
class CustomFreeFlickEditorDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = FrameLayout(requireContext()).apply { id = R.id.custom_free_flick_editor_host }
        childFragmentManager.setFragmentResultListener(RESULT, this) { _, result ->
            parentFragmentManager.setFragmentResult(RESULT, result)
        }
        return Dialog(requireContext(), com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar).apply {
            setContentView(host)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        (childFragmentManager.findFragmentById(host.id) as? PopupMapEditorFragment)?.leaveEditor()
                    }
                    true
                } else false
            }
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        if (childFragmentManager.findFragmentById(R.id.custom_free_flick_editor_host) == null) {
            childFragmentManager.beginTransaction().replace(R.id.custom_free_flick_editor_host,
                PopupMapEditorFragment().apply { arguments = this@CustomFreeFlickEditorDialog.arguments }).commitNow()
        }
    }

    companion object {
        const val ARG_CONFIG = "customFreeFlickConfig"
        const val ARG_KEY_LABEL = "customFreeFlickKeyLabel"
        const val RESULT = "customFreeFlickResult"
        fun create(json: String, keyLabel: String = "") = CustomFreeFlickEditorDialog().apply {
            arguments = bundleOf(ARG_CONFIG to json, ARG_KEY_LABEL to keyLabel)
        }
    }
}
