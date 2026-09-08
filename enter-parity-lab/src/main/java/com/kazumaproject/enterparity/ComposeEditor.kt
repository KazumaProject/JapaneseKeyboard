package com.kazumaproject.enterparity

import android.view.View
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue

object ComposeEditor {
    private var current by mutableStateOf(TextFieldValue("seed", TextRange(4)))
    @JvmStatic fun text() = current.text
    @JvmStatic fun create(activity: LabActivity, action: Int, multi: Boolean): View {
        current = TextFieldValue("seed", TextRange(4))
        return ComposeView(activity).apply {
            setContent {
                val focus = remember { FocusRequester() }
                val ime = listOf(ImeAction.Default, ImeAction.None, ImeAction.Go, ImeAction.Search,
                    ImeAction.Send, ImeAction.Next, ImeAction.Done, ImeAction.Previous)[action]
                BasicTextField(value = current, onValueChange = { current = it },
                    modifier = Modifier.focusRequester(focus), singleLine = !multi,
                    keyboardOptions = KeyboardOptions(imeAction = ime),
                    keyboardActions = KeyboardActions(
                        onDone = { activity.event("keyboardAction", 6) },
                        onGo = { activity.event("keyboardAction", 2) },
                        onSearch = { activity.event("keyboardAction", 3) },
                        onSend = { activity.event("keyboardAction", 4) },
                        onNext = { activity.event("keyboardAction", 5); activity.next.requestFocus() },
                        onPrevious = { activity.event("keyboardAction", 7); activity.next.requestFocus() }))
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
    }
}
