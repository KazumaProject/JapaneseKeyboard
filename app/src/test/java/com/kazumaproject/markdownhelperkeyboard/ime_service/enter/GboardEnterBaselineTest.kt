package com.kazumaproject.markdownhelperkeyboard.ime_service.enter

import android.view.inputmethod.EditorInfo
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Expectations come exclusively from the checked-in, reviewed device observations. */
@RunWith(Parameterized::class)
class GboardEnterBaselineTest(private val caseId: String, private val observation: JsonObject) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf("gboard-observations.json", "gboard-type-observations.json")
            .flatMap { name ->
                GboardEnterBaselineTest::class.java.getResourceAsStream("/enter/$name")!!.bufferedReader().use {
                    JsonParser.parseReader(it).asJsonArray.map { item ->
                        val data = item.asJsonObject
                        arrayOf<Any>(data["caseId"].asString, data)
                    }
                }
            }.also { require(it.isNotEmpty()) { "No reviewed Gboard observations" } }
    }

    @Test fun nonComposingEnterMatchesReviewedGboardOutcomes() {
        assertEquals("reviewed", observation["status"].asString)
        assertTrue(observation["ime"].asString.startsWith("com.google.android.inputmethod.latin/"))
        val attrs = observation.getAsJsonObject("editorInfo")
        val info = EditorInfo().apply {
            inputType = attrs["inputType"].asInt
            imeOptions = attrs["imeOptions"].asInt
            actionId = attrs["actionId"].asInt
            actionLabel = attrs["actionLabel"].takeUnless { it.isJsonNull }?.asString
        }
        val action = EnterActionResolver.resolve(getCurrentInputTypeForIME2(info), info)
        val id = caseId
        val first = observation.getAsJsonArray("after")[0].asJsonObject
        val actions = first.getAsJsonArray("actions")
        if (actions.size() > 0) {
            assertEquals("$id must dispatch exactly one editor action", 1, actions.size())
            assertEquals(id, EnterAction.EditorAction(actions[0].asInt), action)
        } else {
            val beforeText = observation.getAsJsonObject("before")["text"].asString
            if (first["text"].asString == beforeText + "\n") {
                assertTrue("$id must insert newline without executing an action: $action",
                    action == EnterAction.NewLine || action == EnterAction.KeyEvent)
            } else {
                // Some editors filter the newline. Preserve the observed raw Enter route.
                assertEquals("$id unexpected editor outcome", beforeText, first["text"].asString)
                assertTrue(first.getAsJsonArray("calls").any { it.asString.startsWith("sendKeyEvent:0:66:") })
                assertEquals(id, EnterAction.KeyEvent, action)
            }
        }
    }
}
