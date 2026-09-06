package com.kazumaproject.markdownhelperkeyboard.setting_activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsNavigationLayoutContractTest {

    @Test
    fun activityLayoutAnchorsNavHostToTheDirectNavigationContainerChild() {
        val layout = projectFile(
            "app/src/main/res/layout/activity_main.xml",
            "src/main/res/layout/activity_main.xml",
        ).readText()

        val navigationContainerIndex = layout.indexOf("android:id=\"@+id/nav_view_container\"")
        val navHostIndex = layout.indexOf(
            "android:id=\"@+id/nav_host_fragment_activity_main\"",
        )

        assertTrue(navigationContainerIndex >= 0)
        assertTrue(navHostIndex > navigationContainerIndex)
        assertTrue(
            layout.contains(
                "app:layout_constraintBottom_toTopOf=\"@id/nav_view_container\"",
            ),
        )
        assertTrue(
            layout.contains("app:layout_constraintBottom_toBottomOf=\"parent\""),
        )
        assertFalse(layout.contains("android:id=\"@+id/nav_view\""))
    }

    @Test
    fun mainActivityKeepsLegacyNavigationInflationLazyAndDoesNotUseConstraintSet() {
        val source = mainFile("MainActivity.kt").readText()

        assertTrue(source.contains("binding.navViewContainer.visibility = View.GONE"))
        assertTrue(source.contains("binding.navViewContainer.visibility = View.VISIBLE"))
        assertTrue(source.contains("R.layout.view_legacy_bottom_navigation"))
        assertTrue(source.contains("binding.navViewContainer.addView(navView)"))
        assertFalse(source.contains("ConstraintSet"))
        assertFalse(source.contains("updateNavHostBottomConstraint"))
    }

    @Test
    fun navigationAreaConsumersObserveTheContainerInsteadOfTheNestedBottomNavigationView() {
        val exactBottomNavigationId = Regex("""R\.id\.nav_view(?![A-Za-z0-9_])""")
        listOf(
            "ui/candidate_view_height_setting/CandidateViewHeightSettingFragment.kt",
            "ui/candidate_view_height_landscape_setting/CandidateHeightLandscapeSettingFragment.kt",
            "../physical_keyboard/shortcut/ui/PhysicalKeyboardShortcutEditFragment.kt",
            "../physical_keyboard/shortcut/ui/PhysicalKeyboardShortcutListFragment.kt",
        ).forEach { relativePath ->
            val source = mainFile(relativePath).readText()

            assertTrue(
                "$relativePath must observe nav_view_container",
                source.contains("R.id.nav_view_container"),
            )
            assertFalse(
                "$relativePath must not treat nav_view as the navigation area",
                exactBottomNavigationId.containsMatchIn(source),
            )
        }
    }

    @Test
    fun candidatePreviewDoesNotRewriteTheNavHostConstraint() {
        listOf(
            "ui/candidate_view_height_setting/CandidateViewHeightSettingFragment.kt",
            "ui/candidate_view_height_landscape_setting/CandidateHeightLandscapeSettingFragment.kt",
        ).forEach { relativePath ->
            val source = mainFile(relativePath).readText()

            assertFalse(
                "$relativePath must not mutate the activity ConstraintLayout",
                source.contains("ConstraintSet"),
            )
            assertTrue(source.contains("R.id.nav_view_container"))
        }
    }

    private fun mainFile(relativePath: String): File {
        val sourcePath = "java/com/kazumaproject/markdownhelperkeyboard/setting_activity/$relativePath"
        return projectFile("app/src/main/$sourcePath", "src/main/$sourcePath")
    }

    private fun projectFile(vararg candidates: String): File {
        return candidates.asSequence()
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Unable to locate any of ${candidates.toList()}")
    }
}
