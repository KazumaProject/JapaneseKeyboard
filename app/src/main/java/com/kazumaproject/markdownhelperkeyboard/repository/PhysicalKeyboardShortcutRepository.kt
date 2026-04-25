package com.kazumaproject.markdownhelperkeyboard.repository

import android.view.KeyEvent
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutDao
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhysicalKeyboardShortcutRepository @Inject constructor(
    private val dao: PhysicalKeyboardShortcutDao
) {
    fun getAll(): Flow<List<PhysicalKeyboardShortcutItem>> = dao.getAll()
    fun getEnabled(): Flow<List<PhysicalKeyboardShortcutItem>> = dao.getEnabled()
    fun getById(id: Long): Flow<PhysicalKeyboardShortcutItem?> = dao.getById(id)

    suspend fun insert(item: PhysicalKeyboardShortcutItem): Boolean = withContext(Dispatchers.IO) {
        if (findDuplicate(item) != null) return@withContext false
        dao.insert(item)
        true
    }

    suspend fun update(item: PhysicalKeyboardShortcutItem): Boolean = withContext(Dispatchers.IO) {
        if (findDuplicate(item, excludeId = item.id) != null) return@withContext false
        dao.update(item)
        true
    }

    suspend fun delete(item: PhysicalKeyboardShortcutItem) = withContext(Dispatchers.IO) {
        dao.delete(item)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun ensureDefaultShortcuts() = withContext(Dispatchers.IO) {
        if (dao.count() == 0) {
            dao.insertAll(defaultShortcuts)
        }
    }

    private suspend fun findDuplicate(
        item: PhysicalKeyboardShortcutItem,
        excludeId: Long = 0
    ): PhysicalKeyboardShortcutItem? {
        return dao.findDuplicate(
            context = item.context,
            keyCode = item.keyCode,
            scanCode = item.scanCode,
            ctrl = item.ctrl,
            shift = item.shift,
            alt = item.alt,
            meta = item.meta,
            excludeId = excludeId
        )
    }

    companion object {
        val defaultShortcuts = listOf(
            PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_C, ctrl = true, actionId = "copy", sortOrder = 0),
            PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_V, ctrl = true, actionId = "paste", sortOrder = 1),
            PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_X, ctrl = true, actionId = "cut", sortOrder = 2),
            PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_A, ctrl = true, actionId = "select_all", sortOrder = 3),
            PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_SPACE, ctrl = true, actionId = "cycle_input_mode", sortOrder = 4),
            PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_HENKAN, actionId = "switch_to_japanese", sortOrder = 5),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_SPACE, actionId = "convert", sortOrder = 6),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_F6, actionId = "convert_to_hiragana", sortOrder = 7),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_F7, actionId = "convert_to_full_katakana", sortOrder = 8),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_F8, actionId = "convert_to_half_width", sortOrder = 9),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_F9, actionId = "convert_to_full_alphanumeric", sortOrder = 10),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_F10, actionId = "convert_to_half_alphanumeric", sortOrder = 11),
            PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_MUHENKAN, actionId = "switch_to_english", sortOrder = 12),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_SPACE, actionId = "convert_next", sortOrder = 13),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_SPACE, shift = true, actionId = "convert_prev", sortOrder = 14),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_DPAD_UP, actionId = "convert_prev", sortOrder = 15),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_DPAD_DOWN, actionId = "convert_next", sortOrder = 16),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_ENTER, actionId = "commit", sortOrder = 17),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_DEL, actionId = "cancel", sortOrder = 18),
            PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_FORWARD_DEL, actionId = "cancel", sortOrder = 19),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_LEFT, actionId = "segment_focus_left", sortOrder = 20),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_RIGHT, actionId = "segment_focus_right", sortOrder = 21),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_LEFT, shift = true, actionId = "segment_width_shrink", sortOrder = 22),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_RIGHT, shift = true, actionId = "segment_width_expand", sortOrder = 23),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_UP, actionId = "convert_prev", sortOrder = 24),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_DOWN, actionId = "convert_next", sortOrder = 25),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_SPACE, actionId = "convert_next", sortOrder = 26),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_SPACE, shift = true, actionId = "convert_prev", sortOrder = 27),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_ENTER, actionId = "commit", sortOrder = 28),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DEL, actionId = "cancel", sortOrder = 29),
            PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_FORWARD_DEL, actionId = "cancel", sortOrder = 30),
        )
    }
}
