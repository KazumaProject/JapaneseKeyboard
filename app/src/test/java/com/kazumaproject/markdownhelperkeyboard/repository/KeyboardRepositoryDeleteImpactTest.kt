package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * `KeyboardRepository.getDeleteImpactForLayout` / `findMoveToCustomKeyboardReferences` の
 * 振る舞いをロックする。
 *
 * - F: 参照ありを正しく検出
 * - G: 参照なしを正しく検出
 * - flick / circular flick の MoveToCustomKeyboard 参照も検出
 */
class KeyboardRepositoryDeleteImpactTest {

    private val dao: KeyboardLayoutDao = mock()
    private val repository = KeyboardRepository(dao)

    @Test
    fun getDeleteImpactForLayout_referencedByTapAction_isDetected() = runBlocking {
        val targetStableId = "target-stable"
        val target = layout(layoutId = 10, stableId = targetStableId, name = "Target")
        val source = layout(layoutId = 20, stableId = "source-stable", name = "Source")

        val sourceFull = FullKeyboardLayout(
            layout = source,
            keysWithFlicks = listOf(
                keyWithMoveToCustomKeyboardTapAction(
                    keyId = 200,
                    keyIdentifier = "key-source-1",
                    label = "→A",
                    targetStableId = targetStableId
                )
            ),
            spacers = emptyList()
        )
        val targetFull = FullKeyboardLayout(target, emptyList(), emptyList())

        whenever(dao.getFullLayoutOneShot(10L)).thenReturn(targetFull)
        whenever(dao.getAllFullLayoutsOneShot()).thenReturn(listOf(targetFull, sourceFull))

        val impact = repository.getDeleteImpactForLayout(10L)
        assertTrue(impact.hasReferences)
        assertEquals(1, impact.references.size)
        val ref = impact.references.single()
        assertEquals(20L, ref.sourceLayoutId)
        assertEquals("Source", ref.sourceLayoutName)
        assertEquals("key-source-1", ref.sourceKeyIdentifier)
        assertEquals("→A", ref.sourceKeyLabel)
        assertEquals(targetStableId, ref.targetStableId)
    }

    @Test
    fun getDeleteImpactForLayout_referencedByFlickMapping_isDetected() = runBlocking {
        val targetStableId = "target-via-flick"
        val target = layout(layoutId = 11, stableId = targetStableId, name = "Target")
        val source = layout(layoutId = 21, stableId = "source-stable", name = "Source")

        val sourceFull = FullKeyboardLayout(
            layout = source,
            keysWithFlicks = listOf(
                KeyWithFlicks(
                    key = KeyDefinition(
                        keyId = 210,
                        ownerLayoutId = 21,
                        label = "X",
                        row = 0,
                        column = 0,
                        keyType = KeyType.PETAL_FLICK,
                        isSpecialKey = false,
                        keyIdentifier = "key-source-flick",
                        action = null
                    ),
                    flicks = listOf(
                        FlickMapping(
                            ownerKeyId = 210,
                            stateIndex = 0,
                            flickDirection = FlickDirection.UP,
                            actionType = "MoveToCustomKeyboard",
                            actionValue = targetStableId
                        )
                    ),
                    circularFlicks = emptyList(),
                    twoStepFlicks = emptyList(),
                    longPressFlicks = emptyList(),
                    twoStepLongPressFlicks = emptyList()
                )
            ),
            spacers = emptyList()
        )
        val targetFull = FullKeyboardLayout(target, emptyList(), emptyList())

        whenever(dao.getFullLayoutOneShot(11L)).thenReturn(targetFull)
        whenever(dao.getAllFullLayoutsOneShot()).thenReturn(listOf(targetFull, sourceFull))

        val impact = repository.getDeleteImpactForLayout(11L)
        assertTrue(impact.hasReferences)
        assertEquals("key-source-flick", impact.references.single().sourceKeyIdentifier)
    }

    @Test
    fun getDeleteImpactForLayout_referencedByCircularFlick_isDetected() = runBlocking {
        val targetStableId = "target-via-circular"
        val target = layout(layoutId = 12, stableId = targetStableId, name = "Target")
        val source = layout(layoutId = 22, stableId = "source-stable", name = "Source")

        val sourceFull = FullKeyboardLayout(
            layout = source,
            keysWithFlicks = listOf(
                KeyWithFlicks(
                    key = KeyDefinition(
                        keyId = 220,
                        ownerLayoutId = 22,
                        label = "Y",
                        row = 0,
                        column = 0,
                        keyType = KeyType.CIRCULAR_FLICK,
                        isSpecialKey = false,
                        keyIdentifier = "key-source-circ",
                        action = null
                    ),
                    flicks = emptyList(),
                    circularFlicks = listOf(
                        CircularFlickMapping(
                            ownerKeyId = 220,
                            stateIndex = 0,
                            circularDirection =
                                com.kazumaproject.custom_keyboard.data.CircularFlickDirection.SLOT_0,
                            actionType = "MoveToCustomKeyboard",
                            actionValue = targetStableId
                        )
                    ),
                    twoStepFlicks = emptyList(),
                    longPressFlicks = emptyList(),
                    twoStepLongPressFlicks = emptyList()
                )
            ),
            spacers = emptyList()
        )
        val targetFull = FullKeyboardLayout(target, emptyList(), emptyList())

        whenever(dao.getFullLayoutOneShot(12L)).thenReturn(targetFull)
        whenever(dao.getAllFullLayoutsOneShot()).thenReturn(listOf(targetFull, sourceFull))

        val impact = repository.getDeleteImpactForLayout(12L)
        assertTrue(impact.hasReferences)
        assertEquals("key-source-circ", impact.references.single().sourceKeyIdentifier)
    }

    @Test
    fun getDeleteImpactForLayout_noReferences_returnsEmpty() = runBlocking {
        val target = layout(layoutId = 13, stableId = "target-no-ref", name = "Target")
        val targetFull = FullKeyboardLayout(target, emptyList(), emptyList())
        val unrelated = FullKeyboardLayout(
            layout = layout(layoutId = 23, stableId = "stable-unrelated", name = "Unrelated"),
            keysWithFlicks = listOf(
                keyWithTextAction(
                    keyId = 230,
                    keyIdentifier = "k1",
                    label = "あ"
                )
            ),
            spacers = emptyList()
        )

        whenever(dao.getFullLayoutOneShot(13L)).thenReturn(targetFull)
        whenever(dao.getAllFullLayoutsOneShot()).thenReturn(listOf(targetFull, unrelated))

        val impact = repository.getDeleteImpactForLayout(13L)
        assertFalse(impact.hasReferences)
        assertEquals("Target", impact.layoutName)
        assertEquals("target-no-ref", impact.stableId)
    }

    @Test
    fun getDeleteImpactForLayout_targetWithBlankStableId_doesNotScan() = runBlocking {
        val target = layout(layoutId = 14, stableId = "", name = "BadTarget")
        val targetFull = FullKeyboardLayout(target, emptyList(), emptyList())
        whenever(dao.getFullLayoutOneShot(14L)).thenReturn(targetFull)

        val impact = repository.getDeleteImpactForLayout(14L)
        assertFalse(impact.hasReferences)
    }

    @Test
    fun getDeleteImpactForLayout_layoutNotFound_returnsEmptyImpact() = runBlocking {
        whenever(dao.getFullLayoutOneShot(999L)).thenReturn(null)

        val impact = repository.getDeleteImpactForLayout(999L)
        assertFalse(impact.hasReferences)
        assertEquals(999L, impact.layoutId)
        assertEquals("", impact.stableId)
    }

    // ---------------------------------------------------------
    // helpers
    // ---------------------------------------------------------

    private fun layout(layoutId: Long, stableId: String, name: String): CustomKeyboardLayout {
        return CustomKeyboardLayout(
            layoutId = layoutId,
            name = name,
            columnCount = 5,
            rowCount = 4,
            stableId = stableId
        )
    }

    private fun keyWithMoveToCustomKeyboardTapAction(
        keyId: Long,
        keyIdentifier: String,
        label: String,
        targetStableId: String
    ): KeyWithFlicks {
        return KeyWithFlicks(
            key = KeyDefinition(
                keyId = keyId,
                ownerLayoutId = 0,
                label = label,
                row = 0,
                column = 0,
                keyType = KeyType.NORMAL,
                isSpecialKey = true,
                keyIdentifier = keyIdentifier,
                // KeyActionMapper の MOVE_TO_CUSTOM_KEYBOARD_PREFIX に合わせる
                action = "MoveToCustomKeyboard:$targetStableId"
            ),
            flicks = emptyList(),
            circularFlicks = emptyList(),
            twoStepFlicks = emptyList(),
            longPressFlicks = emptyList(),
            twoStepLongPressFlicks = emptyList()
        )
    }

    private fun keyWithTextAction(
        keyId: Long,
        keyIdentifier: String,
        label: String
    ): KeyWithFlicks {
        return KeyWithFlicks(
            key = KeyDefinition(
                keyId = keyId,
                ownerLayoutId = 0,
                label = label,
                row = 0,
                column = 0,
                keyType = KeyType.NORMAL,
                isSpecialKey = false,
                keyIdentifier = keyIdentifier,
                action = "Text:$label"
            ),
            flicks = emptyList(),
            circularFlicks = emptyList(),
            twoStepFlicks = emptyList(),
            longPressFlicks = emptyList(),
            twoStepLongPressFlicks = emptyList()
        )
    }
}
