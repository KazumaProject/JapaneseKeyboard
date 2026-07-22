package com.kazumaproject.markdownhelperkeyboard.short_cut

enum class ShortcutType(
    val id: String,
    val iconResId: Int,
    val activeIconResId: Int? = null,
    val description: String // 設定画面での表示用など
) {
    SETTINGS(
        "settings",
        com.kazumaproject.core.R.drawable.baseline_settings_24,
        description = "設定"
    ),
    EMOJI(
        "emoji",
        com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24,
        description = "絵文字"
    ),
    TEMPLATE(
        "template",
        com.kazumaproject.core.R.drawable.book_3_24px,
        description = "定型文"
    ),
    KEYBOARD_PICKER(
        "keyboard_picker",
        com.kazumaproject.core.R.drawable.language_24dp,
        description = "キーボード切替"
    ),
    KEYBOARD_LAYOUT_EDIT(
        "keyboard_layout_edit",
        com.kazumaproject.core.R.drawable.keyboard_24px,
        activeIconResId = com.kazumaproject.core.R.drawable.baseline_check_24,
        description = "キーボード調整"
    ),
    KEYBOARD_FLOATING_TOGGLE(
        "keyboard_floating_toggle",
        com.kazumaproject.core.R.drawable.keyboard_floating_24px,
        activeIconResId = com.kazumaproject.core.R.drawable.keyboard_normal_24px,
        description = "フローティング切替"
    ),
    INPUT_BEHAVIOR_TOGGLE(
        "input_behavior_toggle",
        com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px,
        activeIconResId = com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px,
        description = "入力方式切替"
    ),
    LIVE_CONVERSION_TOGGLE(
        "live_conversion_toggle",
        com.kazumaproject.core.R.drawable.live_conversion_24px,
        activeIconResId = com.kazumaproject.core.R.drawable.live_conversion_on_24px,
        description = "ライブ変換"
    ),
    LEARNING_PAUSE(
        "learning_pause",
        com.kazumaproject.core.R.drawable.dictionary_24px,
        activeIconResId = com.kazumaproject.core.R.drawable.incognito,
        description = "学習を一時停止"
    ),
    SELECT_ALL(
        "select_all",
        com.kazumaproject.core.R.drawable.text_select_start_24dp,
        description = "全選択"
    ),
    COPY(
        "copy",
        com.kazumaproject.core.R.drawable.content_copy_24dp,
        description = "コピー"
    ),
    PASTE(
        "paste",
        com.kazumaproject.core.R.drawable.content_paste_24px,
        description = "貼り付け"
    ),
    DATE_PICKER(
        "select_date",
        com.kazumaproject.core.R.drawable.calendar_today_24px,
        description = "日付"
    ),
    VOICE_INPUT(
        "voice_input",
        com.kazumaproject.core.R.drawable.settings_voice_24px,
        description = "音声入力"
    ),
    CLIP_BOARD(
        "clip_board",
        com.kazumaproject.core.R.drawable.clip_board,
        description = "クリップボード"
    )
    ;

    companion object {
        fun fromId(id: String): ShortcutType? = entries.find { it.id == id }
    }
}
