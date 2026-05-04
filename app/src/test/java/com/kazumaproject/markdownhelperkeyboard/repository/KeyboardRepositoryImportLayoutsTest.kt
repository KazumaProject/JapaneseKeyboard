package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.ImportableKeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.ImportableKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [KeyboardRepository.importLayouts] が新 [ImportableKeyboardLayout] を受け取り、
 * 既存名重複時に名前を変更し、spacers が空でもクラッシュしないことを保証する。
 */
class KeyboardRepositoryImportLayoutsTest {

    private val dao: KeyboardLayoutDao = mock()
    private val repository = KeyboardRepository(dao)

    @Test
    fun importLayouts_emptySpacers_doesNotCrash() = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(10)
        whenever(dao.findLayoutByName(any())).thenReturn(null)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)

        val importable = ImportableKeyboardLayout(
            layout = layout(name = "MyKeyboard", stableId = "stable-id-1"),
            keysWithFlicks = listOf(keyWithFlicks("k1", "あ")),
            spacers = emptyList()
        )

        // ここで [fullLayout.spacers.map { ... }] 由来の NPE が出ない事が
        // 後方互換修正の最重要回帰テスト。
        repository.importLayouts(listOf(importable))

        val layoutCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).insertFullKeyboardLayout(
            layoutCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        val inserted = layoutCaptor.firstValue
        assertEquals("MyKeyboard", inserted.name)
        assertEquals(0L, inserted.layoutId)
        assertEquals(11, inserted.sortOrder)
        assertEquals("stable-id-1", inserted.stableId)
        assertNotNull(inserted.createdAt)
    }

    @Test
    fun importLayouts_duplicateName_isRenamedWithCounterSuffix() = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(0)

        // 1 回目: name = "MyKeyboard"  -> 衝突
        // 2 回目: name = "MyKeyboard (1)" -> 衝突無し
        whenever(dao.findLayoutByName("MyKeyboard")).thenReturn(
            CustomKeyboardLayout(
                layoutId = 99,
                name = "MyKeyboard",
                columnCount = 5,
                rowCount = 4,
                stableId = "existing"
            )
        )
        whenever(dao.findLayoutByName("MyKeyboard (1)")).thenReturn(null)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)

        val importable = ImportableKeyboardLayout(
            layout = layout(name = "MyKeyboard", stableId = "stable-id-2"),
            keysWithFlicks = emptyList(),
            spacers = emptyList()
        )

        repository.importLayouts(listOf(importable))

        val layoutCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).insertFullKeyboardLayout(
            layoutCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        assertEquals("MyKeyboard (1)", layoutCaptor.firstValue.name)
    }

    @Test
    fun importLayouts_blankStableId_isReplacedWithRandom() = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(0)
        whenever(dao.findLayoutByName(any())).thenReturn(null)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)

        val importable = ImportableKeyboardLayout(
            layout = layout(name = "MyKeyboard", stableId = ""),
            keysWithFlicks = emptyList(),
            spacers = emptyList()
        )

        repository.importLayouts(listOf(importable))

        val layoutCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).insertFullKeyboardLayout(
            layoutCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        val inserted = layoutCaptor.firstValue
        // blank stableId なら自動で UUID が割当てられる
        assert(inserted.stableId.isNotBlank())
    }

    @Test
    fun importLayouts_legacyNumericIds_areResetBeforeDaoInsert() = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(0)
        whenever(dao.findLayoutByName(any())).thenReturn(null)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)

        val importable = ImportableKeyboardLayout(
            layout = layout(name = "LegacyKeyboard", stableId = "legacy-stable").copy(layoutId = 14),
            keysWithFlicks = listOf(
                keyWithFlicks("legacy-key", "あ").copy(
                    key = keyWithFlicks("legacy-key", "あ").key.copy(
                        keyId = 99,
                        ownerLayoutId = 14
                    ),
                    flicks = listOf(
                        FlickMapping(
                            ownerKeyId = 99,
                            stateIndex = 0,
                            flickDirection = FlickDirection.TAP,
                            actionType = "DELETE",
                            actionValue = null
                        )
                    )
                )
            ),
            spacers = emptyList()
        )

        val result = repository.importLayouts(listOf(importable))

        assertTrue(result is KeyboardLayoutImportResult.Success)
        val keysCaptor = argumentCaptor<List<KeyDefinition>>()
        val flicksMapCaptor = argumentCaptor<Map<String, List<FlickMapping>>>()
        verify(dao).insertFullKeyboardLayout(
            any(),
            keysCaptor.capture(),
            flicksMapCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
        assertEquals(0L, keysCaptor.firstValue.single().keyId)
        assertEquals(0L, keysCaptor.firstValue.single().ownerLayoutId)
        assertEquals(0L, flicksMapCaptor.firstValue.getValue("legacy-key").single().ownerKeyId)
    }

    // -----------------------------
    // helpers
    // -----------------------------

    private fun layout(name: String, stableId: String): CustomKeyboardLayout {
        return CustomKeyboardLayout(
            layoutId = 0,
            name = name,
            columnCount = 5,
            rowCount = 4,
            isRomaji = false,
            isDirectMode = false,
            createdAt = 0L,
            sortOrder = 0,
            stableId = stableId
        )
    }

    private fun keyWithFlicks(identifier: String, label: String): ImportableKeyWithFlicks {
        return ImportableKeyWithFlicks(
            key = KeyDefinition(
                keyId = 0,
                ownerLayoutId = 0,
                label = label,
                row = 0,
                column = 0,
                keyType = KeyType.NORMAL,
                isSpecialKey = false,
                drawableResId = null,
                keyIdentifier = identifier,
                action = null
            ),
            flicks = emptyList(),
            circularFlicks = emptyList(),
            twoStepFlicks = emptyList(),
            longPressFlicks = emptyList(),
            twoStepLongPressFlicks = emptyList()
        )
    }
}
