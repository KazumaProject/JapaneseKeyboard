package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceThreadBoundaryContractTest {

    @Test
    fun suggestionAdapterAssignmentsStayInsideMainThreadHelpers() {
        val lines = mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
            .readLines()
        val assignmentRegex = Regex("""suggestionAdapter(?:Full)?\?\.suggestions\s*=""")
        val assignments = lines.withIndex()
            .filter { assignmentRegex.containsMatchIn(it.value) }

        val allowedRanges = listOf(
            "setSuggestionAdapterSuggestionsOnMain",
            "setSuggestionAdaptersOnMain",
            "updateSuggestionAdaptersOnMain"
        ).map { functionBodyLineRange(lines, it) }

        val unexpectedAssignments = assignments.filterNot { assignment ->
            allowedRanges.any { range -> assignment.index in range }
        }

        assertTrue(
            unexpectedAssignments.joinToString(
                separator = "\n",
                prefix = "suggestionAdapter.suggestions must be assigned only through Main helpers:\n"
            ) { "${it.index + 1}: ${it.value.trim()}" },
            unexpectedAssignments.isEmpty()
        )
    }

    @Test
    fun candidateStripViewUpdatesKeepMainThreadAssertions() {
        val text = mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
            .readText()

        assertTrue(text.contains("updateMainCandidateStripAfterListUpdated()"))
        assertTrue(text.contains("assertMainThread(\"setMainSuggestionColumn\")"))
        assertTrue(text.contains("assertMainThread(\"updateCandidateStripPresentation\")"))
        assertTrue(text.contains("withContext(Dispatchers.Main.immediate)"))
    }

    @Test
    fun candidateStripLayoutUsesStableLayoutKey() {
        val text = mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
            .readText()

        assertTrue(text.contains("data class SuggestionLayoutKey"))
        assertTrue(text.contains("lastSuggestionLayoutKey == key"))
        assertTrue(text.contains("mainSuggestionGridSpacingDecoration"))
        assertTrue(text.contains("removeItemDecoration(decoration)"))
    }

    @Test
    fun suggestionAdapterDoesNotCallCalculateDiffDirectly() {
        val text = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt"
        ).readText()

        assertTrue(text.contains("AsyncListDiffer"))
        assertTrue(!text.contains("DiffUtil.calculateDiff("))
    }

    private fun functionBodyLineRange(lines: List<String>, functionName: String): IntRange {
        val start = lines.indexOfFirst { it.contains("fun $functionName") }
        assertTrue("Missing function $functionName", start >= 0)

        var depth = 0
        var seenBody = false
        for (index in start until lines.size) {
            val line = lines[index]
            depth += line.count { it == '{' }
            seenBody = seenBody || line.contains('{')
            depth -= line.count { it == '}' }
            if (seenBody && depth == 0) {
                return start..index
            }
        }
        error("Missing function body end for $functionName")
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
