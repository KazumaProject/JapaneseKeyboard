package com.kazumaproject.markdownhelperkeyboard.enter

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnterObservationComparisonTest {
    private fun observation() = JSONObject("""{
        "status":"reviewed", "ime":"com.google.android.inputmethod.latin/IME",
        "caseId":"synthetic-comparator-test", "operation":"tap-twice", "androidSdk":35,
        "editorInfo":{"inputType":1,"imeOptions":1,"actionId":0,"actionLabel":null},
        "before":{"text":"a","selectionStart":1,"selectionEnd":1,"composingStart":0,"composingEnd":1,"actions":[]},
        "after":[
            {"text":"a","selectionStart":1,"selectionEnd":1,"composingStart":-1,"composingEnd":-1,"actions":[]},
            {"text":"a\n","selectionStart":2,"selectionEnd":2,"composingStart":-1,"composingEnd":-1,"actions":[]}
        ]
    }""")

    @Test fun equivalentResultsMayUseDifferentCalls() {
        val actual = observation()
        actual.getJSONArray("after").getJSONObject(0).put("calls", "different internal implementation")
        EnterObservationComparison.assertMatches(observation(), actual)
    }

    @Test fun missingNewlineWrongActionDoubleActionAndUnfinishedCompositionFail() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("after").getJSONObject(1).put("text", "a") },
            { it.getJSONArray("after").getJSONObject(0).getJSONArray("actions").put(4) },
            { it.getJSONArray("after").getJSONObject(1).getJSONArray("actions").put(3).put(3) },
            { it.getJSONArray("after").getJSONObject(0).put("composingStart", 0) },
            { it.getJSONObject("before").put("selectionStart", 0) },
            { it.put("status", "observed-unreviewed") },
        )
        mutations.forEach { change ->
            val actual = observation().also(change)
            if (actual.getString("status") != "reviewed") {
                assertThrows(IllegalStateException::class.java) { EnterObservationComparison.assertMatches(actual, observation()) }
            } else {
                assertThrows(IllegalStateException::class.java) { EnterObservationComparison.assertMatches(observation(), actual) }
            }
        }
    }

    @Test fun committingAllClausesOnFirstEnterFailsTheObservedSegmentCase() {
        val source = requireNotNull(javaClass.classLoader!!.getResourceAsStream("enter/gboard-editing-observations.json"))
        val observations = source.bufferedReader().use { JSONArray(it.readText()) }
        val reference = (0 until observations.length()).map { observations.getJSONObject(it) }
            .single { it.getString("caseId").endsWith("-segment") }
        val actual = JSONObject(reference.toString())
        actual.getJSONArray("after").getJSONObject(0)
            .put("composingStart", -1).put("composingEnd", -1)
        assertThrows(IllegalStateException::class.java) {
            EnterObservationComparison.assertMatches(reference, actual)
        }
    }

    @Test fun matrixContainsEveryActionFlagAndStateWithoutInventedExpectations() {
        val cases = EnterProbeCase.all()
        assertEquals(cases.size, cases.map { it.id }.distinct().size)
        assertEquals(144, cases.count { it.id.startsWith("action-") && it.state in listOf("committed", "composing") })
        for (action in 0..8) for (flags in 0..7) {
            assertEquals(2, cases.count { it.id.startsWith("action-$action-flags-$flags-") && it.state in listOf("committed", "composing") })
        }
    }
}
