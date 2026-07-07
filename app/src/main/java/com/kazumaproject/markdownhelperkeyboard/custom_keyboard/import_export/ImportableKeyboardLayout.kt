package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity

/**
 * Import 用の正規化済み内部モデル。
 *
 * 外部 JSON ([KeyboardLayoutExportDto])を読み込み、欠損や null を
 * すべて埋めて非 null 化したものをこの型で表す。
 *
 * Repository 以降ではこのモデルだけを使うため、null 防御を都度書く必要はない。
 */
data class ImportableKeyboardLayout(
    val layout: CustomKeyboardLayout,
    val keysWithFlicks: List<ImportableKeyWithFlicks>,
    val spacers: List<SpacerDefinition>
)

/**
 * Import 用の正規化済み key + flick 系マッピング。
 *
 * 各 List は non-null。
 */
data class ImportableKeyWithFlicks(
    val key: KeyDefinition,
    val flicks: List<FlickMapping>,
    val circularFlicks: List<CircularFlickMapping>,
    val twoStepFlicks: List<TwoStepFlickMapping>,
    val longPressFlicks: List<LongPressFlickMapping>,
    val twoStepLongPressFlicks: List<TwoStepLongPressMappingEntity>
)
