package com.kazumaproject.markdownhelperkeyboard.repository

import android.view.KeyEvent
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutAction
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutDao
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardShortcutRepositoryTest {
    @Test
    fun defaultShortcuts_containsAltApostropheFallback() {
        val fallback = PhysicalKeyboardShortcutRepository.defaultShortcuts.singleOrNull {
            it.context == "any" &&
                it.keyCode == KeyEvent.KEYCODE_APOSTROPHE &&
                it.alt &&
                !it.ctrl &&
                !it.shift &&
                !it.meta
        }

        assertNotNull(fallback)
        assertEquals(PhysicalKeyboardShortcutAction.CYCLE_INPUT_MODE.id, fallback?.actionId)
    }

    @Test
    fun ensureDefaultShortcuts_insertsAllWhenEmpty() = runTest {
        val dao = FakePhysicalKeyboardShortcutDao()
        PhysicalKeyboardShortcutRepository(dao).ensureDefaultShortcuts()

        assertEquals(
            PhysicalKeyboardShortcutRepository.defaultShortcuts.size,
            dao.items.size
        )
    }

    @Test
    fun ensureDefaultShortcuts_addsOnlyMissingDefaultsWhenNotEmpty() = runTest {
        val existingUserShortcut = PhysicalKeyboardShortcutItem(
            id = 100,
            context = "any",
            keyCode = KeyEvent.KEYCODE_APOSTROPHE,
            alt = true,
            actionId = PhysicalKeyboardShortcutAction.SWITCH_TO_JAPANESE.id,
            sortOrder = 100
        )
        val existingDefault = PhysicalKeyboardShortcutRepository.defaultShortcuts.first()
        val dao = FakePhysicalKeyboardShortcutDao(
            mutableListOf(existingUserShortcut, existingDefault.copy(id = 101))
        )

        PhysicalKeyboardShortcutRepository(dao).ensureDefaultShortcuts()

        assertEquals(1, dao.items.count { it.keyCode == KeyEvent.KEYCODE_APOSTROPHE && it.alt })
        assertEquals(
            PhysicalKeyboardShortcutAction.SWITCH_TO_JAPANESE.id,
            dao.items.single { it.keyCode == KeyEvent.KEYCODE_APOSTROPHE && it.alt }.actionId
        )
        assertEquals(1, dao.items.count { sameAssignment(it, existingDefault) })
        assertTrue(dao.items.size < PhysicalKeyboardShortcutRepository.defaultShortcuts.size + 2)
    }

    @Test
    fun ensureDefaultShortcuts_isIdempotent() = runTest {
        val dao = FakePhysicalKeyboardShortcutDao(mutableListOf(PhysicalKeyboardShortcutRepository.defaultShortcuts.first()))
        val repository = PhysicalKeyboardShortcutRepository(dao)

        repository.ensureDefaultShortcuts()
        val countAfterFirstEnsure = dao.items.size
        repository.ensureDefaultShortcuts()

        assertEquals(countAfterFirstEnsure, dao.items.size)
    }

    private class FakePhysicalKeyboardShortcutDao(
        val items: MutableList<PhysicalKeyboardShortcutItem> = mutableListOf()
    ) : PhysicalKeyboardShortcutDao {
        private var nextId = 1L

        override fun getAll(): Flow<List<PhysicalKeyboardShortcutItem>> = flowOf(items)

        override fun getEnabled(): Flow<List<PhysicalKeyboardShortcutItem>> = flowOf(items.filter { it.enabled })

        override fun getById(id: Long): Flow<PhysicalKeyboardShortcutItem?> =
            flowOf(items.firstOrNull { it.id == id })

        override suspend fun insert(item: PhysicalKeyboardShortcutItem): Long {
            val id = if (item.id == 0L) nextId++ else item.id
            items.add(item.copy(id = id))
            return id
        }

        override suspend fun insertAll(items: List<PhysicalKeyboardShortcutItem>) {
            items.forEach { insert(it) }
        }

        override suspend fun update(item: PhysicalKeyboardShortcutItem) {
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) items[index] = item
        }

        override suspend fun delete(item: PhysicalKeyboardShortcutItem) {
            items.removeIf { it.id == item.id }
        }

        override suspend fun deleteById(id: Long) {
            items.removeIf { it.id == id }
        }

        override suspend fun count(): Int = items.size

        override suspend fun findDuplicate(
            context: String,
            keyCode: Int,
            scanCode: Int?,
            ctrl: Boolean,
            shift: Boolean,
            alt: Boolean,
            meta: Boolean,
            excludeId: Long
        ): PhysicalKeyboardShortcutItem? {
            return items.firstOrNull {
                it.context == context &&
                    it.keyCode == keyCode &&
                    it.scanCode == scanCode &&
                    it.ctrl == ctrl &&
                    it.shift == shift &&
                    it.alt == alt &&
                    it.meta == meta &&
                    it.id != excludeId
            }
        }
    }
}

private fun sameAssignment(
    lhs: PhysicalKeyboardShortcutItem,
    rhs: PhysicalKeyboardShortcutItem
): Boolean {
    return lhs.context == rhs.context &&
        lhs.keyCode == rhs.keyCode &&
        lhs.scanCode == rhs.scanCode &&
        lhs.ctrl == rhs.ctrl &&
        lhs.shift == rhs.shift &&
        lhs.alt == rhs.alt &&
        lhs.meta == rhs.meta
}

