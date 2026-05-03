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
 * JSON import/export 専用 DTO 群。
 *
 * 重要:
 * - これらは外部 JSON ファイル(ユーザー編集の可能性あり, 旧バージョン由来)を
 *   parse するための「外側の型」であり、Room の Relation 用モデル
 *   ([com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout])
 *   とは明確に分離する。
 * - フィールドはすべて nullable / default null にする。
 *   Gson は Kotlin の constructor default 値を使わないため、欠損フィールドは
 *   null になる前提で扱う必要がある。
 * - 新規追加フィールド(rowOptions / columnOptions / layoutItems / keyMargins 等)を
 *   将来追加する場合も、このファイルに nullable で追加するだけで旧 JSON との
 *   互換性を壊さずに済む。
 */

/**
 * 新しい schemaVersion 付き object 形式の root DTO。
 *
 * 期待形:
 * ```
 * {
 *   "schemaVersion": 2,
 *   "layouts": [ ... ]
 * }
 * ```
 */
data class KeyboardLayoutExportFileDto(
    val schemaVersion: Int? = null,
    val layouts: List<KeyboardLayoutExportDto>? = null
)

/**
 * 1 レイアウト分の DTO。
 *
 * 旧 JSON では root array の各要素がこの形をしている。
 *
 * すべて nullable。
 * - layout が null の要素は import 対象から除外する。
 * - keysWithFlicks が null/欠損の場合は空 list として扱う。
 * - spacers が null/欠損の場合は空 list として扱う(古い JSON は spacers 自体が無い)。
 */
data class KeyboardLayoutExportDto(
    val layout: CustomKeyboardLayout? = null,
    val keysWithFlicks: List<KeyWithFlicksExportDto>? = null,
    val spacers: List<SpacerDefinition>? = null
)

/**
 * 1 キー + そのキーに紐づく flick/circular/twoStep/longPress 系マッピングの DTO。
 *
 * すべて nullable。
 * - key が null の要素は無効扱い(import 時に skip)。
 * - 各 flick 系 List が null/欠損の場合は空 list として扱う。
 */
data class KeyWithFlicksExportDto(
    val key: KeyDefinition? = null,
    val flicks: List<FlickMapping>? = null,
    val circularFlicks: List<CircularFlickMapping>? = null,
    val twoStepFlicks: List<TwoStepFlickMapping>? = null,
    val longPressFlicks: List<LongPressFlickMapping>? = null,
    val twoStepLongPressFlicks: List<TwoStepLongPressMappingEntity>? = null
)
