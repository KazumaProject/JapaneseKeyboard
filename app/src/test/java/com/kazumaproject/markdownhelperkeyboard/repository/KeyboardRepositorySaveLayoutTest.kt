package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyIconRef
import com.kazumaproject.custom_keyboard.data.KeyIconType
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * KeyboardRepository.saveLayout の長期的な不変条件をロックする回帰テスト。
 *
 * このテストが守るのは以下の性質。これが壊れると
 * `KeyAction.MoveToCustomKeyboard(stableId)` が「削除済みのカスタムキーボード」になる。
 *
 * - 既存レイアウト保存で stableId が変わらない
 * - 既存レイアウト保存で createdAt / sortOrder / layoutId が変わらない
 * - 既存レイアウト保存で親 (keyboard_layouts) row は delete されず @Update される
 * - 既存更新対象が DB に無い場合は黙って新規作成にフォールバックしない
 *   (= 新しい stableId を勝手に割り当てない)
 */
class KeyboardRepositorySaveLayoutTest {

    private val dao: KeyboardLayoutDao = mock()
    private val repository = KeyboardRepository(dao)

    // ---------------------------------------------------------
    // A. 既存レイアウト保存で stableId が変わらない
    // E. parent identity 維持
    // ---------------------------------------------------------
    @Test
    fun saveLayout_existingId_keepsStableIdAndIdentity(): Unit = runBlocking {
        val existingStableId = "stable-a"
        val existingCreatedAt = 1_700_000_000_000L
        val existingSortOrder = 7

        whenever(dao.getFullLayoutOneShot(1L)).thenReturn(
            fullLayout(
                layoutId = 1,
                stableId = existingStableId,
                createdAt = existingCreatedAt,
                sortOrder = existingSortOrder,
                name = "OldName"
            )
        )

        val ui = simpleLayout(columns = 5, rows = 4)
        repository.saveLayout(ui, name = "NewName", id = 1L)

        // updateFullKeyboardLayoutKeepingIdentity が呼ばれ、
        // identity 4 値が維持されていることを検証。
        val parentCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).updateFullKeyboardLayoutKeepingIdentity(
            parentCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        val saved = parentCaptor.firstValue
        assertEquals(1L, saved.layoutId)
        assertEquals(existingStableId, saved.stableId)
        assertEquals(existingCreatedAt, saved.createdAt)
        assertEquals(existingSortOrder, saved.sortOrder)
        // 編集可能フィールドは更新される
        assertEquals("NewName", saved.name)

        // 親レイアウトを delete しない
        verify(dao, never()).deleteLayout(any())
        // 既存更新では insertFullKeyboardLayout は呼ばれない (= delete + insert で identity を壊さない)
        verify(dao, never()).insertFullKeyboardLayout(
            any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    // ---------------------------------------------------------
    // B. 既存レイアウト保存で sortOrder が変わらない (E と同じケースで保証)
    // ---------------------------------------------------------
    @Test
    fun saveLayout_existingId_doesNotMoveLayoutToTopOfList(): Unit = runBlocking {
        whenever(dao.getFullLayoutOneShot(2L)).thenReturn(
            fullLayout(
                layoutId = 2,
                stableId = "stable-b",
                createdAt = 1_700_000_000_000L,
                sortOrder = 2,
                name = "B"
            )
        )

        repository.saveLayout(simpleLayout(), name = "B", id = 2L)

        val parentCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).updateFullKeyboardLayoutKeepingIdentity(
            parentCaptor.capture(),
            any(), any(), any(), any(), any(), any(), any()
        )
        // nextTopSortOrder() で再採番されたら 3 などに変わる。
        // identity 維持なので 2 のまま。
        assertEquals(2, parentCaptor.firstValue.sortOrder)
    }

    // ---------------------------------------------------------
    // C. MoveToCustomKeyboard が保存後も有効
    // ---------------------------------------------------------
    @Test
    fun saveLayout_targetLayoutEdit_preservesStableIdSoMoveToCustomKeyboardStaysValid(): Unit = runBlocking {
        val targetStableId = "target-stable"
        whenever(dao.getFullLayoutOneShot(10L)).thenReturn(
            fullLayout(
                layoutId = 10,
                stableId = targetStableId,
                createdAt = 1L,
                sortOrder = 1,
                name = "Target"
            )
        )

        repository.saveLayout(simpleLayout(), name = "Target", id = 10L)

        val parentCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).updateFullKeyboardLayoutKeepingIdentity(
            parentCaptor.capture(),
            any(), any(), any(), any(), any(), any(), any()
        )
        // Source 側の MoveToCustomKeyboard("target-stable") を解決するためには
        // Target.stableId が "target-stable" のままでなければならない。
        assertEquals(targetStableId, parentCaptor.firstValue.stableId)
    }

    // ---------------------------------------------------------
    // I. 既存更新対象が存在しない場合
    // ---------------------------------------------------------
    @Test
    fun saveLayout_existingIdNotFound_throwsLayoutNotFoundException(): Unit = runBlocking {
        whenever(dao.getFullLayoutOneShot(999L)).thenReturn(null)

        try {
            repository.saveLayout(simpleLayout(), name = "ghost", id = 999L)
            fail("Expected LayoutNotFoundException")
        } catch (e: LayoutNotFoundException) {
            assertEquals(999L, e.layoutId)
        }

        // 黙って新規作成にフォールバックしないことを検証
        verify(dao, never()).insertFullKeyboardLayout(
            any(), any(), any(), any(), any(), any(), any(), any()
        )
        verify(dao, never()).updateFullKeyboardLayoutKeepingIdentity(
            any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    // ---------------------------------------------------------
    // 新規作成 (id == null) で stableId は新規生成され、insertFullKeyboardLayout が呼ばれる
    // ---------------------------------------------------------
    @Test
    fun saveLayout_newLayout_generatesUniqueStableIdAndInserts(): Unit = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(3)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)
        whenever(
            dao.insertFullKeyboardLayout(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        ).thenReturn(42L)

        val newLayoutId = repository.saveLayout(simpleLayout(), name = "fresh", id = null)
        assertEquals(42L, newLayoutId)

        val parentCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).insertFullKeyboardLayout(
            parentCaptor.capture(),
            any(), any(), any(), any(), any(), any(), any()
        )
        val inserted = parentCaptor.firstValue
        assertEquals(0L, inserted.layoutId) // AUTOINCREMENT 用
        assertTrue("stableId must be non-blank for new layout", inserted.stableId.isNotBlank())
        assertEquals(4, inserted.sortOrder) // max(3) + 1
        // 既存更新は呼ばれない
        verify(dao, never()).updateFullKeyboardLayoutKeepingIdentity(
            any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun saveLayout_numberUsageMode_clearsOtherNumberLayouts(): Unit = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(3)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)
        whenever(
            dao.insertFullKeyboardLayout(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        ).thenReturn(42L)

        repository.saveLayout(
            simpleLayout().copy(usageMode = KeyboardLayoutUsageMode.Number),
            name = "number",
            id = null
        )

        verify(dao).clearNumberUsageModeExcept(42L)
    }

    @Test
    fun setCurrentLayoutUsageMode_number_isExclusive(): Unit = runBlocking {
        repository.setCurrentLayoutUsageMode(8L, KeyboardLayoutUsageMode.Number)

        verify(dao).setLayoutUsageModeExclusive(8L, KeyboardLayoutUsageMode.Number)
    }

    @Test
    fun saveLayout_specialKeyPersistsIconOverrideStringsWithoutDrawableResId(): Unit = runBlocking {
        whenever(dao.getMaxSortOrder()).thenReturn(0)
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)
        whenever(
            dao.insertFullKeyboardLayout(any(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(42L)

        val key = KeyData(
            label = "",
            row = 0,
            column = 0,
            isFlickable = false,
            keyType = KeyType.NORMAL,
            isSpecialKey = true,
            keyId = "special-1",
            action = KeyAction.Delete,
            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
            icon = KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "keyboard_24px")
        )
        val layout = KeyboardLayout(keys = listOf(key), flickKeyMaps = emptyMap(), columnCount = 5, rowCount = 4)

        repository.saveLayout(layout, name = "icons", id = null)

        val keysCaptor = argumentCaptor<List<KeyDefinition>>()
        verify(dao).insertFullKeyboardLayout(
            any(),
            keysCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
        val savedKey = keysCaptor.firstValue.single()
        assertNull(savedKey.drawableResId)
        assertEquals("DRAWABLE_RESOURCE_NAME", savedKey.iconType)
        assertEquals("keyboard_24px", savedKey.iconValue)
    }

    @Test
    fun saveLayout_existingIdWithBlankStableId_repairsStableIdButNeverChangesValidOne(): Unit = runBlocking {
        // 旧データで stableId が blank だった既存 row。今回の保存で blank → 新 UUID に修復される。
        whenever(dao.getFullLayoutOneShot(5L)).thenReturn(
            fullLayout(
                layoutId = 5,
                stableId = "",
                createdAt = 100L,
                sortOrder = 1,
                name = "blank-id"
            )
        )
        whenever(dao.findLayoutByStableId(any())).thenReturn(null)

        repository.saveLayout(simpleLayout(), name = "blank-id", id = 5L)

        val parentCaptor = argumentCaptor<CustomKeyboardLayout>()
        verify(dao).updateFullKeyboardLayoutKeepingIdentity(
            parentCaptor.capture(),
            any(), any(), any(), any(), any(), any(), any()
        )
        val saved = parentCaptor.firstValue
        // blank だった stableId は埋められる
        assertNotEquals("", saved.stableId)
        assertNotNull(saved.stableId)
        // identity の他の値は維持
        assertEquals(5L, saved.layoutId)
        assertEquals(100L, saved.createdAt)
        assertEquals(1, saved.sortOrder)
    }

    // ---------------------------------------------------------
    // helpers
    // ---------------------------------------------------------

    private fun simpleLayout(columns: Int = 5, rows: Int = 4): KeyboardLayout {
        // 1 個だけのシンプルなキー。convertToDbModel が走るのに最低限必要な状態。
        val key = KeyData(
            label = "あ",
            row = 0,
            column = 0,
            isFlickable = false,
            keyType = KeyType.NORMAL,
            isSpecialKey = false,
            keyId = "key-1",
            action = KeyAction.Text("あ")
        )
        return KeyboardLayout(
            keys = listOf(key),
            flickKeyMaps = mapOf(
                "key-1" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("あ")))
            ),
            columnCount = columns,
            rowCount = rows
        )
    }

    private fun fullLayout(
        layoutId: Long,
        stableId: String,
        createdAt: Long,
        sortOrder: Int,
        name: String
    ): FullKeyboardLayout {
        return FullKeyboardLayout(
            layout = CustomKeyboardLayout(
                layoutId = layoutId,
                name = name,
                columnCount = 5,
                rowCount = 4,
                isRomaji = false,
                isDirectMode = false,
                createdAt = createdAt,
                sortOrder = sortOrder,
                stableId = stableId
            ),
            keysWithFlicks = emptyList(),
            spacers = emptyList()
        )
    }
}
