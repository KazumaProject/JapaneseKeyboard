package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomKeyboardEditorActionBarLifecycleContractTest {

    @Test
    fun keyboardEditorDoesNotClearDestinationActionBarWhenItsViewIsDestroyed() {
        assertOnDestroyViewDoesNotMutateActionBar("KeyboardEditorFragment.kt")
    }

    @Test
    fun keyEditorDoesNotClearDestinationActionBarWhenItsViewIsDestroyed() {
        assertOnDestroyViewDoesNotMutateActionBar("KeyEditorFragment.kt")
    }

    @Test
    fun keyboardListRestoresItsHeaderWheneverItResumes() {
        val source = mainFile("KeyboardListFragment.kt").readText()
        val onResumeBody = source.substringAfter("override fun onResume()")
            .substringBefore("// [ADD] Function to set up the menu")

        assertTrue(
            onResumeBody.contains("title = getString(R.string.custom_layout_fragment_title)"),
        )
        assertFalse(
            "KeyboardListFragment must leave the up indicator to NavigationUI",
            onResumeBody.contains("setDisplayHomeAsUpEnabled"),
        )
        assertTrue(onResumeBody.contains("invalidateOptionsMenu()"))
    }

    private fun assertOnDestroyViewDoesNotMutateActionBar(fileName: String) {
        val source = mainFile(fileName).readText()
        val onDestroyViewBody = source.substringAfter("override fun onDestroyView()")
            .substringBeforeLast("\n}")

        assertFalse(
            "$fileName must not overwrite the shared ActionBar after navigation has restored the destination header",
            onDestroyViewBody.contains("supportActionBar"),
        )
    }

    private fun mainFile(fileName: String): File {
        val relativePath =
            "java/com/kazumaproject/markdownhelperkeyboard/custom_keyboard/ui/$fileName"
        val candidates = listOf(
            File("app/src/main/$relativePath"),
            File("src/main/$relativePath"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate $relativePath")
    }
}
