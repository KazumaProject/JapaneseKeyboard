package com.kazumaproject.markdownhelperkeyboard.ime_service.editor

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.TypeNullInputBehaviorSetting
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorEnterPolicyTest {
    @Test fun recordedGboardMatrix() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/enter_parity/gboard.tsv"))
        val rows = stream.bufferedReader().use { it.readLines() }.drop(1)
        // 22 input types × 8 actions × 4 multiline states × 2 suppression states × 4 custom actions,
        // plus 16 action/suppression combinations × 33 independent probes and one field regression.
        assertEquals("Incomplete Gboard reference", 22 * 8 * 4 * 2 * 4 + 16 * 33 + 1, rows.size)
        assertEquals("Duplicate case IDs", rows.size, rows.map { it.substringBefore('\t') }.toSet().size)
        rows.forEach { line ->
            val parts = line.split('\t')
            val info = EditorInfo().apply {
                inputType = parts[1].toInt()
                imeOptions = parts[2].toLong().toInt()
                actionLabel = parts[3].takeUnless { it == "\\N" }
                actionId = parts[4].toInt()
                hintText = parts.getOrNull(6)?.takeUnless { it == "\\N" }
                fieldName = parts.getOrNull(7)?.takeUnless { it == "\\N" }
                privateImeOptions = parts.getOrNull(8)?.takeUnless { it == "\\N" }
            }
            val expected = when (parts[5]) {
                "enter" -> EditorEnterAction.Enter
                "newline" -> EditorEnterAction.Newline
                else -> EditorEnterAction.Action(parts[5].toInt())
            }
            assertEquals(parts[0], expected, EditorEnterPolicy.resolve(info))
        }
    }

    @Test fun mapsNoEnterActionDoesNotDispatchDone() {
        val info = EditorInfo().apply { inputType = 0x264001; imeOptions = 0x40000006 }
        val keys = mutableListOf<Int>()
        val actions = mutableListOf<Int>()
        EditorEnterPolicy.dispatch(EditorEnterPolicy.resolve(info), { keys += it }, { actions += it }, { fail("Unexpected newline commit") })
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), keys)
        assertTrue(actions.isEmpty())
        assertEquals("改行", EditorEnterPolicy.label(EditorEnterPolicy.resolve(info), true))
    }

    @Test fun fieldSwitchesDoNotRetainPreviousActionOrSendTwice() {
        val actions = mutableListOf<Int>()
        val keys = mutableListOf<Int>()
        listOf(3, 0x40000006, 5, 6).forEach { options ->
            EditorEnterPolicy.dispatch(EditorEnterPolicy.resolve(EditorInfo().apply { imeOptions = options }),
                { keys += it }, { actions += it }, { fail("Unexpected newline commit") })
        }
        assertEquals(listOf(3, 5, 6), actions)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), keys)
    }

    @Test fun builtInDynamicKeyStatesDescribeTheDispatchedAction() {
        val states = checkNotNull(com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
            .createNumberLayout().keys.single { it.keyId == "enter_key" }.dynamicStates)
        val labels = mapOf(2 to "実行", 3 to "検索", 4 to "送信", 5 to "次", 6 to "確定", 7 to "前へ")
        labels.forEach { (id, label) ->
            val action = EditorEnterAction.Action(id)
            assertEquals(label, states[EditorEnterPolicy.keyStateIndex(action)].label)
        }
        assertEquals("改行", states[EditorEnterPolicy.keyStateIndex(EditorEnterAction.Enter)].label)
    }

    @Test fun shortMessageCommitsOnlyNewlineWhenActionIsSuppressed() {
        val commits = mutableListOf<String>()
        val info = EditorInfo().apply { inputType = 0x41; imeOptions = 0x40000004 }
        EditorEnterPolicy.dispatch(EditorEnterPolicy.resolve(info),
            { fail("Must not send key events") }, { fail("Must not send SEND") }, { commits += "\n" })
        assertEquals(listOf("\n"), commits)
    }

    @Test fun typeNullDefaultEnterIsSeparateFromExplicitDirectSettings() {
        assertTrue(EditorEnterPolicy.usesEditorActionForDefaultTypeNull(0, TypeNullInputBehaviorSetting.DEFAULT, false))
        assertFalse(EditorEnterPolicy.usesEditorActionForDefaultTypeNull(1, TypeNullInputBehaviorSetting.DEFAULT, false))
        assertFalse(EditorEnterPolicy.usesEditorActionForDefaultTypeNull(null, TypeNullInputBehaviorSetting.DEFAULT, false))
        assertFalse(EditorEnterPolicy.usesEditorActionForDefaultTypeNull(0, TypeNullInputBehaviorSetting.DIRECT_COMMIT, false))
        assertFalse(EditorEnterPolicy.usesEditorActionForDefaultTypeNull(0, TypeNullInputBehaviorSetting.DEFAULT, true))
        assertFalse(EditorEnterPolicy.usesEditorActionForDefaultTypeNull(0, TypeNullInputBehaviorSetting.COMPOSING_TEXT, false))
    }

    @Test fun absentEditorAndUnknownActionDoNotGuessAnAction() {
        assertEquals(EditorEnterAction.Enter, EditorEnterPolicy.resolve(null))
        assertEquals(EditorEnterAction.Enter, EditorEnterPolicy.resolve(EditorInfo().apply { imeOptions = 255 }))
    }
}
