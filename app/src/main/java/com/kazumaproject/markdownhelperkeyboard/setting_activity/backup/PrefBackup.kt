package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

data class PrefBackup(
    val version: Int = 1,
    val entries: List<PrefEntry>,
    // Stable ShortcutType IDs in toolbar order; absent in preference-only backups.
    val shortcutIds: List<String>? = null,
)
