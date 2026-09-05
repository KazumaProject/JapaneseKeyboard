package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R

internal const val BUTTON_DELETION_WARNING_OPT_OUT_TAG =
    "keyboard-editor-button-deletion-warning-opt-out"

internal enum class KeyboardEditorDeletionTarget {
    ROW,
    COLUMN,
    BUTTON
}

internal data class KeyboardEditorDeletionDialogSpec(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val showsButtonWarningOptOut: Boolean
)

internal fun keyboardEditorDeletionDialogSpec(
    target: KeyboardEditorDeletionTarget
): KeyboardEditorDeletionDialogSpec = when (target) {
    KeyboardEditorDeletionTarget.ROW -> KeyboardEditorDeletionDialogSpec(
        titleRes = R.string.editor_delete_row_confirmation_title,
        messageRes = R.string.editor_delete_row_confirmation_message,
        showsButtonWarningOptOut = false
    )

    KeyboardEditorDeletionTarget.COLUMN -> KeyboardEditorDeletionDialogSpec(
        titleRes = R.string.editor_delete_column_confirmation_title,
        messageRes = R.string.editor_delete_column_confirmation_message,
        showsButtonWarningOptOut = false
    )

    KeyboardEditorDeletionTarget.BUTTON -> KeyboardEditorDeletionDialogSpec(
        titleRes = R.string.editor_delete_button_confirmation_title,
        messageRes = R.string.editor_delete_button_confirmation_message,
        showsButtonWarningOptOut = true
    )
}

internal fun showKeyboardEditorDeletionConfirmationDialog(
    context: Context,
    target: KeyboardEditorDeletionTarget,
    onDeletionConfirmed: () -> Unit,
    onButtonWarningSuppressed: () -> Unit
): AlertDialog {
    val spec = keyboardEditorDeletionDialogSpec(target)
    val buttonWarningOptOut = if (spec.showsButtonWarningOptOut) {
        MaterialCheckBox(context).apply {
            tag = BUTTON_DELETION_WARNING_OPT_OUT_TAG
            text = context.getString(R.string.editor_hide_button_delete_warning_during_editing)
            val horizontalPadding = context.dpToPx(24)
            val verticalPadding = context.dpToPx(8)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
    } else {
        null
    }

    val dialogBuilder = MaterialAlertDialogBuilder(context)
        .setTitle(spec.titleRes)
        .setMessage(spec.messageRes)
        .setPositiveButton(R.string.editor_confirm_delete) { _, _ ->
            if (buttonWarningOptOut?.isChecked == true) {
                onButtonWarningSuppressed()
            }
            onDeletionConfirmed()
        }
        .setNegativeButton(R.string.editor_cancel_delete, null)

    buttonWarningOptOut?.let(dialogBuilder::setView)
    return dialogBuilder.show()
}

private fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()
