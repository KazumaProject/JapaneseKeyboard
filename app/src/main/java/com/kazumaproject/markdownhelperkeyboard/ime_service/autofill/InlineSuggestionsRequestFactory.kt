package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Size
import android.util.TypedValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.content.ContextCompat
import com.kazumaproject.markdownhelperkeyboard.R

@RequiresApi(Build.VERSION_CODES.R)
internal object InlineSuggestionsRequestFactory {

    const val MAX_SUGGESTION_COUNT = 4
    private const val MIN_CHIP_WIDTH_DP = 64
    private const val MAX_CHIP_WIDTH_DP = 240
    private const val CHIP_HEIGHT_DP = 48

    // These style mutators are the AndroidX Inline Autofill API used by the AOSP
    // sample, but the artifact currently marks them RestrictedApi for lint.
    @SuppressLint("RestrictedApi")
    fun create(context: Context): InlineSuggestionsRequest {
        val chipBackground = Icon.createWithResource(
            context,
            R.drawable.inline_suggestion_chip_background,
        )
        val textColor = ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color,
        )
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackground(chipBackground)
                    .setPadding(dp(context, 8), 0, dp(context, 8), 0)
                    .build()
            )
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(chipBackground)
                    .setPadding(dp(context, 12), 0, dp(context, 12), 0)
                    .build()
            )
            .setStartIconStyle(
                ImageViewStyle.Builder()
                    .setLayoutMargin(0, 0, dp(context, 4), 0)
                    .build()
            )
            .setTitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(textColor)
                    .setTextSize(15f)
                    .build()
            )
            .setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(textColor)
                    .setTextSize(13f)
                    .build()
            )
            .setEndIconStyle(
                ImageViewStyle.Builder()
                    .setLayoutMargin(dp(context, 4), 0, 0, 0)
                    .build()
            )
            .build()

        val styles = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()
        val minWidth = dp(context, MIN_CHIP_WIDTH_DP)
        val maxWidth = dp(context, MAX_CHIP_WIDTH_DP)
            .coerceAtMost(context.resources.displayMetrics.widthPixels)
            .coerceAtLeast(minWidth)
        val height = dp(context, CHIP_HEIGHT_DP)
        val specs = ArrayList<InlinePresentationSpec>(MAX_SUGGESTION_COUNT).apply {
            repeat(MAX_SUGGESTION_COUNT) {
                add(
                    InlinePresentationSpec.Builder(
                        Size(minWidth, height),
                        Size(maxWidth, height),
                    ).setStyle(styles).build()
                )
            }
        }
        return InlineSuggestionsRequest.Builder(specs)
            .setMaxSuggestionCount(MAX_SUGGESTION_COUNT)
            .build()
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()
}
