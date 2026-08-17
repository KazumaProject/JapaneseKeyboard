package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceKeyboardSkinRuntimeSyncContractTest {

    @Test
    fun skinChangesRefreshSurvivingCandidateTabs() {
        val source = imeServiceSource()
        val syncBody = source.substringAfter("private fun syncKeyboardSkinPreferences()")
            .substringBefore("private fun syncRuntimeInputPreferences()")

        assertTrue(syncBody.contains("keyboardSkinMode = nextSkin.preferenceValue"))
        assertTrue(syncBody.contains("applyKeyboardSkinToCandidateTabs"))
        assertTrue(syncBody.contains("applyKeyboardSkinThemeToCandidateAdapters"))
    }

    @Test
    fun inputViewRecreationReappliesSkinEvenWhenCachedValueAlreadyMatches() {
        val source = imeServiceSource()
        val syncBody = source.substringAfter("private fun syncKeyboardSkinPreferences()")
            .substringBefore("private fun syncRuntimeInputPreferences()")

        assertTrue(syncBody.contains("newly inflated views still need styling"))
        assertTrue(!syncBody.contains("if (!changed) return"))
    }

    @Test
    fun skinChangesAreObservedAndReloadedWhenInputViewReturns() {
        val source = imeServiceSource()
        val runtimeKeys = source.substringAfter("private val keyboardSkinPreferenceKeys = setOf(")
            .substringBefore(")")
        val startInputView = source.substringAfter(
            "override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean)"
        ).substringBefore("override fun onFinishInputView")

        assertTrue(runtimeKeys.contains("AppPreference.KEYBOARD_SKIN_KEY"))
        assertTrue(runtimeKeys.contains("AppPreference.KEYBOARD_SKIN_MOTION_KEY"))
        assertTrue(startInputView.contains("syncKeyboardSkinPreferences()"))
    }

    @Test
    fun returningToDefaultClearsPreviousTabBackgrounds() {
        val source = imeServiceSource()
        val childSkinBody = source.substringAfter("private fun updateCandidateTabChildSkins(")
            .substringBefore("private fun getCandidateTabDisplayName")

        assertTrue(childSkinBody.contains("KeyboardSkinViewStyler.clearTransientStyle(tabView)"))
        assertTrue(childSkinBody.contains("tabView.background = null"))
        assertTrue(childSkinBody.contains("if (skin != KeyboardSkinId.DEFAULT)"))
    }

    @Test
    fun candidateTabsUseFlatChromeInsteadOfKeycapGeometry() {
        val source = imeServiceSource()
        val childSkinBody = source.substringAfter("private fun updateCandidateTabChildSkins(")
            .substringBefore("private fun getCandidateTabDisplayName")

        assertTrue(childSkinBody.contains("KeyboardSkinViewStyler.applyFlatControl("))
        assertTrue(childSkinBody.contains("tintContent = false"))
        assertTrue(!childSkinBody.contains("KeyboardSkinViewStyler.applyKey("))
    }

    @Test
    fun shortcutIconsUseFlatChromeInsteadOfKeycapGeometry() {
        val shortcutAdapter = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/ShortcutAdapter.kt"
        ).readText().substringAfter("override fun onBindViewHolder(")
            .substringBefore("fun setShortcutToolbarSize(")
        val suggestionAdapter = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt"
        ).readText().substringAfter("private fun applyShortcutSkin(")
            .substringBefore("private fun ShortcutType.resolveShortcutIconResId")

        listOf(shortcutAdapter, suggestionAdapter).forEach { body ->
            assertTrue(body.contains("KeyboardSkinViewStyler.applyFlatControl("))
            assertTrue(!body.contains("KeyboardSkinViewStyler.applyKey("))
        }
    }

    private fun imeServiceSource(): String = mainFile(
        "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
    ).readText()

    private fun mainFile(relativePath: String): File {
        val candidates = listOf(
            File("app/src/main/$relativePath"),
            File("src/main/$relativePath"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate $relativePath")
    }
}
