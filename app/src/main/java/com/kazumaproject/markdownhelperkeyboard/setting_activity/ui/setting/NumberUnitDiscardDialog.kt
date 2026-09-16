package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.kazumaproject.markdownhelperkeyboard.R

internal enum class UnitEditorExit { BACK, SETTINGS_HOME, BOTTOM_TAB, LEARN_DICTIONARY }

/** A navigation request can survive recreation without retaining an Activity callback. */
internal data class UnitEditorNavigation(val exit: UnitEditorExit, val itemId: Int = 0) {
    fun toBundle() = Bundle().apply { putString("exit", exit.name); putInt("itemId", itemId) }
    companion object {
        fun fromBundle(bundle: Bundle) = UnitEditorNavigation(
            UnitEditorExit.valueOf(requireNotNull(bundle.getString("exit"))), bundle.getInt("itemId"))
    }
}

class NumberUnitDiscardDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext()).setMessage(R.string.number_discard)
            .setNegativeButton(R.string.number_cancel) { _, _ -> answer(false) }
            .setPositiveButton(android.R.string.ok) { _, _ -> answer(true) }.create()

    override fun onCancel(dialog: DialogInterface) {
        answer(false)
        super.onCancel(dialog)
    }

    private fun answer(discard: Boolean) {
        parentFragmentManager.setFragmentResult(RESULT, Bundle(requireArguments()).apply {
            putBoolean("discard", discard)
        })
    }

    companion object {
        const val RESULT = "number-unit-exit"
        const val TAG = "number-unit-discard"
    }
}
