package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsBackupRepository @Inject constructor(
    private val shortcutRepository: ShortcutRepository
) {
    suspend fun exportAllToJson(): String = withContext(Dispatchers.IO) {
        AppPreference.exportAllToJson(
            shortcutIds = shortcutRepository.getShortcutsForBackup().map { it.id }
        )
    }

    suspend fun importAllFromJson(json: String) = withContext(Dispatchers.IO) {
        val shortcutIds = AppPreference.importAllFromJson(json, replaceAll = true)
        // Legacy backups do not contain toolbar data, so keep the current selection.
        if (shortcutIds != null) {
            shortcutRepository.updateShortcuts(
                shortcutIds.filterNotNull().mapNotNull { ShortcutType.fromId(it) }
            )
        }
    }
}
