package com.kazumaproject.markdownhelperkeyboard.setting_activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedActionBarNavigationContractTest {

    @Test
    fun fragmentTeardownDoesNotOverrideTheDestinationUpIndicator() {
        listOf(
            "ui/keyboard_size_setting/KeyboardSizeSettingFragment.kt",
            "ui/keyboard_size_landscape_setting/KeyboardSizeLandscapeFragment.kt",
        ).forEach { relativePath ->
            val source = mainFile(relativePath).readText()
            val onDestroyViewBody = source.substringAfter("override fun onDestroyView()")
                .substringBeforeLast("\n}")

            assertFalse(
                "$relativePath must not overwrite the shared ActionBar during teardown",
                onDestroyViewBody.contains("supportActionBar"),
            )
        }
    }

    @Test
    fun mainActivityOwnsSharedActionBarVisibilityForCustomToolbarDestinations() {
        val source = mainFile("MainActivity.kt").readText()

        assertTrue(source.contains("private val destinationsWithOwnToolbar = setOf("))
        assertTrue(source.contains("R.id.candidateViewHeightSettingFragment"))
        assertTrue(source.contains("R.id.candidateHeightLandscapeSettingFragment"))
        assertTrue(source.contains("R.id.shortcutToolbarSizeSettingFragment"))
        assertTrue(source.contains("updateSharedActionBarVisibility(destination.id)"))
        assertTrue(source.contains("destinationId in destinationsWithOwnToolbar"))
        assertTrue(source.contains("supportActionBar?.hide()"))
        assertTrue(source.contains("supportActionBar?.show()"))
    }

    @Test
    fun customToolbarFragmentsDoNotToggleTheSharedActionBar() {
        listOf(
            "ui/candidate_view_height_setting/CandidateViewHeightSettingFragment.kt",
            "ui/candidate_view_height_landscape_setting/CandidateHeightLandscapeSettingFragment.kt",
            "ui/shortcut_toolbar_size/ShortcutToolbarSizeSettingFragment.kt",
        ).forEach { relativePath ->
            val source = mainFile(relativePath).readText()

            assertFalse(
                "$relativePath must leave shared ActionBar visibility to MainActivity",
                source.contains("supportActionBar?.hide()") ||
                    source.contains("supportActionBar?.show()"),
            )
        }
    }

    private fun mainFile(relativePath: String): File {
        val sourcePath = "java/com/kazumaproject/markdownhelperkeyboard/setting_activity/$relativePath"
        val candidates = listOf(
            File("app/src/main/$sourcePath"),
            File("src/main/$sourcePath"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate $sourcePath")
    }
}
