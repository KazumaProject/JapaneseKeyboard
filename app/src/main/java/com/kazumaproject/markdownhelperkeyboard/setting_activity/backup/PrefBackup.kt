package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

data class PrefBackup(
    val version: Int = 1,
    val entries: List<PrefEntry>,
)
