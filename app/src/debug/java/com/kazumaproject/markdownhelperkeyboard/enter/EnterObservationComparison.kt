package com.kazumaproject.markdownhelperkeyboard.enter

import org.json.JSONObject

/** Result equality only: different but equivalent InputConnection traces are permitted. */
object EnterObservationComparison {
    private val fields = listOf("text", "selectionStart", "selectionEnd", "composingStart", "composingEnd", "actions")

    fun assertMatches(reference: JSONObject, actual: JSONObject) {
        check(reference.getString("status") == "reviewed") { "Gboard baseline has not been reviewed" }
        check(reference.getString("ime").startsWith("com.google.android.inputmethod.latin/")) { "Baseline is not Gboard" }
        for (field in listOf("caseId", "operation", "androidSdk")) {
            check(reference.get(field) == actual.get(field)) { "Incompatible $field" }
        }
        for (field in listOf("inputType", "imeOptions", "actionId", "actionLabel")) {
            check(reference.getJSONObject("editorInfo").get(field) == actual.getJSONObject("editorInfo").get(field)) {
                "Incompatible EditorInfo.$field"
            }
        }
        compareSnapshot("before", reference.getJSONObject("before"), actual.getJSONObject("before"))
        val expected = reference.getJSONArray("after")
        val observed = actual.getJSONArray("after")
        check(expected.length() == observed.length()) { "Enter step count differs" }
        for (i in 0 until expected.length()) compareSnapshot("Enter ${i + 1}", expected.getJSONObject(i), observed.getJSONObject(i))
    }

    private fun compareSnapshot(step: String, expected: JSONObject, actual: JSONObject) {
        for (field in fields) {
            check(expected.get(field).toString() == actual.get(field).toString()) {
                "$step $field: expected ${JSONObject.quote(expected.get(field).toString())}, actual ${JSONObject.quote(actual.get(field).toString())}"
            }
        }
    }
}
