package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroVariable

data class TextMacroVariablePresentation(
    val title: String,
    val syntax: String,
    val description: String,
    val example: String,
    val restriction: String?,
)

fun TextMacroVariable.presentation(context: Context): TextMacroVariablePresentation = when (this) {
    TextMacroVariable.DATE -> TextMacroVariablePresentation(
        title = context.getString(R.string.text_macro_variable_date_title),
        syntax = source(),
        description = context.getString(R.string.text_macro_variable_date_description),
        example = context.getString(R.string.text_macro_variable_date_example),
        restriction = context.getString(R.string.text_macro_variable_date_restriction),
    )

    TextMacroVariable.TIME -> TextMacroVariablePresentation(
        title = context.getString(R.string.text_macro_variable_time_title),
        syntax = source(),
        description = context.getString(R.string.text_macro_variable_time_description),
        example = context.getString(R.string.text_macro_variable_time_example),
        restriction = context.getString(R.string.text_macro_variable_time_restriction),
    )

    TextMacroVariable.SELECTION -> TextMacroVariablePresentation(
        title = context.getString(R.string.text_macro_variable_selection_title),
        syntax = source(),
        description = context.getString(R.string.text_macro_variable_selection_description),
        example = context.getString(R.string.text_macro_variable_selection_example),
        restriction = context.getString(R.string.text_macro_variable_selection_restriction),
    )

    TextMacroVariable.CLIPBOARD -> TextMacroVariablePresentation(
        title = context.getString(R.string.text_macro_variable_clipboard_title),
        syntax = source(),
        description = context.getString(R.string.text_macro_variable_clipboard_description),
        example = context.getString(R.string.text_macro_variable_clipboard_example),
        restriction = context.getString(R.string.text_macro_variable_clipboard_restriction),
    )

    TextMacroVariable.CURSOR -> TextMacroVariablePresentation(
        title = context.getString(R.string.text_macro_variable_cursor_title),
        syntax = source(),
        description = context.getString(R.string.text_macro_variable_cursor_description),
        example = context.getString(R.string.text_macro_variable_cursor_example),
        restriction = context.getString(R.string.text_macro_variable_cursor_restriction),
    )

    TextMacroVariable.NEWLINE -> TextMacroVariablePresentation(
        title = context.getString(R.string.text_macro_variable_newline_title),
        syntax = source(),
        description = context.getString(R.string.text_macro_variable_newline_description),
        example = context.getString(R.string.text_macro_variable_newline_example),
        restriction = context.getString(R.string.text_macro_variable_newline_restriction),
    )

}
