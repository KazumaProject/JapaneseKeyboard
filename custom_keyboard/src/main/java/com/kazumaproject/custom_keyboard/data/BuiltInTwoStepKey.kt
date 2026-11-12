package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*

/** Compiled-in kana rows. Layout backups cannot create an instance with different outputs. */
enum class BuiltInTwoStepKey(val label: String) {
    A("あ"),
    KA("か"),
    SA("さ"),
    TA("た"),
    NA("な"),
    HA("は"),
    MA("ま"),
    YA("や"),
    RA("ら"),
    WA("わ"),
    PUNCTUATION("、。?!");

    companion object {
        internal fun forLabel(label: String): BuiltInTwoStepKey? = entries.firstOrNull { it.label == label }
    }
}

/** Fixed single-character vocabulary; no caller-supplied mappings or callbacks. */
internal object BuiltInTwoStepCharacters {
    private val rows: Map<BuiltInTwoStepKey, Map<TfbiFlickDirection, Map<TfbiFlickDirection, Char>>> = mapOf(
        BuiltInTwoStepKey.A to mapOf(
            TAP to mapOf(TAP to 'あ'),
            UP_RIGHT to mapOf(TAP to 'あ', UP_RIGHT to 'ぁ'),
            LEFT to mapOf(TAP to 'あ', LEFT to 'い', DOWN_LEFT to 'ぃ'),
            UP to mapOf(TAP to 'あ', UP to 'う', UP_LEFT to 'ぅ', UP_RIGHT to 'ゔ'),
            RIGHT to mapOf(TAP to 'あ', RIGHT to 'え', DOWN_RIGHT to 'ぇ'),
            DOWN to mapOf(TAP to 'あ', DOWN to 'お', DOWN_RIGHT to 'ぉ'),
        ),
        BuiltInTwoStepKey.KA to mapOf(
            TAP to mapOf(TAP to 'か'),
            UP_RIGHT to mapOf(TAP to 'か', UP_RIGHT to 'が'),
            LEFT to mapOf(TAP to 'か', LEFT to 'き', DOWN_LEFT to 'ぎ'),
            UP to mapOf(TAP to 'か', UP to 'く', UP_LEFT to 'ぐ'),
            RIGHT to mapOf(TAP to 'か', RIGHT to 'け', DOWN_RIGHT to 'げ'),
            DOWN to mapOf(TAP to 'か', DOWN to 'こ', DOWN_RIGHT to 'ご'),
        ),
        BuiltInTwoStepKey.SA to mapOf(
            TAP to mapOf(TAP to 'さ'),
            UP_RIGHT to mapOf(TAP to 'さ', UP_RIGHT to 'ざ'),
            LEFT to mapOf(TAP to 'さ', LEFT to 'し', DOWN_LEFT to 'じ'),
            UP to mapOf(TAP to 'さ', UP to 'す', UP_LEFT to 'ず'),
            RIGHT to mapOf(TAP to 'さ', RIGHT to 'せ', DOWN_RIGHT to 'ぜ'),
            DOWN to mapOf(TAP to 'さ', DOWN to 'そ', DOWN_RIGHT to 'ぞ'),
        ),
        BuiltInTwoStepKey.TA to mapOf(
            TAP to mapOf(TAP to 'た'),
            UP_RIGHT to mapOf(TAP to 'た', UP_RIGHT to 'だ'),
            LEFT to mapOf(TAP to 'た', LEFT to 'ち', DOWN_LEFT to 'ぢ'),
            UP to mapOf(TAP to 'た', UP to 'つ', UP_LEFT to 'づ', UP_RIGHT to 'っ'),
            RIGHT to mapOf(TAP to 'た', RIGHT to 'て', DOWN_RIGHT to 'で'),
            DOWN to mapOf(TAP to 'た', DOWN to 'と', DOWN_RIGHT to 'ど'),
        ),
        BuiltInTwoStepKey.NA to mapOf(
            TAP to mapOf(TAP to 'な'),
            LEFT to mapOf(TAP to 'な', LEFT to 'に'),
            UP to mapOf(TAP to 'な', UP to 'ぬ'),
            RIGHT to mapOf(TAP to 'な', RIGHT to 'ね'),
            DOWN to mapOf(TAP to 'な', DOWN to 'の'),
        ),
        BuiltInTwoStepKey.HA to mapOf(
            TAP to mapOf(TAP to 'は'),
            UP_RIGHT to mapOf(TAP to 'は', UP_RIGHT to 'ば'),
            UP_LEFT to mapOf(TAP to 'は', UP_LEFT to 'ぱ'),
            LEFT to mapOf(TAP to 'は', LEFT to 'ひ', DOWN_LEFT to 'び', UP_LEFT to 'ぴ'),
            UP to mapOf(TAP to 'は', UP to 'ふ', UP_RIGHT to 'ぷ', UP_LEFT to 'ぶ'),
            RIGHT to mapOf(TAP to 'は', RIGHT to 'へ', DOWN_RIGHT to 'べ', UP_RIGHT to 'ぺ'),
            DOWN to mapOf(TAP to 'は', DOWN to 'ほ', DOWN_RIGHT to 'ぼ', DOWN_LEFT to 'ぽ'),
        ),
        BuiltInTwoStepKey.MA to mapOf(
            TAP to mapOf(TAP to 'ま'),
            LEFT to mapOf(TAP to 'ま', LEFT to 'み'),
            UP to mapOf(TAP to 'ま', UP to 'む'),
            RIGHT to mapOf(TAP to 'ま', RIGHT to 'め'),
            DOWN to mapOf(TAP to 'ま', DOWN to 'も'),
        ),
        BuiltInTwoStepKey.YA to mapOf(
            TAP to mapOf(TAP to 'や'),
            UP_RIGHT to mapOf(TAP to 'や', UP_RIGHT to 'ゃ'),
            LEFT to mapOf(TAP to 'や', LEFT to '(', DOWN_LEFT to '「'),
            UP to mapOf(TAP to 'や', UP to 'ゆ', UP_LEFT to 'ゅ'),
            RIGHT to mapOf(TAP to 'や', RIGHT to ')', DOWN_RIGHT to '」'),
            DOWN to mapOf(TAP to 'や', DOWN to 'よ', DOWN_RIGHT to 'ょ'),
        ),
        BuiltInTwoStepKey.RA to mapOf(
            TAP to mapOf(TAP to 'ら'),
            LEFT to mapOf(TAP to 'ら', LEFT to 'り'),
            UP to mapOf(TAP to 'ら', UP to 'る'),
            RIGHT to mapOf(TAP to 'ら', RIGHT to 'れ'),
            DOWN to mapOf(TAP to 'ら', DOWN to 'ろ'),
        ),
        BuiltInTwoStepKey.WA to mapOf(
            TAP to mapOf(TAP to 'わ'),
            UP_RIGHT to mapOf(TAP to 'わ', UP_RIGHT to 'ゎ'),
            LEFT to mapOf(TAP to 'わ', LEFT to 'を'),
            UP to mapOf(TAP to 'わ', UP to 'ん'),
            RIGHT to mapOf(TAP to 'わ', RIGHT to 'ー'),
            DOWN to mapOf(TAP to 'わ', DOWN to '〜'),
        ),
        BuiltInTwoStepKey.PUNCTUATION to mapOf(
            TAP to mapOf(TAP to '、'),
            LEFT to mapOf(TAP to '、', LEFT to '。'),
            UP to mapOf(TAP to '、', UP to '？', UP_LEFT to '：', UP_RIGHT to '・'),
            RIGHT to mapOf(TAP to '、', RIGHT to '！'),
            DOWN to mapOf(TAP to '、', DOWN to '…'),
        ),
    )

    fun output(key: BuiltInTwoStepKey, first: TfbiFlickDirection, second: TfbiFlickDirection,
               rotation: TfbiFlickRotation): Char? = rows.getValue(key)[first]?.get(rotated(key, first, second, rotation))

    fun firstDirections(key: BuiltInTwoStepKey): Set<TfbiFlickDirection> = rows.getValue(key).keys - TAP

    fun secondDirections(key: BuiltInTwoStepKey, first: TfbiFlickDirection,
                         rotation: TfbiFlickRotation): Set<TfbiFlickDirection> =
        rows.getValue(key)[first].orEmpty().keys.filter { it != TAP }
            .map { rotated(key, first, it, rotation) }.toSet()

    fun guideMap(key: BuiltInTwoStepKey, rotation: TfbiFlickRotation): Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>> =
        rows.getValue(key).mapValues { (first, seconds) -> seconds.entries.associate { (second, value) ->
            rotated(key, first, second, rotation) to value.toString()
        } }

    private fun rotated(key: BuiltInTwoStepKey, first: TfbiFlickDirection, second: TfbiFlickDirection,
                        rotation: TfbiFlickRotation): TfbiFlickDirection {
        if (key !in setOf(BuiltInTwoStepKey.KA, BuiltInTwoStepKey.SA, BuiltInTwoStepKey.TA, BuiltInTwoStepKey.HA)) return second
        val swap = when (rotation) {
            TfbiFlickRotation.DEFAULT -> false
            TfbiFlickRotation.CLOCKWISE -> first in setOf(LEFT, UP, DOWN)
            TfbiFlickRotation.COUNTERCLOCKWISE -> first == RIGHT
        }
        if (!swap) return second
        return when (first) {
            LEFT -> when (second) { UP_LEFT -> DOWN_LEFT; DOWN_LEFT -> UP_LEFT; else -> second }
            RIGHT -> when (second) { UP_RIGHT -> DOWN_RIGHT; DOWN_RIGHT -> UP_RIGHT; else -> second }
            UP -> when (second) { UP_LEFT -> UP_RIGHT; UP_RIGHT -> UP_LEFT; else -> second }
            DOWN -> when (second) { DOWN_LEFT -> DOWN_RIGHT; DOWN_RIGHT -> DOWN_LEFT; else -> second }
            else -> second
        }
    }
}
