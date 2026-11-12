package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.SharedPreferences
import androidx.core.content.edit
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet

/** Custom QWERTY keys start with the previously shared values, then remain independent. */
class CustomQwertyPopupPreferences(private val preferences: SharedPreferences) {
    init {
        migrate(preferences)
    }

    var keyPreview: PopupViewStyle
        get() = read(PREVIEW_SIZE, PREVIEW_TEXT)
        set(value) = write(PREVIEW_SIZE, PREVIEW_TEXT, value)

    var variation: PopupViewStyle
        get() = read(VARIATION_SIZE, VARIATION_TEXT)
        set(value) = write(VARIATION_SIZE, VARIATION_TEXT, value)

    fun styles() = QwertyPopupViewStyleSet(keyPreview, variation)

    private fun read(sizeKey: String, textKey: String) = PopupViewStyle(
        preferences.getInt(sizeKey, 100).coerceIn(50, 200),
        preferences.getFloat(textKey, 28f).coerceIn(8f, 48f)
    )

    private fun write(sizeKey: String, textKey: String, value: PopupViewStyle) {
        preferences.edit {
            putInt(sizeKey, value.sizeScalePercent.coerceIn(50, 200))
            putFloat(textKey, value.textSizeSp.coerceIn(8f, 48f))
        }
    }

    companion object {
        const val PREVIEW_SIZE = "custom_qwerty_key_preview_popup_size_scale_percent"
        const val PREVIEW_TEXT = "custom_qwerty_key_preview_popup_text_size_sp"
        const val VARIATION_SIZE = "custom_qwerty_variation_popup_size_scale_percent"
        const val VARIATION_TEXT = "custom_qwerty_variation_popup_text_size_sp"

        fun migrate(preferences: SharedPreferences) {
            val sizes = mapOf(
                PREVIEW_SIZE to "qwerty_key_preview_popup_size_scale_percent_preference",
                VARIATION_SIZE to "qwerty_variation_popup_size_scale_percent_preference"
            )
            val texts = mapOf(
                PREVIEW_TEXT to "qwerty_key_preview_popup_text_size_sp_preference",
                VARIATION_TEXT to "qwerty_variation_popup_text_size_sp_preference"
            )
            if ((sizes.keys + texts.keys).all(preferences::contains)) return
            preferences.edit {
                sizes.forEach { (target, source) ->
                    if (!preferences.contains(target)) putInt(target, preferences.getInt(source, 100).coerceIn(50, 200))
                }
                texts.forEach { (target, source) ->
                    if (!preferences.contains(target)) putFloat(target, preferences.getFloat(source, 28f).coerceIn(8f, 48f))
                }
            }
        }
    }
}
