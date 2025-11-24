package com.kazumaproject.markdownhelperkeyboard.short_cut

enum class ShortcutType(
    val id: String,
    val iconResId: Int,
    val description: String // 設定画面での表示用など
) {
    SETTINGS("settings", com.kazumaproject.core.R.drawable.baseline_settings_24, "設定"),
    EMOJI("emoji", com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24, "絵文字"),
    TEMPLATE("template", com.kazumaproject.core.R.drawable.book_3_24px, "定型文"),
    KEYBOARD_PICKER(
        "keyboard_picker",
        com.kazumaproject.core.R.drawable.language_24dp,
        "キーボード切替"
    ),
    SELECT_ALL("select_all", com.kazumaproject.core.R.drawable.text_select_start_24dp, "全選択"),
    COPY("copy", com.kazumaproject.core.R.drawable.content_copy_24dp, "コピー"),
    PASTE("paste", com.kazumaproject.core.R.drawable.content_paste_24px, "貼り付け"),
    DATE_PICKER(
        "select_date",
        com.kazumaproject.core.R.drawable.calendar_today_24px,
        "日付"
    ), ;

    companion object {
        fun fromId(id: String): ShortcutType? = entries.find { it.id == id }
    }
}
