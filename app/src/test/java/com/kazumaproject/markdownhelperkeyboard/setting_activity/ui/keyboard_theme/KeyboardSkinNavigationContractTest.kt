package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeyboardSkinNavigationContractTest {

    @Test
    fun embeddedThemeTabNavigatesDirectlyToSkinPickerDestination() {
        val source = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/setting_activity/ui/keyboard_theme/KeyboardThemeFragment.kt"
        ).readText()

        assertTrue(source.contains("navigate(R.id.keyboardSkinPickerFragment)"))
        assertFalse(
            source.contains("R.id.action_keyboardThemeFragment_to_keyboardSkinPickerFragment")
        )
    }

    @Test
    fun skinPickerIsRegisteredAsANavigationDestination() {
        val graph = mainFile("res/navigation/mobile_navigation.xml").readText()

        assertTrue(graph.contains("android:id=\"@+id/keyboardSkinPickerFragment\""))
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
