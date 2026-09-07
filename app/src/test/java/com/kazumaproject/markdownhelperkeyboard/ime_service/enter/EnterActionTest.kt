package com.kazumaproject.markdownhelperkeyboard.ime_service.enter

import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import org.junit.Assert.assertEquals
import org.junit.Test

/** Characterization tests, NOT claims about unobserved Gboard behavior. */
class EnterActionTest {
    @Test fun eachInputTypeDispatchesExactlyOneOperation() {
        val newline = setOf(InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine,
            InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage)
        val done = setOf(InputTypeForIME.TextDone, InputTypeForIME.Number, InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword, InputTypeForIME.NumberSigned, InputTypeForIME.Phone,
            InputTypeForIME.Date, InputTypeForIME.Datetime, InputTypeForIME.Time)
        val search = setOf(InputTypeForIME.TextSearchView, InputTypeForIME.TextWebSearchView,
            InputTypeForIME.TextWebSearchViewFireFox)
        val types = InputTypeForIME::class.java.declaredClasses.map {
            it.getField("INSTANCE").get(null) as InputTypeForIME
        }
        types.forEach { type ->
            val expected = when (type) {
                in newline -> "newline"
                in done -> "action:${EditorInfo.IME_ACTION_DONE}"
                in search -> "action:${EditorInfo.IME_ACTION_SEARCH}"
                InputTypeForIME.TextNextLine -> "action:${EditorInfo.IME_ACTION_NEXT}"
                else -> "key"
            }
            val calls = mutableListOf<String>()
            EnterActionExecutor.execute(EnterActionResolver.resolve(type),
                { text, position ->
                    assertEquals("\n", text); assertEquals(1, position); calls.add("newline")
                }, { calls.add("action:$it") }, { code -> assertEquals(66, code); calls.add("key") })
            assertEquals(type.toString(), listOf(expected), calls)
        }
    }

    @Test fun customActionIdIsNotReplacedOrDispatchedTwice() {
        val calls = mutableListOf<String>()
        EnterActionExecutor.execute(EnterAction.EditorAction(12345),
            { text, position ->
                    assertEquals("\n", text); assertEquals(1, position); calls.add("newline")
                }, { calls.add("action:$it") }, { code -> assertEquals(66, code); calls.add("key") })
        assertEquals(listOf("action:12345"), calls)
    }
    @Test fun rejectedEditorActionDoesNotRetryOrInsertNewline() {
        val calls = mutableListOf<String>()
        EnterActionExecutor.execute(EnterAction.EditorAction(EditorInfo.IME_ACTION_SEND),
            { _, _ -> calls.add("newline"); true },
            { calls.add("action:$it"); false },
            { calls.add("key:$it") })
        assertEquals(listOf("action:${EditorInfo.IME_ACTION_SEND}"), calls)
    }

    @Test fun rejectedNewlineDoesNotFallBackToAnotherOperation() {
        val calls = mutableListOf<String>()
        EnterActionExecutor.execute(EnterAction.NewLine,
            { _, _ -> calls.add("newline"); false },
            { calls.add("action:$it"); true },
            { calls.add("key:$it") })
        assertEquals(listOf("newline"), calls)
    }

}
